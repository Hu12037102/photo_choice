<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://github.com/Hu12037102/photo_choice/raw/master/docs/hero-dark.png">
    <img src="docs/hero-light.png" width="860" alt="PhotoChoice — Android 相册选择器：网格、相册、大图预览、裁剪、压缩、实况图">
  </picture>
</p>

<p align="center">
  <a href="https://jitpack.io/#Hu12037102/photo_choice"><img src="https://img.shields.io/jitpack/version/com.github.Hu12037102/photo_choice?style=flat-square&label=JitPack&color=C8763C" alt="JitPack"></a>
  <img src="https://img.shields.io/badge/minSdk-29-1D1D1F?style=flat-square" alt="minSdk 29">
  <img src="https://img.shields.io/badge/language-Kotlin-1D1D1F?style=flat-square" alt="Kotlin">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-Apache%202.0-1D1D1F?style=flat-square" alt="Apache 2.0"></a>
</p>

<p align="center">
  <a href="README.md">English</a> ·
  <a href="README.ja.md">日本語</a> ·
  <a href="README.ko.md">한국어</a> ·
  <a href="README.fr.md">Français</a> ·
  <a href="README.es.md">Español</a> ·
  <a href="README.ar.md">العربية</a> ·
  <a href="README.ru.md">Русский</a>
</p>

<br>

Android 相册选择器组件：网格多选、相册切换、大图预览、拍照入口、单图裁剪与可选压缩，并支持
**实况图 / Motion Photo** 识别与预览播放。通过 **Builder 链式 API** 接入，无需（也不应）直接启动组件内部的
Activity。

<br>

## 演示

<p align="center">
  <a href="https://github.com/Hu12037102/photo_choice/blob/master/docs/demo.mp4">
    <picture>
      <source media="(prefers-color-scheme: dark)" srcset="https://github.com/Hu12037102/photo_choice/raw/master/docs/demo-poster-dark.png">
      <img src="docs/demo-poster.png" width="820" alt="观看 PhotoChoice 演示视频">
    </picture>
  </a>
</p>

<p align="center">
  <a href="https://github.com/Hu12037102/photo_choice/blob/master/docs/demo.mp4"><b>点击播放演示视频</b></a><br>
  <sub>网格与相册 · 选中序号 · 滚动日期 · 拍照入口 · 大图预览<br>
  视频播放 · 实况图 · 裁剪 · JPEG 压缩 · 浅色 / 深色 / 跟随系统</sub>
</p>

<br>

<p align="center">
  <a href="https://huxiaobai.oss-cn-shanghai.aliyuncs.com/open/sample-release.apk"><img src="docs/qr-sample-apk.png" width="200" alt="扫码安装 PhotoChoice 示例应用"></a>
</p>

<p align="center">
  <b><a href="https://huxiaobai.oss-cn-shanghai.aliyuncs.com/open/sample-release.apk">下载示例应用</a></b><br>
  <sub>手机扫码，或点击直接下载 · <code>sample-release.apk</code> · Android 10+</sub>
</p>

---

## 功能概览

| 能力 | 说明 |
|------|------|
| 媒体类型 | 仅图片 / 仅视频 / 图片 + 视频 |
| 选择模式 | 单选或多选（`selectCount` 1–9），带选中序号角标 |
| 相册 | 按 MediaStore 目录聚合，下拉切换相册 |
| 网格 | 可配置列数（2–6），正方形缩略图，Paging 3 分页 |
| 滚动日期条 | 滚动时显示当前可见区域的日期 |
| 拍照 | 可选首格相机入口，照片写入公共相机目录 `DCIM/Camera` |
| 预览 | 大图全屏、左右滑动；视频内嵌播放 |
| 实况图 | 网格 LIVE 角标；预览长按播放内嵌短视频 |
| 裁剪 | 单选 + 图片模式下启用独立 `CropActivity` |
| 压缩 | 可选完成时 JPEG 尺寸 + 质量压缩，带体积目标回退循环 |
| 主题 | 浅色 / 深色 / 跟随系统，per-Activity 生效，绝不改写宿主全局模式 |
| 启动方式 | **`PhotoChoiceContract`**（推荐，无静态状态）或 `forResult` 回调 |
| 进程死亡安全 | Contract 模式可抗 Activity 重建与进程死亡 |

