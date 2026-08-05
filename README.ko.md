<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://github.com/Hu12037102/photo_choice/raw/master/docs/hero-dark.png">
    <img src="docs/hero-light.png" width="860" alt="PhotoChoice — Android 사진 선택기: 그리드, 앨범, 전체 화면 미리보기, 자르기, 압축, Motion Photo">
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
  <a href="README.zh-CN.md">简体中文</a> ·
  <a href="README.ja.md">日本語</a> ·
  <a href="README.fr.md">Français</a> ·
  <a href="README.es.md">Español</a> ·
  <a href="README.ar.md">العربية</a> ·
  <a href="README.ru.md">Русский</a>
</p>

<br>

Android 사진 선택 라이브러리입니다. 다중 선택 그리드, 앨범 전환, 전체 화면 미리보기, 선택적 카메라 타일,
단일 이미지 자르기, 선택적 압축과 함께 **Motion Photo / Live Photo** 감지 및 미리보기 내 재생을 지원합니다.
통합은 **Builder API**를 통해 이루어지며, 라이브러리 내부 Activity를 직접 실행하지 않습니다.

<br>

## 데모

<p align="center">
  <a href="https://github.com/Hu12037102/photo_choice/blob/master/docs/demo.mp4">
    <picture>
      <source media="(prefers-color-scheme: dark)" srcset="https://github.com/Hu12037102/photo_choice/raw/master/docs/demo-poster-dark.png">
      <img src="docs/demo-poster.png" width="820" alt="PhotoChoice 둘러보기 영상 보기">
    </picture>
  </a>
</p>

<p align="center">
  <a href="https://github.com/Hu12037102/photo_choice/blob/master/docs/demo.mp4"><b>클릭하면 둘러보기 영상이 재생됩니다</b></a><br>
  <sub>그리드와 앨범 · 선택 순서 · 스크롤 날짜 · 카메라 타일 · 전체 화면 미리보기<br>
  동영상 재생 · Motion Photo · 자르기 · JPEG 압축 · 라이트 / 다크 / 시스템 따르기</sub>
</p>

<br>

<p align="center">
  <a href="https://huxiaobai.oss-cn-shanghai.aliyuncs.com/open/sample-release.apk"><img src="docs/qr-sample-apk.png" width="200" alt="스캔하여 PhotoChoice 샘플 앱 설치"></a>
</p>

<p align="center">
  <b><a href="https://huxiaobai.oss-cn-shanghai.aliyuncs.com/open/sample-release.apk">샘플 앱 다운로드</a></b><br>
  <sub>휴대폰으로 스캔하거나 탭하여 다운로드 · <code>sample-release.apk</code> · Android 10+</sub>
</p>

---

## 주요 기능

| 영역 | 내용 |
|------|------|
| 미디어 유형 | 이미지만 / 동영상만 / 이미지 + 동영상 |
| 선택 | 단일 또는 다중 (`selectCount` 1–9), 선택 순서 배지 포함 |
| 앨범 | MediaStore 버킷 집계와 드롭다운 전환 |
| 그리드 | 열 수 설정 가능 (2–6), 정사각형 썸네일, Paging 3 |
| 스크롤 날짜 헤더 | 스크롤 중 보이는 영역의 날짜 표시 |
| 카메라 | 선택적 첫 셀 카메라 타일, 사진은 `DCIM/Camera`에 저장 |
| 미리보기 | 전체 화면 스와이프, 인라인 동영상 재생 |
| Motion Photo | 그리드의 LIVE 배지, 미리보기에서 길게 눌러 내장 클립 재생 |
| 자르기 | 단일 선택 + 이미지 모드에서 독립 `CropActivity` |
| 압축 | 완료 시 JPEG 리사이즈 + 품질 압축, 용량 목표 재시도 루프 포함 |
| 테마 | 라이트 / 다크 / 시스템 따르기, Activity 단위 적용 — 호스트 앱의 전역 설정을 덮어쓰지 않음 |
| 실행 API | **`PhotoChoiceContract`** (권장, 정적 상태 없음) 또는 `forResult` 콜백 |
| 프로세스 종료 대응 | Contract 모드는 Activity 재생성과 프로세스 종료를 견딤 |

