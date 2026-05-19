package com.google.photochoice.ui

import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.photochoice.PhotoChoiceResult
import com.google.photochoice.R
import com.google.photochoice.config.DesignTokens
import com.google.photochoice.config.MediaType
import com.google.photochoice.config.PhotoChoiceConfig
import com.google.photochoice.config.ThemeMode
import com.google.photochoice.databinding.ActivityPhotoChoiceBinding
import com.google.photochoice.ui.crop.CropFragment
import com.google.photochoice.ui.grid.MediaGridFragment
import com.google.photochoice.ui.preview.PreviewActivity
import com.google.photochoice.util.CompressHelper
import com.google.photochoice.viewmodel.PhotoChoiceViewModel
import kotlinx.coroutines.launch

/**
 * PhotoChoice 容器 Activity。承载 MediaGridFragment / CropFragment；预览由 [PreviewActivity] 承载。
 *
 * 此 Activity 通过 [PhotoChoice.with].forResult 隐式启动，不暴露给宿主 App 直接调用。
 */
class PhotoChoiceActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPhotoChoiceBinding
    private lateinit var viewModel: PhotoChoiceViewModel
    private lateinit var config: PhotoChoiceConfig

    private var resultDelivered = false
    private var toolbarChevronExpanded = false

    companion object {
        internal var pendingConfig: PhotoChoiceConfig? = null
        internal var pendingResultCallback: ((PhotoChoiceResult?) -> Unit)? = null

        /** 预览 Activity 通过此引用共享 [PhotoChoiceViewModel]。 */
        internal var previewHost: PhotoChoiceActivity? = null

        private const val TAG_GRID = "grid"
        private const val TAG_CROP = "crop"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 在 super 之前应用主题
        config = pendingConfig ?: PhotoChoiceConfig()
        applyThemeMode(config.themeMode)

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityPhotoChoiceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = 0)
            binding.bottomBar.updatePadding(bottom = bars.bottom)
            insets
        }

        viewModel = ViewModelProvider(
            this,
            PhotoChoiceViewModel.Factory(application, config)
        )[PhotoChoiceViewModel::class.java]
        previewHost = this

        setupToolbar()
        setupAlbumDropdown()
        setupBottomBar()
        observeState()
        setupBackPress()

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(binding.fragmentContainer.id, MediaGridFragment(), TAG_GRID)
                .commit()
        }
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
        binding.albumDropdownPanel.onPanelVisibilityChanged = { expanded ->
            animateToolbarChevron(expanded)
        }
        binding.btnNavBack.setOnClickListener {
            if (binding.albumDropdownPanel.isShowing()) {
                binding.albumDropdownPanel.dismiss()
            } else {
                finishWithCancel()
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
        binding.albumDropdownPanel.configure(
            albums = emptyList(),
            currentBucketId = null,
            allPhotosName = viewModel.currentAlbumName.value,
            allPhotosCount = 0,
            allPhotosCoverUri = null,
            onAlbumSelected = { bucketId, displayName ->
                viewModel.switchAlbum(bucketId, displayName)
                binding.albumDropdownPanel.dismiss()
            },
            maskView = binding.maskView
        )
    }

    private fun setupBottomBar() {
        binding.bottomBar.apply {
            onDoneClick = { finishWithResult() }
            onPreviewClick = { viewModel.previewSelected() }
            onThumbnailClick = { mediaFile ->
                viewModel.deselectById(mediaFile.id)
            }
        }
    }

    private fun toggleAlbumDropdown() {
        if (viewModel.albums.value.isEmpty()) return
        binding.albumDropdownPanel.toggle()
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

                val totalCount = albums.sumOf { it.mediaCount }
                val cover = albums.firstOrNull()?.coverUri
                binding.albumDropdownPanel.configure(
                    albums = albums,
                    currentBucketId = viewModel.currentBucketId.value,
                    allPhotosName = getAllPhotosName(),
                    allPhotosCount = totalCount,
                    allPhotosCoverUri = cover,
                    onAlbumSelected = { bucketId, displayName ->
                        viewModel.switchAlbum(bucketId, displayName)
                        binding.albumDropdownPanel.dismiss()
                    },
                    maskView = binding.maskView
                )
            }
        }
        lifecycleScope.launch {
            viewModel.currentBucketId.collect {
                binding.albumDropdownPanel.updateSelection(it)
            }
        }
        // 打开预览 Activity
        lifecycleScope.launch {
            viewModel.showPreview.collect { show ->
                if (show) {
                    startActivity(Intent(this@PhotoChoiceActivity, PreviewActivity::class.java))
                    overridePendingTransition(android.R.anim.fade_in, 0)
                }
            }
        }
        // 裁剪导航
        lifecycleScope.launch {
            viewModel.showCrop.collect { uri ->
                if (uri != null) enterCrop(uri) else exitCrop()
            }
        }
        // 选中状态绑定到底部栏
        lifecycleScope.launch {
            viewModel.selectionState.collect { state ->
                binding.bottomBar.bindState(
                    state = state,
                    minSelectCount = viewModel.config.minSelectCount,
                    maxSelectCount = viewModel.config.maxSelectCount
                )
            }
        }
        // 裁剪结果监听
        supportFragmentManager.setFragmentResultListener(
            CropFragment.REQUEST_KEY, this
        ) { _, bundle ->
            val croppedUri = bundle.getString(CropFragment.EXTRA_CROPPED_URI) ?: return@setFragmentResultListener
            val callback = pendingResultCallback
            pendingResultCallback = null
            resultDelivered = true
            callback?.invoke(
                PhotoChoiceResult(
                    uris = listOf(croppedUri.toUri()),
                    paths = listOf(croppedUri.toUri().path ?: croppedUri),
                    isOriginal = false
                )
            )
            finish()
        }
    }

    private fun getAllPhotosName(): String {
        return when (viewModel.config.mediaType) {
            MediaType.VIDEO -> getString(R.string.photochoice_all_videos)
            else -> getString(R.string.photochoice_all_photos)
        }
    }

    private fun enterCrop(uri: String) {
        binding.toolbar.visibility = View.GONE
        binding.toolbarDivider.visibility = View.GONE
        binding.bottomBar.visibility = View.GONE
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, 0, 0, android.R.anim.fade_out)
            .replace(binding.fragmentContainer.id, CropFragment.newInstance(uri), TAG_CROP)
            .addToBackStack(TAG_CROP)
            .commit()
    }

    private fun exitCrop() {
        if (supportFragmentManager.findFragmentByTag(TAG_CROP) == null) return
        supportFragmentManager.popBackStack(
            TAG_CROP, FragmentManager.POP_BACK_STACK_INCLUSIVE
        )
        binding.toolbar.visibility = View.VISIBLE
        binding.toolbarDivider.visibility = View.VISIBLE
        binding.bottomBar.visibility = View.VISIBLE
    }

    private fun setupBackPress() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    viewModel.showCrop.value != null -> viewModel.dismissCrop()
                    binding.albumDropdownPanel.isShowing() -> binding.albumDropdownPanel.dismiss()
                    else -> finishWithCancel()
                }
            }
        })
    }

    /** 用户主动取消（返回键 / 关闭），回调 null。 */
    private fun finishWithCancel() {
        if (resultDelivered) {
            finish()
            return
        }
        val callback = pendingResultCallback
        pendingResultCallback = null
        resultDelivered = true
        callback?.invoke(null)
        finish()
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
        val isOriginal = viewModel.isOriginal.value
        val needsCompression =
            viewModel.config.compressConfig.enabled && !isOriginal &&
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
                callback(PhotoChoiceResult(uris = outUris, paths = outPaths, isOriginal = false))
                finish()
            }
        } else {
            pendingResultCallback = null
            resultDelivered = true
            callback(
                PhotoChoiceResult(
                    uris = uris,
                    paths = uris.map { resolvePath(it) },
                    isOriginal = isOriginal
                )
            )
            finish()
        }
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
            previewHost = null
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
