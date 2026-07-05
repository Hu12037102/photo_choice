package com.google.photochoice.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * 权限工具：
 * - API 34+：READ_MEDIA_IMAGES / READ_MEDIA_VIDEO / READ_MEDIA_VISUAL_USER_SELECTED（部分授权）
 * - API 33：READ_MEDIA_IMAGES / READ_MEDIA_VIDEO
 * - API 29-32：READ_EXTERNAL_STORAGE
 * - 调用方负责实际请求；本类只提供检测与申请所需权限名单。
 *
 * Android 14 部分授权语义：用户在系统弹窗选"选择部分照片"时，系统授予
 * VISUAL_USER_SELECTED 而 IMAGES/VIDEO 报 denied——此时选择器**可用**
 * （MediaStore 查询自动限定为用户挑选的媒体），必须视为已授权，
 * 否则会把刚授完权的用户错误引导到设置页。
 */
object PermissionHelper {

    fun requiredMediaPermissions(): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    fun hasMediaPermission(context: Context): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                // 全量授权（IMAGES/VIDEO 任一，系统按组同授）或部分授权均视为可用
                isGranted(context, Manifest.permission.READ_MEDIA_IMAGES) ||
                    isGranted(context, Manifest.permission.READ_MEDIA_VIDEO) ||
                    isGranted(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                // API 33 无部分授权；IMAGES/VIDEO 属同一权限组，弹窗一次性同授
                isGranted(context, Manifest.permission.READ_MEDIA_IMAGES) &&
                    isGranted(context, Manifest.permission.READ_MEDIA_VIDEO)
            else -> isGranted(context, Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
}
