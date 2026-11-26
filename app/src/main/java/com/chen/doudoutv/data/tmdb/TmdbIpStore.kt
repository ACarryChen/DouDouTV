package com.chen.doudoutv.data.tmdb

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * TMDB 域名 → 海外真实 IP 的动态 IP 池。
 *
 * ## 为什么需要这个
 *
 * GFW 对 api.themoviedb.org 做了两件事：
 * ① DNS 污染 —— 国内 DNS 把域名解析到假 IP
 * ② IP 封锁 —— AWS Cloudfront 的真实 IP 段也被部分拦截
 *
 * 绕过办法是绕过 DNS 直接连真实 IP。但 AWS Cloudfront 的 IP 池每周滚动，
 * 写死一份快照迟早全部失效。github.com/cnwikee/CheckTMDB 每天从境外网络
 * 检测并发布最新的真实 IP（hosts 格式），本类负责把它拉下来更新本地 IP 池。
 *
 * ## 拉取源
 *
 * raw.githubusercontent.com 国内被墙，所以走三个国内可达的 GitHub 镜像，
 * 依次尝试，谁先通用谁。实测 jsDelivr / ghfast.top / gh-proxy.com 均可在
 * 国内手机网络直连。
 *
 * ## 失效处理
 *
 * - 拉不到新数据 —— 沿用旧池子，不影响已有功能
 * - IP 连不上 —— 通过 OkHttp EventListener 捕获 connectFailed，
 *   把该 IP 冷却 6 小时，后续 lookup 把它排到最后
 * - IP 太老 —— 超过 [TTL_MS] 的排到队尾（仍可用，只是不优先）
 */
object TmdbIpStore {

    private const val TAG = "TmdbIpStore"
    private const val FILE = "tmdb_ip_store"
    private const val KEY_SEEDED = "seeded"
    private const val KEY_LAST_REFRESH = "last_refresh_at"

    /** IP 有效期。超过这个时间的排到队尾但仍保留。 */
    private const val TTL_MS = 45L * 24 * 60 * 60 * 1000

    /** 连接失败后的冷却时长。 */
    private const val BLACKLIST_MS = 6 * 60 * 60 * 1000L

    /**
     * 拉取间隔：一天。
     *
     * CheckTMDB 本身就是每天更新一次 IP，没必要每次开 App 都去拉：
     * 徒增流量和后台请求，还可能把用户刚启动的冷启动拖慢。
     * 上次拉取时间落盘（[KEY_LAST_REFRESH]），杀进程重开也不会重复拉。
     */
    private const val REFRESH_INTERVAL_MS = 24 * 60 * 60 * 1000L

    private val MIRRORS = listOf(
        "https://cdn.jsdelivr.net/gh/cnwikee/CheckTMDB@main/Tmdb_host_ipv4",
        "https://ghfast.top/https://raw.githubusercontent.com/cnwikee/CheckTMDB/main/Tmdb_host_ipv4",
        "https://gh-proxy.com/https://raw.githubusercontent.com/cnwikee/CheckTMDB/main/Tmdb_host_ipv4",
    )

    /** 只关心这几个域名，其余忽略。 */
    private val WANT_HOSTS = setOf(
        "api.themoviedb.org",
        "image.tmdb.org",
        "images.tmdb.org",
        "themoviedb.org",
        "www.themoviedb.org",
        "tmdb.org",
    )

    /**
     * 内置快照（CheckTMDB 2026-10 ~ 11 多期合并）。
     * 拉取失败或者 App 刚装还没拉过时的兜底。fetchedAt = 0 意味着最旧，排序时垫底。
     */
    private val SEED: Map<String, List<String>> = mapOf(
        "api.themoviedb.org" to listOf(
            "3.169.173.96", "3.169.173.22", "3.169.173.45",
            "3.175.34.102", "52.84.150.33", "52.84.150.49",
            "143.204.1.109", "3.169.173.123",
        ),
        "image.tmdb.org" to listOf(
            "169.150.249.167", "143.204.1.109",
            "3.169.173.123", "3.169.173.22",
        ),
        "images.tmdb.org" to listOf(
            "169.150.249.167", "143.204.1.109",
            "3.169.173.123", "3.169.173.22",
        ),
        "themoviedb.org" to listOf(
            "3.169.173.22", "3.169.173.45",
            "52.84.150.33", "52.84.150.49",
        ),
        "www.themoviedb.org" to listOf(
            "3.169.173.45", "3.169.173.22",
            "52.84.150.49", "52.84.150.33",
        ),
        "tmdb.org" to listOf(
            "143.204.1.109", "3.169.173.123",
        ),
    )

    private data class Entry(val ip: String, val fetchedAt: Long)

    @Volatile
    private var pool: Map<String, List<Entry>> = emptyMap()

    /** ip → 冷却截止时间戳 */
    private val blacklist = ConcurrentHashMap<String, Long>()

