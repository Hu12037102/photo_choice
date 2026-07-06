# Live Photo 静态导出列表状态 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不改变现有 Live Photo 检测、播放和默认导出行为的前提下，为“导出静态”增加列表斜杠角标，并保证静态导出失败时绝不回传 Live 原图。

**Architecture:** `LivePhotoExportPolicy` 继续作为会话内唯一状态源，但改为发布静态导出媒体 ID 集合；预览写入状态，列表按 ID 增量刷新，导出层读取同一状态。列表角标通过纯函数解析“隐藏 / 普通 Live / 静态 Live”三态；导出失败策略也抽成纯函数，使关键业务分支可以用 JVM 单元测试覆盖。

**Tech Stack:** Kotlin、Android View/XML、StateFlow、Paging 3、JUnit 4、Gradle Android Library。

## Global Constraints

- 设计规范：`docs/superpowers/specs/2026-07-06-live-photo-static-grid-state-design.md`。
- 只做增量能力：现有普通 Live icon、检测、播放、默认保留 Live、普通图片、GIF 和视频行为不变。
- 静态 Live icon 使用现有 16dp 白色图形加白色斜杠，整体 alpha 固定为 `1f`。
- 导出偏好仅存在于当前 `PhotoChoiceViewModel` 会话，不落盘、不进入 Motion Photo 索引。
- 未勾选或取消勾选不得清除媒体的静态导出偏好。
- 强制静态 Live 压缩失败时终止整批交付；普通静态图片压缩失败仍回退原 URI。
- 所有 Gradle 命令从仓库根目录使用 Windows wrapper `./gradlew.bat`。

---

## File Structure

**Create:**

- `photo-choice/src/main/java/com/google/photochoice/ui/grid/LivePhotoGridBadgeState.kt` — 纯函数解析列表 Live 角标三态。
- `photo-choice/src/main/java/com/google/photochoice/util/ExportFailurePolicy.kt` — 纯函数决定压缩失败时回退或终止整批。
- `photo-choice/src/main/res/drawable/ic_live_photo_off.xml` — 现有 Live 图标加白色斜杠。
- `photo-choice/src/test/java/com/google/photochoice/viewmodel/LivePhotoExportPolicyTest.kt` — 会话导出偏好测试。
- `photo-choice/src/test/java/com/google/photochoice/ui/grid/LivePhotoGridBadgeStateTest.kt` — 列表角标状态组合测试。
- `photo-choice/src/test/java/com/google/photochoice/util/ExportFailurePolicyTest.kt` — 严格静态失败策略测试。

**Modify:**

- `photo-choice/src/main/java/com/google/photochoice/viewmodel/LivePhotoExportPolicy.kt` — 用 `StateFlow<Set<Long>>` 发布静态导出 ID。
- `photo-choice/src/main/java/com/google/photochoice/ui/preview/PreviewActivity.kt` — 监听新的状态流。
- `photo-choice/src/main/java/com/google/photochoice/ui/grid/MediaGridAdapter.kt` — 消费导出状态并切换角标 drawable。
- `photo-choice/src/main/java/com/google/photochoice/ui/grid/MediaGridFragment.kt` — 接线策略查询并按变化 ID 刷新 item。
- `photo-choice/src/main/java/com/google/photochoice/ui/PhotoChoiceActivity.kt` — 标记强制静态项，原子化处理失败和临时文件清理。
- `photo-choice/src/main/res/values/strings.xml` — 英文无障碍与失败文案。
- `photo-choice/src/main/res/values-zh-rCN/strings.xml` — 中文无障碍与失败文案。
- `README.md`、`README.zh-CN.md` — 说明列表静态状态与严格失败语义。

## Spec Coverage Map

- 业务默认、静态状态和会话边界：Tasks 1–2。
- 16dp 完整亮度斜杠图标与无障碍文案：Task 2。
- 会话状态模型、预览同步和未勾选状态保留：Tasks 1–2。
- Paging 列表按媒体 ID 增量刷新、异步 Live 检测兼容和性能约束：Task 2。
- 强制静态成功、失败终止、临时文件清理和普通图片回退：Task 3。
- 单元测试、编译回归、Contract/callback 共用出口和设备 UI 验收：Tasks 1–4。
- 面向接入方的行为说明：Task 4。

