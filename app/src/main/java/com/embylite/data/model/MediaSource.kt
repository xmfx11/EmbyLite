package com.embylite.data.model

// 媒体源信息，用于播放（提取 DirectStreamUrl / Container / Path 等）
data class MediaSource(
    val Id: String? = null,
    val Name: String? = null,
    val Container: String? = null,
    val Path: String? = null,
    val Protocol: String? = null,        // File / Http / Rtsp 等
    val IsRemote: Boolean? = null,
    val DirectStreamUrl: String? = null,  // 直接流地址（可能为相对路径，需拼接 server）
    val SupportsDirectStream: Boolean? = null,
    val SupportsDirectPlay: Boolean? = null,
    val RunTimeTicks: Long? = null
)
