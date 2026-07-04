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
import com.google.photochoice.data.motion.MotionPhotoDetector
import java.util.Locale
import androidx.core.net.toUri
import com.bumptech.glide.Priority

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
    private val onItemClick: (MediaFile) -> Unit,
    private val isSingleSelect: Boolean = false,
    private val onRequestMotionEnrich: ((MediaFile) -> Unit)? = null
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
            if (payloads.contains(PAYLOAD_SELECTION)) {
                holder.bindSelectionState(item)
            }
            if (payloads.contains(PAYLOAD_MOTION)) {
                holder.bindLivePhotoIndicator(item)
            }
        }
    }

    // id -> position 索引，避免每次 notify 都全表扫一遍 snapshot()。
    // 在 addOnPagesUpdatedListener 回调里重建，覆盖 append / refresh / drop 所有场景。
    private val idToPosition = HashMap<Long, Int>()

    init {
        addOnPagesUpdatedListener {
            val snap = snapshot()
            idToPosition.clear()
            for (i in snap.indices) {
                val id = snap[i]?.id ?: continue
                idToPosition[id] = i
            }
        }
    }

    fun snapshotMediaList(): List<MediaFile> = snapshot().items

    fun mediaAt(index: Int): MediaFile? {
        if (index !in 0 until itemCount) return null
        return snapshot()[index]
    }

    fun notifyMediaItemChanged(id: Long) {
        notifyItemChanged(id, PAYLOAD_SELECTION)
    }

    private fun notifyItemChanged(id: Long, payload: String) {
        val position = idToPosition[id] ?: return
        if (position in 0 until itemCount) {
            notifyItemChanged(position, payload)
        }
    }

    fun notifyMotionBadges(ids: Set<Long>) {
        ids.forEach { notifyItemChanged(it, PAYLOAD_MOTION) }
    }

    /** 刷新指定媒体下标区间内 Live 角标（相册预热或缓存更新后）。 */
    fun refreshMotionBadgesForMediaRange(firstMediaIndex: Int, lastMediaIndex: Int) {
        if (firstMediaIndex > lastMediaIndex || itemCount <= 0) return
        val end = lastMediaIndex.coerceAtMost(itemCount - 1)
        for (i in firstMediaIndex..end) {
            snapshot()[i]?.id?.let { notifyItemChanged(it, PAYLOAD_MOTION) }
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
        private val livePhotoBadge: View = itemView.findViewById(R.id.livePhotoBadge)
        private val ivVideoIndicator: AppCompatImageView =
            itemView.findViewById(R.id.ivVideoIndicator)
        private val tvDuration: AppCompatTextView = itemView.findViewById(R.id.tvDuration)
        private val touchTarget: View = itemView.findViewById(R.id.checkboxTouchArea)

        fun bind(mediaItem: MediaFile) {
            Glide.with(ivThumbnail)
                .load(mediaItem.uri.toUri())
                .override(THUMBNAIL_PX)
                .centerCrop()
                .skipMemoryCache(false)
                .priority(Priority.LOW)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .placeholder(R.color.photochoice_thumbnail_placeholder)
                .error(R.color.photochoice_thumbnail_placeholder)
                .into(ivThumbnail)
            if (isSingleSelect) {
                checkbox.visibility = View.GONE
                tvOrder.visibility = View.GONE
                disabledOverlay.visibility = View.GONE
                touchTarget.visibility = View.GONE
            } else {
                checkbox.visibility = View.VISIBLE
                touchTarget.visibility = View.VISIBLE
                bindSelectionState(mediaItem)
            }
            bindVideoIndicator(mediaItem)
            bindLivePhotoIndicator(mediaItem)
            itemView.setOnClickListener { onItemClick(mediaItem) }
            touchTarget.setOnClickListener { onCheckboxClick(mediaItem) }
        }

        fun bindLivePhotoIndicator(mediaItem: MediaFile) {
            val isMotion = mediaItem.isMotionPhoto || MotionPhotoDetector.isMotionPhotoCached(mediaItem)
            livePhotoBadge.visibility = if (isMotion) View.VISIBLE else View.GONE
            if (!isMotion &&
                mediaItem.type == MediaFile.MediaType.IMAGE &&
                !MotionPhotoDetector.hasCachedResult(mediaItem.id)
            ) {
                onRequestMotionEnrich?.invoke(mediaItem)
            }
        }

        private fun bindVideoIndicator(mediaItem: MediaFile) {
            if (mediaItem.type == MediaFile.MediaType.VIDEO) {
                livePhotoBadge.visibility = View.GONE
                ivVideoIndicator.visibility = View.VISIBLE
                val seconds = mediaItem.duration / 1000
                tvDuration.text = itemView.context.getString(
                    R.string.photochoice_video_duration,
                    seconds / 60,
                    seconds % 60,
                )
                tvDuration.visibility = View.VISIBLE
            } else {
                ivVideoIndicator.visibility = View.GONE
                tvDuration.visibility = View.GONE
            }
        }

        fun bindSelectionState(mediaItem: MediaFile) {
            if (isSingleSelect) {
                // 单选模式不展示 checkbox / 序号 / 禁用蒙层
                return
            }
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
        const val PAYLOAD_MOTION = "motion"

        const val THUMBNAIL_PX = 200

        val DiffCallback = object : DiffUtil.ItemCallback<MediaFile>() {
            override fun areItemsTheSame(oldItem: MediaFile, newItem: MediaFile): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: MediaFile, newItem: MediaFile): Boolean =
                oldItem == newItem
        }
    }
}
