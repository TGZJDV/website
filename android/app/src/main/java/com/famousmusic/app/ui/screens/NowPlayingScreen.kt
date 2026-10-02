package com.famousmusic.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.famousmusic.app.data.ApiClient
import com.famousmusic.app.playback.LrcLine
import com.famousmusic.app.playback.LrcParser
import com.famousmusic.app.playback.PlayerManager
import com.famousmusic.app.session.AppSession
import com.famousmusic.app.ui.components.SongCover
import com.famousmusic.app.ui.theme.AppMuted
import com.famousmusic.app.ui.theme.AppPrimary
import com.famousmusic.app.ui.theme.AppSurface
import com.famousmusic.app.ui.theme.AppSurface2
import com.famousmusic.app.ui.theme.AppSurface3
import com.famousmusic.app.ui.theme.AppText
import com.famousmusic.app.util.formatDurationMs
import kotlinx.coroutines.launch

/** 全屏播放页：封面 + LRC 歌词同步 + 控制 */
@Composable
fun NowPlayingScreen(onBack: () -> Unit, onLogin: () -> Unit) {
    val state by PlayerManager.state.collectAsState()
    val song = state.song
    val user by AppSession.user.collectAsState()

    var lyrics by remember { mutableStateOf<List<LrcLine>>(emptyList()) }
    var lyricsLoading by remember { mutableStateOf(false) }
    // 收藏状态取自媒体元数据（PlayerManager 会根据元数据 RATING 同步），
    // 这样 App 内收藏 / 原子随身听收藏 / 切歌 三种情况都能自动一致
    val favorited = state.favorited
    val listState = rememberLazyListState()

    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // 加载歌词
    LaunchedEffect(song?.id) {
        val id = song?.id ?: run {
            lyrics = emptyList()
            return@LaunchedEffect
        }
        // 先把缓存歌词铺上，避免每次进播放页都空一下
        val cached = ApiClient.cachedLyrics(id)
        if (cached != null) {
            lyrics = LrcParser.parse(cached)
            lyricsLoading = false
        } else {
            lyrics = emptyList()
            lyricsLoading = true
        }
        runCatching { ApiClient.lyrics(id) }
            .onSuccess { lyrics = LrcParser.parse(it) }
        lyricsLoading = false
        if (user != null) {
            // 拉到真实收藏状态后写回元数据（随身听与 App 界面都据此刷新）
            runCatching { ApiClient.songDetail(id) }
                .onSuccess { PlayerManager.setFavorite(id, it.favorited) }
        }
    }

    if (song == null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("当前没有正在播放的歌曲", color = AppMuted)
            Spacer(Modifier.height(12.dp))
            Text("返回", color = AppPrimary, modifier = Modifier.clickable(onClick = onBack))
        }
        return
    }

    val lineIndex = LrcParser.currentIndex(lyrics, state.positionMs)

    LaunchedEffect(lineIndex) {
        if (lineIndex >= 0 && LrcParser.isTimed(lyrics)) {
            runCatching { listState.animateScrollToItem(lineIndex) }
        }
    }

    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val durationMs = state.durationMs.coerceAtLeast(1L)
    val progressValue = if (dragging) dragValue else state.positionMs.toFloat().coerceIn(0f, durationMs.toFloat())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(AppSurface2, com.famousmusic.app.ui.theme.AppSurface, androidx.compose.ui.graphics.Color.Black)
                )
            )
            .padding(horizontal = 16.dp),
    ) {
        // 顶栏
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "返回", tint = AppMuted)
            }
            Text("正在播放", color = AppMuted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.weight(1f))
            Text(song.genre, color = AppMuted, style = MaterialTheme.typography.bodySmall)
        }

        // 封面 + 信息
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(12.dp))
            SongCover(song = song, size = 240.dp, corner = 18.dp)
            Spacer(Modifier.height(16.dp))
            Text(
                song.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(song.displayArtist, color = AppMuted, style = MaterialTheme.typography.bodySmall)
        }

        // 歌词
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                lyricsLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("歌词加载中…", color = AppMuted, style = MaterialTheme.typography.bodySmall)
                }
                lyrics.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无歌词", color = AppMuted, style = MaterialTheme.typography.bodySmall)
                }
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    item { Spacer(Modifier.height(40.dp)) }
                    items(lyrics.size) { i ->
                        val line = lyrics[i]
                        val active = i == lineIndex
                        Text(
                            text = line.text.ifBlank { "♪" },
                            color = if (active) AppText else AppMuted,
                            style = if (active) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp, horizontal = 8.dp),
                        )
                    }
                    item { Spacer(Modifier.height(40.dp)) }
                }
            }
        }

        // 进度条
        Column(modifier = Modifier.fillMaxWidth()) {
            Slider(
                value = progressValue,
                onValueChange = { dragging = true; dragValue = it },
                onValueChangeFinished = {
                    PlayerManager.seekTo(dragValue.toLong())
                    dragging = false
                },
                valueRange = 0f..durationMs.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = AppPrimary,
                    activeTrackColor = AppPrimary,
                    inactiveTrackColor = AppSurface3,
                ),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(formatDurationMs(progressValue.toLong()), color = AppMuted, style = MaterialTheme.typography.labelSmall)
                Text(formatDurationMs(state.durationMs), color = AppMuted, style = MaterialTheme.typography.labelSmall)
            }

            // 控制按钮（进度条下方，移动端布局）
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {
                    if (user == null) {
                        onLogin()
                        return@IconButton
                    }
                    scope.launch {
                        runCatching {
                            if (favorited) ApiClient.unfavorite(song.id) else ApiClient.favorite(song.id)
                        }.onSuccess {
                            // 写回媒体元数据 → App 界面与原子随身听同时刷新
                            PlayerManager.setFavorite(song.id, it.favorite)
                        }
                    }
                }) {
                    Icon(
                        imageVector = if (favorited) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "收藏",
                        tint = if (favorited) AppPrimary else AppMuted,
                    )
                }
                IconButton(onClick = { PlayerManager.prev() }) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "上一首", tint = AppText, modifier = Modifier.size(34.dp))
                }
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .clip(RoundedCornerShape(50))
                        .background(AppText)
                        .clickable { PlayerManager.toggle() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (state.isPlaying) "暂停" else "播放",
                        tint = AppSurface,
                        modifier = Modifier.size(34.dp),
                    )
                }
                IconButton(onClick = { PlayerManager.next() }) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "下一首", tint = AppText, modifier = Modifier.size(34.dp))
                }
                // 循环模式：关 → 列表循环 → 单曲循环
                IconButton(onClick = { PlayerManager.cycleRepeatMode() }) {
                    Icon(
                        imageVector = if (state.repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) {
                            Icons.Rounded.RepeatOne
                        } else {
                            Icons.Rounded.Repeat
                        },
                        contentDescription = "循环模式",
                        tint = if (state.repeatMode == androidx.media3.common.Player.REPEAT_MODE_OFF) AppMuted else AppPrimary,
                    )
                }
            }
        }
    }
}
