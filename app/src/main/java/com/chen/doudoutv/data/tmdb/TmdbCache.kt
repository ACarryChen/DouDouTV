package com.chen.doudoutv.data.tmdb

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * TMDB 刮削结果的内存缓存（LRU 300 条）。表（scraped_meta，跨重启持久）命中时不经过这里，
 * 它真正兜住三件事：
 *  1. 正缓存（TTL 7 天）：写表失败时的唯一兜底；
 *  2. 负缓存（TTL 1 天）：表刻意不落「刮不到」（空记录会堵死以后补录的路），靠这里挡同进程内重试；
 *  3. 并发去重：in-flight 合并，后到者挂起等第一次结果。
 *     注意：等待用 CompletableDeferred 在锁外完成，持锁等待会堵死其他 key。
 */
object TmdbCache {

    /** 正缓存有效期：7 天 */
    private const val TTL_HIT_MS = 7L * 24 * 60 * 60 * 1000

    /** 负缓存有效期：1 天（刮不到的结果也记，但更早过期） */
    private const val TTL_MISS_MS = 24L * 60 * 60 * 1000

    /** 最多缓存条数 */
    private const val MAX_ENTRIES = 300

    /** 一条缓存记录：result 为 null 表示「确认刮不到」 */
    private class Entry(val result: TmdbClient.ScrapeResult?, val at: Long, val ttl: Long) {
        fun isValid(now: Long) = now - at < ttl
    }

    // accessOrder = true 即 LRU：每次读把条目移到队尾，淘汰时从队首取最久未用的
    private val entries = object : LinkedHashMap<String, Entry>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>): Boolean =
            size > MAX_ENTRIES
    }

    /** 保护 entries / inFlight 的锁。只包裹极短的临界区，绝不在持锁时做 IO 或等待。 */
    private val lock = Mutex()

    /** 正在请求中的 key → 结果舱。其他协程 await 它，实现并发合并。 */
    private val inFlight = mutableMapOf<String, CompletableDeferred<TmdbClient.ScrapeResult?>>()

    /**
     * 生成缓存键。
     * 传进来的 title 已经是 `ScrapeTitle` 洗过的检索名，所以「嫁金钗（臻彩）」和「嫁金钗 4K」
     * 会算成同一条 —— 换条线路重进不必再刮一遍。
     * 再做一层归一化：全角转半角、全角空格折成普通空格、连续空白压成一个、转小写，
     * 避免「飞行家 」和「飞行家」、同名但括号写法不同被当成两条。
     * 年份和类型仍然参与：同名电影与剧集、同名老片与新片是两条不同数据。
     */
    fun keyOf(title: String, year: Int, kind: String): String =
        "${normalize(title)}|$year|$kind"

    /** 不带年份的那半截键：刮削表用它做「同名不同年」的回落查询 */
    fun titleKeyOf(title: String, kind: String): String = "${normalize(title)}|$kind"

    private fun normalize(title: String): String =
        title.map {
            // 全角标点和字母数字（U+FF01–U+FF5E）折到半角；汉字不受影响
            if (it.code in 0xFF01..0xFF5E) (it.code - 0xFEE0).toChar() else it
        }.joinToString("")
            .replace('　', ' ')
            .lowercase()
            .replace(Regex("""\s+"""), " ")
            .trim()

    /** 读缓存（不加锁，靠 entries 自身的 synchronized 保证安全） */
    private fun readEntry(key: String): Entry? {
        val now = System.currentTimeMillis()
        synchronized(entries) {
            val e = entries[key] ?: return null
            if (!e.isValid(now)) {
                entries.remove(key)
                return null
            }
            return e
        }
    }

    private fun writeEntry(key: String, result: TmdbClient.ScrapeResult?) {
        val now = System.currentTimeMillis()
        val ttl = if (result != null) TTL_HIT_MS else TTL_MISS_MS
        synchronized(entries) { entries[key] = Entry(result, now, ttl) }
    }

    /** 只读查看（不触发请求），用于判断是否值得发起网络请求 */
    fun peek(key: String): TmdbClient.ScrapeResult? = readEntry(key)?.result

    /** 手动写入一条成功结果（外部已有数据时用） */
    fun putHit(key: String, result: TmdbClient.ScrapeResult) = writeEntry(key, result)

    /** 手动写入一条「刮不到」 */
    fun putMiss(key: String) = writeEntry(key, null)

    /**
     * 带并发去重的取值入口。
     *
     * @param fetch 真正的网络请求，只在缓存未命中且无人正在请求时才执行
     * @param ignoreNegative 忽略「确认刮不到」的负缓存，强制重新发请求。
     *   批量刮削要用它：那一轮里因为网络抖动/限流失败的片子会进负缓存，
     *   不忽略的话用户重跑批量，这些片子一天内一次都不会再试，永远补不上。
     * @return 缓存结果或 fetch 的结果；null 表示确认刮不到
     */
    suspend fun getOrFetch(
        key: String,
        ignoreNegative: Boolean = false,
        fetch: suspend () -> TmdbClient.ScrapeResult?
    ): TmdbClient.ScrapeResult? {
        // 快路径：命中缓存直接返回，完全不进锁（绝大多数调用走这里）
        readEntry(key)?.let { if (!ignoreNegative || it.result != null) return it.result }

        // 决定「我是发起者」还是「我等待别人」
        var mine: CompletableDeferred<TmdbClient.ScrapeResult?>? = null
        var isOwner = false

        lock.withLock {
            // 双重检查：可能在上一个持锁者退出前已有结果
            readEntry(key)?.let { if (!ignoreNegative || it.result != null) return it.result }

            val existing = inFlight[key]
            if (existing != null) {
                mine = existing
            } else {
                val deferred = CompletableDeferred<TmdbClient.ScrapeResult?>()
                inFlight[key] = deferred
                mine = deferred
                isOwner = true
            }
        }

        // 等待者：在锁外挂起，拿到发起者的结果就走
        if (!isOwner) return mine!!.await()

        // 发起者：真正联网，结束后把结果广播给所有等待者
        val deferred = mine!!
        try {
            val result = fetch()
            // 忽略负缓存的模式下，失败不写缓存：批量重跑要能一次次重试，
            // 否则这一部片子会被「确认刮不到」钉住一天
            if (!(ignoreNegative && result == null)) writeEntry(key, result)
            deferred.complete(result)
            return result
        } catch (t: Throwable) {
            // 网络异常不写负缓存：这是临时故障，不该让用户一天内都刮不到
            deferred.complete(null)
            throw t
        } finally {
            lock.withLock { inFlight.remove(key) }
        }
    }

    /** 清空缓存（设置页「清空刮削缓存」入口用） */
    fun clear() {
        synchronized(entries) { entries.clear() }
    }
}
