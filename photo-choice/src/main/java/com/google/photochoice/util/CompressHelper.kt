package com.google.photochoice.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import com.google.photochoice.config.CompressConfig
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * 图片压缩：等比缩放 + EXIF 方向校正 + JPEG 编码，可选按目标体积迭代降低质量。
 */
class CompressHelper(private val context: Context) {

    private val outputDir: File
        get() = File(context.cacheDir, "photo_choice").also { it.mkdirs() }

    /**
     * 按 [config] 压缩图片并写入沙盒。
     *
     * @return 成功时返回输出文件；失败返回 null（调用方应回退原 URI）。
     */
    fun compress(uri: Uri, config: CompressConfig): File? {
        if (!config.enabled) return null

        val options = BitmapFactory.Options()

        // Step 1: 仅读边界，获取原始宽高
        options.inJustDecodeBounds = true
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        inputStream.use { BitmapFactory.decodeStream(it, null, options) }
        val originalWidth = options.outWidth
        val originalHeight = options.outHeight
        if (originalWidth <= 0 || originalHeight <= 0) return null

        // Step 2: 计算 inSampleSize，先粗降采样再精缩放
        val maxWidth = if (config.maxWidth > 0) config.maxWidth else originalWidth
        val maxHeight = if (config.maxHeight > 0) config.maxHeight else originalHeight

        options.inSampleSize = calculateSampleSize(originalWidth, originalHeight, maxWidth, maxHeight)
        options.inJustDecodeBounds = false

        // Step 3: 解码 Bitmap
        var bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        } ?: return null

        try {
            // Step 4: 等比缩放到边界内
            val scaled = scaleToBounds(bitmap, maxWidth, maxHeight)
            if (scaled !== bitmap) {
                bitmap.recycle()
                bitmap = scaled
            }

            // Step 5: EXIF 方向校正（仅旋转像素，不保留其它 EXIF）
            val exifRotation = readExifRotation(uri)
            if (exifRotation != 0f) {
                val rotated = rotateBitmap(bitmap, exifRotation)
                bitmap.recycle()
                bitmap = rotated
            }

            // Step 6: JPEG 编码 + 可选体积迭代
            val outputFile = File(outputDir, "compress_${UUID.randomUUID()}.jpg")
            val written = writeJpegWithTargetSize(bitmap, config, outputFile)
            return if (written) outputFile else null
        } catch (e: Exception) {
            Log.w(TAG, "compress failed uri=$uri", e)
            return null
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * 写入 JPEG；若配置了 [CompressConfig.maxFileSizeBytes] 且超限，则递减质量重试。
     *
     * @return 是否写出有效非空文件
     */
    private fun writeJpegWithTargetSize(
        bitmap: Bitmap,
        config: CompressConfig,
        outputFile: File
    ): Boolean {
        val startQuality = config.quality.coerceIn(1, 100)
        val minQuality = config.minQuality.coerceIn(1, startQuality)
        val step = config.qualityStep.coerceAtLeast(1)
        val maxBytes = config.maxFileSizeBytes

        var quality = startQuality
        while (true) {
            FileOutputStream(outputFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, fos)
            }
            val size = outputFile.length()
            if (size <= 0L) return false

            // 未启用体积限制，或已达标，或已达最低质量
            if (maxBytes !in 1..<size || quality <= minQuality) {
                if (maxBytes in 1..<size) {
                    Log.d(
                        TAG,
                        "compress size=$size exceeds max=$maxBytes at minQuality=$quality"
                    )
                }
                return true
            }

            val nextQuality = (quality - step).coerceAtLeast(minQuality)
            if (nextQuality == quality) return true
            quality = nextQuality
            Log.d(TAG, "compress retry quality=$quality size=$size max=$maxBytes")
        }
    }

    /** 计算 inSampleSize，使解码后单边不超过 max 边界（2 的幂次降采样）。 */
    private fun calculateSampleSize(
        width: Int, height: Int, maxWidth: Int, maxHeight: Int
    ): Int {
        var sampleSize = 1
        val halfWidth = width / 2
        val halfHeight = height / 2
        while (halfWidth / sampleSize >= maxWidth || halfHeight / sampleSize >= maxHeight) {
            sampleSize *= 2
        }
        return sampleSize.coerceAtLeast(1)
    }

    /** 等比缩放到 maxWidth × maxHeight 边界内。 */
    private fun scaleToBounds(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxWidth && height <= maxHeight) return bitmap

        val ratio = minOf(maxWidth.toFloat() / width, maxHeight.toFloat() / height)
        val newWidth = (width * ratio).toInt().coerceAtLeast(1)
        val newHeight = (height * ratio).toInt().coerceAtLeast(1)
        return bitmap.scale(newWidth, newHeight, true)
    }

    /** 读取 EXIF 旋转角度（仅 90° 倍数）。 */
    private fun readExifRotation(uri: Uri): Float {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                when (exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (_: Exception) {
            0f
        }
    }

    /** 按角度旋转 Bitmap。 */
    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    companion object {
        private const val TAG = "CompressHelper"
    }
}
