package com.chen.doudoutv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chen.doudoutv.R
import com.chen.doudoutv.ui.theme.Radius

/**
 * 格式徽章：TMDB 图标（开启 TMDB 时）+ Emby / 文件名解析到的分辨率、HDR、编码、音轨，
 * 电视端还会追加源自己给的信息（更新状态、共 N 集、N 条线路）。
 *
 * ## 这个文件为什么同时管「取色」和「画」
 *
 * 手机端（本文件的 [FormatBadgesRow]）和电视端（TvDetailScreen 的徽章行）的**排版**必须分开：
 * 手机是一行不换行的 Row、12sp；电视要 FlowRow 换行、14sp，十英尺外才读得出。
 * 但**取色和编排规则**必须只有一份 —— 之前两端各写了一遍相同的 when 分支，结果：
 *
 *   · Dolby Vision 手机端走 [DolbyVisionBadge] 特制图标，电视端却落进 else 分支的灰蓝兜底；
 *   · 音轨棕、编码灰蓝在两个文件里各写一次字面量，改一处漏一处；
 *   · TMDB 蓝在 [BadgeColors] 和 TmdbBranding 里各定义一次。
 *
 * 所以这里只导出三样东西给两端共用：[BadgeColors]（色相表）、[buildFormatBadges]（编排）、
 * [BadgeChip]（单枚渲染）。字号/内边距留给调用方分档，材质与取色一律不开放。
 */

/**
 * 格式徽章的色相表 —— 全项目唯一一份。手机端与电视端都从这里取色。
 *
 * 这些值是「色相」不是「色块」：[TextBadge] 只取 15% 做底、38% 做描边，字固定近白。
 * 所以保持较高饱和度是有意的 —— 经 15% 稀释落到近黑底（AppColors.Bg）上，
 * 才是那道刚好看得见的色偏；如果换成低饱和灰，一排徽章会全糊成同一个颜色，
 * 徽章「一眼分出这是 HDR 还是音轨」的分类作用就没了。
 */
object BadgeColors {
    /** TMDB 品牌蓝。TmdbBranding 的标识徽章也用这一个，不再单独定义。 */
    val Tmdb = Color(0xFF01B4E4)

    /**
     * 分辨率（4K / 1080P）。目前与 [Tmdb] 同值，视觉上属于同一类「技术规格蓝」。
     * 分开命名不是为了区分颜色，而是将来若要单独调色，不必再回来动调用点。
     */
    val Resolution = Color(0xFF01B4E4)

    /** HDR10+ —— HDR 阵营里最强调的一档 */
    val Hdr10Plus = Color(0xFFE50914)

    /** HDR10 / HDR —— 标准 HDR */
    val Hdr10 = Color(0xFF2E7D32)

    /** HLG —— 广播档 HDR */
    val Hlg = Color(0xFFF57C00)

    /** Dolby Vision —— 杜比棕 */
    val DolbyVision = Color(0xFF6D4C41)

    /** 编码（HEVC / AV1 / VC-1）与源信息兜底 —— 最低调的一档 */
    val Codec = Color(0xFF455A64)

    /** 音轨（DD+ / TrueHD / Atmos）—— 与杜比同族 */
    val Audio = Color(0xFF6D4C41)

    /** HDR 标签 → 色相。未知档统一落 [Codec]，不凭空造新颜色。 */
    fun hdr(label: String): Color = when (label) {
        "Dolby Vision" -> DolbyVision
        "HDR10+" -> Hdr10Plus
        "HDR10", "HDR" -> Hdr10
        "HLG" -> Hlg
        else -> Codec
    }
}

/** 一枚徽章要表达的内容。颜色不在这里，由 [tint] 从 [BadgeColors] 取。 */
sealed interface Badge {
    data object Tmdb : Badge

    /** 单独成类，否则会像以前那样落进 [Hdr] 的 else 分支变成灰蓝。 */
    data object DolbyVision : Badge
    data class Resolution(val label: String) : Badge

