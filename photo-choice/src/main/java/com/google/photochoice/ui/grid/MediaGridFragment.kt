package com.google.photochoice.ui.grid

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.photochoice.config.SelectMode
import com.google.photochoice.databinding.FragmentMediaGridBinding
import com.google.photochoice.config.DesignTokens
import com.google.photochoice.data.model.MediaFile
import com.google.photochoice.util.CameraHelper
import com.google.photochoice.ui.PhotoChoiceActivity
import com.google.photochoice.util.MediaLoadLogger
import com.google.photochoice.util.dp
import com.google.photochoice.viewmodel.PhotoChoiceViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.core.net.toUri

/**
 * 媒体网格页（主选择页）。
 */
class MediaGridFragment : Fragment() {

    private var _binding: FragmentMediaGridBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PhotoChoiceViewModel by viewModels(
        ownerProducer = { requireActivity() }
    )

    private lateinit var mediaAdapter: MediaGridAdapter
    private lateinit var gridAdapter: RecyclerView.Adapter<*>
    private lateinit var cameraHelper: CameraHelper
    private var pendingCameraUri: Uri? = null
    private var gridDateScrollCoordinator: GridDateScrollCoordinator? = null
    private var motionPhotoBadgeResolver: MotionPhotoBadgeResolver? = null
    private var glidePreloader: RecyclerView.OnScrollListener? = null

    private val takePictureLauncher: ActivityResultLauncher<Uri> =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val uri = pendingCameraUri
            pendingCameraUri = null
            if (success && uri != null) {
                viewModel.onCameraPhotoCaptured(uri)
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMediaGridBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        cameraHelper = CameraHelper(requireContext())
        val config = viewModel.config
        val spanCount = config.spanCount

        lateinit var gridMediaAdapter: MediaGridAdapter
        motionPhotoBadgeResolver = MotionPhotoBadgeResolver(
            context = requireContext(),
            scope = viewLifecycleOwner.lifecycleScope,
            onItemDetected = { mediaId -> gridMediaAdapter.notifyMotionPhotoItemChanged(mediaId) }
        )

        gridMediaAdapter = MediaGridAdapter(
            isSelected = { viewModel.isSelected(it) },
            getSelectionOrder = { viewModel.getSelectionOrder(it) },
            isFull = { viewModel.selectionState.value.isFull },
            onCheckboxClick = { mediaFile ->
                if (viewModel.toggleSelection(mediaFile)) {
                    mediaAdapter.notifyMediaItemChanged(mediaFile.id)
                }
            },
            motionPhotoBadgeResolver = motionPhotoBadgeResolver,
            onItemClick = { mediaFile ->
                if (config.selectMode == SelectMode.SINGLE && config.cropConfig.enabled) {
                    viewModel.selectionManager.select(mediaFile)
                    viewModel.navigateToCrop(mediaFile.uri)
                } else if (config.showPreview) {
                    val list = mediaAdapter.snapshotMediaList()
                    if (list.isNotEmpty()) {
                        viewModel.updateMediaSnapshot(list)
                        val pos = list.indexOfFirst { it.id == mediaFile.id }
                            .coerceAtLeast(0)
                        viewModel.navigateToPreview(pos)
                    }
                } else {
                    if (viewModel.toggleSelection(mediaFile)) {
                        mediaAdapter.notifyMediaItemChanged(mediaFile.id)
                    }
                }
            }
        )
        mediaAdapter = gridMediaAdapter

        val leadingItemCount = if (config.showCamera) 1 else 0
        gridAdapter = if (config.showCamera) {
            ConcatAdapter(
                CameraTileAdapter { launchCamera() },
                mediaAdapter
            )
        } else {
            mediaAdapter
        }

        binding.recyclerView.apply {
            layoutManager = GridLayoutManager(context, spanCount).apply {
                spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int = 1
                }
            }
            adapter = gridAdapter
            addItemDecoration(
                GridSpacingItemDecoration(
                    spacingPx = requireContext().dp(DesignTokens.GRID_SPACING_DP),
                    includeEdge = false
                )
            )
            setHasFixedSize(true)
            itemAnimator = null
        }

