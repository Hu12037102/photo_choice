# PhotoChoice

[English Documentation](README.md) | [日本語ドキュメント](README.ja.md) | [한국어 문서](README.ko.md) | [Documentation en français](README.fr.md) | [Documentación en español](README.es.md) | [الوثائق العربية](README.ar.md) | [Документация на русском](README.ru.md)

Android 相册选择器组件：网格多选、相册切换、大图预览、拍照入口、单图裁剪与可选压缩，并支持 **实况图 / Motion Photo** 识别与预览播放。通过 **Builder 链式 API** 接入，无需直接启动内部 Activity。

- **包名**：`com.google.photochoice`
- **最低 SDK**：29（Android 10，Scoped Storage，无需写存储权限即可读公共媒体）
- **目标 SDK**：36
- **语言**：Kotlin
- **许可证**：[Apache License 2.0](LICENSE)

---

## 功能概览

| 能力 | 说明 |
|------|------|
| 媒体类型 | 仅图片 / 仅视频 / 图片+视频 |
| 选择模式 | 单选 / 多选（`selectCount` 1–9） |
| 相册 | 按 MediaStore 目录聚合，下拉切换相册 |
| 网格 | 可配置列数（2–6），正方形缩略图，Paging 3 分页加载 |
| 滚动日期条 | 滚动时显示当前可见区域日期 |
| 拍照 | 可选首格相机入口（写入系统相册） |
| 预览 | 大图预览、左右滑动；视频内嵌播放（点击播放，播放中点屏幕仅切换标题栏/导航栏） |
| 实况图 | 网格 LIVE 角标、预览长按播放内嵌短视频 |
| 裁剪 | 单选 + 图片模式下可启用独立 `CropActivity` |
| 压缩 | 可选完成时 JPEG 尺寸+质量压缩；实况图可保留动效或导出静态图 |
| 主题 | 浅色 / 深色 / 跟随系统（per-Activity 模式，不全局改写宿主） |
| 启动方式 | 双轨 API：**`PhotoChoiceContract`**（推荐，无静态状态）或 **`forResult`** 回调 |
| 进程死亡安全 | Contract 模式自动抗 Activity 重建与进程死亡；回调模式有优雅降级检测 |

### 单选与多选差异

| 模式 | 网格 UI | 交互 |
|------|---------|------|
| 多选（`selectCount > 1`） | 显示圆形 checkbox 与选中序号 | 点 checkbox 切换选中；点缩略图进入预览 |
| 单选（`selectCount = 1`） | **隐藏** checkbox / 序号 / 禁用蒙层 | 点缩略图进入预览或裁剪（若启用） |

---

## 实况图 / Motion Photo

组件将 **Motion Photo、Google Motion Photo、Samsung 动态照片** 等内嵌短视频的 JPEG/HEIC 统一视为实况图（仍为 `IMAGE` 类型）。

### 网格列表

- 缩略图左下角展示 **LIVE** 角标。
- **不阻塞列表加载**：分页 `load` 仅同步读取 MediaStore `IS_MOTION_PHOTO`（API 34+）；XMP 快速筛查在后台异步进行。
- **常驻索引**：检测结果跨配置变更与进程死亡持久保留，每次打开无需重复嗅探。
- **视口优先**：独立高优先级嗅探通道，仅处理可见区 + 预取窗口，快滑时不再被历史页全量队列阻塞。
- 国产 OEM 若未写入 `IS_MOTION_PHOTO`，角标依赖 XMP 头/尾筛查，首次出现在屏幕时可能有极短延迟（通常数百毫秒内）。

### 大图预览

- 标题栏下方显示 LIVE 徽标。
- **长按** 播放内嵌短视频，**抬手** 停止；缩放/多指手势不会误触停播。
- 进入预览页后后台检测并预加载内嵌 MP4（缓存于 `cacheDir/photo_choice_motion/`）。

### 压缩与导出

开启 `CompressConfig` 时，预览页底部可切换 **保留实况 / 导出静态图**：

- **保留实况**（默认）：回传原图 URI，不压缩。
- **导出静态图**：按普通 JPEG 压缩，丢弃动效。

---

## 快速开始

### 1. 引入模块

在宿主工程的 `settings.gradle.kts` 中纳入本仓库（或拷贝 `photo-choice` 模块），例如：

```kotlin
include(":photo-choice")
```

在 **Application 或业务模块** 的 `build.gradle.kts` 中：

```kotlin
dependencies {
    implementation(project(":photo-choice"))
}
```

> 当前以 **源码模块** 方式集成；若你方已发布 Maven 坐标，将 `implementation(project(":photo-choice"))` 替换为对应依赖即可。

### 2. 声明权限

