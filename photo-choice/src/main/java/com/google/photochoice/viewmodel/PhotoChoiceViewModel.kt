package com.google.photochoice.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.google.photochoice.R
import com.google.photochoice.config.MediaType
import com.google.photochoice.config.PhotoChoiceConfig
import com.google.photochoice.data.AlbumRepository
import com.google.photochoice.data.MediaPagingSource
import com.google.photochoice.data.MediaRepository
import com.google.photochoice.data.model.Album
import com.google.photochoice.data.model.MediaFile
import com.google.photochoice.data.motion.MotionPhotoDetector
import com.google.photochoice.util.CompressExportPolicy
import com.google.photochoice.util.MediaLoadLogger
import com.google.photochoice.util.SandboxCleaner
import com.google.photochoice.util.SandboxCleanupScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

class PhotoChoiceViewModel(
    application: Application,
    val config: PhotoChoiceConfig
) : AndroidViewModel(application) {

    private val repository = MediaRepository(application)
    private val albumRepository = AlbumRepository(application)

    val selectionManager = SelectionManager(config)
    val selectionState: StateFlow<SelectionState> = selectionManager.selectionState

    /** Live 图在开启压缩时的导出偏好（预览底栏切换）。 */
    val livePhotoExportPolicy = LivePhotoExportPolicy()

    private val _currentBucketId = MutableStateFlow<String?>(null)
    val currentBucketId: StateFlow<String?> = _currentBucketId.asStateFlow()

    private val defaultAlbumName: String = when (config.mediaType) {
        MediaType.VIDEO -> application.getString(R.string.photochoice_all_videos)
        else -> application.getString(R.string.photochoice_all_photos)
    }

    private val _currentAlbumName = MutableStateFlow(defaultAlbumName)
    val currentAlbumName: StateFlow<String> = _currentAlbumName.asStateFlow()

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

    // 预览页所需快照（来自 grid adapter 当前页面数据）
    private val _previewMediaList = MutableStateFlow<List<MediaFile>>(emptyList())
    val previewMediaList: StateFlow<List<MediaFile>> = _previewMediaList.asStateFlow()

    private val _previewStartPosition = MutableStateFlow(0)
    val previewStartPosition: StateFlow<Int> = _previewStartPosition.asStateFlow()

    // 预览页标题分母（当前相册真实总数）。网格进入时由 resolveAlbumTotalCount() 解析，
    // 口径与相册下拉一致；"预览已选中项"场景为选中数。
    private val _previewTotalCount = MutableStateFlow(0)
    val previewTotalCount: StateFlow<Int> = _previewTotalCount.asStateFlow()

    /** 预览数据源是否允许向后续载：网格进入=true；"预览已选"为固定集合=false。 */
    private var previewCanLoadMore = false

    /** 预览续载是否已到底（末页不足一页即到底），避免到底后反复空查询。 */
    private var previewListExhausted = false

    /** 预览续载任务，同一时刻至多一个在途，防止快速翻页时并发重复查询。 */
    private var previewLoadMoreJob: Job? = null

    // 打开预览页是一次性导航事件：必须用 SharedFlow 而非 StateFlow——
    // StateFlow 当事件用会在 Activity 重建时把旧的 true 回放给新 collector，
    // 特定时序下（下层 Activity 先于预览页 onDestroy 重建）会把用户刚关掉的预览页再拉起来
    private val _showPreviewEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val showPreviewEvent: SharedFlow<Unit> = _showPreviewEvent.asSharedFlow()

    // 相机回拍等"原列表已加载、需重取首页"的场景，仅广播事件由 Fragment 调用 adapter.refresh()；
    // 不再触发整条 Pager Flow 重建（旧实现会丢弃 cachedIn 缓存并重新订阅，浪费）。
    private val _mediaRefreshEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val mediaRefreshEvent: SharedFlow<Unit> = _mediaRefreshEvent.asSharedFlow()

    // UI 提示事件（Toast 文案的 string res id）。集中由 Fragment 消费，避免散落的 Toast 调用。
    private val _uiMessageEvent = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val uiMessageEvent: SharedFlow<Int> = _uiMessageEvent.asSharedFlow()

    // 底部栏取消选中时广播，Fragment 刷新网格选中态
    private val _deselectedEvent = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val deselectedEvent: SharedFlow<Long> = _deselectedEvent.asSharedFlow()

    /** 保持对分页 Flow 的订阅，使 cachedIn 在 Fragment 绑定前即开始首屏查询。 */
    private var pagingWarmUpJob: Job? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    val mediaPagingFlow: Flow<PagingData<MediaFile>> =
        _currentBucketId
            .flatMapLatest { bucketId ->
                Pager(
                    config = PagingConfig(
                        pageSize = GridPaging.pageSize(config.sanitizedSpanCount),
                        initialLoadSize = GridPaging.initialLoadSize(config.sanitizedSpanCount),
                        prefetchDistance = GridPaging.prefetchDistance(config.sanitizedSpanCount),
                        enablePlaceholders = false,
                        // 不设内存上限：MediaFile 是轻量元数据(不含缩略图像素)，全量常驻内存
                        // 代价很小；曾经设过 maxSize 淘汰最远页，但淘汰后既无法正确回填、
                        // 也导致预览页用的 snapshot 总数对不上相册真实总数，弊大于利。
                        maxSize = PagingConfig.MAX_SIZE_UNBOUNDED
                    )
                ) {
                    MediaPagingSource(
                        repository = repository,
                        bucketId = bucketId,
                        mediaType = config.mediaType,
                        minVideoDurationMs = config.sanitizedMinVideoDurationMs,
                        maxVideoDurationMs = config.sanitizedMaxVideoDurationMs,
                        minImageSizeBytes = config.sanitizedMinImageSize,
                        maxImageSizeBytes = config.sanitizedMaxImageSize
                    )
                }.flow
            }
            .cachedIn(viewModelScope)

    init {
        // 沙盒清理是磁盘 IO，进选择器时与首屏抢主线程；扔到 IO 后台异步执行
        viewModelScope.launch(Dispatchers.IO) {
            SandboxCleaner(application).cleanExpired()
        }
        loadAlbums()
    }

    /**
     * 选择器生命周期结束（返回 / 完成 / 进程回收）时清理本会话产生的实况内嵌视频缓存，
     * 避免退出后 photo_choice_motion 目录残留几十兆占用（见 [SandboxCleaner.cleanMotionCache]）。
     *
     * 注意：此时 viewModelScope 已取消，不能用它启动清理；改用 SandboxCleaner 内部
     * 各方法自身的同步删除逻辑，配合应用级 IO 调度执行。onCleared 由主线程回调，
     * 磁盘删除放到 IO 线程，避免阻塞。
     */
    override fun onCleared() {
        super.onCleared()
        val appContext = getApplication<Application>()
        // 用与 viewModelScope 解耦的应用级作用域执行；清理是轻量删除，不追踪其完成
        SandboxCleanupScope.launchCleanup {
            SandboxCleaner(appContext).cleanMotionCache()
        }
    }

    /** Fragment 在确认权限后主动调用，触发相册列表刷新。 */
    fun triggerLoad() {
        loadAlbums()
    }

    /** 权限就绪后尽早订阅分页 Flow，与 Fragment 初始化并行拉取首屏数据。 */
    fun warmUpMediaPaging() {
        if (pagingWarmUpJob?.isActive == true) return
        pagingWarmUpJob = viewModelScope.launch {
            mediaPagingFlow.collect { /* cachedIn 预热；submitData 由 Fragment 负责 */ }
        }
    }

    private fun loadAlbums() {
        viewModelScope.launch {
            runCatching {
                albumRepository.loadAlbums(
                    mediaType = config.mediaType,
                    minVideoDurationMs = config.sanitizedMinVideoDurationMs,
                    maxVideoDurationMs = config.sanitizedMaxVideoDurationMs,
                    minImageSizeBytes = config.sanitizedMinImageSize,
                    maxImageSizeBytes = config.sanitizedMaxImageSize
                )
            }.onSuccess { _albums.value = it }
        }
    }

    fun switchAlbum(bucketId: String?, displayName: String) {
        if (_currentBucketId.value == bucketId) return
        _currentBucketId.value = bucketId
        _currentAlbumName.value = displayName.ifBlank { defaultAlbumName }
    }

    fun updateMediaSnapshot(list: List<MediaFile>) {
        _previewMediaList.value = list
    }

    /**
     * 从网格进入预览：以网格当前已加载快照为初始数据，标题分母取相册真实总数，
     * 并允许预览页滑近末尾时继续向后分页加载（见 [onPreviewPageSelected]）。
     */
    fun navigateToPreview(position: Int) {
        if (_previewMediaList.value.isEmpty()) return
        previewCanLoadMore = true
        previewListExhausted = false
        _previewTotalCount.value = resolveAlbumTotalCount()
        _previewStartPosition.value = position.coerceIn(0, _previewMediaList.value.lastIndex)
        _showPreviewEvent.tryEmit(Unit)
    }

    /**
     * 解析当前相册的真实总数，口径与相册下拉一致：
     * - 全部（bucketId=null）：所有相册计数之和；
     * - 指定相册：对应 bucket 的计数。
     * 相册聚合为异步加载，若尚未就绪则以已加载快照数兜底，保证分母不小于可滑动范围。
     */
    private fun resolveAlbumTotalCount(): Int {
        val bucketId = _currentBucketId.value
        val albumTotal = if (bucketId == null) {
            _albums.value.sumOf { it.mediaCount }
        } else {
            _albums.value.firstOrNull { it.bucketId == bucketId }?.mediaCount ?: 0
        }
        return maxOf(albumTotal, _previewMediaList.value.size)
    }

    /**
     * 预览页翻页回调：当前页距已加载末尾不足阈值时，向后续载一页。
     *
     * 续载与网格分页源同构——用末条的 (dateAdded, id) 作 keyset、同一套过滤条件，
     * 保证与网格 Paging 取数顺序完全一致；结果按 id 去重后合入快照，
     * PreviewActivity 观察 [previewMediaList] 增量扩展 ViewPager。
     * 到底（末页不足一页）后用实际枚举数校正分母，消除相册计数过期导致的偏差。
     */
    fun onPreviewPageSelected(position: Int) {
        if (!previewCanLoadMore || previewListExhausted) return
        val list = _previewMediaList.value
        if (list.isEmpty()) return
        if (list.size - position > PREVIEW_LOAD_MORE_THRESHOLD) return
        if (previewLoadMoreJob?.isActive == true) return
        val last = list.last()
        val pageSize = GridPaging.pageSize(config.sanitizedSpanCount)
        previewLoadMoreJob = viewModelScope.launch {
            // 失败不置 exhausted：下次翻页会再次触发，天然重试
            runCatching {
                repository.loadMedia(
                    bucketId = _currentBucketId.value,
                    mediaType = config.mediaType,
                    limit = pageSize,
                    afterDateAdded = last.dateAdded,
                    afterId = last.id,
                    minVideoDurationMs = config.sanitizedMinVideoDurationMs,
                    maxVideoDurationMs = config.sanitizedMaxVideoDurationMs,
                    minImageSizeBytes = config.sanitizedMinImageSize,
                    maxImageSizeBytes = config.sanitizedMaxImageSize
                )
            }.onSuccess { more ->
                val existing = _previewMediaList.value
                // 会话校验：查询在途期间快照可能被新预览会话整体替换（关闭预览→切相册→再进预览）。
                // 仅当快照仍以本次 keyset 末条收尾时才允许合入，否则丢弃本次结果。
                if (existing.lastOrNull()?.id != last.id) return@onSuccess
                if (more.size < pageSize) previewListExhausted = true
                val existingIds = existing.mapTo(HashSet()) { it.id }
                val appended = more.filter { it.id !in existingIds }
                MediaLoadLogger.logPreviewAppend(
                    position = position,
                    loaded = existing.size,
                    appended = appended.size,
                    exhausted = previewListExhausted
                )
                if (appended.isNotEmpty()) {
                    _previewMediaList.value = existing + appended
                }
                if (previewListExhausted) {
                    // 已枚举完整个相册，以实际条数为准（覆盖过期的相册聚合计数）
                    _previewTotalCount.value = _previewMediaList.value.size
                }
            }
        }
    }

    /** 预览已选中项（从底部栏点击预览触发）：固定集合，分母即选中数，不续载。 */
    fun previewSelected() {
        val items = selectionManager.getSelectedItems()
        if (items.isEmpty()) return
        previewCanLoadMore = false
        previewListExhausted = true
        _previewTotalCount.value = items.size
        _previewMediaList.value = items
        _previewStartPosition.value = 0
        _showPreviewEvent.tryEmit(Unit)
    }

    fun onCameraPhotoCaptured() {
        // MediaStore 的行在 CameraHelper.createImageUri() 的 insert() 时即已存在，
        // 系统相机返回 success=true 时数据也已写入；直接刷新分页源即可，无需任何等待。
        _mediaRefreshEvent.tryEmit(Unit)
    }

    fun toggleSelection(mediaFile: MediaFile): Boolean {
        val changed = selectionManager.toggleSelection(mediaFile)
        if (!changed) {
            // toggleSelection 返回 false 只发生在"要添加但已满"，对应"已达上限"
            _uiMessageEvent.tryEmit(R.string.photochoice_selection_limit_reached)
        }
        return changed
    }

    fun deselectById(id: Long) {
        selectionManager.deselectById(id)
        _deselectedEvent.tryEmit(id)
    }

    fun getSelectedItems(): List<MediaFile> = selectionManager.getSelectedItems()

    fun isSelected(id: Long): Boolean = selectionManager.isSelected(id)

    fun getSelectionOrder(id: Long): Int = selectionManager.getSelectionOrder(id)

    fun isLivePhoto(media: MediaFile): Boolean {
        if (media.type != MediaFile.MediaType.IMAGE) return false
        return media.isMotionPhoto || MotionPhotoDetector.isMotionPhotoCached(media)
    }

    /**
     * 网格 Live 角标是否应显示"静态导出"样式（斜杠 off 图标）。
     * 仅开启压缩时导出策略才生效；未开压缩时预览页无切换入口，网格恒显普通 Live 角标。
     */
    fun isLiveExportStatic(mediaId: Long): Boolean =
        config.compressConfig.enabled && !livePhotoExportPolicy.isKeepLive(mediaId)

    /**
     * 完成回传时是否对该条目执行图片压缩。
     *
     * - GIF 动图：不压缩（避免退化为静态 JPEG）
     * - Live 图：保留动效（默认）时不压缩；关闭实况导出时压成静态图——
     *   此处压缩承担"剥离动效"的语义职责，不受免压基准豁免，否则用户选了静态却回传实况
     * - 低于免压基准的普通图（≤720p 基准 或 <150KB）：不压缩，再压是负收益
     */
    fun shouldCompressOnExport(media: MediaFile): Boolean {
        if (!config.compressConfig.enabled) return false
        if (media.type != MediaFile.MediaType.IMAGE) return false
        if (CompressExportPolicy.isGifImage(media)) return false
        if (isLivePhoto(media)) {
            return !livePhotoExportPolicy.isKeepLive(media.id)
        }
        if (CompressExportPolicy.isBelowCompressBaseline(media, config.compressConfig)) {
            return false
        }
        return true
    }

    companion object {
        /** 预览续载触发阈值：当前页距已加载末尾不足该条数时预取下一页。 */
        private const val PREVIEW_LOAD_MORE_THRESHOLD = 20
    }

    class Factory(
        private val application: Application,
        private val config: PhotoChoiceConfig
    ) : ViewModelProvider.NewInstanceFactory() {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PhotoChoiceViewModel(application, config) as T
        }
    }
}
