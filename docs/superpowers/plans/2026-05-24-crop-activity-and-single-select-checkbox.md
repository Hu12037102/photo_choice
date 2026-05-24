# 裁剪页 Activity 化 & 单选模式隐藏 checkbox 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把裁剪页从 PhotoChoiceActivity 内嵌 Fragment 抽离成独立 `CropActivity`；当 `maxSelectCount == 1` 时隐藏网格 item 的 checkbox/序号/禁用蒙层。

**Architecture:** `CropActivity` 内嵌 `FragmentContainerView` 承载 `CropFragment`（结构与 `PreviewActivity` 一致）。`CropFragment` 解除对 `PhotoChoiceViewModel` 的依赖，源 URI 与初始裁剪比例改由 `arguments` 传入。`MediaGridFragment` 通过 `ActivityResultLauncher` 启动 `CropActivity` 并接收 URI。`MediaGridAdapter` 接收 `isSingleSelect` 参数，单选时跳过 checkbox 相关 UI 绑定。

**Tech Stack:** Kotlin、AndroidX Activity Result API、AppCompatActivity、ViewBinding、FragmentContainerView。

**设计稿：** [docs/superpowers/specs/2026-05-24-crop-activity-and-single-select-checkbox-design.md](../specs/2026-05-24-crop-activity-and-single-select-checkbox-design.md)

**关于测试方式：** 本仓库的功能验证依赖 sample app 手动测试（Android UI/Activity 切换无法用 JVM 单元测试覆盖）。每个 Task 末尾使用 `./gradlew :photo-choice:assembleDebug :sample:assembleDebug` 验证编译，整体功能验证集中在 Task 8。

---

## 文件结构

| 操作 | 文件 | 职责 |
|------|------|------|
| 新建 | `photo-choice/src/main/java/com/google/photochoice/ui/crop/CropActivity.kt` | 承载 `CropFragment` 的独立 Activity；处理 Intent 入参、edge-to-edge、结果回传 |
| 新建 | `photo-choice/src/main/res/layout/activity_crop.xml` | `CropActivity` 根布局（FragmentContainerView） |
| 修改 | `photo-choice/src/main/java/com/google/photochoice/ui/crop/CropFragment.kt` | 解除 ViewModel 依赖；初始比例改由 args 传入；取消按钮 `requireActivity().finish()` |
| 修改 | `photo-choice/src/main/java/com/google/photochoice/ui/PhotoChoiceActivity.kt` | 删除 `enterCrop/exitCrop/setFragmentResultListener`；新增 `finishWithCropResult` |
| 修改 | `photo-choice/src/main/java/com/google/photochoice/viewmodel/PhotoChoiceViewModel.kt` | 删除 `_showCrop/showCrop/navigateToCrop/dismissCrop` |
| 修改 | `photo-choice/src/main/java/com/google/photochoice/ui/grid/MediaGridFragment.kt` | 通过 `ActivityResultLauncher` 启动 `CropActivity`；adapter 传 `isSingleSelect` |
| 修改 | `photo-choice/src/main/java/com/google/photochoice/ui/grid/MediaGridAdapter.kt` | 新增 `isSingleSelect` 参数；单选时跳过 checkbox UI |
| 修改 | `photo-choice/src/main/AndroidManifest.xml` | 注册 `CropActivity` |
| 修改 | `photo-choice/src/main/res/values/themes.xml` | 新增 `Theme.PhotoChoice.Crop` |

---

## Task 1: 主题与 Manifest 注册

**Files:**
- Modify: `photo-choice/src/main/res/values/themes.xml`
- Modify: `photo-choice/src/main/AndroidManifest.xml`
- Create: `photo-choice/src/main/res/layout/activity_crop.xml`

- [ ] **Step 1: 添加 `Theme.PhotoChoice.Crop` 主题**

修改 `photo-choice/src/main/res/values/themes.xml`，在 `Theme.PhotoChoice.Preview` 块下方插入：

