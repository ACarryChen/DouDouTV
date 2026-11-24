package com.chen.doudoutv.ui.tv.nf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chen.doudoutv.data.model.MediaEntity
import com.chen.doudoutv.data.model.LibraryDisplayOrder
import com.chen.doudoutv.data.model.MediaKind
import com.chen.doudoutv.data.model.SourceKind
import com.chen.doudoutv.data.scrape.ScrapeEngine
import com.chen.doudoutv.data.tvbox.CatVodResultParser
import com.chen.doudoutv.ui.LibraryViewModel
import com.chen.doudoutv.ui.KindLikeGenres
import com.chen.doudoutv.ui.applyLocalFilters
import com.chen.doudoutv.ui.genreTokens
import com.chen.doudoutv.ui.localFilterGroups
import com.chen.doudoutv.ui.PrecomposeWindow
import com.chen.doudoutv.ui.ReturnSpot
import com.chen.doudoutv.ui.StandTarget
import com.chen.doudoutv.ui.components.ShimmerCard
import com.chen.doudoutv.ui.components.rememberBatchScrapeUiState
import com.chen.doudoutv.ui.gridState
import com.chen.doudoutv.ui.tv.TvBatchScrapeDialog
import com.chen.doudoutv.ui.tv.TvPagePadding
import com.chen.doudoutv.ui.tv.TvScrapeChip
import com.chen.doudoutv.ui.tv.tvClaimFocusOnEnter
import com.chen.doudoutv.ui.tv.tvMoveFocus
import com.chen.doudoutv.ui.tv.tvPosterColumns
import com.chen.doudoutv.ui.theme.AppColors
import com.chen.doudoutv.ui.theme.TvText
import kotlinx.coroutines.launch

/** [ReturnSpot] 的页面标识：每个分类各记一份滚动位置与落点，key 里直接带上分类 */
private const val NfBrowseKey = "nf_browse"

/**
 * 「分类」页：首页挪走的那些分类行在这里整屏铺开。
 *
 * 两条数据链路按来源类型分开：
 *   · TVBox —— 用源站下发的分类，内容走「查看更多」那套在线分页（[LibraryViewModel.browseItems]）；
 *     分页必须落到具体线路，所以「全部站点」时这一页没有在线内容可拉，给切换入口。
 *   · Emby —— 用入库的 genres 字段聚合出动态分类，纯本地、不产生网络请求。
 *
 * 版式：导航条 → 贴头的分类 chip 条 → 整屏竖版海报网格。页标题与条目计数不占位。
 */
