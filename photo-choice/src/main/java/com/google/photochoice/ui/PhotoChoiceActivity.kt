package com.google.photochoice.ui

import android.app.Activity
import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
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
import kotlinx.coroutines.launch

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
    private var toolbarChevronExpanded = false
    private lateinit var backPressCallback: OnBackPressedCallback

    internal val scrollingDateHeader
        get() = binding.scrollingDateHeader

    companion object {
        internal var pendingConfig: PhotoChoiceConfig? = null
        internal var pendingResultCallback: ((PhotoChoiceResult?) -> Unit)? = null

        private const val TAG_GRID = "grid"
    }

    /** 启动预览页并接收"用户在预览页点了 Done"的事件。 */
    private val previewLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                finishWithResult()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 在 super 之前应用主题
        config = pendingConfig ?: PhotoChoiceConfig()
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
        val night = when (mode) {
            ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            ThemeMode.FOLLOW_SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        if (AppCompatDelegate.getDefaultNightMode() != night) {
            AppCompatDelegate.setDefaultNightMode(night)
        }
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
        // 打开预览 Activity
        lifecycleScope.launch {
            viewModel.showPreview.collect { show ->
                if (show) {
                    previewLauncher.launch(
                        Intent(this@PhotoChoiceActivity, PreviewActivity::class.java)
                    )
                }
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
        if (resultDelivered) return
        val callback = pendingResultCallback ?: run {
            finish()
            return
        }
        val selected = viewModel.getSelectedItems()
        if (selected.isEmpty()) {
            pendingResultCallback = null
            resultDelivered = true
            callback(null)
            finish()
            return
        }

        val uris = selected.map { it.uri.toUri() }
        val needsCompression =
            viewModel.config.compressConfig.enabled &&
                selected.any { it.type == com.google.photochoice.data.model.MediaFile.MediaType.IMAGE }

        if (needsCompression) {
            lifecycleScope.launch {
                val helper = CompressHelper(this@PhotoChoiceActivity)
                val cfg = viewModel.config.compressConfig
                val outUris = mutableListOf<Uri>()
                val outPaths = mutableListOf<String>()
                for (item in selected) {
                    if (item.type == com.google.photochoice.data.model.MediaFile.MediaType.IMAGE) {
                        val file = helper.compress(item.uri.toUri(), cfg)
                        if (file != null) {
                            outUris.add(Uri.fromFile(file))
                            outPaths.add(file.absolutePath)
                        } else {
                            outUris.add(item.uri.toUri())
                            outPaths.add(resolvePath(item.uri.toUri()))
                        }
                    } else {
                        outUris.add(item.uri.toUri())
                        outPaths.add(resolvePath(item.uri.toUri()))
                    }
                }
                pendingResultCallback = null
                resultDelivered = true
                callback(PhotoChoiceResult(uris = outUris, paths = outPaths))
                finish()
            }
        } else {
            pendingResultCallback = null
            resultDelivered = true
            callback(
                PhotoChoiceResult(
                    uris = uris,
                    paths = uris.map { resolvePath(it) }
                )
            )
            finish()
        }
    }

    /**
     * 来自 [com.google.photochoice.ui.crop.CropActivity] 的裁剪结果回调入口。
     * 由 [com.google.photochoice.ui.grid.MediaGridFragment] 在 ActivityResultLauncher 回调中调用。
     */
    fun finishWithCropResult(croppedUri: String) {
        if (resultDelivered) return
        val callback = pendingResultCallback
        pendingResultCallback = null
        resultDelivered = true
        val uri = croppedUri.toUri()
        callback?.invoke(
            PhotoChoiceResult(
                uris = listOf(uri),
                paths = listOf(resolvePath(uri))
            )
        )
        finish()
    }

    private fun resolvePath(uri: Uri): String {
        if (uri.scheme == "file") return uri.path ?: uri.toString()
        return runCatching {
            contentResolver.query(
                uri,
                arrayOf(MediaStore.Files.FileColumns.DATA),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.toString()
    }

    override fun onDestroy() {
        if (isFinishing) {
            // 整个选择流程结束，释放跨 Activity 共享的 ViewModel。
            PhotoChoiceViewModelStore.release()
        }
        super.onDestroy()
    }

    override fun finish() {
        // 兜底：如果 Activity 被系统强制结束（未走 finishWithCancel/finishWithResult）
        if (!resultDelivered) {
            val callback = pendingResultCallback
            pendingResultCallback = null
            resultDelivered = true
            callback?.invoke(null)
        }
        super.finish()
    }

}
