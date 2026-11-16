package com.chen.doudoutv.ui.components

import com.chen.doudoutv.data.emby.EmbyClient
import com.chen.doudoutv.data.format.FormatProbe
import com.chen.doudoutv.data.model.MediaEntity

/**
 * 徽章取值：电影取该电影那个文件的信息，剧集取第一集那个文件的信息。
 *
 * ## 为什么不能直接用「当前条目」
 *
 * 剧集页上的当前条目是剧集壳。壳行身上没有画质字段：
 *  · TVBox 只在入库那一刻把文件名推测值写在壳上，而且分集守卫（库里已有分集就不再拉详情）
 *    会让老条目错过这次推测 —— 壳上的四个字段是空的；
 *  · Emby 的壳行从头到尾没有轨道字段，轨道信息在每个文件的 MediaSources 里。
 *
 * 于是详情页的徽章行整行空掉（徽章行「一项都没有就不渲染」），画质徽章连同
 * 「共 N 集」这类源信息一起消失 —— 现象上就是「徽章整个不见了」。
 *
 * ## 分来源取
 *
 *  · Emby：优先该条目自己的媒体流真值（EmbyMediaCache，按 remoteId 存），
 *    没有再回退详情里解析出的 MediaSources —— 剧集的 MediaSources 正是第一集那个文件；
 *  · TVBox：条目行没有轨道字段，用那个文件的名字推测（分集行的标题就是文件名）；
 *    推测不出来再回退壳行上入库时留下的值。
 *
 * 剧集传入的 [badgeSource] 应当是**当前季的第一集**：徽章描述的是用户正在看的那一档文件。
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
