@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.chen.doudoutv.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntSize
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.chen.doudoutv.data.model.MediaEntity
import com.chen.doudoutv.ui.theme.AppColors
import com.chen.doudoutv.ui.theme.Radius
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 首页顶部大图轮播（Netflix 的「精选」区）。
 *
 * 视觉要点：
 * · 16:9 背景剧照铺满，渐变压暗后沉入页面底色，消除硬边界
 * · 标题优先用刮削到的艺术字（ClearLogo，透明 PNG）；没有才回落到文字
 * · 只保留「播放 / 详情」两个按钮，不再堆影片简介，画面更干净
 * · 遮罩透明度整体调低，让剧照本身的色彩透出来，营造艺术氛围
 *
 * 分层结构（本次调整的重点）：
 *   第 1 层 — HorizontalPager：只有背景图和压暗遮罩，跟着手指/自动播放一起滑动。
 *   第 2 层 — 文字层：标题、年份评分、两个按钮。挂在 Pager 外面，
 *            按 pagerState.currentPage 用 AnimatedContent 做淡入淡出，
 *            所以切换时是「旧页信息淡出 → 新页信息淡入」，不会整块横移。
 *   第 3 层 — 分页指示器：同样挂在 Pager 外面、固定在图片上层同一位置，
 *            圆点只切换“哪一个高亮”，本身不随轮播位移。
 */
