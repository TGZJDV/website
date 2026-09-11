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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.famousmusic.app.data.ApiClient
import com.famousmusic.app.data.Comment
import com.famousmusic.app.data.Playlist
import com.famousmusic.app.data.Song
import com.famousmusic.app.playback.PlayerManager
import com.famousmusic.app.session.AppSession
import com.famousmusic.app.ui.components.ErrorState
import com.famousmusic.app.ui.components.LoadingBox
import com.famousmusic.app.ui.components.SongCover
import com.famousmusic.app.ui.components.TitleBadge
import com.famousmusic.app.ui.components.UserAvatar
import com.famousmusic.app.ui.theme.AppMuted
import com.famousmusic.app.ui.theme.AppPrimary
import com.famousmusic.app.ui.theme.AppSurface2
import com.famousmusic.app.ui.theme.AppText
import com.famousmusic.app.util.formatDuration
import com.famousmusic.app.util.formatRelative
import kotlinx.coroutines.launch

/** 歌曲详情：播放 / 收藏 / 加入歌单 / 删除 / 评论 */
@Composable
fun SongDetailScreen(
    songId: Int,
    onBack: () -> Unit,
    onLogin: () -> Unit,
) {
    var song by remember { mutableStateOf<Song?>(null) }
    var favoriteCount by remember { mutableStateOf(0) }
    var favorited by remember { mutableStateOf(false) }
    var comments by remember { mutableStateOf<List<Comment>>(emptyList()) }
    var newComment by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val user by AppSession.user.collectAsState()
    val playerState by PlayerManager.state.collectAsState()

    LaunchedEffect(songId, reloadKey) {
        loading = true
        runCatching { ApiClient.songDetail(songId) to ApiClient.comments(songId) }
            .onSuccess { (detail, cmt) ->
                song = detail.song
                favoriteCount = detail.favoriteCount
                favorited = detail.favorited
                comments = cmt.comments
                error = null
            }
            .onFailure { error = it.message }
        loading = false
    }

    val isCurrent = playerState.song?.id == songId

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        when {
            loading -> LoadingBox()
            error != null -> ErrorState(error!!, onRetry = { reloadKey++ })
            song == null -> ErrorState("歌曲不存在")
            else -> {
                val s = song!!
                Row {
                    SongCover(song = s, size = 120.dp, corner = 12.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(s.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${s.displayArtist} · ${s.genre} · ${formatDuration(s.duration)}",
                            color = AppMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "上传者：${s.uploaderName ?: "未知"}",
                                color = AppMuted,
                                style = MaterialTheme.typography.labelSmall,
                            )
                            if (!s.uploaderTitle.isNullOrBlank()) {
                                Spacer(Modifier.width(4.dp))
                                TitleBadge(s.uploaderTitle)
                            }
                        }
                        Text(
                            "${formatRelative(s.createdAt)} · ♥ $favoriteCount",
                            color = AppMuted,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (isCurrent) PlayerManager.toggle() else PlayerManager.playSong(s)
                        },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = AppPrimary),
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (isCurrent && playerState.isPlaying) "暂停" else "播放")
                    }
                    IconButton(onClick = {
                        if (user == null) {
                            onLogin()
                            return@IconButton
                        }
                        scope.launch {
                            runCatching {
                                if (favorited) ApiClient.unfavorite(songId) else ApiClient.favorite(songId)
                            }.onSuccess {
                                favorited = it.favorite
                                favoriteCount = it.favoriteCount
                            }.onFailure { message = it.message }
                        }
                    }) {
                        Icon(
                            imageVector = if (favorited) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = "收藏",
                            tint = if (favorited) AppPrimary else AppMuted,
                        )
                    }
                    IconButton(onClick = {
                        if (user == null) {
                            onLogin()
                            return@IconButton
                        }
                        scope.launch {
                            runCatching { ApiClient.myPlaylists() }
                                .onSuccess { playlists = it.playlists; showPlaylistPicker = true }
                                .onFailure { message = it.message }
                        }
                    }) {
                        Icon(Icons.Rounded.QueueMusic, contentDescription = "加入歌单", tint = AppMuted)
                    }
                    if (user != null && user?.id == s.uploaderId) {
                        IconButton(onClick = {
                            scope.launch {
                                runCatching { ApiClient.deleteSong(songId) }
                                    .onSuccess { onBack() }
                                    .onFailure { message = it.message }
                            }
                        }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "删除", tint = AppMuted)
                        }
                    }
                }

                message?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = AppPrimary, style = MaterialTheme.typography.bodySmall)
                }

                // 评论
                Spacer(Modifier.height(20.dp))
                Text(
                    "评论 (${comments.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newComment,
                        onValueChange = { newComment = it },
                        placeholder = { Text(if (user != null) "说点什么…" else "登录后参与评论", color = AppMuted) },
                        singleLine = true,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (user == null) {
                                onLogin()
                                return@Button
                            }
                            val content = newComment.trim()
                            if (content.isEmpty()) return@Button
                            scope.launch {
                                runCatching { ApiClient.createComment(songId, content) }
                                    .onSuccess {
                                        comments = comments + it.comment
                                        newComment = ""
                                    }
                                    .onFailure { message = it.message }
                            }
                        },
                        shape = RoundedCornerShape(50),
                    ) { Text("发布") }
                }

                Spacer(Modifier.height(12.dp))
                if (comments.isEmpty()) {
                    Text("还没有评论，来抢沙发～", color = AppMuted, style = MaterialTheme.typography.bodySmall)
                } else {
                    comments.forEach { c ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                        ) {
                            UserAvatar(
                                userId = c.userId,
                                username = c.username,
                                avatarKey = c.avatarKey,
                                size = 34.dp,
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(c.username, color = AppPrimary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                    if (!c.title.isNullOrBlank()) {
                                        Spacer(Modifier.width(4.dp))
                                        TitleBadge(c.title)
                                    }
                                    Spacer(Modifier.weight(1f))
                                    Text(formatRelative(c.createdAt), color = AppMuted, style = MaterialTheme.typography.labelSmall)
                                    if (user != null && user?.id == c.userId) {
                                        Text(
                                            "删除",
                                            color = AppMuted,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier
                                                .padding(start = 8.dp)
                                                .clickable {
                                                    scope.launch {
                                                        runCatching { ApiClient.deleteComment(c.id) }
                                                            .onSuccess { comments = comments.filterNot { it.id == c.id } }
                                                            .onFailure { message = it.message }
                                                    }
                                                },
                                        )
                                    }
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(c.content, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPlaylistPicker && song != null) {
        AlertDialog(
            onDismissRequest = { showPlaylistPicker = false },
            title = { Text("加入播放列表") },
            text = {
                Column {
                    if (playlists.isEmpty()) {
                        Text("还没有播放列表，去「歌单」页创建一个吧", color = AppMuted, style = MaterialTheme.typography.bodySmall)
                    } else {
                        playlists.forEach { p ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        scope.launch {
                                            runCatching { ApiClient.addSongToPlaylist(p.id, songId) }
                                                .onSuccess { message = "已加入「${p.name}」" }
                                                .onFailure { message = it.message }
                                            showPlaylistPicker = false
                                        }
                                    }
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(p.name, modifier = Modifier.weight(1f))
                                Text("${p.songCount} 首", color = AppMuted, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPlaylistPicker = false }) { Text("关闭") } },
        )
    }
}