---

### Task 1: 将 Live 导出偏好改为可观察的静态 ID 集合

**Files:**

- Create: `photo-choice/src/test/java/com/google/photochoice/viewmodel/LivePhotoExportPolicyTest.kt`
- Modify: `photo-choice/src/main/java/com/google/photochoice/viewmodel/LivePhotoExportPolicy.kt`
- Modify: `photo-choice/src/main/java/com/google/photochoice/ui/preview/PreviewActivity.kt:640-648`

**Interfaces:**

- Produces: `val staticExportIds: StateFlow<Set<Long>>`
- Produces: `fun isKeepLive(mediaId: Long): Boolean`
- Produces: `fun setKeepLive(mediaId: Long, keep: Boolean)`
- Produces: `fun toggleKeepLive(mediaId: Long)`

- [ ] **Step 1: 写出导出偏好失败测试**

创建 `LivePhotoExportPolicyTest.kt`：

```kotlin
package com.google.photochoice.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LivePhotoExportPolicyTest {

    @Test
    fun `默认保留 Live 且静态集合为空`() {
        val policy = LivePhotoExportPolicy()

        assertTrue(policy.isKeepLive(7L))
        assertTrue(policy.staticExportIds.value.isEmpty())
    }

    @Test
    fun `关闭 Live 后媒体 ID 进入静态集合`() {
        val policy = LivePhotoExportPolicy()

        policy.setKeepLive(7L, keep = false)

        assertFalse(policy.isKeepLive(7L))
        assertTrue(7L in policy.staticExportIds.value)
    }

    @Test
    fun `切回 Live 后从静态集合移除`() {
        val policy = LivePhotoExportPolicy()
        policy.setKeepLive(7L, keep = false)

        policy.setKeepLive(7L, keep = true)

        assertTrue(policy.isKeepLive(7L))
        assertFalse(7L in policy.staticExportIds.value)
    }

    @Test
    fun `重复设置同一状态不发布新集合`() {
        val policy = LivePhotoExportPolicy()
        policy.setKeepLive(7L, keep = false)
        val before = policy.staticExportIds.value

        policy.setKeepLive(7L, keep = false)

        assertSame(before, policy.staticExportIds.value)
    }
}
```

- [ ] **Step 2: 运行测试并确认因接口不存在而失败**

Run:

```powershell
./gradlew.bat :photo-choice:testDebugUnitTest --tests "com.google.photochoice.viewmodel.LivePhotoExportPolicyTest"
```

Expected: `FAIL`，错误包含 `Unresolved reference 'staticExportIds'`。

- [ ] **Step 3: 用不可变 Set 实现最小状态源**

将 `LivePhotoExportPolicy.kt` 替换为：

```kotlin
package com.google.photochoice.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Live Photo 在开启压缩时的会话级导出偏好。 */
class LivePhotoExportPolicy {

    private val _staticExportIds = MutableStateFlow<Set<Long>>(emptySet())
    val staticExportIds: StateFlow<Set<Long>> = _staticExportIds.asStateFlow()

    fun isKeepLive(mediaId: Long): Boolean = mediaId !in _staticExportIds.value

    fun setKeepLive(mediaId: Long, keep: Boolean) {
        val current = _staticExportIds.value
        val next = if (keep) current - mediaId else current + mediaId
        if (next == current) return
        _staticExportIds.value = next
    }

    fun toggleKeepLive(mediaId: Long) {
        setKeepLive(mediaId, !isKeepLive(mediaId))
    }
}
```

在 `PreviewActivity.observeState()` 中把旧的 `revision` collector 改成：

```kotlin
lifecycleScope.launch {
    viewModel.livePhotoExportPolicy.staticExportIds.collect {
        updateLiveExportToggle(binding.viewPager.currentItem)
    }
}
```

- [ ] **Step 4: 运行策略测试和 Kotlin 编译**

