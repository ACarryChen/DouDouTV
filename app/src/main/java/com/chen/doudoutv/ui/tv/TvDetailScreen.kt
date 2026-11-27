package com.chen.doudoutv.ui.tv

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import coil.compose.AsyncImage
import com.chen.doudoutv.MainActivity
import com.chen.doudoutv.data.emby.EmbyClient
import com.chen.doudoutv.data.format.FormatProbe
import com.chen.doudoutv.data.model.MediaEntity
import com.chen.doudoutv.data.model.MediaKind
import com.chen.doudoutv.data.model.SourceKind
import com.chen.doudoutv.data.model.splitCastNames
import com.chen.doudoutv.data.tvbox.HtmlText
import com.chen.doudoutv.ui.LibraryViewModel
import com.chen.doudoutv.ui.detail.FocusColorCache
import com.chen.doudoutv.ui.detail.fallbackColorFromTitle
import com.chen.doudoutv.ui.detail.fmtIntroMs
import com.chen.doudoutv.ui.detail.parseIntroInput
import com.chen.doudoutv.ui.detail.focusColorOf
import com.chen.doudoutv.ui.detail.loadPosterBitmap
import com.chen.doudoutv.ui.detail.pageTonesOf
import com.chen.doudoutv.ui.theme.AppColors
import com.chen.doudoutv.ui.theme.Edge
import com.chen.doudoutv.ui.theme.LocalTvPageDark
import com.chen.doudoutv.ui.theme.TvPageText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.zIndex
import com.chen.doudoutv.core.Prefs
import com.chen.doudoutv.ui.StandTarget
import com.chen.doudoutv.ui.components.episodeLabel
import com.chen.doudoutv.ui.components.EpisodeStill
import com.chen.doudoutv.ui.components.matchEpisodeInSource
import com.chen.doudoutv.ui.components.usesSourceEpisodeName
import com.chen.doudoutv.ui.components.BadgeChip
import com.chen.doudoutv.ui.components.badgeInfoFor
import com.chen.doudoutv.ui.components.buildFormatBadges
import com.chen.doudoutv.ui.components.TmdbCreditLine
import com.chen.doudoutv.ui.theme.TvText
import com.chen.doudoutv.ui.theme.Radius
import com.chen.doudoutv.ui.theme.Motion
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 详情页是沉浸式恒暗页（pageTonesOf 把整页底色压成海报聚焦色的深色版本），
 * 文字不随颜色模式翻深 —— 浅色模式下全局 TvText 会翻近黑、落在这层深底上读不出来，
 * 所以本文件用这组恒亮档代替 TvText；唯一的例外是 TvSkipIntroDialog，
 * 它的底是跟随颜色模式的 SurfaceHi，里面仍用 TvText。
 */
private val PageTitle get() = TvPageText.Title
private val PageSection get() = TvPageText.Section
private val PageBody get() = TvPageText.Body
private val PageMeta get() = TvPageText.Meta
private val PageMuted get() = TvPageText.Muted

