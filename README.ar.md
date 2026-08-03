<div dir="rtl">

<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://github.com/Hu12037102/photo_choice/raw/master/docs/hero-dark.png">
    <img src="docs/hero-light.png" width="860" alt="PhotoChoice — منتقي صور لنظام Android: شبكة، ألبومات، معاينة بملء الشاشة، قص، ضغط، Motion Photo">
  </picture>
</p>

<p align="center">
  <a href="https://jitpack.io/#Hu12037102/photo_choice"><img src="https://img.shields.io/jitpack/version/com.github.Hu12037102/photo_choice?style=flat-square&label=JitPack&color=C8763C" alt="JitPack"></a>
  <img src="https://img.shields.io/badge/minSdk-29-1D1D1F?style=flat-square" alt="minSdk 29">
  <img src="https://img.shields.io/badge/language-Kotlin-1D1D1F?style=flat-square" alt="Kotlin">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-Apache%202.0-1D1D1F?style=flat-square" alt="Apache 2.0"></a>
</p>

<p align="center">
  <sub>
    <a href="README.md">English</a> ·
    <a href="README.zh-CN.md">简体中文</a> ·
    <a href="README.ja.md">日本語</a> ·
    <a href="README.ko.md">한국어</a> ·
    <a href="README.fr.md">Français</a> ·
    <a href="README.es.md">Español</a> ·
    <a href="README.ru.md">Русский</a>
  </sub>
</p>

<br>

مكتبة منتقي الصور لنظام Android: شبكة اختيار متعدد، تبديل الألبومات، معاينة بملء الشاشة، بلاطة كاميرا
اختيارية، قص صورة واحدة، ضغط اختياري، واكتشاف **Motion Photo / Live Photo** مع التشغيل داخل المعاينة.
يتم التكامل عبر **واجهة Builder** — وليس عبر تشغيل الأنشطة الداخلية للمكتبة مباشرةً.

<br>

## عرض توضيحي

<p align="center">
  <video src="https://github.com/Hu12037102/photo_choice/raw/master/docs/demo.mp4" poster="https://github.com/Hu12037102/photo_choice/raw/master/docs/demo-poster.png" width="820" controls muted playsinline>
    <a href="https://github.com/Hu12037102/photo_choice/raw/master/docs/demo.mp4">
      <picture>
        <source media="(prefers-color-scheme: dark)" srcset="https://github.com/Hu12037102/photo_choice/raw/master/docs/demo-poster-dark.png">
        <img src="docs/demo-poster.png" width="820" alt="مشاهدة العرض التوضيحي لـ PhotoChoice">
      </picture>
    </a>
  </video>
</p>

<p align="center">
  <sub>الشبكة والألبومات · ترتيب الاختيار · التاريخ أثناء التمرير · بلاطة الكاميرا · معاينة بملء الشاشة<br>
  تشغيل الفيديو · Motion Photo · القص · ضغط JPEG · فاتح / داكن / حسب النظام</sub>
</p>

<br>

<p align="center">
  <a href="https://huxiaobai.oss-cn-shanghai.aliyuncs.com/open/sample-release.apk"><img src="docs/qr-sample-apk.png" width="200" alt="امسح الرمز لتثبيت تطبيق العينة PhotoChoice"></a>
</p>

<p align="center">
  <b><a href="https://huxiaobai.oss-cn-shanghai.aliyuncs.com/open/sample-release.apk">تنزيل تطبيق العينة</a></b><br>
  <sub>امسح الرمز بهاتفك، أو اضغط للتنزيل · <code>sample-release.apk</code> · Android 10+</sub>
</p>

---

## أبرز الميزات

| المجال | ما تحصل عليه |
|--------|---------------|
| أنواع الوسائط | صور فقط / فيديو فقط / صور + فيديو |
| الاختيار | فردي أو متعدد (`selectCount` 1–9)، مع شارات ترتيب الاختيار |
| الألبومات | تجميع حاويات MediaStore مع قائمة منسدلة للتبديل |
| الشبكة | عدد أعمدة قابل للضبط (2–6)، مصغّرات مربّعة، Paging 3 |
| ترويسة التاريخ | تعرض تاريخ المنطقة الظاهرة أثناء التمرير |
| الكاميرا | بلاطة كاميرا اختيارية في الخلية الأولى؛ تُحفظ الصور في `DCIM/Camera` |
| المعاينة | تمرير بملء الشاشة، وتشغيل فيديو مدمج |
| Motion Photo | شارة LIVE في الشبكة؛ الضغط المطوّل في المعاينة يشغّل المقطع المدمج |
| القص | اختيار فردي + وضع الصور؛ نشاط `CropActivity` مستقل |
| الضغط | تغيير الحجم وجودة JPEG عند الإنهاء، مع حلقة إعادة محاولة نحو حجم مستهدف |
| السمة | فاتح / داكن / حسب النظام، تُطبّق على مستوى النشاط — دون تغيير الوضع العام للتطبيق المُضيف |
| واجهة التشغيل | **`PhotoChoiceContract`** (موصى بها، بلا حالة ساكنة) أو استدعاء `forResult` |
| الأمان عند موت العملية | وضع Contract يصمد أمام إعادة إنشاء النشاط وموت العملية |

