package com.chen.doudoutv.player

import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.PriorityTaskManager
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.ContentMetadata
import com.chen.doudoutv.core.VideoCache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * 网盘直链的取数层：流坐标、磁盘缓存、让路，三件事收口在这一个类。
 *
 * 流坐标：防和谐网盘在视频前塞诱饵图，第 0 字节实为文件第 prefix 字节。平移曾在 OkHttp
 * 拦截器里做，与缓存按 DataSpec 的坐标一叠就 READ_POSITION_OUT_OF_RANGE（以前禁用缓存的
 * 真因）。现在平移只在本类发生，上层（含缓存记账）只见流坐标。
 *
 * 分块：夸克 CDN 只有带结尾的定长 Range 闭区间跑得满、且按连接累计限速，故每块 32MB +
 * 每块换新连接。块边界对上层不可见；硬要求：绝不能提前返回 END_OF_INPUT，否则缓存元数据
 * 总长记成半截、整集截断。
 *
 * 缓存挂载：[VideoCacheDataSource] 是唯一决定走不走缓存的地方（判据=URL 是否登记）；
 * Emby 直链、m3u8、容量档关闭都拿裸 OkHttpDataSource，行为与无缓存版逐字节一致。
 *
 * [StreamSpaceProbe]：一次 16KB 闭区间请求同时拿前缀长与总长（Content-Range），
 * 头部像诱饵图时逐级放大窗口找真实容器起点。
 */
/** C.LENGTH_UNSET 在 Java 侧是 int，而 DataSpec.length / 缓存记的总长是 long，Kotlin 比不了；值一致 */
private const val UNSET_LENGTH = -1L

@UnstableApi
internal class StreamSpaceProbe(
    private val client: OkHttpClient,
    private val headers: Map<String, String>,
    private val normalizer: PlaybackNormalizer,
) {

    /** 探测不出流坐标（服务器不认 Range / 伪装图后面找不到容器）时抛这个，调用方据此退回纯网络路径 */
    class Unsupported(message: String) : IOException(message)

    fun cachedPlan(url: String): PlaybackNormalizer.Plan? =
        normalizer.planOf(url)?.takeIf { it.totalBytes != UNSET_LENGTH }

    @Throws(IOException::class)
    fun ensure(url: String): PlaybackNormalizer.Plan {
        cachedPlan(url)?.let { return it }
        return probe(url).also { normalizer.remember(url, it) }
    }

    private fun probe(url: String): PlaybackNormalizer.Plan {
        val first = fetch(url, HEAD_WINDOW)
        val plan = if (normalizer.looksLikeImage(first.head)) {
            normalizer.detect(first.head, allowPrefix = true) ?: run {
                var found: PlaybackNormalizer.Plan? = null
                for (limit in longArrayOf(64L * 1024, 256L * 1024)) {
                    found = normalizer.detect(fetch(url, limit).head, allowPrefix = true)
                    if (found != null) break
                }
                found ?: throw Unsupported("伪装图之后 256KB 内找不到容器，这条直链不缓存")
            }
        } else {
            // 正常文件只认第 0 字节，避免把 MP4 的 free/wide 前置 box 当成伪装前缀
            normalizer.detect(first.head, allowPrefix = false)
                ?: PlaybackNormalizer.Plan(0L, PlaybackNormalizer.Container.UNKNOWN, "文件头即容器")
        }
        val total = first.totalBytes
        return plan.copy(totalBytes = total)
    }

    /** 取文件开头 size 字节，同时拿到文件总长 */
    private fun fetch(url: String, size: Long): Head {
        val builder = Request.Builder()
            .url(url)
            .header("Range", "bytes=0-${size - 1}")
        headers.forEach { (name, value) -> builder.header(name, value) }
        val call = runCatching { client.newCall(builder.build()) }
            .getOrElse { throw Unsupported("探测请求建不出来：${it.message}") }
        val response = try {
            call.execute()
        } catch (e: IOException) {
            throw Unsupported("探测失败：${e.message}")
        }
        response.use {
            val code = it.code
            // 带了 Range 却不回 206 = 服务器不认区间，按块取数这条路走不通
            if (code != 206) throw Unsupported("服务器不支持 Range（code=$code），这条直链不缓存")
            val total = contentRangeTotal(it.header("Content-Range"))
            if (total <= 0L) throw Unsupported("Content-Range 里拿不到文件总长")
            val head = runCatching { it.peekBody(size).bytes() }.getOrDefault(ByteArray(0))
            return Head(head, total)
        }
    }

    /** Content-Range: bytes 0-16383/858993459 */
    private fun contentRangeTotal(value: String?): Long =
        RANGE_TOTAL_REGEX.find(value.orEmpty())?.groupValues?.get(1)?.toLongOrNull() ?: -1L

    private class Head(val head: ByteArray, val totalBytes: Long)

    private companion object {
        const val HEAD_WINDOW = 16L * 1024
        val RANGE_TOTAL_REGEX = Regex("bytes\\s+\\d+-\\d+/(\\d+)")
    }
}

