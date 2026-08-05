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
import java.io.ByteArrayOutputStream
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

            // Step 5: EXIF 方向校正——旋转 + 镜像都落到像素上，不保留其它 EXIF。
            // 输出不写 EXIF，方向必须"烧进"像素，否则宿主拿到的图会是歪的或镜像的。
            val exifTransform = readExifTransform(uri)
            if (!exifTransform.isIdentity) {
                PhotoChoiceLog.d(TAG) {
                    "apply exif transform rotate=${exifTransform.rotationDegrees}" +
                        " flip=${exifTransform.flipHorizontal}"
                }
                val transformed = applyExifTransform(bitmap, exifTransform)
                bitmap.recycle()
                bitmap = transformed
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
     * 迭代阶段编码到内存缓冲（目标体积约 1.5MB，缓冲开销可忽略）探测大小，
     * 达标后一次性落盘——避免每轮质量重试都整文件重写磁盘。
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

        val buffer = ByteArrayOutputStream()
        var quality = startQuality
        while (true) {
            buffer.reset()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, buffer)
            val size = buffer.size().toLong()
            if (size <= 0L) return false

            // 未启用体积限制，或已达标，或已达最低质量 → 收敛，落盘
            if (maxBytes !in 1..<size || quality <= minQuality) {
                if (maxBytes in 1..<size) {
                    PhotoChoiceLog.d(TAG) {
                        "compress size=$size exceeds max=$maxBytes at minQuality=$quality"
                    }
                }
                FileOutputStream(outputFile).use { fos -> buffer.writeTo(fos) }
                return outputFile.length() > 0L
            }

            val nextQuality = (quality - step).coerceAtLeast(minQuality)
            if (nextQuality == quality) {
                FileOutputStream(outputFile).use { fos -> buffer.writeTo(fos) }
                return outputFile.length() > 0L
            }
            quality = nextQuality
            PhotoChoiceLog.d(TAG) { "compress retry quality=$quality size=$size max=$maxBytes" }
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

    /**
     * 读取 EXIF 方向并换算成像素变换。
     *
     * 覆盖规范的 8 种方向（含 4 种镜像），映射表见 [ExifOrientation]。读取失败
     * （无 EXIF、流打不开、文件损坏）按恒等变换处理——宁可不转，也不能因方向读取
     * 失败让整条压缩链失败。
     */
    private fun readExifTransform(uri: Uri): ExifTransform {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val orientation = ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )
                ExifOrientation.transformOf(orientation)
            } ?: IDENTITY_TRANSFORM
        } catch (e: Exception) {
            Log.w(TAG, "readExifTransform failed, treat as normal: $uri", e)
            IDENTITY_TRANSFORM
        }
    }

    /**
     * 对 Bitmap 施加 EXIF 像素变换：先旋转，再水平镜像。
     *
     * 镜像用 `postScale(-1f, 1f)` 沿垂直轴翻转；因 [Matrix] 变换作用于像素坐标，
     * 且 `postScale` 后接于旋转之后，恰好实现 [ExifTransform] 约定的「先旋转后镜像」语义。
     */
    private fun applyExifTransform(bitmap: Bitmap, transform: ExifTransform): Bitmap {
        val matrix = Matrix()
        if (transform.rotationDegrees != 0f) {
            matrix.postRotate(transform.rotationDegrees)
        }
        if (transform.flipHorizontal) {
            matrix.postScale(-1f, 1f)
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    companion object {
        private const val TAG = "CompressHelper"

        /** 无变换的复用实例，避免读不到 EXIF 的常见路径反复建对象。 */
        private val IDENTITY_TRANSFORM = ExifTransform(0f, false)
    }
}
