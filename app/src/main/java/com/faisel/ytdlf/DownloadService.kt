package com.faisel.ytdlf

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

class DownloadService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var activeDownloads = 0
    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "YtDownloadChannel"
    private val TAG = "YTDL_DEBUG"

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        Log.d(TAG, "DownloadService created and Notification Channel initialized.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra("ID") ?: return START_NOT_STICKY
        Log.d(TAG, "onStartCommand received for Download ID: $id")

        if (activeDownloads == 0) {
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("YtDownloader")
                .setContentText(getString(R.string.notification_downloading_queue))
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .build()
            startForeground(NOTIFICATION_ID, notification)
            Log.d(TAG, "Started foreground service notification.")
        }
        activeDownloads++

        serviceScope.launch {
            Log.d(TAG, "Launching download coroutine for ID: $id")
            executeDownload(id)
            activeDownloads--
            Log.d(TAG, "Download execution finished. Active downloads remaining: $activeDownloads")
            if (activeDownloads <= 0) {
                Log.d(TAG, "No more active downloads. Stopping service.")
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun executeDownload(id: String) {
        val sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        var currentLocale = sharedPreferences.getString("PREF_APP_LANG", null)
            ?: androidx.appcompat.app.AppCompatDelegate.getApplicationLocales().get(0)?.language
            ?: java.util.Locale.getDefault().language
        if (currentLocale == "iw") {
            currentLocale = "he"
        }
        val subLang = if (currentLocale == "he") "he,iw" else currentLocale
        val item = DownloadManager.downloadItems.find { it.id == id } ?: run {
            Log.e(TAG, "Could not find DownloadItem with ID: $id")
            return
        }

        Log.d(TAG, "====== STARTING DOWNLOAD: ${item.title} ======")
        Log.d(TAG, "URL: ${item.url}")
        Log.d(TAG, "Is Audio: ${item.isAudio}, Quality: ${item.selectedQuality}")
        Log.d(TAG, "Download Subtitles: ${item.downloadSubtitles}, Embed Metadata: ${item.embedMetadata}")
        Log.d(TAG, "File Path: ${item.filePath}")

        val cookieFile = File(getExternalFilesDir(null), "cookies.txt")
        var success = false

        for (attempt in 1..3) {
            if (item.status == DownloadStatus.PAUSED) {
                Log.d(TAG, "Download paused by user. Aborting loop.")
                break
            }

            try {
                Log.d(TAG, "--- Attempt $attempt of 3 ---")

                val request = YoutubeDLRequest(item.url).apply {
                    addOption("-o", item.filePath)
                    addOption("--no-playlist")
                    addOption("--verbose")

                    if (item.skipSsl) {
                        Log.d(TAG, "Option added: skip SSL")
                        addOption("--no-check-certificate")
                    }
                    if (cookieFile.exists()) {
                        Log.d(TAG, "Option added: using cookies")
                        addOption("--cookies", cookieFile.absolutePath)
                    }

                    if (item.embedMetadata) {
                        Log.d(TAG, "Option added: embed metadata & thumbnail")
                        addOption("--add-metadata")
                        addOption("--embed-thumbnail")
                    }

                    if (item.downloadSubtitles && !item.isAudio) {
                        Log.d(TAG, "Option added: download, convert to srt, and embed subtitles")
                        addOption("--write-sub")
                        addOption("--write-auto-sub")
                        addOption("--sub-langs", subLang)
                        addOption("--convert-subs", "srt")
                        addOption("--embed-subs")
                    }

                    if (!item.startTime.isNullOrBlank() || !item.endTime.isNullOrBlank()) {
                        val start = if (!item.startTime.isNullOrBlank()) "*${item.startTime}" else ""
                        val end = if (!item.endTime.isNullOrBlank()) "-${item.endTime}" else ""
                        Log.d(TAG, "Option added: section download $start$end")
                        addOption("--download-sections", "$start$end")
                        addOption("--force-keyframes-at-cuts")
                    }

                    if (item.isAudio) {
                        addOption("--extract-audio")
                        addOption("--audio-format", "mp3")

                        val audioQualityArg = when (item.selectedQuality) {
                            "best" -> "bestaudio/best"
                            "medium" -> "bestaudio[abr<=128]/best"
                            "low" -> "bestaudio[abr<=64]/best"
                            else -> "bestaudio/best"
                        }
                        Log.d(TAG, "Option added: audio format $audioQualityArg")
                        addOption("-f", audioQualityArg)
                    } else {
                        val videoQualityArg = when (item.selectedQuality) {
                            "best" -> "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best"
                            "1080p" -> "bestvideo[height<=1080][ext=mp4]+bestaudio[ext=m4a]/best[height<=1080][ext=mp4]/best"
                            "720p" -> "bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]/best[height<=720][ext=mp4]/best"
                            "480p" -> "bestvideo[height<=480][ext=mp4]+bestaudio[ext=m4a]/best[height<=480][ext=mp4]/best"
                            "360p" -> "bestvideo[height<=360][ext=mp4]+bestaudio[ext=m4a]/best[height<=360][ext=mp4]/best"
                            else -> "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best"
                        }
                        Log.d(TAG, "Option added: video format $videoQualityArg")
                        addOption("-f", videoQualityArg)
                    }
                }

                Log.d(TAG, "Executing YoutubeDL request...")
                val response = YoutubeDL.getInstance().execute(request, id) { progress, _, _ ->
                    if (progress >= 0) {
                        item.progress = progress.toInt()
                        DownloadManager.updateItemAndSave(this@DownloadService, item)
                    }
                }

                Log.d(TAG, "Download completed successfully! Output log:\n${response.out}")
                item.status = DownloadStatus.COMPLETED
                success = true
                break

            } catch (e: Exception) {
                Log.e(TAG, "CRASH/ERROR on attempt $attempt!")
                Log.e(TAG, "Exception message: ${e.message}")
                Log.e(TAG, "Stacktrace: ", e)

                if (item.status == DownloadStatus.PAUSED) {
                    Log.d(TAG, "Caught exception, but status is PAUSED. Breaking loop.")
                    break
                }

                if (attempt == 3) {
                    Log.e(TAG, "All 3 attempts failed. Setting status to FAILED.")
                    item.status = DownloadStatus.FAILED
                } else {
                    Log.d(TAG, "Waiting 2 seconds before retrying...")
                    Thread.sleep(2000)
                }
            }
        }

        if (!success && item.status == DownloadStatus.FAILED) {
            Log.d(TAG, "Sending failure notification to user.")
            sendFailureNotification(item.title)
        }

        Log.d(TAG, "Updating item status and checking queue.")
        DownloadManager.updateItemAndSave(this, item)
        DownloadManager.checkQueue(this)
        Log.d(TAG, "====== FINISHED PROCESSING: ${item.title} ======")
    }

    private fun sendFailureNotification(title: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title_download_failed))
            .setContentText(getString(R.string.notification_desc_download_failed, title))
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setAutoCancel(true)
            .build()

        notificationManager?.notify(title.hashCode(), notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}