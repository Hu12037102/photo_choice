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

    private val playbackListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState != Player.STATE_ENDED) return
            binding?.zoomableImage?.post {
                onLivePhotoPlaybackEnded()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?): View {
        val itemBinding = ItemPreviewImageBinding.inflate(inflater, null, false)
        binding = itemBinding

        Glide.with(itemBinding.zoomableImage)
            .load(uri)
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .fitCenter()
            .into(itemBinding.zoomableImage)

        itemBinding.zoomableImage.apply {
            onSingleTap?.let { onSingleTapListener = it }
            onLongPressListener = { onLivePhotoLongPress() }
            onLongPressReleaseListener = { onLivePhotoLongPressRelease() }
        }
        attachZoomListeners()
        return itemBinding.root
    }

    override fun onViewCreated() {
        startMotionPhotoPrepare()
    }

    override fun onPause() {
        endLivePhotoPlayback()
    }

    override fun onDestroyView() {
        prepareJob?.cancel()
        endLivePhotoPlayback()
        binding = null
        prepareState = PrepareState.Pending
        pendingPlayWhenReady = false
        isPlaybackOverlayVisible = false
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

    override fun pauseVideo() {
        endLivePhotoPlayback()
    }

    override fun playVideo() = Unit

    override fun isVideoPage(): Boolean = false

    override fun isMotionPhotoPage(): Boolean = host.isMotionPhoto

    private fun onLivePhotoLongPress() {
        when (val state = prepareState) {
            is PrepareState.Ready -> startLivePhotoPlayback(state.playbackUri)
            PrepareState.Preparing, PrepareState.Pending -> {
                showPlaybackOverlay()
                pendingPlayWhenReady = true
            }
            PrepareState.NotMotionPhoto, PrepareState.Failed -> Unit
        }
    }

    private fun onLivePhotoLongPressRelease() {
        pendingPlayWhenReady = false
        endLivePhotoPlayback()
    }

    /** 进入页时检测并提取内嵌视频，长按即可起播。 */
    private fun startMotionPhotoPrepare() {
        if (prepareState is PrepareState.Ready) return
        prepareJob?.cancel()
        prepareState = PrepareState.Preparing
        prepareJob = host.lifecycleScope.launch {
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
                MotionPhotoVideoResolver.resolvePlaybackUri(
                    host.context,
                    host.mediaId,
                    uri.toUri()
                )
            }
            if (playbackUri == null) {
                prepareState = PrepareState.Failed
                return@launch
            }

            prepareState = PrepareState.Ready(playbackUri)
            val sharedPlayer = host.sharedMotionPhotoPlayer()
            if (sharedPlayer != null) {
                withContext(Dispatchers.Main) {
                    sharedPlayer.prepareMedia(playbackUri)
                }
            }
            if (pendingPlayWhenReady) {
                withContext(Dispatchers.Main) {
                    if (pendingPlayWhenReady) {
                        startLivePhotoPlayback(playbackUri)
                    }
                }
            }
        }
    }

    private fun startLivePhotoPlayback(playbackUri: Uri) {
        val itemBinding = binding ?: return
        val sharedPlayer = host.sharedMotionPhotoPlayer() ?: return

        showPlaybackOverlay()
        sharedPlayer.prepareMedia(playbackUri)
        attachPlaybackListener(sharedPlayer)
        itemBinding.motionPhotoPlayer.player = sharedPlayer.obtainPlayer()
        sharedPlayer.playFromStart()
    }

    private fun showPlaybackOverlay() {
        val itemBinding = binding ?: return
        if (isPlaybackOverlayVisible) return
        isPlaybackOverlayVisible = true
        itemBinding.zoomableImage.isLongPressInteractionActive = true
        itemBinding.motionPhotoPlayer.visibility = View.VISIBLE
        itemBinding.zoomableImage.alpha = 0f
    }

    private fun endLivePhotoPlayback() {
        pendingPlayWhenReady = false
        val itemBinding = binding
        itemBinding?.zoomableImage?.isLongPressInteractionActive = false
        val wasVisible = isPlaybackOverlayVisible ||
            itemBinding?.motionPhotoPlayer?.player != null
        if (!wasVisible) {
            detachPlaybackListener()
            return
        }

        isPlaybackOverlayVisible = false
        itemBinding?.motionPhotoPlayer?.player = null
        detachPlaybackListener()
        host.sharedMotionPhotoPlayer()?.stopAndReset()
        itemBinding?.apply {
            motionPhotoPlayer.visibility = View.GONE
            zoomableImage.alpha = 1f
            zoomableImage.isLongPressInteractionActive = false
        }
    }

    private fun onLivePhotoPlaybackEnded() {
        pendingPlayWhenReady = false
        endLivePhotoPlayback()
    }

    private fun attachPlaybackListener(sharedPlayer: PreviewMotionPhotoPlayer) {
        if (playbackListenerAttached) return
        sharedPlayer.addListener(playbackListener)
        playbackListenerAttached = true
    }

    private fun detachPlaybackListener() {
        if (!playbackListenerAttached) return
        host.sharedMotionPhotoPlayer()?.removeListener(playbackListener)
        playbackListenerAttached = false
    }

    private fun attachZoomListeners() {
        val imageView = binding?.zoomableImage ?: return
        val listener = onZoomInteraction ?: return
        imageView.onZoomStateChanged = { zoomed -> listener(zoomed, false) }
        imageView.onScalingChanged = { scaling -> listener(imageView.isZoomed, scaling) }
    }
}
