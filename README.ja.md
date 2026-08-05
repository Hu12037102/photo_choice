<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://github.com/Hu12037102/photo_choice/raw/master/docs/hero-dark.png">
    <img src="docs/hero-light.png" width="860" alt="PhotoChoice — Android 画像ピッカー：グリッド、アルバム、フルスクリーンプレビュー、クロップ、圧縮、Motion Photo">
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
  <a href="README.ko.md">한국어</a> ·
  <a href="README.fr.md">Français</a> ·
  <a href="README.es.md">Español</a> ·
  <a href="README.ar.md">العربية</a> ·
  <a href="README.ru.md">Русский</a>
</p>

<br>

Android 向けの画像ピッカーライブラリです。複数選択グリッド、アルバム切り替え、フルスクリーンプレビュー、
カメラタイル、単一画像のクロップ、任意の圧縮に加え、**Motion Photo / Live Photo** の検出とプレビュー内再生に
対応しています。統合は **Builder API** を通して行い、ライブラリ内部の Activity を直接起動することはありません。

<br>

## デモ

<p align="center">
  <a href="https://github.com/Hu12037102/photo_choice/blob/master/docs/demo.mp4">
    <picture>
      <source media="(prefers-color-scheme: dark)" srcset="https://github.com/Hu12037102/photo_choice/raw/master/docs/demo-poster-dark.png">
      <img src="docs/demo-poster.png" width="820" alt="PhotoChoice のウォークスルーを見る">
    </picture>
  </a>
</p>

<p align="center">
  <a href="https://github.com/Hu12037102/photo_choice/blob/master/docs/demo.mp4"><b>クリックしてウォークスルーを再生</b></a><br>
  <sub>グリッドとアルバム · 選択順 · スクロール日付 · カメラタイル · フルスクリーンプレビュー<br>
  動画再生 · Motion Photo · クロップ · JPEG 圧縮 · ライト / ダーク / システム追従</sub>
</p>

<br>

<p align="center">
  <a href="https://huxiaobai.oss-cn-shanghai.aliyuncs.com/open/sample-release.apk"><img src="docs/qr-sample-apk.png" width="200" alt="スキャンして PhotoChoice サンプルアプリをインストール"></a>
</p>

<p align="center">
  <b><a href="https://huxiaobai.oss-cn-shanghai.aliyuncs.com/open/sample-release.apk">サンプルアプリをダウンロード</a></b><br>
  <sub>スマートフォンでスキャン、またはタップしてダウンロード · <code>sample-release.apk</code> · Android 10+</sub>
</p>

---

## 主な機能

| 分野 | 内容 |
|------|------|
| メディア種別 | 画像のみ / 動画のみ / 画像 + 動画 |
| 選択 | 単一または複数（`selectCount` 1–9）、選択順バッジ付き |
| アルバム | MediaStore のバケット集約とドロップダウン切り替え |
| グリッド | 列数を設定可能（2–6）、正方形サムネイル、Paging 3 |
| スクロール日付ヘッダー | スクロール中に表示領域の日付を表示 |
| カメラ | 先頭セルのカメラタイル（任意）。写真は `DCIM/Camera` に保存 |
| プレビュー | フルスクリーンのスワイプ、インライン動画再生 |
| Motion Photo | グリッドの LIVE バッジ、プレビューで長押しすると埋め込み動画を再生 |
| クロップ | 単一選択 + 画像モードで、独立した `CropActivity` |
| 圧縮 | 完了時の JPEG リサイズ + 品質圧縮（サイズ目標に向けた再試行ループ付き） |
| テーマ | ライト / ダーク / システム追従。Activity 単位で適用され、ホストアプリのグローバル設定は書き換えません |
| 起動 API | **`PhotoChoiceContract`**（推奨、静的状態なし）または `forResult` コールバック |
| プロセス死対策 | Contract モードは Activity 再生成とプロセス死を乗り越えます |

- **パッケージ** `com.google.photochoice` · **バージョン** `1.1.0`（[CHANGELOG](CHANGELOG.md)）
- **minSdk** 29（Android 10、スコープ付きストレージ — レガシーな書き込み権限なしで公開メディアを読み取り）
- **compileSdk** 36 · **Java** 11 · **Kotlin** · [Apache License 2.0](LICENSE)

