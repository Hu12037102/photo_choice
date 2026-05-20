package com.google.photochoice.ui.preview

import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.google.photochoice.R

@OptIn(UnstableApi::class)
internal object PreviewPlayerViewChrome {

    fun configure(playerView: PlayerView, playButtonSizePx: Int) {
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
        applyCenterPlayPauseButtonSize(playerView, playButtonSizePx)
    }

    private fun applyCenterPlayPauseButtonSize(playerView: PlayerView, sizePx: Int) {
        val playPause = playerView.findViewById<ImageButton>(androidx.media3.ui.R.id.exo_play_pause)
            ?: return
        playPause.layoutParams = playPause.layoutParams.apply {
            width = sizePx
            height = sizePx
        }
        playPause.scaleType = ImageView.ScaleType.FIT_CENTER
        playPause.adjustViewBounds = true
    }
}
