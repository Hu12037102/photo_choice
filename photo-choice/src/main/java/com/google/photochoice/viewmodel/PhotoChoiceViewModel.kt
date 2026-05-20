package com.google.photochoice.viewmodel

import android.app.Application
import android.content.ContentUris
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
import com.google.photochoice.util.SandboxCleaner
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

    private val _showCrop = MutableStateFlow<String?>(null)
    val showCrop: StateFlow<String?> = _showCrop.asStateFlow()

    private val _cameraRefreshTrigger = MutableStateFlow(0L)

    @OptIn(ExperimentalCoroutinesApi::class)
    val mediaPagingFlow: Flow<PagingData<MediaFile>> =
        combine(_currentBucketId, _cameraRefreshTrigger) { bucketId, _ -> bucketId }
            .flatMapLatest { bucketId ->
                Pager(
                    config = PagingConfig(
                        pageSize = 60,
                        initialLoadSize = config.spanCount * 16,
                        prefetchDistance = config.spanCount * 12,
                        enablePlaceholders = false
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
        SandboxCleaner(application).cleanExpired()
        loadAlbums()
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

    fun navigateToCrop(uri: String) {
        _showCrop.value = uri
    }

    fun dismissCrop() {
        _showCrop.value = null
    }

    fun onCameraPhotoCaptured(uri: Uri) {
        viewModelScope.launch {
            _cameraRefreshTrigger.value = System.currentTimeMillis()
            val id = runCatching { ContentUris.parseId(uri) }.getOrNull() ?: return@launch
            var mediaFile: MediaFile? = null
            repeat(5) { attempt ->
                delay(if (attempt == 0) 200L else 400L)
                mediaFile = repository.getMediaById(id)
                if (mediaFile != null) return@repeat
            }
            mediaFile?.let { selectionManager.select(it) }
        }
    }

    fun toggleSelection(mediaFile: MediaFile): Boolean =
        selectionManager.toggleSelection(mediaFile)

    fun deselectById(id: Long) = selectionManager.deselectById(id)

    fun getSelectedIds(): List<Long> = selectionManager.getSelectedIds()

    fun getSelectedItems(): List<MediaFile> = selectionManager.getSelectedItems()

    fun isSelected(id: Long): Boolean = selectionManager.isSelected(id)

    fun getSelectionOrder(id: Long): Int = selectionManager.getSelectionOrder(id)

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
