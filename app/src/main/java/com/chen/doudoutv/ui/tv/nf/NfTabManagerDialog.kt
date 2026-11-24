package com.chen.doudoutv.ui.tv.nf

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chen.doudoutv.core.Prefs
import com.chen.doudoutv.ui.theme.AppColors
import com.chen.doudoutv.ui.theme.Edge
import com.chen.doudoutv.ui.theme.Radius
import com.chen.doudoutv.ui.theme.TvText
import com.chen.doudoutv.ui.tv.tvFocusRing
import com.chen.doudoutv.ui.tv.tvMoveFocus
import kotlinx.coroutines.delay

/** 顶栏里可管理的四颗（设置齿轮不在内）。顺序与隐藏都按这份名单存读。 */
private val ManageableTabs = listOf(NfTab.Home, NfTab.Browse, NfTab.Mine, NfTab.Search)

/**
 * 顶栏 tab 管理弹窗：列出四颗标签，选中后按 OK 就地展开「隐藏/显示 · 前移 · 后移」，
 * 保存后顶栏按新顺序与新隐藏集渲染。至少保留一颗显示——隐藏到只剩一颗时「隐藏」键失效，
 * 因此不会出现全隐藏、顶栏空掉的死局。
 *
 * 草稿只在弹窗内存活：取消即丢弃，保存才落 Prefs。焦点走弹窗内几何搜索（单列上下、选项行左右），
 * 与抽屉/线路管理同一套 tvFocusRing + tvMoveFocus 约定。
 */
