package com.google.photochoice.ui.crop

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.photochoice.R
import com.google.photochoice.config.CropAspectRatio

/**
 * 裁剪页独立 Activity。承载 [CropFragment]；通过 setResult 回传裁剪后的 URI。
 *
 * 由 [com.google.photochoice.ui.grid.MediaGridFragment] 启动；不暴露给宿主 App。
 */
class CropActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SOURCE_URI = "source_uri"
        const val EXTRA_INITIAL_RATIO = "initial_ratio"
        const val EXTRA_RESULT_URI = "result_uri"

        private const val TAG_CROP = "crop"

        fun intent(
            context: Context,
            sourceUri: String,
            initialRatio: CropAspectRatio
        ): Intent = Intent(context, CropActivity::class.java).apply {
            putExtra(EXTRA_SOURCE_URI, sourceUri)
            putExtra(EXTRA_INITIAL_RATIO, initialRatio.name)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_crop)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.cropFragmentContainer)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(
                left = systemBars.left,
                top = systemBars.top,
                right = systemBars.right,
                bottom = systemBars.bottom,
            )
            insets
        }

        val sourceUri = intent.getStringExtra(EXTRA_SOURCE_URI)
        if (sourceUri.isNullOrBlank()) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }
        val initialRatio = parseInitialRatio(intent.getStringExtra(EXTRA_INITIAL_RATIO))

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(
                    R.id.cropFragmentContainer,
                    CropFragment.newInstance(sourceUri, initialRatio),
                    TAG_CROP
                )
                .commit()
        }

        supportFragmentManager.setFragmentResultListener(
            CropFragment.REQUEST_KEY, this
        ) { _, bundle ->
            val croppedUri = bundle.getString(CropFragment.EXTRA_CROPPED_URI)
            if (croppedUri.isNullOrBlank()) {
                setResult(Activity.RESULT_CANCELED)
            } else {
                setResult(
                    Activity.RESULT_OK,
                    Intent().putExtra(EXTRA_RESULT_URI, croppedUri)
                )
            }
            finish()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
        })
    }

    private fun parseInitialRatio(name: String?): CropAspectRatio {
        if (name.isNullOrBlank()) return CropAspectRatio.ORIGINAL
        return runCatching { CropAspectRatio.valueOf(name) }
            .getOrDefault(CropAspectRatio.ORIGINAL)
    }
}
