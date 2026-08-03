<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://github.com/Hu12037102/photo_choice/raw/master/docs/hero-dark.png">
    <img src="docs/hero-light.png" width="860" alt="PhotoChoice — Android photo picker: grid, albums, full-screen preview, crop, compression, Motion Photo">
  </picture>
</p>

<p align="center">
  <a href="https://jitpack.io/#Hu12037102/photo_choice"><img src="https://img.shields.io/jitpack/version/com.github.Hu12037102/photo_choice?style=flat-square&label=JitPack&color=C8763C" alt="JitPack"></a>
  <img src="https://img.shields.io/badge/minSdk-29-1D1D1F?style=flat-square" alt="minSdk 29">
  <img src="https://img.shields.io/badge/language-Kotlin-1D1D1F?style=flat-square" alt="Kotlin">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-Apache%202.0-1D1D1F?style=flat-square" alt="Apache 2.0"></a>
</p>

<p align="center">
  <a href="README.zh-CN.md">简体中文</a> ·
  <a href="README.ja.md">日本語</a> ·
  <a href="README.ko.md">한국어</a> ·
  <a href="README.fr.md">Français</a> ·
  <a href="README.es.md">Español</a> ·
  <a href="README.ar.md">العربية</a> ·
  <a href="README.ru.md">Русский</a>
</p>

<br>

An Android photo picker library: multi-select grid, album switching, full-screen preview, optional
camera tile, single-image crop, optional compression, and **Motion Photo / Live Photo** detection
with in-preview playback. You integrate through a **Builder API** — never by launching the
library's internal Activities yourself.

<br>

## Demo

<p align="center">
  <a href="https://github.com/Hu12037102/photo_choice/blob/master/docs/demo.mp4">
    <picture>
      <source media="(prefers-color-scheme: dark)" srcset="https://github.com/Hu12037102/photo_choice/raw/master/docs/demo-poster-dark.png">
      <img src="docs/demo-poster.png" width="820" alt="Watch the PhotoChoice walkthrough">
    </picture>
  </a>
</p>

<p align="center">
  <a href="https://github.com/Hu12037102/photo_choice/blob/master/docs/demo.mp4"><b>Click to play the walkthrough</b></a><br>
  <sub>Grid &amp; albums · selection order · scroll date · camera tile · full-screen preview<br>
  video playback · Motion Photo · crop · JPEG compression · light / dark / system</sub>
</p>

<br>

<p align="center">
  <a href="https://huxiaobai.oss-cn-shanghai.aliyuncs.com/open/sample-release.apk"><img src="docs/qr-sample-apk.png" width="200" alt="Scan to install the PhotoChoice sample app"></a>
</p>

<p align="center">
  <b><a href="https://huxiaobai.oss-cn-shanghai.aliyuncs.com/open/sample-release.apk">Download the sample app</a></b><br>
  <sub>Scan with your phone, or tap to download · <code>sample-release.apk</code> · Android 10+</sub>
</p>

---

## Highlights

| Area | What you get |
|------|--------------|
| Media types | Images only / videos only / images + videos |
| Selection | Single or multi (`selectCount` 1–9), with selection-order badges |
| Albums | MediaStore bucket aggregation with a dropdown switcher |
| Grid | Configurable columns (2–6), square thumbnails, Paging 3 |
| Scroll date header | Shows the date of the visible region while scrolling |
| Camera | Optional first-cell camera tile; photos land in `DCIM/Camera` |
| Preview | Full-screen swipe; inline video playback |
| Motion Photo | LIVE badge on the grid; long-press to play the embedded clip in preview |
| Crop | Single-select + image mode; standalone `CropActivity` |
| Compression | Optional JPEG resize + quality on finish, with a size-targeting retry loop |
| Theme | Light / dark / follow system, per-Activity — never rewrites the host app's global mode |
| Launch API | **`PhotoChoiceContract`** (recommended, no static state) or the `forResult` callback |
| Process-death safety | Contract mode survives Activity recreation and process death |

- **Package** `com.google.photochoice` · **Version** `1.1.0` ([CHANGELOG](CHANGELOG.md))
- **minSdk** 29 (Android 10, scoped storage — public media is read without legacy write permission)
- **compileSdk** 36 · **Java** 11 · **Kotlin** · [Apache License 2.0](LICENSE)

