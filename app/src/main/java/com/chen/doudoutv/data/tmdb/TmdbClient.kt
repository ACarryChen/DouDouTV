package com.chen.doudoutv.data.tmdb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * TMDB (The Movie Database) 刮削客户端。
 *
 * 作为 Emby 刮削的补充：TVBox 源本身没有元数据，Emby 源也可能刮削不全，
 * 这里用 TMDB 公开 API 按「标题 + 年份」搜索并拉取海报/背景图/Logo/评分/简介/演职员。
 *
 * 认证支持两种凭据（设置页各有一个输入框，填任意一个即可）：
 *   - API Key（v3 auth）        → 以 `?api_key=` 查询参数传递
 *   - Read Access Token（v4）   → 以 `Authorization: Bearer` 请求头传递
 * 两者都填时优先用 v4 令牌：密钥不会出现在 URL 与日志里，更安全。
 * 未配置任何凭据时所有方法返回 null，不影响原有流程。
 */
class TmdbClient(
    private val apiKey: String,
    private val readToken: String = ""
) {

    /** 刮削结果：与 EmbyClient.ItemDetail 对齐，便于详情页统一渲染 */
    data class ScrapeResult(
        val overview: String = "",
        val rating: Double = 0.0,
        val genres: String = "",
        val posterUrl: String = "",
        val backdropUrl: String = "",
        val logoUrl: String = "",
        val tagline: String = "",
        val directors: List<Person> = emptyList(),
        val actors: List<Person> = emptyList()
    )

    data class Person(val name: String, val role: String, val imageUrl: String)

    private val http = sharedHttp

    /** 是否具备可用凭据 */
    private val hasCredential: Boolean
        get() = readToken.isNotBlank() || apiKey.isNotBlank()

    /**
     * 发一次 GET，返回解析好的 JSON。
     *
     * 限流(429)会退避后重试：TMDB 的配额按 IP 计，共享出口（移动网络、公司 NAT）
     * 很容易撞上同一出口里别人的请求被限流。撞上了就退避一下再试，
     * 而不是把这一部直接判成「刮不到」—— 那会让整批刮削莫名其妙少一截。
     */
    private suspend fun get(path: String, params: Map<String, String> = emptyMap()): JSONObject? {
        if (!hasCredential) return null

        // v4 令牌走 Bearer 头；否则把 v3 Key 拼进查询串
        val useToken = readToken.isNotBlank()
        val url = StringBuilder("$BASE$path?language=zh-CN")
        if (!useToken) url.append("&api_key=").append(apiKey)
        params.forEach { (k, v) ->
            url.append("&").append(k).append("=")
                .append(java.net.URLEncoder.encode(v, "UTF-8"))
        }

        val builder = Request.Builder().url(url.toString())
            .header("Accept", "application/json")
        if (useToken) builder.header("Authorization", "Bearer $readToken")
        val req = builder.build()

        var attempt = 0
        while (attempt < GET_MAX_ATTEMPTS) {
            attempt++
            var retryAfterMs = 0L
            val obj = runCatching {
                http.newCall(req).execute().use { resp ->
                    if (resp.code == 429) {
                        retryAfterMs = 1500L * attempt
                        return@use null
                    }
                    if (!resp.isSuccessful) return@use null
                    resp.body?.string()?.let { text ->
                        runCatching { JSONObject(text) }.getOrNull()
                    }
                }
            }.getOrNull()
            // 429 才退避重试；其它失败（401/404/网络异常）重试没有意义
            if (retryAfterMs <= 0L) return obj
            delay(retryAfterMs)
        }
        return null
    }

    /**
     * 刮削单个条目。
     *
     * @param title 检索用片名（已由 [com.chen.doudoutv.data.MediaRepository.tmdbScrape] 过 [ScrapeTitle]，
     *              画质/集数这类运营后缀已经去掉；这里不再洗第二次）
     * @param year  年份，0 表示未知
     * @param kind  MediaKind.MOVIE / SERIES
     * @param category 源分类推出的动画/真人剧类别，用于同名片挑对的那条
     */
    suspend fun scrape(
        title: String,
        year: Int,
        kind: String,
        category: Int = ScrapeCategory.UNKNOWN
    ): ScrapeResult? =
        withContext(Dispatchers.IO) {
            if (!hasCredential || title.isBlank()) return@withContext null

            // 先按传进来的 kind 刮
            scrapeAs(title, year, kind, category)?.let { return@withContext it }

            // 兜底：换一种类型再搜一遍。
            // TVBox 的 vod 不区分电影和剧集，kind 一律写死 SERIES，于是所有电影
            // 都去 /search/tv 找 —— 那里当然没有，整批一起刮空（实测 40 部里 16 部
            // 全军覆没，全是这类）。反过来 Emby 的电影被误判成剧集也一样。
            // 详情路径是跟着 kind 走的（/tv/{id} 与 /movie/{id} 是两套），所以整套重来。
            val alt = if (kind == "SERIES") "MOVIE" else "SERIES"
            scrapeAs(title, year, alt, category)
        }

    /** 按指定的类型刮一遍（搜索 + 详情） */
    private suspend fun scrapeAs(
        title: String,
        year: Int,
        kind: String,
        category: Int
    ): ScrapeResult? {
        val id = searchId(title, year, kind, category) ?: return null
        val isMovie = kind != "SERIES"
        val detailPath = if (isMovie) "/movie/$id" else "/tv/$id"
        // include_image_language 必须显式声明：TMDB 的 images 子对象默认只返回
        // 与 language 匹配的语种 + null，大多数影片只有英文 logo，
        // 不带这个参数 logos 数组会是空的 → 详情页永远只显示文字标题。
        val detail = get(
            detailPath, mapOf(
                "append_to_response" to "images,credits",
                "include_image_language" to "en,zh,null"
            )
        )
        if (detail == null) return null

        val images = detail.optJSONObject("images")
        val logoPath = pickLogo(images?.optJSONArray("logos"))
        val posterPath = detail.optString("poster_path", "")
        val backdropPath = detail.optString("backdrop_path", "")

        val credits = detail.optJSONObject("credits")
        val directors = credits?.optJSONArray("crew")
            ?.filterObjects { it.optString("job") == "Director" }
            ?.take(3)
            ?.map {
                Person(
                    name = it.optString("name", ""),
                    role = it.optString("job", ""),
                    imageUrl = imageUrl(it.optString("profile_path", ""), "w185")
                )
            }.orEmpty()

        val actors = credits?.optJSONArray("cast")
            ?.filterObjects { it.optInt("order") in 0..19 }
            ?.take(12)
            ?.map {
                Person(
                    name = it.optString("name", ""),
                    role = it.optString("character", ""),
                    imageUrl = imageUrl(it.optString("profile_path", ""), "w185")
                )
            }.orEmpty()

        val genres = detail.optJSONArray("genres")
            ?.mapObjects { it.optString("name", "") }
            ?.joinToString(" / ")
            .orEmpty()

        return ScrapeResult(
            overview = detail.optString("overview", ""),
            rating = detail.optDouble("vote_average", 0.0),
            genres = genres,
            posterUrl = imageUrl(posterPath, "w500"),
            backdropUrl = imageUrl(backdropPath, "original"),
            logoUrl = imageUrl(logoPath, "original"),
            tagline = detail.optString("tagline", ""),
            directors = directors,
            actors = actors
        )
    }

    /**
     * 搜索并返回最匹配的 TMDB id；已知类别时在前几条里优先挑类别相符的。
     *
     * 年份只当「加分项」不当「门槛」：源站给的年份常常差一年（首播年 vs 发行年、
     * 源站填 0 而 TMDB 填首播年），把年份当硬过滤会让本来能搜到的片子直接搜不到。
     * 所以先用带年份的精确搜，搜不到再退回不带年份搜一次。
     */
    private suspend fun searchId(title: String, year: Int, kind: String, category: Int): Int? {
        val exact = searchOnce(title, year, kind, category)
        if (exact != null) return exact
        if (year > 0) {
            // 年份对不上时的退路：去掉年份再搜一遍，结果照样按类别消歧
            return searchOnce(title, 0, kind, category)
        }
        return null
    }

    /** 真正发一次搜索请求并挑 id */
    private suspend fun searchOnce(title: String, year: Int, kind: String, category: Int): Int? {
        val isMovie = kind != "SERIES"
        val path = if (isMovie) "/search/movie" else "/search/tv"
        val params = buildMap {
            put("query", title)
            if (year > 0) put(if (isMovie) "year" else "first_air_date_year", year.toString())
        }
        val results = get(path, params)?.optJSONArray("results") ?: return null
        if (results.length() == 0) return null
        // 同名消歧：「凡人修仙传」动画比剧版热门、常排第一，源分类已知时
        // 在前 5 条里挑第一条 Animation(16) 标记相符的；都不符再回落第一条。
        if (category != ScrapeCategory.UNKNOWN) {
            for (i in 0 until minOf(results.length(), 5)) {
                val obj = results.optJSONObject(i) ?: continue
                val ids = obj.optJSONArray("genre_ids") ?: continue
                var hasAnimation = false
                for (j in 0 until ids.length()) {
                    if (ids.optInt(j) == 16) {
                        hasAnimation = true
                        break
                    }
                }
                if (ScrapeCategory.ofTmdbIds(hasAnimation) == category) {
                    return obj.optInt("id").takeIf { it > 0 }
                }
            }
        }
        // 取第一条（TMDB 按相关度排序）
        return results.optJSONObject(0)?.optInt("id")?.takeIf { it > 0 }
    }

    /** 从 logos 数组里挑一张：优先中文，其次英文，再其次任意 */
    private fun pickLogo(logos: JSONArray?): String {
        if (logos == null || logos.length() == 0) return ""
        var fallback = ""
        var en = ""
        for (i in 0 until logos.length()) {
            val obj = logos.optJSONObject(i) ?: continue
            val path = obj.optString("file_path", "")
            if (path.isBlank()) continue
            val lang = obj.optString("iso_639_1", "")
            when (lang) {
                "zh" -> return path
                "en" -> en = path
                else -> if (fallback.isBlank()) fallback = path
            }
        }
        return en.ifBlank { fallback }
    }

    private fun imageUrl(path: String, size: String): String =
        if (path.isBlank()) "" else "$IMAGE_BASE/$size$path"

    private fun JSONArray.filterObjects(predicate: (JSONObject) -> Boolean): List<JSONObject> {
        val out = mutableListOf<JSONObject>()
        for (i in 0 until length()) {
            val obj = optJSONObject(i) ?: continue
            if (predicate(obj)) out += obj
        }
        return out
    }

    private fun JSONArray.mapObjects(transform: (JSONObject) -> String): List<String> {
        val out = mutableListOf<String>()
        for (i in 0 until length()) {
            val obj = optJSONObject(i) ?: continue
            out += transform(obj)
        }
        return out
    }

    companion object {
        /**
         * 全局共享的 OkHttpClient。
         * 原来每个 TmdbClient 实例各建一个，等于每次打开详情页都新开一套连接池和线程池，
         * 既浪费资源又复用不了 TCP 连接。OkHttp 本身是线程安全且鼓励复用的，
         * 这里做成单例，连接池（默认 5 个空闲连接保活 5 分钟）才能真正发挥作用。
         */
        private val sharedHttp: OkHttpClient by lazy {
            OkHttpClient.Builder()
                // 超时收紧到 6s/10s（原来是 12s/25s）。
                // 国内直连 TMDB 经常「能连但很慢」，长超时会让每个失败请求白等十几秒，
                // 多个请求叠加起来就是「刮削特别慢」的直接观感。
                // 快速失败 + 缓存兜底，比死等更划算。
                .connectTimeout(6, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                // 整个调用（含重定向、读 body）的上限，防止个别请求无限拖
                .callTimeout(15, TimeUnit.SECONDS)
                // 复用连接：保持 5 个空闲连接 5 分钟，后续请求不用重新握手
                .connectionPool(okhttp3.ConnectionPool(5, 5, TimeUnit.MINUTES))
                .retryOnConnectionFailure(true)
                .build()
        }

        private const val BASE = "https://api.themoviedb.org/3"
        private const val IMAGE_BASE = "https://image.tmdb.org/t/p"

        /** 单个请求最多尝试几次（429 退避重试用） */
        private const val GET_MAX_ATTEMPTS = 3
    }
}
