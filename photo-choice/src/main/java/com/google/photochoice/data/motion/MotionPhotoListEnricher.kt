package com.google.photochoice.data.motion

import android.content.Context
import com.google.photochoice.data.model.MediaFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * 列表 Live 角标异步补齐（不阻塞分页 load）。
 *
 * - **视口通道**：独立协程，永不被后台分页队列阻塞（快滑到底部仍可秒级响应）
 * - **后台通道**：仅低优先级切片，不 enqueue 全量历史页
 */
class MotionPhotoListEnricher(
    private val context: Context,
    scope: CoroutineScope,
    private val onMotionDetected: (Set<Long>) -> Unit
) {
    private val normalInbox = Channel<List<MediaFile>>(Channel.UNLIMITED)
    private val priorityInbox = Channel<List<MediaFile>>(Channel.UNLIMITED)
    private val normalInFlightIds = ConcurrentHashMap.newKeySet<Long>()

    init {
        // 视口专用通道：与后台队列完全隔离，避免深列表底部等前面数百条嗅探结束
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                val batch = priorityInbox.receive()
                enrichAndNotify(batch, urgent = true)
            }
        }
        scope.launch(Dispatchers.IO) {
            var normalQueue: List<MediaFile> = emptyList()
            var normalIndex = 0
            while (isActive) {
                if (normalIndex >= normalQueue.size) {
                    normalQueue = normalInbox.tryReceive().getOrNull() ?: normalInbox.receive()
                    normalIndex = 0
                }
                val sliceEnd = minOf(normalIndex + NORMAL_SLICE_SIZE, normalQueue.size)
                val slice = normalQueue.subList(normalIndex, sliceEnd)
                normalIndex = sliceEnd
                if (normalIndex >= normalQueue.size) {
                    normalQueue.forEach { normalInFlightIds.remove(it.id) }
                    normalQueue = emptyList()
                    normalIndex = 0
                }
                enrichAndNotify(slice, urgent = false)
            }
        }
    }

    /** 后台低优先级：仅预取窗口附近条目，不灌入整页/整相册。 */
    fun scheduleBackground(items: List<MediaFile>) {
        enqueueNormal(items)
    }

    fun scheduleVisible(items: List<MediaFile>) {
        val fresh = items.filter { needsEnrichment(it) }
        if (fresh.isNotEmpty()) {
            priorityInbox.trySend(fresh)
        }
    }

    fun scheduleOne(item: MediaFile) {
        scheduleVisible(listOf(item))
    }

    fun reset() {
        normalInFlightIds.clear()
    }

    private fun enqueueNormal(items: List<MediaFile>) {
        val fresh = items.filter { item ->
            needsEnrichment(item) && normalInFlightIds.add(item.id)
        }
        if (fresh.isNotEmpty()) {
            normalInbox.trySend(fresh)
        }
    }

    private suspend fun enrichAndNotify(items: List<MediaFile>, urgent: Boolean) {
        val images = items.filter { needsEnrichment(it) }
        if (images.isEmpty()) return

        val fromStore = MotionPhotoDetector.queryMotionIdsFromMediaStore(
            context,
            images.map { it.id }
        )
        fromStore.forEach { id -> notifyDetected(setOf(id)) }

        MotionPhotoDetector.quickSniffBatch(
            context = context,
            items = images,
            alreadyKnown = fromStore,
            parallelism = if (urgent) URGENT_SNIFF_PARALLEL else NORMAL_SNIFF_PARALLEL,
            onEachDetected = { id -> notifyDetected(setOf(id)) },
        )
    }

    private suspend fun notifyDetected(ids: Set<Long>) {
        withContext(Dispatchers.Main.immediate) {
            onMotionDetected(ids)
        }
    }

    private fun needsEnrichment(item: MediaFile): Boolean {
        if (item.type != MediaFile.MediaType.IMAGE) return false
        if (item.isMotionPhoto) return false
        return !MotionPhotoDetector.hasCachedResult(item.id)
    }

    companion object {
        private const val NORMAL_SLICE_SIZE = 6
        private const val URGENT_SNIFF_PARALLEL = 20
        private const val NORMAL_SNIFF_PARALLEL = 8
    }
}
