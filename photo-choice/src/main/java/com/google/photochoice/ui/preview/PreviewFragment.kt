package com.google.photochoice.ui.preview

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.google.photochoice.R
import com.google.photochoice.databinding.FragmentPreviewBinding
import com.google.photochoice.viewmodel.PhotoChoiceViewModel
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 大图预览页。
 */
class PreviewFragment : Fragment() {

    private var _binding: FragmentPreviewBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PhotoChoiceViewModel by viewModels(
        ownerProducer = { requireActivity() }
    )

    private lateinit var previewAdapter: PreviewAdapter

    private var dragStartY = 0f
    private var dragStartX = 0f
    private var isDragging = false
    private var touchSlop = 0
    private var screenHeight = 0
    private var barsVisible = true

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        touchSlop = ViewConfiguration.get(requireContext()).scaledTouchSlop
        screenHeight = resources.displayMetrics.heightPixels

        val mediaList = viewModel.previewMediaList.value
        if (mediaList.isEmpty()) {
            viewModel.dismissPreview()
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
                }
            })
        }

        updateIndexIndicator(startPosition)

        binding.btnBack.setOnClickListener { viewModel.dismissPreview() }
        binding.selectionBox.setOnClickListener { toggleCurrentSelection() }
        binding.btnDone.setOnClickListener {
            (activity as? com.google.photochoice.ui.PhotoChoiceActivity)?.finishWithResult()
        }
        binding.previewRoot.setOnClickListener { toggleBars() }

        if (viewModel.config.showOriginalCheckbox) {
            binding.originalContainer.visibility = View.VISIBLE
            binding.originalContainer.setOnClickListener { viewModel.toggleOriginal() }
        } else {
            binding.originalContainer.visibility = View.GONE
        }

        binding.dragLayer.setOnTouchListener { _, event -> handleDragDismiss(event) }

        observeState()
        updateSelectionBox()
    }

    private fun handleDragDismiss(event: MotionEvent): Boolean {
        val currentFragment = getCurrentPageFragment()

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragStartY = event.rawY
                dragStartX = event.rawX
                isDragging = false
                return false
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                // 进入双指操作，让 page fragment 全权接管
                isDragging = false
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount > 1) return false
                if (currentFragment?.isZoomed() == true) return false
                val dy = event.rawY - dragStartY
                val dx = event.rawX - dragStartX
                if (!isDragging) {
                    if (abs(dy) > touchSlop && abs(dy) > abs(dx) * 1.5f && dy > 0) {
                        isDragging = true
                        binding.viewPager.isUserInputEnabled = false
                        currentFragment?.pauseVideo()
                    } else {
                        return false
                    }
                }
                if (isDragging && dy > 0) {
                    val fraction = (dy / (screenHeight * 0.5f)).coerceIn(0f, 1f)
                    binding.dragLayer.translationY = dy
                    val scale = 1f - fraction * 0.25f
                    binding.dragLayer.scaleX = scale.coerceAtLeast(0.6f)
                    binding.dragLayer.scaleY = scale.coerceAtLeast(0.6f)
                    val bgAlpha = ((1f - fraction * 0.6f) * 255).toInt().coerceIn(0, 255)
                    binding.previewRoot.setBackgroundColor(Color.argb(bgAlpha, 0, 0, 0))
                    binding.topBar.alpha = 1f - fraction
                    binding.bottomBar.alpha = 1f - fraction
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    val dy = event.rawY - dragStartY
                    if (dy > screenHeight * 0.25f) {
                        dismissWithAnimation()
                    } else {
                        springBack()
                    }
                    isDragging = false
                    binding.viewPager.isUserInputEnabled = true
                    return true
                }
                return false
            }
        }
        return false
    }

    private fun dismissWithAnimation() {
        binding.dragLayer.animate()
            .translationY(screenHeight.toFloat())
            .scaleX(0.6f)
            .scaleY(0.6f)
            .alpha(0f)
            .setDuration(200)
            .setInterpolator(AccelerateInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    viewModel.dismissPreview()
                }
            })
            .start()
    }

    private fun springBack() {
        binding.dragLayer.animate()
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(200)
            .setInterpolator(DecelerateInterpolator())
            .start()
        binding.previewRoot.setBackgroundColor(
            ContextCompat.getColor(requireContext(), R.color.photochoice_preview_bg)
        )
        binding.topBar.animate().alpha(if (barsVisible) 1f else 0f).setDuration(200).start()
        binding.bottomBar.animate().alpha(if (barsVisible) 1f else 0f).setDuration(200).start()
        getCurrentPageFragment()?.playVideo()
    }

    private fun toggleBars() {
        barsVisible = !barsVisible
        val target = if (barsVisible) 1f else 0f
        binding.topBar.animate().alpha(target).setDuration(150).start()
        binding.bottomBar.animate().alpha(target).setDuration(150).start()
    }

    private fun toggleCurrentSelection() {
        val position = binding.viewPager.currentItem
        val mediaFile = previewAdapter.getMediaAt(position) ?: return
        val ok = viewModel.toggleSelection(mediaFile)
        if (!ok) return
        updateSelectionBox()
    }

    private fun updateIndexIndicator(position: Int) {
        val total = previewAdapter.itemCount
        binding.tvPreviewIndex.text =
            getString(R.string.photochoice_preview_index, position + 1, total)
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
            binding.selectionBox.setBackgroundResource(R.drawable.bg_checkbox_unselected)
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
                    context,
                    if (canConfirm) R.color.photochoice_on_accent
                    else R.color.photochoice_icon_secondary
                )
            )
        }
    }

    private fun getCurrentPageFragment(): PreviewPageFragment? {
        val position = binding.viewPager.currentItem
        return childFragmentManager.findFragmentByTag("f$position") as? PreviewPageFragment
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.selectionState.collect { updateSelectionBox() }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isOriginal.collect { isOriginal ->
                binding.btnOriginal.setBackgroundResource(
                    if (isOriginal) R.drawable.bg_checkbox_selected
                    else R.drawable.bg_checkbox_unselected
                )
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
