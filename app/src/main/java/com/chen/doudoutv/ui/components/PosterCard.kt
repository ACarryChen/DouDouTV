package com.chen.doudoutv.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chen.doudoutv.data.model.MediaEntity
import com.chen.doudoutv.data.model.MediaKind
import com.chen.doudoutv.ui.theme.AppColors
import com.chen.doudoutv.ui.theme.Edge
import com.chen.doudoutv.ui.theme.Lift
import com.chen.doudoutv.ui.theme.Radius

// 卡片用到的形状与渐变都是常量：提到组合外，滚动时每张卡不必每次新建对象
private val PosterShape = Radius.Md
private val TinyShape = Radius.Pill
private val PosterBottomScrim = Brush.verticalGradient(
    0f to Color.Transparent,
    0.45f to Color.Black.copy(alpha = 0.55f),
    1f to Color.Black.copy(alpha = 0.92f)
)

/**
 * 海报卡（海报墙形态）。
 *
 * 四个细节决定"像不像"：
 * 1) 2:3 竖版比例，圆角走 [Radius.Md] 档
 * 2) 静止态勾一道 1dp [Edge.Hairline] 亮边 —— 近黑底上投影读不出来，
 *    卡片和背景的分离只能靠描边和填充明度差，不挂投影也就不多一层 graphicsLayer
 * 3) 聚焦时轻微放大（1.09x）+ 品牌色描边 + 抬起投影 —— TV 遥控器可用性核心
 * 4) 标题默认叠在海报底部渐变上（overlay 模式）；[titleBelow]=true 时标题移到海报下方，
 *    颜色跟随当前颜色模式（分类页网格用，对齐 TV 端口径）
 */
@Composable
fun PosterCard(
    item: MediaEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showProgress: Boolean = true,
    /** 已经刮过：左上角挂 TMDB 官方图标 + 评分（透明底，不衬黑块）；null = 没刮过 */
    scrapedRating: Double? = null,
    /** true = 标题放在海报下方（颜色跟随模式）；false = 标题叠在海报底部渐变上（默认） */
    titleBelow: Boolean = false
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (focused) 1.09f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = 420f
        ),
        label = "poster_scale"
    )

    // 海报本体的公共 BoxModifier（titleBelow=false 时连标题一起包；true 时只包海报图）
    val posterBox: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .aspectRatio(2f / 3f)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .then(
                    if (focused) {
                        Modifier.shadow(
                            elevation = Lift.Focus,
                            shape = PosterShape,
                            clip = false,
                            ambientColor = AppColors.Brand.copy(alpha = 0.35f),
                            spotColor = AppColors.Brand.copy(alpha = 0.55f)
                        )
                    } else Modifier
                )
                .clip(PosterShape)
                .background(if (focused) AppColors.SurfaceRaise else AppColors.Surface)
                .then(
                    if (focused) Modifier.border(2.5.dp, AppColors.Brand, PosterShape)
                    else Modifier.border(1.dp, Edge.Hairline, PosterShape)
                )
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick
                )
                .focusable()
        ) {
            PosterArt(model = item.posterUrl, contentDescription = item.title) {
                PosterFallback(title = item.title, showTitle = false, titleFontSize = 30.sp)
            }

            if (scrapedRating != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(3.dp)
                ) {
                    ScrapedBadge(rating = scrapedRating, compact = true)
                }
            }

            // titleBelow=true：标题移走，海报底部只留进度线（进度线贴海报底边）
            if (titleBelow) {
                if (showProgress && item.positionMs > 0 && item.durationMs > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        ProgressLine(item.positionMs.toFloat() / item.durationMs.toFloat())
                    }
                }
            } else {
                // 默认 overlay：底部渐变 + 白字标题 + 进度线
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(PosterBottomScrim)
                        .padding(horizontal = 8.dp, vertical = 7.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (showProgress && item.positionMs > 0 && item.durationMs > 0) {
                            ProgressLine(item.positionMs.toFloat() / item.durationMs.toFloat())
                        }
                    }
                }
            }
        }
    }

    if (titleBelow) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            posterBox()
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
        }
    } else {
        Box(modifier = modifier) { posterBox() }
    }
}

@Composable
private fun ProgressLine(fraction: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(TinyShape)
            .background(Color.White.copy(alpha = 0.28f))
            .padding(top = 2.5.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .background(AppColors.Brand)
        )
    }
}

/** 剧集显示源给的分集名；Emby 这类没有源名的显示 "S02E05"，电影显示年份 */
fun MediaEntity.subtitle(): String = when {
    kind == MediaKind.EPISODE && usesSourceEpisodeName -> title
    kind == MediaKind.EPISODE && episode > 0 -> "S%02dE%02d".format(season, episode)
    year > 0 -> year.toString()
    else -> ""
}

@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(width = 3.dp, height = 16.dp)
                .clip(TinyShape)
                .background(AppColors.Brand)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
            color = AppColors.TextPrimary,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp)
        )
        trailing?.invoke()
    }
}
