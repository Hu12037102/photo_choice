package com.google.photochoice.ui.grid

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.photochoice.R
import com.google.photochoice.data.model.MediaFile
import java.util.Locale

/**
 * 媒体缩略图网格（Paging 3）。
 *
 * 相机入口请使用 [CameraTileAdapter] + [androidx.recyclerview.widget.ConcatAdapter]，
 * 勿在本 Adapter 中偏移 position / 重写 [getItemCount]。
 */
class MediaGridAdapter(
    private val isSelected: (Long) -> Boolean,
    private val getSelectionOrder: (Long) -> Int,
    private val isFull: () -> Boolean,
    private val onCheckboxClick: (MediaFile) -> Unit,
    private val onItemClick: (MediaFile) -> Unit
) : PagingDataAdapter<MediaFile, MediaGridAdapter.MediaVH>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_media_grid, parent, false)
        return MediaVH(view)
    }

    override fun onBindViewHolder(holder: MediaVH, position: Int) {
        val item = getItem(position) ?: return
        holder.bind(item)
    }

    override fun onBindViewHolder(
        holder: MediaVH,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
        } else {
            val item = getItem(position) ?: return
            holder.bindSelectionState(item)
        }
    }

    fun snapshotMediaList(): List<MediaFile> = snapshot().items

    fun notifyMediaItemChanged(id: Long) {
        val list = snapshot()
        for (i in list.indices) {
            if (list[i]?.id == id) {
                notifyItemChanged(i, PAYLOAD_SELECTION)
                return
            }
        }
    }

    fun notifyAllSelectionChanged() {
        val total = itemCount
        if (total > 0) {
            notifyItemRangeChanged(0, total, PAYLOAD_SELECTION)
        }
    }

    inner class MediaVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivThumbnail: AppCompatImageView = itemView.findViewById(R.id.ivThumbnail)
        private val checkbox: View = itemView.findViewById(R.id.checkbox)
        private val tvOrder: AppCompatTextView = itemView.findViewById(R.id.tvSelectionOrder)
        private val disabledOverlay: View = itemView.findViewById(R.id.disabledOverlay)
        private val ivVideoIndicator: AppCompatImageView =
            itemView.findViewById(R.id.ivVideoIndicator)
        private val tvDuration: AppCompatTextView = itemView.findViewById(R.id.tvDuration)
        private val touchTarget: View = itemView.findViewById(R.id.checkboxTouchArea)

        fun bind(mediaItem: MediaFile) {
            Glide.with(ivThumbnail)
                .load(Uri.parse(mediaItem.uri))
                .override(THUMBNAIL_PX)
                .centerCrop()
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .placeholder(R.color.photochoice_thumbnail_placeholder)
                .error(R.color.photochoice_thumbnail_placeholder)
                .into(ivThumbnail)
            bindSelectionState(mediaItem)
            bindVideoIndicator(mediaItem)
            itemView.setOnClickListener { onItemClick(mediaItem) }
            touchTarget.setOnClickListener { onCheckboxClick(mediaItem) }
        }

        private fun bindVideoIndicator(mediaItem: MediaFile) {
            if (mediaItem.type == MediaFile.MediaType.VIDEO) {
                ivVideoIndicator.visibility = View.VISIBLE
                val seconds = mediaItem.duration / 1000
                tvDuration.text = String.format(
                    Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60
                )
                tvDuration.visibility = View.VISIBLE
            } else {
                ivVideoIndicator.visibility = View.GONE
                tvDuration.visibility = View.GONE
            }
        }

        fun bindSelectionState(mediaItem: MediaFile) {
            val order = getSelectionOrder(mediaItem.id)
            if (order > 0) {
                checkbox.setBackgroundResource(R.drawable.bg_checkbox_selected)
                tvOrder.visibility = View.VISIBLE
                tvOrder.text = String.format(Locale.ROOT, "%d", order)
                disabledOverlay.visibility = View.GONE
            } else {
                checkbox.setBackgroundResource(R.drawable.bg_checkbox_unselected)
                tvOrder.visibility = View.GONE
                disabledOverlay.visibility = if (isFull()) View.VISIBLE else View.GONE
            }
        }
    }

    companion object {
        const val PAYLOAD_SELECTION = "selection"

        private const val THUMBNAIL_PX = 400

        val DiffCallback = object : DiffUtil.ItemCallback<MediaFile>() {
            override fun areItemsTheSame(oldItem: MediaFile, newItem: MediaFile): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: MediaFile, newItem: MediaFile): Boolean =
                oldItem == newItem
        }
    }
}
