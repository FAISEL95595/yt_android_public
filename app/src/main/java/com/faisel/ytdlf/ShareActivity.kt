package com.faisel.ytdlf

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ShareActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rawText = if (intent?.action == Intent.ACTION_SEND) {
            intent.getStringExtra(Intent.EXTRA_TEXT)
        } else null

        if (rawText.isNullOrEmpty()) {
            finish()
            return
        }

        val urlRegex = "(?i)\\bhttps?://\\S+".toRegex()
        val url = urlRegex.find(rawText)?.value ?: rawText

        val prefs = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        if (prefs.getBoolean("PREF_AUTO_DOWNLOAD", false)) {
            Toast.makeText(this, getString(R.string.toast_analyzing_background), Toast.LENGTH_SHORT).show()
            DownloadManager.processUrlInBackground(applicationContext, url.trim())
            finish()
        } else {
            val bottomSheet = DownloadOptionsBottomSheet.newInstance(url.trim())
            bottomSheet.show(supportFragmentManager, "DownloadOptions")
        }
    }
}