package com.tgzjdv.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.tgzjdv.music.ui.theme.AppSurface2
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

    return clipped
        .drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(radius = blurRadius.toPx())
                // ⚠️ 只在面板够大时才启用透镜折射。
                // lens 走 AGSL 圆角矩形 SDF：尺寸过小 / 圆角半径超过边长时会算出非法值，
                // 同样会在 RenderThread 里 SIGSEGV。小面板只做模糊+通透，视觉无差别但安全。
                if (size.minDimension >= 120f) {
                    lens(
                        refractionHeight = 12f.dp.toPx(),
                        refractionAmount = 16f.dp.toPx(),
                        depthEffect = true,
                        chromaticAberration = true,
                    )
                }
            },
            highlight = { Highlight.Default },
            shadow = { Shadow(alpha = 0.30f) },
        )
        .background(tint)
}

/**
 * 圆形玻璃按钮（播放/暂停等）。
 * 经典样式下退化为 [fallback] 实心圆。
 */
@Composable
fun Modifier.glassCircle(
    fallback: Color,
    tint: Color = Color.White.copy(alpha = 0.18f),
    blurRadius: Dp = 12.dp,
): Modifier {
    val backdrop = LocalGlassBackdrop.current
    if (backdrop == null) return this.background(fallback, CircleShape)

    return this
        .clip(CircleShape)
        .drawBackdrop(
            backdrop = backdrop,
            shape = { CircleShape },
            effects = {
                vibrancy()
                blur(radius = blurRadius.toPx())
                if (size.minDimension >= 100f) {
                    lens(
                        refractionHeight = 10f.dp.toPx(),
                        refractionAmount = 14f.dp.toPx(),
                        depthEffect = true,
                        chromaticAberration = true,
                    )
                }
            },
            highlight = { Highlight.Default },
            shadow = { Shadow(alpha = 0.40f) },
        )
        .background(tint)
}
