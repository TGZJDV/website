package com.tgzjdv.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
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

/**
 * 通用「卡片 / 面板」材质。
 *
 * - **经典样式**（[LocalGlassBackdrop] 为 null）：就是原来的 `.clip(shape).background(AppSurface2)`
 * - **液态玻璃样式**：换成真·玻璃 —— 采样背后的内容做模糊 + 透镜折射 + 通透 + 边缘高光
 *
 * 这样各页面的卡片在两种样式下自动切换，调用处不需要写 if。
 *
 * 用法：把原来的 `.clip(shape).background(AppSurface2)` 换成 `.glassPanel(shape)`。
 */
@Composable
fun Modifier.glassPanel(
    shape: Shape,
    /** 经典样式下的底色，默认与原来的卡片一致 */
    fallback: Color = AppSurface2,
    /** 玻璃之上的压暗层：保证文字/图标在亮背景（封面）上也读得清 */
    tint: Color = Color.Black.copy(alpha = 0.26f),
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
                lens(
                    refractionHeight = 12f.dp.toPx(),
                    refractionAmount = 14f.dp.toPx(),
                    depthEffect = true,
                    chromaticAberration = true,
                )
            },
            highlight = { Highlight.Default },
            shadow = { Shadow(alpha = 0.28f) },
        )
        .background(tint)
}

/**
 * 圆形玻璃按钮（播放/暂停、悬浮操作等）。
 * 经典样式下退化为 [fallback] 实心圆。
 */
@Composable
fun Modifier.glassCircle(
    fallback: Color,
    tint: Color = Color.White.copy(alpha = 0.22f),
    blurRadius: Dp = 12.dp,
): Modifier {
    val backdrop = LocalGlassBackdrop.current
    if (backdrop == null) return this.background(fallback, androidx.compose.foundation.shape.CircleShape)

    val shape = androidx.compose.foundation.shape.CircleShape
    return this
        .clip(shape)
        .drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(radius = blurRadius.toPx())
                lens(
                    refractionHeight = 10f.dp.toPx(),
                    refractionAmount = 12f.dp.toPx(),
                    depthEffect = true,
                    chromaticAberration = true,
                )
            },
            highlight = { Highlight.Default },
            shadow = { Shadow(alpha = 0.40f) },
        )
        .background(tint)
}
