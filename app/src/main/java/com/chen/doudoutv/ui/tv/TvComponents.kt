package com.chen.doudoutv.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.LocalPinnableContainer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import com.chen.doudoutv.core.Prefs
import com.chen.doudoutv.data.model.MediaEntity
import com.chen.doudoutv.ui.components.ButtonMaterial
import com.chen.doudoutv.ui.components.PosterArt
import com.chen.doudoutv.ui.components.PosterFallback
import com.chen.doudoutv.ui.components.ScrapedBadge
import com.chen.doudoutv.ui.components.batchScrapeConfirmText
import com.chen.doudoutv.ui.theme.AppColors
import com.chen.doudoutv.ui.theme.Edge
import com.chen.doudoutv.ui.theme.LocalTvPageDark
import com.chen.doudoutv.ui.theme.TvPageText
import com.chen.doudoutv.ui.theme.TvText
import com.chen.doudoutv.ui.theme.Radius
import com.chen.doudoutv.ui.theme.Motion

/**
 * 电视端专用组件（十英尺界面），与手机端完全分开实现：
 * 电视没触摸，可交互元素必须是「可聚焦 + 明显焦点态」，尺寸按观看距离放大；
 * 手机的涟漪/阴影三米外看不见，这里用白边和放大表达状态。
 *
 * 字号取 MaterialTheme.typography（实际大小由 TvTypography 定），颜色取 TvText 层级
 * （整屏只用白→灰一条轴，品牌色只留给按钮/进度条这类「可操作/进行中」元素）。
 *
 * 聚焦的元件一律先 .zIndex(1f) 再放大：抬高绘制层级，白边才不会被同排兄弟节点压住。
 * 焦点路由硬约束见下方「焦点路由规范 R1-R8」；新增可聚焦元件一律走
 * tvFocusRoute / tvMoveFocus / tvClaimFocusOnEnter，不要手拼。
 */

/* ========================================================
   焦点路由规范（TV 端硬约束，R1-R8）
   ========================================================
   这八条是从 8 个真机故障里归纳出来的，评审新页面就按它们打回：

   R1 显式路由优先。同一功能区里每颗可聚焦元件的 up/down 必须显式给 FocusRequester；
      默认几何搜索只允许用于同一行内的左右移动。跨功能区（顶栏 ↔ Hero、控制条 ↔ 选集网格、
      Header ↔ 选集）禁止依赖几何搜索 —— 它只在「当前视口内的可聚焦节点」里找，
      可达性被 Hero 高度这类无关参数决定，用户看到的就是「按了没反应」。
   R2 按键拦截挂在元件自己身上（modifier 级 onPreviewKeyEvent）。
      禁止「顶层容器 + 页面级布尔位」表示焦点在哪颗按钮上：一个布尔覆盖不了一片按钮，
      结果就是「播放键的 ↑ 有效，旁边详情键的 ↑ 无效」。状态位只用于视觉高亮。
   R3 不许用状态位校验跳转结果。成败只看 requestFocus 是否抛异常，跳完即止；
      降级顺序固定：同区备用锚 → moveFocus(方向) → 什么都不做。
      「跳完读状态位、读不到再跳一次」会把已经落对的焦点拽走。
   R4 焦点离开哪一层就留在哪一层。在控制条上完成的动作（换源/换季/翻页）本来就站在控制条上，
      所以数据回填后「什么都不做」才是对的；只有用户主动按下才进网格。
      「内容一变就抢焦回网格首行」这类无来源的抢焦一律禁止 —— 按钮还在组合里没消失，
      焦点不会掉，加一次恢复反而是把高亮从刚按下的按钮拽走。
   R5 弹窗与列表的默认焦点 = 当前选中项，不是第 0 项；没有选中项才回退第 0。
   R6 不允许焦点死区。任何占视口高 >40% 的不可聚焦区域必须自带至少一颗可聚焦 CTA，
      首屏必须存在一条「能往下走」的路。空态比满态更容易犯这条。
   R7 可见即恢复。Activity resume（进出播放器）与上级页 pop 回来时，把焦点交回该页声明的
      入口锚点；恢复焦点要与恢复构图（滚动位置）成对做，只靠 bring-into-view 的最小滚动
      回不到首屏大图。FocusManager 不暴露 hasFocus/currentFocus，查不到「当前有没有焦点」，
      所以只能统一重抢一次；1.9 的 requestFocus 会返回落没落上，抢到了就不再抢第二次。
   R8 引用别的子树或条件分支里的 requester 前，先确认它这一帧真的挂载了。
      淡出即摘除的元件不能当路由目标；跨分支 requestFocus 一律走 tvMoveFocus（内部已兜异常）。
      requestFocus 返回 false 就说明这一帧没挂上，等一帧再试，不要当成功。
   ======================================================== */

