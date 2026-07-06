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
            // 控制器显隐改由播放状态驱动（PreviewVideoPageDelegate 监听 Player 状态手动 show/hide），
            // 关闭自动弹出，避免起播/暂停瞬间与手动逻辑打架
            controllerAutoShow = false
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
            // 暂停态下只留中央播放 icon，不压暗画面
            androidx.media3.ui.R.id.exo_controls_background,
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
