package com.google.photochoice.ui.grid

import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.photochoice.R
import com.google.photochoice.config.DesignTokens
import com.google.photochoice.config.SelectMode
import com.google.photochoice.data.model.MediaFile
import com.google.photochoice.databinding.FragmentMediaGridBinding
import com.google.photochoice.ui.PhotoChoiceActivity
import com.google.photochoice.ui.crop.CropActivity
import com.google.photochoice.util.CameraHelper
import com.google.photochoice.util.MediaLoadLogger
import com.google.photochoice.util.PermissionHelper
import com.google.photochoice.util.dp
import com.google.photochoice.viewmodel.PhotoChoiceViewModel
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
                    results.getOrDefault(perm, false) == false &&
                        !ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(), perm)
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
        motionPhotoBadgeResolver?.cancelAll()
        motionPhotoBadgeResolver = null
        gridDateScrollCoordinator?.detach()
        gridDateScrollCoordinator = null
        glidePreloader?.let { binding.recyclerView.removeOnScrollListener(it) }
        glidePreloader = null
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
            viewModel.triggerLoad()
            startMediaObservation()
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
                    if (!viewModel.isSelected(mediaFile.id)) {
                        mediaAdapter.notifyAllSelectionChanged()
                    } else {
                        mediaAdapter.notifyMediaItemChanged(mediaFile.id)
                    }
                }
            },
            motionPhotoBadgeResolver = motionPhotoBadgeResolver,
            isSingleSelect = config.maxSelectCount == 1,
            onItemClick = { mediaFile ->
                if (config.selectMode == SelectMode.SINGLE && config.cropConfig.enabled) {
                    viewModel.selectionManager.select(mediaFile)
                    cropLauncher.launch(
                        CropActivity.intent(
                            requireContext(),
                            mediaFile.uri,
                            config.cropConfig.aspectRatio
                        )
                    )
                } else if (config.showPreview) {
                    val list = mediaAdapter.snapshotMediaList()
                    if (list.isNotEmpty()) {
                        viewModel.updateMediaSnapshot(list)
                        val pos = list.indexOfFirst { it.id == mediaFile.id }.coerceAtLeast(0)
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
            ConcatAdapter(CameraTileAdapter { launchCamera() }, mediaAdapter)
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
            clipToPadding = false
            updatePadding(bottom = bottomContentInset)
        }

        // 向下滚动时预解码前方缩略图，减少占位闪烁
        val leadingOffset = if (config.showCamera) 1 else 0
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

        // 滚动期间暂停 MotionPhoto 嗅探，IDLE 时再批量处理
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
    }

    // ── 数据观察（仅在权限确认后启动，且只启动一次）──────────────────────

    private fun startMediaObservation() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.mediaPagingFlow.collectLatest { pagingData ->
                mediaAdapter.submitData(pagingData)
            }
        }

        // 相机回拍后刷新首页（无需重建整条 Pager.flow）
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.mediaRefreshEvent.collect {
                mediaAdapter.refresh()
            }
        }

        // UI 提示（Toast），集中收口
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

        // 底部栏取消选中后，刷新全部网格选中态（序号会整体前移）
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.deselectedEvent.collect {
                mediaAdapter.notifyAllSelectionChanged()
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
                .collect {
                    binding.recyclerView.scrollToPosition(0)
                    gridDateScrollCoordinator?.reset()
                }
        }
    }

    // ── 相机拍照 ──────────────────────────────────────────────────────────────

    private fun launchCamera() {
        val uri = cameraHelper.createImageUri() ?: return
        pendingCameraUri = uri
        runCatching { takePictureLauncher.launch(uri) }
    }
}