```xml
    <style name="Theme.PhotoChoice.Crop" parent="Theme.PhotoChoice">
        <item name="android:windowBackground">@color/photochoice_preview_bg</item>
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@color/photochoice_preview_bg</item>
        <item name="android:windowLightStatusBar">false</item>
        <item name="android:windowLightNavigationBar">false</item>
    </style>
```

- [ ] **Step 2: 注册 `CropActivity` 到 Manifest**

修改 `photo-choice/src/main/AndroidManifest.xml`，在 `PreviewActivity` 的 `</activity>` 后追加：

```xml
        <activity
            android:name=".ui.crop.CropActivity"
            android:exported="false"
            android:theme="@style/Theme.PhotoChoice.Crop" />
```

- [ ] **Step 3: 创建 `activity_crop.xml` 根布局**

创建 `photo-choice/src/main/res/layout/activity_crop.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.fragment.app.FragmentContainerView
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/cropFragmentContainer"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/photochoice_preview_bg" />
```

- [ ] **Step 4: 编译验证**

Run: `.\gradlew.bat :photo-choice:assembleDebug`
Expected: BUILD SUCCESSFUL（此时还没有 `CropActivity` 类，因此 Manifest 引用的类不存在 —— 应该会报 `AndroidManifest` 引用未定义类，跳过本 step 的"必须 SUCCESSFUL"，验收标准放宽为：除 "Class referenced in the manifest, com.google.photochoice.ui.crop.CropActivity, was not found" 之外不出现其他错误）

> 注：编译期 Manifest 检查不一定阻断构建，但若构建失败仅因为 `CropActivity` 不存在，则 Task 1 验证通过 —— 此问题在 Task 2 创建类后即解决。

- [ ] **Step 5: 提交**

```powershell
git add photo-choice/src/main/res/values/themes.xml photo-choice/src/main/AndroidManifest.xml photo-choice/src/main/res/layout/activity_crop.xml
git commit -m "feat(crop): 注册 CropActivity 主题与布局"
```

---

## Task 2: 新建 `CropActivity`

**Files:**
- Create: `photo-choice/src/main/java/com/google/photochoice/ui/crop/CropActivity.kt`

- [ ] **Step 1: 创建 `CropActivity.kt`**

文件内容：

```kotlin
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
```

- [ ] **Step 2: 编译验证**

Run: `.\gradlew.bat :photo-choice:assembleDebug`
Expected: 编译失败 —— `CropFragment.newInstance(sourceUri, initialRatio)` 签名不匹配（当前是 `newInstance(uri: String)`）。这是预期失败，下一个 Task 修复。

> 这一步是有意保留编译错误，因为本 Task 的代码独立成立，下一个 Task 会让两者契合。如果你倾向"每个 Task 都通过编译"，可以将本 Step 跳过，将 commit 与 Task 3 合并 —— 这里选择保留以让 Task 边界清晰。

- [ ] **Step 3: 提交（带 WIP 标记）**

```powershell
git add photo-choice/src/main/java/com/google/photochoice/ui/crop/CropActivity.kt
git commit -m "feat(crop): 新建 CropActivity（依赖 CropFragment 新签名，下一 Task 完成）"
```

---

## Task 3: 改造 `CropFragment` 解除 ViewModel 依赖

**Files:**
- Modify: `photo-choice/src/main/java/com/google/photochoice/ui/crop/CropFragment.kt`

- [ ] **Step 1: 重写 `CropFragment.kt` 内容**

完整替换为：