- **패키지** `com.google.photochoice` · **버전** `1.1.0` ([CHANGELOG](CHANGELOG.md))
- **minSdk** 29 (Android 10, Scoped Storage — 레거시 쓰기 권한 없이 공용 미디어 읽기)
- **compileSdk** 36 · **Java** 11 · **Kotlin** · [Apache License 2.0](LICENSE)

---

## 설치

### 방법 A — JitPack (권장)

호스트의 **`settings.gradle.kts`**에 JitPack 저장소를 추가합니다. 이 프로젝트는
`FAIL_ON_PROJECT_REPOS`를 사용하므로 저장소는 모듈이 아니라 `dependencyResolutionManagement`에
작성해야 합니다.

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

그런 다음 앱 또는 기능 모듈에 의존성을 선언합니다.

```kotlin
dependencies {
    implementation("com.github.Hu12037102:photo_choice:1.1.0")
}
```

> JitPack은 태그된 소스에서 AAR을 온디맨드로 빌드하므로, 새 태그에 대한 첫 요청은 1분 정도 걸릴 수 있습니다.

### 방법 B — 소스 모듈

```kotlin
// settings.gradle.kts
include(":photo-choice")

// app/build.gradle.kts
dependencies {
    implementation(project(":photo-choice"))
}
```

---

## 빠른 시작

### 1. 권한 선언

라이브러리는 자체 Manifest에 미디어 읽기 권한을 선언하지만, **호스트 앱도 동일한 권한을 선언**하고
런타임에 요청해야 합니다.

| Android 버전 | 권한 |
|--------------|------|
| API 34+ | `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_VISUAL_USER_SELECTED` — 부분 허용도 사용 가능한 것으로 간주 |
| API 33 | `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO` |
| API 29–32 | `READ_EXTERNAL_STORAGE` |

`PermissionHelper`가 권한 목록과 허용 여부 확인을 제공합니다.

```kotlin
import com.google.photochoice.util.PermissionHelper

if (PermissionHelper.hasMediaPermission(context)) {
    openPhotoChoice()
} else {
    requestPermissionLauncher.launch(PermissionHelper.requiredMediaPermissions())
}
```

`requiredMediaPermissions()`는 실행 중인 SDK 수준에 해당하는 **전체** 권한 집합을 반환하며,
`mediaType`에 따라 범위를 좁히지 **않습니다**. API 34+에서는 셋 중 **하나라도** 허용되면
`hasMediaPermission()`이 `true`를 반환합니다(부분 사진 접근 포함). API 33에서는 이미지와 동영상
권한이 **모두** 필요합니다.

### 2. 선택기 실행 — Contract (권장)

```kotlin
import com.google.photochoice.PhotoChoice
import com.google.photochoice.PhotoChoiceContract
import com.google.photochoice.config.MediaType

val launcher = registerForActivityResult(PhotoChoiceContract()) { result ->
    if (result == null) return@registerForActivityResult   // 취소됨
    result.uris.forEach { uri ->
        // content:// 또는 file:// URI, 선택 순서대로
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

`PhotoChoiceContract`는 `ActivityResultContract<PhotoChoiceConfig, PhotoChoiceResult?>`입니다.
설정은 Intent extra로 전달되고 결과는 `setResult()`로 돌아오며, 둘 다 시스템이 관리하므로
정적 상태 없이도 Activity 재생성과 프로세스 종료를 견딥니다. **프로덕션에서는 이 방식을 권장합니다.**

### 3. 대안 — 콜백 API (레거시)

`FragmentActivity`(또는 `AppCompatActivity`)에서 호출합니다.

```kotlin
PhotoChoice.with(this)
    .selectCount(9)
    .mediaType(MediaType.IMAGE)
    .forResult(this) { result ->
        if (result == null) return@forResult   // 취소됨
        result.uris.forEach { uri -> /* ... */ }
    }
