package com.chen.doudoutv.data.tvbox

import org.json.JSONObject

/**
 * CatVod Spider 返回的 Result JSON 解析器。
 *
 * Spider 通过 Result.string(...) 返回统一格式 JSON，主要字段：
 *   list       ← 视频条目数组（homeContent/categoryContent/detailContent/searchContent）
 *   class      ← 分类数组（homeContent）
 *   filters    ← 筛选条件（homeContent）
 *   url        ← 真实播放地址（playerContent）
 *   header     ← 播放请求头（playerContent）
 */
object CatVodResultParser {

    data class ClassItem(val typeId: String, val typeName: String)

    /** 筛选取值（协议 value 数组项：n 显示文案、v 传给 categoryContent 的值） */
    data class FilterValue(val n: String, val v: String)

    /** 筛选组（类型/年份/地区/排序…），来自 homeContent 的 filters 字段 */
    data class FilterGroup(val key: String, val name: String, val value: List<FilterValue>)

    data class PlayUrlResult(
        val url: String,
        val headers: Map<String, String>
    )

    /** 解析 class（分类）数组 */
    fun parseClasses(json: String): List<ClassItem> {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        val arr = root.optJSONArray("class") ?: return emptyList()
        val out = ArrayList<ClassItem>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("type_id").ifBlank { o.optString("typeId") }
            val name = o.optString("type_name").ifBlank { o.optString("typeName") }
            if (id.isNotBlank() && name.isNotBlank()) out += ClassItem(id, name)
        }
        return out
    }

    /**
     * 解析 filters 字段：`{ "type_id": [ {key, name, value:[{n,v}]} ] }`。
     * 只有「全部」一项的组没有筛选意义，直接丢弃；整体缺失返回空 Map。
     */
    fun parseFilters(json: String): Map<String, List<FilterGroup>> {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyMap()
        val obj = root.optJSONObject("filters") ?: return emptyMap()
        val out = LinkedHashMap<String, List<FilterGroup>>()
        for (tid in obj.keys()) {
            val arr = obj.optJSONArray(tid) ?: continue
            val groups = ArrayList<FilterGroup>(arr.length())
            for (i in 0 until arr.length()) {
                val g = arr.optJSONObject(i) ?: continue
                val name = g.optString("name")
                val varr = g.optJSONArray("value") ?: continue
                val values = ArrayList<FilterValue>(varr.length())
                for (j in 0 until varr.length()) {
                    val vo = varr.optJSONObject(j) ?: continue
                    val n = vo.optString("n")
                    if (n.isBlank()) continue
                    values += FilterValue(n, vo.optString("v"))
                }
                if (name.isNotBlank() && values.size > 1) {
                    // 源站约定不统一：有的组首值就是「全部/不限」（v 空串），有的直接以实值开头。
                    // 缺兜底值的补一颗「全部」，保证每组都能取消筛选、下标语义与 Emby 本地组一致（0=不筛）
                    val first = values.first()
                    if (first.v.isNotBlank() && first.n != "全部" && first.n != "不限") {
                        values.add(0, FilterValue("全部", ""))
                    }
                    groups += FilterGroup(g.optString("key"), name, values)
                }
            }
            if (groups.isNotEmpty()) out[tid] = groups
        }
        return out
    }

    /** 解析 list 数组 → TvBoxClient.Vod 列表（不含 playSources） */
    fun parseVodList(json: String, siteKey: String, siteName: String): List<TvBoxClient.Vod> {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        val list = root.optJSONArray("list") ?: return emptyList()
        val out = ArrayList<TvBoxClient.Vod>(list.length())
        for (i in 0 until list.length()) {
            val o = list.optJSONObject(i) ?: continue
            val vodId = o.optString("vod_id").ifBlank { o.optString("vodId") }
            if (vodId.isBlank()) continue
            val picRaw = o.optString("vod_pic").ifBlank { o.optString("vodPic") }
            out += TvBoxClient.Vod(
                vodId = vodId,
                name = o.optString("vod_name").ifBlank { o.optString("vodName") },
                pic = TvBoxClient.AbsoluteUrl(picRaw),
                remarks = o.optString("vod_remarks").ifBlank { o.optString("vodRemarks") },
                year = o.optString("vod_year").toIntOrNull() ?: 0,
                area = o.optString("vod_area").ifBlank { o.optString("vodArea") },
                typeName = o.optString("vod_class").ifBlank { o.optString("vodClass") },
                content = o.optString("vod_content").ifBlank { o.optString("vodContent") },
                score = o.optString("vod_score").toDoubleOrNull() ?: 0.0,
                actors = o.optString("vod_actor").ifBlank { o.optString("vodActor") },
                directors = o.optString("vod_director").ifBlank { o.optString("vodDirector") },
                siteKey = siteKey,
                siteName = siteName
            )
        }
        return out
    }

    /**
     * 解析 detailContent 返回的单条 Vod（含 play_from/play_url）。
     * 也可能直接返回完整 list 数组（某些 spider 这么干）。
     */
    fun parseDetail(json: String, siteKey: String, siteName: String): TvBoxClient.Vod? {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val list = root.optJSONArray("list")
        val obj = if (list != null && list.length() > 0) list.optJSONObject(0) else root
        if (obj == null) return null

        val id = obj.optString("vod_id").ifBlank { obj.optString("vodId") }
        if (id.isBlank()) return null

        val picRaw = obj.optString("vod_pic").ifBlank { obj.optString("vodPic") }
        val vod = TvBoxClient.Vod(
            vodId = id,
            name = obj.optString("vod_name").ifBlank { obj.optString("vodName") },
            pic = TvBoxClient.AbsoluteUrl(picRaw),
            remarks = obj.optString("vod_remarks").ifBlank { obj.optString("vodRemarks") },
            year = obj.optString("vod_year").toIntOrNull() ?: 0,
            area = obj.optString("vod_area").ifBlank { obj.optString("vodArea") },
            typeName = obj.optString("vod_class").ifBlank { obj.optString("vodClass") },
            content = obj.optString("vod_content").ifBlank { obj.optString("vodContent") },
            score = obj.optString("vod_score").toDoubleOrNull() ?: 0.0,
            actors = obj.optString("vod_actor").ifBlank { obj.optString("vodActor") },
            directors = obj.optString("vod_director").ifBlank { obj.optString("vodDirector") },
            siteKey = siteKey,
            siteName = siteName
        )

        // 手动解析 play_from / play_url 到 playSources
        val from = obj.optString("vod_play_from")
        val urlStr = obj.optString("vod_play_url")
        if (urlStr.isNotBlank()) {
            val fromNames = from.split("$$$").map { it.trim() }
            val groups = urlStr.split("$$$")
            val sources = groups.mapIndexedNotNull { gi, group ->
                val episodes = group.split('#').mapIndexedNotNull { idx, piece ->
                    if (piece.isBlank()) return@mapIndexedNotNull null
                    val sep = piece.indexOf('$')
                    val (epName, epUrl) = if (sep < 0) {
                        "正片" to piece.trim()
                    } else {
                        val n = piece.substring(0, sep).trim().ifBlank { "第${idx + 1}集" }
                        val u = piece.substring(sep + 1).trim()
                        n to u
                    }
                    // 保留所有 URL，包括云盘 scheme（quark://、aliyundrive://、uc:// 等）。
                    // 注意不能按 http(s) 过滤，否则网盘线路的分集会全部丢失。
                    // 云盘地址的真实播放地址由 playerContent 在播放时按需解析。
                    val absolute = TvBoxClient.AbsoluteUrl(epUrl)
                    TvBoxClient.Episode(epName, absolute)
                }
                if (episodes.isEmpty()) null else {
                    val name = fromNames.getOrNull(gi)?.takeIf { it.isNotBlank() } ?: "线路${gi + 1}"
                    TvBoxClient.PlaySource(name, episodes)
                }
            }
            vod.playSources = sources
        }

        return vod
    }

    /** 解析 playerContent 返回的真实播放地址 + 可选请求头 */
    fun parsePlayer(json: String): PlayUrlResult? {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val url = root.optString("url").ifBlank { root.optString("parse") }
        if (url.isBlank()) return null
        val header = root.optJSONObject("header")
        val headers = LinkedHashMap<String, String>()
        if (header != null) {
            val keys = header.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                headers[k] = header.optString(k)
            }
        }
        return PlayUrlResult(url = TvBoxClient.AbsoluteUrl(url), headers = headers)
    }
}
