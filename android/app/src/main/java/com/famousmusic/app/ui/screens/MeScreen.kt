package com.famousmusic.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.famousmusic.app.data.ApiClient
import com.famousmusic.app.data.Playlist
import com.famousmusic.app.data.Song
import com.famousmusic.app.playback.PlayerManager
import com.famousmusic.app.session.AppSession
import com.famousmusic.app.ui.components.EmptyState
import com.famousmusic.app.ui.components.LoadingBox
import com.famousmusic.app.ui.components.SongRow
import com.famousmusic.app.ui.components.TitleBadge
import com.famousmusic.app.ui.components.UserAvatar
import com.famousmusic.app.ui.theme.AppAccent
import com.famousmusic.app.ui.theme.AppMuted
import com.famousmusic.app.ui.theme.AppPrimary
import com.famousmusic.app.ui.theme.AppSurface2
import com.famousmusic.app.ui.theme.AppSurface3
import com.famousmusic.app.ui.theme.AppText
import com.famousmusic.app.ui.theme.LocalBottomBarInset
import com.famousmusic.app.ui.theme.NavStyleStore
import kotlinx.coroutines.launch

/** 个人中心：资料 + 管理入口 + 我的上传 / 我的收藏 */
@Composable
fun MeScreen(
    onLogin: () -> Unit,
    onUpload: () -> Unit,
    onAdmin: () -> Unit,
    onSongDetail: (Int) -> Unit,
    onOpenPlaylist: (Int) -> Unit,
    onEqualizer: () -> Unit,
    onSettings: () -> Unit,
) {
    val user by AppSession.user.collectAsState()
    val navStyle by NavStyleStore.style.collectAsState()
    var uploaded by remember { mutableStateOf<List<Song>>(emptyList()) }
    var favorites by remember { mutableStateOf<List<Song>>(emptyList()) }
    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var tab by remember { mutableStateOf(0) } // 0=上传 1=收藏
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val playerState by PlayerManager.state.collectAsState()

    fun load() {
        val u = user ?: return
        scope.launch {
            loading = true
            runCatching {
                Triple(
                    ApiClient.listSongs(limit = 50),
                    ApiClient.favorites(),
                    ApiClient.myPlaylists(),
                )
            }.onSuccess { (all, fav, pl) ->
                uploaded = all.songs.filter { it.uploaderId == u.id }
                favorites = fav.songs
                playlists = pl.playlists
            }.onFailure { message = it.message }
            loading = false
        }
    }

    LaunchedEffect(user?.id) {
        if (user == null) {
            loading = false
        } else {
            load()
        }
    }

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            message = null
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("无法读取图片")
                val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                val ext = mime.substringAfterLast('/').ifBlank { "jpg" }
                val pre = ApiClient.avatarPresign("avatar.$ext")
                ApiClient.uploadToPresigned(pre.url, bytes, mime)
                ApiClient.avatarComplete(pre.key)
                AppSession.updateUser(ApiClient.me().user)
            }.onSuccess { message = "头像已更新" }
                .onFailure { message = it.message }
        }
    }

    if (user == null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("请先登录", color = AppMuted)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onLogin, shape = RoundedCornerShape(50)) { Text("去登录") }
        }
        return
    }

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp + LocalBottomBarInset.current)) {
        // 用户信息
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.clickable { avatarPicker.launch("image/*") }) {
                    UserAvatar(user = user, size = 64.dp)
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = user!!.username,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        if (user!!.isAdmin == 1) {
                            Spacer(Modifier.width(6.dp))
                            TitleBadge("管理员", AppAccent)
                        }
                        if (!user!!.title.isNullOrBlank()) {
                            Spacer(Modifier.width(6.dp))
                            TitleBadge(user!!.title!!)
                        }
                    }
                    Text(user!!.email, color = AppMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // 快捷入口
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onUpload,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = AppPrimary),
                ) {
                    Icon(Icons.Rounded.Upload, contentDescription = null, modifier = Modifier.height(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("上传音乐")
                }
                if (user!!.isAdmin == 1) {
                    Button(
                        onClick = onAdmin,
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = AppAccent.copy(alpha = 0.85f)),
                    ) {
                        Icon(Icons.Rounded.AdminPanelSettings, contentDescription = null, modifier = Modifier.height(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("管理后台")
                    }
                }
            }
        }

        // 音效设置
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(AppSurface2)
                    .clickable { onEqualizer() }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = AppPrimary)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("均衡器与音效", fontWeight = FontWeight.SemiBold, color = AppText)
                    Text(
                        "超重低音 / 低音增强 / 人声 / 摇滚 等预置",
                        color = AppMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = AppMuted)
            }
        }

        // 外观设置（底部导航栏样式）
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(AppSurface2)
                    .clickable { onSettings() }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Palette, contentDescription = null, tint = AppPrimary)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("设置", fontWeight = FontWeight.SemiBold, color = AppText)
                    Text(
                        "底部导航栏样式：当前「${navStyle.label}」",
                        color = AppMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = AppMuted)
            }
        }

        // 我的歌单
        if (playlists.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(top = 18.dp)) {
                    Text(
                        "我的歌单",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp)) {
                        items(playlists, key = { it.id }) { p ->
                            Row(
                                modifier = Modifier
                                    .padding(end = 10.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(AppSurface2)
                                    .clickable { onOpenPlaylist(p.id) }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Rounded.QueueMusic, contentDescription = null, tint = AppMuted)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(p.name, style = MaterialTheme.typography.bodyMedium)
                                    Text("${p.songCount} 首", color = AppMuted, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 消息
        message?.let {
            item {
                Text(
                    it,
                    color = AppPrimary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        // Tabs
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TabText("我的上传", tab == 0) { tab = 0 }
                TabText("我的收藏", tab == 1) { tab = 1 }
                Spacer(Modifier.weight(1f))
                if (tab == 0 && uploaded.isNotEmpty()) {
                    TextButton(onClick = { PlayerManager.playQueue(uploaded) }) {
                        Text("播放全部", color = AppPrimary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        if (loading) {
            item { LoadingBox() }
        } else if (tab == 0) {
            if (uploaded.isEmpty()) {
                item { EmptyState("还没有上传过歌曲") }
            } else {
                itemsIndexed(uploaded, key = { _, s -> s.id }) { i, song ->
                    SongRow(
                        song = song,
                        index = i + 1,
                        isCurrent = playerState.song?.id == song.id,
                        onClick = { PlayerManager.playSong(song, uploaded) },
                        onPlay = { PlayerManager.playSong(song, uploaded) },
                        onMore = { onSongDetail(song.id) },
                        trailing = {
                            IconButton(onClick = {
                                scope.launch {
                                    runCatching { ApiClient.deleteSong(song.id) }
                                        .onSuccess { uploaded = uploaded.filterNot { it.id == song.id }; message = "已删除" }
                                        .onFailure { message = it.message }
                                }
                            }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "删除", tint = AppMuted)
                            }
                        },
                    )
                }
            }
        } else {
            if (favorites.isEmpty()) {
                item { EmptyState("还没有收藏歌曲") }
            } else {
                itemsIndexed(favorites, key = { _, s -> s.id }) { i, song ->
                    SongRow(
                        song = song,
                        index = i + 1,
                        isCurrent = playerState.song?.id == song.id,
                        onClick = { PlayerManager.playSong(song, favorites) },
                        onPlay = { PlayerManager.playSong(song, favorites) },
                        onMore = { onSongDetail(song.id) },
                        trailing = {
                            IconButton(onClick = {
                                scope.launch {
                                    runCatching { ApiClient.unfavorite(song.id) }
                                        .onSuccess {
                                            favorites = favorites.filterNot { it.id == song.id }
                                            // 同步到媒体元数据 → 原子随身听的爱心也会变
                                            PlayerManager.setFavorite(song.id, false)
                                        }
                                        .onFailure { message = it.message }
                                }
                            }) {
                                Icon(Icons.Rounded.FavoriteBorder, contentDescription = "取消收藏", tint = AppMuted)
                            }
                        },
                    )
                }
            }
        }

        item {
            TextButton(
                onClick = {
                    AppSession.logout()
                    message = "已退出登录"
                },
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text("退出登录", color = AppMuted)
            }
        }
    }
}

@Composable
private fun TabText(text: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        color = if (active) AppPrimary else AppMuted,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clickable(onClick = onClick),
    )
}