/**
 * 流坐标读取层：把播放器的 DataSpec（流坐标）翻译成网盘的闭区间 Range（文件坐标），
 * 并在块边界内部续请求，对外表现成一条连续流。
 */
@UnstableApi
internal class StreamSpaceDataSource(
    private val httpFactory: DataSource.Factory,
    private val probe: StreamSpaceProbe,
    private val chunkBytes: Long,
) : DataSource {

    private val listeners = ArrayList<TransferListener>()
    private var http: DataSource? = null
    private var requestUri: Uri? = null
    private var streamLength = UNSET_LENGTH
    private var prefix = 0L

    /** 下一条将交付给上层的字节的流坐标 */
    private var nextPos = 0L

    /** 本次 open 还要交付多少流字节；归零就按约定返回 END_OF_INPUT */
    private var requestRemaining = 0L
    private var chunkDelivered = 0L
    private var emptyChunks = 0

    override fun addTransferListener(transferListener: TransferListener) {
        listeners += transferListener
    }

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        val plan = probe.ensure(dataSpec.uri.toString())
        prefix = plan.prefixBytes
        streamLength = plan.totalBytes - prefix
        requestUri = dataSpec.uri
        nextPos = dataSpec.position.coerceAtMost(streamLength)
        val available = (streamLength - nextPos).coerceAtLeast(0L)
        requestRemaining =
            if (dataSpec.length == UNSET_LENGTH) available else minOf(dataSpec.length, available)
        // 关键：这里必须回「本次请求的剩余流长度」，不能回一个分块大小 ——
        // CacheDataSource 会拿这个数当整条资源写进缓存元数据，回小了整集就只剩这一块。
        if (requestRemaining == 0L) return 0L
        openChunk(nextPos)
        return requestRemaining
    }

    private fun openChunk(streamPos: Long) {
        closeHttp()
        val size = minOf(chunkBytes, streamLength - streamPos).coerceAtLeast(1L)
        val source = httpFactory.createDataSource()
        listeners.forEach { source.addTransferListener(it) }
        http = source
        chunkDelivered = 0L
        source.open(
            DataSpec.Builder()
                .setUri(checkNotNull(requestUri))
                .setPosition(streamPos + prefix)
                .setLength(size)
                .build()
        )
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (requestRemaining <= 0L) return C.RESULT_END_OF_INPUT
        val source = http ?: return C.RESULT_END_OF_INPUT
        val n = source.read(buffer, offset, minOf(length.toLong(), requestRemaining).toInt())
        if (n == C.RESULT_END_OF_INPUT) {
            if (nextPos >= streamLength) return C.RESULT_END_OF_INPUT   // 真正的流末尾
            if (chunkDelivered == 0L) {
                // 开口成功但一个字节没给，再来三次还是这样就报故障，别在这空转
                if (++emptyChunks >= 3) throw IOException("连续 $emptyChunks 个分块拿不到字节")
            } else {
                emptyChunks = 0
            }
            openChunk(nextPos)
            return read(buffer, offset, length)
        }
        nextPos += n
        chunkDelivered += n
        requestRemaining -= n
        return n
    }

    override fun getUri(): Uri? = requestUri

    override fun getResponseHeaders(): Map<String, List<String>> =
        http?.getResponseHeaders() ?: emptyMap()

    @Throws(IOException::class)
    override fun close() {
        closeHttp()
        requestUri = null
        requestRemaining = 0L
        nextPos = 0L
        streamLength = UNSET_LENGTH
    }

    private fun closeHttp() {
        val source = http ?: return
        http = null
        // 残包关掉失败不外抛：那是我们主动放弃的字节，不该盖掉真正的读错误
        runCatching { source.close() }
    }

    class Factory(
        private val httpFactory: DataSource.Factory,
        private val probe: StreamSpaceProbe,
        private val chunkBytes: Long,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            StreamSpaceDataSource(httpFactory, probe, chunkBytes)
    }
}

