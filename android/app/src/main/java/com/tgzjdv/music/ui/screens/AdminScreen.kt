package com.tgzjdv.music.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
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
import com.tgzjdv.music.data.AdminUpdateUserRequest
import com.tgzjdv.music.data.AdminUser
import com.tgzjdv.music.data.ApiClient
import com.tgzjdv.music.session.AppSession
import com.tgzjdv.music.ui.components.EmptyState
import com.tgzjdv.music.ui.components.ErrorState
import com.tgzjdv.music.ui.components.LoadingBox
import com.tgzjdv.music.ui.components.TitleBadge
import com.tgzjdv.music.ui.theme.AppAccent
import com.tgzjdv.music.ui.theme.AppMuted
import com.tgzjdv.music.ui.theme.AppPrimary
import com.tgzjdv.music.ui.theme.AppSurface2
import com.tgzjdv.music.ui.theme.AppText
import kotlinx.coroutines.launch

/** 管理员面板：用户管理（封禁 / 头衔 / 用户名 / 删除 / 管理员） */
@Composable
fun AdminScreen(onBack: () -> Unit, onLogin: () -> Unit) {
    val me by AppSession.user.collectAsState()
    var users by remember { mutableStateOf<List<AdminUser>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<AdminUser?>(null) }
    var reloadKey by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(me?.id, reloadKey) {
        val u = me
        if (u == null || u.isAdmin != 1) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        runCatching { ApiClient.adminUsers() }
            .onSuccess { users = it.users; error = null }
            .onFailure { error = it.message }
        loading = false
    }

    if (me == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("请先登录", color = AppMuted)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onLogin, shape = RoundedCornerShape(50)) { Text("去登录") }
            }
        }
        return
    }

    if (me?.isAdmin != 1) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("需要管理员权限", color = AppMuted)
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("管理员面板", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("用户管理 · 封禁 / 头衔 / 用户名 / 删除", color = AppMuted, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onBack) { Text("返回", color = AppMuted) }
        }

        message?.let {
            Text(it, color = AppPrimary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
        }

        when {
            loading -> LoadingBox()
            error != null -> ErrorState(error!!, onRetry = { reloadKey++ })
            users.isEmpty() -> EmptyState("暂无用户")
            else -> LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                items(users, key = { it.id }) { u ->
                    AdminUserCard(
                        user = u,
                        isSelf = u.id == me?.id,
                        onEdit = { editing = u },
                        onToggleBan = {
                            scope.launch {
                                runCatching { ApiClient.adminUpdateUser(u.id, AdminUpdateUserRequest(banned = if (u.banned == 1) 0 else 1)) }
                                    .onSuccess { reloadKey++ }
                                    .onFailure { message = it.message }
                            }
                        },
                        onDelete = {
                            scope.launch {
                                runCatching { ApiClient.adminDeleteUser(u.id) }
                                    .onSuccess { message = "已删除用户「${u.username}」"; reloadKey++ }
                                    .onFailure { message = it.message }
                            }
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    editing?.let { target ->
        EditUserDialog(
            user = target,
            isSelf = target.id == me?.id,
            onDismiss = { editing = null },
            onSave = { req ->
                scope.launch {
                    runCatching { ApiClient.adminUpdateUser(target.id, req) }
                        .onSuccess { message = "已保存"; editing = null; reloadKey++ }
                        .onFailure { message = it.message }
                }
            },
        )
    }
}

@Composable
private fun AdminUserCard(
    user: AdminUser,
    isSelf: Boolean,
    onEdit: () -> Unit,
    onToggleBan: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppSurface2)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(AppPrimary)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(user.username.take(1).uppercase(), color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(user.username, fontWeight = FontWeight.Medium)
                    if (user.isAdmin == 1) {
                        Spacer(Modifier.width(6.dp))
                        TitleBadge("管理员", AppAccent)
                    }
                    if (user.banned == 1) {
                        Spacer(Modifier.width(6.dp))
                        TitleBadge("已封禁", MaterialTheme.colorScheme.error)
                    }
                    if (!user.title.isNullOrBlank()) {
                        Spacer(Modifier.width(6.dp))
                        TitleBadge(user.title!!)
                    }
                }
                Text(
                    "${user.email} · ${user.songsCount} 首上传",
                    color = AppMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onEdit,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = AppPrimary.copy(alpha = 0.16f)),
            ) { Text("编辑", color = AppPrimary, style = MaterialTheme.typography.labelMedium) }

            if (!isSelf) {
                Button(
                    onClick = onToggleBan,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (user.banned == 1) AppPrimary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.error.copy(alpha = 0.16f)
                    ),
                ) {
                    Text(
                        if (user.banned == 1) "解封" else "封禁",
                        color = if (user.banned == 1) AppPrimary else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Button(
                    onClick = onDelete,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.16f)),
                ) { Text("删除", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
}

@Composable
private fun EditUserDialog(
    user: AdminUser,
    isSelf: Boolean,
    onDismiss: () -> Unit,
    onSave: (AdminUpdateUserRequest) -> Unit,
) {
    var username by remember { mutableStateOf(user.username) }
    var title by remember { mutableStateOf(user.title.orEmpty()) }
    var isAdmin by remember { mutableStateOf(user.isAdmin == 1) }
    var banned by remember { mutableStateOf(user.banned == 1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑用户") },
        text = {
            Column {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("用户名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("头衔（留空清除）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isAdmin, onCheckedChange = { isAdmin = it }, enabled = !isSelf)
                    Text("管理员")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = banned, onCheckedChange = { banned = it }, enabled = !isSelf)
                    Text("封禁（禁止登录）")
                }
                if (isSelf) {
                    Text("不能修改自己的管理员身份或封禁自己", color = AppMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        AdminUpdateUserRequest(
                            username = username.trim(),
                            title = title.trim().ifBlank { null },
                            isAdmin = if (isAdmin) 1 else 0,
                            banned = if (banned) 1 else 0,
                        )
                    )
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = AppMuted) } },
    )
}