/**
 * Compose 1.9 焦点档案：1.9 改了焦点目标的析构语义 —— 焦点所在项被 Lazy 容器回收时，
 * [FocusTargetNode.onReset] 会在 owner 级直接 clearFocus，焦点当场消失；网格里往下移几行、
 * 容器回收旧项时焦点就掉，再按方向键会从根节点重找、跳回第一列第一张卡。
 *
 * 1.9 的 [LocalPinnableContainer] 能让被 pin 的项滚出视口也不回收：这里在拿到焦点时 pin
 * 所在项、失焦时 release，焦点所在项从此不被回收 —— 「移几行后焦点消失、再动跳回第一列」
 * 从根上断掉。不在 Lazy 容器里（LocalPinnableContainer 为 null）时什么都不做。
 */
@Composable
fun tvKeepFocusedItemAlive(focused: Boolean) {
    val pinnable = LocalPinnableContainer.current
    DisposableEffect(pinnable, focused) {
        // pin() 在「项已被回收」时会抛（checkPrecondition），这里兜住：
        // 组合完成到 effect 生效之间，列表数据可能已经变了。
        val handle = if (focused && pinnable != null) {
            runCatching { pinnable.pin() }.getOrNull()
        } else {
            null
        }
        onDispose { runCatching { handle?.release() } }
    }
}

/**
 * R1：声明一颗可聚焦元件的上/下落点。
 * 传 null 表示这一向不设规则，交给默认几何搜索 —— 那只允许用于同一行内的左右移动。
 */
fun Modifier.tvFocusRoute(
    up: FocusRequester? = null,
    down: FocusRequester? = null,
): Modifier = focusProperties {
    if (up != null) this.up = up
    if (down != null) this.down = down
}

/**
 * R3：跳焦点的唯一入口。
 *
 * 按顺序试各个落点，第一个成功的就返回。
 *
 * Compose 1.9 起 [FocusRequester.requestFocus] 直接返回「有没有落到目标节点」，
 * 所以这里不用再靠「抛异常 = 没挂载」反推成功与否：带方向的版本还能让框架
 * 按这个方向做最小滚动（往下跳就往下翻一格，不多滚）。
 * 全部没挂载时才按 [direction] 降级到默认几何搜索。
 * 绝不读 onFocusChanged 的状态位再校验一遍。
 */
fun tvMoveFocus(
    vararg targets: FocusRequester,
    focusManager: FocusManager? = null,
    direction: FocusDirection? = null,
): Boolean {
    for (target in targets) {
        val landed = runCatching { target.requestFocus(direction ?: FocusDirection.Enter) }
            .getOrDefault(false)
        if (landed) return true
    }
    if (focusManager != null && direction != null) {
        runCatching { focusManager.moveFocus(direction) }
    }
    return false
}

/** 焦点描边线宽：TV 端所有按键共用这一个数，不再一处 1.5 一处 2 */
val TvFocusRingWidth = 1.5.dp

/** 白线内侧那圈黑的宽度 */
private val TvFocusRingKeyline = 1.dp

/**
 * TV 端统一的焦点描边：外圈纯白，紧贴内侧再压一道 40% 黑。不发光、不放大。
 *
 * 为什么要那圈黑：描边画在节点边界之内，海报卡的边界里面就是图片本身 ——
 * 浅色海报的边缘碰上白线等于白对白，三米外直接消失。黑边把白线和底图隔开，
 * 白线两侧（外侧是近黑背景、内侧是黑边）就都有对比，任何底图上都是同一圈白。
 *
 * 全部画在节点内，所以挂在 clip 之后也不会被裁掉。和 [androidx.compose.foundation.border]
 * 一样先画内容再画线，图片盖不住它。
 */
fun Modifier.tvFocusRing(focused: Boolean, radius: Dp = 12.dp): Modifier =
    if (!focused) this else drawWithContent {
        drawContent()
        tvDrawFocusRing(Rect(Offset.Zero, size), radius.toPx())
    }

/**
 * 把焦点描边画到指定矩形上（bar 内移动焦点框用：矩形每帧在变，线宽和圆角不变）。
 * 半径按矩形短边夹一次，圆形元件传一个大值就能得到正圆。
 */