```kotlin
package com.google.photochoice.ui.crop

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.photochoice.R
import com.google.photochoice.config.CropAspectRatio
import com.google.photochoice.databinding.FragmentCropBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import androidx.lifecycle.lifecycleScope

class CropFragment : Fragment() {

    private var _binding: FragmentCropBinding? = null
    private val binding get() = _binding!!
    private var sourceUri: String? = null
    private var currentRatio = CropAspectRatio.ORIGINAL

    companion object {
        const val REQUEST_KEY = "photochoice_crop_result"
        const val EXTRA_CROPPED_URI = "cropped_uri"
        private const val CROP_QUALITY = 95
        private const val ARG_URI = "uri"
        private const val ARG_INITIAL_RATIO = "initial_ratio"

        fun newInstance(uri: String, initialRatio: CropAspectRatio): CropFragment {
            return CropFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_URI, uri)
                    putString(ARG_INITIAL_RATIO, initialRatio.name)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCropBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sourceUri = arguments?.getString(ARG_URI)

        sourceUri?.let { uri ->
            Glide.with(this)
                .load(uri)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .into(binding.cropView)
        }

        binding.btnCancel.setOnClickListener { requireActivity().finish() }
        binding.btnConfirm.setOnClickListener { performCrop() }

        binding.btnRatioFree.setOnClickListener { selectRatio(CropAspectRatio.ORIGINAL) }
        binding.btnRatio11.setOnClickListener { selectRatio(CropAspectRatio.SQUARE) }
        binding.btnRatio34.setOnClickListener { selectRatio(CropAspectRatio.RATIO_3_4) }
        binding.btnRatio916.setOnClickListener { selectRatio(CropAspectRatio.RATIO_9_16) }

        val initialRatioName = arguments?.getString(ARG_INITIAL_RATIO)
        val initialRatio = if (initialRatioName != null) {
            runCatching { CropAspectRatio.valueOf(initialRatioName) }
                .getOrDefault(CropAspectRatio.ORIGINAL)
        } else {
            CropAspectRatio.ORIGINAL
        }
        selectRatio(initialRatio)
    }

    private fun selectRatio(ratio: CropAspectRatio) {
        currentRatio = ratio
        binding.cropView.aspectRatio = ratio
        updateRatioButtons()
    }

    private fun updateRatioButtons() {
        applyButtonState(binding.btnRatioFree, currentRatio == CropAspectRatio.ORIGINAL)
        applyButtonState(binding.btnRatio11, currentRatio == CropAspectRatio.SQUARE)
        applyButtonState(binding.btnRatio34, currentRatio == CropAspectRatio.RATIO_3_4)
        applyButtonState(binding.btnRatio916, currentRatio == CropAspectRatio.RATIO_9_16)
    }

    private fun applyButtonState(button: AppCompatTextView, selected: Boolean) {
        val ctx = button.context
        button.setTextColor(
            ContextCompat.getColor(
                ctx,
                if (selected) R.color.photochoice_preview_text else R.color.photochoice_icon_secondary
            )
        )
        button.setBackgroundResource(
            if (selected) R.drawable.bg_ratio_selected else android.R.color.transparent
        )
    }

    private fun performCrop() {
        val view = binding.cropView
        viewLifecycleOwner.lifecycleScope.launch {
            val outputFile = withContext(Dispatchers.IO) {
                val cropped: Bitmap = view.crop() ?: return@withContext null
                try {
                    val outputDir = File(requireContext().cacheDir, "photo_choice").also { it.mkdirs() }
                    val file = File(outputDir, "crop_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(file).use { fos ->
                        cropped.compress(Bitmap.CompressFormat.JPEG, CROP_QUALITY, fos)
                    }
                    file
                } finally {
                    cropped.recycle()
                }
            }
            if (outputFile == null) {
                Toast.makeText(
                    requireContext(), getString(R.string.photochoice_crop_failed), Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
            val uri = Uri.fromFile(outputFile).toString()
            parentFragmentManager.setFragmentResult(
                REQUEST_KEY,
                Bundle().apply { putString(EXTRA_CROPPED_URI, uri) }
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
```

主要变更：
- 移除 `viewModels` 与 `PhotoChoiceViewModel` 引用
- `newInstance(uri, initialRatio)` 双参签名，比例通过 args 传入
- `btnCancel.setOnClickListener` 改为 `requireActivity().finish()`
- `onViewCreated` 中读取 `ARG_INITIAL_RATIO` 而非 `viewModel.config.cropConfig.aspectRatio`

- [ ] **Step 2: 编译验证**

Run: `.\gradlew.bat :photo-choice:assembleDebug`
Expected: 编译失败 —— `MediaGridFragment` 与 `PhotoChoiceActivity` 仍引用 `viewModel.navigateToCrop / showCrop / dismissCrop`，下一些 Task 修复。