Run:

```powershell
./gradlew.bat :photo-choice:testDebugUnitTest --tests "com.google.photochoice.viewmodel.LivePhotoExportPolicyTest"
./gradlew.bat :photo-choice:compileDebugKotlin
```

Expected: 两条命令均为 `BUILD SUCCESSFUL`。

- [ ] **Step 5: 提交状态源变更**

```powershell
git add photo-choice/src/main/java/com/google/photochoice/viewmodel/LivePhotoExportPolicy.kt photo-choice/src/main/java/com/google/photochoice/ui/preview/PreviewActivity.kt photo-choice/src/test/java/com/google/photochoice/viewmodel/LivePhotoExportPolicyTest.kt
git commit -m "feat: expose live photo static export state"
```

---

### Task 2: 在相册列表增加 Live 静态斜杠状态

**Files:**

- Create: `photo-choice/src/main/java/com/google/photochoice/ui/grid/LivePhotoGridBadgeState.kt`
- Create: `photo-choice/src/test/java/com/google/photochoice/ui/grid/LivePhotoGridBadgeStateTest.kt`
- Create: `photo-choice/src/main/res/drawable/ic_live_photo_off.xml`
- Modify: `photo-choice/src/main/java/com/google/photochoice/ui/grid/MediaGridAdapter.kt`
- Modify: `photo-choice/src/main/java/com/google/photochoice/ui/grid/MediaGridFragment.kt:272-323,456-463`
- Modify: `photo-choice/src/main/res/values/strings.xml:22-30`
- Modify: `photo-choice/src/main/res/values-zh-rCN/strings.xml:21-29`

**Interfaces:**

- Consumes: `LivePhotoExportPolicy.staticExportIds` and `isKeepLive(mediaId)` from Task 1。
- Produces: `enum class LivePhotoGridBadgeState { HIDDEN, LIVE, STATIC_EXPORT }`
- Produces: `fun resolveLivePhotoGridBadgeState(isLivePhoto: Boolean, compressionEnabled: Boolean, keepLive: Boolean): LivePhotoGridBadgeState`
- Produces: `MediaGridAdapter.notifyExportModeChanged(id: Long)`。

- [ ] **Step 1: 写出角标三态失败测试**

创建 `LivePhotoGridBadgeStateTest.kt`：

```kotlin
package com.google.photochoice.ui.grid

import org.junit.Assert.assertEquals
import org.junit.Test

class LivePhotoGridBadgeStateTest {

    @Test
    fun `非 Live 图片隐藏角标`() {
        assertEquals(
            LivePhotoGridBadgeState.HIDDEN,
            resolveLivePhotoGridBadgeState(
                isLivePhoto = false,
                compressionEnabled = true,
                keepLive = false
            )
        )
    }

    @Test
    fun `未开启压缩时保持普通 Live 角标`() {
        assertEquals(
            LivePhotoGridBadgeState.LIVE,
            resolveLivePhotoGridBadgeState(
                isLivePhoto = true,
                compressionEnabled = false,
                keepLive = false
            )
        )
    }

    @Test
    fun `开启压缩且保留 Live 时显示普通角标`() {
        assertEquals(
            LivePhotoGridBadgeState.LIVE,
            resolveLivePhotoGridBadgeState(
                isLivePhoto = true,
                compressionEnabled = true,
                keepLive = true
            )
        )
    }

    @Test
    fun `开启压缩且导出静态时显示斜杠角标`() {
        assertEquals(
            LivePhotoGridBadgeState.STATIC_EXPORT,
            resolveLivePhotoGridBadgeState(
                isLivePhoto = true,
                compressionEnabled = true,
                keepLive = false
            )
        )
    }
}
```

- [ ] **Step 2: 运行测试并确认因三态解析器不存在而失败**

Run:

```powershell
./gradlew.bat :photo-choice:testDebugUnitTest --tests "com.google.photochoice.ui.grid.LivePhotoGridBadgeStateTest"
```

Expected: `FAIL`，错误包含 `Unresolved reference 'LivePhotoGridBadgeState'`。

