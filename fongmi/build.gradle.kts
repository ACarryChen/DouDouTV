plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

// FongMi 播放器舱（2026-11-23 移植，方案①连根搬）：
// 源码 = FongMi/TV @ 773cae11b（player/bean/utils/setting/db/api/gson/event/
// exception/impl/server/service + ui/custom/CustomWebView + browse/BrowseTree
// + App/Constant）+ catvod 子项目源码。
// 包名保持 com.fongmi.android.tv 原样，是为了以后跟上游 diff 搬运；
// 不要把这里面的代码「顺手重构」或改包名，那会毁掉对齐能力。
// 协议库（forcetech/hook/jianpian/thunder/tvbus）与 media3 aar 以抽出的 classes.jar 进
// libs/jars/（见依赖块注释），原 .aar 保留在 fongmi/libs/ 作档案。

android {
    namespace = "com.fongmi.android.tv"
    compileSdk = 36

    // db 包（Room 集合持久化）。照 FongMi 本尊用 annotationProcessor（javap）：
    // DAO 是纯 Java，APT 生成 Java 代码；走 KSP 会生成 Kotlin 且有平台类型 bug。
    defaultConfig {
        minSdk = 24
        javaCompileOptions {
            annotationProcessorOptions {
                argument("room.schemaLocation", "$projectDir/schemas")
            }
        }
        // FongMi 本尊 app 模块的 BuildConfig 字段：ActionEvent 的广播 action 前缀、
        // Util 的 flavor 判定（本舱无 flavor，统一按 mobile 编译）都引用它们
        buildConfigField("String", "APPLICATION_ID", "\"com.fongmi.android.tv\"")
        buildConfigField("String", "FLAVOR", "\"mobile\"")
    }

    buildFeatures {
        // ActionEvent 等 FongMi 代码引用 com.fongmi.android.tv.BuildConfig
        buildConfig = true
    }

    compileOptions {
        // Math.clamp 是 Java 21 API（AudioEffectConfig 在用），照 FongMi 本尊开 21 + 脱糖
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // 5 个协议 aar（forcetech/hook/jianpian/thunder/tvbus）与 FongMi 私改 media3 aar
    // 都不能以「本地 aar 文件依赖」进本模块：:app 依赖 :fongmi 会触发 bundleReleaseAar，
    // AGP 对「library 模块直接依赖本地 aar」是硬报错（产出的 aar 不含这些类）。
    // 处理：抽出各 aar 的 classes.jar 放 libs/jars/（仅编译期类型）；运行期的资源/res
    // 由 :app 自己声明的同一批 media3 aar 提供，协议库的 .so 已落到 src/main/jniLibs/，
    // forcetech 的 assets/libmitv.so 落到 src/main/assets/。mpv 的 .so（34MB）二期再落。
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf(
        "jars/forcetech.jar", "jars/hook.jar", "jars/jianpian.jar", "jars/thunder.jar", "jars/tvbus.jar"
    ))))
    // media3/danmaku 只编译期可见：运行期由 :app 自己声明的同一批 aar/jar 提供，
    // 若打成 implementation 会随 :fongmi aar 进 APK，与 :app 的 aar 依赖撞重复类
    compileOnly(fileTree(mapOf("dir" to "libs", "include" to listOf("jars/lib-*.jar"))))
    implementation("com.google.guava:guava:33.4.0-android")
    implementation("androidx.media:media:1.7.0")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-service:2.9.1")

    // db 包（Room 集合持久化）。照 FongMi 本尊用 annotationProcessor（javap）：
    // DAO 是纯 Java，APT 生成 Java 代码；走 KSP 会生成 Kotlin 且有平台类型 bug。
    implementation("androidx.room:room-runtime:2.8.4")
    annotationProcessor("androidx.room:room-compiler:2.8.4")

    // catvod 基础库（独立模块，namespace 生成 com.github.catvod.crawler.R）
    implementation(project(":catvod"))

    // api/loader 的 quickjs 型 spider（JsLoader），TVBox js 源支持
    implementation("wang.harlon.quickjs:wrapper-android:3.2.3")

    // impl/PermissionCallback
    implementation("com.guolindev.permissionx:permissionx:1.8.1")

    // server/Nano.java 本地代理服务器（FongMi 同款）
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    implementation("com.google.android.material:material:1.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // event/ 包走 EventBus；utils 走 logger；图片走 Glide（照 FongMi 本尊全家桶）。
    // 用 4.16 而非上游的 5.0.9：glide 5 的 aar-metadata 要求 compileSdk 37（AGP 9 才认），
    // 4.16 同样有 avif/okhttp3-integration 且 API 覆盖本模块全部用法，compileSdk 36 可编
    implementation("org.greenrobot:eventbus:3.3.1")
    implementation("com.orhanobut:logger:2.2.0")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("com.github.bumptech.glide:annotations:4.16.0")
    implementation("com.github.bumptech.glide:avif-integration:4.16.0")
    implementation("com.github.bumptech.glide:okhttp3-integration:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")

    // bean 注解解析（Parser 用 simple-xml；TextDrawable 渲染字母占位图）
    implementation("org.simpleframework:simple-xml:2.7.1")
    implementation("com.github.jahirfiquitiva:TextDrawable:1.0.3")

    // YouTube 免解析直连（impl/NewPipeImpl），jitpack 提供
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")

    // DLNA 投递（browse/BrowseTree）
    implementation("org.jupnp:org.jupnp:3.0.5")

    // Java 21 API（Math.clamp）脱糖
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
}
