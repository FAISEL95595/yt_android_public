package com.faisel.ytdlf

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButtonToggleGroup
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URLDecoder
import java.util.UUID

class DownloadOptionsBottomSheet : BottomSheetDialogFragment() {

    private var videoUrl: String? = null
    private var isPlaylist = false
    private var playlistItems = mutableListOf<PlaylistItem>()
    private var currentTitle = ""
    private var currentThumbnail = ""

    private var qualitiesDisplay = arrayOf<String>()
    private var qualitiesValues = arrayOf<String>()

    companion object {
        fun newInstance(url: String): DownloadOptionsBottomSheet {
            return DownloadOptionsBottomSheet().apply {
                arguments = Bundle().apply { putString("URL", url) }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.bottom_sheet_download_options, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        videoUrl = arguments?.getString("URL")

        if (videoUrl == null) {
            dismiss()
            return
        }
        analyzeLink(view)
    }

    private fun analyzeLink(view: View) {
        val context = requireContext()
        val prefs = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        val skipSsl = prefs.getBoolean("PREF_SKIP_SSL_DEFAULT", true)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = videoUrl!!.trim()
                var decodedUrl = url
                try { decodedUrl = URLDecoder.decode(url, "UTF-8") } catch (e: Exception) {}

                isPlaylist = decodedUrl.contains("list=") || decodedUrl.contains("@") || decodedUrl.contains("/videos") || url.contains("list=")

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

                    playlistItems.clear()
                    for (line in lines) {
                        try {
                            val json = JSONObject(line)
                            if (json.has("title") && json.has("id")) {
                                val title = json.getString("title")
                                val id = json.getString("id")
                                if (title != "null" && title.isNotBlank()) {
                                    playlistItems.add(PlaylistItem(id, title, true))
                                }
                            }
                        } catch (e: Exception) {}
                    }

                    if (playlistItems.isNotEmpty()) {
                        currentTitle = context.getString(R.string.title_playlist_items_found, playlistItems.size)
                        currentThumbnail = "https://i.ytimg.com/vi/${playlistItems[0].id}/hqdefault.jpg"
                    } else {
                        throw Exception("Playlist parsed but is empty")
                    }
                } else {
                    val info = YoutubeDL.getInstance().getInfo(request)
                    currentTitle = info.title ?: "Download"
                    currentThumbnail = info.thumbnail ?: ""
                }

                withContext(Dispatchers.Main) {
                    setupUI(view, prefs)
                }
            } catch (e: Exception) {
                Log.e("YTDL_BOTTOM_SHEET", "Error analyzing link", e)
                withContext(Dispatchers.Main) {
                    val errorMsg = e.message ?: ""
                    if (errorMsg.contains("Sign in") || errorMsg.contains("bot")) {
                        Toast.makeText(context, getString(R.string.toast_bot_block_refreshing), Toast.LENGTH_LONG).show()
                        startActivity(Intent(context, LoginActivity::class.java))
                    } else {
                        Toast.makeText(context, getString(R.string.toast_load_error), Toast.LENGTH_SHORT).show()
                    }
                    dismiss()
                }
            }
        }
    }

    private fun setupUI(view: View, prefs: android.content.SharedPreferences) {
        view.findViewById<View>(R.id.layoutLoading).visibility = View.GONE
        view.findViewById<View>(R.id.layoutContent).visibility = View.VISIBLE

        val tvTitle = view.findViewById<TextView>(R.id.tvSheetTitle)
        val ivThumb = view.findViewById<ImageView>(R.id.ivSheetThumbnail)
        val toggleFormat = view.findViewById<MaterialButtonToggleGroup>(R.id.toggleSheetFormat)
        val spinnerQuality = view.findViewById<Spinner>(R.id.spinnerSheetQuality)
        val btnDownload = view.findViewById<Button>(R.id.btnSheetDownload)

        val cbSheetSubtitles = view.findViewById<CheckBox>(R.id.cbSheetSubtitles)

        val rvPlaylist = view.findViewById<RecyclerView>(R.id.rvSheetPlaylist)
        val cbSelectAll = view.findViewById<CheckBox>(R.id.cbSheetSelectAll)

        tvTitle.text = currentTitle
        Glide.with(this).load(currentThumbnail).into(ivThumb)

        if (isPlaylist) {
            rvPlaylist.visibility = View.VISIBLE
            cbSelectAll.visibility = View.VISIBLE
            btnDownload.text = getString(R.string.btn_download_selected)

            val adapter = PlaylistDialogAdapter(playlistItems)
            rvPlaylist.layoutManager = LinearLayoutManager(requireContext())
            rvPlaylist.adapter = adapter

            cbSelectAll.setOnCheckedChangeListener { _, isChecked ->
                playlistItems.forEach { it.isChecked = isChecked }
                adapter.notifyDataSetChanged()
            }
        }

        val isDefaultAudio = prefs.getBoolean("PREF_DEFAULT_AUDIO", false)
        toggleFormat.check(if (isDefaultAudio) R.id.btnSheetAudio else R.id.btnSheetVideo)

        cbSheetSubtitles.visibility = if (isDefaultAudio) View.GONE else View.VISIBLE
        cbSheetSubtitles.isChecked = prefs.getBoolean("PREF_DOWNLOAD_SUBTITLES_DEFAULT", false)

        fun updateQualitySpinner(isAudio: Boolean) {
            if (isAudio) {
                qualitiesDisplay = arrayOf(getString(R.string.quality_best), getString(R.string.quality_medium), getString(R.string.quality_low))
                qualitiesValues = arrayOf("best", "medium", "low")
            } else {
                qualitiesDisplay = arrayOf(getString(R.string.quality_best), "1080p", "720p", "480p", "360p")
                qualitiesValues = arrayOf("best", "1080p", "720p", "480p", "360p")
            }
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, qualitiesDisplay)
            spinnerQuality.adapter = adapter

            val savedQuality = prefs.getString(if (isAudio) "PREF_DEFAULT_AUDIO_QUALITY" else "PREF_DEFAULT_VIDEO_QUALITY", "best")
            spinnerQuality.setSelection(qualitiesValues.indexOf(savedQuality).coerceAtLeast(0))
        }

        updateQualitySpinner(isDefaultAudio)

        toggleFormat.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val isAudioSelected = checkedId == R.id.btnSheetAudio
                updateQualitySpinner(isAudioSelected)
                cbSheetSubtitles.visibility = if (isAudioSelected) View.GONE else View.VISIBLE
            }
        }

        btnDownload.setOnClickListener {
            val isAudio = toggleFormat.checkedButtonId == R.id.btnSheetAudio
            val quality = qualitiesValues[spinnerQuality.selectedItemPosition]
            val skipSsl = prefs.getBoolean("PREF_SKIP_SSL_DEFAULT", true)
            val downloadSubtitles = cbSheetSubtitles.isChecked

            if (isPlaylist) {
                val selectedItems = playlistItems.filter { it.isChecked }
                if (selectedItems.isEmpty()) {
                    Toast.makeText(context, getString(R.string.toast_select_at_least_one), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val playlistId = UUID.randomUUID().toString()
                selectedItems.forEach { item ->
                    DownloadManager.enqueueDownload(
                        context = requireContext(),
                        url = "https://www.youtube.com/watch?v=${item.id}",
                        title = item.title,
                        isAudio = isAudio,
                        skipSsl = skipSsl,
                        quality = quality,
                        downloadSubtitles = downloadSubtitles,
                        folderName = "Shared Playlist",
                        playlistId = playlistId,
                        thumbnailUrl = "https://i.ytimg.com/vi/${item.id}/hqdefault.jpg"
                    )
                }
                Toast.makeText(context, getString(R.string.toast_added_multiple_videos, selectedItems.size), Toast.LENGTH_SHORT).show()
            } else {
                DownloadManager.enqueueDownload(
                    context = requireContext(),
                    url = videoUrl!!,
                    title = currentTitle,
                    isAudio = isAudio,
                    skipSsl = skipSsl,
                    quality = quality,
                    downloadSubtitles = downloadSubtitles,
                    thumbnailUrl = currentThumbnail
                )
                Toast.makeText(context, getString(R.string.toast_added_to_queue), Toast.LENGTH_SHORT).show()
            }
            dismiss()
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        activity?.finish()
    }
}