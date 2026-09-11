package com.famousmusic.app.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.famousmusic.app.MainActivity
import com.famousmusic.app.R
import com.famousmusic.app.data.ApiClient
import com.famousmusic.app.data.TokenStore
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 后台播放服务（Media3 MediaSessionService）：
 * - 提供通知栏 / 锁屏 / 蓝牙 / 车机 / 原子随身听 所需的 MediaSession 与元数据
 * - 支持后台播放、音频焦点、耳机拔出暂停、Wi-Fi 锁
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var currentFavorited = false

    override fun onCreate() {
        super.onCreate()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .setCallback(SessionCallback())
            .setCustomLayout(ImmutableList.of(favoriteButton(false)))
            .build()

        // 切歌后刷新「收藏」按钮状态
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                refreshFavoriteState()
            }
        })
    }

    /** 系统媒体控件/通知栏上的「收藏」按钮（MediaSession 自定义动作） */
    private fun favoriteButton(favorited: Boolean): CommandButton =
        CommandButton.Builder()
            .setDisplayName(if (favorited) "已收藏" else "收藏")
            .setIconResId(if (favorited) R.drawable.ic_favorite else R.drawable.ic_favorite_border)
            .setSessionCommand(SessionCommand(ACTION_FAVORITE, Bundle.EMPTY))
            .build()

    private fun applyFavorite(favorited: Boolean) {
        currentFavorited = favorited
        mediaSession?.setCustomLayout(ImmutableList.of(favoriteButton(favorited)))
    }

    private fun refreshFavoriteState() {
        val songId = player.currentMediaItem?.mediaId?.toIntOrNull()
        if (songId == null || !TokenStore.isLoggedIn) {
            applyFavorite(false)
            return
        }
        scope.launch {
            val fav = runCatching { ApiClient.songDetail(songId).favorited }.getOrDefault(false)
            applyFavorite(fav)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = mediaSession?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        scope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    /** 兜底：控制器只传 mediaId 时，按 id 补上播放地址与元数据 */
    private inner class SessionCallback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val resolved = mediaItems.map { item ->
                if (item.localConfiguration != null) {
                    item
                } else {
                    val id = item.mediaId.toIntOrNull()
                    if (id == null) item
                    else item.buildUpon()
                        .setUri(ApiClient.streamUrl(id))
                        .setMediaId(id.toString())
                        .build()
                }
            }.toMutableList()
            return Futures.immediateFuture(resolved)
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == ACTION_FAVORITE) {
                val songId = player.currentMediaItem?.mediaId?.toIntOrNull()
                if (songId != null && TokenStore.isLoggedIn) {
                    scope.launch {
                        runCatching {
                            if (currentFavorited) ApiClient.unfavorite(songId) else ApiClient.favorite(songId)
                        }.onSuccess { applyFavorite(it.favorite) }
                    }
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    companion object {
        private const val ACTION_FAVORITE = "com.famousmusic.app.action.TOGGLE_FAVORITE"
    }
}
