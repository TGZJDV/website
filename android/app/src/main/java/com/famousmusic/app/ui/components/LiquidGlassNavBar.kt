package com.famousmusic.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.famousmusic.app.ui.Routes
import com.famousmusic.app.ui.theme.AppMuted
import com.famousmusic.app.ui.theme.AppPrimary
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow

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
 * 液态玻璃底栏 —— 参照本机「蓝河工具箱」6.15 的底部导航栏：
 * 悬浮胶囊 + 半透明玻璃 + 顶部高光描边 + 滑动圆角滑块 + 选中项着主色。
 */
@Composable
fun LiquidGlassNavBar(
    nav: NavHostController,
    currentRoute: String?,
    backdrop: Backdrop,
) {
    val selected = navDestIndex(currentRoute).coerceAtLeast(0)

    // 滑块位置用 spring 跟随，形成「液态」滑动手感
    val thumb by animateFloatAsState(
        targetValue = selected.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "glassThumb",
    )

    val capsule = RoundedCornerShape(percent = 50)
    val density = LocalDensity.current
    val innerPadPx = with(density) { 5.dp.toPx() }

    var barWidth by remember { mutableStateOf(0f) }
    var barHeight by remember { mutableStateOf(0f) }
    val itemWidth = if (barWidth > 0f) (barWidth - innerPadPx * 2) / appNavDests.size else 0f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .height(60.dp)
            // ↓↓↓ 真·液态玻璃：采样「底栏背后的真实内容」做模糊 + 透镜折射 + 通透 + 边缘高光
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
                if (itemWidth <= 0f || barHeight <= 0f) return@drawBehind
                val h = barHeight - innerPadPx * 2
                val x = innerPadPx + itemWidth * thumb

                // ① 选中滑块：**磨砂白玻璃**（对齐蓝河工具箱：白滑块 + 蓝色图标）
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.20f),
                            Color.White.copy(alpha = 0.08f),
                        ),
                    ),
                    topLeft = Offset(x, innerPadPx),
                    size = Size(itemWidth, h),
                    cornerRadius = CornerRadius(h / 2f),
                )

                // ② 滑块描边：玻璃厚度
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.30f),
                    topLeft = Offset(x + 0.6f, innerPadPx + 0.6f),
                    size = Size(itemWidth - 1.2f, h - 1.2f),
                    cornerRadius = CornerRadius(h / 2f),
                    style = Stroke(width = 1.2f),
                )

                // ③ 顶部内高光
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.38f), Color.Transparent),
                    ),
                    topLeft = Offset(x + h * 0.20f, innerPadPx + 2f),
                    size = Size((itemWidth - h * 0.40f).coerceAtLeast(0f), 2f),
                    cornerRadius = CornerRadius(1f),
                )
            },
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 5.dp),
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
                        .clip(RoundedCornerShape(percent = 50))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            if (!active) navigateToTab(nav, dest.route)
                        },
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
    }
}