- **الحزمة** `com.google.photochoice` · **الإصدار** `1.1.0` ([CHANGELOG](CHANGELOG.md))
- **الحد الأدنى لـ SDK** 29 (Android 10، Scoped Storage — قراءة الوسائط العامة دون إذن كتابة قديم)
- **compileSdk** 36 · **Java** 11 · **Kotlin** · [Apache License 2.0](LICENSE)

---

## التثبيت

### الخيار أ — JitPack (موصى به)

أضف مستودع JitPack إلى ملف **`settings.gradle.kts`** في التطبيق المُضيف. يستخدم هذا المشروع
`FAIL_ON_PROJECT_REPOS`، لذا يجب وضع المستودع داخل `dependencyResolutionManagement` وليس داخل الوحدة:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

ثم أعلن التبعية في وحدة التطبيق أو وحدة الميزة:

```kotlin
dependencies {
    implementation("com.github.Hu12037102:photo_choice:1.1.0")
}
```

> يبني JitPack ملف AAR عند الطلب من المصدر الموسوم، لذا قد يستغرق أول طلب لوسم جديد نحو دقيقة.

### الخيار ب — وحدة المصدر

```kotlin
// settings.gradle.kts
include(":photo-choice")

// app/build.gradle.kts
dependencies {
    implementation(project(":photo-choice"))
}
```

---

## البدء السريع

### 1. إعلان الأذونات

تُعلن المكتبة أذونات قراءة الوسائط في ملف Manifest الخاص بها، لكن **على التطبيق المُضيف إعلان الأذونات
نفسها** وطلبها في وقت التشغيل.

| إصدار Android | الأذونات |
|----------------|-----------|
| API 34+ | `READ_MEDIA_IMAGES`، `READ_MEDIA_VIDEO`، `READ_MEDIA_VISUAL_USER_SELECTED` — المنح الجزئي يُعدّ صالحًا للاستخدام |
| API 33 | `READ_MEDIA_IMAGES`، `READ_MEDIA_VIDEO` |
| API 29–32 | `READ_EXTERNAL_STORAGE` |

يوفّر `PermissionHelper` القائمة والتحقق من المنح:

```kotlin
import com.google.photochoice.util.PermissionHelper

if (PermissionHelper.hasMediaPermission(context)) {
    openPhotoChoice()
} else {
    requestPermissionLauncher.launch(PermissionHelper.requiredMediaPermissions())
}
```

تُعيد `requiredMediaPermissions()` المجموعة **الكاملة** لمستوى SDK قيد التشغيل، ولا تُضيّقها **إطلاقًا**
بناءً على `mediaType`. في API 34+ تُعيد `hasMediaPermission()` القيمة `true` إذا مُنح **أيّ** من الثلاثة
(الوصول الجزئي إلى الصور يُحتسب)؛ أما في API 33 فيلزم إذنا الصور والفيديو **معًا**.

### 2. تشغيل المنتقي — Contract (موصى به)

```kotlin
import com.google.photochoice.PhotoChoice
import com.google.photochoice.PhotoChoiceContract
import com.google.photochoice.config.MediaType

val launcher = registerForActivityResult(PhotoChoiceContract()) { result ->
    if (result == null) return@registerForActivityResult   // أُلغي
    result.uris.forEach { uri ->
        // معرّف content:// أو file://، بترتيب الاختيار
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

إن `PhotoChoiceContract` هو `ActivityResultContract<PhotoChoiceConfig, PhotoChoiceResult?>`. تنتقل
الإعدادات ضمن extra في الـ Intent وتعود النتيجة عبر `setResult()` — وكلاهما يديره النظام، لذا يصمد هذا
المسار أمام إعادة إنشاء النشاط وموت العملية دون أي حالة ساكنة. **يُفضّل استخدامه في كل بيئات الإنتاج.**

### 3. البديل — واجهة الاستدعاء (قديمة)

من `FragmentActivity` (أو `AppCompatActivity`):

```kotlin
PhotoChoice.with(this)
    .selectCount(9)
    .mediaType(MediaType.IMAGE)
    .forResult(this) { result ->
        if (result == null) return@forResult   // أُلغي
        result.uris.forEach { uri -> /* ... */ }
    }
