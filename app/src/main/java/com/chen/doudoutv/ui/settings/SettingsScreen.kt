package com.chen.doudoutv.ui.settings

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chen.doudoutv.R
import com.chen.doudoutv.data.scrape.ScrapeStrategy
import com.chen.doudoutv.core.AppCache
import com.chen.doudoutv.core.Prefs
import com.chen.doudoutv.core.VideoCache
import com.chen.doudoutv.player.PlayerFactory
import com.chen.doudoutv.ui.components.AppButton
import com.chen.doudoutv.ui.components.AppButtonVariant
import com.chen.doudoutv.ui.components.GlassBarClearance
import com.chen.doudoutv.ui.components.TmdbDisclaimer
import com.chen.doudoutv.ui.components.ThemeSwatch
import com.chen.doudoutv.ui.LibraryViewModel
import com.chen.doudoutv.ui.theme.AppColors
import com.chen.doudoutv.ui.theme.AppPalette
import com.chen.doudoutv.ui.theme.Edge
import com.chen.doudoutv.ui.theme.ThemePalettes
import com.chen.doudoutv.ui.theme.themeBackdrop
import com.chen.doudoutv.ui.theme.Radius
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PREFS_NAME = "app_prefs"
private const val KEY_FORCE_TV = "force_tv_mode"

/** 关于页的版本号：与 build.gradle.kts 的 versionName、CHANGELOG.md 一起改（发版纪律见 README「发版纪律」） */
internal const val APP_VERSION_LABEL = "DouDouTV 1.0.3"

/** 根面板与关于页用的纯数字短版本号，跟 [APP_VERSION_LABEL] 同一批改 */
internal const val APP_VERSION_SHORT = "1.0.3"

/** 强制 TV 模式开关（测试用）。true 时覆盖系统判断 */
fun Context.forceTvMode(): Boolean =
    getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_FORCE_TV, false)

fun Context.setForceTvMode(enabled: Boolean) {
    getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putBoolean(KEY_FORCE_TV, enabled).apply()
}

/** 设置页上「点开才展开选项」的那几组，一次只开一个弹窗 */
internal enum class SettingChoice { PosterSize, Theme, ThemeMode, VideoTier, CardMotion, PlayerKernel }

/** 三档颜色模式的显示名 */
internal fun Prefs.ThemeMode.modeLabel(): String = when (this) {
    Prefs.ThemeMode.DARK -> "暗色"
    Prefs.ThemeMode.LIGHT -> "浅色"
    Prefs.ThemeMode.SYSTEM -> "跟随系统"
}

internal fun playerKernelLabel(kernel: String): String = when (kernel) {
    Prefs.KERNEL_FONGMI -> "FongMi（实验）"
    else -> "经典"
}

internal fun Prefs.PosterSize.label(): String = when (this) {
    Prefs.PosterSize.SMALL -> "小"
    Prefs.PosterSize.MEDIUM -> "中"
    Prefs.PosterSize.LARGE -> "大"
}

/** 焦点移动效果的两档文案（只在 TV 设置页出现，只有新版布局的海报卡读这个值） */
internal fun Prefs.CardMotion.label(): String = when (this) {
    Prefs.CardMotion.ART_ZOOM -> "放大"
    Prefs.CardMotion.LIFT -> "上抬"
}

/** 设置页内部导航：根面板 + 五个二级页；返回键在非根页回根（底栏常驻，与搜索页同款先例） */
private enum class SettingsPage { ROOT, INTERFACE, FEATURE, PLAYBACK, SCRAPE, CACHE, ABOUT }

