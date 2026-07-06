package com.google.photochoice.ui

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

/**
 * SDK 内所有 Activity 的基类。
 *
 * 目的：让 SDK 界面不受系统「显示大小」(densityDpi) 与「文字大小」(fontScale) 设置影响，
 * 始终按设备默认密度、1.0 字号缩放渲染，保证 UI 在不同系统设置下视觉一致。
 *
 * 实现：在 [attachBaseContext] 中以系统默认配置覆盖应用配置。
 * [Resources.getSystem] 是系统级单例，以系统默认 Configuration 初始化，
 * 不随用户「显示大小」/「文字大小」调整更新，故可作为"默认基准"。
 *
 * 作用范围：仅影响继承本类的 SDK 内 Activity，不污染宿主 App 其他页面。
 */
abstract class BaseActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        val original = newBase.resources.configuration
        val systemDefault = Resources.getSystem().configuration
        val config = Configuration(original).apply {
            // 固定文字大小：忽略系统 fontScale（对应"文字大小"设置）
            fontScale = 1.0f
            // 固定显示大小：用系统默认 densityDpi 覆盖（对应"显示大小"设置）
            val defaultDpi = systemDefault.densityDpi
            if (defaultDpi > 0) {
                densityDpi = defaultDpi
            }
        }
        Log.i(
            TAG,
            "attachBaseContext: original fontScale=${original.fontScale}, " +
                "densityDpi=${original.densityDpi}; " +
                "system default densityDpi=${systemDefault.densityDpi}; " +
                "applied fontScale=${config.fontScale}, densityDpi=${config.densityDpi}"
        )
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    companion object {
        private const val TAG = "PhotoChoice.BaseActivity"
    }
}
