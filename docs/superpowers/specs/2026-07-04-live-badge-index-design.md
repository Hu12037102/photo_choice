# 相册列表 Live 角标"秒显"优化设计

- 日期：2026-07-04
- 模块：`photo-choice`（Android Library）
- 主题：列表快滑时实况图角标同步显示（对齐微信体验）

---

## 1. 背景与问题

相册网格列表中，实况图（Motion Photo / Live Photo）在 item 左下角显示一个 Live 角标。当前问题：

- **快滑时角标"迟到弹出"**：item 已经滚到屏幕上很久，角标才姗姗出现，而不是与缩略图同步显示。
- 参照微信：Live 角标与 item **同帧出现**，滑到哪一张都立刻可见，无滞后、无弹跳。

用户明确排除了"只依赖读取系统数据库"的做法：系统 `MediaStore.IS_MOTION_PHOTO`（API 34+）在部分机型（含用户当前设备、大量国产 OEM）**根本不落库**，兜底路径失效。

## 2. 根因分析

当前判定是**在滚动热路径上现算**，且依赖磁盘 I/O：

```
快滑一屏（单个 item 在屏约 50ms）
  │
  ├─ onBindViewHolder ──► bindLivePhotoIndicator
  │      └─ 查内存 LruCache：MISS（首次浏览该图）
  │         └─ onRequestMotionEnrich ─► priorityInbox（异步通道）
  │
  │   item 已经滑走 ◄───────────────────────┐
  │                                          │
  │      开 fd → 读 72KB 头尾 → 匹配 XMP 标记 → 切主线程 notifyMotionBadges
  │           （几十毫秒后才回来，角标"迟到弹出"）
```

三层瓶颈：

1. **判定在滚动热路径**：`MotionPhotoXmpSniffer` 每张要 `openFileDescriptor` + 读 48KB 头 + 24KB 尾。
2. **结果只存内存**：`MotionPhotoDetector.cache` 是 `LruCache`，冷启动 / 切相册 / 进程重建后清零，每次都要重嗅。
3. **系统 DB 兜底失效**：`warmAlbumFromMediaStore` 在无 `IS_MOTION_PHOTO` 的机型返回空集。

**微信为什么能同步显示**：它在 bind **之前**就已知结果——本地维护一份持久化"实况图索引"，滑到任意位置都是内存 O(1) 查表，自然与缩略图同帧出现。

**核心解法**：把判定从"滚动时现算"改为"**绑定前已知**"——自建持久化索引 + 进相册后台预建，让 bind 时 O(1) 命中；对极少数首次仍未知的项，用**文件名启发式**零 I/O 先显示、异步校正，并对异步到达者加淡入动效，让偶发的到达看起来是"渐现"而非"跳出"。

## 3. 目标与非目标

**目标**

- 已预建 / 已浏览过的实况图：滑到即显，与缩略图同帧，**跨会话、跨切相册后依然如此**。
- 首次浏览、尚未预建到的项：文件名启发式命中者立即显示；未命中者异步嗅探 + 淡入，且从"角标出现"到"用户注意到"之间感知不到滞后。
- 不引入重依赖（Room 等），不给宿主 App 带来版本冲突。
- 不阻塞分页 load 与滚动主线程；不引入 Crash。

**非目标**

- 不改变实况图的**播放**逻辑（预览页长按播放不在本次范围）。
- 不追求 100% 首次命中——极少数冷门机型命名的项，仍可能有一次淡入，这是可接受的。
- 不做实况图内容的二次编辑 / 导出策略变更。

## 4. 架构总览

### 4.1 判定级联（Decision Cascade）

bind 时按成本从低到高逐级查询，命中即返回，**只有最后一级才碰磁盘**：