- [ ] **Step 3: 实现纯角标状态解析器**

创建 `LivePhotoGridBadgeState.kt`：

```kotlin
package com.google.photochoice.ui.grid

internal enum class LivePhotoGridBadgeState {
    HIDDEN,
    LIVE,
    STATIC_EXPORT
}

internal fun resolveLivePhotoGridBadgeState(
    isLivePhoto: Boolean,
    compressionEnabled: Boolean,
    keepLive: Boolean
): LivePhotoGridBadgeState = when {
    !isLivePhoto -> LivePhotoGridBadgeState.HIDDEN
    compressionEnabled && !keepLive -> LivePhotoGridBadgeState.STATIC_EXPORT
    else -> LivePhotoGridBadgeState.LIVE
}
```

- [ ] **Step 4: 新增完全不透明的斜杠矢量和文案**

创建 `ic_live_photo_off.xml`，复用 `ic_live_photo.xml` 的全部 path，并在最后增加斜杠 path：

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="16dp"
    android:height="16dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M12,12m-2.2,0a2.2,2.2 0,1 1,4.4 0a2.2,2.2 0,1 1,-4.4 0" />
    <path
        android:fillColor="@android:color/transparent"
        android:strokeColor="#FFFFFF"
        android:strokeWidth="1.5"
        android:pathData="M12,12m-5,0a5,5 0,1 1,10 0a5,5 0,1 1,-10 0" />
    <path android:fillColor="@android:color/transparent" android:strokeColor="#FFFFFF" android:strokeWidth="1.5" android:strokeLineCap="round" android:pathData="M12,2.5 L12,4.5" />
    <path android:fillColor="@android:color/transparent" android:strokeColor="#FFFFFF" android:strokeWidth="1.5" android:strokeLineCap="round" android:pathData="M12,19.5 L12,21.5" />
    <path android:fillColor="@android:color/transparent" android:strokeColor="#FFFFFF" android:strokeWidth="1.5" android:strokeLineCap="round" android:pathData="M2.5,12 L4.5,12" />
    <path android:fillColor="@android:color/transparent" android:strokeColor="#FFFFFF" android:strokeWidth="1.5" android:strokeLineCap="round" android:pathData="M19.5,12 L21.5,12" />
    <path android:fillColor="@android:color/transparent" android:strokeColor="#FFFFFF" android:strokeWidth="1.5" android:strokeLineCap="round" android:pathData="M4.93,4.93 L6.34,6.34" />
    <path android:fillColor="@android:color/transparent" android:strokeColor="#FFFFFF" android:strokeWidth="1.5" android:strokeLineCap="round" android:pathData="M17.66,17.66 L19.07,19.07" />
    <path android:fillColor="@android:color/transparent" android:strokeColor="#FFFFFF" android:strokeWidth="1.5" android:strokeLineCap="round" android:pathData="M4.93,19.07 L6.34,17.66" />
    <path android:fillColor="@android:color/transparent" android:strokeColor="#FFFFFF" android:strokeWidth="1.5" android:strokeLineCap="round" android:pathData="M17.66,6.34 L19.07,4.93" />
    <path
        android:fillColor="@android:color/transparent"
        android:strokeColor="#FFFFFF"
        android:strokeWidth="2.3"
        android:strokeLineCap="round"
        android:pathData="M3.5,20.5 L20.5,3.5" />
