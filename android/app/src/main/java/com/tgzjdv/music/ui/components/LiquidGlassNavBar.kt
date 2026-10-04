package com.tgzjdv.music.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import com.tgzjdv.music.ui.Routes
import com.tgzjdv.music.ui.theme.AppMuted
import com.tgzjdv.music.ui.theme.AppPrimary
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
import kotlin.math.abs
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
 * ⚠️ 回 HOME 时必须 `inclusive = true`：
 * `popUpTo(HOME)` 之后栈顶**已经**是 HOME，紧接着再 `navigate(HOME) + launchSingleTop`
 * 会被 NavController 当成「栈顶已是同一个目的地」而整体忽略 ——
 * 表现就是「别的 tab 都能切，唯独切不回主页」。
 */
fun navigateToTab(nav: NavHostController, route: String) {
    nav.navigate(route) {
        popUpTo(Routes.HOME) { inclusive = (route == Routes.HOME) }
        launchSingleTop = true
    }
}

/**
 * 液态玻璃底栏 —— 参照本机「蓝河工具箱」6.15 的底部导航栏。
 *
 * 结构（注意三块是**兄弟节点**，不是父子）：
 *  ① **背板**：`drawBackdrop` 玻璃胶囊（单独一层）
 *  ② **图标层**：显示 + 用 `layerBackdrop` 暴露给水滴做折射源
 *  ③ **水滴**：独立的液态玻璃透镜，按下放大并可**凸出背板**
 *
 * ⚠️ ③ 必须放在 ① 的**外面**：`drawBackdrop` 内部会给自己的内容层设置
 * `clip = true + shape = 胶囊`（见库源码 DrawBackdropModifier.layoutLayerBlock），
 * 水滴若是 ① 的子节点，一旦放大凸出胶囊就会被**直接裁掉**。
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

    val tabsBackdrop = rememberLayerBackdrop()
    val scope = rememberCoroutineScope()

    val innerPadPx = with(density) { 5.dp.toPx() }
    var barWidth by remember { mutableStateOf(0f) }
    var barHeight by remember { mutableStateOf(0f) }
    // 图标行**内层**宽度（已扣掉水平 padding）。
    // 命中判定必须用它来算，不能拿 barWidth 再手工减 padding —— 坐标系容易错位。
    var rowWidth by remember { mutableStateOf(0f) }
    val itemWidth = if (barWidth > 0f) (barWidth - innerPadPx * 2) / itemCount else 0f

    // 水滴位置（拖动跟手 + 松手吸附最近 tab）
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
            .height(60.dp),
    ) {
        // ① 背板：真·液态玻璃。单独一层，这样它内部的 clip 只作用于自己。
        Box(
            modifier = Modifier
                .matchParentSize()
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
                )
                .onSizeChanged {
                    barWidth = it.width.toFloat()
                    barHeight = it.height.toFloat()
                }
                .drawBehind {
                    // 稍微压暗，提升图标可读性
                    drawRoundRect(
                        color = Color.Black.copy(alpha = 0.22f),
                        cornerRadius = CornerRadius(barHeight / 2f),
                    )
                },
        )

        // ② 图标层：显示 + 作为水滴的折射源；同时统一处理「点」和「拖」
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 5.dp)
                .layerBackdrop(tabsBackdrop)
                .onSizeChanged { rowWidth = it.width.toFloat() }
                .pointerInput(itemWidth, itemCount, selected, rowWidth) {
                    if (itemWidth <= 0f || rowWidth <= 0f) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startX = down.position.x
                        // ⚠️ 这里必须用**向下取整**，不能用 roundToInt：
                        // 按在第 k 个按钮上时 (startX/itemWidth) ∈ [k, k+1)，
                        // round 会把右半边判成 k+1（按第三个蹦到第四个）；
                        // 而且坐标已经是行内坐标，不能再手工减 padding。
                        val startIndex = ((startX / rowWidth) * itemCount)
                            .toInt()
                            .coerceIn(0, itemCount - 1)
                        // 只有按在**当前页所属的按钮**上才进入拖动模式（按下放大 + 跟手）
                        val dragging = startIndex == selected
                        if (dragging) {
                            scope.launch { press.animateTo(1f, spring(0.6f, 500f, 0.001f)) }
                        }

                        var totalDx = 0f
                        var moved = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val dx = change.position.x - change.previousPosition.x
                            if (dx != 0f && dragging) {
                                totalDx += dx
                                if (abs(totalDx) > viewConfiguration.touchSlop) moved = true
                                scope.launch {
                                    thumbPos.snapTo(
                                        (thumbPos.value + dx / itemWidth)
                                            .coerceIn(0f, (itemCount - 1).toFloat()),
                                    )
                                }
                                change.consume()
                            }
                        }

                        if (dragging) {
                            scope.launch { press.animateTo(0f, spring(0.6f, 500f, 0.001f)) }
                        }

                        val targetIndex = (if (dragging && moved) {
                            thumbPos.value.roundToInt()
                        } else {
                            startIndex
                        }).coerceIn(0, itemCount - 1)

                        scope.launch {
                            thumbPos.animateTo(
                                targetIndex.toFloat(),
                                spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                            )
                        }
                        if (targetIndex != selected) {
                            navigateToTab(nav, appNavDests[targetIndex].route)
                        }
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

        // ③ 水滴：独立液态玻璃透镜。**在背板外面**，放大凸出时不会被裁掉。
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
                        // 按下变大，凸出胶囊边框
                        val s = 1f + 0.50f * press.value
                        scaleX = s
                        scaleY = s
                    }
                    .drawBackdrop(
                        backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                        shape = { capsule },
                        effects = {
                            val p = press.value
                            // 只折射不模糊 —— 水滴要的是「透明 + 边缘折射」，模糊会变毛玻璃
                            lens(
                                refractionHeight = 14f.dp.toPx() * (0.35f + 0.65f * p),
                                refractionAmount = 20f.dp.toPx() * (0.35f + 0.65f * p),
                                depthEffect = true,
                                chromaticAberration = true,
                            )
                        },
                        // 常驻描边：水滴凸出背板后背后就是普通内容，只靠折射会「隐形」，
                        // 必须靠高光 / 投影 / 内阴影把轮廓勾出来
                        highlight = { Highlight.Default.copy(alpha = 0.45f + 0.55f * press.value) },
                        shadow = { Shadow(alpha = 0.28f + 0.42f * press.value) },
                        innerShadow = {
                            InnerShadow(
                                radius = 6.dp + 6.dp * press.value,
                                alpha = 0.35f + 0.65f * press.value,
                            )
                        },
                        onDrawSurface = {
                            val p = press.value
                            drawRect(Color.White.copy(alpha = 0.10f), alpha = 1f - p)
                            drawRect(Color.White.copy(alpha = 0.07f * p))
                        },
                    ),
            )
        }
    }
}
