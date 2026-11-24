package com.chen.doudoutv.core

import android.content.Context
import coil.Coil
import coil.annotation.ExperimentalCoilApi
import java.io.File

/**
 * 应用里真正落盘、能被清掉的缓存（图片缓存口径：磁盘实测唯一稳定长大的是 Coil 封面缓存）。
 * 视频缓存不并入这里（见 [VideoCache]）：两者的清除路径不能共用——视频缓存必须整体
 * release 再删目录，图片缓存必须走 Coil 自己的 clear。这里只清 `media_cache`
 * （老版本 SimpleCache 的目录名，存量机器可能有残留字节）。
 *
 * 不参与清除的部分（删了是坏功能）：
 * - `cacheDir/spiders`、`spider_opt`、`danMugo_*.so`：壳与解出的 so，删了要重下载重跑 Init（实测约 24s）；
 * - `files/AiWex`、`Pizazz`、`ali_*`、`quark_ut`：网盘授权与登录态；
 * - `databases`、`shared_prefs`：进度、收藏与设置。
 */
object AppCache {

    /** 老版本 SimpleCache 用的目录名，现在只在个别存量机器上可能还有残留 */
    private const val LEGACY_DIR = "media_cache"

    private fun legacyDir(context: Context) = File(context.cacheDir, LEGACY_DIR)

    private fun dirSize(dir: File): Long {
        if (!dir.isDirectory) return 0L
        return runCatching {
            dir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
        }.getOrDefault(0L)
    }

    /** 可清除缓存的当前体积：图片磁盘缓存 + 老版本残留的播放缓存目录 */
    @OptIn(ExperimentalCoilApi::class)
    fun sizeBytes(context: Context): Long {
        val appContext = context.applicationContext
        val imageCache = runCatching { Coil.imageLoader(appContext).diskCache?.size }.getOrNull() ?: 0L
        return imageCache + dirSize(legacyDir(appContext))
    }

    /**
     * 清除可清除的部分。
     *
     * 走 Coil 自己的 `diskCache.clear()` 而不是删目录：磁盘缓存带一个 journal 库，
     * 直接 rm 目录会让正在跑的 ImageLoader 持有失效句柄。
     * Coil 也明确要求 `clear()` 不能在 UI 线程调，调用方要自己包 IO。
     *
     * @return 实际释放的字节数
     */
    @OptIn(ExperimentalCoilApi::class)
    fun clear(context: Context): Long {
        val appContext = context.applicationContext
        val before = sizeBytes(appContext)
        runCatching { Coil.imageLoader(appContext).diskCache?.clear() }
        runCatching { legacyDir(appContext).deleteRecursively() }
        return (before - sizeBytes(appContext)).coerceAtLeast(0L)
    }
}
