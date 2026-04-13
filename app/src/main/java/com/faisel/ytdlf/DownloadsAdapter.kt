package com.faisel.ytdlf

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class DownloadsAdapter(
    private val listener: DownloadActionListener
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var displayList: List<Any> = emptyList()
    private val expandedPlaylists = mutableSetOf<String>()

    interface DownloadActionListener {
        fun onPauseClicked(item: DownloadItem)
        fun onResumeClicked(item: DownloadItem)
        fun onCancelClicked(item: DownloadItem)
        fun onOpenClicked(item: DownloadItem)
        fun onOpenFolderClicked(item: DownloadItem)
    }

    fun getItemAtPosition(position: Int): Any {
        return displayList[position]
    }

    fun updateData(fullList: List<DownloadItem>) {
        val newList = mutableListOf<Any>()
        val processedPlaylists = mutableSetOf<String>()

        for (item in fullList) {
            val pId = item.playlistId
            if (pId == null) {
                newList.add(item)
            } else {
                if (!processedPlaylists.contains(pId)) {
                    val folderName = item.folderName ?: ""
                    newList.add(PlaylistHeader(pId, folderName, fullList.filter { it.playlistId == pId }))
                    processedPlaylists.add(pId)
                }
                if (expandedPlaylists.contains(pId)) {
                    newList.add(item)
                }
            }
        }

        val diffCallback = object : DiffUtil.Callback() {
            override fun getOldListSize() = displayList.size
            override fun getNewListSize() = newList.size

            override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean {
                val oldItem = displayList[oldPos]
                val newItem = newList[newPos]
                if (oldItem is PlaylistHeader && newItem is PlaylistHeader) return oldItem.id == newItem.id
                if (oldItem is DownloadItem && newItem is DownloadItem) return oldItem.id == newItem.id
                return false
            }
            override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean = false
            override fun getChangePayload(oldPos: Int, newPos: Int): Any = true
        }

        val diffResult = DiffUtil.calculateDiff(diffCallback)
        displayList = newList
        diffResult.dispatchUpdatesTo(this)
    }

    data class PlaylistHeader(val id: String, val title: String, val children: List<DownloadItem>) {
        val progress: Int get() = if (children.isEmpty()) 0 else children.sumOf { it.progress } / children.size
        val isCompleted: Boolean get() = children.all { it.status == DownloadStatus.COMPLETED }
    }

    class DownloadViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        val tvFormatInfo: TextView = itemView.findViewById(R.id.tvFormatInfo)
        val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        val progressBar: ProgressBar = itemView.findViewById(R.id.progressBar)
        val ivItemThumbnail: ImageView = itemView.findViewById(R.id.ivItemThumbnail)
        val btnPauseResume: Button = itemView.findViewById(R.id.btnPauseResume)
        val btnCancel: Button = itemView.findViewById(R.id.btnCancel)
        val btnOpen: Button = itemView.findViewById(R.id.btnOpen)
    }

    class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvPlaylistTitle: TextView = itemView.findViewById(R.id.tvPlaylistTitle)
        val tvPlaylistStatus: TextView = itemView.findViewById(R.id.tvPlaylistStatus)
        val pbPlaylist: ProgressBar = itemView.findViewById(R.id.pbPlaylist)
        val btnOpenPlaylistFolder: Button = itemView.findViewById(R.id.btnOpenPlaylistFolder)
    }

    override fun getItemViewType(position: Int): Int {
        return if (displayList[position] is PlaylistHeader) 0 else 1
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == 0) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_playlist_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_download, parent, false)
            DownloadViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = displayList[position]
        val context = holder.itemView.context

        if (holder is HeaderViewHolder && item is PlaylistHeader) {
            val displayTitle = item.title.ifEmpty { context.getString(R.string.default_playlist_name) }
            holder.tvPlaylistTitle.text = context.getString(R.string.playlist_title_format, displayTitle, item.children.size)
            holder.pbPlaylist.progress = item.progress

            holder.tvPlaylistStatus.text = if (item.isCompleted) {
                context.getString(R.string.status_completed)
            } else {
                context.getString(R.string.status_in_progress, item.progress)
            }

            if (item.isCompleted) {
                holder.btnOpenPlaylistFolder.visibility = View.VISIBLE
                holder.btnOpenPlaylistFolder.setOnClickListener { listener.onOpenFolderClicked(item.children[0]) }
            } else {
                holder.btnOpenPlaylistFolder.visibility = View.GONE
            }

            holder.itemView.setOnClickListener {
                if (expandedPlaylists.contains(item.id)) expandedPlaylists.remove(item.id)
                else expandedPlaylists.add(item.id)
                updateData(DownloadManager.downloadItems)
            }
        }
        else if (holder is DownloadViewHolder && item is DownloadItem) {
            holder.tvTitle.text = if (item.playlistId != null) context.getString(R.string.tree_branch_prefix, item.title) else item.title
            holder.progressBar.progress = item.progress

            val typeStr = if (item.isAudio) context.getString(R.string.format_audio_with_icon) else context.getString(R.string.format_video_with_icon)
            val qualityStr = if (item.selectedQuality == "best") context.getString(R.string.quality_best_short) else item.selectedQuality
            holder.tvFormatInfo.text = "$typeStr | $qualityStr"

            if (!item.thumbnailUrl.isNullOrEmpty()) {
                holder.ivItemThumbnail.visibility = View.VISIBLE
                Glide.with(context)
                    .load(item.thumbnailUrl)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .into(holder.ivItemThumbnail)
            } else {
                holder.ivItemThumbnail.visibility = View.GONE
            }

            holder.itemView.setOnClickListener(null)
            setupButtons(holder, item, context)
        }
    }

    private fun setupButtons(holder: DownloadViewHolder, item: DownloadItem, context: Context) {
        holder.btnOpen.visibility = View.GONE
        holder.btnPauseResume.visibility = View.VISIBLE
        holder.btnCancel.visibility = View.VISIBLE

        when (item.status) {
            DownloadStatus.DOWNLOADING -> {
                holder.tvStatus.text = context.getString(R.string.status_downloading_progress, item.progress)
                holder.btnPauseResume.text = context.getString(R.string.btn_pause)
            }
            DownloadStatus.PAUSED -> {
                holder.tvStatus.text = context.getString(R.string.status_paused_progress, item.progress)
                holder.btnPauseResume.text = context.getString(R.string.btn_resume)
            }
            DownloadStatus.COMPLETED -> {
                holder.tvStatus.text = context.getString(R.string.status_completed)
                holder.btnPauseResume.visibility = View.GONE
                holder.btnCancel.visibility = View.GONE
                holder.btnOpen.visibility = View.VISIBLE
                holder.btnOpen.text = context.getString(R.string.btn_open)
                holder.progressBar.progress = 100
            }
            DownloadStatus.FAILED -> {
                holder.tvStatus.text = context.getString(R.string.status_failed)
                holder.btnPauseResume.visibility = View.VISIBLE
                holder.btnPauseResume.text = context.getString(R.string.btn_try_again)
            }
            else -> { holder.tvStatus.text = context.getString(R.string.status_pending) }
        }

        holder.btnPauseResume.setOnClickListener {
            if (item.status == DownloadStatus.DOWNLOADING) listener.onPauseClicked(item)
            else listener.onResumeClicked(item)
        }

        holder.btnCancel.setOnClickListener { listener.onCancelClicked(item) }
        holder.btnOpen.setOnClickListener { listener.onOpenClicked(item) }
    }

    override fun getItemCount(): Int = displayList.size
}