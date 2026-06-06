package com.google.photochoice.ui.widget

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Interpolator
import android.view.animation.PathInterpolator
import androidx.appcompat.widget.AppCompatImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.photochoice.R
import com.google.photochoice.config.DesignTokens
import com.google.photochoice.data.model.MediaFile
import com.google.photochoice.databinding.BottomSelectionBarBinding
import com.google.photochoice.util.dp
import com.google.photochoice.viewmodel.SelectionState

/**
 * 底部选中栏。包含：横滑缩略图列表 + 预览按钮 + 完成按钮。
 *
 * 持续可见：选中数=0 时按钮置灰、缩略图为空也保留 bar。
 */
class BottomSelectionBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val binding = BottomSelectionBarBinding.inflate(
        LayoutInflater.from(context), this, true
    )

    private val thumbAdapter = ThumbnailAdapter { mediaFile ->
        onThumbnailClick?.invoke(mediaFile)
    }

    var onPreviewClick: (() -> Unit)? = null
    var onDoneClick: (() -> Unit)? = null
    var onThumbnailClick: ((MediaFile) -> Unit)? = null
    var onVisibleHeightChanged: ((Int) -> Unit)? = null

    private var heightAnimator: ValueAnimator? = null
    private var expanded = false
    private var lastReportedVisibleHeight = -1
    private val heightInterpolator: Interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)

    init {
        binding.rvThumbnails.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = thumbAdapter
            itemAnimator = null
            setHasFixedSize(true)
        }
        binding.btnPreview.setOnClickListener { onPreviewClick?.invoke() }
        binding.btnDone.setOnClickListener { onDoneClick?.invoke() }
        // 拦截点击穿透到下方
        isClickable = true
        isFocusable = true
        clipChildren = true
        clipToPadding = true
    }

    fun bindState(
        state: SelectionState,
        selectCount: Int
    ) {
        thumbAdapter.submitList(state.items.toList()) {
            val lastIndex = thumbAdapter.itemCount - 1
            if (lastIndex >= 0) {
                binding.rvThumbnails.scrollToPosition(lastIndex)
            }
        }
        setExpanded(state.count > 0)

        val count = state.count
        val canConfirm = state.canConfirm

        binding.btnDone.apply {
            text = if (count == 0) {
                context.getString(R.string.photochoice_done)
            } else {
                context.getString(R.string.photochoice_done_count, count, selectCount)
            }
            isEnabled = canConfirm
            setBackgroundResource(
                if (canConfirm) R.drawable.ripple_btn_done_enabled
                else R.drawable.ripple_btn_done_disabled
            )
            setTextColor(
                ContextCompat.getColor(
                    context,
                    if (canConfirm) R.color.photochoice_on_accent
                    else R.color.photochoice_icon_secondary
                )
            )
        }

        binding.btnPreview.apply {
            isEnabled = count > 0
            alpha = if (count > 0) 1.0f else 0.4f
        }
    }

    fun hideImmediately() {
        heightAnimator?.cancel()
        heightAnimator = null
        expanded = false
        alpha = 1f
        visibility = View.GONE
        setBarHeight(0)
        reportVisibleHeight(0)
    }

    fun restoreForSelectionCount(count: Int) {
        setExpanded(count > 0)
    }

    private fun setExpanded(shouldExpand: Boolean) {
        if (expanded == shouldExpand) return
        expanded = shouldExpand
        if (shouldExpand) {
            expand()
        } else {
            collapse()
        }
    }

    private fun expand() {
        val from = if (visibility == View.VISIBLE) height.coerceAtLeast(0) else 0
        visibility = View.VISIBLE
        val target = measureExpandedHeight().coerceAtLeast(1)
        alpha = if (from == 0) 0f else alpha
        setBarHeight(from)
        animateBarHeight(
            from = from,
            to = target,
            duration = DesignTokens.BOTTOM_BAR_ANIM_SHOW_MS,
            interpolator = heightInterpolator
        ) {
            alpha = 1f
            setBarHeight(ViewGroup.LayoutParams.WRAP_CONTENT)
            reportVisibleHeight(target)
        }
    }

    private fun collapse() {
        val measuredHeight = measureExpandedHeight().coerceAtLeast(1)
        val from = if (visibility == View.VISIBLE) {
            height.takeIf { it > 0 } ?: measuredHeight
        } else {
            measuredHeight
        }
        visibility = View.VISIBLE
        setBarHeight(from)
        animateBarHeight(
            from = from,
            to = 0,
            duration = DesignTokens.BOTTOM_BAR_ANIM_DISMISS_MS,
            interpolator = heightInterpolator
        ) {
            alpha = 1f
            visibility = View.GONE
            setBarHeight(0)
            reportVisibleHeight(0)
        }
    }

    private fun measureExpandedHeight(): Int {
        val parentWidth = (parent as? View)?.width ?: 0
        val fallbackWidth = resources.displayMetrics.widthPixels
        val width = width.takeIf { it > 0 } ?: parentWidth.takeIf { it > 0 } ?: fallbackWidth
        val widthSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
        val heightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        measure(widthSpec, heightSpec)
        return measuredHeight
    }

    private fun setBarHeight(heightPx: Int) {
        val lp = layoutParams ?: return
        lp.height = heightPx
        layoutParams = lp
        if (heightPx >= 0) {
            reportVisibleHeight(heightPx)
        }
    }

    private fun reportVisibleHeight(heightPx: Int) {
        if (lastReportedVisibleHeight == heightPx) return
        lastReportedVisibleHeight = heightPx
        onVisibleHeightChanged?.invoke(heightPx)
    }

    private fun animateBarHeight(
        from: Int,
        to: Int,
        duration: Long,
        interpolator: Interpolator,
        onEnd: () -> Unit
    ) {
        heightAnimator?.cancel()
        if (from == to) {
            setBarHeight(to)
            onEnd()
            return
        }
        val startAlpha = alpha
        val endAlpha = if (to > from) 1f else 0.65f
        heightAnimator = ValueAnimator.ofInt(from, to).apply {
            this.duration = duration
            this.interpolator = interpolator
            addUpdateListener { animator ->
                val fraction = animator.animatedFraction
                setBarHeight(animator.animatedValue as Int)
                alpha = startAlpha + ((endAlpha - startAlpha) * fraction)
            }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false

                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                    heightAnimator = null
                }

                override fun onAnimationEnd(animation: Animator) {
                    heightAnimator = null
                    if (!cancelled) onEnd()
                }
            })
            start()
        }
    }

    override fun onDetachedFromWindow() {
        heightAnimator?.cancel()
        heightAnimator = null
        super.onDetachedFromWindow()
    }

    private class ThumbnailAdapter(
        private val onClick: (MediaFile) -> Unit
    ) : ListAdapter<MediaFile, ThumbnailAdapter.VH>(ThumbnailDiff) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val container = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_bottom_thumbnail, parent, false)
            return VH(container)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(getItem(position), onClick)
        }

        class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val image: AppCompatImageView =
                itemView.findViewById(R.id.ivThumbnail)

            fun bind(file: MediaFile, onClick: (MediaFile) -> Unit) {
                Glide.with(image)
                    .load(file.uri)
                    .override(itemView.dp(48))
                    .centerCrop()
                    .into(image)
                itemView.setOnClickListener { onClick(file) }
            }
        }

        object ThumbnailDiff : DiffUtil.ItemCallback<MediaFile>() {
            override fun areItemsTheSame(oldItem: MediaFile, newItem: MediaFile): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: MediaFile, newItem: MediaFile): Boolean =
                oldItem.id == newItem.id
        }
    }
}
