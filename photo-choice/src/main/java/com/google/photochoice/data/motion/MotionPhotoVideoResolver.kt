package com.google.photochoice.data.motion

import android.content.Context
import android.net.Uri
import androidx.collection.LruCache
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer

/**
 * 从 Motion Photo 解析内嵌视频；按 mediaId 缓存提取结果，避免每次长按重复 IO。
 */
object MotionPhotoVideoResolver {

    private const val CACHE_DIR = "photo_choice_motion"
    private val playbackUriCache = LruCache<Long, Uri>(32)

    /** 预提取内嵌视频，降低长按起播延迟。 */
    fun warmCache(context: Context, mediaId: Long, imageUri: Uri) {
        if (playbackUriCache.get(mediaId) != null) return
        runCatching {
            resolvePlaybackUri(context, mediaId, imageUri)
        }
    }

    fun resolvePlaybackUri(context: Context, mediaId: Long, imageUri: Uri): Uri? {
        playbackUriCache.get(mediaId)?.let { return it }

        val extracted = extractEmbeddedVideoFile(context, imageUri)
        if (extracted != null) {
            val uri = Uri.fromFile(extracted)
            playbackUriCache.put(mediaId, uri)
            return uri
        }

        playbackUriCache.put(mediaId, imageUri)
        return imageUri
    }

    private fun extractEmbeddedVideoFile(context: Context, imageUri: Uri): File? {
        val range = MotionPhotoXmpSniffer.parseEmbeddedVideoRange(context, imageUri)
            ?: return null
        val length = range.last - range.first + 1L
        if (length <= 0L) return null

        val outDir = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
        val outFile = File(outDir, "motion_${imageUri.hashCode()}_${range.first}.mp4")

        if (outFile.exists() && outFile.length() == length) {
            return outFile
        }

        return runCatching {
            context.contentResolver.openFileDescriptor(imageUri, "r")?.use { pfd ->
                FileInputStream(pfd.fileDescriptor).channel.use { channel ->
                    FileOutputStream(outFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        channel.position(range.first)
                        var remaining = length
                        while (remaining > 0) {
                            val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                            val read = channel.read(ByteBuffer.wrap(buffer, 0, toRead))
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            remaining -= read
                        }
                    }
                }
            }
            if (outFile.length() > 0L) outFile else null
        }.getOrNull()
    }
}
