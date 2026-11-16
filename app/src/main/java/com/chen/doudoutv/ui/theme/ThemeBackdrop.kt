package com.chen.doudoutv.ui.theme

import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import com.chen.doudoutv.ui.components.GradientBake

/**
 * 页面根背景：近黑底色之上，一层品牌色光晕。
 *
 * 两层光：
 *   ① 天光（两端）：一团从屏幕上方洒下来的品牌光，亮心压在顶边之外（cy 为负），
 *     屏幕顶边拿到接近峰值的颜色，往下渐变到透明、铺到屏中。它跟着屏幕走、不跟着内容走：
 *     没有 Hero 的页面（设置 / 来源 / 搜索）顶部一定看得见。
 *   ② 地火（仅手机，2026-11-15 拍板）：同一枚椭圆翻到底部 —— 亮心压在底边之外
 *     （cy 为 1.10h），光从导航栏背后向上晕开、到 0.62h 归零，页面下重上轻。
 *     电视端不叠这层：背景常年被 Hero 占住，底部导航是悬浮磨砂条，十英尺外读不出差别，
 *     用户拍板电视保持单层天光、观感不动。
 * 都用椭圆而不是正圆：电视是宽屏，光要横向铺开、纵向收住（rx=1.25W），
 * 正圆在电视上会糊成一团。Compose 自带的 radialGradient 只能画正圆，这里用 Android 的
 * RadialGradient 配 X 向缩放矩阵拉成椭圆。
 *
 * 强度：天光手机 α0.46、电视 α0.30；地火只在中段（手机屏）比天光收一档（×0.74）。
 * 电视一条光铺满 1920 宽、感知更强，且首页 Hero 会盖住顶部，所以收一档；手机屏幕小、要多给一点才看得见。
 *
 * 曾经还叠过一层 ⑥ 胶片颗粒（256×256 噪点图 BitmapShader REPEAT 铺满全屏），已摘掉：
 * 它在 Mali-G52 上每像素要做一次依赖纹理寻址，1920×1080 一遍就是 2M 像素，
 * 而 display list 每帧重放 —— 实测电视端 GPU 中位 23ms（预算 16.67ms），同机 OK影视 只有 6ms。
 * 观感收益也不值这个价。要再加质感层，先量 GPU 中位，别只看静态效果图。
 *
 * 红线：只画在 Bg 之上，不动 Bg/Surface/文字对比度基线；色板 id 不改（色值可按拍板换）；详情页不接这一层。
 *
 * 走 [GradientBake]：光晕烘成 1/4 分辨率的一张图，每帧只采纹理、不再逐像素求径向渐变。
 * 两层光烘进同一张图、一次 drawImage —— 逐像素求值只在重烘那一次发生，叠两层不加每帧成本。
 * 绘制范围也只到各自 alpha 归零的线，两头中段（0.40h~0.62h）是透明像素，采了也不显。
 */
@Composable
fun Modifier.themeBackdrop(): Modifier {
    val brand = AppColors.Brand
    val isTv = LocalTvMode.current
    val glowAlpha = if (isTv) 0.30f else 0.46f
    val bake = remember { GradientBake() }
    return this
        .background(AppColors.Bg)
        .drawWithCache {
            val w = size.width
            val h = size.height
            val rx = w * 1.25f
            val img = bake.image(w, h, brand to glowAlpha) { cv ->
                // 外圈收到「品牌色减 alpha」而不是 Color.Transparent：后者把 RGB 一起拖向纯黑，
                // 淡出末尾会压出一条暗边；保持 RGB 不变只降 alpha，淡出才干净。
                run {
                    // ① 天光：亮心在顶边外，alpha 归零线在 0.384h，下面不画
                    val cy = -h * 0.12f
                    val ry = h * 0.72f
                    val shader = RadialGradient(
                        w * 0.5f, cy, ry,
                        intArrayOf(
                            brand.copy(alpha = glowAlpha).toArgb(),
                            brand.copy(alpha = glowAlpha * 0.39f).toArgb(),
                            brand.copy(alpha = 0f).toArgb()
                        ),
                        floatArrayOf(0f, 0.34f, 0.70f),
                        Shader.TileMode.CLAMP
                    ).apply { setLocalMatrix(Matrix().apply { setScale(rx / ry, 1f, w * 0.5f, cy) }) }
                    cv.drawRect(0f, 0f, w, h * 0.40f, Paint().apply { this.shader = shader })
                }
                if (!isTv) {
                    // ② 地火：同一枚椭圆翻到底部，归零线在 0.624h，上面不画
                    val peak = glowAlpha * 0.74f
                    val cy = h * 1.10f
                    val ry = h * 0.68f
                    val shader = RadialGradient(
                        w * 0.5f, cy, ry,
                        intArrayOf(
                            brand.copy(alpha = peak).toArgb(),
                            brand.copy(alpha = peak * 0.39f).toArgb(),
                            brand.copy(alpha = 0f).toArgb()
                        ),
                        floatArrayOf(0f, 0.34f, 0.70f),
                        Shader.TileMode.CLAMP
                    ).apply { setLocalMatrix(Matrix().apply { setScale(rx / ry, 1f, w * 0.5f, cy) }) }
                    cv.drawRect(0f, h * 0.62f, w, h, Paint().apply { this.shader = shader })
                }
            }
            val dst = IntSize(w.toInt(), h.toInt())
            onDrawBehind {
                drawImage(img, dstSize = dst, filterQuality = FilterQuality.Low)
            }
        }
}

/**
 * 顶部满幅大图与页面背景之间的收色带：从 [seam] 实色淡到完全透明，把背景接出来。
 *
 * 大图的渐隐无论配成什么色，接缝都正好压在它自己的底边上：配纯黑是一条黑线，
 * 配猜出来的品牌色混合值又只在那一个点准，横着走两步就不对。
 * 所以让大图只管收到纯 [seam]，这条带的顶边取同一个 [seam]（发界因此隐形），
 * 底边 alpha 归零露出 [themeBackdrop] 画出来的真实背景 —— 接缝被挪进渐变中段，不再靠配色去猜。
 *
 * 收尾用 [seam] 自己减 alpha 而不是 Color.Transparent：后者会把 RGB 一起拖向纯黑，
 * 淡出末尾反而压出一条暗边。
 */
@Composable
fun Modifier.themeSeamFade(height: Dp, seam: Color = AppColors.Bg): Modifier {
    val brush = remember(seam) {
        Brush.verticalGradient(0f to seam, 1f to seam.copy(alpha = 0f))
    }
    return this
        .fillMaxWidth()
        .height(height)
        .background(brush)
}
