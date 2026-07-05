package com.google.photochoice.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import com.google.photochoice.config.CompressConfig
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class CompressHelper(private val context: Context) {

    private val outputDir: File
        get() = File(context.cacheDir, "photo_choice").also { it.mkdirs() }

    fun compress(uri: Uri, config: CompressConfig): File? {
        if (!config.enabled) return null

        val options = BitmapFactory.Options()

        // Step 1: Decode bounds to get original dimensions
        options.inJustDecodeBounds = true
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        inputStream.use { BitmapFactory.decodeStream(it, null, options) }
        // 注意：inJustDecodeBounds=true 时 decodeStream 恒返回 null，这是正常行为，
        // 不能在此处用 ?: return null——那会把 bounds 解码也判为失败。
        // 改为通过 outWidth/outHeight 判断解码是否成功。
        val originalWidth = options.outWidth
        val originalHeight = options.outHeight
        if (originalWidth <= 0 || originalHeight <= 0) return null

        // Step 2: Calculate sample size for size compression
        val maxWidth = if (config.maxWidth > 0) config.maxWidth else originalWidth
        val maxHeight = if (config.maxHeight > 0) config.maxHeight else originalHeight

        options.inSampleSize = calculateSampleSize(originalWidth, originalHeight, maxWidth, maxHeight)
        options.inJustDecodeBounds = false

        // Step 3: Decode bitmap via stream
        var bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        } ?: return null

        try {
            // Step 4: Scale if still larger than max dimensions
            val scaled = scaleToBounds(bitmap, maxWidth, maxHeight)
            if (scaled !== bitmap) {
                bitmap.recycle()
                bitmap = scaled
            }

            // Step 5: Apply EXIF rotation
            val exifRotation = readExifRotation(uri)
            if (exifRotation != 0f) {
                val rotated = rotateBitmap(bitmap, exifRotation)
                bitmap.recycle()
                bitmap = rotated
            }

            // Step 6: Compress and write to file
            val outputFile = File(outputDir, "compress_${UUID.randomUUID()}.jpg")
            FileOutputStream(outputFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, config.quality, fos)
            }
            return outputFile
        } catch (_: Exception) {
            return null
        } finally {
            bitmap.recycle()
        }
    }

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

    private fun scaleToBounds(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxWidth && height <= maxHeight) return bitmap

        val ratio = minOf(maxWidth.toFloat() / width, maxHeight.toFloat() / height)
        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()
        return bitmap.scale(newWidth, newHeight, true)
    }

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

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
