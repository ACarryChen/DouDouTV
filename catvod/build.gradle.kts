plugins {
    id("com.android.library")
}

// catvod 舱：TVBox 生态基础库（爬虫 Spider 基类 / OkHttp 网络栈 / DoH / Prefers），
// 源码 = FongMi/TV @ 773cae11b 的 :catvod 子项目原样搬运。
// namespace 必须保持 com.github.catvod.crawler——R 类的包名依赖它，
// TVBox 壳 jar 也按这个包找 Spider 基类，别改。
android {
    namespace = "com.github.catvod.crawler"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api("androidx.annotation:annotation:1.8.2")
    api("org.brotli:dec:0.1.2")
    // okhttp 4.12：5.x 的 okhttp-android aar-metadata 要求 compileSdk 37（本仓锁 36）；
    // catvod 全部走 Request.Builder/OkHttpClient 等 4/5 同源 API，降级无感
    api("com.squareup.okhttp3:okhttp:4.12.0")
    api("com.squareup.okhttp3:okhttp-dnsoverhttps:4.12.0")
    api("com.squareup.okhttp3:logging-interceptor:4.12.0")
    api("com.google.code.gson:gson:2.10.1")
    api("com.google.guava:guava:33.4.0-android")
    api("com.googlecode.juniversalchardet:juniversalchardet:1.0.3")
    api("com.orhanobut:logger:2.2.0")
    api("androidx.preference:preference:1.2.1")
}