---

## 導入

### 方法 A — JitPack（推奨）

ホストの **`settings.gradle.kts`** に JitPack リポジトリを追加します。本プロジェクトは
`FAIL_ON_PROJECT_REPOS` を使用しているため、リポジトリはモジュールではなく
`dependencyResolutionManagement` に記述する必要があります。

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

その上で、アプリまたは機能モジュールに依存関係を宣言します。

```kotlin
dependencies {
    implementation("com.github.Hu12037102:photo_choice:1.1.0")
}
```

> JitPack はタグ付きソースから AAR をオンデマンドでビルドするため、新しいタグへの最初のリクエストは
> 1 分程度かかることがあります。

### 方法 B — ソースモジュール

```kotlin
// settings.gradle.kts
include(":photo-choice")

// app/build.gradle.kts
dependencies {
    implementation(project(":photo-choice"))
}
```

---

## クイックスタート

### 1. 権限を宣言する

ライブラリは自身の Manifest でメディア読み取り権限を宣言していますが、
**ホストアプリでも同じ権限を宣言し**、実行時にリクエストする必要があります。

| Android バージョン | 権限 |
|--------------------|------|
| API 34+ | `READ_MEDIA_IMAGES`、`READ_MEDIA_VIDEO`、`READ_MEDIA_VISUAL_USER_SELECTED` — 部分的な許可も利用可能として扱われます |
| API 33 | `READ_MEDIA_IMAGES`、`READ_MEDIA_VIDEO` |
| API 29–32 | `READ_EXTERNAL_STORAGE` |

`PermissionHelper` が権限リストと許可状態のチェックを提供します。

```kotlin
import com.google.photochoice.util.PermissionHelper

if (PermissionHelper.hasMediaPermission(context)) {
    openPhotoChoice()
} else {
    requestPermissionLauncher.launch(PermissionHelper.requiredMediaPermissions())
}
```

`requiredMediaPermissions()` は実行中の SDK レベルに応じた**完全な**権限セットを返します。
`mediaType` に応じてセットを絞り込むことは**ありません**。API 34+ では 3 つのうち**いずれか**が許可されていれば
`hasMediaPermission()` は `true` を返します（部分的な写真アクセスも含む）。API 33 では画像と動画の
**両方**の権限が必要です。

### 2. ピッカーを起動する — Contract（推奨）

```kotlin
import com.google.photochoice.PhotoChoice
import com.google.photochoice.PhotoChoiceContract
import com.google.photochoice.config.MediaType

val launcher = registerForActivityResult(PhotoChoiceContract()) { result ->
    if (result == null) return@registerForActivityResult   // キャンセル
    result.uris.forEach { uri ->
        // content:// または file:// URI（選択順）
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

`PhotoChoiceContract` は `ActivityResultContract<PhotoChoiceConfig, PhotoChoiceResult?>` です。
設定は Intent extra として渡され、結果は `setResult()` で返されます。どちらもシステム管理下にあるため、
静的状態を一切使わずに Activity の再生成とプロセス死を乗り越えられます。
**本番環境ではこちらを推奨します。**

### 3. 代替手段 — コールバック API（レガシー）

`FragmentActivity`（または `AppCompatActivity`）から呼び出します。

```kotlin
PhotoChoice.with(this)
    .selectCount(9)
    .mediaType(MediaType.IMAGE)
    .forResult(this) { result ->
        if (result == null) return@forResult   // キャンセル
        result.uris.forEach { uri -> /* ... */ }
    }
