package com.famousmusic.app.playback

/** 一行歌词（timeMs < 0 表示无时间轴的纯文本歌词） */
data class LrcLine(val timeMs: Long, val text: String)

/** LRC 歌词解析（支持 [mm:ss]、[mm:ss.xx]、[mm:ss.xxx] 与一行多标签） */
object LrcParser {
    private val TAG = Regex("\\[(\\d{1,2}):(\\d{1,2})(?:[.:](\\d{1,3}))?\\]")

    fun parse(raw: String): List<LrcLine> {
        val timed = mutableListOf<LrcLine>()
        val plain = mutableListOf<LrcLine>()

        raw.lines().forEach { line ->
            val matches = TAG.findAll(line).toList()
            if (matches.isEmpty()) {
                val t = line.trim()
                if (t.isNotEmpty()) plain.add(LrcLine(-1, t))
                return@forEach
            }
            val text = line.substring(matches.last().range.last + 1).trim()
            matches.forEach { m ->
                val min = m.groupValues[1].toLongOrNull() ?: 0L
                val sec = m.groupValues[2].toLongOrNull() ?: 0L
                val frac = m.groupValues[3]
                val ms = when {
                    frac.isEmpty() -> 0L
                    frac.length == 1 -> frac.toLong() * 100
                    frac.length == 2 -> frac.toLong() * 10
                    else -> frac.take(3).toLong()
                }
                timed.add(LrcLine(min * 60_000 + sec * 1000 + ms, text))
            }
        }

        return if (timed.isNotEmpty()) timed.sortedBy { it.timeMs }
        else plain
    }

    /** 当前应高亮的行索引（无匹配返回 -1） */
    fun currentIndex(lines: List<LrcLine>, positionMs: Long): Int {
        if (lines.isEmpty() || lines[0].timeMs < 0) return -1
        var idx = -1
        for (i in lines.indices) {
            val t = lines[i].timeMs
            if (t <= positionMs) idx = i else break
        }
        return idx
    }

    val isTimed: (List<LrcLine>) -> Boolean = { it.isNotEmpty() && it[0].timeMs >= 0 }
}
