package com.chen.doudoutv.ui.tv

import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.KeyEvent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.chen.doudoutv.core.Prefs
import com.chen.doudoutv.data.model.MediaEntity
import com.chen.doudoutv.data.tvbox.TvBoxClient.TvBoxSite
import com.chen.doudoutv.ui.FocusPulse
import com.chen.doudoutv.ui.LibraryViewModel
import com.chen.doudoutv.ui.components.GradientBake
import com.chen.doudoutv.ui.components.argbWithAlpha
import com.chen.doudoutv.ui.components.continueTitle
import com.chen.doudoutv.ui.components.episodeLabel
import com.chen.doudoutv.ui.components.PosterArt
import com.chen.doudoutv.ui.theme.AppColors
import com.chen.doudoutv.ui.theme.LocalTvPageDark
import com.chen.doudoutv.ui.theme.Motion
import com.chen.doudoutv.ui.theme.Radius
import com.chen.doudoutv.ui.theme.TvText
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 经典版首页（TvHomeScreen）下线后，NF 布局仍在复用的共享件：
 * 主视觉轮播（[TvHero] / [TvHeroEmpty] / [TvHeroSeamFade] / [tvHeroHeight] / [HERO_DWELL_SECONDS]）
 * 与两个弹窗（[TvContinueActionDialog] / [TvLineManagerDialog]）。
 * 从 TvHomeScreen.kt 原样迁出，画法与注释保持不动。
 */

/** 一张精选在 Hero 上停留的秒数；右下进度条按这个时长走满，走满即换页。 */
internal const val HERO_DWELL_SECONDS = 9f

/**
 * Hero 与页面背景间的收色带（画法同手机端轮播 seamFade）。高度=Hero 与首行的额外空白，
 * 已从 40dp 收到 10dp。顶边取 heroLandingColor，「海报下缘一条死黑」就是这块在治。
 * 淡出走 drawBehind 画到带外（[SEAM_FADE_DEPTH]）：10dp 内降 0 观感是「戛然而止」，
 * 画得长颜色才化得开；不复用 themeSeamFade——着陆色要跟底图逐帧淡 600ms，必须重画不重组。
 * 收尾用实色减 alpha 而非 Color.Transparent：后者把 RGB 拖向纯黑，淡出末尾压出暗边。
 */
@Composable
internal fun TvHeroSeamFade(ambient: Color, modifier: Modifier = Modifier) {
    val blended = rememberAmbientBlend(ambient)
    val bake = remember { GradientBake() }
    Box(
        modifier
            .fillMaxWidth()
            // 只有一条很薄的落色带就够了：Hero 底部自己有纵向渐隐叠上去，
            // 再给 10dp 条带显得 Hero 和下面的「继续观看」之间空一大片
            .height(4.dp)
            .drawWithCache {
                val seam = heroLandingColor(blended())
                val argb = seam.toArgb()
                val w = size.width
                val total = size.height + SEAM_FADE_DEPTH.toPx()
                val img = bake.image(w, total, seam) { cv ->
                    cv.drawRect(
                        0f, 0f, w, total,
                        Paint().apply {
                            shader = LinearGradient(
                                0f, 0f, 0f, total,
                                intArrayOf(
                                    argb,
                                    argbWithAlpha(argb, 0.80f),
                                    argbWithAlpha(argb, 0.52f),
                                    argbWithAlpha(argb, 0.28f),
                                    argbWithAlpha(argb, 0.10f),
                                    argbWithAlpha(argb, 0f)
                                ),
                                floatArrayOf(0f, 0.10f, 0.28f, 0.50f, 0.72f, 1f),
                                Shader.TileMode.CLAMP
                            )
                        }
                    )
                }
                val dst = IntSize(w.toInt(), total.toInt())
                onDrawBehind {
                    drawImage(img, dstSize = dst, filterQuality = FilterQuality.Low)
                }
            }
    )
}