```

> **تحتفظ واجهة الاستدعاء بالـ callback في حقل ساكن.** لذلك فهي لا تصمد أمام إعادة إنشاء النشاط المُضيف
> أو موت العملية: إذا قُتل المُضيف أثناء عمل المنتقي، يُفقد الاستدعاء ويخرج المنتقي بهدوء دون نتيجة.
> استخدم Contract أعلاه عندما يكون ذلك مهمًا.

---

## الإعدادات

يُعيد كل setter كائن `Builder`. الاستدعاءات الطرفية هي `buildConfig()` (لأجل `PhotoChoiceContract`) أو
`forResult(activity, callback)` أو `build()` إذا أردت كائن `PhotoChoice` نفسه.

| الدالة | النوع | الافتراضي | ملاحظات |
|--------|-------|------------|----------|
| `selectCount` | `Int` | `9` | `1` = فردي، `>1` = متعدد. أي قيمة خارج `1..9` **تعود إلى `1`** ولا تُقرّب إلى أقرب حد |
| `mediaType` | `MediaType` | `IMAGE` | `IMAGE` / `VIDEO` / `ALL` |
| `spanCount` | `Int` | `3` | أعمدة الشبكة، محصورة ضمن `2..6` |
| `showCamera` | `Boolean` | `true` | بلاطة الكاميرا في الخلية الأولى — راجع [التقاط الصور بالكاميرا](#التقاط-الصور-بالكاميرا) |
| `minImageSize` | `Long` | `0` | أصغر حجم لملف الصورة بالبايت؛ يستبعد الأيقونات الصغيرة. للصور فقط |
| `maxImageSize` | `Long` | `Long.MAX_VALUE` | أكبر حجم لملف الصورة بالبايت. للصور فقط |
| `minVideoDuration` | `Long` | `0` | أقل مدة للفيديو بالمللي ثانية |
| `maxVideoDuration` | `Long` | `60_000` | أقصى مدة للفيديو بالمللي ثانية |
| `themeMode` | `ThemeMode` | `FOLLOW_SYSTEM` | `LIGHT` / `DARK` / `FOLLOW_SYSTEM`، تُطبّق على مستوى النشاط |
| `cropConfig` | `CropConfig` | `CropConfig()` | راجع أدناه |
| `compressConfig` | `CompressConfig` | `CompressConfig()` | راجع أدناه |

> **لـ `spanCount` قيمتان افتراضيتان مختلفتان.** الافتراضي في `Builder` هو `3`، بينما الافتراضي لمعامل
> المُنشئ في `PhotoChoiceConfig` هو `4`. فإذا أنشأت `PhotoChoiceConfig` مباشرةً دون المرور بـ Builder
> فستحصل على 4 أعمدة.

تتجاهل `PhotoChoice.with(context)` حاليًا الوسيط `context`؛ وقد أُبقي عليه من أجل توافق الواجهة وصيغة
استدعاء طبيعية.

### القص — `CropConfig`

```kotlin
import com.google.photochoice.config.CropConfig
import com.google.photochoice.config.CropAspectRatio

