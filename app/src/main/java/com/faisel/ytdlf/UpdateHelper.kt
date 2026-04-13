package com.faisel.ytdlf

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.MenuItem
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class UpdateHelper(private val activity: AppCompatActivity) {

    private val TAG = "UpdateHelper"

    fun checkForUpdates(isManual: Boolean = false) {
        val prefs = activity.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        val neverShowUpdates = prefs.getBoolean("PREF_NEVER_SHOW_UPDATES", false)

        if (!isManual && neverShowUpdates) return

        activity.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val apiUrl = "https://api.github.com/repos/FAISEL95595/yt_android_public/releases/latest"
                val response = java.net.URL(apiUrl).readText()
                val json = JSONObject(response)

                val latestVersion = json.getString("tag_name")
                val releaseNotes = json.getString("body")

                val assets = json.getJSONArray("assets")
                var downloadUrl = ""
                var universalUrl = ""
                var fallbackUrl = ""

                val supportedAbis = android.os.Build.SUPPORTED_ABIS

                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val fileName = asset.getString("name").lowercase()

                    if (fileName.endsWith(".apk")) {
                        if (fileName.contains("universal")) {
                            universalUrl = asset.getString("browser_download_url")
                        }
                        if (fallbackUrl.isEmpty()) {
                            fallbackUrl = asset.getString("browser_download_url")
                        }

                        var isMatch = false
                        for (abi in supportedAbis) {
                            if (fileName.contains(abi.lowercase())) {
                                downloadUrl = asset.getString("browser_download_url")
                                isMatch = true
                                break
                            }
                        }
                        if (isMatch) break
                    }
                }

                if (downloadUrl.isEmpty()) {
                    downloadUrl = universalUrl.ifEmpty { fallbackUrl }
                }

                val currentVersion = activity.packageManager.getPackageInfo(activity.packageName, 0).versionName

                if (latestVersion != currentVersion && latestVersion != "v$currentVersion" && downloadUrl.isNotEmpty()) {
                    var langCode = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales().get(0)?.language
                        ?: java.util.Locale.getDefault().language
                    if (langCode == "iw") langCode = "he"
                    val localizedNotes = getLocalizedNotes(releaseNotes, langCode)

                    withContext(Dispatchers.Main) {
                        showUpdateBottomSheet(latestVersion, localizedNotes, downloadUrl)
                    }
                } else if (isManual) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(activity, activity.getString(R.string.toast_up_to_date), Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Update check failed", e)
            }
        }
    }

    fun checkUpdateBadge(menuItem: MenuItem) {
        activity.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val apiUrl = "https://api.github.com/repos/FAISEL95595/yt_android_public/releases/latest"
                val response = java.net.URL(apiUrl).readText()
                val json = JSONObject(response)
                val latestVersion = json.getString("tag_name")
                val currentVersion = activity.packageManager.getPackageInfo(activity.packageName, 0).versionName

                if (latestVersion != currentVersion && latestVersion != "v$currentVersion") {
                    withContext(Dispatchers.Main) {
                        val s = SpannableString(menuItem.title)
                        s.setSpan(ForegroundColorSpan(Color.RED), 0, s.length, 0)
                        menuItem.title = s

                        menuItem.iconTintList = ColorStateList.valueOf(Color.RED)
                    }
                }
            } catch (e: Exception) {
            }
        }
    }

    private fun getLocalizedNotes(fullNotes: String?, langCode: String): String {
        if (fullNotes.isNullOrBlank()) return ""
        val startTag = "[$langCode]"
        var startIndex = fullNotes.indexOf(startTag)
        if (startIndex == -1) {
            if (langCode != "en") return getLocalizedNotes(fullNotes, "en")
            return fullNotes.trim()
        }
        startIndex += startTag.length
        val endIndex = fullNotes.indexOf("[", startIndex)
        return if (endIndex == -1) fullNotes.substring(startIndex).trim() else fullNotes.substring(startIndex, endIndex).trim()
    }

    private fun showUpdateBottomSheet(versionName: String, releaseNotes: String, downloadUrl: String) {
        val bottomSheetDialog = BottomSheetDialog(activity)
        val view = activity.layoutInflater.inflate(R.layout.bottom_sheet_update, null)
        bottomSheetDialog.setContentView(view)

        val tvTitle = view.findViewById<TextView>(R.id.tvUpdateTitle)
        val tvDetails = view.findViewById<TextView>(R.id.tvUpdateDetails)
        val btnDownload = view.findViewById<Button>(R.id.btnDownloadUpdate)
        val btnNoThanks = view.findViewById<Button>(R.id.btnNoThanks)
        val btnNeverAgain = view.findViewById<Button>(R.id.btnDontShowAgain)

        tvTitle.text = activity.getString(R.string.update_available_title, versionName)
        tvDetails.text = releaseNotes

        btnDownload.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, downloadUrl.toUri())
            activity.startActivity(intent)
            bottomSheetDialog.dismiss()
        }

        btnNoThanks.setOnClickListener { bottomSheetDialog.dismiss() }

        btnNeverAgain.setOnClickListener {
            activity.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE).edit {
                putBoolean("PREF_NEVER_SHOW_UPDATES", true)
            }
            bottomSheetDialog.dismiss()
            Toast.makeText(activity, activity.getString(R.string.toast_updates_disabled), Toast.LENGTH_SHORT).show()
        }

        bottomSheetDialog.show()
    }
}