- **包名** `com.google.photochoice` · **版本** `1.1.0`（见 [CHANGELOG](CHANGELOG.md)）
- **最低 SDK** 29（Android 10，Scoped Storage — 无需写存储权限即可读取公共媒体）
- **compileSdk** 36 · **Java** 11 · **Kotlin** · [Apache License 2.0](LICENSE)

---

## 引入依赖

### 方式 A — JitPack（推荐）

在宿主的 **`settings.gradle.kts`** 中添加 JitPack 仓库。本项目使用 `FAIL_ON_PROJECT_REPOS`，
因此仓库必须写在 `dependencyResolutionManagement` 中，而不能写在模块里：

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

然后在 app 或业务模块中声明依赖：

```kotlin
dependencies {
    implementation("com.github.Hu12037102:photo_choice:1.1.0")
}
```

> JitPack 会按 tag 源码按需构建 AAR，新 tag 的首次请求可能需要一分钟左右。

### 方式 B — 源码模块

```kotlin
// settings.gradle.kts
include(":photo-choice")

// app/build.gradle.kts
dependencies {
    implementation(project(":photo-choice"))
}
```

---

## 快速开始

### 1. 声明权限

组件在自身 Manifest 中声明了媒体读取权限，但**宿主 App 必须声明同样的权限**并在运行时申请。

| Android 版本 | 权限 |
|--------------|------|
| API 34+ | `READ_MEDIA_IMAGES`、`READ_MEDIA_VIDEO`、`READ_MEDIA_VISUAL_USER_SELECTED` — 部分授权也视为可用 |
| API 33 | `READ_MEDIA_IMAGES`、`READ_MEDIA_VIDEO` |
| API 29–32 | `READ_EXTERNAL_STORAGE` |

`PermissionHelper` 提供权限清单与授权检查：

```kotlin
import com.google.photochoice.util.PermissionHelper

if (PermissionHelper.hasMediaPermission(context)) {
    openPhotoChoice()
} else {
    requestPermissionLauncher.launch(PermissionHelper.requiredMediaPermissions())
}
```

`requiredMediaPermissions()` 返回当前 SDK 档位的**完整**权限数组，**不会**根据 `mediaType` 缩小范围。
API 34+ 上 `hasMediaPermission()` 只要三者**任一**被授予即返回 `true`（部分照片授权也算）；
API 33 上则要求图片与视频权限**同时**被授予。

### 2. 启动选择器 — Contract（推荐）

```kotlin
import com.google.photochoice.PhotoChoice
import com.google.photochoice.PhotoChoiceContract
import com.google.photochoice.config.MediaType

val launcher = registerForActivityResult(PhotoChoiceContract()) { result ->
    if (result == null) return@registerForActivityResult   // 用户取消
    result.uris.forEach { uri ->
        // content:// 或 file:// URI，按选中顺序排列
    }
}

launcher.launch(
    PhotoChoice.with(this)
        .selectCount(9)
        .mediaType(MediaType.IMAGE)
        .spanCount(4)
        .showCamera(true)
        .buildConfig()
)
```

`PhotoChoiceContract` 是 `ActivityResultContract<PhotoChoiceConfig, PhotoChoiceResult?>`。
配置通过 Intent extra 传入、结果通过 `setResult()` 返回，两者都由系统托管，因此不依赖任何静态状态，
天然可抗 Activity 重建与进程死亡。**生产环境请优先使用这一方式。**

### 3. 备选方案 — 回调 API（旧轨）

从 `FragmentActivity`（或 `AppCompatActivity`）调用：

```kotlin
PhotoChoice.with(this)
    .selectCount(9)
    .mediaType(MediaType.IMAGE)
    .forResult(this) { result ->
        if (result == null) return@forResult   // 用户取消
        result.uris.forEach { uri -> /* ... */ }
    }
```

