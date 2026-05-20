package com.google.photochoice.ui.grid

import android.content.Context
import com.google.photochoice.data.model.MediaFile
import com.google.photochoice.data.motion.MotionPhotoDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * 网格项实况图角标：列表分页不做全量 XMP，绑定可见项时异步检测并刷新。
 *
 * 滚动期间 [setPaused] 会被置位，此时 resolve 只入队不发起 IO；当 RecyclerView
 * 进入 IDLE 状态再批量发起。避免快速滑动时几百个 XMP 任务挤入调度器。
 */
class MotionPhotoBadgeResolver(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onItemDetected: (mediaId: Long) -> Unit
) {
    private val inflight = ConcurrentHashMap<Long, Job>()
    private val pending = ConcurrentHashMap<Long, MediaFile>()

    @Volatile
    private var paused = false

    /** RecyclerView 滚动状态变化时调用。true=滚动中（推迟 IO），false=IDLE（消费队列）。 */
    fun setPaused(paused: Boolean) {
        if (this.paused == paused) return
        this.paused = paused
        if (!paused) drainPending()
    }

    fun resolve(media: MediaFile, onResult: (Boolean) -> Unit) {
        if (media.type != MediaFile.MediaType.IMAGE) {
            onResult(false)
            return
        }
        if (media.isMotionPhoto || MotionPhotoDetector.isMotionPhotoCached(media)) {
            onResult(true)
            return
        }
        if (paused) {
            // 滚动中：只入队，不发起 IO；onResult 给个保守值，等 IDLE 后通过 onItemDetected 刷新
            pending[media.id] = media
            onResult(false)
            return
        }
        startDetect(media, onResult)
    }

    private fun startDetect(media: MediaFile, onResult: (Boolean) -> Unit) {
        val existing = inflight[media.id]
        if (existing != null) {
            scope.launch {
                existing.join()
                withContext(Dispatchers.Main) {
                    onResult(MotionPhotoDetector.isMotionPhotoCached(media))
                }
            }
            return
        }
        inflight[media.id] = scope.launch {
            val detected = MotionPhotoDetector.detectSingle(context, media)
            inflight.remove(media.id)
            if (detected) {
                onItemDetected(media.id)
            }
            withContext(Dispatchers.Main) {
                onResult(detected)
            }
        }
    }

    private fun drainPending() {
        if (pending.isEmpty()) return
        // 取 snapshot 后清空：被滚出去的项已经不再可见，没必要等 IDLE 再补救
        val items = pending.values.toList()
        pending.clear()
        for (media in items) {
            // 静默检测：结果通过 onItemDetected 回到 adapter，触发 PAYLOAD_LIVE_PHOTO 局部刷新
            startDetect(media) { /* no-op */ }
        }
    }

    fun cancelAll() {
        inflight.values.forEach { it.cancel() }
        inflight.clear()
        pending.clear()
    }
}