internal fun DrawScope.tvDrawFocusRing(rect: Rect, radiusPx: Float) {
    val w = TvFocusRingWidth.toPx()
    val k = TvFocusRingKeyline.toPx()
    if (rect.width <= w * 2f || rect.height <= w * 2f) return
    val maxR = minOf(rect.width, rect.height) / 2f
    drawRoundRect(
        color = AppColors.Focus,
        topLeft = Offset(rect.left + w / 2f, rect.top + w / 2f),
        size = Size(rect.width - w, rect.height - w),
        cornerRadius = CornerRadius(minOf(radiusPx - w / 2f, maxR).coerceAtLeast(0f)),
        style = Stroke(w)
    )
    drawRoundRect(
        color = if (AppColors.lightOn()) Color.White.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.4f),
        topLeft = Offset(rect.left + w + k / 2f, rect.top + w + k / 2f),
        size = Size(rect.width - 2f * w - k, rect.height - 2f * w - k),
        cornerRadius = CornerRadius(minOf(radiusPx - w - k / 2f, maxR).coerceAtLeast(0f)),
        style = Stroke(k)
    )
}

/**
 * 每次界面重新回到前台 +1（进播放器再退出来也会走一次 ON_RESUME）。
 *
 * 注册观察者时 Lifecycle 会把当前状态同步回放一次 ON_RESUME —— 那不是「回到前台」，
 * 是初始状态对齐。不滤掉它，入口抢焦会在进页后立刻被重放一遍：
 * 搜索页「点 tab 进来焦点留标签」就是被这次重放拽到输入框的。
 */
