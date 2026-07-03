package com.google.photochoice.data.motion

import android.content.Context
import android.net.Uri
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer

/**
 * 通过 XMP 元数据识别 Motion Photo / 实况图（兼容 API 34 以下及未写入 IS_MOTION_PHOTO 的机型）。
 */
internal object MotionPhotoXmpSniffer {

    private const val QUICK_HEAD_BYTES = 48 * 1024
    private const val QUICK_TAIL_BYTES = 24 * 1024
    private const val HEAD_BYTES = 96 * 1024
    private const val TAIL_BYTES = 64 * 1024

    /** 仅匹配实况图专有标记，避免普通 JPEG 的 XMP 误报。 */
    private val MOTION_MARKERS = listOf(
        "GCamera:MicroVideo",
        "MicroVideoOffset",
        "MotionPhoto",
        "Camera:MotionPhoto",
        "Item:Mime=\"video/mp4\"",
        "GCamera:MotionPhotoPresentationTimestampUs",
        "GCamera:MotionPhoto",
        // Samsung Motion Photo
        "MotionPhoto_PresentationTimestampUs",
        "MotionPhoto_Data",
        // Apple Live Photo
        "com.apple.quicktime.still-image-time",
    )

    /** 分页列表快速筛查：读文件头 + 尾部，覆盖 MicroVideoOffset 等尾部元数据。 */
    fun isMotionPhotoQuick(context: Context, uri: Uri): Boolean {
        val headBytes = readQuickHeadBytes(context, uri) ?: return false
        if (containsMotionMarker(headBytes)) return true
        val tailBytes = readQuickTailBytes(context, uri) ?: return false
        return containsMotionMarker(tailBytes)
    }

    private fun containsMotionMarker(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        val text = bytes.toString(Charsets.ISO_8859_1)
        return MOTION_MARKERS.any { text.contains(it, ignoreCase = true) }
    }

    fun isMotionPhoto(context: Context, uri: Uri): Boolean {
        if (isMotionPhotoQuick(context, uri)) return true
        val bytes = readSniffBytes(context, uri) ?: return false
        return containsMotionMarker(bytes)
    }

    private fun readQuickHeadBytes(context: Context, uri: Uri): ByteArray? {
        return runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val size = pfd.statSize
                if (size <= 0L) return@use ByteArray(0)
                val len = minOf(QUICK_HEAD_BYTES.toLong(), size).toInt()
                FileInputStream(pfd.fileDescriptor).channel.use { channel ->
                    val head = ByteArray(len)
                    channel.position(0)
                    channel.read(ByteBuffer.wrap(head))
                    head
                }
            }
        }.getOrNull()
    }

    private fun readQuickTailBytes(context: Context, uri: Uri): ByteArray? {
        return runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val size = pfd.statSize
                if (size <= 0L) return@use ByteArray(0)
                val len = minOf(QUICK_TAIL_BYTES.toLong(), size).toInt()
                FileInputStream(pfd.fileDescriptor).channel.use { channel ->
                    val tail = ByteArray(len)
                    channel.position((size - len).coerceAtLeast(0L))
                    channel.read(java.nio.ByteBuffer.wrap(tail))
                    tail
                }
            }
        }.getOrNull()
    }

    /**
     * 解析内嵌视频在文件中的字节区间 [start, end)，失败返回 null。
     */
    fun parseEmbeddedVideoRange(context: Context, uri: Uri): LongRange? {
        val fileSize = openFileSize(context, uri) ?: return null
        if (fileSize <= 0L) return null

        val bytes = readSniffBytes(context, uri) ?: return null
        val text = bytes.toString(Charsets.ISO_8859_1)

        // Google Camera 旧版：视频在文件末尾，MicroVideoOffset 为距文件尾的字节数
        Regex("""MicroVideoOffset="(\d+)"""").find(text)?.groupValues?.getOrNull(1)
            ?.toLongOrNull()
            ?.let { offsetFromEnd ->
                if (offsetFromEnd in 1 until fileSize) {
                    val start = fileSize - offsetFromEnd
                    return start until fileSize
                }
            }

        // Motion Photo 1.0：Container Item Length + Mime video/mp4（取最后一个 video item）
        val videoLengthRegex = Regex("""Item:Mime="video/mp4"[^>]*Item:Length="(\d+)"""")
        val match = videoLengthRegex.findAll(text).lastOrNull()
            ?: Regex("""Item:Length="(\d+)"[^>]*Item:Mime="video/mp4"""").findAll(text).lastOrNull()
        match?.groupValues?.getOrNull(1)?.toLongOrNull()?.let { videoLen ->
            if (videoLen in 1 until fileSize) {
                val start = fileSize - videoLen
                return start until fileSize
            }
        }

        return null
    }

    private fun readSniffBytes(context: Context, uri: Uri): ByteArray? {
        return runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val size = pfd.statSize
                if (size <= 0L) return@use ByteArray(0)
                FileInputStream(pfd.fileDescriptor).channel.use { channel ->
                    val headLen = minOf(HEAD_BYTES.toLong(), size).toInt()
                    val head = ByteArray(headLen)
                    channel.position(0)
                    channel.read(ByteBuffer.wrap(head))
                    val tailLen = minOf(TAIL_BYTES.toLong(), size).toInt()
                    val tail = ByteArray(tailLen)
                    channel.position((size - tailLen).coerceAtLeast(0L))
                    channel.read(ByteBuffer.wrap(tail))
                    head + tail
                }
            } ?: context.contentResolver.openInputStream(uri)?.use { readHeadAndTailFromStream(it) }
        }.getOrNull()
    }

    private fun readHeadAndTailFromStream(input: InputStream): ByteArray {
        val head = ByteArray(HEAD_BYTES)
        val headRead = input.read(head)
        val headSlice = if (headRead > 0) head.copyOf(headRead) else ByteArray(0)
        val tail = ByteArray(TAIL_BYTES)
        runCatching {
            val available = input.available()
            if (available > TAIL_BYTES) input.skip((available - TAIL_BYTES).toLong())
        }
        val tailRead = input.read(tail)
        val tailSlice = if (tailRead > 0) tail.copyOf(tailRead) else ByteArray(0)
        return headSlice + tailSlice
    }

    private fun openFileSize(context: Context, uri: Uri): Long? {
        return runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize }
        }.getOrNull()
    }
}
