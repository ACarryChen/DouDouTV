package com.github.catvod.crawler

import android.content.Context
import okhttp3.Dns
import java.util.HashMap

/**
 * TVBox 爬虫基类（宿主 App 提供）。
 *
 * 饭太硬等加密壳 spider 包的 Guard 壳类（如 com.github.catvod.spider.DouDouGuard）
 * 继承本类。壳 dex 里没有这个类，必须由宿主 App 的 classloader 提供，
 * 否则加载 Guard 时父类解析失败 → ClassNotFoundException。
 *
 * 方法签名与 CatVod 标准 Spider 一致，Guard 壳会 override 这些方法
 * 并委托给 native 解密出的真实 spider 实例。
 */
abstract class Spider {

    companion object {
        /**
         * 新版 FongMi 接口的 spider jar（如 lmw.jar 的 Wogg）会调用宿主 Spider 的
         * 这个静态方法拿「安全 DNS」：优先解析 IPv4，没有 IPv4 时退回全部结果。
         * 宿主不提供的话，jar 直接抛 NoSuchMethodError、init 失败，整条线路不可用。
         */
        @JvmStatic
        fun safeDns(): Dns = object : Dns {
            override fun lookup(hostname: String): List<java.net.InetAddress> {
                val all = Dns.SYSTEM.lookup(hostname)
                val ipv4 = all.filterIsInstance<java.net.Inet4Address>()
                return if (ipv4.isNotEmpty()) ipv4 else all
            }
        }
    }

    open fun init(context: Context, extend: String) {}

    open fun homeContent(filter: Boolean): String = ""

    open fun homeVideoContent(): String = ""

    open fun categoryContent(
        cid: String,
        pg: String,
        filter: Boolean,
        extend: HashMap<String, String>
    ): String = ""

    open fun detailContent(ids: List<String>): String = ""

    open fun playerContent(flag: String, id: String, vipFlags: List<String>): String = ""

    open fun searchContent(key: String, quick: Boolean): String = ""

    open fun searchContent(key: String, quick: Boolean, pn: String): String = ""

    open fun action(action: String): String = ""

    open fun destroy() {}

    open fun clearCookies() {}
}
