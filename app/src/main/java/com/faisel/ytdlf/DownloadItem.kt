package com.faisel.ytdlf

enum class DownloadStatus {
    PENDING, DOWNLOADING, PAUSED, COMPLETED, FAILED
}

data class DownloadItem(
    val id: String,
    val url: String,
    val title: String,
    var progress: Int,
    var status: DownloadStatus,
    val filePath: String,
    val skipSsl: Boolean,
    val folderName: String? = null,
    val playlistId: String? = null,
    val isAudio: Boolean = false,
    val selectedQuality: String = "best",
    val embedMetadata: Boolean = false,
    val startTime: String? = null,
    val endTime: String? = null,
    val thumbnailUrl: String? = null
)