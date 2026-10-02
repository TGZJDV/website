package com.famousmusic.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.famousmusic.app.ui.Routes
import com.famousmusic.app.ui.theme.AppMuted
import com.famousmusic.app.ui.theme.AppPrimary
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 一个底部导航项 */
data class NavDest(val route: String, val label: String, val icon: ImageVector)

/** 五个 tab（两种底栏样式共用，保证行为一致） */
val appNavDests: List<NavDest> = listOf(
    NavDest(Routes.HOME, "发现", Icons.Rounded.Home),
    NavDest(Routes.SEARCH, "搜索", Icons.Rounded.Search),
    NavDest(Routes.GENRES, "分类", Icons.Rounded.Category),
    NavDest(Routes.PLAYLISTS, "歌单", Icons.Rounded.QueueMusic),
    NavDest(Routes.ME, "我的", Icons.Rounded.Person),
)

/** 当前路由对应哪个 tab（-1 = 不在任何 tab 上） */
fun navDestIndex(currentRoute: String?): Int = appNavDests.indexOfFirst { dest ->
    currentRoute == dest.route ||
        (dest.route == Routes.GENRES && currentRoute == Routes.GENRES_WITH_ARG)
}

/**
 * 点底栏统一回到该 tab 的根页面。
 *
 * ⚠️ 刻意不用 saveState / restoreState：
 * 非 inclusive 的 popUpTo 会被 NavController 把保存的状态映射到 popUpTo 目标
 * （startDestination = home）的 id 上；而我们紧接着又导航回 home，NavController
 * 发现 backStackMap 里已有 home 的键，就会立刻把刚弹掉的详情页原样恢复出来 ——
 * 表现就是「点底栏没反应」以及「切到别的 tab 再切回来仍停在详情页」。
 */
fun navigateToTab(nav: NavHostController, route: String) {
    nav.navigate(route) {
        popUpTo(nav.graph.startDestinationId) { inclusive = false }
        launchSingleTop = true
    }
}

/**
 * 液态玻璃底栏 —— 参照本机「蓝河工具箱」6.15 的底部导航栏。
 *
 * 结构（与官方 LiquidBottomTabs 同构）：
 *  ① **背板**：整条胶囊，`drawBackdrop` 采样屏幕内容做模糊/折射（保持不动）
 *  ② **图标层**：既是显示层，又用 `layerBackdrop` 暴露给滑块采样
 *  ③ **选中块**：一块**独立的液态玻璃透镜** —— 不是填充色！
 *     - 平时只有 10% 白（几乎透明）→ 所以像水滴而不是塑料板
 *     - 采样「屏幕内容 + 图标层」，`lens()` 让边缘产生真实折射
 *     - 按下/拖动时折射加强、`Highlight` + `Shadow` + `InnerShadow` 浮现、整体放大
 *     - 水平拖动可跨 tab 切换
 */
