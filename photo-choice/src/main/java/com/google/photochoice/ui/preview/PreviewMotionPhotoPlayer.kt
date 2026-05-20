package com.google.photochoice.ui.preview

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * 预览页实况图内嵌视频播放器：整个 [PreviewActivity] 生命周期内复用单个 ExoPlayer。
 */
class PreviewMotionPhotoPlayer(context: Context) {

    private val appContext = context.applicationContext
    private var player: ExoPlayer? = null
    private var boundUri: String? = null

    fun obtainPlayer(): ExoPlayer =
        player ?: ExoPlayer.Builder(appContext).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
            volume = 1f
            playWhenReady = false
        }.also { player = it }

    fun addListener(listener: Player.Listener) {
        obtainPlayer().addListener(listener)
    }

    fun removeListener(listener: Player.Listener) {
        player?.removeListener(listener)
    }

    /** 预加载媒体；与当前已绑定 URI 相同时仅在非 IDLE 时跳过。 */
    fun prepareMedia(uri: android.net.Uri) {
        val uriString = uri.toString()
        val exo = obtainPlayer()
        val sameMedia = boundUri == uriString && exo.mediaItemCount > 0
        if (!sameMedia) {
            boundUri = uriString
            exo.setMediaItem(MediaItem.fromUri(uri))
        }
        if (exo.playbackState == Player.STATE_IDLE) {
            exo.prepare()
        }
        exo.playWhenReady = false
    }

    fun playFromStart() {
        val exo = player ?: return
        if (exo.playbackState == Player.STATE_IDLE) {
            exo.prepare()
        }
        exo.seekTo(0)
        exo.playWhenReady = true
    }

    fun pause() {
        player?.playWhenReady = false
    }

    /** 停止播放但保持已 prepare 的媒体，便于再次长按起播。 */
    fun stopAndReset() {
        player?.apply {
            playWhenReady = false
            pause()
            seekTo(0)
        }
    }

    fun detachFromView() {
        pause()
    }

    fun release() {
        player?.release()
        player = null
        boundUri = null
    }
}
