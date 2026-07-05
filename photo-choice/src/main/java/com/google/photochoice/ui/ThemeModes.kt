package com.google.photochoice.ui

import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.photochoice.config.ThemeMode
import com.google.photochoice.viewmodel.PhotoChoiceViewModelStore

/**
 * 库内 Activity 的夜间模式应用工具。
 *
 * 必须使用 per-Activity 的 [AppCompatDelegate.setLocalNightMode]，
 * 严禁 [AppCompatDelegate.setDefaultNightMode]——后者是进程级全局 API，
 * 会改写宿主 App 全部 Activity 的日夜模式并强制重建其 Activity 栈，属于库不可接受的副作用。
 */
internal object ThemeModes {

    /** [ThemeMode] → AppCompat night mode 常量。 */
    fun nightModeOf(mode: ThemeMode): Int = when (mode) {
        ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
        ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
        ThemeMode.FOLLOW_SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }

    /** 在 super.onCreate 之前调用：仅对当前 Activity 生效，不影响宿主。 */
    fun applyLocal(activity: AppCompatActivity, mode: ThemeMode) {
        activity.delegate.localNightMode = nightModeOf(mode)
    }

    /**
     * 供 Preview/Crop 等二级页使用：从共享 ViewModel 取配置的主题模式；
     * 会话已失效（进程死亡重建）时不设置，保持系统默认。
     */
    fun applyLocalFromSession(activity: AppCompatActivity) {
        val mode = PhotoChoiceViewModelStore.peek()?.config?.themeMode ?: return
        applyLocal(activity, mode)
    }
}
