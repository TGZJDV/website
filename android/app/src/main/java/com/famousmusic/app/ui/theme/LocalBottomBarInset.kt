package com.famousmusic.app.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 底部浮层（迷你播放条 + 导航栏）需要占用的高度。
 *
 * - 「经典」样式：底栏由 Scaffold 的 bottomBar 预留空间，这里为 [0.dp]
 * - 「液态玻璃」样式：底栏浮在内容之上，内容要**滚到它后面**（玻璃才有东西可采样），
 *   所以各页面的滚动容器需要在自己的 contentPadding 里加上这个高度，
 *   否则列表最后一项会被永久压在底栏下面。
 */
val LocalBottomBarInset = compositionLocalOf { 0.dp }
