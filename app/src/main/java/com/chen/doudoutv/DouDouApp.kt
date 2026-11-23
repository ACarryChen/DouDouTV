package com.chen.doudoutv

import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.chen.doudoutv.core.Prefs
import com.chen.doudoutv.core.VideoCache
import com.chen.doudoutv.data.db.AppDatabase
import com.chen.doudoutv.data.image.PicHeaderInterceptor
import com.chen.doudoutv.data.scrape.ScrapeStrategy
import com.chen.doudoutv.ui.theme.isTelevision

class DouDouTvApp : com.fongmi.android.tv.App(), ImageLoaderFactory {

    val db: AppDatabase by lazy { AppDatabase.get(this) }

    override fun onCreate() {
        super.onCreate()
        // super.onCreate：Notify 建通知渠道 + 注册 Activity 生命周期回调（App.activity() 追踪）。
        // 继承 FongMi App 是初始化其运行时的最短路径：instance / 主线程 Handler / catvod Init
        // 都在它的构造与 attachBaseContext 里就位，PlayerManager 全家桶直接可用。
        instance = this
        Prefs.init(this)
        // FongMi 本地在配置解析时启动 Nano 代理（9978 起）。爬虫返回的播放地址里
        // 会带自己的代理转发（如韩小圈 http://127.0.0.1:<port>/proxy?do=hxq&...），
        // 端口取 Proxy.getPort()；Server 不启动时恒为 -1，喂给播放器就是 Malformed URL。
        com.fongmi.android.tv.server.Server.get().start()
        // 网盘直链的磁盘缓存只记一个 applicationContext，真正的 SimpleCache 到首次取数才建
        VideoCache.init(this)

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
    }

    /**
     * Coil 全局 ImageLoader：开启淡入，
     * 让「先出占位、再到清晰图」的切换更平滑，不闪白也不闪黑。
     *
     * [PicHeaderInterceptor] 负责拆 TVBox 海报直链末尾的 @Referer= / @User-Agent= 后缀，
     * 否则豆瓣图床会把整串当路径并返回 418（表现为大量卡片没有封面）。
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
    }
}
