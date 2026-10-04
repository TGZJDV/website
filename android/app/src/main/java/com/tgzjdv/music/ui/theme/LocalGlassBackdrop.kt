package com.tgzjdv.music.ui.theme

import androidx.compose.runtime.compositionLocalOf
import com.kyant.backdrop.Backdrop

/**
 * 当前是否处于「液态玻璃」样式，以及玻璃要采样的 backdrop 层。
 *
 * - 「经典」样式：[null] → 各组件走原来的 Material 背景
 * - 「液态玻璃」样式：非空 → 组件可以用 [com.tgzjdv.music.ui.components.glassPanel] 变成玻璃
 *
 * 由 `AppRoot` 提供；内容是整屏的采样层，所以任何页面里的面板都能采到它背后的东西。
 */
val LocalGlassBackdrop = compositionLocalOf<Backdrop?> { null }