库模块已在 `photo-choice` 的 Manifest 中声明读取媒体权限；**宿主 App 仍需在自身 Manifest 中声明相同权限**，并在运行时向用户申请。

| Android 版本 | 权限 |
|--------------|------|
| API 34+ | `READ_MEDIA_IMAGES`、`READ_MEDIA_VIDEO`（按 `mediaType` 实际需要申请）；`READ_MEDIA_VISUAL_USER_SELECTED` 已声明，部分授权视为可用 |
| API 33 | `READ_MEDIA_IMAGES`、`READ_MEDIA_VIDEO`（按 `mediaType` 实际需要申请） |
| API 29–32 | `READ_EXTERNAL_STORAGE` |

可使用库提供的 `PermissionHelper` 获取权限列表与是否已授权：

```kotlin
import com.google.photochoice.util.PermissionHelper

if (PermissionHelper.hasMediaPermission(context)) {
    openPhotoChoice()
} else {
    requestPermissionLauncher.launch(PermissionHelper.requiredMediaPermissions())
}
```

参考实现见仓库 **`sample`** 模块中的 `MainActivity`。

### 3. 启动选择器（推荐：Contract 模式）

使用 `ActivityResultContract` 接入，**自动抗进程死亡与 Activity 重建**：

```kotlin
import com.google.photochoice.PhotoChoiceContract
import com.google.photochoice.PhotoChoice
import com.google.photochoice.config.MediaType

val launcher = registerForActivityResult(PhotoChoiceContract()) { result ->
    if (result == null) {
        // 用户取消
        return@registerForActivityResult
    }
    result.uris.forEach { uri ->
        // 使用 content:// 或 file:// URI
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

**Contract 模式**：配置通过 Intent Extra 传递，结果通过 `setResult()` 回传——均由系统托管，天然抗 Activity 重建与进程死亡。**生产环境首选。**

### 4. 备选：回调 API（旧轨）

在 **`FragmentActivity`**（或 `AppCompatActivity`）中调用：

```kotlin
import com.google.photochoice.PhotoChoice
import com.google.photochoice.config.MediaType

PhotoChoice.with(this)
    .selectCount(9)
    .mediaType(MediaType.IMAGE)
    .spanCount(4)
    .showCamera(true)
    .forResult(this) { result ->
        if (result == null) {
            // 用户取消（返回键 / 未点完成即退出）
            return@forResult
        }
        result.uris.forEach { uri ->
            // 使用 content:// 或 file:// URI
        }
    }
```

**注意：** 回调 API 内部使用静态字段传参，**不能**跨宿主 Activity 重建与进程死亡。选择期间宿主被重建时回调仍指向旧实例。对可靠性有要求请使用 Contract 模式。

---

## 返回结果

```kotlin
data class PhotoChoiceResult(
    val uris: List<Uri>,   // 选中媒体的 URI 列表（顺序与选择顺序一致）
    val paths: List<String> // 尽力解析的本地路径；无法解析时为 URI 字符串
)
```

| 媒体类型 | 未开启压缩 | 开启压缩 |
|----------|-----------|----------|
| 静态图片 | `content://` MediaStore URI | `file://` 压缩后 JPEG（`cacheDir/photo_choice/compress_*.jpg`） |
| 视频 | `content://` MediaStore URI | 不压缩（视频不参与压缩逻辑） |
| GIF | `content://` MediaStore URI | 不压缩（压缩会丢失动画） |
| 实况图（保留动效） | `content://` MediaStore URI | 不压缩（保留动效） |
| 实况图（导出静态） | N/A | `file://` 压缩后 JPEG（`cacheDir/photo_choice/compress_*.jpg`） |

使用完毕后若不再需要缓存文件，可调用：

```kotlin
PhotoChoice.cleanup(context)
```

会清理组件沙盒目录中超过 24 小时的临时文件（亦可在业务处理完图片后主动调用）。

---

## 配置项（Builder API）

| 方法 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `selectCount` | `Int` | `9` | 可选数量，范围 `1..9`；`1`=单选、`>1`=多选；超出区间自动回落到 `1` |
| `mediaType` | `MediaType` | `IMAGE` | `IMAGE` / `VIDEO` / `ALL` |
| `spanCount` | `Int` | `3` | 网格列数，自动 clamp 到 **2–6** |
| `showCamera` | `Boolean` | `true` | 是否在网格首格显示拍照入口 |
| `minImageSize` | `Long` | `0` | 图片体积下限（字节），过滤图标类小图；仅作用于图片 |
| `maxImageSize` | `Long` | `Long.MAX_VALUE` | 图片体积上限（字节），过滤超大图；仅作用于图片 |
| `minVideoDuration` | `Long` | `0` | 视频最短时长（毫秒）；若 > maxVideoDuration 自动交换 |
| `maxVideoDuration` | `Long` | `60000` | 视频最长时长（毫秒）；若 < minVideoDuration 自动交换 |
| `themeMode` | `ThemeMode` | `FOLLOW_SYSTEM` | `LIGHT` / `DARK` / `FOLLOW_SYSTEM`（per-Activity 模式，不影响宿主全局） |
| `cropConfig` | `CropConfig` | 见下 | 裁剪配置 |
| `compressConfig` | `CompressConfig` | 见下 | 完成时压缩配置 |