```
bindLivePhotoIndicator(item)
  │
  ├─ L0  item.isMotionPhoto            （MediaStore 已标记，DB 层已知）→ 显示
  ├─ L1  内存 LruCache                 （本次会话已嗅过）           → 显示 / 隐藏
  ├─ L2  持久化索引 IndexStore          （历史会话已嗅过，O(1) 内存 Map）→ 显示 / 隐藏
  ├─ L3  文件名启发式 Heuristics        （零 I/O，MVIMG_ / 三星等命名）→ 疑似显示（待校正）
  └─ L4  未知 → 触发异步嗅探（现有 enricher 双通道）+ 结果落 L1/L2 + 淡入
```

L0/L1/L2 是**确定态**，同帧显示；L3 是**疑似态**，立即显示但可能被 L4 校正；L4 是**异步补齐**，淡入呈现。

### 4.2 组件关系

```
                     ┌─────────────────────────────┐
   bind 热路径 ───►  │  MotionPhotoDecision（新）    │  统一级联查询入口
                     │  resolve(item): BadgeState   │
                     └──┬─────┬─────┬─────┬─────────┘
                        │L1   │L2   │L3   │L4
                        ▼     ▼     ▼     ▼
                LruCache  IndexStore  Heuristics  Enricher
                (现有)     (新·持久化)  (新·纯函数)  (现有·增强)
                            │                          │
                            └──────────◄───────────────┘
                              嗅探结果双写：内存 + 持久化

   进相册 ──► AlbumMotionPrebuilder（新）──► 拉全册 image → 分片低优先级嗅探 → 写 IndexStore
```

新增 4 个组件，改造 2 个现有组件，新增 1 个 MediaFile 字段与 1 处 projection。

## 5. 组件详设

### 5.1 MotionPhotoIndexStore（新·持久化索引）

**职责**：把"id → 是否实况图"的判定结果持久化到 App 私有存储，冷启动异步载入内存，提供 O(1) 查询与幂等写入。

**位置**：`data/motion/MotionPhotoIndexStore.kt`，`object` 单例（与 `MotionPhotoDetector` 同包）。

**存储介质**：`context.filesDir/photochoice/motion_index.csv`（用 `filesDir` 而非 `cacheDir`，避免被系统清理导致索引蒸发；即便丢失也能由预建重建，但不希望频繁重建）。

**为什么自建文件而非 Room**：本模块是 library，会被宿主 App 集成。引入 Room 会带来 KSP/编译期依赖、数据库版本迁移、与宿主 Room 版本冲突的风险；而本场景数据结构极简（单表、无关联、无复杂查询），一个追加写文本文件足矣。

**记录格式**（每行一条，CSV）：

```
<id>,<flag>,<size>,<dateAdded>
```

- `flag`：`1`=实况图，`0`=非实况图（**阴性也记录**，避免重复嗅探非实况图）。
- `size` + `dateAdded`：**防 id 复用校验**。MediaStore 的 `_ID` 在媒体删除后可能被新文件复用；查询时比对当前 `MediaFile.size` / `dateAdded`，不一致则视为失效条目，返回 `UNKNOWN` 重新嗅探。

**内存结构**：`ConcurrentHashMap<Long, Entry>`，`Entry(flag, size, dateAdded)`。

**API**：

```kotlin
object MotionPhotoIndexStore {
    /** 冷启动异步载入（在 IO 线程读全量文件构建内存 Map）。幂等。 */
    fun ensureLoaded(context: Context)

    /** O(1) 查询，返回 MOTION / NOT_MOTION / UNKNOWN（含校验失败）。 */
    fun query(item: MediaFile): IndexResult

    /** 写入结果（内存即时生效 + 异步追加落盘，串行化）。 */
    fun put(item: MediaFile, isMotion: Boolean)

    /** 批量写入（预建用，合并一次 flush）。 */
    fun putAll(entries: List<Triple<MediaFile, Boolean, Unit>>)
}
```

**并发与落盘**：单后台协程 + `Channel` 串行化写入，避免预建高并发下的文件竞争。内存 Map 先写（查询立即可见），落盘异步 flush（批量、防抖）。

