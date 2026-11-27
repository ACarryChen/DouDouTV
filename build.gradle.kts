plugins {
    id("com.android.application") version "8.6.1" apply false
    // 2.2.0/2.2.10 会导致 TV 来源胶囊失焦，2.1.21 同源真机正常；关闭 Compose flag 与 jvm-default 均无效
    id("org.jetbrains.kotlin.android") version "2.1.21" apply false
    // Compose 编译器从 Kotlin 2.0 起由 Kotlin 仓库发布（org.jetbrains.kotlin.plugin.compose），
    // 不再由 AGP 的 composeOptions 指定 extension 版本，必须与 Kotlin 同版本
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21" apply false
    id("com.google.devtools.ksp") version "2.1.21-2.0.1" apply false
}