/**
 * Hero 高度。
 *
 * Netflix 的 billboard 约占视口高的 55~60%，但那家的标题排在画面**下方**；
 * 这里标题、元信息、按钮全压在画面里（logo 就占 110dp），所以要留出内容高度。
 * 底部那一块固定高约 250dp（logo 110 + 间距 + 元信息 + 按钮 + 30 底边距），
 * 下限因此不能低于 360dp，再小就成了「按钮贴着画面顶」。
 *
 * 比例 0.62→0.70：0.62 在 540dp 屏上算出来是 334.8dp，被下限夹回 360dp（66.7%），
 * 也就是说那档比例在小屏上从来没真正生效过。改 0.70 后 540dp 屏是 378dp，
 * 下限不再介入，折痕下方仍有 162dp —— 和夹值时代的 160dp 基本一样，
 * 「继续观看」的标题与卡片上半照样露得出来；720dp 的高密度屏按比例走 504dp。
 */
@Composable
internal fun tvHeroHeight(): Dp {
    val screenH = LocalConfiguration.current.screenHeightDp.dp
    return (screenH * 0.80f).coerceIn(432.dp, 864.dp)
}

/** 主视觉：满宽横版图，左下压标题与操作，底部渐隐接入页面底色；轮播指示器在右下。
 *  Hero 从屏幕最顶端开始铺满，顶栏透明悬浮其上。
 *
 *  动效按 Netflix billboard 的量级来：
 *    · 背景换页用淡入覆盖（600ms），不是硬切；
 *    · 标题/简介随内容淡入淡出，但按钮行留在动画之外 —— 同一 FocusRequester
 *      若被两份实例同时 attach，焦点落点会变得不确定；
 *    · 背景持续做极慢的 Ken Burns 缩放（1.02→1.075 再倒回），
 *      缩放值只在 graphicsLayer 的 lambda 里读取，走 draw 阶段，画面在动但 Composable 不重组；
 *    · 缩放后的画面必须裁在 Hero 边界内，否则会溢出盖住下面一行和渐隐遮罩。 */
