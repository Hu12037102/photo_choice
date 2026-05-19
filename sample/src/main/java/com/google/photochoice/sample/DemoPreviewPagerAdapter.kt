package com.google.photochoice.sample

import android.media.MediaPlayer
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.VideoView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import androidx.core.view.isVisible

class DemoPreviewPagerAdapter(
    private val uris: List<Uri>,
) : RecyclerView.Adapter<DemoPreviewPagerAdapter.PageViewHolder>() {

    override fun getItemCount(): Int = uris.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_demo_preview_page, parent, false)
        return PageViewHolder(view)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        holder.bind(uris[position])
    }

    override fun onViewRecycled(holder: PageViewHolder) {
        holder.release()
    }

    class PageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val imagePreview: ImageView = itemView.findViewById(R.id.imagePreview)
        private val videoPreview: VideoView = itemView.findViewById(R.id.videoPreview)
        private val btnPlayVideo: ImageView = itemView.findViewById(R.id.btnPlayVideo)
        private var preparedListener: MediaPlayer.OnPreparedListener? = null

        fun bind(uri: Uri) {
            release()
            val context = itemView.context
            if (uri.isVideo(context)) {
                imagePreview.visibility = View.GONE
                videoPreview.visibility = View.VISIBLE
                btnPlayVideo.visibility = View.VISIBLE
                videoPreview.setVideoURI(uri)
                preparedListener = MediaPlayer.OnPreparedListener { mp ->
                    mp.isLooping = true
                    val ratio = mp.videoWidth.toFloat() / mp.videoHeight.coerceAtLeast(1)
                    val width = itemView.width.takeIf { it > 0 } ?: context.resources.displayMetrics.widthPixels
                    videoPreview.layoutParams = videoPreview.layoutParams.apply {
                        height = (width / ratio).toInt()
                    }
                }
                videoPreview.setOnPreparedListener(preparedListener)
                btnPlayVideo.setOnClickListener { startVideo() }
                imagePreview.setOnClickListener(null)
            } else {
                imagePreview.visibility = View.VISIBLE
                videoPreview.visibility = View.GONE
                btnPlayVideo.visibility = View.GONE
                Glide.with(imagePreview).load(uri).fitCenter().into(imagePreview)
                imagePreview.setOnClickListener(null)
                btnPlayVideo.setOnClickListener(null)
            }
        }

        fun startVideo() {
            if (videoPreview.visibility != View.VISIBLE) return
            btnPlayVideo.visibility = View.GONE
            videoPreview.start()
        }

        fun pauseVideo() {
            if (videoPreview.isPlaying) {
                videoPreview.pause()
            }
            if (videoPreview.isVisible) {
                btnPlayVideo.visibility = View.VISIBLE
            }
        }

        fun release() {
            videoPreview.stopPlayback()
            videoPreview.setOnPreparedListener(null)
            preparedListener = null
            btnPlayVideo.setOnClickListener(null)
            Glide.with(imagePreview).clear(imagePreview)
        }
    }
}
