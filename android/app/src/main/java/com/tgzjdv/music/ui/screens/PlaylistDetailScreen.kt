package com.tgzjdv.music.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tgzjdv.music.data.ApiClient
import com.tgzjdv.music.data.Playlist
import com.tgzjdv.music.data.Song
import com.tgzjdv.music.playback.PlayerManager
import com.tgzjdv.music.session.AppSession
import com.tgzjdv.music.ui.components.EmptyState
import com.tgzjdv.music.ui.components.ErrorState
import com.tgzjdv.music.ui.components.LoadingBox
import com.tgzjdv.music.ui.components.SongRow
import com.tgzjdv.music.ui.theme.AppMuted
import com.tgzjdv.music.ui.theme.LocalBottomBarInset
import com.tgzjdv.music.ui.theme.AppPrimary
import com.tgzjdv.music.ui.theme.AppSurface2
import com.tgzjdv.music.ui.theme.AppText
import kotlinx.coroutines.launch

/** 歌单详情 */
@Composable
fun PlaylistDetailScreen(
    playlistId: Int,
    onBack: () -> Unit,
    onSongDetail: (Int) -> Unit,
) {
    val user by AppSession.user.collectAsState()
    var playlist by remember { mutableStateOf<Playlist?>(null) }
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val playerState by PlayerManager.state.collectAsState()

    LaunchedEffect(playlistId, reloadKey) {
        loading = true
        runCatching { ApiClient.playlistDetail(playlistId) }
            .onSuccess { playlist = it.playlist; songs = it.songs; error = null }
            .onFailure { error = it.message }
        loading = false
    }

    val isOwner = user != null && playlist != null && user?.id == playlist?.userId

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist?.name ?: "歌单",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${playlist?.ownerName ?: ""} 创建 · ${songs.size} 首",
                    color = AppMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (songs.isNotEmpty()) {
                Button(
                    onClick = { PlayerManager.playQueue(songs) },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = AppPrimary),
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.height(18.dp))
                    Spacer(Modifier.padding(horizontal = 2.dp))
                    Text("播放")
                }
            }
            if (isOwner) {
                IconButton(onClick = {
                    scope.launch {
                        runCatching { ApiClient.deletePlaylist(playlistId) }
                            .onSuccess { onBack() }
                            .onFailure { error = it.message }
                    }
                }) {
                    Icon(Icons.Rounded.Delete, contentDescription = "删除歌单", tint = AppMuted)
                }
            }
        }

        when {
            loading -> LoadingBox()
            error != null -> ErrorState(error!!, onRetry = { reloadKey++ })
            songs.isEmpty() -> EmptyState("歌单还是空的，去添加歌曲吧！")
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 16.dp + LocalBottomBarInset.current)) {
                itemsIndexed(songs, key = { _, s -> s.id }) { i, song ->
                    SongRow(
                        song = song,
                        index = i + 1,
                        isCurrent = playerState.song?.id == song.id,
                        onClick = { PlayerManager.playSong(song, songs) },
                        onPlay = { PlayerManager.playSong(song, songs) },
                        onMore = { onSongDetail(song.id) },
                        trailing = if (isOwner) {
                            {
                                IconButton(onClick = {
                                    scope.launch {
                                        runCatching { ApiClient.removeSongFromPlaylist(playlistId, song.id) }
                                            .onSuccess { songs = songs.filterNot { it.id == song.id } }
                                            .onFailure { error = it.message }
                                    }
                                }) {
                                    Icon(
                                        Icons.Rounded.RemoveCircleOutline,
                                        contentDescription = "移除",
                                        tint = AppMuted,
                                    )
                                }
                            }
                        } else null,
                    )
                    Spacer(Modifier.height(2.dp))
                }
            }
        }
    }
}
