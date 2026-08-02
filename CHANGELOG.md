# Changelog

本文件记录 PhotoChoice 的所有重要变更。

版本遵循 [语义化版本](https://semver.org/lang/zh-CN/)（`主版本.次版本.修订号`）。

---

## [1.1.0] - 2026-08-02

修复拍照功能完全不可用的问题，并重做拍照落库链路。

### 修复

- **拍照后照片丢失（严重）**：旧实现向系统相机传递 `IS_PENDING=1` 的 MediaStore Uri。MediaProvider 对 pending 行在 SQL 层按 `owner_package` 过滤，URI 授权无法绕过该限制，系统相机 `openOutputStream` 抛 `FileNotFoundException`，照片从未写入磁盘——表现为拍照后列表与系统图库均看不到照片，只残留空目录。改为「私有临时文件 + FileProvider 交给相机 → 库自身复制进公共目录并发布」，落库全程由库掌控，不依赖各 ROM 相机对 MediaStore Uri 的实现差异。
- **相册列表不刷新**：拍照后未重新加载相册聚合数据，导致相册下拉中不出现新建的"相机"相册，计数与封面也不更新。
- **无相机应用时静默失败**：`ActivityNotFoundException` 被吞掉，用户点击相机 tile 毫无反馈；现给出明确提示。
- **部分 ROM 误判为取消**：某些机型相机写完照片仍返回 `RESULT_CANCELED`。成功判据改为「临时文件是否有内容」，不再仅依赖 `resultCode`。
- **体积过滤误伤新照片**：落库时未写入 `SIZE`，宿主配置 `minImageSize` 时刚拍的照片会被列表 SQL 条件筛掉；现显式写入。
- **落库中断残留孤儿记录**：字节复制失败时回滚删除已插入的 MediaStore 行，不再留下 0 字节记录（系统相册显示为损坏图）。

### 变更

- **拍照存储位置**：由 `Pictures/PhotoChoice` 改为公共相机目录 **`DCIM/Camera`**，与系统相机一致，拍摄结果直接归入"相机"相册。
- **拍照文件命名**：`IMG` + 时间戳后八位 + 四位随机数 + `.jpg`，例如 `IMG064001234821.jpg`。
- **拍照后自动选中（仅多选）**：多选模式下拍摄结果自动加入已选，达到 `selectCount` 上限时给出提示。单选模式不自动选中——该模式网格无 checkbox、无"已选中"中间态，自动选中会展开用户无法取消的底部栏。
- **拍照后不切换相册**：仅刷新当前列表与相册数据，不改变用户正在浏览的相册。若当前相册非"相机"，新照片需切换后可见。
- **单选 + 裁剪开启时拍照直接进裁剪页**，与点击网格已有照片的行为一致；取消裁剪时回补刷新列表，使照片可见。

### 内部

- 新增 `FileProvider` 声明，authority 为 `${applicationId}.photochoice.fileprovider`，用宿主 `applicationId` 拼接，多集成方之间不冲突。**宿主无需任何额外配置**。
- 拍照临时目录 `photo_choice_camera` 纳入 `SandboxCleaner` 的 24 小时 TTL 与 `cleanup()` 清理范围；正常流程下临时文件在落库后立即删除。
- 新增 `MediaRepository.loadMediaById()`，按 id 精确取回刚落库的媒体；刻意不套用宿主的类型/体积/时长过滤条件，避免"拍了照却选不上"。
- 拍照中间态（临时文件路径、裁剪来源、落库 id）随 `onSaveInstanceState` 持久化，跳转相机/裁剪页期间进程被回收后仍可正确恢复。
- 新增 `CameraFileNamingTest`，锁定文件名格式、补零与越界取模行为。

---

## [1.0.1] - 2026-07-28

首个正式发布版本。面向宿主 App 提供开箱即用的 Android 相册选择器组件，通过 Builder API + `PhotoChoiceContract` 接入，无需直接启动内部 Activity。

### 功能

- **媒体选择**：仅图片 / 仅视频 / 图片+视频三种模式；单选或多选（`selectCount` 1–9）。
- **相册切换**：按 MediaStore 目录聚合，下拉切换相册。
- **网格**：可配置列数（2–6），正方形缩略图，基于 Paging 3 的 keyset 分页加载，`load` 不做 XMP 解析，冷启动与快滑流畅。
- **滚动日期条**：滚动时显示当前可见区域日期，悬浮胶囊从右侧滑入 / 滑出。
- **拍照入口**：可选首格相机 tile，拍摄结果写入系统相册。
- **大图预览**：全屏左右滑动，视频内嵌播放。
- **实况图 / Motion Photo**：识别 Motion Photo / Google Motion Photo / Samsung 动态照片，网格 LIVE 角标 + 预览长按播放内嵌短视频；检测结果常驻索引，跨配置变更与进程死亡持久保留。
- **单图裁剪**：单选 + 图片模式下进入独立 `CropActivity`，支持多种宽高比；可选输出尺寸上限（`CropConfig.maxWidth/maxHeight` 等比缩放）。
- **压缩**：完成时对图片做等比缩放 + JPEG 质量压缩（默认最长边 1280、起始质量 80、体积上限约 1.5MB 自适应递减）；视频、GIF、保留动效的实况图不压缩。
- **主题**：浅色 / 深色 / 跟随系统，per-Activity 生效，不改写宿主全局。
- **双轨启动 API**：推荐 `PhotoChoiceContract`（无静态状态，抗 Activity 重建与进程死亡）；兼容 `forResult` 回调（旧轨）。

### 稳定性与正确性

- **配置防御性规整**：`selectCount`、`spanCount`、视频时长、图片体积等越界入参一律回退修正而非抛异常，宿主传参失误不导致 Crash。
- **无效组合静默降级**：视频 / 多选模式下裁剪自动关闭（`effectiveCropEnabled`）；VIDEO 模式下相机 tile 自动隐藏（`effectiveShowCamera`），避免"拍了照却看不到"的断裂路径。
- **进程死亡安全**：Contract 模式配置经 Intent Extra 传递、结果经 `setResult()` 回传，均由系统托管；回调模式带优雅降级检测。
- 相机拍照落库补齐 `IS_PENDING` 两阶段发布，避免半成品文件被并发扫描读取。
- 裁剪读取 View/Matrix 移回主线程，消除 IO 线程竞争并加防崩兜底。
- 修复 `CompressHelper` 中 `inJustDecodeBounds=true` 导致提前返回 `null` 的压缩失效问题。

### 发布交付

- 随 AAR 下发 `consumer-rules.pro`，宿主开启 R8/混淆时自动合并 keep 规则，保护公开 API 与经 Intent 序列化传递的配置对象。

### 已知限制

- 数据源为 MediaStore 公共媒体，不含私有/隐藏目录。
- UI 与主题色不对外开放自定义，仅 `ThemeMode` 三档。
- 视频时长过滤仅影响列表展示，不改变磁盘原始文件。
- 部分未写入 `IS_MOTION_PHOTO` 的 OEM 机型，LIVE 角标首次进入视口时可能有极短延迟。

[1.1.0]: #110---2026-08-02
[1.0.1]: #101---2026-07-28
