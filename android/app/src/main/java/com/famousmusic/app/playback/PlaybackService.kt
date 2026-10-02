package com.famousmusic.app.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.HeartRating
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Rating
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.famousmusic.app.MainActivity
import com.famousmusic.app.R
import com.famousmusic.app.data.ApiClient
import com.famousmusic.app.data.AppJson
import com.famousmusic.app.data.Song
import com.famousmusic.app.data.TokenStore
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 后台播放服务。
 *
 * 用 [MediaLibraryService]（而不是 MediaSessionService）是为了支持
 * **浏览/订阅接口** —— 原子随身听的「播放列表 / 收藏列表 / 下载列表」
 * 是通过 `MediaBrowserCompat.subscribe(parentId)` 拉取的。
 *
 * 同时实现原子随身听的第三方接入协议，见 [VivoWidget]。
 */
@UnstableApi
class PlaybackService : MediaLibraryService() {

    private var mediaSession: MediaLibrarySession? = null
    private lateinit var player: ExoPlayer
    private var mediaCache: SimpleCache? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var currentFavorited = false

    /**
     * 浏览列表条目缓存：mediaId → 带 URI 与完整元数据的原始 MediaItem。
     * 随身听从列表点播时只回传 mediaId，靠它把完整信息还原回去，
     * 否则 App 界面会认为「没有正在播放的音乐」。
     */
    private val browseCache = java.util.concurrent.ConcurrentHashMap<String, MediaItem>()

