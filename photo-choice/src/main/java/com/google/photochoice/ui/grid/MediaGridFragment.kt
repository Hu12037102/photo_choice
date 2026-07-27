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
import com.google.photochoice.config.MediaType
import com.google.photochoice.data.MediaRepository
import com.google.photochoice.data.model.MediaFile
import com.google.photochoice.databinding.FragmentMediaGridBinding
import com.google.photochoice.ui.PhotoChoiceActivity
import com.google.photochoice.ui.crop.CropActivity
import com.google.photochoice.util.CameraHelper
import com.google.photochoice.util.MediaLoadLogger
import com.google.photochoice.data.motion.AlbumMotionPrebuilder
import com.google.photochoice.data.motion.MotionPhotoDetector
import com.google.photochoice.data.motion.MotionPhotoIndexStore
import com.google.photochoice.data.motion.MotionPhotoListEnricher
import com.google.photochoice.util.PermissionHelper
import com.google.photochoice.util.dp
import com.google.photochoice.viewmodel.PhotoChoiceViewModel
import com.google.photochoice.viewmodel.PhotoChoiceViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private var albumPrebuilder: AlbumMotionPrebuilder? = null
    /** 首屏是否已首次 IDLE（用于延迟启动预建，让路首屏缩略图）。 */
    private var firstIdlePassed = false
    /** 恢复预建的防抖 Runnable（稳定引用，便于 removeCallbacks 取消过期恢复）。 */
    private val resumePrebuildRunnable = Runnable { albumPrebuilder?.resume() }
    private var gridLeadingItemCount = 0
    private var lastVisibleEnrichUptimeMs = 0L

    private var bottomContentInset = 0

    /** 防止 onResume 或重复授权后重复注册数据观察者。 */
    private var mediaObservationStarted = false

    // ── 相机拍照 ──────────────────────────────────────────────────────────────

    private val takePictureLauncher: ActivityResultLauncher<Uri> =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val uri = pendingCameraUri
            pendingCameraUri = null
            if (success && uri != null) {
                // 拍照成功：清除 IS_PENDING 使照片正式对相册可见，再刷新列表
                cameraHelper.publishImage(uri)
                viewModel.onCameraPhotoCaptured()
            } else if (uri != null) {
                // 取消/失败：删除 createImageUri 预插入的 MediaStore 空行，避免系统相册残留 0 字节孤儿记录
                runCatching { requireContext().contentResolver.delete(uri, null, null) }
            }
        }

    // ── 裁剪页结果接收 ──────────────────────────────────────────────────────────
    private val cropLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != android.app.Activity.RESULT_OK) return@registerForActivityResult
            // CropActivity 已就地压缩完成，父页直接交付，不再二次压缩
            (requireActivity() as PhotoChoiceActivity).deliverPreprocessedResult(result.data)
        }

    // ── 权限申请 ──────────────────────────────────────────────────────────────

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            // 不用 results.all{}：Android 14 部分授权时 VISUAL_USER_SELECTED=granted 而
            // IMAGES/VIDEO=denied——以 hasMediaPermission 的语义化判断为准，部分授权视为可用
            if (PermissionHelper.hasMediaPermission(requireContext())) {
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 跳转系统相机期间本进程极易被回收：恢复 pendingCameraUri，
        // 保证 TakePicture 结果回调能拿到照片（否则照片已入库但列表不刷新、不选中）
        pendingCameraUri = savedInstanceState?.getString(STATE_PENDING_CAMERA_URI)?.toUri()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pendingCameraUri?.let { outState.putString(STATE_PENDING_CAMERA_URI, it.toString()) }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMediaGridBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        MotionPhotoIndexStore.ensureLoaded(requireContext().applicationContext)
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

    override fun onStop() {
        super.onStop()
        // 页面不可见：主动 flush 未落盘的判定，避免丢失
        MotionPhotoIndexStore.requestFlush()
    }

    override fun onDestroyView() {
        gridDateScrollCoordinator?.detach()
        gridDateScrollCoordinator = null
        motionPhotoEnricher = null
        _binding?.recyclerView?.removeCallbacks(resumePrebuildRunnable)
        albumPrebuilder?.cancel()
        albumPrebuilder = null

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
        val spanCount = config.sanitizedSpanCount

        val gridMediaAdapter = MediaGridAdapter(
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
                    // effectiveCropEnabled：仅"单选+IMAGE"生效，VIDEO/ALL 等无效组合已在 config 层降级
                    if (config.effectiveCropEnabled) {
                        cropLauncher.launch(
                            CropActivity.intent(
                                requireContext(),
                                mediaFile.uri,
                                config.cropConfig.aspectRatio,
                                config.cropConfig.maxWidth,
                                config.cropConfig.maxHeight
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
            },
            isLiveExportStatic = { mediaId -> viewModel.isLiveExportStatic(mediaId) }
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
                        RecyclerView.SCROLL_STATE_IDLE -> {
                            scheduleVisibleWindowEnrichment()
                            // 滑动结束防抖后恢复预建（先移除过期恢复，避免快滑时误触）
                            recyclerView.removeCallbacks(resumePrebuildRunnable)
                            recyclerView.postDelayed(resumePrebuildRunnable, PREBUILD_RESUME_DEBOUNCE_MS)
                            onFirstIdle()
                        }
                        RecyclerView.SCROLL_STATE_SETTLING,
                        RecyclerView.SCROLL_STATE_DRAGGING -> {
                            // 滑动期暂停预建，磁盘带宽让给缩略图；取消过期的恢复回调
                            recyclerView.removeCallbacks(resumePrebuildRunnable)
                            albumPrebuilder?.pause()
                            scheduleVisibleWindowEnrichmentThrottled(FAST_ENRICH_INTERVAL_MS)
                        }
                    }
                }

                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (dy != 0) {
                        scheduleVisibleWindowEnrichmentThrottled(FAST_ENRICH_INTERVAL_MS)
                    }
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
        // VIDEO-only 模式不显示实况角标，不创建预建器；后续 start/pause/resume/cancel 皆为空安全 no-op，避免白烧图片库 I/O
        if (viewModel.config.mediaType != MediaType.VIDEO) {
            albumPrebuilder = AlbumMotionPrebuilder(
                context = requireContext().applicationContext,
                repository = MediaRepository(requireContext().applicationContext),
                scope = viewLifecycleOwner.lifecycleScope,
                onMotionDetected = { ids -> mediaAdapter.notifyMotionBadges(ids) }
            )
        }

        warmAlbumMotionIndex(viewModel.currentBucketId.value)

        mediaAdapter.addOnPagesUpdatedListener {
            // 首帧非空后兜底启动预建：若用户先滑动过则 onFirstIdle 已置位，此处 no-op
            if (!firstIdlePassed && mediaAdapter.itemCount > 0) {
                _binding?.recyclerView?.postDelayed(
                    { onFirstIdle() },
                    PREBUILD_FIRST_KICK_DELAY_MS
                )
            }
            scheduleVisibleWindowEnrichment()
            val windowItems = collectMediaInWindow(includePrefetch = true)
            if (windowItems.isNotEmpty()) {
                motionPhotoEnricher?.scheduleBackground(windowItems)
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
                motionPhotoEnricher?.reset()
                mediaAdapter.refresh()
                warmAlbumMotionIndex(viewModel.currentBucketId.value)
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

        // 预览页切换 Live 图"实况/静态"导出后，定点刷新网格对应 item 的 Live 角标样式；
        // 未开压缩时预览页无切换入口，不订阅
        if (viewModel.config.compressConfig.enabled) {
            viewLifecycleOwner.lifecycleScope.launch {
                viewModel.livePhotoExportPolicy.changedMediaId.collect { mediaId ->
                    mediaAdapter.notifyLiveExportChanged(mediaId)
                }
            }
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
                .collect { bucketId ->
                    motionPhotoEnricher?.reset()
                    binding.recyclerView.scrollToPosition(0)
                    gridDateScrollCoordinator?.reset()
                    warmAlbumMotionIndex(bucketId)
                    // 切相册：重启预建(内部会取消上一个 Job；跳过已知)
                    firstIdlePassed = true
                    albumPrebuilder?.start(viewLifecycleOwner, bucketId)
                }
        }
    }

    /**
     * 相册级 MediaStore 预热：有 DB 标记的实况图 bind 时 O(1) 显示；
     * 无标记的（国产 OEM）仍走视口 XMP 通道。
     */
    private fun warmAlbumMotionIndex(bucketId: String?) {
        // 主线程提前捕获 applicationContext：避免在 IO 线程调用 requireContext()——
        // Fragment detach 竞争下 requireContext() 会抛 IllegalStateException
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            MotionPhotoDetector.warmAlbumFromMediaStore(appContext, bucketId)
            withContext(Dispatchers.Main) {
                visibleMediaIndexRange(includePrefetch = true)?.let { range ->
                    mediaAdapter.refreshMotionBadgesForMediaRange(range.first, range.last)
                }
                scheduleVisibleWindowEnrichment()
            }
        }
    }

    /** 首屏首次 IDLE 后启动预建，避免与首屏缩略图抢 I/O。 */
    private fun onFirstIdle() {
        if (firstIdlePassed) return
        if (mediaAdapter.itemCount <= 0) return
        firstIdlePassed = true
        albumPrebuilder?.start(viewLifecycleOwner, viewModel.currentBucketId.value)
    }

    private fun spanCount(): Int =
        (binding.recyclerView.layoutManager as? GridLayoutManager)?.spanCount
            ?: viewModel.config.sanitizedSpanCount

    private fun visibleMediaIndexRange(includePrefetch: Boolean): IntRange? {
        val lm = binding.recyclerView.layoutManager as? GridLayoutManager ?: return null
        val firstRv = lm.findFirstVisibleItemPosition()
        val lastRv = lm.findLastVisibleItemPosition()
        if (firstRv == RecyclerView.NO_POSITION || lastRv == RecyclerView.NO_POSITION) return null
        if (mediaAdapter.itemCount <= 0) return null
        val prefetchRv = if (includePrefetch) spanCount() * PREFETCH_ROWS else 0
        val firstMedia = (firstRv - gridLeadingItemCount).coerceAtLeast(0)
        val lastMedia = (lastRv + prefetchRv - gridLeadingItemCount)
            .coerceAtMost(mediaAdapter.itemCount - 1)
        if (firstMedia > lastMedia) return null
        return firstMedia..lastMedia
    }

    private fun collectMediaInWindow(includePrefetch: Boolean): List<MediaFile> {
        val range = visibleMediaIndexRange(includePrefetch) ?: return emptyList()
        return (range.first..range.last).mapNotNull { mediaAdapter.mediaAt(it) }
    }

    private fun scheduleVisibleWindowEnrichment() {
        val items = collectMediaInWindow(includePrefetch = true)
        if (items.isNotEmpty()) {
            motionPhotoEnricher?.scheduleVisible(items)
        }
    }

    private fun scheduleVisibleWindowEnrichmentThrottled(
        minIntervalMs: Long = VISIBLE_ENRICH_INTERVAL_MS
    ) {
        val now = SystemClock.uptimeMillis()
        if (now - lastVisibleEnrichUptimeMs < minIntervalMs) return
        lastVisibleEnrichUptimeMs = now
        scheduleVisibleWindowEnrichment()
    }

    companion object {
        /** 常规滚动节流。 */
        private const val VISIBLE_ENRICH_INTERVAL_MS = 120L
        /** 快滑 / 拖拽：更 aggressive 地调度视口嗅探。 */
        private const val FAST_ENRICH_INTERVAL_MS = 32L
        /** 可见区下方预取行数（约 1.5 屏）。 */
        private const val PREFETCH_ROWS = 5
        /** 滑动结束后恢复预建的防抖(避免抬手瞬间又滑)。 */
        private const val PREBUILD_RESUME_DEBOUNCE_MS = 300L
        /** 首帧加载后兜底启动预建的延迟(仍让路首屏缩略图；仅在用户未滑动时生效)。 */
        private const val PREBUILD_FIRST_KICK_DELAY_MS = 700L
        /** 进程重建恢复拍照 pending uri 的 saved state key。 */
        private const val STATE_PENDING_CAMERA_URI = "photochoice:pending_camera_uri"
    }

    // ── 相机拍照 ──────────────────────────────────────────────────────────────

    private fun launchCamera() {
        val uri = cameraHelper.createImageUri() ?: return
        pendingCameraUri = uri
        runCatching { takePictureLauncher.launch(uri) }
    }
}
