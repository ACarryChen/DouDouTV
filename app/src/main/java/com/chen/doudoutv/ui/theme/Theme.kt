package com.chen.doudoutv.ui.theme

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration

import com.chen.doudoutv.core.Prefs
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object AppColors {
    // ---------- 三档颜色模式（暗 / 浅 / 跟随系统） ----------
    //
    // themeMode 是快照状态，设置页是唯一写点（同时落 Prefs）；所有中性色令牌的 getter 都读它，
    // 改档即全局重组 —— 调用点全部保持 `AppColors.Xxx` 原样，不引入 CompositionLocal 大迁移。
    // 手机端是「浅色 / 暗色 / 跟随系统」三档；TV 端设置里只给「浅色 / 暗色」两档，
    // 但底层共用同一个 Prefs 键（手机存的「跟随系统」落到 TV 上表现为跟随系统，属可接受的边角）。
    // darkUi 只由播放器 Activity 生命周期写（进入压 true、返回时 Main.onResume 写回 false）：
    // 播放器界面一律恒暗，浏览界面（手机 / TV / 强制 TV）跟随颜色模式。
    // systemDark 在 DouDouTvTheme 里随 LocalConfiguration 同步（manifest 对 uiMode 做了
    // configChanges，系统切换深浅色不会重建 Activity，只能靠组合内跟着配置走）。
    var themeMode by mutableStateOf(Prefs.ThemeMode.LIGHT)
    var darkUi by mutableStateOf(true)
    var systemDark by mutableStateOf(true)

    fun lightOn(): Boolean = !darkUi && when (themeMode) {
        Prefs.ThemeMode.LIGHT -> true
        Prefs.ThemeMode.DARK -> false
        Prefs.ThemeMode.SYSTEM -> !systemDark
    }

    /** 详情页取色的静态兜底 —— 固定中性灰，**不跟主题**。 */
    val BrandBase = Color(0xFF8E8E93)
    val BrandSoftBase = Color(0xFFAEAEB4)

    /** 品牌色：跟随设置页选的主题色板，只在组合式上下文里可读 */
    val Brand: Color
        @Composable get() = LocalPalette.current.brand

    /** 品牌浅色：用于品牌色上的文字与弱强调；浅色模式下直接用品牌色本体保对比度 */
    val BrandSoft: Color
        @Composable get() = if (lightOn()) LocalPalette.current.brand else LocalPalette.current.brandSoft

    /**
     * 材质四层 L0~L3 与文字三级。暗色为纯中性灰（2026-11-10 去蓝调，明度未动）；
     * 浅色为 Apple TV 口径：#F5F5F7 底 + 白卡 + 灰字，hairline 反转为黑 8%。
     */
    val Bg: Color get() = if (lightOn()) Color(0xFFF5F5F7) else Color(0xFF080808)

    /** L1 —— 分区容器、分组面板、图片占位 */
    val Surface: Color get() = if (lightOn()) Color(0xFFFFFFFF) else Color(0xFF131313)

    /** L2 —— 卡片、列表行、输入控件 */
    val SurfaceHi: Color get() = if (lightOn()) Color(0xFFE9E9EE) else Color(0xFF1F1F1F)

    /** L3 —— 焦点/抬起态的填充 */
    val SurfaceRaise: Color get() = if (lightOn()) Color(0xFFD9D9DF) else Color(0xFF2C2C2C)

    /** 文字三级：白 → 灰（暗色）/ 近黑 → 灰（浅色） */
    val TextPrimary: Color get() = if (lightOn()) Color(0xFF1C1C1E) else Color(0xFFF4F4F4)
    val TextSecondary: Color get() = if (lightOn()) Color(0xFF6E6E73) else Color(0xFFA3A3A3)
    val TextTertiary: Color get() = if (lightOn()) Color(0xFF98989D) else Color(0xFF747474)
    val Rating = Color(0xFFFFC107)

    /** 磨砂玻璃底（底栏抓不到模糊时的兜底色） */
    val GlassBase: Color get() = if (lightOn()) Color(0xFFF2F2F6) else Color(0xFF161618)

    /** 磨砂玻璃的暗色压层：浅色下换成透明白（防止 copy(alpha) 把 RGB 拖向纯黑） */
    val GlassShade: Color get() = if (lightOn()) Color(0x00FFFFFF) else Color(0xFF0B0B0D)

    /**
     * 语义色三档：危险 / 警告 / 正常。状态色是语义，不随颜色模式换。
     */
    val Danger = Color(0xFFE5484D)

    /** 警告橙：登录失效这类「还能用但不对」的状态 */
    val Warn = Color(0xFFFF8A65)

    /** 正常绿：在线、启用、同步成功 */
    val Ok = Color(0xFF34C759)

    /** 已停用这类中性弱状态（原先散落在各处的灰色字面量） */
    val Disabled: Color get() = if (lightOn()) Color(0xFF98989D) else Color(0xFF747474)

    /**
     * 焦点态指示色：焦点环 / 焦点描边。TV 的焦点语言是「反白高亮」，
     * 浅色模式下白环落在亮底上读不出来，翻成近黑。
     */
    val Focus: Color get() = if (lightOn()) Color(0xFF1C1C1E) else Color.White

    /**
     * 详情页顶栏 scrim 的底色：要「和屏幕底色完全一致」，跟 [Bg] 同源。
     */
    val ScrimTop: Color get() = Bg
}

