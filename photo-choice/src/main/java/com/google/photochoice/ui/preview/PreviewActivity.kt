package com.google.photochoice.ui.preview

import android.os.Bundle
import android.view.View
import android.view.animation.Interpolator
import androidx.activity.OnBackPressedCallback
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.google.photochoice.R
import com.google.photochoice.databinding.ActivityPreviewBinding
import com.google.photochoice.ui.PhotoChoiceActivity
import com.google.photochoice.viewmodel.PhotoChoiceViewModel
import kotlinx.coroutines.launch

/**
 * 媒体大图预览页（独立 Activity）。
 *
 * 图片缩放：仅双击、双指 pinch（见 [ZoomableImageView]）。
 * 单击图片区域切换全屏 / 非全屏；关闭预览请使用返回键或顶栏返回按钮。
 */
class PreviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPreviewBinding
    private lateinit var viewModel: PhotoChoiceViewModel
    private lateinit var previewAdapter: PreviewAdapter
    private lateinit var systemUiController: PreviewSystemUiController

    private var isFullscreen = false
    private var pendingAfterSystemBars: (() -> Unit)? = null
    private var lastPagePosition = -1

    companion object {
        private const val STATE_FULLSCREEN = "state_fullscreen"
        private const val CHROME_ANIM_DURATION_MS = 280L
        /** 部分机型系统栏 insets 动画无回调时的兜底等待。 */
        private const val SYSTEM_BARS_ANIM_FALLBACK_MS = 400L
    }

    private val chromeInterpolator: Interpolator = FastOutSlowInInterpolator()

    private val systemBarsAnimFallback = Runnable { runPendingAfterSystemBars() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val host = PhotoChoiceActivity.previewHost
        if (host == null) {
            finish()
            return
        }
        viewModel = ViewModelProvider(host)[PhotoChoiceViewModel::class.java]

        enableEdgeToEdge()
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
            offscreenPageLimit = 1
            setCurrentItem(startPosition, false)
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    if (lastPagePosition >= 0 && lastPagePosition != position) {
                        previewPageAt(lastPagePosition)?.pauseVideo()
                    }
                    lastPagePosition = position
                    updateIndexIndicator(position)
                    updateSelectionBox()
                    bindCurrentPageGestures()
                    syncPageChrome(animated = false)
                }
            })
        }

        lastPagePosition = startPosition
        updateIndexIndicator(startPosition)
        binding.viewPager.post {
            bindCurrentPageGestures()
            syncPageChrome(animated = false)
        }

        binding.btnBack.setOnClickListener { finishPreview() }
        binding.selectionBox.setOnClickListener { toggleCurrentSelection() }
        binding.btnDone.setOnClickListener {
            PhotoChoiceActivity.previewHost?.finishWithResult()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isFullscreen) {
                    toggleFullscreen()
                } else {
                    finishPreview()
                }
            }
        })

        observeState()
        updateSelectionBox()
        applyDisplayMode(animated = false)

        overridePendingTransition(android.R.anim.fade_in, 0)
    }

    /**
     * 根布局不消费系统栏间距（padding 恒为 0，图片全屏铺满）；
     * 顶栏 / 底栏各自叠加状态栏、导航栏 inset。
     */
    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.previewRoot) { root, insets ->
            root.updatePadding(0, 0, 0, 0)
            val statusBarInset = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars()).top
            binding.topBar.updatePadding(top = statusBarInset)
            val navBarInset = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.navigationBars()).bottom
            binding.bottomBar.updatePadding(bottom = navBarInset)
            insets
        }
        ViewCompat.requestApplyInsets(binding.previewRoot)
    }

    private fun setupSystemBarsAnimationListener() {
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
        val page = currentPreviewPage() ?: return
        page.setOnSingleTapListener { toggleFullscreen() }
        page.setOnZoomInteractionListener { zoomed, scaling ->
            updateViewPagerScrollEnabled(zoomed, scaling)
        }
        updateViewPagerScrollEnabled(page.isZoomed(), scaling = false)
    }

    private fun currentPreviewPage(): PreviewPageFragment? =
        previewPageAt(binding.viewPager.currentItem)

    private fun previewPageAt(position: Int): PreviewPageFragment? {
        if (!::previewAdapter.isInitialized) return null
        if (position !in 0 until previewAdapter.itemCount) return null
        val itemId = previewAdapter.getItemId(position)
        return supportFragmentManager.findFragmentByTag("f$itemId") as? PreviewPageFragment
    }

    private fun syncPageChrome(animated: Boolean) {
        currentPreviewPage()?.syncChromeFromHost(
            fullscreen = isFullscreen,
            animated = animated
        )
    }

    private fun updateViewPagerScrollEnabled(zoomed: Boolean, scaling: Boolean) {
        binding.viewPager.isUserInputEnabled = !zoomed && !scaling
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
        }
    }

    private fun animateChromeHide(onEnd: () -> Unit = {}) {
        val topBar = binding.topBar
        val bottomBar = binding.bottomBar
        topBar.animate().cancel()
        bottomBar.animate().cancel()
        syncPageChrome(animated = true)

        fun runHide() {
            val topOffset = -topBar.height.toFloat()
            val bottomOffset = bottomBar.height.toFloat()
            var topDone = false
            var bottomDone = false
            fun tryFinish() {
                if (topDone && bottomDone) {
                    topBar.visibility = View.GONE
                    bottomBar.visibility = View.GONE
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
        topBar.animate().cancel()
        bottomBar.animate().cancel()
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
                .start()
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

    private fun updateIndexIndicator(position: Int) {
        binding.tvPreviewIndex.text = getString(
            R.string.photochoice_preview_index,
            position + 1,
            previewAdapter.itemCount
        )
    }

    private fun updateSelectionBox() {
        val position = binding.viewPager.currentItem
        val mediaFile = previewAdapter.getMediaAt(position) ?: return
        val order = viewModel.getSelectionOrder(mediaFile.id)
        if (order > 0) {
            binding.selectionBox.setBackgroundResource(R.drawable.bg_checkbox_selected)
            binding.tvSelectionOrder.visibility = View.VISIBLE
            binding.tvSelectionOrder.text = String.format(java.util.Locale.ROOT, "%d", order)
        } else {
            binding.selectionBox.setBackgroundResource(R.drawable.bg_checkbox_unselected_preview)
            binding.tvSelectionOrder.visibility = View.GONE
        }
        updateDoneButton()
    }

    private fun updateDoneButton() {
        val state = viewModel.selectionState.value
        val canConfirm = state.canConfirm
        binding.btnDone.apply {
            text = if (state.count == 0) {
                getString(R.string.photochoice_done)
            } else {
                getString(
                    R.string.photochoice_done_count,
                    state.count,
                    viewModel.config.maxSelectCount
                )
            }
            isEnabled = canConfirm
            setBackgroundResource(
                if (canConfirm) R.drawable.bg_btn_done_enabled
                else R.drawable.bg_btn_done_disabled
            )
            setTextColor(
                ContextCompat.getColor(
                    this@PreviewActivity,
                    if (canConfirm) R.color.photochoice_on_accent
                    else R.color.photochoice_icon_secondary
                )
            )
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.selectionState.collect { updateSelectionBox() }
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
        cancelChromeTransition()
        if (::systemUiController.isInitialized) {
            systemUiController.restore()
        }
        viewModel.dismissPreview()
        super.onDestroy()
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, android.R.anim.fade_out)
    }
}