Contract 模式可直接获取配置对象：

```kotlin
val config = PhotoChoice.with(context)
    .selectCount(1)
    .buildConfig()  // 直接返回 PhotoChoiceConfig
```

### 裁剪 `CropConfig`

仅在 **`selectCount = 1`** 且 **`mediaType` 含图片** 时，用户选图后会进入裁剪页（独立 `CropActivity`）。
裁剪在多选或视频模式时自动静默降级为不裁剪（`effectiveCropEnabled` 守卫）。

```kotlin
import com.google.photochoice.config.CropConfig
import com.google.photochoice.config.CropAspectRatio

.cropConfig(
    CropConfig(
        enabled = true,
        aspectRatio = CropAspectRatio.SQUARE, // ORIGINAL, SQUARE, RATIO_3_4, RATIO_4_3, RATIO_9_16, RATIO_16_9
    )
)
```

单选且启用裁剪时，选图即走裁剪流程，完成后直接回调结果并关闭选择器。

### 压缩 `CompressConfig`

在用户点击「完成」后、回调前对**图片**做等比缩放 + JPEG 压缩；视频、GIF、保留动效的实况图不压缩。实况图默认保留动效，可在预览页切换为静态图后再压缩。

**默认策略（对齐微信朋友圈常见档位）：**

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `maxWidth` / `maxHeight` | `1280` | 最长边限制 |
| `quality` | `80` | JPEG 起始质量 |
| `maxFileSizeBytes` | `1572864`（约 1.5MB） | 超限则递减质量；`0` 表示不限制体积 |
| `minQuality` | `50` | 体积迭代下限 |
| `qualityStep` | `10` | 每次递减步长 |

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
        qualityStep = 10
    )
)
```

---

## 常见场景示例

### 多图选择（最多 9 张图）

```kotlin
PhotoChoice.with(activity)
    .selectCount(9)
    .mediaType(MediaType.IMAGE)
    .spanCount(4)
    .showCamera(true)
    .forResult(activity) { result -> /* ... */ }
```

### 头像（单选 + 正方形裁剪）

```kotlin
PhotoChoice.with(activity)
    .selectCount(1)
    .mediaType(MediaType.IMAGE)
    .cropConfig(CropConfig(enabled = true, aspectRatio = CropAspectRatio.SQUARE))
    .compressConfig(CompressConfig(enabled = true))
    .forResult(activity) { result -> /* ... */ }
```

### 仅选视频（最长 60 秒）

```kotlin
PhotoChoice.with(activity)
    .selectCount(1)
    .mediaType(MediaType.VIDEO)
    .showCamera(false)
    .maxVideoDuration(60_000L)
    .forResult(activity) { result -> /* ... */ }
```

### 图片 + 视频混合

```kotlin
PhotoChoice.with(activity)
    .selectCount(9)
    .mediaType(MediaType.ALL)
    .maxVideoDuration(60_000L)
    .forResult(activity) { result -> /* ... */ }
```

---

## Demo 工程

仓库包含 **`sample`** 应用模块，用于演示全部配置项与快捷场景：

```bash
./gradlew :sample:installDebug
```

运行 **PhotoChoice 示例** App：可调整参数后打开选择器，并在结果区点击缩略图预览已选媒体。

---

## 架构与性能

### 分页加载

采用 **Paging 3 + MediaStore keyset 分页**（`DATE_ADDED` + `_ID`），避免全量 Cursor 遍历：

| 参数 | 说明（以 `spanCount=3` 为例） |
|------|-------------------------------|
| 首屏加载 | 约 15 行 × 列数 ≈ 45 条 |
| 单页大小 | 约 25 行 × 列数 ≈ 75 条 |
| 预取距离 | 约 35 行 × 列数 ≈ 105 条（约 3 屏） |
| 内存上限 | 约 900–1200 条元数据（自动丢弃最远页） |

分页 `load` **不做 XMP 文件解析**，保证冷启动与快滑翻页流畅。

### 实况图检测链路

```
MediaStore 分页 load
    ├─ 同步：API 34+ 批量读 IS_MOTION_PHOTO → MediaFile.isMotionPhoto
    └─ 异步（不阻塞 load）：
           ├─ 相册打开：warmAlbumFromMediaStore 预热 DB 标记
           ├─ 视口通道：可见区 + 预取窗口，高优先级 XMP 快速筛查
           └─ 后台通道：低优先级预取窗口补扫
