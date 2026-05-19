package com.google.photochoice.sample

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.photochoice.PhotoChoice
import com.google.photochoice.config.CompressConfig
import com.google.photochoice.config.CropAspectRatio
import com.google.photochoice.config.CropConfig
import com.google.photochoice.config.MediaType
import com.google.photochoice.config.SelectMode
import com.google.photochoice.config.ThemeMode
import com.google.photochoice.util.PermissionHelper

class MainActivity : AppCompatActivity() {

    private lateinit var spinnerMediaType: Spinner
    private lateinit var spinnerSelectMode: Spinner
    private lateinit var spinnerSpanCount: Spinner
    private lateinit var spinnerTheme: Spinner
    private lateinit var spinnerCropRatio: Spinner
    private lateinit var inputMaxCount: TextInputEditText
    private lateinit var inputMinCount: TextInputEditText
    private lateinit var inputMaxVideoSec: TextInputEditText
    private lateinit var switchShowCamera: MaterialSwitch
    private lateinit var switchShowPreview: MaterialSwitch
    private lateinit var switchCrop: MaterialSwitch
    private lateinit var switchCompress: MaterialSwitch
    private lateinit var sectionVideo: View
    private lateinit var labelCropRatio: View
    private lateinit var textResult: TextView
    private lateinit var recyclerResults: RecyclerView
    private val resultAdapter = DemoResultAdapter { index ->
        selectedUris.takeIf { it.isNotEmpty() }?.let { uris ->
            DemoPreviewActivity.start(this, uris, index)
        }
    }
    private var selectedUris: List<Uri> = emptyList()

