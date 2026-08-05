package com.google.photochoice.util

import androidx.exifinterface.media.ExifInterface

/**
 * EXIF 方向对应的像素变换：先旋转 [rotationDegrees]，再按 [flipHorizontal] 决定是否水平镜像。
 *
 * 顺序不可交换——"旋转后镜像"与"镜像后旋转"对含镜像的 4 种方向会得到不同结果，
 * 这里统一采用「先旋转、后镜像」，与 [ExifOrientation.transformOf] 的映射表一致。
 *
 * @param rotationDegrees 顺时针旋转角度，取值 0 / 90 / 180 / -90。
 * @param flipHorizontal 是否在旋转后沿垂直轴做水平镜像。
 */
internal data class ExifTransform(
    val rotationDegrees: Float,
    val flipHorizontal: Boolean
) {
    /** 是否为恒等变换。恒等时调用方可跳过一次 Bitmap 重建，省一份内存拷贝。 */
    val isIdentity: Boolean get() = rotationDegrees == 0f && !flipHorizontal
}

/**
 * EXIF 方向标签到像素变换的映射。
 *
 * 抽成不依赖 Android 图形类的纯函数，一是让 8 种方向的映射可被 JVM 单元测试覆盖
 * （[android.graphics.Matrix] 在单测环境是 stub，无法断言），二是让手工解码链
 * （[CompressHelper]）与将来可能出现的其它解码链共用同一份口径。
 *
 * 完整覆盖 EXIF 规范的 8 种方向，其中 4 种含镜像（FLIP_HORIZONTAL / FLIP_VERTICAL /
 * TRANSPOSE / TRANSVERSE）——多由前置摄像头自拍镜像、部分修图工具写出。
 * 漏掉镜像会让输出图左右颠倒，属正确性问题。
 */
internal object ExifOrientation {

    /**
     * 查表得到 [orientation] 对应的像素变换。
     *
     * @param orientation [ExifInterface.TAG_ORIENTATION] 的原始取值。
     * @return 对应变换；未定义或非法取值按 [ExifInterface.ORIENTATION_NORMAL] 处理，
     *         即返回恒等变换，避免脏数据导致图片被错误旋转。
     */
    fun transformOf(orientation: Int): ExifTransform = when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> ExifTransform(0f, true)
        ExifInterface.ORIENTATION_ROTATE_180 -> ExifTransform(180f, false)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> ExifTransform(180f, true)
        ExifInterface.ORIENTATION_TRANSPOSE -> ExifTransform(90f, true)
        ExifInterface.ORIENTATION_ROTATE_90 -> ExifTransform(90f, false)
        ExifInterface.ORIENTATION_TRANSVERSE -> ExifTransform(-90f, true)
        ExifInterface.ORIENTATION_ROTATE_270 -> ExifTransform(-90f, false)
        // 含 ORIENTATION_NORMAL、ORIENTATION_UNDEFINED 与任何越界值
        else -> ExifTransform(0f, false)
    }
}