---

## Install

### Option A — JitPack (recommended)

Add the JitPack repository to the host **`settings.gradle.kts`**. This project uses
`FAIL_ON_PROJECT_REPOS`, so the repository must go in `dependencyResolutionManagement`, not in the
module:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Then declare the dependency in your app or feature module:

```kotlin
dependencies {
    implementation("com.github.Hu12037102:photo_choice:1.1.0")
}
```

> JitPack builds the AAR on demand from the tagged source, so the first request for a new tag may
> take a minute.

### Option B — source module

```kotlin
// settings.gradle.kts
include(":photo-choice")

// app/build.gradle.kts
dependencies {
    implementation(project(":photo-choice"))
}
```

---

## Quick start

### 1. Declare permissions

The library declares media read permissions in its own Manifest, but **the host app must declare
the same permissions** and request them at runtime.

| Android version | Permissions |
|-----------------|-------------|
| API 34+ | `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_VISUAL_USER_SELECTED` — a partial grant is treated as usable |
| API 33 | `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO` |
| API 29–32 | `READ_EXTERNAL_STORAGE` |

`PermissionHelper` gives you the list and the grant check:

```kotlin
import com.google.photochoice.util.PermissionHelper

if (PermissionHelper.hasMediaPermission(context)) {
    openPhotoChoice()
} else {
    requestPermissionLauncher.launch(PermissionHelper.requiredMediaPermissions())
}
```

`requiredMediaPermissions()` returns the full set for the running SDK level; it does **not** narrow
the set based on your `mediaType`. On API 34+, `hasMediaPermission()` returns `true` if **any** of
the three is granted (partial photo access counts); on API 33 it requires **both** image and video
permissions.

### 2. Launch the picker — Contract (recommended)

```kotlin
import com.google.photochoice.PhotoChoice
import com.google.photochoice.PhotoChoiceContract
import com.google.photochoice.config.MediaType

val launcher = registerForActivityResult(PhotoChoiceContract()) { result ->
    if (result == null) return@registerForActivityResult   // cancelled
    result.uris.forEach { uri ->
        // content:// or file:// URI, in selection order
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

`PhotoChoiceContract` is an `ActivityResultContract<PhotoChoiceConfig, PhotoChoiceResult?>`. The
config travels as an Intent extra and the result comes back through `setResult()` — both are
system-managed, so this survives Activity recreation and process death with no static state
involved. **Prefer this for all production use.**

### 3. Alternative — callback API (legacy)

From a `FragmentActivity` (or `AppCompatActivity`):

```kotlin
PhotoChoice.with(this)
    .selectCount(9)
    .mediaType(MediaType.IMAGE)
    .forResult(this) { result ->
        if (result == null) return@forResult   // cancelled
        result.uris.forEach { uri -> /* ... */ }
    }
```

> **The callback API holds the callback in a static field.** It does not survive host Activity
> recreation or process death: if the host is killed while the picker is running, the callback is
> lost and the picker exits cleanly without a result. Use the Contract above when that matters.

---

## Configuration

Every setter returns the `Builder`. Terminal calls are `buildConfig()` (for `PhotoChoiceContract`),
`forResult(activity, callback)`, or `build()` if you want the `PhotoChoice` instance itself.

| Method | Type | Default | Notes |
|--------|------|---------|-------|
| `selectCount` | `Int` | `9` | `1` = single, `>1` = multi. A value outside `1..9` **falls back to `1`** — it is not clamped to the nearest bound |
| `mediaType` | `MediaType` | `IMAGE` | `IMAGE` / `VIDEO` / `ALL` |
| `spanCount` | `Int` | `3` | Grid columns, clamped to `2..6` |
| `showCamera` | `Boolean` | `true` | Camera tile as the first cell — see [Camera capture](#camera-capture) |
| `minImageSize` | `Long` | `0` | Minimum image file size in bytes; filters out tiny icons. Images only |
| `maxImageSize` | `Long` | `Long.MAX_VALUE` | Maximum image file size in bytes. Images only |
| `minVideoDuration` | `Long` | `0` | Minimum video length in ms |
| `maxVideoDuration` | `Long` | `60_000` | Maximum video length in ms |
| `themeMode` | `ThemeMode` | `FOLLOW_SYSTEM` | `LIGHT` / `DARK` / `FOLLOW_SYSTEM`, applied per-Activity |
| `cropConfig` | `CropConfig` | `CropConfig()` | See below |
| `compressConfig` | `CompressConfig` | `CompressConfig()` | See below |

> **`spanCount` has two different defaults.** The `Builder` defaults to `3`, but
> `PhotoChoiceConfig`'s own constructor parameter defaults to `4`. If you construct a
> `PhotoChoiceConfig` directly instead of going through the Builder, you get 4 columns.

`PhotoChoice.with(context)` currently ignores its `context` argument — it is kept for API
compatibility and for a natural call site.

### Crop — `CropConfig`

```kotlin
import com.google.photochoice.config.CropConfig
import com.google.photochoice.config.CropAspectRatio

