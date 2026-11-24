package com.chen.doudoutv.ui.components

import com.chen.doudoutv.data.emby.EmbyClient
import com.chen.doudoutv.data.format.FormatProbe
import com.chen.doudoutv.data.model.MediaEntity

/**
 * 徽章取值：电影取该电影文件的信息，剧集取第一集文件的信息（[badgeSource] 应传当前季第一集）。
 * 不能直接用当前条目——剧集壳行没有画质字段：TVBox 壳的推测值会被分集守卫跳过、
 * Emby 壳行从头没有轨道字段，直接取会让徽章行整行消失。
 * Emby 优先 EmbyMediaCache 真值、回退详情 MediaSources（即第一集文件）；TVBox 用文件名推测、
 * 推测不出回退壳行入库值。
 */
fun badgeInfoFor(
    isEmbySource: Boolean,
    item: MediaEntity?,
    badgeSource: MediaEntity?,
    scraped: EmbyClient.ItemDetail?,
    mediaInfo: EmbyClient.MediaInfo?
): FormatProbe.Info {
    val src = badgeSource ?: item
    if (isEmbySource) {
        return FormatProbe.Info(
            resolution = mediaInfo?.resolution.orEmpty()
                .ifBlank { scraped?.videoResolution.orEmpty() }
                .ifBlank { src?.videoResolution.orEmpty() }
                .ifBlank { item?.videoResolution.orEmpty() },
            hdr = mediaInfo?.hdr.orEmpty()
                .ifBlank { scraped?.hdrType.orEmpty() }
                .ifBlank { src?.hdrType.orEmpty() }
                .ifBlank { item?.hdrType.orEmpty() },
            codec = mediaInfo?.videoCodec.orEmpty()
                .ifBlank { scraped?.videoCodec.orEmpty() }
                .ifBlank { src?.videoCodec.orEmpty() }
                .ifBlank { item?.videoCodec.orEmpty() },
            audio = mediaInfo?.audioTracks?.primaryAudioLabel().orEmpty()
                .ifBlank { src?.audioType.orEmpty() }
                .ifBlank { item?.audioType.orEmpty() }
        )
    }
    val guess = FormatProbe.fromText("${src?.title.orEmpty()} ${src?.uri.orEmpty()}")
    return FormatProbe.Info(
        resolution = guess.resolution
            .ifBlank { src?.videoResolution.orEmpty() }
            .ifBlank { item?.videoResolution.orEmpty() },
        hdr = guess.hdr
            .ifBlank { src?.hdrType.orEmpty() }
            .ifBlank { item?.hdrType.orEmpty() },
        codec = guess.codec
            .ifBlank { src?.videoCodec.orEmpty() }
            .ifBlank { item?.videoCodec.orEmpty() },
        audio = guess.audio
            .ifBlank { src?.audioType.orEmpty() }
            .ifBlank { item?.audioType.orEmpty() }
    )
}

/**
 * 音轨徽章标签：优先默认轨，否则第一条。
 *
 * Atmos 单独报 Atmos（识别度最高）；其余把编码名翻成通用叫法 ——
 * Emby 那边给的是 "eac3"/"ac3"/"truehd" 这种协议名，直接印在徽章上用户读不懂。
 */
fun List<EmbyClient.AudioTrack>.primaryAudioLabel(): String {
    val track = firstOrNull { it.isDefault } ?: firstOrNull() ?: return ""
    if (track.atmos) return "Atmos"
    return when (track.codec.lowercase()) {
        "eac3" -> "DD+"
        "ac3" -> "DD"
        "truehd" -> "TrueHD"
        "dts" -> "DTS"
        "dts-hd", "dtshd" -> "DTS-HD"
        "aac" -> "AAC"
        "mp3" -> "MP3"
        "flac" -> "FLAC"
        "opus" -> "Opus"
        else -> track.codec.uppercase()
    }
}