/**
 * 电视端简介页（独立实现，不复用手机端 DetailScreen）。单页滚动 + 焦点分区（Netflix 式）：
 * 信息全部常驻组合树，方向键只在焦点锚点间移动、视口跟随；只读区块不接焦点。
 * 焦点锚点链（唯一，全显式）：收藏 ⇅ 播放 ⇅ 选集控制条 ⇅ 选集网格 ⇅ 主演头像。
 * 关键取舍：主演排在选集之后作整页最后一层（电视上焦点走不到=看不到，电影无分集必须可落）；
 * ↑ 回到收藏时滚动送回首屏构图（海报+标题大图态），不能只靠 bring-into-view 最小滚动。
 * 版式：左侧海报 + 右侧文案两栏；分集 10 列网格（几百集按右键滚不动，网格一屏可见进度）。
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun TvDetailScreen(
    vm: LibraryViewModel,
    mediaId: Long,
    onBack: () -> Unit,
    onPlay: (Long) -> Unit,
    onCategoryVodId: ((sourceId: Long, siteKey: String, typeId: String, typeName: String) -> Unit)? = null
) {
    // 加载链（TvDetailLoadChain）要回写的状态全部建成显式 MutableState 再委托出去：
    // 容器随组合新建、拿到的是当下这批状态对象（记住容器反而会攥着旧状态）。
    val mediaState = remember { mutableStateOf<MediaEntity?>(null) }
    var media by mediaState
    val episodesState = remember { mutableStateOf<List<MediaEntity>>(emptyList()) }
    var episodes by episodesState

    /** 当前条目是否来自 Emby：只有 Emby 分集走剧照缩略图卡，TVBox 保持原文字卡 */
    val isEmbySourceState = remember(mediaId) { mutableStateOf(false) }
    var isEmbySource by isEmbySourceState

    /**
     * 从「继续观看」进来、页面改绑到剧集壳之后，记住用户真正在看的那条分集。
     * 剧集壳自己没有可播地址也不带进度，主按钮要续播的仍是这条分集。
     * 其他入口进来时恒为 null，按钮行为不变。
     */
    val resumeEpisodeState = remember(mediaId) { mutableStateOf<MediaEntity?>(null) }
    var resumeEpisode by resumeEpisodeState
    val scrapedState = remember { mutableStateOf<EmbyClient.ItemDetail?>(null) }
    var scraped by scrapedState

    /** TVBox 多播放源（云盘/线路） */
    val playSourcesState = remember { mutableStateOf<List<String>>(emptyList()) }
    var playSources by playSourcesState
    val selectedPlaySourceState = remember { mutableStateOf<String>("") }
    var selectedPlaySource by selectedPlaySourceState

    /** 「跳过片头」预设弹窗（遥控器焦点）显隐：只被下方 UI 树读写，状态交 Body 挂名使用 */
    val showSkipDialogState = remember { mutableStateOf(false) }

    // 详情页期间把 MainActivity 的 finish() 拦下来：爬虫壳拉详情（网盘线路）时会顺手结束宿主，
    // 不拦就会「点详情直接闪退」。
    DisposableEffect(Unit) {
        MainActivity.detailVisible = true
        onDispose { MainActivity.detailVisible = false }
    }

    /**
     * TMDB 刮削结果。
     *
     * 这个页面之前完全没有接 TMDB，所以「刮削前 / 刮削后」看起来一模一样 ——
     * 手机端有海报、艺术字 Logo、评分、简介、演职员，电视端却只有 Emby 的基础字段。
     * 这里和手机端 DetailScreen 走同一条链路（同样的策略判定 + 新鲜期判断），
     * 保证两端刮削结果一致。
     */
    val tmdbState = remember { mutableStateOf<com.chen.doudoutv.data.tmdb.TmdbClient.ScrapeResult?>(null) }
    var tmdb by tmdbState
    val loadingState = remember { mutableStateOf(true) }
    var loading by loadingState

    /** 分季 + 分页：几百集的剧直接一屏铺满很难找，先按季过滤再按页翻 */
    val selectedSeasonState = remember(mediaId) { mutableStateOf(0) }
    var selectedSeason by selectedSeasonState
    val epPageState = remember(mediaId) { mutableStateOf(0) }
    var epPage by epPageState
    /** 整页重建后要把焦点交回的那一集（0 = 没有），由 [LaunchedEffect] 的接址逻辑填 */
    val recallEpisodeIdState = remember(mediaId) { mutableStateOf(0L) }
    var recallEpisodeId by recallEpisodeIdState
    /** 返回重建时 Emby 货架要滚到的那一格（页内序号，-1=无）；滚一次即消费，正常翻页仍回第一格 */
    val recallScrollTargetState = remember(mediaId) { mutableStateOf(-1) }
    var recallScrollTarget by recallScrollTargetState
    /**
     * 这一页是不是「播放器把宿主 Activity 打断后重建」进来的（true 才需要接回原来那一集）。
     *
     * 只有上面那条接址分支会把它翻真。[recallEpisodeId] 单独不够：换播放源（rebindSource）
     * 也写它，进程级记下的分集 id 还可能是上一次看别的片子留下的 —— 于是第一次正常进这一页
     * 也会被「接回老落点」带走，焦点落在选集格里，bring-into-view 顺势把简介摊出来，
     * 首屏那张大图构图就没有了。
     */
    val restoredStandState = remember(mediaId) { mutableStateOf(false) }
    var restoredStand by restoredStandState

    /**
     * 焦点锚点。
     *
     * 页面只有一条滚动轴，信息全程在场，方向键只负责在这些锚点之间移动：
     *   收藏 ↑ 页面顶部 / ↓ 播放
     *   播放 ↓ 选集控制条（有则）→ 第一集
     *   剧集首行 ↑ 控制条（无则播放）
     * 不再用全局 onPreviewKeyEvent 切换内容显隐，避免「上键把选集收掉」。
     */
    val favoriteFocusRequester = remember { FocusRequester() }
    val playFocusRequester = remember { FocusRequester() }
    val controlsFocusRequester = remember { FocusRequester() }
    val firstEpFocusRequester = remember { FocusRequester() }
    /**
     * Emby 横滑货架「最后聚焦那一集」的活落点。
     * 货架右移后首格会被 LazyRow 回收，死钉首格的 [firstEpFocusRequester] 变成死节点，
     * 下键就回不到选集（同首页继续观看行 #59 的根因）。最后聚焦那集停在当前滚动位置、
     * 必然还组合着，进货架优先落它。换季 / 翻页时重置（见 embyRowState 的 LaunchedEffect）。
     */
    val epStand = remember { StandTarget() }
    /** 翻页行（剧集网格右下角）首颗按钮的焦点器：网格末行 ↓ / 主演 ↑ 都经过它 */
    val pageFocusRequester = remember { FocusRequester() }
    /** 视频信息面板（整页最后一站）：主演 ↓ 落这里，面板 ↑ 回主演 */
    val mediaInfoFocusRequester = remember { FocusRequester() }
    // 滚动状态提到抢焦之前建：入口抢焦要在焦点落定后把首屏构图压回顶部
    val scrollState = rememberScrollState()
    // 离开这一页（进播放器被系统回收 / 导航走）时记下竖滚位置，返回重建时接回那一屏。
    // 进程级记忆穿得过 Activity 重建；onDispose 在旧树销毁时跑，此刻 scrollState 还是离开那一屏。
    DisposableEffect(mediaId) {
        onDispose { detailScrollMemory[mediaId] = scrollState.value }
    }

    /**
     * 离开时焦点站在哪个元件上（播放 / 收藏 / 控制条 / 某一集），回来就还站在它上面。
     * 详情页的子树在进播放器时不销毁，所以这份记录只要活过一次组合就够了，
     * 不用像首页那样按页面 key 持久化。
     */
    val stand = remember { StandTarget() }
    /**
     * 播放键是不是真的拿到过焦点（首屏那一次落点是不是站上了）。
     * 只由播放键自己的焦点上报翻真，见 [publishStand]。
     */
    var playLanded by remember(mediaId) { mutableStateOf(false) }
    /** 谁拿到焦点谁就把自己交给 [stand]；一个节点只交它自己已经挂着的那个 requester */
    fun publishStand(target: FocusRequester): Modifier =
        Modifier.onFocusChanged {
            if (it.hasFocus) {
                stand.requester = target
                if (target === playFocusRequester) playLanded = true
            }
        }

    /** 界面每次回到前台（进出播放器）都要重新站好焦点，见 [tvResumeTick] */
    val resumeTick = tvResumeTick()

    val scope = rememberCoroutineScope()

    // 首帧就用「上次算好的主色 / 内存缓存 / 标题哈希色」，避免进入瞬间一片黑
    // 同手机端：mediaOf() 是 suspend，组合期不能调。这里单条订阅这一条媒体，
    // 不订阅整库（整表 Flow 每次失效都要全表重读进内存，而详情页只要这颗种子色）；
    // 库数据晚到时 flow 补发，remember 的 key 挂到标题上，初值自动补正确颜色。
    val seedMedia by remember(mediaId) { vm.seedMediaFlow(mediaId) }
        .collectAsStateWithLifecycle(initialValue = null)
    val dominantState = remember(mediaId, seedMedia?.title) {
        mutableStateOf(seedDominantColor(seedMedia))
    }
    var dominant by dominantState

    /**
     * 已经刮过的片子先把图抢上屏。
     *
     * 完整那次刮削挂在下面加载链的最末端（要等分集、详情那几段跑完），而背景图的取值是
     * 「刮削图优先、条目自有图兜底」，于是第二次及以后进同一部片，仍然会先上一帧源站老图 ——
     * 数据其实早就躺在 scraped_meta 表里，一次网都没联。这里只读那张表，命中就直接用，
     * 没命中什么也不做，仍由下面那条链负责（首次联网刮的行为一个字没改）。
     * 分集行是「继续观看」进来那条：标题和类型都挂在剧集壳上，键算出来对不上，不去猜。
     */
    LaunchedEffect(mediaId, seedMedia?.title, seedMedia?.year, seedMedia?.kind) {
        val seed = seedMedia ?: return@LaunchedEffect
        if (seed.kind == MediaKind.EPISODE) return@LaunchedEffect
        if (tmdb == null) tmdb = vm.tmdbCached(seed.title, seed.year, seed.kind, seed.genres)
    }

    LaunchedEffect(mediaId) {
        // 聚合网盘分类入口检测：只有明确等于网盘类型名的 vodId 才跳 CategoryList
        val CLOUD_TYPES = setOf("quark", "xunlei", "aliyundrive", "ucwangpan", "douyin", "baidu", "weiyun", "onedrive", "aliyunpan", "pikpak", "115pan", "123pan")
        val probe = vm.mediaOf(mediaId)
        if (probe != null) {
            val parts = probe.remoteId.removePrefix("tvbox:").split(":")
            if (parts.size >= 3) {
                val sourceId = parts.getOrNull(0)?.toLongOrNull()
                val siteKey = parts.getOrNull(1).orEmpty()
                val cleanVodId = parts.last()
                val isCategory = cleanVodId.lowercase() in CLOUD_TYPES &&
                    sourceId != null && onCategoryVodId != null
                if (isCategory) {
                    onCategoryVodId(sourceId, siteKey, cleanVodId, cleanVodId.uppercase())
                    return@LaunchedEffect
                }
            }
        }
        // 加载链本体在文件下方的 [tvDetailLoadChain]：原来内联在这里，
        // Compose 编译器会把这段并回宿主方法，是 TvDetailScreen 方法超限的头号来源。
        tvDetailLoadChain(
            vm, mediaId,
            TvDetailLoadSink(
                mediaState, isEmbySourceState, resumeEpisodeState, scrapedState,
                playSourcesState, selectedPlaySourceState, episodesState,
                selectedSeasonState, epPageState, recallEpisodeIdState,
                recallScrollTargetState, restoredStandState, tmdbState, loadingState
            )
        )
    }

    // 播放器「设片头」写的是壳行，而正常进出播放器这棵子树不销毁、上面那条加载链不会重跑，
    // 胶囊会停在进页时的旧值。回前台时把当前展示的这一行重读一次。
    LaunchedEffect(resumeTick) {
        if (resumeTick == 0) return@LaunchedEffect
        val id = media?.id ?: return@LaunchedEffect
        vm.mediaOf(id)?.let { media = it }
    }

    /**
     * 加载态两段：骨架晚 250ms 才出现；内容到位后，挂过骨架才 200ms 淡入，没挂过直接上屏。
     *
     * 为什么要晚这 250ms：刮削表命中的片子整条链路只有几十毫秒，骨架立刻挂上就是
     * 「白框架闪一下」，比原来干等更晃眼；真正慢的是 TVBox 拉分集和 TMDB 联网那两类，
     * 它们超过 250ms 才值得给一个「正在加载」的样子。
     * 淡入只在加载结束这一刻走一次，不参与滚动，所以和当初为滚动性能关掉的淡入动画不冲突。
     */
    val skeletonArmedState = remember(mediaId) { mutableStateOf(false) }
    var skeletonArmed by skeletonArmedState
    val reveal = remember(mediaId) { Animatable(1f) }
    LaunchedEffect(loading) { tvPlaySkeletonReveal(loading, skeletonArmedState, reveal) }

    // 取色：与手机端一致，用 Coil ImageLoader 直接解码 bitmap，失败回退标题哈希色
    val tvContext = LocalContext.current
    // 依赖里带上 tmdb 的两个图：刮削完成后海报换了，主色要跟着重算
    LaunchedEffect(media?.posterUrl, media?.backdropUrl, tmdb?.posterUrl, tmdb?.backdropUrl) {
        val m = media ?: return@LaunchedEffect
        // 解码/写库本体在 [tvResolveDominantColor]，同样为方法体积外移
        tvResolveDominantColor(tvContext, m, tmdb, dominantState)
    }

    /**
     * 进页与「界面每次回到前台」都要把焦点站好（规范 R7）：
     * 离开时站在某一集 / 控制条上，从播放器回来就还站在那里；这一页第一次进来（没有落点记录）
     * 才落到播放键，进页即可直接按 OK 播放。
     * key 只带「数据到位与否 + resume 次数」：刮削回填会换掉 media 实例，
     * 但 id 不变，所以不会把用户已经站好的焦点从选集抢走（等价于原来的 initialFocusDone 只落一次）。
     */
    // 第一次进这一页：焦点站播放键（首屏大图构图），不读上次离开时的落点 ——
    // 读了会把焦点直接交到下面的选集/控制条，bring-into-view 顺势把详情滚出来，首屏大图就没了。
    // stand 是本组合私有的：第一次进来还没人拿过焦点，它必然是空的，天然就落到播放键；
    // 从播放器回来（子树重建）时 recallEpisodeId 会在组合期把那一格交给 stand，于是还站在那一集。
    // 不再另立 entered 标记：LaunchedEffect(Unit) 和抢焦效果同一帧启动，抢焦还在 delay 里
    // 标记就已经翻真，读出来的永远是「已进过」，等于没有这道闸。
    //
    // tick 用「内容有没有真的组合出来」当信号，而不是「loading 结束了没有」：
    // loading=false 那一刻内容才刚进组合，抢焦效果同一帧启动时播放键可能还没挂上，
    // requestFocus 静默失败、之后再没有第二次机会（于是焦点干脆没落点）。
    // 内容组合出来了（骨架已撤、条目已到）才发一次 tick，抢焦必然命中。
    val contentReady = media != null && !(loading && skeletonArmed)
    // 第一落点 vs 之后：播放键真正拿到过焦点（[playLanded]）之前，每一轮抢焦都只认播放键。
    // 不能只判「这一页跑过第一轮没有」：tick 每变一次，上一轮就被取消重开，
    // 真机日志里进一次页跑了三轮，前两轮还在骨架阶段（ready=false），播放键根本没挂上，
    // 标记却被第二轮消费掉了 —— 第三轮改按 stand 里那个先拿到焦点的元件跑，
    // bring-into-view 一滚，简介就摊在眼前（stand 会被抢焦以外的焦点上报填上）。
    // 这一轮的落点判据（抢焦和「压回顶部」共用一份，两边不能各算各的）
    fun claimTarget(): FocusRequester = when {
        // 播放器把宿主 Activity 打断后重建回来的：接回刚才那一集，stand 说了算
        restoredStand -> stand.requester ?: playFocusRequester
        // 首屏那一次还没站上播放键：换源重绑、进程级老记录、别人先抢到的焦点都不认
        !playLanded -> playFocusRequester
        else -> stand.requester ?: playFocusRequester
    }
    tvClaimFocusOnEnter(
        tick = listOf(resumeTick, if (contentReady) media?.id else null),
        anchorsProvider = { listOf(claimTarget()) },
        settleMillis = 120,
        // 播放键排在首屏 55% 占位之下，1.9 的 requestFocus 会把它「滚到可见」，顺势把简介摊出来。
        // 只有这一轮确实往播放键上落才把页面压回顶部；从播放器回来落在某一集上时不能滚，
        // 否则那一集又被滚出视口。
        keepTop = true,
        scrollToTop = {
            val t = claimTarget()
            if (t === playFocusRequester) scrollState.scrollTo(0)
        }
    )

    // 返回重建：焦点落定前把竖滚 instant 接回离开那一屏（货架），目标已在视口，
    // bring-into-view 不再补滚 —— 消掉「先在顶部露一下再滚下去」那一下闪。
    LaunchedEffect(contentReady, restoredStand) {
        if (!contentReady || !restoredStand) return@LaunchedEffect
        val saved = detailScrollMemory[mediaId] ?: return@LaunchedEffect
        if (saved <= 0) return@LaunchedEffect
        withFrameNanos { }
        scrollState.scrollTo(saved)
    }

    val item = media

    // 主演名单源数据：Emby 刮削优先、TMDB 补齐，最后才回落到源自己给的 vod_actor。
    val sourceActorNames = splitCastNames(item?.actors.orEmpty())
    /**
     * 主演头像行：Emby / TMDB 给演员照片就铺照片头像；
     * TVBox（CatVod 协议只有姓名）没有照片，用姓名首字的圆形占位头像顶上，
     * 与手机端详情页主演行同口径 —— 不再退回纯文字行。
     */
    val tvCast = remember(scraped, tmdb, sourceActorNames) {
        computeTvCast(scraped, tmdb, sourceActorNames)
    }
    val hasCast = tvCast.isNotEmpty()

    // ---------- 分集：按季过滤 + 按页切块 ----------
    val tvSeasons = remember(episodes) { episodes.map { it.season }.distinct().sorted() }
    val seasonEpisodes = remember(episodes, selectedSeason, tvSeasons) {
        if (tvSeasons.size <= 1) episodes else episodes.filter { it.season == selectedSeason }
    }
    // Emby 走横向货架、每页 20；TVBox 仍是 4 列网格、每页 40
    val epsPerPage = if (isEmbySource) TV_EMBY_EPS_PAGE else TV_EPS_PAGE
    val totalPages = remember(seasonEpisodes, epsPerPage) {
        ((seasonEpisodes.size + epsPerPage - 1) / epsPerPage).coerceAtLeast(1)
    }
    // 换季时页码可能越界，这里钳一下，避免出现空白页
    val pageIndex = epPage.coerceIn(0, totalPages - 1)
    val pagedEpisodes = remember(seasonEpisodes, pageIndex, epsPerPage) {
        seasonEpisodes.drop(pageIndex * epsPerPage).take(epsPerPage)
    }
    val epRows = remember(pagedEpisodes) { pagedEpisodes.chunked(TV_GRID_COLS) }
    // Emby 横滑货架的滚动状态：翻页 / 换季把货架收回头一格，
    // 保证 firstEpFocusRequester（控制条下键的落点）始终挂在已组合的节点上。
    val embyRowState = rememberLazyListState()
    // 翻页 / 换季：货架收回头一格，保证 firstEpFocusRequester（控制条下键的落点）挂在已组合节点上。
    // 有 recall 目标时跳过——那一格由下面专用效果负责，这里抢滚回 0 会把它甩出横滑视口、组合不出来。
    LaunchedEffect(pageIndex, selectedSeason, isEmbySource) {
        if (isEmbySource && recallScrollTarget < 0) {
            embyRowState.scrollToItem(0)
            // 换季 / 翻页后旧那集的节点已回收，落点记录作废，回到首格
            epStand.requester = null
        }
    }
    // 返回重建 / 继续观看：只要 recall 记下了页内序号就滚到它。key 带上 recallScrollTarget 本身——
    // 那一集在第 0 页时 epPage 不变，旧的「只 key pageIndex」写法不会重跑，目标格永远组合不出来，
    // stand 拿不到焦点就退回播放键（真机时好时坏的根因）。滚一次即消费。
    LaunchedEffect(recallScrollTarget, isEmbySource) {
        if (isEmbySource && recallScrollTarget >= 0) {
            val target = recallScrollTarget
            embyRowState.scrollToItem(target)
            recallScrollTarget = -1
        }
    }
    /** 焦点停在货架哪一集：下方视频信息面板跟着它走；离开货架保留最后一集（UI 树里写，这里读） */
    val focusedEpIdState = remember(mediaId) { mutableStateOf(0L) }
    var focusedEpId by focusedEpIdState
    // 面板跟焦：优先焦点所在集，其次接址那一集，最后本页首集；
    // Emby 电影没有分集，壳行本身就是可播实体，影片信息挂在它上面
    val infoEpisode = pagedEpisodes.firstOrNull { it.id == focusedEpId }
        ?: pagedEpisodes.firstOrNull { it.id == recallEpisodeId }
        ?: pagedEpisodes.firstOrNull()
        ?: media?.takeIf { isEmbySource && it.kind != MediaKind.SERIES }
    // 订阅后台补填信号：缓存填好递增 tick → 本屏重组 → 下面重读缓存，面板随即出现
    vm.embyMediaInfoTick
    val episodeMediaInfo = if (isEmbySource) infoEpisode?.let { vm.embyMediaInfoOf(it.remoteId) } else null
    val hasMediaInfoPanel = episodeMediaInfo != null && infoEpisode != null
    /**
     * 徽章取值：电影取该电影那个文件，剧集取当前季第一集那个文件。
     *
     * 剧集页上的当前条目是剧集壳，壳行没有画质字段（TVBox 只在入库那一刻把推测值写在壳上，
     * 分集守卫还会让老条目错过这次推测；Emby 的壳行从头到尾没有轨道字段）——
     * 直接用壳，徽章行会整行空掉。取值规则见 [badgeInfoFor]。
     */
    val badgeSource = if (item?.kind == MediaKind.SERIES) {
        seasonEpisodes.firstOrNull() ?: episodes.firstOrNull()
    } else item
    val badgeMediaInfo = badgeSource?.let { vm.embyMediaInfoOf(it.remoteId) }
    val badgeInfo = badgeInfoFor(
        isEmbySource = isEmbySource,
        item = item,
        badgeSource = badgeSource,
        scraped = scraped,
        mediaInfo = badgeMediaInfo
    )
    val hasEpisodes = episodes.isNotEmpty()
    /** 「跳过片头」胶囊：剧集壳 + 全局开关开启才出现在控制条末尾 */
    val skipPillVisible = media?.let {
        Prefs.skipIntroEnabled && it.kind == MediaKind.SERIES && it.seriesKey.isNotBlank()
    } == true
    /** 翻页行：多页时独立成一行，排在剧集网格右下角、主演之上。
     *  位置固定，不随每页集数浮动，遥控落点有肌肉记忆。 */
    val hasPageRow = totalPages > 1
    /** 选集控制条只有在「多源」「多季」或「有跳过片头胶囊」时才存在：
     *  它是网格首行上键的落点之一，所以条件必须和上面控制条的显示条件完全一致，
     *  否则按钮会变成按不到的孤立节点。
     *  controlsFocusRequester 按「源首颗 → 季首颗 → 跳过片头胶囊」的顺序只挂一颗。 */
    val hasControlAnchor = playSources.size > 1 || tvSeasons.size > 1 || skipPillVisible

    // 换季 / 换源 / 翻页会换掉整组卡片，但按键落点在控制条上，控制条本身不随内容变化消失，
    // 焦点因此不会掉 —— 这里刻意不再做任何抢焦。
    // 原来的 restoreGridFocus 会在每次切换后把焦点抢到第一集，
    // 表现就是「切完云盘，焦点自己跑到剧集上了」（规范 R4：离开哪一层就留在哪一层）。

    // rebindSource（换源重绑）只被下方 UI 树调用，已随 Box 树一并搬进 [TvDetailScreenBody]。

    val tones = remember(dominant) {
        if (dominant == Color.Unspecified) {
            // 详情页是海报基调的恒暗页（与手机端同一策略）：没拿到主色时兜底也钉死近黑。
            // 不能回落 AppColors.Bg —— 浅色模式下那是亮底，整页白字全部打在亮底上读不出来。
            Color(0xFF080808) to Color(0xFF040404)
        } else {
            pageTonesOf(dominant)
        }
    }
    val top by animateColorAsState(tones.first, tween(Motion.SLOW), label = "tv_top")
    val bottom by animateColorAsState(tones.second, tween(Motion.SLOW), label = "tv_bottom")

    val bgBrush = remember(top, bottom) {
        Brush.verticalGradient(
            0f to top,
            0.34f to top,
            0.72f to lerp(top, bottom, 0.72f),
            1f to bottom
        )
    }
    // 头图 → 填充色的溶解过渡
    val headerScrim = remember(top) {
        Brush.verticalGradient(
            0f to Color.Black.copy(alpha = 0.30f),
            0.28f to Color.Transparent,
            0.55f to top.copy(alpha = 0.34f),
            0.80f to top.copy(alpha = 0.82f),
            1f to top
        )
    }

    // 图一律「刮来的优先」，与手机端同一口径：源站那张常常是豆瓣缩略图，
    // Emby 公共片库的图也一样糙；刮不到时（字段为空）自然回落到条目自己的图。
    val backdrop = tmdb?.backdropUrl.orEmpty().ifBlank {
        item?.let { it.backdropUrl.ifBlank { it.posterUrl } }.orEmpty()
    }
    val logo = tmdb?.logoUrl.orEmpty().ifBlank { item?.logoUrl.orEmpty() }

    // 导演：TMDB 优先，其次 Emby 刮削，最后源自己给的名字（画法在 [TvDetailDescription]）
    val directors = (tmdb?.directors?.takeIf { it.isNotEmpty() }?.map { it.name }
        ?: scraped?.directors?.map { it.name }
        ?: splitCastNames(item?.directors.orEmpty()).take(3))
        ?.joinToString(" / ").orEmpty()

    TvDetailScreenBody(
        vm = vm,
        sink = TvDetailBodySink(
            mediaState, episodesState, resumeEpisodeState,
            selectedSeasonState, epPageState, recallEpisodeIdState,
            selectedPlaySourceState, showSkipDialogState, focusedEpIdState
        ),
        focus = TvDetailBodyFocus(
            favoriteFocusRequester, playFocusRequester, controlsFocusRequester,
            firstEpFocusRequester, pageFocusRequester, mediaInfoFocusRequester,
            epStand, stand
        ),
        ui = TvDetailBodyUi(
            loading = loading,
            skeletonArmed = skeletonArmed,
            reveal = reveal,
            bgBrush = bgBrush,
            backdrop = backdrop,
            headerScrim = headerScrim,
            logo = logo,
            directors = directors,
            scraped = scraped,
            tmdb = tmdb,
            playSources = playSources,
            badgeInfo = badgeInfo,
            tvCast = tvCast,
            isEmbySource = isEmbySource,
            tvSeasons = tvSeasons,
            seasonEpisodes = seasonEpisodes,
            pagedEpisodes = pagedEpisodes,
            epRows = epRows,
            pageIndex = pageIndex,
            totalPages = totalPages,
            embyRowState = embyRowState,
            skipPillVisible = skipPillVisible,
            hasEpisodes = hasEpisodes,
            hasControlAnchor = hasControlAnchor,
            hasCast = hasCast,
            hasMediaInfoPanel = hasMediaInfoPanel,
            hasPageRow = hasPageRow,
            episodeMediaInfo = episodeMediaInfo,
            infoEpisode = infoEpisode
        ),
        scrollState = scrollState,
        scope = scope,
        publishStand = { target -> publishStand(target) },
        onPlay = onPlay
    )
}