.cropConfig(
    CropConfig(
        enabled = true,
        aspectRatio = CropAspectRatio.SQUARE,
        maxWidth = 0,      // 0 = unlimited
        maxHeight = 0,     // 0 = unlimited
    )
)
```

| Field | Default | Notes |
|-------|---------|-------|
| `enabled` | `false` | Opens the standalone `CropActivity` after the pick |
| `aspectRatio` | `ORIGINAL` | `ORIGINAL` / `SQUARE` / `RATIO_3_4` / `RATIO_4_3` / `RATIO_9_16` / `RATIO_16_9`; each exposes a `ratio: Float?` (`null` for `ORIGINAL`) |
| `maxWidth` | `0` | Caps the output width in pixels; `0` or less means unlimited |
| `maxHeight` | `0` | Caps the output height in pixels; `0` or less means unlimited |

Crop only runs when `selectCount == 1` **and** `mediaType == MediaType.IMAGE`.

> **`MediaType.ALL` silently disables crop.** The gate is an exact match on `IMAGE`, not "includes
> images", so a mixed image+video picker never reaches the crop screen even with `enabled = true`.

With single-select + crop enabled, picking an image goes straight to crop, then returns and closes
the picker.

### Compression — `CompressConfig`

On **Done**, images are scaled and JPEG-compressed before the result is delivered. Videos, GIFs and
keep-live Motion Photos are never compressed.

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

| Field | Default | Notes |
|-------|---------|-------|
| `enabled` | `false` | Master switch |
| `maxWidth` / `maxHeight` | `1280` | Longest-edge bounds for the resize |
| `quality` | `80` | Starting JPEG quality, clamped to `1..100` at use |
| `maxFileSizeBytes` | `1_572_864` (~1.5 MB) | Target output size; quality is stepped down until it fits |
| `minQuality` | `50` | Floor for that retry loop — it never degrades below this |
| `qualityStep` | `10` | How much quality drops per retry |
| `skipCompressBaselineLongEdge` | `1280` | Skip threshold, long edge |
| `skipCompressBaselineShortEdge` | `720` | Skip threshold, short edge |
| `skipCompressMaxBytes` | `153_600` (150 KB) | Skip threshold, file size |

**An image is returned untouched when it is already small enough:** long edge ≤ 1280 **and** short
edge ≤ 720, **or** the file is under 150 KB. Recompressing those would only lose quality for no
meaningful saving. Motion Photos exported as static images deliberately bypass this exemption and
are always compressed.

> Output is always JPEG. A transparent PNG or WebP comes back with a black background.

---

## Result

```kotlin
data class PhotoChoiceResult(
    val uris: List<Uri>,    // selected URIs, in selection order
    val paths: List<String> // best-effort local paths; the URI string when unresolved
)
```

`paths` only contains real filesystem paths for files the library itself produced (compressed or
cropped output). MediaStore items return their `content://` URI as a string.

| Media | Without compression | With compression |
|-------|---------------------|------------------|
| Static image | `content://` MediaStore URI | `file://` JPEG under `cacheDir/photo_choice/compress_<uuid>.jpg` |
| Small image (below the skip baseline) | `content://` MediaStore URI | `content://` — untouched |
| Video | `content://` MediaStore URI | Untouched |
| GIF | `content://` MediaStore URI | Untouched (compression would lose the animation) |
| Live Photo — keep live | `content://` MediaStore URI | Untouched (motion preserved) |
| Live Photo — export static | n/a | `file://` compressed JPEG |
| Cropped image | `file://` under `cacheDir/photo_choice/crop_<timestamp>.jpg` | same, then compressed |

