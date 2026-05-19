package com.google.photochoice.ui.grid

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.photochoice.config.SelectMode
import com.google.photochoice.databinding.FragmentMediaGridBinding
import com.google.photochoice.util.CameraHelper
import com.google.photochoice.util.MediaLoadLogger
import com.google.photochoice.util.dp
import com.google.photochoice.viewmodel.PhotoChoiceViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

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

        mediaAdapter = MediaGridAdapter(
            isSelected = { viewModel.isSelected(it) },
            getSelectionOrder = { viewModel.getSelectionOrder(it) },
            isFull = { viewModel.selectionState.value.isFull },
            onCheckboxClick = { mediaFile ->
                if (viewModel.toggleSelection(mediaFile)) {
                    mediaAdapter.notifyMediaItemChanged(mediaFile.id)
                }
            },
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
                    spacingPx = requireContext().dp(4),
                    includeEdge = false
                )
            )
            addItemDecoration(
                DateDivider(requireContext(), mediaAdapter, leadingItemCount, spanCount)
            )
            setHasFixedSize(true)
            itemAnimator = null
        }

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
                .collect { binding.recyclerView.scrollToPosition(0) }
        }
    }

    private fun launchCamera() {
        val uri = cameraHelper.createImageUri() ?: return
        pendingCameraUri = uri
        runCatching { takePictureLauncher.launch(uri) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
