package com.google.photochoice

import android.content.Context
import android.content.Intent
import androidx.fragment.app.FragmentActivity
import com.google.photochoice.config.CompressConfig
import com.google.photochoice.config.CropConfig
import com.google.photochoice.config.MediaType
import com.google.photochoice.config.PhotoChoiceConfig
import com.google.photochoice.config.ThemeMode
import com.google.photochoice.ui.PhotoChoiceActivity
import com.google.photochoice.util.SandboxCleaner

/**
 * PhotoChoice 入口。Builder 模式，链式 API。
 *
 * 用法：
 * ```
 * PhotoChoice.with(activity)
 *     .selectCount(9)            // 1 = 单选，>1 = 多选；超出 1..9 区间会回落到 1
 *     .mediaType(MediaType.IMAGE)
 *     .forResult { result -> ... }
 * ```
 */
class PhotoChoice private constructor(val config: PhotoChoiceConfig) {

    /**
     * 启动选择器。
     *
     * @param activity FragmentActivity 用于发起 Intent
     * @param callback 选择结果回调。用户取消选择时回调 null
     */
    fun forResult(activity: FragmentActivity, callback: (PhotoChoiceResult?) -> Unit) {
        PhotoChoiceActivity.pendingConfig = config
        PhotoChoiceActivity.pendingResultCallback = callback
        val intent = Intent(activity, PhotoChoiceActivity::class.java)
        activity.startActivity(intent)
    }

    class Builder {
        private var selectCount: Int = 9
        private var mediaType: MediaType = MediaType.IMAGE
        private var spanCount: Int = 3
        private var showCamera: Boolean = true
        private var minVideoDurationMs: Long = 0L
        private var maxVideoDurationMs: Long = 60_000L
        private var themeMode: ThemeMode = ThemeMode.FOLLOW_SYSTEM
        private var cropConfig: CropConfig = CropConfig()
        private var compressConfig: CompressConfig = CompressConfig()

        /** 可选数量。1 = 单选，>1 = 多选；超出 1..9 区间会被回退为 1。 */
        fun selectCount(count: Int) = apply { selectCount = count }
        fun mediaType(type: MediaType) = apply { mediaType = type }
        fun spanCount(count: Int) = apply { spanCount = count }
        fun showCamera(show: Boolean) = apply { showCamera = show }
        fun maxVideoDuration(durationMs: Long) = apply { maxVideoDurationMs = durationMs }
        fun minVideoDuration(durationMs: Long) = apply { minVideoDurationMs = durationMs }
        fun themeMode(mode: ThemeMode) = apply { themeMode = mode }
        fun cropConfig(config: CropConfig) = apply { cropConfig = config }
        fun compressConfig(config: CompressConfig) = apply { compressConfig = config }

        fun build(): PhotoChoice {
            val config = PhotoChoiceConfig(
                selectCount = selectCount,
                mediaType = mediaType,
                spanCount = spanCount,
                showCamera = showCamera,
                minVideoDurationMs = minVideoDurationMs,
                maxVideoDurationMs = maxVideoDurationMs,
                themeMode = themeMode,
                cropConfig = cropConfig,
                compressConfig = compressConfig
            )
            return PhotoChoice(config)
        }

        fun forResult(activity: FragmentActivity, callback: (PhotoChoiceResult?) -> Unit) {
            build().forResult(activity, callback)
        }
    }

    companion object {
        @JvmStatic
        fun with(context: Context): Builder = Builder()

        /** 主动清理沙盒目录。调用方在处理完图片后调用以释放磁盘。 */
        @JvmStatic
        fun cleanup(context: Context) {
            SandboxCleaner(context).cleanAll()
        }
    }
}
