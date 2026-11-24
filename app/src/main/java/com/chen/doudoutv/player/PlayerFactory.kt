package com.chen.doudoutv.player

import android.content.Context
import android.net.Uri
import android.util.Log
import com.chen.doudoutv.core.LogGate
import com.chen.doudoutv.core.VideoCache
import androidx.media3.common.C
import androidx.media3.common.FileTypes
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PriorityTaskManager
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.image.ImageRenderer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.extractor.mp4.FragmentedMp4Extractor
import androidx.media3.extractor.mp4.Mp4Extractor
import androidx.media3.extractor.ts.PsExtractor
import androidx.media3.extractor.ts.TsExtractor
import okhttp3.ConnectionPool
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import okio.BufferedSource
import okio.buffer
import okio.source
import java.io.EOFException
import java.io.IOException
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * 播放器工厂 —— 手机端与电视端各自一套参数。
 *
 * 「播一下就转圈」的真因是起播门槛太低（旧 1s 缓冲即开播，下载速率略低于码率时余量秒穿）。
 * 关键档位：起播 2s（5s 在夸克限速时要等超久，实测后回调）、rebuffer 后 3s、
 * minBuffer 50s/60s（网盘按连接累计限速，一次限速窗口实测能到 66 秒，存粮薄必穿）。
 *
 * 磁盘缓存只对 VideoCache.bind 登记过的网盘直链生效（判据见 VideoCacheDataSource）；
 * Emby 直链、HLS/DASH、容量档关闭都是裸 OkHttpDataSource，取数路径逐字节一致。
 * 缓存层异常只让缓存失效，不拖垮正在播的一集；失效状态码（404/403/412）在 HTTP 层
 * 统一转成中文提示 IOException（分享链接过期/Cookie 失效），不再无限转圈。
 */
@UnstableApi
object PlayerFactory {

    /** 起播门槛：攒够 2 秒再出画面（5 秒太激进，夸克 CDN 限速时要等超久） */
    private const val BUFFER_FOR_PLAYBACK_MS = 2_000

    /** 卡顿后恢复门槛：3 秒，兼顾恢复速度与稳定性 */
    private const val BUFFER_AFTER_REBUFFER_MS = 3_000

    /** 开区间 Range 头（bytes=X-）：只有这种请求才需要补一个结尾，防止一路读到 EOF */
    private val OPEN_RANGE_REGEX = Regex("bytes=(\\d+)-$")

    /** 加载失败的重试次数（与之前 DefaultLoadErrorHandlingPolicy(8) 保持一致） */
    private const val MIN_LOAD_RETRY_COUNT = 8

    /** 手机端：屏幕小、常切前后台，缓冲略收一点以保证内存占用可控 */
    fun createPhonePlayer(
        context: Context,
        /** Emby 反向代理 / 严格鉴权时需要额外 header（如 X-Emby-Authorization） */
        headers: Map<String, String> = emptyMap()
    ): ExoPlayer = build(context, isTv = false, headers = headers)

    /** 电视端：码率更高、网络更稳，缓冲池更大、超时更宽松 */
    fun createTvPlayer(
        context: Context,
        headers: Map<String, String> = emptyMap()
    ): ExoPlayer = build(context, isTv = true, headers = headers)

