package com.google.photochoice.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Live 图导出策略：仅在开启压缩时生效。
 *
 * - 默认 [isKeepLive] = true → 回传原图（保留动效）
 * - false → 按普通图片压缩为静态 JPEG
 *
 * 变化通过 [revision] 版本号广播：StateFlow 可回放，订阅方（网格/预览）在 STARTED 外
 * 停止收集期间发生的变化，回前台重启收集时能从首帧拿到最新版本号补偿刷新；
 * 具体哪条变了由订阅方按当前策略全量比对，不再单独广播 mediaId。
 */
class LivePhotoExportPolicy {

    private val keepLiveById = mutableMapOf<Long, Boolean>()

    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    /** 该 Live 图导出时是否保留实况动效（默认保留）。 */
    fun isKeepLive(mediaId: Long): Boolean = keepLiveById[mediaId] ?: true

    /** 设置导出策略；值未变化时不广播，避免无谓刷新。 */
    fun setKeepLive(mediaId: Long, keep: Boolean) {
        if (isKeepLive(mediaId) == keep) return
        keepLiveById[mediaId] = keep
        _revision.value++
    }

    /** 在"保留实况 / 静态导出"之间切换。 */
    fun toggleKeepLive(mediaId: Long) {
        setKeepLive(mediaId, !isKeepLive(mediaId))
    }
}
