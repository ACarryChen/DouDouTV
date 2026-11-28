package com.chen.doudoutv.ui.tv.nf

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chen.doudoutv.core.Prefs
import com.chen.doudoutv.data.model.MediaEntity
import com.chen.doudoutv.data.model.SourceEntity
import com.chen.doudoutv.ui.LibraryViewModel
import com.chen.doudoutv.ui.ReturnSpot
import com.chen.doudoutv.ui.StandTarget
import com.chen.doudoutv.ui.PrecomposeWindow
import com.chen.doudoutv.ui.cardStand
import com.chen.doudoutv.ui.gridState
import com.chen.doudoutv.ui.listState
import com.chen.doudoutv.ui.tv.TvPagePadding
import com.chen.doudoutv.ui.tv.TvSearchUiState
import com.chen.doudoutv.ui.tv.TvTextField
import com.chen.doudoutv.ui.tv.tvClaimFocusOnEnter
import com.chen.doudoutv.ui.tv.tvFocusRing
import com.chen.doudoutv.ui.tv.tvMoveFocus
import com.chen.doudoutv.ui.tv.tvResumeTick
import com.chen.doudoutv.ui.theme.AppColors
import com.chen.doudoutv.ui.theme.Edge
import com.chen.doudoutv.ui.theme.Radius
import com.chen.doudoutv.ui.theme.TvText
import kotlinx.coroutines.delay

/** [ReturnSpot] 的页面标识：搜索结果网格的滚动与落点记在它下面 */
private const val NfSearchKey = "nf_search"

/**
 * 左栏来源折叠状态的进程级记忆：进详情时本页子树被整个销毁，组合级 remember 活不过这一趟。
 * 按提交关键字分键 —— 换词就是另一份结果集，折叠状态各记各的，新搜索回到默认。
 */
private val nfRailExpandedMemory = HashMap<String, Map<String, Boolean>>()

/**
 * Netflix 版布局的搜索页 —— 导航条里的第四颗 tab，不是从首页跳出去的独立页面。
 *
 * 数据和查询链路整条复用旧搜索页（[TvSearchUiState] 由导航层持有）：
 *   · 输入只出联想，选中联想 / 点「搜索」/ 键盘回车才真正查询；
 *   · 本地库先上屏，随后并发搜 TVBox 各站点，谁先返回先追加谁；
 *   · `submittedKeyword` 没变就不重查 —— 从详情页按返回回来时结果原样留着。
 * 换掉的只有版式：导航条 + 竖版海报网格 + 十英尺输入行，外壳由 [NfScaffold] 统一给。
 */
