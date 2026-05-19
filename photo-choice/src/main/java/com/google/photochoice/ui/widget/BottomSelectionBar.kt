package com.google.photochoice.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.photochoice.R
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
    }

    fun bindState(
        state: SelectionState,
        minSelectCount: Int,
        maxSelectCount: Int
    ) {
        thumbAdapter.submitList(state.items.toList())

        val count = state.count
        val canConfirm = state.canConfirm

        binding.btnDone.apply {
            text = if (count == 0) {
                context.getString(R.string.photochoice_done)
            } else {
                context.getString(R.string.photochoice_done_count, count, maxSelectCount)
            }
            isEnabled = canConfirm
            setBackgroundResource(
                if (canConfirm) R.drawable.bg_btn_done_enabled
                else R.drawable.bg_btn_done_disabled
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
