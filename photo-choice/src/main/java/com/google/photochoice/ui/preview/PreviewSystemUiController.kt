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
    private val anchor: View
) {
    private val insetsController = WindowCompat.getInsetsController(window, anchor)

    var isFullscreen: Boolean = false
        private set

    init {
        // 外观由 PreviewActivity 的 OnApplyWindowInsetsListener 统一兜底，不在此设置
    }

    fun applyFullscreen(fullscreen: Boolean) {
        isFullscreen = fullscreen
        if (fullscreen) {
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        }
        // show/hide + systemBarsBehavior 触发系统异步重置外观，
        // post 延迟确保在系统重置之后强制覆盖为白色图标
        forceLightAppearance()
    }

    private fun forceLightAppearance() {
        anchor.post {
            insetsController.isAppearanceLightStatusBars = true
            insetsController.isAppearanceLightNavigationBars = true
        }
    }

    fun restore() {
        applyFullscreen(false)
    }
}
