plugins {
    alias(libs.plugins.android.library)
    // JitPack / Maven 发布：产出可被宿主 implementation("...") 引用的 AAR 坐标
    `maven-publish`
}

// 发布坐标 group：JitPack 约定为 com.github.<GitHub 用户名>；本地手动发布时保持一致。
group = "com.github.Hu12037102"

// 发布版本号：优先取环境变量 VERSION（JitPack 构建 tag 时注入），缺省回退 1.0.0。
// 便于打 tag 后由 CI/JitPack 覆盖，无需每次手改此文件。
version = System.getenv("VERSION") ?: "1.0.0"

android {
    namespace = "com.google.photochoice"
    compileSdk = 36

    defaultConfig {
        minSdk = 29
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // 随 AAR 下发给宿主的 keep 规则：宿主开启 R8/minify 时自动合并，
        // 保证公开 API 与经 Intent 序列化传递的配置对象不被裁剪/改名
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            // 库模块刻意不自混淆：AAR 保持类/成员明文，由宿主 App 打最终包时统一 R8 处理。
            // 库若自混淆会改名公开 API 导致宿主无法调用；需保护的符号改由下发给宿主的
            // consumerProguardFiles("consumer-rules.pro") 在宿主侧合并生效。
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    // 声明仅发布 release 变体，供下方 maven-publish 的 release 组件消费
    publishing {
        singleVariant("release") {
            // 一并产出 sources jar，方便宿主查看/跳转源码
            withSourcesJar()
        }
    }
}

// AGP 的软件组件在 evaluate 后才就绪，故在 afterEvaluate 中绑定发布物
afterEvaluate {
    publishing {
        publications {
            // JitPack 只需一个从 release 变体生成的 Maven 发布物
            create<MavenPublication>("release") {
                from(components["release"])
                // artifactId 与 GitHub 仓库名保持一致，最终坐标：
                // com.github.Hu12037102:photo_choice:<version>
                groupId = project.group.toString()
                artifactId = "photo_choice"
                version = project.version.toString()
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.viewmodel.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.glide)
    implementation(libs.androidx.paging)
    implementation(libs.androidx.viewpager2)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
