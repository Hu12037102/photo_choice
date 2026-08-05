package com.google.photochoice.data.motion

import android.content.Context
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.google.photochoice.data.MediaRepository
import com.google.photochoice.data.model.MediaFile
import com.google.photochoice.util.PhotoChoiceLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 相册级后台全量预建(L2)：把整册图片嗅一遍写入 IndexStore，之后滑到任意位置 O(1) 命中。
 *
 * 性能治理(预建是优化项，非正确性依赖，对用户可见操作无条件让路)：
 * - 滑动暂停：DRAGGING/SETTLING 时 pause，IDLE 防抖后 resume
 * - onStop 停：页面不可见即不推进(repeatOnLifecycle STARTED)
 * - 编排在 Default 调度器（不占主线程）+ 分片间 yield
 * - 低并发：区别于视口紧急通道
 * - 跳过已知：先过滤 IndexStore 已有有效记录
 * - 可中断/断点续建：切相册取消 Job，下次从索引已有处继续
 *
 * @param onMotionDetected 预建中命中实况图的 id 回调(刷新可见角标)
 */
class AlbumMotionPrebuilder(
    private val context: Context,
    private val repository: MediaRepository,
    private val scope: CoroutineScope,
    private val onMotionDetected: (Set<Long>) -> Unit
) {
    private var job: Job? = null
    private val paused = AtomicBoolean(false)

    /** 滑动开始：暂停预建，把磁盘带宽让给缩略图。 */
    fun pause() { paused.set(true) }

    /** 滑动结束(防抖后)：恢复预建。 */
    fun resume() { paused.set(false) }

    /**
     * 启动某相册的预建。会取消上一个相册的预建 Job。
     * 应在首屏 IDLE 后调用(让路首屏缩略图)。
     */
    fun start(lifecycleOwner: LifecycleOwner, bucketId: String?) {
        job?.cancel()
        job = scope.launch {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 编排移出主线程；repeatOnLifecycle 的 onStop 取消仍会协作式传播进 withContext
                withContext(Dispatchers.Default) {
                    runPrebuild(bucketId)
                }
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }

    private suspend fun runPrebuild(bucketId: String?) {
        try {
            // 先等索引加载完成，否则冷启动会把整册重嗅一遍
            MotionPhotoIndexStore.awaitLoaded(context)
            val manifest = repository.loadImageManifestForPrebuild(bucketId)
            if (manifest.isEmpty()) return

            // 跳过已知：过滤 IndexStore 已有有效结果
            val pending = manifest.filter {
                MotionPhotoIndexStore.query(it) == IndexResult.UNKNOWN
            }
            if (pending.isEmpty()) {
                PhotoChoiceLog.d(TAG) { "prebuild bucket=$bucketId all known, skip" }
                return
            }

            // 写穿内存缓存(warmAlbum/上次会话)已命中的条目，使 IndexStore 收敛、避免重嗅；
            // 仅对内存也未知的条目走真正的 XMP 嗅探
            val toSniff = ArrayList<MediaFile>(pending.size)
            for (item in pending) {
                val cached = MotionPhotoDetector.memoryResult(item.id)
                if (cached != null) {
                    MotionPhotoIndexStore.put(item, cached)
                } else {
                    toSniff.add(item)
                }
            }
            PhotoChoiceLog.d(TAG) {
                "prebuild bucket=$bucketId toSniff=${toSniff.size}/${manifest.size}"
            }
            if (toSniff.isEmpty()) {
                MotionPhotoIndexStore.requestFlush()
                return
            }

            for (slice in toSniff.chunked(SLICE_SIZE)) {
                if (!currentCoroutineContext().isActive) return
                // 滑动期暂停：轮询等待恢复(让磁盘给缩略图)
                while (paused.get() && currentCoroutineContext().isActive) {
                    delay(PAUSE_POLL_MS)
                }
                if (!currentCoroutineContext().isActive) return

                val detected = MotionPhotoDetector.quickSniffBatch(
                    context = context,
                    items = slice,
                    parallelism = PREBUILD_PARALLEL,   // 低并发
                )
                // 通知集 = 命中实况图(淡入) + 启发式误报但嗅探为否(淡出校正)；批量一次切主线程
                val toNotify = HashSet(detected)
                for (item in slice) {
                    if (item.id !in detected && MotionPhotoHeuristics.guess(item)) {
                        toNotify.add(item.id)
                    }
                }
                if (toNotify.isNotEmpty()) {
                    withContext(Dispatchers.Main) { onMotionDetected(toNotify) }
                }
                yield()  // 分片间让出，保证紧急通道可插队
            }
            MotionPhotoIndexStore.requestFlush()
            PhotoChoiceLog.d(TAG) { "prebuild bucket=$bucketId done" }
        } catch (ce: CancellationException) {
            throw ce  // 取消必须原样抛出，保证 repeatOnLifecycle/切相册能停下
        } catch (t: Throwable) {
            // 预建是优化项，任何异常静默降级，绝不 Crash
            Log.w(TAG, "prebuild failed bucket=$bucketId", t)
        }
    }

    companion object {
        private const val TAG = "AlbumMotionPrebuilder"
        /** 每片大小：一批嗅探后让出。 */
        private const val SLICE_SIZE = 12
        /** 预建低并发(视口紧急通道为 20)。 */
        private const val PREBUILD_PARALLEL = 4
        /** 暂停轮询间隔。 */
        private const val PAUSE_POLL_MS = 150L
    }
}