/**
 * [TvDetailScreenBody] 要读写的可变状态收口容器（同 [TvDetailLoadSink] 的路子）：
 * 主函数保留各状态的 remember，Body 拆包成同名委托变量，下方 Box 树因此逐行未动。
 */
private class TvDetailBodySink(
    val media: MutableState<MediaEntity?>,
    val episodes: MutableState<List<MediaEntity>>,
    val resumeEpisode: MutableState<MediaEntity?>,
    val selectedSeason: MutableState<Int>,
    val epPage: MutableState<Int>,
    val recallEpisodeId: MutableState<Long>,
    val selectedPlaySource: MutableState<String>,
    val showSkipDialog: MutableState<Boolean>,
    val focusedEpId: MutableState<Long>
)

/** 页面焦点锚点与落点：纯对象引用，Body 只把它们转交给各区块接线用 */
private class TvDetailBodyFocus(
    val favorite: FocusRequester,
    val play: FocusRequester,
    val controls: FocusRequester,
    val firstEp: FocusRequester,
    val page: FocusRequester,
    val mediaInfo: FocusRequester,
    val epStand: StandTarget,
    val stand: StandTarget
)

/** Body 用到的只读派生值：全部在主函数组合期算好传入，Body 不再重算 */
private class TvDetailBodyUi(
    val loading: Boolean,
    val skeletonArmed: Boolean,
    val reveal: Animatable<Float, AnimationVector1D>,
    val bgBrush: Brush,
    val backdrop: String,
    val headerScrim: Brush,
    val logo: String,
    val directors: String,
    val scraped: EmbyClient.ItemDetail?,
    val tmdb: com.chen.doudoutv.data.tmdb.TmdbClient.ScrapeResult?,
    val playSources: List<String>,
    val badgeInfo: FormatProbe.Info,
    val tvCast: List<TvCast>,
    val isEmbySource: Boolean,
    val tvSeasons: List<Int>,
    val seasonEpisodes: List<MediaEntity>,
    val pagedEpisodes: List<MediaEntity>,
    val epRows: List<List<MediaEntity>>,
    val pageIndex: Int,
    val totalPages: Int,
    val embyRowState: LazyListState,
    val skipPillVisible: Boolean,
    val hasEpisodes: Boolean,
    val hasControlAnchor: Boolean,
    val hasCast: Boolean,
    val hasMediaInfoPanel: Boolean,
    val hasPageRow: Boolean,
    val episodeMediaInfo: EmbyClient.MediaInfo?,
    val infoEpisode: MediaEntity?
)

