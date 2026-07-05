package com.google.photochoice

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.content.IntentCompat
import com.google.photochoice.config.PhotoChoiceConfig
import com.google.photochoice.ui.PhotoChoiceActivity

/**
 * 基于 [ActivityResultContract] 的选择器启动契约（推荐接入方式）。
 *
 * 与 [PhotoChoice.forResult] 的静态回调方式相比：
 * - 配置经 Intent extra 传入、结果经 setResult 回传，全程无静态变量；
 * - 天然抗宿主 Activity 重建与进程死亡（系统托管 Intent 与 result 派发）；
 * - 支持并发/重复发起（每次 launch 独立会话，无覆盖问题）。
 *
 * 用法：
 * ```
 * private val pickMedia = registerForActivityResult(PhotoChoiceContract()) { result ->
 *     // result == null 表示用户取消
 * }
 * pickMedia.launch(PhotoChoice.with(this).selectCount(9).buildConfig())
 * ```
 */
class PhotoChoiceContract : ActivityResultContract<PhotoChoiceConfig, PhotoChoiceResult?>() {

    /** 构造启动 Intent：配置随 Intent 传递，不经静态变量。 */
    override fun createIntent(context: Context, input: PhotoChoiceConfig): Intent =
        Intent(context, PhotoChoiceActivity::class.java)
            .putExtra(PhotoChoiceActivity.EXTRA_CONFIG, input)

    /** 解析回传：RESULT_OK 时从 extras 还原结果；取消/异常返回 null。 */
    override fun parseResult(resultCode: Int, intent: Intent?): PhotoChoiceResult? {
        if (resultCode != android.app.Activity.RESULT_OK || intent == null) return null
        val uris = IntentCompat.getParcelableArrayListExtra(
            intent, PhotoChoiceActivity.EXTRA_RESULT_URIS, Uri::class.java
        ) ?: return null
        val paths = intent.getStringArrayListExtra(PhotoChoiceActivity.EXTRA_RESULT_PATHS)
            ?: emptyList<String>()
        return PhotoChoiceResult(uris = uris, paths = paths)
    }
}
