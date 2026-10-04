package com.tgzjdv.music.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
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
import com.tgzjdv.music.data.ApiClient
import com.tgzjdv.music.data.Playlist
import com.tgzjdv.music.session.AppSession
import com.tgzjdv.music.ui.components.EmptyState
import com.tgzjdv.music.ui.components.ErrorState
import com.tgzjdv.music.ui.components.LoadingBox
import com.tgzjdv.music.ui.theme.AppMuted
import com.tgzjdv.music.ui.theme.AppPrimary
import com.tgzjdv.music.ui.theme.AppSurface2
import com.tgzjdv.music.ui.theme.AppSurface3
import com.tgzjdv.music.ui.theme.AppText
import com.tgzjdv.music.ui.theme.LocalBottomBarInset
import kotlinx.coroutines.launch

/** 我的歌单 */
@Composable
fun PlaylistsScreen(
    onOpenPlaylist: (Int) -> Unit,
    onLogin: () -> Unit,
) {
    val user by AppSession.user.collectAsState()
    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var reloadKey by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(user?.id, reloadKey) {
        if (user == null) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        runCatching { ApiClient.myPlaylists() }
            .onSuccess { playlists = it.playlists; error = null }
            .onFailure { error = it.message }
        loading = false
    }

    if (user == null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("请先登录查看你的歌单", color = AppMuted)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onLogin, shape = RoundedCornerShape(50)) { Text("去登录") }
        }
        return
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("我的歌单", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Button(
                onClick = { showCreate = true },
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = AppPrimary),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.height(18.dp))
                Spacer(Modifier.padding(horizontal = 3.dp))
                Text("新建")
            }
        }

        message?.let {
            Text(it, color = AppPrimary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
        }

        when {
            loading -> LoadingBox()
            error != null -> ErrorState(error!!, onRetry = { reloadKey++ })
            playlists.isEmpty() -> EmptyState("还没有歌单，创建一个吧！")
            else -> LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 8.dp,
                    end = 16.dp,
                    bottom = 8.dp + LocalBottomBarInset.current,
                ),
            ) {
                items(playlists, key = { it.id }) { p ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppSurface2)
                            .clickable { onOpenPlaylist(p.id) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(AppSurface3)
                                .padding(12.dp),
                        ) {
                            Icon(Icons.Rounded.QueueMusic, contentDescription = null, tint = AppMuted)
                        }
                        Spacer(Modifier.padding(horizontal = 8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(p.name, fontWeight = FontWeight.Medium)
                            Text("${p.songCount} 首", color = AppMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = {
                            scope.launch {
                                runCatching { ApiClient.deletePlaylist(p.id) }
                                    .onSuccess { playlists = playlists.filterNot { it.id == p.id }; message = "已删除" }
                                    .onFailure { message = it.message }
                            }
                        }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "删除歌单", tint = AppMuted)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("新建歌单") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = { Text("歌单名称", color = AppMuted) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = newName.trim()
                        if (name.isEmpty()) return@TextButton
                        scope.launch {
                            runCatching { ApiClient.createPlaylist(name) }
                                .onSuccess {
                                    showCreate = false
                                    newName = ""
                                    reloadKey++
                                }
                                .onFailure { message = it.message }
                        }
                    },
                ) { Text("创建") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("取消", color = AppMuted) } },
        )
    }
}
