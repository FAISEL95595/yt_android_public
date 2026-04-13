package com.faisel.ytdlf

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.faisel.ytdlf.databinding.ActivityMainBinding
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.navigation.NavigationView
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class PlaylistItem(val id: String, val title: String, var isChecked: Boolean = true)

class PlaylistDialogAdapter(private val items: List<PlaylistItem>) : RecyclerView.Adapter<PlaylistDialogAdapter.ViewHolder>() {
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cbSelect: CheckBox = view.findViewById(R.id.cbSelect)
        val ivThumbnail: ImageView = view.findViewById(R.id.ivThumbnail)
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_playlist_dialog, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvTitle.text = item.title
        holder.cbSelect.isChecked = item.isChecked

        val thumbUrl = "https://i.ytimg.com/vi/${item.id}/hqdefault.jpg"
        Glide.with(holder.itemView.context)
            .load(thumbUrl)
            .placeholder(android.R.drawable.ic_menu_gallery)
            .into(holder.ivThumbnail)

        holder.itemView.setOnClickListener {
            item.isChecked = !item.isChecked
            holder.cbSelect.isChecked = item.isChecked
        }
        holder.cbSelect.setOnCheckedChangeListener { _, isChecked ->
            item.isChecked = isChecked
        }
    }

    override fun getItemCount() = items.size
}

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var drawerLayout: DrawerLayout
    private val TAG = "YTDL_DEBUG"

    private var videoQualitiesDisplay = arrayOf("הכי טוב שקיים (Best)", "1080p", "720p", "480p", "360p")
    private var videoQualitiesValues = arrayOf("best", "1080p", "720p", "480p", "360p")
    private val audioQualitiesDisplay = arrayOf("הכי טוב שקיים (Best)", "בינוני (Medium)", "נמוך (Low)")
    private val audioQualitiesValues = arrayOf("best", "medium", "low")

    private var currentThumbnailUrl: String? = null

    private val permissionsLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val storageGranted = permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: true
        if (storageGranted) {
            fetchInfo()
        } else {
            Toast.makeText(this, getString(R.string.toast_permission_denied), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        drawerLayout = findViewById(R.id.drawerLayout)
        val topAppBar = findViewById<MaterialToolbar>(R.id.topAppBar)
        val navigationView = findViewById<NavigationView>(R.id.navigationView)

        val toggle = ActionBarDrawerToggle(this, drawerLayout, topAppBar, 0, 0)
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_login -> startActivity(Intent(this, LoginActivity::class.java))
                R.id.nav_settings -> startActivity(Intent(this, SettingsActivity::class.java))
            //    R.id.nav_update -> UpdateHelper(this).checkForUpdates(isManual = true)
                R.id.nav_donate -> openKoFi()
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try { YoutubeDL.getInstance().updateYoutubeDL(this@MainActivity) } catch (e: Exception) {}
        }

        binding.btnAnalyze.setOnClickListener {
            val url = binding.etUrl.text.toString()
            if (url.isEmpty()) return@setOnClickListener
            checkPermissionsAndStart()
        }

        binding.btnStartDownload.setOnClickListener {
            startSingleDownload()
        }

        binding.btnGoToDownloads.setOnClickListener {
            startActivity(Intent(this, DownloadsActivity::class.java))
        }

        setupFormatToggle()

        intent.getStringExtra("SHARED_URL")?.let { binding.etUrl.setText(it) }
        checkAndShowDonateDialog()
     //   UpdateHelper(this).checkForUpdates()
     //   val navUpdateItem = navigationView.menu.findItem(R.id.nav_update)
     //   UpdateHelper(this).checkUpdateBadge(navUpdateItem)
    }

    private fun setupFormatToggle() {
        val prefs = getSharedPreferences("AppPreferences", MODE_PRIVATE)

        binding.toggleFormat.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val isAudio = checkedId == R.id.btnAudio
                binding.cbMetadata.visibility = if (isAudio) View.VISIBLE else View.GONE

                val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, if (isAudio) audioQualitiesDisplay else videoQualitiesDisplay)
                binding.spinnerQuality.adapter = adapter

                val defaultQuality = if (isAudio) prefs.getString("PREF_DEFAULT_AUDIO_QUALITY", "best") else prefs.getString("PREF_DEFAULT_VIDEO_QUALITY", "best")
                val valuesArray = if (isAudio) audioQualitiesValues else videoQualitiesValues
                binding.spinnerQuality.setSelection(valuesArray.indexOf(defaultQuality).takeIf { it >= 0 } ?: 0)
            }
        }
    }

    private fun checkPermissionsAndStart() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionsLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            fetchInfo()
        }
    }

    private fun fetchInfo() {
        val url = binding.etUrl.text.toString()
        binding.tvStatus.visibility = View.VISIBLE
        val skipSsl = binding.cbSkipSsl.isChecked
        binding.tvStatus.text = if (skipSsl) getString(R.string.status_analyzing_no_ssl) else getString(R.string.status_analyzing)

        currentThumbnailUrl = null

        val isPlaylistOrChannel = url.contains("list=") || url.contains("@") || url.contains("/videos")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val request = YoutubeDLRequest(url)
                if (skipSsl) request.addOption("--no-check-certificate")

                val cookieFile = File(getExternalFilesDir(null), "cookies.txt")
                if (cookieFile.exists()) {
                    request.addOption("--cookies", cookieFile.absolutePath)
                } else {
                    request.addOption("--extractor-args", "youtube:player-client=tv,web_embedded")
                }

                if (isPlaylistOrChannel) {
                    request.addOption("--flat-playlist")
                    request.addOption("--dump-json")

                    val response = YoutubeDL.getInstance().execute(request)
                    val lines = response.out.split("\n").filter { it.isNotBlank() }

                    val items = mutableListOf<PlaylistItem>()
                    for (line in lines) {
                        try {
                            val json = JSONObject(line)
                            if (json.has("title") && json.has("id")) {
                                val title = json.getString("title")
                                val id = json.getString("id")
                                if (title != "null" && title.isNotBlank()) {
                                    items.add(PlaylistItem(id, title))
                                }
                            }
                        } catch (e: Exception) {}
                    }

                    withContext(Dispatchers.Main) {
                        binding.tvStatus.visibility = View.GONE
                        if (items.isNotEmpty()) {
                            showPlaylistBottomSheet(items, "פלייליסט")
                        } else {
                            Toast.makeText(this@MainActivity, getString(R.string.toast_playlist_empty), Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    val info = YoutubeDL.getInstance().getInfo(request)

                    val formats = info.formats
                    if (formats != null) {
                        val availableHeights = formats
                            .filter { !it.vcodec.isNullOrEmpty() && it.vcodec != "none" && it.height > 0 }
                            .map { it.height }
                            .distinct()
                            .sortedDescending()

                        if (availableHeights.isNotEmpty()) {
                            val dynamicDisplays = mutableListOf("הכי טוב שקיים (Best)")
                            val dynamicValues = mutableListOf("best")

                            availableHeights.forEach { h ->
                                dynamicDisplays.add("${h}p")
                                dynamicValues.add("${h}p")
                            }

                            videoQualitiesDisplay = dynamicDisplays.toTypedArray()
                            videoQualitiesValues = dynamicValues.toTypedArray()
                        }
                    }

                    withContext(Dispatchers.Main) {
                        binding.tvStatus.visibility = View.GONE
                        binding.cardVideoInfo.visibility = View.VISIBLE
                        binding.tvVideoTitle.text = info.title

                        currentThumbnailUrl = info.thumbnail
                        Glide.with(this@MainActivity).load(currentThumbnailUrl).into(binding.ivThumbnail)

                        val prefs = getSharedPreferences("AppPreferences", MODE_PRIVATE)
                        val isDefaultAudio = prefs.getBoolean("PREF_DEFAULT_AUDIO", false)
                        binding.toggleFormat.check(if (isDefaultAudio) R.id.btnAudio else R.id.btnVideo)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fetch info failed", e)
                withContext(Dispatchers.Main) {
                    val msg = e.message ?: ""
                    binding.tvStatus.visibility = View.GONE
                    if (msg.contains("Sign in") || msg.contains("bot")) {
                        showBotBlockDialog()
                    } else {
                        Toast.makeText(this@MainActivity, getString(R.string.toast_load_error), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun showBotBlockDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.bot_block_title))
            .setMessage(getString(R.string.bot_block_msg))
            .setPositiveButton(getString(R.string.btn_login_now)) { _, _ ->
                startActivity(Intent(this, LoginActivity::class.java))
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun showPlaylistBottomSheet(items: List<PlaylistItem>, playlistTitle: String) {
        val bottomSheetDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_playlist, null)
        bottomSheetDialog.setContentView(view)

        val rvPlaylist = view.findViewById<RecyclerView>(R.id.rvPlaylist)
        val cbSelectAll = view.findViewById<CheckBox>(R.id.cbSelectAll)
        val btnDownload = view.findViewById<Button>(R.id.btnDownloadSelected)
        val toggleFormatSheet = view.findViewById<MaterialButtonToggleGroup>(R.id.toggleFormatSheet)

        val prefs = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        val isDefaultAudio = prefs.getBoolean("PREF_DEFAULT_AUDIO", false)
        toggleFormatSheet.check(if (isDefaultAudio) R.id.btnAudioSheet else R.id.btnVideoSheet)

        val adapter = PlaylistDialogAdapter(items)
        rvPlaylist.layoutManager = LinearLayoutManager(this)
        rvPlaylist.adapter = adapter

        cbSelectAll.setOnCheckedChangeListener { _, isChecked ->
            items.forEach { it.isChecked = isChecked }
            adapter.notifyDataSetChanged()
        }

        btnDownload.setOnClickListener {
            val isAudio = toggleFormatSheet.checkedButtonId == R.id.btnAudioSheet
            val skipSsl = binding.cbSkipSsl.isChecked
            val selected = items.filter { it.isChecked }

            val quality = if (isAudio) prefs.getString("PREF_DEFAULT_AUDIO_QUALITY", "best") else prefs.getString("PREF_DEFAULT_VIDEO_QUALITY", "best")
            val playlistId = UUID.randomUUID().toString()

            selected.forEach { item ->
                val thumbUrl = "https://i.ytimg.com/vi/${item.id}/hqdefault.jpg"
                DownloadManager.enqueueDownload(
                    context = this,
                    url = "https://www.youtube.com/watch?v=${item.id}",
                    title = item.title,
                    isAudio = isAudio,
                    skipSsl = skipSsl,
                    folderName = playlistTitle,
                    playlistId = playlistId,
                    quality = quality ?: "best",
                    metadata = false,
                    startTime = null,
                    endTime = null,
                    thumbnailUrl = thumbUrl
                )
            }

            bottomSheetDialog.dismiss()
            binding.etUrl.text?.clear()
            Toast.makeText(this, getString(R.string.toast_added_multiple_to_queue, selected.size), Toast.LENGTH_SHORT).show()
        }

        bottomSheetDialog.show()
    }

    private fun startSingleDownload() {
        val url = binding.etUrl.text.toString()
        val isAudio = binding.toggleFormat.checkedButtonId == R.id.btnAudio
        val skipSsl = binding.cbSkipSsl.isChecked
        val title = binding.tvVideoTitle.text.toString()
        val embedMetadata = binding.cbMetadata.isChecked

        val startTime = binding.etStartTime.text.toString().takeIf { it.isNotBlank() }
        val endTime = binding.etEndTime.text.toString().takeIf { it.isNotBlank() }

        val qualityIndex = binding.spinnerQuality.selectedItemPosition
        val valuesArray = if (isAudio) audioQualitiesValues else videoQualitiesValues
        val selectedQuality = valuesArray.getOrElse(qualityIndex) { "best" }

        DownloadManager.enqueueDownload(this, url, title, isAudio, skipSsl, null, null, selectedQuality, embedMetadata, startTime, endTime, currentThumbnailUrl)

        binding.etUrl.text?.clear()
        binding.cardVideoInfo.visibility = View.GONE

        binding.etStartTime.text?.clear()
        binding.etEndTime.text?.clear()
        binding.cbMetadata.isChecked = false

        Toast.makeText(this, getString(R.string.toast_added_to_queue), Toast.LENGTH_SHORT).show()
    }

    private fun openKoFi() {
        val url = "https://ko-fi.com/faisel"
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.toast_no_browser), Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkAndShowDonateDialog() {
        val prefs = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        val neverShow = prefs.getBoolean("PREF_DONATE_NEVER_SHOW", false)

        if (neverShow) return

        val lastNagTime = prefs.getLong("PREF_LAST_DONATE_NAG_TIME", 0)
        val currentTime = System.currentTimeMillis()
        val twoDaysInMillis = 2 * 24 * 60 * 60 * 1000L

        if (currentTime - lastNagTime > twoDaysInMillis) {
            showDonateDialog()
            prefs.edit { putLong("PREF_LAST_DONATE_NAG_TIME", currentTime) }
        }
    }

    private fun showDonateDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.donate_title))
            .setMessage(getString(R.string.donate_msg))
            .setPositiveButton(getString(R.string.donate_yes)) { _, _ ->
                openKoFi()
            }
            .setNegativeButton(getString(R.string.donate_later)) { _, _ ->
            }
            .setNeutralButton(getString(R.string.donate_never)) { _, _ ->
                getSharedPreferences("AppPreferences", MODE_PRIVATE)
                    .edit { putBoolean("PREF_DONATE_NEVER_SHOW", true) }
                Toast.makeText(this, getString(R.string.toast_donate_never_again), Toast.LENGTH_SHORT).show()
            }
            .setCancelable(false)
            .show()
    }
}
