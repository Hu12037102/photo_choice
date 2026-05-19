package com.google.photochoice.ui.preview

import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
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

    companion object {
        private const val STATE_FULLSCREEN = "state_fullscreen"
    }

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
                    updateIndexIndicator(position)
                    updateSelectionBox()
                    bindCurrentPageGestures()
                }
            })
        }

        updateIndexIndicator(startPosition)
        binding.viewPager.post { bindCurrentPageGestures() }

        binding.btnBack.setOnClickListener { finishPreview() }
        binding.selectionBox.setOnClickListener { toggleCurrentSelection() }
        binding.btnDone.setOnClickListener {
            PhotoChoiceActivity.previewHost?.finishWithResult()
        }

        if (viewModel.config.showOriginalCheckbox) {
            binding.originalContainer.visibility = View.VISIBLE
            binding.originalContainer.setOnClickListener { viewModel.toggleOriginal() }
        } else {
            binding.originalContainer.visibility = View.GONE
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

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.previewRoot) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            if (!isFullscreen) {
                binding.topBar.updatePadding(top = bars.top)
                binding.bottomBar.updatePadding(bottom = bars.bottom)
            } else {
                binding.topBar.updatePadding(top = 0)
                binding.bottomBar.updatePadding(bottom = 0)
            }
            insets
        }
        ViewCompat.requestApplyInsets(binding.previewRoot)
    }

    private fun bindCurrentPageGestures() {
        val page = currentPreviewPage() ?: return
        page.setOnSingleTapListener { toggleFullscreen() }
        page.setOnZoomInteractionListener { zoomed, scaling ->
            updateViewPagerScrollEnabled(zoomed, scaling)
        }
        updateViewPagerScrollEnabled(page.isZoomed(), scaling = false)
    }

    private fun currentPreviewPage(): PreviewPageFragment? {
        if (!::previewAdapter.isInitialized) return null
        val itemId = previewAdapter.getItemId(binding.viewPager.currentItem)
        return supportFragmentManager.findFragmentByTag("f$itemId") as? PreviewPageFragment
    }

    private fun updateViewPagerScrollEnabled(zoomed: Boolean, scaling: Boolean) {
        binding.viewPager.isUserInputEnabled = !zoomed && !scaling
    }

    private fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        applyDisplayMode(animated = true)
    }

    private fun applyDisplayMode(animated: Boolean) {
        systemUiController.applyFullscreen(isFullscreen)
        ViewCompat.requestApplyInsets(binding.previewRoot)

        val duration = if (animated) 200L else 0L
        val chromeAlpha = if (isFullscreen) 0f else 1f
        val chromeVisibility = if (isFullscreen) View.GONE else View.VISIBLE

        if (!isFullscreen) {
            binding.topBar.visibility = View.VISIBLE
            binding.bottomBar.visibility = View.VISIBLE
        }

        binding.topBar.animate().cancel()
        binding.bottomBar.animate().cancel()
        binding.topBar.animate()
            .alpha(chromeAlpha)
            .setDuration(duration)
            .withEndAction {
                binding.topBar.visibility = chromeVisibility
                if (!isFullscreen) binding.topBar.alpha = 1f
            }
            .start()
        binding.bottomBar.animate()
            .alpha(chromeAlpha)
            .setDuration(duration)
            .setInterpolator(
                if (isFullscreen) AccelerateInterpolator() else DecelerateInterpolator()
            )
            .withEndAction {
                binding.bottomBar.visibility = chromeVisibility
                if (!isFullscreen) binding.bottomBar.alpha = 1f
            }
            .start()
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
        lifecycleScope.launch {
            viewModel.isOriginal.collect { isOriginal ->
                binding.btnOriginal.setBackgroundResource(
                    if (isOriginal) R.drawable.bg_checkbox_selected
                    else R.drawable.bg_checkbox_unselected_preview
                )
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