@Composable
fun SettingsScreen(vm: LibraryViewModel) {
    val sources by vm.sources.collectAsStateWithLifecycle()
    val libraryCount by vm.libraryCount.collectAsStateWithLifecycle()
    val favoriteCount by vm.favorites.collectAsStateWithLifecycle()
    val resumeCount by vm.continueWatching.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    var page by remember { mutableStateOf(SettingsPage.ROOT) }
    BackHandler(enabled = page != SettingsPage.ROOT) { page = SettingsPage.ROOT }

    var forceTv by remember { mutableStateOf(ctx.forceTvMode()) }
    // 总开关：默认关闭。关闭时不渲染任何 TMDB 设置、数据来源与声明
    var tmdbOn by remember { mutableStateOf(Prefs.tmdbEnabled) }
    // 只保留 v4 持续访问令牌（官方推荐，令牌走请求头不进 URL）
    var tmdbToken by remember { mutableStateOf(Prefs.tmdbReadToken) }

    // 凭据变更计数：自增即触发重组，并重新判定刮削引擎
    var engineTick by remember { mutableStateOf(0) }
    // 只在记忆化里做纯计算（读凭据推导），不写全局状态
    val activeEngine = remember(engineTick) { ScrapeStrategy.resolve() }

    // 写操作放到副作用里：凭据一变就同步给全局，供其他页面读取。
    // 必须走 vm.refreshScrapeEngine()：分类页刮削按钮的显隐读的是 VM 快照，
    // 只刷全局 ScrapeStrategy.engine 的话按钮要等重启才出现。
    LaunchedEffect(engineTick) { vm.refreshScrapeEngine() }

    // 界面与缓存的可选项收进弹窗：页面上只留「分组 + 当前值」，点哪一行才展开那一组的档位
    var choice by remember { mutableStateOf<SettingChoice?>(null) }
    // Prefs 背后是 SharedPreferences，写它不会触发重组；行上的当前值得自己留一份状态
    var posterSize by remember { mutableStateOf(Prefs.posterSize) }
    var videoTier by remember { mutableStateOf(Prefs.videoCacheTier) }
    var skipIntro by remember { mutableStateOf(Prefs.skipIntroEnabled) }
    var playerKernel by remember { mutableStateOf(Prefs.playerKernel) }
    var videoCacheBytes by remember { mutableStateOf(0L) }
    var imageCacheBytes by remember { mutableStateOf(0L) }
    val scope = rememberCoroutineScope()
    // 体积要读 SQLite 索引（缓存没建起来时是遍历目录），不能压在主线程；
    // 换档与清空都在 IO 线程就地重读，这里只在进页时读一次
    LaunchedEffect(Unit) {
        videoCacheBytes = withContext(Dispatchers.IO) { VideoCache.usedBytes() }
        imageCacheBytes = withContext(Dispatchers.IO) { AppCache.sizeBytes(ctx) }
    }

    Box(Modifier.fillMaxSize().themeBackdrop()) {
        when (page) {
            SettingsPage.ROOT -> RootPanel(
                sources = sources.size,
                libraryCount = libraryCount,
                embyLoggedIn = sources.count {
                    it.kind == com.chen.doudoutv.data.model.SourceKind.EMBY && it.token.isNotBlank()
                },
                tvboxCount = sources.count {
                    it.kind == com.chen.doudoutv.data.model.SourceKind.TVBOX
                },
                favoriteCount = favoriteCount.size,
                resumeCount = resumeCount.size,
                themeLabel = AppPalette.current.label,
                tmdbOn = tmdbOn,
                cacheSummary = "视频 ${PlayerFactory.formatBytes(videoCacheBytes)} · " +
                    "图片 ${PlayerFactory.formatBytes(imageCacheBytes)}",
                onOpen = { page = it }
            )

            SettingsPage.INTERFACE -> SubPage("界面", onBack = { page = SettingsPage.ROOT }) {
                GroupHead("外观")
                GroupCard {
                    NavRow(
                        icon = Icons.Filled.Palette,
                        title = "主题色",
                        value = AppPalette.current.label,
                        valueAccent = true,
                        onClick = { choice = SettingChoice.Theme }
                    )
                    NavRow(
                        icon = Icons.Filled.DarkMode,
                        divider = true,
                        title = "颜色模式",
                        value = AppColors.themeMode.modeLabel(),
                        valueAccent = AppColors.themeMode == Prefs.ThemeMode.LIGHT,
                        onClick = { choice = SettingChoice.ThemeMode }
                    )
                    NavRow(
                        icon = Icons.Filled.Image,
                        divider = true,
                        title = "缩略图大小",
                        value = posterSize.label(),
                        onClick = { choice = SettingChoice.PosterSize }
                    )
                }
            }

            SettingsPage.FEATURE -> SubPage("功能", onBack = { page = SettingsPage.ROOT }) {
                GroupHead("开关")
                GroupCard {
                    ToggleRow(
                        icon = Icons.Filled.Smartphone,
                        neutral = true,
                        title = "强制 TV 模式",
                        sub = "手机上模拟电视界面布局，重启后生效",
                        checked = forceTv,
                        onChange = { forceTv = it; ctx.setForceTvMode(it) }
                    )
                    ToggleRow(
                        icon = Icons.Filled.SkipNext,
                        neutral = true,
                        divider = true,
                        title = "自动跳过片头",
                        sub = "详情页与播放器显示「跳过片头」按钮；关闭则不显示、也不再自动跳过",
                        checked = skipIntro,
                        onChange = { skipIntro = it; Prefs.skipIntroEnabled = it }
                    )
                }
                GroupHead("播放")
                GroupCard {
                    NavRow(
                        icon = Icons.Filled.PlayCircle,
                        title = "播放内核",
                        value = playerKernelLabel(playerKernel),
                        valueAccent = true,
                        onClick = { choice = SettingChoice.PlayerKernel }
                    )
                }
                GroupFoot("FongMi 档为实验内核，对齐 FongMi 官方播放栈；经典档为原 Exo 直连。改动在下次进播放器时生效。")
            }

            // 播放内核已并入「功能」子页，这里留空
            SettingsPage.PLAYBACK -> Unit

            SettingsPage.SCRAPE -> SubPage("刮削", onBack = { page = SettingsPage.ROOT }) {
                GroupHead("TMDB")
                GroupCard {
                    ToggleRow(
                        icon = Icons.Filled.AutoAwesome,
                        title = "使用 TMDB 刮削",
                        sub = "开启后自动补全海报、评分、简介与演职员；关闭则只用本地元数据",
                        checked = tmdbOn,
                        onChange = { tmdbOn = it; Prefs.tmdbEnabled = it; engineTick++ }
                    )
                    if (tmdbOn) {
                        RowDivider()
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Text(
                                "Read Access Token（v4）",
                                style = MaterialTheme.typography.bodyMedium,
                                color = AppColors.TextPrimary
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = tmdbToken,
                                onValueChange = {
                                    tmdbToken = it
                                    Prefs.tmdbReadToken = it
                                    // 填了令牌就自动打开总开关，避免「密钥填了但刮削没生效」的困惑
                                    if (it.isNotBlank() && !tmdbOn) {
                                        tmdbOn = true
                                        Prefs.tmdbEnabled = true
                                    }
                                    engineTick++
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                placeholder = { Text("粘贴以 eyJ 开头的令牌") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = AppColors.TextPrimary,
                                    unfocusedTextColor = AppColors.TextSecondary,
                                    focusedBorderColor = AppColors.Brand,
                                    unfocusedBorderColor = AppColors.SurfaceHi
                                )
                            )
                            Text(
                                "TMDB 账号 → Settings → API → Read Access Token，整串复制。",
                                style = MaterialTheme.typography.labelSmall,
                                color = AppColors.TextTertiary,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                            val tmdbActive =
                                activeEngine == com.chen.doudoutv.data.scrape.ScrapeEngine.TMDB
                            Text(
                                when {
                                    tmdbActive -> "当前方案：TMDB 刮削"
                                    Prefs.tmdbReadToken.isBlank() -> "当前方案：本地元数据（尚未填写令牌）"
                                    else -> "当前方案：本地元数据"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (tmdbActive) AppColors.Brand else AppColors.TextSecondary,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                        RowDivider()
                        ScrapeCacheRow(vm = vm, scope = scope)
                    }
                }
                // 关掉 tmdbOn 后旧条目还在，声明照样可见；没开过也可能有残留需要清
                if (tmdbOn) {
                    Column(Modifier.padding(top = 8.dp)) { TmdbDisclaimer() }
                }
            }

            SettingsPage.CACHE -> SubPage("缓存", onBack = { page = SettingsPage.ROOT }) {
                GroupHead("容量")
                GroupCard {
                    NavRow(
                        icon = Icons.Filled.Storage,
                        title = "视频缓存容量上限",
                        sub = "只给看过的网盘内容留一份，看完的自动删，超出上限先清最久没看的",
                        value = videoTier.label,
                        valueAccent = true,
                        onClick = { choice = SettingChoice.VideoTier }
                    )
                }
                GroupHead("占用")
                GroupCard {
                    CacheUsageRow(
                        title = "视频缓存占用",
                        value = PlayerFactory.formatBytes(videoCacheBytes),
                        actionLabel = "清空",
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    VideoCache.clearAll()
                                    videoCacheBytes = VideoCache.usedBytes()
                                }
                            }
                        }
                    )
                    CacheUsageRow(
                        divider = true,
                        title = "图片缓存占用",
                        value = PlayerFactory.formatBytes(imageCacheBytes),
                        actionLabel = "清除",
                        onClick = {
                            // 清缓存同样是磁盘操作，不能压在点击回调里同步跑
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    AppCache.clear(ctx)
                                    imageCacheBytes = AppCache.sizeBytes(ctx)
                                }
                            }
                        }
                    )
                }
                GroupFoot("看过的内容在本机留一份：往回拖进度、重看同一集不再重复下载。")
            }

            SettingsPage.ABOUT -> AboutPage()
        }

        // ---------- 选项弹窗：一次只开一个 ----------
        when (choice) {
            SettingChoice.PosterSize -> ChoiceDialog(
                title = "缩略图大小",
                options = Prefs.PosterSize.values().toList(),
                label = { it.label() },
                selected = posterSize,
                hint = null,
                onPick = { Prefs.posterSize = it; posterSize = it },
                onDismiss = { choice = null }
            )

            SettingChoice.Theme -> ChoiceDialog(
                title = "主题色",
                options = ThemePalettes,
                label = { it.label },
                selected = AppPalette.current,
                swatch = { palette ->
                    ThemeSwatch(palette, selected = palette.id == AppPalette.current.id, onClick = { })
                },
                hint = "只换强调色与背景氛围，文字层级与中性底色保持不变。",
                onPick = { AppPalette.select(it.id) },
                onDismiss = { choice = null }
            )

            SettingChoice.VideoTier -> ChoiceDialog(
                title = "视频缓存容量上限",
                options = Prefs.VideoCacheTier.values().toList(),
                label = { it.label },
                selected = videoTier,
                hint = "只给看过的网盘内容留一份，看完的自动删，超出上限先清最久没看的。",
                onPick = { t ->
                    val previous = Prefs.videoCacheTier
                    if (previous != t) {
                        Prefs.videoCacheTier = t
                        videoTier = t
                        // 降档（含关闭）要清空目录，占用数字跟着重读；都在 IO 线程做
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                VideoCache.applyTier(t.maxBytes, previous.maxBytes)
                                videoCacheBytes = VideoCache.usedBytes()
                            }
                        }
                    }
                },
                onDismiss = { choice = null }
            )

            // 颜色模式三档：选中即生效不关窗（与其它档位弹窗同口径）；浅色只作用于手机端浏览界面
            SettingChoice.ThemeMode -> ChoiceDialog(
                title = "颜色模式",
                options = Prefs.ThemeMode.values().toList(),
                label = { it.modeLabel() },
                selected = AppColors.themeMode,
                hint = "「跟随系统」随系统深浅色自动切换；浅色只在手机端生效，TV 端与播放器保持暗色。",
                onPick = {
                    Prefs.themeMode = it
                    AppColors.themeMode = it
                },
                onDismiss = { choice = null }
            )

            // 这一组只挂在 TV 设置页：手机端没有对应开关，落到这里就是空转
            SettingChoice.CardMotion -> Unit

            SettingChoice.PlayerKernel -> ChoiceDialog(
                title = "播放内核",
                options = listOf(Prefs.KERNEL_FONGMI, Prefs.KERNEL_CLASSIC),
                label = { playerKernelLabel(it) },
                selected = playerKernel,
                hint = "FongMi 档为实验内核，对齐 FongMi 官方播放栈；经典档为原自研 Exo 直连。改动在下次进播放器时生效。",
                onPick = { Prefs.playerKernel = it; playerKernel = it },
                onDismiss = { choice = null }
            )

            null -> Unit
        }
    }
}