/**
 * 详情页整棵 UI 树（原来内联在 [TvDetailScreen] 里的那个 Box）。
 *
 * 为方法体积外移（见文件下方 #41 背景）：主函数只留状态声明、加载链与派生计算，
 * 寄存器数回到 ART 校验器雷区之下；本函数允许大 —— 它不 remember 任何状态、
 * 没有内联的 LaunchedEffect，捕获的局部引用都改成了参数，寄存器数远低于原宿主方法。
 * 纯搬移：Box 树逐行未动，逻辑与焦点接线一概不变。
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
private fun TvDetailScreenBody(
    vm: LibraryViewModel,
    sink: TvDetailBodySink,
    focus: TvDetailBodyFocus,
    ui: TvDetailBodyUi,
    scrollState: ScrollState,
    scope: CoroutineScope,
    publishStand: (FocusRequester) -> Modifier,
    onPlay: (Long) -> Unit
) {
    // 容器拆包回原主函数的局部同名变量，下面整段 Box 树因此一个字都不用改
    var media by sink.media
    var episodes by sink.episodes
    var resumeEpisode by sink.resumeEpisode
    var selectedSeason by sink.selectedSeason
    var epPage by sink.epPage
    var recallEpisodeId by sink.recallEpisodeId
    var selectedPlaySource by sink.selectedPlaySource
    var showSkipDialog by sink.showSkipDialog
    var focusedEpId by sink.focusedEpId
    val item = media
    val loading = ui.loading
    val skeletonArmed = ui.skeletonArmed
    val reveal = ui.reveal
    val bgBrush = ui.bgBrush
    val backdrop = ui.backdrop
    val headerScrim = ui.headerScrim
    val logo = ui.logo
    val directors = ui.directors
    val scraped = ui.scraped
    val tmdb = ui.tmdb
    val playSources = ui.playSources
    val badgeInfo = ui.badgeInfo
    val tvCast = ui.tvCast
    val isEmbySource = ui.isEmbySource
    val tvSeasons = ui.tvSeasons
    val seasonEpisodes = ui.seasonEpisodes
    val pagedEpisodes = ui.pagedEpisodes
    val epRows = ui.epRows
    val pageIndex = ui.pageIndex
    val totalPages = ui.totalPages
    val embyRowState = ui.embyRowState
    val skipPillVisible = ui.skipPillVisible
    val hasEpisodes = ui.hasEpisodes
    val hasControlAnchor = ui.hasControlAnchor
    val hasCast = ui.hasCast
    val hasMediaInfoPanel = ui.hasMediaInfoPanel
    val hasPageRow = ui.hasPageRow
    val episodeMediaInfo = ui.episodeMediaInfo
    val infoEpisode = ui.infoEpisode
    val favoriteFocusRequester = focus.favorite
    val playFocusRequester = focus.play
    val controlsFocusRequester = focus.controls
    val firstEpFocusRequester = focus.firstEp
    val pageFocusRequester = focus.page
    val mediaInfoFocusRequester = focus.mediaInfo
    val epStand = focus.epStand
    val stand = focus.stand

    /**
     * 换播放源（网盘 / 线路）之后，把这一页接回用户原来那集。
     *
     * 新线路的分集是另一批行、另一批 id：旧行要是继续留在 resumeEpisode 里，
     * 主按钮「继续观看」就会播回上一条网盘 —— 换源后进度看着还在，按下去却来自另一个源。
     * 找不到同一集时宁可清掉续播锚点、回到第一页，也不能拿着旧源的行往下走。
     */
    fun rebindSource(next: List<MediaEntity>, anchor: MediaEntity?) {
        val same = matchEpisodeInSource(anchor, next)
        episodes = next
        // 续播锚点只在「这页本来就是靠它开的」时跟着换源重绑；
        // 新线路没有同一集就清掉，不能让主按钮拿着旧网盘的行去播。
        if (resumeEpisode != null) resumeEpisode = same
        val seasons = next.map { it.season }.distinct()
        if (seasons.size > 1) {
            selectedSeason = when {
                same != null -> same.season
                selectedSeason in seasons -> selectedSeason
                else -> seasons.first()
            }
        }
        val inSeason =
            if (seasons.size <= 1) next else next.filter { it.season == selectedSeason }
        val pos = same?.let { s -> inSeason.indexOfFirst { it.id == s.id } } ?: -1
        epPage = if (pos >= 0) pos / TV_EPS_PAGE else 0
        recallEpisodeId = same?.id ?: 0L
    }

    // 本页是恒暗页：页上共享组件（TvPillButton / TvIconButton / TvSectionTitle / TvMetaRow /
    // TvEmptyState）都按暗页口径取色，不随颜色模式翻转。跳过片头弹窗是模式色面板，局部豁免。
    CompositionLocalProvider(LocalTvPageDark provides true) {
        Box(
            Modifier
                .fillMaxSize()
                .background(bgBrush)
        ) {
        // 背景横版图铺满整个页面，向下溶解到主色填充（画法见 [TvDetailBackdropScrim]）。
        TvDetailBackdropScrim(
            backdrop = backdrop,
            title = item?.title,
            headerScrim = headerScrim
        )

        val screenH = LocalConfiguration.current.screenHeightDp.dp
        val contentAnchor = remember { BringIntoViewRequester() }

        /**
         * 一次下键的完整转场：焦点交给 [target]，滚动只走 requestFocus 自带的那一次
         * bring-into-view（最小滚动到落点可见）。落定后简介与选集首行天然同屏。
         *
         * 以前这里先 contentAnchor.bringIntoView() 把内容顶对齐视口、再靠焦点补滚：
         * 同一次按键滚两段动画（真机轨迹 0→694→1099），观感就是画面跳两次。
         * 对齐那一步是多余的 —— 最小滚动落定后内容顶本来就在视口里。
         */
        /** 进货架的活落点：右移后首格可能被 LazyRow 回收，优先最后聚焦那集（停在当前滚动位置、必活） */
        fun shelfEntry(): FocusRequester =
            if (isEmbySource) (epStand.requester ?: firstEpFocusRequester) else firstEpFocusRequester

        fun enterEpisodeSection(target: FocusRequester) {
            // 控制条这次没渲染出来（单源单季单页）时退回货架活落点
            tvMoveFocus(target, shelfEntry())
        }

        /**
         * 简介文本：优先 TMDB，其次刮削到的 tagline，最后源自己给的 overview。
         * 数据源（TVBox / Emby）常带 `<span>`、`<br>` 之类的 HTML 片段，统一清成纯文本。
         *
         * 定义在焦点链之前，是因为「有没有这颗展开按钮」要参与决定下键的落点。
         */
        val overview = (
            item?.let { media ->
                tmdb?.overview?.takeIf { it.isNotBlank() }
                    ?: scraped?.tagline?.takeIf { it.isNotBlank() }
                    ?: media.overview
            }.orEmpty()
            ).let { HtmlText.strip(it) }
        var descOverflow by remember(item?.id, overview) { mutableStateOf(false) }
        val descFocusRequester = remember { FocusRequester() }
        var descExpanded by remember(item?.id) { mutableStateOf(false) }
        val descTruncated = descExpanded || descOverflow
        // 每颗头像一个焦点站：requester 由本页先建好再交给头像行，
        // 「播放键 ↓ 落到谁」要在这里就能算出来，不能让组件自己造。
        val castFocusRequesters = remember(tvCast) { List(tvCast.size) { FocusRequester() } }
        /**
         * 网格末行最后一格的焦点器：主演挪到选集下面之后，它的上键要落回这一格。
         * 用状态存，网格换季/翻页换了节点时头像行会跟着重组，不会攥着旧节点不放。
         */
        val lastCellRequester = remember { mutableStateOf<FocusRequester?>(null) }

        /**
         * 播放区按下 ↓ 的落点。展开按钮排在简介正下方，所以有长简介时它是第一站；
         * 之后才是选集链（控制条 > 首格），电影没有分集就落到主演头像行。
         *
         * 电影 / Emby 单条内容没有分集：这一层原来一个可聚焦元件都没有，下键只能 Cancel ——
         * 焦点走不动，滚动也就不发生，完整简介和主演永远看不到（规范 R1/R4：一层一层走，不许跨层抢）。
         */
        val belowPlay: FocusRequester = when {
            descTruncated -> descFocusRequester
            hasEpisodes && hasControlAnchor -> controlsFocusRequester
            hasEpisodes -> firstEpFocusRequester
            hasCast -> castFocusRequesters.first()
            hasMediaInfoPanel -> mediaInfoFocusRequester
            else -> FocusRequester.Cancel
        }
        /**
         * 展开按钮的下一站：往下是选集链（控制条 > 网格），再往下才是主演头像行；
         * 电影没有分集时直接落到主演，这就是整页最后一站。
         */
        val belowDesc: FocusRequester = when {
            hasEpisodes && hasControlAnchor -> controlsFocusRequester
            hasEpisodes -> firstEpFocusRequester
            hasCast -> castFocusRequesters.first()
            hasMediaInfoPanel -> mediaInfoFocusRequester
            else -> FocusRequester.Cancel
        }
        /**
         * 头像行的上一站：它现在排在选集网格下面，正上方是网格末行最后一格。
         * 网格还没组合出来时（首帧）退回控制条，不能指向没挂载的节点。
         */
        fun castUpTarget(): FocusRequester = when {
            hasPageRow -> pageFocusRequester
            hasEpisodes && isEmbySource -> shelfEntry()
            hasEpisodes -> lastCellRequester.value ?: firstEpFocusRequester
            hasControlAnchor -> controlsFocusRequester
            descTruncated -> descFocusRequester
            else -> playFocusRequester
        }
        /** 头像行的下一站：有视频信息面板时它是整页最后一站，没有则主演收尾 */
        val castDownTarget: FocusRequester =
            if (hasMediaInfoPanel) mediaInfoFocusRequester else FocusRequester.Cancel
        /** 展开按钮的上一站：它紧贴简介，往上就是播放键 */
        val descUpTarget: FocusRequester = playFocusRequester

        /**
         * 操作按钮的下键进选集区 / 展开按钮 / 主演头像行：
         * 只读的简介、导演、署名不接收焦点，所以不会出现「按几次才滚到剧集」。
         * 下键：先把内容块滚到顶部再交给落点（默认控制条首颗）；预览阶段消费，
         * 避免默认焦点搜索先一步把视口滚到「只露出卡片」的位置。
         * 一站都没有（短简介 + 无演员照片 + 无分集）时退化成「把这一屏滚到底」，
         * 至少简介全文和主演文字看得见，而不是按下方向键毫无反应。
         * 首屏按钮共用，见 [TvDetailHeroHeader]。
         */
        val episodeDownModifier = Modifier.onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown || e.key != Key.DirectionDown) {
                false
            } else if (hasEpisodes || descTruncated || hasCast || hasMediaInfoPanel) {
                enterEpisodeSection(belowPlay)
                true
            } else {
                scope.launch {
                    runCatching { scrollState.animateScrollTo(scrollState.maxValue) }
                }
                true
            }
        }

        /** 焦点回到首屏锚点时，构图也要回到首屏（规范 R7）。
         *  只靠 bring-into-view 的最小滚动，标题与大海报会留在视口外，
         *  用户看到的就是「名字顶在屏幕顶上，再按上键也回不到刚进页那个状态」。 */
        fun restoreHeaderComposition() {
            if (scrollState.value > 0) scope.launch { runCatching { scrollState.animateScrollTo(0) } }
        }

        /**
         * 单页滚动。所有内容一次性组合，方向键只移动焦点：
         *   · 首屏 = 大图 + 标题 / 元信息 / 操作按钮；
         *   · 下键把焦点交给第一集，滚动容器随焦点 bring-into-view 滚动，
         *     简介与选集首行因此能同屏出现；
         *   · 没有「整页翻转」动画，也就不会出现旧页退场时内容被卸载、焦点丢失。
         */
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
        ) {
            // 顶部占位：把首屏骨架推到屏幕 55% 处，
            // 落在 headerScrim 主色已较浓的区域，不挡海报主体、沉浸感更强
            Spacer(Modifier.height(screenH * 0.55f))

            if (loading && skeletonArmed) {
                TvDetailSkeleton()
            } else if (item == null) {
                // 还没到 250ms 的加载窗口里什么都不画：背景与暗层已经在下面铺着，
                // 先弹一行「加载中…」再换成骨架是两次跳变
                if (!loading) {
                    TvEmptyState(
                        "找不到该内容",
                        modifier = Modifier.fillMaxWidth().height(420.dp)
                    )
                }
            } else {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer { alpha = reveal.value }
                ) {
                    // ========== 首屏骨架：标题 / 收藏 / 元信息 / 徽章 / 操作 ==========
                    // 只占左 72%：电视是 16:9 横屏，首屏只有标题、元信息、
                    // 徽章和一个主按钮，铺满整宽会变成「左边一列字、右边一大片空」。
                    // 整块画法与焦点接线在 [TvDetailHeroHeader]（为方法体积外移）。
                    TvDetailHeroHeader(
                        vm = vm,
                        item = item,
                        logo = logo,
                        scraped = scraped,
                        episodes = episodes,
                        playSources = playSources,
                        badgeInfo = badgeInfo,
                        tmdbAvailable = tmdb != null,
                        resumeEpisode = resumeEpisode,
                        favoriteFocusRequester = favoriteFocusRequester,
                        playFocusRequester = playFocusRequester,
                        publishStand = { target -> publishStand(target) },
                        restoreHeader = { restoreHeaderComposition() },
                        belowPlay = belowPlay,
                        episodeDownModifier = episodeDownModifier,
                        onToggleFavorite = { next ->
                            vm.toggleFavorite(item.id, next)
                            // media 是进页时一次性读出的快照，库 Flow 的回填不会流到这里，
                            // 不本地回写就是「按了没反应」（同手机端的处理）
                            media = media?.copy(favorite = next)
                        },
                        onChooseEpisodes = {
                            epPage = 0
                            enterEpisodeSection(firstEpFocusRequester)
                        },
                        onPlayId = onPlay
                    )

                    // ========== 内容区：简介 / 导演 / 主演 / 署名 / 选集 ==========
                    // 全部常驻组合，不再有「展开第几层」的条件渲染。
                    // contentAnchor 挂在内容块顶部：下键进入选集时把它对齐到视口顶部。
                    Column(
                        Modifier
                            .bringIntoViewRequester(contentAnchor)
                            .padding(start = TvPagePadding, end = TvPagePadding)
                    ) {
                        // ---------- 简介 / 导演 / 署名：画法在 [TvDetailDescription] ----------
                        TvDetailDescription(
                            overview = overview,
                            directors = directors,
                            showCredit = tmdb != null,
                            descExpanded = descExpanded,
                            descOverflow = descOverflow,
                            onOverflowChange = { descOverflow = it },
                            onToggleExpanded = {
                                descExpanded = !descExpanded
                                // 展开和收起都从简介第一行起读，而不是停在贴着按钮的那一屏
                                scope.launch { runCatching { contentAnchor.bringIntoView() } }
                            },
                            descFocusRequester = descFocusRequester,
                            descUpTarget = descUpTarget,
                            belowDesc = belowDesc,
                            hasEpisodes = hasEpisodes,
                            publishStand = { target -> publishStand(target) },
                            enterEpisodeSection = { target -> enterEpisodeSection(target) }
                        )

                        // ---------- 选集：标题 + 控制条 + 货架/网格 + 翻页行 ----------
                        // 画法与焦点接线在 [TvDetailEpisodesSection]（为方法体积外移）。
                        if (hasEpisodes) {
                            TvDetailEpisodesSection(
                                item = item,
                                episodes = episodes,
                                tvSeasons = tvSeasons,
                                selectedSeason = selectedSeason,
                                onSeasonChange = { sn ->
                                    selectedSeason = sn
                                    epPage = 0
                                },
                                seasonCount = seasonEpisodes.size,
                                pagedEpisodes = pagedEpisodes,
                                epRows = epRows,
                                pageIndex = pageIndex,
                                totalPages = totalPages,
                                onPageChange = { p -> epPage = p },
                                playSources = playSources,
                                selectedPlaySource = selectedPlaySource,
                                onSelectSource = { src ->
                                    // 换源前先记住「用户站在哪一集」：
                                    // 续播的那集 > 回来后要落回的那格 > 当前页第一集
                                    val anchor = resumeEpisode
                                        ?: episodes.firstOrNull { it.id == recallEpisodeId }
                                        ?: pagedEpisodes.firstOrNull()
                                    selectedPlaySource = src
                                    scope.launch {
                                        val item2 = media
                                        if (item2 != null) {
                                            rebindSource(
                                                vm.episodesBySource(item2.seriesKey, src),
                                                anchor
                                            )
                                        }
                                    }
                                },
                                isEmbySource = isEmbySource,
                                embyRowState = embyRowState,
                                skipPillVisible = skipPillVisible,
                                onShowSkipDialog = { showSkipDialog = true },
                                recallEpisodeId = recallEpisodeId,
                                stand = stand,
                                publishStand = { target -> publishStand(target) },
                                controlsFocusRequester = controlsFocusRequester,
                                firstEpFocusRequester = firstEpFocusRequester,
                                pageFocusRequester = pageFocusRequester,
                                lastCellRequester = lastCellRequester,
                                descFocusRequester = descFocusRequester,
                                descTruncated = descTruncated,
                                playFocusRequester = playFocusRequester,
                                shelfEntry = { shelfEntry() },
                                hasControlAnchor = hasControlAnchor,
                                hasPageRow = hasPageRow,
                                belowEpisodesFocusRequester = if (hasCast) castFocusRequesters.first()
                                    else castDownTarget,
                                onFocusedEp = { id, target ->
                                    focusedEpId = id
                                    epStand.requester = target
                                },
                                onPlayEpisode = { id ->
                                    TvNavHistory.playedEpisodeId = id
                                    onPlay(id)
                                }
                            )
                        } else if (!loading && item.kind == MediaKind.SERIES) {
                            // 剧集但分集为空：加载失败时给用户一个明确提示，而不是一块空白
                            TvEmptyState(
                                "暂无分集信息",
                                hint = "数据源可能未返回剧集列表，请稍后重试",
                                modifier = Modifier.fillMaxWidth().height(160.dp)
                            )
                        }

                        // ---------- 主演：排在选集之后 ----------
                        // 原来夹在简介与选集之间，主页选集前总要先路过一排头像；
                        // 挪到选集下面后选集末行按下即落到这里。Emby 有媒体流信息时
                        // 再往下还有一站视频信息面板，没有则主演收尾。
                        if (hasCast) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "主演",
                                color = PageMuted,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(6.dp))
                            TvCastRow(
                                cast = tvCast,
                                requesters = castFocusRequesters,
                                focusUp = { castUpTarget() },
                                focusDown = castDownTarget
                            )
                        }

                        // ---------- 视频信息：Emby 分集媒体流参数，整页最后一层 ----------
                        // 电视没有自由滚动：焦点走不到的内容等于看不到，所以面板自己是一站
                        // （主演 ↓ 落这里），而不是挂在主演下面的只读死区。
                        if (episodeMediaInfo != null && infoEpisode != null) {
                            Spacer(Modifier.height(16.dp))
                            TvEpisodeMediaInfoPanel(
                                info = episodeMediaInfo,
                                episode = infoEpisode,
                                focusRequester = mediaInfoFocusRequester,
                                focusUp = { if (hasCast) castFocusRequesters.first() else castUpTarget() }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(60.dp))
        }
        // 遥控器 Back 键 = TvApp.BackHandler 统一处理，不再显示页面内返回按钮

        // ---------- 「跳过片头」预设弹窗（遥控器焦点） ----------
        val skipShell = media
        if (showSkipDialog && skipShell != null && skipShell.kind == MediaKind.SERIES &&
            skipShell.seriesKey.isNotBlank()
        ) {
            // 弹窗底是模式色 SurfaceHi（浅色=浅面板），里面用 TvText 取色：豁免暗页标记
            CompositionLocalProvider(LocalTvPageDark provides false) {
                TvSkipIntroDialog(
                    currentMs = skipShell.introSkipMs,
                    onDismiss = { showSkipDialog = false },
                    onPick = { ms ->
                        showSkipDialog = false
                        scope.launch {
                            // 写成功后重读壳行，胶囊文案立刻反映新值
                            if (vm.setIntroSkip(skipShell.seriesKey, ms)) {
                                media = vm.mediaOf(skipShell.id) ?: media
                            }
                        }
                    }
                )
            }
        }
        }
    }
}

// =====================================================================
// 下方这一组是【从 TvDetailScreen 拆出来的私有件】。
//
// 背景看 #41：TvDetailScreen 曾是单个约 1360 行的 Compose 函数，编译产物
// 12753 条指令，跨过了 ART 校验器在超大方法上的雷区 —— 类加载时
// VerifyError（[0x15CE] register v260 has type MutableState but expected Float），
// 进详情页必崩，且触发点随每次编译的字节码排布浮动（clean 有时能躲过，
// 下轮增量又崩）。Compose 编译器会把 remember / LaunchedEffect / 内容
// Lambda 的代码并回宿主方法，所以拆法是：加载链等内联块外移成独立
// 函数，UI 区块外移成子 Composable。纯搬移，逻辑与焦点接线一概未动。
// =====================================================================

/**
 * 加载链（[tvDetailLoadChain]）回写状态的收口容器。
 *
 * 不 remember：随组合新建，拿到的是当下这批状态对象（各状态的 remember key
 * 不同，记住容器反而会攥着 key 命中前已经换代的旧状态）。
 */
private class TvDetailLoadSink(
    val media: MutableState<MediaEntity?>,
    val isEmbySource: MutableState<Boolean>,
    val resumeEpisode: MutableState<MediaEntity?>,
    val scraped: MutableState<EmbyClient.ItemDetail?>,
    val playSources: MutableState<List<String>>,
    val selectedPlaySource: MutableState<String>,
    val episodes: MutableState<List<MediaEntity>>,
    val selectedSeason: MutableState<Int>,
    val epPage: MutableState<Int>,
    val recallEpisodeId: MutableState<Long>,
    val recallScrollTarget: MutableState<Int>,
    val restoredStand: MutableState<Boolean>,
    val tmdb: MutableState<com.chen.doudoutv.data.tmdb.TmdbClient.ScrapeResult?>,
    val loading: MutableState<Boolean>
)

/**
 * 详情页加载链：负 id 落库、剧集壳换绑、拉分集/详情、接回上次播放的分集、刮削回填。
 *
 * 整个过程兜底：任何 IO/解析异常都不允许冒泡到 Composition，
 * 否则电视端会直接崩溃退出（用户看到的就是「点详情闪退」）。
 */