    /** [logoRes] 非空时该枚改画官方标识（电视端），为空时画文字（手机端）。 */
    data class Hdr(val label: String, val logoRes: Int? = null) : Badge

    data class Codec(val label: String) : Badge

    /** [logoRes] 同 [Hdr]：Dolby 系与 DTS 有官方字标，其余走文字。 */
    data class Audio(val label: String, val logoRes: Int? = null) : Badge

    /** 源自己给的信息：更新状态、共 N 集、N 条线路。手机端没有这类数据时自然为空。 */
    data class Source(val label: String) : Badge
}

/**
 * 官方标识素材（电视端用，手机端不启用）。
 *
 * 素材来自 Wikimedia Commons 的官方字标，统一取「黑字透明底」那一版：
 * 黑色像素配 alpha，在 Compose 里用 [Icon] 的 tint 一次刷成近白即可，
 * 不用为每个品牌各出一份白色版本。
 *
 * 收进来的几张是筛选过的：金框 FullHD、红蓝 HLG 曲线、棕团 DTS-HD 三张已被淘汰 ——
 * 颜色跳、形状看不懂，跟哑光徽章摆在一排像贴纸。分辨率（4K / 1080P）和编码
 * （HEVC / AV1）也没有收录：前者官方标是一块黑底方牌，后者根本没有官方字标，
 * 这两类一律走文字。
 */
object BadgeLogos {
    val Hdr10 = R.drawable.ic_badge_hdr10
    val Hdr10Plus = R.drawable.ic_badge_hdr10plus
    val DolbyAtmos = R.drawable.ic_badge_dolby_atmos
    val DolbyTrueHd = R.drawable.ic_badge_dolby_truehd
    val DolbyDigital = R.drawable.ic_badge_dolby_dd
    val DolbyDigitalPlus = R.drawable.ic_badge_dolby_ddplus
    val Dts = R.drawable.ic_badge_dts

    /** HDR 档位 → 官方标。只有 HDR10 / HDR10+ 有干净的黑字版，HLG 与泛称 HDR 走文字。 */
    fun forHdr(label: String): Int? = when (label) {
        "HDR10+" -> Hdr10Plus
        "HDR10" -> Hdr10
        else -> null
    }

    /** 音轨 → 官方标。Dolby 系与 DTS 有体面的官方字标，其余（AAC / MP3 / FLAC…）走文字。 */
    fun forAudio(label: String): Int? = when (label) {
        "Atmos" -> DolbyAtmos
        "TrueHD" -> DolbyTrueHd
        "DD+" -> DolbyDigitalPlus
        "DD" -> DolbyDigital
        "DTS", "DTS-HD" -> Dts
        else -> null
    }
}

/**
 * 徽章编排 —— 两端共用的一套规则。
 *
 * 手机端不传 [sourceInfo]，用默认空列表即可，不必再养一份「不带源信息」的副本。
 *
 * 两条刻意过滤的规则，两端行为必须一致，所以放在这里而不是各自的调用点：
 *   · `H.264` 不显示 —— 它太通用，挂上去只是噪声；徽章的价值在于「这条片源特别在哪」。
 *   · `Dolby Digital` 同理，只让 DD+ / TrueHD / Atmos 这类有辨识度的音轨出现。
 *
 * 编码统一转大写：以前手机端显示 `hevc`、电视端显示 `HEVC`，同一个字段两块屏大小写不同。
 */