```

> **コールバック API はコールバックを静的フィールドで保持します。** そのためホスト Activity の再生成や
> プロセス死には耐えられません。ピッカー動作中にホストが強制終了されるとコールバックは失われ、
> ピッカーは結果を返さずに静かに終了します。信頼性が求められる場合は上記の Contract を使用してください。

---

## 設定

すべてのセッターは `Builder` を返します。終端メソッドは `buildConfig()`（`PhotoChoiceContract` 用）、
`forResult(activity, callback)`、または `PhotoChoice` インスタンス自体が必要な場合の `build()` です。

| メソッド | 型 | 既定値 | 備考 |
|----------|-----|--------|------|
| `selectCount` | `Int` | `9` | `1` = 単一、`>1` = 複数。`1..9` の範囲外の値は最も近い境界ではなく**`1` にフォールバック**します |
| `mediaType` | `MediaType` | `IMAGE` | `IMAGE` / `VIDEO` / `ALL` |
| `spanCount` | `Int` | `3` | グリッドの列数。`2..6` にクランプされます |
| `showCamera` | `Boolean` | `true` | 先頭セルのカメラタイル — [カメラ撮影](#カメラ撮影)を参照 |
| `minImageSize` | `Long` | `0` | 画像の最小ファイルサイズ（バイト）。小さなアイコンを除外できます。画像のみ |
| `maxImageSize` | `Long` | `Long.MAX_VALUE` | 画像の最大ファイルサイズ（バイト）。画像のみ |
| `minVideoDuration` | `Long` | `0` | 動画の最短長（ミリ秒） |
| `maxVideoDuration` | `Long` | `60_000` | 動画の最長長（ミリ秒） |
| `themeMode` | `ThemeMode` | `FOLLOW_SYSTEM` | `LIGHT` / `DARK` / `FOLLOW_SYSTEM`。Activity 単位で適用 |
| `cropConfig` | `CropConfig` | `CropConfig()` | 下記参照 |
| `compressConfig` | `CompressConfig` | `CompressConfig()` | 下記参照 |

> **`spanCount` には既定値が 2 つあります。** `Builder` の既定値は `3` ですが、`PhotoChoiceConfig` の
> コンストラクタ引数自体の既定値は `4` です。Builder を経由せず `PhotoChoiceConfig` を直接構築すると
> 4 列になります。

`PhotoChoice.with(context)` は現在 `context` 引数を使用していません。API 互換性と自然な呼び出し記述の
ために残されています。

### クロップ — `CropConfig`

```kotlin
import com.google.photochoice.config.CropConfig
import com.google.photochoice.config.CropAspectRatio

.cropConfig(
    CropConfig(
        enabled = true,
        aspectRatio = CropAspectRatio.SQUARE,
        maxWidth = 0,      // 0 = 無制限
        maxHeight = 0,     // 0 = 無制限
    )
)
```

| フィールド | 既定値 | 備考 |
|------------|--------|------|
| `enabled` | `false` | 選択後に独立した `CropActivity` を開きます |
| `aspectRatio` | `ORIGINAL` | `ORIGINAL` / `SQUARE` / `RATIO_3_4` / `RATIO_4_3` / `RATIO_9_16` / `RATIO_16_9`。各定数は `ratio: Float?` を公開します（`ORIGINAL` は `null`） |
| `maxWidth` | `0` | 出力幅の上限（ピクセル）。`0` 以下は無制限 |
| `maxHeight` | `0` | 出力高さの上限（ピクセル）。`0` 以下は無制限 |

クロップは `selectCount == 1` **かつ** `mediaType == MediaType.IMAGE` のときのみ動作します。

> **`MediaType.ALL` はクロップを暗黙的に無効化します。** 判定は「画像を含む」ではなく `IMAGE` との
> 完全一致であるため、画像と動画が混在するピッカーでは `enabled = true` でもクロップ画面に到達しません。

単一選択 + クロップ有効の場合、画像を選ぶとそのままクロップに進み、完了後にピッカーが閉じます。

### 圧縮 — `CompressConfig`

**完了**を押すと、結果を返す前に画像がスケーリングされ JPEG 圧縮されます。動画、GIF、
動きを保持した Motion Photo は圧縮されません。

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

| フィールド | 既定値 | 備考 |
|------------|--------|------|
| `enabled` | `false` | マスタースイッチ |
| `maxWidth` / `maxHeight` | `1280` | リサイズ時の最長辺の上限 |
| `quality` | `80` | JPEG の開始品質。使用時に `1..100` にクランプされます |
| `maxFileSizeBytes` | `1_572_864`（約 1.5 MB） | 出力サイズの目標。収まるまで品質を段階的に下げます |
| `minQuality` | `50` | 上記再試行ループの下限。これより下がることはありません |
| `qualityStep` | `10` | 再試行 1 回あたりの品質低下量 |
| `skipCompressBaselineLongEdge` | `1280` | スキップ判定のしきい値（長辺） |
| `skipCompressBaselineShortEdge` | `720` | スキップ判定のしきい値（短辺） |
| `skipCompressMaxBytes` | `153_600`（150 KB） | スキップ判定のしきい値（ファイルサイズ） |

**既に十分小さい画像はそのまま返されます。** 長辺 ≤ 1280 **かつ**短辺 ≤ 720、**または**
ファイルサイズが 150 KB 未満の場合です。これらを再圧縮しても意味のある削減にはならず、画質が落ちるだけです。
静止画としてエクスポートされた Motion Photo は、この免除を意図的にバイパスして必ず圧縮されます。

> 出力は常に JPEG です。透過 PNG や WebP は黒背景になって返ります。

---

## 結果

```kotlin
data class PhotoChoiceResult(
    val uris: List<Uri>,    // 選択された URI（選択順）
    val paths: List<String> // ベストエフォートのローカルパス。解決できない場合は URI 文字列
)
```

`paths` が実際のファイルシステムパスになるのは、ライブラリ自身が生成したファイル（圧縮またはクロップの出力）
だけです。MediaStore の項目は `content://` URI を文字列として返します。

