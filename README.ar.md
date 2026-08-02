<div dir="rtl">

# PhotoChoice

[English Documentation](README.md) | [简体中文文档](README.zh-CN.md) | [日本語ドキュメント](README.ja.md) | [한국어 문서](README.ko.md) | [Documentation en français](README.fr.md) | [Documentación en español](README.es.md) | [Документация на русском](README.ru.md)

مكتبة منتقي الصور لنظام Android: شبكة اختيار متعدد، تبديل الألبومات، معاينة بملء الشاشة، بلاطة كاميرا اختيارية، قص صورة واحدة، ضغط اختياري، واكتشاف **Motion Photo / Live Photo** مع التشغيل داخل المعاينة. التكامل عبر **واجهة Builder** — لا تُشغّل الأنشطة الداخلية مباشرةً.

- **الحزمة**: `com.google.photochoice`
- **الإصدار**: `1.1.0` (راجع [CHANGELOG.md](CHANGELOG.md))
- **الحد الأدنى لـ SDK**: 29 (Android 10، Scoped Storage؛ قراءة الوسائط العامة دون إذن كتابة قديم)
- **هدف SDK**: 36
- **اللغة**: Kotlin
- **الترخيص**: [Apache License 2.0](LICENSE)

---

## الميزات

| الميزة | الوصف |
|--------|-------|
| أنواع الوسائط | صور فقط / فيديو فقط / صور + فيديو |
| الاختيار | فردي أو متعدد (`selectCount` 1–9) |
| الألبومات | تجميع buckets من MediaStore مع مُبدّل منسدل |
| الشبكة | أعمدة قابلة للتكوين (2–6)، صور مصغرة مربعة، Paging 3 |
| رأس التاريخ عند التمرير | يعرض تاريخ المنطقة المرئية أثناء التمرير |
| الكاميرا | بلاطة كاميرا اختيارية في الخلية الأولى؛ تُحفظ الصور في `DCIM/Camera` |
| المعاينة | تمرير بملء الشاشة؛ تشغيل فيديو مضمّن (نقرة للتشغيل، النقرة أثناء التشغيل تُبدّل الواجهة فقط) |
| Motion Photo | شارة LIVE على الشبكة؛ ضغطة مطولة لتشغيل المقطع المضمّن في المعاينة |
| القص | اختيار فردي + وضع الصورة؛ `CropActivity` مستقل |
| الضغط | تغيير حجم JPEG + جودة اختياري عند الإنهاء؛ Live Photos يمكنها الاحتفاظ بالحركة أو التصدير كصورة ثابتة |
| السمة | فاتح / داكن / اتباع النظام (لكل Activity، لا يُعدّل التطبيق المضيف عالمياً) |
| واجهة التشغيل | مسار مزدوج: **`PhotoChoiceContract`** (موصى به، بلا حالة ثابتة) أو استدعاء **`forResult`** |
| أمان إنهاء العملية | وضع Contract يتحمل إعادة إنشاء Activity وإنهاء العملية؛ وضع الاستدعاء له كشف تدهور أنيق |

### الاختيار الفردي مقابل المتعدد

| الوضع | واجهة الشبكة | التفاعل |
|-------|-------------|---------|
| متعدد (`selectCount > 1`) | مربع اختيار + شارة ترتيب الاختيار | النقر على مربع الاختيار للتبديل؛ النقر على الصورة المصغرة للمعاينة |
| فردي (`selectCount = 1`) | **يخفي** مربع الاختيار وشارة الترتيب وطبقة التعطيل | النقر على الصورة المصغرة → معاينة أو قص (إن وُفع) |

---

## التقاط الصور بالكاميرا

عند تفعيل `showCamera(true)` (القيمة الافتراضية)، تكون الخلية الأولى في الشبكة مدخلًا للكاميرا.

### موقع التخزين وتسمية الملفات

| العنصر | القيمة |
|--------|--------|
| المجلد | `DCIM/Camera` (مجلد الكاميرا العام، أي ألبوم «الكاميرا» في النظام) |
| اسم الملف | `IMG` + آخر 8 أرقام من الطابع الزمني + 4 أرقام عشوائية + `.jpg`، مثل `IMG064001234821.jpg` |
| الصيغة | JPEG |

