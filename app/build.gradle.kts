import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Compose 编译器插件：Kotlin 2.0 起由 Kotlin 仓库发布，取代旧的 composeOptions 写法
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Room 把每个版本的表结构 JSON 落到这里，配合 AppDatabase 的 exportSchema = true：
// 下次改表（加索引、加字段）能看出漏没漏迁移，也能直接上自动迁移。
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "com.chen.doudoutv"
    // FongMi 私改版 media3（release-1.11.0-fongmi）的 aar-metadata 要求 compileSdk 36；
    // 36 ≥ Compose 1.9 foundation 要求的 35，两者兼容
    compileSdk = 36

    // 固定签名（照 FongMi/影视仓的做法）：keystore 随仓库分发。
    //
    // 为什么公开这把钥匙：私有密钥 + 公开仓库是个死结 —— CI 拿不到私钥，
    // 产出的包签名就与线上那个装在用户手机上的不同，系统判定为两个应用直接拒绝覆盖
    // （INSTALL_FAILED_UPDATE_INCOMPATIBLE）。要么把钥匙公开（所有环境签名一致，
    // 任何地方构建的包都能互相覆盖），要么把私钥塞进 CI Secrets（多一步配置，
    // 且换机器/换人构建时照样要配）。本应用是自用 + 网盘侧载分发，取前者：
    // 签名一致 > 签名保密。
    //
    // 代价：任何人 clone 下来都能构建出能覆盖你手机的包。
    // 若将来要上架应用商店，必须换回私有密钥，并接受一次卸载重装（数据会丢）。
    signingConfigs {
        create("fixed") {
            storeFile = file("keystore.jks")
            storePassword = "ccIz21K39gsWXtuD0RmKlXZj"
            keyAlias = "doudoutv"
            keyPassword = "ccIz21K39gsWXtuD0RmKlXZj"
        }
    }

    defaultConfig {
        applicationId = "com.chen.doudoutv"
        // media3 1.11 起 gradle 侧要求 minSdk 24（随 FongMi 对齐，P0-① 已批）
        minSdk = 24
        targetSdk = 28
        versionCode = 4
        versionName = "1.0.3"

        vectorDrawables { useSupportLibrary = true }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // 本机与目标设备都是 arm，砍掉 x86 系
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("fixed")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // debug 包与 release 同包名同签名，方便覆盖安装测试
            signingConfig = signingConfigs.getByName("fixed")
        }
    }

    lint {
        // 本应用走 GitHub Releases / 侧载分发，不上 Google Play；
        // targetSdk 28 是 TVBox 爬虫壳的硬要求（调高会破坏播放代理路径），
        // Play 的目标 API 政策检查不适用。只关这一条，其余致命检查保留。
        disable += "ExpiredTargetSdkVersion"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // :fongmi 开了 desugaring，aar-metadata 要求下游也开启
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=kotlin.RequiresOptIn")
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/DEPENDENCIES",
            "META-INF/INDEX.LIST"
        )
    }
}

dependencies {
    // desugaring（:fongmi 要求下游开启）：hook/thunder aar 的 aar-metadata 要 nio flavor
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")

    // FongMi 播放器舱（PlayerManager/ExoPlayerEngine）：源码模块直接依赖。
    // 不能用 aar 落仓 —— AGP 禁止本地 aar 依赖参与 bundleReleaseAar，项目依赖是唯一通路
    implementation(project(":fongmi"))

    // catvod 的 Proxy（静态端口通告）在 :app 侧要用：:fongmi 对它是 implementation，不传递
    implementation(project(":catvod"))

    // FongMi 私改版 media3（源码 E:/tmp/ref/FongMi-media，分支 release-1.11.0-fongmi，
    // 与 TV 锚点 773cae11b 配对）。官方 maven 没有这批改动，只能本地源码编 aar 进仓。
    // aar 在根级 libs/（与 :fongmi 模块共用）；aar 之间没有传递依赖信息，
    // exoplayer 的兄弟模块（container/decoder/common/datasource/extractor/database）
    // 必须逐个显式声明；guava 是 common 的传递依赖。
    implementation(rootProject.files(
        "libs/lib-common-release.aar",
        "libs/lib-database-release.aar",
        "libs/lib-container-release.aar",
        "libs/lib-decoder-release.aar",
        "libs/lib-datasource-release.aar",
        "libs/lib-datasource-okhttp-release.aar",
        "libs/lib-exoplayer-release.aar",
        "libs/lib-exoplayer-dash-release.aar",
        "libs/lib-exoplayer-hls-release.aar",
        "libs/lib-extractor-release.aar",
        "libs/lib-effect-release.aar",
        "libs/lib-session-release.aar",
        "libs/lib-ui-release.aar",
        "libs/lib-ui-danmaku-release.jar"
    ))
    implementation("com.google.guava:guava:33.4.0-android")
    // lib-session 的通知模板布局（notification_template_lines_media 等）定义在 legacy androidx.media 里
    implementation("androidx.media:media:1.7.0")

    // TVBox Spider Guard 内部依赖（饭太硬/花卷/玩偶/观影等线路的加密壳都用了 Gson 做 JSON 解析）
    implementation("com.google.code.gson:gson:2.10.1")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")

    // Compose：BOM 2025.08.00 = ui/foundation 1.9.0 + material3 1.3.2
    implementation(platform("androidx.compose:compose-bom:2025.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // 网络 / JSON
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // 手机扫码维护：电视端把局域网地址编码成二维码（纯 Java，只用到 core）
    implementation("com.google.zxing:core:3.5.3")

    // 汉字 → 拼音（联icode 区间估想用的首字母必须准确：Un算会把「兰」算成 b）
    implementation("com.github.stuxuhai:jpinyin:1.1.8")

    // TVBox type 3 爬虫运行时（Jsoup + Fastjson —— spider JAR 依赖这些类在 Dex 里解析）
    implementation("org.jsoup:jsoup:1.17.2")
    implementation("com.alibaba:fastjson:1.2.83")

    // 本地媒体库
    // Room 2.6.1 的编译器在 Kotlin 2.x / KSP2 下不工作，必须跟到 2.7.2
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")

    // 海报图
    implementation("io.coil-kt:coil-compose:2.6.0")

    // 海报主色提取（详情页沉浸式渐变背景）
    implementation("androidx.palette:palette-ktx:1.0.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // 端到端设备测试（自己走完 TVBox 源完整流程用）
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.6.1")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