    private val mediaPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) {
            launchPicker()
        } else {
            Toast.makeText(this, R.string.demo_permission_denied, Toast.LENGTH_SHORT).show()
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

        bindViews()
        setupSpinners()
        setupDependentUi()
        setupPresets()
        setupResultList()
        findViewById<MaterialButton>(R.id.btnLaunch).setOnClickListener { requestLaunch() }
    }

    private fun setupResultList() {
        recyclerResults = findViewById(R.id.recyclerResults)
        recyclerResults.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerResults.adapter = resultAdapter
    }

    private fun bindViews() {
        spinnerMediaType = findViewById(R.id.spinnerMediaType)
        spinnerSelectMode = findViewById(R.id.spinnerSelectMode)
        spinnerSpanCount = findViewById(R.id.spinnerSpanCount)
        spinnerTheme = findViewById(R.id.spinnerTheme)
        spinnerCropRatio = findViewById(R.id.spinnerCropRatio)
        inputMaxCount = findViewById(R.id.inputMaxCount)
        inputMinCount = findViewById(R.id.inputMinCount)
        inputMaxVideoSec = findViewById(R.id.inputMaxVideoSec)
        switchShowCamera = findViewById(R.id.switchShowCamera)
        switchShowPreview = findViewById(R.id.switchShowPreview)
        switchCrop = findViewById(R.id.switchCrop)
        switchCompress = findViewById(R.id.switchCompress)
        sectionVideo = findViewById(R.id.sectionVideo)
        labelCropRatio = findViewById(R.id.labelCropRatio)
        textResult = findViewById(R.id.textResult)
    }

    private fun setupSpinners() {
        spinnerMediaType.adapter = arrayAdapter(R.array.demo_media_types)
        spinnerSelectMode.adapter = arrayAdapter(R.array.demo_select_modes)
        spinnerSpanCount.adapter = arrayAdapter(R.array.demo_span_counts)
        spinnerTheme.adapter = arrayAdapter(R.array.demo_theme_modes)
        spinnerCropRatio.adapter = arrayAdapter(R.array.demo_crop_ratios)
        spinnerSpanCount.setSelection(2) // default 4 columns

        spinnerMediaType.onItemSelectedListener = simpleSelectedListener { updateDependentUi() }
        spinnerSelectMode.onItemSelectedListener = simpleSelectedListener { updateDependentUi() }
        switchCrop.setOnCheckedChangeListener { _, _ -> updateCropUi() }
    }

    private fun setupDependentUi() {
        updateDependentUi()
        updateCropUi()
    }

    private fun setupPresets() {
        findViewById<MaterialButton>(R.id.btnPresetWechat).setOnClickListener {
            applyPreset(
                mediaType = MediaType.IMAGE,
                selectMode = SelectMode.MULTI,
                maxCount = 9,
                minCount = 1,
                spanCount = 4,
                showCamera = true,
                showPreview = true,
                crop = false,
                compress = false,
                theme = ThemeMode.FOLLOW_SYSTEM,
            )
        }
        findViewById<MaterialButton>(R.id.btnPresetAvatar).setOnClickListener {
            applyPreset(
                mediaType = MediaType.IMAGE,
                selectMode = SelectMode.SINGLE,
                maxCount = 1,
                minCount = 1,
                spanCount = 3,
                showCamera = true,
                showPreview = true,
                crop = true,
                cropRatioIndex = 1,
                compress = true,
                theme = ThemeMode.FOLLOW_SYSTEM,
            )
        }
        findViewById<MaterialButton>(R.id.btnPresetVideo).setOnClickListener {
            applyPreset(
                mediaType = MediaType.VIDEO,
                selectMode = SelectMode.MULTI,
                maxCount = 1,
                minCount = 1,
                spanCount = 3,
                showCamera = false,
                showPreview = true,
                crop = false,
                compress = false,
                theme = ThemeMode.FOLLOW_SYSTEM,
                maxVideoSec = 60,
            )
        }
        findViewById<MaterialButton>(R.id.btnPresetAllMedia).setOnClickListener {
            applyPreset(
                mediaType = MediaType.ALL,
                selectMode = SelectMode.MULTI,
                maxCount = 9,
                minCount = 1,
                spanCount = 4,
                showCamera = true,
                showPreview = true,
                crop = false,
                compress = false,
                theme = ThemeMode.FOLLOW_SYSTEM,
                maxVideoSec = 60,
            )
        }
        findViewById<MaterialButton>(R.id.btnPresetDark).setOnClickListener {
            applyPreset(
                mediaType = MediaType.IMAGE,
                selectMode = SelectMode.MULTI,
                maxCount = 9,
                minCount = 1,
                spanCount = 4,
                showCamera = true,
                showPreview = true,
                crop = false,
                compress = false,
                theme = ThemeMode.DARK,
            )
        }
    }

    private fun applyPreset(
        mediaType: MediaType,
        selectMode: SelectMode,
        maxCount: Int,
        minCount: Int,
        spanCount: Int,
        showCamera: Boolean,
        showPreview: Boolean,
        crop: Boolean,
        cropRatioIndex: Int = 0,
        compress: Boolean = false,
        theme: ThemeMode = ThemeMode.FOLLOW_SYSTEM,
        maxVideoSec: Int = 60,
    ) {
        spinnerMediaType.setSelection(mediaType.ordinal)
        spinnerSelectMode.setSelection(if (selectMode == SelectMode.MULTI) 0 else 1)
        inputMaxCount.setText(maxCount.toString())
        inputMinCount.setText(minCount.toString())
        spinnerSpanCount.setSelection((spanCount - 2).coerceIn(0, 4))
        switchShowCamera.isChecked = showCamera
        switchShowPreview.isChecked = showPreview
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
        inputMaxVideoSec.setText(maxVideoSec.toString())
        updateDependentUi()
        updateCropUi()
    }

    private fun updateDependentUi() {
        val single = spinnerSelectMode.selectedItemPosition == 1
        val mediaType = mediaTypeFromSpinner()
        val showsVideo = mediaType == MediaType.VIDEO || mediaType == MediaType.ALL

        sectionVideo.visibility = if (showsVideo) View.VISIBLE else View.GONE

        if (single) {
            inputMaxCount.setText("1")
            inputMaxCount.isEnabled = false
            switchCrop.isEnabled = mediaType != MediaType.VIDEO
        } else {
            inputMaxCount.isEnabled = true
            switchCrop.isChecked = false
            switchCrop.isEnabled = false
        }
        updateCropUi()
    }

    private fun updateCropUi() {
        val showCrop = switchCrop.isEnabled && switchCrop.isChecked
        val visibility = if (showCrop) View.VISIBLE else View.GONE
        labelCropRatio.visibility = visibility
        spinnerCropRatio.visibility = visibility
    }

    private fun requestLaunch() {
        if (!validateCounts()) return
        if (PermissionHelper.hasMediaPermission(this)) {
            launchPicker()
        } else {
            mediaPermissionLauncher.launch(PermissionHelper.requiredMediaPermissions())
        }
    }

    private fun validateCounts(): Boolean {
        val max = inputMaxCount.text?.toString()?.toIntOrNull() ?: return false
        val min = inputMinCount.text?.toString()?.toIntOrNull() ?: return false
        if (min !in 1..max) {
            Toast.makeText(
                this,
                getString(R.string.demo_invalid_count, max),
                Toast.LENGTH_SHORT
            ).show()
            return false
        }
        return true
    }

    private fun launchPicker() {
        val max = inputMaxCount.text.toString().toInt()
        val min = inputMinCount.text.toString().toInt()
        val span = spinnerSpanCount.selectedItem.toString().toInt()
        val mediaType = mediaTypeFromSpinner()
        val maxVideoMs = (inputMaxVideoSec.text?.toString()?.toLongOrNull() ?: 60L) * 1000L

        val builder = PhotoChoice.with(this)
            .maxSelectCount(max)
            .minSelectCount(min)
            .selectMode(if (spinnerSelectMode.selectedItemPosition == 0) SelectMode.MULTI else SelectMode.SINGLE)
            .mediaType(mediaType)
            .spanCount(span)
            .showCamera(switchShowCamera.isChecked)
            .showPreview(switchShowPreview.isChecked)
            .themeMode(themeFromSpinner())
            .maxVideoDuration(maxVideoMs)

        if (switchCrop.isEnabled && switchCrop.isChecked) {
            builder.cropConfig(
                CropConfig(
                    enabled = true,
                    aspectRatio = cropRatioFromSpinner(),
                )
            )
        }

        if (switchCompress.isChecked) {
            builder.compressConfig(CompressConfig(enabled = true))
        }

        builder.forResult(this) { result ->
            showSelectionResult(result?.uris.orEmpty())
            Toast.makeText(
                this,
                if (result != null) getString(R.string.demo_result_ok, result.uris.size)
                else getString(R.string.demo_result_cancel),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showSelectionResult(uris: List<Uri>) {
        selectedUris = uris
        if (uris.isEmpty()) {
            textResult.setText(R.string.demo_result_hint)
            recyclerResults.visibility = View.GONE
            resultAdapter.submitList(emptyList())
            return
        }
        textResult.text = getString(R.string.demo_result_ok, uris.size) +
            "\n" + getString(R.string.demo_result_tap_hint)
        recyclerResults.visibility = View.VISIBLE
        resultAdapter.submitList(uris)
    }

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