.cropConfig(
    CropConfig(
        enabled = true,
        aspectRatio = CropAspectRatio.SQUARE,
        maxWidth = 0,      // 0 = بلا حد
        maxHeight = 0,     // 0 = بلا حد
    )
)
```

| الحقل | الافتراضي | ملاحظات |
|-------|------------|----------|
| `enabled` | `false` | يفتح `CropActivity` المستقل بعد الاختيار |
| `aspectRatio` | `ORIGINAL` | `ORIGINAL` / `SQUARE` / `RATIO_3_4` / `RATIO_4_3` / `RATIO_9_16` / `RATIO_16_9`؛ يكشف كل ثابت عن `ratio: Float?` (`null` لـ `ORIGINAL`) |
| `maxWidth` | `0` | يحدّ عرض الناتج بالبكسل؛ `0` أو أقل يعني بلا حد |
| `maxHeight` | `0` | يحدّ ارتفاع الناتج بالبكسل؛ `0` أو أقل يعني بلا حد |

لا يعمل القص إلا عندما يكون `selectCount == 1` **و** `mediaType == MediaType.IMAGE`.

> **يعطّل `MediaType.ALL` القص بصمت.** الشرط هو تطابق تام مع `IMAGE` وليس «يتضمّن صورًا»، لذا فإن منتقيًا
> يجمع الصور والفيديو لن يصل أبدًا إلى شاشة القص حتى مع `enabled = true`.

مع الاختيار الفردي والقص مفعّلًا، يؤدي اختيار صورة إلى الانتقال مباشرةً إلى القص ثم إغلاق المنتقي.

### الضغط — `CompressConfig`

عند الضغط على **تم**، تُصغَّر الصور وتُضغط بصيغة JPEG قبل تسليم النتيجة. أما الفيديو وصور GIF وصور
Motion Photo في وضع الإبقاء على الحركة فلا تُضغط أبدًا.

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

| الحقل | الافتراضي | ملاحظات |
|-------|------------|----------|
| `enabled` | `false` | المفتاح الرئيسي |
| `maxWidth` / `maxHeight` | `1280` | حدود الضلع الأطول عند تغيير الحجم |
| `quality` | `80` | جودة JPEG الابتدائية، تُحصر ضمن `1..100` عند الاستخدام |
| `maxFileSizeBytes` | `1_572_864` (نحو 1.5 ميجابايت) | حجم الناتج المستهدف؛ تُخفَّض الجودة تدريجيًا حتى يتحقق |
| `minQuality` | `50` | الحد الأدنى لحلقة إعادة المحاولة — لا تنزل دونه أبدًا |
| `qualityStep` | `10` | مقدار انخفاض الجودة في كل محاولة |
| `skipCompressBaselineLongEdge` | `1280` | عتبة التخطي، الضلع الأطول |
| `skipCompressBaselineShortEdge` | `720` | عتبة التخطي، الضلع الأقصر |
| `skipCompressMaxBytes` | `153_600` (150 كيلوبايت) | عتبة التخطي، حجم الملف |

**تُعاد الصورة كما هي إذا كانت صغيرة بما يكفي أصلًا:** الضلع الأطول ≤ 1280 **و** الأقصر ≤ 720، **أو**
حجم الملف أقل من 150 كيلوبايت. فإعادة ضغط هذه الصور تُفقد الجودة دون توفير يُذكر. أما صور Motion Photo
المُصدَّرة كصور ثابتة فتتجاوز هذا الاستثناء عمدًا وتُضغط دائمًا.

> الناتج دائمًا بصيغة JPEG. صور PNG أو WebP الشفافة تعود بخلفية سوداء.

---

## النتيجة

```kotlin
data class PhotoChoiceResult(
    val uris: List<Uri>,    // المعرّفات المختارة، بترتيب الاختيار
    val paths: List<String> // مسارات محلية قدر الإمكان؛ نص المعرّف إن تعذّر تحديد المسار
)
```

لا يحتوي `paths` على مسارات حقيقية في نظام الملفات إلا للملفات التي أنتجتها المكتبة نفسها (ناتج الضغط أو
القص). أما عناصر MediaStore فتُعيد معرّف `content://` الخاص بها كنص.

| الوسيط | بدون ضغط | مع الضغط |
|--------|-----------|-----------|
| صورة ثابتة | معرّف MediaStore بصيغة `content://` | ملف JPEG بصيغة `file://` في `cacheDir/photo_choice/compress_<uuid>.jpg` |
| صورة صغيرة (دون عتبة التخطي) | معرّف MediaStore بصيغة `content://` | `content://` — دون تعديل |
| فيديو | معرّف MediaStore بصيغة `content://` | دون تعديل |
| GIF | معرّف MediaStore بصيغة `content://` | دون تعديل (الضغط يُفقد الحركة) |
| Live Photo — الإبقاء على الحركة | معرّف MediaStore بصيغة `content://` | دون تعديل (الحركة محفوظة) |
| Live Photo — التصدير كصورة ثابتة | لا ينطبق | ملف JPEG مضغوط بصيغة `file://` |
| صورة مقصوصة | `file://` في `cacheDir/photo_choice/crop_<timestamp>.jpg` | المثل، ثم تُضغط |

### التنظيف

```kotlin
PhotoChoice.cleanup(context)
```