// ============================================================
// 根面板
// ============================================================

@Composable
private fun RootPanel(
    sources: Int,
    libraryCount: Int,
    embyLoggedIn: Int,
    tvboxCount: Int,
    favoriteCount: Int,
    resumeCount: Int,
    themeLabel: String,
    tmdbOn: Boolean,
    cacheSummary: String,
    onOpen: (SettingsPage) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp, 20.dp, 16.dp, 32.dp + GlassBarClearance)
    ) {
        Text(
            "设置",
            style = MaterialTheme.typography.displaySmall,
            color = AppColors.TextPrimary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 18.dp)
        )

        StatHero(
            listOf(
                "$sources" to "来源数量",
                "$libraryCount" to "媒体条目",
                "$embyLoggedIn" to "Emby 已登录",
                "$tvboxCount" to "TVBox 接口",
                "$favoriteCount" to "收藏",
                "$resumeCount" to "继续观看"
            )
        )
        Spacer(Modifier.height(20.dp))

        GroupCard {
            NavRow(
                icon = Icons.Filled.Palette,
                title = "界面",
                sub = "主题色 · 颜色模式 · 缩略图",
                value = themeLabel,
                valueAccent = true,
                onClick = { onOpen(SettingsPage.INTERFACE) }
            )
            NavRow(
                icon = Icons.Filled.Tune,
                divider = true,
                title = "功能",
                sub = "强制 TV 模式 · 自动跳过片头 · 播放内核",
                onClick = { onOpen(SettingsPage.FEATURE) }
            )
            NavRow(
                icon = Icons.Filled.AutoAwesome,
                divider = true,
                title = "刮削",
                sub = "TMDB · 令牌 · 缓存",
                value = if (tmdbOn) "已开启" else "已关闭",
                valueAccent = tmdbOn,
                onClick = { onOpen(SettingsPage.SCRAPE) }
            )
            NavRow(
                icon = Icons.Filled.Storage,
                divider = true,
                title = "缓存",
                sub = cacheSummary,
                onClick = { onOpen(SettingsPage.CACHE) }
            )
            NavRow(
                icon = Icons.Filled.Info,
                neutral = true,
                divider = true,
                title = "关于",
                sub = "版本 · 功能介绍 · 开源与致谢",
                value = "v$APP_VERSION_SHORT",
                onClick = { onOpen(SettingsPage.ABOUT) }
            )
        }
    }
}

