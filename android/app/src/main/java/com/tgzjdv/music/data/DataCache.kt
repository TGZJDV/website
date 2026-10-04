package com.tgzjdv.music.data

import android.content.Context
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.Serializable

/**
 * HTTP 响应缓存（内存 + 磁盘）。
 *
 * 直接缓存**原始 JSON 文本**，因此与具体数据类型无关：
 * 所有 GET 接口共用同一套缓存，不需要为每个响应类型写序列化。
 * 磁盘层让冷启动也能秒出内容（断网时同样可用）。
 */
object DataCache {

    /** 磁盘最多保留的条目数，超出按最近最少使用淘汰 */
    private const val MAX_DISK_ENTRIES = 400

    private class Entry(val text: String, val at: Long)

    private val memory = ConcurrentHashMap<String, Entry>()

    @Volatile
    private var dir: File? = null

    fun init(context: Context) {
        if (dir != null) return
        // 优先用外部缓存目录（便于检查占用、随卸载清理），不可用时退回内部
        val base = context.externalCacheDir ?: context.cacheDir
        dir = File(base, "http").apply { mkdirs() }
    }

    /** 取缓存（含磁盘回读）。过期与否由调用方判断，这里返回 (文本, 写入时间戳) */
    fun get(key: String): Pair<String, Long>? {
        memory[key]?.let { return it.text to it.at }
        val f = fileFor(key) ?: return null
        if (!f.exists()) return null
        return runCatching {
            val obj = AppJson.instance.decodeFromString(CacheEntry.serializer(), f.readText())
            if (obj.key != key) return null
            memory[key] = Entry(obj.text, obj.at)
            obj.text to obj.at
        }.getOrNull()
    }

    /** 只查内存（给需要同步拿到首屏数据的场景用） */
    fun peek(key: String): String? = memory[key]?.text

    fun put(key: String, text: String, at: Long = System.currentTimeMillis()) {
        memory[key] = Entry(text, at)
        val f = fileFor(key) ?: return
        runCatching {
            f.writeText(
                AppJson.instance.encodeToString(
                    CacheEntry.serializer(),
                    CacheEntry(key, text, at),
                )
            )
        }
        evictIfNeeded()
    }

    /** 清空全部缓存（任何写操作后调用，保证列表不会显示旧数据） */
    fun clear() {
        memory.clear()
        dir?.listFiles()?.forEach { runCatching { it.delete() } }
    }

    /** 当前磁盘缓存占用（字节），用于设置页展示 */
    fun diskSizeBytes(): Long =
        dir?.listFiles()?.sumOf { it.length() } ?: 0L

    private fun evictIfNeeded() {
        val d = dir ?: return
        val files = d.listFiles() ?: return
        if (files.size <= MAX_DISK_ENTRIES) return
        files.sortedBy { it.lastModified() }
            .take(files.size - MAX_DISK_ENTRIES)
            .forEach { runCatching { it.delete() } }
    }

    private fun fileFor(key: String): File? {
        val d = dir ?: return null
        val md = MessageDigest.getInstance("MD5").digest(key.toByteArray(Charsets.UTF_8))
        val name = md.joinToString("") { "%02x".format(it) }
        return File(d, "$name.json")
    }
}

@Serializable
internal data class CacheEntry(val key: String, val text: String, val at: Long)
