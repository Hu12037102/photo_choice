package com.google.photochoice.ui.preview

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.view.animation.Interpolator
import androidx.activity.OnBackPressedCallback
import androidx.core.net.toUri
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import com.google.photochoice.ui.BaseActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.google.photochoice.R
import com.google.photochoice.data.model.MediaFile
import com.google.photochoice.data.motion.MotionPhotoDetector
import com.google.photochoice.PhotoChoiceResult
import com.google.photochoice.databinding.ActivityPreviewBinding
import com.google.photochoice.ui.PhotoChoiceActivity
import com.google.photochoice.ui.ThemeModes
import com.google.photochoice.util.SelectionResultController
import com.google.photochoice.util.SelectionResultProcessor
import com.google.photochoice.util.dp
import com.google.photochoice.viewmodel.PhotoChoiceViewModel
import com.google.photochoice.viewmodel.PhotoChoiceViewModelStore
import kotlinx.coroutines.launch

/**
 * 媒体大图预览页（独立 Activity）。
 *
 * 图片缩放：双击、双指 pinch；放大后单指拖拽平移（见 [ZoomableImageView]）。
 * 单击图片区域切换全屏 / 非全屏；关闭预览请使用返回键或顶栏返回按钮。
 */
class PreviewActivity : BaseActivity(),
    PreviewPageFragment.LivePhotoBadgeHost,
    MotionPhotoPlaybackOwner {

    private lateinit var binding: ActivityPreviewBinding
    private var _motionPhotoPlayer: PreviewMotionPhotoPlayer? = null
    override val motionPhotoPlayer: PreviewMotionPhotoPlayer
        get() = _motionPhotoPlayer ?: PreviewMotionPhotoPlayer(this).also { _motionPhotoPlayer = it }
    private lateinit var viewModel: PhotoChoiceViewModel
    private lateinit var previewAdapter: PreviewAdapter
    private lateinit var systemUiController: PreviewSystemUiController

    /** 供 Fragment 在 onAttach 时获取，保证 onCreateView 之前单击回调已就绪。 */
    val chromeToggleCallback: () -> Unit = { toggleFullscreen() }

    private var isFullscreen = false
    private var pendingAfterSystemBars: (() -> Unit)? = null
    private var lastPagePosition = -1
    private val detectedLivePhotoIds = mutableSetOf<Long>()

    /** 压缩进行中拦截返回键，允许取消压缩而非退出预览。 */
    private lateinit var compressCancelCallback: OnBackPressedCallback

    /**
     * 预览页"完成"结果协调器：就地压缩 + 遮罩 + 取消，
     * 交付走 [deliverPreviewResult]（子 Activity：setResult 已压缩结果 + finish，父页不再二次压缩）。
     */
    private val resultController: SelectionResultController by lazy {
        SelectionResultController(
            scope = lifecycleScope,
            overlay = binding.compressOverlay,
            processor = SelectionResultProcessor(this),
            onProcessingChanged = { processing ->
                binding.btnDone.isEnabled = !processing
                compressCancelCallback.isEnabled = processing
            },
            onDeliver = { deliverPreviewResult(it) }
        )
    }

    companion object {
        private const val STATE_FULLSCREEN = "state_fullscreen"
        private const val CHROME_ANIM_DURATION_MS = 280L
        /** 部分机型系统栏 insets 动画无回调时的兜底等待。 */
        private const val SYSTEM_BARS_ANIM_FALLBACK_MS = 400L
    }

    private val chromeInterpolator: Interpolator = FastOutSlowInInterpolator()

    private val systemBarsAnimFallback = Runnable { runPendingAfterSystemBars() }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 应用会话配置的日夜模式（per-Activity，不影响宿主）
        ThemeModes.applyLocalFromSession(this)
        super.onCreate(savedInstanceState)

        val vm = PhotoChoiceViewModelStore.peek()
        if (vm == null) {
            finish()
            return
        }
        viewModel = vm

        // SystemBarStyle.dark() 强制白色状态栏/导航栏图标，不受内容颜色影响
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        binding = ActivityPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        savedInstanceState?.let { isFullscreen = it.getBoolean(STATE_FULLSCREEN, false) }

        systemUiController = PreviewSystemUiController(window, binding.previewRoot)
        setupWindowInsets()
        setupSystemBarsAnimationListener()

        val mediaList = viewModel.previewMediaList.value
        if (mediaList.isEmpty()) {
            finish()
            return
        }

        val startPosition = viewModel.previewStartPosition.value
        previewAdapter = PreviewAdapter(this, mediaList)
        binding.viewPager.apply {
            adapter = previewAdapter
            offscreenPageLimit = 2
            setCurrentItem(startPosition, false)
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    if (lastPagePosition >= 0 && lastPagePosition != position) {
                        previewPageAt(lastPagePosition)?.pauseVideo()
                    }
                    lastPagePosition = position
                    updateIndexIndicator(position)
                    updateSelectionBox()
                    updateLivePhotoBadge(position)
                    updateLiveExportToggle(position)
                    bindCurrentPageGestures()
                    syncPageChrome(animated = false)
                    // 滑近已加载末尾时向后续载（"预览已选"等固定集合场景内部直接忽略）
                    viewModel.onPreviewPageSelected(position)
                }
            })
        }

        lastPagePosition = startPosition
        updateIndexIndicator(startPosition)
        // 进入位置本就靠近快照末尾时（如点击网格已加载区的最后几项），立即触发一次续载检查；
        // setCurrentItem(0) 不会回调 onPageSelected，此处兜底
        viewModel.onPreviewPageSelected(startPosition)
        binding.viewPager.post {
            bindCurrentPageGestures()
            updateLivePhotoBadge(startPosition)
            updateLiveExportToggle(startPosition)
            syncPageChrome(animated = false)
        }

        binding.btnBack.setOnClickListener { finishPreview() }
        binding.selectionBox.setOnClickListener { toggleCurrentSelection() }
        binding.btnDone.setOnClickListener { onDoneClicked() }
        binding.liveExportToggle.setOnClickListener { toggleLiveExportMode() }
        setupChromeTouchGuard()

        // 压缩进行中时返回键取消压缩并恢复 UI，而非退出预览页
        compressCancelCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                resultController.cancel()
            }
        }
        onBackPressedDispatcher.addCallback(this, compressCancelCallback)

        if (viewModel.config.isSingleSelect) {
            // 单选：隐藏 checkbox，Done 按钮即"选中当前并完成"
            binding.selectionBoxContainer.visibility = View.GONE
        }
        observeState()
        updateSelectionBox()
        applyDisplayMode(animated = false)

    }

    /**
     * 根布局不消费系统栏间距（padding 恒为 0，图片全屏铺满）；
     * 顶栏 / 底栏各自叠加状态栏、导航栏 inset。
     */
    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.previewRoot) { root, insets ->
            // 每次 insets 变化强制白色状态栏/导航栏图标（show/hide 之后兜底）
            WindowCompat.getInsetsController(window, root).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
            root.updatePadding(0, 0, 0, 0)
            val statusBarInset = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars()).top
            binding.topBar.updatePadding(top = statusBarInset)
            val navBarInset = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.navigationBars()).bottom
            binding.bottomBar.updatePadding(bottom = navBarInset+dp(10))
            positionLivePhotoBadgeBelowTopBar()
            insets
        }
        ViewCompat.requestApplyInsets(binding.previewRoot)
    }

    private fun livePhotoBadgeView(): View = binding.livePhotoBadge.root

    override fun isCurrentPreviewPage(mediaId: Long): Boolean {
        if (!::previewAdapter.isInitialized) return false
        return previewAdapter.getMediaAt(binding.viewPager.currentItem)?.id == mediaId
    }

    override fun onLivePhotoDetected(mediaId: Long) {
        detectedLivePhotoIds.add(mediaId)
        val current = previewAdapter.getMediaAt(binding.viewPager.currentItem)
        if (current?.id == mediaId && !isFullscreen) {
            showLivePhotoBadgeUi()
            updateLiveExportToggle(binding.viewPager.currentItem)
        }
    }

    private fun updateLiveExportToggle(position: Int) {
        if (!viewModel.config.compressConfig.enabled) {
            binding.liveExportToggle.visibility = View.GONE
            return
        }
        if (isFullscreen) {
            binding.liveExportToggle.visibility = View.GONE
            return
        }
        val media = previewAdapter.getMediaAt(position)
        if (media == null || !shouldShowLivePhotoBadge(media)) {
            binding.liveExportToggle.visibility = View.GONE
            return
        }
        binding.liveExportToggle.visibility = View.VISIBLE
        applyLiveExportToggleState(media.id, animate = false)
    }

    private fun toggleLiveExportMode() {
        val media = previewAdapter.getMediaAt(binding.viewPager.currentItem) ?: return
        if (!shouldShowLivePhotoBadge(media)) return
        viewModel.livePhotoExportPolicy.toggleKeepLive(media.id)
        applyLiveExportToggleState(media.id, animate = true)
    }

    private fun applyLiveExportToggleState(mediaId: Long, animate: Boolean) {
        val keepLive = viewModel.livePhotoExportPolicy.isKeepLive(mediaId)
        binding.liveExportCheckIcon.applyAppearance(keepLive)
        binding.liveExportCheckIcon.setChecked(keepLive, animate = animate)
        binding.tvLiveExportLabel.text = getString(
            if (keepLive) {
                R.string.photochoice_live_export_toggle_live
            } else {
                R.string.photochoice_live_export_toggle_still
            }
        )
        binding.tvLiveExportLabel.setTextColor(
            ContextCompat.getColor(this, R.color.photochoice_preview_text)
        )
        binding.tvLiveExportLabel.alpha = if (keepLive) 1f else 0.58f
        binding.liveExportCheckIcon.alpha = if (keepLive) 1f else 0.58f
        binding.liveExportToggle.contentDescription = getString(
            if (keepLive) {
                R.string.photochoice_live_export_keep
            } else {
                R.string.photochoice_live_export_static
            }
        )
    }

    private fun updateLivePhotoBadge(position: Int) {
        if (!::previewAdapter.isInitialized) return
        val media = previewAdapter.getMediaAt(position)
        if (isFullscreen || media == null || media.type != MediaFile.MediaType.IMAGE) {
            livePhotoBadgeView().visibility = View.GONE
            return
        }
        if (shouldShowLivePhotoBadge(media)) {
            showLivePhotoBadgeUi()
        } else {
            livePhotoBadgeView().visibility = View.GONE
        }
    }

    /**
     * 顶栏/底栏默认在空白区域不会消费触摸事件，触摸会穿透到底层预览页并触发全屏切换。
     * 这里将 chrome 容器设置为可点击，确保只有图片/视频内容容器点击才会切换显隐。
     */
    private fun setupChromeTouchGuard() {
        binding.topBar.isClickable = true
        binding.bottomBar.isClickable = true
        livePhotoBadgeView().isClickable = true
    }

    private fun shouldShowLivePhotoBadge(media: MediaFile): Boolean =
        media.isMotionPhoto ||
            media.id in detectedLivePhotoIds ||
            MotionPhotoDetector.isMotionPhotoCached(media)

    private fun showLivePhotoBadgeUi() {
        val badge = livePhotoBadgeView()
        badge.visibility = View.VISIBLE
        badge.contentDescription = getString(R.string.photochoice_live_photo_hold_hint)
        badge.alpha = 1f
        badge.translationY = 0f
        positionLivePhotoBadgeBelowTopBar()
    }

    private fun positionLivePhotoBadgeBelowTopBar() {
        val badge = livePhotoBadgeView()
        if (badge.visibility != View.VISIBLE) return
        binding.topBar.post {
            val lp = badge.layoutParams as? FrameLayout.LayoutParams ?: return@post
            val gap = (8 * resources.displayMetrics.density).toInt()
            lp.topMargin = binding.topBar.bottom + gap
            badge.layoutParams = lp
        }
    }

    private fun setupSystemBarsAnimationListener() {
        window.isNavigationBarContrastEnforced = false
        ViewCompat.setWindowInsetsAnimationCallback(
            binding.previewRoot,
            object : WindowInsetsAnimationCompat.Callback(
                WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_STOP
            ) {
                override fun onProgress(
                    insets: WindowInsetsCompat,
                    runningAnimations: MutableList<WindowInsetsAnimationCompat>
                ): WindowInsetsCompat = insets

                override fun onEnd(animation: WindowInsetsAnimationCompat) {
                    if (animation.typeMask and WindowInsetsCompat.Type.systemBars() == 0) return
                    runPendingAfterSystemBars()
                }
            }
        )
    }

    private fun runAfterSystemBarsAnimation(action: () -> Unit) {
        pendingAfterSystemBars = action
        binding.previewRoot.removeCallbacks(systemBarsAnimFallback)
        binding.previewRoot.postDelayed(systemBarsAnimFallback, SYSTEM_BARS_ANIM_FALLBACK_MS)
    }

    private fun runPendingAfterSystemBars() {
        val action = pendingAfterSystemBars ?: return
        pendingAfterSystemBars = null
        binding.previewRoot.removeCallbacks(systemBarsAnimFallback)
        action()
    }

    private fun cancelChromeTransition() {
        pendingAfterSystemBars = null
        binding.previewRoot.removeCallbacks(systemBarsAnimFallback)
        binding.topBar.animate().cancel()
        binding.bottomBar.animate().cancel()
    }

    private fun bindCurrentPageGestures() {
        if (isFinishing || isDestroyed) return
        val page = currentPreviewPage()
        if (page == null) {
            if (!isFinishing && !isDestroyed) {
                binding.viewPager.post { bindCurrentPageGestures() }
            }
            return
        }
        // Fragment 在 onAttach 中已从 chromeToggleCallback 自取回调；
        // 此处 setOnSingleTapListener 作为兜底更新（页面切换等情况）
        page.setOnSingleTapListener { toggleFullscreen() }
        page.setOnZoomInteractionListener { _, scaling ->
            updateViewPagerScrollEnabled(scaling)
        }
        updateViewPagerScrollEnabled(scaling = false)
    }

    private fun currentPreviewPage(): PreviewPageFragment? =
        previewPageAt(binding.viewPager.currentItem)

    private fun previewPageAt(position: Int): PreviewPageFragment? {
        if (!::previewAdapter.isInitialized) return null
        if (position !in 0 until previewAdapter.itemCount) return null
        val itemId = previewAdapter.getItemId(position)
        // FragmentStateAdapter 生成的 tag 格式为 "f{viewId}:{itemId}"
        return supportFragmentManager.findFragmentByTag("f${binding.viewPager.id}:$itemId") as? PreviewPageFragment
    }

    private fun syncPageChrome(animated: Boolean) {
        currentPreviewPage()?.syncChromeFromHost(
            fullscreen = isFullscreen,
            animated = animated
        )
    }

    /**
     * 动态控制 ViewPager2 的滑动能力。
     *
     * - pinch 缩放进行中（[scaling]=true）：禁用 ViewPager2，避免缩放手势被误判为切页。
     * - 放大静止或拖拽平移中：[ZoomableImageView] 通过 requestDisallowInterceptTouchEvent
     *   动态控制——图片还能平移时阻止 ViewPager2 拦截，拖到水平边界后释放拦截权以切页。
     */
    private fun updateViewPagerScrollEnabled(scaling: Boolean) {
        binding.viewPager.isUserInputEnabled = !scaling
    }

    private fun toggleFullscreen() {
        cancelChromeTransition()
        isFullscreen = !isFullscreen
        applyDisplayMode(animated = true)
    }

    private fun applyDisplayMode(animated: Boolean) {
        if (!animated) {
            systemUiController.applyFullscreen(isFullscreen)
            applyChromeImmediate(isFullscreen)
            updateLivePhotoBadge(binding.viewPager.currentItem)
            ViewCompat.requestApplyInsets(binding.previewRoot)
            return
        }

        if (isFullscreen) {
            // 1. 先隐藏系统状态栏 / 导航栏；2. 完成后再隐藏预览顶栏 / 底栏
            prepareChromeVisible()
            systemUiController.applyFullscreen(true)
            ViewCompat.requestApplyInsets(binding.previewRoot)
            runAfterSystemBarsAnimation {
                animateChromeHide()
            }
        } else {
            // 1. 先显示系统栏；2. 完成后再显示预览顶栏 / 底栏
            systemUiController.applyFullscreen(false)
            ViewCompat.requestApplyInsets(binding.previewRoot)
            runAfterSystemBarsAnimation {
                animateChromeShow()
            }
        }
    }

    private fun prepareChromeVisible() {
        binding.topBar.apply {
            visibility = View.VISIBLE
            alpha = 1f
            translationY = 0f
        }
        binding.bottomBar.apply {
            visibility = View.VISIBLE
            alpha = 1f
            translationY = 0f
        }
    }

    private fun applyChromeImmediate(fullscreen: Boolean) {
        binding.topBar.animate().cancel()
        binding.bottomBar.animate().cancel()
        livePhotoBadgeView().animate().cancel()
        syncPageChrome(animated = false)
        if (fullscreen) {
            binding.topBar.apply {
                visibility = View.GONE
                alpha = 0f
                translationY = 0f
            }
            binding.bottomBar.apply {
                visibility = View.GONE
                alpha = 0f
                translationY = 0f
            }
            livePhotoBadgeView().visibility = View.GONE
        } else {
            binding.topBar.apply {
                visibility = View.VISIBLE
                alpha = 1f
                translationY = 0f
            }
            binding.bottomBar.apply {
                visibility = View.VISIBLE
                alpha = 1f
                translationY = 0f
            }
            updateLivePhotoBadge(binding.viewPager.currentItem)
            updateLiveExportToggle(binding.viewPager.currentItem)
        }
    }

    private fun animateChromeHide(onEnd: () -> Unit = {}) {
        val topBar = binding.topBar
        val bottomBar = binding.bottomBar
        val liveBadge = livePhotoBadgeView()
        topBar.animate().cancel()
        bottomBar.animate().cancel()
        liveBadge.animate().cancel()
        syncPageChrome(animated = true)

        fun runHide() {
            val topOffset = -topBar.height.toFloat()
            val bottomOffset = bottomBar.height.toFloat()
            var topDone = false
            var bottomDone = false
            var liveDone = liveBadge.visibility != View.VISIBLE
            fun tryFinish() {
                if (topDone && bottomDone && liveDone) {
                    topBar.visibility = View.GONE
                    bottomBar.visibility = View.GONE
                    liveBadge.visibility = View.GONE
                    onEnd()
                }
            }
            topBar.animate()
                .alpha(0f)
                .translationY(topOffset)
                .setDuration(CHROME_ANIM_DURATION_MS)
                .setInterpolator(chromeInterpolator)
                .withEndAction {
                    topDone = true
                    tryFinish()
                }
                .start()
            if (liveBadge.visibility == View.VISIBLE) {
                liveBadge.animate()
                    .alpha(0f)
                    .translationY(topOffset)
                    .setDuration(CHROME_ANIM_DURATION_MS)
                    .setInterpolator(chromeInterpolator)
                    .withEndAction {
                        liveDone = true
                        tryFinish()
                    }
                    .start()
            }
            bottomBar.animate()
                .alpha(0f)
                .translationY(bottomOffset)
                .setDuration(CHROME_ANIM_DURATION_MS)
                .setInterpolator(chromeInterpolator)
                .withEndAction {
                    bottomDone = true
                    tryFinish()
                }
                .start()
        }

        if (topBar.height > 0 && bottomBar.height > 0) {
            runHide()
        } else {
            topBar.post { runHide() }
        }
    }

    private fun animateChromeShow() {
        val topBar = binding.topBar
        val bottomBar = binding.bottomBar
        val liveBadge = livePhotoBadgeView()
        topBar.animate().cancel()
        bottomBar.animate().cancel()
        liveBadge.animate().cancel()
        syncPageChrome(animated = true)

        fun runShow() {
            val topOffset = -topBar.height.toFloat()
            val bottomOffset = bottomBar.height.toFloat()
            topBar.visibility = View.VISIBLE
            bottomBar.visibility = View.VISIBLE
            topBar.alpha = 0f
            bottomBar.alpha = 0f
            topBar.translationY = topOffset
            bottomBar.translationY = bottomOffset
            topBar.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(CHROME_ANIM_DURATION_MS)
                .setInterpolator(chromeInterpolator)
                .start()
            bottomBar.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(CHROME_ANIM_DURATION_MS)
                .setInterpolator(chromeInterpolator)
                .withEndAction {
                    updateLivePhotoBadge(binding.viewPager.currentItem)
                    updateLiveExportToggle(binding.viewPager.currentItem)
                }
                .start()
            val current = previewAdapter.getMediaAt(binding.viewPager.currentItem)
            val showLive = current != null && shouldShowLivePhotoBadge(current)
            if (showLive) {
                liveBadge.visibility = View.VISIBLE
                positionLivePhotoBadgeBelowTopBar()
                liveBadge.alpha = 0f
                liveBadge.translationY = topOffset
                liveBadge.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(CHROME_ANIM_DURATION_MS)
                    .setInterpolator(chromeInterpolator)
                    .start()
            } else {
                liveBadge.visibility = View.GONE
            }
        }

        if (topBar.height > 0 && bottomBar.height > 0) {
            runShow()
        } else {
            topBar.post { runShow() }
        }
    }

    private fun toggleCurrentSelection() {
        val position = binding.viewPager.currentItem
        val mediaFile = previewAdapter.getMediaAt(position) ?: return
        if (!viewModel.toggleSelection(mediaFile)) return
        updateSelectionBox()
    }

    private fun onDoneClicked() {
        if (resultController.isInFlight) return
        if (viewModel.config.isSingleSelect) {
            // 单选：以当前预览页为最终选中项
            val mediaFile = previewAdapter.getMediaAt(binding.viewPager.currentItem) ?: return
            viewModel.selectionManager.select(mediaFile)
        }
        val selected = viewModel.getSelectedItems()
        if (selected.isEmpty()) return
        // 与网格页"完成"完全同构：同一套选中项、同一套压缩判定，就地在预览页压缩，
        // 不再先 finish 跳回网格页再压缩（消除"离开发起页才压缩"的不对称）。
        val exportItems = selected.map { media ->
            SelectionResultProcessor.ExportItem(
                uri = media.uri.toUri(),
                shouldCompress = viewModel.shouldCompressOnExport(media)
            )
        }
        resultController.submit(exportItems, viewModel.config.compressConfig)
    }

    /**
     * 交付预览页处理结果：已就地压缩完成，经 setResult 把结果回传父页（[PhotoChoiceActivity]），
     * 由父页直接交付宿主、不再二次压缩。
     */
    private fun deliverPreviewResult(result: PhotoChoiceResult) {
        setResult(
            Activity.RESULT_OK,
            Intent()
                .putParcelableArrayListExtra(
                    PhotoChoiceActivity.EXTRA_RESULT_URIS, ArrayList(result.uris)
                )
                .putStringArrayListExtra(
                    PhotoChoiceActivity.EXTRA_RESULT_PATHS, ArrayList(result.paths)
                )
        )
        finish()
    }

    /**
     * 刷新标题序号指示器。
     * 分母取「相册真实总数」与「已加载条数」的较大者：相册聚合未就绪时以已加载数兜底，
     * 续载超出过期总数时也不会出现分子大于分母。
     */
    private fun updateIndexIndicator(position: Int) {
        binding.tvPreviewIndex.text = getString(
            R.string.photochoice_preview_index,
            position + 1,
            maxOf(viewModel.previewTotalCount.value, previewAdapter.itemCount)
        )
    }

    private fun updateSelectionBox() {
        // Done 按钮的状态不依赖 previewAdapter 的当前页（单选下只看 config），先刷新按钮，
        // 再尝试刷新 checkbox。这样即使 fragment 尚未就绪也能保证 Done 处于正确的 enabled 状态。
        updateDoneButton()
        val position = binding.viewPager.currentItem
        val mediaFile = previewAdapter.getMediaAt(position) ?: return
        val order = viewModel.getSelectionOrder(mediaFile.id)
        if (order > 0) {
            binding.selectionBox.setBackgroundResource(R.drawable.ripple_checkbox_preview_selected)
            binding.tvSelectionOrder.visibility = View.VISIBLE
            binding.tvSelectionOrder.text = String.format(java.util.Locale.ROOT, "%d", order)
        } else {
            binding.selectionBox.setBackgroundResource(R.drawable.ripple_checkbox_preview_unselected)
            binding.tvSelectionOrder.visibility = View.GONE
        }
    }

    private fun updateDoneButton() {
        val state = viewModel.selectionState.value
        val isSingle = viewModel.config.isSingleSelect
        // 单选下没有"已选中"的中间态，Done 始终可点击
        val canConfirm = isSingle || state.canConfirm
        binding.btnDone.apply {
            text = when {
                isSingle -> getString(R.string.photochoice_done)
                state.count == 0 -> getString(R.string.photochoice_done)
                else -> getString(
                    R.string.photochoice_done_count,
                    state.count,
                    viewModel.config.sanitizedSelectCount
                )
            }
            isEnabled = canConfirm
            setBackgroundResource(
                if (canConfirm) R.drawable.ripple_btn_done_preview_enabled
                else R.drawable.ripple_btn_done_preview_disabled
            )
            setTextColor(
                ContextCompat.getColor(
                    this@PreviewActivity,
                    if (canConfirm) R.color.photochoice_preview_btn_text_enabled
                    else R.color.photochoice_icon_secondary
                )
            )
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.selectionState.collect { updateSelectionBox() }
        }
        lifecycleScope.launch {
            viewModel.livePhotoExportPolicy.revision.collect {
                updateLiveExportToggle(binding.viewPager.currentItem)
            }
        }
        // 预览续载：快照增长时把新增段追加进 ViewPager，并刷新序号分母。
        // 追加前校验边界项 id 对齐，防御快照被整体替换（如新预览会话）时的错误拼接。
        lifecycleScope.launch {
            viewModel.previewMediaList.collect { list ->
                val loaded = previewAdapter.itemCount
                if (list.size > loaded && loaded > 0 &&
                    list[loaded - 1].id == previewAdapter.getMediaAt(loaded - 1)?.id
                ) {
                    previewAdapter.append(list.subList(loaded, list.size))
                    updateIndexIndicator(binding.viewPager.currentItem)
                }
            }
        }
    }

    private fun finishPreview() {
        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_FULLSCREEN, isFullscreen)
    }

    override fun onDestroy() {
        // 进程死亡重建时 onCreate 会因 peek()==null 早退，binding/viewModel 均未初始化；
        // onDestroy 全链路必须判初始化，否则 UninitializedPropertyAccessException 崩溃
        _motionPhotoPlayer?.release()
        _motionPhotoPlayer = null
        if (::binding.isInitialized) {
            cancelChromeTransition()
        }
        if (::systemUiController.isInitialized) {
            systemUiController.restore()
        }
        super.onDestroy()
    }

    override fun finish() {
        super.finish()
    }
}
