package com.google.photochoice.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.google.photochoice.PhotoChoiceResult
import com.google.photochoice.R
import com.google.photochoice.config.DesignTokens
import com.google.photochoice.config.MediaType
import com.google.photochoice.config.PhotoChoiceConfig
import com.google.photochoice.config.ThemeMode
import com.google.photochoice.databinding.ActivityPhotoChoiceBinding
import com.google.photochoice.ui.grid.MediaGridFragment
import com.google.photochoice.ui.preview.PreviewActivity
import com.google.photochoice.util.CompressHelper
import com.google.photochoice.viewmodel.PhotoChoiceViewModel
import com.google.photochoice.viewmodel.PhotoChoiceViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * PhotoChoice 容器 Activity。承载 MediaGridFragment；预览由 [PreviewActivity] 承载；裁剪由 [com.google.photochoice.ui.crop.CropActivity] 承载。
 *
 * 此 Activity 通过 [PhotoChoice.with].forResult 隐式启动，不暴露给宿主 App 直接调用。
 */
class PhotoChoiceActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPhotoChoiceBinding
    private lateinit var viewModel: PhotoChoiceViewModel
    private lateinit var config: PhotoChoiceConfig

    private var resultDelivered = false
    /** 异步压缩交付进行中：拦截 finishWithResult/finishWithCropResult 重入。 */
    private var resultDeliveryInFlight = false
    private var toolbarChevronExpanded = false
    private lateinit var backPressCallback: OnBackPressedCallback

    internal val scrollingDateHeader
        get() = binding.scrollingDateHeader

    companion object {
        internal var pendingConfig: PhotoChoiceConfig? = null
        internal var pendingResultCallback: ((PhotoChoiceResult?) -> Unit)? = null

        /** Contract 模式：配置经 Intent 传入（无静态变量，抗进程死亡/重建）。 */
        internal const val EXTRA_CONFIG = "photochoice:config"
        internal const val EXTRA_RESULT_URIS = "photochoice:result_uris"
        internal const val EXTRA_RESULT_PATHS = "photochoice:result_paths"

        private const val TAG_GRID = "grid"
    }

    /** true = 经 PhotoChoiceContract 启动，结果走 setResult；false = 旧静态回调轨。 */
    private var contractMode = false

    /** 启动预览页并接收"用户在预览页点了 Done"的事件。 */
    private val previewLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                finishWithResult()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Contract 轨：配置随 Intent 传递，重建/进程死亡后自动恢复，会话完全可续
        val intentConfig = IntentCompat.getSerializableExtra(
            intent, EXTRA_CONFIG, PhotoChoiceConfig::class.java
        )
        contractMode = intentConfig != null

        // 旧轨进程死亡兜底：companion 静态传参已被清零，config/callback 均不可恢复。
        // 绝不能带默认配置继续跑（宿主要的可能是"视频单选裁剪"，默认却是"图片9选"），
        // 也无法再回调结果——直接结束会话，避免"幽灵选择器"。
        if (savedInstanceState != null && intentConfig == null && pendingConfig == null) {
            resultDelivered = true // 无 callback 可回调，跳过 finish() 兜底逻辑
            super.onCreate(savedInstanceState)
            finish()
            return
        }
        // 在 super 之前应用主题
        config = intentConfig ?: pendingConfig ?: PhotoChoiceConfig()
        applyThemeMode(config.themeMode)

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityPhotoChoiceBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarAppearance()

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val horizontal = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(
                left = horizontal.left,
                top = statusBars.top,
                right = horizontal.right,
                bottom = navBars.bottom,
            )
            insets
        }
        ViewCompat.requestApplyInsets(binding.main)

        viewModel = PhotoChoiceViewModelStore.obtain(application, config)

        backPressCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackStarted(backEvent: BackEventCompat) {
                binding.albumDropdownLayer.onBackGestureStarted()
            }
            override fun handleOnBackProgressed(backEvent: BackEventCompat) {
                binding.albumDropdownLayer.setDismissProgress(backEvent.progress)
            }
            override fun handleOnBackPressed() {
                binding.albumDropdownLayer.commitBackDismiss()
            }
            override fun handleOnBackCancelled() {
                binding.albumDropdownLayer.cancelBackDismiss()
            }
        }

        setupToolbar()
        setupAlbumDropdown()
        setupBottomBar()
        setupDateHeaderLayer()
        observeState()
        setupBackPress()

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(binding.fragmentContainer.id, MediaGridFragment(), TAG_GRID)
                .commit()
        }
    }

    private fun applySystemBarAppearance() {
        window.isNavigationBarContrastEnforced = false
        WindowCompat.getInsetsController(window, binding.root).apply {
            isAppearanceLightStatusBars = !isNightMode()
            isAppearanceLightNavigationBars = !isNightMode()
        }
    }

    private fun isNightMode(): Boolean {
        val night = resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return night == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    private fun applyThemeMode(mode: ThemeMode) {
        // per-Activity localNightMode：严禁 setDefaultNightMode（会全局改写宿主 App 的日夜模式）
        ThemeModes.applyLocal(this, mode)
    }

    private fun setupToolbar() {
        binding.ivToolbarArrow.rotation = 0f
        binding.albumDropdownLayer.onPanelVisibilityChanged = { expanded ->
            backPressCallback.isEnabled = expanded
            animateToolbarChevron(expanded)
            if (expanded) {
                binding.scrollingDateHeader.hideImmediately()
            }
        }
        binding.btnNavBack.setOnClickListener {
            if (binding.albumDropdownLayer.isShowing()) {
                binding.albumDropdownLayer.dismiss()
            } else {
                finish()
            }
        }
        binding.toolbarTitleContainer.setOnClickListener { toggleAlbumDropdown() }
    }

    /** 目录 Chevron：收起 0°，展开 180°（与下拉面板动画时长一致）。 */
    private fun animateToolbarChevron(expanded: Boolean) {
        if (toolbarChevronExpanded == expanded) return
        toolbarChevronExpanded = expanded
        binding.ivToolbarArrow.animate().cancel()
        binding.ivToolbarArrow.animate()
            .rotation(if (expanded) 180f else 0f)
            .setDuration(DesignTokens.TOOLBAR_CHEVRON_ANIM_MS)
            .setInterpolator(
                if (expanded) DecelerateInterpolator() else AccelerateInterpolator()
            )
            .start()
    }

    private fun setupAlbumDropdown() {
        // initial state 用空数据填充；后续 observe 更新
        binding.albumDropdownLayer.configure(
            albums = emptyList(),
            currentBucketId = null,
            allPhotosName = viewModel.currentAlbumName.value,
            allPhotosCount = 0,
            allPhotosCoverUri = null,
            onAlbumSelected = { bucketId, displayName ->
                viewModel.switchAlbum(bucketId, displayName)
                binding.albumDropdownLayer.dismiss()
            },
        )
    }

    private fun setupDateHeaderLayer() {
        binding.dateHeaderClipHost.post {
            binding.dateHeaderClipHost.bringToFront()
            binding.scrollingDateHeader.bringToFront()
        }
    }

    private fun setupBottomBar() {
        binding.bottomBar.apply {
            onDoneClick = { finishWithResult() }
            onPreviewClick = { viewModel.previewSelected() }
            onThumbnailClick = { mediaFile ->
                viewModel.deselectById(mediaFile.id)
            }
            onVisibleHeightChanged = { height ->
                updateMediaGridBottomInset(height)
            }
        }
    }

    private fun updateMediaGridBottomInset(height: Int) {
        (supportFragmentManager.findFragmentByTag(TAG_GRID) as? MediaGridFragment)
            ?.setBottomContentInset(height)
    }

    private fun toggleAlbumDropdown() {
        if (viewModel.albums.value.isEmpty()) return
        binding.albumDropdownLayer.toggle()
    }

    private fun observeState() {
        // 标题
        lifecycleScope.launch {
            viewModel.currentAlbumName.collect { name ->
                binding.tvToolbarTitle.text = name
            }
        }
        // 相册数据更新 / 标题箭头显隐
        lifecycleScope.launch {
            viewModel.albums.collect { albums ->
                val hasMedia = albums.isNotEmpty()
                binding.tvToolbarTitle.visibility = if (hasMedia) View.VISIBLE else View.INVISIBLE
                binding.ivToolbarArrow.visibility = if (hasMedia) View.VISIBLE else View.GONE
                binding.toolbarTitleContainer.isClickable = hasMedia
                if (!hasMedia) {
                    toolbarChevronExpanded = false
                    binding.ivToolbarArrow.rotation = 0f
                }

                // 无数据时强制隐藏；有数据时由 selectionState 决定是否展开底部栏
                if (!hasMedia) {
                    binding.bottomBar.hideImmediately()
                }

                val totalCount = albums.sumOf { it.mediaCount }
                val cover = albums.firstOrNull()?.coverUri
                binding.albumDropdownLayer.configure(
                    albums = albums,
                    currentBucketId = viewModel.currentBucketId.value,
                    allPhotosName = getAllPhotosName(),
                    allPhotosCount = totalCount,
                    allPhotosCoverUri = cover,
                    onAlbumSelected = { bucketId, displayName ->
                        viewModel.switchAlbum(bucketId, displayName)
                        binding.albumDropdownLayer.dismiss()
                    },
                )
            }
        }
        lifecycleScope.launch {
            viewModel.currentBucketId.collect {
                binding.albumDropdownLayer.updateSelection(it)
            }
        }
        // 打开预览 Activity（一次性事件，无重建回放）
        lifecycleScope.launch {
            viewModel.showPreviewEvent.collect {
                previewLauncher.launch(
                    Intent(this@PhotoChoiceActivity, PreviewActivity::class.java)
                )
            }
        }
        // 选中状态绑定到底部栏
        lifecycleScope.launch {
            viewModel.selectionState.collect { state ->
                binding.bottomBar.bindState(
                    state = state,
                    selectCount = viewModel.config.sanitizedSelectCount
                )
            }
        }
    }

    private fun getAllPhotosName(): String {
        return when (viewModel.config.mediaType) {
            MediaType.VIDEO -> getString(R.string.photochoice_all_videos)
            else -> getString(R.string.photochoice_all_photos)
        }
    }

    private fun setupBackPress() {
        onBackPressedDispatcher.addCallback(this, backPressCallback)
    }

    /** 用户点击完成。 */
    fun finishWithResult() {
        // resultDeliveryInFlight：压缩异步窗口内拦截重入（双击 Done / 裁剪与 Done 竞态），
        // 否则会启动两个压缩协程并向宿主回调两次
        if (resultDelivered || resultDeliveryInFlight) return
        if (!contractMode && pendingResultCallback == null) {
            // 旧轨会话已失效（callback 丢失），无从回传，直接结束
            finish()
            return
        }
        val selected = viewModel.getSelectedItems()
        if (selected.isEmpty()) {
            deliverResult(null)
            return
        }
        val exportItems = selected.map { media ->
            ExportUri(
                uri = media.uri.toUri(),
                shouldCompress = viewModel.shouldCompressOnExport(media)
            )
        }
        deliverProcessedResult(exportItems)
    }

    /**
     * 裁剪页回传入口：与 [finishWithResult] 共用压缩与回传逻辑，尊重 Demo/宿主传入的 compressConfig。
     */
    fun finishWithCropResult(croppedUri: String) {
        if (resultDelivered || resultDeliveryInFlight) return
        if (!contractMode && pendingResultCallback == null) {
            finish()
            return
        }
        val exportItems = listOf(
            ExportUri(
                uri = croppedUri.toUri(),
                // 裁剪输出恒为 JPEG，开启压缩时按 compressConfig 再压一层
                shouldCompress = viewModel.config.compressConfig.enabled
            )
        )
        deliverProcessedResult(exportItems)
    }

    /** 单条回传项：源 URI + 是否执行压缩。 */
    private data class ExportUri(val uri: Uri, val shouldCompress: Boolean)

    /**
     * 按配置处理 URI 列表（可选压缩）后回传宿主；与 Demo Builder 参数一致。
     */
    private fun deliverProcessedResult(items: List<ExportUri>) {
        val needsCompression =
            viewModel.config.compressConfig.enabled && items.any { it.shouldCompress }
        if (!needsCompression) {
            deliverResult(
                PhotoChoiceResult(
                    uris = items.map { it.uri },
                    paths = items.map { resolvePath(it.uri) }
                )
            )
            return
        }
        resultDeliveryInFlight = true
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                buildCompressedResult(items)
            }
            deliverResult(result)
        }
    }

    /** 在 IO 线程对需压缩项写入沙盒并组装 [PhotoChoiceResult]。 */
    private fun buildCompressedResult(items: List<ExportUri>): PhotoChoiceResult {
        val helper = CompressHelper(this)
        val cfg = viewModel.config.compressConfig
        val outUris = mutableListOf<Uri>()
        val outPaths = mutableListOf<String>()
        for (item in items) {
            if (item.shouldCompress) {
                val file = helper.compress(item.uri, cfg)
                if (file != null) {
                    outUris.add(Uri.fromFile(file))
                    outPaths.add(file.absolutePath)
                } else {
                    outUris.add(item.uri)
                    outPaths.add(resolvePath(item.uri))
                }
            } else {
                outUris.add(item.uri)
                outPaths.add(resolvePath(item.uri))
            }
        }
        return PhotoChoiceResult(uris = outUris, paths = outPaths)
    }

    /**
     * 交付结果并结束 Activity（双轨收口）。
     * Contract 轨走 setResult（系统托管，抗重建）；旧轨走静态 callback。
     */
    private fun deliverResult(result: PhotoChoiceResult?) {
        // 幂等守卫：压缩期间用户按返回 → finish() 兜底已交付取消并置位；
        // 压缩协程随后完成时必须拦截，否则宿主先收"取消"再收"结果"（双重回调）
        if (resultDelivered) return
        resultDelivered = true
        resultDeliveryInFlight = false
        if (contractMode) {
            if (result != null) {
                setResult(
                    Activity.RESULT_OK,
                    Intent()
                        .putParcelableArrayListExtra(EXTRA_RESULT_URIS, ArrayList(result.uris))
                        .putStringArrayListExtra(EXTRA_RESULT_PATHS, ArrayList(result.paths))
                )
            } else {
                setResult(Activity.RESULT_CANCELED)
            }
        } else {
            val callback = pendingResultCallback
            pendingResultCallback = null
            callback?.invoke(result)
        }
        finish()
    }

    /**
     * paths 语义：仅库产物（压缩/裁剪的 file://）保证是可直接打开的文件路径；
     * 原始媒体（content://）在分区存储下不存在可靠的文件路径（DATA 列已废弃且常为 null），
     * 统一返回 URI 字符串——宿主对原始媒体应使用 uris + ContentResolver 读取。
     */
    private fun resolvePath(uri: Uri): String {
        if (uri.scheme == "file") return uri.path ?: uri.toString()
        return uri.toString()
    }

    override fun onDestroy() {
        val finishing = isFinishing
        super.onDestroy()
        // release 必须在 super.onDestroy() 之后：super 内部才 dispatch Fragment 的销毁，
        // 提前清空共享 VM 会留下"Fragment 尚存活但 VM 已没收"的时序窗口
        if (finishing) {
            PhotoChoiceViewModelStore.release()
        }
    }

    override fun finish() {
        // 兜底：如果 Activity 被系统/用户返回结束（未走 finishWithResult 等显式交付）
        if (!resultDelivered) {
            resultDelivered = true
            if (!contractMode) {
                val callback = pendingResultCallback
                pendingResultCallback = null
                callback?.invoke(null)
            }
            // Contract 轨无需处理：未 setResult 时系统默认回传 RESULT_CANCELED
        }
        super.finish()
    }

}
