package com.faisel.ytdlf

import android.app.Application
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException

class DLPApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        val isDarkMode = sharedPreferences.getBoolean("PREF_DARK_MODE", false)
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

        try {
            YoutubeDL.getInstance().init(this)
            FFmpeg.getInstance().init(this)
            Aria2c.getInstance().init(this)
        } catch (e: YoutubeDLException) {
            Log.e("DLPApplication", "Failed to initialize libraries", e)
        }
        DownloadManager.loadDownloads(this)
    }
}