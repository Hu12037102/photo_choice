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
import com.google.photochoice.util.PhotoChoiceLog

@OptIn(UnstableApi::class)
internal class PreviewVideoPageDelegate(
    private val host: PreviewPageDelegateHost,
    private val uri: String,
) : PreviewPageDelegate {

    private var binding: ItemPreviewVideoBinding? = null

    /**
     * ExoPlayer 按页面可见性惰性创建、离开即释放，而非跟随 View 生命周期。
     *
     * 原因：ViewPager2 会预创建相邻页的 View，若在 [onCreateView] 建播放器，
     * `offscreenPageLimit` 为 N 时最多有 2N+1 个 ExoPlayer 同时持有编解码器与缓冲区，
     * 低端机内存吃紧，极端情况会耗尽硬件解码器实例导致后续页播放失败。
     * 只让当前页持有播放器，把并存实例数压到 1。
     */
    private var exoPlayer: ExoPlayer? = null

    /**
     * 释放播放器时暂存的播放进度（毫秒），重新可见时续播。
     * 不持久化到 savedInstanceState——预览页是临时浏览场景，进程重建后从头开始可接受。
     */
    private var savedPositionMs = 0L

    /** 释放前是否处于播放意图，用于回到本页时恢复播放/暂停态。 */
    private var savedPlayWhenReady = false

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

        val playButtonSize = host.resources.getDimensionPixelSize(
            R.dimen.photochoice_preview_play_button_size
        )
        PreviewPlayerViewChrome.configure(itemBinding.root, playButtonSize)
        // 此处不建播放器：等 onPageVisibilityChanged(true) 再建，见 exoPlayer 字段说明。
        // PlayerView 无 player 时显示空白背景，与视频首帧未解码时的观感一致。

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

    /**
     * 页面可见性驱动播放器的生命周期，把并存实例数压到 1。
     *
     * visible=true → 建播放器并恢复到释放前的进度；visible=false → 存进度后立即释放。
     * 由 [PreviewPageFragment] 的 onResume/onPause 触发（ViewPager2 采用
     * BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT，只有当前页会 resume）。
     */
    override fun onPageVisibilityChanged(visible: Boolean) {
        if (visible) {
            acquirePlayer()
        } else {
            releasePlayer()
        }
    }

    override fun onDestroyView() {
        releasePlayer()
        gestureDetector = null
        binding = null
    }

    /**
     * 创建播放器并挂到 PlayerView，恢复释放前的进度与播放意图。
     *
     * 幂等：已持有实例时直接返回，避免 onResume 重入造成实例泄漏。
     * View 已销毁（binding 为空）时不创建——没有承载它的 PlayerView。
     */
    private fun acquirePlayer() {
        if (exoPlayer != null) return
        val itemBinding = binding ?: return

        val player = ExoPlayer.Builder(host.context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            // 续播到释放前的位置；首次进入时 savedPositionMs 为 0，等价于从头播
            if (savedPositionMs > 0L) {
                seekTo(savedPositionMs)
            }
            playWhenReady = savedPlayWhenReady
            addListener(playStateListener)
        }
        itemBinding.root.player = player
        exoPlayer = player
        PhotoChoiceLog.d(TAG) { "player acquired at ${savedPositionMs}ms uri=$uri" }
        updateControllerForPlayState()
    }

    /**
     * 存下进度与播放意图后释放播放器，解除 PlayerView 绑定。
     *
     * 播放结束时进度已到片尾，续播会立刻又结束，故归零让下次从头播——
     * 与用户"回到这页再点播放"的预期一致。
     */
    private fun releasePlayer() {
        val player = exoPlayer ?: return
        savedPositionMs = if (player.playbackState == Player.STATE_ENDED) {
            0L
        } else {
            player.currentPosition
        }
        savedPlayWhenReady = player.playWhenReady
        player.removeListener(playStateListener)
        player.release()
        exoPlayer = null
        binding?.root?.player = null
        PhotoChoiceLog.d(TAG) { "player released at ${savedPositionMs}ms uri=$uri" }
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
        val player = exoPlayer
        if (player != null) {
            player.pause()
        } else {
            // 播放器已释放（本页不可见）：只落到暂存意图，下次 acquire 时生效
            savedPlayWhenReady = false
        }
    }

    override fun playVideo() {
        val player = exoPlayer
        if (player != null) {
            player.play()
        } else {
            // 外部在本页不可见时请求播放：记下意图，等页面可见建播放器时自动起播
            savedPlayWhenReady = true
        }
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

    private companion object {
        const val TAG = "PreviewVideoPage"
    }
}
