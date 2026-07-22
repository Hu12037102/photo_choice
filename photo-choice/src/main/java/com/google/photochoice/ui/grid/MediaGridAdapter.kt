package com.google.photochoice.ui.grid

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.photochoice.R
import com.google.photochoice.data.model.MediaFile
import com.google.photochoice.data.motion.BadgeState
import com.google.photochoice.data.motion.MotionPhotoDecision
import com.google.photochoice.data.motion.MotionPhotoDetector
import com.google.photochoice.data.motion.MotionPhotoHeuristics
import com.google.photochoice.data.motion.MotionPhotoIndexStore
import com.google.photochoice.util.CompressExportPolicy
import java.util.Locale
import androidx.core.net.toUri
import com.bumptech.glide.Priority
import androidx.core.view.isVisible

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
                holder.refreshLivePhotoIndicator(item)
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
        private val gifBadge: View = itemView.findViewById(R.id.gifBadge)
        private val ivVideoIndicator: AppCompatImageView =
            itemView.findViewById(R.id.ivVideoIndicator)
        private val tvDuration: AppCompatTextView = itemView.findViewById(R.id.tvDuration)
        private val touchTarget: View = itemView.findViewById(R.id.checkboxTouchArea)

        fun bind(mediaItem: MediaFile) {
            // 不用 Glide 的 centerCrop(BitmapTransformation)：它会对动画 WebP/AVIF 解出的
            // AnimatedImageDrawable 强转 Bitmap 而崩，也会让 GIF 无法逐帧播放。
            // 裁剪交给 ImageView 的 android:scaleType="centerCrop"(见 item_media_grid.xml) 在绘制层完成——
            // 对静态图 / GIF / 动画 WebP 一视同仁，既不崩又保留动画。override 仍对静态图降采样省内存。
            // RGB_565：缩略图无需 alpha，每像素 4B→2B 内存减半；PREFER 语义下带透明的图自动回退 8888。
            // disallowHardwareConfig：P+ 上硬件位图会忽略 565 偏好，须强制软件解码 565 才生效；
            // 软件位图还能进 Glide bitmap pool 复用，降低反复分配。动图路径不受这两项影响。
            Glide.with(ivThumbnail)
                .load(mediaItem.uri.toUri())
                .override(THUMBNAIL_PX)
                .format(DecodeFormat.PREFER_RGB_565)
                .disallowHardwareConfig()
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
            bindGifIndicator(mediaItem)
            itemView.setOnClickListener { onItemClick(mediaItem) }
            touchTarget.setOnClickListener { onCheckboxClick(mediaItem) }
        }

        fun bindLivePhotoIndicator(mediaItem: MediaFile) {
            // GIF 属动图但非 Live Photo，不参与 motion 判定，避免与 GIF 角标重叠及无谓的异步嗅探
            if (mediaItem.type != MediaFile.MediaType.IMAGE ||
                CompressExportPolicy.isGifImage(mediaItem)) {
                setBadgeVisible(livePhotoBadge, visible = false, animate = false)
                return
            }
            val state = MotionPhotoDecision.resolve(
                isMotionFlag = mediaItem.isMotionPhoto,
                memoryResult = MotionPhotoDetector.memoryResult(mediaItem.id),
                indexResult = MotionPhotoIndexStore.query(mediaItem),
                heuristicGuess = MotionPhotoHeuristics.guess(mediaItem)
            )
            when (state) {
                BadgeState.CONFIRMED_MOTION,
                BadgeState.HEURISTIC_MOTION -> setBadgeVisible(livePhotoBadge, visible = true, animate = false)

                BadgeState.CONFIRMED_NOT -> setBadgeVisible(livePhotoBadge, visible = false, animate = false)

                BadgeState.UNKNOWN -> {
                    setBadgeVisible(livePhotoBadge, visible = false, animate = false)
                    onRequestMotionEnrich?.invoke(mediaItem)
                }
            }
        }

        /** payload 刷新入口(嗅探回调后)：与首帧不同，允许淡入/淡出动画。 */
        fun refreshLivePhotoIndicator(mediaItem: MediaFile) {
            // GIF 不参与 motion 判定，异步补判(payload)路径同样排除，杜绝与 GIF 角标重叠
            if (mediaItem.type != MediaFile.MediaType.IMAGE ||
                CompressExportPolicy.isGifImage(mediaItem)) {
                setBadgeVisible(livePhotoBadge, visible = false, animate = false)
                return
            }
            val state = MotionPhotoDecision.resolve(
                isMotionFlag = mediaItem.isMotionPhoto,
                memoryResult = MotionPhotoDetector.memoryResult(mediaItem.id),
                indexResult = MotionPhotoIndexStore.query(mediaItem),
                heuristicGuess = MotionPhotoHeuristics.guess(mediaItem)
            )
            val shouldShow = state == BadgeState.CONFIRMED_MOTION ||
                state == BadgeState.HEURISTIC_MOTION
            setBadgeVisible(livePhotoBadge, shouldShow, animate = true)
        }

        /** 统一显隐入口。animate=true 时用 alpha 淡入/淡出(150ms)。
         *  参数化 [view]，供 Live / GIF 等多个角标复用同一套淡入淡出逻辑。 */
        private fun setBadgeVisible(view: View, visible: Boolean, animate: Boolean) {
            view.animate().cancel()
            if (visible) {
                if (view.isVisible && view.alpha == 1f) return
                if (animate) {
                    view.alpha = 0f
                    view.visibility = View.VISIBLE
                    view.animate()
                        .alpha(1f).setDuration(BADGE_FADE_MS)
                        .setInterpolator(DecelerateInterpolator()).start()
                } else {
                    view.alpha = 1f
                    view.visibility = View.VISIBLE
                }
            } else {
                if (view.visibility != View.VISIBLE) return
                if (animate) {
                    view.animate()
                        .alpha(0f).setDuration(BADGE_FADE_MS)
                        .setInterpolator(DecelerateInterpolator())
                        .withEndAction { view.visibility = View.GONE }.start()
                } else {
                    view.alpha = 1f
                    view.visibility = View.GONE
                }
            }
        }

        private fun bindVideoIndicator(mediaItem: MediaFile) {
            if (mediaItem.type == MediaFile.MediaType.VIDEO) {
                setBadgeVisible(livePhotoBadge, visible = false, animate = false)
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

        /**
         * 绑定 GIF 角标。
         *
         * 判定依据 [CompressExportPolicy.isGifImage]：type==IMAGE 且 MIME 为 image/gif 或扩展名 .gif。
         * GIF 属 IMAGE，与视频角标互斥；Live 角标入口([bindLivePhotoIndicator]/[refreshLivePhotoIndicator])
         * 已排除 GIF，故三者不会重叠。判定为同步零 I/O，无需 payload 局部刷新。
         */
        private fun bindGifIndicator(mediaItem: MediaFile) {
            setBadgeVisible(
                gifBadge,
                visible = CompressExportPolicy.isGifImage(mediaItem),
                animate = false
            )
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

        /**
         * 缩略图解码目标边长（px）。网格单元在 1080p/4 列下约 270px，160 略有上采但
         * 观感仍清晰；配合 RGB_565，单张缩略图内存约为 200px/ARGB_8888 方案的 1/3。
         * 若产品反馈偏糊，可回调至 200。
         */
        const val THUMBNAIL_PX = 160

        /** 角标淡入/淡出时长。 */
        private const val BADGE_FADE_MS = 150L

        val DiffCallback = object : DiffUtil.ItemCallback<MediaFile>() {
            override fun areItemsTheSame(oldItem: MediaFile, newItem: MediaFile): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: MediaFile, newItem: MediaFile): Boolean =
                oldItem == newItem
        }
    }
}
