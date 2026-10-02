package com.famousmusic.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.famousmusic.app.data.ApiClient
import com.famousmusic.app.data.GenreCount
import com.famousmusic.app.data.Song
import com.famousmusic.app.playback.PlayerManager
import com.famousmusic.app.ui.components.EmptyState
import com.famousmusic.app.ui.components.ErrorState
import com.famousmusic.app.ui.components.LoadingBox
import com.famousmusic.app.ui.components.SectionTitle
import com.famousmusic.app.ui.components.SongCover
import com.famousmusic.app.ui.theme.AppAccent
import com.famousmusic.app.ui.theme.AppMuted
import com.famousmusic.app.ui.theme.AppPrimary
import com.famousmusic.app.ui.theme.AppSurface2
import com.famousmusic.app.ui.theme.AppText
import com.famousmusic.app.ui.theme.LocalBottomBarInset
import com.famousmusic.app.util.formatDuration
import com.famousmusic.app.util.formatRelative

/** 发现页：横幅 + 分类入口 + 最新上传 */
@Composable
fun HomeScreen(
    onSongDetail: (Int) -> Unit,
    onGenreClick: (String) -> Unit,
    onUpload: () -> Unit,
    onSeeAll: () -> Unit,
) {
    // 首屏直接用缓存渲染：切回「发现」页不再空白一下
    val cachedSongs = remember { ApiClient.cachedSongs(limit = 12)?.songs ?: emptyList() }
    val cachedGenres = remember { ApiClient.cachedGenres()?.genres ?: emptyList() }
    var latest by remember { mutableStateOf(cachedSongs) }
    var genres by remember { mutableStateOf(cachedGenres) }
    var loading by remember { mutableStateOf(cachedSongs.isEmpty() && cachedGenres.isEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(reloadKey) {
        // 已有缓存时不显示 loading，后台静默刷新
        loading = latest.isEmpty() && genres.isEmpty()
        runCatching { ApiClient.listSongs(limit = 12) to ApiClient.genres() }
            .onSuccess { (songsRes, genreRes) ->
                latest = songsRes.songs
                genres = genreRes.genres
                error = null
            }
            .onFailure { error = it.message ?: "加载失败" }
        loading = false
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            // 液态玻璃底栏浮在内容之上，这里让列表能滚到它后面（经典样式下为 0）
            bottom = 16.dp + LocalBottomBarInset.current,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 横幅
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(AppPrimary.copy(alpha = 0.32f), AppAccent.copy(alpha = 0.28f))
                        )
                    )
                    .padding(20.dp),
            ) {
                Text("欢迎来到 TGZJDV's Music 🎵", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "上传你自己的音乐，与大家分享好听的声音",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppText.copy(alpha = 0.8f),
                )
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = onUpload,
                    colors = ButtonDefaults.buttonColors(containerColor = AppPrimary),
                    shape = RoundedCornerShape(50),
                ) {
                    Text("立即上传", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // 分类
        if (genres.isNotEmpty()) {
            item {
                Column {
                    SectionTitle("音乐分类")
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(genres) { g ->
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(AppSurface2)
                                    .clickable { onGenreClick(g.genre) }
                                    .padding(horizontal = 14.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(g.genre, style = MaterialTheme.typography.bodySmall, color = AppText)
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    g.count.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AppMuted,
                                )
                            }
                        }
                    }
                }
            }
        }

        // 最新上传
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionTitle("最新上传")
                Text(
                    "查看全部 →",
                    color = AppPrimary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.clickable(onClick = onSeeAll),
                )
            }
        }

        when {
            loading -> item { LoadingBox() }
            error != null -> item { ErrorState(error!!, onRetry = { reloadKey++ }) }
            latest.isEmpty() -> item { EmptyState("还没有歌曲，来上传第一首吧！") }
            else -> {
                val rows = latest.chunked(2)
                items(rows) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { song ->
                            Box(modifier = Modifier.weight(1f)) {
                                SongGridCard(
                                    song = song,
                                    onPlay = { PlayerManager.playSong(song, latest) },
                                    onDetail = { onSongDetail(song.id) },
                                )
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                item {
                    Button(
                        onClick = { PlayerManager.playQueue(latest) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = AppSurface2),
                    ) {
                        Text("播放全部", color = AppText)
                    }
                }
            }
        }
    }
}

/** 网格歌曲卡片 */
@Composable
fun SongGridCard(song: Song, onPlay: () -> Unit, onDetail: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(AppSurface2)
            .clickable(onClick = onPlay)
            .padding(8.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            SongCover(song = song, corner = 10.dp)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(AppPrimary)
                    .clickable(onClick = onPlay)
                    .padding(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "播放",
                    tint = AppText,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = song.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable(onClick = onDetail),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = song.displayArtist,
                color = AppMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(formatDuration(song.duration), color = AppMuted, style = MaterialTheme.typography.labelSmall)
        }
        Text(formatRelative(song.createdAt), color = AppMuted.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
    }
}
