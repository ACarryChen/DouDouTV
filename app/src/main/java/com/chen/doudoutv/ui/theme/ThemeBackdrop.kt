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
 * 页面根背景：近黑底 + 品牌色光晕（烘进一张 1/4 分辨率图，每帧只采纹理不逐像素求渐变）。
 * ① 天光（两端，屏幕顶边外亮心）：跟着屏幕走，无 Hero 页面顶部必见；电视 α0.30 / 手机 α0.46。
 * ② 地火（仅手机，底部对称）：电视端不叠——背景被 Hero 占住，十英尺外读不出差别（拍板维持单层）。
 * 用 X 向缩放矩阵把径向渐变拉成椭圆（rx=1.25W）：电视宽屏正圆会糊成一团。
 * 性能档案：曾叠过 ⑥ 胶片颗粒层（BitmapShader REPEAT 全屏），在 Mali-G52 上 GPU 中位 23ms
 * （预算 16.67ms，同机 OK影视 6ms）——display list 每帧重放、2M 像素纹理寻址，已摘。
 * 要再加质感层，先量 GPU 中位，别只看静态效果图。
 * 红线：只画在 Bg 之上不动对比度基线；色板 id 不改；详情页不接。
 */
@Composable
fun Modifier.themeBackdrop(): Modifier {
    val brand = AppColors.Brand
    val isTv = LocalTvMode.current
    // 浅色底吃色重：手机浅色 0.18 几乎看不见（2026-11-24 装机反馈），提到 0.34；暗色保持 0.46。
    // TV 天光按 2026-11-24 反馈提到 0.50（α 档位），TV 暗浅两档同用。
    val glowAlpha = when {
        isTv -> 0.50f
        AppColors.lightOn() -> 0.34f
        else -> 0.46f
    }
    if (glowAlpha == 0f) return this.background(AppColors.Bg)
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
                    // ① 天光：亮心在顶边外；ry 提到 0.95h（2026-11-24 反馈「高度增加一些」），
                    //    alpha 归零线到 0.545h，下面不画
                    val cy = -h * 0.12f
                    val ry = h * 0.95f
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
                    cv.drawRect(0f, 0f, w, h * 0.56f, Paint().apply { this.shader = shader })
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
