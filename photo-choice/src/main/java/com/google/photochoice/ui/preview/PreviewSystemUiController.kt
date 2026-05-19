package com.google.photochoice.ui.preview

import android.view.View
import android.view.Window
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * 预览页系统 UI：基于 [WindowInsetsControllerCompat] 控制沉浸式全屏。
 */
class PreviewSystemUiController(
    window: Window,
    anchor: View
) {
    private val insetsController = WindowCompat.getInsetsController(window, anchor)

    var isFullscreen: Boolean = false
        private set

    fun applyFullscreen(fullscreen: Boolean) {
        isFullscreen = fullscreen
        if (fullscreen) {
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    fun restore() {
        applyFullscreen(false)
    }
}