/**
 * 电视端文字色阶 —— 参考 Netflix：整屏只用「白 → 灯」一条轴拉开层级，
 * 正文、元信息、辅助提示靠不透明度分级，不引入第二种色相；
 * 品牌色只留给按钮和进度条这类「可操作 / 进行中」的元素。
 *
 * 数值取 Netflix TV 的量级：正文约 70%，元信息约 60%，辅助约 45%。
 * 暗色为白系、浅色翻为近黑系（同色系同透明度），全部做成 getter 跟随颜色模式：
 * TV 端 144 处调用点零改动，切档即全局重组。
 */
object TvText {
    private val baseDark get() = if (AppColors.lightOn()) Color(0xFF1C1C1E) else Color.White

    /** Hero 片名、按钮文字 */
    val Title get() = baseDark

    /** 分区标题、卡片标题 */
    val Section get() = baseDark.copy(alpha = 0.88f)

    /** 简介正文、次按钮文字、返回按钮 */
    val Body get() = baseDark.copy(alpha = 0.70f)

    /** 元信息、空状态的补充说明 */
    val Meta get() = baseDark.copy(alpha = 0.60f)

    /**
     * 计数、时间戳这类可有可无的信息。
     *
     * 不透明度 0x80（50%）。暗色下落在四个表面分别是 5.35 / 5.33 / 5.11 / 4.70，
     * 全部过 WCAG AA 的 4.5:1，且和 Meta(60%) 仍差出一档，层级不会塌掉。
     */
    val Muted get() = baseDark.copy(alpha = 0.50f)

    /** 分隔点、极弱提示 */
    val Hairline get() = baseDark.copy(alpha = 0.25f)
}

/**
 * 恒暗页标记：TV 详情页这类海报基调页永远暗底（与手机端详情页同一策略），
 * 页上共享组件（TvPillButton / TvIconButton / TvSectionTitle / TvMetaRow / TvEmptyState）
 * 按暗页口径取色，不随颜色模式翻转。页面根节点 provides true，模式色弹窗局部 provides false 豁免。
 */
val LocalTvPageDark = staticCompositionLocalOf { false }

/**
 * 恒暗页（TV 详情页）的钉死取色：页面永远暗底，不随颜色模式翻转。
 * 数值沿用 TV 详情页原本的 Page* 白系常量与暗色模式的表面值，保证暗色模式观感零变化。
 */
object TvPageText {
    val Title = Color.White
    val Section = Color(0xE0FFFFFF)
    val Body = Color(0xB3FFFFFF)
    val Meta = Color(0x99FFFFFF)
    val Muted = Color(0x80FFFFFF)
    val Hairline = Color(0x40FFFFFF)

    /** 页上组件的暗底（对齐暗色模式 Surface / SurfaceHi） */
    val Surface = Color(0xFF131313)
    val SurfaceHi = Color(0xFF1F1F1F)
}

/**
 * Material3 配色表。
 *
 * 原来是一个顶层 val：Compose 的 object 初始化只跑一次，色板换了它也拿不到新值，
 * 所以改成按色板现算。传进来的是色板而不是直接读状态，让「谁订阅了主题」这件事
 * 只发生在 DouDouTvTheme 那一处，下面整棵树都靠 CompositionLocal 各自订阅。
 */
