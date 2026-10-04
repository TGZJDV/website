package com.tgzjdv.music.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 与网站一致的配色
val AppSurface = Color(0xFF121212)
val AppSurface2 = Color(0xFF1E1E1E)
val AppSurface3 = Color(0xFF2A2A2A)
val AppPrimary = Color(0xFF0EA5E9)
val AppPrimaryDark = Color(0xFF0284C7)
val AppAccent = Color(0xFFE91E63)
val AppText = Color(0xFFF5F5F5)
val AppMuted = Color(0xFFA0A0A0)

private val DarkColors = darkColorScheme(
    primary = AppPrimary,
    onPrimary = Color.White,
    primaryContainer = AppPrimaryDark,
    onPrimaryContainer = Color.White,
    secondary = AppAccent,
    onSecondary = Color.White,
    background = AppSurface,
    onBackground = AppText,
    surface = AppSurface,
    onSurface = AppText,
    surfaceVariant = AppSurface2,
    onSurfaceVariant = AppMuted,
    outline = AppSurface3,
    outlineVariant = AppSurface3,
    surfaceContainer = AppSurface2,
    surfaceContainerHigh = AppSurface3,
    error = Color(0xFFEF4444),
)

/** 应用主题（强制深色，贴合音乐播放场景） */
@Composable
fun FamousMusicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content,
    )
}