@Composable
fun tvResumeTick(): Int {
    val owner = LocalLifecycleOwner.current
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(owner) {
        var primed = false
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (!primed) {
                    primed = true
                    return@LifecycleEventObserver
                }
                tick++
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return tick
}

/**
 * R7：入口抢焦。[tick] 每次变化（首次组合 + 每次界面回到前台）都把焦点交给第一个已挂载的落点。
 *
 * 为什么不在这里判断「当前是不是已经有焦点」：跨 Activity 回来时（进播放器再退出）
 * 恰恰是「焦点状态还在、视觉选中框没了」和「焦点彻底丢了」两种情况混在一起，
 * 只能统一重抢一次来保证一定看得见选中态。
 * 代价是从播放器回来会把焦点放回该页入口锚点（而不是你离开时那颗卡片），
 * 这比「按了没反应」可预期；以后要做「记住离开位置」，把锚点表换成按层级记录的表即可。
 */
@Composable
fun tvClaimFocusOnEnter(
    tick: Any,
    vararg anchors: FocusRequester,
    scrollToTop: suspend () -> Unit = {},
    settleMillis: Long = 0L,
    keepTop: Boolean = false,
) {
    tvClaimFocusOnEnter(tick, { anchors.toList() }, scrollToTop, settleMillis, keepTop)
}

/**
 * [tvClaimFocusOnEnter] 的按需取锚点版本：落点由子树里的卡片在组合/拿焦点时才交出来，
 * 页面组合那一刻还读不到，所以这里传的是取值函数，效果启动时才读。
 *
 * [keepTop]：抢到焦点之后再把容器滚回顶部一次。1.9 的 requestFocus 自带 bring-into-view，
 * 入口锚点排在首屏偏下时它会把页面往下滚一截，进页看到的就不是刚进来那一屏构图了
 * （详情页的简介、设置页的分组被直接摊在眼前）。等一帧再滚，压住那次自动滚动。
 */
@Composable
fun tvClaimFocusOnEnter(
    tick: Any,
    anchorsProvider: () -> List<FocusRequester>,
    scrollToTop: suspend () -> Unit = {},
    settleMillis: Long = 0L,
    keepTop: Boolean = false,
) {
    LaunchedEffect(tick) {
        runCatching { scrollToTop() }
        if (settleMillis > 0) delay(settleMillis)
        // 先立即抢一次：LaunchedEffect 跑在组合应用之后、下一帧绘制之前。上一屏销毁时
        // 焦点被 Compose 兜底到树里第一个可聚焦元件（导航条的「首页」tab），直接等一帧再抢，
        // 那一帧就会被画出来 —— 用户看到的就是「返回时焦点从首页闪一下再落到卡片」。
        // 立即抢没有空窗；真没抢到（锚点节点还没挂上）再退回等帧重试。
        val g0 = tvMoveFocus(*anchorsProvider().toTypedArray())
        if (!g0) {
            // 等一帧再试：requester 要等节点组合完成才挂得上
            withFrameNanos { }
            val g1 = tvMoveFocus(*anchorsProvider().toTypedArray())
            if (!g1) {
                // 只有「这一次没抢到」才补跳：Compose 1.9 的 requestFocus 会直接告诉我们落没落上，
                // 抢到了就说明焦点已经在该去的地方，再补一次只会把用户已经走到的焦点拽回来。
                // 没抢到多半是锚点节点这一帧还没挂上（路由淡入淡出、刚从播放器 resume 回来），等一下再试。
                delay(120)
                tvMoveFocus(*anchorsProvider().toTypedArray())
            }
        }
        if (keepTop) {
            withFrameNanos { }
            runCatching { scrollToTop() }
            // 再等一帧压第二次：1.9 的 requestFocus 自带的 bring-into-view 是排在这一帧之后
            // 才落地的，只压一次会被它顶回去 —— 用户看到的就是「进页就摊开了下面那几行」。
            withFrameNanos { }
            runCatching { scrollToTop() }
        }
    }
}

/**
 * 电视端按钮的尺寸档位。
 *
 * 分档只动内边距，字号一律 titleMedium —— 这是 Netflix 的规矩：可操作元件靠
 * 「实心 / 幽灵 + 图标」分主次，不靠字号和尺寸分主次。一旦让次要按钮掉到 label 字号，
 * 就会出现「分区标题旁边一颗大胶囊、Hero 上一对小胶囊」的倒挂，两边都显得不对。
 *
 * 三档算上文字行高（23dp）大致是 35 / 43 / 49dp：
 *   · [Sm] 顶栏入口条（横向预算最紧）；
 *   · [Md] 页内次操作、分区「查看全部」、选集控制条；
 *   · [Lg] Hero 与详情页的主操作 —— 全页最大的可点元素。
 */
enum class TvButtonSize { Sm, Md, Lg }

private fun tvButtonPadding(size: TvButtonSize): PaddingValues = when (size) {
    TvButtonSize.Sm -> PaddingValues(horizontal = 14.dp, vertical = 6.dp)
    TvButtonSize.Md -> PaddingValues(horizontal = 20.dp, vertical = 10.dp)
    TvButtonSize.Lg -> PaddingValues(horizontal = 26.dp, vertical = 13.dp)
}

/**
 * 电视上的主按钮：品牌渐变实底（主操作）或磨砂玻璃（次操作），聚焦时加白边并放大。
 *
 * 材质与手机端共用 [ButtonMaterial]：品牌色上浅下深的渐变 + 顶部内高光 + 品牌光晕，
 * 实底压黑字、玻璃压白字。焦点信号仍是白边 + 放大，不靠换填充色——
 * 十英尺下焦点是常态，整颗反白会把主题色在屏幕上唯一的大色块抹掉。
 */
/**
 * 恒暗页（TV 详情页）上的玻璃档：对齐暗色模式 [ButtonMaterial.glassStops]/glassEdge 的白透明口径。
 * 浅色模式下 ButtonMaterial 会翻黑透明，落在详情页的暗底上就成了黑块压暗底。
 */
private fun pageGlassStops(): List<Color> =
    listOf(Color.White.copy(alpha = 0.13f), Color.White.copy(alpha = 0.07f))

private val pageGlassEdge = Brush.verticalGradient(
    listOf(Color.White.copy(alpha = 0.26f), Color.White.copy(alpha = 0.13f))
)

@Composable
fun TvPillButton(
    text: String,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    size: TvButtonSize = TvButtonSize.Md,
    /**
     * 左侧图标。
     *
     * 以前这里靠往文案里塞 ▶ / ＋ / ↻ 这类字符当图标，整棵电视树里因此出现两套图标语汇：
     * Material 图标只用了三处，其余全是 Unicode 字符。现在一律走真图标。
     */
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    /**
     * 右侧图标。
     *
     * 「下一页 ›」这类箭头在右边，「上一页 ‹」在左边，两种方向不能共用一个位置参数。
     */
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    // 焦点态统一由 interactionSource 驱动：clickable 与 focusable 共用同一个 source。
    // 之前用 Modifier.onFocusChanged 里的 isFocused 判断，在「遥控器跨子树跳焦点」
    // （Hero 播放键 → 顶栏同步键）时会出现逻辑焦点已移走、画面高亮却停在原按钮上的错位。
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val brand = AppColors.Brand
    val brandSoft = AppColors.BrandSoft
    // 恒暗页（TV 详情页）：玻璃档翻成白透明、次档文字钉白，浅色模式下不能翻成黑玻璃/深字
    val onDark = LocalTvPageDark.current

    Box(
        modifier
            .then(
                // 只有实底档发光：一屏多颗光晕会互相干扰，反而显脏
                if (primary) {
                    Modifier.shadow(
                        ButtonMaterial.PrimaryGlow,
                        Radius.Md,
                        clip = false,
                        ambientColor = brand.copy(alpha = 0.35f),
                        spotColor = brand.copy(alpha = 0.45f)
                    )
                } else Modifier
            )
            .clip(Radius.Md)
            .background(
                Brush.verticalGradient(
                    if (primary) ButtonMaterial.primaryStops(brand, brandSoft)
                    else if (onDark) pageGlassStops() else ButtonMaterial.glassStops()
                )
            )
            .tvFocusRing(focused)
            .then(
                when {
                    focused -> Modifier
                    primary -> Modifier.border(1.dp, ButtonMaterial.highlightEdge, Radius.Md)
                    else -> Modifier.border(
                        1.dp, if (onDark) pageGlassEdge else ButtonMaterial.glassEdge, Radius.Md
                    )
                }
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interaction)
            .padding(tvButtonPadding(size))
    ) {
        val textColor = when {
            // 实底压黑字：品牌色明度在中档，黑字比白字对比度高一倍
            primary -> Color.Black
            size == TvButtonSize.Sm -> if (onDark) TvPageText.Body else TvText.Body
            else -> if (onDark) TvPageText.Title else TvText.Title
        }
        val iconSize = when (size) {
            TvButtonSize.Sm -> 18.dp
            TvButtonSize.Md -> 22.dp
            TvButtonSize.Lg -> 24.dp
        }
        if (icon != null || trailingIcon != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (icon != null) {
                    androidx.compose.material3.Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = textColor,
                        // 图标跟按钮同档缩放，避免出现「小胶囊里塞个标准大的三角」
                        modifier = Modifier.size(iconSize)
                    )
                }
                Text(
                    text,
                    color = textColor,
                    // 三档共用同一个字号，层级交给填充与高度
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
                if (trailingIcon != null) {
                    androidx.compose.material3.Icon(
                        imageVector = trailingIcon,
                        contentDescription = null,
                        tint = textColor,
                        modifier = Modifier.size(iconSize)
                    )
                }
            }
        } else {
            Text(
                text,
                color = textColor,
                // 三档共用同一个字号，层级交给填充与高度
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1
            )
        }
    }
}

