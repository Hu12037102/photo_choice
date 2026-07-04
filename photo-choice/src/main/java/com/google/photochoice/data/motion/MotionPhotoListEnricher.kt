package com.google.photochoice.data.motion

import android.content.Context
import com.google.photochoice.data.model.MediaFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * 列表 Live 角标异步补齐（不阻塞分页 load）。
 *
 * - 分页 append 后后台切片嗅探；视口任务高优先级插队
 * - 识别到实况图即逐条刷新角标（不等整片/整页结束）
 */
class MotionPhotoListEnricher(
    private val context: Context,
    scope: CoroutineScope,
    private val onMotionDetected: (Set<Long>) -> Unit
) {
    private val normalInbox = Channel<List<MediaFile>>(Channel.UNLIMITED)
    private val priorityInbox = Channel<List<MediaFile>>(Channel.UNLIMITED)
    /** 普通队列去重；视口任务不受此限制，避免被大批次占坑后无法插队。 */
    private val normalInFlightIds = ConcurrentHashMap.newKeySet<Long>()

    init {
        scope.launch(Dispatchers.IO) {
            var normalQueue: List<MediaFile> = emptyList()
            var normalIndex = 0

            while (isActive) {
                drainPriorityQueue()

                if (normalIndex >= normalQueue.size) {
                    normalQueue = pollNormalBatch()
                    normalIndex = 0
                    if (normalQueue.isEmpty()) continue
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

    fun schedule(items: List<MediaFile>) {
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

    private suspend fun drainPriorityQueue() {
        while (true) {
            val batch = priorityInbox.tryReceive().getOrNull() ?: return
            enrichAndNotify(batch, urgent = true)
        }
    }

    private suspend fun pollNormalBatch(): List<MediaFile> {
        normalInbox.tryReceive().getOrNull()?.let { return it }
        return select {
            priorityInbox.onReceive { batch ->
                enrichAndNotify(batch, urgent = true)
                normalInbox.tryReceive().getOrNull() ?: normalInbox.receive()
            }
            normalInbox.onReceive { it }
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
        /** 后台分页每片条数：较小值让视口任务更频繁插队。 */
        private const val NORMAL_SLICE_SIZE = 8
        private const val URGENT_SNIFF_PARALLEL = 16
        private const val NORMAL_SNIFF_PARALLEL = 12
    }
}