> **هذا يحذف كل شيء، وليس الملفات القديمة فقط.** تُفرّغ `cleanup()` دون شروط المجلدات
> `cacheDir/photo_choice/` و`cacheDir/photo_choice_motion/` و`cacheDir/photo_choice_camera/`، وتُسقط
> ذاكرة Motion Photo المؤقتة. استدعِها **بعد** الانتهاء من استهلاك النتيجة — فأي معرّف `file://` ما زلت
> تحتفظ به سيتوقف عن العمل.
>
> أما المسح الدوري كل 24 ساعة فهو إجراء داخلي منفصل تُشغّله المكتبة تلقائيًا، ولا حاجة لجدولته بنفسك.

---

## أمثلة

```kotlin
// صور متعددة، حتى 9
PhotoChoice.with(activity)
    .selectCount(9)
    .mediaType(MediaType.IMAGE)
    .spanCount(4)
    .showCamera(true)
    .forResult(activity) { result -> /* ... */ }

// صورة شخصية: اختيار فردي + قص مربع + ضغط
PhotoChoice.with(activity)
    .selectCount(1)
    .mediaType(MediaType.IMAGE)
    .cropConfig(CropConfig(enabled = true, aspectRatio = CropAspectRatio.SQUARE))
    .compressConfig(CompressConfig(enabled = true))
    .forResult(activity) { result -> /* ... */ }

// فيديو فقط، بحد أقصى 60 ثانية
PhotoChoice.with(activity)
    .selectCount(1)
    .mediaType(MediaType.VIDEO)
    .showCamera(false)          // تُخفى تلقائيًا في وضع VIDEO على أي حال
    .maxVideoDuration(60_000L)
    .forResult(activity) { result -> /* ... */ }

// صور + فيديو — لاحظ أن القص غير متاح في وضع ALL
PhotoChoice.with(activity)
    .selectCount(9)
    .mediaType(MediaType.ALL)
    .maxVideoDuration(60_000L)
    .forResult(activity) { result -> /* ... */ }
```

---

## تفاصيل السلوك

### الاختيار الفردي مقابل المتعدد

| الوضع | واجهة الشبكة | التفاعل |
|-------|---------------|----------|
| متعدد (`selectCount > 1`) | مربع اختيار + شارة الترتيب | اضغط المربع للتبديل؛ اضغط المصغّرة للمعاينة |
| فردي (`selectCount == 1`) | **يُخفي** مربع الاختيار وشارة الترتيب وطبقة التعطيل | اضغط المصغّرة ← المعاينة، أو القص إن كان مفعّلًا |

لا توجد في الاختيار الفردي حالة وسيطة «مُختار»، ولهذا تختفي عناصر واجهة الاختيار تمامًا بدل أن تُعطَّل.

### التقاط الصور بالكاميرا

مع `showCamera(true)` (الافتراضي)، تكون الخلية الأولى في الشبكة مدخلًا للكاميرا.

| البند | القيمة |
|-------|---------|
| المجلد | `DCIM/Camera` — مجلد الكاميرا العام، أي ألبوم «الكاميرا» في معرض النظام |
| اسم الملف | `IMG` + آخر 8 أرقام من الطابع الزمني + 4 أرقام عشوائية + `.jpg`، مثل `IMG064001234821.jpg` |
| الصيغة | JPEG |
| المنطقة المؤقتة | `cacheDir/photo_choice_camera/`، ينظّفها منظّف الصندوق الرملي |

تُدرَج الصور عبر بروتوكول المرحلتين `IS_PENDING` في MediaStore: لا يصبح السجل مرئيًا لمعرض النظام إلا بعد
كتابة البايتات كاملةً، فلا تفحص التطبيقات الأخرى ملفًا ناقصًا أبدًا. وإذا فشل النسخ، يُحذف السجل المعلّق
بدل أن يبقى يتيمًا.

**ما الذي يلزم التطبيق المُضيف فعله: لا شيء.** تُعلن المكتبة `FileProvider` خاصًا بها بمعرّف
`${applicationId}.photochoice.fileprovider` — مشتقًا من `applicationId` الخاص بالمُضيف، فلا يمكن أن
يتعارض مع أي مُدمِج آخر. ولا يلزم إذن كاميرا كذلك: يمرّ الالتقاط عبر `ACTION_IMAGE_CAPTURE`، وتطبيق
الكاميرا هو من يملك الإذن.

