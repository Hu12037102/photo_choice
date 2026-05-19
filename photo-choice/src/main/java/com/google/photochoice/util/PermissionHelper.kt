package com.google.photochoice.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * 权限工具：
 * - API 33+：READ_MEDIA_IMAGES / READ_MEDIA_VIDEO
 * - API 29-32：READ_EXTERNAL_STORAGE
 * - 调用方负责实际请求；本类只提供检测与申请所需权限名单。
 */
object PermissionHelper {

    fun requiredMediaPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    fun hasMediaPermission(context: Context): Boolean {
        return requiredMediaPermissions().all { perm ->
            ContextCompat.checkSelfPermission(context, perm) ==
                PackageManager.PERMISSION_GRANTED
        }
    }
}