fun buildFormatBadges(
    tmdbEnabled: Boolean,
    resolution: String,
    hdr: String,
    codec: String,
    audio: String,
    sourceInfo: List<String> = emptyList(),
    useLogos: Boolean = false
): List<Badge> = buildList {
    if (tmdbEnabled) add(Badge.Tmdb)
    resolution.takeIf { it.isNotBlank() }?.let { add(Badge.Resolution(it)) }
    hdr.takeIf { it.isNotBlank() }?.let {
        add(
            if (it == "Dolby Vision") Badge.DolbyVision
            else Badge.Hdr(it, if (useLogos) BadgeLogos.forHdr(it) else null)
        )
    }
    codec.takeIf { it.isNotBlank() && !it.equals("H.264", ignoreCase = true) }
        ?.let { add(Badge.Codec(it.uppercase())) }
    audio.takeIf { it.isNotBlank() && it != "Dolby Digital" }?.let {
        add(Badge.Audio(it, if (useLogos) BadgeLogos.forAudio(it) else null))
    }
    sourceInfo.forEach { add(Badge.Source(it)) }
}

/** 徽章色相。两端共用，Dolby Vision 不会再落进棕兜底。 */
fun Badge.tint(): Color = when (this) {
    Badge.Tmdb -> BadgeColors.Tmdb
    Badge.DolbyVision -> BadgeColors.DolbyVision
    is Badge.Resolution -> BadgeColors.Resolution
    is Badge.Hdr -> BadgeColors.hdr(label)
    is Badge.Codec -> BadgeColors.Codec
    is Badge.Audio -> BadgeColors.Audio
    is Badge.Source -> BadgeColors.Codec
}

/** 文字徽章的文字内容；图标类徽章没有文字，返回空串（不会走到渲染）。 */
private fun Badge.labelText(): String = when (this) {
    is Badge.Resolution -> label
    is Badge.Hdr -> label
    is Badge.Codec -> label
    is Badge.Audio -> label
    is Badge.Source -> label
    Badge.Tmdb, Badge.DolbyVision -> ""
}

/**
 * 单枚徽章的渲染 —— 两端唯一的画法。
 *
 * 只把「字号 / 内边距 / 图标高度」留给调用方分档：手机 12sp，电视 14sp。
 * 材质（15% 染色底 + 38% 描边）、圆角、取色一律不开放参数，
 * 否则又会像以前那样各自长出两套。
 */
@Composable
fun BadgeChip(
    badge: Badge,
    fontSize: TextUnit = 12.sp,
    lineHeight: TextUnit = 14.sp,
    letterSpacing: TextUnit = 0.6.sp,
    tmdbIconHeight: Dp = 22.dp,
    dolbyIconHeight: Dp = 16.dp,
    dolbyCaptionSize: TextUnit = 9.sp,
    logoHeight: Dp = 16.dp
) {
    when (badge) {
        Badge.Tmdb -> TmdbIconOnly(tmdbIconHeight)
        Badge.DolbyVision -> DolbyVisionBadge(
            iconHeight = dolbyIconHeight,
            captionSize = dolbyCaptionSize
        )
        is Badge.Hdr -> badge.logoRes?.let {
            LogoBadge(label = badge.label, logoRes = it, tint = badge.tint(), iconHeight = logoHeight)
        } ?: TextBadge(
            label = badge.label,
            bg = badge.tint(),
            fontSize = fontSize,
            lineHeight = lineHeight,
            letterSpacing = letterSpacing
        )
        is Badge.Audio -> badge.logoRes?.let {
            LogoBadge(label = badge.label, logoRes = it, tint = badge.tint(), iconHeight = logoHeight)
        } ?: TextBadge(
            label = badge.label,
            bg = badge.tint(),
            fontSize = fontSize,
            lineHeight = lineHeight,
            letterSpacing = letterSpacing
        )
        else -> TextBadge(
            label = badge.labelText(),
            bg = badge.tint(),
            fontSize = fontSize,
            lineHeight = lineHeight,
            letterSpacing = letterSpacing
        )
    }
}

/**
 * 官方标识徽章：材质跟 [TextBadge] 完全一致（15% 染色底 + 38% 描边），
 * 里面装一张官方字标。
 *
 * 字标素材是黑字透明底的那版，用 [Icon] 的 tint 一次刷成近白 ——
 * 所以 chip 里不再另写一遍同样的字（官方标自己已经写着「HDR 10+」「DOLBY ATMOS」），
 * 文字与 logo 并排会变成同一句话说两遍。
 */