```

相关模块：`data/motion/`（`MotionPhotoDetector`、`MotionPhotoListEnricher`、`MotionPhotoXmpSniffer`、`MotionPhotoVideoResolver`）。

### 主要依赖

- **Glide**：缩略图与预览图加载
- **Paging 3**：网格分页
- **Media3 ExoPlayer**：预览页视频 / 实况图内嵌视频播放
- **ViewPager2**：预览页左右滑动

---

## 配置安全

所有对外配置参数均做了**防御性规整**，宿主传参失误不会导致 Crash：

| 字段 | 规整方式 |
|------|---------|
| `selectCount` | clamp 到 `1..9`；越界时回退为 `1` |
| `spanCount` | clamp 到 `2..6` |
| `minVideoDurationMs` / `maxVideoDurationMs` | 若 min > max 自动交换；min 下限 `>= 0` |
| `minImageSize` / `maxImageSize` | 若 min > max 自动交换；min 下限 `>= 0` |
| `cropConfig.enabled` | 视频模式或多选时自动降级为不裁剪（`effectiveCropEnabled` 守卫） |

---

## 工程结构

```
photo_choice/
├── photo-choice/              # 库模块（对外 API：PhotoChoice）
│   └── src/main/java/com/google/photochoice/
│       ├── PhotoChoice.kt     # Builder 入口，forResult()
│       ├── PhotoChoiceContract.kt     # ActivityResultContract（推荐方式）
│       ├── config/            # PhotoChoiceConfig, CropConfig, CompressConfig, …
│       ├── data/              # MediaRepository, AlbumRepository, PagingSource
│       │   └── motion/        # 实况图检测、XMP 筛查、内嵌视频提取
│       ├── viewmodel/         # PhotoChoiceViewModel, SelectionManager, GridPaging
│       └── ui/
│           ├── PhotoChoiceActivity.kt
│           ├── grid/          # MediaGridFragment, MediaGridAdapter
│           ├── album/         # 相册下拉
│           ├── crop/          # CropActivity
│           └── preview/       # PreviewActivity, 实况长按播放
├── sample/                    # 示例 App
├── PRD.md                     # 产品规格（内部参考）
├── README.md                  # English documentation
├── README.zh-CN.md            # 本文档
├── README.ja.md               # 日本語ドキュメント
├── README.ko.md               # 한국어 문서
├── README.fr.md               # Documentation en français
├── README.es.md               # Documentación en español
├── README.ar.md               # الوثائق العربية
└── README.ru.md               # Документация на русском
```

---

## 构建与校验

```bash
# 编译库
./gradlew :photo-choice:assembleDebug

# 编译并安装示例
./gradlew :sample:installDebug

# 单元测试
./gradlew test

# Lint
./gradlew lint
```

---

## 接入检查清单

- [ ] 已 `implementation(project(":photo-choice"))`（或等价 Maven 依赖）
- [ ] 宿主 Manifest 已声明媒体读取权限
- [ ] 启动前已申请并获得权限（可参考 `PermissionHelper`）
- [ ] 选择接入方式：**`PhotoChoiceContract`**（推荐，抗进程死亡）或 `forResult` 回调
- [ ] 正确处理 `result == null`（取消）与 `PhotoChoiceResult`（成功）
- [ ] 若启用压缩/裁剪，按需在处理完成后调用 `PhotoChoice.cleanup(context)`
- [ ] 若业务需实况图动效，压缩场景下注意预览页「保留实况 / 导出静态图」语义

---

## 限制与说明

- 数据源为 **MediaStore 公共媒体**，不包含私有/隐藏目录。
- 组件 UI 与主题色不对外开放自定义，仅支持 `ThemeMode` 三档。
- 内部 Activity（`PhotoChoiceActivity`、`PreviewActivity`、`CropActivity`）**不应**由业务方直接启动。
- 视频时长过滤仅影响列表展示，不改变系统相册中的原始文件。
- **实况图角标**：
  - API 34+ 且 MediaStore 已写入 `IS_MOTION_PHOTO` 的机型，角标可接近即时显示。
  - 未写入 DB 标记的机型（常见国产 OEM），角标依赖 XMP 异步筛查，首次进入视口时可能有短暂延迟。
  - 预览页长按播放仍可通过完整检测（含 XMP）识别未标记的实况图。

---

## 问题反馈

请在仓库 Issue 中说明：**Android 版本、设备型号、配置代码片段、期望与实际行为**。复现步骤越完整越便于排查；涉及实况图时请注明是否为 Motion Photo / 动态照片及系统相册是否识别为实况。
