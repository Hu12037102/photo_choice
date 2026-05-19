package com.google.photochoice.util

import android.content.Context
import android.util.Log
import java.io.File

class SandboxCleaner(private val context: Context) {

    private val sandboxDir: File
        get() = File(context.cacheDir, "photo_choice")

    fun cleanExpired(maxAgeMs: Long = 24 * 60 * 60 * 1000) {
        val dir = sandboxDir
        if (!dir.exists()) return
        val cutoff = System.currentTimeMillis() - maxAgeMs
        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < cutoff) {
                if (!file.delete()) {
                    Log.w(TAG, "Failed to delete expired file: ${file.name}")
                }
            }
        }
    }

    fun cleanAll() {
        val dir = sandboxDir
        if (!dir.exists()) return
        dir.listFiles()?.forEach { file ->
            if (!file.delete()) {
                Log.w(TAG, "Failed to delete file: ${file.name}")
            }
        }
    }

    companion object {
        private const val TAG = "SandboxCleaner"
    }
}