```

> **콜백 API는 콜백을 정적 필드에 보관합니다.** 따라서 호스트 Activity 재생성이나 프로세스 종료를
> 견디지 못합니다. 선택기가 실행 중일 때 호스트가 종료되면 콜백이 유실되고, 선택기는 결과 없이 조용히
> 종료됩니다. 신뢰성이 중요하다면 위의 Contract를 사용하세요.

---

## 설정

모든 setter는 `Builder`를 반환합니다. 종단 메서드는 `buildConfig()`(`PhotoChoiceContract`용),
`forResult(activity, callback)`, 또는 `PhotoChoice` 인스턴스 자체가 필요할 때의 `build()`입니다.

| 메서드 | 타입 | 기본값 | 비고 |
|--------|------|--------|------|
| `selectCount` | `Int` | `9` | `1` = 단일, `>1` = 다중. `1..9` 범위를 벗어나면 가장 가까운 경계가 아니라 **`1`로 되돌아갑니다** |
| `mediaType` | `MediaType` | `IMAGE` | `IMAGE` / `VIDEO` / `ALL` |
| `spanCount` | `Int` | `3` | 그리드 열 수, `2..6`으로 클램프 |
| `showCamera` | `Boolean` | `true` | 첫 셀의 카메라 타일 — [카메라 촬영](#카메라-촬영) 참조 |
| `minImageSize` | `Long` | `0` | 이미지 최소 파일 크기(바이트). 작은 아이콘을 걸러냅니다. 이미지 전용 |
| `maxImageSize` | `Long` | `Long.MAX_VALUE` | 이미지 최대 파일 크기(바이트). 이미지 전용 |
| `minVideoDuration` | `Long` | `0` | 동영상 최소 길이(ms) |
| `maxVideoDuration` | `Long` | `60_000` | 동영상 최대 길이(ms) |
| `themeMode` | `ThemeMode` | `FOLLOW_SYSTEM` | `LIGHT` / `DARK` / `FOLLOW_SYSTEM`, Activity 단위 적용 |
| `cropConfig` | `CropConfig` | `CropConfig()` | 아래 참조 |
| `compressConfig` | `CompressConfig` | `CompressConfig()` | 아래 참조 |

> **`spanCount`에는 서로 다른 기본값이 두 개 있습니다.** `Builder`의 기본값은 `3`이지만
> `PhotoChoiceConfig` 생성자 매개변수 자체의 기본값은 `4`입니다. Builder를 거치지 않고
> `PhotoChoiceConfig`를 직접 생성하면 4열이 됩니다.

`PhotoChoice.with(context)`는 현재 `context` 인자를 사용하지 않습니다. API 호환성과 자연스러운
호출 형태를 위해 남겨두었습니다.

### 자르기 — `CropConfig`

```kotlin
import com.google.photochoice.config.CropConfig
import com.google.photochoice.config.CropAspectRatio