- [ ] **Step 3: 提交**

```powershell
git add photo-choice/src/main/java/com/google/photochoice/ui/crop/CropFragment.kt
git commit -m "refactor(crop): CropFragment 解除 ViewModel 依赖，比例通过 args 传入"
```

---

## Task 4: `MediaGridFragment` 改用 `ActivityResultLauncher` 启动裁剪

**Files:**
- Modify: `photo-choice/src/main/java/com/google/photochoice/ui/grid/MediaGridFragment.kt`

- [ ] **Step 1: 添加 `cropLauncher` 与 import**

文件顶部 import 区追加：

```kotlin
import com.google.photochoice.ui.crop.CropActivity
```

类成员（`takePictureLauncher` 块附近）追加：

```kotlin
    // ── 裁剪页结果接收 ──────────────────────────────────────────────────────────
    private val cropLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != android.app.Activity.RESULT_OK) return@registerForActivityResult
            val uri = result.data?.getStringExtra(CropActivity.EXTRA_RESULT_URI) ?: return@registerForActivityResult
            (requireActivity() as PhotoChoiceActivity).finishWithCropResult(uri)
        }
```

- [ ] **Step 2: 替换单选裁剪路径**

定位 `setupAdaptersAndRecyclerView` 中 `onItemClick` lambda，将：

```kotlin
                if (config.selectMode == SelectMode.SINGLE && config.cropConfig.enabled) {
                    viewModel.selectionManager.select(mediaFile)
                    viewModel.navigateToCrop(mediaFile.uri)
```

替换为：

```kotlin
                if (config.selectMode == SelectMode.SINGLE && config.cropConfig.enabled) {
                    viewModel.selectionManager.select(mediaFile)
                    cropLauncher.launch(
                        CropActivity.intent(
                            requireContext(),
                            mediaFile.uri,
                            config.cropConfig.aspectRatio
                        )
                    )
```

- [ ] **Step 3: 编译验证**

Run: `.\gradlew.bat :photo-choice:assembleDebug`
Expected: 编译失败 —— `PhotoChoiceActivity.finishWithCropResult` 尚未定义，且 `viewModel.navigateToCrop` 仍被旧代码（`PhotoChoiceActivity.observeState`）引用。下一 Task 修复。

- [ ] **Step 4: 提交**

```powershell
git add photo-choice/src/main/java/com/google/photochoice/ui/grid/MediaGridFragment.kt
git commit -m "feat(crop): MediaGridFragment 通过 ActivityResultLauncher 启动 CropActivity"
```

---

## Task 5: `PhotoChoiceActivity` 移除内嵌裁剪逻辑

**Files:**
- Modify: `photo-choice/src/main/java/com/google/photochoice/ui/PhotoChoiceActivity.kt`

- [ ] **Step 1: 删除 `TAG_CROP` 常量**

定位 companion object 中：

```kotlin
        private const val TAG_GRID = "grid"
        private const val TAG_CROP = "crop"
```

替换为：

```kotlin
        private const val TAG_GRID = "grid"
```

- [ ] **Step 2: 删除 `observeState` 中裁剪导航与 setFragmentResultListener**

定位并删除整段：

```kotlin
        // 裁剪导航
        lifecycleScope.launch {
            viewModel.showCrop.collect { uri ->
                if (uri != null) enterCrop(uri) else exitCrop()
            }
        }
```

以及：

```kotlin
        // 裁剪结果监听
        supportFragmentManager.setFragmentResultListener(
            CropFragment.REQUEST_KEY, this
        ) { _, bundle ->
            val croppedUri = bundle.getString(CropFragment.EXTRA_CROPPED_URI) ?: return@setFragmentResultListener
            val callback = pendingResultCallback
            pendingResultCallback = null
            resultDelivered = true
            callback?.invoke(
                PhotoChoiceResult(
                    uris = listOf(croppedUri.toUri()),
                    paths = listOf(croppedUri.toUri().path ?: croppedUri)
                )
            )
            finish()
        }
```