@Composable
fun HeroBanner(
    items: List<MediaEntity>,
    isTv: Boolean,
    onOpen: (MediaEntity) -> Unit,
    onPlay: (MediaEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { items.size })

    LaunchedEffect(items.size) {
        if (items.size <= 1) return@LaunchedEffect
        while (true) {
            delay(6_500)
            val next = (pagerState.currentPage + 1) % items.size
            pagerState.animateScrollToPage(next)
        }
    }

    // 淡入淡出的时长；进出用同一时长，交替时才不会有一闪而过的空档
    val fadeMs = 460

    // 每页海报「可见底边」的沉色（采样后向底色压暗）：遮罩沉到它，
    // 悬出下边界的减淡带再接着它往下化开 —— 色链连贯、不留硬边
    val sinkColors = remember { mutableStateMapOf<Int, Color>() }
    val currentSink = sinkColors[pagerState.currentPage] ?: AppColors.Bg
    // 过渡带落色：跨页时按 600ms 插值（与电视端环境光同一条曲线），换页不跳色
    val seamLanding by animateColorAsState(
        targetValue = currentSink,
        animationSpec = tween(durationMillis = 600),
        label = "seamLanding"
    )
    // Crop 居中裁切后可见的底边不是原图底边：按显示框宽高比反推可见窗口再取色
    val boxAspect = LocalConfiguration.current.screenWidthDp.toFloat() /
        (if (isTv) 430f else 460f)

    Box(
        modifier
            .fillMaxWidth()
            .height(if (isTv) 430.dp else 460.dp)
    ) {
        // ================= 第 1 层：图 + 遮罩（唯一参与滑动的内容） =================
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val item = items[page]
            Box(Modifier.fillMaxSize()) {
                val art = item.backdropUrl.ifBlank { item.posterUrl }
                if (art.isNotBlank()) {
                    // 直接 Crop：手机这一格接近正方（宽 × 460dp），竖版海报裁掉的是
                    // 海报自身的上下留白，主体还在画面里。
                    AsyncImage(
                        model = art,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    // 底边取色：allowHardware(false) 才拿得到可读像素；同参数请求命中
                    // Coil 内存缓存，不会重复下载。采完向底色压暗 —— 保住标题/按钮可读性
                    val ctx = LocalContext.current
                    LaunchedEffect(art) {
                        val bmp = runCatching {
                            val result = ctx.imageLoader.execute(
                                ImageRequest.Builder(ctx)
                                    .data(art)
                                    .allowHardware(false)
                                    .size(96)
                                    .build()
                            )
                            (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                        }.getOrNull() ?: return@LaunchedEffect
                        sinkColors[page] = withContext(Dispatchers.Default) {
                            sampleBottomSink(bmp, AppColors.Bg, 0.35f, boxAspect)
                        }
                    }
                } else {
                    Box(Modifier.fillMaxSize().background(AppColors.Surface))
                }

                // 纵向遮罩：顶部只轻微压暗保住白字。手机端底部沉进海报自己的底边色
                // （没采到色前沉页面底色），再由下方悬出边界的减淡带接着往下化开；
                // 电视端维持原样 —— 沉页面底色，不采色不接带，观感不动。
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.06f),
                                0.42f to Color.Black.copy(alpha = 0.16f),
                                0.78f to Color.Black.copy(alpha = 0.40f),
                                1f to (if (isTv) AppColors.Bg else sinkColors[page] ?: AppColors.Bg)
                            )
                        )
                )
                // 横向遮罩：只在左侧给标题留一层薄纱，右侧完全放开，避免压死画面。
                // 标题与按钮都是居中排版，左侧这条纱不需要承担可读性，数值越低越透。
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                0f to AppColors.Bg.copy(alpha = 0.40f),
                                0.45f to AppColors.Bg.copy(alpha = 0.10f),
                                1f to Color.Transparent
                            )
                        )
                )
            }
        }

        // ===== 过渡：参照电视端 TvHeroSeamFade 的「落色 + 长尾 alpha 曲线」画法 =====
        // 跟电视端同一套思路：先取海报自身底边的落色，再按一条长尾曲线一路化进页面底色，
        // 尾巴收得极缓，接缝处不会出现「戛然而止」的硬边。
        // 手机 GPU 富余，比电视端多做四件事（电视端一行不动）：
        //   ① alpha 档位 6 → 13 档：手机屏更近更亮，档位少会看出横向色阶纹
        //   ② 加一层「向内叠」：过渡从海报内部就开始，接缝位置彻底看不见（电视是纯向外）
        //   ③ 落色随翻页做 600ms 插值：换页时色带不跳色，跟着画面淡过来
        //   ④ 接缝中线补一点极淡的径向溢光：色带不至于像一块灰板，像光从画面里漫出来
        // 渐变全部烘焙成 1/4 分辨率纹理（GradientBake），稳态下每帧只采样一张图。
        if (!isTv) {
            val bakeIn = remember { GradientBake() }
            val bakeOut = remember { GradientBake() }
            val seamArgb = seamLanding.toArgb()

            // ① 内叠层：贴在海报下边界内侧，由落色反向化开 96dp
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(SeamOverlap)
                    .align(Alignment.BottomCenter)
                    .drawWithCache {
                        val w = size.width
                        val h = size.height
                        val img = bakeIn.image(w, h, "seamIn-$seamArgb") { canvas ->
                            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                            paint.shader = LinearGradient(
                                0f, h, 0f, 0f,
                                argbWithAlpha(seamArgb, 0.88f),
                                argbWithAlpha(seamArgb, 0f),
                                Shader.TileMode.CLAMP
                            )
                            canvas.drawRect(0f, 0f, w, h, paint)
                        }
                        onDrawBehind {
                            drawImage(
                                img,
                                dstSize = IntSize(w.toInt(), h.toInt()),
                                filterQuality = FilterQuality.Low
                            )
                        }
                    }
            )

            // ② 外延层：从海报下边界往下 260dp，13 档长尾曲线化进底色
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(SeamTail)
                    .align(Alignment.BottomCenter)
                    .offset(y = SeamTail)
                    .drawWithCache {
                        val w = size.width
                        val h = size.height
                        val img = bakeOut.image(w, h, "seamOut-$seamArgb-$h") { canvas ->
                            val colors = IntArray(SEAM_ALPHA.size) { k ->
                                argbWithAlpha(seamArgb, SEAM_ALPHA[k])
                            }
                            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                            paint.shader = LinearGradient(
                                0f, 0f, 0f, h,
                                colors, SEAM_POS, Shader.TileMode.CLAMP
                            )
                            canvas.drawRect(0f, 0f, w, h, paint)
                        }
                        onDrawBehind {
                            drawImage(
                                img,
                                dstSize = IntSize(w.toInt(), h.toInt()),
                                filterQuality = FilterQuality.Low
                            )
                        }
                    }
            )

            // ③ 溢光：接缝中线一层极淡的径向光，只给色带一点亮度层次
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(SeamGlowDepth)
                    .align(Alignment.BottomCenter)
                    .offset(y = SeamGlowDepth * 0.30f)
                    .background(
                        Brush.radialGradient(
                            0f to seamLanding.copy(alpha = 0.16f),
                            0.55f to seamLanding.copy(alpha = 0.05f),
                            1f to seamLanding.copy(alpha = 0f)
                        )
                    )
            )
        }

        // ================= 第 2 层：文字 + 按钮（淡入淡出，不做横向位移） =================
        AnimatedContent(
            targetState = pagerState.currentPage,
            transitionSpec = {
                fadeIn(animationSpec = tween(durationMillis = fadeMs)) togetherWith
                    fadeOut(animationSpec = tween(durationMillis = fadeMs))
            },
            label = "heroInfo",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                // 手机端把文字层定高：标题有一行、两行、艺术字三种高度，框高一变，
                // 交叉淡入淡出时新旧内容按中心对齐，播放/详情按钮就上下跳。
                // 定高 + 内容贴底，切到哪一页按钮都钉在同一处。电视端不动。
                .then(if (isTv) Modifier else Modifier.height(210.dp))
        ) { page ->
            val item = items.getOrNull(page)
            if (item != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (isTv) Modifier else Modifier.fillMaxHeight())
                        // 底部留出 46dp，让下方的固定指示器有独立空间，两者不重叠
                        .padding(start = 22.dp, end = 22.dp, bottom = 46.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement =
                        if (isTv) Arrangement.spacedBy(10.dp)
                        else Arrangement.spacedBy(10.dp, Alignment.Bottom)
                ) {
                    // 标题：有刮削艺术字（ClearLogo）就用图，没有才用文字兜底
                    if (item.logoUrl.isNotBlank()) {
                        AsyncImage(
                            model = item.logoUrl,
                            contentDescription = item.title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .height(if (isTv) 100.dp else 80.dp)
                                .widthIn(max = if (isTv) 420.dp else 360.dp)
                        )
                    } else {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.6.sp,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }

                    val meta = metaLine(item)
                    if (meta.isNotBlank()) {
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.78f),
                            letterSpacing = 1.2.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }

                    Row(
                        Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 主按钮：品牌渐变实底 + 光晕，压黑字，视觉最突出
                        AppButton(
                            text = "播放",
                            onClick = { onPlay(item) },
                            variant = AppButtonVariant.Primary,
                            shape = Radius.Md,
                            icon = Icons.Filled.PlayArrow,
                            iconSize = 20.dp,
                            contentPadding = PaddingValues(horizontal = 26.dp, vertical = 10.dp),
                            fontWeight = FontWeight.SemiBold
                        )
                        // 次按钮：磨砂玻璃 + 发丝描边，压白字，与主按钮拉开层级
                        AppButton(
                            text = "详情",
                            onClick = { onOpen(item) },
                            variant = AppButtonVariant.Secondary,
                            onImage = true,
                            shape = Radius.Md,
                            contentPadding = PaddingValues(horizontal = 26.dp, vertical = 10.dp),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // ================= 第 3 层：分页指示器（固定位置，不随轮播滑动） =================
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            repeat(items.size) { i ->
                val active = pagerState.currentPage == i
                Box(
                    Modifier
                        .size(width = if (active) 16.dp else 6.dp, height = 6.dp)
                        .clip(Radius.Pill)
                        .background(
                            if (active) AppColors.Brand else Color.White.copy(alpha = 0.35f)
                        )
                )
            }
        }
    }
}

