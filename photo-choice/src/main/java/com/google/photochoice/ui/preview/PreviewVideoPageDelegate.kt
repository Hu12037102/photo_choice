package com.google.photochoice.ui.preview

import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
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
    private var gestureDetector: GestureDetector? = null

    /**
     * 控制器显隐：只跟播放状态走，不跟 chrome/全屏走。
     * 播放中（含缓冲）隐藏整个控制器（无按钮无蒙层）；暂停/停止/播放结束显示中央播放 icon。
     * 不用 isPlaying 判断——缓冲时 isPlaying=false 会闪现播放按钮，
     * 用 playWhenReady + playbackState 表达"用户意图在播放"。
     */
    private val playStateListener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            updateControllerForPlayState()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            updateControllerForPlayState()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?): View {
        val itemBinding = ItemPreviewVideoBinding.inflate(inflater, null, false)
        binding = itemBinding

        val player = ExoPlayer.Builder(host.context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = false
            addListener(playStateListener)
        }

        val playButtonSize = host.resources.getDimensionPixelSize(
            R.dimen.photochoice_preview_play_button_size
        )
        PreviewPlayerViewChrome.configure(itemBinding.root, playButtonSize)
        itemBinding.root.player = player
        exoPlayer = player

        // PlayerView（ViewGroup）不会自动调用 performClick()，
        // 通过 GestureDetector + OnTouchListener 检测单击。
        // 单击不干预播放状态：任何状态下都只切换顶栏/底栏 chrome。
        // 播放状态仅受"播放完成"或外部因素（滑走页面/退后台 onPause）影响。
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
            // 消费事件：掐断 PlayerView 内建的"点击切换控制器显隐"(performClick→toggleControllerVisibility)，
            // 否则播放中单击会把播放/暂停按钮弹出来。控制器显隐只由播放状态驱动(updateControllerForPlayState)。
            // 中央播放按钮是子 View，点击由它自己消费，不经过此监听，不受影响。
            true
        }

        // 初始为未播放态 → 显示中央播放 icon
        updateControllerForPlayState()
        return itemBinding.root
    }

    override fun onViewCreated() = Unit

    override fun onPause() {
        exoPlayer?.pause()
    }

    override fun onDestroyView() {
        exoPlayer?.removeListener(playStateListener)
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
        // 播放 icon 只由播放状态驱动（updateControllerForPlayState），
        // 不再跟随顶栏/底栏 chrome 显隐——暂停态切全屏时按钮仍保留，否则无法恢复播放。
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

    /** 用户意图在播放（含缓冲）：playWhenReady 且未结束/未失败。 */
    private fun isPlayIntent(): Boolean {
        val player = exoPlayer ?: return false
        return player.playWhenReady &&
            player.playbackState != Player.STATE_ENDED &&
            player.playbackState != Player.STATE_IDLE
    }

    /** 播放状态 → 控制器显隐的唯一收口。 */
    private fun updateControllerForPlayState() {
        val playerView: PlayerView = binding?.root ?: return
        if (isPlayIntent()) {
            playerView.hideController()
        } else {
            playerView.showController()
        }
    }
}
