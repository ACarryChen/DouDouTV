package com.chen.doudoutv.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/**
 * fongmi 档的字节计量钩子：挂进 ExoMediaSourceFactory 的上游数据源（CacheDataSource 外侧），
 * 只有真实网络读取进账，缓存命中不进账——与 classic 档的 OkHttp 拦截器计数同口径，
 * TvPlayer/PhonePlayer 的网速差分读同一个 [DownloadMeter]。
 *
 * :fongmi 模块不能反向依赖 :app，所以计量回调由舱暴露静态注入点（networkTransferListener），
 * 这里在 App 启动时把自己挂上去。
 */
@OptIn(UnstableApi::class)
object FongMiNetworkMeter : TransferListener {
    override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
    }

    override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
    }

    override fun onBytesTransferred(
        source: DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytesTransferred: Int
    ) {
        DownloadMeter.add(bytesTransferred.toLong())
    }

    override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
    }
}
