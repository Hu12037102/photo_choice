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
        private var minImageSize: Long = 0L
        private var maxImageSize: Long = Long.MAX_VALUE
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
        /** 图片体积下限（字节），过滤图标类小图；0 = 不限制。仅作用于图片。 */
        fun minImageSize(sizeBytes: Long) = apply { minImageSize = sizeBytes }
        /** 图片体积上限（字节），过滤超大图；Long.MAX_VALUE = 不限制。仅作用于图片。 */
        fun maxImageSize(sizeBytes: Long) = apply { maxImageSize = sizeBytes }
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
                minImageSize = minImageSize,
                maxImageSize = maxImageSize,
                minVideoDurationMs = minVideoDurationMs,
                maxVideoDurationMs = maxVideoDurationMs,
                themeMode = themeMode,
                cropConfig = cropConfig,
                compressConfig = compressConfig
            )
            return PhotoChoice(config)
        }

        /**
         * 产出配置对象，配合 [PhotoChoiceContract] 使用（推荐接入方式）：
         * ```
         * val launcher = registerForActivityResult(PhotoChoiceContract()) { result -> ... }
         * launcher.launch(PhotoChoice.with(this).selectCount(9).buildConfig())
         * ```
         * Contract 轨全程无静态变量，天然抗宿主重建与进程死亡。
         */
        fun buildConfig(): PhotoChoiceConfig = build().config

        /**
         * 旧轨启动（静态回调）。注意约束：回调不跨宿主 Activity 重建与进程死亡——
         * 选择期间宿主被重建时回调仍指向旧实例。对可靠性有要求请改用 [buildConfig] + [PhotoChoiceContract]。
         */
        fun forResult(activity: FragmentActivity, callback: (PhotoChoiceResult?) -> Unit) {
            build().forResult(activity, callback)
        }
    }

    companion object {
        /**
         * 创建 Builder。
         *
         * 说明：[context] 目前未被使用，保留是为对齐 Glide/Picasso 等主流库 `with(context)` 的调用习惯，
         * 并为将来需要 Context 的能力（如按宿主主题预取资源、基于 Context 的默认配置）预留入口，
         * 避免后续新增该能力时被迫做破坏性的 API 变更。
         */
        @Suppress("UNUSED_PARAMETER")
        @JvmStatic
        fun with(context: Context): Builder = Builder()

        /** 主动清理沙盒缓存（压缩/裁剪目录 + 实况图内嵌视频目录）。处理完结果后调用以释放磁盘。 */
        @JvmStatic
        fun cleanup(context: Context) {
            SandboxCleaner(context).cleanAll()
        }
    }
}
