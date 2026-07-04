package com.google.photochoice.sample

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class DemoResultAdapter(
    private val onItemClick: (Int) -> Unit,
) : RecyclerView.Adapter<DemoResultAdapter.ThumbViewHolder>() {

    private val uris = mutableListOf<Uri>()

    fun submitList(items: List<Uri>) {
        uris.clear()
        uris.addAll(items)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = uris.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThumbViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_demo_result_thumb, parent, false)
        return ThumbViewHolder(view, onItemClick)
    }

    override fun onBindViewHolder(holder: ThumbViewHolder, position: Int) {
        holder.bind(uris[position])
    }

    class ThumbViewHolder(
        itemView: View,
        onItemClick: (Int) -> Unit,
    ) : RecyclerView.ViewHolder(itemView) {

        private val imageThumb: ImageView = itemView.findViewById(R.id.imageThumb)
        private val iconPlay: ImageView = itemView.findViewById(R.id.iconPlay)

        init {
            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(position)
                }
            }
        }

        fun bind(uri: Uri) {
            val context = itemView.context
            val video = uri.isVideo(context)
            iconPlay.visibility = if (video) View.VISIBLE else View.GONE
            // 裁剪交给 ImageView scaleType="centerCrop"(item_demo_result_thumb.xml)，
            // 不用 Glide 变换 → 动图照常播放且不崩
            Glide.with(imageThumb)
                .load(uri)
                .into(imageThumb)
        }
    }
}
