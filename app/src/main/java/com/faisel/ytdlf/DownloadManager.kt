package com.faisel.ytdlf

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLDecoder
import java.util.UUID

object DownloadManager {
    val downloadItems = mutableListOf<DownloadItem>()
    var onItemAdded: ((Int) -> Unit)? = null
    var onItemUpdated: ((Int) -> Unit)? = null

    private const val PREFS_NAME = "DownloadsPrefs"
    private const val KEY_DOWNLOADS = "downloads_data"

    private val backgroundScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun processUrlInBackground(context: Context, url: String) {
        val prefs = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        val isAudio = prefs.getBoolean("PREF_DEFAULT_AUDIO", false)
        val quality = prefs.getString(if (isAudio) "PREF_DEFAULT_AUDIO_QUALITY" else "PREF_DEFAULT_VIDEO_QUALITY", "best") ?: "best"
        val skipSsl = prefs.getBoolean("PREF_SKIP_SSL_DEFAULT", true)

        backgroundScope.launch {
            try {
                var decodedUrl = url
                try { decodedUrl = URLDecoder.decode(url, "UTF-8") } catch (e: Exception) {}

                val isPlaylist = decodedUrl.contains("list=") || decodedUrl.contains("@") || decodedUrl.contains("/videos") || url.contains("list=")

                val request = YoutubeDLRequest(url)
                if (skipSsl) request.addOption("--no-check-certificate")

                val cookieFile = File(context.getExternalFilesDir(null), "cookies.txt")
                if (cookieFile.exists()) {
                    request.addOption("--cookies", cookieFile.absolutePath)
                } else {
                    request.addOption("--extractor-args", "youtube:player-client=tv,web_embedded")
                }

                if (isPlaylist) {
                    request.addOption("--flat-playlist")
                    request.addOption("--dump-json")

                    val response = YoutubeDL.getInstance().execute(request)
                    val lines = response.out.split("\n").filter { it.isNotBlank() }

                    val playlistId = UUID.randomUUID().toString()
                    var addedCount = 0

                    for (line in lines) {
                        try {
                            val json = JSONObject(line)
                            if (json.has("title") && json.has("id")) {
                                val title = json.getString("title")
                                val id = json.getString("id")
                                if (title != "null" && title.isNotBlank()) {
                                    withContext(Dispatchers.Main) {
                                        enqueueDownload(
                                            context = context,
                                            url = "https://www.youtube.com/watch?v=$id",
                                            title = title,
                                            isAudio = isAudio,
                                            skipSsl = skipSsl,
                                            folderName = context.getString(R.string.title_auto_downloaded_playlist),
                                            playlistId = playlistId,
                                            quality = quality,
                                            thumbnailUrl = "https://i.ytimg.com/vi/$id/hqdefault.jpg"
                                        )
                                    }
                                    addedCount++
                                }
                            }
                        } catch (e: Exception) {}
                    }
                    if (addedCount > 0) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, context.getString(R.string.toast_added_multiple_videos, addedCount), Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    val info = YoutubeDL.getInstance().getInfo(request)
                    withContext(Dispatchers.Main) {
                        enqueueDownload(
                            context = context,
                            url = url,
                            title = info.title ?: "Download",
                            isAudio = isAudio,
                            skipSsl = skipSsl,
                            quality = quality,
                            thumbnailUrl = info.thumbnail
                        )
                        Toast.makeText(context, context.getString(R.string.toast_download_added), Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("DownloadManager", "Background processing failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.toast_error_bot_block_background), Toast.LENGTH_LONG).show()
                }
            }
        }
    }


    fun loadDownloads(context: Context) {
        downloadItems.clear()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_DOWNLOADS, null) ?: return
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val statusStr = obj.getString("status")
                var parsedStatus = DownloadStatus.valueOf(statusStr)
                if (parsedStatus == DownloadStatus.DOWNLOADING) parsedStatus = DownloadStatus.PAUSED

                val folderName = if (obj.has("folderName") && !obj.isNull("folderName")) obj.getString("folderName") else null
                val playlistId = if (obj.has("playlistId") && !obj.isNull("playlistId")) obj.getString("playlistId") else null
                val startTime = if (obj.has("startTime") && !obj.isNull("startTime")) obj.getString("startTime") else null
                val endTime = if (obj.has("endTime") && !obj.isNull("endTime")) obj.getString("endTime") else null
                val thumbnailUrl = if (obj.has("thumbnailUrl") && !obj.isNull("thumbnailUrl")) obj.getString("thumbnailUrl") else null

                downloadItems.add(DownloadItem(
                    id = obj.getString("id"),
                    url = obj.getString("url"),
                    title = obj.getString("title"),
                    progress = obj.getInt("progress"),
                    status = parsedStatus,
                    filePath = obj.getString("filePath"),
                    skipSsl = obj.optBoolean("skipSsl", false),
                    folderName = folderName,
                    playlistId = playlistId,
                    isAudio = obj.optBoolean("isAudio", false),
                    selectedQuality = obj.optString("selectedQuality", "best"),
                    embedMetadata = obj.optBoolean("embedMetadata", false),
                    startTime = startTime,
                    endTime = endTime,
                    thumbnailUrl = thumbnailUrl
                ))
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun saveDownloads(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        for (item in downloadItems) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("url", item.url)
                put("title", item.title)
                put("progress", item.progress)
                put("status", item.status.name)
                put("filePath", item.filePath)
                put("skipSsl", item.skipSsl)
                put("folderName", item.folderName)
                put("playlistId", item.playlistId)
                put("isAudio", item.isAudio)
                put("selectedQuality", item.selectedQuality)
                put("embedMetadata", item.embedMetadata)
                put("startTime", item.startTime)
                put("endTime", item.endTime)
                put("thumbnailUrl", item.thumbnailUrl)
            }
            jsonArray.put(obj)
        }
        prefs.edit { putString(KEY_DOWNLOADS, jsonArray.toString()) }
    }

    fun updateItemAndSave(context: Context, item: DownloadItem) {
        val index = downloadItems.indexOf(item)
        if (index != -1) {
            saveDownloads(context)
            onItemUpdated?.invoke(index)
        }
    }

    private fun getBaseDownloadDirectory(context: Context): File {
        val prefs = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        val savedLocation = prefs.getString("PREF_SAVE_LOCATION", null)

        var baseDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "YtDownloader")

        if (!savedLocation.isNullOrEmpty()) {
            val customDir = File(savedLocation)
            if (customDir.exists() || customDir.mkdirs()) {
                baseDir = customDir
            }
        }

        return baseDir
    }