</vector>
```

在英文 `strings.xml` 增加：

```xml
<string name="photochoice_live_photo_static_export">Live Photo, static export</string>
```

在中文 `values-zh-rCN/strings.xml` 增加：

```xml
<string name="photochoice_live_photo_static_export">实况照片，静态导出</string>
```

- [ ] **Step 5: 让 Adapter 读取同一导出状态并支持定向 payload**

在 `MediaGridAdapter` 构造参数中加入：

```kotlin
private val compressionEnabled: Boolean = false,
private val isKeepLive: (Long) -> Boolean = { true },
```

把 `livePhotoBadge` 类型改为 `AppCompatImageView`，新增 payload 和通知入口：

```kotlin
fun notifyExportModeChanged(id: Long) {
    notifyItemChanged(id, PAYLOAD_LIVE_EXPORT)
}
```

在 payload bind 中增加：

```kotlin
if (payloads.contains(PAYLOAD_LIVE_EXPORT)) {
    holder.refreshLivePhotoExportState(item)
}
```

将 `bindLivePhotoIndicator()` 与 `refreshLivePhotoIndicator()` 的重复判定收口为：

```kotlin
private fun applyLivePhotoIndicator(mediaItem: MediaFile, animate: Boolean) {
    if (mediaItem.type != MediaFile.MediaType.IMAGE) {
        setLivePhotoBadgeState(LivePhotoGridBadgeState.HIDDEN, animate = false)
        return
    }
    val motionState = MotionPhotoDecision.resolve(
        isMotionFlag = mediaItem.isMotionPhoto,
        memoryResult = MotionPhotoDetector.memoryResult(mediaItem.id),
        indexResult = MotionPhotoIndexStore.query(mediaItem),
        heuristicGuess = MotionPhotoHeuristics.guess(mediaItem)
    )
    if (motionState == BadgeState.UNKNOWN) {
        onRequestMotionEnrich?.invoke(mediaItem)
    }
    val isLivePhoto = motionState == BadgeState.CONFIRMED_MOTION ||
        motionState == BadgeState.HEURISTIC_MOTION
    val badgeState = resolveLivePhotoGridBadgeState(
        isLivePhoto = isLivePhoto,
        compressionEnabled = compressionEnabled,
        keepLive = isKeepLive(mediaItem.id)
    )
    setLivePhotoBadgeState(badgeState, animate)
}

private fun setLivePhotoBadgeState(
    state: LivePhotoGridBadgeState,
    animate: Boolean
) {
    if (state == LivePhotoGridBadgeState.HIDDEN) {
        setBadgeVisible(visible = false, animate = animate)
        return
    }
    livePhotoBadge.setImageResource(
        if (state == LivePhotoGridBadgeState.STATIC_EXPORT) {
            R.drawable.ic_live_photo_off
        } else {
            R.drawable.ic_live_photo
        }
    )
    livePhotoBadge.contentDescription = itemView.context.getString(
        if (state == LivePhotoGridBadgeState.STATIC_EXPORT) {
            R.string.photochoice_live_photo_static_export
        } else {
            R.string.photochoice_live_photo
        }
    )
    livePhotoBadge.alpha = 1f
    setBadgeVisible(visible = true, animate = animate)
}

fun bindLivePhotoIndicator(mediaItem: MediaFile) {
    applyLivePhotoIndicator(mediaItem, animate = false)
}

fun refreshLivePhotoIndicator(mediaItem: MediaFile) {
    applyLivePhotoIndicator(mediaItem, animate = true)
}

fun refreshLivePhotoExportState(mediaItem: MediaFile) {
    applyLivePhotoIndicator(mediaItem, animate = false)
}
```

在 companion object 增加：

```kotlin
const val PAYLOAD_LIVE_EXPORT = "live_export"
```

保留现有 `setBadgeVisible()` 淡入/淡出逻辑，它只服务于 Live 检测结果出现或消失；`STATIC_EXPORT` 状态自身始终将 alpha 恢复为 `1f`。

- [ ] **Step 6: 在 Fragment 中按变化 ID 接线**

构造 `MediaGridAdapter` 时增加：

```kotlin
compressionEnabled = config.compressConfig.enabled,
isKeepLive = { mediaId -> viewModel.livePhotoExportPolicy.isKeepLive(mediaId) },
```

在 `startMediaObservation()` 中增加定向刷新 collector：

```kotlin
var previousStaticExportIds = viewModel.livePhotoExportPolicy.staticExportIds.value
viewLifecycleOwner.lifecycleScope.launch {
    viewModel.livePhotoExportPolicy.staticExportIds
        .drop(1)
        .collect { currentStaticExportIds ->
            val changedIds = previousStaticExportIds xor currentStaticExportIds
            changedIds.forEach(mediaAdapter::notifyExportModeChanged)
            previousStaticExportIds = currentStaticExportIds
        }
}
```

这里的 `xor` 使用 Kotlin Set 的对称差写法，需在同一文件加入私有扩展：

```kotlin
private infix fun <T> Set<T>.xor(other: Set<T>): Set<T> =
    (this - other) + (other - this)