@Composable
internal fun TvHero(
    item: MediaEntity,
    heroIndex: Int,
    heroTotal: Int,
    progress: State<Float>,
    /** 列表正在滚动：暂停 Ken Burns，别让全屏缩放和滚动抢同一帧的绘制时间。焦点换卡那一段由 [FocusPulse] 补上 */
    paused: Boolean,
    playFocusRequester: FocusRequester,
    onPlay: () -> Unit,
    onDetail: () -> Unit,
    /** ↑ 交棒给顶栏来源按钮；两颗按钮都要，之前只有播放键接了 ↑，详情键按上没反应 */
    onNavigateUp: () -> Unit,
    /** 两颗按钮任一颗拿到焦点：首页已经不站在卡片上了 */
    onButtonFocus: () -> Unit,
    /**
     * 这张底图下缘一条带的取样色，见 [rememberHeroAmbient]。
     * 由调用方算一次，和下面那条 [TvHeroSeamFade] 传同一个值 —— 两处必须同色同相位，
     * 各自独立取样的话换页那一瞬接缝会错开，就又看得见一条线。
     */
    ambient: Color,
    /**
     * 底图 URL 覆盖：刮削横板海报优先；加载失败后依次回退源站 backdrop 和 poster。
     */
    backdropUrlOverride: String? = null,
    titleContent: (@Composable (MediaEntity) -> Unit)? = null
) {
    val imageUrl = (backdropUrlOverride?.takeIf { it.isNotBlank() } ?: item.backdropUrl).ifBlank { item.posterUrl }

    // 背景换页：旧图留在底层不动，新图在上层淡入盖过去。
    // 没有直接用 Crossfade —— 它会把两张图同时压到半透明，中间那一瞬露出黑色页面底色，
    // 整块画面发灰，底部渐隐也跟着破相。
    val currentUrl = remember { mutableStateOf(imageUrl) }
    val outgoingUrl = remember { mutableStateOf<String?>(null) }
    val backdropAlpha = remember { Animatable(1f) }
    LaunchedEffect(imageUrl) {
        if (imageUrl != currentUrl.value) {
            outgoingUrl.value = currentUrl.value
            currentUrl.value = imageUrl
            backdropAlpha.snapTo(0f)
            backdropAlpha.animateTo(1f, tween(600))
            outgoingUrl.value = null
        }
    }

    // Ken Burns：只用 Animatable 而不用 rememberInfiniteTransition ——
    // 后者没法暂停。缩放值只在 graphicsLayer 的 lambda 里读取，走 draw 阶段，Composable 不重组。
    //
    // 「让路」有两个触发：列表滚动、焦点上下左右换卡。后者并不会滚动列表，
    // 但卡片放大动画正和这层全屏缩放抢同一帧 —— 弱盒子上「一按方向键就掉帧」的元凶就是它。
    // 两个信号合成一个布尔喂给 snapshotFlow：暂停只取消动画协程，不惊动 hero 的重组；
    // 冻住时 zoom 停在当前值，安定后从那儿接着走（和原来的 LaunchedEffect(paused) 语义一致）。
    val zoom = remember { Animatable(1.02f) }
    LaunchedEffect(paused) {
        var leg: Job? = null
        snapshotFlow { paused || FocusPulse.movingState.value }.collect { busy ->
            leg?.cancel()
            leg = if (busy) null else launch {
                val legMs = (HERO_DWELL_SECONDS * 2600).toInt()
                while (true) {
                    zoom.animateTo(1.075f, tween(legMs, easing = LinearEasing))
                    zoom.animateTo(1.02f, tween(legMs, easing = LinearEasing))
                }
            }
        }
    }

    // Hero 整块压在底图上，等同恒暗面：玻璃按钮、元信息行按暗页口径取色，
    // 浅色模式下不能翻成黑玻璃/深字压在画面上。
    CompositionLocalProvider(LocalTvPageDark provides true) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(tvHeroHeight())
        ) {
        // 背景层：放大后的图要裁在 Hero 里，不能溢出到下方内容区
        Box(
            Modifier
                .fillMaxSize()
                .clipToBounds()
        ) {
            val old = outgoingUrl.value
            if (old != null) {
                AsyncImage(
                    model = old,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = zoom.value
                            scaleY = zoom.value
                        }
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = backdropAlpha.value
                        scaleX = zoom.value
                        scaleY = zoom.value
                    }
            ) {
                PosterArt(
                    model = backdropUrlOverride.orEmpty(),
                    contentDescription = null,
                    fallbackModel = item.backdropUrl,
                    fallbackModels = listOf(item.posterUrl),
                    fallback = {}
                )
            }
        }

        // 渐变遮罩：横向压暗 + 纵向着陆色渐隐，两层烘成一张图一次画完（见 [HeroOverlayBake]）。
        // 纵向渐隐的终点色跟着底图走，所以不能在组合期把颜色算死：
        // 换页时底图淡入 600ms，着陆色要同一路混过去，否则那条带子会在中途硬跳一次色。
        // 暗化的起点从 0.28 后移到 0.45：原来画面刚过中线就开始压黑，整块下半截糊成一片，
        // 看着不像渐隐而像被截断。中段（0.45~0.82）只铺到四成透明度，画面还认得出；
        // 左半边另有横向 0.88 黑压着，两片叠加后标题/按钮的对比度不受影响。
        // 底边最后 3% 必须走满不透明：这里差一点都藏不住。上一版把底边留在 0.92，
        // 让底图自己的下边缘透出 8%（红绸那种亮部就是十几二十个单位的台阶），
        // 横贯整宽的一条亮线，肉眼读出来就是「海报下方那条边」——过渡做得再长也白搭。
        // 纯色平板本身不是问题，问题以前是它下面只接了 10dp 的淡出；现在接的是 [SEAM_FADE_DEPTH]。
        // 这条带原来是纯 Bg（画面无论什么色调都沉进黑里），现在换成 [heroLandingColor]：
        // 色相来自底图下缘、亮度只比页面底色高一档，TvHeroSeamFade 顶边取同一个值来接，接缝照样隐形。
        // 取不到色（黑白灰底图或加载失败）时它退回纯 Bg，画出来和改造前一模一样。
        val blended = rememberAmbientBlend(ambient)
        val overlayBake = remember { GradientBake() }
        Box(
            Modifier
                .fillMaxSize()
                // drawWithCache 订阅缓存块里读到的 state：着陆色混色的 600ms 里逐帧重烘，
                // 稳态下一帧也不重烘，只把上一张图再铺一次。
                .drawWithCache {
                    val w = size.width
                    val h = size.height
                    val landing = heroLandingColor(blended())
                    val argb = landing.toArgb()
                    val overlay = overlayBake.image(w, h, landing) { cv ->
                        cv.drawRect(
                            0f, 0f, w, h,
                            Paint().apply {
                                shader = LinearGradient(
                                    0f, 0f, 0f, h * 0.24f,
                                    intArrayOf(0x73000000, 0x00000000),
                                    floatArrayOf(0f, 1f),
                                    Shader.TileMode.CLAMP
                                )
                            }
                        )
                        cv.drawRect(
                            0f, 0f, w, h,
                            Paint().apply {
                                shader = LinearGradient(
                                    0f, 0f, w, 0f,
                                    intArrayOf(0xE0000000.toInt(), 0x66000000, 0x00000000),
                                    floatArrayOf(0f, 0.45f, 1f),
                                    Shader.TileMode.CLAMP
                                )
                            }
                        )
                        cv.drawRect(
                            0f, 0f, w, h,
                            Paint().apply {
                                shader = LinearGradient(
                                    0f, 0f, 0f, h,
                                    intArrayOf(
                                        0x00000000,
                                        argbWithAlpha(argb, 0.14f),
                                        argbWithAlpha(argb, 0.42f),
                                        argbWithAlpha(argb, 0.82f),
                                        argbWithAlpha(argb, 0.98f),
                                        argb
                                    ),
                                    floatArrayOf(0.45f, 0.66f, 0.82f, 0.92f, 0.97f, 1f),
                                    Shader.TileMode.CLAMP
                                )
                            }
                        )
                        // 底边必须走满不透明，差一点就会让底图下边缘透出来、横贯整宽一条亮线。
                        // 烘焙图最后一行放大后正好是 4px，比原来的收尾还宽一点，接缝只会更严实。
                        cv.drawRect(0f, h - 4f, w, h, Paint().apply { color = argb })
                    }
                    val dst = IntSize(w.toInt(), h.toInt())
                    onDrawBehind {
                        drawImage(overlay, dstSize = dst, filterQuality = FilterQuality.Low)
                    }
                }
        )

        // 左对齐内容块：logo/标题 + 元信息 + 操作按钮
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = TvPagePadding, end = 320.dp, bottom = 30.dp)
        ) {
            // 文字块换页时淡出旧、淡入新，底边对齐保证不会上下跳
            AnimatedContent(
                targetState = item,
                transitionSpec = {
                    fadeIn(tween(Motion.SLOW)) togetherWith fadeOut(tween(Motion.FAST))
                },
                contentAlignment = Alignment.BottomStart,
                label = "heroText"
            ) { current ->
                Column {
                    if (titleContent != null) {
                        titleContent(current)
                    } else if (current.logoUrl.isNotBlank()) {
                        AsyncImage(
                            model = current.logoUrl,
                            contentDescription = current.title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.height(110.dp).widthIn(max = 520.dp)
                        )
                    } else {
                        Text(
                            current.title,
                            // Hero 压在底图上：兜底标题钉白，不随颜色模式翻深
                            color = Color.White,
                            style = MaterialTheme.typography.displayLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    TvMetaRow(current)
                    val synopsis = remember(current.overview) { current.overview.trim().replace(Regex("\\s+"), " ") }
                    if (synopsis.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            synopsis,
                            color = Color.White.copy(alpha = 0.72f),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = this@BoxWithConstraints.maxWidth * 0.5f)
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            // Hero 的两个按钮走最大一档：它们旁边是 44sp 的片名，
            // 再用顶栏那一档会显得缩在画面角落里。
            // ↑ 交棒挂在按钮自己身上（规范 R2），两颗都接 ——
            // 之前是顶层看 heroFocused 这个页面级布尔位判断，只有播放键喂得动它，
            // 详情键按 ↑ 就什么都不发生。
            val heroUpKey: Modifier = Modifier
                .onFocusChanged { if (it.hasFocus) onButtonFocus() }
                .onPreviewKeyEvent { e ->
                    if (e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown &&
                        e.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP
                    ) {
                        onNavigateUp()
                        true
                    } else false
                }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvPillButton(
                    text = if (item.positionMs > 5_000L) "继续播放" else "播放",
                    modifier = heroUpKey.focusRequester(playFocusRequester),
                    primary = true,
                    size = TvButtonSize.Lg,
                    hero = true,
                    icon = Icons.Filled.PlayArrow,
                    onClick = onPlay
                )
                TvPillButton(
                    text = "详情",
                    modifier = heroUpKey,
                    primary = false,
                    size = TvButtonSize.Lg,
                    hero = true,
                    icon = Icons.Filled.Info,
                    onClick = onDetail
                )
            }
        }

        // 轮播进度（右下）：序号 + 一条细进度线，替代圆点指示器
        if (heroTotal > 1) {
            Row(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = TvPagePadding, bottom = 30.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${heroIndex + 1} / $heroTotal",
                    color = TvText.Muted,
                    style = MaterialTheme.typography.labelMedium
                )
                // 品牌色在组合阶段取好：drawBehind 不是组合式上下文，读不了 CompositionLocal
                val brandColor = AppColors.Brand
                Box(
                    Modifier
                        .width(120.dp)
                        .height(3.dp)
                        .clip(Radius.Pill)
                        .background(TvText.Hairline)
                        // 在 draw 阶段读 State：进度每秒变几十次，只重画这条线，不重组整个 Hero
                        .drawBehind {
                            drawRect(
                                color = brandColor,
                                size = Size(size.width * progress.value, size.height)
                            )
                        }
                )
            }
        }
        }
    }
}