@Composable
fun NfSearchScreen(
    vm: LibraryViewModel,
    state: TvSearchUiState,
    onTab: (NfTab) -> Unit,
    onDetail: (Long) -> Unit,
    onSources: () -> Unit,
    onSettings: () -> Unit
) {
    val chrome = rememberNfChrome()
    val fieldFocus = remember { FocusRequester() }
    // 首行/首卡的锚点随「那一格换成谁了」重建：同一实例先后挂两个节点会留下指向废弃节点的记录
    val sugFirstFocus = remember(state.suggestions.firstOrNull()) { FocusRequester() }
    val stand = remember { StandTarget() }
    // 搜索历史：和手机端同一份 Prefs 记录，提交一次搜索或清空历史时 historyEpoch 自增触发重读
    val history = remember(state.historyEpoch) { Prefs.getSearchHistory() }
    val histFirstFocus = remember(history.firstOrNull()) { FocusRequester() }

    // 首次进搜索页后台拉一次热门榜补齐联想（24h 内走本地缓存，不联网）
    LaunchedEffect(Unit) {
        if (runCatching { vm.refreshHotSuggestions() }.getOrDefault(false)) state.hotEpoch++
    }

    // 联想：输入停顿 200ms 后查库。中文按标题模糊匹配，字母按拼音首字母前缀匹配
    LaunchedEffect(state.keyword, state.hotEpoch) {
        val q = state.keyword.trim()
        if (q.isEmpty() || state.submittedKeyword.isNotEmpty()) {
            state.suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(200)
        state.suggestions = runCatching { vm.pinyinSuggest(q.lowercase()) }
            .getOrDefault(emptyList())
    }

    // 实际搜索：submittedKeyword 变化才执行；从详情返回时关键字没变，直接复用已有结果。
    // 屏蔽/恢复线路（hideEpoch 变化）后同词也重查，让屏蔽立即生效、取消立即恢复。
    LaunchedEffect(state.submittedKeyword, vm.hideEpoch) {
        val q = state.submittedKeyword.trim()
        if (q.isEmpty() || (q == state.searchedKeyword && state.searchedHideEpoch == vm.hideEpoch)) {
            return@LaunchedEffect
        }
        val epoch = vm.hideEpoch
        state.searchedHideEpoch = epoch
        state.loading = true
        state.searchedKeyword = q

        val local = runCatching { vm.searchMedia(q) }.getOrDefault(emptyList())
        if (state.submittedKeyword == q && state.searchedHideEpoch == epoch) {
            state.results = local
            state.loading = false
            state.searched = true
        }

        /** 把一批远程结果并进当前列表（按 remoteId 去重） */
        fun appendRemote(batch: List<MediaEntity>) {
            val seen = state.results.map { it.remoteId }.toSet()
            state.results = state.results + batch.filter { it.remoteId !in seen }
        }

        state.remoteSearching = true
        val remote = vm.searchRemote(q) { partial ->
            // 远程返回前用户可能已经改词重新搜索或切换了屏蔽，旧结果不能覆盖当前列表
            if (state.submittedKeyword != q || state.searchedHideEpoch != epoch) return@searchRemote
            appendRemote(partial)
        }
        if (state.submittedKeyword == q && state.searchedHideEpoch == epoch) {
            appendRemote(remote)
            state.remoteSearching = false
        }
    }

    val showSuggestions =
        state.submittedKeyword.isEmpty() && state.keyword.isNotBlank() && state.suggestions.isNotEmpty()
    // 历史只在「还没提交、也还没打字」的空态铺开：一开始打字就让位给联想
    val showHistory =
        state.submittedKeyword.isEmpty() && state.keyword.isBlank() && history.isNotEmpty()
    // 双层分组：先按来源（一个来源一组：TVBox 一整份配置、Emby 一台服务器），
    // 组内再按线路（站点）。Emby 没有线路概念，那一组就只有来源这一层。
    val sources by vm.enabledSources.collectAsStateWithLifecycle()
    val sourceGroups = remember(state.results, sources) { nfSearchGroups(state.results, sources) }

    // 每个来源的折叠状态 —— 默认第一个来源展开，其余折叠。
    // 只有「来源」这一层可折叠；组内线路行常显、不折叠（点线路聚焦即切右侧，折叠反而多一层按键）。
    // 从详情返回时子树重建，先按提交关键字把离开前的折叠状态恢复回来：不恢复的话
    // 全部塌回「只展开第一个」，用户离开的那条线路行不在组合树里，左移焦点只能落到首个表头。
    val expandedSources = remember(state.submittedKeyword) {
        mutableStateMapOf<String, Boolean>().apply {
            nfRailExpandedMemory[state.submittedKeyword]?.let { putAll(it) }
            // 恢复之后新出现的来源（远程批次晚到）补一个默认展开位：只给第一个来源，
            // 和首次进页的默认一致；用户显式折叠过的（记录里有 false）不会被覆盖。
            sourceGroups.firstOrNull()?.let { first -> if (!containsKey(first.key)) this[first.key] = true }
        }
    }
    // 提交那一刻 sourceGroups 还是空的，上面的 remember 补不了默认位；远程批次把结果带上来后
    // 整张表若还空着，这里把第一个来源展开 —— 右侧显示的就是第一条线路，左栏必须同样看得见它。
    LaunchedEffect(sourceGroups, state.submittedKeyword) {
        if (expandedSources.isEmpty() && sourceGroups.isNotEmpty()) {
            val firstKey = sourceGroups.first().key
            expandedSources[firstKey] = true
            nfRailExpandedMemory[state.submittedKeyword] = expandedSources.toMap()
        }
    }
    // 左栏第一颗可聚焦行（首个来源表头）：输入框 ↓ 与进页抢焦的落点（规范 R6 不留焦点死区）
    val firstHeaderFocus = remember(sourceGroups.firstOrNull()?.key) { FocusRequester() }
    // 右侧网格只认「按过 OK 的线路」：上下移动只换高亮，不再重建右侧网格 ——
    // 切线路要重排整屏海报，焦点每移到一行就重建一次，是搜索页上下卡顿的主因。
    // 从详情返回时先恢复离开时确认过的线路：不恢复的话右侧整栏是空的，
    // 离开那张卡不在组合树里，stand.requester 交不出来，抢焦只能退到输入框。
    // 线路 key 记在 stand 落点里（"route/<线路key>#<媒体id>"），直接从那读；
    // 新搜索提交时落点被清掉，回到「未确认」的提示态。
    var confirmedRouteKey by remember(state.submittedKeyword) {
        mutableStateOf(
            ReturnSpot.standOf(NfSearchKey)?.let { spot ->
                val i = spot.lastIndexOf('#')
                if (i > 6 && spot.startsWith("route/")) spot.substring(6, i) else ""
            } ?: ""
        )
    }
    // 按 OK 确认新线路后，等网格组合完成再把焦点送进首卡
    var pendingGridFocus by remember(state.submittedKeyword) { mutableStateOf(false) }
    // 每条线路行的锚点：网格首卡 ← 交回「刚离开的那一行」
    val railFocusers = remember { mutableStateMapOf<String, FocusRequester>() }
    // 摊平的线路表：key →（展示名，命中列表）。Emby 的线路名为空，展示名用来源名。
    val routeIndex = remember(sourceGroups) {
        LinkedHashMap<String, Pair<String, List<MediaEntity>>>().apply {
            sourceGroups.forEach { g ->
                g.routes.forEach { (routeName, items) ->
                    put("${g.key}/$routeName", (routeName.ifBlank { g.label }) to items)
                }
            }
        }
    }
    val activeKey = routeIndex.keys.firstOrNull { it == confirmedRouteKey }.orEmpty()
    val activeRoute = routeIndex[activeKey]
    // 右栏首卡锚点：随「当前线路的首卡换成谁」重建 —— 切线路时旧首卡正在销毁、
    // 新首卡又挂同一个实例，同一 FocusRequester 同时占两个节点会撞 claim
    val gridFirstFocus = remember(activeRoute?.second?.firstOrNull()?.id) { FocusRequester() }
    // 刚确认的线路要等这一帧组合出网格才挂得上首卡锚点，晚一小步再把焦点送进去
    LaunchedEffect(pendingGridFocus, activeKey) {
        if (pendingGridFocus && activeRoute != null) {
            delay(60)
            if (tvMoveFocus(gridFirstFocus)) pendingGridFocus = false
        }
    }
    // 输入框 ↓ / 导航条 ↓ 的落点：联想列表 > 左栏首行 > 历史首行 > 输入框自身
    // （空态且没有历史时页面只有输入框可聚焦，不能留焦点死区 —— 规范 R6）。
    val belowField = when {
        showSuggestions -> sugFirstFocus
        state.results.isNotEmpty() -> firstHeaderFocus
        showHistory -> histFirstFocus
        else -> fieldFocus
    }

    val resumeTick = tvResumeTick()
    // tick 里刻意不放 showSuggestions：联想是「打出第一个字 200ms 后」冒出来的，
    // 一旦进 tick，抢焦就会打断正在输入的编辑态；联想出现时焦点该留在原输入框内，
    // 往下走由输入框浏览态的 ↓（nfFocusRoute）显式交点。
    // 从别的 tab 点「搜索」切过来：焦点留在标签上，不交给输入框（NfTabSwitch）。
    // 一次性消费：只有进页那一次落标签；tick 也不再挂 results.size —— 否则提交搜索、
    // 远程批次每追加一次结果就重放一遍抢焦，焦点被从输入行拽回导航条（clearFocus 先闪首页 tab）。
    var viaTab by remember { mutableStateOf(NfTabSwitch.consume(NfTab.Search)) }
    tvClaimFocusOnEnter(
        tick = resumeTick,
        // 输入 / 联想阶段这一串锚点只留输入框自己：
        // 焦点往下走只有两条正当路径：用户自己按 ↓（nfFocusRoute 显式交点），或提交搜索出结果。
        // 提交之后（submittedKeyword 非空）恢复原来的抢焦：还站在离开时那张卡，没有记录才落首卡。
        anchorsProvider = {
            when {
                viaTab -> {
                    viaTab = false
                    listOf(chrome.tabOf(NfTab.Search))
                }
                state.submittedKeyword.isEmpty() -> listOf(fieldFocus)
                else -> listOfNotNull(stand.requester, belowField, fieldFocus)
            }
        }
    )

    // 返回键分层的依据：焦点此刻在不在「输入框 + 两颗按钮」这一行
    val jumpTo = rememberNfLayerJump()
    var fieldRowFocused by remember { mutableStateOf(false) }
    var fieldEditing by remember { mutableStateOf(false) }

    NfScaffold(
        vm = vm,
        current = NfTab.Search,
        chrome = chrome,
        // 导航条 ↓ 显式交回输入行那一层（联想首行 > 结果首卡 > 输入框），规范 R1
        navInFlow = true,
        belowFocusRequester = belowField,
        onTab = onTab,
        onSettings = onSettings,
        onManageSources = onSources
    ) {
        Column(
            Modifier
                .fillMaxSize()
                // 返回键的层级：联想 / 结果 / 历史 → 输入框 → tab 栏（tab 栏那一级由 TvApp 根部接）
                .nfBackKey {
                    if (fieldEditing) false
                    else if (fieldRowFocused) jumpTo(chrome.tabOf(NfTab.Search))
                    else jumpTo(fieldFocus)
                }
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = TvPagePadding, end = TvPagePadding, bottom = 14.dp)
                    .onFocusEvent { fieldRowFocused = it.hasFocus },
                verticalAlignment = Alignment.CenterVertically
            ) {
                NfSearchField(
                    value = state.keyword,
                    focusRequester = fieldFocus,
                    modifier = Modifier.weight(1f),
                    upFocusRequester = chrome.tabOf(NfTab.Search),
                    downFocusRequester = belowField,
                    onEditingChange = { fieldEditing = it },
                    onValueChange = {
                        state.keyword = it
                        // 改词就清掉上一次的结果，回到「输入 + 联想」状态
                        if (state.submittedKeyword.isNotEmpty()) {
                            state.submittedKeyword = ""
                            state.searchedKeyword = ""
                            state.results = emptyList()
                            state.searched = false
                            state.loading = false
                            state.remoteSearching = false
                        }
                    },
                    onSubmit = { state.submit() }
                )
                Spacer(Modifier.width(16.dp))
                NfActionButton(
                    text = "搜索",
                    // 提交会把联想/历史那一整块从组合里摘掉：焦点若还站在联想行 / 历史词章上，
                    // 节点销毁后 Compose 找不到可聚焦的祖先，会一路掉回根节点 —— 树里第一个
                    // 可聚焦元件正是导航条的「首页」tab，看起来就是焦点跑到了首页。
                    // 提交完立刻把焦点显式交回输入框（常驻在输入行，不会被摘）。
                    onClick = {
                        // 新搜索 = 新结果集：清掉上次的落点/滚动记忆，右侧回到「按 OK 才加载」的初始态
                        ReturnSpot.clear(NfSearchKey)
                        state.submit(); tvMoveFocus(fieldFocus)
                    },
                    primary = true
                )
                Spacer(Modifier.width(12.dp))
                // 清空：词、联想、结果和各进行中标记一次全清，回到「还没输入」的初始态。
                // 清完把焦点交回输入框 —— 接着打下一个词不用再按一次 ↑。
                NfActionButton(
                    text = "清空",
                    onClick = {
                        state.clear()
                        tvMoveFocus(fieldFocus)
                    }
                )
            }

            Box(Modifier.fillMaxWidth().weight(1f)) {
                when {
                    showSuggestions -> {
                        Column(Modifier.fillMaxSize()) {
                            Text(
                                "猜你想看 · 按 OK 直接搜索",
                                color = TvText.Meta,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = TvPagePadding, bottom = 8.dp)
                            )
                            LazyColumn(
                                contentPadding = PaddingValues(
                                    start = TvPagePadding, end = TvPagePadding, bottom = 24.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(state.suggestions.take(8), key = { "nf_sug_$it" }) { title ->
                                    NfSuggestionRow(
                                        title = title,
                                        onClick = {
                                            ReturnSpot.clear(NfSearchKey)
                                            state.submit(title); tvMoveFocus(fieldFocus)
                                        },
                                        modifier = if (title == state.suggestions.firstOrNull())
                                            Modifier.nfFocusRoute(up = fieldFocus).focusRequester(sugFirstFocus)
                                        else Modifier
                                    )
                                }
                            }
                        }
                    }

                    state.loading -> NfSearchBusy("搜索中…")

                    // 左 4 右 6 分栏：左栏来源/线路轨道，右侧当前线路的影片
                    state.results.isNotEmpty() -> Row(Modifier.fillMaxSize()) {
                        Column(
                            Modifier
                                .weight(4f)
                                .padding(start = TvPagePadding, end = 10.dp)
                        ) {
                            LazyColumn(
                                state = ReturnSpot.listState(
                                    "$NfSearchKey/rail", precompose = PrecomposeWindow.Tv
                                ),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                contentPadding = PaddingValues(bottom = 24.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                sourceGroups.forEachIndexed { sourceIdx, group ->
                                    val isExpanded = expandedSources[group.key] ?: false
                                    item(key = "nf_rail_src_${group.key}") {
                                        NfSourceHeader(
                                            label = group.label,
                                            meta = if (group.isTvBox)
                                                "${group.routes.sumOf { it.second.size }} 条 · ${group.routes.size} 线路"
                                            else "${group.routes.firstOrNull()?.second?.size ?: 0} 条",
                                            expanded = isExpanded,
                                            // 第一颗来源表头交回页面持有的锚点：输入框的 ↓ 和进页抢焦都落在这儿
                                            focusRequester = if (sourceIdx == 0) firstHeaderFocus
                                                else remember(group.key) { FocusRequester() },
                                            upFocusRequester = if (sourceIdx == 0) fieldFocus else null,
                                            onToggle = {
                                                expandedSources[group.key] = !isExpanded
                                                // 实时写回进程级记忆：进详情销毁子树后还能按原样恢复
                                                nfRailExpandedMemory[state.submittedKeyword] =
                                                    expandedSources.toMap()
                                            }
                                        )
                                    }
                                    if (isExpanded) {
                                        group.routes.forEach { (routeName, routeItems) ->
                                            val routeKey = "${group.key}/$routeName"
                                            item(key = "nf_rail_$routeKey") {
                                                val fr = railFocusers.getOrPut(routeKey) { FocusRequester() }
                                                NfRailRouteRow(
                                                    label = routeName.ifBlank { group.label },
                                                    kind = if (group.isTvBox) "站点" else "服务器",
                                                    count = routeItems.size,
                                                    selected = routeKey == activeKey,
                                                    focusRequester = fr,
                                                    rightFocusRequester = gridFirstFocus,
                                                    // 上下移动只换高亮，不再动右侧（重排整屏海报是卡顿主因）
                                                    // OK：确认这条线路并进右侧网格首卡
                                                    onEnter = {
                                                        if (routeKey == confirmedRouteKey) {
                                                            tvMoveFocus(gridFirstFocus)
                                                        } else {
                                                            confirmedRouteKey = routeKey
                                                            pendingGridFocus = true
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Column(
                            Modifier
                                .weight(6f)
                                .padding(end = TvPagePadding)
                        ) {
                            activeRoute?.let { (_, items) ->
                                NfSearchRouteGrid(
                                    routeKey = activeKey,
                                    items = items,
                                    stand = stand,
                                    firstFocus = gridFirstFocus,
                                    upFocusRequester = fieldFocus,
                                    leftFocusRequester = railFocusers[activeKey] ?: firstHeaderFocus,
                                    modifier = Modifier.weight(1f),
                                    onDetail = onDetail
                                )
                            } ?: NfInlineEmpty(
                                text = "按 OK 查看线路资源",
                                hint = "上下移动选择线路，按 OK 才加载右侧影片"
                            )
                        }
                    }

                    // 远程还没回来时不要急着判「没找到」，否则用户看到的是搜索中的空屏
                    state.remoteSearching -> NfSearchBusy("正在搜索各站点…", "本地库没有命中，正在向 TVBox 各站点查询")

                    state.searched -> NfInlineEmpty(
                        text = "没有找到「${state.submittedKeyword}」",
                        hint = "换个关键词试试，左栏选线路按 OK 看右侧影片"
                    )

                    else -> if (showHistory) {
                        NfSearchHistory(
                            history = history,
                            firstFocus = histFirstFocus,
                            upFocusRequester = fieldFocus,
                            onSelect = {
                                ReturnSpot.clear(NfSearchKey)
                                state.submit(it); tvMoveFocus(fieldFocus)
                            },
                            onClear = { state.clearHistory(); tvMoveFocus(fieldFocus) }
                        )
                    } else NfInlineEmpty(
                        text = "搜索媒体库",
                        hint = "输入片名、演员或拼音首字母，选中联想或按「搜索」确认"
                    )
                }
            }
        }
    }
}

/**
 * 搜索输入框。方向键显式交回：↑ 给本 tab 在导航条上的那颗按钮，↓ 给联想列表 / 结果首卡。
 * 文本框自己会把方向键读成光标移动，所以走按键拦截（[nfFocusRoute]）而不是 focusProperties。
 */
@Composable
private fun NfSearchField(
    value: String,
    focusRequester: FocusRequester,
    upFocusRequester: FocusRequester,
    downFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
    onEditingChange: (Boolean) -> Unit,
    onSubmit: () -> Unit
) {
    var editing by remember { mutableStateOf(false) }

    TvTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .zIndex(1f)
            .then(
                if (editing) Modifier
                else Modifier.nfFocusRoute(up = upFocusRequester, down = downFocusRequester)
            ),
        focusRequester = focusRequester,
        placeholder = "搜索片名 / 演员 / 拼音首字母",
        imeAction = ImeAction.Search,
        onImeAction = onSubmit,
        onEditingChange = {
            editing = it
            onEditingChange(it)
        },
        bare = true,
        leadingIcon = {
            androidx.compose.material3.Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = TvText.Meta,
                modifier = Modifier.size(22.dp)
            )
        },
        height = 56.dp,
        textStyle = TextStyle(
            color = TvText.Title,
            fontSize = MaterialTheme.typography.bodyLarge.fontSize,
            fontWeight = FontWeight.Medium
        )
    )
}

/** 联想片名行：整行可聚焦，OK 直接以该片名搜索；首行的 ↑ 显式还给输入框 */
@Composable
private fun NfSuggestionRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Row(
        modifier
            .fillMaxWidth()
            .clip(Radius.Md)
            .background(if (focused) Color.White else AppColors.Surface)
            .tvFocusRing(focused)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .focusable(interactionSource = interaction)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = if (focused) Color.Black else TvText.Meta,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            title,
            color = if (focused) Color.Black else TvText.Title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 空态的「最近搜索」：横向流式铺开，放不下自动换下一行，最多 10 条（与手机端同一口径，
 * 超出 10 条的旧词在 [Prefs.addSearchHistory] 写入时就已经丢掉）。
 * 首颗的 ↑ 显式还给输入框，其余方向走系统几何搜索；「清空历史」排在最后一颗，是这一段的最后一站。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NfSearchHistory(
    history: List<String>,
    firstFocus: FocusRequester,
    upFocusRequester: FocusRequester,
    onSelect: (String) -> Unit,
    onClear: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = TvPagePadding, end = TvPagePadding, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            history.take(10).forEachIndexed { index, kw ->
                NfHistoryChip(
                    title = kw,
                    onClick = { onSelect(kw) },
                    modifier = if (index == 0)
                        Modifier.nfFocusRoute(up = upFocusRequester).focusRequester(firstFocus)
                    else Modifier
                )
            }
            NfActionButton(text = "清空历史", onClick = onClear)
        }
    }
}

/** 历史记录的一颗词章：整颗可聚焦，OK 直接以该词搜索 */
@Composable
private fun NfHistoryChip(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Row(
        modifier
            .widthIn(max = 420.dp)
            .clip(Radius.Md)
            .background(if (focused) Color.White else AppColors.Surface)
            .tvFocusRing(focused)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .focusable(interactionSource = interaction)
            .padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = if (focused) Color.Black else TvText.Meta,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            title,
            color = if (focused) Color.Black else TvText.Title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 查询进行中的整块状态：转圈 + 一句话，不可聚焦（此时焦点仍留在输入框上） */
@Composable
private fun NfSearchBusy(text: String, hint: String? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            color = AppColors.Brand,
            strokeWidth = 2.dp,
            modifier = Modifier.width(26.dp).height(26.dp)
        )
        Text(
            text,
            color = TvText.Section,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 16.dp)
        )
        if (!hint.isNullOrBlank()) {
            Text(
                hint,
                color = TvText.Meta,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

/**
 * 搜索结果的分组：来源 → 线路（站点）。
 *
 * Emby 没有线路概念，[routes] 只有一项且线路名为空 —— 界面上就只有来源那一层。
 */
private class NfSearchGroup(
    val key: String,
    val label: String,
    val isTvBox: Boolean,
    val routes: List<Pair<String, List<MediaEntity>>>,
)

private fun nfSearchGroups(results: List<MediaEntity>, sources: List<SourceEntity>): List<NfSearchGroup> =
    nfGroupBySource(results, sources).map { group ->
        if (!group.isTvBox) return@map NfSearchGroup(group.key, group.label, false, listOf("" to group.items))
        // 线路名来自入库/远程命中时带的站点名；老库里还没记站点名的条目汇到最后一组，
        // 不然会给它们铺出一堆空标题行
        val byRoute = group.items.groupBy { it.playSource }
        val named = byRoute.filterKeys { it.isNotBlank() }
        val unlabeled = byRoute[""] ?: emptyList()
        val routes = named.toList() + if (unlabeled.isEmpty()) emptyList() else listOf("未标注线路" to unlabeled)
        NfSearchGroup(group.key, group.label, true, routes)
    }

/**
 * 左栏的线路行：常显、不折叠 —— 焦点只控制当前行高亮，不切换右栏内容。
 * 选中行给一条品牌红竖条作静态标识；聚焦态仍是全页统一的 [tvFocusRing] 双环。
 * → / OK 进右栏首卡；↑↓ 交给系统几何寻焦（轨道本身就是纵向列表）。
 */
@Composable
private fun NfRailRouteRow(
    label: String,
    kind: String,
    count: Int,
    selected: Boolean,
    focusRequester: FocusRequester,
    rightFocusRequester: FocusRequester,
    onEnter: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(Radius.Md)
            .background(if (selected || focused) AppColors.SurfaceHi else AppColors.Surface)
            .tvFocusRing(focused)
            .nfFocusRoute(right = rightFocusRequester)
            .focusRequester(focusRequester)
            .clickable(interactionSource = interaction, indication = null, onClick = onEnter)
            .focusable(interactionSource = interaction)
            .padding(start = 12.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selected) {
            Box(
                Modifier
                    .padding(end = 10.dp)
                    .size(width = 3.dp, height = 26.dp)
                    .clip(Radius.Md)
                    .background(AppColors.Brand)
            )
        }
        Text(
            label,
            // 聚焦底是 SurfaceHi（跟随模式），文字跟着 TvText 走两种模式都落在同一档上
            color = TvText.Title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(10.dp))
        // 种类小徽章：站点 / 服务器
        Box(
            Modifier
                .clip(Radius.Md)
                .background(Edge.HairlineStrong)
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(kind, color = TvText.Meta, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            "$count 条",
            color = TvText.Meta,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

/**
 * 右栏：当前线路的影片，常规尺寸的竖版海报网格 —— 顶对齐、不拉伸铺满。
 * 列数按海报默认宽自适应，滚动位置按线路记账（[ReturnSpot.gridState]），切回来还站在原来那一屏。
 * 首卡 ↑ 显式还给输入行、← 还给左栏线路行；其余卡片走系统几何寻焦。
 */
@Composable
private fun NfSearchRouteGrid(
    routeKey: String,
    items: List<MediaEntity>,
    stand: StandTarget,
    firstFocus: FocusRequester,
    upFocusRequester: FocusRequester,
    leftFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    onDetail: (Long) -> Unit
) {
    val rowKey = "route/$routeKey"
    val gridState = ReturnSpot.gridState("$NfSearchKey/$rowKey", precompose = PrecomposeWindow.Tv)
    val firstId = items.firstOrNull()?.id
    // 单卡高度对齐左栏三行线路行（52dp×3 + 行距 6dp×2 ≈ 168dp）：卡片 = 2:3 海报图 + 图下约 24dp 片名行，
    // 反推卡宽 ≈ 96dp。列数不写死，按右栏实际宽度动态算 —— 屏更宽的电视自然排下更多列
    val cardWidth = (52.dp * 3 + 6.dp * 2 - 24.dp) / 1.5f
    val panelWidth = (LocalConfiguration.current.screenWidthDp.dp - TvPagePadding * 2) * 0.6f - 10.dp
    val columns = ((panelWidth + NfCardGap) / (cardWidth + NfCardGap)).toInt().coerceAtLeast(1)

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(columns),
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(top = NfGridTopPadding, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(NfCardGap),
        verticalArrangement = Arrangement.spacedBy(NfRowVerticalPadding)
    ) {
        gridItemsIndexed(
            items,
            key = { _, it -> "nf_rt_${it.id}" },
            contentType = { _, _ -> "poster" }
        ) { index, mediaItem ->
            val ownFocus = remember(mediaItem.id) { FocusRequester() }
            NfPosterCard(
                item = mediaItem,
                onClick = { onDetail(mediaItem.id) },
                fillCell = true,
                anchorStart = index % columns == 0,
                modifier = Modifier
                    .then(
                        if (mediaItem.id == firstId)
                            Modifier.nfFocusRoute(up = upFocusRequester, left = leftFocusRequester)
                        else Modifier
                    )
                    .then(
                        ReturnSpot.cardStand(
                            pageKey = NfSearchKey,
                            rowKey = rowKey,
                            mediaId = mediaItem.id,
                            target = stand,
                            requester = if (mediaItem.id == firstId) firstFocus else ownFocus
                        )
                    )
            )
        }
    }
}

/**
 * 来源组表头：整页唯一可折叠的一层 —— 可聚焦，OK 键切换展开/折叠，组内线路行常显。
 */
@Composable
private fun NfSourceHeader(
    label: String,
    meta: String,
    expanded: Boolean,
    focusRequester: FocusRequester,
    upFocusRequester: FocusRequester?,
    onToggle: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Radius.Md)
            .background(if (focused) AppColors.SurfaceHi else Color.Transparent)
            .tvFocusRing(focused)
            .then(upFocusRequester?.let { Modifier.nfFocusRoute(up = it) } ?: Modifier)
            .focusRequester(focusRequester)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onToggle
            )
            .focusable(interactionSource = interaction)
            .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (expanded) "▾" else "▸",
            color = TvText.Meta,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            label,
            color = TvText.Title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            meta,
            color = TvText.Meta,
            style = MaterialTheme.typography.labelMedium
        )
    }
}
