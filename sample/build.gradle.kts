import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// 读取根目录 keystore.properties（已被 .gitignore 忽略，不随公开仓库分发）。
// 公开仓库 / JitPack 在线构建环境下该文件不存在，此处以空 Properties 兜底；
// 是否启用 release 签名交由 hasReleaseKeystore 判定，杜绝 null 强转导致配置阶段崩溃。
val keystoreFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().also { props ->
    if (keystoreFile.exists()) props.load(keystoreFile.inputStream())
}
// 是否具备完整的 release 签名：文件存在且四项键齐全才算，缺任一项即视为“无签名环境”，回退 debug 签名。
val hasReleaseKeystore = keystoreFile.exists() &&
    listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
        .all { keystoreProps.getProperty(it) != null }

android {
    namespace = "com.google.photochoice.sample"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.google.photochoice.sample"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // 仅当具备完整 keystore 时才创建 release 签名配置；
    // 公开仓库 / JitPack 环境无 keystore，跳过创建，下方回退默认 debug 签名。
    if (hasReleaseKeystore) {
        signingConfigs {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            // 有正式 keystore 用之，否则沿用 AGP 默认 debug 签名（无签名环境可正常编译）。
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 无 keystore 时不指定签名，交由使用者自备密钥后再出正式包，避免公开构建因缺密钥失败。
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":photo-choice"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.glide)
    implementation(libs.androidx.viewpager2)
}
