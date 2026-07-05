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

    /** 清空内存中的播放 URI 映射；磁盘清理后必须调用，避免返回已删文件。 */
    fun clearMemoryCache() {
        playbackUriCache.evictAll()
    }

    /** 预提取内嵌视频，降低长按起播延迟。 */
    fun warmCache(context: Context, mediaId: Long, imageUri: Uri) {
        if (playbackUriCache.get(mediaId) != null) return
        runCatching {
            resolvePlaybackUri(context, mediaId, imageUri)
        }
    }

    fun resolvePlaybackUri(context: Context, mediaId: Long, imageUri: Uri): Uri? {
        playbackUriCache.get(mediaId)?.let { cached ->
            if (isCachedPlaybackUriValid(cached)) return cached
            playbackUriCache.remove(mediaId)
        }

        val extracted = extractEmbeddedVideoFile(context, imageUri)
        if (extracted != null) {
            val uri = Uri.fromFile(extracted)
            playbackUriCache.put(mediaId, uri)
            return uri
        }

        playbackUriCache.put(mediaId, imageUri)
        return imageUri
    }

    /** 校验内存缓存中的 file Uri 是否仍对应磁盘上的有效文件。 */
    private fun isCachedPlaybackUriValid(uri: Uri): Boolean {
        if (uri.scheme != "file") return true
        val path = uri.path ?: return false
        return File(path).exists()
    }

    private fun extractEmbeddedVideoFile(context: Context, imageUri: Uri): File? {
        val range = MotionPhotoXmpSniffer.parseEmbeddedVideoRange(context, imageUri)
            ?: return null
        val length = range.last - range.first + 1L
        if (length <= 0L) return null

        val outDir = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
        val outFile = File(outDir, "motion_${imageUri.hashCode()}_${range.first}.mp4")

        if (outFile.exists() && outFile.length() == length) {
            // 命中磁盘缓存时刷新 mtime，便于 SandboxCleaner 按最近使用保留
            outFile.setLastModified(System.currentTimeMillis())
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