| メディア | 圧縮なし | 圧縮あり |
|----------|----------|----------|
| 静止画像 | `content://` MediaStore URI | `cacheDir/photo_choice/compress_<uuid>.jpg` の `file://` JPEG |
| 小さい画像（スキップ基準未満） | `content://` MediaStore URI | `content://` — 無変換 |
| 動画 | `content://` MediaStore URI | 無変換 |
| GIF | `content://` MediaStore URI | 無変換（圧縮するとアニメーションが失われるため） |
| Live Photo — 動きを保持 | `content://` MediaStore URI | 無変換（動きを保持） |
| Live Photo — 静止画としてエクスポート | 該当なし | `file://` の圧縮済み JPEG |
| クロップ済み画像 | `cacheDir/photo_choice/crop_<timestamp>.jpg` の `file://` | 同上、その後圧縮 |

### クリーンアップ

```kotlin
PhotoChoice.cleanup(context)
```

> **これは古いファイルだけでなく、すべてを削除します。** `cleanup()` は
> `cacheDir/photo_choice/`、`cacheDir/photo_choice_motion/`、`cacheDir/photo_choice_camera/` を
> 無条件にクリアし、Motion Photo のインメモリキャッシュも破棄します。結果を**消費し終えてから**
> 呼び出してください。保持したままの `file://` URI は解決できなくなります。
>
> 24 時間経過分のスイープはライブラリが自動で実行する別の内部処理であり、自分でスケジュールする必要は
> ありません。

---

## レシピ

```kotlin
// 複数画像、最大 9 枚
PhotoChoice.with(activity)
    .selectCount(9)
    .mediaType(MediaType.IMAGE)
    .spanCount(4)
    .showCamera(true)
    .forResult(activity) { result -> /* ... */ }

// アバター：単一選択 + 正方形クロップ + 圧縮
PhotoChoice.with(activity)
    .selectCount(1)
    .mediaType(MediaType.IMAGE)
    .cropConfig(CropConfig(enabled = true, aspectRatio = CropAspectRatio.SQUARE))
    .compressConfig(CompressConfig(enabled = true))
    .forResult(activity) { result -> /* ... */ }

// 動画のみ、最大 60 秒
PhotoChoice.with(activity)
    .selectCount(1)
    .mediaType(MediaType.VIDEO)
    .showCamera(false)          // VIDEO モードではいずれにせよ自動的に非表示
    .maxVideoDuration(60_000L)
    .forResult(activity) { result -> /* ... */ }

// 画像 + 動画 — ALL モードではクロップが使えない点に注意
PhotoChoice.with(activity)
    .selectCount(9)
    .mediaType(MediaType.ALL)
    .maxVideoDuration(60_000L)
    .forResult(activity) { result -> /* ... */ }
```

---

## 動作の詳細

### 単一選択と複数選択

| モード | グリッド UI | 操作 |
|--------|-------------|------|
| 複数（`selectCount > 1`） | チェックボックス + 選択順バッジ | チェックボックスをタップして切り替え、サムネイルをタップでプレビュー |
| 単一（`selectCount == 1`） | チェックボックス、選択順バッジ、無効化オーバーレイを**非表示** | サムネイルをタップ → プレビュー、クロップ有効時はクロップへ |

