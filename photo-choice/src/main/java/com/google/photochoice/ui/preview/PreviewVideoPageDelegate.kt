package com.google.photochoice.ui.preview

import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.google.photochoice.R
import androidx.media3.ui.PlayerView
import com.google.photochoice.databinding.ItemPreviewVideoBinding

@OptIn(UnstableApi::class)
internal class PreviewVideoPageDelegate(
    private val host: PreviewPageDelegateHost,
    private val uri: String,
) : PreviewPageDelegate {

    private var binding: ItemPreviewVideoBinding? = null
    private var exoPlayer: ExoPlayer? = null
    private var onSingleTap: (() -> Unit)? = null
    private var chromeVisible = true
    private var gestureDetector: GestureDetector? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?): View {
        val itemBinding = ItemPreviewVideoBinding.inflate(inflater, null, false)
        binding = itemBinding

        val player = ExoPlayer.Builder(host.context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = false
        }

        val playButtonSize = host.resources.getDimensionPixelSize(
            R.dimen.photochoice_preview_play_button_size
        )
        PreviewPlayerViewChrome.configure(itemBinding.root, playButtonSize)
        itemBinding.root.player = player
        exoPlayer = player

        // PlayerView（ViewGroup）不会自动调用 performClick()，
        // 通过 GestureDetector + OnTouchListener 检测单击。
        // 优先取 host 注入的回调 → 消除 Activity 查找 Fragment 时序问题 → 再 fallback setOnSingleTapListener
        val tapAction = host.onSingleTap ?: { onSingleTap?.invoke() }
        val gd = GestureDetector(host.context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                tapAction()
                return true
            }
        })
        gestureDetector = gd
        itemBinding.root.setOnTouchListener { _, event ->
            gd.onTouchEvent(event)
            false // 不消费事件，让 PlayerView 内部继续处理
        }

        applyChromeImmediate(chromeVisible)
        return itemBinding.root
    }

    override fun onViewCreated() = Unit

    override fun onPause() {
        exoPlayer?.pause()
    }

    override fun onDestroyView() {
        exoPlayer?.release()
        exoPlayer = null
        gestureDetector = null
        binding = null
    }

    override fun setOnSingleTapListener(listener: () -> Unit) {
        onSingleTap = listener
    }

    override fun setOnZoomInteractionListener(listener: (zoomed: Boolean, scaling: Boolean) -> Unit) =
        Unit

    override fun syncChromeFromHost(fullscreen: Boolean, animated: Boolean) {
        chromeVisible = !fullscreen
        val playerView = binding?.root ?: return
        if (chromeVisible) {
            playerView.showController()
        } else {
            playerView.hideController()
        }
    }

    override fun isZoomed(): Boolean = false

    override fun pauseVideo() {
        exoPlayer?.pause()
    }

    override fun playVideo() {
        exoPlayer?.play()
    }

    override fun isVideoPage(): Boolean = true

    override fun isMotionPhotoPage(): Boolean = false

    private fun applyChromeImmediate(visible: Boolean) {
        val playerView: PlayerView = binding?.root ?: return
        if (visible) playerView.showController() else playerView.hideController()
    }
}
