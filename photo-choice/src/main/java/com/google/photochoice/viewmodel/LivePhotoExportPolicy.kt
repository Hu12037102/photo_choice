package com.google.photochoice.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Live 图导出策略：仅在开启压缩时生效。
 *
 * - 默认 [isKeepLive] = true → 回传原图（保留动效）
 * - false → 按普通图片压缩为静态 JPEG
 */
class LivePhotoExportPolicy {

    private val keepLiveById = mutableMapOf<Long, Boolean>()

    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    fun isKeepLive(mediaId: Long): Boolean = keepLiveById[mediaId] ?: true

    fun setKeepLive(mediaId: Long, keep: Boolean) {
        if (isKeepLive(mediaId) == keep) return
        keepLiveById[mediaId] = keep
        _revision.value++
    }

    fun toggleKeepLive(mediaId: Long) {
        setKeepLive(mediaId, !isKeepLive(mediaId))
    }
}
