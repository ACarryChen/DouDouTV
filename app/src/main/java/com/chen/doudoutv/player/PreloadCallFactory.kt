package com.chen.doudoutv.player

import okhttp3.Call
import okhttp3.Request

internal class PreloadCallFactory(private val delegate: Call.Factory) : Call.Factory {
    private val lock = Any()
    private var activeCall: Call? = null

    @Volatile
    var isCancelled = false
        private set

    override fun newCall(request: Request): Call {
        val call = delegate.newCall(request.newBuilder().tag(PreloadCallFactory::class.java, this).build())
        val cancelNow = synchronized(lock) {
            // 一次预读块内的 HTTP 分段串行打开，上一段已由数据源关闭。
            activeCall = call
            isCancelled
        }
        if (cancelNow) call.cancel()
        return call
    }

    fun cancel() {
        val call = synchronized(lock) {
            isCancelled = true
            activeCall
        }
        call?.cancel()
    }
}