@Composable
fun NfBrowseScreen(
    vm: LibraryViewModel,
    onTab: (NfTab) -> Unit,
    onDetail: (Long) -> Unit,
    onSources: () -> Unit,
    onSettings: () -> Unit
) {
    val library by vm.library.collectAsStateWithLifecycle()
    val sources by vm.enabledSources.collectAsStateWithLifecycle()
    val selectedSource = sources.firstOrNull { it.id == vm.homeSourceId } ?: sources.firstOrNull()
    val isTvBox = selectedSource?.kind == SourceKind.TVBOX

    val chrome = rememberNfChrome()
    val stand = remember { StandTarget() }

    // 换来源 / 换线路把选择重置：两个维度下的分类集合完全不同；
    // 「进详情再返回」是同一个 scopeKey，选择要从进程级记忆接着上次读 —— 落回第一个分类会被读成「返回时刷新了数据」
    val scopeKey = "${selectedSource?.id}_${vm.homeSiteKey}"
    var onlineIdx by remember(scopeKey) { mutableStateOf(NfBrowseMemory.get("on_$scopeKey") ?: 0) }
    var localIdx by remember(scopeKey) { mutableStateOf(NfBrowseMemory.get("lo_$scopeKey") ?: 0) }

    val onlineCats = vm.homeCategories
    val localItems = remember(library, vm.homeSourceId, vm.homeSiteKey) { vm.filterForHome(library) }
    // Emby 题材 chip 优先用服务端 /Genres 清单（跟库内容相关、完整）；拉到空时回退本地频次聚合
    val serverGenres = selectedSource?.id?.let { vm.embyGenres(it) }.orEmpty()
    LaunchedEffect(selectedSource?.id) { selectedSource?.id?.let { vm.ensureEmbyGenres(it) } }
    val localCats = remember(localItems, serverGenres) { nfLocalCategories(localItems, serverGenres) }

    val safeOnlineIdx = onlineIdx.coerceIn(0, (onlineCats.size - 1).coerceAtLeast(0))
    val safeLocalIdx = localIdx.coerceIn(0, (localCats.size - 1).coerceAtLeast(0))
    val currentTypeId = onlineCats.getOrNull(safeOnlineIdx)?.typeId

    val labels = if (isTvBox) onlineCats.map { it.typeName } else localCats.map { it.name }
    val selIndex = if (isTvBox) safeOnlineIdx else safeLocalIdx
    val showSubNav = labels.size > 1
    // 逐颗 chip 各一个锚点、由本页持有：返回键那一层要落回「当前选中的那颗」分类，
    // 只留第一颗的锚点不够。一个实例只挂一颗 chip，不跨节点共用（共用会让 requestFocus 空转）。
    val chipFocuses = remember(labels.size) { List(labels.size) { FocusRequester() } }
    val firstChipFocus = chipFocuses.firstOrNull() ?: FocusRequester()
    /** 分类栏这一层当前的落点：选中的那颗 chip（选中项本来就会被滚进视野，锚点必然挂得上） */
    val currentChipFocus = chipFocuses.getOrElse(selIndex) { firstChipFocus }
    // 刮削按钮固定挂在分类条右端（TMDB 开启才有）：自己一个锚点，
    // 最右 chip 按 → 走到它、它按 ← 回到 chip 条；不掺进 chipFocuses 的返回键落点链
    val scrapeVisible = vm.scrapeEngine == ScrapeEngine.TMDB
    val scrapeChipFocus = remember { FocusRequester() }
    // 分类条上最后真正站过焦点的那颗：只给「焦点还站在分类条上、数据重载翻 tick」的抢焦当锚点
    // （见下方 tvClaimFocusOnEnter），不许把焦点从用户站着的 chip 拽回选中那颗（规范 R4）。
    var lastChipIdx by remember(scopeKey) { mutableStateOf(selIndex) }

    // 筛选面板：
    // · TVBox —— 焦点在已选中的分类 chip 上再按一次 OK → 收起分类条，原位展开「组名行 + 取值行」两行 NfSubNav，
    //   与分类条同一套视觉与间距；选中值以 extend 传给 categoryContent 重新请求该分类第 1 页。
    // · Emby —— 组从本地库聚合（年份/类型/题材/排序），选中值直接对本地条目做谓词过滤，不发网络请求。
    val localGroups = remember(localItems) { localFilterGroups(localItems) }
    val groups = if (isTvBox) vm.homeFilters[currentTypeId].orEmpty() else localGroups
    // 面板状态按「当前分类」重置：Emby 分支 currentTypeId 恒 null，改用分类名做 key，
    // 否则换题材 chip 时上一类的选中值会残留
    val panelCatKey = if (isTvBox) currentTypeId else localCats.getOrNull(safeLocalIdx)?.name
    var panelOpen by remember(scopeKey, panelCatKey) { mutableStateOf(false) }
    var activeGroup by remember(scopeKey, panelCatKey) { mutableStateOf(0) }
    var filterSel by remember(scopeKey, panelCatKey) { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    val gIdx = activeGroup.coerceIn(0, (groups.size - 1).coerceAtLeast(0))
    // 组 key → 选中取值 v（「全部」= 0 不传）
    val extend = remember(filterSel, groups) {
        buildMap {
            groups.forEachIndexed { i, g ->
                val vi = filterSel[i] ?: 0
                if (vi > 0) put(g.key.ifBlank { i.toString() }, g.value[vi].v)
            }
        }
    }
    val filterSig = remember(extend) { extend.entries.joinToString("|") { "${it.key}=${it.value}" } }
    // 面板两行各自的 chip 锚点：组行含「返回分类」多一颗；取值行随当前组重建
    val groupAnchors = remember(groups) { List(groups.size + 1) { FocusRequester() } }
    val valueAnchors = remember(groups, gIdx) { groups.getOrNull(gIdx)?.value?.map { FocusRequester() }.orEmpty() }
    // 面板整体 / 取值行是否持焦：入口抢焦与返回键都按这两档决定落点
    var panelFocused by remember { mutableStateOf(false) }
    var panelValueFocused by remember { mutableStateOf(false) }
    var panelWasOpen by remember(scopeKey) { mutableStateOf(false) }

    // 网格首行按 ↑ 的落点：与顶栏同口径 —— 顶栏交回本页那颗 tab（选中的），这里交回选中的那颗 chip；
    // 面板展开时交回取值行第一颗；
    // 只有一个分类时交回本页那颗「分类」tab（规范 R1）；
    // 分类条只剩刮削按钮时（单分类 + TMDB 开启）先交给按钮、由它再 ↑ 交回 tab —— 否则按钮不在任何链路上，遥控器够不着
    val gridUpFocus = when {
        panelOpen -> valueAnchors.firstOrNull() ?: currentChipFocus
        showSubNav -> currentChipFocus
        scrapeVisible -> scrapeChipFocus
        else -> chrome.tabOf(NfTab.Browse)
    }

    // 记忆的是「修正后」的下标：分类集合变短时被 clamp 过，下次进来不该再跳一格
    LaunchedEffect(scopeKey, selIndex) {
        NfBrowseMemory.save(if (isTvBox) "on_$scopeKey" else "lo_$scopeKey", selIndex)
    }

    // 本页只抢一次焦：有内容交给网格首卡，没内容交给空态那颗按钮，
    // 不出现「没人持焦、按一下才亮」（规范 R6/R7）
    val sitePicked = vm.homeSiteKey != null
    val onlineItems = if (isTvBox && sitePicked) vm.browseItems(currentTypeId.orEmpty(), extend) else emptyList()
    val localShown = if (!isTvBox) localCats.getOrNull(safeLocalIdx)?.let { cat ->
        applyLocalFilters(localItems.filter(cat.matches), extend)
    } ?: emptyList() else emptyList()
    // 批量刮削：已刮标记（网格角标）、确认弹窗、批次进度（与经典版分类页同一套组件）
    val scrapeUi = rememberBatchScrapeUiState(vm)
    /** 当前分类已加载的条目：刮削按钮和角标都只认这一份 */
    val scrapeTargetItems = if (isTvBox) onlineItems else localShown
    // 批次跑完 ViewModel 会自增 scrapeEpoch，这一轮把「已刮」重新查一遍
    LaunchedEffect(scrapeTargetItems, vm.scrapeEpoch) { scrapeUi.refresh(scrapeTargetItems) }
    val contentReady = if (isTvBox) sitePicked && onlineItems.isNotEmpty() else localShown.isNotEmpty()
    // 分类名要等这条线路的 homeContent 回来才有：换线路那次拉取不带 scanning，光看 scanning 会把「还在读」误报成「没有分类」
    val catsLoading = vm.scanning || vm.homeSiteLoading

    // 页面锚点按「首卡是谁」重新生成，网格与空态 CTA 各用一个实例：
    // 同一实例先后挂在两个节点上时，旧节点销毁后实例仍留着那条记录，requestFocus 不抛异常但焦点不动
    val gridFirstId = (if (isTvBox) onlineItems else localShown).firstOrNull()?.id
    val gridFirstFocus = remember(gridFirstId) { FocusRequester() }
    val emptyFocus = remember(contentReady) { FocusRequester() }
    /** 这一屏真正能接住 ↓ 的元件：有内容是网格首卡，没内容是空态那颗按钮 */
    val contentAnchor = if (contentReady) gridFirstFocus else emptyFocus

    // 从别的 tab 点「分类」切过来：焦点留在标签上，不交给网格/分类条（NfTabSwitch）。
    // 这个标记只在「刚切过来」那一次有效：入口抢焦是多次触发的（contentReady / scopeKey 都会换 tick），
    // 一直认 viaTab 的话，用户已经下到分类条、点 OK 换了分类，数据一到位抢焦又把焦点送回 tab 栏 ——
    // 这就是「选完分类左右移动直接回到 tab 栏」。离开导航条后改用当前焦点判断。
    var entryFocusOnNav by remember { mutableStateOf(NfTabSwitch.consume(NfTab.Browse)) }
    LaunchedEffect(chrome.navFocused) {
        if (!chrome.navFocused) entryFocusOnNav = false
    }
    // 分类条这一层当前是否持焦：点 chip 会换分类、内容跟着重载，这一页的「入口抢焦」必须能分辨这件事
    var subNavFocused by remember { mutableStateOf(false) }
    // 首屏数据到位后把焦点交给卡片，遥控器立刻可用；离开时站在哪张卡就还给哪张（规范 R6/R7）。
    // 焦点还在分类条上时不抢内容：换分类那一趟 contentReady 会翻转两次，抢焦会把焦点从
    // 刚选中的 chip 拽走（随后左右移动被读成「回到 tab 栏」）。
    tvClaimFocusOnEnter(
        tick = listOf(contentReady, scopeKey),
        anchorsProvider = {
            when {
                entryFocusOnNav -> listOf(chrome.tabOf(NfTab.Browse))
                // 焦点站在筛选面板里：数据重载翻 tick 时不许把焦点拽进网格，原地落在当前那一行
                panelFocused -> listOfNotNull(
                    if (panelValueFocused) valueAnchors.getOrNull(filterSel[gIdx] ?: 0) ?: valueAnchors.firstOrNull()
                    else groupAnchors.getOrNull(gIdx) ?: groupAnchors.firstOrNull()
                )
                // 分类条只剩刮削按钮时 currentChipFocus 没挂节点，交回按钮自己的锚点。
                // 持焦时锚到「最后站过」那颗：数据重载翻 tick 时不许把焦点从用户站着的 chip 拽回选中那颗
                subNavFocused -> listOf(if (showSubNav) chipFocuses.getOrElse(lastChipIdx) { currentChipFocus } else scrapeChipFocus)
                else -> listOfNotNull(stand.requester, contentAnchor)
            }
        }
    )

    LaunchedEffect(currentTypeId, extend, vm.homeRefreshEpoch) {
        if (isTvBox && sitePicked) currentTypeId?.let { vm.ensureBrowsePage(it, extend) }
    }

    // 面板开/关的焦点交接：展开给组行第一颗，收起还给当前选中的分类 chip。
    // panelWasOpen 门禁：首次组合（panelOpen 本来就是 false）不许把焦点从网格/入口落点拽到分类条。
    LaunchedEffect(panelOpen) {
        if (panelOpen) {
            groupAnchors.firstOrNull()?.let { tvMoveFocus(it) }
        } else if (panelWasOpen && showSubNav) {
            tvMoveFocus(currentChipFocus)
        }
        panelWasOpen = panelOpen
    }

    // 返回键的层级：卡片 → 分类栏 → tab 栏（tab 栏那一层由 TvApp 根部接，它不在这页的子树里）
    val jumpTo = rememberNfLayerJump()
    val scope = rememberCoroutineScope()
    // 网格滚动 state 提到页面级：返回键要就地把网格滚回顶部。
    // key 必须和下面两个 NfPosterGrid 的 pageKey 逐字一致，否则记账会分成两份。
    val gridPageKey = if (isTvBox) "$NfBrowseKey/$currentTypeId/$filterSig"
        else "$NfBrowseKey/local_${localCats.getOrNull(safeLocalIdx)?.name}/$filterSig"
    val gridState = ReturnSpot.gridState(gridPageKey, precompose = PrecomposeWindow.Tv)

    NfScaffold(
        vm = vm,
        current = NfTab.Browse,
        chrome = chrome,
        // 每一向都显式给落点（规范 R1）：有分类条 → 当前选中的那颗 chip，没有 → 这一屏的接焦元件
        navInFlow = true,
        belowFocusRequester = when {
            panelOpen -> groupAnchors.first()
            showSubNav -> currentChipFocus
            scrapeVisible -> scrapeChipFocus
            else -> contentAnchor
        },
        onTab = onTab,
        onSettings = onSettings,
        onManageSources = onSources
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .nfBackKey {
                    when {
                        // 面板展开时 Back 先收起面板：焦点由 panelOpen 的交焦逻辑还给当前分类 chip
                        panelFocused -> { panelOpen = false; true }
                        subNavFocused -> jumpTo(chrome.tabOf(NfTab.Browse))
                        showSubNav -> {
                            // 从卡片层返回：滚过就先瞬跳回顶部，再把焦点交给面板组行（展开时）
                            // 或当前选中的那颗 chip。已经在顶部时只做交焦点 ——
                            // 无条件 scrollToItem 会白白重组合一屏卡片。
                            val scrolled = gridState.firstVisibleItemIndex > 0 ||
                                gridState.firstVisibleItemScrollOffset > 0
                            val target = if (panelOpen) groupAnchors.first() else currentChipFocus
                            if (scrolled) {
                                scope.launch {
                                    // 先滚再交焦点：滚回顶部会回收持焦的那张卡，1.9 会顺手 clearFocus，
                                    // 焦点落到整页第一个可聚焦节点（tab 栏），随后这一跳再把它拿回来
                                    gridState.scrollToItem(0, 0)
                                    jumpTo(target, firstChipFocus)
                                }
                                true
                            } else {
                                jumpTo(target, firstChipFocus)
                            }
                        }
                        else -> jumpTo(chrome.tabOf(NfTab.Browse))
                    }
                }
        ) {
            // 分类 chip 条拆两区：chip 栏占掉除按钮以外的全部宽度（weight(1f)），
            // 刮削按钮固定在最右一颗 —— 分类再多也不会把按钮挤出屏幕（与经典版题材页同一做法）。
            // 焦点在已选中的分类 chip 上再按一次 OK → 收起分类条，原位展开筛选面板（组名行 + 取值行）。
            if (panelOpen && groups.isNotEmpty()) {
                // 筛选面板：两行 NfSubNav —— 与分类条同一套视觉、间距、焦点路由
                Column(
                    Modifier
                        .fillMaxWidth()
                        .onFocusEvent { panelFocused = it.hasFocus }
                ) {
                    NfSubNav(
                        labels = groups.mapIndexed { i, g ->
                            val vi = filterSel[i] ?: 0
                            if (vi > 0) "${g.name} · ${g.value[vi].n}" else g.name
                        } + "重置",
                        selectedIndex = gIdx,
                        // 「重置」清空全部选中值、面板保持展开（收起分类条走返回键）
                        onSelect = { i -> if (i < groups.size) activeGroup = i else filterSel = emptyMap() },
                        downFocusRequester = valueAnchors.firstOrNull(),
                        upFocusRequester = chrome.tabOf(NfTab.Browse),
                        firstChipFocus = firstChipFocus,
                        chipFocuses = groupAnchors
                    )
                    groups.getOrNull(gIdx)?.let { gv ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .onFocusEvent { panelValueFocused = it.hasFocus }
                        ) {
                            NfSubNav(
                                labels = gv.value.map { it.n },
                                selectedIndex = filterSel[gIdx] ?: 0,
                                onSelect = { vi -> filterSel = filterSel + (gIdx to vi) },
                                downFocusRequester = contentAnchor,
                                upFocusRequester = groupAnchors.getOrNull(gIdx),
                                firstChipFocus = gridUpFocus,
                                chipFocuses = valueAnchors
                            )
                        }
                    }
                }
            } else if (showSubNav || scrapeVisible) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        // subNavFocused 上移到整行：按钮与 chip 同属「分类条这一层」，
                        // 返回键从按钮直接退回 tab 栏，与 chip 同一口径
                        .onFocusEvent { subNavFocused = it.hasFocus },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showSubNav) {
                        NfSubNav(
                            labels = labels,
                            selectedIndex = selIndex,
                            onSelect = { i ->
                                if (isTvBox) {
                                    // 二次 OK 落在已选中的分类上 → 展开筛选面板（源站没下发 filters 则无动作）
                                    if (i == safeOnlineIdx && groups.isNotEmpty()) panelOpen = true
                                    else onlineIdx = i
                                } else if (i == safeLocalIdx && groups.isNotEmpty()) {
                                    // Emby 同口径：二次 OK 展开本地筛选面板
                                    panelOpen = true
                                } else localIdx = i
                            },
                            downFocusRequester = contentAnchor,
                            upFocusRequester = chrome.tabOf(NfTab.Browse),
                            firstChipFocus = firstChipFocus,
                            chipFocuses = chipFocuses,
                            onChipFocus = { lastChipIdx = it },
                            trailingFocusRequester = if (scrapeVisible) scrapeChipFocus else null,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (scrapeVisible) {
                        TvScrapeChip(
                            running = scrapeUi.running,
                            done = scrapeUi.done,
                            total = scrapeUi.total,
                            onClick = {
                                if (scrapeUi.running) scrapeUi.cancel() else scrapeUi.request(scrapeTargetItems)
                            },
                            modifier = Modifier
                                .padding(start = if (showSubNav) 0.dp else TvPagePadding, end = TvPagePadding)
                                .nfFocusRoute(
                                    up = chrome.tabOf(NfTab.Browse),
                                    down = contentAnchor,
                                    // ← 回到最右那颗 chip（进入按钮的唯一来路）；没有 chip 条时到头
                                    left = if (showSubNav) chipFocuses.lastOrNull() else FocusRequester.Cancel,
                                    right = FocusRequester.Cancel
                                )
                                .focusRequester(scrapeChipFocus)
                        )
                    }
                }
            }

            Box(Modifier.fillMaxWidth().weight(1f)) {
                when {
                    !isTvBox && localShown.isEmpty() -> NfInlineEmpty(
                        text = if (vm.scanning) "正在同步这个 Emby 媒体库…" else "这个库里还没有可分类的内容",
                        hint = if (vm.scanning) null else "先在顶栏刷新，或切换到别的来源",
                        actionLabel = if (vm.scanning) null else "立即同步",
                        actionIcon = if (vm.scanning) null else Icons.Filled.Refresh,
                        onAction = if (vm.scanning) null else { { vm.refreshHome() } },
                        focusRequester = emptyFocus
                    )

                    !isTvBox -> NfPosterGrid(
                        items = localShown,
                        pageKey = gridPageKey,
                        stand = stand,
                        firstFocus = gridFirstFocus,
                        upFocusRequester = gridUpFocus,
                        onDetail = onDetail,
                        modifier = Modifier.fillMaxSize(),
                        state = gridState,
                        scrapedRatings = scrapeUi.scrapedRatings
                    )

                    !sitePicked -> NfInlineEmpty(
                        text = "TVBox 要先选定一条线路",
                        hint = "分类内容按线路请求，「全部站点」是聚合视图、没有分页入口",
                        actionLabel = "选择线路",
                        actionIcon = Icons.AutoMirrored.Filled.List,
                        onAction = { chrome.openScopeDrawer = true },
                        focusRequester = emptyFocus
                    )

                    onlineCats.isEmpty() -> NfInlineEmpty(
                        // 切来源/切线路后 homeContent 还在路上（换线路那次拉取不带 scanning 标志），
                        // 这时候判「没有下发分类」是误报
                        text = if (catsLoading) "正在读取这条线路的分类…" else "这条线路没有下发分类",
                        hint = if (catsLoading) null else "换一条线路，或先在来源里同步",
                        actionLabel = if (catsLoading) null else "立即同步",
                        actionIcon = if (catsLoading) null else Icons.Filled.Refresh,
                        onAction = if (catsLoading) null else { { vm.refreshHome() } },
                        focusRequester = emptyFocus
                    )

                    onlineItems.isEmpty() && vm.browseItemsError(currentTypeId.orEmpty(), extend) != null -> NfInlineEmpty(
                        text = "加载失败：${vm.browseItemsError(currentTypeId.orEmpty(), extend)}",
                        hint = "重试这一页，或者换一条线路",
                        actionLabel = "重试",
                        actionIcon = Icons.Filled.Refresh,
                        onAction = { currentTypeId?.let { vm.retryBrowsePage(it, extend) } },
                        focusRequester = emptyFocus
                    )

                    onlineItems.isEmpty() -> NfSkeletonGrid()

                    else -> NfPosterGrid(
                        items = onlineItems,
                        pageKey = gridPageKey,
                        stand = stand,
                        firstFocus = gridFirstFocus,
                        upFocusRequester = gridUpFocus,
                        onDetail = onDetail,
                        modifier = Modifier.fillMaxSize(),
                        state = gridState,
                        scrapedRatings = scrapeUi.scrapedRatings,
                        onNearEnd = { currentTypeId?.let { vm.loadMoreBrowsePage(it, extend) } },
                        footer = {
                            NfGridFooter(
                                loading = vm.browseItemsLoading(currentTypeId.orEmpty(), extend),
                                ended = vm.browseItemsEnded(currentTypeId.orEmpty(), extend),
                                error = vm.browseItemsError(currentTypeId.orEmpty(), extend)
                            )
                        }
                    )
                }
            }
        }
    }

    // 批量刮削确认框：独立窗口，焦点天然关在框里，Back 由窗口自己处理
    if (scrapeUi.confirmVisible) {
        TvBatchScrapeDialog(
            total = scrapeUi.pendingTotal,
            skipped = scrapeUi.pendingSkipped,
            onConfirm = { scrapeUi.confirm() },
            onDismiss = { scrapeUi.dismiss() }
        )
    }
}

