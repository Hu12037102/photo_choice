package com.google.photochoice.ui.crop

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.google.photochoice.ui.BaseActivity
import com.google.photochoice.R
import com.google.photochoice.config.CropAspectRatio
import com.google.photochoice.databinding.ActivityCropBinding
import com.google.photochoice.ui.PhotoChoiceActivity
import com.google.photochoice.ui.ThemeModes
import com.google.photochoice.util.SelectionResultController
import com.google.photochoice.util.SelectionResultProcessor
import com.google.photochoice.viewmodel.PhotoChoiceViewModelStore

/**
 * 裁剪页独立 Activity。承载 [CropFragment]；通过 setResult 回传裁剪+压缩后的结果。
 *
 * 裁剪确认后，若配置开启压缩则就地执行（遮罩显示在裁剪页）、压缩完成后再回传父页；
 * 父页（[PhotoChoiceActivity]）收到结果后直接交付宿主，不再二次压缩——消除旧实现中
 * 先跳回网格页才压缩的不对称体验。
 *
 * 由 [com.google.photochoice.ui.grid.MediaGridFragment] 启动；不暴露给宿主 App。
 */
class CropActivity : BaseActivity() {

    private lateinit var binding: ActivityCropBinding

    /** 压缩进行中时拦截返回键，允许取消压缩而非退出裁剪页。 */
    private lateinit var compressCancelCallback: OnBackPressedCallback

    /**
     * 裁剪页"确认"结果协调器：就地压缩 + 遮罩 + 取消，
     * 交付走 [deliverCropResult]（setResult 已压缩结果 + finish，父页不再二次压缩）。
     */
    private val resultController: SelectionResultController by lazy {
        SelectionResultController(
            scope = lifecycleScope,
            overlay = binding.compressOverlay,
            processor = SelectionResultProcessor(this),
            onProcessingChanged = { processing ->
                compressCancelCallback.isEnabled = processing
            },
            onDeliver = { result ->
                deliverCropResult(result.uris, result.paths)
            }
        )
    }

    companion object {
        const val EXTRA_SOURCE_URI = "source_uri"
        const val EXTRA_INITIAL_RATIO = "initial_ratio"
        /** 裁剪输出尺寸上限（px）；0 = 不限制。来源 [com.google.photochoice.config.CropConfig]。 */
        private const val EXTRA_MAX_WIDTH = "max_width"
        private const val EXTRA_MAX_HEIGHT = "max_height"

        private const val TAG_CROP = "crop"

        fun intent(
            context: Context,
            sourceUri: String,
            initialRatio: CropAspectRatio,
            maxWidth: Int = 0,
            maxHeight: Int = 0
        ): Intent = Intent(context, CropActivity::class.java).apply {
            putExtra(EXTRA_SOURCE_URI, sourceUri)
            putExtra(EXTRA_INITIAL_RATIO, initialRatio.name)
            putExtra(EXTRA_MAX_WIDTH, maxWidth)
            putExtra(EXTRA_MAX_HEIGHT, maxHeight)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 应用会话配置的日夜模式（per-Activity，不影响宿主）
        ThemeModes.applyLocalFromSession(this)
        super.onCreate(savedInstanceState)
        // SystemBarStyle.dark() 强制白色状态栏/导航栏图标，不受内容颜色影响
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        binding = ActivityCropBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 仅压缩进行中才拦截返回键；初始关闭，不阻断系统预测性返回动画
        compressCancelCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                resultController.cancel()
            }
        }
        onBackPressedDispatcher.addCallback(this, compressCancelCallback)

        val sourceUri = intent.getStringExtra(EXTRA_SOURCE_URI)
        if (sourceUri.isNullOrBlank()) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }
        val initialRatio = parseInitialRatio(intent.getStringExtra(EXTRA_INITIAL_RATIO))
        val maxWidth = intent.getIntExtra(EXTRA_MAX_WIDTH, 0)
        val maxHeight = intent.getIntExtra(EXTRA_MAX_HEIGHT, 0)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(
                    R.id.cropFragmentContainer,
                    CropFragment.newInstance(sourceUri, initialRatio, maxWidth, maxHeight),
                    TAG_CROP
                )
                .commit()
        }

        supportFragmentManager.setFragmentResultListener(
            CropFragment.REQUEST_KEY, this
        ) { _, bundle ->
            val croppedUri = bundle.getString(CropFragment.EXTRA_CROPPED_URI)
            if (croppedUri.isNullOrBlank()) {
                setResult(Activity.RESULT_CANCELED)
                finish()
                return@setFragmentResultListener
            }
            // 裁剪产物恒为 JPEG；视配置决定是否就地再压一层
            val vm = PhotoChoiceViewModelStore.peek()
            val compressEnabled = vm?.config?.compressConfig?.enabled ?: false
            val exportItems = listOf(
                SelectionResultProcessor.ExportItem(
                    uri = croppedUri.toUri(),
                    shouldCompress = compressEnabled
                )
            )
            val config = vm?.config?.compressConfig
                ?: com.google.photochoice.config.CompressConfig()
            resultController.submit(exportItems, config)
        }
    }

    /**
     * 交付裁剪压缩结果给父页（[PhotoChoiceActivity]）：携带 EXTRA_RESULT_URIS/PATHS 新格式。
     */
    private fun deliverCropResult(
        uris: List<Uri>,
        paths: List<String>
    ) {
        setResult(
            Activity.RESULT_OK,
            Intent()
                .putParcelableArrayListExtra(
                    PhotoChoiceActivity.EXTRA_RESULT_URIS, ArrayList(uris)
                )
                .putStringArrayListExtra(
                    PhotoChoiceActivity.EXTRA_RESULT_PATHS, ArrayList(paths)
                )
        )
        finish()
    }

    private fun parseInitialRatio(name: String?): CropAspectRatio {
        if (name.isNullOrBlank()) return CropAspectRatio.ORIGINAL
        return runCatching { CropAspectRatio.valueOf(name) }
            .getOrDefault(CropAspectRatio.ORIGINAL)
    }
}
