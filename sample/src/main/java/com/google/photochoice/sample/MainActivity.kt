package com.google.photochoice.sample

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.photochoice.PhotoChoice
import com.google.photochoice.config.MediaType
import com.google.photochoice.util.PermissionHelper

class MainActivity : AppCompatActivity() {

    private val mediaPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) {
            openPicker()
        } else {
            Toast.makeText(this, "需要媒体权限才能浏览相册", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<Button>(R.id.btnOpenPicker).setOnClickListener {
            if (PermissionHelper.hasMediaPermission(this)) {
                openPicker()
            } else {
                mediaPermissionLauncher.launch(PermissionHelper.requiredMediaPermissions())
            }
        }
    }

    private fun openPicker() {
        PhotoChoice.with(this)
            .maxSelectCount(9)
            .minSelectCount(1)
            .mediaType(MediaType.IMAGE)
            .spanCount(4)
            .showCamera(true)
            .forResult(this) { result ->
                val msg = if (result != null) {
                    "已选 ${result.uris.size} 张"
                } else {
                    "取消选择"
                }
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
    }
}
