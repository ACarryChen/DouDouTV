package com.chen.doudoutv

import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.chen.doudoutv.core.Prefs
import com.chen.doudoutv.core.VideoCache
import com.chen.doudoutv.data.db.AppDatabase
import com.chen.doudoutv.data.image.PicHeaderInterceptor
import com.chen.doudoutv.data.scrape.ScrapeStrategy
import com.chen.doudoutv.data.tmdb.TmdbIpStore
import com.chen.doudoutv.ui.theme.isTelevision
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DouDouTvApp : com.fongmi.android.tv.App(), ImageLoaderFactory {

    val db: AppDatabase by lazy { AppDatabase.get(this) }

    override fun onCreate() {
        super.onCreate()
        // super.onCreate：Notify 建通知渠道 + 注册 Activity 生命周期回调（App.activity() 追踪）。
        // 继承 FongMi App 是初始化其运行时的最短路径：instance / 主线程 Handler / catvod Init
        // 都在它的构造与 attachBaseContext 里就位，PlayerManager 全家桶直接可用。
        instance = this
        Prefs.init(this)
        // 端口顺序是承重链（顺序反了 Nano 抢走 9978，壳代理请求全打到它拿 500）：
        // ① 先占 8964+9978（壳探测起点+兜底端口）；
        // ② 再启动 FongMi Nano（9978 被占自动退 9979），plain jar 爬虫地址由它处理；
        // ③ 通告 catvod 静态端口：Proxy.port 进程级共享（壳 dex 经 parent-first 解析到同一个类），
        //    爬虫拼 http://127.0.0.1:<port>/proxy?do=xx 时端口必须已就位，否则恒 -1 → Malformed URL。
        com.chen.doudoutv.data.tvbox.LocalProxyServer.start()
        com.fongmi.android.tv.server.Server.get().start()
        com.github.catvod.Proxy.set(com.chen.doudoutv.data.tvbox.LocalProxyServer.proxyPort)
        // 网盘直链的磁盘缓存只记一个 applicationContext，真正的 SimpleCache 到首次取数才建
        VideoCache.init(this)
        // fongmi 档的网速计量：舱内数据源把真实网络字节回调到全局 DownloadMeter（classic 走自己的拦截器，两档同口径）
        com.fongmi.android.tv.player.exo.ExoMediaSourceFactory.networkTransferListener =
            com.chen.doudoutv.player.FongMiNetworkMeter

        // 记录未捕获异常：MIUI 有时会吞掉 crash 缓冲区日志，
        // 这里额外写一份到应用私有目录，方便排查"闪退但没有崩溃日志"的问题。
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                Log.e("DouDouTvCrash", "uncaught on ${thread.name}", error)
                val file = java.io.File(filesDir, "crash_last.txt")
                file.writeText(
                    "time=${System.currentTimeMillis()}\nthread=${thread.name}\n" +
                        Log.getStackTraceString(error)
                )
            }
            previous?.uncaughtException(thread, error)
        }

        // 启动时判定刮削方案：总开关开启且令牌非空才走 TMDB，否则沿用原有方案。
        // 这里只做本地判断，不发网络请求，所以不会拖慢冷启动。
        val engine = ScrapeStrategy.refresh()
        Log.i(
            "DouDouTv",
            "刮削方案：${engine.name} / ${ScrapeStrategy.describe()}" +
                "（TMDB 开关${if (Prefs.tmdbEnabled) "开" else "关"}，" +
                "令牌${if (Prefs.tmdbConfigured) "已填" else "未填"}）"
        )

        // TMDB 海外 IP 池：GFW 污染了 DNS，只能直连 AWS Cloudfront 真实 IP，
        // 而 IP 池每周滚动。后台从 CheckTMDB 仓库拉最新 IP 覆盖旧值，
        // 连不通的 IP 由 TmdbClient 的 EventListener 自动冷却剔除。
        // 后台线程执行，不阻塞冷启动；拉取失败也不影响任何功能。
        TmdbIpStore.init(this)
        appScope.launch { TmdbIpStore.refresh() }
    }

    /**
     * Coil 全局 ImageLoader：开启淡入，
     * 让「先出占位、再到清晰图」的切换更平滑，不闪白也不闪黑。
     *
     * [PicHeaderInterceptor] 负责拆 TVBox 海报直链末尾的 @Referer= / @User-Agent= 后缀，
     * 否则豆瓣图床会把整串当路径并返回 418（表现为大量卡片没有封面）。
     *
     * 图片加载一律走系统默认网络栈，不注入 [TmdbIpStore.tmdbDns]：注入版让
     * 缩略图也经过 TMDB IP 池，源站缩略图反而加载不出来（已撤回）。
     * TMDB 域名的 DNS 污染只在刮削客户端（TmdbClient）侧解决。
     */
    override fun newImageLoader(): ImageLoader {
        // TV 端低端 GPU 大面积 alpha 混合吃不消：一屏海报同时做 220ms 淡入，
        // 每帧都要重画整屏卡片，实测掉帧里「Slow issue draw commands」占一半。
        // TV 直接出图，手机端保留淡入过渡。
        val builder = ImageLoader.Builder(this).crossfade(!isTelevision())
        return builder
            .components { add(PicHeaderInterceptor()) }
            .build()
    }

    companion object {
        lateinit var instance: DouDouTvApp
            private set

        /** 应用级后台任务作用域：IP 池刷新这类不阻塞 UI 的活儿都挂这里。 */
        private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