**文件增长控制**：append-only 会有重复行（同 id 多次写）。加载时"后写覆盖先写"。当行数超过阈值（如 20000）或重复率过高时，触发一次 compact（内存 Map 全量重写文件）。

**容错**：单行解析失败跳过该行不影响整体；文件损坏 / 不存在时视为空索引，由预建与浏览逐步重建。全部 I/O `runCatching` 包裹，任何异常降级为"索引未命中"，绝不 Crash。

### 5.2 MotionPhotoHeuristics（新·文件名启发式）

**职责**：bind 时**零 I/O**，仅凭文件名 / mime 特征给出"疑似实况图"的快速判断，用于首次未预建时立即显示角标。

**位置**：`data/motion/MotionPhotoHeuristics.kt`，纯函数 `object`。

**前置改动**：`MediaFile` 增加 `displayName: String` 字段；`MediaRepository.PROJECTION` 增加 `MediaStore.Files.FileColumns.DISPLAY_NAME` 并在 `ColumnIndex.toMediaFile` 填充。当前 `uri` 是 content uri 不含文件名，必须显式查询 `DISPLAY_NAME`。

**判定规则**（**保守优先，宁漏不错**，降低误判回撤概率）：

```kotlin
fun guess(item: MediaFile): Boolean {
    if (item.type != IMAGE) return false
    val name = item.displayName.uppercase()
    return name.startsWith("MVIMG_")        // Google 相机 Motion Photo
        || name.startsWith("MV")             // 部分机型 Motion 前缀
        || name.contains("MOTIONPHOTO")      // 通用命名
        || name.contains("LIVE")             // 部分相机 Live 命名
        // 三星实况：文件名无强特征，主要靠 L2/L4 兜底，不在启发式误报
}
```

规则集中管理、可按机型灰度扩充。命中即"疑似显示"，最终以 L4 嗅探结果为准（校正）。

### 5.3 MotionPhotoDecision（新·级联入口）

**职责**：封装 5.1 节的判定级联，作为 bind 时唯一查询入口，返回明确的角标状态，避免 Adapter 里散落多处判断。

**位置**：`data/motion/MotionPhotoDecision.kt`。

```kotlin
enum class BadgeState {
    CONFIRMED_MOTION,   // L0/L1/L2 确认是 → 立即显示（同帧，无动画）
    CONFIRMED_NOT,      // 确认否 → 隐藏
    HEURISTIC_MOTION,   // L3 疑似是 → 立即显示（待 L4 校正）
    UNKNOWN             // L4 待嗅探 → 先隐藏，触发异步
}

fun resolve(item: MediaFile): BadgeState
```

`MotionPhotoDetector` 现有的 `isMotionPhotoCached` / `hasCachedResult` 逻辑并入此级联（L1）；新增 L2 查 `IndexStore`、L3 查 `Heuristics`。嗅探结果回调时**双写** L1 内存 + L2 持久化。

### 5.4 AlbumMotionPrebuilder（新·后台全量预建）

**职责**：进相册后，后台低优先级把**整册**所有图片嗅一遍，结果写入 IndexStore。跑完后该相册滑到任意位置都是 L2 O(1) 命中。

**位置**：`data/motion/AlbumMotionPrebuilder.kt`。

**核心定位：预建是"优化项"，不是"正确性依赖"**。因为 L3（启发式）+ L4（异步嗅探）已覆盖任意 item，无论预建跑到哪、跑没跑完，角标都能显示。这条性质是本组件所有性能治理的前提——**预建可以做得极度"礼貌"，让路给一切用户可见的操作，慢一点、甚至这次没跑完都没关系**（下次进入从 IndexStore 断点续建）。

**流程**：