    override fun onCreate() {
        super.onCreate()

        // ---- 音频缓存：边下边存，重复播放/拖动进度不再走网络 ----
        // 放在外部文件目录（随卸载清理），便于检查占用；不可用时退回内部缓存目录
        val audioCacheDir = getExternalFilesDir("audio") ?: java.io.File(cacheDir, "audio")
        val cache = SimpleCache(
            audioCacheDir,
            LeastRecentlyUsedCacheEvictor(AUDIO_CACHE_BYTES),
            StandaloneDatabaseProvider(this),
        )
        mediaCache = cache
        val upstream = DefaultDataSource.Factory(
            this,
            DefaultHttpDataSource.Factory()
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(30_000),
        )
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
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

        // 均衡器：跟随播放器的音频会话
        EqualizerManager.attach(this, player.audioSessionId)
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                EqualizerManager.attach(this@PlaybackService, audioSessionId)
            }
        })

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaLibrarySession.Builder(this, player, SessionCallback())
            .setSessionActivity(sessionActivity)
            .setCustomLayout(ImmutableList.of(favoriteButton(false)))
            .build()

        // 切歌：刷新收藏状态、推送歌词、通知浏览端列表变化、记录播放进度
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                refreshFavoriteState()
                pushLyricsToVivoWidget(mediaItem)
                savePlayback()
                runCatching {
                    mediaSession?.notifyChildrenChanged(LIST_CURRENT, player.mediaItemCount, null)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) = savePlayback()
        })

        // 循环/随机变化时：写回 LOOP_MODE（随身听用）+ 记录状态
        player.addListener(object : Player.Listener {
            override fun onRepeatModeChanged(repeatMode: Int) {
                syncVivoLoopMode()
                savePlayback()
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                syncVivoLoopMode()
                savePlayback()
            }
        })

        PlaybackStateStore.init(this)
        restoreLastPlayback()

        // 播放中每 5 秒记录一次进度：进程被系统杀掉时也能还原到大致位置
        scope.launch {
            while (true) {
                kotlinx.coroutines.delay(5_000)
                if (player.isPlaying) savePlayback()
            }
        }

        // 探针：确认播放器是否真的发出了元数据变化事件
        player.addListener(object : Player.Listener {
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                probe("onMediaMetadataChanged loop=${mediaMetadata.extras?.getInt(VivoWidget.META_LOOP_MODE, -1)} title=${mediaMetadata.title}")
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                probe("onMediaItemTransition id=${mediaItem?.mediaId} reason=$reason")
            }
        })
    }

    // ---------- 媒体条目构造 ----------

    /** 把 Song 组装成带 vivo 能力位元数据的 MediaItem */
    private fun toMediaItem(song: Song): MediaItem {
        val extras = Bundle().apply {
            // App 界面靠这个 key 还原「正在播放」的歌曲信息
            putString(EXTRA_SONG, AppJson.instance.encodeToString(Song.serializer(), song))
            putInt(VivoWidget.META_SUPPORT_EVENT, VivoWidget.SUPPORT_ALL)
            putInt(VivoWidget.META_LOOP_MODE, vivoLoopModeOf(player))
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.displayArtist)
            .setAlbumTitle(song.genre.ifBlank { "TGZJDV's Music" })
            .setGenre(song.genre)
            .setArtworkUri(ApiClient.coverUrl(song)?.toUri())
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setUserRating(HeartRating(false))
            .setOverallRating(HeartRating(false))
            .setExtras(extras)
            .build()
        return MediaItem.Builder()
            .setMediaId(song.id.toString())
            .setUri(ApiClient.streamUrl(song.id))
            .setMediaMetadata(metadata)
            .build()
    }

    /**
     * Media3 对「浏览子项」有硬性要求：mediaId 非空、mediaMetadata 必须显式声明
     * isBrowsable / isPlayable，且不能带本地播放配置（URI）。否则
     * [LibraryResult.ofItemList] 会抛 IllegalArgumentException 直接把进程崩掉。
     * 这里统一把条目归一化成合法的浏览子项。
     */
    private fun asBrowseChild(item: MediaItem): MediaItem {
        // 缓存原始条目（带 URI 与完整元数据），供列表点播时还原
        browseCache[item.mediaId] = item
        val md = item.mediaMetadata.buildUpon()
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .build()
        return MediaItem.Builder()
            .setMediaId(item.mediaId)
            .setMediaMetadata(md)
            .build()
    }

    /** 当前播放队列（取播放器时间线，元数据已齐全） */
    private fun currentQueueItems(): List<MediaItem> =
        (0 until player.mediaItemCount).map { asBrowseChild(player.getMediaItemAt(it)) }

    // ---------- 通知栏自定义按钮 ----------

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
            // 关键：把真实的收藏状态写回元数据（MediaMetadataCompat 的 RATING），
            // 否则随身听的爱心永远显示「未收藏」
            updateRatingMetadata(fav)
            probe("refreshFavoriteState songId=$songId fav=$fav")
        }
    }

    // ---------- 探针（release 版 Log 会被裁掉，写文件便于 adb 取证） ----------

    private fun probe(msg: String) {
        runCatching {
            java.io.File(getExternalFilesDir(null), "probe.log")
                .appendText("${System.currentTimeMillis()} $msg\n")
        }
    }

    // ---------- 「上次播放到哪」的保存 / 恢复 ----------

    /** 记录当前队列、下标与播放位置（服务销毁后下次打开可还原） */
    private fun savePlayback() {
        val p = mediaSession?.player ?: return
        if (p.mediaItemCount == 0) {
            // 队列被清空（例如「停止播放」）→ 一起清掉存档，避免下次还原出已删的歌单
            PlaybackStateStore.clear()
            return
        }
        val queue = (0 until p.mediaItemCount).mapNotNull { i ->
            p.getMediaItemAt(i).mediaMetadata.extras?.getString(EXTRA_SONG)?.let { raw ->
                runCatching { AppJson.instance.decodeFromString(Song.serializer(), raw) }.getOrNull()
            }
        }
        if (queue.isEmpty()) return
        PlaybackStateStore.save(
            SavedPlayback(
                queue = queue,
                index = p.currentMediaItemIndex.coerceIn(0, queue.lastIndex),
                positionMs = p.currentPosition.coerceAtLeast(0L),
                repeatMode = p.repeatMode,
                shuffle = p.shuffleModeEnabled,
            )
        )
    }

    /**
     * 启动时还原上次的队列与进度。
     * 只 `prepare()` 不自动播放 —— 打开 App 不该突然出声，点一下播放即可接着听。
     */
    private fun restoreLastPlayback() {
        val p = mediaSession?.player ?: return
        if (p.mediaItemCount > 0) return
        val saved = PlaybackStateStore.load() ?: return
        if (saved.queue.isEmpty()) return
        runCatching {
            val items = saved.queue.map { toMediaItem(it) }
            val idx = saved.index.coerceIn(0, items.lastIndex)
            p.repeatMode = saved.repeatMode
            p.shuffleModeEnabled = saved.shuffle
            p.setMediaItems(items, idx, saved.positionMs.coerceAtLeast(0L))
            p.prepare()
            probe("restoreLastPlayback queue=${items.size} index=$idx pos=${saved.positionMs}")
        }.onFailure { probe("restoreLastPlayback 失败 ${it.message}") }
    }

    // ---------- 原子随身听（vivo）协议实现 ----------

    private fun toggleFavorite() = setFavorite(!currentFavorited)

    /**
     * 收藏/取消收藏到**指定状态**。
     * 原子随身听走 `MediaControllerCompat.setRating(HeartRating(目标值))`，
     * 传入的是目标状态而非「切换」，所以用 set 语义。
     */
    private fun setFavorite(target: Boolean) {
        val songId = player.currentMediaItem?.mediaId?.toIntOrNull() ?: return
        probe("setFavorite target=$target songId=$songId loggedIn=${TokenStore.isLoggedIn}")
        if (!TokenStore.isLoggedIn) {
            Toast.makeText(this, "未登录，无法收藏", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            runCatching {
                if (target) ApiClient.favorite(songId) else ApiClient.unfavorite(songId)
            }.onSuccess {
                probe("setFavorite 结果 favorited=${it.favorite} count=${it.favoriteCount}")
                applyFavorite(it.favorite)
                updateRatingMetadata(it.favorite)
            }.onFailure {
                probe("setFavorite 失败 ${it.message}")
                Toast.makeText(this@PlaybackService, "收藏失败：${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** 把收藏状态写回元数据（MediaMetadataCompat 的 RATING），随身听爱心据此刷新 */
    private fun updateRatingMetadata(favorited: Boolean) {
        val p = mediaSession?.player ?: return
        val item = p.currentMediaItem ?: return
        // 已经是目标状态就跳过：replaceMediaItem 会让 timeline 抖动，UI 会闪错歌
        val current = (item.mediaMetadata.userRating as? HeartRating)?.isHeart == true
        if (current == favorited) return
        val newItem = item.buildUpon()
            .setMediaMetadata(
                item.mediaMetadata.buildUpon()
                    .setUserRating(HeartRating(favorited))
                    .setOverallRating(HeartRating(favorited))
                    .build()
            )
            .build()
        p.replaceMediaItem(p.currentMediaItemIndex, newItem)
    }

    /** 随身听 PLAY_MODE 指令：1=列表循环 2=单曲循环 3=随机 */
    private fun applyVivoLoopMode(mode: Int) {
        val p = mediaSession?.player ?: return
        when (mode) {
            VivoWidget.LOOP_SINGLE -> {
                p.shuffleModeEnabled = false
                p.repeatMode = Player.REPEAT_MODE_ONE
            }
            VivoWidget.LOOP_RANDOM -> {
                p.shuffleModeEnabled = true
                p.repeatMode = Player.REPEAT_MODE_ALL
            }
            else -> {
                p.shuffleModeEnabled = false
                p.repeatMode = Player.REPEAT_MODE_ALL
            }
        }
        syncVivoLoopMode()
    }

    private fun vivoLoopModeOf(p: Player): Int = when {
        p.shuffleModeEnabled -> VivoWidget.LOOP_RANDOM
        p.repeatMode == Player.REPEAT_MODE_ONE -> VivoWidget.LOOP_SINGLE
        else -> VivoWidget.LOOP_LIST
    }

    /** 把当前循环状态写回曲目元数据；随身听 onMetadataChanged 时回读并更新图标 */
    private fun syncVivoLoopMode() {
        val p = mediaSession?.player ?: return
        val item = p.currentMediaItem ?: return
        val target = vivoLoopModeOf(p)
        if (item.mediaMetadata.extras?.getInt(VivoWidget.META_LOOP_MODE, -1) == target) return
        val extras = Bundle().apply {
            putAll(item.mediaMetadata.extras ?: Bundle())
            putInt(VivoWidget.META_LOOP_MODE, target)
        }
        val newItem = item.buildUpon()
            .setMediaMetadata(
                item.mediaMetadata.buildUpon()
                    .setExtras(extras)
                    // ⚠️ 关键：Media3 只在「非 extras 字段」变化时才发出 metadata 变更通知，
                    // 只改 extras 不会推送，随身听也就读不到新的 LOOP_MODE。
                    // 这里把循环模式镜像到 trackNumber（本应用不展示该字段）以强制触发通知；
                    // 仍走 replaceMediaItem，URI 未变 → 不会重新 prepare，不断流。
                    .setTrackNumber(target)
                    .build()
            )
            .build()
        // 用 replaceMediaItem 而不是重建队列：Media3 在 localConfiguration 未变时
        // 只更新元数据、不重新 prepare，因此不会打断播放、也不会丢失总时长
        p.replaceMediaItem(p.currentMediaItemIndex, newItem)
        Log.i(TAG, "syncVivoLoopMode -> $target")
        probe("syncLoop -> $target")
    }

    /**
     * 向原子随身听推送歌词：[MediaSession.setSessionExtras]，
     * 随身听在 CooperateController.onExtrasChanged 里读取。
     * 键名里的 `meida` / `meidia` 是 vivo 原版拼写，必须照抄。
     */
    private fun pushLyricsToVivoWidget(mediaItem: MediaItem?) {
        val item = mediaItem ?: return
        val songId = item.mediaId.toIntOrNull() ?: return
        scope.launch {
            val text = runCatching { ApiClient.lyrics(songId) }.getOrDefault("")
            val extras = Bundle().apply {
                putString(VivoWidget.EXTRA_ACTION_KEY, VivoWidget.EXTRA_LRC_CHANGE)
                putString(VivoWidget.EXTRA_MEDIA_ID, item.mediaId)
                putString(VivoWidget.EXTRA_LYRIC, text)
            }
            mediaSession?.setSessionExtras(extras)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        savePlayback()
        val p = mediaSession?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        savePlayback()
        scope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        EqualizerManager.release()
        mediaCache?.release()
        mediaCache = null
        super.onDestroy()
    }

    /** 播放器 / 浏览 / 随身听协议回调 */
    private inner class SessionCallback : MediaLibrarySession.Callback {

        // ---------- 原子随身听：自定义指令 ----------

        /**
         * 原子随身听通过 legacy `sendCommand(action, extras)` 下发指令。
         * Media3 要求自定义指令必须先在 onConnect 里声明，否则会被直接拒绝。
         */
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            Log.i(TAG, "onConnect pkg=${controller.packageName}")
            probe("onConnect pkg=${controller.packageName} uid=${controller.uid}")
            val default = super.onConnect(session, controller)
            val commands = default.availableSessionCommands.buildUpon()
                // 收藏走 setRating，需要显式授予该会话指令（否则 legacy 控制器会被拒）
                .add(SessionCommand(SessionCommand.COMMAND_CODE_SESSION_SET_RATING))
                .add(SessionCommand(VivoWidget.ACTION_FAVORITE, Bundle.EMPTY))
                .add(SessionCommand(VivoWidget.ACTION_PLAY_MODE, Bundle.EMPTY))
                .add(SessionCommand(VivoWidget.ACTION_GET_LIST, Bundle.EMPTY))
                .build()
            val builder = MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .setAvailablePlayerCommands(default.availablePlayerCommands)
            default.customLayout?.let { builder.setCustomLayout(it) }
            default.sessionActivity?.let { builder.setSessionActivity(it) }
            default.sessionExtras?.let { builder.setSessionExtras(it) }
            // 随身听只在 extras「发生变化」时读歌词；它可能切歌之后才连上来，故补推一次
            pushLyricsToVivoWidget(player.currentMediaItem)
            return builder.build()
        }

        /**
         * 原子随身听的收藏爱心：`setRating(HeartRating(目标状态))`。
         * Media3 有 **两个重载**（带/不带 mediaId），且默认实现各自独立返回「不支持」，
         * 所以两个都必须覆盖，否则可能一个都不会被调用。
         */
        override fun onSetRating(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            rating: Rating,
        ): ListenableFuture<SessionResult> = handleSetRating(controller, rating)

        override fun onSetRating(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaId: String,
            rating: Rating,
        ): ListenableFuture<SessionResult> = handleSetRating(controller, rating)

        private fun handleSetRating(
            controller: MediaSession.ControllerInfo,
            rating: Rating,
        ): ListenableFuture<SessionResult> {
            Log.i(TAG, "onSetRating rating=$rating from ${controller.packageName}")
            probe("onSetRating rating=$rating pkg=${controller.packageName}")
            return if (rating is HeartRating) {
                setFavorite(rating.isHeart)
                Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            } else {
                Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
            }
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            Log.i(TAG, "onCustomCommand action=${customCommand.customAction} args=${args.keySet()}")
            probe("onCustomCommand ${customCommand.customAction} args=${args.keySet()}")
            when (customCommand.customAction) {
                ACTION_FAVORITE, VivoWidget.ACTION_FAVORITE -> {
                    toggleFavorite()
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                VivoWidget.ACTION_PLAY_MODE -> {
                    val mode = args.getInt(VivoWidget.META_LOOP_MODE, VivoWidget.LOOP_LIST)
                    applyVivoLoopMode(mode)
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                VivoWidget.ACTION_GET_LIST -> {
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        // ---------- 浏览接口（随身听的三个列表标签） ----------

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            Log.i(TAG, "onGetLibraryRoot from ${browser.packageName}")
            val root = MediaItem.Builder()
                .setMediaId(LIB_ROOT)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("TGZJDV's Music")
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .build()
                )
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(root, params))
        }

        override fun onSubscribe(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> {
            Log.i(TAG, "onSubscribe parentId=$parentId from ${browser.packageName}")
            return Futures.immediateFuture(LibraryResult.ofVoid(params))
        }

        override fun onUnsubscribe(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
        ): ListenableFuture<LibraryResult<Void>> =
            Futures.immediateFuture(LibraryResult.ofVoid())

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            Log.i(TAG, "onGetChildren parentId=$parentId page=$page from ${browser.packageName}")            // 随身听会用根节点订阅，这里把「根」也当当前播放列表处理
            val listId = if (parentId == LIB_ROOT) LIST_CURRENT else parentId
            probe("onGetChildren parentId=$parentId -> $listId (queue=${player.mediaItemCount})")
            return when (listId) {
                LIST_CURRENT -> Futures.immediateFuture(
                    LibraryResult.ofItemList(currentQueueItems(), params)
                )

                LIST_FAVORITE -> {
                    val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
                    scope.launch {
                        val songs = runCatching { ApiClient.favorites().songs }.getOrDefault(emptyList())
                        future.set(LibraryResult.ofItemList(songs.map { asBrowseChild(toMediaItem(it)) }, params))
                    }
                    future
                }

                else -> Futures.immediateFuture(
                    LibraryResult.ofItemList(emptyList(), params)
                )
            }
        }

        // ---------- 播放 ----------

        /**
         * 列表点播：随身听会带着 mediaId 走 playFromMediaId →
         * 优先用浏览缓存还原**完整条目**（含 URI、标题、歌手、封面与 song_json），
         * 这样 App 界面也能正确显示「正在播放」。
         */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val resolved = mediaItems.map { item ->
                if (item.localConfiguration != null) {
                    item
                } else {
                    val cached = browseCache[item.mediaId]
                    if (cached != null) {
                        probe("onAddMediaItems 命中缓存 ${item.mediaId}")
                        cached
                    } else {
                        probe("onAddMediaItems 未命中缓存 ${item.mediaId}，仅补 URI")
                        val id = item.mediaId.toIntOrNull()
                        if (id == null) {
                            item
                        } else {
                            item.buildUpon()
                                .setUri(ApiClient.streamUrl(id))
                                .setMediaId(id.toString())
                                .setMediaMetadata(
                                    item.mediaMetadata.buildUpon()
                                        .setIsBrowsable(false)
                                        .setIsPlayable(true)
                                        .build()
                                )
                                .build()
                        }
                    }
                }
            }.toMutableList()
            return Futures.immediateFuture(resolved)
        }

        /** 供 playFromMediaId 查询条目（返回合法的浏览子项形态） */
        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val found = currentQueueItems().firstOrNull { it.mediaId == mediaId }
                ?: MediaItem.Builder()
                    .setMediaId(mediaId)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setIsBrowsable(false)
                            .setIsPlayable(true)
                            .build()
                    )
                    .build()
            return Futures.immediateFuture(LibraryResult.ofItem(found, null))
        }
    }

    companion object {
        private const val ACTION_FAVORITE = "com.famousmusic.app.action.TOGGLE_FAVORITE"
        private const val TAG = "VivoWidget"

        /** 与 PlayerManager 约定：MediaMetadata.extras 里存 Song 的 JSON */
        private const val EXTRA_SONG = "song_json"

        /** 音频缓存上限：256 MB（约 80~100 首） */
        private const val AUDIO_CACHE_BYTES = 256L * 1024 * 1024

        /** 浏览根节点 */
        private const val LIB_ROOT = "vivomusicmix_root"

        /** 随身听请求的三个列表（parentId 必须与 vivo 原版一致） */
        private const val LIST_CURRENT = "vivomusicmix_current_list"
        private const val LIST_FAVORITE = "vivomusicmix_favorite_list"
    }
}
