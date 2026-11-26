# 混淆规则

# FongMi 壳 — com.fongmi.android.tv 是 App 的壳主体（App/Server/PlayerManager/Api/Loader/Parser）
# 启动时靠这些类的原始反射名工作，R8 不能混淆或削
-keep class com.fongmi.android.tv.** { *; }
-keep class com.fongmi.quickjs.** { *; }
-keep class com.fongmi.android.com.fongmi.chaquo.** { *; }
-dontwarn com.fongmi.**

# TVBox Shell — com.github.catvod 是 TVBox 标准壳（Proxy/Init/LocalServer/Config）
# 爬虫 native proxyInvoke 回调要按原始方法名找这些类
-keep class com.github.catvod.** { *; }
-dontwarn com.github.catvod.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**

# TVBox Spider — 壳 dex 是运行时独立 ClassLoader 加载的，R8 只处理 App 自身 dex，
# 壳内部的 com.github.catvod.spider.Init / LocalServer 等不需要 keep。
# 但 App 自己的 TvBoxClient / SpiderLoader / Parser 等类要 keep 成员不被削，
# 因为壳 native proxyInvoke 回调会按方法名反射
-keep class com.chen.doudoutv.data.tvbox.** { *; }
-keep class com.chen.doudoutv.**Spider** { *; }

# Fongmi 播放器舱 — PlayerManager / ExoPlayerEngine 是公开 API，keep 住
-keep class com.chen.doudoutv.player.** { *; }
-dontwarn tv.danmaku.ijk.**

# Room — KSP 生成的 DAO 实现需要 keep
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.paging.**

# Compose — R8 默认处理 Compose，这里只 keep 自定义状态
-keepclassmembers class **State {
    public <init>(...);
}

# ZXing 二维码
-dontwarn com.google.zxing.**

# Jpinyin
-dontwarn com.github.stuxuhai.jpinyin.**

# Fastjson / Jsoup — spider 运行时要从 dex 里找这些类解析 JSON/HTML
-keep class com.alibaba.fastjson.** { *; }
-keep class org.jsoup.** { *; }
-dontwarn com.alibaba.fastjson.**

# coil
-dontwarn coil.**

# desugar
-dontwarn java.nio.**
-dontwarn java.beans.**

# Rhino JS 引擎（fongmi 壳用）
-dontwarn org.mozilla.javascript.**
-keep class org.mozilla.javascript.** { *; }

# media3 / exoplayer AAR — R8 可能削掉播放器用到的内部类
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
-keep class com.google.android.exoplayer2.** { *; }
-dontwarn com.google.android.exoplayer2.**
