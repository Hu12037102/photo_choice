package com.google.photochoice.ui.preview

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.media3.common.Player
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.photochoice.data.motion.MotionPhotoDetector
import com.google.photochoice.data.motion.MotionPhotoVideoResolver
import com.google.photochoice.databinding.ItemPreviewImageBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class PreviewImagePageDelegate(
    private val host: PreviewPageDelegateHost,
    private val uri: String,
) : PreviewPageDelegate {

    private sealed class PrepareState {
        data object Pending : PrepareState()
        data object Preparing : PrepareState()
        data class Ready(val playbackUri: Uri) : PrepareState()
        data object NotMotionPhoto : PrepareState()
        data object Failed : PrepareState()
    }

    private var binding: ItemPreviewImageBinding? = null
    private var onSingleTap: (() -> Unit)? = null
    private var onZoomInteraction: ((zoomed: Boolean, scaling: Boolean) -> Unit)? = null
    private var prepareJob: Job? = null
    private var prepareState: PrepareState = PrepareState.Pending
    private var pendingPlayWhenReady = false
    private var isPlaybackOverlayVisible = false
    private var playbackListenerAttached = false
    /** 首次自动播放已完成，onResume 切回不再重播；长按不受此限制。 */
    private var hasAutoPlayed = false

    private val playbackListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState != Player.STATE_ENDED) return
            binding?.zoomableImage?.post { onPlaybackEnded() }
        }
    }

    // ── PreviewPageDelegate ──────────────────────────────────────────────

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?): View {
        val b = ItemPreviewImageBinding.inflate(inflater, null, false)
        binding = b
        Glide.with(b.zoomableImage)
            .load(uri)
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .fitCenter()
            .into(b.zoomableImage)
        b.zoomableImage.apply {
            onSingleTap?.let { onSingleTapListener = it }
            onLongPressListener = { onLongPress() }
            onLongPressReleaseListener = { onLongPressRelease() }
        }
        attachZoomListeners()
        return b.root
    }

    override fun onViewCreated() {
        startPrepare()
    }

    override fun onPause() {
        endPlayback()
    }

    override fun onDestroyView() {
        prepareJob?.cancel()
        endPlayback()
        binding = null
        prepareState = PrepareState.Pending
        pendingPlayWhenReady = false
        isPlaybackOverlayVisible = false
        hasAutoPlayed = false
    }

    override fun setOnSingleTapListener(listener: () -> Unit) {
        onSingleTap = listener
        binding?.zoomableImage?.onSingleTapListener = listener
    }

    override fun setOnZoomInteractionListener(listener: (zoomed: Boolean, scaling: Boolean) -> Unit) {
        onZoomInteraction = listener
        attachZoomListeners()
    }

    override fun syncChromeFromHost(fullscreen: Boolean, animated: Boolean) = Unit
    override fun isZoomed(): Boolean = binding?.zoomableImage?.isZoomed == true
    override fun pauseVideo() { endPlayback() }
    override fun playVideo() = Unit
    override fun isVideoPage(): Boolean = false
    override fun isMotionPhotoPage(): Boolean = host.isMotionPhoto

    /**
     * 由 Fragment.onResume/onPause 驱动（ViewPager2 BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT）。
     * visible=true → 首次未播过则自动播放；visible=false → 停止播放。
     */
    override fun onPageVisibilityChanged(visible: Boolean) {
        if (!visible) {
            pendingPlayWhenReady = false
            endPlayback()
            return
        }
        if (hasAutoPlayed) return
        tryAutoPlay()
    }

    // ── 自动播放 ─────────────────────────────────────────────────────────

    private fun tryAutoPlay() {
        when (val s = prepareState) {
            is PrepareState.Ready -> {
                hasAutoPlayed = true
                startPlayback(s.playbackUri)
            }
            PrepareState.Preparing, PrepareState.Pending -> {
                pendingPlayWhenReady = true
            }
            PrepareState.NotMotionPhoto, PrepareState.Failed -> Unit
        }
    }

    // ── 长按播放 ─────────────────────────────────────────────────────────

    private fun onLongPress() {
        when (val s = prepareState) {
            is PrepareState.Ready -> startPlayback(s.playbackUri)
            PrepareState.Preparing, PrepareState.Pending -> {
                showOverlay()
                pendingPlayWhenReady = true
            }
            PrepareState.NotMotionPhoto, PrepareState.Failed -> Unit
        }
    }

    private fun onLongPressRelease() {
        pendingPlayWhenReady = false
        endPlayback()
    }

    // ── Prepare ──────────────────────────────────────────────────────────

    private fun startPrepare() {
        if (prepareState is PrepareState.Ready) return
        prepareJob?.cancel()
        prepareState = PrepareState.Preparing
        prepareJob = host.lifecycleScope.launch {
            try {
                val media = host.probeMediaFile()
                val detected = host.isMotionPhoto ||
                    MotionPhotoDetector.detectSingle(host.context, media)
                if (!detected) {
                    prepareState = PrepareState.NotMotionPhoto
                    return@launch
                }
                host.isMotionPhoto = true
                host.notifyLivePhotoDetected()

                val playbackUri = withContext(Dispatchers.IO) {
                    MotionPhotoVideoResolver.resolvePlaybackUri(host.context, host.mediaId, uri.toUri())
                }
                if (playbackUri == null) {
                    prepareState = PrepareState.Failed
                    return@launch
                }

                prepareState = PrepareState.Ready(playbackUri)

                // 首次自动播放：本页为当前展示页时起播（确定性信号，不依赖 onResume 时序）。
                // 共享单例 ExoPlayer：离屏邻接页绝不触碰播放器——否则其 prepareMedia 会换走 mediaItem
                // 并 playWhenReady=false，偶发打断当前页正在进行的播放（startPlayback 内部已自带预加载）。
                // hasAutoPlayed 保证整个展示周期内只自动播一次；长按不受此限制。
                if (!hasAutoPlayed && (pendingPlayWhenReady || host.isCurrentPage())) {
                    pendingPlayWhenReady = false
                    hasAutoPlayed = true
                    withContext(Dispatchers.Main) { startPlayback(playbackUri) }
                }
            } catch (_: Exception) {
                prepareState = PrepareState.Failed
            }
        }
    }

    // ── 播放控制 ─────────────────────────────────────────────────────────

    private fun startPlayback(playbackUri: Uri) {
        val b = binding ?: return
        val player = host.sharedMotionPhotoPlayer() ?: return
        showOverlay()
        player.prepareMedia(playbackUri)
        attachListener(player)
        b.motionPhotoPlayer.player = player.obtainPlayer()
        player.playFromStart()
    }

    private fun showOverlay() {
        val b = binding ?: return
        if (isPlaybackOverlayVisible) return
        isPlaybackOverlayVisible = true
        b.zoomableImage.isLongPressInteractionActive = true
        b.motionPhotoPlayer.visibility = View.VISIBLE
        b.zoomableImage.alpha = 0f
    }

    private fun endPlayback() {
        pendingPlayWhenReady = false
        val b = binding
        b?.zoomableImage?.isLongPressInteractionActive = false
        if (!isPlaybackOverlayVisible && b?.motionPhotoPlayer?.player == null) {
            detachListener()
            return
        }
        isPlaybackOverlayVisible = false
        b?.motionPhotoPlayer?.player = null
        detachListener()
        host.sharedMotionPhotoPlayer()?.stopAndReset()
        b?.apply {
            motionPhotoPlayer.visibility = View.GONE
            zoomableImage.alpha = 1f
            zoomableImage.isLongPressInteractionActive = false
        }
    }

    private fun onPlaybackEnded() {
        pendingPlayWhenReady = false
        endPlayback()
    }

    private fun attachListener(player: PreviewMotionPhotoPlayer) {
        if (playbackListenerAttached) return
        player.addListener(playbackListener)
        playbackListenerAttached = true
    }

    private fun detachListener() {
        if (!playbackListenerAttached) return
        host.sharedMotionPhotoPlayer()?.removeListener(playbackListener)
        playbackListenerAttached = false
    }

    private fun attachZoomListeners() {
        val iv = binding?.zoomableImage ?: return
        val l = onZoomInteraction ?: return
        iv.onZoomStateChanged = { z -> l(z, false) }
        iv.onScalingChanged = { s -> l(iv.isZoomed, s) }
    }
}
