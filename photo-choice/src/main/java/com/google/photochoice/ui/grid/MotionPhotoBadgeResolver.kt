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
 */
class MotionPhotoBadgeResolver(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onItemDetected: (mediaId: Long) -> Unit
) {
    private val inflight = ConcurrentHashMap<Long, Job>()

    fun resolve(media: MediaFile, onResult: (Boolean) -> Unit) {
        if (media.type != MediaFile.MediaType.IMAGE) {
            onResult(false)
            return
        }
        if (media.isMotionPhoto || MotionPhotoDetector.isMotionPhotoCached(media)) {
            onResult(true)
            return
        }
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

    fun cancelAll() {
        inflight.values.forEach { it.cancel() }
        inflight.clear()
    }
}