### Cleaning up

```kotlin
PhotoChoice.cleanup(context)
```

> **This deletes everything, not just old files.** `cleanup()` unconditionally clears
> `cacheDir/photo_choice/`, `cacheDir/photo_choice_motion/` and `cacheDir/photo_choice_camera/`,
> and drops the in-memory Motion Photo cache. Call it **after** you have finished consuming the
> result — a `file://` URI you are still holding will stop resolving.
>
> The 24-hour age-based sweep is a separate internal routine that the library runs on its own; you
> do not need to schedule it.

---

## Recipes

```kotlin
// Multiple images, up to 9
PhotoChoice.with(activity)
    .selectCount(9)
    .mediaType(MediaType.IMAGE)
    .spanCount(4)
    .showCamera(true)
    .forResult(activity) { result -> /* ... */ }

// Avatar: single select + square crop + compression
PhotoChoice.with(activity)
    .selectCount(1)
    .mediaType(MediaType.IMAGE)
    .cropConfig(CropConfig(enabled = true, aspectRatio = CropAspectRatio.SQUARE))
    .compressConfig(CompressConfig(enabled = true))
    .forResult(activity) { result -> /* ... */ }

// Video only, max 60 s
PhotoChoice.with(activity)
    .selectCount(1)
    .mediaType(MediaType.VIDEO)
    .showCamera(false)          // hidden automatically anyway in VIDEO mode
    .maxVideoDuration(60_000L)
    .forResult(activity) { result -> /* ... */ }

// Images + videos — note that crop is unavailable in ALL mode
PhotoChoice.with(activity)
    .selectCount(9)
    .mediaType(MediaType.ALL)
    .maxVideoDuration(60_000L)
    .forResult(activity) { result -> /* ... */ }
```

---

## Behavior in detail

### Single vs multi select

| Mode | Grid UI | Interaction |
|------|---------|-------------|
| Multi (`selectCount > 1`) | Checkbox + selection-order badge | Tap the checkbox to toggle; tap the thumbnail to preview |
| Single (`selectCount == 1`) | **Hides** the checkbox, order badge and disabled overlay | Tap the thumbnail → preview, or crop when enabled |

Single-select has no intermediate "selected" state, which is why the selection chrome disappears
entirely rather than being disabled.

### Camera capture

With `showCamera(true)` (the default), the first grid cell is a camera entry.

| Item | Value |
|------|-------|
| Directory | `DCIM/Camera` — the public camera directory, i.e. the system "Camera" album |
| File name | `IMG` + the last 8 digits of the timestamp + 4 random digits + `.jpg`, e.g. `IMG064001234821.jpg` |
| Format | JPEG |
| Staging area | `cacheDir/photo_choice_camera/`, cleared by the sandbox cleaner |

Photos are inserted through the MediaStore `IS_PENDING` two-phase protocol: the row only becomes
visible to the system gallery once the bytes are fully written, so other apps never scan a partial
file. If the copy fails, the pending row is deleted rather than left orphaned.

**What the host app must do: nothing.** The library declares its own `FileProvider` with authority
`${applicationId}.photochoice.fileprovider` — derived from the host's `applicationId`, so it can
never clash with another integrator. No camera permission is required either: capture goes through
`ACTION_IMAGE_CAPTURE`, and the camera app holds the permission itself.

> If no camera app is installed, tapping the tile shows a message instead of crashing.
>
> If your app declares `<uses-permission android:name="android.permission.CAMERA" />` in its own
> Manifest, Android then requires that permission to be granted before the intent can be used. That
> is a platform rule, not a library requirement.

After a capture:

| Mode | Behavior |
|------|----------|
| Multi | The photo is auto-selected. If `selectCount` is already reached, a "limit reached" message appears and the photo still stays in the gallery |
| Single + crop enabled | Goes straight to the crop screen; cancelling refreshes the list so the photo remains visible in the grid |
| Single + crop disabled | Refreshes the list and album data only — no auto-selection |

