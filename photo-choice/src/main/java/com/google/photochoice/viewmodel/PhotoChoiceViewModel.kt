package com.google.photochoice.viewmodel

import android.app.Application
import android.net.Uri
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
import com.google.photochoice.util.SandboxCleaner
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

    private val _showPreview = MutableStateFlow(false)
    val showPreview: StateFlow<Boolean> = _showPreview.asStateFlow()

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
                        pageSize = GridPaging.pageSize(config.spanCount),
                        initialLoadSize = GridPaging.initialLoadSize(config.spanCount),
                        prefetchDistance = GridPaging.prefetchDistance(config.spanCount),
                        enablePlaceholders = false,
                        maxSize = GridPaging.maxSize(config.spanCount)
                    )
                ) {
                    MediaPagingSource(
                        repository = repository,
                        bucketId = bucketId,
                        mediaType = config.mediaType,
                        minVideoDurationMs = config.minVideoDurationMs,
                        maxVideoDurationMs = config.maxVideoDurationMs
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
                    minVideoDurationMs = config.minVideoDurationMs,
                    maxVideoDurationMs = config.maxVideoDurationMs
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

    fun navigateToPreview(position: Int) {
        if (_previewMediaList.value.isEmpty()) return
        _previewStartPosition.value = position.coerceIn(0, _previewMediaList.value.lastIndex)
        _showPreview.value = true
    }

    /** 预览已选中项（从底部栏点击预览触发）。 */
    fun previewSelected() {
        val items = selectionManager.getSelectedItems()
        if (items.isEmpty()) return
        _previewMediaList.value = items
        _previewStartPosition.value = 0
        _showPreview.value = true
    }

    fun dismissPreview() {
        _showPreview.value = false
    }

    @Suppress("UNUSED_PARAMETER")
    fun onCameraPhotoCaptured(uri: Uri) {
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

    fun getSelectedIds(): List<Long> = selectionManager.getSelectedIds()

    fun getSelectedItems(): List<MediaFile> = selectionManager.getSelectedItems()

    fun isSelected(id: Long): Boolean = selectionManager.isSelected(id)

    fun getSelectionOrder(id: Long): Int = selectionManager.getSelectionOrder(id)

    fun isLivePhoto(media: MediaFile): Boolean {
        if (media.type != MediaFile.MediaType.IMAGE) return false
        return media.isMotionPhoto || MotionPhotoDetector.isMotionPhotoCached(media)
    }

    /**
     * 完成回传时是否对该条目执行图片压缩。
     * Live 图在保留动效（默认）时不压缩；关闭实况导出时压成静态图。
     */
    fun shouldCompressOnExport(media: MediaFile): Boolean {
        if (!config.compressConfig.enabled) return false
        if (media.type != MediaFile.MediaType.IMAGE) return false
        if (isLivePhoto(media)) {
            return !livePhotoExportPolicy.isKeepLive(media.id)
        }
        return true
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