> إذا لم يكن أي تطبيق كاميرا مثبّتًا، فسيعرض الضغط على البلاطة رسالة بدل أن ينهار التطبيق.
>
> وإذا أعلن تطبيقك `<uses-permission android:name="android.permission.CAMERA" />` في ملف Manifest الخاص
> به، فسيشترط Android منح هذا الإذن قبل استخدام النية. هذه قاعدة من المنصّة، لا متطلَّب من المكتبة.

بعد الالتقاط:

| الوضع | السلوك |
|-------|---------|
| متعدد | تُختار الصورة تلقائيًا. وإذا بُلغ `selectCount` مسبقًا، تظهر رسالة بلوغ الحد وتبقى الصورة في المعرض |
| فردي + القص مفعّل | ينتقل مباشرةً إلى شاشة القص؛ وإلغاء القص يُحدّث القائمة فتبقى الصورة ظاهرة في الشبكة |
| فردي + القص معطّل | يُحدّث القائمة وبيانات الألبوم فقط — دون اختيار تلقائي |

لا يتغيّر الألبوم الذي يتصفحه المستخدم؛ إذ تُحدَّث القائمة وتجميعات الألبومات فحسب. وإذا لم يكن ذلك
الألبوم هو «الكاميرا»، فستظهر الصورة الجديدة بعد التبديل إليه.

عندما يكون `mediaType` هو `VIDEO`، تُخفى بلاطة الكاميرا تلقائيًا (`effectiveShowCamera`): فالصورة الثابتة
الملتقطة لا يمكن أن تظهر في قائمة مقتصرة على الفيديو، لذا لا يُعرض المدخل أصلًا.

### Motion Photo / Live Photo

تتعامل المكتبة مع **Motion Photo وGoogle Motion Photo والصور المتحركة من Samsung** وما شابهها من ملفات
JPEG/HEIC التي تتضمّن فيديو قصيرًا على أنها صور متحركة. وتظل هذه الملفات من نوع `IMAGE` طوال المسار.

**في الشبكة**

- تظهر شارة **LIVE** أسفل يسار المصغّرة.
- **لا يُحجب التصفح أبدًا.** لا تقرأ عملية `load` للصفحة سوى `IS_MOTION_PHOTO` من MediaStore بشكل متزامن
  (API 34+)؛ أما فحص XMP فيجري بشكل غير متزامن.
- **الفهرس دائم.** تصمد نتائج الفحص أمام تغيّرات الإعداد وموت العملية، فلا يُعاد الفحص عند كل فتح.
- **الأولوية للمنطقة الظاهرة.** توجد قناة فحص مخصّصة عالية الأولوية تغطي المنطقة الظاهرة ونافذة الجلب
  المسبق، فلا يعلق التمرير السريع خلف طابور يشمل السجل كاملًا.
- على أجهزة الشركات المصنّعة التي لا توفّر `IS_MOTION_PHOTO` — وهو أمر شائع في بعض الطُرز — تعتمد الشارات
  على فحص XMP غير المتزامن لبداية الملف ونهايته، لذا قد يتأخر ظهورها الأول على الشاشة قليلًا، وعادةً أقل
  من بضع مئات من المللي ثانية.

**في المعاينة بملء الشاشة**

- تقع شارة LIVE أسفل الشريط العلوي.
- **الضغط المطوّل** يشغّل الفيديو المدمج، و**الإفلات** يوقفه. ولن يوقف التكبير أو التصغير التشغيل عن طريق
  الخطأ.
- عند الدخول، يُكتشف ملف MP4 المدمج ويُحمَّل مسبقًا في الخلفية، ويُخزَّن في `cacheDir/photo_choice_motion/`.

**عند تفعيل الضغط**، تعرض المعاينة خيارين:

- **الإبقاء على الحركة** (الافتراضي) — يُعيد المعرّف الأصلي دون ضغط.
- **التصدير كصورة ثابتة** — ضغط JPEG مع إسقاط الحركة.

---

## البنية والأداء

### التصفح (Paging)

**Paging 3 فوق مجموعة مفاتيح MediaStore** (`DATE_ADDED` + `_ID`) — دون مسح كامل للمؤشر.

| المعامل | القيمة |
|---------|---------|
| التحميل الأولي | 500 عنصر ثابتة، مقرّبة لأعلى إلى صف كامل |
| حجم الصفحة | `spanCount × 25` عنصرًا |
| مسافة الجلب المسبق | `spanCount × 35` عنصرًا (نحو 3 شاشات) |
| سقف الذاكرة | **لا يوجد.** لم يُضبط `maxSize` عمدًا |

