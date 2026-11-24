package com.chen.doudoutv.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.chen.doudoutv.ui.theme.AppColors

/** 玻璃底栏的一格：图标 + 文字 */
data class GlassTabItem(val label: String, val icon: ImageVector)

// 滚动内容需避让底栏高度 50dp 与底部留白 24dp。
val GlassBarClearance: Dp = 74.dp

/** 记一个坐标：绘制时现读，不进重组 */
private class MutableOffset {
    var value: Offset = Offset.Zero
}

/**
 * 页面背景抓取器：底栏靠它拿到「自己脚下那块内容」去做真模糊。
 *
 * Android 没有系统级的 backdrop blur —— Modifier.blur 模糊的是节点自己，看不见下层。
 * 要做到「透过底栏看到下面被模糊的内容」，只能先把页面内容离屏录一份，
 * 再从这份里取底栏脚下那一条去做高斯。
 *
 * 录的不是整屏而是页面底部一条（[stripHeight]）：整屏离屏纹理在 1440×3200 上
 * 一次就是十几 MB，而且每帧都要重新光栅化；只录底栏脚下够用的一条约 2MB。
 */
class GlassBackdrop internal constructor(internal val layer: GraphicsLayer) {
    /** 抓取节点在根坐标里的位置 */
    internal var originInRoot: Offset = Offset.Zero

    /** 抓取条顶边在抓取节点内的 y（px） */
    internal var stripTop: Float = 0f
}

@Composable
fun rememberGlassBackdrop(): GlassBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) {
        GlassBackdrop(layer).also { it.layer.clip = true }
    }
}

/**
 * 抓取：把节点内容录进 [backdrop] 的离屏图层，只留页面底部 [stripHeight] 那一条。
 *
 * 录的时候把内容整体上移一条的高度，于是这一层的原点正好落在页面底部那条的顶边上；
 * 底栏再按根坐标差值平移画它，就正好取到自己脚下那截内容。
 */
fun Modifier.glassBackdropCapture(
    backdrop: GlassBackdrop,
    stripHeight: Dp = 160.dp
): Modifier = drawWithContent {
    val pageH = size.height
    val stripPx = stripHeight.toPx().coerceAtMost(pageH)
    if (stripPx >= 1f && size.width >= 1f) {
        val stripTop = pageH - stripPx
        backdrop.stripTop = stripTop
        backdrop.layer.record(IntSize(size.width.toInt(), stripPx.toInt())) {
            translate(top = -stripTop) {
                this@drawWithContent.drawContent()
            }
        }
    }
    drawContent()
}

/**
 * 一块玻璃面板：底栏和独立的玻璃按钮共用这一套材质。
 *
 * 材质分层（自下而上）：
 *   ① 栏脚下方的内容，离屏录下来模糊后垫底 —— Android 没有系统级 backdrop blur，
 *     Modifier.blur 模糊的是节点自己，看不见下层，所以只能自己录一份再取；
 *   ② 暗底 20%（压不下去，压暗就变成一块黑玻璃）；
 *   ③ 磨砂白 9% —— 磨砂是往上调的；
 *   ④ 斜向镜面扫光 + 顶边高光描边。
 */
@Composable
private fun GlassSurface(
    shape: RoundedCornerShape,
    backdrop: GlassBackdrop?,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    // Modifier.blur 只在 Android 12（API 31）以上真正生效，低版本上它是个空操作：
    // 那时会把底下的内容原样（不模糊）切一块出来贴在栏里，比不透明底还难看，
    // 所以低版本直接退回半透明底色。
    val blurUsable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    // 面板自己在根坐标里的位置：抓取层按这个差值平移，才对得上面板脚下的内容
    val selfRoot = remember { MutableOffset() }

    Box(
        modifier
            .onGloballyPositioned { selfRoot.value = it.positionInRoot() }
            .clip(shape)
    ) {
        // ① 面板脚下的内容：抓来模糊后垫底。抓不到（或系统不支持模糊）就退回半透明底色
        if (backdrop != null && blurUsable) {
            Box(
                Modifier
                    .fillMaxSize()
                    .blur(10.dp)
                    .drawBehind {
                        val dx = backdrop.originInRoot.x - selfRoot.value.x
                        val dy = backdrop.originInRoot.y + backdrop.stripTop - selfRoot.value.y
                        translate(dx, dy) { drawLayer(backdrop.layer) }
                    }
            )
        } else {
            Box(Modifier.fillMaxSize().background(AppColors.GlassBase.copy(alpha = 0.62f)))
        }

        // ② 暗底只留薄薄一层，再叠一层磨砂白把玻璃「提亮」
        Box(Modifier.fillMaxSize().background(AppColors.GlassShade.copy(alpha = 0.20f)))
        Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.09f)))

        // ③ 斜向扫光 + ④ 顶边高光
        Box(Modifier.fillMaxSize().glassSweep())
        Box(
            Modifier.fillMaxSize().border(
                1.dp,
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.06f))
                ),
                shape
            )
        )

        content()
    }
}