単一選択には中間的な「選択済み」状態が存在しないため、選択関連の UI は無効化ではなく完全に非表示になります。

### カメラ撮影

`showCamera(true)`（既定）のとき、グリッドの先頭セルがカメラの入口になります。

| 項目 | 値 |
|------|-----|
| ディレクトリ | `DCIM/Camera` — 公開カメラディレクトリ、つまりシステムギャラリーの「カメラ」アルバム |
| ファイル名 | `IMG` + タイムスタンプ下 8 桁 + ランダム 4 桁 + `.jpg`（例：`IMG064001234821.jpg`） |
| 形式 | JPEG |
| 一時領域 | `cacheDir/photo_choice_camera/`（サンドボックスクリーナーが回収） |

写真は MediaStore の `IS_PENDING` 二段階プロトコルで挿入されます。バイトが完全に書き込まれるまで
その行はシステムギャラリーから見えないため、他のアプリが不完全なファイルをスキャンすることはありません。
コピーに失敗した場合は pending 行が削除され、孤立レコードは残りません。

**ホストアプリ側の対応：不要です。** ライブラリは authority が
`${applicationId}.photochoice.fileprovider` の `FileProvider` を自前で宣言します。これはホストの
`applicationId` から導出されるため、他の利用者と衝突することはありません。カメラ権限も不要です。
撮影は `ACTION_IMAGE_CAPTURE` 経由で行われ、権限はカメラアプリ側が保持します。

> カメラアプリがインストールされていない場合、タイルをタップするとクラッシュせずメッセージが表示されます。
>
> アプリ自身の Manifest に `<uses-permission android:name="android.permission.CAMERA" />` を宣言している場合、
> Android はその権限が許可されるまでこの Intent の使用を許しません。これはプラットフォームの規則であり、
> ライブラリの要件ではありません。

撮影後の動作：

| モード | 動作 |
|--------|------|
| 複数 | 写真が自動選択されます。`selectCount` に達している場合は上限メッセージが表示され、写真はギャラリーに残ります |
| 単一 + クロップ有効 | そのままクロップ画面へ。クロップをキャンセルするとリストが更新され、写真はグリッドに残ります |
| 単一 + クロップ無効 | リストとアルバムデータの更新のみ。自動選択はしません |

ユーザーが閲覧中のアルバムは変更されず、リストとアルバム集計だけが更新されます。
そのアルバムが「カメラ」でない場合、新しい写真は切り替え後に表示されます。

`mediaType` が `VIDEO` のとき、カメラタイルは自動的に非表示になります（`effectiveShowCamera`）。
撮影した静止画は動画のみのリストには決して現れないため、入口自体を提供しません。

### Motion Photo / Live Photo

ライブラリは **Motion Photo、Google Motion Photo、Samsung のモーションフォト**、および短い動画を埋め込んだ
同種の JPEG/HEIC ファイルをモーションフォトとして扱います。これらは全工程を通じて `IMAGE` 型のままです。

**グリッドでは**

- サムネイルの左下に **LIVE** バッジが表示されます。
- **ページングは決してブロックされません。** ページの `load` は MediaStore の `IS_MOTION_PHOTO`
  （API 34+）を同期的に読むだけで、XMP スニッフは非同期に実行されます。
- **インデックスは永続化されます。** スキャン結果は構成変更とプロセス死を越えて保持されるため、
  開くたびに再スニッフすることはありません。
- **ビューポートが優先されます。** 表示中とプリフェッチ範囲だけを対象とする高優先度のスニッフチャネルが
  あるため、高速スクロールが全履歴のキューに詰まることはありません。
- `IS_MOTION_PHOTO` を提供しない OEM 端末（一部の機種では珍しくありません）では、バッジは非同期の
  XMP ヘッダー/フッタースニッフに依存するため、画面への初回表示がわずかに遅れることがあります
  （通常は数百ミリ秒以内）。

**フルスクリーンプレビューでは**

- LIVE バッジはトップバーの下に表示されます。
- **長押し**で埋め込み動画を再生し、**離す**と停止します。ピンチやズームで誤って再生が止まることはありません。
- 表示開始時に埋め込み MP4 をバックグラウンドで検出・プリロードし、`cacheDir/photo_choice_motion/` に
  キャッシュします。