@Composable
private fun LogoBadge(
    label: String,
    logoRes: Int,
    tint: Color,
    iconHeight: Dp
) {
    Box(
        Modifier
            .clip(Radius.Sm)
            .background(tint.copy(alpha = 0.15f))
            .border(1.dp, tint.copy(alpha = 0.38f), Radius.Sm)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(logoRes),
            contentDescription = label,
            tint = Color.White.copy(alpha = 0.92f),
            modifier = Modifier.height(iconHeight)
        )
    }
}

/**
 * 手机端徽章行：TMDB 图标 + 画质，单行不换行。
 * 统一放在详情页标题覆盖层底部（第三行位置）。
 */
@Composable
fun FormatBadgesRow(
    tmdbEnabled: Boolean,
    resolution: String,
    hdr: String,
    codec: String,
    audio: String = "",
    modifier: Modifier = Modifier
) {
    val items = buildFormatBadges(
        tmdbEnabled = tmdbEnabled,
        resolution = resolution,
        hdr = hdr,
        codec = codec,
        audio = audio
    )
    if (items.isEmpty()) return

    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        items.forEach { BadgeChip(it) }
    }
}

/**
 * 纯文字徽章：哑光材质 —— 底色只取传入色的 15% 做同色相染色，再勾一道同色相的描边，字用近白。
 *
 * 之前是六个高饱和实底色块（青/绿/红/橙/蓝灰/棕）并排，徽章本身比海报还抢眼，
 * 等于在画面上撒了一排彩色贴纸。传入的色值保留下来：它现在表示「这一类信息的色相」，
 * 不再等于色块本身。
 *
 * 尺寸与内边距一字未改，只换材质 —— 徽章行在详情页覆盖层的第三行，动内边距会挤动整行。
 */
@Composable
fun TextBadge(
    label: String,
    bg: Color,
    fontSize: TextUnit = 12.sp,
    lineHeight: TextUnit = 14.sp,
    letterSpacing: TextUnit = 0.6.sp
) {
    Box(
        Modifier
            .clip(Radius.Sm)
            .background(bg.copy(alpha = 0.15f))
            .border(1.dp, bg.copy(alpha = 0.38f), Radius.Sm)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = fontSize,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = letterSpacing,
            lineHeight = lineHeight
        )
    }
}

/** 只保留 TMDB 官方图标（Simple Icons · 纯蓝 logo），无任何文字。 */
@Composable
fun TmdbIconOnly(height: Dp = 22.dp) {
    Icon(
        painter = painterResource(R.drawable.ic_tmdb_logo),
        contentDescription = "TMDB",
        tint = BadgeColors.Tmdb,
        modifier = Modifier.height(height)
    )
}

/**
 * Dolby Vision 徽章：官方 D 字母图标 + VISION 小字。
 *
 * 原来是一整块纯白底 —— 一排哑光徽章里唯一发亮的色块，视线先被它抓走。
 * 现在跟着 [TextBadge] 的材质走：低亮度白底 + 一道亮边，Dolby 的标识本身保持原样。
 *
 * 图标高度与字号做成参数，是为了让电视端能跟 [BadgeChip] 的 14sp 档一起放大，
 * 不至于在换行的大徽章堆里缩成一颗小点。
 */
@Composable
fun DolbyVisionBadge(
    iconHeight: Dp = 16.dp,
    captionSize: TextUnit = 9.sp
) {
    Box(
        Modifier
            .clip(Radius.Sm)
            .background(Color.White.copy(alpha = 0.13f))
            .border(1.dp, Color.White.copy(alpha = 0.34f), Radius.Sm)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_dolby),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.height(iconHeight)
            )
            Spacer(Modifier.width(2.dp))
            Text(
                "VISION",
                color = Color.White.copy(alpha = 0.92f),
                fontSize = captionSize,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.3.sp
            )
        }
    }
}