.cropConfig(
    CropConfig(
        enabled = true,
        aspectRatio = CropAspectRatio.SQUARE,
        maxWidth = 0,      // 0 = 제한 없음
        maxHeight = 0,     // 0 = 제한 없음
    )
)
```

| 필드 | 기본값 | 비고 |
|------|--------|------|
| `enabled` | `false` | 선택 후 독립 `CropActivity`를 엽니다 |
| `aspectRatio` | `ORIGINAL` | `ORIGINAL` / `SQUARE` / `RATIO_3_4` / `RATIO_4_3` / `RATIO_9_16` / `RATIO_16_9`. 각 상수는 `ratio: Float?`를 노출합니다(`ORIGINAL`은 `null`) |
| `maxWidth` | `0` | 출력 너비 상한(픽셀). `0` 이하는 제한 없음 |
| `maxHeight` | `0` | 출력 높이 상한(픽셀). `0` 이하는 제한 없음 |

자르기는 `selectCount == 1` **이면서** `mediaType == MediaType.IMAGE`일 때만 동작합니다.

> **`MediaType.ALL`은 자르기를 조용히 비활성화합니다.** 판정은 "이미지를 포함"이 아니라 `IMAGE`와의
> 정확한 일치이므로, 이미지와 동영상이 섞인 선택기는 `enabled = true`여도 자르기 화면에 도달하지 않습니다.

단일 선택 + 자르기 활성화 시, 이미지를 고르면 곧바로 자르기로 이동하고 완료 후 선택기가 닫힙니다.

### 압축 — `CompressConfig`

**완료**를 누르면 결과를 전달하기 전에 이미지가 스케일링되고 JPEG로 압축됩니다. 동영상, GIF,
움직임을 유지한 Motion Photo는 압축되지 않습니다.

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

| 필드 | 기본값 | 비고 |
|------|--------|------|
| `enabled` | `false` | 전체 스위치 |
| `maxWidth` / `maxHeight` | `1280` | 리사이즈 시 긴 변 상한 |
| `quality` | `80` | JPEG 시작 품질, 사용 시 `1..100`으로 클램프 |
| `maxFileSizeBytes` | `1_572_864` (약 1.5 MB) | 목표 출력 용량. 만족할 때까지 품질을 단계적으로 낮춥니다 |
| `minQuality` | `50` | 위 재시도 루프의 하한. 이보다 낮아지지 않습니다 |
| `qualityStep` | `10` | 재시도 1회당 품질 감소폭 |
| `skipCompressBaselineLongEdge` | `1280` | 건너뛰기 임계값, 긴 변 |
| `skipCompressBaselineShortEdge` | `720` | 건너뛰기 임계값, 짧은 변 |
| `skipCompressMaxBytes` | `153_600` (150 KB) | 건너뛰기 임계값, 파일 크기 |

**이미 충분히 작은 이미지는 그대로 반환됩니다.** 긴 변 ≤ 1280 **그리고** 짧은 변 ≤ 720이거나,
**또는** 파일이 150 KB 미만인 경우입니다. 이런 이미지를 다시 압축해도 의미 있는 용량 절감 없이
화질만 손해입니다. 정지 이미지로 내보낸 Motion Photo는 이 면제를 의도적으로 우회하여 항상 압축됩니다.

> 출력은 항상 JPEG입니다. 투명 PNG나 WebP는 검은 배경으로 돌아옵니다.

---

## 결과

```kotlin
data class PhotoChoiceResult(
    val uris: List<Uri>,    // 선택된 URI, 선택 순서대로
    val paths: List<String> // 최선의 로컬 경로. 해석할 수 없으면 URI 문자열
)
```

`paths`가 실제 파일 시스템 경로가 되는 것은 라이브러리가 직접 만든 파일(압축 또는 자르기 출력)뿐입니다.
MediaStore 항목은 `content://` URI를 문자열로 반환합니다.

| 미디어 | 압축 없음 | 압축 있음 |
|--------|-----------|-----------|
| 정지 이미지 | `content://` MediaStore URI | `cacheDir/photo_choice/compress_<uuid>.jpg`의 `file://` JPEG |
| 작은 이미지(건너뛰기 기준 미만) | `content://` MediaStore URI | `content://` — 변경 없음 |
| 동영상 | `content://` MediaStore URI | 변경 없음 |
| GIF | `content://` MediaStore URI | 변경 없음(압축하면 애니메이션이 사라짐) |
| Live Photo — 움직임 유지 | `content://` MediaStore URI | 변경 없음(움직임 보존) |
| Live Photo — 정지 이미지로 내보내기 | 해당 없음 | `file://` 압축된 JPEG |
| 잘린 이미지 | `cacheDir/photo_choice/crop_<timestamp>.jpg`의 `file://` | 위와 동일, 이후 압축 |

### 정리

```kotlin
PhotoChoice.cleanup(context)
```

> **이 메서드는 오래된 파일만이 아니라 전부를 삭제합니다.** `cleanup()`은
> `cacheDir/photo_choice/`, `cacheDir/photo_choice_motion/`, `cacheDir/photo_choice_camera/`를
> 조건 없이 비우고 Motion Photo의 인메모리 캐시도 버립니다. 결과를 **모두 소비한 뒤에** 호출하세요.
> 아직 들고 있는 `file://` URI는 더 이상 해석되지 않습니다.
>
> 24시간 경과분 정리는 라이브러리가 스스로 실행하는 별도의 내부 루틴이므로 직접 예약할 필요가 없습니다.

