package com.embylite.utils

import com.embylite.data.model.MediaSource
import com.embylite.data.model.MediaStream

/**
 * 播放工具：从 MediaSource 提取最终播放 URL（处理 302 STRM 直链、转码回退）
 *
 * URL 优先级：
 * 1. DirectStreamUrl（相对路径，拼接 server + api_key）—— Emby 标准直链
 * 2. TranscodingUrl（转码直播流，拼接 server + api_key）—— 不支持直链时回退
 * 3. Path 完整 http（远程 STRM 等）—— 直接用
 * 4. /Videos/{itemId}/stream?Static=true&api_key=token —— 最终兜底
 */
object PlayerUtils {

    /**
     * 构建播放 URL
     * @param server 服务器地址
     * @param itemId 媒体 id
     * @param mediaSource 媒体源
     * @param token 访问令牌（用于 api_key 参数）
     */
    fun buildPlayUrl(server: String, itemId: String, mediaSource: MediaSource?, token: String?): String? {
        val base = NetworkModule.formatBaseUrl(server)

        // 1. DirectStreamUrl（Emby PlaybackInfo 通常返回这个）
        val direct = mediaSource?.DirectStreamUrl
        if (!direct.isNullOrEmpty()) {
            val url = if (direct.startsWith("http")) direct else "${base}${direct.trimStart('/')}"
            return appendApiKey(url, token)
        }

        // 2. TranscodingUrl（不支持直链时 Emby 返回转码直播流地址）
        val trans = mediaSource?.TranscodingUrl
        if (!trans.isNullOrEmpty()) {
            val url = if (trans.startsWith("http")) trans else "${base}${trans.trimStart('/')}"
            return appendApiKey(url, token)
        }

        // 3. Path 为完整 http URL（远程 STRM 等已包含直链）
        val path = mediaSource?.Path
        if (!path.isNullOrEmpty() && (path.startsWith("http://") || path.startsWith("https://"))) {
            return path
        }

        // 4. 最终兜底：Emby 通用流接口，Static=true 让 Emby 直出原始流（不支持则自动转码）
        return if (token.isNullOrEmpty()) {
            "${base}Videos/$itemId/stream?Static=true"
        } else {
            "${base}Videos/$itemId/stream?Static=true&api_key=$token"
        }
    }

    // 给 URL 追加 api_key（自动处理已有 ? 的情况）
    private fun appendApiKey(url: String, token: String?): String {
        if (token.isNullOrEmpty()) return url
        return if (url.contains("?")) "${url}&api_key=$token" else "${url}?api_key=$token"
    }

    // ticks 转 ms（1 tick = 100 ns = 0.0001 ms）
    fun ticksToMs(ticks: Long?): Long {
        return ticks?.div(10000) ?: 0L
    }

    // ticks 转"HH:MM:SS"或"MM:SS"
    fun ticksToTimeStr(ticks: Long?): String {
        val ms = ticksToMs(ticks)
        return msToTimeStr(ms)
    }

    // ms 转"HH:MM:SS"或"MM:SS"
    fun msToTimeStr(ms: Long): String {
        if (ms < 0) return "00:00"
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format("%02d:%02d:%02d", h, m, s)
        else String.format("%02d:%02d", m, s)
    }

    // 从 MediaStreams 提取字幕轨道（Type=Subtitle）
    fun getSubtitleStreams(mediaSource: MediaSource?): List<MediaStream> {
        return mediaSource?.MediaStreams?.filter { it.Type == "Subtitle" } ?: emptyList()
    }

    // 从 MediaStreams 提取音轨（Type=Audio）
    fun getAudioStreams(mediaSource: MediaSource?): List<MediaStream> {
        return mediaSource?.MediaStreams?.filter { it.Type == "Audio" } ?: emptyList()
    }

    // 提取分辨率描述（如 1080p）
    fun getResolutionLabel(mediaSource: MediaSource?): String? {
        val h = mediaSource?.Height ?: return null
        return when {
            h >= 2160 -> "4K"
            h >= 1080 -> "1080p"
            h >= 720 -> "720p"
            h >= 480 -> "480p"
            else -> "${h}p"
        }
    }
}