تُدرَج الصور عبر بروتوكول `IS_PENDING` ثنائي المرحلة في MediaStore: لا يصبح السجل مرئيًا لمعرض النظام إلا بعد اكتمال كتابة البايتات، لذا لا يفحص أي تطبيق آخر ملفًا غير مكتمل.

### السلوك بعد الالتقاط

| الوضع | السلوك |
|-------|--------|
| الاختيار المتعدد | تُحدَّد الصورة تلقائيًا؛ وإذا بلغ العدد حد `selectCount` تظهر رسالة «تم بلوغ الحد الأقصى» مع بقاء الصورة محفوظة في المعرض |
| الاختيار المفرد + القص مفعّل | ينتقل مباشرةً إلى شاشة القص؛ وعند إلغاء القص تُحدَّث القائمة لتظل الصورة ظاهرة في الشبكة |
| الاختيار المفرد + القص معطّل | تُحدَّث القائمة وبيانات الألبومات فقط دون تحديد تلقائي (لا توجد حالة وسيطة «محدَّد» في الاختيار المفرد) |

**لا يتم تبديل الألبوم**: يبقى الألبوم الذي يتصفحه المستخدم كما هو، ويُحدَّث فقط محتوى القائمة وبيانات الألبومات. وإذا لم يكن الألبوم الحالي هو «الكاميرا»، فستظهر الصورة الجديدة بعد الانتقال إليه.

### ما الذي يلزم التطبيق المُضيف فعله

**لا شيء.** تُعلن المكتبة عن `FileProvider` خاص بها (بـ authority هو `${applicationId}.photochoice.fileprovider`، مُشتق من `applicationId` الخاص بالتطبيق المُضيف فلا يتعارض مع أي تطبيق آخر يدمج المكتبة)، ولا يلزم إذن الكاميرا — إذ يتم الالتقاط عبر `ACTION_IMAGE_CAPTURE`، ويملك تطبيق الكاميرا نفسه هذا الإذن.

> إذا لم يكن على الجهاز أي تطبيق كاميرا، فإن النقر على بلاطة الكاميرا يعرض رسالة بدلًا من التعطّل.
> وإذا أعلن تطبيقك عن `<uses-permission android:name="android.permission.CAMERA" />` في Manifest الخاص به، فسيشترط Android منح هذا الإذن قبل استخدام الـ intent — وهذه قاعدة من النظام وليست متطلبًا للمكتبة.

### التراجع عند التركيبات غير الصالحة

عندما تكون قيمة `mediaType` هي `VIDEO`، تُخفى بلاطة الكاميرا تلقائيًا (`effectiveShowCamera`): فالصورة الثابتة الملتقَطة لن تظهر أبدًا في قائمة تعرض مقاطع الفيديو فقط، لذا لا يُعرض المدخل أصلًا.

---

## Motion Photo / Live Photo

تتعامل المكتبة مع **Motion Photo وGoogle Motion Photo وصور Samsung المتحركة** وملفات JPEG/HEIC المشابهة ذات فيديو قصير مضمّن كصور متحركة (لا تزال من نوع `IMAGE`).

### قائمة الشبكة

- شارة **LIVE** في أسفل يسار الصور المصغرة.
- **لا يعيق التصفح**: تحميل الصفحة يقرأ فقط `IS_MOTION_PHOTO` من MediaStore (API 34+) بشكل متزامن؛ فحص XMP السريع يعمل بشكل غير متزامن.
- **فهرس دائم**: نتائج المسح تبقى عبر تغييرات الإعدادات وإنهاء العملية؛ بلا إعادة فحص عند كل فتح.
- **أولوية منطقة العرض**: قناة فحص عالية الأولوية للنافذة المرئية + prefetch فقط — التمرير السريع لا يُحجب بطابور تاريخ كامل.
- على OEM التي تتجاهل `IS_MOTION_PHOTO` (شائع في بعض الأجهزة)، تعتمد الشارات على فحص XMP غير المتزامن؛ قد يتأخر الظهور الأول على الشاشة (عادة أقل من بضع مئات من ms).

### معاينة بملء الشاشة

- شارة LIVE أسفل الشريط العلوي.
- **ضغطة مطولة** لتشغيل الفيديو المضمّن، **الرفع** للإيقاف؛ القرص/التكبير لا يوقف التشغيل بالخطأ.
- اكتشاف في الخلفية + تحميل مسبق لـ MP4 المضمّن عند الدخول (مخزّن مؤقتاً تحت `cacheDir/photo_choice_motion/`).

