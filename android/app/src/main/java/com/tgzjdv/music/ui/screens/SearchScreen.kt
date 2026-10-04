package com.tgzjdv.music.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.tgzjdv.music.data.ApiClient
import com.tgzjdv.music.data.Song
import com.tgzjdv.music.playback.PlayerManager
import com.tgzjdv.music.ui.components.EmptyState
import com.tgzjdv.music.ui.components.ErrorState
import com.tgzjdv.music.ui.components.LoadingBox
import com.tgzjdv.music.ui.components.SongRow
import com.tgzjdv.music.ui.theme.AppMuted
import com.tgzjdv.music.ui.theme.AppSurface2
import com.tgzjdv.music.ui.theme.AppSurface3
import com.tgzjdv.music.ui.theme.LocalBottomBarInset
import kotlinx.coroutines.launch

/** 搜索页 */
@Composable
fun SearchScreen(onSongDetail: (Int) -> Unit) {
    var query by remember { mutableStateOf("") }
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var searched by remember { mutableStateOf(false) }
    var total by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val playerState by PlayerManager.state.collectAsState()

    fun search(keyword: String) {
        val kw = keyword.trim()
        scope.launch {
            if (kw.isEmpty()) {
                loading = true
                runCatching { ApiClient.listSongs(limit = 20) }
                    .onSuccess { songs = it.songs; total = it.total; searched = false; error = null }
                    .onFailure { error = it.message }
                loading = false
                return@launch
            }
            loading = true
            runCatching { ApiClient.listSongs(q = kw, limit = 50) }
                .onSuccess { songs = it.songs; total = it.total; error = null }
                .onFailure { songs = emptyList(); error = it.message }
            searched = true
            loading = false
        }
    }

    LaunchedEffect(Unit) { search("") }

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("输入歌名或歌手…", color = AppMuted) },
            singleLine = true,
            shape = RoundedCornerShape(50),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = AppSurface2,
                unfocusedContainerColor = AppSurface2,
                focusedBorderColor = AppSurface3,
                unfocusedBorderColor = AppSurface3,
            ),
            trailingIcon = {
                IconButton(onClick = { search(query) }) {
                    Icon(Icons.Rounded.Search, contentDescription = "搜索", tint = AppMuted)
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { search(query) }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )

        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp + LocalBottomBarInset.current)) {
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text(
                        text = if (searched) "「$query」的搜索结果 · $total 首" else "全部歌曲 · $total 首",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppMuted,
                    )
                }
            }
            when {
                loading -> item { LoadingBox() }
                error != null -> item { ErrorState(error!!, onRetry = { search(query) }) }
                songs.isEmpty() -> item { EmptyState(if (searched) "没有找到相关歌曲" else "暂无歌曲") }
                else -> itemsIndexed(songs, key = { _, s -> s.id }) { i, song ->
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