/**
 * 分类选择进度表：按「来源_线路」记住这一路选到第几个分类。
 *
 * 放在进程级而不是 ViewModel：本页组件在进详情时被摘出组合，`remember` 会连状态一起销毁，
 * 返回后重新从 0 开始就等于换了一个分类，整屏内容跟着换 —— 用户读到的是「返回时刷新了」。
 */
private object NfBrowseMemory {
    private val selected = HashMap<String, Int>()
    fun get(key: String): Int? = synchronized(selected) { selected[key] }
    fun save(key: String, index: Int): Unit = synchronized(selected) { selected[key] = index }
}

/** 网格底部那一行的状态：加载中 / 已到底 / 上一页失败 */
@Composable
private fun NfGridFooter(loading: Boolean, ended: Boolean, error: String?) {
    val text = when {
        loading -> "加载中…"
        error != null -> "下一页失败：$error"
        ended -> "已经到底了"
        else -> ""
    }
    if (text.isEmpty()) return
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = AppColors.Brand,
                strokeWidth = 2.dp,
                modifier = Modifier.width(22.dp).height(22.dp)
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(text, color = TvText.Meta, style = MaterialTheme.typography.bodyMedium)
    }
}

/** 首屏骨架：列数与真实网格一致，卡片陆续替换时不会整屏抖 */
@Composable
private fun NfSkeletonGrid() {
    LazyVerticalGrid(
        columns = GridCells.Fixed(tvPosterColumns()),
        contentPadding = PaddingValues(start = TvPagePadding, end = TvPagePadding, top = NfGridTopPadding),
        horizontalArrangement = Arrangement.spacedBy(NfCardGap),
        verticalArrangement = Arrangement.spacedBy(NfRowVerticalPadding),
        modifier = Modifier.fillMaxSize()
    ) {
        items(18) { ShimmerCard() }
    }
}

