package com.tgzjdv.music.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.tgzjdv.music.ui.theme.AppSurface2
import com.tgzjdv.music.ui.theme.AppText
import com.tgzjdv.music.ui.theme.GlassTuningStore
import com.tgzjdv.music.ui.theme.LocalGlassBackdrop

/*
 * ⚠️ 关于这里为什么**不能**采样内容层（崩溃排查记录，别踩第二次）
 *
 * 页面内部的卡片/按钮位于 `AppRoot` 内容层 `layerBackdrop(contentBackdrop)` **内部**。
 * 如果它们采样 contentBackdrop，就是「在自己的采样层里画自己」→ 自引用环 →
 * HWUI 在 DirtyStack::computeTransformImpl 沿父链无限递归 → 栈溢出：
 *
 *     F/libc: Fatal signal 11 (SIGSEGV) in tid RenderThread
 *     Cause: stack pointer is not in a rw map; likely due to stack overflow.
 *     512 total frames ... libhwui.so computeTransformImpl
 *
 * 因此 `AppRoot` 额外准备了一份**独立背景层**（内容层的兄弟节点，只画背景），
 * 通过 [LocalGlassBackdrop] 提供给页面内部使用 —— 那才是这里采样的对象。
 * 底栏 / 迷你播放条在内容层**外面**，它们直接采内容层，所以能折射滚动的内容。
 */

/**
 * 通用「卡片 / 面板」材质。
 *
 * - **经典样式**（[LocalGlassBackdrop] 为 null）：`.clip(shape).background(AppSurface2)`
 * - **液态玻璃样式**：真玻璃 —— 模糊 + 透镜折射 + 通透 + 边缘高光 + 投影
 */
@Composable
fun Modifier.glassPanel(
    shape: Shape,
    fallback: Color = AppSurface2,
    /** 玻璃之上的压暗层：亮背景下保证文字可读 */
    tint: Color = Color.Black.copy(alpha = 0.20f),
    blurRadius: Dp = 14.dp,
): Modifier {
    val backdrop = LocalGlassBackdrop.current
    val clipped = this.clip(shape)
    if (backdrop == null) return clipped.background(fallback)

    // 「设置 → 玻璃通透度」：0 = 最实（满模糊+满压暗），1 = 最通透（几乎不模糊、不压暗）
    // ⚠️ 只调压暗层是不够的 —— 那样只是"糊得更透"，用户要的是"清楚"。所以模糊也要跟着削弱。
    val translucency by GlassTuningStore.translucency.collectAsState()

    return clipped
        .drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                // 注意：blur/lens 的 px 换算必须在 effects lambda 里做 —— 它是 Density 作用域
                val b = blurRadius.toPx() * (1f - translucency * 0.96f)
                if (b > 0.5f) blur(radius = b)
                // 只在面板够大时才启用透镜折射（AGSL 一笔不小的开销）。
                // 另外 lens 走圆角矩形 SDF：尺寸过小 / 圆角半径超过边长时会算出非法值，
                // 同样会在 RenderThread 里 SIGSEGV。
                if (size.minDimension >= 120f) {
                    val l = 0.25f + 0.75f * (1f - translucency)
                    lens(
                        refractionHeight = 12f.dp.toPx() * l,
                        refractionAmount = 16f.dp.toPx() * l,
                        depthEffect = true,
                        chromaticAberration = true,
                    )
                }
            },
            highlight = { Highlight.Default },
            shadow = { Shadow(alpha = 0.30f * (0.4f + 0.6f * (1f - translucency))) },
        )
        .background(tint.copy(alpha = tint.alpha * (1f - translucency)))
}

/**
 * 「q 弹」点击：按下时整块玻璃轻微缩小，松手带弹簧回弹。
 * 比 Material 的涟漪更贴合液态玻璃的手感 —— 玻璃是"软"的。
 *
 * 用法：接在 [glassPanel] / [glassCircle] **之后**，整块玻璃（含折射层）一起缩放。
 */
@Composable
fun Modifier.bouncyClickable(
    enabled: Boolean = true,
    /** 按下时缩到多小：越小越"弹" */
    pressedScale: Float = 0.94f,
    onClick: () -> Unit,
): Modifier {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    return this
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
        .pointerInput(enabled, pressedScale) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                scope.launch {
                    scale.animateTo(
                        pressedScale,
                        spring(dampingRatio = 0.32f, stiffness = 900f),
                    )
                }
                // 等手指抬起
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                }
                scope.launch {
                    scale.animateTo(
                        1f,
                        spring(dampingRatio = 0.30f, stiffness = 420f),
                    )
                }
                onClick()
            }
        }
}

/**
 * 透明圆形图标按钮（返回、收藏等）。
 *
 * - **经典样式**：完全透明，只有图标（就是"透明圆形按钮"）
 * - **液态玻璃样式**：一枚淡淡的玻璃圆片 + 白描边，悬在内容上
 */
@Composable
fun GlassIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 22.dp,
    tint: Color = AppText,
) {
    Box(
        modifier = modifier
            .size(size)
            .glassCircle(fallback = Color.Transparent)
            .bouncyClickable(pressedScale = 0.86f, onClick = onClick),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
fun Modifier.glassCircle(
    fallback: Color,
    tint: Color = Color.White.copy(alpha = 0.18f),
    blurRadius: Dp = 12.dp,
): Modifier {
    val backdrop = LocalGlassBackdrop.current
    if (backdrop == null) return this.background(fallback, CircleShape)

    val translucency by GlassTuningStore.translucency.collectAsState()

    return this
        .clip(CircleShape)
        .drawBackdrop(
            backdrop = backdrop,
            shape = { CircleShape },
            effects = {
                vibrancy()
                val b = blurRadius.toPx() * (1f - translucency * 0.96f)
                if (b > 0.5f) blur(radius = b)
                if (size.minDimension >= 100f) {
                    val l = 0.25f + 0.75f * (1f - translucency)
                    lens(
                        refractionHeight = 10f.dp.toPx() * l,
                        refractionAmount = 14f.dp.toPx() * l,
                        depthEffect = true,
                        chromaticAberration = true,
                    )
                }
            },
            highlight = { Highlight.Default },
            shadow = { Shadow(alpha = 0.40f * (0.4f + 0.6f * (1f - translucency))) },
        )
        .background(tint.copy(alpha = tint.alpha * (1f - translucency)))
}
