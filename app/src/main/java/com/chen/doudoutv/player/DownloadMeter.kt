package com.chen.doudoutv.player

import java.util.concurrent.atomic.AtomicLong

/**
 * 全局 HTTP 响应读取计数器（值单调递增，读取方自己取差分）；本地代理响应不计。
 * 自己数而不用 Media3 DefaultBandwidthMeter：数据源链是手工搭的（OkHttpDataSource →
 * VideoCacheDataSource → ...），setBandwidthMeter 那条自动挂 TransferListener 的路径只有
 * MediaSourceFactory 默认构造才生效，挂不上则估计值永不回调、屏幕网速恒 0。
 * 计数点在 OkHttp 最后一个应用拦截器（见 [PlayerFactory]）：缓存命中不发起请求天然不进账；
 * 预读器与播放器共用 client 两路都算（同一次真下载）；没被读走的响应体一个字节不计。
 */
object DownloadMeter {
    private val bytes = AtomicLong(0L)

    /** 进程启动以来累计的网络下载字节 */
    val totalBytes: Long
        get() = bytes.get()

    fun add(count: Long) {
        if (count > 0L) bytes.addAndGet(count)
    }
}