private suspend fun tvDetailLoadChain(vm: LibraryViewModel, mediaId: Long, s: TvDetailLoadSink) {
    s.loading.value = true
    try {
        // 首页分类行是内存临时条目（负 id）：先落库换成真实 id，否则 mediaOf() 查不到
        var id = mediaId
        // 「继续观看」进来那条分集行的 id：换绑成剧集壳后焦点也要落回这一集，与播放器返回同口径
        var continueEpisodeId = 0L
        var m = vm.mediaOf(id)
        if (m == null && id < 0) {
            val realId = runCatching { vm.materializeRemote(id) }.getOrNull()
            if (realId != null && realId > 0) {
                id = realId
                m = vm.mediaOf(id)
                // 记下真实 id：这一页要是被爬虫壳的 finish 打断后重建，导航栈里存的还是那个负 id，
                // 换成这里记的真实 id 才还能原样打开（负 id 靠的是内存里的浏览结果，重建后已没了）。
                TvNavHistory.resolvedDetailId = realId
            }
        }
        if (m == null) s.media.value = null
        if (m != null) {
            // 「继续观看」进来的是 EPISODE 行：海报/年份/简介/选集全都挂在剧集壳上，
            // 绑分集行整页几乎是空的。seriesKey 里已经带 sourceId + siteKey + vodId，
            // 查到的壳就是这条记录所属那个数据源的整部剧，不存在跨源歧义，直接换绑、不用问。
            // 壳不在库里（换源重入库、老数据）时保持分集行渲染。
            if (m.kind == MediaKind.EPISODE) {
                val shell = vm.shellOf(m.seriesKey)
                if (shell != null) {
                    s.resumeEpisode.value = m.takeIf { it.positionMs > 5_000L }
                    continueEpisodeId = m.id
                    id = shell.id
                    m = shell
                }
            }
            s.media.value = m
            val src = vm.sourceOf(m.sourceId)
            s.isEmbySource.value = src?.kind == SourceKind.EMBY
            if (src?.kind == SourceKind.TVBOX && m.kind == MediaKind.SERIES) {
                val cachedSources = runCatching { vm.playSourcesOf(m.seriesKey) }.getOrDefault(emptyList())
                s.playSources.value = cachedSources
                val cachedSource = s.selectedPlaySource.value.takeIf { it in cachedSources } ?: cachedSources.firstOrNull().orEmpty()
                if (cachedSource.isNotBlank()) s.selectedPlaySource.value = cachedSource
                s.episodes.value = runCatching {
                    if (cachedSource.isNotBlank()) vm.episodesBySource(m.seriesKey, cachedSource)
                    else vm.episodesOf(m.seriesKey)
                }.getOrDefault(emptyList())
            }
            if (src?.kind == SourceKind.EMBY) {
                s.scraped.value = runCatching { vm.embyDetailOf(m.sourceId, m.remoteId) }.getOrNull()
                // 与 TVBox 同口径：库里已有分集就不重拉。返回详情页时系统常把后台 Activity
                // 回收重建（进程还活着，分集/媒体信息都在库和进程内存里），无条件重拉会白等
                // 一两秒网络、骨架再走一遍；直接读本地即可，焦点由下面的 recall 接回那一集。
                if (m.kind == MediaKind.SERIES && vm.episodesOf(m.seriesKey).isEmpty()) {
                    runCatching { vm.loadEmbyEpisodes(m.sourceId, m.remoteId) }
                } else if (m.kind == MediaKind.SERIES) {
                    // 库里已有分集：不阻塞重拉（保返回秒开）。但进程重建后媒体流缓存可能为空、
                    // 主演下方的影片信息面板会消失，后台非阻塞补一次详情把缓存填上。
                    vm.ensureEmbyMediaInfoBackground(m.sourceId, m.remoteId, m.seriesKey)
                } else {
                    // 电影没有分集拉取这条路，媒体流缓存永远不会被顺带写 —— 单独非阻塞补拉一次，
                    // 否则主演下方的信息面板永远取不到缓存、不显示
                    vm.ensureEmbyItemInfoBackground(m.sourceId, m.remoteId)
                }
            } else if (src?.kind == SourceKind.TVBOX && m.kind == MediaKind.SERIES) {
                // 每次进入都请求最新详情；上面已先展示缓存，网络失败时旧分集仍可用。
                runCatching { vm.loadTvBoxDetail(m.sourceId, m.remoteId) }
            }
            val latest = vm.mediaOf(id) ?: m
            s.media.value = latest

            // TVBox 多播放源：读取全部云盘/线路，默认选第一条，按选中源过滤分集；
            // Emby 无源概念，playSources 为空时走全量分集。
            val sources = runCatching { vm.playSourcesOf(latest.seriesKey) }.getOrDefault(emptyList())
            s.playSources.value = sources
            if (sources.isNotEmpty()) {
                if (s.selectedPlaySource.value !in sources) {
                    s.selectedPlaySource.value = sources.first()
                }
                s.episodes.value = runCatching {
                    vm.episodesBySource(latest.seriesKey, s.selectedPlaySource.value)
                }.getOrDefault(emptyList())
            } else {
                s.episodes.value = runCatching { vm.episodesOf(latest.seriesKey) }.getOrDefault(emptyList())
            }
            s.episodes.value.firstOrNull()?.season?.let {
                if (s.selectedSeason.value == 0) s.selectedSeason.value = it
            }

            // 「看完视频回来还站在那一集」：爬虫壳在播放期间 finish 宿主 → MainActivity recreate()，
            // 这一页连同季 / 页码 / 焦点全是一份新组合的默认值。用进程级记下的分集 id 把线路、季、页码接回去，
            // 并把该格的 requester 交给 [stand]，抢焦效果（tvClaimFocusOnEnter）就以它为第一顺位锚点。
            // 正常进出播放器时子树没销毁，这个 LaunchedEffect 不会再跑，落点仍由 stand 原路接住。
            // 落点优先级：播放器返回记的那一集 > 「继续观看」进来那一集 > 没有（落播放键）。
            // 两条入口共用下面这套换源 / 季 / 页码 / 横滑接回，TVBox 与 Emby 行为一致。
            val playedId = TvNavHistory.playedEpisodeId.takeIf { it != 0L } ?: continueEpisodeId
            if (playedId != 0L) {
                val all = runCatching { vm.episodesOf(latest.seriesKey) }.getOrDefault(emptyList())
                val played = all.firstOrNull { it.id == playedId }
                if (played != null) {
                    if (sources.isNotEmpty() && played.playSource.isNotBlank() &&
                        played.playSource in sources && played.playSource != s.selectedPlaySource.value
                    ) {
                        s.selectedPlaySource.value = played.playSource
                        s.episodes.value = runCatching {
                            vm.episodesBySource(latest.seriesKey, played.playSource)
                        }.getOrDefault(s.episodes.value)
                    }
                    val seasons = s.episodes.value.map { it.season }.distinct()
                    if (seasons.size > 1) s.selectedSeason.value = played.season
                    val inSeason =
                        if (seasons.size <= 1) s.episodes.value
                        else s.episodes.value.filter { it.season == played.season }
                    val pos = inSeason.indexOfFirst { it.id == playedId }
                    if (pos >= 0) {
                        val perPage = if (src?.kind == SourceKind.EMBY) TV_EMBY_EPS_PAGE else TV_EPS_PAGE
                        s.epPage.value = pos / perPage
                        // Emby 货架默认滚到第一格，会把这一集甩到视口外、焦点环看不见；
                        // 记下页内序号，下面那条滚动效果改滚到它，焦点才落在刚播那集上。
                        if (src?.kind == SourceKind.EMBY) s.recallScrollTarget.value = pos % perPage
                    }
                    s.recallEpisodeId.value = playedId
                    s.restoredStand.value = true
                }
            }

            // 刮削结果一律问仓储层：它先读 scraped_meta 表，表里没有才轮到「联网刮削」开关
            // 和凭据决定要不要发请求。这里再拦一道开关，会把已经刮到、躺在表里的数据一起藏掉。
            // 只在刮到东西时覆盖：上面那个只读表的抢跑已经摆好了图，这里失败不能把它擦回老封面。
            runCatching {
                vm.tmdbScrape(latest.title, latest.year, latest.kind, id, latest.genres)
            }.getOrNull()?.let { s.tmdb.value = it }
            // 刮削会把海报/Logo 回填进库，取回最新的再渲染
            vm.mediaOf(id)?.let { s.media.value = it }
        }
    } catch (e: Throwable) {
        e.printStackTrace()
    } finally {
        s.loading.value = false
    }
}

/** 首帧种子色：持久化的取色结果优先，其次内存缓存，最后标题哈希色 */
private fun seedDominantColor(seed: MediaEntity?): Color = seed?.let {
    val persisted = Prefs.detailTone(it.title).takeIf { tone -> tone != 0 }
    if (persisted != null) Color(persisted)
    else FocusColorCache.get(it.posterUrl.ifBlank { it.backdropUrl })
        ?: fallbackColorFromTitle(it.title)
} ?: AppColors.Bg

/**
 * 取色：与手机端一致，用 Coil ImageLoader 直接解码 bitmap，失败回退标题哈希色。
 * 取色要跟页面上真正显示的那张图一致，不能停留在源站那张豆瓣图上。
 */
private suspend fun tvResolveDominantColor(
    context: android.content.Context,
    media: MediaEntity,
    tmdb: com.chen.doudoutv.data.tmdb.TmdbClient.ScrapeResult?,
    dominant: MutableState<Color>
) {
    val scrapedArt = tmdb?.posterUrl.orEmpty().ifBlank { tmdb?.backdropUrl.orEmpty() }
    val url = scrapedArt.ifBlank { media.posterUrl.ifBlank { media.backdropUrl } }
    if (url.isBlank()) {
        dominant.value = fallbackColorFromTitle(media.title)
        return
    }
    val cached = FocusColorCache.get(url)
    if (cached != null) {
        dominant.value = cached
        Prefs.putDetailTone(media.title, cached.toArgb())
        return
    }
    val bmp = withContext(Dispatchers.Default) { loadPosterBitmap(context, url) }
    val color = bmp?.let { runCatching { focusColorOf(it) }.getOrNull() }
        ?: fallbackColorFromTitle(media.title)
    FocusColorCache.put(url, color)
    Prefs.putDetailTone(media.title, color.toArgb())
    dominant.value = color
}

/**
 * 加载态两段：骨架晚 250ms 才出现；内容到位后，挂过骨架才 200ms 淡入，没挂过直接上屏。
 *
 * 为什么要晚这 250ms：刮削表命中的片子整条链路只有几十毫秒，骨架立刻挂上就是
 * 「白框架闪一下」，比原来干等更晃眼；真正慢的是 TVBox 拉分集和 TMDB 联网那两类，
 * 它们超过 250ms 才值得给一个「正在加载」的样子。
 * 骨架没挂过（缓存命中、整条链几十毫秒就完）说明内容直接到位，没有「骨架→内容」
 * 的过渡要平滑，再淡入 200ms 纯属让返回详情页干等 —— 直接 snap 上屏。
 * 淡入只在加载结束这一刻走一次，不参与滚动，所以和当初为滚动性能关掉的淡入动画不冲突。
 */
private suspend fun tvPlaySkeletonReveal(
    loading: Boolean,
    armed: MutableState<Boolean>,
    reveal: Animatable<Float, AnimationVector1D>
) {
    if (loading) {
        delay(250L)
        armed.value = true
    } else {
        val showedSkeleton = armed.value
        armed.value = false
        if (showedSkeleton) {
            reveal.snapTo(0f)
            reveal.animateTo(1f, tween(200))
        } else {
            reveal.snapTo(1f)
        }
    }
}

/**
 * 主演名单：Emby / TMDB 给演员照片就铺照片头像；
 * TVBox（CatVod 协议只有姓名）没有照片，用姓名首字的圆形占位头像顶上，
 * 与手机端详情页主演行同口径 —— 不再退回纯文字行。
 */
private fun computeTvCast(
    scraped: EmbyClient.ItemDetail?,
    tmdb: com.chen.doudoutv.data.tmdb.TmdbClient.ScrapeResult?,
    actorNames: List<String>
): List<TvCast> {
    val fromEmby = scraped?.actors?.takeIf { it.isNotEmpty() }
        ?.map { TvCast(it.name, it.role, it.imageUrl) }
    val fromTmdb = tmdb?.actors?.takeIf { it.isNotEmpty() }
        ?.map { TvCast(it.name, it.role, it.imageUrl) }
    val fromSource = actorNames.map { TvCast(it, "", "") }
    return (fromEmby ?: fromTmdb ?: fromSource)
        .filter { it.name.isNotBlank() }
        .distinctBy { it.name }
        .take(6)
}

/** 背景横版图铺满整个页面，向下溶解到主色填充；文字堆在左半屏，横向再压一层暗保证可读性 */
@Composable
private fun TvDetailBackdropScrim(backdrop: String, title: String?, headerScrim: Brush) {
    if (backdrop.isNotBlank()) {
        AsyncImage(
            model = backdrop,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        // 纵向：顶部 30% 黑 → 中段透明 → 底部完全收进主色
        Box(
            Modifier
                .fillMaxSize()
                .background(headerScrim)
        )
        // 横向左侧加深：文字都堆在左半屏，需要更厚的暗层保证可读性
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = 0.62f),
                        0.45f to Color.Black.copy(alpha = 0.30f),
                        0.75f to Color.Transparent
                    )
                )
        )
    }
}

/**
 * 首屏骨架：标题 / 收藏 / 元信息 / 徽章 / 操作按钮（只占左 72%）。
 * 从 [TvDetailScreen] 原样拆出：焦点接线不变 —— 收藏 ↑ 到顶并回首屏构图，
 * 播放下键走 [episodeDownModifier] 的落点链。
 */
