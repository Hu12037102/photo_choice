# 相册列表 Live 角标"秒显"优化 — 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把实况图角标判定从"滚动时现算 + 只存内存"改为"绑定前已知 + 持久化 + 后台预建 + 启发式兜底"，让列表快滑时 Live 角标与缩略图同帧显示，且不牺牲系统 I/O / 帧率 / 耗电。

**Architecture:** 五级判定级联（L0 系统 DB 标记 → L1 内存 LruCache → L2 持久化索引 → L3 文件名启发式 → L4 异步 XMP 嗅探）。新增自建轻量文件索引（非 Room）持久化判定结果，进相册后台低优先级全量预建写入索引；bind 时纯内存查表 O(1)。后台预建做完整性能治理：滑动暂停、首屏延迟、onStop 停、低并发、后台线程优先级、批量防抖落盘。

**Tech Stack:** Kotlin, Coroutines(1.11.0), AndroidX Paging(3.5), Glide(5.0), RecyclerView。测试用 JUnit4 + kotlinx-coroutines-test（纯 JVM 单测，无 Robolectric）。

**设计文档：** `docs/superpowers/specs/2026-07-04-live-badge-index-design.md`

> **⚠️ Git 策略（遵循用户全局规则，最高优先级）：** 增删改查类代码改动**不自动执行 `git add` / `git commit`**——提交由用户本人处理。下文每个 Task 末尾的 "Commit" 步骤仅作为**逻辑检查点**（标记一个可独立提交的完整单元），执行者**不要**真正运行其中的 git 命令；到达检查点时暂停，由用户决定是否提交。

---

## 文件结构

**新增：**
- `photo-choice/src/main/java/com/google/photochoice/data/motion/MotionPhotoHeuristics.kt` — 纯函数：文件名启发式（L3）
- `photo-choice/src/main/java/com/google/photochoice/data/motion/MotionPhotoIndexStore.kt` — 持久化索引（L2）：含 `IndexRecord`、`IndexResult`、`IndexCodec`、`MotionPhotoIndexStore`
- `photo-choice/src/main/java/com/google/photochoice/data/motion/MotionPhotoDecision.kt` — 纯函数级联（`BadgeState` + `resolve`）
- `photo-choice/src/main/java/com/google/photochoice/data/motion/AlbumMotionPrebuilder.kt` — 后台全量预建 + 性能治理
- 测试：
  - `photo-choice/src/test/java/com/google/photochoice/data/motion/MotionPhotoHeuristicsTest.kt`
  - `photo-choice/src/test/java/com/google/photochoice/data/motion/IndexCodecTest.kt`
  - `photo-choice/src/test/java/com/google/photochoice/data/motion/IndexMapMergeTest.kt`
  - `photo-choice/src/test/java/com/google/photochoice/data/motion/MotionPhotoDecisionTest.kt`

**修改：**
- `gradle/libs.versions.toml` — 加 junit / coroutines-test 版本与库
- `photo-choice/build.gradle.kts` — 加 testImplementation
- `photo-choice/src/main/java/com/google/photochoice/data/model/MediaFile.kt` — 加 `displayName`
- `photo-choice/src/main/java/com/google/photochoice/data/MediaRepository.kt` — projection + 填充 displayName
- `photo-choice/src/main/java/com/google/photochoice/data/motion/MotionPhotoDetector.kt` — 嗅探结果双写 IndexStore
- `photo-choice/src/main/java/com/google/photochoice/data/motion/MotionPhotoListEnricher.kt` — 回调兼顾正/负结果（用于淡入/淡出校正）
- `photo-choice/src/main/java/com/google/photochoice/ui/grid/MediaGridAdapter.kt` — 消费 `BadgeState`，淡入/淡出
- `photo-choice/src/main/java/com/google/photochoice/ui/grid/MediaGridFragment.kt` — 接线：ensureLoaded、预建生命周期、滚动暂停

---

## Task 1: 测试基础设施

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `photo-choice/build.gradle.kts`

- [ ] **Step 1: 在 libs.versions.toml 增加测试库版本**

在 `[versions]` 末尾（`media3 = "1.10.1"` 之后）追加：

```toml
junit = "4.13.2"
coroutinesTest = "1.11.0"
```

在 `[libraries]` 末尾（`media3-ui = ...` 之后）追加：

```toml
junit = { group = "junit", name = "junit", version.ref = "junit" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutinesTest" }
```

- [ ] **Step 2: 在 build.gradle.kts 增加 testImplementation**

在 `photo-choice/build.gradle.kts` 的 `dependencies { ... }` 块末尾（`media3-ui` 那行之后、`}` 之前）追加：

```kotlin
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
```

- [ ] **Step 3: 验证 Gradle 同步与已有源码仍能编译**

Run: `./gradlew :photo-choice:compileDebugKotlin`
Expected: BUILD SUCCESSFUL（仅加依赖，未改代码）

- [ ] **Step 4: 建立空测试目录并验证 test 任务可运行**

创建占位测试 `photo-choice/src/test/java/com/google/photochoice/SanityTest.kt`：

```kotlin
package com.google.photochoice

import org.junit.Assert.assertTrue
import org.junit.Test

class SanityTest {
    @Test
    fun sanity() {
        assertTrue(true)
    }
}
```

Run: `./gradlew :photo-choice:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, 1 test passed

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml photo-choice/build.gradle.kts photo-choice/src/test/java/com/google/photochoice/SanityTest.kt
git commit -m "chore: 添加单元测试基础设施(junit + coroutines-test)"
```

---

## Task 2: MediaFile 增加 displayName + Repository 填充

**Files:**
- Modify: `photo-choice/src/main/java/com/google/photochoice/data/model/MediaFile.kt:11-16`
- Modify: `photo-choice/src/main/java/com/google/photochoice/data/MediaRepository.kt`

- [ ] **Step 1: MediaFile 增加 displayName 字段**

在 `MediaFile.kt` 的 `bucketName` 之后、`isMotionPhoto` 之前插入字段（保持 data class）：

```kotlin
    val bucketId: String,
    val bucketName: String,
    /** 文件名（含扩展名），用于零 I/O 的实况图启发式判定。 */
    val displayName: String = "",
    /** Motion Photo / 实况图（仍为 IMAGE 类型，含内嵌短视频）。 */
    val isMotionPhoto: Boolean = false
```

- [ ] **Step 2: Repository PROJECTION 增加 DISPLAY_NAME 列**

在 `MediaRepository.kt` 的 `companion object` 中 `PROJECTION` 数组末尾（`BUCKET_DISPLAY_NAME` 之后）加一列：

```kotlin
        private val PROJECTION = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.WIDTH,
            MediaStore.Files.FileColumns.HEIGHT,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DURATION,
            MediaStore.Files.FileColumns.BUCKET_ID,
            MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
            MediaStore.Files.FileColumns.DISPLAY_NAME
        )
```

- [ ] **Step 3: ColumnIndex 增加列索引并填充**

在 `MediaRepository.kt` 的 `ColumnIndex` 类中，`bucketNameCol` 之后增加列索引：

```kotlin
        val bucketNameCol =
            cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
        val displayNameCol =
            cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
```

在 `toMediaFile` 的 `MediaFile(...)` 构造里，`bucketName` 之后补 `displayName`：

```kotlin
                bucketId = cursor.getString(bucketIdCol) ?: "",
                bucketName = cursor.getString(bucketNameCol) ?: "",
                displayName = cursor.getString(displayNameCol) ?: ""
            )
```

- [ ] **Step 4: 编译验证**

