package com.famousmusic.app.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.HeartRating
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.famousmusic.app.data.ApiClient
import com.famousmusic.app.data.AppJson
import com.famousmusic.app.data.Song
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 播放器 UI 状态 */
data class PlayerUiState(
    val song: Song? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedMs: Long = 0L,
    val hasNext: Boolean = false,
    val hasPrev: Boolean = false,
    /** Player.REPEAT_MODE_OFF / ALL / ONE */
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val connected: Boolean = false,
    val error: String? = null,
)

/**
 * 播放控制器（App 级单例）：
 * 连接 [PlaybackService]，向 Compose UI 暴露状态与控制方法。
 */
@UnstableApi
object PlayerManager {
    private const val EXTRA_SONG = "song_json"

    private var controller: MediaController? = null
    private var connectFuture: ListenableFuture<MediaController>? = null

    /** 已收藏曲目缓存（用于媒体元数据的 heart rating 状态） */
    private val favoriteIds = mutableSetOf<Int>()

    /** 同步收藏状态到当前曲目元数据（供系统媒体控件显示心形按钮状态） */
    fun setFavorite(songId: Int, favorited: Boolean) {
        val changed = if (favorited) favoriteIds.add(songId) else favoriteIds.remove(songId)
        if (!changed) return
        val c = controller ?: return
        val index = c.currentMediaItemIndex
        val item = c.currentMediaItem ?: return
        if (item.mediaId.toIntOrNull() != songId) return
        val newItem = item.buildUpon()
            .setMediaMetadata(
                item.mediaMetadata.buildUpon()
                    .setUserRating(HeartRating(favorited))
                    .setOverallRating(HeartRating(favorited))
                    .build()
            )
            .build()
        c.replaceMediaItem(index, newItem)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var ticker: Job? = null

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = syncState()

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            _state.value = _state.value.copy(error = "播放失败：${error.errorCodeName}")
        }
    }

    /** 在 Activity/Application 中调用一次（主线程） */
    fun connect(context: Context) {
        if (controller != null || connectFuture != null) return
        val ctx = context.applicationContext
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val future = MediaController.Builder(ctx, token).buildAsync()
        connectFuture = future
        future.addListener({
            val c = runCatching { future.get() }.getOrNull()
            if (c != null) {
                controller = c
                c.addListener(listener)
                syncState()
                startTicker()
            }
        }, MoreExecutors.directExecutor())
    }

    fun release() {
        ticker?.cancel()
        controller?.removeListener(listener)
        connectFuture?.let { MediaController.releaseFuture(it) }
        connectFuture = null
        controller = null
        _state.value = PlayerUiState()
    }

    // ---------- 控制 ----------

    fun playQueue(songs: List<Song>, startIndex: Int = 0) {
        val c = controller ?: return
        if (songs.isEmpty()) return
        val items = songs.map { toMediaItem(it) }
        c.setMediaItems(items, startIndex.coerceIn(0, songs.lastIndex), 0L)
        // 设置播放列表标题（系统媒体控件/原子随身听展示用）
        c.playlistMetadata = MediaMetadata.Builder()
            .setTitle("TGZJDV's Music 播放列表")
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .build()
        c.prepare()
        c.play()
        syncState()
    }

    /** 播放单曲；若当前队列已包含该曲目则直接跳转过去 */
    fun playSong(song: Song, queue: List<Song>? = null) {
        val c = controller ?: return
        val list = queue?.takeIf { it.isNotEmpty() } ?: listOf(song)
        val index = list.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        // 队列与当前一致时只切歌，避免重建播放列表打断播放
        val currentIds = (0 until c.mediaItemCount).mapNotNull { c.getMediaItemAt(it).mediaId.toIntOrNull() }
        if (currentIds == list.map { it.id }) {
            c.seekTo(index, 0L)
            c.play()
            syncState()
            return
        }
        playQueue(list, index)
    }

    fun toggle() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
        syncState()
    }

    fun next() {
        controller?.let {
            if (it.currentMediaItemIndex < it.mediaItemCount - 1) {
                it.seekToNextMediaItem()
            } else {
                it.seekTo(0, 0L) // 循环回第一首
            }
            it.play()
        }
    }

    fun prev() {
        controller?.let {
            if (it.currentPosition > 3000) it.seekTo(0L)
            else if (it.currentMediaItemIndex > 0) it.seekToPreviousMediaItem()
            else it.seekTo(0L)
        }
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0L))
        syncState()
    }

    /** 循环模式：关 → 列表循环 → 单曲循环 → 关 */
    fun cycleRepeatMode() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        syncState()
    }

    fun setRepeatMode(mode: Int) {
        controller?.repeatMode = mode
        syncState()
    }

    fun currentQueue(): List<Song> {
        val c = controller ?: return emptyList()
        return (0 until c.mediaItemCount).mapNotNull { songOf(c.getMediaItemAt(it)) }
    }

    fun stop() {
        controller?.apply {
            stop()
            clearMediaItems()
        }
        syncState()
    }

    // ---------- 内部 ----------

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                if (controller?.isPlaying == true) syncState()
                delay(500)
            }
        }
    }

    private fun syncState() {
        val c = controller
        if (c == null) {
            _state.value = PlayerUiState()
            return
        }
        val song = c.currentMediaItem?.let { songOf(it) }
        val fallbackDuration = (song?.duration ?: 0).toLong() * 1000L
        _state.value = PlayerUiState(
            song = song,
            isPlaying = c.isPlaying,
            positionMs = c.currentPosition.coerceAtLeast(0L),
            durationMs = if (c.duration > 0) c.duration else fallbackDuration,
            bufferedMs = c.bufferedPosition.coerceAtLeast(0L),
            hasNext = c.currentMediaItemIndex < c.mediaItemCount - 1,
            hasPrev = c.currentMediaItemIndex > 0,
            repeatMode = c.repeatMode,
            connected = true,
        )
    }

    private fun songOf(item: MediaItem): Song? {
        val raw = item.mediaMetadata.extras?.getString(EXTRA_SONG) ?: return null
        return runCatching { AppJson.instance.decodeFromString(Song.serializer(), raw) }.getOrNull()
    }

    private fun toMediaItem(song: Song): MediaItem {
        val extras = Bundle().apply {
            putString(EXTRA_SONG, AppJson.instance.encodeToString(Song.serializer(), song))
            putString("genre", song.genre)
            putString("uploader", song.uploaderName ?: "")
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.displayArtist)
            .setAlbumTitle(song.genre.ifBlank { "TGZJDV's Music" })
            .setGenre(song.genre)
            .setArtworkUri(ApiClient.coverUrl(song)?.toUri())
            .setIsBrowsable(false)
            .setIsPlayable(true)
            // 声明支持“收藏”（HEART）：系统媒体控件/原子随身听据此决定是否显示心形收藏按钮
            .setUserRating(HeartRating(favoriteIds.contains(song.id)))
            .setOverallRating(HeartRating(favoriteIds.contains(song.id)))
            .setExtras(extras)
            .build()

        return MediaItem.Builder()
            .setMediaId(song.id.toString())
            .setUri(ApiClient.streamUrl(song.id))
            .setMediaMetadata(metadata)
            .build()
    }
}
