package com.google.photochoice.data

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import com.google.photochoice.config.MediaType

/**
 * MediaStore 外部变更监听器。
 *
 * 选图过程中媒体库可能被外部改写——用户切到系统相机拍照再切回、其它 App 写入/删除媒体、
 * 云同步落库等。网格分页与相册聚合都是一次性查询的快照，感知不到这些变化；本类注册
 * [ContentObserver] 监听对应媒体集合 Uri，变化时经防抖合并后回调 [onMediaChanged]，
 * 由上层触发相册聚合与网格分页的刷新。
 *
 * 设计要点：
 * - 按 [mediaType] 只监听需要的集合（图片 / 视频 / 两者），不监听 Files 全集，
 *   避免下载、文档等无关变更造成无谓刷新；
 * - 系统在批量写入（连拍、云同步、批量删除）时会高频回调 onChange，这里以
 *   [debounceMs] 为窗口合并：窗口内已有待执行回调时只累计计数不重复调度，
 *   保证每个窗口至多刷新一次；
 * - 所有状态都由主线程 Handler 收口（onChange 投递、防抖执行、注册/反注册均在主线程），
 *   以线程封闭代替加锁。
 */
class MediaStoreChangeObserver(
    context: Context,
    private val mediaType: MediaType,
    private val debounceMs: Long = DEFAULT_DEBOUNCE_MS,
    private val onMediaChanged: () -> Unit
) {

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())

    /** 是否已注册，配合 [register] / [unregister] 幂等。 */
    private var registered = false

    /** 防抖窗口内是否已有待执行的刷新回调。 */
    private var pendingNotify = false

    /** 当前窗口内被合并的变更次数，仅用于日志观测批量写入的密度。 */
    private var mergedChangeCount = 0

    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            mergedChangeCount++
            // 窗口内只保留第一次调度，后续变更合并进同一次刷新
            if (pendingNotify) return
            pendingNotify = true
            handler.postDelayed({
                pendingNotify = false
                Log.i(TAG, "media changed, merged=$mergedChangeCount, dispatch refresh")
                mergedChangeCount = 0
                onMediaChanged()
            }, debounceMs)
        }
    }

    /**
     * 注册监听，与 [unregister] 成对调用；重复注册会被忽略。
     *
     * notifyForDescendants 传 true：单条媒体的变更通知发在 content://...media/<id>
     * 这类子 Uri 上，必须监听后代 Uri 才能收到。
     */
    fun register() {
        if (registered) return
        registered = true
        observedUris(mediaType).forEach { uri ->
            appContext.contentResolver.registerContentObserver(uri, true, observer)
        }
        Log.i(TAG, "registered, mediaType=$mediaType")
    }

    /**
     * 反注册并丢弃未执行的防抖回调，避免宿主销毁后残留的延迟消息仍触发刷新。
     * handler 为本类私有，removeCallbacksAndMessages 不会波及其它组件。
     */
    fun unregister() {
        if (!registered) return
        registered = false
        appContext.contentResolver.unregisterContentObserver(observer)
        handler.removeCallbacksAndMessages(null)
        pendingNotify = false
        mergedChangeCount = 0
        Log.i(TAG, "unregistered")
    }

    companion object {
        private const val TAG = "PhotoChoice/MediaWatch"

        /** 防抖窗口：合并批量写入（连拍、云同步）产生的高频变更，避免刷新风暴。 */
        private const val DEFAULT_DEBOUNCE_MS = 500L

        /** 按媒体类型给出需监听的集合 Uri，与查询侧 MEDIA_TYPE 过滤口径一致。 */
        private fun observedUris(mediaType: MediaType): List<Uri> = when (mediaType) {
            MediaType.IMAGE -> listOf(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            MediaType.VIDEO -> listOf(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            MediaType.ALL -> listOf(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            )
        }
    }
}