**圧縮が有効な場合**、プレビューで選択できます。

- **動きを保持**（既定）— 元の URI を返し、圧縮せず動きを保持します。
- **静止画としてエクスポート** — JPEG 圧縮を行い、動きを破棄します。

---

## アーキテクチャとパフォーマンス

### ページング

**MediaStore のキーセット**（`DATE_ADDED` + `_ID`）上で **Paging 3** を使用します。
Cursor の全走査は行いません。

| パラメータ | 値 |
|------------|-----|
| 初回ロード | 固定 500 件を、行単位に切り上げ |
| ページサイズ | `spanCount × 25` 件 |
| プリフェッチ距離 | `spanCount × 35` 件（約 3 画面分） |
| メモリ上限 | **なし。** `maxSize` は意図的に設定していません |

`maxSize` は意図的に削除されました。最も遠いページを破棄するとページの再充填が壊れ、
プレビューの総数が正しくなくなるためです。ページの `load` は XMP 解析を一切行わず、
これがコールドスタートと高速スクロールを滑らかに保っています。

### Motion Photo パイプライン

```
MediaStore ページロード
    ├─ 同期：API 34+ の IS_MOTION_PHOTO 一括取得 → MediaFile.isMotionPhoto
    └─ 非同期（ノンブロッキング）：
           ├─ アルバムを開く：warmAlbumFromMediaStore
           ├─ ビューポートチャネル：表示中 + プリフェッチ、高優先度の XMP スニッフ
           └─ バックグラウンドチャネル：低優先度のプリフェッチ範囲
```

実装は `data/motion/` 配下：`MotionPhotoDetector`、`MotionPhotoListEnricher`、
`MotionPhotoXmpSniffer`、`MotionPhotoVideoResolver`。

### サンドボックスディレクトリ

| ディレクトリ | 内容 | 保持ポリシー |
|--------------|------|--------------|
| `cacheDir/photo_choice/` | 圧縮とクロップの出力 | 24 時間スイープ。`cleanup()` で消去 |
| `cacheDir/photo_choice_motion/` | 抽出した Motion Photo クリップ | 24 時間スイープに加え、150 MB / 50 ファイルの上限 |
| `cacheDir/photo_choice_camera/` | 撮影の一時ファイル | 撮影ごとに削除。24 時間スイープはバックストップ |

### 主要な依存関係

サムネイルとプレビュー画像に **Glide** · グリッドに **Paging 3** · 動画と Motion Photo の再生に
**Media3 ExoPlayer** · プレビューのページングに **ViewPager2**。

---

## 公開 API の範囲

サポート対象かつ難読化に耐える公開 API は以下だけです。これらは `consumer-rules.pro` で keep されています。

`PhotoChoice` · `PhotoChoice.Builder` · `PhotoChoiceContract` · `PhotoChoiceResult` ·
`config.**` 配下すべて

その他のクラスは Kotlin の可視性としては public であり呼び出すこともできますが（`CameraHelper`、
`CompressHelper`、`SandboxCleaner` など）、それらは**内部実装の詳細**です。
セマンティックバージョニングの対象外であり、どのリリースでも変更・削除される可能性があります。
`PermissionHelper` だけが例外で、上記のとおりホストからの利用を想定して文書化されています。

`PhotoChoiceActivity`、`PreviewActivity`、`CropActivity` を直接起動しないでください。

### 設定の安全性

不正な入力は例外ではなくサニタイズで処理されるため、設定ミスでライブラリがクラッシュすることはありません。

| フィールド | ルール |
|------------|--------|
| `selectCount` | `1..9` の範囲内ならそのまま、範囲外なら**`1` にリセット** |
| `spanCount` | `2..6` にクランプ |
| `minVideoDurationMs` / `maxVideoDurationMs` | min > max なら入れ替え。min の下限は `0` |
| `minImageSize` / `maxImageSize` | min > max なら入れ替え。両方とも下限は `0` |
| `cropConfig.enabled` | 単一選択**かつ** `MediaType.IMAGE` が必要（`effectiveCropEnabled`） |
| `showCamera` | `MediaType.VIDEO` モードでは強制的に無効（`effectiveShowCamera`） |