/**
 * 一条播放会话的缓存挂载点：SimpleCache 与 CacheDataSource 工厂都在这里收口。
 *
 * [bundleOrNull] 会打开缓存目录和一张 SQLite 索引表，只许在 IO / 播放器加载线程调用；
 * 主线程（设置页、看完删）只用 [com.chen.doudoutv.core.VideoCache] 上那几个不新建的方法。
 */
@UnstableApi
internal class CloudCachePipeline(
    private val client: OkHttpClient,
    private val headers: Map<String, String>,
    normalizer: PlaybackNormalizer,
    private val httpFactory: DataSource.Factory,
    private val chunkBytes: Long,
    private val priorityTaskManager: PriorityTaskManager,
) {

    val probe = StreamSpaceProbe(client, headers, normalizer)

    private val lock = Any()
    private var built: Bundle? = null

    /** 容量档关闭或 SimpleCache 建不起来时返回 null，调用方退回纯网络 */
    fun bundleOrNull(): Bundle? {
        val cache = runCatching { VideoCache.cacheOrNull() }.getOrNull() ?: return null
        synchronized(lock) {
            built?.takeIf { it.cache === cache }?.let { return it }
            return Bundle(cache).also { built = it }
        }
    }

    private fun newFactory(cache: Cache, upstream: DataSource.Factory): CacheDataSource.Factory =
        CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(
                StreamSpaceDataSource.Factory(upstream, probe, chunkBytes)
            )
            // key 已经在 VideoCacheDataSource 里查过一遍，这里只是把它交给 Media3
            .setCacheKeyFactory { dataSpec ->
                VideoCache.keyFor(dataSpec.uri.toString()) ?: dataSpec.uri.toString()
            }
            // 缓存这一层出任何问题都只让缓存失效，不许把正在播的那一集拖下水
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    inner class Bundle(val cache: Cache) {
        private val playbackFactory = newFactory(cache, httpFactory)

        /** 播放器不受预读优先级限制，也不等待预读占有的缓存区间锁。 */
        fun newPlaybackSource(): DataSource = playbackFactory.createDataSource()

        fun newDownload(): Download {
            val calls = PreloadCallFactory(client)
            val upstream = OkHttpDataSource.Factory(calls).setDefaultRequestProperties(headers)
            val source = newFactory(cache, upstream)
                .setUpstreamPriorityTaskManager(priorityTaskManager)
                .createDataSourceForDownloading()
            return Download(source, calls)
        }
    }

    class Download(val source: DataSource, val calls: PreloadCallFactory)
}

/**
 * 播放器实际拿到的 DataSource —— 「这条直链走不走磁盘缓存」只在 [open] 判一次。
 *
 * 判据是 URL 有没有被登记过（[com.chen.doudoutv.core.VideoCache.keyFor]）：
 * Emby 直链、m3u8 分片、容量档关闭时都拿不到 key，于是每次 open 新建一个裸
 * OkHttpDataSource，Range 改写仍由拦截器负责 —— 与今天逐字节一致。
 */
