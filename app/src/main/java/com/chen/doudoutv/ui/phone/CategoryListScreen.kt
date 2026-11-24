package com.chen.doudoutv.ui.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chen.doudoutv.data.tvbox.TvBoxClient
import com.chen.doudoutv.ui.LibraryViewModel
import com.chen.doudoutv.ui.theme.AppColors
import kotlinx.coroutines.launch

/**
 * 聚合网盘搜索的资源列表页。
 * 某些聚合网盘源（觅盘/盘搜等）searchContent 返回 vodId=quark/xunlei 这类网盘类型入口，
 * 点详情 detailContent 返回空。这里调 categoryContent 拉真实影片列表。
 */
@Composable
fun CategoryListScreen(
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
    val scope = rememberCoroutineScope()

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
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(AppColors.SurfaceHi),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.padding(start = 4.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = AppColors.TextPrimary)
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(
                    text = typeName,
                    fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = siteKeyToCloudName(siteKey),
                    fontSize = 12.sp, color = AppColors.TextSecondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            items?.let { list ->
                Text(
                    text = "共 ${list.size} 部",
                    fontSize = 12.sp, color = AppColors.TextSecondary,
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(AppColors.Surface)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(AppColors.Hairline)
        )

        val current = items
        when {
            current == null && error == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 2.5.dp, color = AppColors.Brand, modifier = Modifier.size(28.dp))
                }
            }
            error != null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = error ?: "加载失败", color = AppColors.TextSecondary, fontSize = 14.sp)
                        Spacer(Modifier.height(12.dp))
                        androidx.compose.material3.TextButton(onClick = {
                            scope.launch {
                                items = null
                                val (list, _) = vm.tvBoxCategory(sourceId, siteKey, typeId)
                                items = list
                                error = if (list.isEmpty()) "暂无内容" else null
                            }
                        }) { Text("重试", color = AppColors.Brand) }
                    }
                }
            }
            current?.isEmpty() == true -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "暂无内容", color = AppColors.TextSecondary, fontSize = 14.sp)
                }
            }
            else -> {
                val list = current ?: return@Column
                val stableList = remember(list) { list }  // 锁定列表引用，避免 LazyColumn measure 期间引用变
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)
                ) {
                    items(stableList, key = { it.vodId }) { vod ->
                        CategoryRowItem(vod = vod, onClick = { onOpenDetail(vod) })
                    }
                }
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

@Composable
private fun CategoryRowItem(vod: TvBoxClient.Vod, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.Bg)
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Text(
            text = vod.name,
            fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary,
            maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 21.sp,
            modifier = Modifier.fillMaxWidth()
        )

        val metaParts = buildList {
            if (vod.score > 0) add("★ ${"%.1f".format(vod.score)}")
            if (vod.year > 0) add(vod.year.toString())
            if (vod.typeName.isNotBlank()) add(vod.typeName)
            if (vod.actors.isNotBlank() && vod.actors.length <= 40) add(vod.actors)
        }
        if (metaParts.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                metaParts.forEachIndexed { i, part ->
                    if (i > 0) Text("·", fontSize = 12.sp, color = AppColors.Hairline.copy(alpha = 0.8f))
                    Text(
                        text = part, fontSize = 12.sp,
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
            Row(modifier = Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = vod.remarks, fontSize = 12.sp, color = remarkColor, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .height(0.5.dp)
                .background(AppColors.Hairline.copy(alpha = 0.4f))
        )
    }
}
