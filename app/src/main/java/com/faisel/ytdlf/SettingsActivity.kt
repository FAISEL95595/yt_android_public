package com.faisel.ytdlf

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var tvCurrentSaveLocation: TextView

    private lateinit var videoQualitiesDisplay: Array<String>
    private val videoQualitiesValues = arrayOf("best", "1080p", "720p", "480p", "360p")

    private lateinit var audioQualitiesDisplay: Array<String>
    private val audioQualitiesValues = arrayOf("best", "medium", "low")

    private val languagesDisplay = arrayOf("English", "עברית", "Español", "Русский", "Français", "العربية")
    private val languagesTags = arrayOf("en", "he", "es", "ru", "fr", "ar")

    private val folderPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                val folderPath = RealPathUtil.getRealPath(this, uri) ?: uri.toString()
                sharedPreferences.edit { putString("PREF_SAVE_LOCATION", folderPath) }
                tvCurrentSaveLocation.text = folderPath
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE)

        videoQualitiesDisplay = arrayOf(getString(R.string.quality_best), "1080p", "720p", "480p", "360p")
        audioQualitiesDisplay = arrayOf(getString(R.string.quality_best), getString(R.string.quality_medium), getString(R.string.quality_low))

        val toolbar = findViewById<MaterialToolbar>(R.id.settingsToolbar)
        toolbar.setNavigationOnClickListener { finish() }

        val sliderConcurrent = findViewById<Slider>(R.id.sliderConcurrent)
        val tvConcurrentLabel = findViewById<TextView>(R.id.tvConcurrentLabel)

        val savedConcurrent = sharedPreferences.getInt("PREF_MAX_CONCURRENT", 2)
        sliderConcurrent.value = savedConcurrent.toFloat()
        tvConcurrentLabel.text = getString(R.string.label_max_concurrent, savedConcurrent)

        sliderConcurrent.addOnChangeListener { _, value, _ ->
            val intValue = value.toInt()
            tvConcurrentLabel.text = getString(R.string.label_max_concurrent, intValue)
            sharedPreferences.edit { putInt("PREF_MAX_CONCURRENT", intValue) }
        }

        tvCurrentSaveLocation = findViewById(R.id.tvCurrentSaveLocation)
        val savedLocation = sharedPreferences.getString("PREF_SAVE_LOCATION", getExternalFilesDir(null)?.absolutePath ?: "")
        tvCurrentSaveLocation.text = savedLocation

        val btnChooseLocation = findViewById<Button>(R.id.btnChooseLocation)
        btnChooseLocation.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            folderPickerLauncher.launch(Intent.createChooser(intent, getString(R.string.chooser_save_folder)))
        }

        val switchDarkMode = findViewById<SwitchMaterial>(R.id.switchDarkMode)
        val isDark = sharedPreferences.getBoolean("PREF_DARK_MODE", false)
        switchDarkMode.isChecked = isDark

        switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit { putBoolean("PREF_DARK_MODE", isChecked) }
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
        }

        val spinnerLanguage = findViewById<Spinner>(R.id.spinnerLanguage)
        val langAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, languagesDisplay)
        langAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerLanguage.adapter = langAdapter

        var currentLocale = AppCompatDelegate.getApplicationLocales().get(0)?.language
            ?: sharedPreferences.getString("PREF_APP_LANG", "en") ?: "en"
        if (currentLocale == "iw") currentLocale = "he"
        val langIndex = languagesTags.indexOf(currentLocale).takeIf { it >= 0 } ?: 0
        spinnerLanguage.setSelection(langIndex)

        var isLangInit = true
        spinnerLanguage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (isLangInit) {
                    isLangInit = false
                    return
                }
                val selectedLang = languagesTags[position]
                if (selectedLang != currentLocale) {
                    sharedPreferences.edit { putString("PREF_APP_LANG", selectedLang) }
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(selectedLang))
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val spinnerVideoQuality = findViewById<Spinner>(R.id.spinnerVideoQuality)
        val spinnerAudioQuality = findViewById<Spinner>(R.id.spinnerAudioQuality)

        val videoAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, videoQualitiesDisplay)
        videoAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerVideoQuality.adapter = videoAdapter

        findViewById<Button>(R.id.btnOpenShareSettings).setOnClickListener {
            startActivity(Intent(this, DefaultSettingsActivity::class.java))
        }

        val audioAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, audioQualitiesDisplay)
        audioAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerAudioQuality.adapter = audioAdapter

        val savedVideoQuality = sharedPreferences.getString("PREF_DEFAULT_VIDEO_QUALITY", "best")
        val savedAudioQuality = sharedPreferences.getString("PREF_DEFAULT_AUDIO_QUALITY", "best")

        spinnerVideoQuality.setSelection(videoQualitiesValues.indexOf(savedVideoQuality).takeIf { it >= 0 } ?: 0)
        spinnerAudioQuality.setSelection(audioQualitiesValues.indexOf(savedAudioQuality).takeIf { it >= 0 } ?: 0)

        spinnerVideoQuality.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                sharedPreferences.edit { putString("PREF_DEFAULT_VIDEO_QUALITY", videoQualitiesValues[position]) }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        spinnerAudioQuality.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                sharedPreferences.edit { putString("PREF_DEFAULT_AUDIO_QUALITY", audioQualitiesValues[position]) }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val rgDefaultFormat = findViewById<RadioGroup>(R.id.rgDefaultFormat)
        val savedFormat = sharedPreferences.getString("PREF_DEFAULT_FORMAT", "video")
        if (savedFormat == "audio") rgDefaultFormat.check(R.id.rbDefaultAudio) else rgDefaultFormat.check(R.id.rbDefaultVideo)

        rgDefaultFormat.setOnCheckedChangeListener { _, checkedId ->
            val format = if (checkedId == R.id.rbDefaultAudio) "audio" else "video"
            sharedPreferences.edit { putString("PREF_DEFAULT_FORMAT", format) }
            sharedPreferences.edit { putBoolean("PREF_DEFAULT_AUDIO", format == "audio") }
        }
    }
}