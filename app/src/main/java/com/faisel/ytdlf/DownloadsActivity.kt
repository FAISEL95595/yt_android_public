package com.faisel.ytdlf

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yausername.youtubedl_android.YoutubeDL
import java.io.File

class DownloadsActivity : AppCompatActivity(), DownloadsAdapter.DownloadActionListener {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: DownloadsAdapter
    private lateinit var btnClearAll: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_downloads)

        recyclerView = findViewById(R.id.recyclerViewDownloads)
        btnClearAll = findViewById(R.id.btnClearAll)

        adapter = DownloadsAdapter(this)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        adapter.updateData(DownloadManager.downloadItems)

        DownloadManager.onItemAdded = { runOnUiThread { adapter.updateData(DownloadManager.downloadItems) } }
        DownloadManager.onItemUpdated = { runOnUiThread { adapter.updateData(DownloadManager.downloadItems) } }

        setupSwipeToDelete()

        btnClearAll.setOnClickListener {
            DownloadManager.clearCompletedAndFailed(this)
            adapter.updateData(DownloadManager.downloadItems)
            Toast.makeText(this, getString(R.string.toast_cleared_completed_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSwipeToDelete() {
        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val item = adapter.getItemAtPosition(position)

                if (item is DownloadItem) {
                    if (item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.PENDING) {
                        Toast.makeText(this@DownloadsActivity, getString(R.string.toast_cannot_delete_active), Toast.LENGTH_SHORT).show()
                        adapter.notifyItemChanged(position)
                    } else {
                        DownloadManager.removeItem(this@DownloadsActivity, item)
                        adapter.updateData(DownloadManager.downloadItems)
                    }
                } else if (item is DownloadsAdapter.PlaylistHeader) {
                    val canDeleteAll = item.children.all { it.status != DownloadStatus.DOWNLOADING && it.status != DownloadStatus.PENDING }

                    if (canDeleteAll) {
                        item.children.forEach { DownloadManager.removeItem(this@DownloadsActivity, it) }
                        adapter.updateData(DownloadManager.downloadItems)
                        Toast.makeText(this@DownloadsActivity, getString(R.string.toast_playlist_deleted), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@DownloadsActivity, getString(R.string.toast_cannot_delete_active_playlist), Toast.LENGTH_SHORT).show()
                        adapter.notifyItemChanged(position)
                    }
                }
            }
        }

        val itemTouchHelper = ItemTouchHelper(itemTouchHelperCallback)
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    override fun onPauseClicked(item: DownloadItem) {
        item.status = DownloadStatus.PAUSED
        DownloadManager.updateItemAndSave(this, item)
        YoutubeDL.getInstance().destroyProcessById(item.id)
        DownloadManager.checkQueue(this)
    }

    override fun onResumeClicked(item: DownloadItem) {
        DownloadManager.resumeDownload(this, item)
    }

    override fun onCancelClicked(item: DownloadItem) {
        item.status = DownloadStatus.FAILED
        DownloadManager.updateItemAndSave(this, item)
        YoutubeDL.getInstance().destroyProcessById(item.id)
        DownloadManager.checkQueue(this)
    }

    override fun onOpenClicked(item: DownloadItem) {
        val file = File(item.filePath)
        if (!file.exists()) {
            Toast.makeText(this, getString(R.string.toast_file_not_found), Toast.LENGTH_SHORT).show()
            return
        }

        val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
        val mimeType = if (item.filePath.endsWith(".mp3")) "audio/*" else "video/*"

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            startActivity(Intent.createChooser(intent, getString(R.string.open_with)))
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.error_no_app), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onOpenFolderClicked(item: DownloadItem) {
        val file = File(item.filePath)
        val folder = file.parentFile ?: return

        val uri = FileProvider.getUriForFile(this, "${packageName}.provider", folder)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "resource/folder")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            startActivity(intent)
        } catch (e: Exception) {
            val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                startActivity(fallbackIntent)
            } catch (e2: Exception) {
                Toast.makeText(this, getString(R.string.toast_cannot_open_explorer, folder.absolutePath), Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        DownloadManager.onItemAdded = null
        DownloadManager.onItemUpdated = null
    }
}