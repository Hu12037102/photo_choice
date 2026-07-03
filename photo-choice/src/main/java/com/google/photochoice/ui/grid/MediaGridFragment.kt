package com.google.photochoice.ui.grid

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

import com.google.photochoice.R
import com.google.photochoice.config.DesignTokens
import com.google.photochoice.data.model.MediaFile
import com.google.photochoice.databinding.FragmentMediaGridBinding
import com.google.photochoice.ui.PhotoChoiceActivity
import com.google.photochoice.ui.crop.CropActivity
import com.google.photochoice.util.CameraHelper
import com.google.photochoice.util.MediaLoadLogger
import com.google.photochoice.data.motion.MotionPhotoListEnricher
import com.google.photochoice.util.PermissionHelper
import com.google.photochoice.util.dp
import com.google.photochoice.viewmodel.PhotoChoiceViewModel
import com.google.photochoice.viewmodel.PhotoChoiceViewModelStore
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 媒体网格页。
 *
 * 进入后先校验权限：
 *  - 未授权 → 自动发起申请
 *  - 授权后 → 触发媒体加载并启动数据观察
 *  - 永久拒绝 → 展示引导状态（占位图 + 描述 + 前往设置按钮）
 *  - 普通拒绝 → 展示引导状态（占位图 + 描述 + 重试按钮）
 */
class MediaGridFragment : Fragment() {

    private var _binding: FragmentMediaGridBinding? = null
    private val binding get() = _binding!!
    // PhotoChoiceViewModel 由库级 [PhotoChoiceViewModelStore] 在 PhotoChoiceActivity.onCreate 中创建；
    // 这里只读 peek，正常情况下不会为 null（Fragment 一定挂在 PhotoChoiceActivity 内）。
    private val viewModel: PhotoChoiceViewModel
        get() = PhotoChoiceViewModelStore.peek()
            ?: error("PhotoChoiceViewModel not initialized — MediaGridFragment must be hosted in PhotoChoiceActivity")

    private lateinit var mediaAdapter: MediaGridAdapter
    private lateinit var gridAdapter: RecyclerView.Adapter<*>
    private lateinit var cameraHelper: CameraHelper
    private var pendingCameraUri: Uri? = null
    private var gridDateScrollCoordinator: GridDateScrollCoordinator? = null
    private var motionPhotoEnricher: MotionPhotoListEnricher? = null
    private var gridLeadingItemCount = 0
    /** 已 schedule 过的 snapshot 尾部下标，用于分页增量入队。 */
    private var motionEnrichedSnapshotSize = 0
    private var lastVisibleEnrichUptimeMs = 0L

    private var bottomContentInset = 0

    /** 防止 onResume 或重复授权后重复注册数据观察者。 */
    private var mediaObservationStarted = false

    // ── 相机拍照 ──────────────────────────────────────────────────────────────