- [ ] **Step 3: 删除 `enterCrop` 与 `exitCrop` 方法**

定位并整段删除：

```kotlin
    private fun enterCrop(uri: String) {
        binding.scrollingDateHeader.hideImmediately()
        binding.toolbar.visibility = View.GONE
        binding.toolbarDivider.visibility = View.GONE
        binding.bottomBar.hideImmediately()
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, 0, 0, android.R.anim.fade_out)
            .replace(binding.fragmentContainer.id, CropFragment.newInstance(uri), TAG_CROP)
            .addToBackStack(TAG_CROP)
            .commit()
    }

    private fun exitCrop() {
        if (supportFragmentManager.findFragmentByTag(TAG_CROP) == null) return
        supportFragmentManager.popBackStack(
            TAG_CROP, FragmentManager.POP_BACK_STACK_INCLUSIVE
        )
        binding.toolbar.visibility = View.VISIBLE
        binding.toolbarDivider.visibility = View.VISIBLE
        // 恢复底部栏时仍按当前选中数决定是否展开
        if (viewModel.albums.value.isNotEmpty()) {
            binding.bottomBar.restoreForSelectionCount(viewModel.selectionState.value.count)
        }
    }
```

- [ ] **Step 4: 删除 `setupBackPress` 中 `showCrop` 分支**

定位：

```kotlin
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    viewModel.showCrop.value != null -> viewModel.dismissCrop()
                    binding.albumDropdownLayer.isShowing() -> binding.albumDropdownLayer.dismiss()
                    else -> finishWithCancel()
                }
            }
        })
```

替换为：

```kotlin
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    binding.albumDropdownLayer.isShowing() -> binding.albumDropdownLayer.dismiss()
                    else -> finishWithCancel()
                }
            }
        })
```

- [ ] **Step 5: 新增 `finishWithCropResult` 方法**

在 `finishWithResult()` 方法之后插入：

```kotlin
    /**
     * 来自 [com.google.photochoice.ui.crop.CropActivity] 的裁剪结果回调入口。
     * 由 [com.google.photochoice.ui.grid.MediaGridFragment] 在 ActivityResultLauncher 回调中调用。
     */
    fun finishWithCropResult(croppedUri: String) {
        if (resultDelivered) return
        val callback = pendingResultCallback
        pendingResultCallback = null
        resultDelivered = true
        val uri = croppedUri.toUri()
        callback?.invoke(
            PhotoChoiceResult(
                uris = listOf(uri),
                paths = listOf(uri.path ?: croppedUri)
            )
        )
        finish()
    }
```

- [ ] **Step 6: 清理 unused import**

删除文件顶部已不再需要的 import（编译器会警告，按警告删）。预期可删除：

```kotlin
import androidx.fragment.app.FragmentManager
import com.google.photochoice.ui.crop.CropFragment
```

> 验证：删除前用 IDE / `grep` 确认这两个符号是否在 `PhotoChoiceActivity.kt` 内还有其他引用。如果没有，则一并删除。

- [ ] **Step 7: 编译验证**

Run: `.\gradlew.bat :photo-choice:assembleDebug`
Expected: 编译失败 —— `viewModel.showCrop / navigateToCrop / dismissCrop` 已无消费者但仍存在；`PhotoChoiceViewModel` 内方法定义保留，Task 6 删除。本 Step 应不再有 `PhotoChoiceActivity` 相关编译错误。

- [ ] **Step 8: 提交**

```powershell
git add photo-choice/src/main/java/com/google/photochoice/ui/PhotoChoiceActivity.kt
git commit -m "refactor(crop): PhotoChoiceActivity 移除内嵌裁剪逻辑，新增 finishWithCropResult"
```

---

## Task 6: `PhotoChoiceViewModel` 删除 `showCrop` 状态

**Files:**
- Modify: `photo-choice/src/main/java/com/google/photochoice/viewmodel/PhotoChoiceViewModel.kt`

- [ ] **Step 1: 删除 `_showCrop / showCrop / navigateToCrop / dismissCrop`**