`PhotoChoiceConfig` はこれらの境界を定数として公開しています — `SELECT_COUNT_MIN` / `SELECT_COUNT_MAX`、
`SPAN_COUNT_MIN` / `SPAN_COUNT_MAX` — さらに `sanitized*` と `effective*` の派生プロパティも提供するため、
実効値を自前の UI に反映できます。

---

## プロジェクト構成

```
photo_choice/
├── photo-choice/                    # ライブラリ
│   └── src/main/java/com/google/photochoice/
│       ├── PhotoChoice.kt           # Builder のエントリポイント、forResult()
│       ├── PhotoChoiceContract.kt   # ActivityResultContract（推奨）
│       ├── PhotoChoiceResult.kt
│       ├── config/                  # PhotoChoiceConfig、MediaType、ThemeMode、Crop/CompressConfig
│       ├── data/
│       │   ├── model/
│       │   └── motion/              # Motion Photo の検出、XMP スニッフ、クリップ抽出
│       ├── ui/
│       │   ├── grid/
│       │   ├── album/
│       │   ├── crop/                # CropActivity
│       │   ├── preview/             # PreviewActivity、長押しでのライブ再生
│       │   └── widget/
│       ├── util/                    # PermissionHelper、CameraHelper、CompressHelper、SandboxCleaner
│       └── viewmodel/
├── sample/                          # 全オプションを網羅したデモアプリ
├── docs/
│   ├── demo.mp4                     # ウォークスルー動画
│   ├── demo-poster.png              # 動画のポスター（ライト / ダーク）
│   ├── hero-light.png               # README ヘッダー（ライト / ダーク）
│   ├── qr-sample-apk.png            # サンプル APK の QR コード
│   └── assets/                      # 上記すべてを生成
├── CHANGELOG.md
└── README.md                        # 他に 7 言語の翻訳
```

### ビルドと検証

```bash
./gradlew :photo-choice:assembleDebug
./gradlew :sample:installDebug
./gradlew test
./gradlew lint
```

README の画像とウォークスルー動画はいずれも生成物です。画面内のスマートフォン UI は
イラストなので、実際のフォトライブラリがリポジトリに入ることはありません。

```bash
python docs/assets/make_assets.py       # header image, video poster, QR code
python docs/assets/make_demo_video.py   # the walkthrough video itself
python docs/assets/verify_readmes.py    # structural checks across all 8 READMEs
```

---

## 統合チェックリスト

- [ ] 依存関係を追加した — JitPack または `implementation(project(":photo-choice"))`
- [ ] ホストの Manifest にメディア読み取り権限を宣言した
- [ ] 起動前に `PermissionHelper` で実行時権限をリクエストした
- [ ] 起動 API を選んだ — **`PhotoChoiceContract`**（プロセス死に強い）または `forResult` コールバック
- [ ] `null`（キャンセル）と `PhotoChoiceResult`（成功）を分けて処理した
- [ ] クロップ/圧縮の出力を**消費し終えてから** `PhotoChoice.cleanup(context)` を呼んだ
- [ ] Motion Photo + 圧縮では、**動きを保持 / 静止画としてエクスポート**の選択を理解した

---

## 制限事項

- データソースは**公開 MediaStore メディア**のみで、プライベートまたは非表示のフォルダーは含みません。
- UI とアクセントカラーはカスタマイズできません。`ThemeMode` のライト / ダーク / システム追従のみです。
- 動画の長さフィルターはリスト表示にのみ影響し、ディスク上のファイルは変更しません。
- `MediaType.ALL` と複数選択ではクロップを利用できません。
- LIVE バッジは `IS_MOTION_PHOTO` が設定されていれば（API 34+）ほぼ即座に表示されますが、
  DB フラグを持たない OEM 端末ではわずかに遅れます。プレビューの長押しでは、フラグのないモーションフォトも
  XMP を含む完全な検出で認識されます。

## 問題報告

issue を作成する際は、**Android バージョン、端末モデル、設定のコード片、期待する動作と実際の動作**を
記載してください。Motion Photo の不具合については、システムギャラリーがその項目をライブとして認識するか
どうかも併せてお知らせください。