@Composable
private fun TvDetailHeroHeader(
    vm: LibraryViewModel,
    item: MediaEntity,
    logo: String,
    scraped: EmbyClient.ItemDetail?,
    episodes: List<MediaEntity>,
    playSources: List<String>,
    badgeInfo: FormatProbe.Info,
    tmdbAvailable: Boolean,
    resumeEpisode: MediaEntity?,
    favoriteFocusRequester: FocusRequester,
    playFocusRequester: FocusRequester,
    publishStand: (FocusRequester) -> Modifier,
    restoreHeader: () -> Unit,
    belowPlay: FocusRequester,
    episodeDownModifier: Modifier,
    onToggleFavorite: (Boolean) -> Unit,
    onChooseEpisodes: () -> Unit,
    onPlayId: (Long) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth(0.72f)
            .padding(start = TvPagePadding, end = 12.dp)
    ) {
        // 标题位：Logo 与文字标题共用同一块标题槽（[TvMediaTitle]）
        Row(verticalAlignment = Alignment.CenterVertically) {
            TvMediaTitle(
                title = item.title,
                logoUrl = logo,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.width(14.dp))
            // 收藏是焦点链最顶端：↑ 到此为止，同时把构图送回「海报 + 大标题」的首屏态；
            // 下键回播放。
            // focusRequester 必须挂在这里，否则播放按钮的上键是个空引用、按了没反应。
            TvIconButton(
                icon = if (item.favorite) Icons.Filled.Favorite
                else Icons.Filled.FavoriteBorder,
                active = item.favorite,
                contentDescription = if (item.favorite) "取消收藏" else "收藏",
                sizeDp = 40.dp,
                modifier = Modifier
                    .focusRequester(favoriteFocusRequester)
                    // 焦点走到这一层 = 用户已经按回到顶，构图跟着回首屏（规范 R7）
                    .onFocusChanged { if (it.hasFocus) restoreHeader() }
                    .then(publishStand(favoriteFocusRequester))
                    .onPreviewKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionUp) {
                            restoreHeader()
                            true
                        } else {
                            false
                        }
                    }
                    .focusProperties {
                        up = FocusRequester.Cancel
                        down = playFocusRequester
                    },
                onClick = { onToggleFavorite(!item.favorite) }
            )
        }

        // 元信息放在标题与徽章之间，形成层级节奏：
        // 大标题 → 元信息 → 徽章 → 按钮，各档字号都由 TvTypography 决定
        Spacer(Modifier.height(8.dp))
        TvMetaRow(item)
        Spacer(Modifier.height(10.dp))
        // TVBox 源的画质来自详情入库时的文件名推测 + 播放期真实轨道回写；
        // 源自己给的更新状态/集数/线路数照旧保留，两类信息一起排。
        val sourceBadges = buildList {
            val remarks = item.remarks.trim()
            if (remarks.isNotBlank()) add(remarks)
            if (item.kind == MediaKind.SERIES && episodes.isNotEmpty()) {
                add("共 ${episodes.size} 集")
            }
            if (playSources.size > 1) add("${playSources.size} 条线路")
        }
        FormatBadgesRow(
            tmdbEnabled = tmdbAvailable,   // 真拿到 TMDB 数据时，徽章行带上 TMDB 标识
            // 画质取「那个文件」的信息：电影是本片，剧集是第一集（取值见 [badgeInfoFor]）
            resolution = badgeInfo.resolution,
            hdr = badgeInfo.hdr,
            codec = badgeInfo.codec,
            audio = badgeInfo.audio,
            sourceInfo = sourceBadges
        )

        // 操作按钮行：只出一个主按钮，三种内容状态各自对应一个明确动词。
        // 没看过的剧先选集，看过的直接续播。
        // resumeEp 只从「继续观看」进来时有值：那类入口给的是分集行，
        // 页面换成剧集壳后壳自身不带进度，主按钮仍要落回那集才能一键接上。
        val resumeEp = resumeEpisode
        val seriesUnwatched = item.kind == MediaKind.SERIES &&
            item.positionMs <= 5_000L && resumeEp == null
        // 焦点走到操作区 = 用户已经按回顶部，构图同步送回「海报 + 大标题」态
        val restoreHeaderOnFocus = Modifier.onFocusChanged {
            if (it.hasFocus) restoreHeader()
        }
        val playFocusModifier = Modifier
            .focusRequester(playFocusRequester)
            .then(restoreHeaderOnFocus)
            .then(publishStand(playFocusRequester))
            .focusProperties {
                up = favoriteFocusRequester
                down = belowPlay
            }
            .then(episodeDownModifier)
        Spacer(Modifier.height(18.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (seriesUnwatched) {
                // 没看过的剧：不猜「从哪集开始」，让用户自己点。
                // 按下即把焦点送进第一集，页面随焦点滚到选集。
                // 这里刻意落到网格、不落控制条：按钮写的是「选择剧集」，
                // 要换云盘 / 换季从首行按 ↑ 回控制条即可。
                TvPlayButton(
                    text = "选择剧集",
                    size = TvButtonSize.Lg,
                    modifier = playFocusModifier,
                    onClick = onChooseEpisodes
                )
            } else {
                // 看过 / 电影：主按钮直接播放，剧集带上集号
                TvPlayButton(
                    text = run {
                        when {
                            resumeEp != null ->
                                if (resumeEp.usesSourceEpisodeName) "继续观看"
                                else "继续观看 S${resumeEp.season}E${resumeEp.episode}"
                            item.kind == MediaKind.SERIES && item.episode > 0 ->
                                if (item.usesSourceEpisodeName) "继续观看"
                                else "继续观看 S${item.season}E${item.episode}"
                            else ->
                                if (item.positionMs > 5_000L) "继续播放" else "立即播放"
                        }
                    },
                    size = TvButtonSize.Lg,
                    modifier = playFocusModifier,
                    onClick = {
                        val target = resumeEp?.id ?: item.id
                        TvNavHistory.playedEpisodeId = target
                        onPlayId(target)
                    }
                )
                if (item.positionMs > 5_000L || resumeEp != null) {
                    TvPillButton(
                        text = "从头开始",
                        primary = false,
                        // 次操作取中档：比主按钮小一档，但仍是同一字号
                        size = TvButtonSize.Md,
                        modifier = Modifier
                            .then(restoreHeaderOnFocus)
                            .focusProperties {
                                up = favoriteFocusRequester
                                down = belowPlay
                            }
                            .then(episodeDownModifier),
                        onClick = {
                            // 「从头开始」归零的必须是真正要播的那条：
                            // 从「继续观看」进来时 item 是剧集壳，进度写在分集行上
                            val target = resumeEp ?: item
                            vm.resetPosition(target.id)
                            TvNavHistory.playedEpisodeId = target.id
                            onPlayId(target.id)
                        }
                    )
                }
            }
        }
    }
}

/**
 * 简介 / 展开 / 导演 / TMDB 署名：从 [TvDetailScreen] 原样拆出。
 * 有没有那颗展开按钮要参与决定下键落点，所以 overview 与截断状态由父级算好传进来。
 */
@Composable
private fun TvDetailDescription(
    overview: String,
    directors: String,
    showCredit: Boolean,
    descExpanded: Boolean,
    descOverflow: Boolean,
    onOverflowChange: (Boolean) -> Unit,
    onToggleExpanded: () -> Unit,
    descFocusRequester: FocusRequester,
    descUpTarget: FocusRequester,
    belowDesc: FocusRequester,
    hasEpisodes: Boolean,
    publishStand: (FocusRequester) -> Modifier,
    enterEpisodeSection: (FocusRequester) -> Unit
) {
    // ---------- 简介：只读，收起态下键会跳过它（展开入口紧贴其下方） ----------
    if (overview.isNotBlank()) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = overview,
            color = PageBody,
            style = MaterialTheme.typography.bodyLarge,
            // 4 行以内：保证简介 + 展开按钮 + 导演 + 主演能挤进同一屏
            maxLines = if (descExpanded) Int.MAX_VALUE else 4,
            overflow = if (descExpanded) TextOverflow.Visible else TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 620.dp),
            onTextLayout = { layout ->
                if (!descExpanded) onOverflowChange(layout.hasVisualOverflow)
            }
        )

        // ---------- 展开 / 收起简介：只读区里唯一的焦点站 ----------
        // 聚焦不做额外对齐：requestFocus 自带的最小滚动已够，再补一次对齐会和它
        // 打架形成回弹（选择剧集→展开更多跳两次）。展开 / 收起仍对齐到简介首行。
        if (descExpanded || descOverflow) {
            Spacer(Modifier.height(12.dp))
            TvPillButton(
                text = if (descExpanded) "收起" else "展开更多",
                primary = false,
                size = TvButtonSize.Md,
                modifier = Modifier
                    .focusRequester(descFocusRequester)
                    .focusProperties {
                        up = descUpTarget
                        down = belowDesc
                    }
                    .onPreviewKeyEvent { e ->
                        // 有分集时下键走活落点回退，避免右移后首格死节点（#59 同款）
                        if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionDown && hasEpisodes) {
                            enterEpisodeSection(belowDesc)
                            true
                        } else false
                    }
                    .then(publishStand(descFocusRequester)),
                onClick = onToggleExpanded
            )
        }
    }

    // ---------- 导演：紧接简介，只读、不接收焦点 ----------
    if (directors.isNotBlank()) {
        Spacer(Modifier.height(10.dp))
        TvInfoLine("导演", directors)
    }

    // ---------- 数据来源署名：只读，位置仍要在焦点滚得到的区域 ----------
    // TMDB 条款要求署名可见：放在选集下方等于藏在焦点走不到的地方，等于没署。
    if (showCredit) {
        Spacer(Modifier.height(10.dp))
        TmdbCreditLine()
    }
}

/**
 * 选集区：标题 + 控制条（源 · 季 · 跳过片头）+ Emby 货架 / TVBox 网格 + 翻页行。
 * 从 [TvDetailScreen] 原样拆出：focusUp/focusDown 的回退链、recall 组合期交入 stand、
 * 翻页行锚点挂首颗 —— 一概未动。换源/换季/翻页的状态写回经回调交父级。
 */
@Composable
private fun TvDetailEpisodesSection(
    item: MediaEntity,
    episodes: List<MediaEntity>,
    tvSeasons: List<Int>,
    selectedSeason: Int,
    onSeasonChange: (Int) -> Unit,
    seasonCount: Int,
    pagedEpisodes: List<MediaEntity>,
    epRows: List<List<MediaEntity>>,
    pageIndex: Int,
    totalPages: Int,
    onPageChange: (Int) -> Unit,
    playSources: List<String>,
    selectedPlaySource: String,
    onSelectSource: (String) -> Unit,
    isEmbySource: Boolean,
    embyRowState: LazyListState,
    skipPillVisible: Boolean,
    onShowSkipDialog: () -> Unit,
    recallEpisodeId: Long,
    stand: StandTarget,
    publishStand: (FocusRequester) -> Modifier,
    controlsFocusRequester: FocusRequester,
    firstEpFocusRequester: FocusRequester,
    pageFocusRequester: FocusRequester,
    lastCellRequester: MutableState<FocusRequester?>,
    descFocusRequester: FocusRequester,
    descTruncated: Boolean,
    playFocusRequester: FocusRequester,
    shelfEntry: () -> FocusRequester,
    hasControlAnchor: Boolean,
    hasPageRow: Boolean,
    belowEpisodesFocusRequester: FocusRequester,
    onFocusedEp: (Long, FocusRequester) -> Unit,
    onPlayEpisode: (Long) -> Unit
) {
    val hasEpisodes = episodes.isNotEmpty()
    val scope = rememberCoroutineScope()
    TvSectionTitle(
        text = if (tvSeasons.size > 1) {
            "选集（第 $selectedSeason 季 · 共 $seasonCount 集）"
        } else {
            "选集（共 ${episodes.size} 集）"
        },
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 6.dp)
    )

    // 控制条：多源 / 多季 / 跳过片头胶囊（翻页已挪到下方独立行）。
    // 有源或季时它是「播放 ↔ 选集」之间的焦点站；只有翻页时交给翻页行。
    if (hasControlAnchor) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val pillFocus = Modifier.focusProperties {
                // ↑ 按版面顺序往回走：展开按钮 > 播放键
                // （主演已下移到选集之后，不再是控制条的上一站）
                up = if (descTruncated) descFocusRequester else playFocusRequester
                // ↓ 有分集时交给按键拦截落活节点；无分集留在原地
                down = FocusRequester.Cancel
            }.onPreviewKeyEvent { e ->
                // 落点要在按键这一刻读「最后聚焦那集」：组合期写死会攥着
                // 右移后被回收的首格死节点，下键回不到选集（#59 同款）。
                if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionDown && hasEpisodes) {
                    scope.launch {
                        withFrameNanos { }
                        tvMoveFocus(shelfEntry(), firstEpFocusRequester)
                    }
                    true
                } else false
            }.then(publishStand(controlsFocusRequester))
            // 播放源 / 云盘切换：多条源时展示，默认第一条
            if (playSources.size > 1) {
                playSources.forEachIndexed { index, src ->
                    TvPillButton(
                        text = src,
                        primary = selectedPlaySource == src,
                        modifier = if (index == 0) {
                            pillFocus.focusRequester(controlsFocusRequester)
                        } else pillFocus,
                        onClick = { onSelectSource(src) }
                    )
                }
            }
            if (tvSeasons.size > 1) {
                tvSeasons.forEachIndexed { index, sn ->
                    TvPillButton(
                        text = if (sn > 0) "第 $sn 季" else "正片",
                        primary = sn == selectedSeason,
                        // 源和季都在时，锚点交给第一颗源按钮，避免两个节点挂同一个 requester
                        modifier = if (playSources.size <= 1 && index == 0) {
                            pillFocus.focusRequester(controlsFocusRequester)
                        } else pillFocus,
                        onClick = { onSeasonChange(sn) }
                    )
                }
            }
            // 「跳过片头」胶囊：剧集 + 全局开关开启才出现，点击弹预设弹窗。
            // 配置写在剧集壳行上：全剧生效、每部剧只配一次。
            if (skipPillVisible) {
                TvPillButton(
                    text = if (item.introSkipMs > 0)
                        "跳过片头 ${fmtIntroMs(item.introSkipMs)}"
                    else "跳过片头 · 关",
                    primary = item.introSkipMs > 0,
                    // 单源单季时胶囊就是控制条唯一元件，锚点得跟着挂到它，
                    // 否则控制条的上键落点指向一个没挂载的节点。
                    modifier = if (playSources.size <= 1 && tvSeasons.size <= 1) {
                        pillFocus.focusRequester(controlsFocusRequester)
                    } else pillFocus,
                    onClick = onShowSkipDialog
                )
            }
        }
    }

    if (isEmbySource) {
        // Emby：横向剧照货架，遥控左右浏览。单行 LazyRow 宽度有界、
        // 卡片固定宽 + 16:9 定高，不会触发网格那种双向无限约束崩溃。
        // 焦点链与原网格同口径：首格挂 firstEp（换季/翻页落点），
        // 末格交给主演上键，recall 那一格组合期交入 stand。
        LazyRow(
            state = embyRowState,
            modifier = Modifier.fillMaxWidth().height(EMBY_CARD_HEIGHT),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(pagedEpisodes, key = { _, ep -> ep.id }) { idx, ep ->
                val cellFocus = remember(ep.id) { FocusRequester() }
                val cellTarget =
                    if (idx == 0) firstEpFocusRequester else cellFocus
                // 横滑货架回程使用最后聚焦项，不能把可能已回收的首格或末格写死。
                if (ep.id == recallEpisodeId) {
                    remember(cellTarget) {
                        stand.requester = cellTarget
                    }
                }
                TvEpisodeCard(
                    episode = ep,
                    isEmby = true,
                    modifier = Modifier.width(EMBY_CARD_WIDTH)
                        .then(publishStand(cellTarget)),
                    focusRequester = cellTarget,
                    // 单行货架：每格上键都回控制条（没有就展开按钮，再没有才播放）
                    focusUp = if (hasControlAnchor) controlsFocusRequester
                        else if (descTruncated) descFocusRequester
                        else playFocusRequester,
                    // 每格下键先落翻页行（多页时它在货架与主演之间），再往下是主演头像行
                    focusDown = if (hasPageRow) pageFocusRequester
                        else belowEpisodesFocusRequester,
                    onFocused = {
                        if (it) onFocusedEp(ep.id, cellTarget)
                    },
                    onClick = { onPlayEpisode(ep.id) }
                )
            }
        }
    } else {
        // 网格用 chunked 手动分行：LazyVerticalGrid 嵌在滚动容器里会因为
        // 双向无限高度约束直接崩，分行 Row 是最稳的做法
        epRows.forEachIndexed { rowIdx, rowEps ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowEps.forEachIndexed { idx, ep ->
                    // 每格自己的 requester：焦点停在哪一格，从播放器回来就还落回那一格。
                    // 首行首格已经把 controlsFocusRequester 那个位置让给了
                    // firstEpFocusRequester（换季/翻页的固定落点），不能两个节点挂同一个。
                    val cellFocus = remember(ep.id) { FocusRequester() }
                    val cellTarget =
                        if (rowIdx == 0 && idx == 0) firstEpFocusRequester else cellFocus
                    // 末行最后一格 = 主演头像行正上方那一格，主演的上键落在它
                    if (rowIdx == epRows.lastIndex && idx == rowEps.lastIndex) {
                        lastCellRequester.value = cellTarget
                    }
                    if (ep.id == recallEpisodeId) {
                        // 重建后的落点：组合期就把这一格交给 stand（同 ReturnSpot.cardStand 的交法），
                        // 抢焦效果在本帧组合之后才跑，取到的就是它
                        remember(cellTarget) {
                            stand.requester = cellTarget
                        }
                    }
                    TvEpisodeCard(
                        episode = ep,
                        isEmby = isEmbySource,
                        modifier = Modifier.weight(1f).then(publishStand(cellTarget)),
                        // 首行第一格挂焦点器，「选择剧集」与换季/翻页后直接落这里
                        focusRequester = cellTarget,
                        // 首行再按上：回控制条（没有就回展开按钮，再没有才回播放）
                        focusUp = if (rowIdx == 0) {
                            if (hasControlAnchor) controlsFocusRequester
                            else if (descTruncated) descFocusRequester
                            else playFocusRequester
                        } else null,
                        // 末行再按下：先落翻页行（多页时它在网格与主演之间），再往下是主演
                        focusDown = if (rowIdx == epRows.lastIndex) {
                            if (hasPageRow) pageFocusRequester
                            else belowEpisodesFocusRequester
                        } else null,
                        onClick = { onPlayEpisode(ep.id) }
                    )
                }
                repeat(TV_GRID_COLS - rowEps.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }

    // ---------- 翻页行：剧集网格右下角 ----------
    // 选集区的最后一层：网格末行 ↓ 进这里，↑ 回末行格，↓ 再往主演。
    // 窄按钮（Sm 档）、纯文字，页码夹在两颗中间。
    if (hasPageRow) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
        ) {
            val pageFocus = Modifier.focusProperties {
                up = FocusRequester.Cancel
                down = belowEpisodesFocusRequester
            }.onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionUp) {
                    scope.launch {
                        withFrameNanos { }
                        val target = if (isEmbySource) shelfEntry()
                            else lastCellRequester.value ?: firstEpFocusRequester
                        tvMoveFocus(target, firstEpFocusRequester)
                    }
                    true
                } else false
            }.then(publishStand(pageFocusRequester))
            // 锚点挂首颗：上下键进这一行都落在「上一页」
            TvPillButton(
                text = "上一页",
                primary = false,
                size = TvButtonSize.Sm,
                modifier = pageFocus.focusRequester(pageFocusRequester),
                onClick = {
                    if (pageIndex > 0) {
                        onPageChange(pageIndex - 1)
                    }
                }
            )
            Text(
                "${pageIndex + 1} / $totalPages",
                color = Color.White.copy(alpha = 0.55f),
                style = MaterialTheme.typography.bodyMedium
            )
            TvPillButton(
                text = "下一页",
                primary = false,
                size = TvButtonSize.Sm,
                modifier = pageFocus,
                onClick = {
                    if (pageIndex < totalPages - 1) {
                        onPageChange(pageIndex + 1)
                    }
                }
            )
        }
    }
}