    private fun build(
        context: Context,
        isTv: Boolean,
        headers: Map<String, String> = emptyMap()
    ): ExoPlayer {
        // OkHttp 底层 —— DefaultHttpDataSource (HttpURLConnection) 在 MIUI/Xiaomi 上会静默丢弃
        // Cookie 等自定义 header，导致夸克/阿里云盘 CDN 返回 412 或空响应，播放器无限卡 BUFFERING。
        // OkHttpDataSource 直接透传所有 header，行为与 OkHttp 调用完全一致。
        val connectTimeout = if (isTv) 15_000 else 10_000
        val readTimeout = if (isTv) 60_000 else 45_000

        // Range 请求改写逻辑：
        // ExoPlayer 的进度式播放（MP4/MKV/AVI...）在 seek 后会发开区间 Range（"bytes=X-"），
        // 如果原样透传，CDN 会一直返回到文件 EOF（动辄几个 GB），OkHttp 连接被长时间占用，
        // 后续 seek 抢不到连接就卡住。所以只对这种「开区间 Range」限制结尾。
        //
        // 关键：请求里本来就没有 Range 头时绝对不能再加！
        // HLS/DASH 的媒体分片（.ts / .m4s）请求就是不带 Range 的，强行改写成
        // "bytes=0-32767" 会把几 MB 的分片截成 32KB，分片数据不完整 → 解析失败 / 丢帧，
        // 表现为播放器每 0.2~0.3 秒就「缓冲→就绪→播放→暂停」循环、进度条一顿一顿往前跳。
        //
        // 块大小 32MB：每块结束都要重开一次请求（Media3 会报一次人为 EOF），块太小会让
        // 这部分开销占比过高（8MB 时实测每 4 秒就空一下）。32MB 在 2MB/s 下约 16 秒一块。
        val chunkSize = 32L * 1024 * 1024
        // 每个播放器一份「防和谐」归一化器：记录哪些直链带伪装前缀、真实容器是什么。
        // 按完整 URL 记录：同一次播放里的签名参数不变，映射生命周期刚好覆盖一次播放。
        val normalizer = PlaybackNormalizer()
        // 「整文件 200」被限速、改走 Range 通道的网盘直链。
        // 这类 CDN 按「连接」累计限速：同一条 keep-alive 连接越读越慢
        // （实测 32MB 一块从 12.5 秒劣化到 21 秒、32 秒，最后彻底停摆，
        //  字节还在 trickle，所以 60 秒 socket 读超时永远不触发，只有用户手动 seek 才恢复）。
        // 给这些 URL 的每个分块请求加 Connection: close，逼 OkHttp 每块重新建连，
        // 等于把「用户手动 seek」自动化。
        // 边界：只覆盖被切过通道的网盘大文件；Emby 直链、HLS/DASH 分片不进这个集合，
        //       行为完全不变。HTTP/2 下 OkHttp 会丢弃 connection 头（无副作用），
        //       夸克这类下载 CDN 走 HTTP/1.1，正好生效。
        val rotateConnection = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
        fun freshConnectionPerChunk(request: Request, urlKey: String): Request =
            if (rotateConnection.contains(urlKey))
                request.newBuilder().header("Connection", "close").build()
            else request

        // ===== 失效状态码的统一出口 =====
        // Media3 对被拒的响应只给一个 ERROR_CODE_IO_BAD_HTTP_STATUS 错误码，用户看不懂；
        // 在 OkHttp 层换成带中文提示的 IOException，ExoPlayer 会把它放进 PlaybackException.message，
        // onPlayerError 再翻译一次就能显示「Cookie 可能已过期」。
        //
        // 三个响应出口都必须过这道检查：原样透传、伪装平移分支、改走 Range 通道的重发分支。
        // 前两个分支以前是直接 return 的，于是 403 的错误页被当成视频数据喂进 extractor，
        // 用户看到的是无限转圈，日志里只剩 BUFFERING，根本看不出真正原因是令牌过期。
        fun failOnDeadStatus(request: Request, response: okhttp3.Response): okhttp3.Response {
            val code = response.code
            if (code != 404 && code != 403 && code != 412) return response
            val host = request.url.host
            if (code == 404) {
                Log.e("PlayerHttp", "404 Not Found — 资源已失效 ${LogGate.url(context, request.url.toString())}")
                response.close()
                throw java.io.IOException(
                    "HTTP 404: 该资源已失效（$host），可能是云盘分享链接过期或内容被移除。"
                )
            }
            // 403/412 = Cookie 失效或签名过期（云盘常见）
            Log.e("PlayerHttp", "$code ${response.message} — 认证失效 host=$host")
            response.close()
            throw java.io.IOException(
                "HTTP $code: 访问被拒绝（$host），云盘 Cookie 或登录状态可能已过期。"
            )
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(connectTimeout.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(readTimeout.toLong(), TimeUnit.MILLISECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            // 显式连接池：保持 5 个空闲连接 5 分钟，Range 请求可复用 keep-alive 连接
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            .addInterceptor(Interceptor { chain ->
                val original = chain.request()
                val urlKey = original.url.toString()

                // 走磁盘缓存的直链：Range 已经是 [StreamSpaceDataSource] 按流坐标算好的
                // 网盘文件区间，这里再平移一次就自己给自己挖洞，所以只做两件事 ——
                // 每块换新连接、失效状态码转中文。
                if (VideoCache.keyFor(urlKey) != null) {
                    val cached = original.newBuilder().header("Connection", "close").build()
                    return@Interceptor failOnDeadStatus(cached, chain.proceed(cached))
                }

                // 已知带伪装头：请求坐标整体右移前缀字节数
                normalizer.planOf(urlKey)?.takeIf { it.shifted }?.let { plan ->
                    val shifted = freshConnectionPerChunk(
                        normalizer.shiftRange(original, plan.prefixBytes, chunkSize),
                        urlKey
                    )
                    val shiftedResponse = chain.proceed(shifted)
                    return@Interceptor failOnDeadStatus(shifted, shiftedResponse)
                }

                val rangeHeader = original.header("Range")
                val openEnded = rangeHeader != null && OPEN_RANGE_REGEX.containsMatchIn(rangeHeader)
                val request = if (openEnded) {
                    // 有 start 无 end（"bytes=X-"）→ 32MB 固定块，避免读到 EOF 拖住连接
                    val start = OPEN_RANGE_REGEX.find(rangeHeader!!)!!.groupValues[1].toLong()
                    freshConnectionPerChunk(
                        original.newBuilder().header("Range", "bytes=$start-${start + chunkSize - 1}").build(),
                        urlKey
                    )
                } else {
                    // 无 Range（HLS/DASH 分片、清单）或已带完整 end → 原样透传
                    original
                }
                var response = chain.proceed(request)
                val code = response.code
                val cl = response.body?.contentLength() ?: -1L

                // ===== 伪装头识别 + 整文件限速规避 =====
                // 两件事共用同一次头部探测：
                //   1. 网盘伪装：视频前面塞一张图片（8 字节魔术字或整张 PNG/JPEG），
                //      必须找到真实容器起点后才能解析；
                //   2. 无 Range 的整文件 200：CDN 会把它限速在 ~100KB/s（实测夸克 99KB/s），
                //      4K 片源永远填不满缓冲，表现为「一直缓冲中、速度像 0」。改走 Range 通道即满速。
                // 伪装长度不固定，所以按窗口逐级扫描真实容器头，命中即停。
                val fromStart = rangeHeader == null || rangeHeader.startsWith("bytes=0-")
                val bigBody = cl <= 0 || cl > 8L * 1024 * 1024
                if (fromStart && bigBody) {
                    val head = runCatching { response.peekBody(16).bytes() }.getOrNull()
                    var plan: PlaybackNormalizer.Plan? = null
                    if (head != null && normalizer.looksLikeImage(head)) {
                        for (limit in normalizer.scanWindowLimits) {
                            val window = runCatching { response.peekBody(limit).bytes() }
                                .getOrNull() ?: break
                            plan = normalizer.detect(window, allowPrefix = true)
                            // 命中真实容器头，或已经读到流末尾（伪装图之外没有容器头）
                            if (plan != null || window.size < limit) break
                        }
                        plan?.let { normalizer.remember(urlKey, it) }
                    } else if (head != null) {
                        // 正常文件头：只认第 0 字节，避免把 MP4 的 free/wide 前置 box 当成伪装前缀
                        plan = normalizer.detect(head, allowPrefix = false)
                    }

                    // 无 Range 的整文件 200 → 改用 Range 通道；伪装头顺带把坐标平移到真实容器起点。
                    // 只对已知 >8MB 的响应生效，HLS 分片/小文件完全不受影响。
                    // 重发带的区间要按容器分：
                    //   开区间（bytes=X-）虽然能通过 Content-Range 报出真实总长，但实测夸克对这种
                    //   响应同样限速 ~99KB/s，4K MKV 光首包就要转十几秒；
                    //   MKV 的头部自描述、不依赖总长，所以首包也直接切带结尾的块，走满速通道。
                    //   MP4 的 moov 常在尾部，必须靠开区间拿到真实总长才能算进度条和 seek，
                    //   容器未识别时也保守走开区间。
                    val needRetry = rangeHeader == null && code == 200 && cl > 8L * 1024 * 1024
                    if (needRetry) {
                        val observedProtocol = response.protocol
                        response.close()
                        val prefix = plan?.prefixBytes ?: 0L
                        val retryChunk =
                            if (plan?.container == PlaybackNormalizer.Container.MATROSKA) chunkSize else null
                        // 这条直链之后被 CDN 按连接累计限速，每个分块都换新连接
                        rotateConnection.add(urlKey)
                        val retry = freshConnectionPerChunk(
                            normalizer.shiftRange(original, prefix, retryChunk),
                            urlKey
                        )
                        // 只留这一条决策日志：切换通道是一次性的，分片级流水账已删
                        // 协议是能不能真的换连接的证据：http/1.1 才认 Connection: close，
                        // h2 下 OkHttp 会直接丢掉这个头（此时日志里会显示 h2，说明得换别的招）
                        Log.w(
                            "PlayerHttp",
                            "整文件 200（${cl / 1024 / 1024}MB，${plan?.evidence ?: "容器未识别"}）" +
                                "改走 Range 通道：${retry.header("Range")}，" +
                                "分块换连接=$observedProtocol"
                        )
                        val retryResponse = chain.proceed(retry)
                        return@Interceptor failOnDeadStatus(retry, retryResponse)
                    }
                }
                // ===== 404 / 403 / 412 出口 =====
                // 透传分支的响应在这里过统一检查；另外两个分支在各自 return 前已经查过。
                failOnDeadStatus(request, response)

                response
            })
            // 这里计的是 App 读走的 HTTP 字节；源代理内部的外网并发并不在此计量。
            .addInterceptor(Interceptor { chain ->
                val response = chain.proceed(chain.request())
                val body = response.body ?: return@Interceptor response
                val counted = object : java.io.FilterInputStream(body.byteStream()) {
                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                        val n = super.read(buffer, offset, length)
                        if (n > 0) DownloadMeter.add(n.toLong())
                        return n
                    }
                }
                response.newBuilder()
                    .body(
                        object : ResponseBody() {
                            private val countedSource = counted.source().buffer()
                            override fun contentType() = body.contentType()
                            override fun contentLength() = body.contentLength()
                            override fun source(): BufferedSource = countedSource
                            override fun close() = countedSource.close()
                        }
                    )
                    .build()
            })
            .build()

        val httpFactory = OkHttpDataSource.Factory(client)
        if (headers.isNotEmpty()) {
            httpFactory.setDefaultRequestProperties(headers)
        }

        // ===== 磁盘缓存 =====
        // 伪装前缀的平移从拦截器上移到这里的数据源层（[StreamSpaceDataSource]），于是
        // Media3 的区间记账和播放器的 seek 只剩「流坐标」一套坐标系 —— 以前两套坐标系叠在
        // 一起必抛 READ_POSITION_OUT_OF_RANGE，那正是这个仓库禁用缓存的原因。
        // 走不走缓存只看一条判据：这条 URL 有没有被登记过。Emby 直链、m3u8 分片、
        // 容量档关闭时都查不到 key，这里给出的仍然是裸 OkHttpDataSource，取数路径一字节不变。
        val priorityTaskManager = PriorityTaskManager()
        val pipeline = CloudCachePipeline(client, headers, normalizer, httpFactory, chunkSize, priorityTaskManager)
        val playerBusy = AtomicBoolean(true)
        val playingUrl = AtomicReference<String?>()
        val filler = VideoCacheFiller(pipeline, chunkSize, priorityTaskManager) { playerBusy.get() }
        val dataSourceFactory = VideoCacheDataSource.Factory(
            pipeline,
            httpFactory,
            onStreamOpen = { url, streamPos ->
                playingUrl.set(url)
                filler.onPlayerOpen(url, streamPos)
            },
            onStreamRead = { url, streamPos -> filler.onPlayerRead(url, streamPos) },
        )

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                // minBufferMs 是「常态下始终保持 ahead 这么多秒」的存粮，不是起播门槛（起播见下面两项）。
                // 抬到 50s/60s 的依据是实测的限速窗口：32MiB 一块的完成间隔会 12.5s→21s→32s 一路劣化，
                // 最坏一次 66 秒几乎无字节；旧值 30s/45s 正好穿不过这种窗口，表现就是「看着看着要加载」。
                // 电视给 60s 而不是 50s：4K 码率更高、盒子内存吃得住。
                // 优先满足最小缓冲时长；达到最小时长后仍可能受字节阈值限制。
                if (isTv) 60_000 else 50_000,
                if (isTv) 300_000 else 240_000,
                BUFFER_FOR_PLAYBACK_MS,
                BUFFER_AFTER_REBUFFER_MS
            )
            .setTargetBufferBytes(if (isTv) 384 * 1024 * 1024 else 256 * 1024 * 1024)
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(60_000, true)
            .build()

        val errorPolicy = ChunkEofRetryPolicy()
        val mediaSourceFactory =
            DefaultMediaSourceFactory(dataSourceFactory, ContainerExtractorsFactory(normalizer))
                .setLoadErrorHandlingPolicy(errorPolicy)

        // 自定义 RenderersFactory：过滤掉 ImageRenderer。
        // 原因：Range 拦截器会改写 "bytes=X-" 开区间请求，MKV/MP4 内嵌的 poster 图片
        // attachment 轨道数据可能不完整，ImageRenderer 拿到残缺数据 → BitmapFactory 解码失败
        // → ParserException → 播放器崩掉。作为视频播放器我们不需要渲染内嵌图片，直接移除。
        val defaultFactory = DefaultRenderersFactory(context)
        val renderersFactory = RenderersFactory { handler, videoRendererEventListener, audioRendererEventListener, textRendererOutput, metadataRendererOutput ->
            defaultFactory.createRenderers(
                handler,
                videoRendererEventListener,
                audioRendererEventListener,
                textRendererOutput,
                metadataRendererOutput
            ).filter { it !is ImageRenderer }.toTypedArray()
        }

        // 电视端 tunnel 直通：让硬解绕过 SurfaceFlinger/GPU 合成，画面直送显示硬件。
        // 这台 MT9653 显示合成锁 SDR，HDR10 走 GPU 既被压灰又慢到丢 85% 帧；tunnel 一并治。
        // 默认 1x 开；倍速 != 1 时由 TvPlayerActivity 调 [setTunnelingEnabled] 关掉
        // （tunnel 下硬件按原生速率渲染，变速会失效）。codec 不支持 tunnel 时选轨器自动回退普通路径。
        val trackSelector = if (isTv) {
            DefaultTrackSelector(context).apply {
                setParameters(buildUponParameters().setTunnelingEnabled(true))
            }
        } else null

        val playerBuilder = ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .setWakeMode(C.WAKE_MODE_NETWORK)
        if (trackSelector != null) playerBuilder.setTrackSelector(trackSelector)
        val player = playerBuilder.build()
        player.setPriorityTaskManager(priorityTaskManager)

        // 一个播放器实例一份预读会话；释放必须走 [release]，否则缓存引用计数减不下来，
        // 设置页清空时只能退化成逐条删资源（SQLite 与目录都留着）。
        // 计数只在真开了容量档时加：关档也计的话，设置页会误判「有播放器在用」而把新档位推后生效。
        val useRef = VideoCache.enabled
        if (useRef) VideoCache.beginUse()
        player.addListener(object : Player.Listener {
            private fun updatePreloadGate() {
                playerBusy.set(player.isLoading || player.playbackState != Player.STATE_READY)
                filler.onPlayerWorkChanged()
            }

            override fun onIsLoadingChanged(isLoading: Boolean) {
                updatePreloadGate()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val url = mediaItem?.localConfiguration?.uri?.toString()
                playingUrl.set(url)
                filler.onMediaItemChanged(url)
            }

            override fun onPlaybackStateChanged(state: Int) {
                updatePreloadGate()
                when (state) {
                    Player.STATE_IDLE -> filler.stop()
                    Player.STATE_ENDED -> playingUrl.get()?.let { filler.onItemEnded(it) }
                }
            }
        })
        sessions[player] = CacheSession(filler, useRef, trackSelector)
        return player
    }

    /** 播放器 → 它的预读会话。用 WeakHashMap：万一哪个界面漏调 [release]，不至于一直拽住 */
    private val sessions = Collections.synchronizedMap(WeakHashMap<ExoPlayer, CacheSession>())

    private class CacheSession(
        val filler: VideoCacheFiller,
        val useRef: Boolean,
        val trackSelector: DefaultTrackSelector?,
    )

    /**
     * 电视端倍速联动 tunnel：1x 开直通（治 HDR10 偏灰+掉帧），非 1x 关（tunnel 下变速失效）。
     * 手机端没有 trackSelector（null），直接 no-op。codec 不支持 tunnel 时选轨器自动回退，
     * 所以这里只管「想不想开」，能不能开交给 Media3 判定。
     */
    fun setTunnelingEnabled(player: ExoPlayer?, enabled: Boolean) {
        if (player == null) return
        val ts = sessions[player]?.trackSelector ?: return
        if (ts.parameters.tunnelingEnabled == enabled) return
        ts.setParameters(ts.buildUponParameters().setTunnelingEnabled(enabled))
    }

    /**
     * 释放播放器：先停预读线程、放掉缓存引用计数，再交给 ExoPlayer 自己收尾。
     * Activity 请调这里，不要再直接 `player.release()`（那样预读线程还在跑，缓存引用也减不下来）。
     * 传 null 什么都不做，与调用点原来的 `player?.release()` 语义一致。
     */
    fun release(player: ExoPlayer?) {
        if (player == null) return
        val session = sessions.remove(player)
        session?.filler?.cancel()
        if (session?.useRef == true) VideoCache.endUse()
        player.release()
    }

    /**
     * 分块 Range 读到块尾时，ExtractorInput 会抛一记 EOFException —— 那不是网络故障，
     * 只是「这一块读完了」；但默认策略会退避 1 秒再续下一块（实测 8MB 块 + 1 秒空窗，
     * 吞吐直接少 20%）。所以 EOF 立即续读，真正的 IO 错误仍走默认退避，
     * 避免网络真的断掉时疯狂重试打爆服务器。
     */
    private class ChunkEofRetryPolicy :
        DefaultLoadErrorHandlingPolicy(MIN_LOAD_RETRY_COUNT) {

        override fun getRetryDelayMsFor(
            loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo
        ): Long {
            if (loadErrorInfo.exception is EOFException) return 0L
            return super.getRetryDelayMsFor(loadErrorInfo)
        }
    }

    /**
     * 容器识别兜底。
     *
     * 夸克 CDN 对同一部剧的 MKV 会给出两种 Content-Type：video/x-matroska 或
     * application/octet-stream。前者 Media3 只产出一个 MatroskaExtractor，直接采用、
     * 不做嗅探；后者 Media3 认不出容器，会退回「十几个 extractor 逐个嗅探」，实测
     * 读到 32KB 就全部失败 → 播放器转圈后直接 ENDED，没画面也没错误提示。
     *
     * 所以 MIME 不可判定容器时，按「探测到的容器 > 文件名后缀」排序候选，交给 Media3
     * sniff 最终定夺。文件名只能当提示、不能当事实：实测 E06.mkv 的真实容器是 MP4。
     * Emby 等 MIME 可靠的来源完全走原路径，不受影响。
     */
    private class ContainerExtractorsFactory(
        private val normalizer: PlaybackNormalizer
    ) : ExtractorsFactory {

        private val fallback = DefaultExtractorsFactory()

        override fun createExtractors(): Array<Extractor> = fallback.createExtractors()

        override fun createExtractors(
            uri: Uri,
            responseHeaders: Map<String, List<String>>
        ): Array<Extractor> {
            val contentType = contentTypeOf(responseHeaders)
            val mimeType = FileTypes.inferFileTypeFromMimeType(contentType)
            // extractor 在 MediaPeriod 构造时就创建，此时首包可能还没探测完，
            // 所以探测结果只是「锦上添花」，缺失时退回 MIME / 文件名。
            val detected = normalizer.planOf(uri.toString())?.container
                ?.takeIf { it != PlaybackNormalizer.Container.UNKNOWN }
            if (mimeType != FileTypes.UNKNOWN &&
                (detected == null || detected.fileType == mimeType)
            ) {
                return fallback.createExtractors(uri, responseHeaders)
            }
            // 夸克直链的路径里只有 hash，真实文件名在 filename 查询参数上
            val filename = uri.getQueryParameter("filename")
            val named = if (!filename.isNullOrBlank()) {
                Uri.Builder().scheme("file").appendPath(filename).build()
            } else {
                uri
            }
            val type = detected?.fileType ?: when (mimeType) {
                FileTypes.UNKNOWN -> FileTypes.inferFileTypeFromUri(named)
                else -> mimeType
            }
            // extractor 是有状态的：init() 不重置、release() 是空实现，同一实例绝不能
            // 跨剧集复用，否则新周期的 endTracks() 永远发不出来 → 播放器无限转圈。
            // 所以每次都向默认工厂要一批全新实例。
            //
            // 名字匹配的排在前面、其余保留，让 Media3 用 sniff 决定真正匹配的那个
            // —— 与 MIME 能判定时默认工厂返回「首选 + 全部」的行为一致。
            val all = fallback.createExtractors(named, emptyMap())
            val (preferred, rest) = all.partition { matches(type, it) }
            Log.w(
                "PlayerFactory",
                "MIME=${contentType ?: "无"} 探测容器=${detected?.label ?: "无"} " +
                    "filename=${filename ?: "无"} " +
                    "按${if (detected != null) "探测" else "名"}优先=" +
                    preferred.joinToString { it.javaClass.simpleName }.ifEmpty { "无" } +
                    " 共${all.size}个候选（嗅探定夺）"
            )
            return (preferred + rest).toTypedArray()
        }

        private fun contentTypeOf(headers: Map<String, List<String>>): String? =
            headers.entries
                .firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
                ?.value?.firstOrNull()

        private fun matches(type: Int, extractor: Extractor): Boolean = when (type) {
            FileTypes.MATROSKA -> extractor is MatroskaExtractor
            FileTypes.MP4 -> extractor is Mp4Extractor || extractor is FragmentedMp4Extractor
            FileTypes.TS -> extractor is TsExtractor
            FileTypes.PS -> extractor is PsExtractor
            else -> false
        }
    }

    /** 字节数格式化，展示友好 */
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 KB"
        val kb = 1024.0
        val mb = kb * 1024
        val gb = mb * 1024
        return when {
            bytes >= gb -> "%.1f GB".format(bytes / gb)
            bytes >= mb -> "%.1f MB".format(bytes / mb)
            else -> "%.0f KB".format(bytes / kb)
        }
    }
}
