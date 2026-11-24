package com.chen.doudoutv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chen.doudoutv.data.tvbox.CatVodResultParser
import com.chen.doudoutv.ui.theme.AppColors
import com.chen.doudoutv.ui.theme.Edge
import com.chen.doudoutv.ui.theme.Radius

/**
 * 手机端分类页筛选：tab 下方常驻一条筛选条（透出已选条件），点按展开面板。
 * 组数据两端同源：TVBox 用源站 homeContent 下发的 filters，Emby 用本地聚合（localFilterGroups）。
 * 选中即生效、面板不关；「重置」清空全部维度。选中态 Map<组下标, 值下标>，「全部」= 下标 0 不进 extend。
 */
@Composable
internal fun PhoneFilterBar(
    groups: List<CatVodResultParser.FilterGroup>,
    selected: Map<Int, Int>,
    onPick: (groupIdx: Int, valueIdx: Int) -> Unit,
    onReset: () -> Unit
) {
    var open by remember { mutableStateOf(false) }

    if (!open) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clip(Radius.Md)
                .background(AppColors.Surface.copy(alpha = 0.45f))
                .border(1.dp, Edge.Hairline, Radius.Md)
                .clickable { open = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            // 已选条件占左侧；没有已选条件时右侧的「筛选」也要贴到最右边
            if (selected.isNotEmpty()) {
                Row(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    selected.keys.sorted().forEach { gi ->
                        val g = groups.getOrNull(gi) ?: return@forEach
                        val v = g.value.getOrNull(selected[gi] ?: 0) ?: return@forEach
                        Box(
                            Modifier
                                .clip(Radius.Pill)
                                .background(AppColors.Brand.copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "${g.name}·${v.n}",
                                fontSize = 11.sp,
                                color = AppColors.BrandSoft,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
            Text(
                "筛选",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary
            )
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = "展开筛选",
                tint = AppColors.TextTertiary,
                modifier = Modifier.height(16.dp)
            )
        }
        return
    }

    // ---------- 展开态：面板顶开网格 ----------
    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(Radius.Md)
            // 半透明面板（2026-11-24 反馈要「透明的」）：45% 面板底，透出页面背景色
            .background(AppColors.Surface.copy(alpha = 0.45f))
            .border(1.dp, Edge.Hairline, Radius.Md)
            .padding(horizontal = 14.dp, vertical = 4.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 38.dp)
                .clickable { open = false },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.KeyboardArrowUp,
                contentDescription = null,
                tint = AppColors.TextTertiary,
                modifier = Modifier.height(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "筛选",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary
            )
            Spacer(Modifier.weight(1f))
            Text(
                "重置",
                fontSize = 12.sp,
                color = AppColors.BrandSoft,
                modifier = Modifier
                    .clip(Radius.Pill)
                    .border(1.dp, AppColors.Brand.copy(alpha = 0.4f), Radius.Pill)
                    .clickable { onReset() }
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Edge.Hairline))

        // 组名列宽取最长组名的实测文本宽：写死 34dp 时四字组名会折行
        val textMeasurer = rememberTextMeasurer()
        val labelStyle = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
        val labelWidthPx = remember(groups, textMeasurer) {
            groups.maxOfOrNull { g ->
                textMeasurer.measure(AnnotatedString(g.name), labelStyle, maxLines = 1).size.width
            } ?: 0
        }
        val labelWidth = with(LocalDensity.current) { labelWidthPx.toDp() }

        groups.forEachIndexed { gi, g ->
            Row(
                Modifier.fillMaxWidth().padding(top = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    g.name,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = AppColors.TextSecondary,
                    maxLines = 1,
                    modifier = Modifier.width(labelWidth)
                )
                Row(
                    Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    g.value.forEachIndexed { vi, v ->
                        val sel = (selected[gi] ?: 0) == vi
                        Box(
                            Modifier
                                .clip(Radius.Pill)
                                .background(if (sel) AppColors.Brand else AppColors.SurfaceHi)
                                .clickable {
                                    // 点「全部」（下标 0）即清掉该维度的选中
                                    if (vi == 0) onPick(gi, 0) else onPick(gi, vi)
                                }
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Text(
                                v.n,
                                fontSize = 12.sp,
                                fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (sel) Color.White else AppColors.TextSecondary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
