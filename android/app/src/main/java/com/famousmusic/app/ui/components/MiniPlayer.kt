package com.famousmusic.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.famousmusic.app.playback.PlayerManager
import com.famousmusic.app.ui.theme.AppMuted
import com.famousmusic.app.ui.theme.AppPrimary
import com.famousmusic.app.ui.theme.AppSurface2
import com.famousmusic.app.ui.theme.AppSurface3
import com.famousmusic.app.ui.theme.AppText

/** 底部迷你播放条（点击进入全屏播放页） */
@Composable
fun MiniPlayer(onOpen: () -> Unit) {
    val state by PlayerManager.state.collectAsState()
    val song = state.song ?: return

    val progress = if (state.durationMs > 0) {
        (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Surface(color = AppSurface2, tonalElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
        Column {
            // 顶部进度细条
            Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(AppSurface3)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .background(AppPrimary)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp)
                    .clickable(onClick = onOpen)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SongCover(song = song, size = 44.dp, corner = 6.dp)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        color = AppText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = song.displayArtist,
                        color = AppMuted,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(
                    onClick = { PlayerManager.toggle() },
                    modifier = Modifier
                        .size(40.dp)
                        .background(AppText, RoundedCornerShape(50)),
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (state.isPlaying) "暂停" else "播放",
                        tint = Color(0xFF121212),
                        modifier = Modifier.size(22.dp),
                    )
                }
                IconButton(onClick = { PlayerManager.next() }) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "下一首", tint = AppMuted)
                }
            }
        }
    }
}