```
进相册 / 切相册
  │
  ├─ 1. 查整册 image 的 (id, uri, size, dateAdded, displayName)  ← 轻查询，仅这几列
  ├─ 2. L0 批量：先用 IS_MOTION_PHOTO 一次性 DB 查（零 per-file I/O）给能分类的分类
  ├─ 3. 过滤掉 IndexStore / L0 已有有效结果的 id（跳过已知，最小化真实读盘集）
  ├─ 4. 剩余项分片（每片 N 张），后台低优先级、可暂停、可中断地逐片 XMP 快嗅
  └─ 5. 每片结果 putAll 到 IndexStore；命中实况图的 id 回调刷新可见角标
```

**优先级隔离**：复用现有"视口优先通道 vs 后台通道"设计——预建走**后台低优先级**，永远不阻塞视口的紧急嗅探（用户当前正在看的窗口始终最快响应）。

**可中断**：切相册 / 退出页面时取消当前预建 Job（`viewLifecycleOwner.lifecycleScope` 承载）。下次进入从 IndexStore 已有记录继续，不重复。

详细的系统性能治理见 [§8 系统性能治理](#8-系统性能治理与预算)。

### 5.5 角标交互状态机（淡入 + 校正）

**目标**：确定态同帧显示（无动画）；异步到达者淡入（渐现，非跳出）；启发式误判者淡出回撤（极少）。

`bindLivePhotoIndicator` 按 `BadgeState` 驱动：

| 状态 | bind 首帧表现 | L4 嗅探回来后 |
| --- | --- | --- |
| `CONFIRMED_MOTION` | 立即 `VISIBLE`，无动画 | — |
| `CONFIRMED_NOT` | `GONE` | — |
| `HEURISTIC_MOTION` | 立即 `VISIBLE`，无动画 | 确认是→保持；确认否→**淡出隐藏**（150ms） |
| `UNKNOWN` | `GONE`，触发嗅探 | 确认是→**淡入显示**（150ms）；确认否→保持隐藏 |

**淡入 / 淡出实现**：`livePhotoBadge` 现为 `View`，用 `animate().alpha()` 即可（150ms，`DecelerateInterpolator`）。

**payload 区分**：`notifyMotionBadges` 走 `PAYLOAD_MOTION`，在 payload 分支里根据"是否从隐藏变为显示"决定是否播放淡入，避免复用 ViewHolder 时的错误动画。滚动飞速经过时的 item 若已在 confirmed 态则直接显示，不排队动画。

**校正闪烁控制**：启发式规则保守（5.2），绝大多数命中正确，回撤淡出是罕见事件；即便发生，150ms 淡出比"突然消失"更柔和。

## 6. 关键数据流时序

### 6.1 首次进相册

```
进相册
  ├─ IndexStore.ensureLoaded()（IO 异步载入历史索引到内存）
  ├─ warmAlbumFromMediaStore()（API34+ 机型补 DB 标记，无则空跑）
  ├─ 首屏 bind：L0/L1/L2 命中即同帧显示；未命中走 L3 疑似 / L4 视口紧急通道
  └─ 首屏进入 IDLE 后（让路首屏缩略图）→ 启动 AlbumMotionPrebuilder 后台全量预建
```

### 6.2 快速滑动（核心场景）

```
快滑
  ├─ 后台预建：检测到 DRAGGING/SETTLING → 暂停（把磁盘带宽让给缩略图）
  ├─ item 进屏 → bind → MotionPhotoDecision.resolve()
  │     ├─ 已预建区：L2 命中 → 同帧显示 ✅（微信同款体验，纯内存零 I/O）
  │     └─ 未预建区：L3 命中 → 疑似同帧显示；L3 未命中 → L4 视口紧急通道异步 + 淡入
  ├─ 视口优先通道保证"用户正在看的窗口"最快补齐（不受预建暂停影响）
  └─ IDLE 后防抖 ~300ms → 恢复后台预建
```

### 6.3 切相册 / 二次进入

```
切相册
  ├─ 取消上一相册预建 Job
  ├─ 新相册：IndexStore 已有记录 → 直接 L2 命中（二次进入几乎全命中）
  └─ 启动新相册预建（跳过已知，仅补新增）
```

## 7. 边界与异常

- **id 复用**：`size`/`dateAdded` 校验，失效条目重新嗅探。
- **文件被删除 / 无权限**：嗅探 `runCatching` 返回 `false`，写阴性缓存，不 Crash。
- **索引文件损坏**：加载容错，坏行跳过；极端情况整体重建。
- **超大相册（数万张）**：预建分片 + 可中断 + 跳过已知，避免长时间占用 IO；未预建区仍有 L3/L4 兜底，不影响可用性。
- **进程被杀重建**：内存缓存清零，但 IndexStore 落盘仍在，`ensureLoaded` 后 L2 立即恢复命中。
- **多相册共享全局索引**：`_ID` 全局唯一，一个索引文件服务所有相册，切相册命中率高。

## 8. 系统性能治理与预算

> 本节回应"不能为完成业务牺牲系统性能"的硬约束。核心风险**不是内存 / CPU**，而是**磁盘 I/O 及其连锁反应**：全量预建会挨个开 fd 读文件头尾，若不治理会与 Glide 缩略图**抢同一块闪存 I/O**，引发滚动掉帧、发热、耗电、以及 CSV 频繁小写入的**闪存写放大**。治理的总纲是——**预建是优化项而非正确性依赖（见 §5.4），因此可以对一切用户可见操作无条件让路**。

### 8.1 磁盘 I/O 争抢治理（最高优先级）

这是本设计最大的性能风险点，分三重防护：

1. **滑动期暂停预建**：`RecyclerView` 处于 `DRAGGING` / `SETTLING` 时，`AlbumMotionPrebuilder` **挂起**后台分片嗅探，只保留视口紧急通道（用户正在看的窗口）。`IDLE` 后延迟一小段（防抖，约 300ms）再恢复。用户滑动时的磁盘带宽完全让给缩略图，从根源上避免"预建拖累滚动"。
2. **首屏让路**：预建**延迟启动**——等首屏首次进入 `IDLE`（首屏缩略图基本加载完）后再启动，不与冷启动首屏抢 I/O。
3. **并发上限**：后台预建的嗅探并行度**独立且更低**（区别于视口紧急通道的高并行）。视口通道保持"秒响应"的高并行；后台通道限制在低并行，避免打满 I/O 队列深度。

### 8.2 线程与调度

- 后台预建嗅探线程设 `Process.THREAD_PRIORITY_BACKGROUND`（或 `THREAD_PRIORITY_LOWEST`），让 Linux CFS 调度器天然把 CPU 让给 UI / 视口通道。
- 分片之间 `yield()`，避免长时间独占协程调度，保证紧急通道随时插队。

### 8.3 生命周期与耗电 / 发热

- 页面**不可见即停**：`onStop`（`Lifecycle.State < STARTED`）暂停预建，`onStart` 恢复。用户切后台 / 锁屏不空烧 CPU 与 I/O，直接消除"离开页面还在发热耗电"。
- **跳过已知**：预建前先批量 L0（`IS_MOTION_PHOTO` 一次 DB 查）+ 过滤 IndexStore 已有记录，把真实读盘集压到最小；已预建相册二次进入几乎零工作量。
- **断点续建**：进程被杀 / 页面销毁后，已写入 IndexStore 的部分永久有效，下次从断点继续，不重复读盘。

### 8.4 闪存写放大治理

CSV 若"每嗅一条 fsync 一次"，高频小写入会显著磨损闪存并占 I/O。治理：

- **内存先写、落盘后延**：`put` 先更新内存 Map（查询立即可见），落盘走**单写协程 + 批量防抖**——攒够 N 条（如 64）或距上次超过 T 秒（如 2s）或生命周期 `onStop` 时，合并一次追加写。
- **单写协程串行化**：预建高并发嗅探的结果统一进一个 `Channel`，由唯一写协程消费，杜绝文件竞争与多次 open。
- **compact 控体积**：append-only 的重复行在超过阈值（行数 / 重复率）时，用内存 Map 全量重写一次文件，保持加载快、体积小。

### 8.5 单次读盘量治理

- **快嗅优先、命中即返回**：`isMotionPhotoQuick` 先读 48KB 头，头部命中标记（大量实况图的 XMP 在头部）即返回，**不再读尾部 24KB**——实况图的常见情况单次仅 48KB。
- **头尾定位读**：非实况图需读头 + 尾判否，但用 `FileChannel.position` 定点读，不整文件扫描；单条上限 72KB。
- **批量 DB 优先**：能被 `IS_MOTION_PHOTO` 分类的项零 per-file I/O，只有该列缺失的机型 / 项才降级到读盘。

### 8.6 嗅探并发治理（视口紧急通道吞吐）

`quickSniffBatch` 是所有 XMP 嗅探的收口，其并发上限直接决定"首次快滑 / 无 DB 标记机型"时角标的追赶速度——这正是本需求的核心场景。

- **真并发限流**：用 `Semaphore(parallel)` 收口在飞嗅探数，`parallel` 由调用方按通道区分——视口紧急通道 20、后台预建 4。视口通道由此获得快滑追赶所需的高吞吐，预建通道保持低占用。
- **并发上限与通知粒度解耦**：早期实现用嵌套 `chunked` + 逐子批 `awaitAll`，把有效并发钳制成固定小值（无论 `parallel` 传多少），使"紧急通道 20 并发"名存实亡（约慢 5 倍）；改用信号量后二者彻底解耦——每条嗅完立即回调刷新角标，不必攒批。
- **许可证只圈 I/O**：信号量许可证仅圈住昂贵的文件读，拿到结果即释放；角标回调的主线程 hop 在许可证外执行，避免检测爆发时大量结果同时切主线程造成许可证堆积、挤占并发名额。
- **协程数有界**：外层按 `QUERY_BATCH`(100) 分批 `awaitAll`，即使候选上万也最多同时存在 100 个协程对象（其中仅 `parallel` 个在做 I/O，余者廉价挂起），无失控创建。
- **线程安全 & 取消**：结果集用 `ConcurrentHashMap.newKeySet`（回调跨多个 IO 协程写入）；`coroutineScope` 结构化并发保证滑动离开 / 切相册 / onStop 时在飞嗅探随外层取消，`withPermit` 在取消时经 finally 释放许可证。

### 8.7 内存与 CPU

- **内存**：索引 Map 每条约几十字节，数万张量级仅几 MB；`LruCache` 上限不变；预建分片处理，不一次性把整册对象常驻。
- **CPU**：bind 热路径由"可能磁盘 I/O"降为"纯内存 Map 查 + 字符串前缀比较"，微秒级；标记匹配是有限字符串包含，无正则回溯风险（`parseEmbeddedVideoRange` 的正则仅在播放前单条调用，不在列表路径）。

### 8.8 性能预算（验收基线）

| 指标 | 预算 | 依据 |
| --- | --- | --- |
| bind 内角标判定耗时 | < 0.1ms（无磁盘） | 纯内存查 + 字符串比较 |
| 滑动期后台预建 I/O | ≈ 0（暂停） | §8.1 滑动暂停 |
| 单条嗅探读盘量 | ≤ 72KB，实况图常见 48KB | §8.5 |
| 视口紧急通道有效并发 | = 20（不被通知粒度钳制） | §8.6 信号量限流 |
| 索引落盘频率 | ≤ 每 2s 或每 64 条一次 | §8.4 批量防抖 |
| 页面不可见时后台活动 | 0 | §8.3 onStop 停 |
| 滚动帧率 | 不低于无本功能时 | §8.1/8.2 让路 |

### 8.9 兼容性

- **minSdk 29**：`IndexStore` / `Heuristics` 纯文件与字符串操作，全版本可用；不依赖 API34 的 `IS_MOTION_PHOTO`。
- **无新依赖**：不引入 Room / DataStore，`build.gradle.kts` 不变，不给宿主 App 增加体积与冲突面。

## 9. 测试策略

- **单元测试**：
  - `IndexStore` 读写往返、id 复用校验、坏行容错、compact 触发。
  - `Heuristics` 各命名规则命中 / 不命中（含大小写、边界命名）。
  - `MotionPhotoDecision` 级联优先级（L0>L1>L2>L3>L4）与状态映射。
- **仪器 / 手动验证**：
  - 全新安装首次快滑：观察启发式命中率与淡入是否自然。
  - 二次进入 / 切回相册：应全部同帧显示（L2 命中）。
  - 无 `IS_MOTION_PHOTO` 机型（用户设备）：验证不再依赖 DB 也能同帧。
  - 快滑到底再快滑回：不应有角标批量迟到弹出。
- **回归**：预览页播放、导出压缩策略（依赖 `isLivePhoto`）不受影响。
- **性能剖面**（对齐 §8.8 预算）：
  - 快滑时用 Profiler 观察磁盘 I/O：滑动期后台预建 I/O 应≈0（缩略图 I/O 独占带宽）。
  - 大相册进入后静置：预建运行时不掉帧；切后台后 CPU/I/O 归零。
  - 索引落盘监测：不应出现每条一次的高频小写入。
  - 对比开关本功能前后的滚动帧率（Systrace / FrameMetrics），不应劣化。

## 10. 实施步骤概览（详细计划见后续 plan）

1. `MediaFile` 加 `displayName`；`MediaRepository` projection + 填充。
2. 新增 `MotionPhotoIndexStore`（持久化 + 加载 + **批量防抖落盘** + 单写协程 + compact）。
3. 新增 `MotionPhotoHeuristics`（纯函数规则）。
4. 新增 `MotionPhotoDecision`（级联入口），并入 `MotionPhotoDetector` 现有缓存逻辑，嗅探结果双写 IndexStore。
5. 新增 `AlbumMotionPrebuilder`（后台全量预建 + **性能治理**：滑动暂停、首屏延迟、`onStop` 停、低并发、后台线程优先级、断点续建）。
6. `MediaGridAdapter.bindLivePhotoIndicator` 改为消费 `BadgeState`，实现淡入 / 淡出 / 校正。
7. `MediaGridFragment` 接线：`ensureLoaded`、启动 / 取消预建、**滚动状态驱动预建暂停/恢复**、生命周期停/续、切相册处理。
8. 单元测试 + 手动验证 + **性能验证（§8.8 预算 + §9 性能剖面）**。

## 11. 风险与权衡

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| **预建与缩略图抢磁盘 I/O** | **滚动掉帧、发热、耗电** | **滑动期暂停预建 + 首屏延迟 + onStop 停 + 后台低并发（§8.1/8.2/8.3）** |
| CSV 高频小写入磨损闪存 | 写放大、I/O 占用 | 内存先写 + 批量防抖 flush + 单写协程 + compact（§8.4） |
| 启发式误判导致角标淡出回撤 | 偶发轻微闪烁 | 规则保守 + 150ms 柔和淡出 + L4 快速校正 |
| 索引文件随时间膨胀 | 磁盘 / 加载变慢 | append 覆盖语义 + 阈值 compact |
| 超大相册预建耗时长 | 首次覆盖不完整 | 预建是优化项非依赖，L3/L4 兜底；断点续建，下次补齐 |
| `filesDir` 索引被用户清数据清空 | 需重建 | 预建 + 浏览自动重建，属可接受降级 |

---

**一句话总结**：用"持久化索引（L2）+ 后台全量预建"根治滞后，让绝大多数 item 同帧显示；用"文件名启发式（L3）+ 淡入校正（L4）"优雅兜底极少数首次未知项——架构上把判定从"滚动时现算"彻底前移到"绑定前已知"。