/**
 * 播放主按钮：在 TvPillButton 基础上左侧加一个播放图标，
 * 视觉上和截图的「▶ 开始观看 S05E01」一致。
 *
 * 材质与焦点行为跟 TvPillButton 完全一致，直接委托过去：
 * 以前这里是另一份手写实现，两份各自演进过一次，填充色已经对不上了。
 */
@Composable
fun TvPlayButton(
    text: String,
    modifier: Modifier = Modifier,
    size: TvButtonSize = TvButtonSize.Md,
    onClick: () -> Unit
) {
    TvPillButton(
        text = text,
        modifier = modifier,
        primary = true,
        size = size,
        icon = Icons.Filled.PlayArrow,
        onClick = onClick
    )
}

/**
 * 按钮圆角档：一律圆角矩形（Radius.Md）。
 * 详情页、来源页、设置页这些元件共用同一份。
 */
@Composable
fun tvButtonShape(): Shape = Radius.Md

/**
 * 图标按钮：电视端的次操作（收藏等）用图标而不是长条文字按钮。
 * 横向只占一个按钮宽度，把省下来的空间留给海报；
 * 聚焦时放大并加白边，符合十英尺界面的可辨识要求。
 * 形状见 [tvButtonShape]：圆角矩形。
 */
@Composable
fun TvIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    contentDescription: String? = null,
    /**
     * 直径。放进标题行时贴着大标题站，46dp 会显得比字还抢眼，
     * 所以允许调用方收小一档；操作区里的默认值仍是 46dp。
     */
    sizeDp: Dp = 46.dp
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = tvButtonShape()
    val onDark = LocalTvPageDark.current

    Box(
        modifier
            .size(sizeDp)
            .clip(shape)
            .background(
                when {
                    // 焦点态整颗反白：这一档不参与材质统一。
                    // 十英尺下焦点是常态，整颗纯白是隔着三米唯一还能一眼认出的信号。
                    focused -> SolidColor(Color.White)
                    // 选中态（已收藏等）走品牌渐变 + 黑字，和主按钮同一套语言
                    active -> Brush.verticalGradient(
                        ButtonMaterial.primaryStops(AppColors.Brand, AppColors.BrandSoft)
                    )
                    else -> Brush.verticalGradient(
                        if (onDark) pageGlassStops() else ButtonMaterial.glassStops()
                    )
                }
            )
            .tvFocusRing(focused)
            .then(
                if (focused) Modifier
                else if (active) Modifier.border(1.dp, ButtonMaterial.highlightEdge, shape)
                else Modifier.border(
                    1.dp, if (onDark) pageGlassEdge else ButtonMaterial.glassEdge, shape
                )
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .focusable(interactionSource = interaction),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = when {
                focused -> Color.Black
                active -> Color.Black
                else -> if (onDark) TvPageText.Title else TvText.Title
            },
            // 图标随按钮等比缩，收小后不会出现「大图标塞在小圆里」
            modifier = Modifier.size(sizeDp * 0.48f)
        )
    }
}

