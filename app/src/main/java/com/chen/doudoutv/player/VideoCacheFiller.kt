package com.chen.doudoutv.player

import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.PriorityTaskManager
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.ContentMetadata
import com.chen.doudoutv.core.VideoCache
import java.io.IOException

@UnstableApi
internal class VideoCacheFiller(
    private val pipeline: CloudCachePipeline,
    private val chunkBytes: Long,
    private val priorityTaskManager: PriorityTaskManager,
    private val busy: () -> Boolean,
) {
    private class Session(val url: String, val key: String) {
        @Volatile var playerPos = 0L
        @Volatile var stop = false
        var download: CloudCachePipeline.Download? = null
    }

    private val lock = Any()
    @Volatile private var cancelled = false
    @Volatile private var current: Session? = null
    private var worker: Thread? = null
    private var retired: String? = null

    fun onPlayerOpen(playUrl: String, streamPos: Long) {
        if (cancelled) return
        val key = VideoCache.keyFor(playUrl)
        synchronized(lock) {
            if (cancelled) return
            if (key == null) {
                stopLocked()
                return
            }
            val existing = current
            if (existing != null && existing.url == playUrl) {
                existing.playerPos = streamPos
                return
            }
            stopLocked()
            if (retired == key) return
            val session = Session(playUrl, key).apply { playerPos = streamPos }
            current = session
            // 引用覆盖异步收尾，避免播放器释放后设置页先关掉仍在提交半块的缓存。
            VideoCache.beginUse()
            val thread = Thread({ run(session) }, "VideoCacheFill").apply { isDaemon = true }
            worker = thread
            thread.start()
        }
    }

    fun onMediaItemChanged(playUrl: String?) {
        synchronized(lock) {
            if (current?.url != playUrl) stopLocked()
        }
    }

    fun onPlayerRead(playUrl: String, streamPos: Long) {
        current?.takeIf { it.url == playUrl }?.playerPos = streamPos
    }

    fun onPlayerWorkChanged() {
        if (!busy()) return
        synchronized(lock) {
            current?.download?.calls?.cancel()
            worker?.interrupt()
        }
    }

    fun stop() {
        synchronized(lock) { stopLocked() }
    }

    private fun stopLocked() {
        current?.let {
            it.stop = true
            it.download?.calls?.cancel()
        }
        worker?.interrupt()
        worker = null
        current = null
    }

    fun onItemEnded(playUrl: String) {
        val key = VideoCache.keyFor(playUrl) ?: return
        val stopping: Thread?
        synchronized(lock) {
            retired = key
            stopping = worker
            stopLocked()
        }
        Thread {
            // 必须等半块提交完成再删，不能超时后边删边写。
            stopping?.join()
            VideoCache.drop(key)
        }.apply { isDaemon = true }.start()
    }

    fun cancel() {
        synchronized(lock) {
            cancelled = true
            stopLocked()
        }
    }

    private fun run(session: Session) {
        var idleRounds = 0
        var noLengthSince = 0L
        priorityTaskManager.add(C.PRIORITY_DOWNLOAD)
        try {
            while (!cancelled && !session.stop) {
                Thread.interrupted()
                if (!VideoCache.enabled) return
                if (busy()) {
                    noLengthSince = 0L
                    if (!waitForIdle(session)) continue
                }
                if (cancelled || session.stop) break
                val bundle = runCatching { pipeline.bundleOrNull() }.getOrNull() ?: return
                val cache = bundle.cache
                val total = ContentMetadata.getContentLength(cache.getContentMetadata(session.key))
                if (total == C.LENGTH_UNSET.toLong()) {
                    val now = SystemClock.elapsedRealtime()
                    if (noLengthSince == 0L) noLengthSince = now
                    if (now - noLengthSince > MAX_NO_LENGTH_MS) {
                        retire(session)
                        return
                    }
                    sleepQuietly(session)
                    continue
                }
                noLengthSince = 0L
                val pos = (session.playerPos + AHEAD_CHUNKS * chunkBytes).coerceIn(0L, total)
                val cachedRun = runCatching { cache.getCachedLength(session.key, pos, total - pos) }
                    .getOrDefault(0L).coerceAtLeast(0L)
                val cursor = (pos + cachedRun).coerceAtMost(total)
                if (cursor >= total) {
                    retire(session)
                    return
                }
                val written = fetchOne(session, bundle, cursor, minOf(chunkBytes, total - cursor))
                if (written == null) {
                    sleepQuietly(session)
                    continue
                }
                if (written <= 0L) {
                    if (++idleRounds >= MAX_IDLE_ROUNDS) {
                        retire(session)
                        return
                    }
                    sleepQuietly(session)
                    continue
                }
                idleRounds = 0
            }
        } catch (t: Exception) {
            Log.w(TAG, "预读退出：${t.javaClass.simpleName}")
        } finally {
            priorityTaskManager.remove(C.PRIORITY_DOWNLOAD)
            VideoCache.endUse()
            synchronized(lock) {
                if (current === session) {
                    current = null
                    worker = null
                }
            }
        }
    }

    private fun fetchOne(
        session: Session,
        bundle: CloudCachePipeline.Bundle,
        streamPos: Long,
        block: Long,
    ): Long? {
        val download = synchronized(lock) {
            if (cancelled || session.stop || busy()) return null
            bundle.newDownload().also { session.download = it }
        }
        val source = download.source
        var bytes = 0L
        try {
            if (busy()) download.calls.cancel()
            if (download.calls.isCancelled) return null
            source.open(
                DataSpec.Builder()
                    .setUri(Uri.parse(session.url))
                    .setPosition(streamPos)
                    .setLength(block)
                    .build()
            )
            val buffer = ByteArray(READ_BYTES)
            while (bytes < block) {
                if (cancelled || session.stop || busy() || download.calls.isCancelled) return null
                val n = source.read(buffer, 0, minOf(buffer.size.toLong(), block - bytes).toInt())
                if (n == C.RESULT_END_OF_INPUT) break
                bytes += n
            }
            return bytes
        } catch (e: IOException) {
            if (cancelled || session.stop || download.calls.isCancelled ||
                e is PriorityTaskManager.PriorityTooLowException || Thread.currentThread().isInterrupted
            ) return null
            Log.w(TAG, "预读下载失败 ${e.javaClass.simpleName} 已拉=${bytes / 1024}KB")
            return 0L
        } finally {
            // 优先级中断不是下载失败；close 在原线程提交半块，不跨线程操作 CacheDataSource。
            runCatching { source.close() }
            synchronized(lock) {
                if (session.download === download) session.download = null
            }
        }
    }

    private fun waitForIdle(session: Session): Boolean {
        while (!cancelled && !session.stop && busy()) {
            if (!sleepQuietly(session)) return false
        }
        return !cancelled && !session.stop
    }

    private fun sleepQuietly(session: Session): Boolean = try {
        Thread.sleep(SLEEP_MS)
        !cancelled && !session.stop
    } catch (_: InterruptedException) {
        false
    }

    private fun retire(session: Session) {
        synchronized(lock) {
            if (current === session) retired = session.key
        }
    }

    private companion object {
        const val TAG = "VideoCacheFill"
        const val READ_BYTES = 64 * 1024
        const val SLEEP_MS = 400L
        const val MAX_NO_LENGTH_MS = 10_000L
        const val MAX_IDLE_ROUNDS = 3
        const val AHEAD_CHUNKS = 2L
    }
}