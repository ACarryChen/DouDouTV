plugins {
    id("com.android.application") version "8.6.1" apply false
    // Kotlin 2.2：FongMi 私改 media3 aar 的元数据是 2.2.0，2.0 编译器读不动（跨两个版本不兼容）
    id("org.jetbrains.kotlin.android") version "2.2.0" apply false
    // Compose 编译器从 Kotlin 2.0 起由 Kotlin 仓库发布（org.jetbrains.kotlin.plugin.compose），
    // 不再由 AGP 的 composeOptions 指定 extension 版本，必须与 Kotlin 同版本
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.0" apply false
    id("com.google.devtools.ksp") version "2.2.0-2.0.2" apply false
}