/**
 * 玻璃图标按钮：和底栏同一块材质，只是换成独立的一小块（圆角方块）。
 *
 * 单独用是因为它不属于「标签栏」：底栏切的是页面，这个按钮是动作。
 */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    /** 页面背景抓取器；传 null 就退回半透明底色（没有模糊） */
    backdrop: GlassBackdrop? = null,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    iconSize: Dp = 19.dp
) {
    val brand = AppColors.Brand
    val brandSoft = AppColors.BrandSoft
    GlassSurface(
        shape = RoundedCornerShape(17.dp),
        backdrop = backdrop,
        modifier = modifier
            .size(50.dp)
            .clickable(interactionSource = null, indication = null) { onClick() }
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (selected) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(brand.copy(alpha = 0.14f))
                )
            }
            Icon(
                icon,
                contentDescription = contentDescription,
                // 未选中用白（半透），选中用主题色 —— 与底栏完全一致
                tint = if (selected) brandSoft else AppColors.TextPrimary.copy(alpha = 0.78f),
                modifier = Modifier.size(if (selected) 22.dp else iconSize)
            )
        }
    }
}

/**
 * 手机端底部标签栏：悬浮液态玻璃条，替代 Material NavigationBar（通栏 52dp 切屏底）。
 * 50dp、离底 24dp、只留图标；底色是页面底部的离屏模糊（Android 无系统级 backdrop blur，
 * [GlassBackdrop] 把页面录进离屏层，底栏取自己脚下那条套 BlurEffect 画），透出滚动内容。
 * 高度刻意压死：再高会挡首页最后一行海报，还得让开系统手势区，可用只有 40dp 出头。
 */
@Composable
fun GlassTabBar(
    items: List<GlassTabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    /** 页面背景抓取器；传 null 就退回半透明底色（没有模糊） */
    backdrop: GlassBackdrop? = null,
    modifier: Modifier = Modifier
) {
    val brand = AppColors.Brand
    val brandSoft = AppColors.BrandSoft
    val barShape = RoundedCornerShape(17.dp)
    val cellShape = RoundedCornerShape(13.dp)

    GlassSurface(
        shape = barShape,
        backdrop = backdrop,
        modifier = modifier.height(50.dp)
    ) {
        // ⑤ 三格
        Row(
            Modifier.fillMaxSize().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                val selected = index == selectedIndex
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        // 去掉水波纹（与全局一致）：底衬缩进 6dp 后，整格大的水波纹会在它
                        // 四周露出一圈半透明矩形闪现；选中底衬移到本格就是反馈，不要水波纹。
                        .clickable(interactionSource = null, indication = null) { onSelect(index) },
                    contentAlignment = Alignment.Center
                ) {
                    // 选中底衬：主题色压出来的柔光，不换图标形状。
                    // 上下让开 6dp —— 原来这层铺满整格高，首/末格就顶到玻璃条 17dp 圆角上
                    // （底衬才 13dp、左侧还缩进 4dp），两弧对不齐、角上缺一块，看着像没填圆；
                    // 让开后它是一块完整圆角矩形，整块落在条内，不再和条的圆角打架。
                    if (selected) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .padding(vertical = 6.dp)
                                .clip(cellShape)
                                .background(brand.copy(alpha = 0.14f))
                        )
                    }
                    Icon(
                        item.icon,
                        contentDescription = item.label,
                        // 未选中用白（半透），不用主题里的 TextTertiary ——
                        // 那是给纯色页面上的正文用的，压在玻璃上几乎看不见
                        tint = if (selected) brandSoft else AppColors.TextPrimary.copy(alpha = 0.78f),
                        // 选中格放大一点：没有文字做区分，只能靠图标本身
                        modifier = Modifier.size(if (selected) 22.dp else 19.dp)
                    )
                }
            }
        }
    }
}

/**
 * 斜向镜面扫光：一道从左下往右上的白色渐变带，横过整条玻璃。
 *
 * 只画一条很淡的带（峰值 9% 白），位置固定不动 —— 动起来要每帧重绘，
 * 底栏又是常驻元素，为一点「流光」换常驻重绘不值当。
 */
private fun Modifier.glassSweep(): Modifier = drawWithCache {
    val w = size.width
    val h = size.height
    val sweep = Brush.linearGradient(
        colorStops = arrayOf(
            0f to Color.Transparent,
            0.26f to Color.White.copy(alpha = 0.09f),
            0.44f to Color.Transparent,
            1f to Color.Transparent
        ),
        start = Offset(0f, h),
        end = Offset(w * 0.78f, 0f)
    )
    onDrawBehind { drawRect(sweep) }
}