/**
 * 电视端「跳过片头」预设弹窗：预设档位 + 自定义 mm:ss 输入，遥控器焦点操作。
 * 选中即写入剧集壳行（全剧生效），「不跳」= 清除配置。
 */
@Composable
private fun TvSkipIntroDialog(
    currentMs: Long,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit
) {
    val presets = listOf(0L to "不跳", 30_000L to "30s", 60_000L to "60s", 90_000L to "90s",
        120_000L to "120s", 150_000L to "150s", 180_000L to "180s")
    var custom by remember { mutableStateOf("") }
    val firstFocus = remember { FocusRequester() }
    // 打开即落在当前档位上；未配置落「不跳」
    val focusIndex = presets.indexOfFirst { it.first == currentMs }.coerceAtLeast(0)
    LaunchedEffect(Unit) {
        withFrameNanos { }
        tvMoveFocus(firstFocus)
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val compact = maxWidth < 700.dp
            val dialogWidth = if (compact) maxWidth - 40.dp
            else (maxWidth * 0.55f).coerceIn(520.dp, 900.dp)
            Column(
                Modifier
                    .width(dialogWidth)
                    .clip(Radius.Lg)
                    .background(AppColors.SurfaceHi)
                    .border(1.dp, Edge.HairlineStrong, Radius.Lg)
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "跳过片头（全剧生效，只需配置一次）",
                    style = MaterialTheme.typography.titleMedium,
                    color = TvText.Title
                )
                // 7 颗档位挤一行时 each 只分到 ~60dp，Md 档左右内边距就吃 40dp，文字被裁。
                // 改 4 列两行 + Sm 档：电视每颗 ~115dp、窄屏 ~69dp，「180s」也放得下。
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    var presetIndex = 0
                    presets.chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { (ms, label) ->
                                val i = presetIndex++
                                TvPillButton(
                                    text = label,
                                    primary = currentMs == ms,
                                    size = TvButtonSize.Sm,
                                    modifier = Modifier.weight(1f).then(
                                        if (i == focusIndex) Modifier.focusRequester(firstFocus) else Modifier
                                    ),
                                    onClick = { onPick(ms) }
                                )
                            }
                            // 末行不足补等宽空位，两行列宽对齐
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = custom,
                        onValueChange = { custom = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("自定义 分:秒，如 1:30", color = TvText.Muted) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = TvText.Title),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TvText.Title,
                            unfocusedTextColor = TvText.Title,
                            focusedBorderColor = AppColors.Brand,
                            unfocusedBorderColor = Edge.HairlineStrong,
                            cursorColor = AppColors.Brand
                        )
                    )
                    TvPillButton(
                        text = "确定",
                        primary = false,
                        onClick = { parseIntroInput(custom)?.let { onPick(it) } }
                    )
                }
                Text(
                    "OK 选中 · Back 关闭",
                    style = MaterialTheme.typography.bodySmall,
                    color = TvText.Muted
                )
            }
        }
    }
}

@Composable
private fun TvInfoLine(label: String, value: String) {
    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
        Text(
            label,
            color = PageMuted,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(58.dp)
        )
        Text(
            value,
            color = PageBody,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 电视端主演行用的轻量数据结构，避免直接依赖 Emby / TMDB 两套 Person 类型 */
private data class TvCast(val name: String, val role: String, val imageUrl: String)

/**
 * 加载期的整页骨架：形状对着真实排版抄（首屏是左 72% 的标题块，内容区是整宽 + 40dp 左轴），
 * 所有格子共用同一条横扫整屏的光带。
 *
 * 为什么这里可以动、首页骨架却必须是静态：首页是几十张卡各挂一条无限动画，
 * 每帧重算几十个渐变还要和滚动抢填充率；这里全页只有下面这一条 transition，
 * 每块在 drawBehind 里填一个矩形渐变，而且首屏真正在场的只有 5 组块
 * （简介往后都在 0.55 屏高之下，屏幕外不参与绘制），加载一结束整个骨架就摘掉。
 */
@Composable
private fun TvDetailSkeleton() {
    val density = LocalDensity.current
    val pageW = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val bandW = pageW * 0.34f
    val transition = rememberInfiniteTransition(label = "tv_detail_skeleton")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1250, easing = LinearEasing), RepeatMode.Restart),
        label = "tv_detail_band"
    )
    // 光带左端在「整屏坐标系」里的位置：从屏幕外左侧进、屏幕外右侧出，一轮 1.25s
    val bandX = -bandW + phase * (pageW + bandW)
    // 底色取 SurfaceHi → SurfaceRaise 的中段，高光在其上再叠 8.5% 白
    val base = lerp(AppColors.SurfaceHi, AppColors.SurfaceRaise, 0.45f)
    val hi = lerp(base, Color.White, 0.085f)

    Column(Modifier.fillMaxWidth()) {
        // ---- 首屏：标题 / 收藏 / 元信息 / 徽章 / 按钮（间距与真实代码同档）----
        Column(
            Modifier
                .fillMaxWidth(0.72f)
                .padding(start = TvPagePadding, end = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Skel(Modifier.width(360.dp).height(72.dp), bandX, bandW, base, hi, Radius.Sm)
                Spacer(Modifier.width(14.dp))
                Skel(Modifier.size(40.dp), bandX, bandW, base, hi, CircleShape)
            }
            Spacer(Modifier.height(8.dp))
            Skel(Modifier.width(220.dp).height(18.dp), bandX, bandW, base, hi, Radius.Pill)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Skel(Modifier.width(64.dp).height(26.dp), bandX, bandW, base, hi, Radius.Pill)
                Skel(Modifier.width(52.dp).height(26.dp), bandX, bandW, base, hi, Radius.Pill)
                Skel(Modifier.width(74.dp).height(26.dp), bandX, bandW, base, hi, Radius.Pill)
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Skel(Modifier.width(176.dp).height(58.dp), bandX, bandW, base, hi, Radius.Pill)
                Skel(Modifier.width(132.dp).height(52.dp), bandX, bandW, base, hi, Radius.Pill)
            }
        }

        // ---- 屏幕外那一半：简介 / 主演 / 选集，尺度照真实内容排 ----
        Column(Modifier.padding(start = TvPagePadding, end = TvPagePadding)) {
            Spacer(Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(620, 600, 560, 430).forEach { w ->
                    Skel(Modifier.width(w.dp).height(16.dp), bandX, bandW, base, hi, Radius.Sm)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                repeat(6) { Skel(Modifier.size(56.dp), bandX, bandW, base, hi, CircleShape) }
            }
            Spacer(Modifier.height(16.dp))
            Skel(Modifier.width(220.dp).height(18.dp), bandX, bandW, base, hi, Radius.Pill)
            Spacer(Modifier.height(10.dp))
            repeat(2) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    repeat(TV_GRID_COLS) {
                        Skel(Modifier.weight(1f).height(64.dp), bandX, bandW, base, hi, Radius.Md)
                    }
                }
            }
        }
    }
}

/**
 * 骨架上的一块。
 *
 * 光带是按「整屏 x」算的，而 drawBehind 用的是块自己的局部坐标，所以每块要把光带位置
 * 平移回自己这儿 —— 这一步就是「一道光横扫整屏」和「每块各自亮」的全部区别。
 * 左边界在 onGloballyPositioned 里取一次、存进普通数组（不是 State）：每帧绘制时读它，
 * 不需要为位置变化触发重组。
 */
@Composable
private fun Skel(
    modifier: Modifier,
    bandX: Float,
    bandW: Float,
    base: Color,
    hi: Color,
    shape: Shape
) {
    val rootX = remember { FloatArray(1) }
    Box(
        modifier
            .onGloballyPositioned { rootX[0] = it.positionInRoot().x }
            .clip(shape)
            .drawBehind {
                val start = bandX - rootX[0]
                drawRect(
                    Brush.linearGradient(
                        colors = listOf(base, hi, base),
                        start = Offset(start, 0f),
                        end = Offset(start + bandW, 0f)
                    )
                )
            }
    )
}

/**
 * 主演头像行：圆形头像 + 姓名 + 角色，每颗头像是一个焦点站。
 *
 * 为什么必须能聚焦：电视上没有滚轮，焦点走不到的内容就等于看不到。Emby / TMDB 是唯一
 * 带演员照片的两路数据（CatVod 协议只有姓名），所以这一行只在有照片时出现，
 * 同时充当「电影详情页」下键链的落点。
 *
 * 头像 56dp、一行 6 个：和原来「简介 → 导演 → 主演 → 选集首行同屏」的量级保持一致，
 * 不会把选集挤出屏幕；聚焦时只放大 8% 并描一圈白边，不改变格子宽度，
 * 所以整行不会因为聚焦而移位（规范 R2）。
 */
