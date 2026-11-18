package com.chen.doudoutv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chen.doudoutv.data.model.MediaEntity
import com.chen.doudoutv.ui.LibraryViewModel
import com.chen.doudoutv.ui.theme.AppColors
import com.chen.doudoutv.ui.theme.Radius
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 分类页批量刮削的页面级状态。
 *
 * 四个页面（手机端题材页 / TVBox 分类页、电视端题材页 / TVBox 分类页）共用这一份：
 * 已刮标记的 id 集合、确认弹窗的开关与两个数字、批次的进度。
 * 进度本身住在 ViewModel（[LibraryViewModel.batchState]），这里只负责把
 * 「哪些条目已刮过」这层展示状态摆出来。
 */
@Stable
class BatchScrapeUiState internal constructor(
    private val vm: LibraryViewModel,
    private val scope: CoroutineScope
) {
    /** 当前这一页里已经刮过的条目：id → TMDB 评分（网格角标要摆评分） */
    var scrapedRatings by mutableStateOf(emptyMap<Long, Double>())
        private set

    /** 确认弹窗是否打开 */
    var confirmVisible by mutableStateOf(false)
        private set

    /** 确认弹窗里的两个数字：当前分类已加载的总数、其中已刮过要跳过的数 */
    var pendingTotal by mutableStateOf(0)
        private set
    var pendingSkipped by mutableStateOf(0)
        private set

    /** 待刮的那一批条目 */
    private var pendingItems: List<MediaEntity> = emptyList()

    /** 批次是否在跑 */
    val running: Boolean get() = vm.batchState.running

    /** 已完成 / 总数 */
    val done: Int get() = vm.batchState.done
    val total: Int get() = vm.batchState.total

    /** 重查一次「这批里哪些已经刮过」，批次跑完（轮次自增）后也要再查 */
    suspend fun refresh(items: List<MediaEntity>) {
        scrapedRatings = vm.scrapedIdsOf(items)
    }

    /**
     * 点刮削按钮：先查一遍已刮数量，再弹确认框。
     * 数字要在弹窗里现算 —— 已刮过的条目随时会变，弹窗里写死就是错的。
     */
    fun request(items: List<MediaEntity>) {
        if (running) return
        scope.launch {
            val ratings = vm.scrapedIdsOf(items)
            scrapedRatings = ratings
            pendingItems = items
            pendingTotal = items.size
            pendingSkipped = ratings.size
            confirmVisible = true
        }
    }

    /** 确认：开始刮这一批 */
    fun confirm() {
        confirmVisible = false
        vm.startBatchScrape(pendingItems)
    }

    /** 关闭确认框 */
    fun dismiss() {
        confirmVisible = false
    }

    /** 取消正在跑的批次 */
    fun cancel() {
        vm.cancelBatchScrape()
    }
}

/** 拿到批量刮削的页面级状态 */
@Composable
fun rememberBatchScrapeUiState(vm: LibraryViewModel): BatchScrapeUiState {
    val scope = rememberCoroutineScope()
    return remember(vm) { BatchScrapeUiState(vm, scope) }
}

/**
 * 确认弹窗的文案。
 *
 * 「60 部影片」与「12 部已刮过」两个数字用粗体：用户按下确认前只想知道这两件事，
 * 其余是背景说明。数字都在点按钮那一刻现查，不写死。
 */
fun batchScrapeConfirmText(total: Int, skipped: Int): AnnotatedString = buildAnnotatedString {
    append("将刮削当前分类已加载的 ")
    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
    append(total.toString())
    pop()
    append(" 部影片，其中 ")
    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
    append(skipped.toString())
    pop()
    append(" 部已刮过将跳过，可随时取消。")
}

/**
 * 网格海报左上角的「已刮削」角标：TMDB 官方图标 + 评分。
 *
 * 背景全透明（海报明暗不定，衬色块会在画面上挖洞）；
 * 评分文字带一层轻投影，压在浅色海报上也能读。
 * 手机端与 TV 端共用这一颗，只有图标高度按卡片尺寸分档。
 */
@Composable
fun ScrapedBadge(rating: Double?, iconHeight: Dp) {
    if (rating == null) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        TmdbIconOnly(height = iconHeight)
        if (rating > 0.0) {
            val score = String.format(Locale.US, "%.1f", rating)
            // 不用背景衬色块，也不依赖 TextStyle.shadow：黑字错开 1dp 垫在琥珀字底下，
            // 等于给文字描了一道软边，压在浅色海报上也能读
            Box {
                Text(
                    text = score,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black.copy(alpha = 0.85f),
                    modifier = Modifier.offset(1.dp, 1.dp)
                )
                Text(
                    text = score,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.Rating
                )
            }
        }
    }
}

/**
 * 手机端顶栏上的刮削按钮。
 *
 * 胶囊形 + 品牌色薄底 + 一道半透明品牌描边：原来那块 SurfaceHi 深灰底
 * 在近黑顶栏上就是一块黑疙瘩，和整页的材质层级（L2 卡片底）串了味。
 * 现在它读起来是「一颗可点的品牌动作」，和详情页「播放」那颗主按钮同一语言。
 *
 * 空闲态是「TMDB 官方图标 + 刮削」；批次在跑时同一颗按钮变成「进度 + 取消」，
 * 位置不动 —— 用户随时能找到取消的地方。
 */
@Composable
fun PhoneScrapeButton(
    running: Boolean,
    done: Int,
    total: Int,
    onClick: () -> Unit
) {
    val brand = AppColors.Brand
    Box(
        Modifier
            .clip(Radius.Pill)
            .background(brand.copy(alpha = 0.14f))
            .border(1.dp, brand.copy(alpha = 0.5f), Radius.Pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (running) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = brand,
                    modifier = Modifier.size(13.dp)
                )
            } else {
                TmdbIconOnly(height = 14.dp)
            }
            Text(
                text = if (running) "$done/$total 取消" else "刮削",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = brand,
                maxLines = 1
            )
        }
    }
}

/**
 * 手机端批量刮削的确认弹窗。
 *
 * 用 Material3 的 [AlertDialog]：遮罩、焦点、Back 关闭都是系统现成的，
 * 与本项目其它确认框（继续观看菜单、来源删除）同一套视觉。
 */
@Composable
fun PhoneBatchScrapeDialog(
    total: Int,
    skipped: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.Surface,
        title = {
            Text(
                text = "批量刮削",
                color = AppColors.TextPrimary
            )
        },
        text = {
            Text(
                text = batchScrapeConfirmText(total, skipped),
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("开始刮削", color = AppColors.Brand)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = AppColors.TextSecondary)
            }
        }
    )
}
