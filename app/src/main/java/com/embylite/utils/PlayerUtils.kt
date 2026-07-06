package com.embylite.utils

import com.embylite.data.model.MediaSource

/**
 * 播放工具：从 MediaSource 提取最终播放 URL（处理 302 STRM 直链）
 * 优先级：
 * 1. DirectStreamUrl（相对路径，需拼接 server + 加 api_key）
 * 2. Path（若为 http 开头直接用，否则拼接 /Videos/{id}/stream）
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
        val tokenPart = if (!token.isNullOrEmpty()) "?api_key=$token" else ""

        // 1. DirectStreamUrl
        val direct = mediaSource?.DirectStreamUrl
        if (!direct.isNullOrEmpty()) {
            return if (direct.startsWith("http")) {
                // 已是完整 URL
                if (token.isNullOrEmpty()) direct else "${direct}${if (direct.contains("?")) "&" else "?"}api_key=$token"
            } else {
                // 相对路径，拼接 server
                "${base}${direct.trimStart('/')}${if (token.isNullOrEmpty()) "" else (if (direct.contains("?")) "&" else "?") + "api_key=$token"}"
            }
        }

        // 2. Path 为完整 http URL（远程 STRM 等）
        val path = mediaSource?.Path
        if (!path.isNullOrEmpty() && (path.startsWith("http://") || path.startsWith("https://"))) {
            return path
        }

        // 3. 回退到 /Videos/{itemId}/stream
        return "${base}Videos/$itemId/stream$tokenPart"
    }

    // ticks 转 ms（1 tick = 100 ns = 0.0001 ms）
    fun ticksToMs(ticks: Long?): Long {
        return ticks?.div(10000) ?: 0L
    }
}