/**
 * 片名艺术字（刮削来的 ClearLogo）优先、文字兜底。
 *
 * 详情页与首页轮播都走这里，同为一档 80dp：原来详情页只让 Logo 长到 48dp，
 * 贴在元信息上面小得像水印，同一部片在首页和详情页是两种量级的标题。
 * 图挂不上就回落文字标题，不留一格空白。
 */
@Composable
fun TvMediaTitle(
    title: String,
    logoUrl: String,
    modifier: Modifier = Modifier,
    /**
     * 紧凑档：首页轮播用。轮播是一整块画面，片名再按详情页那档 44sp 压上去会盖掉半个海报，
     * 这里用 displayMedium（34sp）、logo 64dp，字号与画面比例更配。
     * 详情页仍走默认档，两处的槽宽都由内容决定（不再是固定宽度）。
     */
    compact: Boolean = false
) {
    var logoFailed by remember(logoUrl) { mutableStateOf(false) }
    val titleStyle = if (compact) {
        // compact 档文字再收一档：displayMedium (34sp) 在 80% 屏高的 Hero 上中文片名容易换行，
        // headlineLarge 视觉密度更配；logo 保持 64dp 不动
        MaterialTheme.typography.headlineLarge
    } else {
        MaterialTheme.typography.displayLarge
    }
    val logoHeight = if (compact) 64.dp else 80.dp
    Box(
        modifier.widthIn(max = if (compact) 360.dp else 520.dp).heightIn(min = logoHeight),
        contentAlignment = Alignment.CenterStart
    ) {
        if (logoUrl.isNotBlank() && !logoFailed) {
            AsyncImage(
                model = logoUrl,
                contentDescription = title,
                contentScale = ContentScale.Fit,
                alignment = Alignment.CenterStart,
                onError = { logoFailed = true },
                modifier = Modifier.height(logoHeight).widthIn(max = if (compact) 360.dp else 420.dp)
            )
        } else {
            Text(
                text = title,
                style = titleStyle,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 分区标题：电视上字要大，右侧可挂「更多」 */
@Composable
fun TvSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    // 默认走页面左轴；详情页等信息列更宽的区域由调用方覆写。
    // 上下留白从 22/10 收到 13/6：首页一屏最多叠 7 个分类行，原来单行的间距合计 52dp，
    // 七行就是半屏多的纯空白，标题和它下面那排海报被推得像是两个分区。
    // 再收一档 13/6 → 10/4 → 8/4：排与排之间的纯空白仍是横向 gap 的两倍量级，
    // 而十英尺口径（Android TV 官方栅格）里纵向和横向是同量级的，纵向才是「页面松散」的主因。
    // 上边距这次单独让 2dp：首页折痕那片黑已经由 Hero 的收色带负责，标题再压一条上边距等于在黑之外又加一段空白。
    // 行内左右仍保持页面左轴不动，只收纵向。
    contentPadding: PaddingValues = PaddingValues(
        start = TvPagePadding, end = TvPagePadding, top = 8.dp, bottom = 4.dp
    ),
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            // 分区标题比 Hero 低一档：titleLarge + Section 级白，靠字号分层而不是靠粗体
            color = if (LocalTvPageDark.current) TvPageText.Section else TvText.Section,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke()
    }
}

/**
 * 功能菜单里的单行选项：可聚焦，聚焦时反白高亮。
 * 来源页的操作面板和首页「继续观看」长按菜单共用这一种行样式，
 * 菜单类弹窗不要再另造一套按钮视觉。
 */
@Composable
fun TvMenuRow(
    text: String,
    hint: String? = null,
    danger: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .fillMaxWidth()
            .clip(Radius.Md)
            .background(
                when {
                    focused -> Color.White
                    danger -> AppColors.Danger.copy(alpha = 0.10f)
                    else -> AppColors.SurfaceHi.copy(alpha = 0.35f)
                }
            )
            .tvFocusRing(focused)
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                color = when {
                    focused && danger -> AppColors.Danger
                    focused -> Color.Black
                    danger -> AppColors.Warn
                    else -> TvText.Title
                },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            if (!hint.isNullOrBlank()) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (focused) Color.Black.copy(alpha = 0.6f) else TvText.Meta
                )
            }
        }
    }
}