---

## 레시피

```kotlin
// 다중 이미지, 최대 9장
PhotoChoice.with(activity)
    .selectCount(9)
    .mediaType(MediaType.IMAGE)
    .spanCount(4)
    .showCamera(true)
    .forResult(activity) { result -> /* ... */ }

// 아바타: 단일 선택 + 정사각형 자르기 + 압축
PhotoChoice.with(activity)
    .selectCount(1)
    .mediaType(MediaType.IMAGE)
    .cropConfig(CropConfig(enabled = true, aspectRatio = CropAspectRatio.SQUARE))
    .compressConfig(CompressConfig(enabled = true))
    .forResult(activity) { result -> /* ... */ }

// 동영상만, 최대 60초
PhotoChoice.with(activity)
    .selectCount(1)
    .mediaType(MediaType.VIDEO)
    .showCamera(false)          // VIDEO 모드에서는 어차피 자동으로 숨겨집니다
    .maxVideoDuration(60_000L)
    .forResult(activity) { result -> /* ... */ }

// 이미지 + 동영상 — ALL 모드에서는 자르기를 쓸 수 없습니다
PhotoChoice.with(activity)
    .selectCount(9)
    .mediaType(MediaType.ALL)
    .maxVideoDuration(60_000L)
    .forResult(activity) { result -> /* ... */ }
```

---

## 동작 상세

### 단일 선택 vs 다중 선택

| 모드 | 그리드 UI | 상호작용 |
|------|-----------|----------|
| 다중 (`selectCount > 1`) | 체크박스 + 선택 순서 배지 | 체크박스를 눌러 토글, 썸네일을 눌러 미리보기 |
| 단일 (`selectCount == 1`) | 체크박스, 순서 배지, 비활성 오버레이를 **숨김** | 썸네일 탭 → 미리보기, 자르기가 켜져 있으면 자르기로 |

단일 선택에는 중간 단계의 "선택됨" 상태가 없기 때문에, 선택 관련 UI는 비활성화가 아니라 완전히 사라집니다.

### 카메라 촬영

`showCamera(true)`(기본값)일 때 그리드의 첫 셀이 카메라 진입점이 됩니다.

| 항목 | 값 |
|------|-----|
| 디렉터리 | `DCIM/Camera` — 공용 카메라 디렉터리, 즉 시스템 갤러리의 "카메라" 앨범 |
| 파일 이름 | `IMG` + 타임스탬프 뒤 8자리 + 무작위 4자리 + `.jpg` (예: `IMG064001234821.jpg`) |
| 형식 | JPEG |
| 임시 영역 | `cacheDir/photo_choice_camera/`, 샌드박스 클리너가 정리 |

사진은 MediaStore의 `IS_PENDING` 2단계 프로토콜로 삽입됩니다. 바이트가 완전히 기록되기 전까지는
해당 행이 시스템 갤러리에 보이지 않으므로, 다른 앱이 불완전한 파일을 스캔하는 일이 없습니다.
복사에 실패하면 pending 행이 삭제되어 고아 레코드가 남지 않습니다.

**호스트 앱이 할 일: 없습니다.** 라이브러리는 authority가
`${applicationId}.photochoice.fileprovider`인 `FileProvider`를 스스로 선언합니다. 호스트의
`applicationId`에서 파생되므로 다른 통합자와 충돌할 수 없습니다. 카메라 권한도 필요 없습니다.
촬영은 `ACTION_IMAGE_CAPTURE`를 통하며, 권한은 카메라 앱이 보유합니다.

> 카메라 앱이 설치되어 있지 않으면 타일을 눌렀을 때 크래시 대신 메시지가 표시됩니다.
>
> 앱이 자체 Manifest에 `<uses-permission android:name="android.permission.CAMERA" />`를 선언했다면,
> Android는 그 권한이 허용되어야만 해당 Intent를 사용하도록 요구합니다. 이는 플랫폼 규칙이지
> 라이브러리의 요구 사항이 아닙니다.