أُزيل `maxSize` عن قصد: فإسقاط أبعد الصفحات كان يُفسد إعادة تعبئة الصفحات ويجعل إجماليات المعاينة خاطئة.
ولا تُجري عملية `load` للصفحة أي تحليل لـ XMP، وهذا ما يُبقي البدء البارد والتمرير السريع سلسين.

### مسار Motion Photo

```
تحميل صفحة من MediaStore
    ├─ متزامن: قراءة IS_MOTION_PHOTO دفعةً واحدة في API 34+ ← MediaFile.isMotionPhoto
    └─ غير متزامن (دون حجب):
           ├─ فتح الألبوم: warmAlbumFromMediaStore
           ├─ قناة المنطقة الظاهرة: الظاهر + الجلب المسبق، فحص XMP عالي الأولوية
           └─ القناة الخلفية: نافذة جلب مسبق منخفضة الأولوية
```

التنفيذ ضمن `data/motion/`: `MotionPhotoDetector` و`MotionPhotoListEnricher` و
`MotionPhotoXmpSniffer` و`MotionPhotoVideoResolver`.

### مجلدات الصندوق الرملي

| المجلد | المحتوى | مدة الاحتفاظ |
|--------|----------|---------------|
| `cacheDir/photo_choice/` | نواتج الضغط والقص | مسح كل 24 ساعة؛ وتُفرّغه `cleanup()` |
| `cacheDir/photo_choice_motion/` | مقاطع Motion Photo المستخرجة | مسح كل 24 ساعة، مع سقفَي 150 ميجابايت و50 ملفًا |
| `cacheDir/photo_choice_camera/` | ملفات الالتقاط المؤقتة | تُحذف بعد كل التقاط؛ والمسح كل 24 ساعة شبكة أمان |

### التبعيات الرئيسية

**Glide** للمصغّرات وصور المعاينة · **Paging 3** للشبكة · **Media3 ExoPlayer** لتشغيل الفيديو و
Motion Photo · **ViewPager2** لتصفّح المعاينة.

---

## نطاق الواجهة العامة

هذه الأنواع وحدها هي الواجهة المدعومة والآمنة أمام التشويش — وهي التي يُبقي عليها `consumer-rules.pro`:

`PhotoChoice` · `PhotoChoice.Builder` · `PhotoChoiceContract` · `PhotoChoiceResult` ·
وكل ما تحت `config.**`

أما بقية الأصناف فهي عامة من حيث رؤية Kotlin ويمكن استدعاؤها (`CameraHelper` و`CompressHelper` و
`SandboxCleaner` و`DesignTokens` وغيرها)، لكنها **تفاصيل تنفيذ داخلية**: لا يشملها الإصدار الدلالي وقد
تتغيّر أو تختفي في أي إصدار. الاستثناء الوحيد هو `PermissionHelper`: فهو موثّق أعلاه ومُعدّ لاستخدام
التطبيق المُضيف.

لا تُشغّل `PhotoChoiceActivity` أو `PreviewActivity` أو `CropActivity` مباشرةً أبدًا.

### أمان الإعدادات

تُعالَج المدخلات غير الصالحة بالتنقية لا بإطلاق الاستثناءات، فلا يمكن لإعداد خاطئ أن يُسقط المكتبة:

| الحقل | القاعدة |
|-------|----------|
| `selectCount` | يُحفظ إن كان ضمن `1..9`، وإلا **يُعاد ضبطه إلى `1`** |
| `spanCount` | يُحصر ضمن `2..6` |
| `minVideoDurationMs` / `maxVideoDurationMs` | يُبدَّلان إن كان min > max؛ وحد min الأدنى `0` |
| `minImageSize` / `maxImageSize` | يُبدَّلان إن كان min > max؛ وحدهما الأدنى `0` |
| `cropConfig.enabled` | يتطلب اختيارًا فرديًا **و** `MediaType.IMAGE` (`effectiveCropEnabled`) |
| `showCamera` | يُعطَّل قسرًا في وضع `MediaType.VIDEO` (`effectiveShowCamera`) |

يكشف `PhotoChoiceConfig` عن هذه الحدود كثوابت — `SELECT_COUNT_MIN` / `SELECT_COUNT_MAX` و
`SPAN_COUNT_MIN` / `SPAN_COUNT_MAX` — إلى جانب الخصائص المشتقة `sanitized*` و`effective*`، إن أردت
عكس القيم الفعلية في واجهتك.

---

## هيكل المشروع

