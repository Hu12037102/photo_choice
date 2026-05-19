package com.google.photochoice.ui.preview

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import androidx.annotation.OptIn
import androidx.fragment.app.Fragment
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.photochoice.R
import com.google.photochoice.data.model.MediaFile
import com.google.photochoice.databinding.ItemPreviewVideoBinding

class PreviewPageFragment : Fragment() {

    private var zoomableImageView: ZoomableImageView? = null
    private var videoBinding: ItemPreviewVideoBinding? = null
    private var exoPlayer: ExoPlayer? = null
    private var isVideo = false
    private var onSingleTap: (() -> Unit)? = null
    private var onZoomInteraction: ((zoomed: Boolean, scaling: Boolean) -> Unit)? = null

    private var chromeVisible = true

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
        val type = arguments?.getString(ARG_TYPE) ?: MediaFile.MediaType.IMAGE.name
        isVideo = type == MediaFile.MediaType.VIDEO.name
        return if (isVideo) createVideoView(uri, inflater) else createImageView(uri)
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

    @OptIn(UnstableApi::class)
    private fun createVideoView(uri: String, inflater: LayoutInflater): View {
        val binding = ItemPreviewVideoBinding.inflate(inflater, null, false)
        videoBinding = binding

        val player = ExoPlayer.Builder(requireContext()).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = false
        }

        configurePlayerViewChrome(binding.root)
        binding.root.player = player
        exoPlayer = player

        // PlayerView 自带控制器接管播放/暂停按钮；空白区域单击转发给宿主切换全屏。
        binding.root.setOnClickListener { onSingleTap?.invoke() }

        applyVideoChromeImmediate(chromeVisible)
        return binding.root
    }

    /**
     * 关闭 PlayerView 自带控制器中不需要的子视图，仅保留中央播放/暂停按钮。
     */
    @OptIn(UnstableApi::class)
    private fun configurePlayerViewChrome(playerView: PlayerView) {
        playerView.apply {
            setShowFastForwardButton(false)
            setShowRewindButton(false)
            setShowNextButton(false)
            setShowPreviousButton(false)
            setShowShuffleButton(false)
            setShowSubtitleButton(false)
            setShowVrButton(false)
            controllerHideOnTouch = false
            controllerAutoShow = true
            setControllerShowTimeoutMs(0)
        }
        // 隐藏底部进度条、时间文本、设置等组件，仅保留中央播放/暂停按钮。
        intArrayOf(
            androidx.media3.ui.R.id.exo_bottom_bar,
            androidx.media3.ui.R.id.exo_minimal_controls,
            androidx.media3.ui.R.id.exo_basic_controls,
            androidx.media3.ui.R.id.exo_extra_controls,
            androidx.media3.ui.R.id.exo_progress,
            androidx.media3.ui.R.id.exo_progress_placeholder,
            androidx.media3.ui.R.id.exo_position,
            androidx.media3.ui.R.id.exo_duration,
            androidx.media3.ui.R.id.exo_settings,
            androidx.media3.ui.R.id.exo_prev,
            androidx.media3.ui.R.id.exo_next,
        ).forEach { id -> playerView.findViewById<View?>(id)?.visibility = View.GONE }
        // 控制器在首帧布局后才完成 inflate，延后设置播放按钮尺寸。
        applyCenterPlayPauseButtonSize(playerView)
    }

    private fun applyCenterPlayPauseButtonSize(playerView: PlayerView) {
        val playPause = playerView.findViewById<ImageButton>(androidx.media3.ui.R.id.exo_play_pause)
            ?: return
        val size = resources.getDimensionPixelSize(R.dimen.photochoice_preview_play_button_size)
        playPause.layoutParams = playPause.layoutParams.apply {
            width = size
            height = size
        }
        playPause.scaleType = ImageView.ScaleType.FIT_CENTER
        playPause.adjustViewBounds = true
    }

    fun isVideoPage(): Boolean = isVideo

    fun setOnSingleTapListener(listener: () -> Unit) {
        onSingleTap = listener
        zoomableImageView?.onSingleTapListener = listener
        videoBinding?.root?.setOnClickListener { listener() }
    }

    fun setOnZoomInteractionListener(listener: (zoomed: Boolean, scaling: Boolean) -> Unit) {
        onZoomInteraction = listener
        attachZoomListeners()
    }

    /**
     * 与 [PreviewActivity] 顶栏/底栏同步：全屏时隐藏 PlayerView 自带控制器。
     */
    @OptIn(UnstableApi::class)
    fun syncChromeFromHost(fullscreen: Boolean, animated: Boolean) {
        if (!isVideo) return
        chromeVisible = !fullscreen
        val playerView = videoBinding?.root ?: return
        if (chromeVisible) {
            playerView.showController()
        } else {
            if (animated) playerView.hideController() else playerView.hideController()
        }
    }

    @OptIn(UnstableApi::class)
    private fun applyVideoChromeImmediate(visible: Boolean) {
        val playerView = videoBinding?.root ?: return
        if (visible) playerView.showController() else playerView.hideController()
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
        exoPlayer?.release()
        exoPlayer = null
        zoomableImageView = null
        videoBinding = null
        super.onDestroyView()
    }
}