촬영 후 동작:

| 모드 | 동작 |
|------|------|
| 다중 | 사진이 자동 선택됩니다. `selectCount`에 이미 도달했다면 한도 메시지가 표시되고 사진은 갤러리에 남습니다 |
| 단일 + 자르기 활성화 | 곧바로 자르기 화면으로. 자르기를 취소하면 목록이 새로고침되어 사진이 그리드에 남습니다 |
| 단일 + 자르기 비활성화 | 목록과 앨범 데이터만 새로고침. 자동 선택 없음 |

사용자가 보고 있던 앨범은 바뀌지 않으며, 목록과 앨범 집계만 새로고침됩니다.
그 앨범이 "카메라"가 아니라면 새 사진은 전환한 뒤에 보입니다.

`mediaType`이 `VIDEO`이면 카메라 타일은 자동으로 숨겨집니다(`effectiveShowCamera`).
촬영한 정지 이미지는 동영상 전용 목록에 결코 나타날 수 없으므로 진입점 자체를 제공하지 않습니다.

### Motion Photo / Live Photo

라이브러리는 **Motion Photo, Google Motion Photo, 삼성 모션 포토** 및 짧은 동영상이 내장된 유사한
JPEG/HEIC 파일을 모션 포토로 취급합니다. 이들은 전 과정에서 `IMAGE` 유형으로 유지됩니다.

**그리드에서**

- 썸네일 왼쪽 아래에 **LIVE** 배지가 표시됩니다.
- **페이징을 절대 막지 않습니다.** 페이지 `load`는 MediaStore의 `IS_MOTION_PHOTO`(API 34+)만
  동기적으로 읽고, XMP 스니핑은 비동기로 실행됩니다.
- **인덱스는 영속적입니다.** 스캔 결과가 구성 변경과 프로세스 종료를 넘어 유지되므로 열 때마다
  다시 스니핑하지 않습니다.
- **뷰포트가 우선입니다.** 보이는 영역과 프리페치 범위만 담당하는 고우선순위 스니핑 채널이 있어,
  빠른 스크롤이 전체 이력 큐에 막히지 않습니다.
- `IS_MOTION_PHOTO`를 제공하지 않는 OEM 기기(일부 기종에서 흔합니다)에서는 배지가 비동기 XMP
  헤드/테일 스니핑에 의존하므로 화면에 처음 나타날 때 잠시 지연될 수 있습니다(보통 수백 밀리초 이내).

**전체 화면 미리보기에서**

- LIVE 배지는 상단 바 아래에 있습니다.
- **길게 누르면** 내장 동영상이 재생되고 **떼면** 멈춥니다. 핀치와 줌으로 재생이 잘못 멈추지 않습니다.
- 진입 시 내장 MP4를 백그라운드에서 감지·프리로드하여 `cacheDir/photo_choice_motion/`에 캐시합니다.

**압축이 활성화된 경우** 미리보기에서 선택할 수 있습니다.

- **움직임 유지**(기본값) — 원본 URI를 반환하며 압축하지 않고 움직임을 보존합니다.
- **정지 이미지로 내보내기** — JPEG 압축을 수행하고 움직임을 버립니다.

---

## 아키텍처와 성능

### 페이징

**MediaStore 키셋**(`DATE_ADDED` + `_ID`) 위에서 **Paging 3**를 사용하며, 전체 Cursor 스캔은 없습니다.

| 매개변수 | 값 |
|----------|-----|
| 초기 로드 | 고정 500개를 행 단위로 올림 |
| 페이지 크기 | `spanCount × 25`개 |
| 프리페치 거리 | `spanCount × 35`개 (약 3화면) |
| 메모리 상한 | **없음.** `maxSize`는 의도적으로 설정하지 않았습니다 |