@Composable
fun LiquidGlassNavBar(
    nav: NavHostController,
    currentRoute: String?,
    backdrop: Backdrop,
) {
    val selected = navDestIndex(currentRoute).coerceAtLeast(0)
    val capsule = RoundedCornerShape(percent = 50)
    val density = LocalDensity.current
    val itemCount = appNavDests.size

    // ② 图标层单独一层，供选中块的透镜折射
    val tabsBackdrop = rememberLayerBackdrop()
    val scope = rememberCoroutineScope()

    val innerPadPx = with(density) { 5.dp.toPx() }
    var barWidth by remember { mutableStateOf(0f) }
    var barHeight by remember { mutableStateOf(0f) }
    val itemWidth = if (barWidth > 0f) (barWidth - innerPadPx * 2) / itemCount else 0f

    // ③ 选中块的滑动位置（拖动跟手 + 松手回弹到最近 tab）
    val thumbPos = remember { Animatable(selected.toFloat()) }
    // 按下进度 0..1：驱动折射强度 / 高光 / 内阴影 / 缩放
    val press = remember { Animatable(0f) }

    LaunchedEffect(selected) {
        thumbPos.animateTo(
            selected.toFloat(),
            spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .height(60.dp)
            // ① 背板：真·液态玻璃（采样屏幕内容）—— 保持不动
            .drawBackdrop(
                backdrop = backdrop,
                shape = { capsule },
                effects = {
                    vibrancy()
                    blur(radius = 14f.dp.toPx())
                    lens(
                        refractionHeight = 20f.dp.toPx(),
                        refractionAmount = 20f.dp.toPx(),
                        depthEffect = true,
                        chromaticAberration = true,
                    )
                },
                highlight = { Highlight.Default },
                shadow = { Shadow(alpha = 0.35f) },
                onDrawSurface = null,
            )
            .onSizeChanged {
                barWidth = it.width.toFloat()
                barHeight = it.height.toFloat()
            }
            .drawBehind {
                // 只做一点压暗（提升图标可读性）。
                // 不要用高不透明度底色去「统一各页面」——那会把玻璃糊死。
                // 各页面观感一致靠的是采样层里始终有不透明底（见 AppNav）。
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.22f),
                    cornerRadius = CornerRadius(barHeight / 2f),
                )
            },
    ) {
        // ② 图标层：显示 + 作为选中块的采样源
        //    「点」和「拖」都在这一层统一处理：拖动滑块切换 + 点击直达
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 5.dp)
                .layerBackdrop(tabsBackdrop)
                .pointerInput(itemWidth, itemCount) {
                    if (itemWidth <= 0f) return@pointerInput
                    detectDragGestures(
                        onDragStart = {
                            scope.launch { press.animateTo(1f, spring(0.6f, 400f, 0.001f)) }
                        },
                        onDragEnd = {
                            scope.launch { press.animateTo(0f, spring(0.6f, 400f, 0.001f)) }
                            val target = thumbPos.value.roundToInt().coerceIn(0, itemCount - 1)
                            scope.launch {
                                thumbPos.animateTo(
                                    target.toFloat(),
                                    spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMediumLow,
                                    ),
                                )
                            }
                            if (target != selected) navigateToTab(nav, appNavDests[target].route)
                        },
                        onDragCancel = {
                            scope.launch { press.animateTo(0f, spring(0.6f, 400f, 0.001f)) }
                            scope.launch {
                                thumbPos.animateTo(
                                    selected.toFloat(),
                                    spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMediumLow,
                                    ),
                                )
                            }
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            thumbPos.snapTo(
                                (thumbPos.value + dragAmount.x / itemWidth)
                                    .coerceIn(0f, (itemCount - 1).toFloat()),
                            )
                        }
                    }
                }
                .pointerInput(itemWidth, itemCount, selected) {
                    if (itemWidth <= 0f) return@pointerInput
                    detectTapGestures { pos ->
                        val idx = ((pos.x - innerPadPx) / itemWidth)
                            .roundToInt()
                            .coerceIn(0, itemCount - 1)
                        if (idx != selected) navigateToTab(nav, appNavDests[idx].route)
                    }
                },
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            appNavDests.forEachIndexed { index, dest ->
                val active = index == selected
                val tint by animateColorAsState(
                    targetValue = if (active) AppPrimary else AppMuted,
                    label = "glassTint$index",
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(percent = 50)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        dest.icon,
                        contentDescription = dest.label,
                        tint = tint,
                        modifier = Modifier.size(23.dp),
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        dest.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        color = tint,
                    )
                }
            }
        }

        // ③ 选中块：独立的液态玻璃透镜（水滴感）
        if (itemWidth > 0f) {
            val thumbWidthDp = with(density) { itemWidth.toDp() }
            val innerPadDp = with(density) { innerPadPx.toDp() }
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (innerPadPx + itemWidth * thumbPos.value).roundToInt(),
                            y = 0,
                        )
                    }
                    .width(thumbWidthDp)
                    .fillMaxHeight()
                    .padding(vertical = innerPadDp)
                    .graphicsLayer {
                        // 按下变大，且要**超出背板边框**（1.5 倍：50dp → 75dp，比 60dp 的背板还高）
                        val s = 1f + 0.50f * press.value
                        scaleX = s
                        scaleY = s
                    }
                    .drawBackdrop(
                        // 采样「屏幕内容 + 图标层」→ 滑块能折射它下面的图标
                        backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                        shape = { capsule },
                        effects = {
                            val p = press.value
                            // 平时也有轻微折射（边缘能看到弯折），按下明显增强
                            lens(
                                refractionHeight = 14f.dp.toPx() * (0.30f + 0.70f * p),
                                refractionAmount = 18f.dp.toPx() * (0.30f + 0.70f * p),
                                depthEffect = true,
                                chromaticAberration = true,
                            )
                        },
                        highlight = { Highlight.Default.copy(alpha = 0.30f + 0.70f * press.value) },
                        shadow = { Shadow(alpha = 0.15f + 0.55f * press.value) },
                        innerShadow = {
                            InnerShadow(radius = 8f.dp * press.value, alpha = press.value)
                        },
                        onDrawSurface = {
                            // 关键：平时只有 10% 白 —— 这才像「水滴」而不是实心板子
                            drawRect(
                                Color.White.copy(alpha = 0.10f),
                                alpha = 1f - press.value,
                            )
                            drawRect(Color.Black.copy(alpha = 0.03f * press.value))
                        },
                    ),
            )
        }
    }
}