/**
 * 取「显示框里真正可见的海报底边」一条带的平均色，再向 bg 压暗 mix。
 * ContentScale.Crop 居中裁切：图比框瘦时纵向被裁，可见窗口只占原图一部分 ——
 * 之前直接采原图最底 12%，采到的是被裁掉的画面，颜色对不上，底边就露出一条硬线。
 */
private fun sampleBottomSink(bitmap: android.graphics.Bitmap, bg: Color, mix: Float, boxAspect: Float): Color {
    val w = bitmap.width
    val h = bitmap.height
    if (w == 0 || h == 0) return bg
    val a = w.toFloat() / h
    val visH = if (a > boxAspect) h.toFloat() else h * a / boxAspect
    val vEnd = (h + visH) / 2f
    val y0 = (vEnd - visH * 0.10f).toInt().coerceIn(0, h - 1)
    val y1 = vEnd.toInt().coerceIn(y0 + 1, h)
    val stepX = (w / 48).coerceAtLeast(1)
    var r = 0f; var g = 0f; var b = 0f; var n = 0f
    var y = y0
    while (y < y1) {
        var x = 0
        while (x < w) {
            val c = bitmap.getPixel(x, y)
            r += (c shr 16 and 0xFF) / 255f
            g += (c shr 8 and 0xFF) / 255f
            b += (c and 0xFF) / 255f
            n += 1f
            x += stepX
        }
        y += 2
    }
    if (n == 0f) return bg
    return lerp(Color(r / n, g / n, b / n), bg, mix)
}

private fun metaLine(item: MediaEntity): String {
    val parts = mutableListOf<String>()
    if (item.year > 0) parts += item.year.toString()
    if (item.rating > 0) parts += "★ %.1f".format(item.rating)
    if (item.genres.isNotBlank()) {
        parts += item.genres.split(",", "/", " ", "，").first().trim()
    }
    return parts.joinToString("  ·  ")
}

// ===== 手机端轮播底部过渡参数（只作用于手机，电视端不用） =====
/** 内叠层高度：过渡从海报内部就开始，接缝不可见 */
private val SeamOverlap = 96.dp
/** 外延层高度：从海报下边界往下延伸的化开距离 */
private val SeamTail = 260.dp
/** 溢光层高度 */
private val SeamGlowDepth = 150.dp
/** 13 档长尾曲线：位置（电视端是 6 档，手机加密一倍多，消除色阶横纹） */
private val SEAM_POS = floatArrayOf(
    0f, 0.06f, 0.13f, 0.21f, 0.30f, 0.40f, 0.50f,
    0.61f, 0.72f, 0.82f, 0.90f, 0.96f, 1f
)
/** 对应的 alpha：开局掉得快、尾巴拖得极长，接到页面底色时已接近 0 */
private val SEAM_ALPHA = floatArrayOf(
    1f, 0.92f, 0.81f, 0.68f, 0.54f, 0.41f, 0.29f,
    0.19f, 0.12f, 0.07f, 0.035f, 0.012f, 0f
)