private fun schemeFor(palette: ThemePalette) = if (AppColors.lightOn()) lightColorScheme(
    primary = palette.brand,
    onPrimary = Color.White,
    primaryContainer = palette.brandSoft,
    secondary = palette.brand,
    background = AppColors.Bg,
    onBackground = AppColors.TextPrimary,
    surface = AppColors.Surface,
    onSurface = AppColors.TextPrimary,
    surfaceVariant = AppColors.SurfaceHi,
    onSurfaceVariant = AppColors.TextSecondary
) else darkColorScheme(
    primary = palette.brand,
    onPrimary = Color.White,
    primaryContainer = palette.brandSoft,
    secondary = palette.brandSoft,
    background = AppColors.Bg,
    onBackground = AppColors.TextPrimary,
    surface = AppColors.Surface,
    onSurface = AppColors.TextPrimary,
    surfaceVariant = AppColors.SurfaceHi,
    onSurfaceVariant = AppColors.TextSecondary
)

/**
 * 手机端字号表。这里列出的档位是唯一真相：没写进来的档位会静默回落到 Material3 默认值，
 * 曾经就是因此让电视端标题拿到了 57sp/Regular 的默认 displayLarge。
 *
 * 带数字的档位统一加 `tnum`（等宽数字）：进度「01:24 / 45:00」、评分、计数这类值一变，
 * 比例数字的字宽就跟着抖，行宽跳来跳去。系统字体不支持这个特性时它是空操作，不会报错。
 */
private const val TabularDigits = "tnum"

private val MobileTypography = Typography(
    displayLarge = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    displayMedium = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    displaySmall = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 13.5.sp, lineHeight = 20.sp, fontFeatureSettings = TabularDigits),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontFeatureSettings = TabularDigits),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = TabularDigits),
    labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = TabularDigits),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = TabularDigits)
)

/**
 * 电视端字号表：尺度参考 Netflix TV 客户端。
 *
 * Netflix 在电视上没有把标题堆到「海报级」的大字，而是靠层级差和行高拉开重点：
 *   · Hero 片名约 44sp、SemiBold —— 粗黑体在远距观看下反而发糊，Medium/SemiBold 更干净；
 *   · 行标题 20sp、正文 17sp，正文行高约 1.45 倍（25sp）—— 大段简介靠行呼吸而不是靠字号；
 *   · 最小的一档也保住 12sp：三米外低于这个值就只能看个形状。
 * 这里列出的档位是唯一真相：没写进来的档位会静默回落到 Material3 默认值。
 */
private val TvTypography = Typography(
    displayLarge = TextStyle(
        fontSize = 44.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 52.sp,
        letterSpacing = (-0.4).sp
    ),
    displayMedium = TextStyle(
        fontSize = 34.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 42.sp,
        letterSpacing = (-0.3).sp
    ),
    displaySmall = TextStyle(
        fontSize = 28.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 36.sp
    ),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, lineHeight = 31.sp),
    headlineSmall = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.Medium, lineHeight = 27.sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium, lineHeight = 26.sp),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium, lineHeight = 23.sp),
    titleSmall = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Normal, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, lineHeight = 22.sp, fontFeatureSettings = TabularDigits),
    bodySmall = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, lineHeight = 18.sp, fontFeatureSettings = TabularDigits),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp, fontFeatureSettings = TabularDigits),
    labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp, fontFeatureSettings = TabularDigits),
    labelSmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp, fontFeatureSettings = TabularDigits)
)

fun Context.isTelevision(): Boolean {
    val manager = getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    if (manager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) return true

    // 兜底：电视盒子常以普通模式上报，用 Leanback 特性/无触摸屏来识别
    val hasLeanback = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    val hasTouchscreen = packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
    return hasLeanback || !hasTouchscreen
}

@Composable
fun DouDouTvTheme(isTv: Boolean = false, content: @Composable () -> Unit) {
    // 跟随系统档的数据源：manifest 对 uiMode 做了 configChanges，系统切换深浅色
    // 不重建 Activity，只能在这里随 LocalConfiguration 同步（组合订阅配置变化）。
    val config = LocalConfiguration.current
    SideEffect {
        AppColors.systemDark =
            (config.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }
    val palette = AppPalette.current
    CompositionLocalProvider(
        LocalPalette provides palette,
        LocalTvMode provides isTv
    ) {
        MaterialTheme(
            colorScheme = schemeFor(palette),
            typography = if (isTv) TvTypography else MobileTypography,
            content = content
        )
    }
}