    private lateinit var sp: SharedPreferences
    private var lastRefreshAt = 0L

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    fun init(context: Context) {
        if (!::sp.isInitialized) {
            sp = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        }
        if (!sp.getBoolean(KEY_SEEDED, false)) {
            // 首次安装：把内置快照灌进去，之后就靠远程刷新维护
            pool = SEED.mapValues { (_, ips) -> ips.map { Entry(it, 0L) } }
            sp.edit().putBoolean(KEY_SEEDED, true).apply()
            persist()
        } else {
            pool = sp.all
                .filterKeys { it in WANT_HOSTS }
                .mapValues { (_, raw) ->
                    raw.toString().split('|').mapNotNull { parseEntry(it) }
                }
                .filterValues { it.isNotEmpty() }
        }
        // 上次拉取时间跨进程保留：杀进程重开 App 也不会在同一天重复拉
        lastRefreshAt = sp.getLong(KEY_LAST_REFRESH, 0L)
    }

    private fun parseEntry(raw: String): Entry? {
        val idx = raw.lastIndexOf(':')
        if (idx <= 0) return null
        val ip = raw.substring(0, idx)
        val ts = raw.substring(idx + 1).toLongOrNull() ?: return null
        return Entry(ip, ts)
    }

    /**
     * 取某个域名当前可用的 IP 列表（已按优先级排序）。
     *
     * 排序规则：新鲜的优先 → 陈旧的靠后 → 刚失败冷却中的垫底。
     */
    fun ipsFor(host: String): List<String> {
        val list = pool[host].orEmpty()
        if (list.isEmpty()) return SEED[host].orEmpty()
        val now = System.currentTimeMillis()
        val (cooled, alive) = list.partition { (blacklist[it.ip] ?: 0L) > now }
        val byFresh = alive.sortedByDescending { it.fetchedAt }
        val cooledSorted = cooled.sortedByDescending { it.fetchedAt }
        val result = (byFresh + cooledSorted).map { it.ip }
        return result.ifEmpty { SEED[host].orEmpty() }
    }

    /** 标记某个 IP 暂时连不通，冷却 6 小时。 */
    fun markFailed(ip: String?) {
        if (ip.isNullOrBlank()) return
        blacklist[ip] = System.currentTimeMillis() + BLACKLIST_MS
    }

    /**
     * 拉取最新 IP 池。成功返回 true。
     * 任何一个镜像可用即算成功，失败时保持原池子不变。
     *
     * 一天最多真正发一次请求：距上次拉取不足 24 小时直接跳过。
     * 拉取时间在发起请求时就落盘（成功与否都记），这样即使镜像全挂、
     * 用户反复重启 App，也不会短时间内反复打扰镜像。
     */
    suspend fun refresh(): Boolean = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (now - lastRefreshAt < REFRESH_INTERVAL_MS) {
            Log.d(TAG, "距上次拉取不足 24 小时，本次跳过")
            return@withContext false
        }
        lastRefreshAt = now
        if (::sp.isInitialized) {
            sp.edit().putLong(KEY_LAST_REFRESH, now).apply()
        }

        for (mirror in MIRRORS) {
            try {
                val req = Request.Builder().url(mirror).build()
                val body = client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    resp.body?.string()
                }
                if (body.isNullOrBlank()) continue
                val parsed = parseHosts(body)
                if (parsed.isEmpty()) continue
                merge(parsed)
                Log.i(TAG, "刷新 TMDB IP 池成功：来源 $mirror，域名 ${parsed.keys}")
                return@withContext true
            } catch (e: Exception) {
                Log.d(TAG, "镜像不可用 $mirror : ${e.message}")
            }
        }
        Log.w(TAG, "全部镜像不可用，沿用旧 IP 池")
        false
    }

    /** 解析 hosts 格式：`1.2.3.4 host1 host2` */
    private fun parseHosts(text: String): Map<String, List<Entry>> {
        val now = System.currentTimeMillis()
        val out = mutableMapOf<String, MutableList<Entry>>()
        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            val parts = line.split(Regex("\\s+"))
            if (parts.size < 2) return@forEach
            val ip = parts[0]
            if (!IP_PATTERN.matches(ip)) return@forEach
            for (host in parts.drop(1)) {
                val h = host.lowercase()
                if (h !in WANT_HOSTS) continue
                val list = out.getOrPut(h) { mutableListOf() }
                if (list.none { it.ip == ip }) list.add(Entry(ip, now))
            }
        }
        return out
    }

    /**
     * 合并新拉到的 IP：新 IP 排前面，本地旧的（未过期且不重复）保留在后。
     * 这样既有最新数据，又不会因为 CheckTMDB 那天抽风只给一个 IP 就全断。
     */
    private fun merge(fresh: Map<String, List<Entry>>) {
        val now = System.currentTimeMillis()
        val hosts = (pool.keys + fresh.keys).toSet()
        val merged = mutableMapOf<String, List<Entry>>()
        for (host in hosts) {
            val newList = fresh[host].orEmpty()
            val newIps = newList.map { it.ip }.toSet()
            val keepOld = pool[host].orEmpty().filter { old ->
                old.ip !in newIps && (now - old.fetchedAt) < TTL_MS
            }
            merged[host] = newList + keepOld
        }
        pool = merged
        persist()
    }

    private fun persist() {
        if (!::sp.isInitialized) return
        val editor = sp.edit()
        for ((host, list) in pool) {
            editor.putString(host, list.joinToString("|") { "${it.ip}:${it.fetchedAt}" })
        }
        editor.apply()
    }

    private val IP_PATTERN = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")
}