```

- [ ] **Step 7: 运行纯状态测试、资源编译和模块编译**

Run:

```powershell
./gradlew.bat :photo-choice:testDebugUnitTest --tests "com.google.photochoice.ui.grid.LivePhotoGridBadgeStateTest"
./gradlew.bat :photo-choice:compileDebugKotlin
```

Expected: 两条命令均为 `BUILD SUCCESSFUL`；Android resource linking 不报告 vector 或 string 错误。

- [ ] **Step 8: 提交列表状态变更**

```powershell
git add photo-choice/src/main/java/com/google/photochoice/ui/grid/LivePhotoGridBadgeState.kt photo-choice/src/main/java/com/google/photochoice/ui/grid/MediaGridAdapter.kt photo-choice/src/main/java/com/google/photochoice/ui/grid/MediaGridFragment.kt photo-choice/src/main/res/drawable/ic_live_photo_off.xml photo-choice/src/main/res/values/strings.xml photo-choice/src/main/res/values-zh-rCN/strings.xml photo-choice/src/test/java/com/google/photochoice/ui/grid/LivePhotoGridBadgeStateTest.kt
git commit -m "feat: show static export state for live photos"
```

---

### Task 3: 强制静态 Live 失败时原子终止交付

**Files:**

- Create: `photo-choice/src/main/java/com/google/photochoice/util/ExportFailurePolicy.kt`
- Create: `photo-choice/src/test/java/com/google/photochoice/util/ExportFailurePolicyTest.kt`
- Modify: `photo-choice/src/main/java/com/google/photochoice/ui/PhotoChoiceActivity.kt:325-415`
- Modify: `photo-choice/src/main/res/values/strings.xml`
- Modify: `photo-choice/src/main/res/values-zh-rCN/strings.xml`

**Interfaces:**

- Consumes: `PhotoChoiceViewModel.isLivePhoto(media)`、`shouldCompressOnExport(media)` 与 `LivePhotoExportPolicy.isKeepLive(id)`。
- Produces: `enum class ExportFailureAction { FALLBACK_TO_ORIGINAL, ABORT_BATCH }`
- Produces: `fun compressionFailureAction(mustExportStatic: Boolean): ExportFailureAction`
- Produces: `ExportUri.mustExportStatic: Boolean`。

- [ ] **Step 1: 写出压缩失败策略测试**

创建 `ExportFailurePolicyTest.kt`：

```kotlin
package com.google.photochoice.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ExportFailurePolicyTest {

    @Test
    fun `普通静态图片失败时回退原图`() {
        assertEquals(
            ExportFailureAction.FALLBACK_TO_ORIGINAL,
            compressionFailureAction(mustExportStatic = false)
        )
    }

    @Test
    fun `Live 强制静态失败时终止整批`() {
        assertEquals(
            ExportFailureAction.ABORT_BATCH,
            compressionFailureAction(mustExportStatic = true)
        )
    }
}
```

- [ ] **Step 2: 运行测试并确认因策略不存在而失败**

Run:

```powershell
./gradlew.bat :photo-choice:testDebugUnitTest --tests "com.google.photochoice.util.ExportFailurePolicyTest"
```

Expected: `FAIL`，错误包含 `Unresolved reference 'ExportFailureAction'`。

- [ ] **Step 3: 实现纯失败策略**

创建 `ExportFailurePolicy.kt`：

```kotlin
package com.google.photochoice.util

internal enum class ExportFailureAction {
    FALLBACK_TO_ORIGINAL,
    ABORT_BATCH
}

