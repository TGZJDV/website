package com.tgzjdv.music.playback

/**
 * 原子随身听（vivo `com.vivo.musicwidgetmix`）第三方接入协议。
 *
 * 协议由反编译 4.0.4.8 还原，实现要点：
 *  1. 应用需先被写入 `musicwidget_list_pkg_type_key` 白名单（Shizuku / root，重启后需重做）
 *  2. 通过 [META_SUPPORT_EVENT] 在 MediaMetadata 里声明能力位，决定循环/收藏/列表按钮是否点亮
 *  3. 随身听通过 custom action 下发指令（见 [ACTION_FAVORITE] / [ACTION_PLAY_MODE] / [ACTION_GET_LIST]）
 *  4. 歌词通过 `MediaSession.setSessionExtras()` 主动推送
 *
 * 注意：键名里的 `meida` / `meidia` 是 **vivo 原版拼写错误**，必须照抄，写对了反而不生效。
 */
object VivoWidget {

    // ---------- MediaMetadata extras ----------

    /** 能力位掩码（int），见 [SUPPORT_ALL] 及各 EVENT_* 常量 */
    const val META_SUPPORT_EVENT = "vivomusicmix.media.metadata.support_event"

    /** 当前循环模式（int）：1=列表 2=单曲 3=随机 */
    const val META_LOOP_MODE = "vivomusicmix.media.metadata.LOOP_MODE"

    // ---------- 能力位（MusicData.EventType） ----------

    const val EVENT_PLAY_CONTROL = 1
    const val EVENT_MUSIC_INFO = 2
    const val EVENT_MUSIC_IMAGE = 4
    const val EVENT_MUSIC_LRC = 8
    const val EVENT_SEEK_POSITION = 16
    const val EVENT_MUSIC_TAG = 32
    const val EVENT_FAVORITE = 64
    const val EVENT_LOOP_MODE = 128
    const val EVENT_LIST_CURRENT = 256
    const val EVENT_LIST_FAVORITE = 512
    const val EVENT_LIST_LOCAL = 1024
    const val EVENT_PLAY_INDEX = 2048

    /** 全能力 = 0xFFF（酷狗 / QQ音乐 / i音乐 用的就是这个值） */
    const val SUPPORT_ALL = 4095

    // ---------- 循环模式取值 ----------

    const val LOOP_LIST = 1
    const val LOOP_SINGLE = 2
    const val LOOP_RANDOM = 3

    // ---------- 随身听下发的 custom action ----------

    const val ACTION_FAVORITE = "vivomusicmix.media.action.FAVORITE"
    const val ACTION_PLAY_MODE = "vivomusicmix.media.action.PLAY_MODE"
    const val ACTION_GET_LIST = "vivomusicmix.media.action.GET_LIST"

    // ---------- 推送歌词用的 session extras ----------

    /** ⚠️ vivo 原版拼写：`meida`（不是 media） */
    const val EXTRA_ACTION_KEY = "vivomusicmix.meida.extra.key.action"

    /** 该 action 的值，表示「歌词变了」 */
    const val EXTRA_LRC_CHANGE = "vivomusicmix.extra.lrc_change"

    /** ⚠️ vivo 原版拼写：`meidia_id`（不是 media_id） */
    const val EXTRA_MEDIA_ID = "vivomusicmix.extra.key.meidia_id"

    /** 歌词正文（LRC 或纯文本） */
    const val EXTRA_LYRIC = "vivomusicmix.extra.key.lyric"

    // ---------- 合作方 AIDL 服务入口（可选，功能更全） ----------

    /** 声明该 action 的 exported Service 会被随身听主动绑定（酷狗/网易云走这条路） */
    const val SUPPORT_SERVICE_ACTION = "com.vivo.musicwidgetmix.support.service"
}