@Composable
private fun StatHero(stats: List<Pair<String, String>>) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(Radius.Md)
            .background(
                Brush.verticalGradient(
                    listOf(AppColors.Brand.copy(alpha = 0.10f), AppColors.Surface)
                )
            )
            .border(1.dp, Edge.Hairline, Radius.Md)
            .padding(vertical = 18.dp, horizontal = 6.dp)
    ) {
        stats.chunked(3).forEachIndexed { rowIndex, rowStats ->
            if (rowIndex > 0) Spacer(Modifier.height(14.dp))
            Row {
                rowStats.forEachIndexed { index, (value, label) ->
                    if (index > 0) {
                        Box(
                            Modifier
                                .width(1.dp)
                                .height(40.dp)
                                .background(Edge.Hairline)
                                .align(Alignment.CenterVertically)
                        )
                    }
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            value,
                            style = MaterialTheme.typography.headlineSmall,
                            color = AppColors.TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = AppColors.TextTertiary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                // 末行不足 3 个时补位，让前三列的宽度和首行对齐
                repeat(3 - rowStats.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// ============================================================
// 二级页骨架与通用行
// ============================================================

@Composable
private fun SubPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp, 12.dp, 16.dp, 32.dp + GlassBarClearance)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                Modifier
                    .clip(Radius.Sm)
                    .clickable(onClick = onBack)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回设置",
                    modifier = Modifier.size(18.dp),
                    tint = AppColors.BrandSoft
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "设置",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.BrandSoft,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            color = AppColors.TextPrimary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 8.dp)
        )
        content()
    }
}