/**
 * Emby 的动态分类：全部 → 类型（电影 / 剧集）→ 入库 genres 里按出现次数排前的标签。
 *
 * 分类集合完全由已同步到 media 表的数据算出来，因此换来源、换库都自动跟着变，
 * 也不需要额外请求 Emby 的 /Genres 接口。
 */
private data class NfLocalCategory(val name: String, val matches: (MediaEntity) -> Boolean)

private fun nfLocalCategories(
    items: List<MediaEntity>,
    serverGenres: List<String> = emptyList()
): List<NfLocalCategory> {
    val out = mutableListOf<NfLocalCategory>()
    out += NfLocalCategory("全部") { true }

    // Emby 媒体库优先：条目带 libraryName 时按库分区（NSFW/国漫 等是 CollectionFolder，不是题材）。
    // 库名本身已含「电影/电视剧」，不再另加类型 chip，免得出现两颗同名「电影」。
    // 没有库信息（TVBox / 旧数据 / 回退整库同步）才退到类型 + 题材标签。
    val libraries = items.filter { it.libraryName.isNotBlank() }
        .groupBy { it.libraryName }
        .entries.sortedWith(LibraryDisplayOrder)
        .map { it.key }
    if (libraries.isNotEmpty()) {
        libraries.forEach { name -> out += NfLocalCategory(name) { it.libraryName == name } }
        return out
    }

    val movieCount = items.count { it.kind == MediaKind.MOVIE }
    val seriesCount = items.size - movieCount
    if (movieCount > 0) out += NfLocalCategory("电影") { it.kind == MediaKind.MOVIE }
    if (seriesCount > 0) out += NfLocalCategory("剧集") { it.kind != MediaKind.MOVIE }

    // 服务端题材优先（完整、跟库相关）；没有时回退本地频次前 14，保证接口失败也有分类可用
    val tags = if (serverGenres.isNotEmpty()) {
        serverGenres.filter { it !in KindLikeGenres }
    } else {
        val counts = LinkedHashMap<String, Int>()
        items.forEach { item ->
            item.genreTokens().distinct().forEach { tag ->
                if (tag !in KindLikeGenres) counts[tag] = (counts[tag] ?: 0) + 1
            }
        }
        counts.entries.sortedByDescending { it.value }.take(14).map { it.key }
    }
    tags.forEach { tag ->
        out += NfLocalCategory(tag) { it.genreTokens().contains(tag) }
    }
    return out
}