/** 空状态提示 —— 主文 headlineSmall，下面补一条更弱的 hint；可选挂一个可聚焦操作（如重试） */
@Composable
fun TvEmptyState(
    text: String,
    hint: String? = null,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier
            .fillMaxSize()
            .padding(48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text,
            color = if (LocalTvPageDark.current) TvPageText.Section else TvText.Section,
            style = MaterialTheme.typography.headlineSmall
        )
        if (hint != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = hint,
                // hint 比主文低两档，别抢注意力
                color = if (LocalTvPageDark.current) TvPageText.Meta else TvText.Meta,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}

/**
 * TV 端元信息行：年份 · 类型 · 时长，中间用圆点分隔。
 * 评分不在这里显示 —— 海报背景上已带 IMDb 分，重复出现会打架。
 * 权重介于标题与徽章之间：bodyMedium + Meta 级白。
 */
@Composable
fun TvMetaRow(item: MediaEntity, modifier: Modifier = Modifier) {
    val bits = buildList {
        if (item.year > 0) add(item.year.toString())
        if (item.genres.isNotBlank()) add(item.genres.split(",", "/", " ", "，").first().trim())
        if (item.durationMs > 0) add("${item.durationMs / 60_000} 分钟")
    }.filter { it.isNotBlank() }

    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        val onDark = LocalTvPageDark.current
        bits.forEachIndexed { index, s ->
            if (index > 0) {
                Text(
                    text = "  ·  ",
                    color = if (onDark) TvPageText.Hairline else TvText.Hairline,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(
                text = s,
                color = if (onDark) TvPageText.Meta else TvText.Meta,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * 页面左右边距：电视端所有页面共用这一条左轴。
 *
 * 取 40dp（1080p 电视上约等于屏宽的 4~5%），和 Netflix 电视端的页面留白同一量级：
 * 元素不贴屏边，屏幕不被塞满，聚焦描边也天然有处可放。
 * Hero 文案、顶栏、行标题、海报行、网格页都从这里取值，
 * 任何一处单独写死就会立刻出现第二条左轴。
 */
internal val TvPagePadding = 40.dp

/**
 * 海报网格的最小格宽（dp）。
 *
 * 96dp 是 1080p 上「缩略图 = 小」档的真实格宽（(880 - 7×16) / 8 = 96dp）。
 * 再窄，海报上的字和脸在三米外就糊成一片，那一档就不该排这么多列。
 */
internal val TvPosterMinCellWidth = 96.dp

/**
 * 来源卡片的最小格宽（dp）。
 *
 * 来源卡带名称、徽章、URL 和统计行，比海报重得多，格宽要留够；
 * 280dp 对应 1080p 上的三列布局（(880 - 2×16) / 3 = 282dp）。
 */
internal val TvSourceMinCellWidth = 280.dp

/**
 * 电视端网格列数统一入口。以前各写各的：海报网格只认档位不看屏宽（720p 也排 8 列、
 * 每格 56dp 认不出内容），来源页只看屏宽不认档位（两页列宽对不上）。
 * 现在统一为：按最小格宽算屏宽能排几列（自然列数），再被档位封顶、被下限兜底。
 */
fun tvGridColumns(
    screenWidth: Dp,
    minCellWidth: Dp,
    maxColumns: Int,
    minColumns: Int = 1,
    horizontalPadding: Dp = TvPagePadding * 2,
    gap: Dp = 16.dp
): Int {
    val usable = screenWidth - horizontalPadding
    if (usable <= 0.dp) return minColumns.coerceAtMost(maxColumns)
    // 列间距也要占地方：usable 里要先把 n-1 段间距扣掉，所以按 (usable+gap)/(min+gap) 反解
    val byWidth = ((usable + gap) / (minCellWidth + gap)).toInt()
    return byWidth.coerceIn(minColumns.coerceAtMost(maxColumns), maxColumns)
}

/**
 * 海报网格列数：档位封顶（8/6/4），屏宽兜底。
 *
 * 所有 TV 网格页（分类 / 查看全部 / 搜索 / NF 浏览 / NF 我的）都取这个值，
 * 避免各处各写一套。1080p 上仍是 8 / 6 / 4 三档，与旧规则一致。
 */
@Composable
fun tvPosterColumns(): Int = tvGridColumns(
    screenWidth = LocalConfiguration.current.screenWidthDp.dp,
    minCellWidth = TvPosterMinCellWidth,
    maxColumns = when (Prefs.posterSize) {
        Prefs.PosterSize.SMALL -> 8
        Prefs.PosterSize.MEDIUM -> 6
        Prefs.PosterSize.LARGE -> 4
    }
)

/** 电视端可聚焦的小尺寸标签（题材筛选等） */
@Composable
fun TvFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** 拿到焦点时回调一次：横向 chip 排被裁掉时，靠它把焦点项滚进可视区 */
    onFocused: (() -> Unit)? = null
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            // 与 TvPillButton 同档圆角：这颗原来是胶囊，和同一屏里的按钮不是一套元件
            .clip(Radius.Md)
            .background(
                when {
                    focused -> Color.White
                    selected -> AppColors.Brand
                    else -> AppColors.SurfaceHi
                }
            )
            .tvFocusRing(focused)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused?.invoke()
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 20.dp, vertical = 9.dp)
    ) {
        Text(
            text,
            color = when {
                focused -> Color.Black
                // 选中态落在品牌实底上：白字两种颜色模式下都成立（Title 在浅色下会翻近黑）
                selected -> Color.White
                else -> TvText.Body
            },
            // 徽章级：labelMedium，比正文轻一档
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1
        )
    }
}

/**
 * 电视端分类栏右端的刮削按钮。
 *
 * 高度必须和分类 chip 一致：横向排布时两者上下边不齐会很明显。
 * 这里直接沿用 [TvFilterChip] 的 padding（horizontal 20 / vertical 9）与 labelMedium 字号。
 * 空闲态用主按钮材质（品牌渐变实底 + 上缘高光 + 黑字），三米外一眼认出这是动作键
 * 而不是又一个分类；焦点态仍是白底黑字，焦点体系不动。跑批时同一颗变进度 + 取消。
 */
@Composable
fun TvScrapeChip(
    running: Boolean,
    done: Int,
    total: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val brand = AppColors.Brand
    val brandSoft = AppColors.BrandSoft
    Box(
        modifier
            .clip(Radius.Md)
            .background(
                if (focused) SolidColor(Color.White)
                else Brush.verticalGradient(ButtonMaterial.primaryStops(brand, brandSoft))
            )
            .then(
                if (focused) Modifier
                else Modifier.border(1.dp, ButtonMaterial.highlightEdge, Radius.Md)
            )
            .tvFocusRing(focused)
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 20.dp, vertical = 9.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (running) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = Color.Black,
                    modifier = Modifier.size(13.dp)
                )
            }
            Text(
                text = if (running) "$done/$total 取消" else "刮削",
                color = Color.Black,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

/**
 * 电视端批量刮削的确认弹窗。
 *
 * 形态跟首页「继续观看」长按菜单一致（Compose Dialog + tvMoveFocus 抢焦，规范 R1/R5）：
 * 独立窗口天然把焦点关在弹窗里，Back 由窗口自己处理。
 * 行样式复用 [TvMenuRow]，与其它电视端菜单同一套视觉。
 */
@Composable
fun TvBatchScrapeDialog(
    total: Int,
    skipped: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // 等一帧：Dialog 内容要先组合完成，requester 才挂得上
        withFrameNanos { }
        tvMoveFocus(firstFocus)
    }

    Dialog(
        onDismissRequest = onDismiss,
        // 关掉平台默认窄宽度，宽度由弹窗自己按屏幕尺寸决定
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(
            Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            val dialogWidth = (maxWidth * 0.34f).coerceIn(380.dp, 640.dp)
            Column(
                Modifier
                    .width(dialogWidth)
                    .background(AppColors.SurfaceHi, Radius.Lg)
                    .padding(vertical = 26.dp)
            ) {
                Text(
                    text = "批量刮削",
                    color = TvText.Title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 26.dp)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = batchScrapeConfirmText(total, skipped),
                    color = TvText.Meta,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 26.dp)
                )
                Spacer(Modifier.height(18.dp))
                Column(
                    Modifier.padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TvMenuRow(
                        text = "开始刮削",
                        hint = "共 $total 部",
                        modifier = Modifier.focusRequester(firstFocus),
                        onClick = onConfirm
                    )
                    TvMenuRow(text = "取消", onClick = onDismiss)
                }
            }
        }
    }
}