Run: `./gradlew :photo-choice:compileDebugKotlin`
Expected: BUILD SUCCESSFUL（`displayName` 有默认值，其它 MediaFile 构造点不受影响）

- [ ] **Step 5: Commit**

```bash
git add photo-choice/src/main/java/com/google/photochoice/data/model/MediaFile.kt photo-choice/src/main/java/com/google/photochoice/data/MediaRepository.kt
git commit -m "feat: MediaFile 增加 displayName 并在 Repository 填充"
```

---

## Task 3: MotionPhotoHeuristics（L3 文件名启发式，TDD）

**Files:**
- Create: `photo-choice/src/main/java/com/google/photochoice/data/motion/MotionPhotoHeuristics.kt`
- Test: `photo-choice/src/test/java/com/google/photochoice/data/motion/MotionPhotoHeuristicsTest.kt`

- [ ] **Step 1: 写失败测试**

创建 `MotionPhotoHeuristicsTest.kt`：

```kotlin
package com.google.photochoice.data.motion

import com.google.photochoice.data.model.MediaFile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionPhotoHeuristicsTest {

    private fun image(name: String) = MediaFile(
        id = 1L, uri = "content://x/1", mimeType = "image/jpeg",
        type = MediaFile.MediaType.IMAGE, dateAdded = 0L, width = 0, height = 0,
        size = 0L, bucketId = "b", bucketName = "B", displayName = name
    )

    private fun video(name: String) = image(name).copy(type = MediaFile.MediaType.VIDEO)

    @Test
    fun `MVIMG 前缀命中`() {
        assertTrue(MotionPhotoHeuristics.guess(image("MVIMG_20240101_120000.jpg")))
    }

    @Test
    fun `小写 mvimg 也命中(大小写不敏感)`() {
        assertTrue(MotionPhotoHeuristics.guess(image("mvimg_20240101.jpg")))
    }

    @Test
    fun `MV 前缀命中`() {
        assertTrue(MotionPhotoHeuristics.guess(image("MV_1234.jpg")))
    }

    @Test
    fun `包含 MOTIONPHOTO 命中`() {
        assertTrue(MotionPhotoHeuristics.guess(image("IMG_motionphoto_1.jpg")))
    }

    @Test
    fun `普通 IMG 不命中`() {
        assertFalse(MotionPhotoHeuristics.guess(image("IMG_20240101_120000.jpg")))
    }

    @Test
    fun `视频不命中`() {
        assertFalse(MotionPhotoHeuristics.guess(video("MVIMG_1.mp4")))
    }

    @Test
    fun `空文件名不命中`() {
        assertFalse(MotionPhotoHeuristics.guess(image("")))
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew :photo-choice:testDebugUnitTest --tests "*MotionPhotoHeuristicsTest*"`
Expected: FAIL，编译错误 "unresolved reference: MotionPhotoHeuristics"

- [ ] **Step 3: 写最小实现**

创建 `MotionPhotoHeuristics.kt`：