        // 在滚动方向上预解码 spanCount*2 行的缩略图，减少滑动时的占位闪烁
        val leadingOffset = if (viewModel.config.showCamera) 1 else 0
        val preloadWindow = spanCount * 2
        glidePreloader = object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val lm = recyclerView.layoutManager as? GridLayoutManager ?: return
                val last = lm.findLastVisibleItemPosition()
                for (i in last + 1..last + preloadWindow) {
                    val mediaPos = i - leadingOffset
                    if (mediaPos < 0) continue
                    val item = mediaAdapter.mediaAt(mediaPos) ?: continue
                    Glide.with(this@MediaGridFragment)
                        .load(item.uri.toUri())
                        .override(MediaGridAdapter.THUMBNAIL_PX)
                        .centerCrop()
                        .preload()
                }
            }
        }
        binding.recyclerView.addOnScrollListener(glidePreloader!!)

        // 滚动期间挂起 MotionPhoto 嗅探，IDLE 时再批量发起，避免快速滑动打满 IO 调度器
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                motionPhotoBadgeResolver?.setPaused(newState != RecyclerView.SCROLL_STATE_IDLE)
            }
        })

        gridDateScrollCoordinator = GridDateScrollCoordinator(
            recyclerView = binding.recyclerView,
            mediaAdapter = mediaAdapter,
            leadingItemCount = leadingItemCount,
            dateHeader = (requireActivity() as PhotoChoiceActivity).scrollingDateHeader,
            formatter = DateLabelFormatter(requireContext()),
        ).also { it.attach() }

        mediaAdapter.addOnPagesUpdatedListener {
            MediaLoadLogger.logGridSubmit(
                itemCount = mediaAdapter.itemCount,
                snapshot = mediaAdapter.snapshot().items
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.mediaPagingFlow.collectLatest { pagingData ->
                mediaAdapter.submitData(pagingData)
            }
        }

        // 相机回拍后只 invalidate 当前 PagingSource，无需重建整条 Pager.flow
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.mediaRefreshEvent.collect {
                mediaAdapter.refresh()
            }
        }

        // UI 消息（已达选择上限、相机抓取失败等）统一收口，避免散落的 Toast 调用
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiMessageEvent.collect { resId ->
                Toast.makeText(requireContext(), resId, Toast.LENGTH_SHORT).show()
            }
        }

        // 选中数量变化时刷新全部以更新「未选 item 是否变灰」
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.selectionState
                .map { it.isFull }
                .distinctUntilChanged()
                .drop(1)
                .collect { mediaAdapter.notifyAllSelectionChanged() }
        }

        mediaAdapter.addLoadStateListener { loadState ->
            MediaLoadLogger.logGridLoadState(
                refresh = loadState.refresh::class.java.simpleName,
                append = loadState.append::class.java.simpleName,
                itemCount = mediaAdapter.itemCount,
                error = loadState.refresh.takeIf { it is LoadState.Error }
                    ?.let { (it as LoadState.Error).error }
                    ?: loadState.append.takeIf { it is LoadState.Error }
                        ?.let { (it as LoadState.Error).error }
            )
            val isNotLoading = loadState.refresh is LoadState.NotLoading
            val isEmpty = isNotLoading && mediaAdapter.itemCount == 0
            binding.emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.currentBucketId
                .drop(1)
                .collect {
                    binding.recyclerView.scrollToPosition(0)
                    gridDateScrollCoordinator?.reset()
                }
        }
    }

    private fun launchCamera() {
        val uri = cameraHelper.createImageUri() ?: return
        pendingCameraUri = uri
        runCatching { takePictureLauncher.launch(uri) }
    }

    override fun onDestroyView() {
        motionPhotoBadgeResolver?.cancelAll()
        motionPhotoBadgeResolver = null
        gridDateScrollCoordinator?.detach()
        gridDateScrollCoordinator = null
        glidePreloader?.let { binding.recyclerView.removeOnScrollListener(it) }
        glidePreloader = null
        super.onDestroyView()
        _binding = null
    }
}