/** Hero 空态底部的一颗按钮：文案 + 真图标 + 主次 + 动作。
 *  以前图标是塞在文案里的 Unicode 字符（＋ / ↻），整套电视界面因此有两套图标语汇。 */
private class HeroAction(
    val label: String,
    val icon: ImageVector,
    val primary: Boolean,
    val onClick: () -> Unit
)

/** 空态主视觉：轮播区常驻，但没有可推荐内容时占位。
 *  高度走同一个 [tvHeroHeight]，避免首页在「加载中 / 空库」之间上下跳版。 */
@Composable
internal fun TvHeroEmpty(
    libraryIsEmpty: Boolean,
    hasCategories: Boolean,
    error: String?,
    scanning: Boolean,
    firstFocusRequester: FocusRequester,
    onNavigateUp: () -> Unit,
    onAddSource: () -> Unit,
    onRefresh: () -> Unit,
    /** CTA 拿到焦点：首页已经不站在卡片上了 */
    onButtonFocus: () -> Unit
) {
    val text: String
    val hint: String?
    when {
        scanning -> {
            text = "正在同步媒体库…"
            hint = null
        }
        error != null -> {
            text = error
            hint = "可以重新同步，或换一个来源/线路"
        }
        libraryIsEmpty -> {
            text = "还没有内容"
            hint = "先添加 Emby 或 TVBox 来源，再同步媒体库"
        }
        hasCategories -> {
            text = "暂无推荐内容"
            hint = "可从下方分类入口浏览"
        }
        else -> {
            text = "当前来源/站点暂无内容"
            hint = "重新同步，或换一个来源/线路"
        }
    }
    val bg = remember {
        Brush.verticalGradient(0f to AppColors.Surface, 0.7f to AppColors.Bg, 1f to AppColors.Bg)
    }

    /**
     * 空态必须自带可聚焦入口（规范 R6）。
     * 这块 Hero 高 360~620dp、540dp 视口上是 378dp（七成），原来里面一个字都点不动：
     * 顶栏按 ↓ 时焦点在视口内找不到任何落点，就卡在工具栏上出不去。
     * 按钮行的位置与有内容时一致（左下、bottom 44dp），顶栏的 ↓ 正好落在这第一颗上。
     */
    val actions: List<HeroAction> = when {
        libraryIsEmpty -> listOf(HeroAction("添加来源", Icons.Filled.Add, true, onAddSource))
        scanning -> listOf(HeroAction("添加来源", Icons.Filled.Add, false, onAddSource))
        else -> listOf(
            HeroAction("立即同步", Icons.Filled.Refresh, true, onRefresh),
            HeroAction("切换来源", Icons.Filled.Add, false, onAddSource)
        )
    }
    val heroUpKey: Modifier = Modifier
        .onFocusChanged { if (it.hasFocus) onButtonFocus() }
        .onPreviewKeyEvent { e ->
            if (e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown &&
                e.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP
            ) {
                onNavigateUp()
                true
            } else false
        }

    Box(
        Modifier
            .fillMaxWidth()
            .height(tvHeroHeight())
            .background(bg)
    ) {
        TvEmptyState(
            text = text,
            hint = hint,
            modifier = Modifier.align(Alignment.Center)
        )
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = TvPagePadding, bottom = 44.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            actions.forEachIndexed { index, action ->
                val focusModifier = if (index == 0) heroUpKey.focusRequester(firstFocusRequester) else heroUpKey
                TvPillButton(
                    text = action.label,
                    modifier = focusModifier,
                    primary = action.primary,
                    size = TvButtonSize.Lg,
                    icon = action.icon,
                    onClick = action.onClick
                )
            }
        }
    }
}

