package com.chen.doudoutv.ui

import com.chen.doudoutv.data.model.MediaEntity
import com.chen.doudoutv.data.model.MediaKind
import com.chen.doudoutv.data.tvbox.CatVodResultParser

/**
 * Emby「本地筛选」共享逻辑：TV 端 NfBrowseScreen 与手机端分类页共用同一套组构造与谓词过滤。
 *
 * 组形状与 TVBox 源站下发的 homeContent filters 完全一致（首值一律「全部」，v 为空串不参与 extend），
 * 上层不为两端各写一份。库里没有的维度不出组（比如全片单没有年份就不放年份行），排序组恒在。
 */

private val GenreSeparator = Regex("[,，、|/]+")

/** 这些标签本身就是类型，已经做成「电影 / 剧集」两颗 chip，不再重复出现在题材列表里 */
internal val KindLikeGenres = setOf("电影", "剧集", "电视剧", "动漫", "综艺")

internal fun MediaEntity.genreTokens(): List<String> =
    genres.split(GenreSeparator).map { it.trim() }.filter { it.isNotBlank() }

internal fun localFilterGroups(items: List<MediaEntity>): List<CatVodResultParser.FilterGroup> {
    if (items.isEmpty()) return emptyList()
    val out = mutableListOf<CatVodResultParser.FilterGroup>()
    val years = items.map { it.year }.filter { it > 0 }.distinct().sortedDescending()
    if (years.size > 1) {
        out += CatVodResultParser.FilterGroup(
            "year", "年份",
            listOf(CatVodResultParser.FilterValue("全部", "")) +
                years.map { CatVodResultParser.FilterValue(it.toString(), it.toString()) }
        )
    }
    val movieCount = items.count { it.kind == MediaKind.MOVIE }
    if (movieCount > 0 && movieCount < items.size) {
        out += CatVodResultParser.FilterGroup(
            "kind", "类型",
            listOf(
                CatVodResultParser.FilterValue("全部", ""),
                CatVodResultParser.FilterValue("电影", "movie"),
                CatVodResultParser.FilterValue("剧集", "series")
            )
        )
    }
    // 题材取频次前 20：和分类 chip 的兜底口径同源，「类型」chip 已表达的不重复列
    val counts = LinkedHashMap<String, Int>()
    items.forEach { item ->
        item.genreTokens().distinct().forEach { tag ->
            if (tag !in KindLikeGenres) counts[tag] = (counts[tag] ?: 0) + 1
        }
    }
    val tags = counts.entries.sortedByDescending { it.value }.take(20).map { it.key }
    if (tags.size > 1) {
        out += CatVodResultParser.FilterGroup(
            "genre", "题材",
            listOf(CatVodResultParser.FilterValue("全部", "")) +
                tags.map { CatVodResultParser.FilterValue(it, it) }
        )
    }
    out += CatVodResultParser.FilterGroup(
        "sort", "排序",
        listOf(
            CatVodResultParser.FilterValue("默认", ""),
            CatVodResultParser.FilterValue("按年份", "year"),
            CatVodResultParser.FilterValue("按评分", "rating"),
            CatVodResultParser.FilterValue("按片名", "title")
        )
    )
    return out
}

/** 把面板选中值作用到本地条目上：各维度是 AND 关系，排序最后统一施加 */
internal fun applyLocalFilters(
    items: List<MediaEntity>,
    extend: Map<String, String>
): List<MediaEntity> {
    if (extend.isEmpty()) return items
    var out = items
    extend["year"]?.toIntOrNull()?.let { y -> out = out.filter { it.year == y } }
    extend["genre"]?.let { g -> out = out.filter { it.genreTokens().contains(g) } }
    extend["kind"]?.let { k ->
        out = out.filter { if (k == "movie") it.kind == MediaKind.MOVIE else it.kind != MediaKind.MOVIE }
    }
    when (extend["sort"]) {
        "year" -> out = out.sortedByDescending { it.year }
        "rating" -> out = out.sortedByDescending { it.rating }
        "title" -> out = out.sortedBy { it.title }
    }
    return out
}
