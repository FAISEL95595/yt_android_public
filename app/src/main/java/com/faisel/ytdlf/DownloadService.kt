package com.faisel.ytdlf

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
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

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra("ID") ?: return START_NOT_STICKY

        if (activeDownloads == 0) {
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("YtDownloader")
                .setContentText(getString(R.string.notification_downloading_queue))
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .build()
            startForeground(NOTIFICATION_ID, notification)
        }
        activeDownloads++

        serviceScope.launch {
            executeDownload(id)
            activeDownloads--
            if (activeDownloads <= 0) stopSelf()
        }
        return START_STICKY
    }

    private fun executeDownload(id: String) {
        val item = DownloadManager.downloadItems.find { it.id == id } ?: return
        val cookieFile = File(getExternalFilesDir(null), "cookies.txt")

        var success = false

        for (attempt in 1..3) {
            if (item.status == DownloadStatus.PAUSED) break

            try {
                val request = YoutubeDLRequest(item.url).apply {
                    addOption("-o", item.filePath)
                    addOption("--no-playlist")

                    if (item.skipSsl) addOption("--no-check-certificate")
                    if (cookieFile.exists()) addOption("--cookies", cookieFile.absolutePath)

                    if (item.embedMetadata) {
                        addOption("--add-metadata")
                        addOption("--embed-thumbnail")
                    }

                    if (!item.startTime.isNullOrBlank() || !item.endTime.isNullOrBlank()) {
                        val start = if (!item.startTime.isNullOrBlank()) "*${item.startTime}" else ""
                        val end = if (!item.endTime.isNullOrBlank()) "-${item.endTime}" else ""
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
                        addOption("-f", videoQualityArg)
                    }
                }

                YoutubeDL.getInstance().execute(request, id) { progress, _, _ ->
                    if (progress >= 0) {
                        item.progress = progress.toInt()
                        DownloadManager.updateItemAndSave(this@DownloadService, item)
                    }
                }

                item.status = DownloadStatus.COMPLETED
                success = true
                break

            } catch (e: Exception) {
                if (item.status == DownloadStatus.PAUSED) break

                if (attempt == 3) {
                    item.status = DownloadStatus.FAILED
                } else {
                    Thread.sleep(2000)
                }
            }
        }

        if (!success && item.status == DownloadStatus.FAILED) {
            sendFailureNotification(item.title)
        }

        DownloadManager.updateItemAndSave(this, item)
        DownloadManager.checkQueue(this)
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