/**
 * 「继续观看」卡片的操作菜单（长按 OK / Menu 键唤出）。
 *
 * 弹窗形态用 Compose Dialog + tvMoveFocus 抢焦（规范 R1/R5）：
 * 独立窗口天然把焦点关在菜单里，不会逃逸到下层海报行，Back 也由窗口自己处理。
 * 行样式复用 [TvMenuRow]，与来源页功能菜单同一套视觉。
 */
@Composable
internal fun TvContinueActionDialog(
    item: MediaEntity,
    onPlay: () -> Unit,
    onDetail: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // 等一帧：Dialog 内容要先组合完成，requester 才挂得上
        withFrameNanos { }
        tvMoveFocus(firstFocus)
    }

    val progress = if (item.durationMs > 0L) (item.positionMs.toFloat() / item.durationMs).coerceIn(0f, 1f) else 0f

    Dialog(
        onDismissRequest = onDismiss,
        // 关掉平台默认窄宽度，宽度由弹窗自己按屏幕尺寸决定
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(
            Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            val dialogWidth = (maxWidth * 0.34f).coerceIn(380.dp, 640.dp)
            Column(
                Modifier
                    .width(dialogWidth)
                    .background(AppColors.SurfaceHi, Radius.Lg)
                    .padding(vertical = 26.dp)
            ) {
                Text(
                    text = item.continueTitle,
                    color = TvText.Title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 26.dp)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = buildString {
                        val epLabel = item.episodeLabel().takeIf { it.isNotBlank() && it != item.continueTitle }
                        if (epLabel != null) {
                            append(epLabel)
                            append(" · ")
                        }
                        append("看到 ")
                        append(tvClock(item.positionMs))
                        if (item.durationMs > 0L) {
                            append(" / ")
                            append(tvClock(item.durationMs))
                            append("（")
                            append((progress * 100).roundToInt())
                            append("%）")
                        }
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = TvText.Meta,
                    modifier = Modifier.padding(horizontal = 26.dp)
                )
                Spacer(Modifier.height(18.dp))
                Column(
                    Modifier.padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TvMenuRow(
                        text = "继续播放",
                        hint = "从上次的位置接着看",
                        modifier = Modifier.focusRequester(firstFocus),
                        onClick = onPlay
                    )
                    TvMenuRow(text = "查看详情", onClick = onDetail)
                    TvMenuRow(
                        text = "清除观看记录",
                        danger = true,
                        onClick = onClear
                    )
                }
            }
        }
    }
}

