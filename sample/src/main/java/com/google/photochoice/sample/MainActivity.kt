package com.google.photochoice.sample

import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.photochoice.PhotoChoice
import com.google.photochoice.PhotoChoiceResult
import com.google.photochoice.config.CompressConfig
import com.google.photochoice.config.CropAspectRatio
import com.google.photochoice.config.CropConfig
import com.google.photochoice.config.MediaType
import com.google.photochoice.config.ThemeMode

/**
 * PhotoChoice 全功能演示入口：预设场景 + 可微调 Builder 参数 + 结果路径展示。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var spinnerMediaType: Spinner
    private lateinit var spinnerSpanCount: Spinner
    private lateinit var spinnerTheme: Spinner
    private lateinit var spinnerCropRatio: Spinner
    private lateinit var inputSelectCount: TextInputEditText
    private lateinit var inputMinVideoSec: TextInputEditText
    private lateinit var inputMaxVideoSec: TextInputEditText
    private lateinit var inputCompressQuality: TextInputEditText
    private lateinit var inputCompressMaxEdge: TextInputEditText
    private lateinit var switchShowCamera: MaterialSwitch
    private lateinit var switchCrop: MaterialSwitch
    private lateinit var switchCompress: MaterialSwitch
    private lateinit var sectionVideo: View
    private lateinit var sectionCompressAdvanced: View
    private lateinit var labelCropRatio: View
    private lateinit var textCropHint: TextView
    private lateinit var textResult: TextView
    private lateinit var recyclerResults: RecyclerView
    private val resultAdapter = DemoResultAdapter { index ->
        selectedUris.takeIf { it.isNotEmpty() }?.let { uris ->
            DemoPreviewActivity.start(this, uris, index)
        }
    }
    private var selectedUris: List<Uri> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        bindViews()
        setupSpinners()
        setupDependentUi()
        setupPresets()
        setupResultList()
        findViewById<MaterialButton>(R.id.btnLaunch).setOnClickListener { launchPicker() }
        findViewById<MaterialButton>(R.id.btnCleanup).setOnClickListener { cleanupSandbox() }
    }

    private fun setupResultList() {
        recyclerResults = findViewById(R.id.recyclerResults)
        recyclerResults.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerResults.adapter = resultAdapter
    }

    private fun bindViews() {
        spinnerMediaType = findViewById(R.id.spinnerMediaType)
        spinnerSpanCount = findViewById(R.id.spinnerSpanCount)
        spinnerTheme = findViewById(R.id.spinnerTheme)
        spinnerCropRatio = findViewById(R.id.spinnerCropRatio)
        inputSelectCount = findViewById(R.id.inputSelectCount)
        inputMinVideoSec = findViewById(R.id.inputMinVideoSec)
        inputMaxVideoSec = findViewById(R.id.inputMaxVideoSec)
        inputCompressQuality = findViewById(R.id.inputCompressQuality)
        inputCompressMaxEdge = findViewById(R.id.inputCompressMaxEdge)
        switchShowCamera = findViewById(R.id.switchShowCamera)
        switchCrop = findViewById(R.id.switchCrop)
        switchCompress = findViewById(R.id.switchCompress)
        sectionVideo = findViewById(R.id.sectionVideo)
        sectionCompressAdvanced = findViewById(R.id.sectionCompressAdvanced)
        labelCropRatio = findViewById(R.id.labelCropRatio)
        textCropHint = findViewById(R.id.textCropHint)
        textResult = findViewById(R.id.textResult)
    }

    private fun setupSpinners() {
        spinnerMediaType.adapter = arrayAdapter(R.array.demo_media_types)
        spinnerSpanCount.adapter = arrayAdapter(R.array.demo_span_counts)
        spinnerTheme.adapter = arrayAdapter(R.array.demo_theme_modes)
        spinnerCropRatio.adapter = arrayAdapter(R.array.demo_crop_ratios)
        spinnerSpanCount.setSelection(2)

        spinnerMediaType.onItemSelectedListener = simpleSelectedListener { updateDependentUi() }
        switchCrop.setOnCheckedChangeListener { _, _ -> updateCropUi() }
        switchCompress.setOnCheckedChangeListener { _, _ -> updateCompressUi() }
        inputSelectCount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                updateDependentUi()
            }
        })
    }

    private fun setupDependentUi() {
        updateDependentUi()
        updateCropUi()
        updateCompressUi()
    }

    private fun setupPresets() {
        findViewById<MaterialButton>(R.id.btnPresetWechat).setOnClickListener {
            applyPreset(
                mediaType = MediaType.IMAGE,
                selectCount = 9,
                spanCount = 4,
                showCamera = true,
                crop = false,
                compress = false,
            )
        }
        findViewById<MaterialButton>(R.id.btnPresetWechatCompress).setOnClickListener {
            applyPreset(
                mediaType = MediaType.IMAGE,
                selectCount = 9,
                spanCount = 4,
                showCamera = true,
                crop = false,
                compress = true,
            )
        }
        findViewById<MaterialButton>(R.id.btnPresetLivePhoto).setOnClickListener {
            applyPreset(
                mediaType = MediaType.IMAGE,
                selectCount = 9,
                spanCount = 4,
                showCamera = true,
                crop = false,
                compress = true,
            )
        }
        findViewById<MaterialButton>(R.id.btnPresetAvatar).setOnClickListener {
            applyPreset(
                mediaType = MediaType.IMAGE,
                selectCount = 1,
                spanCount = 3,
                showCamera = true,
                crop = true,
                cropRatioIndex = 1,
                compress = true,
            )
        }
        findViewById<MaterialButton>(R.id.btnPresetVideo).setOnClickListener {
            applyPreset(
                mediaType = MediaType.VIDEO,
                selectCount = 1,
                spanCount = 3,
                showCamera = false,
                crop = false,
                compress = false,
                minVideoSec = 3,
                maxVideoSec = 60,
            )
        }
        findViewById<MaterialButton>(R.id.btnPresetAllMedia).setOnClickListener {
            applyPreset(
                mediaType = MediaType.ALL,
                selectCount = 9,
                spanCount = 4,
                showCamera = true,
                crop = false,
                compress = false,
                maxVideoSec = 60,
            )
        }
        findViewById<MaterialButton>(R.id.btnPresetDark).setOnClickListener {
            applyPreset(
                mediaType = MediaType.IMAGE,
                selectCount = 9,
                spanCount = 4,
                showCamera = true,
                crop = false,
                compress = false,
                theme = ThemeMode.DARK,
            )
        }
    }

    private fun applyPreset(
        mediaType: MediaType,
        selectCount: Int,
        spanCount: Int,
        showCamera: Boolean,
        crop: Boolean,
        cropRatioIndex: Int = 0,
        compress: Boolean = false,
        theme: ThemeMode = ThemeMode.FOLLOW_SYSTEM,
        minVideoSec: Int = 0,
        maxVideoSec: Int = 60,
    ) {
        spinnerMediaType.setSelection(mediaType.ordinal)
        inputSelectCount.setText(selectCount.toString())
        spinnerSpanCount.setSelection((spanCount - 2).coerceIn(0, 4))
        switchShowCamera.isChecked = showCamera
        switchCrop.isChecked = crop
        spinnerCropRatio.setSelection(cropRatioIndex)
        switchCompress.isChecked = compress
        spinnerTheme.setSelection(
            when (theme) {
                ThemeMode.LIGHT -> 1
                ThemeMode.DARK -> 2
                ThemeMode.FOLLOW_SYSTEM -> 0
            }
        )
        inputMinVideoSec.setText(minVideoSec.toString())
        inputMaxVideoSec.setText(maxVideoSec.toString())
        updateDependentUi()
    }

    private fun updateDependentUi() {
        val mediaType = mediaTypeFromSpinner()
        val selectCount = readSelectCount()
        val showsVideo = mediaType == MediaType.VIDEO || mediaType == MediaType.ALL
        sectionVideo.visibility = if (showsVideo) View.VISIBLE else View.GONE

        val canCrop = selectCount == 1 && mediaType != MediaType.VIDEO
        switchCrop.isEnabled = canCrop
        if (!canCrop) {
            switchCrop.isChecked = false
        }
        textCropHint.visibility = if (!canCrop) View.VISIBLE else View.GONE

        updateCropUi()
        updateCompressUi()
    }

    private fun updateCropUi() {
        val showCrop = switchCrop.isEnabled && switchCrop.isChecked
        val visibility = if (showCrop) View.VISIBLE else View.GONE
        labelCropRatio.visibility = visibility
        spinnerCropRatio.visibility = visibility
    }

    private fun updateCompressUi() {
        sectionCompressAdvanced.visibility =
            if (switchCompress.isChecked) View.VISIBLE else View.GONE
    }

    private fun launchPicker() {
        val selectCount = readSelectCount()
        val span = spinnerSpanCount.selectedItem.toString().toInt()
        val mediaType = mediaTypeFromSpinner()
        val minVideoMs = (inputMinVideoSec.text?.toString()?.toLongOrNull() ?: 0L) * 1000L
        val maxVideoMs = (inputMaxVideoSec.text?.toString()?.toLongOrNull() ?: 60L) * 1000L

        val builder = PhotoChoice.with(this)
            .selectCount(selectCount)
            .mediaType(mediaType)
            .spanCount(span)
            .showCamera(switchShowCamera.isChecked)
            .themeMode(themeFromSpinner())
            .minVideoDuration(minVideoMs)
            .maxVideoDuration(maxVideoMs)

        if (switchCrop.isEnabled && switchCrop.isChecked) {
            builder.cropConfig(
                CropConfig(
                    enabled = true,
                    aspectRatio = cropRatioFromSpinner(),
                )
            )
        }

        buildCompressConfig()?.let { builder.compressConfig(it) }

        builder.forResult(this) { result ->
            showSelectionResult(result)
            Toast.makeText(
                this,
                if (result != null) getString(R.string.demo_result_ok, result.uris.size)
                else getString(R.string.demo_result_cancel),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /** 读取并校验压缩参数；未开启压缩时返回 null。 */
    private fun buildCompressConfig(): CompressConfig? {
        if (!switchCompress.isChecked) return null
        val quality = inputCompressQuality.text?.toString()?.toIntOrNull()?.coerceIn(1, 100) ?: 80
        val maxEdge = inputCompressMaxEdge.text?.toString()?.toIntOrNull()?.coerceAtLeast(1) ?: 1920
        return CompressConfig(
            enabled = true,
            maxWidth = maxEdge,
            maxHeight = maxEdge,
            quality = quality,
        )
    }

    /** 清空组件沙盒缓存（压缩/裁剪/实况 MP4）。 */
    private fun cleanupSandbox() {
        PhotoChoice.cleanup(this)
        Toast.makeText(this, R.string.demo_cleanup_done, Toast.LENGTH_SHORT).show()
    }

    /**
     * 展示选择结果：缩略图 + 每条 path，并标注压缩/裁剪/原文件。
     */
    private fun showSelectionResult(result: PhotoChoiceResult?) {
        if (result == null || result.uris.isEmpty()) {
            selectedUris = emptyList()
            textResult.setText(R.string.demo_result_hint)
            recyclerResults.visibility = View.GONE
            resultAdapter.submitList(emptyList())
            return
        }
        selectedUris = result.uris
        val compressedCount = result.paths.count { isCompressedSandboxPath(it) }
        val croppedCount = result.paths.count { isCroppedSandboxPath(it) }
        val detail = buildString {
            append(getString(R.string.demo_result_ok, result.uris.size))
            if (compressedCount > 0 || croppedCount > 0) {
                append('\n')
                append(getString(R.string.demo_result_sandbox_count, compressedCount, croppedCount))
            }
            append('\n')
            append(getString(R.string.demo_result_tap_hint))
            append("\n\n")
            append(getString(R.string.demo_result_path_header))
            result.uris.forEachIndexed { index, uri ->
                val path = result.paths.getOrNull(index).orEmpty().ifBlank { uri.toString() }
                append('\n')
                append(index + 1)
                append(". ")
                append(
                    when {
                        isCompressedSandboxPath(path) -> getString(R.string.demo_result_tag_compressed)
                        isCroppedSandboxPath(path) -> getString(R.string.demo_result_tag_cropped)
                        else -> getString(R.string.demo_result_tag_original)
                    }
                )
                append('\n')
                append(path)
            }
            append("\n\n")
            append(getString(R.string.demo_result_cache_dir_hint))
        }
        textResult.text = detail
        recyclerResults.visibility = View.VISIBLE
        resultAdapter.submitList(result.uris)
    }

    private fun readSelectCount(): Int =
        inputSelectCount.text?.toString()?.toIntOrNull()?.coerceIn(1, 9) ?: 1

    private fun isCompressedSandboxPath(path: String): Boolean =
        path.contains("/photo_choice/compress_") || path.contains("\\photo_choice\\compress_")

    private fun isCroppedSandboxPath(path: String): Boolean =
        path.contains("/photo_choice/crop_") || path.contains("\\photo_choice\\crop_")

    private fun mediaTypeFromSpinner(): MediaType = when (spinnerMediaType.selectedItemPosition) {
        1 -> MediaType.VIDEO
        2 -> MediaType.ALL
        else -> MediaType.IMAGE
    }

    private fun themeFromSpinner(): ThemeMode = when (spinnerTheme.selectedItemPosition) {
        1 -> ThemeMode.LIGHT
        2 -> ThemeMode.DARK
        else -> ThemeMode.FOLLOW_SYSTEM
    }

    private fun cropRatioFromSpinner(): CropAspectRatio = when (spinnerCropRatio.selectedItemPosition) {
        1 -> CropAspectRatio.SQUARE
        2 -> CropAspectRatio.RATIO_3_4
        3 -> CropAspectRatio.RATIO_4_3
        4 -> CropAspectRatio.RATIO_9_16
        5 -> CropAspectRatio.RATIO_16_9
        else -> CropAspectRatio.ORIGINAL
    }

    private fun arrayAdapter(arrayRes: Int): ArrayAdapter<CharSequence> =
        ArrayAdapter.createFromResource(this, arrayRes, android.R.layout.simple_spinner_item).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

    private fun simpleSelectedListener(onSelected: () -> Unit) = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            onSelected()
        }

        override fun onNothingSelected(parent: AdapterView<*>?) = Unit
    }
}