/** 组头：卡片外的小节标题 */
@Composable
private fun GroupHead(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = AppColors.TextTertiary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 6.dp, bottom = 7.dp, top = 4.dp)
    )
}

/** 卡片底部说明 */
@Composable
private fun GroupFoot(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = AppColors.TextTertiary,
        lineHeight = MaterialTheme.typography.labelSmall.lineHeight,
        modifier = Modifier.padding(start = 6.dp, top = 8.dp, end = 6.dp)
    )
}

@Composable
private fun GroupCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(Radius.Md)
            .background(AppColors.Surface)
            .border(1.dp, Edge.Hairline, Radius.Md)
    ) {
        content()
    }
}

/** 行间分隔线：左端从图标右侧起头（56dp ≈ 行内边距 14 + 图标 30 + 间距 12） */
@Composable
private fun RowDivider() {
    Box(
        Modifier
            .padding(start = 56.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(Edge.Hairline)
    )
}

@Composable
private fun Chip(icon: ImageVector, neutral: Boolean) {
    Box(
        Modifier
            .size(30.dp)
            .background(
                if (neutral) AppColors.SurfaceRaise else AppColors.Brand.copy(alpha = 0.16f),
                Radius.Sm
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (neutral) AppColors.TextSecondary else AppColors.BrandSoft
        )
    }
}

@Composable
private fun RowShell(
    divider: Boolean,
    onClick: (() -> Unit)?,
    content: @Composable RowScope.() -> Unit
) {
    Column {
        if (divider) RowDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            content()
        }
    }
}

