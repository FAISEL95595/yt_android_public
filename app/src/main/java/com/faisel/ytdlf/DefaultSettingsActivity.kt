package com.faisel.ytdlf

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.RadioGroup
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.switchmaterial.SwitchMaterial

class DefaultSettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private val videoQualitiesValues = arrayOf("best", "1080p", "720p", "480p", "360p")
    private val audioQualitiesValues = arrayOf("best", "medium", "low")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_default_settings)

        prefs = getSharedPreferences("AppPreferences", MODE_PRIVATE)

        findViewById<MaterialToolbar>(R.id.defaultSettingsToolbar).setNavigationOnClickListener { finish() }

        setupSwitch(R.id.swAutoDownload, "PREF_AUTO_DOWNLOAD", false)
        setupSwitch(R.id.swPlaylistSubfolder, "PREF_PLAYLIST_SUBFOLDER", true)
        setupSwitch(R.id.swSkipSslDefault, "PREF_SKIP_SSL_DEFAULT", true)

        val rgFormat = findViewById<RadioGroup>(R.id.rgDefaultFormatGlobal)
        val isAudio = prefs.getBoolean("PREF_DEFAULT_AUDIO", false)
        rgFormat.check(if (isAudio) R.id.rbAudioGlobal else R.id.rbVideoGlobal)
        rgFormat.setOnCheckedChangeListener { _, id ->
            prefs.edit { putBoolean("PREF_DEFAULT_AUDIO", id == R.id.rbAudioGlobal) }
        }

        setupQualitySpinner(R.id.spinnerDefaultVideoQuality, R.array.video_qualities_display, videoQualitiesValues, "PREF_DEFAULT_VIDEO_QUALITY")
        setupQualitySpinner(R.id.spinnerDefaultAudioQuality, R.array.audio_qualities_display, audioQualitiesValues, "PREF_DEFAULT_AUDIO_QUALITY")
    }

    private fun setupSwitch(id: Int, key: String, default: Boolean) {
        val sw = findViewById<SwitchMaterial>(id)
        sw.isChecked = prefs.getBoolean(key, default)
        sw.setOnCheckedChangeListener { _, checked -> prefs.edit { putBoolean(key, checked) } }
    }

    private fun setupQualitySpinner(spinnerId: Int, displayArrayRes: Int, values: Array<String>, key: String) {
        val spinner = findViewById<Spinner>(spinnerId)
        val adapter = ArrayAdapter.createFromResource(this, displayArrayRes, android.R.layout.simple_spinner_item)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        val savedVal = prefs.getString(key, "best")
        spinner.setSelection(values.indexOf(savedVal).coerceAtLeast(0))
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: android.view.View?, pos: Int, p3: Long) {
                prefs.edit { putString(key, values[pos]) }
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
    }
}