The album the user is browsing never changes; only the list and album aggregates refresh. If that
album is not "Camera", the new photo appears after switching to it.

When `mediaType` is `VIDEO`, the camera tile is hidden automatically (`effectiveShowCamera`): a
captured still could never appear in a video-only list, so the entry point is not offered.

### Motion Photo / Live Photo

The library treats **Motion Photo, Google Motion Photo, Samsung motion photos** and similar
JPEG/HEIC files with an embedded short video as motion photos. They remain `IMAGE` type throughout.

**In the grid**

- A **LIVE** badge sits at the bottom-left of the thumbnail.
- **Paging is never blocked.** A page `load` only reads MediaStore's `IS_MOTION_PHOTO` synchronously
  (API 34+); the XMP sniff runs asynchronously.
- **The index is persistent.** Scan results survive configuration changes and process death, so
  nothing is re-sniffed on every open.
- **The viewport has priority.** A dedicated high-priority sniff channel covers the visible and
  prefetch window, so a fast scroll is not stuck behind a full-history queue.
- On OEMs that omit `IS_MOTION_PHOTO` — common on some devices — badges depend on the async XMP
  head/tail sniff, so the first appearance on screen can lag briefly, typically under a few hundred
  milliseconds.

**In full-screen preview**

- The LIVE badge sits below the top bar.
- **Long-press** to play the embedded video, **release** to stop. Pinch and zoom will not
  accidentally stop playback.
- On entry, the embedded MP4 is detected and preloaded in the background, cached under
  `cacheDir/photo_choice_motion/`.

**With compression enabled**, preview offers a choice:

- **Keep live** (default) — returns the original URI, no compression, motion preserved.
- **Export static** — JPEG compression, motion discarded.

---

## Architecture & performance

### Paging

**Paging 3 over a MediaStore keyset** (`DATE_ADDED` + `_ID`) — there is no full cursor scan.

| Parameter | Value |
|-----------|-------|
| Initial load | A fixed 500 items, rounded up to a whole row |
| Page size | `spanCount × 25` items |
| Prefetch distance | `spanCount × 35` items (~3 screens) |
| Memory cap | **None.** `maxSize` is deliberately not set |

`maxSize` was removed on purpose: dropping the farthest pages broke page refill and made the preview
totals wrong. Page `load` runs no XMP parsing, which is what keeps cold start and fast scroll smooth.

### Motion Photo pipeline

```
MediaStore page load
    ├─ Sync: API 34+ batch IS_MOTION_PHOTO → MediaFile.isMotionPhoto
    └─ Async (non-blocking):
           ├─ Album open: warmAlbumFromMediaStore
           ├─ Viewport channel: visible + prefetch, high-priority XMP sniff
           └─ Background channel: low-priority prefetch window
```

Implemented under `data/motion/`: `MotionPhotoDetector`, `MotionPhotoListEnricher`,
`MotionPhotoXmpSniffer`, `MotionPhotoVideoResolver`.

### Sandbox directories

| Directory | Contents | Retention |
|-----------|----------|-----------|
| `cacheDir/photo_choice/` | Compressed and cropped output | 24 h sweep; wiped by `cleanup()` |
| `cacheDir/photo_choice_motion/` | Extracted Motion Photo clips | 24 h sweep, plus caps of 150 MB / 50 files |
| `cacheDir/photo_choice_camera/` | Capture staging files | Deleted after each capture; 24 h sweep as a backstop |

### Key dependencies

**Glide** for thumbnails and preview images · **Paging 3** for the grid · **Media3 ExoPlayer** for
video and Motion Photo playback · **ViewPager2** for preview paging.

---

## Public API surface

Only these types are the supported, obfuscation-safe API — they are the ones kept by
`consumer-rules.pro`:

`PhotoChoice` · `PhotoChoice.Builder` · `PhotoChoiceContract` · `PhotoChoiceResult` ·
everything under `config.**`

Other classes are public by Kotlin visibility and therefore callable — `CameraHelper`,
`CompressHelper`, `SandboxCleaner`, `DesignTokens` and friends — but they are **internal
implementation details**, not covered by semantic versioning, and may change or disappear in any
release. `PermissionHelper` is the one exception: it is documented above and intended for host use.