@UnstableApi
internal class VideoCacheDataSource(
    private val pipeline: CloudCachePipeline,
    private val httpFactory: DataSource.Factory,
    private val onStreamOpen: (url: String, streamPos: Long) -> Unit,
    private val onStreamRead: (url: String, streamPos: Long) -> Unit,
) : DataSource {

    private val listeners = ArrayList<TransferListener>()
    private var active: DataSource? = null

    /** 走了缓存的那条直链，本次 open 已交付到哪个流位置；没走缓存时为 null */
    private var trackedUrl: String? = null
    private var readUntil = 0L
    private var lastReportedUntil = 0L

    override fun addTransferListener(transferListener: TransferListener) {
        listeners += transferListener
    }

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        val delegate = cacheSourceFor(dataSpec) ?: httpFactory.createDataSource().also { trackedUrl = null }
        listeners.forEach { delegate.addTransferListener(it) }
        active = delegate
        trackedUrl?.let { onStreamOpen(it, dataSpec.position) }
        return delegate.open(dataSpec)
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val n = checkNotNull(active).read(buffer, offset, length)
        val url = trackedUrl
        if (n > 0 && url != null) {
            readUntil += n
            // 预读只需要知道播放器大致读到哪，不必每 16KB 通知一次
            if (readUntil - lastReportedUntil >= READ_REPORT_STEP) {
                lastReportedUntil = readUntil
                onStreamRead(url, readUntil)
            }
        }
        return n
    }

    private fun cacheSourceFor(dataSpec: DataSpec): DataSource? {
        val url = dataSpec.uri.toString()
        val key = VideoCache.keyFor(url) ?: return null
        val bundle = pipeline.bundleOrNull() ?: return null
        val plan = runCatching { pipeline.probe.ensure(url) }.getOrElse {
            Log.w("VideoCache", "流坐标探测失败，这条直链退回纯网络：${it.message}")
            VideoCache.exclude(url)
            return null
        }
        dropIfStale(bundle.cache, key, plan)
        trackedUrl = url
        readUntil = dataSpec.position
        lastReportedUntil = dataSpec.position
        return bundle.newPlaybackSource()
    }

    private fun dropIfStale(cache: Cache, key: String, plan: PlaybackNormalizer.Plan) {
        val cachedLength = ContentMetadata.getContentLength(cache.getContentMetadata(key))
        if (cachedLength == UNSET_LENGTH || cachedLength == plan.totalBytes - plan.prefixBytes) {
            return
        }
        // 同一集换了文件（重传 / 换清晰度），或者上次的前缀探错了 —— 错位的半截缓存比没缓存更糟
        Log.w(
            "VideoCache",
            "缓存记的总长=$cachedLength 与本集流长=${plan.totalBytes - plan.prefixBytes} 不符，整条作废"
        )
        runCatching { cache.removeResource(key) }
    }

    override fun getUri(): Uri? = active?.getUri()

    override fun getResponseHeaders(): Map<String, List<String>> =
        active?.getResponseHeaders() ?: emptyMap()

    @Throws(IOException::class)
    override fun close() {
        trackedUrl?.let { onStreamRead(it, readUntil) }
        trackedUrl = null
        val source = active ?: return
        active = null
        source.close()
    }

    class Factory(
        private val pipeline: CloudCachePipeline,
        private val httpFactory: DataSource.Factory,
        private val onStreamOpen: (String, Long) -> Unit,
        private val onStreamRead: (String, Long) -> Unit,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            VideoCacheDataSource(pipeline, httpFactory, onStreamOpen, onStreamRead)
    }

    private companion object {
        /** 播放器每读够这么多字节才回灌一次游标，够预读用，又不至于每 16KB 通知一次 */
        const val READ_REPORT_STEP = 4L * 1024 * 1024
    }
}