> **回调 API 内部用静态字段持有回调**，无法在宿主 Activity 重建或进程死亡后存活：若选择器运行期间宿主被杀，
> 回调会丢失，选择器会安静退出且不返回结果。对可靠性有要求时请改用上面的 Contract 方式。

---

## 配置项

所有 setter 均返回 `Builder`。终结方法为 `buildConfig()`（配合 `PhotoChoiceContract`）、
`forResult(activity, callback)`，或 `build()`（若你需要 `PhotoChoice` 实例本身）。

| 方法 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `selectCount` | `Int` | `9` | `1` = 单选，`>1` = 多选。超出 `1..9` 的值会**回落为 `1`**，而不是就近钳制 |
| `mediaType` | `MediaType` | `IMAGE` | `IMAGE` / `VIDEO` / `ALL` |
| `spanCount` | `Int` | `3` | 网格列数，钳制到 `2..6` |
| `showCamera` | `Boolean` | `true` | 首格显示相机入口 — 见[拍照](#拍照) |
| `minImageSize` | `Long` | `0` | 图片最小体积（字节），可过滤掉小图标。仅对图片生效 |
| `maxImageSize` | `Long` | `Long.MAX_VALUE` | 图片最大体积（字节）。仅对图片生效 |
| `minVideoDuration` | `Long` | `0` | 视频最短时长（毫秒） |
| `maxVideoDuration` | `Long` | `60_000` | 视频最长时长（毫秒） |
| `themeMode` | `ThemeMode` | `FOLLOW_SYSTEM` | `LIGHT` / `DARK` / `FOLLOW_SYSTEM`，per-Activity 生效 |
| `cropConfig` | `CropConfig` | `CropConfig()` | 见下文 |
| `compressConfig` | `CompressConfig` | `CompressConfig()` | 见下文 |

> **`spanCount` 存在两个不同的默认值。** `Builder` 默认为 `3`，而 `PhotoChoiceConfig` 构造参数自身默认为
> `4`。若你绕过 Builder 直接构造 `PhotoChoiceConfig`，拿到的是 4 列。

`PhotoChoice.with(context)` 目前并不使用传入的 `context` 参数，保留它是为了 API 兼容与调用处的书写习惯。

### 裁剪 `CropConfig`

```kotlin
import com.google.photochoice.config.CropConfig
import com.google.photochoice.config.CropAspectRatio

.cropConfig(
    CropConfig(
        enabled = true,
        aspectRatio = CropAspectRatio.SQUARE,
        maxWidth = 0,      // 0 = 不限制
        maxHeight = 0,     // 0 = 不限制
    )
)
```

| 字段 | 默认值 | 说明 |
|------|--------|------|
| `enabled` | `false` | 选中后进入独立的 `CropActivity` |
| `aspectRatio` | `ORIGINAL` | `ORIGINAL` / `SQUARE` / `RATIO_3_4` / `RATIO_4_3` / `RATIO_9_16` / `RATIO_16_9`；每个枚举都暴露 `ratio: Float?`（`ORIGINAL` 为 `null`） |
| `maxWidth` | `0` | 输出宽度上限（像素）；`0` 或负数表示不限制 |
| `maxHeight` | `0` | 输出高度上限（像素）；`0` 或负数表示不限制 |

裁剪仅在 `selectCount == 1` **且** `mediaType == MediaType.IMAGE` 时生效。

> **`MediaType.ALL` 会静默禁用裁剪。** 判定条件是与 `IMAGE` 精确相等，而非「包含图片」，
> 因此图片+视频混合模式下即便 `enabled = true` 也永远不会进入裁剪页。

单选 + 裁剪开启时，选中图片会直接进入裁剪，裁剪完成后返回并关闭选择器。

### 压缩 `CompressConfig`

点击**完成**时，图片会先缩放并做 JPEG 压缩再返回结果。视频、GIF 与保留动效的实况图不会被压缩。

```kotlin
import com.google.photochoice.config.CompressConfig

.compressConfig(
    CompressConfig(
        enabled = true,
        maxWidth = 1280,
        maxHeight = 1280,
        quality = 80,
        maxFileSizeBytes = CompressConfig.DEFAULT_MAX_FILE_SIZE_BYTES,
        minQuality = 50,
        qualityStep = 10,
    )
)
```

| 字段 | 默认值 | 说明 |
|------|--------|------|
| `enabled` | `false` | 总开关 |
| `maxWidth` / `maxHeight` | `1280` | 缩放的长边上限 |
| `quality` | `80` | JPEG 起始质量，使用时钳制到 `1..100` |
| `maxFileSizeBytes` | `1_572_864`（约 1.5 MB） | 目标输出体积；未达标则逐级下调质量重试 |
| `minQuality` | `50` | 上述重试循环的质量下限，绝不会低于此值 |
| `qualityStep` | `10` | 每次重试下调的质量步长 |
| `skipCompressBaselineLongEdge` | `1280` | 跳过压缩的判定阈值：长边 |
| `skipCompressBaselineShortEdge` | `720` | 跳过压缩的判定阈值：短边 |
| `skipCompressMaxBytes` | `153_600`（150 KB） | 跳过压缩的判定阈值：体积 |

**本身已经足够小的图片会被原样返回：** 长边 ≤ 1280 **且**短边 ≤ 720，**或**文件小于 150 KB。
对这类图再压一次只会损失画质而省不下多少体积。以静态图导出的实况图会有意绕过该豁免，始终执行压缩。

> 输出恒为 JPEG。带透明通道的 PNG / WebP 压缩后会呈现黑色背景。

---

## 返回结果

```kotlin
data class PhotoChoiceResult(
    val uris: List<Uri>,    // 选中的 URI，按选中顺序排列
    val paths: List<String> // 尽力而为的本地路径；无法解析时为 URI 字符串
)
```

`paths` 只有对组件自己产出的文件（压缩或裁剪输出）才是真实的文件系统路径；
MediaStore 条目返回的是其 `content://` URI 的字符串形式。

| 媒体 | 未开启压缩 | 已开启压缩 |
|------|------------|------------|
| 静态图片 | `content://` MediaStore URI | `file://` JPEG，位于 `cacheDir/photo_choice/compress_<uuid>.jpg` |
| 小图（低于跳过基线） | `content://` MediaStore URI | `content://` — 原样返回 |
| 视频 | `content://` MediaStore URI | 不处理 |
| GIF | `content://` MediaStore URI | 不处理（压缩会丢失动画） |
| 实况图 — 保留动效 | `content://` MediaStore URI | 不处理（动效保留） |
| 实况图 — 导出静态 | 不适用 | `file://` 压缩后的 JPEG |
| 裁剪后的图片 | `file://`，位于 `cacheDir/photo_choice/crop_<timestamp>.jpg` | 同上，并追加压缩 |

### 清理缓存

```kotlin
PhotoChoice.cleanup(context)
```

> **该方法会清空全部文件，而不只是过期文件。** `cleanup()` 会无条件清空
> `cacheDir/photo_choice/`、`cacheDir/photo_choice_motion/` 与 `cacheDir/photo_choice_camera/`，
> 并清除实况图的内存缓存。请在**消费完结果之后**再调用 — 否则你仍持有的 `file://` URI 会失效。
>
> 按 24 小时时效清理是组件内部自行调度的另一套流程，无需你手动触发。

---

## 常见场景

```kotlin
// 多图选择，最多 9 张
PhotoChoice.with(activity)
    .selectCount(9)
    .mediaType(MediaType.IMAGE)
    .spanCount(4)
    .showCamera(true)
    .forResult(activity) { result -> /* ... */ }

// 头像：单选 + 正方形裁剪 + 压缩
PhotoChoice.with(activity)
    .selectCount(1)
    .mediaType(MediaType.IMAGE)
    .cropConfig(CropConfig(enabled = true, aspectRatio = CropAspectRatio.SQUARE))
    .compressConfig(CompressConfig(enabled = true))
    .forResult(activity) { result -> /* ... */ }

// 仅视频，最长 60 秒
PhotoChoice.with(activity)
    .selectCount(1)
    .mediaType(MediaType.VIDEO)
    .showCamera(false)          // VIDEO 模式下本就会自动隐藏
    .maxVideoDuration(60_000L)
    .forResult(activity) { result -> /* ... */ }

// 图片 + 视频混合 — 注意 ALL 模式下裁剪不可用
PhotoChoice.with(activity)
    .selectCount(9)
    .mediaType(MediaType.ALL)
    .maxVideoDuration(60_000L)
    .forResult(activity) { result -> /* ... */ }
```

---

## 行为细节

### 单选与多选的差异

| 模式 | 网格 UI | 交互 |
|------|---------|------|
| 多选（`selectCount > 1`） | 复选框 + 选中序号角标 | 点复选框切换选中；点缩略图进入预览 |
| 单选（`selectCount == 1`） | **隐藏**复选框、序号角标与禁用蒙层 | 点缩略图 → 进入预览，或在启用裁剪时进入裁剪 |

单选没有中间的「已选中」状态，因此选择相关的 UI 是整体隐藏，而不是置灰。

### 拍照

`showCamera(true)`（默认值）时，网格首格是拍照入口。

| 项目 | 取值 |
|------|------|
| 目录 | `DCIM/Camera` — 公共相机目录，即系统相册中的「相机」 |
| 文件名 | `IMG` + 时间戳后 8 位 + 4 位随机数 + `.jpg`，例如 `IMG064001234821.jpg` |
| 格式 | JPEG |
| 暂存区 | `cacheDir/photo_choice_camera/`，由沙盒清理器负责回收 |

照片通过 MediaStore 的 `IS_PENDING` 两阶段协议写入：只有字节完全写完后该行才对系统相册可见，
因此其他 App 永远不会扫描到半截文件。若拷贝失败，pending 行会被删除，不会残留孤儿记录。

**宿主需要做什么：什么都不用做。** 组件自带 `FileProvider`，authority 为
`${applicationId}.photochoice.fileprovider` — 由宿主的 `applicationId` 推导而来，
因此绝不会与其他接入方冲突。也不需要相机权限：拍照走 `ACTION_IMAGE_CAPTURE`，权限由相机 App 自己持有。

> 若设备上没有安装任何相机 App，点击拍照格会提示，而不会崩溃。
>
> 若你的 App 在自己的 Manifest 中声明了 `<uses-permission android:name="android.permission.CAMERA" />`，
> 那么 Android 会要求该权限被授予后才能使用该 Intent。这是平台规则，不是组件的要求。

拍照完成后：

| 模式 | 行为 |
|------|------|
| 多选 | 照片自动选中。若已达 `selectCount` 上限，会提示「已达上限」，照片依然保留在相册中 |
| 单选 + 启用裁剪 | 直接进入裁剪页；取消裁剪会刷新列表，照片仍可在网格中看到 |
| 单选 + 未启用裁剪 | 仅刷新列表与相册数据，不自动选中 |

用户当前浏览的相册不会被切换，只刷新列表与相册聚合数据。若当前相册不是「相机」，
新照片需切换到该相册后才可见。

`mediaType` 为 `VIDEO` 时，拍照入口会自动隐藏（`effectiveShowCamera`）：
拍出的静态图不可能出现在纯视频列表中，因此干脆不提供该入口。

### 实况图 / Motion Photo

组件把 **Motion Photo、Google Motion Photo、三星动态照片**以及类似的内嵌短视频 JPEG/HEIC
文件统一视为实况图，它们在全流程中仍属于 `IMAGE` 类型。

**网格列表**

- 缩略图左下角显示 **LIVE** 角标。
- **不阻塞分页。** 分页 `load` 只同步读取 MediaStore 的 `IS_MOTION_PHOTO`（API 34+），
  XMP 嗅探完全异步执行。
- **索引持久化。** 扫描结果可跨配置变更与进程死亡存活，不会每次打开都重新嗅探。
- **可视区优先。** 独立的高优先级嗅探通道只覆盖可见区与预取窗口，快速滚动不会被全量历史队列拖住。
- 在不提供 `IS_MOTION_PHOTO` 的 OEM 设备上（部分机型较常见），角标依赖异步 XMP 头尾嗅探，
  首次出现在屏幕上时可能有短暂延迟，通常在数百毫秒以内。

**大图预览**

- LIVE 角标位于顶栏下方。
- **长按**播放内嵌视频，**松手**停止。缩放手势不会误触停止播放。
- 进入预览时后台检测并预加载内嵌 MP4，缓存于 `cacheDir/photo_choice_motion/`。

**开启压缩时**，预览页提供二选一：

- **保留动效**（默认）— 返回原始 URI，不压缩，动效保留。
- **导出静态** — 执行 JPEG 压缩，丢弃动效。

---

## 架构与性能

### 分页加载

**Paging 3 + MediaStore keyset**（`DATE_ADDED` + `_ID`），不做全量 Cursor 扫描。

| 参数 | 取值 |
|------|------|
| 初始加载 | 固定 500 项，向上取整到整行 |
| 每页大小 | `spanCount × 25` 项 |
| 预取距离 | `spanCount × 35` 项（约 3 屏） |
| 内存上限 | **无。** `maxSize` 是刻意不设置的 |

`maxSize` 是被有意移除的：丢弃最远端的页会破坏页面回填，并导致预览页的总数不正确。
分页 `load` 不执行任何 XMP 解析，这正是冷启动与快速滚动保持流畅的原因。

### 实况图检测链路

```
MediaStore 分页加载
    ├─ 同步：API 34+ 批量读取 IS_MOTION_PHOTO → MediaFile.isMotionPhoto
    └─ 异步（非阻塞）：
           ├─ 相册打开：warmAlbumFromMediaStore
           ├─ 可视区通道：可见 + 预取范围，高优先级 XMP 嗅探
           └─ 后台通道：低优先级预取窗口
```

实现位于 `data/motion/`：`MotionPhotoDetector`、`MotionPhotoListEnricher`、
`MotionPhotoXmpSniffer`、`MotionPhotoVideoResolver`。

### 沙盒目录

| 目录 | 内容 | 保留策略 |
|------|------|----------|
| `cacheDir/photo_choice/` | 压缩与裁剪输出 | 24 小时清理；`cleanup()` 会直接清空 |
| `cacheDir/photo_choice_motion/` | 抽取出的实况图短视频 | 24 小时清理，另有 150 MB / 50 个文件的上限 |
| `cacheDir/photo_choice_camera/` | 拍照暂存文件 | 每次拍照后即删；24 小时清理作为兜底 |

### 主要依赖

**Glide** 负责缩略图与预览图 · **Paging 3** 负责网格分页 · **Media3 ExoPlayer** 负责视频与实况图播放 ·
**ViewPager2** 负责预览翻页。

---

## 公开 API 边界

只有以下类型属于受支持、且经过混淆保留的公开 API — 它们正是 `consumer-rules.pro` 中 keep 的部分：

`PhotoChoice` · `PhotoChoice.Builder` · `PhotoChoiceContract` · `PhotoChoiceResult` ·
`config.**` 下的全部内容

其余类虽然按 Kotlin 可见性是 public、也确实可以调用（`CameraHelper`、`CompressHelper`、
`SandboxCleaner`、`DesignTokens` 等），但它们属于**内部实现细节**，不受语义化版本保护，
可能在任何版本中变更或消失。`PermissionHelper` 是唯一例外：它已在上文文档化，供宿主直接使用。

请不要直接启动 `PhotoChoiceActivity`、`PreviewActivity` 或 `CropActivity`。

### 配置安全

非法输入一律做净化处理而非抛异常，因此错误配置不会导致组件崩溃：

| 字段 | 规则 |
|------|------|
| `selectCount` | 在 `1..9` 内则保留，否则**重置为 `1`** |
| `spanCount` | 钳制进 `2..6` |
| `minVideoDurationMs` / `maxVideoDurationMs` | min > max 时自动交换；min 下限为 `0` |
| `minImageSize` / `maxImageSize` | min > max 时自动交换；两者下限均为 `0` |
| `cropConfig.enabled` | 需同时满足单选**且** `MediaType.IMAGE`（`effectiveCropEnabled`） |
| `showCamera` | `MediaType.VIDEO` 模式下强制关闭（`effectiveShowCamera`） |

`PhotoChoiceConfig` 以常量形式暴露了这些边界 — `SELECT_COUNT_MIN` / `SELECT_COUNT_MAX`、
`SPAN_COUNT_MIN` / `SPAN_COUNT_MAX` — 同时提供 `sanitized*` 与 `effective*` 派生属性，
便于你在自己的 UI 中反映最终生效值。

---

## 工程结构

```
photo_choice/
├── photo-choice/                    # 组件库
│   └── src/main/java/com/google/photochoice/
│       ├── PhotoChoice.kt           # Builder 入口、forResult()
│       ├── PhotoChoiceContract.kt   # ActivityResultContract（推荐）
│       ├── PhotoChoiceResult.kt
│       ├── config/                  # PhotoChoiceConfig、MediaType、ThemeMode、Crop/CompressConfig
│       ├── data/
│       │   ├── model/
│       │   └── motion/              # 实况图检测、XMP 嗅探、短视频抽取
│       ├── ui/
│       │   ├── grid/
│       │   ├── album/
│       │   ├── crop/                # CropActivity
│       │   ├── preview/             # PreviewActivity、长按播放实况
│       │   └── widget/
│       ├── util/                    # PermissionHelper、CameraHelper、CompressHelper、SandboxCleaner
│       └── viewmodel/
├── sample/                          # 覆盖全部配置项的示例应用
├── docs/
│   ├── demo.mp4                     # 演示视频
│   ├── demo-poster.png              # 视频封面（浅色 / 深色）
│   ├── hero-light.png               # README 头图（浅色 / 深色）
│   ├── qr-sample-apk.png            # 示例 APK 二维码
│   └── assets/                      # 生成上述全部素材
├── CHANGELOG.md
└── README.md                        # 另有 7 个语种的翻译
```

### 构建与校验

```bash
./gradlew :photo-choice:assembleDebug
./gradlew :sample:installDebug
./gradlew test
./gradlew lint
```

README 的配图与演示视频都是程序生成的：画面中的手机界面是示意图，
因此仓库里不会混入任何真实相册内容。

```bash
python docs/assets/make_assets.py       # header image, video poster, QR code
python docs/assets/make_demo_video.py   # the walkthrough video itself
python docs/assets/verify_readmes.py    # structural checks across all 8 READMEs
```

---

## 接入检查清单

- [ ] 已添加依赖 — JitPack 或 `implementation(project(":photo-choice"))`
- [ ] 宿主 Manifest 中已声明媒体读取权限
- [ ] 启动前已通过 `PermissionHelper` 申请运行时权限
- [ ] 已选定启动方式 — **`PhotoChoiceContract`**（抗进程死亡）或 `forResult` 回调
- [ ] 已区分处理 `null`（取消）与 `PhotoChoiceResult`（成功）
- [ ] 在**消费完**裁剪/压缩产物之后才调用 `PhotoChoice.cleanup(context)`
- [ ] 实况图 + 压缩场景下，已理解**保留动效 / 导出静态**的取舍

---

## 限制与说明

- 数据源仅为**公共 MediaStore 媒体**，不包含私有或隐藏目录。
- UI 与主题色不可自定义，仅支持 `ThemeMode` 的浅色 / 深色 / 跟随系统。
- 视频时长筛选只影响列表展示，不会改动磁盘上的文件。
- `MediaType.ALL` 与多选模式下裁剪不可用。
- 当 `IS_MOTION_PHOTO` 可用时（API 34+）LIVE 角标近乎即时；缺少该数据库字段的 OEM 设备上会有短暂延迟。
  预览长按仍会通过完整检测（含 XMP）识别出未被标记的实况图。

## 问题反馈

提交 issue 时请附上 **Android 版本、设备型号、配置代码片段、期望行为与实际行为**。
若是实况图相关问题，请一并说明系统相册是否将该文件识别为实况图。