Never launch `PhotoChoiceActivity`, `PreviewActivity` or `CropActivity` directly.

### Configuration safety

Invalid input is sanitized rather than thrown, so bad configuration can never crash the library:

| Field | Rule |
|-------|------|
| `selectCount` | Kept if within `1..9`, otherwise **reset to `1`** |
| `spanCount` | Clamped into `2..6` |
| `minVideoDurationMs` / `maxVideoDurationMs` | Swapped if min > max; min floored at `0` |
| `minImageSize` / `maxImageSize` | Swapped if min > max; both floored at `0` |
| `cropConfig.enabled` | Requires single-select **and** `MediaType.IMAGE` (`effectiveCropEnabled`) |
| `showCamera` | Forced off in `MediaType.VIDEO` mode (`effectiveShowCamera`) |

`PhotoChoiceConfig` exposes the bounds as constants — `SELECT_COUNT_MIN` / `SELECT_COUNT_MAX`,
`SPAN_COUNT_MIN` / `SPAN_COUNT_MAX` — along with the derived `sanitized*` and `effective*`
properties, if you want to reflect the effective values in your own UI.

---

## Project layout

```
photo_choice/
├── photo-choice/                    # the library
│   └── src/main/java/com/google/photochoice/
│       ├── PhotoChoice.kt           # Builder entry point, forResult()
│       ├── PhotoChoiceContract.kt   # ActivityResultContract (recommended)
│       ├── PhotoChoiceResult.kt
│       ├── config/                  # PhotoChoiceConfig, MediaType, ThemeMode, Crop/CompressConfig
│       ├── data/
│       │   ├── model/
│       │   └── motion/              # Motion Photo detection, XMP sniffing, clip extraction
│       ├── ui/
│       │   ├── grid/
│       │   ├── album/
│       │   ├── crop/                # CropActivity
│       │   ├── preview/             # PreviewActivity, long-press live playback
│       │   └── widget/
│       ├── util/                    # PermissionHelper, CameraHelper, CompressHelper, SandboxCleaner
│       └── viewmodel/
├── sample/                          # demo app covering every option
├── docs/
│   ├── demo.mp4                     # walkthrough video
│   ├── demo-poster.png              # video poster (light / dark)
│   ├── hero-light.png               # README header (light / dark)
│   ├── qr-sample-apk.png            # sample APK QR code
│   └── assets/                      # generates everything above
├── CHANGELOG.md
└── README.md                        # plus 7 translations
```

### Build & verify

```bash
./gradlew :photo-choice:assembleDebug
./gradlew :sample:installDebug
./gradlew test
./gradlew lint
```

The README artwork and the walkthrough video are generated, not hand-made — the phone
screens are illustrations, so no real photo library ends up in the repo:

```bash
python docs/assets/make_assets.py       # header image, video poster, QR code
python docs/assets/make_demo_video.py   # the walkthrough video itself
python docs/assets/verify_readmes.py    # structural checks across all 8 READMEs
```

---

## Integration checklist

- [ ] Dependency added — JitPack or `implementation(project(":photo-choice"))`
- [ ] Media read permissions declared in the host Manifest
- [ ] Runtime permission requested before launch, via `PermissionHelper`
- [ ] Launch API chosen — **`PhotoChoiceContract`** (process-death-safe) or the `forResult` callback
- [ ] `null` (cancelled) handled separately from a `PhotoChoiceResult`
- [ ] `PhotoChoice.cleanup(context)` called **after** consuming crop/compress output
- [ ] For Motion Photos + compression, the **Keep live / Export static** choice understood

---

## Limitations

- The data source is **public MediaStore media** only — not private or hidden folders.
- UI and accent colors are not customizable; only `ThemeMode` light / dark / system.
- Video duration filters affect listing only, never the files on disk.
- Crop is unavailable in `MediaType.ALL` and in multi-select.
- LIVE badges are near-instant when `IS_MOTION_PHOTO` is set (API 34+), but lag briefly on OEMs
  without the DB flag. Preview long-press still detects unflagged motion photos via a full detect
  including XMP.

## Issues

When filing an issue, please include the **Android version, device model, a config snippet, and the
expected vs actual behavior**. For Motion Photo bugs, also note whether the system gallery
recognizes the item as live.
