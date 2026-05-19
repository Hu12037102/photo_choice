package com.google.photochoice.ui.preview

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.photochoice.data.model.MediaFile
import com.google.photochoice.databinding.ItemPreviewVideoBinding

class PreviewPageFragment : Fragment() {

    private var zoomableImageView: ZoomableImageView? = null
    private var videoRoot: View? = null
    private var exoPlayer: ExoPlayer? = null
    private var isVideo = false
    private var onSingleTap: (() -> Unit)? = null
    private var onZoomInteraction: ((zoomed: Boolean, scaling: Boolean) -> Unit)? = null

    companion object {
        private const val ARG_URI = "uri"
        private const val ARG_TYPE = "type"

        fun newInstance(mediaFile: MediaFile): PreviewPageFragment {
            return PreviewPageFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_URI, mediaFile.uri)
                    putString(ARG_TYPE, mediaFile.type.name)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val uri = arguments?.getString(ARG_URI) ?: return FrameLayout(requireContext())
        val type = arguments?.getString(ARG_TYPE) ?: "IMAGE"
        isVideo = type == "VIDEO"
        return if (isVideo) createVideoView(uri) else createImageView(uri)
    }

    private fun createImageView(uri: String): View {
        val view = ZoomableImageView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        Glide.with(view)
            .load(uri)
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .fitCenter()
            .into(view)
        zoomableImageView = view
        onSingleTap?.let { view.onSingleTapListener = it }
        attachZoomListeners()
        return view
    }

    private fun createVideoView(uri: String): View {
        val binding = ItemPreviewVideoBinding.inflate(
            LayoutInflater.from(requireContext()), requireView() as ViewGroup?, false
        )
        val player = ExoPlayer.Builder(requireContext()).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }
        binding.playerView.player = player
        exoPlayer = player
        videoRoot = binding.root
        videoRoot?.setOnClickListener { onSingleTap?.invoke() }
        return binding.root
    }

    fun setOnSingleTapListener(listener: () -> Unit) {
        onSingleTap = listener
        zoomableImageView?.onSingleTapListener = listener
        videoRoot?.setOnClickListener { listener() }
    }

    fun setOnZoomInteractionListener(listener: (zoomed: Boolean, scaling: Boolean) -> Unit) {
        onZoomInteraction = listener
        attachZoomListeners()
    }

    private fun attachZoomListeners() {
        val imageView = zoomableImageView ?: return
        val listener = onZoomInteraction ?: return
        imageView.onZoomStateChanged = { zoomed -> listener(zoomed, false) }
        imageView.onScalingChanged = { scaling -> listener(imageView.isZoomed, scaling) }
    }

    fun isZoomed(): Boolean = if (isVideo) false else zoomableImageView?.isZoomed == true

    fun pauseVideo() {
        exoPlayer?.pause()
    }

    fun playVideo() {
        exoPlayer?.play()
    }

    override fun onPause() {
        super.onPause()
        exoPlayer?.pause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        exoPlayer?.release()
        exoPlayer = null
        zoomableImageView = null
        videoRoot = null
    }
}
