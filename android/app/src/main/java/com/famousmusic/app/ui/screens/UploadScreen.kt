package com.famousmusic.app.ui.screens

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.famousmusic.app.data.CompleteRequest
import com.famousmusic.app.data.PresignRequest
import com.famousmusic.app.session.AppSession
import com.famousmusic.app.ui.theme.AppMuted
import com.famousmusic.app.ui.theme.AppPrimary
import com.famousmusic.app.ui.theme.AppSurface2
import com.famousmusic.app.ui.theme.AppText
import com.famousmusic.app.util.AudioTagReader
import com.famousmusic.app.util.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val GENRES = listOf("流行", "摇滚", "民谣", "电子", "嘻哈", "古典", "爵士", "国风", "纯音乐", "其他")

/** 上传音乐：音频 + 封面 + 歌词，直传 OSS */
@Composable
fun UploadScreen(
    onLogin: () -> Unit,
    onDone: (Int) -> Unit,
) {
    val user by AppSession.user.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf(GENRES.first()) }
    var duration by remember { mutableStateOf(0) }

    var audioUri by remember { mutableStateOf<Uri?>(null) }
    var audioName by remember { mutableStateOf("") }
    var coverBytes by remember { mutableStateOf<ByteArray?>(null) }
    var coverMime by remember { mutableStateOf("image/jpeg") }
    var coverName by remember { mutableStateOf("") }
    var lyricsBytes by remember { mutableStateOf<ByteArray?>(null) }
    var lyricsName by remember { mutableStateOf("") }

    var readingTags by remember { mutableStateOf(false) }
    var tagInfo by remember { mutableStateOf<String?>(null) }
    var uploading by remember { mutableStateOf(false) }
    var progressText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var successId by remember { mutableStateOf<Int?>(null) }

    // 选择音频：读取 ID3 / FLAC 标签，自动填充标题、歌手、分类、时长、内嵌封面与歌词
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        audioUri = uri
        val name = queryDisplayName(context, uri) ?: "audio.mp3"
        audioName = name
        if (title.isBlank()) title = name.substringBeforeLast('.')
        tagInfo = null
        readingTags = true
        scope.launch {
            val tags = withContext(Dispatchers.IO) { AudioTagReader.read(context, uri, name) }
            tags.title?.let { title = it }
            tags.artist?.let { artist = it }
            tags.durationSec.takeIf { it > 0 }?.let { duration = it }
            // 分类：只有能对应到站内分类才覆盖
            tags.genre?.let { g ->
                GENRES.firstOrNull { it.equals(g, ignoreCase = true) || g.contains(it, ignoreCase = true) }
                    ?.let { genre = it }
            }
            tags.coverBytes?.takeIf { it.isNotEmpty() }?.let {
                coverBytes = it
                coverMime = tags.coverMime ?: AudioTagReader.sniffImageMime(it)
                coverName = "cover." + mimeExt(coverMime)
            }
            tags.lyrics?.takeIf { it.isNotBlank() }?.let {
                lyricsBytes = it.toByteArray(Charsets.UTF_8)
                lyricsName = "lyrics.lrc"
            }
            readingTags = false
            val found = buildList {
                if (tags.title != null) add("标题")
                if (tags.artist != null) add("歌手")
                if (tags.genre != null) add("分类")
                if (duration > 0) add("时长")
                if (coverBytes != null) add("封面")
                if (lyricsBytes != null) add("歌词")
            }
            tagInfo = if (found.isEmpty()) {
                "未发现内嵌标签，请手动填写"
            } else {
                "已自动读取：" + found.joinToString("、")
            }
        }
    }
    val pickCover = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = withContext(Dispatchers.IO) { readBytes(context, uri) }
            if (bytes == null) {
                error = "无法读取图片文件"
                return@launch
            }
            coverBytes = bytes
            coverMime = context.contentResolver.getType(uri) ?: AudioTagReader.sniffImageMime(bytes)
            coverName = queryDisplayName(context, uri) ?: ("cover." + mimeExt(coverMime))
        }
    }
    val pickLyrics = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = withContext(Dispatchers.IO) { readBytes(context, uri) }
            if (bytes == null) {
                error = "无法读取歌词文件"
                return@launch
            }
            lyricsBytes = bytes
            lyricsName = queryDisplayName(context, uri) ?: "lyrics.lrc"
        }
    }

    if (user == null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("登录后即可上传音乐", color = AppMuted)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onLogin, shape = RoundedCornerShape(50)) { Text("去登录") }
        }
        return
    }

    successId?.let { id ->
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("🎉", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(8.dp))
            Text("上传成功！", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("你的歌曲已经发布到 TGZJDV's Music", color = AppMuted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { onDone(id) }, shape = RoundedCornerShape(50)) { Text("查看歌曲") }
                Button(
                    onClick = {
                        successId = null
                        title = ""; artist = ""; audioUri = null
                        coverBytes = null; lyricsBytes = null
                        duration = 0; audioName = ""; coverName = ""; lyricsName = ""
                        tagInfo = null
                    },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = AppSurface2),
                ) { Text("再传一首", color = AppText) }
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("上传音乐", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        // 音频
        PickerCard(
            label = "音频文件 *",
            value = if (audioName.isBlank()) "点击选择音频文件" else "$audioName${if (duration > 0) " · " + formatDuration(duration) else ""}",
            picked = audioName.isNotBlank(),
            onClick = { pickAudio.launch("audio/*") },
        )
        if (readingTags) {
            Spacer(Modifier.height(6.dp))
            Text("正在读取音频标签…", color = AppMuted, style = MaterialTheme.typography.labelSmall)
        } else {
            tagInfo?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = AppPrimary, style = MaterialTheme.typography.labelSmall)
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("歌曲标题 *") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = artist,
            onValueChange = { artist = it },
            label = { Text("歌手 / 艺术家") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(14.dp))
        Text("分类", style = MaterialTheme.typography.bodySmall, color = AppMuted)
        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            GENRES.chunked(5).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { g ->
                        val active = genre == g
                        Text(
                            text = g,
                            color = if (active) AppPrimary else AppText,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (active) AppPrimary.copy(alpha = 0.18f) else AppSurface2)
                                .clickable { genre = g }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        PickerCard(
            label = "封面图（可选）",
            value = if (coverName.isBlank()) "点击选择图片" else coverName,
            picked = coverName.isNotBlank(),
            onClick = { pickCover.launch("image/*") },
        )
        Spacer(Modifier.height(10.dp))
        PickerCard(
            label = "LRC 歌词（可选，支持同步滚动）",
            value = if (lyricsName.isBlank()) "点击选择 .lrc / .txt 文件" else lyricsName,
            picked = lyricsName.isNotBlank(),
            onClick = { pickLyrics.launch("*/*") },
        )

        if (uploading) {
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = AppPrimary)
            Spacer(Modifier.height(6.dp))
            Text(progressText, color = AppMuted, style = MaterialTheme.typography.bodySmall)
        }

        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(18.dp))
        Button(
            onClick = {
                val uri = audioUri
                if (uri == null) {
                    error = "请选择音频文件"
                    return@Button
                }
                if (title.isBlank()) {
                    error = "请填写歌曲标题"
                    return@Button
                }
                scope.launch {
                    uploading = true
                    error = null
                    try {
                        progressText = "读取文件…"
                        val audioData = withContext(Dispatchers.IO) { readBytes(context, uri) } ?: error("无法读取音频文件")
                        val coverData = coverBytes
                        val lyricsData = lyricsBytes

                        progressText = "获取上传地址…"
                        val pre = ApiClient.presign(
                            PresignRequest(
                                title = title.trim(),
                                artist = artist.trim(),
                                genre = genre,
                                duration = duration,
                                audioName = audioName.ifBlank { "audio.mp3" },
                                coverName = coverName.ifBlank { null },
                                lyricsName = lyricsName.ifBlank { null },
                            )
                        )

                        progressText = "上传音频中…"
                        ApiClient.uploadToPresigned(pre.audioUrl, audioData, guessAudioMime(audioName))
                        if (coverData != null && pre.coverUrl != null) {
                            progressText = "上传封面中…"
                            ApiClient.uploadToPresigned(pre.coverUrl, coverData, coverMime)
                        }
                        if (lyricsData != null && pre.lyricsUrl != null) {
                            progressText = "上传歌词中…"
                            ApiClient.uploadToPresigned(pre.lyricsUrl, lyricsData, "text/plain; charset=utf-8")
                        }

                        progressText = "登记歌曲…"
                        val res = ApiClient.complete(
                            CompleteRequest(
                                title = title.trim(),
                                artist = artist.trim(),
                                genre = genre,
                                duration = duration,
                                audioKey = pre.audioKey,
                                coverKey = pre.coverKey,
                                lyricsKey = pre.lyricsKey,
                            )
                        )
                        successId = res.id
                    } catch (e: Exception) {
                        error = e.message ?: "上传失败"
                    } finally {
                        uploading = false
                        progressText = ""
                    }
                }
            },
            enabled = !uploading,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(50),
        ) {
            Text(if (uploading) "上传中…" else "发布歌曲", fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PickerCard(label: String, value: String, picked: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppSurface2)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = AppMuted)
        Spacer(Modifier.height(4.dp))
        Text(value, color = if (picked) AppPrimary else AppText, style = MaterialTheme.typography.bodyMedium)
    }
}

// ---------- 工具 ----------

private fun readBytes(context: Context, uri: Uri): ByteArray? =
    runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()

private fun queryDisplayName(context: Context, uri: Uri): String? =
    runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    }.getOrNull()

private fun readDurationSeconds(context: Context, uri: Uri): Int =
    runCatching {
        val mmr = MediaMetadataRetriever()
        mmr.setDataSource(context, uri)
        val ms = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        mmr.release()
        (ms / 1000L).toInt()
    }.getOrDefault(0)

private fun mimeExt(mime: String): String = when {
    mime.contains("png", ignoreCase = true) -> "png"
    mime.contains("webp", ignoreCase = true) -> "webp"
    mime.contains("gif", ignoreCase = true) -> "gif"
    else -> "jpg"
}

private fun guessAudioMime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
    "mp3" -> "audio/mpeg"
    "flac" -> "audio/flac"
    "wav" -> "audio/wav"
    "m4a", "aac" -> "audio/mp4"
    "ogg" -> "audio/ogg"
    else -> "application/octet-stream"
}

private fun guessImageMime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
    "png" -> "image/png"
    "webp" -> "image/webp"
    "gif" -> "image/gif"
    else -> "image/jpeg"
}