/** 观看进度菜单用的简短时长：小时才带时位，分钟秒补足 */
private fun tvClock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0L)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** 电视端「线路管理」弹窗：列出全部线路，点击切换屏蔽/恢复状态。
 *  屏蔽的线路右上角显示红减号图标。6 列固定，上下滚动。
 *  NF「切换来源/线路」抽屉在用。 */
@Composable
internal fun TvLineManagerDialog(
    sourceId: Long,
    vm: LibraryViewModel,
    onDismiss: () -> Unit,
    matchDrawerWidth: Boolean = false
) {
    var allSites by remember { mutableStateOf<List<TvBoxSite>>(emptyList()) }
    var hidden by remember(sourceId) {
        mutableStateOf(Prefs.allHiddenLines().filter { it.startsWith("$sourceId:") }.map { it.substringAfter(":") }.toSet())
    }
    LaunchedEffect(sourceId) {
        allSites = vm.allSitesForSource(sourceId)
        hidden = Prefs.allHiddenLines().filter { it.startsWith("$sourceId:") }.map { it.substringAfter(":") }.toSet()
    }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // 先立即抢，抢不到再等帧：避免弹窗打开的第一帧焦点环画在兜底元件上（闪现）。
        if (!tvMoveFocus(firstFocus)) {
            withFrameNanos { }
            tvMoveFocus(firstFocus)
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(
            Modifier.fillMaxSize().then(
                if (matchDrawerWidth) Modifier.padding(vertical = 32.dp)
                else Modifier.padding(horizontal = 48.dp, vertical = 32.dp)
            ),
            contentAlignment = Alignment.Center
        ) {
            val dialogWidth =
                if (matchDrawerWidth) (maxWidth * 0.8f).coerceAtMost(1600.dp)
                else (maxWidth * 0.80f).coerceIn(560.dp, 1280.dp)
            val gridMaxHeight = maxHeight * 0.70f
            Column(
                Modifier.width(dialogWidth).background(AppColors.SurfaceHi, Radius.Lg).padding(vertical = 26.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 26.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "线路管理",
                        color = TvText.Title,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "共 ${allSites.size} 条，${hidden.size} 条已屏蔽",
                        color = TvText.Body,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(Modifier.height(14.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    modifier = Modifier.fillMaxWidth().heightIn(max = gridMaxHeight),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    gridItems(allSites, key = { "mgr_${it.key}" }) { site ->
                        val isHidden = hidden.contains(site.key)
                        TvManageLineChip(
                            name = site.name,
                            hidden = isHidden,
                            onClick = {
                                val newHidden = !isHidden
                                Prefs.setLineHidden(sourceId, site.key, newHidden)
                                hidden = if (newHidden) hidden + site.key else hidden - site.key
                                vm.reloadHomeSites(sourceId)
                            },
                            modifier = if (site == allSites.first()) Modifier.focusRequester(firstFocus) else Modifier
                        )
                    }
                }
            }
        }
    }
}

/** 线路管理里的单个线路 chip：右上角红减号（屏蔽中）/ 无角标（可见） */
@Composable
private fun TvManageLineChip(
    name: String,
    hidden: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .fillMaxWidth()
            .clip(Radius.Md)
            .background(
                when {
                    focused -> Color.White
                    hidden -> AppColors.Surface.copy(alpha = 0.55f)
                    else -> AppColors.Surface
                }
            )
            .then(if (focused) Modifier.border(2.dp, AppColors.Focus, Radius.Md) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(
            name,
            color = when {
                focused -> Color.Black
                hidden -> TvText.Section.copy(alpha = 0.55f)
                else -> TvText.Section
            },
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (hidden) {
            Icon(
                Icons.Filled.RemoveCircle,
                contentDescription = "已屏蔽",
                tint = Color(0xFFE5484D),
                modifier = Modifier.size(16.dp).align(Alignment.TopEnd)
            )
        }
    }
}