@Composable
internal fun NfTabManagerDialog(
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    var order by remember {
        mutableStateOf(Prefs.navTabOrder().map { n -> ManageableTabs.first { it.name == n } })
    }
    var hidden by remember {
        mutableStateOf(Prefs.navTabHidden().map { n -> ManageableTabs.first { it.name == n } }.toSet())
    }
    var selectedTab by remember { mutableStateOf<NfTab?>(null) }
    var expanded by remember { mutableStateOf(false) }

    val visibleCount = order.count { it !in hidden }
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        // 先立即抢一次（赶在首帧绘制前），抢空再等帧补抢——消除打开瞬间焦点环
        // 画在兜底元件上的闪现。
        if (!tvMoveFocus(firstFocus)) {
            withFrameNanos { }
            if (!tvMoveFocus(firstFocus)) {
                delay(140)
                tvMoveFocus(firstFocus)
            }
        }
    }

    fun moveSelected(delta: Int) {
        val tab = selectedTab ?: return
        val i = order.indexOf(tab)
        val j = i + delta
        if (i < 0 || j < 0 || j > order.lastIndex) return
        order = order.toMutableList().apply { add(j, removeAt(i)) }
    }

    fun toggleHiddenSelected() {
        val tab = selectedTab ?: return
        hidden = if (tab in hidden) {
            hidden - tab
        } else if (visibleCount > 1) {
            hidden + tab
        } else {
            hidden
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(contentAlignment = Alignment.Center) {
            val panelWidth = (maxWidth * 0.5f).coerceAtLeast(420.dp)
            Column(
                Modifier
                    .width(panelWidth)
                    .heightIn(max = maxHeight * 0.86f)
                    .background(AppColors.SurfaceHi, Radius.Lg)
                    .border(1.dp, Edge.HairlineStrong, Radius.Lg)
                    .padding(horizontal = 24.dp, vertical = 22.dp)
            ) {
                Text(
                    "顶栏管理",
                    color = TvText.Title,
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "选中后按 OK 展开：隐藏 / 显示、前移、后移。至少保留一个显示的标签。",
                    color = TvText.Meta,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(16.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    order.forEachIndexed { index, tab ->
                        key(tab) {
                            val isHidden = tab in hidden
                            NfTabRow(
                                label = tab.label(),
                                isHidden = isHidden,
                                expanded = expanded && selectedTab == tab,
                                firstRow = index == 0,
                                firstFocus = firstFocus,
                                onFocused = {
                                    if (selectedTab != tab) {
                                        selectedTab = tab
                                        expanded = false
                                    }
                                },
                                onClick = {
                                    selectedTab = tab
                                    expanded = !expanded
                                }
                            )
                            if (expanded && selectedTab == tab) {
                                Row(
                                    Modifier.fillMaxWidth().padding(start = 16.dp, top = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    NfTabOptionChip(
                                        icon = if (isHidden) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                        text = if (isHidden) "显示" else "隐藏",
                                        // 已隐藏→随时可显示；显示中→只剩一颗时不许再隐藏
                                        enabled = isHidden || visibleCount > 1,
                                        onClick = { toggleHiddenSelected() }
                                    )
                                    NfTabOptionChip(
                                        icon = Icons.Filled.ArrowUpward,
                                        text = "前移",
                                        enabled = index > 0,
                                        onClick = { moveSelected(-1) }
                                    )
                                    NfTabOptionChip(
                                        icon = Icons.Filled.ArrowDownward,
                                        text = "后移",
                                        enabled = index < order.lastIndex,
                                        onClick = { moveSelected(1) }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NfActionButton(text = "取消", onClick = onDismiss)
                    NfActionButton(
                        text = "保存",
                        primary = true,
                        onClick = {
                            Prefs.setNavTabs(order.map { it.name }, hidden.map { it.name }.toSet())
                            onSaved()
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

/** 一颗标签行：焦点白框由 tvFocusRing 画；隐藏的行压暗并挂红减号，展开态右侧箭头翻转。 */
@Composable
private fun NfTabRow(
    label: String,
    isHidden: Boolean,
    expanded: Boolean,
    firstRow: Boolean,
    firstFocus: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        Modifier
            .then(if (firstRow) Modifier.focusRequester(firstFocus) else Modifier)
            .onFocusChanged { if (it.isFocused) onFocused() }
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(Radius.Md)
            .background(
                when {
                    focused -> AppColors.SurfaceRaise
                    // 浅色下白透明填充落在亮底上读不出，翻成黑透明
                    expanded -> if (AppColors.lightOn()) Color.Black.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.07f)
                    else -> if (AppColors.lightOn()) Color.Black.copy(alpha = 0.03f) else Color.White.copy(alpha = 0.03f)
                }
            )
            .tvFocusRing(focused)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .focusable(interactionSource = interaction)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            label,
            color = if (isHidden) TvText.Muted else TvText.Title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (expanded) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        if (isHidden) {
            Icon(
                imageVector = Icons.Filled.RemoveCircle,
                contentDescription = null,
                tint = AppColors.Danger,
                modifier = Modifier.size(18.dp)
            )
            Text("已隐藏", color = AppColors.Danger, style = MaterialTheme.typography.labelMedium)
        } else {
            Text("显示中", color = TvText.Muted, style = MaterialTheme.typography.labelMedium)
        }
        Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = TvText.Muted,
            modifier = Modifier.size(22.dp)
        )
    }
}

/**
 * 选项键：失效时压暗但仍可聚焦——TV 上若把失效键移出焦点序列，
 * 前移/后移到边界那一刻正在它上面的焦点会凭空跳走，所以留着焦点、只是按下去不动作。
 */
@Composable
private fun NfTabOptionChip(
    icon: ImageVector,
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val content = when {
        !enabled -> TvText.Muted.copy(alpha = 0.45f)
        focused -> TvText.Title
        else -> TvText.Section
    }
    Row(
        Modifier
            .clip(Radius.Sm)
            .background(
                if (focused) AppColors.SurfaceRaise
                else if (AppColors.lightOn()) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.06f)
            )
            .tvFocusRing(focused)
            .clickable(interactionSource = interaction, indication = null) { if (enabled) onClick() }
            .focusable(interactionSource = interaction)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        Text(text, color = content, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}