### الضغط والتصدير

عند تفعيل `CompressConfig`، توفر المعاينة **الاحتفاظ بالـ live / التصدير كصورة ثابتة**:

- **الاحتفاظ بالـ live** (افتراضي): يُرجع URI الأصلي، بلا ضغط.
- **التصدير كصورة ثابتة**: ضغط JPEG، تُهمل الحركة.

---

## البدء السريع

### 1. إضافة التبعية

**الطريقة أ — تبعية JitPack (موصى بها).**

[![](https://jitpack.io/v/Hu12037102/photo_choice.svg)](https://jitpack.io/#Hu12037102/photo_choice)

الخطوة 1 — أضف مستودع JitPack إلى `settings.gradle.kts` للمضيف (يستخدم هذا المشروع `FAIL_ON_PROJECT_REPOS`، لذا يجب وضع المستودع في `dependencyResolutionManagement` وليس في `build.gradle.kts` للوحدة):

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

الخطوة 2 — أضف التبعية في `build.gradle.kts` للتطبيق أو وحدة الميزة:

```kotlin
dependencies {
    implementation("com.github.Hu12037102:photo_choice:1.1.0")
}
```

> يبني JitPack ملف AAR عند الطلب من مصدر الـ tag؛ قد يستغرق الطلب الأول لـ tag جديد دقيقة تقريباً.

**الطريقة ب — وحدة المصدر.**
في `settings.gradle.kts` للمضيف:

```kotlin
include(":photo-choice")
```

في `build.gradle.kts` للتطبيق أو وحدة الميزة:

```kotlin
dependencies {
    implementation(project(":photo-choice"))
}
```

### 2. الأذونات

تُعلن المكتبة أذونات قراءة الوسائط في Manifest؛ **يجب على التطبيق المضيف إعلان نفس الأذونات** وطلبها وقت التشغيل.

| إصدار Android | الأذونات |
|---------------|----------|
| API 34+ | `READ_MEDIA_IMAGES`، `READ_MEDIA_VIDEO` (حسب `mediaType`)، `READ_MEDIA_VISUAL_USER_SELECTED` مُعلَن؛ يُعامل المنح الجزئي كقابل للاستخدام |
| API 33 | `READ_MEDIA_IMAGES`، `READ_MEDIA_VIDEO` (حسب `mediaType`) |
| API 29–32 | `READ_EXTERNAL_STORAGE` |

استخدم `PermissionHelper` لقائمة الأذونات والتحقق:

```kotlin
import com.google.photochoice.util.PermissionHelper

if (PermissionHelper.hasMediaPermission(context)) {
    openPhotoChoice()
} else {
    requestPermissionLauncher.launch(PermissionHelper.requiredMediaPermissions())
}
```

راجع **`sample`** / `MainActivity` لمثال كامل.

### 3. تشغيل المنتقي (موصى به: Contract)

استخدم `ActivityResultContract` لتكامل **آمن أمام إنهاء العملية**:

```kotlin
import com.google.photochoice.PhotoChoiceContract
import com.google.photochoice.PhotoChoice
import com.google.photochoice.config.MediaType

val launcher = registerForActivityResult(PhotoChoiceContract()) { result ->
    if (result == null) {
        // ألغى المستخدم
        return@registerForActivityResult
    }
    result.uris.forEach { uri ->
        // URI من نوع content:// أو file://
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

**وضع Contract** يمرّر الإعداد عبر Intent extras، والنتيجة عبر `setResult()` — كلاهما يُدار من النظام، ويتحمل إعادة إنشاء Activity وإنهاء العملية. بلا متغيرات ثابتة. **مفضّل لكل الاستخدام الإنتاجي.**

### 4. بديل: واجهة الاستدعاء (قديم)

من **`FragmentActivity`** (أو `AppCompatActivity`):

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
            // ألغى المستخدم
            return@forResult
        }
        result.uris.forEach { uri ->
            // URI من نوع content:// أو file://
        }
    }
```

**مهم:** واجهة الاستدعاء تستخدم حقولاً ثابتة داخلياً و**لا تتحمل** إعادة إنشاء Activity المضيف أو إنهاء العملية. إذا كان نشاط المنتقي يعمل بينما يُنهى المضيف، يُفقد الاستدعاء ويُغلق المنتقي بلا نتيجة. للموثوقية، استخدم نهج Contract أعلاه.

---

## النتيجة

```kotlin
data class PhotoChoiceResult(
    val uris: List<Uri>,    // URIs المختارة بترتيب الاختيار
    val paths: List<String> // مسارات محلية بأفضل جهد؛ سلسلة URI إن لم تُحل
)
```

| نوع الوسائط | بلا ضغط | مع الضغط |
|-------------|---------|----------|
| صورة ثابتة | URI MediaStore `content://` | JPEG مضغوط `file://` تحت `cacheDir/photo_choice/compress_*.jpg` |
| فيديو | URI MediaStore `content://` | بلا تغيير (الفيديوهات لا تُضغط أبداً) |
| GIF | URI MediaStore `content://` | بلا تغيير (الضغط يفقد الحركة) |
| Live Photo (الاحتفاظ بالـ live) | URI MediaStore `content://` | بلا تغيير (الحركة محفوظة) |
| Live Photo (تصدير ثابت) | غير متاح | JPEG مضغوط `file://` تحت `cacheDir/photo_choice/compress_*.jpg` |

تنظيف ملفات التخزين المؤقت القديمة:

```kotlin
PhotoChoice.cleanup(context)
```

يحذف ملفات sandbox الأقدم من 24 ساعة (استدعِ بعد معالجة النتيجة عند الحاجة).

---

## واجهة Builder

| الدالة | النوع | الافتراضي | الوصف |
|--------|------|---------|-------|
| `selectCount` | `Int` | `9` | `1` = فردي، `>1` = متعدد؛ يُحدّ تلقائياً إلى `1..9` |
| `mediaType` | `MediaType` | `IMAGE` | `IMAGE` / `VIDEO` / `ALL` |
| `spanCount` | `Int` | `3` | أعمدة الشبكة؛ يُحدّ تلقائياً إلى **2–6** |
| `showCamera` | `Boolean` | `true` | عرض بلاطة الكاميرا في الخلية الأولى؛ تُحفظ الصور في `DCIM/Camera` (راجع [التقاط الصور بالكاميرا](#التقاط-الصور-بالكاميرا)) |
| `minImageSize` | `Long` | `0` | الحد الأدنى لحجم ملف الصورة (بايت)، يُصفّي الأيقونات الصغيرة. للصور فقط |
| `maxImageSize` | `Long` | `Long.MAX_VALUE` | الحد الأقصى لحجم ملف الصورة (بايت)، يُصفّي الصور الضخمة. للصور فقط |
| `minVideoDuration` | `Long` | `0` | أقل مدة فيديو (ms)، يُبدّل تلقائياً إن > maxVideoDuration |
| `maxVideoDuration` | `Long` | `60000` | أقصى مدة فيديو (ms)، يُبدّل تلقائياً إن < minVideoDuration |
| `themeMode` | `ThemeMode` | `FOLLOW_SYSTEM` | `LIGHT` / `DARK` / `FOLLOW_SYSTEM` (لكل Activity، ليس عالمياً) |
| `cropConfig` | `CropConfig` | انظر أدناه | إعدادات القص |
| `compressConfig` | `CompressConfig` | انظر أدناه | الضغط عند الإنهاء |

البناء بشكل منفصل لاستخدام Contract:

```kotlin
val config = PhotoChoice.with(context)
    .selectCount(1)
    .buildConfig()  // يُرجع PhotoChoiceConfig مباشرة
```

### القص `CropConfig`

فقط عند **`selectCount = 1`** و**`mediaType` يتضمن صوراً** — يفتح `CropActivity` مستقلاً.
يُعطّل القص تلقائياً (تدهور صامت) لوضع الفيديو فقط أو الاختيار المتعدد.

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

مع اختيار فردي + قص مفعّل، اختيار صورة يذهب مباشرة للقص، ثم يُرجع النتيجة ويُغلق المنتقي.

### الضغط `CompressConfig`

عند **تم**، يُغيّر حجم ويضغط **الصور** بصيغة JPEG قبل الاستدعاء؛ الفيديو وGIF وLive Photos (وضع الاحتفاظ بالـ live) لا تُضغط. Motion Photos تحتفظ بالـ live افتراضياً؛ بدّل إلى ثابت في المعاينة قبل الضغط.

**الاستراتيجية الافتراضية (متوافقة مع إعدادات WeChat Moments الشائعة):**

| المعامل | الافتراضي | الوصف |
|---------|-----------|-------|
| `maxWidth` / `maxHeight` | `1280` | حد أطول ضلع |
| `quality` | `80` | جودة JPEG البدائية |
| `maxFileSizeBytes` | `1572864` (~1.5 ميجابايت) | عند التجاوز، تُخفّض الجودة تدريجياً؛ `0` = بلا حد حجم |
| `minQuality` | `50` | أدنى جودة في تكرار الحجم |
| `qualityStep` | `10` | خطوة خفض الجودة في كل تكرار |

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

> **ملاحظة:** المخرجات دائماً JPEG. PNG/WebP الشفاف سيكون له خلفية سوداء بعد الضغط (سلوك مشابه لـ WeChat وتطبيقات رئيسية أخرى).

---

## أمثلة

### صور متعددة (حتى 9)

```kotlin
PhotoChoice.with(activity)
    .selectCount(9)
    .mediaType(MediaType.IMAGE)
    .spanCount(4)
    .showCamera(true)
    .forResult(activity) { result -> /* ... */ }
```

### صورة شخصية (فردي + قص مربع)

```kotlin
PhotoChoice.with(activity)
    .selectCount(1)
    .mediaType(MediaType.IMAGE)
    .cropConfig(CropConfig(enabled = true, aspectRatio = CropAspectRatio.SQUARE))
    .compressConfig(CompressConfig(enabled = true))
    .forResult(activity) { result -> /* ... */ }
```

### فيديو فقط (بحد أقصى 60 ثانية)

```kotlin
PhotoChoice.with(activity)
    .selectCount(1)
    .mediaType(MediaType.VIDEO)
    .showCamera(false)
    .maxVideoDuration(60_000L)
    .forResult(activity) { result -> /* ... */ }
```

### صور + فيديو

```kotlin
PhotoChoice.with(activity)
    .selectCount(9)
    .mediaType(MediaType.ALL)
    .maxVideoDuration(60_000L)
    .forResult(activity) { result -> /* ... */ }
```

---

## تطبيق العينة

وحدة **`sample`** تعرض كل الخيارات:

```bash
./gradlew :sample:installDebug
```

شغّل **PhotoChoice Sample**، عدّل الإعدادات، افتح المنتقي، واعرض الوسائط المختارة من قائمة النتائج.

---

## البنية والأداء

### التصفح (Paging)

**Paging 3 + keyset MediaStore** (`DATE_ADDED` + `_ID`) — بلا مسح Cursor كامل:

| المعامل | مثال (`spanCount = 3`) |
|---------|------------------------|
| التحميل الأولي | ~15 صف × أعمدة ≈ 45 عنصر |
| حجم الصفحة | ~25 صف × أعمدة ≈ 75 عنصر |
| مسافة prefetch | ~35 صف × أعمدة ≈ 105 عناصر (~3 شاشات) |
| سقف الذاكرة | ~900–1200 عنصر بيانات وصفية (يُسقط أبعد الصفحات) |

تحميل الصفحة **لا يُنفّذ تحليل XMP** — البدء البارد والتمرير السريع يبقيان سلسين.

### مسار Motion Photo

```
تحميل صفحة MediaStore
    ├─ متزامن: API 34+ دفعة IS_MOTION_PHOTO → MediaFile.isMotionPhoto
    └─ غير متزامن (غير حاجز):
           ├─ فتح الألبوم: warmAlbumFromMediaStore
           ├─ قناة viewport: مرئي + prefetch، فحص XMP عالي الأولوية
           └─ قناة خلفية: نافذة prefetch منخفضة الأولوية
```

وحدات تحت `data/motion/`: `MotionPhotoDetector`، `MotionPhotoListEnricher`، `MotionPhotoXmpSniffer`، `MotionPhotoVideoResolver`.

### التبعيات الرئيسية

- **Glide** — الصور المصغرة وصور المعاينة
- **Paging 3** — تصفح الشبكة
- **Media3 ExoPlayer** — تشغيل فيديو المعاينة / Motion Photo
- **ViewPager2** — تصفح المعاينة

---

## أمان الإعدادات

تطبّق PhotoChoice **تنقيحاً دفاعياً** على كل قيم الإعداد المعرّضة للمستخدم، حتى لا يُسبب الإدخال غير الصالح تعطّل المكتبة:

| الحقل | التنقيح |
|-------|---------|
| `selectCount` | يُحدّ إلى `1..9`؛ خارج النطاق يُرجع `1` |
| `spanCount` | يُحدّ إلى `2..6` |
| `minVideoDurationMs` / `maxVideoDurationMs` | يُبدّل تلقائياً إن min > max؛ min يُحدّ إلى `>= 0` |
| `minImageSize` / `maxImageSize` | يُبدّل تلقائياً إن min > max؛ min يُحدّ إلى `>= 0` |
| `cropConfig.enabled` | يُعطّل تلقائياً لوضع VIDEO أو الاختيار المتعدد (`effectiveCropEnabled`) |

---

## هيكل المشروع

```
photo_choice/
├── photo-choice/              # المكتبة (API عام: PhotoChoice)
│   └── src/main/java/com/google/photochoice/
│       ├── PhotoChoice.kt     # مدخل Builder، forResult()
│       ├── PhotoChoiceContract.kt     # ActivityResultContract (موصى به)
│       ├── config/
│       ├── data/
│       │   └── motion/        # اكتشاف Motion Photo، XMP، استخراج فيديو
│       ├── viewmodel/
│       └── ui/
│           ├── grid/
│           ├── album/
│           ├── crop/          # CropActivity
│           └── preview/       # PreviewActivity، تشغيل live بضغطة مطولة
├── sample/
├── CHANGELOG.md               # سجل التغييرات
├── README.md                  # English documentation
├── README.zh-CN.md            # 简体中文文档
├── README.ja.md               # 日本語ドキュメント
├── README.ko.md               # 한국어 문서
├── README.fr.md               # Documentation en français
├── README.es.md               # Documentación en español
├── README.ar.md               # هذا المستند (العربية)
└── README.ru.md               # Документация на русском
```

---

## البناء والتحقق

```bash
./gradlew :photo-choice:assembleDebug
./gradlew :sample:installDebug
./gradlew test
./gradlew lint
```

---

## قائمة التحقق للتكامل

- [ ] `implementation(project(":photo-choice"))` (أو ما يعادله في Maven)
- [ ] أذونات قراءة الوسائط في Manifest المضيف
- [ ] إذن وقت التشغيل قبل التشغيل (`PermissionHelper`)
- [ ] اختيار واجهة التشغيل: **`PhotoChoiceContract`** (موصى به، آمن أمام إنهاء العملية) أو استدعاء `forResult`
- [ ] معالجة `null` (إلغاء) مقابل `PhotoChoiceResult` (نجاح)
- [ ] استدعاء `PhotoChoice.cleanup(context)` عند استخدام الضغط/القص
- [ ] لـ Live Photos + الضغط، فهم **الاحتفاظ بالـ live / التصدير كصورة ثابتة** في المعاينة

---

## القيود

- مصدر البيانات هو **وسائط MediaStore العامة فقط** — لا مجلدات خاصة/مخفية.
- ألوان الواجهة والتمييز غير قابلة للتخصيص؛ فقط `ThemeMode` فاتح/داكن/نظام.
- لا **تُشغّل** `PhotoChoiceActivity` أو `PreviewActivity` أو `CropActivity` مباشرة.
- مرشحات مدة الفيديو تؤثر على القائمة فقط، لا الملفات على القرص.
- **شارات LIVE**:
  - شبه فورية عند API 34+ و`IS_MOTION_PHOTO` مضبوط في MediaStore.
  - تأخير قصير على OEM بلا أعلام DB (فحص XMP غير متزامن عند أول دخول viewport).
  - الضغطة المطولة في المعاينة ما زالت تكتشف motion photos غير المعلّمة عبر اكتشاف كامل (يشمل XMP).

---

## الإبلاغ عن المشاكل

يرجى تضمين **إصدار Android، طراز الجهاز، مقتطف الإعداد، السلوك المتوقع مقابل الفعلي**. لأخطاء Motion Photo، اذكر ما إذا كان معرض النظام يتعرّف العنصر كـ live/Motion Photo.

</div>