```kotlin
package com.google.photochoice.data.motion

import com.google.photochoice.data.model.MediaFile

/**
 * 文件名启发式：bind 时零 I/O，仅凭 displayName 特征快速猜"疑似实况图"。
 *
 * 设计原则：保守优先（宁漏不错）——命中即立即显示角标，最终以 L4 XMP 嗅探结果校正。
 * 误报会导致角标淡出回撤，故规则只覆盖强特征命名，降低回撤闪烁概率。
 */
object MotionPhotoHeuristics {

    /**
     * 判断是否"疑似实况图"。
     * @return true=疑似实况图（可立即显示角标，待异步校正）
     */
    fun guess(item: MediaFile): Boolean {
        if (item.type != MediaFile.MediaType.IMAGE) return false
        val name = item.displayName.uppercase()
        if (name.isEmpty()) return false
        return name.startsWith("MVIMG_") ||      // Google 相机 Motion Photo
            name.startsWith("MV_") ||             // 部分机型 Motion 前缀
            name.contains("MOTIONPHOTO") ||       // 通用命名
            name.contains("LIVEPHOTO")            // 部分相机 Live 命名
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `./gradlew :photo-choice:testDebugUnitTest --tests "*MotionPhotoHeuristicsTest*"`
Expected: PASS，7 tests passed

- [ ] **Step 5: Commit**

```bash
git add photo-choice/src/main/java/com/google/photochoice/data/motion/MotionPhotoHeuristics.kt photo-choice/src/test/java/com/google/photochoice/data/motion/MotionPhotoHeuristicsTest.kt
git commit -m "feat: 新增 MotionPhotoHeuristics 文件名启发式(L3)"
```

---

## Task 4: IndexRecord + IndexResult + IndexCodec（编解码，TDD）

**Files:**
- Create: `photo-choice/src/main/java/com/google/photochoice/data/motion/MotionPhotoIndexStore.kt`（本任务先建文件的编解码部分）
- Test: `photo-choice/src/test/java/com/google/photochoice/data/motion/IndexCodecTest.kt`

- [ ] **Step 1: 写失败测试**

创建 `IndexCodecTest.kt`：

```kotlin
package com.google.photochoice.data.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IndexCodecTest {

    @Test
    fun `编码为 CSV 行`() {
        val line = IndexCodec.encode(IndexRecord(id = 42L, isMotion = true, size = 1000L, dateAdded = 123L))
        assertEquals("42,1,1000,123", line)
    }

    @Test
    fun `阴性结果编码 flag 为 0`() {
        val line = IndexCodec.encode(IndexRecord(id = 7L, isMotion = false, size = 5L, dateAdded = 9L))
        assertEquals("7,0,5,9", line)
    }

    @Test
    fun `解码正常行`() {
        val r = IndexCodec.decode("42,1,1000,123")
        assertEquals(IndexRecord(42L, true, 1000L, 123L), r)
    }

    @Test
    fun `编码解码往返一致`() {
        val original = IndexRecord(999L, false, 88L, 77L)
        assertEquals(original, IndexCodec.decode(IndexCodec.encode(original)))
    }

    @Test
    fun `坏行(列数不足)解码返回 null`() {
        assertNull(IndexCodec.decode("42,1"))
    }

    @Test
    fun `坏行(非数字)解码返回 null`() {
        assertNull(IndexCodec.decode("abc,1,2,3"))
    }

    @Test
    fun `空行解码返回 null`() {
        assertNull(IndexCodec.decode(""))
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew :photo-choice:testDebugUnitTest --tests "*IndexCodecTest*"`
Expected: FAIL，"unresolved reference: IndexCodec"

- [ ] **Step 3: 写最小实现**

创建 `MotionPhotoIndexStore.kt`（本任务只放数据模型 + 编解码，Task 5 再补文件存储）：

```kotlin
package com.google.photochoice.data.motion

/** L2 查询结果：确认是 / 确认否 / 未知（未记录或校验失效）。 */
enum class IndexResult { MOTION, NOT_MOTION, UNKNOWN }

/**
 * 索引记录。size + dateAdded 用于 id 复用校验：
 * MediaStore._ID 在媒体删除后可能被新文件复用，比对不一致则视为失效。
 */
data class IndexRecord(
    val id: Long,
    val isMotion: Boolean,
    val size: Long,
    val dateAdded: Long
)

/** 单行 CSV 编解码。纯函数，便于单测。 */
object IndexCodec {

    /** 编码为 `id,flag,size,dateAdded` 一行。 */
    fun encode(record: IndexRecord): String {
        val flag = if (record.isMotion) 1 else 0
        return "${record.id},$flag,${record.size},${record.dateAdded}"
    }

    /** 解码一行；格式非法返回 null（坏行跳过，不影响整体加载）。 */
    fun decode(line: String): IndexRecord? {
        val parts = line.split(",")
        if (parts.size != 4) return null
        val id = parts[0].toLongOrNull() ?: return null
        val flag = parts[1].toIntOrNull() ?: return null
        val size = parts[2].toLongOrNull() ?: return null
        val dateAdded = parts[3].toLongOrNull() ?: return null
        return IndexRecord(id, flag == 1, size, dateAdded)
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `./gradlew :photo-choice:testDebugUnitTest --tests "*IndexCodecTest*"`
Expected: PASS，7 tests passed

- [ ] **Step 5: Commit**

```bash
git add photo-choice/src/main/java/com/google/photochoice/data/motion/MotionPhotoIndexStore.kt photo-choice/src/test/java/com/google/photochoice/data/motion/IndexCodecTest.kt
git commit -m "feat: 新增索引记录模型与 CSV 编解码(IndexCodec)"
```

---

## Task 5: MotionPhotoIndexStore 文件存储（加载合并 + 防抖落盘 + compact）

**Files:**
- Modify: `photo-choice/src/main/java/com/google/photochoice/data/motion/MotionPhotoIndexStore.kt`
- Test: `photo-choice/src/test/java/com/google/photochoice/data/motion/IndexMapMergeTest.kt`

- [ ] **Step 1: 写失败测试（内存合并纯逻辑）**

创建 `IndexMapMergeTest.kt`——测试"多行合并成内存 Map，后写覆盖先写，坏行跳过"：

```kotlin
package com.google.photochoice.data.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IndexMapMergeTest {

    @Test
    fun `多行合并到 Map`() {
        val map = MotionPhotoIndexStore.mergeLines(
            listOf("1,1,100,10", "2,0,200,20")
        )
        assertEquals(IndexRecord(1L, true, 100L, 10L), map[1L])
        assertEquals(IndexRecord(2L, false, 200L, 20L), map[2L])
    }

    @Test
    fun `同 id 后写覆盖先写`() {
        val map = MotionPhotoIndexStore.mergeLines(
            listOf("1,0,100,10", "1,1,100,10")
        )
        assertEquals(true, map[1L]?.isMotion)
    }

    @Test
    fun `坏行被跳过不影响其它`() {
        val map = MotionPhotoIndexStore.mergeLines(
            listOf("1,1,100,10", "GARBAGE", "2,1,200,20")
        )
        assertEquals(2, map.size)
        assertNull(map[999L])
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew :photo-choice:testDebugUnitTest --tests "*IndexMapMergeTest*"`
Expected: FAIL，"unresolved reference: mergeLines"

- [ ] **Step 3: 实现 IndexStore 存储层**

在 `MotionPhotoIndexStore.kt` 的 `IndexCodec` 之后追加 `MotionPhotoIndexStore` 单例。注意 import：

```kotlin
import android.content.Context
import android.util.Log
import com.google.photochoice.data.model.MediaFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
```

实现：

```kotlin
/**
 * 实况图判定结果的持久化索引（L2）。
 *
 * - 存储：App 私有 filesDir/photochoice/motion_index.csv（append-only + 阈值 compact）
 * - 加载：冷启动异步读全量构建内存 Map（O(1) 查询）
 * - 落盘：内存先写(查询立即可见) + 单写协程 + 批量防抖(攒够/超时/onStop 时 flush)
 * - 防 id 复用：size + dateAdded 校验
 * - 容错：所有 I/O runCatching 包裹，任何异常降级为"未命中"，绝不 Crash
 *
 * 为什么自建文件而非 Room：本模块是 library，Room 会带来编译期依赖与宿主版本冲突；
 * 本场景数据结构极简，单表 append 文本足矣。
 */
object MotionPhotoIndexStore {

    private const val TAG = "MotionPhotoIndexStore"
    private const val DIR_NAME = "photochoice"
    private const val FILE_NAME = "motion_index.csv"

    /** 批量落盘阈值：攒够这么多条即 flush 一次。 */
    private const val FLUSH_BATCH = 64
    /** 触发 compact 的总写入行数阈值（含重复行）。 */
    private const val COMPACT_LINE_THRESHOLD = 20000

    private val map = ConcurrentHashMap<Long, IndexRecord>()
    @Volatile private var loaded = false
    @Volatile private var indexFile: File? = null

    /** 累计写入文件的行数（含重复），用于判断 compact 时机。 */
    private val writtenLines = AtomicInteger(0)

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    // 待落盘队列：null 为 flush 信号（onStop 主动触发）
    private val writeChannel = Channel<IndexRecord?>(Channel.UNLIMITED)
    private val pendingBuffer = ArrayList<IndexRecord>(FLUSH_BATCH)

    init {
        scope.launch {
            for (item in writeChannel) {
                if (item == null) {
                    flushBuffer()
                } else {
                    pendingBuffer.add(item)
                    if (pendingBuffer.size >= FLUSH_BATCH) flushBuffer()
                }
            }
        }
    }

    /** 纯逻辑：把多行合并为内存 Map（后写覆盖先写，坏行跳过）。供单测。 */
    fun mergeLines(lines: List<String>): Map<Long, IndexRecord> {
        val result = HashMap<Long, IndexRecord>()
        for (line in lines) {
            val record = IndexCodec.decode(line) ?: continue
            result[record.id] = record
        }
        return result
    }

    /** 冷启动异步载入。幂等：仅首次真正读盘。 */
    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            loaded = true
        }
        scope.launch {
            runCatching {
                val file = resolveFile(context)
                indexFile = file
                if (file.exists()) {
                    val lines = file.readLines()
                    writtenLines.set(lines.size)
                    map.putAll(mergeLines(lines))
                    Log.d(TAG, "index loaded: ${map.size} entries from ${lines.size} lines")
                }
            }.onFailure { Log.w(TAG, "ensureLoaded failed", it) }
        }
    }

    /** O(1) 查询，含 id 复用校验。 */
    fun query(item: MediaFile): IndexResult {
        val record = map[item.id] ?: return IndexResult.UNKNOWN
        if (record.size != item.size || record.dateAdded != item.dateAdded) {
            // id 被复用为不同文件，旧记录失效
            return IndexResult.UNKNOWN
        }
        return if (record.isMotion) IndexResult.MOTION else IndexResult.NOT_MOTION
    }

    /** 写入一条：内存立即生效 + 入队异步落盘。 */
    fun put(item: MediaFile, isMotion: Boolean) {
        val record = IndexRecord(item.id, isMotion, item.size, item.dateAdded)
        map[item.id] = record
        writeChannel.trySend(record)
    }

    /** 生命周期 onStop 时主动 flush，避免丢失未落盘的判定。 */
    fun requestFlush() {
        writeChannel.trySend(null)
    }

    private fun flushBuffer() {
        if (pendingBuffer.isEmpty()) return
        val file = indexFile
        if (file == null) {
            pendingBuffer.clear()
            return
        }
        runCatching {
            file.parentFile?.mkdirs()
            val text = buildString {
                for (r in pendingBuffer) {
                    append(IndexCodec.encode(r)); append('\n')
                }
            }
            file.appendText(text)
            val total = writtenLines.addAndGet(pendingBuffer.size)
            if (total > COMPACT_LINE_THRESHOLD) compact(file)
        }.onFailure { Log.w(TAG, "flush failed", it) }
        pendingBuffer.clear()
    }

    /** 用内存 Map 全量重写文件，消除重复行，控制体积。 */
    private fun compact(file: File) {
        runCatching {
            val snapshot = map.values.toList()
            val tmp = File(file.parentFile, "$FILE_NAME.tmp")
            tmp.writeText(buildString {
                for (r in snapshot) { append(IndexCodec.encode(r)); append('\n') }
            })
            if (tmp.renameTo(file)) {
                writtenLines.set(snapshot.size)
                Log.d(TAG, "compacted to ${snapshot.size} entries")
            }
        }.onFailure { Log.w(TAG, "compact failed", it) }
    }

    private fun resolveFile(context: Context): File {
        val dir = File(context.filesDir, DIR_NAME)
        return File(dir, FILE_NAME)
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `./gradlew :photo-choice:testDebugUnitTest --tests "*IndexMapMergeTest*"`
Expected: PASS，3 tests passed

- [ ] **Step 5: 编译整模块**

Run: `./gradlew :photo-choice:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add photo-choice/src/main/java/com/google/photochoice/data/motion/MotionPhotoIndexStore.kt photo-choice/src/test/java/com/google/photochoice/data/motion/IndexMapMergeTest.kt
git commit -m "feat: IndexStore 文件存储(加载合并+防抖落盘+compact)"
```

---

## Task 6: MotionPhotoDecision 级联（纯函数，TDD）

**Files:**
- Create: `photo-choice/src/main/java/com/google/photochoice/data/motion/MotionPhotoDecision.kt`
- Test: `photo-choice/src/test/java/com/google/photochoice/data/motion/MotionPhotoDecisionTest.kt`

- [ ] **Step 1: 写失败测试**

创建 `MotionPhotoDecisionTest.kt`：

```kotlin
package com.google.photochoice.data.motion

import org.junit.Assert.assertEquals
import org.junit.Test

class MotionPhotoDecisionTest {

    @Test
    fun `L0 系统标记为真 直接确认`() {
        val s = MotionPhotoDecision.resolve(
            isMotionFlag = true, memoryResult = null,
            indexResult = IndexResult.UNKNOWN, heuristicGuess = false
        )
        assertEquals(BadgeState.CONFIRMED_MOTION, s)
    }

    @Test
    fun `L1 内存命中为真 确认`() {
        val s = MotionPhotoDecision.resolve(
            isMotionFlag = false, memoryResult = true,
            indexResult = IndexResult.UNKNOWN, heuristicGuess = true
        )
        assertEquals(BadgeState.CONFIRMED_MOTION, s)
    }

    @Test
    fun `L1 内存命中为假 确认否(优先级高于启发式)`() {
        val s = MotionPhotoDecision.resolve(
            isMotionFlag = false, memoryResult = false,
            indexResult = IndexResult.UNKNOWN, heuristicGuess = true
        )
        assertEquals(BadgeState.CONFIRMED_NOT, s)
    }

    @Test
    fun `L2 索引命中为真 确认`() {
        val s = MotionPhotoDecision.resolve(
            isMotionFlag = false, memoryResult = null,
            indexResult = IndexResult.MOTION, heuristicGuess = false
        )
        assertEquals(BadgeState.CONFIRMED_MOTION, s)
    }

    @Test
    fun `L2 索引命中为假 确认否`() {
        val s = MotionPhotoDecision.resolve(
            isMotionFlag = false, memoryResult = null,
            indexResult = IndexResult.NOT_MOTION, heuristicGuess = true
        )
        assertEquals(BadgeState.CONFIRMED_NOT, s)
    }

    @Test
    fun `L3 启发式命中(前面均未知) 疑似`() {
        val s = MotionPhotoDecision.resolve(
            isMotionFlag = false, memoryResult = null,
            indexResult = IndexResult.UNKNOWN, heuristicGuess = true
        )
        assertEquals(BadgeState.HEURISTIC_MOTION, s)
    }

    @Test
    fun `L4 全未知且启发式不命中 未知`() {
        val s = MotionPhotoDecision.resolve(
            isMotionFlag = false, memoryResult = null,
            indexResult = IndexResult.UNKNOWN, heuristicGuess = false
        )
        assertEquals(BadgeState.UNKNOWN, s)
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew :photo-choice:testDebugUnitTest --tests "*MotionPhotoDecisionTest*"`
Expected: FAIL，"unresolved reference: MotionPhotoDecision"

- [ ] **Step 3: 写最小实现**

创建 `MotionPhotoDecision.kt`：

```kotlin
package com.google.photochoice.data.motion

/**
 * bind 时角标状态。
 * - CONFIRMED_*：确定态，同帧显示/隐藏，无动画
 * - HEURISTIC_MOTION：启发式疑似，立即显示但待 L4 校正
 * - UNKNOWN：待异步嗅探，先隐藏并触发嗅探
 */
enum class BadgeState {
    CONFIRMED_MOTION,
    CONFIRMED_NOT,
    HEURISTIC_MOTION,
    UNKNOWN
}

/**
 * 五级判定级联（纯函数）：L0 系统标记 > L1 内存 > L2 索引 > L3 启发式 > L4 未知。
 * 命中即返回，成本从低到高。调用方(Adapter)从各单例取值后传入。
 */
object MotionPhotoDecision {

    fun resolve(
        isMotionFlag: Boolean,        // L0: MediaFile.isMotionPhoto
        memoryResult: Boolean?,       // L1: LruCache（null=未缓存）
        indexResult: IndexResult,     // L2: 持久化索引
        heuristicGuess: Boolean       // L3: 文件名启发式
    ): BadgeState {
        // L0
        if (isMotionFlag) return BadgeState.CONFIRMED_MOTION
        // L1
        if (memoryResult != null) {
            return if (memoryResult) BadgeState.CONFIRMED_MOTION else BadgeState.CONFIRMED_NOT
        }
        // L2
        when (indexResult) {
            IndexResult.MOTION -> return BadgeState.CONFIRMED_MOTION
            IndexResult.NOT_MOTION -> return BadgeState.CONFIRMED_NOT
            IndexResult.UNKNOWN -> Unit
        }
        // L3
        if (heuristicGuess) return BadgeState.HEURISTIC_MOTION
        // L4
        return BadgeState.UNKNOWN
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `./gradlew :photo-choice:testDebugUnitTest --tests "*MotionPhotoDecisionTest*"`
Expected: PASS，7 tests passed

- [ ] **Step 5: Commit**

```bash
git add photo-choice/src/main/java/com/google/photochoice/data/motion/MotionPhotoDecision.kt photo-choice/src/test/java/com/google/photochoice/data/motion/MotionPhotoDecisionTest.kt
git commit -m "feat: 新增 MotionPhotoDecision 五级判定级联(纯函数)"
```

---

## Task 7: MotionPhotoDetector 嗅探结果双写 IndexStore

**Files:**
- Modify: `photo-choice/src/main/java/com/google/photochoice/data/motion/MotionPhotoDetector.kt`

**说明：** 所有把结果写进 `cache` 的地方，紧邻加一行写 IndexStore，使 L1(内存) 与 L2(持久化) 同步。`cache.put` 只有 id 无 size/dateAdded，而 IndexStore.put 需要 MediaFile；因此只在持有 `MediaFile` 的路径双写（`quickSniffBatch`、`detectSingle`）。纯 id 的 DB 批量查询（`queryFromMediaStore`/`warmAlbumFromMediaStore`）保持只写内存缓存——它们本就是 API34+ 机型的 DB 快路径，L0 已能覆盖，不需要落 L2。

- [ ] **Step 1: quickSniffBatch 双写 + 回调携带 isMotion**

在 `MotionPhotoDetector.kt` 顶部 import 区加：

```kotlin
import com.google.photochoice.data.motion.MotionPhotoIndexStore
```

把 `quickSniffBatch` 的回调签名从 `onEachDetected: suspend (Long) -> Unit` 改为携带结果，并在 `cache.put` 旁双写 IndexStore。替换整个 `quickSniffBatch`：

```kotlin
    /**
     * 快速 XMP 批量嗅探。
     *
     * @param onEachResult 每条嗅探完成即回调 (id, isMotion)，用于角标逐条刷新/校正
     */
    suspend fun quickSniffBatch(
        context: Context,
        items: List<MediaFile>,
        alreadyKnown: Set<Long> = emptySet(),
        parallelism: Int? = null,
        onEachResult: suspend (Long, Boolean) -> Unit = { _, _ -> },
    ): Set<Long> = coroutineScope {
        val candidates = items
            .filter { it.type == MediaFile.MediaType.IMAGE && it.id !in alreadyKnown }
            .filter { cache.get(it.id) == null }
        if (candidates.isEmpty()) return@coroutineScope emptySet()

        val parallel = parallelism ?: QUICK_SNIFF_PARALLEL
        val detected = mutableSetOf<Long>()
        for (chunk in candidates.chunked(QUERY_BATCH)) {
            chunk.chunked(parallel).forEach { group ->
                group.chunked(SNIFF_NOTIFY_CHUNK).forEach { subGroup ->
                    subGroup.map { file ->
                        async(Dispatchers.IO) {
                            val uri = Uri.parse(file.uri)
                            val isMotion = MotionPhotoXmpSniffer.isMotionPhotoQuick(context, uri)
                            cache.put(file.id, isMotion)
                            MotionPhotoIndexStore.put(file, isMotion)  // 双写 L2
                            file to isMotion
                        }
                    }.awaitAll().forEach { (file, isMotion) ->
                        onEachResult(file.id, isMotion)
                        if (isMotion) detected.add(file.id)
                    }
                }
            }
        }
        detected
    }
```

- [ ] **Step 2: detectSingle 双写**

在 `detectSingle` 中，两处 `cache.put(media.id, ...)` 后各加一行 IndexStore.put。替换 `detectSingle` 的 XMP 兜底段：

```kotlin
            val fromXmp = runCatching {
                MotionPhotoXmpSniffer.isMotionPhoto(context, media.uri.toUri())
            }.getOrDefault(false)
            cache.put(media.id, fromXmp)
            MotionPhotoIndexStore.put(media, fromXmp)  // 双写 L2
            fromXmp
```

（`fromStore` 命中那段是 DB 快路径，只写内存缓存即可，不改。）

- [ ] **Step 3: 编译验证（此时 enricher 调用点会因签名变更报错，Task 8 修复）**

Run: `./gradlew :photo-choice:compileDebugKotlin`
Expected: FAIL，`MotionPhotoListEnricher.kt` 报 "onEachDetected" 参数不存在 —— 预期内，下个 Task 修复。

- [ ] **Step 4: Commit（与 Task 8 连续，先提交本步）**

```bash
git add photo-choice/src/main/java/com/google/photochoice/data/motion/MotionPhotoDetector.kt
git commit -m "feat: 嗅探结果双写 IndexStore 并让回调携带 isMotion"
```

---

## Task 8: MotionPhotoListEnricher 回调兼顾正/负结果

**Files:**
- Modify: `photo-choice/src/main/java/com/google/photochoice/data/motion/MotionPhotoListEnricher.kt`

**说明：** 为支持 L3 启发式误报的淡出校正，enricher 需把"嗅探确认为非实况图、但文件名启发式曾命中"的 id 也通知出去（让 bind 重算 → 淡出）。正结果照常通知（淡入）。这样通知集精准，避免给大量本就隐藏的 item 无谓 rebind。

- [ ] **Step 1: 修改 enrichAndNotify 使用新回调并精准通知**

在 `MotionPhotoListEnricher.kt` 顶部 import 区加：

```kotlin
import com.google.photochoice.data.motion.MotionPhotoHeuristics
```

替换 `enrichAndNotify` 方法：

```kotlin
    private suspend fun enrichAndNotify(items: List<MediaFile>, urgent: Boolean) {
        val images = items.filter { needsEnrichment(it) }
        if (images.isEmpty()) return

        val fromStore = MotionPhotoDetector.queryMotionIdsFromMediaStore(
            context,
            images.map { it.id }
        )
        // DB 命中的正结果直接通知（淡入）
        fromStore.forEach { id -> notifyDetected(setOf(id)) }

        // 剩余走 XMP：正结果通知淡入；负结果但启发式曾命中的通知淡出校正
        val byId = images.associateBy { it.id }
        MotionPhotoDetector.quickSniffBatch(
            context = context,
            items = images,
            alreadyKnown = fromStore,
            parallelism = if (urgent) URGENT_SNIFF_PARALLEL else NORMAL_SNIFF_PARALLEL,
            onEachResult = { id, isMotion ->
                val needNotify = isMotion ||
                    (byId[id]?.let { MotionPhotoHeuristics.guess(it) } == true)
                if (needNotify) notifyDetected(setOf(id))
            },
        )
    }
```

- [ ] **Step 2: 编译验证**

Run: `./gradlew :photo-choice:compileDebugKotlin`
Expected: BUILD SUCCESSFUL（回调签名对齐）

- [ ] **Step 3: 跑全量单测确保无回归**

Run: `./gradlew :photo-choice:testDebugUnitTest`
Expected: PASS（全部既有测试通过）

- [ ] **Step 4: Commit**

```bash
git add photo-choice/src/main/java/com/google/photochoice/data/motion/MotionPhotoListEnricher.kt
git commit -m "feat: enricher 通知正结果(淡入)与启发式误报负结果(淡出校正)"
```

---

## Task 9: AlbumMotionPrebuilder 后台全量预建 + 性能治理

**Files:**
- Create: `photo-choice/src/main/java/com/google/photochoice/data/motion/AlbumMotionPrebuilder.kt`

**说明：** 进相册后台把整册图片嗅一遍写入 IndexStore。核心是性能治理——预建是优化项非正确性依赖，故对一切用户可见操作让路：滑动暂停、onStop 停、后台线程优先级、低并发、分片让出、跳过已知、断点续建。查询整册用一个独立轻量方法（仅取判定所需列）。

- [ ] **Step 1: 在 MediaRepository 增加"整册轻量清单"查询**

在 `MediaRepository.kt` 的 `loadMedia` 方法之后、`queryMediaCursor` 之前，增加方法：

```kotlin
    /**
     * 预建用：拉取某相册(或全库)所有图片的判定所需最小列(id/size/dateAdded/displayName)。
     * 不参与分页，一次性轻查询；仅 IMAGE。
     */
    suspend fun loadImageManifestForPrebuild(
        bucketId: String? = null
    ): List<MediaFile> = withContext(Dispatchers.IO) {
        val result = mutableListOf<MediaFile>()
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
        )
        val selection = StringBuilder(
            "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}"
        )
        val args = mutableListOf<String>()
        if (!bucketId.isNullOrEmpty()) {
            selection.append(" AND ${MediaStore.Files.FileColumns.BUCKET_ID} = ?")
            args.add(bucketId)
        }
        runCatching {
            context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                projection,
                selection.toString(),
                if (args.isEmpty()) null else args.toTypedArray(),
                null
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    result.add(
                        MediaFile(
                            id = id,
                            uri = MediaStoreUris.contentUriString(id, MediaFile.MediaType.IMAGE),
                            mimeType = "",
                            type = MediaFile.MediaType.IMAGE,
                            dateAdded = cursor.getLong(dateCol),
                            width = 0,
                            height = 0,
                            size = cursor.getLong(sizeCol),
                            bucketId = bucketId ?: "",
                            bucketName = "",
                            displayName = cursor.getString(nameCol) ?: ""
                        )
                    )
                }
            }
        }
        result
    }
```

- [ ] **Step 2: 创建 AlbumMotionPrebuilder**

创建 `AlbumMotionPrebuilder.kt`：

```kotlin
package com.google.photochoice.data.motion

import android.content.Context
import android.os.Process
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.google.photochoice.data.MediaRepository
import com.google.photochoice.data.model.MediaFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 相册级后台全量预建(L2)：把整册图片嗅一遍写入 IndexStore，之后滑到任意位置 O(1) 命中。
 *
 * 性能治理(预建是优化项，非正确性依赖，对用户可见操作无条件让路)：
 * - 滑动暂停：DRAGGING/SETTLING 时 pause，IDLE 防抖后 resume
 * - onStop 停：页面不可见即不推进(repeatOnLifecycle STARTED)
 * - 后台线程优先级 + 分片间 yield
 * - 低并发：区别于视口紧急通道
 * - 跳过已知：先过滤 IndexStore 已有有效记录
 * - 可中断/断点续建：切相册取消 Job，下次从索引已有处继续
 *
 * @param onMotionDetected 预建中命中实况图的 id 回调(刷新可见角标)
 */
class AlbumMotionPrebuilder(
    private val context: Context,
    private val repository: MediaRepository,
    private val scope: CoroutineScope,
    private val onMotionDetected: (Set<Long>) -> Unit
) {
    private var job: Job? = null
    private val paused = AtomicBoolean(false)

    /** 滑动开始：暂停预建，把磁盘带宽让给缩略图。 */
    fun pause() { paused.set(true) }

    /** 滑动结束(防抖后)：恢复预建。 */
    fun resume() { paused.set(false) }

    /**
     * 启动某相册的预建。会取消上一个相册的预建 Job。
     * 应在首屏 IDLE 后调用(让路首屏缩略图)。
     */
    fun start(lifecycleOwner: LifecycleOwner, bucketId: String?) {
        job?.cancel()
        job = scope.launch {
            // onStop 自动挂起，onStart 自动恢复
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                runPrebuild(bucketId)
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }

    private suspend fun runPrebuild(bucketId: String?) {
        // 后台线程优先级：调度器天然让路 UI
        runCatching {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
        }
        val manifest = repository.loadImageManifestForPrebuild(bucketId)
        if (manifest.isEmpty()) return

        // 跳过已知：过滤 IndexStore 已有有效结果
        val pending = manifest.filter {
            MotionPhotoIndexStore.query(it) == IndexResult.UNKNOWN
        }
        if (pending.isEmpty()) {
            Log.d(TAG, "prebuild bucket=$bucketId all known, skip")
            return
        }
        Log.d(TAG, "prebuild bucket=$bucketId pending=${pending.size}/${manifest.size}")

        for (slice in pending.chunked(SLICE_SIZE)) {
            if (!currentCoroutineActive()) return
            // 滑动期暂停：轮询等待恢复(让磁盘给缩略图)
            while (paused.get() && currentCoroutineActive()) {
                delay(PAUSE_POLL_MS)
            }
            if (!currentCoroutineActive()) return

            MotionPhotoDetector.quickSniffBatch(
                context = context,
                items = slice,
                parallelism = PREBUILD_PARALLEL,   // 低并发
                onEachResult = { id, isMotion ->
                    if (isMotion) {
                        withContext(Dispatchers.Main) { onMotionDetected(setOf(id)) }
                    }
                }
            )
            yield()  // 分片间让出，保证紧急通道可插队
        }
        MotionPhotoIndexStore.requestFlush()
        Log.d(TAG, "prebuild bucket=$bucketId done")
    }

    private suspend fun currentCoroutineActive(): Boolean =
        kotlin.coroutines.coroutineContext[Job]?.isActive ?: false

    companion object {
        private const val TAG = "AlbumMotionPrebuilder"
        /** 每片大小：一批嗅探后让出。 */
        private const val SLICE_SIZE = 12
        /** 预建低并发(视口紧急通道为 20)。 */
        private const val PREBUILD_PARALLEL = 4
        /** 暂停轮询间隔。 */
        private const val PAUSE_POLL_MS = 150L
    }
}
```

- [ ] **Step 3: 编译验证**

Run: `./gradlew :photo-choice:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add photo-choice/src/main/java/com/google/photochoice/data/MediaRepository.kt photo-choice/src/main/java/com/google/photochoice/data/motion/AlbumMotionPrebuilder.kt
git commit -m "feat: 新增 AlbumMotionPrebuilder 后台全量预建(含性能治理)"
```

---

## Task 10: MediaGridAdapter 消费 BadgeState + 淡入/淡出

**Files:**
- Modify: `photo-choice/src/main/java/com/google/photochoice/ui/grid/MediaGridAdapter.kt`

**说明：** `bindLivePhotoIndicator` 从"直接查缓存显隐"改为走 `MotionPhotoDecision.resolve` 得到 `BadgeState` 驱动。确定态无动画同帧显示；`UNKNOWN` 触发嗅探；payload 刷新路径根据"从隐藏变显示/从显示变隐藏"决定淡入/淡出。

- [ ] **Step 1: 增加 import**

在 `MediaGridAdapter.kt` 顶部 import 区加：

```kotlin
import android.view.animation.DecelerateInterpolator
import com.google.photochoice.data.motion.BadgeState
import com.google.photochoice.data.motion.MotionPhotoDecision
import com.google.photochoice.data.motion.MotionPhotoHeuristics
import com.google.photochoice.data.motion.MotionPhotoIndexStore
```

- [ ] **Step 2: 替换 bindLivePhotoIndicator**

把 `MediaVH.bindLivePhotoIndicator` 整个替换为：

```kotlin
        fun bindLivePhotoIndicator(mediaItem: MediaFile) {
            if (mediaItem.type != MediaFile.MediaType.IMAGE) {
                setBadgeVisible(false, animate = false)
                return
            }
            val state = MotionPhotoDecision.resolve(
                isMotionFlag = mediaItem.isMotionPhoto,
                memoryResult = MotionPhotoDetector.memoryResult(mediaItem.id),
                indexResult = MotionPhotoIndexStore.query(mediaItem),
                heuristicGuess = MotionPhotoHeuristics.guess(mediaItem)
            )
            when (state) {
                BadgeState.CONFIRMED_MOTION,
                BadgeState.HEURISTIC_MOTION -> setBadgeVisible(true, animate = false)

                BadgeState.CONFIRMED_NOT -> setBadgeVisible(false, animate = false)

                BadgeState.UNKNOWN -> {
                    setBadgeVisible(false, animate = false)
                    onRequestMotionEnrich?.invoke(mediaItem)
                }
            }
        }

        /** payload 刷新入口(嗅探回调后)：与首帧不同，允许淡入/淡出动画。 */
        fun refreshLivePhotoIndicator(mediaItem: MediaFile) {
            if (mediaItem.type != MediaFile.MediaType.IMAGE) {
                setBadgeVisible(false, animate = false)
                return
            }
            val state = MotionPhotoDecision.resolve(
                isMotionFlag = mediaItem.isMotionPhoto,
                memoryResult = MotionPhotoDetector.memoryResult(mediaItem.id),
                indexResult = MotionPhotoIndexStore.query(mediaItem),
                heuristicGuess = MotionPhotoHeuristics.guess(mediaItem)
            )
            val shouldShow = state == BadgeState.CONFIRMED_MOTION ||
                state == BadgeState.HEURISTIC_MOTION
            setBadgeVisible(shouldShow, animate = true)
        }

        /** 统一显隐入口。animate=true 时用 alpha 淡入/淡出(150ms)。 */
        private fun setBadgeVisible(visible: Boolean, animate: Boolean) {
            livePhotoBadge.animate().cancel()
            if (visible) {
                if (livePhotoBadge.visibility == View.VISIBLE && livePhotoBadge.alpha == 1f) return
                if (animate) {
                    livePhotoBadge.alpha = 0f
                    livePhotoBadge.visibility = View.VISIBLE
                    livePhotoBadge.animate()
                        .alpha(1f).setDuration(BADGE_FADE_MS)
                        .setInterpolator(DecelerateInterpolator()).start()
                } else {
                    livePhotoBadge.alpha = 1f
                    livePhotoBadge.visibility = View.VISIBLE
                }
            } else {
                if (livePhotoBadge.visibility != View.VISIBLE) return
                if (animate) {
                    livePhotoBadge.animate()
                        .alpha(0f).setDuration(BADGE_FADE_MS)
                        .setInterpolator(DecelerateInterpolator())
                        .withEndAction { livePhotoBadge.visibility = View.GONE }.start()
                } else {
                    livePhotoBadge.alpha = 1f
                    livePhotoBadge.visibility = View.GONE
                }
            }
        }
```

**注意：** `livePhotoBadge` 当前声明为 `View`（第 124 行）。`View` 本身就有 `animate()`/`alpha`/`visibility`，无需改类型。

- [ ] **Step 3: payload 分支改调 refreshLivePhotoIndicator**

把 `onBindViewHolder(holder, position, payloads)` 中 `PAYLOAD_MOTION` 分支的 `holder.bindLivePhotoIndicator(item)` 改为 `holder.refreshLivePhotoIndicator(item)`：

```kotlin
            if (payloads.contains(PAYLOAD_MOTION)) {
                holder.refreshLivePhotoIndicator(item)
            }
```

- [ ] **Step 4: bindVideoIndicator 里对 badge 的隐藏也走统一入口**

`bindVideoIndicator` 中 `livePhotoBadge.visibility = View.GONE` 一行改为（视频项确定隐藏，无动画）：

```kotlin
        private fun bindVideoIndicator(mediaItem: MediaFile) {
            if (mediaItem.type == MediaFile.MediaType.VIDEO) {
                setBadgeVisible(false, animate = false)
                ivVideoIndicator.visibility = View.VISIBLE
```

- [ ] **Step 5: 增加淡入时长常量**

在 `companion object` 中 `THUMBNAIL_PX` 之后加：

```kotlin
        const val THUMBNAIL_PX = 200
        /** 角标淡入/淡出时长。 */
        private const val BADGE_FADE_MS = 150L
```

- [ ] **Step 6: 在 MotionPhotoDetector 暴露 memoryResult**

`MediaGridAdapter` 需要读 L1 内存三态（null/true/false）。在 `MotionPhotoDetector.kt` 的 `hasCachedResult` 之后增加：

```kotlin
    /** L1 内存缓存三态：null=未缓存，true/false=已判定。供级联 L1。 */
    fun memoryResult(id: Long): Boolean? = cache.get(id)
```

- [ ] **Step 7: 编译验证**

Run: `./gradlew :photo-choice:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add photo-choice/src/main/java/com/google/photochoice/ui/grid/MediaGridAdapter.kt photo-choice/src/main/java/com/google/photochoice/data/motion/MotionPhotoDetector.kt
git commit -m "feat: Adapter 角标改由 BadgeState 驱动并支持淡入/淡出校正"
```

---

## Task 11: MediaGridFragment 接线（ensureLoaded + 预建生命周期 + 滚动暂停）

**Files:**
- Modify: `photo-choice/src/main/java/com/google/photochoice/ui/grid/MediaGridFragment.kt`

**说明：** 接入 IndexStore.ensureLoaded、AlbumMotionPrebuilder；滚动状态驱动预建 pause/resume；首屏 IDLE 后启动预建；切相册取消并重启；onStop 触发索引 flush。原 `warmAlbumMotionIndex` 保留（API34+ 机型的 DB 快路径），预建作为补充。

- [ ] **Step 1: 增加成员与 import**

在 `MediaGridFragment.kt` 顶部 import 区加：

```kotlin
import com.google.photochoice.data.MediaRepository
import com.google.photochoice.data.motion.AlbumMotionPrebuilder
import com.google.photochoice.data.motion.MotionPhotoIndexStore
```

在类成员区（`motionPhotoEnricher` 之后）加：

```kotlin
    private var motionPhotoEnricher: MotionPhotoListEnricher? = null
    private var albumPrebuilder: AlbumMotionPrebuilder? = null
    /** 首屏是否已首次 IDLE（用于延迟启动预建，让路首屏缩略图）。 */
    private var firstIdlePassed = false
```

- [ ] **Step 2: onViewCreated 尽早 ensureLoaded**

在 `onViewCreated` 中 `setupAdaptersAndRecyclerView()` 之前加一行（异步载入历史索引）：

```kotlin
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        MotionPhotoIndexStore.ensureLoaded(requireContext().applicationContext)
        cameraHelper = CameraHelper(requireContext())
        setupAdaptersAndRecyclerView()
        checkPermission()
    }
```

- [ ] **Step 3: 滚动监听驱动预建 pause/resume + 首屏 IDLE 启动预建**

替换 `setupAdaptersAndRecyclerView` 中 `addOnScrollListener(...)` 整段为：

```kotlin
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    when (newState) {
                        RecyclerView.SCROLL_STATE_IDLE -> {
                            scheduleVisibleWindowEnrichment()
                            // 滑动结束防抖后恢复预建
                            recyclerView.postDelayed({
                                albumPrebuilder?.resume()
                            }, PREBUILD_RESUME_DEBOUNCE_MS)
                            onFirstIdle()
                        }
                        RecyclerView.SCROLL_STATE_SETTLING,
                        RecyclerView.SCROLL_STATE_DRAGGING -> {
                            // 滑动期暂停预建，磁盘带宽让给缩略图
                            albumPrebuilder?.pause()
                            scheduleVisibleWindowEnrichmentThrottled(FAST_ENRICH_INTERVAL_MS)
                        }
                    }
                }

                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (dy != 0) {
                        scheduleVisibleWindowEnrichmentThrottled(FAST_ENRICH_INTERVAL_MS)
                    }
                }
            })
```

- [ ] **Step 4: 在 startMediaObservation 创建 prebuilder 并首屏后启动**

在 `startMediaObservation()` 里，`motionPhotoEnricher = ...` 之后加创建 prebuilder：

```kotlin
        motionPhotoEnricher = MotionPhotoListEnricher(
            context = requireContext(),
            scope = viewLifecycleOwner.lifecycleScope,
            onMotionDetected = { ids -> mediaAdapter.notifyMotionBadges(ids) }
        )

        albumPrebuilder = AlbumMotionPrebuilder(
            context = requireContext().applicationContext,
            repository = MediaRepository(requireContext().applicationContext),
            scope = viewLifecycleOwner.lifecycleScope,
            onMotionDetected = { ids -> mediaAdapter.notifyMotionBadges(ids) }
        )
```

- [ ] **Step 5: 增加首屏 IDLE 启动预建的方法**

在 `MediaGridFragment` 中增加方法（放在 `warmAlbumMotionIndex` 附近）：

```kotlin
    /** 首屏首次 IDLE 后启动预建，避免与首屏缩略图抢 I/O。 */
    private fun onFirstIdle() {
        if (firstIdlePassed) return
        if (mediaAdapter.itemCount <= 0) return
        firstIdlePassed = true
        albumPrebuilder?.start(viewLifecycleOwner, viewModel.currentBucketId.value)
    }
```

- [ ] **Step 6: 切相册时重启预建；相机回拍后不重建预建索引**

在 `startMediaObservation` 的 `currentBucketId.drop(1).collect { ... }` 块中，加预建重启（`warmAlbumMotionIndex(bucketId)` 之后）：

```kotlin
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.currentBucketId
                .drop(1)
                .collect { bucketId ->
                    motionPhotoEnricher?.reset()
                    binding.recyclerView.scrollToPosition(0)
                    gridDateScrollCoordinator?.reset()
                    warmAlbumMotionIndex(bucketId)
                    // 切相册：重启预建(内部会取消上一个 Job；跳过已知)
                    firstIdlePassed = true
                    albumPrebuilder?.start(viewLifecycleOwner, bucketId)
                }
        }
```

- [ ] **Step 7: onStop flush 索引；onDestroyView 取消预建**

在 `MediaGridFragment` 增加 `onStop`（若无则新增），并在 `onDestroyView` 取消预建：

```kotlin
    override fun onStop() {
        super.onStop()
        // 页面不可见：主动 flush 未落盘的判定，避免丢失
        MotionPhotoIndexStore.requestFlush()
    }

    override fun onDestroyView() {
        gridDateScrollCoordinator?.detach()
        gridDateScrollCoordinator = null
        motionPhotoEnricher = null
        albumPrebuilder?.cancel()
        albumPrebuilder = null

        super.onDestroyView()
        _binding = null
    }
```

- [ ] **Step 8: 增加防抖常量**

在 `companion object` 中 `PREFETCH_ROWS` 之后加：

```kotlin
        /** 可见区下方预取行数（约 1.5 屏）。 */
        private const val PREFETCH_ROWS = 5
        /** 滑动结束后恢复预建的防抖(避免抬手瞬间又滑)。 */
        private const val PREBUILD_RESUME_DEBOUNCE_MS = 300L
```

- [ ] **Step 9: 编译 + 全量单测**

Run: `./gradlew :photo-choice:compileDebugKotlin :photo-choice:testDebugUnitTest`
Expected: BUILD SUCCESSFUL，全部单测通过

- [ ] **Step 10: Commit**

```bash
git add photo-choice/src/main/java/com/google/photochoice/ui/grid/MediaGridFragment.kt
git commit -m "feat: Fragment 接线 IndexStore 与预建(滚动暂停/首屏延迟/onStop flush)"
```

---

## Task 12: 集成验证（构建 + 手动 + 性能剖面）

**Files:** 无（验证任务）

- [ ] **Step 1: 全量构建 debug APK**

Run: `./gradlew :sample:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 全量单元测试**

Run: `./gradlew :photo-choice:testDebugUnitTest`
Expected: 全部通过（Heuristics 7 + IndexCodec 7 + IndexMapMerge 3 + Decision 7 + Sanity 1）

- [ ] **Step 3: 功能手动验证（真机，用户当前机型）**

依设计文档 §9 逐条验证并记录：
- 全新安装首次快滑：启发式命中项与缩略图同帧；未知项淡入自然、无跳出感。
- 二次进入 / 切回相册：角标全部同帧显示（L2 命中），无迟到弹出。
- 快滑到底再快滑回：不出现角标批量迟到弹出。
- 无 `IS_MOTION_PHOTO` 机型：不依赖 DB 也能同帧显示。
- 预览页长按播放、导出压缩策略正常（回归 `isLivePhoto` 依赖）。

- [ ] **Step 4: 性能剖面验证（对齐 §8.7 预算）**

用 Android Studio Profiler：
- 快滑时观察 Disk I/O：滑动期后台预建 I/O 应≈0（缩略图独占带宽）；滚动帧率不劣于关闭本功能。
- 大相册进入静置：预建运行时不掉帧；切后台(onStop)后 CPU/Disk 归零。
- 索引落盘：不应出现每条一次的高频小写入（应批量）。
- 对比开关本功能前后 FrameMetrics/Systrace 帧率无劣化。

- [ ] **Step 5: 记录验证结论并提交（若前面步骤有小修则一并提交）**

```bash
git add -A
git commit -m "test: 集成/性能验证通过，记录基线"
```

---

## 自查记录

- **Spec 覆盖**：L0–L4 级联(Task 6/10)、持久化索引(Task 4/5)、启发式(Task 3)、后台预建(Task 9)、性能治理§8 全部映射到 Task 9/11、淡入淡出§5.5→Task 10、displayName 前置→Task 2。
- **类型一致性**：`IndexResult`/`IndexRecord`/`BadgeState` 跨 Task 4/5/6/10 命名一致；回调统一为 `onEachResult(id, isMotion)`（Task 7/8/9）；`memoryResult(id): Boolean?`(Task 10 Step 6) 供级联 L1。
- **无占位符**：每个改动步骤均含完整代码与精确路径/行为。

---

## 评审后补充修复（Task 1~11 提交后）

执行期间的两级评审 + 最终整体评审共拦下并修复了 1 Critical + 8 Important + 1 关键并发问题，均已在实现中闭环：

- **Prebuilder（C1/I1/I2/M1/M2）**：`setThreadPriority` 误降主线程 → 编排整体移入 `Dispatchers.Default`；`ensureLoaded` 未 await → 新增 `awaitLoaded` 防冷启动整册重嗅；预建按 IndexStore 过滤而 `quickSniffBatch` 按 LruCache 过滤致 API34+ 索引不收敛 → 内存命中写穿 IndexStore；slice 级批量通知；主体 try/catch 防 Crash。
- **IndexStore（I-1/I-2/I-3）**：`flushBuffer` 静默丢盘 → 文件未就绪时暂缓重试；`ensureLoaded` 旧盘覆盖会话新值 → 改 `putIfAbsent`；compact rename 前 fsync + 失败清理。
- **跨模块整合（Important×3 + Minor）**：嗅探调度层接入 L2 检查（`needsEnrichment` + `quickSniffBatch` 过滤），杜绝冷启动重嗅与弱嗅探覆盖强结果；预建通道补启发式误报的淡出校正通知；VIDEO-only 模式跳过预建；预建 manifest 排除 pending 文件。
- **嗅探并发（对应设计 §8.6）**：`quickSniffBatch` 的嵌套 `chunked`+`awaitAll` 把视口紧急通道有效并发钳制为固定小值（`URGENT_SNIFF_PARALLEL=20` 名存实亡）→ 改用 `Semaphore(parallel)` 真并发限流，许可证只圈 I/O、回调在许可证外，`ConcurrentHashMap.newKeySet` 保线程安全，删除失效的 `SNIFF_NOTIFY_CHUNK`。

**验证**：sample APK `assembleDebug` 构建成功；25 个单元测试全绿。真机 + Profiler 性能剖面验证（快滑同帧、滑动期后台 I/O≈0、帧率不劣化）待在设备上完成。
