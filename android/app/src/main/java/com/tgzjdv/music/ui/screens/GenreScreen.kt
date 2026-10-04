package com.tgzjdv.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.tgzjdv.music.data.ApiClient
import com.tgzjdv.music.data.GenreCount
import com.tgzjdv.music.data.Song
import com.tgzjdv.music.playback.PlayerManager
import com.tgzjdv.music.ui.components.EmptyState
import com.tgzjdv.music.ui.components.ErrorState
import com.tgzjdv.music.ui.components.LoadingBox
import com.tgzjdv.music.ui.components.SongRow
import com.tgzjdv.music.ui.theme.AppMuted
import com.tgzjdv.music.ui.theme.AppPrimary
import com.tgzjdv.music.ui.theme.AppSurface2
import com.tgzjdv.music.ui.theme.AppText
import com.tgzjdv.music.ui.theme.LocalBottomBarInset

/** 分类页：选择流派查看歌曲 */
@Composable
fun GenreScreen(
    initialGenre: String?,
    onSongDetail: (Int) -> Unit,
) {
    var genres by remember { mutableStateOf<List<GenreCount>>(emptyList()) }
    var selected by remember { mutableStateOf(initialGenre.orEmpty()) }
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val playerState by PlayerManager.state.collectAsState()

    LaunchedEffect(Unit) {
        runCatching { ApiClient.genres() }.onSuccess { genres = it.genres }
    }

    LaunchedEffect(selected) {
        if (selected.isBlank()) {
            songs = emptyList()
            return@LaunchedEffect
        }
        loading = true
        runCatching { ApiClient.listSongs(genre = selected, limit = 50) }
            .onSuccess { songs = it.songs; error = null }
            .onFailure { error = it.message }
        loading = false
    }

    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp + LocalBottomBarInset.current)) {
        item {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("音乐分类", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.padding(top = 12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(genres) { g ->
                        val active = selected == g.genre
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (active) AppPrimary.copy(alpha = 0.18f) else AppSurface2)
                                .clickable { selected = if (active) "" else g.genre }
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                g.genre,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (active) AppPrimary else AppText,
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(g.count.toString(), style = MaterialTheme.typography.labelSmall, color = AppMuted)
                        }
                    }
                }
            }
        }

        when {
            selected.isBlank() -> item { EmptyState("请选择一个分类") }
            loading -> item { LoadingBox() }
            error != null -> item { ErrorState(error!!) }
            songs.isEmpty() -> item { EmptyState("该分类下暂无歌曲") }
            else -> {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("$selected · ${songs.size} 首", color = AppMuted, style = MaterialTheme.typography.bodySmall)
                        Button(
                            onClick = { PlayerManager.playQueue(songs) },
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = AppSurface2),
                        ) {
                            Text("播放全部", color = AppText, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                itemsIndexed(songs, key = { _, s -> s.id }) { i, song ->
                    SongRow(
                        song = song,
                        index = i + 1,
                        isCurrent = playerState.song?.id == song.id,
                        onClick = { PlayerManager.playSong(song, songs) },
                        onPlay = { PlayerManager.playSong(song, songs) },
                        onMore = { onSongDetail(song.id) },
                    )
                }
            }
        }
    }
}