`maxSize`는 의도적으로 제거되었습니다. 가장 먼 페이지를 버리면 페이지 재충전이 깨지고 미리보기 총계가
잘못되기 때문입니다. 페이지 `load`는 XMP 파싱을 전혀 하지 않으며, 이것이 콜드 스타트와 빠른 스크롤을
매끄럽게 유지하는 이유입니다.

### Motion Photo 파이프라인

```
MediaStore 페이지 로드
    ├─ 동기: API 34+ IS_MOTION_PHOTO 일괄 조회 → MediaFile.isMotionPhoto
    └─ 비동기(논블로킹):
           ├─ 앨범 열기: warmAlbumFromMediaStore
           ├─ 뷰포트 채널: 보이는 영역 + 프리페치, 고우선순위 XMP 스니핑
           └─ 백그라운드 채널: 저우선순위 프리페치 범위
```

구현은 `data/motion/` 아래에 있습니다: `MotionPhotoDetector`, `MotionPhotoListEnricher`,
`MotionPhotoXmpSniffer`, `MotionPhotoVideoResolver`.

### 샌드박스 디렉터리

| 디렉터리 | 내용 | 보존 정책 |
|----------|------|-----------|
| `cacheDir/photo_choice/` | 압축과 자르기 출력 | 24시간 정리. `cleanup()`이 비움 |
| `cacheDir/photo_choice_motion/` | 추출된 Motion Photo 클립 | 24시간 정리에 더해 150 MB / 50개 파일 상한 |
| `cacheDir/photo_choice_camera/` | 촬영 임시 파일 | 촬영마다 삭제. 24시간 정리는 안전장치 |

### 주요 의존성

썸네일과 미리보기 이미지에 **Glide** · 그리드에 **Paging 3** · 동영상과 Motion Photo 재생에
**Media3 ExoPlayer** · 미리보기 페이징에 **ViewPager2**.

---

## 공개 API 범위

지원되며 난독화에도 안전한 공개 API는 다음뿐입니다. `consumer-rules.pro`가 keep하는 대상입니다.

`PhotoChoice` · `PhotoChoice.Builder` · `PhotoChoiceContract` · `PhotoChoiceResult` ·
`config.**` 아래 전부

그 밖의 클래스는 Kotlin 가시성상 public이라 호출은 가능하지만(`CameraHelper`, `CompressHelper`,
`SandboxCleaner` 등) **내부 구현 세부사항**입니다. 시맨틱 버저닝의 보호를 받지 않으며
어떤 릴리스에서든 변경되거나 사라질 수 있습니다. `PermissionHelper`만 예외로, 위에 문서화되어 있고
호스트에서 사용하도록 의도되었습니다.

`PhotoChoiceActivity`, `PreviewActivity`, `CropActivity`를 직접 실행하지 마세요.

### 설정 안전성

잘못된 입력은 예외 대신 정규화로 처리되므로 잘못된 설정이 라이브러리를 크래시시키지 않습니다.

| 필드 | 규칙 |
|------|------|
| `selectCount` | `1..9` 안이면 유지, 아니면 **`1`로 재설정** |
| `spanCount` | `2..6`으로 클램프 |
| `minVideoDurationMs` / `maxVideoDurationMs` | min > max이면 교환. min 하한은 `0` |
| `minImageSize` / `maxImageSize` | min > max이면 교환. 둘 다 하한은 `0` |
| `cropConfig.enabled` | 단일 선택**이면서** `MediaType.IMAGE` 필요 (`effectiveCropEnabled`) |
| `showCamera` | `MediaType.VIDEO` 모드에서 강제로 꺼짐 (`effectiveShowCamera`) |

`PhotoChoiceConfig`는 이 경계를 상수로 노출합니다 — `SELECT_COUNT_MIN` / `SELECT_COUNT_MAX`,
`SPAN_COUNT_MIN` / `SPAN_COUNT_MAX` — 여기에 `sanitized*`와 `effective*` 파생 프로퍼티도 제공하므로
실제 적용값을 자체 UI에 반영할 수 있습니다.

---

## 프로젝트 구조