    private val takePictureLauncher: ActivityResultLauncher<Uri> =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val uri = pendingCameraUri
            pendingCameraUri = null
            if (success && uri != null) viewModel.onCameraPhotoCaptured(uri)
        }

    // ── 裁剪页结果接收 ──────────────────────────────────────────────────────────
    private val cropLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != android.app.Activity.RESULT_OK) return@registerForActivityResult
            val uri = result.data?.getStringExtra(CropActivity.EXTRA_RESULT_URI) ?: return@registerForActivityResult
            (requireActivity() as PhotoChoiceActivity).finishWithCropResult(uri)
        }

    // ── 权限申请 ──────────────────────────────────────────────────────────────

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            if (results.values.all { it }) {
                onPermissionGranted()
            } else {
                // 判断是否永久拒绝：申请后 shouldShowRationale 为 false 且未授权 → 永久拒绝
                val permanentlyDenied = PermissionHelper.requiredMediaPermissions().any { perm ->
                    !results.getOrDefault(perm, false) && !ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(), perm)
                }
                showPermissionState(permanentlyDenied)
            }
        }

    // ── 生命周期 ──────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMediaGridBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cameraHelper = CameraHelper(requireContext())
        setupAdaptersAndRecyclerView()
        checkPermission()
    }

    fun setBottomContentInset(inset: Int) {
        val newInset = inset.coerceAtLeast(0)
        val delta = newInset - bottomContentInset
        bottomContentInset = newInset

        val recyclerView = _binding?.recyclerView ?: return
        if (delta > 0) {
            recyclerView.updatePadding(bottom = newInset)
            recyclerView.scrollBy(0, delta)
        } else {
            recyclerView.scrollBy(0, delta)
            recyclerView.updatePadding(bottom = newInset)
        }
    }

    override fun onResume() {
        super.onResume()
        // 用户可能从系统设置授权后返回，重新检测并恢复正常流程
        if (!mediaObservationStarted && PermissionHelper.hasMediaPermission(requireContext())) {
            onPermissionGranted()
        }
    }

    override fun onDestroyView() {
        gridDateScrollCoordinator?.detach()
        gridDateScrollCoordinator = null
        motionPhotoEnricher = null

        super.onDestroyView()
        _binding = null
    }

    // ── 权限流程 ──────────────────────────────────────────────────────────────

    private fun checkPermission() {
        if (PermissionHelper.hasMediaPermission(requireContext())) {
            onPermissionGranted()
        } else {
            permissionLauncher.launch(PermissionHelper.requiredMediaPermissions())
        }
    }

    private fun onPermissionGranted() {
        showGrid()
        if (!mediaObservationStarted) {
            mediaObservationStarted = true
            viewModel.warmUpMediaPaging()
            startMediaObservation()
            viewModel.triggerLoad()
        }
    }

    /**
     * 展示权限引导状态。
     * @param permanentlyDenied true = 永久拒绝（引导去设置），false = 普通拒绝（可重新申请）
     */
    private fun showPermissionState(permanentlyDenied: Boolean) {
        binding.recyclerView.visibility = View.GONE
        binding.stateContainer.visibility = View.VISIBLE
        binding.ivStateIcon.setImageResource(R.drawable.ic_no_permission)
        binding.ivStateIcon.alpha = 0.4f
        if (permanentlyDenied) {
            binding.tvStateTitle.setText(R.string.photochoice_permission_denied_title)
            binding.tvStateDesc.setText(R.string.photochoice_permission_denied_desc)
            binding.tvStateAction.apply {
                setText(R.string.photochoice_permission_open_settings)
                visibility = View.VISIBLE
                setOnClickListener { openAppSettings() }
            }
        } else {
            binding.tvStateTitle.setText(R.string.photochoice_permission_required_title)
            binding.tvStateDesc.setText(R.string.photochoice_permission_required_desc)
            binding.tvStateAction.apply {
                setText(R.string.photochoice_permission_grant)
                visibility = View.VISIBLE
                setOnClickListener {
                    permissionLauncher.launch(PermissionHelper.requiredMediaPermissions())
                }
            }
        }
    }

    private fun showEmptyMediaState() {
        binding.recyclerView.visibility = View.GONE
        binding.stateContainer.visibility = View.VISIBLE
        binding.ivStateIcon.setImageResource(R.drawable.ic_image)
        binding.ivStateIcon.alpha = 0.3f
        binding.tvStateTitle.setText(R.string.photochoice_empty_title)
        binding.tvStateDesc.setText(R.string.photochoice_no_media)
        binding.tvStateAction.visibility = View.GONE
    }

    private fun showGrid() {
        binding.stateContainer.visibility = View.GONE
        binding.recyclerView.visibility = View.VISIBLE
    }

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", requireContext().packageName, null)
            }
        )
    }

    // ── RecyclerView 初始化（不依赖权限，权限确认后再启动数据观察）─────────

    private fun setupAdaptersAndRecyclerView() {
        val config = viewModel.config
        val spanCount = config.spanCount

        val gridMediaAdapter = MediaGridAdapter(
            isSelected = { viewModel.isSelected(it) },
            getSelectionOrder = { viewModel.getSelectionOrder(it) },
            isFull = { viewModel.selectionState.value.isFull },
            onCheckboxClick = { mediaFile ->
                if (viewModel.toggleSelection(mediaFile)) {
                    if (!viewModel.isSelected(mediaFile.id)) {
                        mediaAdapter.notifyAllSelectionChanged()
                    } else {
                        mediaAdapter.notifyMediaItemChanged(mediaFile.id)
                    }
                }
            },
            isSingleSelect = config.isSingleSelect,
            onItemClick = { mediaFile ->
                if (config.isSingleSelect) {
                    // 单选：点击 item 不算"已选中"，统一走二级页面再确认
                    if (config.cropConfig.enabled) {
                        cropLauncher.launch(
                            CropActivity.intent(
                                requireContext(),
                                mediaFile.uri,
                                config.cropConfig.aspectRatio
                            )
                        )
                    } else {
                        val list = mediaAdapter.snapshotMediaList()
                        if (list.isNotEmpty()) {
                            viewModel.updateMediaSnapshot(list)
                            val pos = list.indexOfFirst { it.id == mediaFile.id }.coerceAtLeast(0)
                            viewModel.navigateToPreview(pos)
                        }
                    }
                } else {
                    val list = mediaAdapter.snapshotMediaList()
                    if (list.isNotEmpty()) {
                        viewModel.updateMediaSnapshot(list)
                        val pos = list.indexOfFirst { it.id == mediaFile.id }.coerceAtLeast(0)
                        viewModel.navigateToPreview(pos)
                    }
                }
            },
            onRequestMotionEnrich = { mediaFile ->
                motionPhotoEnricher?.scheduleOne(mediaFile)
            }
        )
        mediaAdapter = gridMediaAdapter

        gridLeadingItemCount = if (config.showCamera) 1 else 0
        gridAdapter = if (config.showCamera) {
            ConcatAdapter(CameraTileAdapter { launchCamera() }, mediaAdapter)
        } else {
            mediaAdapter
        }

        val gridLayoutManager = GridLayoutManager(context, spanCount).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int = 1
            }
            isItemPrefetchEnabled = true
            // 约 2 行提前 bind；仅布局预取，不触发额外 MediaStore 查询
            initialPrefetchItemCount = spanCount * 2
        }
        binding.recyclerView.apply {
            layoutManager = gridLayoutManager
            // 默认 2；略增以复用 ViewHolder，缩略图内存由 Glide LRU 管控
            setItemViewCacheSize(spanCount * 6)
            adapter = gridAdapter
            addItemDecoration(
                GridSpacingItemDecoration(
                    spacingPx = requireContext().dp(DesignTokens.GRID_SPACING_DP),
                    includeEdge = false
                )
            )
            setHasFixedSize(true)
            itemAnimator = null
            clipToPadding = false
            updatePadding(bottom = bottomContentInset)
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    when (newState) {
                        RecyclerView.SCROLL_STATE_IDLE -> scheduleVisibleMotionEnrichment()
                        RecyclerView.SCROLL_STATE_SETTLING -> scheduleVisibleMotionEnrichmentThrottled()
                    }
                }

                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (dy != 0) scheduleVisibleMotionEnrichmentThrottled()
                }
            })
        }

        gridDateScrollCoordinator = GridDateScrollCoordinator(
            recyclerView = binding.recyclerView,
            mediaAdapter = mediaAdapter,
            leadingItemCount = gridLeadingItemCount,
            dateHeader = (requireActivity() as PhotoChoiceActivity).scrollingDateHeader,
            formatter = DateLabelFormatter(requireContext()),
        ).also { it.attach() }

        mediaAdapter.addOnPagesUpdatedListener {
            MediaLoadLogger.logGridSubmit(
                itemCount = mediaAdapter.itemCount,
                snapshot = mediaAdapter.snapshot().items
            )
        }
    }

    // ── 数据观察（仅在权限确认后启动，且只启动一次）──────────────────────

    private fun startMediaObservation() {
        motionPhotoEnricher = MotionPhotoListEnricher(
            context = requireContext(),
            scope = viewLifecycleOwner.lifecycleScope,
            onMotionDetected = { ids -> mediaAdapter.notifyMotionBadges(ids) }
        )

        mediaAdapter.addOnPagesUpdatedListener {
            val snap = mediaAdapter.snapshot().items
            if (snap.size < motionEnrichedSnapshotSize) {
                motionEnrichedSnapshotSize = 0
                motionPhotoEnricher?.reset()
            }
            if (snap.size > motionEnrichedSnapshotSize) {
                motionPhotoEnricher?.schedule(
                    snap.subList(motionEnrichedSnapshotSize, snap.size)
                )
                motionEnrichedSnapshotSize = snap.size
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.mediaPagingFlow.collectLatest { pagingData ->
                mediaAdapter.submitData(pagingData)
            }
        }

        // 相机回拍后刷新首页（无需重建整条 Pager.flow）
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.mediaRefreshEvent.collect {
                motionEnrichedSnapshotSize = 0
                motionPhotoEnricher?.reset()
                mediaAdapter.refresh()
            }
        }

        // UI 提示（Toast），集中收口
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiMessageEvent.collect { resId ->
                Toast.makeText(requireContext(), resId, Toast.LENGTH_SHORT).show()
            }
        }

        // 选中集变化时刷新网格（含预览页 toggle、底部栏取消等）
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.selectionState
                .map { it.orderedIds }
                .distinctUntilChanged()
                .drop(1)
                .collect { mediaAdapter.notifyAllSelectionChanged() }
        }

        // 加载状态：NotLoading + 0 条 → 空相册状态；否则显示网格
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
            if (isEmpty) showEmptyMediaState() else showGrid()
        }

        // 切换相册后滚动到顶部
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.currentBucketId
                .drop(1)
                .collect {
                    motionEnrichedSnapshotSize = 0
                    motionPhotoEnricher?.reset()
                    binding.recyclerView.scrollToPosition(0)
                    gridDateScrollCoordinator?.reset()
                }
        }
    }

    private fun scheduleVisibleMotionEnrichmentThrottled() {
        val now = SystemClock.uptimeMillis()
        if (now - lastVisibleEnrichUptimeMs < VISIBLE_ENRICH_INTERVAL_MS) return
        lastVisibleEnrichUptimeMs = now
        scheduleVisibleMotionEnrichment()
    }

    private fun scheduleVisibleMotionEnrichment() {
        val lm = binding.recyclerView.layoutManager as? GridLayoutManager ?: return
        val first = lm.findFirstVisibleItemPosition()
        val last = lm.findLastVisibleItemPosition()
        if (first == RecyclerView.NO_POSITION || last == RecyclerView.NO_POSITION) return
        val visible = (first..last).mapNotNull { rvPos ->
            val mediaIndex = rvPos - gridLeadingItemCount
            if (mediaIndex < 0) null else mediaAdapter.mediaAt(mediaIndex)
        }
        motionPhotoEnricher?.scheduleVisible(visible)
    }

    companion object {
        private const val VISIBLE_ENRICH_INTERVAL_MS = 120L
    }

    // ── 相机拍照 ──────────────────────────────────────────────────────────────

    private fun launchCamera() {
        val uri = cameraHelper.createImageUri() ?: return
        pendingCameraUri = uri
        runCatching { takePictureLauncher.launch(uri) }
    }
}