    fun enqueueDownload(
        context: Context, url: String, title: String, isAudio: Boolean, skipSsl: Boolean,
        folderName: String? = null, playlistId: String? = null,
        quality: String = "best", metadata: Boolean = false, startTime: String? = null, endTime: String? = null,
        thumbnailUrl: String? = null
    ) {
        val id = UUID.randomUUID().toString()
        val downloadDir = getBaseDownloadDirectory(context)
        val safeTitle = title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val ext = if (isAudio) "mp3" else "mp4"

        val finalDir = if (folderName != null) {
            File(downloadDir, folderName.replace(Regex("[\\\\/:*?\"<>|]"), "_")).apply { if (!exists()) mkdirs() }
        } else downloadDir.apply { if (!exists()) mkdirs() }

        val filePath = "${finalDir.absolutePath}/$safeTitle.$ext"

        val newItem = DownloadItem(
            id, url, title, 0, DownloadStatus.PENDING, filePath, skipSsl,
            folderName, playlistId, isAudio, quality, metadata, startTime, endTime, thumbnailUrl
        )

        downloadItems.add(newItem)
        saveDownloads(context)
        onItemAdded?.invoke(downloadItems.size - 1)
        checkQueue(context)
    }

    fun checkQueue(context: Context) {
        val prefs = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        val maxConcurrent = prefs.getInt("PREF_MAX_CONCURRENT", 2)
        val activeCount = downloadItems.count { it.status == DownloadStatus.DOWNLOADING }

        if (activeCount < maxConcurrent) {
            downloadItems.firstOrNull { it.status == DownloadStatus.PENDING }?.let { next ->
                startServiceForItem(context, next)
            }
        }
    }

    private fun startServiceForItem(context: Context, item: DownloadItem) {
        item.status = DownloadStatus.DOWNLOADING
        updateItemAndSave(context, item)
        val serviceIntent = Intent(context, DownloadService::class.java).apply { putExtra("ID", item.id) }
        ContextCompat.startForegroundService(context, serviceIntent)
    }

    fun resumeDownload(context: Context, item: DownloadItem) {
        item.status = DownloadStatus.PENDING
        updateItemAndSave(context, item)
        checkQueue(context)
    }

    fun removeItem(context: Context, item: DownloadItem) {
        downloadItems.remove(item)
        saveDownloads(context)
    }

    fun clearCompletedAndFailed(context: Context) {
        downloadItems.removeAll { it.status == DownloadStatus.COMPLETED || it.status == DownloadStatus.FAILED || it.status == DownloadStatus.PAUSED }
        saveDownloads(context)
    }
}