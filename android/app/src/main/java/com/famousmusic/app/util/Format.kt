package com.famousmusic.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** 秒 → mm:ss */
fun formatDuration(seconds: Int): String {
    if (seconds <= 0) return "0:00"
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}

/** 毫秒 → mm:ss */
fun formatDurationMs(ms: Long): String {
    if (ms <= 0) return "0:00"
    return formatDuration((ms / 1000).toInt())
}

/** 相对时间（与网站 formatRelative 保持一致） */
fun formatRelative(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    val parsed = parseServerTime(iso) ?: return iso.take(16).replace('T', ' ')
    val diff = System.currentTimeMillis() - parsed
    val min = diff / 60_000
    return when {
        min < 1 -> "刚刚"
        min < 60 -> "${min} 分钟前"
        min < 60 * 24 -> "${min / 60} 小时前"
        min < 60 * 24 * 30 -> "${min / 60 / 24} 天前"
        min < 60 * 24 * 365 -> "${min / 60 / 24 / 30} 个月前"
        else -> "${min / 60 / 24 / 365} 年前"
    }
}

private fun parseServerTime(value: String): Long? {
    // 后端返回 "YYYY-MM-DD HH:MM:SS"（UTC），也兼容 ISO8601
    val patterns = listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss")
    for (p in patterns) {
        runCatching {
            val fmt = SimpleDateFormat(p, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            return fmt.parse(value)?.time ?: 0L
        }
    }
    return null
}

/** 月份/年份展示用的日期 */
fun formatDate(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return iso.take(10)
}