```
photo_choice/
├── photo-choice/                    # 라이브러리
│   └── src/main/java/com/google/photochoice/
│       ├── PhotoChoice.kt           # Builder 진입점, forResult()
│       ├── PhotoChoiceContract.kt   # ActivityResultContract (권장)
│       ├── PhotoChoiceResult.kt
│       ├── config/                  # PhotoChoiceConfig, MediaType, ThemeMode, Crop/CompressConfig
│       ├── data/
│       │   ├── model/
│       │   └── motion/              # Motion Photo 감지, XMP 스니핑, 클립 추출
│       ├── ui/
│       │   ├── grid/
│       │   ├── album/
│       │   ├── crop/                # CropActivity
│       │   ├── preview/             # PreviewActivity, 길게 눌러 라이브 재생
│       │   └── widget/
│       ├── util/                    # PermissionHelper, CameraHelper, CompressHelper, SandboxCleaner
│       └── viewmodel/
├── sample/                          # 모든 옵션을 다루는 데모 앱
├── docs/
│   ├── demo.mp4                     # 둘러보기 영상
│   ├── demo-poster.png              # 영상 포스터 (라이트 / 다크)
│   ├── hero-light.png               # README 헤더 (라이트 / 다크)
│   ├── qr-sample-apk.png            # 샘플 APK QR 코드
│   └── assets/                      # 위 항목 전부를 생성
├── CHANGELOG.md
└── README.md                        # 그 외 7개 언어 번역
```

### 빌드 및 검증

```bash
./gradlew :photo-choice:assembleDebug
./gradlew :sample:installDebug
./gradlew test
./gradlew lint
```

README 이미지와 둘러보기 영상은 모두 생성물입니다. 화면 속 휴대폰 UI는 일러스트이므로
실제 사진 라이브러리가 저장소에 들어가지 않습니다.

```bash
python docs/assets/make_assets.py       # header image, video poster, QR code
python docs/assets/make_demo_video.py   # the walkthrough video itself
python docs/assets/verify_readmes.py    # structural checks across all 8 READMEs
```

---

## 통합 체크리스트

- [ ] 의존성 추가 — JitPack 또는 `implementation(project(":photo-choice"))`
- [ ] 호스트 Manifest에 미디어 읽기 권한 선언
- [ ] 실행 전 `PermissionHelper`로 런타임 권한 요청
- [ ] 실행 API 선택 — **`PhotoChoiceContract`**(프로세스 종료에 강함) 또는 `forResult` 콜백
- [ ] `null`(취소)과 `PhotoChoiceResult`(성공)를 구분해 처리
- [ ] 자르기/압축 출력을 **모두 소비한 뒤에** `PhotoChoice.cleanup(context)` 호출
- [ ] Motion Photo + 압축에서 **움직임 유지 / 정지 이미지로 내보내기** 선택을 이해

---

## 제한 사항

- 데이터 소스는 **공용 MediaStore 미디어**뿐이며, 비공개 또는 숨김 폴더는 포함하지 않습니다.
- UI와 강조 색상은 커스터마이즈할 수 없습니다. `ThemeMode`의 라이트 / 다크 / 시스템 따르기만 지원합니다.
- 동영상 길이 필터는 목록 표시에만 영향을 주며 디스크의 파일은 건드리지 않습니다.
- `MediaType.ALL`과 다중 선택에서는 자르기를 사용할 수 없습니다.
- LIVE 배지는 `IS_MOTION_PHOTO`가 설정되어 있으면(API 34+) 거의 즉시 표시되지만, DB 플래그가 없는
  OEM 기기에서는 잠시 지연됩니다. 미리보기 길게 누르기는 플래그가 없는 모션 포토도 XMP를 포함한
  전체 감지로 인식합니다.

## 문제 보고

이슈를 등록할 때는 **Android 버전, 기기 모델, 설정 코드 조각, 기대 동작과 실제 동작**을 함께
적어주세요. Motion Photo 관련 버그라면 시스템 갤러리가 해당 항목을 라이브로 인식하는지도
알려주시기 바랍니다.
