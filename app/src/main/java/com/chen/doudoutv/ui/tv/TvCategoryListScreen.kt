package com.chen.doudoutv.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chen.doudoutv.data.tvbox.TvBoxClient
import com.chen.doudoutv.ui.LibraryViewModel
import com.chen.doudoutv.ui.theme.AppColors

/**
 * TV 端聚合网盘搜索资源列表页。
 * 纯文本横向列表（无封面），焦点行有品牌色半透明背景 + 2dp 描边。
 */
@Composable
fun TvCategoryListScreen(
    sourceId: Long,
    siteKey: String,
    typeId: String,
    typeName: String,
    onBack: () -> Unit,
    onOpenDetail: (TvBoxClient.Vod) -> Unit,
    vm: LibraryViewModel
) {
    var items by remember { mutableStateOf<List<TvBoxClient.Vod>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(sourceId, siteKey, typeId) {
        val (list, _) = vm.tvBoxCategory(sourceId, siteKey, typeId)
        if (list.isNotEmpty()) {
            items = list
        } else {
            error = vm.message ?: "暂无内容"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Bg)
            .padding(horizontal = 28.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(42.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = AppColors.TextPrimary)
            }
            Spacer(Modifier.size(14.dp))
            Text(text = typeName, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
            Spacer(Modifier.size(10.dp))
            Text(text = siteKeyToCloudName(siteKey), fontSize = 13.sp, color = AppColors.TextSecondary)
            items?.let {
                Text(
                    text = "${it.size} 部",
                    fontSize = 13.sp,
                    color = AppColors.TextSecondary,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(AppColors.Hairline)
                .padding(bottom = 14.dp)
        )

        val current = items
        when {
            current == null && error == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 2.5.dp, color = AppColors.Brand, modifier = Modifier.size(32.dp))
                }
            }
            error != null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = error ?: "加载失败", color = AppColors.TextSecondary, fontSize = 16.sp)
                }
            }
            else -> {
                val list = current ?: return@Column
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)
                ) {
                    itemsIndexed(list, key = { _, vod -> vod.vodId }) { idx, vod ->
                        TvCategoryRowItem(
                            vod = vod,
                            isFirst = idx == 0,
                            onClick = { onOpenDetail(vod) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvCategoryRowItem(
    vod: TvBoxClient.Vod,
    isFirst: Boolean,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(isFirst) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                color = if (focused) AppColors.Brand.copy(alpha = 0.14f) else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .border(
                width = 2.dp,
                color = if (focused) AppColors.Brand.copy(alpha = 0.5f) else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 2.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f, fill = true)
        ) {
            Text(
                text = vod.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val metaParts = buildList {
                if (vod.score > 0) add("★ ${"%.1f".format(vod.score)}")
                if (vod.year > 0) add(vod.year.toString())
                if (vod.typeName.isNotBlank()) add(vod.typeName)
            }
            if (metaParts.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(top = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    metaParts.forEachIndexed { i, part ->
                        if (i > 0) {
                            Text(
                                text = "·",
                                fontSize = 12.sp,
                                color = AppColors.TextSecondary.copy(alpha = 0.6f)
                            )
                        }
                        Text(
                            text = part,
                            fontSize = 12.sp,
                            color = if (part.startsWith("★")) Color(0xFFFFB74D) else AppColors.TextSecondary,
                            fontWeight = if (part.startsWith("★")) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
            if (vod.remarks.isNotBlank()) {
                val remarkColor = when {
                    vod.remarks.contains("有效") || vod.remarks.contains("✅") -> Color(0xFF66BB6A)
                    vod.remarks.contains("即将") || vod.remarks.contains("⚠️") -> Color(0xFFFFB74D)
                    else -> AppColors.TextSecondary
                }
                Text(
                    text = vod.remarks,
                    fontSize = 12.sp,
                    color = remarkColor,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
    }
}

private fun siteKeyToCloudName(siteKey: String): String {
    val s = siteKey.lowercase()
    return when {
        s.contains("quark") -> "夸克网盘"
        s.contains("xunlei") -> "迅雷网盘"
        s.contains("aliyundrive") || s.contains("alipan") -> "阿里云盘"
        s.contains("uc") -> "UC 网盘"
        s.contains("baidu") || s.contains("panbaidu") -> "百度网盘"
        s.contains("115") -> "115 网盘"
        s.contains("onedrive") -> "OneDrive"
        else -> siteKey
    }
}
