package com.google.photochoice.viewmodel

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    // 单条策略变化广播（携带 mediaId），供网格定点刷新对应 item 的 Live 角标样式；
    // revision 只表达"有变化"，不带定位信息，两者服务不同订阅方
    private val _changedMediaId = MutableSharedFlow<Long>(extraBufferCapacity = 8)
    val changedMediaId: SharedFlow<Long> = _changedMediaId.asSharedFlow()

    /** 该 Live 图导出时是否保留实况动效（默认保留）。 */
    fun isKeepLive(mediaId: Long): Boolean = keepLiveById[mediaId] ?: true

    /** 设置导出策略；值未变化时不广播，避免无谓刷新。 */
    fun setKeepLive(mediaId: Long, keep: Boolean) {
        if (isKeepLive(mediaId) == keep) return
        keepLiveById[mediaId] = keep
        _revision.value++
        _changedMediaId.tryEmit(mediaId)
    }

    /** 在"保留实况 / 静态导出"之间切换。 */
    fun toggleKeepLive(mediaId: Long) {
        setKeepLive(mediaId, !isKeepLive(mediaId))
    }
}