internal fun compressionFailureAction(
    mustExportStatic: Boolean
): ExportFailureAction = if (mustExportStatic) {
    ExportFailureAction.ABORT_BATCH
} else {
    ExportFailureAction.FALLBACK_TO_ORIGINAL
}
```

- [ ] **Step 4: 为导出任务增加强制静态标记**

将 `ExportUri` 改为：

```kotlin
private data class ExportUri(
    val uri: Uri,
    val shouldCompress: Boolean,
    val mustExportStatic: Boolean
)
```

在 `finishWithResult()` 映射选中媒体时使用：

```kotlin
val exportItems = selected.map { media ->
    val mustExportStatic = viewModel.config.compressConfig.enabled &&
        viewModel.isLivePhoto(media) &&
        !viewModel.livePhotoExportPolicy.isKeepLive(media.id)
    ExportUri(
        uri = media.uri.toUri(),
        shouldCompress = viewModel.shouldCompressOnExport(media),
        mustExportStatic = mustExportStatic
    )
}
```

裁剪结果不是 Live Photo，将其构造改为：

```kotlin
ExportUri(
    uri = croppedUri.toUri(),
    shouldCompress = viewModel.config.compressConfig.enabled,
    mustExportStatic = false
)
```

- [ ] **Step 5: 将批量压缩结果改成成功或严格失败两态**

在 `PhotoChoiceActivity` 内新增：

```kotlin
private sealed interface BuildCompressedResult {
    data class Success(val result: PhotoChoiceResult) : BuildCompressedResult
    data object StaticExportFailed : BuildCompressedResult
}
```

将 `buildCompressedResult()` 改为：

```kotlin
private fun buildCompressedResult(items: List<ExportUri>): BuildCompressedResult {
    val helper = CompressHelper(this)
    val cfg = viewModel.config.compressConfig
    val outUris = mutableListOf<Uri>()
    val outPaths = mutableListOf<String>()
    val generatedFiles = mutableListOf<java.io.File>()

    for (item in items) {
        if (!item.shouldCompress) {
            outUris.add(item.uri)
            outPaths.add(resolvePath(item.uri))
            continue
        }

        val file = helper.compress(item.uri, cfg)
        if (file != null) {
            generatedFiles.add(file)
            outUris.add(Uri.fromFile(file))
            outPaths.add(file.absolutePath)
            continue
        }

        when (compressionFailureAction(item.mustExportStatic)) {
            ExportFailureAction.FALLBACK_TO_ORIGINAL -> {
                outUris.add(item.uri)
                outPaths.add(resolvePath(item.uri))
            }
            ExportFailureAction.ABORT_BATCH -> {
                generatedFiles.forEach { generated ->
                    runCatching { generated.delete() }
                }
                return BuildCompressedResult.StaticExportFailed
            }
        }
    }

    return BuildCompressedResult.Success(
        PhotoChoiceResult(uris = outUris, paths = outPaths)
    )
}
```

为 `PhotoChoiceActivity` 增加 imports：

```kotlin
import android.widget.Toast
import com.google.photochoice.util.ExportFailureAction
import com.google.photochoice.util.compressionFailureAction
```

- [ ] **Step 6: 失败时留在选择器并允许重试**

将 `deliverProcessedResult()` 中的协程完成段改为：

```kotlin
lifecycleScope.launch {
    when (val buildResult = withContext(Dispatchers.IO) {
        buildCompressedResult(items)
    }) {
        is BuildCompressedResult.Success -> deliverResult(buildResult.result)
        BuildCompressedResult.StaticExportFailed -> {
            resultDeliveryInFlight = false
            Toast.makeText(
                this@PhotoChoiceActivity,
                R.string.photochoice_static_export_failed,
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
```

英文 `strings.xml` 增加：

```xml
<string name="photochoice_static_export_failed">Static image export failed. Try again.</string>
```

中文 `values-zh-rCN/strings.xml` 增加：

```xml
<string name="photochoice_static_export_failed">静态图片导出失败，请重试</string>
```

- [ ] **Step 7: 运行失败策略测试、全量 JVM 测试和编译**

Run:

```powershell
./gradlew.bat :photo-choice:testDebugUnitTest --tests "com.google.photochoice.util.ExportFailurePolicyTest"
./gradlew.bat :photo-choice:testDebugUnitTest
./gradlew.bat :photo-choice:compileDebugKotlin
```

Expected: 三条命令均为 `BUILD SUCCESSFUL`；已有 GIF、压缩配置和 Motion Photo 测试保持通过。

- [ ] **Step 8: 提交严格导出变更**

```powershell
git add photo-choice/src/main/java/com/google/photochoice/util/ExportFailurePolicy.kt photo-choice/src/main/java/com/google/photochoice/ui/PhotoChoiceActivity.kt photo-choice/src/main/res/values/strings.xml photo-choice/src/main/res/values-zh-rCN/strings.xml photo-choice/src/test/java/com/google/photochoice/util/ExportFailurePolicyTest.kt
git commit -m "fix: prevent live fallback for static export"
```

---

### Task 4: 文档与端到端回归验收

**Files:**

- Modify: `README.md:45-67`
- Modify: `README.zh-CN.md` 对应 Motion Photo / 实况照片章节

**Interfaces:**

- Consumes: Tasks 1–3 的最终行为。
- Produces: 面向接入方的准确行为说明和可复现验收记录。

- [ ] **Step 1: 更新英文 README**

在 `Motion Photo / Live Photo` 的 Grid list 小节增加：

```markdown
- With compression enabled, a slashed LIVE icon means that media is currently set to export as a static JPEG. The preference is retained for the picker session even when the item is not selected.
```

在 Compression & export 小节增加：

```markdown
- If a Live Photo explicitly set to **Export static** cannot produce a JPEG, the picker keeps the current selection and reports an error instead of returning the original Live Photo.
```

- [ ] **Step 2: 更新中文 README**

在中文对应章节增加：

```markdown
- 开启压缩后，带斜杠的实况图标表示该媒体当前将导出为静态 JPEG；即使尚未勾选，该偏好也会在本次选择器会话内保留。
- 明确选择“导出静态”的实况照片若无法生成 JPEG，选择器会保留当前选择并提示失败，不会回传原始实况照片。
```

- [ ] **Step 3: 运行最终自动化验证**

Run:

```powershell
./gradlew.bat :photo-choice:testDebugUnitTest
./gradlew.bat :photo-choice:compileDebugKotlin
./gradlew.bat :sample:compileDebugKotlin
```

Expected: 三条命令均为 `BUILD SUCCESSFUL`，无新增 warning 被当作 error。

- [ ] **Step 4: 在设备或模拟器完成 UI 验收**

按顺序执行并记录结果：

1. 开启压缩进入相册，确认 Live Photo 初始显示现有普通 Live icon。
2. 进入预览切换为“静态”，不勾选该项直接返回列表，确认图标变为完整亮度的 Live + 白色斜杠。
3. 滚动使 item 离屏后再滚回，确认 ViewHolder 复用后仍显示斜杠。
4. 再次进入预览，确认仍为“静态”；切回 Live 后返回，确认普通图标恢复。
5. 取消勾选再重新勾选，确认静态偏好不被清除。
6. 使用需要异步识别的 Live Photo，确认识别完成后直接显示与当前偏好一致的角标。
7. 在浅色、深色主题以及亮、暗、高细节缩略图上检查 16dp 斜杠清晰度与对齐。
8. 让 `CompressHelper.compress()` 对强制静态 Live 返回 `null`，确认停留在相册页、显示失败提示、宿主不收到成功结果；恢复后重试可成功。
9. 选择普通图片、GIF、视频和“保留 Live”，确认原有回调 URI 与行为不变。

- [ ] **Step 5: 检查最终差异只包含本功能**

Run:

```powershell
git status --short
git diff --check
git diff --stat HEAD~3..HEAD
```

Expected: 没有行尾空格；差异只覆盖本计划列出的代码、资源、测试和文档文件；`.superpowers/` 不得进入提交。

- [ ] **Step 6: 提交文档更新**

```powershell
git add README.md README.zh-CN.md
git commit -m "docs: describe live photo static export state"
```
