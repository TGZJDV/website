package com.tgzjdv.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tgzjdv.music.ui.theme.AppSurface2
import com.tgzjdv.music.ui.theme.LocalGlassBackdrop

/*
 * ⚠️⚠️ 为什么这里**不用** drawBackdrop（重要，别改回来）⚠️⚠️
 *
 * 界面里的卡片/按钮都位于 `AppRoot` 的 `layerBackdrop(backdrop)` **采样层内部**。
 * 如果在层内部再调 `drawBackdrop(backdrop)`，就会「在自己的采样层里画自己」——
 * 自引用循环。HWUI 在 DirtyStack::computeTransformImpl 里沿父链递归，
 * 遇到环就无限递归 → 栈溢出 → 原生崩溃：
 *
 *     F/libc: Fatal signal 11 (SIGSEGV) ... in tid RenderThread
 *     Cause: stack pointer is not in a rw map; likely due to stack overflow.
 *     512 total frames ... libhwui.so computeTransformImpl
 *
 * 底栏 / 迷你播放条 / 悬浮按钮在采样层**外面**（兄弟节点），所以它们用
 * `drawBackdrop` 是真玻璃、也不会崩。
 *
 * 层内部只能用「静态玻璃」：半透明底 + 白色渐变 + 高光描边 + 投影。
 * 视觉上够用，且不会有循环依赖。
 */

/** 静态玻璃的通用实现 */
private fun Modifier.staticGlass(
    shape: Shape,
    base: Color,
    elevation: Dp,
): Modifier = this
    .shadow(
        elevation = elevation,
        shape = shape,
        ambientColor = Color.Black.copy(alpha = 0.45f),
        spotColor = Color.Black.copy(alpha = 0.55f),
    )
    .clip(shape)
    // 半透明冷灰打底：纯 alpha 叠在近黑背景上会糊成脏灰
    .background(base)
    // 白色竖向渐变 = 玻璃受光面
    .background(
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.10f),
                Color.White.copy(alpha = 0.05f),
                Color.White.copy(alpha = 0.02f),
            ),
        ),
    )
    // 顶部高光描边 = 玻璃厚度
    .border(
        width = 1.dp,
        brush = Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.22f),
                Color.White.copy(alpha = 0.07f),
                Color.White.copy(alpha = 0.03f),
            ),
        ),
        shape = shape,
    )

/**
 * 通用「卡片 / 面板」材质。
 *
 * - **经典样式**（[LocalGlassBackdrop] 为 null）：原来的 `.clip(shape).background(AppSurface2)`
 * - **液态玻璃样式**：静态玻璃（原因见文件头注释 —— 这里不能用 drawBackdrop）
 */
@Composable
fun Modifier.glassPanel(
    shape: Shape,
    fallback: Color = AppSurface2,
    /** 玻璃模式下的半透明底色 */
    base: Color = AppSurface2.copy(alpha = 0.72f),
    elevation: Dp = 6.dp,
): Modifier {
    val glass = LocalGlassBackdrop.current != null
    return if (glass) this.staticGlass(shape, base, elevation)
    else this.clip(shape).background(fallback)
}

/**
 * 圆形玻璃按钮（播放/暂停等）。
 * 经典样式下退化为 [fallback] 实心圆。
 */
@Composable
fun Modifier.glassCircle(
    fallback: Color,
    base: Color = AppSurface2.copy(alpha = 0.62f),
    elevation: Dp = 10.dp,
): Modifier {
    val glass = LocalGlassBackdrop.current != null
    return if (glass) this.staticGlass(CircleShape, base, elevation)
    else this.background(fallback, CircleShape)
}