@Composable
private fun TvCastRow(
    cast: List<TvCast>,
    requesters: List<FocusRequester>,
    focusUp: () -> FocusRequester,
    focusDown: FocusRequester
) {
    val scope = rememberCoroutineScope()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        cast.forEachIndexed { index, person ->
            val interaction = remember { MutableInteractionSource() }
            val focused by interaction.collectIsFocusedAsState()
            val avatarShape = remember { CircleShape }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(76.dp)
                    .zIndex(if (focused) 1f else 0f)
                    .then(
                        requesters.getOrNull(index)?.let { Modifier.focusRequester(it) } ?: Modifier
                    )
                    .focusProperties { up = FocusRequester.Cancel; down = focusDown }
                    .onPreviewKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionUp) {
                            scope.launch {
                                withFrameNanos { }
                                tvMoveFocus(focusUp())
                            }
                            true
                        } else false
                    }
                    .focusable(interactionSource = interaction)
            ) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(avatarShape)
                        // 恒暗页：头像占位底钉暗，浅色模式下不能翻成白圆压白字
                        .background(TvPageText.SurfaceHi)
                        .tvFocusRing(focused, radius = 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (person.imageUrl.isNotBlank()) {
                        AsyncImage(
                            model = person.imageUrl,
                            contentDescription = person.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // 个别演员没有 PrimaryImageTag：用姓名首字顶上，避免出现一串空灰圆
                        Text(
                            person.name.take(1),
                            color = PageMeta,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    person.name,
                    color = PageSection,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (person.role.isNotBlank()) {
                    Text(
                        person.role,
                        color = PageMuted,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** 每页显示的分集数：TVBox 网格 40 集一页，超出用翻页按钮切换 */
private const val TV_EPS_PAGE = 40

/** Emby 剧照货架每页 20 集：横向滚动浏览，与手机端同一口径 */
private const val TV_EMBY_EPS_PAGE = 20

/** Emby 横向货架卡宽：16:9 剧照，十英尺下标题/时长可读，一屏约 4~5 张 */
private val EMBY_CARD_WIDTH = 240.dp
private val EMBY_CARD_HEIGHT = 135.dp

/**
 * 详情页竖滚位置进程级记忆（键 mediaId）。
 * 返回时系统把 Activity 整只重建，新组合的 scrollState 从 0 起；焦点落到货架那一集后
 * bring-into-view 再动画滚下去 —— 用户看到的就是「先在顶部闪一下才停到那一集」。
 * 离开时记下滚到哪，返回内容到位后先 instant 滚回那一屏，焦点落定时目标已在视口，不再补滚。
 */
private val detailScrollMemory = java.util.concurrent.ConcurrentHashMap<Long, Int>()

/** 分集网格列数：分集名是源给的原名（如 "[5.4GB]E01.mkv【…】"），
 *  10 列时一格不到 90dp，名字全被省略号吃掉；4 列后单格够宽，集名能看清。 */
private const val TV_GRID_COLS = 4

/**
 * TV 端分集卡片。
 *
 * 为什么不用原来的胶囊按钮：几百集的剧横排滚动，遥控器要按几百次右键才能翻到中间，
 * 而且看不到已看进度。改成卡片后可以按网格方向键移动，一屏能看到 40 集，
 * 已看的集数带进度条，一眼就能找到接到哪里。
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TvEpisodeCard(
    episode: MediaEntity,
    modifier: Modifier = Modifier,
    isEmby: Boolean = false,
    focusRequester: FocusRequester? = null,
    /** 首行再按上时要去哪里；null = 交给默认焦点搜索（上一行） */
    focusUp: FocusRequester? = null,
    /** 末行再按下时的行为；FocusRequester.Cancel = 留在原地 */
    focusDown: FocusRequester? = null,
    /** 焦点进入/离开回调（Emby 货架用它驱动视频信息面板跟焦） */
    onFocused: ((Boolean) -> Unit)? = null,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    tvKeepFocusedItemAlive(focused)

    val label = episode.episodeLabel()
    val watched = episode.positionMs > 5_000L
    val progress = if (episode.durationMs > 0L) {
        (episode.positionMs.toFloat() / episode.durationMs).coerceIn(0f, 1f)
    } else 0f

    // 焦点链两端共用：路由必须排在焦点目标之前；clickable 必须排在 focusable 外面，
    // 两者共用一个 interactionSource（反过来写 OK 键会冒泡到不了里层，详见原注释）。
    val focusChain = modifier
        .zIndex(if (focused) 1f else 0f)
        .focusProperties {
            if (focusUp != null) up = focusUp
            if (focusDown != null) down = focusDown
        }
        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
        .onFocusChanged { onFocused?.invoke(it.hasFocus) }
        .clickable(
            interactionSource = interaction,
            indication = null,
            onClick = onClick
        )
        .focusable(interactionSource = interaction)

    // Emby 来源：16:9 剧照缩略图卡。尺寸一动都不动（放大改焦点矩形会让 verticalScroll
    // 的 bring-into-view 每次补一点滚动，左右切集整屏上下抖），焦点仍只用边框表示。
    if (isEmby) {
        Column(
            focusChain
                .clip(Radius.Md)
                // 与手机端 Emby 卡同款白透明玻璃；剧照没加载出来时不至于是一块黑砖。
                // 填充取 50% 档：原来 16% 太透，浅色模式下焦点环（深色）压在暗底上几乎看不见，
                // 底提亮后焦点框才有对比度可依。
                .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.50f), Color.White.copy(alpha = 0.38f))))
                .tvFocusRing(focused)
                .then(
                    if (focused) Modifier
                    else Modifier.border(
                        1.dp,
                        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.32f), Color.White.copy(alpha = 0.15f))),
                        Radius.Md
                    )
                )
        ) {
            EpisodeStill(episode)
        }
        return
    }

    // TVBox 来源：竖排卡（排版不动），底换成手机端 EpisodeCard 同款白透明玻璃
    // —— 原来钉死的 0xFF131313 实底在暗底页上是一块黑砖，玻璃档才压得住海报基调底。
    Column(
        focusChain
            // 详情页不放大获焦的卡片：放大一改焦点矩形，verticalScroll 的
            // bring-into-view 每次都补一点滚动，左右切集时整屏跟着上下抖。
            // 焦点只用全局那圈白描边表示，尺寸一动都不动。
            // 填充 50% 档：同 Emby 分支，太透会让浅色模式下的焦点环看不清。
            .clip(Radius.Md)
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.50f), Color.White.copy(alpha = 0.38f))))
            .tvFocusRing(focused)
            .then(
                if (focused) Modifier
                else Modifier.border(
                    1.dp,
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.32f), Color.White.copy(alpha = 0.15f))),
                    Radius.Md
                )
            )
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Text(
            label,
            color = if (watched) AppColors.BrandSoft else PageTitle,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(6.dp))
        // 已看进度：一眼能看出这部剧看到哪了
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(Radius.Pill)
                .background(Color.White.copy(alpha = 0.2f))
        ) {
            if (progress > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth(progress)
                        .height(3.dp)
                        .background(AppColors.Brand)
                )
            }
        }
    }
}

/**
 * 徽章行：元信息下面那一行。
 *
 * 作用是把「片源质量」这类离散事实压缩成一排小胶囊，一眼扫过即可，
 * 不占用正文空间。TMDB 徽章只在真的拿到 TMDB 数据时才出现，
 * 避免开关关闭时界面上出现不该有的来源标识。
 *
 * 分辨率 / HDR / 编码 / 音轨三条路都能给：Emby 来自 MediaSources，
 * TVBox 来自详情入库时的文件名推测 + 播放期真实轨道回写。
 * 源自己给的信息（[sourceInfo]：更新状态、集数、线路数）与画质徽章并存，
 * 画质在前、源信息在后；两边都没有就整行不出现。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormatBadgesRow(
    tmdbEnabled: Boolean,
    resolution: String,
    hdr: String,
    codec: String,
    audio: String,
    sourceInfo: List<String>
) {
    // 编排与取色全部走 components/FormatBadges.kt 的共用实现。
    // 以前这里自己抄了一份 when 分支：Dolby Vision 落进 else 的灰蓝兜底，
    // 音轨棕和编码灰蓝各写一次字面量，跟手机端改一处漏一处。
    val badges = buildFormatBadges(
        tmdbEnabled = tmdbEnabled,
        resolution = resolution,
        hdr = hdr,
        codec = codec,
        audio = audio,
        sourceInfo = sourceInfo,
        // 电视端才换官方标识：十英尺外字标比纯文字更容易认，手机端 12sp 放字标会糊。
        useLogos = true
    )
    if (badges.isEmpty()) return
    // 徽章是电视上最小的一类字，11sp 在三米外只剩一个形状；抬到 14sp 并允许换行，
    // 免得「共 xx 集」「x 条线路」这类右侧信息被裁掉。手机端仍用原来的小档。
    // 只放大字号和内边距，材质与取色不开放 —— 那是两端唯一的画法。
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        badges.forEach { badge ->
            BadgeChip(
                badge = badge,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.2.sp,
                tmdbIconHeight = 26.dp,
                dolbyIconHeight = 20.dp,
                dolbyCaptionSize = 11.sp,
                logoHeight = 18.dp
            )
        }
    }
}

/**
 * Emby 分集媒体流信息面板（横排两行，铺满整宽，整页最后一层）。
 *
 * 无底无边：直接铺在详情页环境渐层上，与简介/主演同口径 ——
 * 黑卡版本在渐层上像一块贴上去的补丁，已摘。
 * 第一行：视频一条线（分辨率大字 + 像素 + 编码/HDR/位深胶囊）接竖分隔线，
 * 右侧文件四项（容器/体积/总码率/时长）铺满剩余宽度；第二行：音轨横铺。
 * 数据来自进详情时分集请求顺带取回的 MediaSources/MediaStreams，无需播放。
 *
 * 焦点：面板自己是整页最后一站（主演 ↓ 落这里、↑ 回主演）。电视没有自由滚动，
 * 挂在主演下面的只读死区永远滚不到。焦点描边左右各画到内容外 12dp（落进页边距），
 * 文字仍与上方主演标题同一条页面网格。
 */
@Composable
private fun TvEpisodeMediaInfoPanel(
    info: EmbyClient.MediaInfo,
    episode: MediaEntity,
    focusRequester: FocusRequester,
    focusUp: () -> FocusRequester
) {
    val scope = rememberCoroutineScope()
    val epTag = if (episode.episode > 0) "第 ${episode.episode} 集" else episode.episodeLabel()
    val headSub = if (episode.title.isNotBlank() && episode.title != epTag) "$epTag · ${episode.title}" else epTag

    val videoSub = listOfNotNull(
        fmtFrameRate(info.frameRate).takeIf { it.isNotBlank() },
        fmtMediaBitrate(info.videoBitrate).takeIf { it.isNotBlank() }
    ).joinToString(" · ")
    val hasVideoLine = info.resolution.isNotBlank() || info.width > 0 ||
        info.videoCodec.isNotBlank() || info.hdr.isNotBlank() || info.bitDepth > 0

    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    // 外框与内容网格同宽：焦点环左缘 = 主演 / 简介的左缘，不再浮进页边距显得左移。
    // 文字作为卡片内缩进（horizontal padding），块边缘与上方各分区对齐。
    Box(
        Modifier
            .fillMaxWidth()
            .focusProperties {
                up = FocusRequester.Cancel
                down = FocusRequester.Cancel
            }
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionUp) {
                    scope.launch {
                        withFrameNanos { }
                        tvMoveFocus(focusUp())
                    }
                    true
                } else false
            }
            .focusRequester(focusRequester)
            .focusable(interactionSource = interaction)
            .tvFocusRing(focused)
    ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
                // 头：分区标题 + 当前集 + 帧率码率 + 来源说明
                // 帧率/码率放头排右侧：880dp 内容宽下第一行放不下「视频一条线 + 文件四项 + 子行」，
                // 头排是唯一常年有余量的横排，放这里不挤任何一组。
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "视频信息",
                        color = PageMuted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        headSub,
                        color = PageMeta,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (videoSub.isNotBlank()) {
                        Spacer(Modifier.width(12.dp))
                        Text(videoSub, color = PageMeta, fontSize = 12.sp, maxLines = 1)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("来自 Emby 媒体流 · 无需播放", color = PageMuted, fontSize = 12.sp)
                }
                Spacer(Modifier.height(10.dp))

                // 第一行：视频一条线 + 文件四项
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (hasVideoLine) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("视频", color = PageMeta, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                            if (info.resolution.isNotBlank()) {
                                Text(info.resolution, color = PageTitle, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                            }
                            if (info.width > 0 && info.height > 0) {
                                Text("${info.width} × ${info.height}", color = PageBody, fontSize = 13.sp)
                            }
                            if (info.videoCodec.isNotBlank() || info.hdr.isNotBlank() || info.bitDepth > 0) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (info.videoCodec.isNotBlank()) MediaInfoChip(info.videoCodec)
                                    // Dolby Vision 全名 97dp，会把第一行挤溢出；胶囊用行业通用短标 DV
                                    if (info.hdr.isNotBlank()) MediaInfoChip(if (info.hdr == "Dolby Vision") "DV" else info.hdr)
                                    if (info.bitDepth > 0) MediaInfoChip("${info.bitDepth}bit")
                                }
                            }
                        }
                        Box(Modifier.width(1.dp).height(30.dp).background(Edge.Hairline))
                    }
                    Row(
                        Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (info.container.isNotBlank()) MediaInfoKv("容器", info.container)
                        if (info.sizeBytes > 0) MediaInfoKv("体积", fmtMediaSize(info.sizeBytes))
                        if (info.totalBitrate > 0) MediaInfoKv("总码率", fmtMediaBitrate(info.totalBitrate))
                        val dur = if (info.durationMs > 0) info.durationMs else episode.durationMs
                        if (dur > 0) MediaInfoKv("时长", fmtMediaDuration(dur))
                    }
                }

                // 第二行：音轨横铺（最多展示 3 条，总数在标签里）
                if (info.audioTracks.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Edge.Hairline))
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            "音频 · ${info.audioTracks.size} 条",
                            color = PageMeta,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.sp
                        )
                        info.audioTracks.take(3).forEachIndexed { idx, track ->
                            if (idx > 0) Box(Modifier.width(1.dp).height(24.dp).background(Edge.Hairline))
                            Row(
                                Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    track.language.ifBlank { "音轨 ${idx + 1}" },
                                    color = PageSection,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (track.isDefault) {
                                    Text("默认", color = PageMuted, fontSize = 11.sp)
                                }
                                val spec = listOfNotNull(
                                    track.codec.takeIf { it.isNotBlank() },
                                    track.channels.takeIf { it.isNotBlank() },
                                    "Atmos".takeIf { track.atmos },
                                    fmtSampleRate(track.sampleRate).takeIf { it.isNotBlank() },
                                    fmtMediaBitrate(track.bitrate).takeIf { it.isNotBlank() }
                                ).joinToString(" · ")
                                if (spec.isNotBlank()) {
                                    Text(
                                        spec,
                                        color = PageMeta,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // 第三行：字幕横铺（最多展示 3 条，总数在标签里），追加在音频下方
                if (info.subtitleTracks.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Edge.Hairline))
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            "字幕 · ${info.subtitleTracks.size} 条",
                            color = PageMeta,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.sp
                        )
                        info.subtitleTracks.take(3).forEachIndexed { idx, sub ->
                            if (idx > 0) Box(Modifier.width(1.dp).height(24.dp).background(Edge.Hairline))
                            Row(
                                Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    sub.language.ifBlank { "字幕 ${idx + 1}" },
                                    color = PageSection,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (sub.isDefault) {
                                    Text("默认", color = PageMuted, fontSize = 11.sp)
                                }
                                if (sub.forced) {
                                    Text("强制", color = PageMuted, fontSize = 11.sp)
                                }
                                if (sub.codec.isNotBlank()) {
                                    Text(
                                        sub.codec,
                                        color = PageMeta,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
    }
}

@Composable
private fun MediaInfoChip(text: String) {
    Text(
        text,
        color = PageSection,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(Radius.Sm)
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Edge.Hairline, Radius.Sm)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

@Composable
private fun MediaInfoKv(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = PageMeta, fontSize = 12.sp, maxLines = 1)
        Text(value, color = PageSection, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

private fun fmtMediaSize(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> String.format(java.util.Locale.US, "%.2f GB", bytes / 1_073_741_824.0)
    bytes >= 1_048_576L -> String.format(java.util.Locale.US, "%.0f MB", bytes / 1_048_576.0)
    bytes > 0 -> String.format(java.util.Locale.US, "%.0f KB", bytes / 1024.0)
    else -> ""
}

private fun fmtMediaBitrate(bps: Long): String = when {
    bps >= 1_000_000L -> String.format(java.util.Locale.US, "%.1f Mbps", bps / 1_000_000.0)
    bps > 0 -> "${bps / 1000} kbps"
    else -> ""
}

private fun fmtMediaDuration(ms: Long): String {
    if (ms <= 0) return ""
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(java.util.Locale.US, "%d:%02d", m, s)
}

private fun fmtFrameRate(fps: Double): String {
    if (fps <= 0) return ""
    val r = Math.round(fps * 1000) / 1000.0
    val num = if (r == Math.floor(r)) r.toInt().toString()
    else String.format(java.util.Locale.US, "%.3f", r).trimEnd('0').trimEnd('.')
    return "$num fps"
}

private fun fmtSampleRate(hz: Int): String {
    if (hz <= 0) return ""
    val k = hz / 1000.0
    val num = if (k == Math.floor(k)) k.toInt().toString()
    else String.format(java.util.Locale.US, "%.1f", k)
    return "$num kHz"
}

