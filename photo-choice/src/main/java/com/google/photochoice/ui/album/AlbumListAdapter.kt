package com.google.photochoice.ui.album

import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.photochoice.R
import com.google.photochoice.data.model.Album

/**
 * 相册下拉面板列表 Adapter。
 *
 * 第 0 项固定为"所有照片/视频"。
 */
class AlbumListAdapter(
    var currentBucketId: String?,
    var allPhotosName: String,
    var allPhotosCount: Int,
    var allPhotosCoverUri: String?,
    private val onItemClick: (bucketId: String?, displayName: String) -> Unit
) : ListAdapter<Album, AlbumListAdapter.AlbumVH>(DiffCallback) {

    override fun getItemCount(): Int = super.getItemCount() + 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlbumVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_album, parent, false)
        return AlbumVH(view)
    }

    override fun onBindViewHolder(holder: AlbumVH, position: Int) {
        if (position == 0) {
            holder.bind(
                name = allPhotosName,
                count = allPhotosCount,
                coverUri = allPhotosCoverUri,
                isSelected = currentBucketId == null,
                onClick = { onItemClick(null, allPhotosName) }
            )
        } else {
            val album = getItem(position - 1)
            holder.bind(
                name = album.displayName.ifBlank { 
                    holder.itemView.context.getString(R.string.photochoice_album_default)
                },
                count = album.mediaCount,
                coverUri = album.coverUri,
                isSelected = currentBucketId == album.bucketId,
                onClick = { onItemClick(album.bucketId, album.displayName) }
            )
        }
    }

    class AlbumVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivCover: AppCompatImageView = itemView.findViewById(R.id.ivAlbumCover)
        private val tvName: AppCompatTextView = itemView.findViewById(R.id.tvAlbumName)
        private val ivCheckmark: AppCompatImageView = itemView.findViewById(R.id.ivCheckmark)

        fun bind(
            name: String,
            count: Int,
            coverUri: String?,
            isSelected: Boolean,
            onClick: () -> Unit
        ) {
            tvName.text = formatAlbumTitle(itemView, name, count)
            ivCheckmark.visibility = if (isSelected) View.VISIBLE else View.GONE

            if (coverUri != null) {
                // 裁剪交给 ImageView scaleType="centerCrop"(item_album.xml)，不用 Glide 变换 →
                // 封面若是动图(GIF/动画WebP)照常播放且不崩。
                // RGB_565 + 软件解码：与网格缩略图同一套省内存策略（见 MediaGridAdapter.bind 注释）
                // DiskCacheStrategy.NONE：本地媒体源文件即"缓存"，仅内存缓存，未命中从源文件解码
                Glide.with(ivCover)
                    .load(coverUri.toUri())
                    .override(120)
                    .format(DecodeFormat.PREFER_RGB_565)
                    .disallowHardwareConfig()
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .placeholder(R.color.photochoice_thumbnail_placeholder)
                    .into(ivCover)
            } else {
                Glide.with(ivCover).clear(ivCover)
                ivCover.setImageDrawable(null)
                ivCover.setBackgroundResource(R.color.photochoice_thumbnail_placeholder)
            }
            itemView.setOnClickListener { onClick() }
        }
    }

    companion object {
        /** 标题 + 张数同一行，如「所有照片 （1000张）」；张数使用次要色。 */
        private fun formatAlbumTitle(itemView: View, name: String, count: Int): CharSequence {
            val countPart = itemView.context.getString(
                R.string.photochoice_album_count_in_parens,
                count
            )
            val full = "$name $countPart"
            val countStart = full.length - countPart.length
            return SpannableString(full).apply {
                setSpan(
                    ForegroundColorSpan(
                        ContextCompat.getColor(itemView.context, R.color.photochoice_album_count)
                    ),
                    countStart,
                    full.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        val DiffCallback = object : DiffUtil.ItemCallback<Album>() {
            override fun areItemsTheSame(oldItem: Album, newItem: Album): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Album, newItem: Album): Boolean =
                oldItem == newItem
        }
    }
}
