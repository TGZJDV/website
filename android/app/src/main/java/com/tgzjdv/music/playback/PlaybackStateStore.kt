package com.tgzjdv.music.playback

import android.content.Context
import android.content.SharedPreferences
import com.tgzjdv.music.data.AppJson
import com.tgzjdv.music.data.Song
import kotlinx.serialization.Serializable

/**
 * 「上次播放到哪」的持久化。
 *
 * 保存的是**完整队列（Song 列表）+ 当前下标 + 播放位置 + 循环/随机状态**，
 * 所以下次打开不需要联网也能把歌单和封面还原出来。
 */
@Serializable
data class SavedPlayback(
    val queue: List<Song> = emptyList(),
    val index: Int = 0,
    val positionMs: Long = 0L,
    val repeatMode: Int = 0,
    val shuffle: Boolean = false,
    val updatedAt: Long = 0L,
)

object PlaybackStateStore {

    private const val KEY = "last_playback"
    private const val PREFS = "playback"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
    }

    fun save(state: SavedPlayback) {
        val p = prefs ?: return
        runCatching {
            val json = AppJson.instance.encodeToString(
                SavedPlayback.serializer(),
                state.copy(updatedAt = System.currentTimeMillis()),
            )
            p.edit().putString(KEY, json).apply()
        }
    }

    fun load(): SavedPlayback? {
        val raw = prefs?.getString(KEY, null) ?: return null
        return runCatching {
            AppJson.instance.decodeFromString(SavedPlayback.serializer(), raw)
        }.getOrNull()
    }

    fun clear() {
        prefs?.edit()?.remove(KEY)?.apply()
    }
}