定位并删除：

```kotlin
    private val _showCrop = MutableStateFlow<String?>(null)
    val showCrop: StateFlow<String?> = _showCrop.asStateFlow()
```

```kotlin
    fun navigateToCrop(uri: String) {
        _showCrop.value = uri
    }

    fun dismissCrop() {
        _showCrop.value = null
    }
```

- [ ] **Step 2: 编译验证**

Run: `.\gradlew.bat :photo-choice:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```powershell
git add photo-choice/src/main/java/com/google/photochoice/viewmodel/PhotoChoiceViewModel.kt
git commit -m "refactor(crop): 删除 ViewModel 中已不再使用的 showCrop 状态"
```

---

## Task 7: `MediaGridAdapter` 单选模式隐藏 checkbox

**Files:**
- Modify: `photo-choice/src/main/java/com/google/photochoice/ui/grid/MediaGridAdapter.kt`
- Modify: `photo-choice/src/main/java/com/google/photochoice/ui/grid/MediaGridFragment.kt`

- [ ] **Step 1: `MediaGridAdapter` 增加 `isSingleSelect` 参数**

定位类签名：

```kotlin
class MediaGridAdapter(
    private val isSelected: (Long) -> Boolean,
    private val getSelectionOrder: (Long) -> Int,
    private val isFull: () -> Boolean,
    private val onCheckboxClick: (MediaFile) -> Unit,
    private val onItemClick: (MediaFile) -> Unit,
    private val motionPhotoBadgeResolver: MotionPhotoBadgeResolver? = null
) : PagingDataAdapter<MediaFile, MediaGridAdapter.MediaVH>(DiffCallback) {
```

替换为：

```kotlin
class MediaGridAdapter(
    private val isSelected: (Long) -> Boolean,
    private val getSelectionOrder: (Long) -> Int,
    private val isFull: () -> Boolean,
    private val onCheckboxClick: (MediaFile) -> Unit,
    private val onItemClick: (MediaFile) -> Unit,
    private val motionPhotoBadgeResolver: MotionPhotoBadgeResolver? = null,
    private val isSingleSelect: Boolean = false
) : PagingDataAdapter<MediaFile, MediaGridAdapter.MediaVH>(DiffCallback) {
```

- [ ] **Step 2: `MediaVH.bind` 单选模式跳过 checkbox**

定位 `bind(mediaItem: MediaFile)` 方法：

```kotlin
        fun bind(mediaItem: MediaFile) {
            Glide.with(ivThumbnail)
                .load(mediaItem.uri.toUri())
                .override(THUMBNAIL_PX)
                .centerCrop()
                .skipMemoryCache(false)
                .priority(Priority.LOW)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .placeholder(R.color.photochoice_thumbnail_placeholder)
                .error(R.color.photochoice_thumbnail_placeholder)
                .into(ivThumbnail)
            bindSelectionState(mediaItem)
            bindVideoIndicator(mediaItem)
            bindLivePhotoIndicator(mediaItem)
            itemView.setOnClickListener { onItemClick(mediaItem) }
            touchTarget.setOnClickListener { onCheckboxClick(mediaItem) }
        }
```

替换为：

```kotlin
        fun bind(mediaItem: MediaFile) {
            Glide.with(ivThumbnail)
                .load(mediaItem.uri.toUri())
                .override(THUMBNAIL_PX)
                .centerCrop()
                .skipMemoryCache(false)
                .priority(Priority.LOW)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .placeholder(R.color.photochoice_thumbnail_placeholder)
                .error(R.color.photochoice_thumbnail_placeholder)
                .into(ivThumbnail)
            if (isSingleSelect) {
                checkbox.visibility = View.GONE
                tvOrder.visibility = View.GONE
                disabledOverlay.visibility = View.GONE
                touchTarget.visibility = View.GONE
            } else {
                checkbox.visibility = View.VISIBLE
                touchTarget.visibility = View.VISIBLE
                bindSelectionState(mediaItem)
            }
            bindVideoIndicator(mediaItem)
            bindLivePhotoIndicator(mediaItem)
            itemView.setOnClickListener { onItemClick(mediaItem) }
            touchTarget.setOnClickListener { onCheckboxClick(mediaItem) }
        }
```

> 注：`touchTarget.setOnClickListener` 即使在单选下也保留，因为 `View.GONE` 时不会响应点击；保留赋值不增加运行时开销，避免与 `bind` 重用 ViewHolder 时的状态泄漏。

- [ ] **Step 3: `bindSelectionState` 单选下早返回（防御）**

定位：

```kotlin
        fun bindSelectionState(mediaItem: MediaFile) {
            val order = getSelectionOrder(mediaItem.id)
            if (order > 0) {
                checkbox.setBackgroundResource(R.drawable.bg_checkbox_selected)
                tvOrder.visibility = View.VISIBLE
                tvOrder.text = String.format(Locale.ROOT, "%d", order)
                disabledOverlay.visibility = View.GONE
            } else {
                checkbox.setBackgroundResource(R.drawable.bg_checkbox_unselected)
                tvOrder.visibility = View.GONE
                disabledOverlay.visibility = if (isFull()) View.VISIBLE else View.GONE
            }
        }
```

替换为：

```kotlin
        fun bindSelectionState(mediaItem: MediaFile) {
            if (isSingleSelect) {
                // 单选模式不展示 checkbox / 序号 / 禁用蒙层
                return
            }
            val order = getSelectionOrder(mediaItem.id)
            if (order > 0) {
                checkbox.setBackgroundResource(R.drawable.bg_checkbox_selected)
                tvOrder.visibility = View.VISIBLE
                tvOrder.text = String.format(Locale.ROOT, "%d", order)
                disabledOverlay.visibility = View.GONE
            } else {
                checkbox.setBackgroundResource(R.drawable.bg_checkbox_unselected)
                tvOrder.visibility = View.GONE
                disabledOverlay.visibility = if (isFull()) View.VISIBLE else View.GONE
            }
        }
```

- [ ] **Step 4: `MediaGridFragment` 创建 adapter 时传入 `isSingleSelect`**

定位 `setupAdaptersAndRecyclerView` 中：

```kotlin
        gridMediaAdapter = MediaGridAdapter(
            isSelected = { viewModel.isSelected(it) },
            getSelectionOrder = { viewModel.getSelectionOrder(it) },
            isFull = { viewModel.selectionState.value.isFull },
            onCheckboxClick = { mediaFile ->
                if (viewModel.toggleSelection(mediaFile)) {
                    if (!viewModel.isSelected(mediaFile.id)) {
                        mediaAdapter.notifyAllSelectionChanged()
                    } else {
                        mediaAdapter.notifyMediaItemChanged(mediaFile.id)
                    }
                }
            },
            motionPhotoBadgeResolver = motionPhotoBadgeResolver,
            onItemClick = { mediaFile ->
```

把 `motionPhotoBadgeResolver = motionPhotoBadgeResolver,` 这行后面、`onItemClick = ...` 这行的同一参数列表里，追加 `isSingleSelect = config.maxSelectCount == 1,`。注意 `onItemClick` 必须保持已有顺序；既然 `onItemClick` 是 lambda 长块，最简改法是在它上方加：

```kotlin
            motionPhotoBadgeResolver = motionPhotoBadgeResolver,
            isSingleSelect = config.maxSelectCount == 1,
            onItemClick = { mediaFile ->
```

- [ ] **Step 5: 编译验证**

Run: `.\gradlew.bat :photo-choice:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 提交**

```powershell
git add photo-choice/src/main/java/com/google/photochoice/ui/grid/MediaGridAdapter.kt photo-choice/src/main/java/com/google/photochoice/ui/grid/MediaGridFragment.kt
git commit -m "feat(grid): maxSelectCount==1 时隐藏网格 checkbox"
```

---

## Task 8: 全量编译 + Lint + 手动验证

**Files:** 无新改动；本 Task 只验证。

- [ ] **Step 1: 全量编译**

Run: `.\gradlew.bat :photo-choice:assembleDebug :sample:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Lint 检查（无 @Deprecated 引入）**

Run: `.\gradlew.bat :photo-choice:lint`
Expected: 无新增 lint error / `DeprecatedCall` 警告。如果有新增告警，回退到对应 Task 修复。

- [ ] **Step 3: 安装 sample 到设备/模拟器**

Run: `.\gradlew.bat :sample:installDebug`
Expected: APK 安装成功。

- [ ] **Step 4: 手动验证场景**

逐项执行以下场景，记录是否通过：

1. **多选 + 裁剪关闭**（默认 maxSelectCount=9）：进入选择器 → 网格上每个 item 显示 checkbox → 选 3 张 → 完成。✔ checkbox 正常；序号正常。
2. **单选 + 裁剪开启**（sample 中需提供入口；若无现成入口，临时在 `sample` 的 `MainActivity` 添加按钮，配置 `maxSelectCount=1, selectMode=SINGLE, cropConfig.enabled=true`）：进入 → 网格上**无任何 checkbox / 序号 / 禁用蒙层** → 点击一个图 → 进入 `CropActivity`（独立任务栈页面，非 fragment 切换）→ 裁剪 → 返回宿主，回调有 URI。
3. **单选 + 裁剪关闭**（`maxSelectCount=1, cropConfig.enabled=false`）：网格**无 checkbox** → 点击一个图（走现有 fall-through 路径，行为同 main 分支不变）。
4. **裁剪取消**（场景 2 流程进入 `CropActivity` 后）：按返回键 → 回到网格无异常，selectionManager 中之前选中的 item 仍在选中态（多选下可见，单选下下次点击会替换）。
5. **裁剪页旋转**：进入 `CropActivity` 后旋转设备 → 不崩溃，CropFragment 通过 args 重建。
6. **后台/前台切换**：在 `CropActivity` 时按 Home 切到桌面 → 切回 → 不崩溃，状态保留。
7. **预览页与裁剪页互不干扰**：先在多选下进预览（点击图）→ 退出 → 切到单选+裁剪配置 → 点图 → `CropActivity` 正常打开。

- [ ] **Step 5: 验证完成提交（如有任何小修复）**

如果 Step 4 发现问题，回退到对应 Task 修复并 commit。如果所有场景通过，无需额外 commit。

---

## Self-Review

**1. Spec 覆盖：**
- 设计稿"改动 1：裁剪页独立 Activity 承载" → Task 1 (主题/Manifest)、Task 2 (CropActivity)、Task 3 (CropFragment)、Task 4 (MediaGridFragment)、Task 5 (PhotoChoiceActivity)、Task 6 (ViewModel) ✔
- 设计稿"改动 2：单选模式隐藏 checkbox" → Task 7 ✔
- 验证清单 → Task 8 全覆盖 ✔

**2. 占位符扫描：** 无 TBD / TODO / "类似 Task N"。每个修改步骤都给出了完整代码片段。✔

**3. 类型/签名一致性：**
- `CropActivity.intent(context, sourceUri, initialRatio: CropAspectRatio)` ↔ `CropFragment.newInstance(uri: String, initialRatio: CropAspectRatio)` ✔
- `CropActivity.EXTRA_RESULT_URI` ↔ `MediaGridFragment.cropLauncher` 中读取的字段 ✔
- `CropFragment.REQUEST_KEY / EXTRA_CROPPED_URI` ↔ `CropActivity.setFragmentResultListener` ✔
- `PhotoChoiceActivity.finishWithCropResult(croppedUri: String)` ↔ `MediaGridFragment.cropLauncher` 调用 ✔
- `MediaGridAdapter` 构造参数 `isSingleSelect: Boolean` 在 `MediaGridFragment` 创建处与类签名一致 ✔

**4. 隐含风险：**
- Task 1-3 之间存在编译失败的中间提交（已在 plan 中明确标注），git 历史可读但无法 bisect。可接受 —— 这是把改动分解为可读小步骤的代价；如果倾向"每步都通过编译"，可将 Task 1+2+3 合并为一个 commit。