```
photo_choice/
├── photo-choice/                    # المكتبة
│   └── src/main/java/com/google/photochoice/
│       ├── PhotoChoice.kt           # نقطة دخول Builder، forResult()
│       ├── PhotoChoiceContract.kt   # ActivityResultContract (موصى به)
│       ├── PhotoChoiceResult.kt
│       ├── config/                  # PhotoChoiceConfig، MediaType، ThemeMode، Crop/CompressConfig
│       ├── data/
│       │   ├── model/
│       │   └── motion/              # اكتشاف Motion Photo، فحص XMP، استخراج المقطع
│       ├── ui/
│       │   ├── grid/
│       │   ├── album/
│       │   ├── crop/                # CropActivity
│       │   ├── preview/             # PreviewActivity، التشغيل الحي بالضغط المطوّل
│       │   └── widget/
│       ├── util/                    # PermissionHelper، CameraHelper، CompressHelper، SandboxCleaner
│       └── viewmodel/
├── sample/                          # تطبيق عرض يغطي كل الخيارات
├── docs/
│   ├── demo.mp4                     # فيديو العرض التوضيحي
│   ├── demo-poster.png              # ملصق الفيديو (فاتح / داكن)
│   ├── hero-light.png               # ترويسة README (فاتح / داكن)
│   ├── qr-sample-apk.png            # رمز QR لملف APK التجريبي
│   └── assets/                      # يُولّد كل ما سبق
├── CHANGELOG.md
└── README.md                        # إضافةً إلى 7 ترجمات
```

### البناء والتحقق

```bash
./gradlew :photo-choice:assembleDebug
./gradlew :sample:installDebug
./gradlew test
./gradlew lint
```

صور README وفيديو العرض التوضيحي كلاهما مُولَّد برمجيًا: واجهات الهاتف في اللقطات رسوم
توضيحية، لذا لا تدخل أي مكتبة صور حقيقية إلى المستودع.

```bash
python docs/assets/make_assets.py       # header image, video poster, QR code
python docs/assets/make_demo_video.py   # the walkthrough video itself
python docs/assets/verify_readmes.py    # structural checks across all 8 READMEs
```

---

## قائمة التحقق للتكامل

- [ ] أُضيفت التبعية — عبر JitPack أو `implementation(project(":photo-choice"))`
- [ ] أُعلنت أذونات قراءة الوسائط في ملف Manifest الخاص بالمُضيف
- [ ] طُلب الإذن في وقت التشغيل قبل الإطلاق، عبر `PermissionHelper`
- [ ] اختيرت واجهة التشغيل — **`PhotoChoiceContract`** (تصمد أمام موت العملية) أو استدعاء `forResult`
- [ ] عُولجت الحالة `null` (الإلغاء) بمعزل عن `PhotoChoiceResult` (النجاح)
- [ ] استُدعيت `PhotoChoice.cleanup(context)` **بعد** استهلاك نواتج القص/الضغط
- [ ] فُهم خيار **الإبقاء على الحركة / التصدير كصورة ثابتة** عند الجمع بين Motion Photo والضغط

---

## القيود

- مصدر البيانات هو **وسائط MediaStore العامة** فقط — دون المجلدات الخاصة أو المخفية.
- لا يمكن تخصيص الواجهة وألوان التمييز؛ يتوفر `ThemeMode` فاتح / داكن / حسب النظام فقط.
- تؤثر مرشّحات مدة الفيديو على العرض في القائمة فقط، لا على الملفات المخزّنة.
- القص غير متاح في `MediaType.ALL` ولا في الاختيار المتعدد.
- تظهر شارات LIVE فورًا تقريبًا عندما يكون `IS_MOTION_PHOTO` مضبوطًا (API 34+)، لكنها تتأخر قليلًا على
  أجهزة الشركات المصنّعة التي تفتقر إلى هذا الحقل في قاعدة البيانات. ومع ذلك يظل الضغط المطوّل في المعاينة
  يكتشف الصور المتحركة غير الموسومة عبر اكتشاف كامل يشمل XMP.

## الإبلاغ عن المشاكل

عند فتح تذكرة، يُرجى تضمين **إصدار Android وطراز الجهاز ومقتطف الإعدادات والسلوك المتوقع مقابل السلوك
الفعلي**. وبالنسبة لمشاكل Motion Photo، يُرجى الإشارة أيضًا إلى ما إذا كان معرض النظام يتعرّف على العنصر
باعتباره صورة حية.

</div>