/** 图标位 + 标题/副文案 + 右侧当前值 + 箭头的导航行 */
@Composable
private fun NavRow(
    icon: ImageVector,
    title: String,
    sub: String? = null,
    value: String? = null,
    valueAccent: Boolean = false,
    neutral: Boolean = false,
    divider: Boolean = false,
    onClick: () -> Unit
) {
    RowShell(divider, onClick) {
        Chip(icon, neutral)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextPrimary
            )
            if (sub != null) {
                Text(
                    sub,
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.TextTertiary,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
        if (value != null) {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = if (valueAccent) AppColors.BrandSoft else AppColors.TextTertiary
            )
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = Color(0xFF555555)
        )
    }
}

/** 开关行：图标位 + 标题/副文案 + 右侧 iOS 形态开关 */
@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    sub: String? = null,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    neutral: Boolean = false,
    divider: Boolean = false
) {
    RowShell(divider, null) {
        Chip(icon, neutral)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextPrimary
            )
            if (sub != null) {
                Text(
                    sub,
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.TextTertiary,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AppColors.Brand,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = AppColors.SurfaceRaise,
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

/** 占用行：标题 + 数值在左，胶囊按钮在右（清空缓存这类直接生效的操作） */
@Composable
private fun CacheUsageRow(
    title: String,
    value: String,
    actionLabel: String,
    onClick: () -> Unit,
    divider: Boolean = false
) {
    RowShell(divider, null) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextPrimary
            )
            Text(
                value,
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.TextTertiary,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
        AppButton(
            text = actionLabel,
            onClick = onClick,
            variant = AppButtonVariant.Neutral,
            shape = Radius.Sm,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            textStyle = MaterialTheme.typography.labelMedium
        )
    }
}

/** 刮削缓存行：条数要查库，进这一段时异步填一次；清除动作就地重读 */
@Composable
private fun ScrapeCacheRow(vm: LibraryViewModel, scope: kotlinx.coroutines.CoroutineScope) {
    var cacheCount by remember { mutableStateOf(0) }
    var clearing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { cacheCount = vm.scrapeCacheSize() }

    RowShell(divider = false, onClick = null) {
        Column(Modifier.weight(1f)) {
            Text(
                "刮削缓存",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextPrimary
            )
            Text(
                "已记住 $cacheCount 部影片，同名同年不再联网重刮。",
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.TextTertiary,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
        AppButton(
            text = if (clearing) "清除中…" else "清除缓存",
            onClick = {
                if (clearing) return@AppButton
                clearing = true
                scope.launch {
                    vm.clearScrapeCache()
                    cacheCount = vm.scrapeCacheSize()
                    clearing = false
                }
            },
            variant = AppButtonVariant.Neutral,
            shape = Radius.Sm,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            textStyle = MaterialTheme.typography.labelMedium
        )
    }
}

// ============================================================
// 关于页
// ============================================================

@Composable
private fun AboutPage() {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp, 12.dp, 16.dp, 32.dp + GlassBarClearance)
    ) {
        Text(
            "关于",
            style = MaterialTheme.typography.titleLarge,
            color = AppColors.TextPrimary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 16.dp)
        )

        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 用真正的应用图标（launcher 同款 mipmap），不再画占位的品牌渐变 + PlayCircle
            Image(
                painter = painterResource(R.mipmap.ic_launcher),
                contentDescription = "DouDouTV 应用图标",
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
            Text(
                "DouDouTV",
                style = MaterialTheme.typography.titleMedium,
                color = AppColors.TextPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp)
            )
            Box(
                Modifier
                    .padding(top = 8.dp)
                    .background(AppColors.Brand.copy(alpha = 0.16f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 12.dp, vertical = 3.dp)
            ) {
                Text(
                    "v$APP_VERSION_SHORT",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.BrandSoft
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        AboutBlock("功能介绍") {
            Text("海报墙与沉浸式首页，继续观看断点续播、单集播完自动连播。")
            Text("Emby 直连原码流、TVBox 多线路聚合，网盘直链播放与本机磁盘缓存。")
            Text("可选 TMDB 刮削补全海报与简介；支持扫码在手机上维护数据源。")
        }

        AboutBlock("开源与致谢") {
            Text(
                buildAnnotatedString {
                    append("播放内核基于 ")
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)) {
                        append("Media3 / ExoPlayer")
                    }
                    append("（Apache-2.0）构建，电视端内核集成了 ")
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)) {
                        append("FongMi/TV")
                    }
                    append("（GPL-3.0）的开源代码。本应用遵循 GPL-3.0 许可证公开源代码，修改与分发同样适用该许可证。")
                }
            )
            Text("另向 Jetpack Compose、Room、Coil、OkHttp、jsoup，以及 TVBox / CatVod 公开配置规范的作者致谢。")
        }

        AboutBlock("个人开发声明") {
            Text("本应用为个人业余项目，按「现状」提供，不作任何明示或暗示的担保，不保证持续更新与所有设备上的兼容性。")
            Text("媒体库、观看进度与收藏均存储在本机，卸载应用即清除，请自行备份重要数据。")
        }

        AboutBlock("内容版权") {
            Text("本应用不内置、不存储、也不提供任何影视资源，仅用于播放你自有或已获授权的内容；相关版权与合法性由内容来源及使用者负责。")
        }

        Spacer(Modifier.height(14.dp))
        Text(
            "DouDouTV · 我家有只喵子叫豆豆",
            style = MaterialTheme.typography.labelSmall,
            color = AppColors.TextTertiary,
            modifier = Modifier.padding(top = 8.dp).align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
private fun AboutBlock(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(Radius.Md)
            .background(AppColors.Surface)
            .border(1.dp, Edge.Hairline, Radius.Md)
            .padding(14.dp, 16.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = AppColors.BrandSoft,
            fontWeight = FontWeight.SemiBold
        )
        Column(
            Modifier.padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            content()
        }
    }
}

/**
 * 通用选项弹窗：标题 + 一行一个档位 + 底部一句说明。
 *
 * 选中即生效但弹窗不关（多档设置常连着调），退出走返回键或点弹窗外。
 */
@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    label: (T) -> String,
    selected: T,
    swatch: (@Composable (T) -> Unit)? = null,
    hint: String?,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(Modifier.fillMaxSize().padding(horizontal = 32.dp), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 360.dp)
                    .background(AppColors.Surface, Radius.Lg)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = AppColors.Brand
                )
                options.forEach { option ->
                    val isCurrent = option == selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(
                                if (isCurrent) AppColors.Brand.copy(alpha = 0.16f) else AppColors.SurfaceHi,
                                Radius.Sm
                            )
                            .clickable { onPick(option) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        swatch?.invoke(option)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            label(option),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isCurrent) AppColors.Brand else AppColors.TextPrimary
                        )
                        if (isCurrent) {
                            Text("当前", style = MaterialTheme.typography.labelSmall, color = AppColors.Brand)
                        }
                    }
                }
                if (hint != null) {
                    Text(
                        hint,
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.TextTertiary
                    )
                }
            }
        }
    }
}
