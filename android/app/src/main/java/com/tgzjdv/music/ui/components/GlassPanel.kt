package com.tgzjdv.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
                // ⚠️ 只在面板够大时才启用透镜折射。
                // lens 走 AGSL 圆角矩形 SDF：尺寸过小 / 圆角半径超过边长时会算出非法值，
                // 同样会在 RenderThread 里 SIGSEGV。小面板只做模糊+通透，视觉无差别但安全。
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
            .glassCircle(fallback = Color.Transparent),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier
                .size(iconSize)
                .clickable(onClick = onClick),
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
