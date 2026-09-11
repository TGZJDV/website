package com.famousmusic.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.famousmusic.app.data.ApiClient
import com.famousmusic.app.data.Song
import com.famousmusic.app.data.User
import com.famousmusic.app.ui.theme.AppMuted
import com.famousmusic.app.ui.theme.AppPrimary
import com.famousmusic.app.ui.theme.AppSurface3
import com.famousmusic.app.ui.theme.AppText
import com.famousmusic.app.util.formatDuration

/** 歌曲封面（无封面时显示音符占位；size 为空则自适应宽度并保持 1:1） */
@Composable
fun SongCover(
    song: Song,
    modifier: Modifier = Modifier,
    size: Dp? = null,
    corner: Dp = 8.dp,
) {
    val url = ApiClient.coverUrl(song)
    val base = if (size != null) modifier.size(size) else modifier.fillMaxWidth().aspectRatio(1f)
    Box(
        modifier = base
            .clip(RoundedCornerShape(corner))
            .background(AppSurface3),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = song.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                tint = AppMuted,
                modifier = Modifier.fillMaxSize(0.42f),
            )
        }
    }
}

/** 用户头像（无头像时显示首字母） */
@Composable
fun UserAvatar(user: User?, size: Dp = 40.dp) = UserAvatar(
    userId = user?.id ?: 0,
    username = user?.username.orEmpty(),
    avatarKey = user?.avatarKey,
    size = size,
)

/** 通用头像（评论等只有 userId / username / avatarKey 的场景） */
@Composable
fun UserAvatar(userId: Int, username: String, avatarKey: String?, size: Dp = 40.dp) {
    val url = if (avatarKey != null && userId > 0) ApiClient.avatarUrl(userId, avatarKey) else null
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(AppPrimary),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = username,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = username.take(1).uppercase().ifBlank { "?" },
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** 头衔小徽章 */
@Composable
fun TitleBadge(text: String, color: Color = AppPrimary) {
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/** 列表行：序号 + 封面 + 标题/歌手 + 时长 + 操作 */
@Composable
fun SongRow(
    song: Song,
    index: Int? = null,
    isCurrent: Boolean = false,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onMore: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (index != null) {
            Text(
                text = index.toString(),
                color = if (isCurrent) AppPrimary else AppMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.width(22.dp),
            )
        }
        SongCover(song = song, size = 46.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = if (isCurrent) AppPrimary else AppText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = song.displayArtist,
                    color = AppMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!song.uploaderTitle.isNullOrBlank()) {
                    Spacer(Modifier.width(4.dp))
                    TitleBadge(song.uploaderTitle)
                }
            }
        }
        trailing?.invoke()
        Text(
            text = formatDuration(song.duration),
            color = AppMuted,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(start = 6.dp),
        )
        if (onMore != null) {
            IconButton(onClick = onMore) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "更多", tint = AppMuted)
            }
        }
    }
}

/** 加载中 */
@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = AppPrimary, strokeWidth = 2.dp)
    }
}

/** 空状态 */
@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
        Text(text = text, color = AppMuted, style = MaterialTheme.typography.bodyMedium)
    }
}

/** 错误提示 */
@Composable
fun ErrorState(message: String, onRetry: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        if (onRetry != null) {
            Text(
                text = "点击重试",
                color = AppPrimary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.clickable(onClick = onRetry),
            )
        }
    }
}

/** 区块标题 */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = AppText,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier,
    )
}
