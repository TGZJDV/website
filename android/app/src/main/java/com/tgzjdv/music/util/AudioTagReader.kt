package com.tgzjdv.music.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.InputStream

/** 从音频文件读出的元数据（用于上传时自动填充表单） */
data class AudioTags(
    val title: String? = null,
    val artist: String? = null,
    val genre: String? = null,
    val album: String? = null,
    val durationSec: Int = 0,
    val coverBytes: ByteArray? = null,
    val coverMime: String? = null,
    val lyrics: String? = null,
)

/**
 * 音频标签读取（对齐网站端 music-metadata 的效果）：
 * 1) MediaMetadataRetriever —— 各格式的基础标签与内嵌封面
 * 2) MP3 的 ID3v2 —— 补歌词（USLT）与缺失字段、封面（APIC）
 * 3) FLAC 的 Vorbis comment / PICTURE —— 补歌词与封面
 */
object AudioTagReader {

    fun read(context: Context, uri: Uri, displayName: String): AudioTags {
        var title: String? = null
        var artist: String? = null
        var genre: String? = null
        var album: String? = null
        var durationSec = 0
        var coverBytes: ByteArray? = null
        var coverMime: String? = null
        var lyrics: String? = null

        // ---------- 1) 系统元数据（所有格式） ----------
        runCatching {
            val mmr = MediaMetadataRetriever()
            try {
                mmr.setDataSource(context, uri)
                title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.trim()?.nonBlank()
                artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.trim()?.nonBlank()
                genre = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)?.trim()?.nonBlank()
                album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.trim()?.nonBlank()
                durationSec =
                    ((mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) / 1000L).toInt()
                mmr.embeddedPicture?.takeIf { it.isNotEmpty() }?.let {
                    coverBytes = it
                    coverMime = sniffImageMime(it)
                }
            } finally {
                runCatching { mmr.release() }
            }
        }

        val lower = displayName.lowercase()

        // ---------- 2) MP3: ID3v2 ----------
        if (lower.endsWith(".mp3") || lower.endsWith(".mp2")) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val tag = Id3v2.parse(input)
                    if (title == null) title = tag.title
                    if (artist == null) artist = tag.artist
                    if (genre == null) genre = tag.genre
                    if (album == null) album = tag.album
                    if (coverBytes == null && tag.picture != null) {
                        coverBytes = tag.picture
                        coverMime = tag.pictureMime
                    }
                    if (lyrics == null) lyrics = tag.lyrics
                }
            }
        }

        // ---------- 3) FLAC: Vorbis comment + PICTURE ----------
        if (lower.endsWith(".flac")) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val tag = Flac.parse(input)
                    if (title == null) title = tag.title
                    if (artist == null) artist = tag.artist
                    if (genre == null) genre = tag.genre
                    if (album == null) album = tag.album
                    if (coverBytes == null && tag.picture != null) {
                        coverBytes = tag.picture
                        coverMime = tag.pictureMime
                    }
                    if (lyrics == null) lyrics = tag.lyrics
                }
            }
        }

        return AudioTags(
            title = title,
            artist = artist,
            genre = genre,
            album = album,
            durationSec = durationSec,
            coverBytes = coverBytes,
            coverMime = coverMime,
            lyrics = lyrics,
        )
    }

    /** 按字节头判断图片类型 */
    fun sniffImageMime(bytes: ByteArray): String = when {
        bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() -> "image/png"
        bytes.size >= 4 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" -> "image/webp"
        else -> "image/jpeg"
    }

    private fun String.nonBlank(): String? = takeIf { it.isNotBlank() }

    // ============================================================
    // MP3: ID3v2.3 / ID3v2.4
    // ============================================================
    private object Id3v2 {
        data class Tag(
            val title: String?,
            val artist: String?,
            val album: String?,
            val genre: String?,
            val lyrics: String?,
            val picture: ByteArray?,
            val pictureMime: String?,
        )

        private val EMPTY = Tag(null, null, null, null, null, null, null)

        fun parse(input: InputStream): Tag {
            val header = ByteArray(10)
            if (!readFully(input, header)) return EMPTY
            if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) return EMPTY

            val major = header[3].toInt() and 0xFF
            val flags = header[4].toInt() and 0xFF
            val tagSize = syncSafeInt(header, 6)
            if (tagSize <= 0 || tagSize > 20 * 1024 * 1024) return EMPTY

            val tag = ByteArray(tagSize)
            if (!readFully(input, tag)) return EMPTY

            var pos = 0
            if (flags and 0x40 != 0 && tag.size >= 6) {
                pos += if (major >= 4) syncSafeInt(tag, 0) else readInt(tag, 0) + 4
            }
            if (pos < 0 || pos >= tag.size) return EMPTY

            var title: String? = null
            var artist: String? = null
            var album: String? = null
            var genre: String? = null
            var lyrics: String? = null
            var picture: ByteArray? = null
            var pictureMime: String? = null

            while (pos + 10 <= tag.size) {
                val id = String(tag, pos, 4, Charsets.ISO_8859_1)
                if (id[0] == '\u0000' || id.isBlank()) break
                val size = if (major >= 4) syncSafeInt(tag, pos + 4) else readInt(tag, pos + 4)
                if (size <= 0 || pos + 10 + size > tag.size) break
                val start = pos + 10
                when (id) {
                    "TIT2" -> if (title == null) title = textFrame(tag, start, size)
                    "TPE1" -> if (artist == null) artist = textFrame(tag, start, size)
                    "TALB" -> if (album == null) album = textFrame(tag, start, size)
                    "TCON" -> if (genre == null) genre = cleanGenre(textFrame(tag, start, size))
                    "USLT" -> if (lyrics == null) lyrics = usltFrame(tag, start, size)
                    "APIC" -> if (picture == null) {
                        apicFrame(tag, start, size)?.let { picture = it.data; pictureMime = it.mime }
                    }
                }
                pos = start + size
            }

            return Tag(title, artist, album, genre, lyrics, picture, pictureMime)
        }

        private data class Apic(val mime: String, val data: ByteArray)

        private fun textFrame(buf: ByteArray, off: Int, len: Int): String? {
            if (len <= 1) return null
            val enc = buf[off].toInt() and 0xFF
            return decode(buf, off + 1, off + len, enc)?.trim('\u0000', ' ', '\r', '\n')?.nonBlank()
        }

        private fun usltFrame(buf: ByteArray, off: Int, len: Int): String? {
            val end = off + len
            if (len < 5) return null
            val enc = buf[off].toInt() and 0xFF
            val p = skipTerminated(buf, off + 4, end, enc) // 跳过 语言(3) 与内容描述
            if (p >= end) return null
            return decode(buf, p, end, enc)?.trim('\u0000')?.nonBlank()
        }

        private fun apicFrame(buf: ByteArray, off: Int, len: Int): Apic? {
            val end = off + len
            if (len < 4) return null
            val enc = buf[off].toInt() and 0xFF
            val mimeEnd = indexOfZero(buf, off + 1, end)
            if (mimeEnd < 0) return null
            val mime = String(buf, off + 1, mimeEnd - off - 1, Charsets.ISO_8859_1).ifBlank { "image/jpeg" }
            val p = skipTerminated(buf, mimeEnd + 2, end, enc) // 跳过 mime 的 \0 + pictureType(1)
            if (p >= end) return null
            return Apic(mime, buf.copyOfRange(p, end))
        }

        /** 跳过以 \0（或 UTF-16 的 \0\0）结尾的字符串 */
        private fun skipTerminated(buf: ByteArray, start: Int, end: Int, enc: Int): Int {
            var p = start.coerceAtLeast(0)
            if (enc == 1 || enc == 2) {
                while (p + 1 < end) {
                    if (buf[p] == 0.toByte() && buf[p + 1] == 0.toByte()) return p + 2
                    p += 2
                }
                return end
            }
            while (p < end) {
                if (buf[p] == 0.toByte()) return p + 1
                p++
            }
            return end
        }

        private fun decode(buf: ByteArray, from: Int, to: Int, enc: Int): String? {
            if (to <= from || from < 0 || to > buf.size) return null
            val bytes = buf.copyOfRange(from, to)
            return runCatching {
                when (enc) {
                    0 -> String(bytes, Charsets.ISO_8859_1)
                    1 -> String(bytes, Charsets.UTF_16) // 带 BOM
                    2 -> String(bytes, Charsets.UTF_16BE)
                    else -> String(bytes, Charsets.UTF_8)
                }
            }.getOrNull()
        }

        /** TCON 可能是 "(17)" 这类 ID3v1 数值形式，去掉前缀数字 */
        private fun cleanGenre(g: String?): String? =
            g?.replace(Regex("^\\((\\d+)\\)\\s*"), "")?.trim()?.nonBlank()
    }

    // ============================================================
    // FLAC: Vorbis comment + PICTURE
    // ============================================================
    private object Flac {
        data class Tag(
            val title: String?,
            val artist: String?,
            val album: String?,
            val genre: String?,
            val lyrics: String?,
            val picture: ByteArray?,
            val pictureMime: String?,
        )

        private val EMPTY = Tag(null, null, null, null, null, null, null)

        fun parse(input: InputStream): Tag {
            val magic = ByteArray(4)
            if (!readFully(input, magic)) return EMPTY
            if (String(magic, Charsets.US_ASCII) != "fLaC") return EMPTY

            var title: String? = null
            var artist: String? = null
            var album: String? = null
            var genre: String? = null
            var lyrics: String? = null
            var picture: ByteArray? = null
            var pictureMime: String? = null

            while (true) {
                val head = ByteArray(4)
                if (!readFully(input, head)) break
                val last = (head[0].toInt() and 0x80) != 0
                val type = head[0].toInt() and 0x7F
                val size = ((head[1].toInt() and 0xFF) shl 16) or
                    ((head[2].toInt() and 0xFF) shl 8) or (head[3].toInt() and 0xFF)
                if (size < 0 || size > 16 * 1024 * 1024) break

                val block = ByteArray(size)
                if (!readFully(input, block)) break

                when (type) {
                    4 -> { // VORBIS_COMMENT
                        val c = parseVorbisComments(block)
                        title = title ?: c["TITLE"]
                        artist = artist ?: c["ARTIST"]
                        album = album ?: c["ALBUM"]
                        genre = genre ?: c["GENRE"]
                        if (lyrics == null) {
                            lyrics = c["LYRICS"] ?: c["UNSYNCEDLYRICS"] ?: c["UNSYNCED LYRICS"]
                        }
                    }
                    6 -> { // PICTURE
                        if (picture == null) {
                            parsePicture(block)?.let { picture = it.data; pictureMime = it.mime }
                        }
                    }
                }
                if (last) break
            }

            return Tag(title, artist, album, genre, lyrics, picture, pictureMime)
        }

        private fun parseVorbisComments(block: ByteArray): Map<String, String> {
            val map = mutableMapOf<String, String>()
            if (block.size < 8) return map
            var p = 0
            val vendorLen = readLEInt(block, p)
            if (vendorLen < 0 || p + 4 + vendorLen + 4 > block.size) return map
            p += 4 + vendorLen
            val count = readLEInt(block, p)
            p += 4
            repeat(count.coerceIn(0, 500)) {
                if (p + 4 > block.size) return map
                val len = readLEInt(block, p)
                p += 4
                if (len < 0 || p + len > block.size) return map
                val entry = runCatching { String(block, p, len, Charsets.UTF_8) }.getOrNull()
                p += len
                if (entry != null) {
                    val idx = entry.indexOf('=')
                    if (idx > 0) {
                        val key = entry.substring(0, idx).trim().uppercase()
                        val value = entry.substring(idx + 1).trim()
                        if (value.isNotEmpty()) map.putIfAbsent(key, value)
                    }
                }
            }
            return map
        }

        private data class Picture(val mime: String, val data: ByteArray)

        private fun parsePicture(block: ByteArray): Picture? {
            if (block.size < 8) return null
            var p = 4 // picture type
            val mimeLen = readInt(block, p)
            p += 4
            if (mimeLen < 0 || p + mimeLen > block.size) return null
            val mime = String(block, p, mimeLen, Charsets.US_ASCII).ifBlank { "image/jpeg" }
            p += mimeLen
            val descLen = readInt(block, p)
            p += 4
            if (descLen < 0 || p + descLen > block.size) return null
            p += descLen + 16 // width/height/depth/colors
            if (p + 4 > block.size) return null
            val dataLen = readInt(block, p)
            p += 4
            if (dataLen < 0 || p + dataLen > block.size) return null
            return Picture(mime, block.copyOfRange(p, p + dataLen))
        }
    }

    // ---------- 字节工具 ----------

    private fun readFully(input: InputStream, buf: ByteArray): Boolean {
        var read = 0
        while (read < buf.size) {
            val n = runCatching { input.read(buf, read, buf.size - read) }.getOrNull() ?: return false
            if (n < 0) return false
            read += n
        }
        return true
    }

    private fun syncSafeInt(b: ByteArray, off: Int): Int {
        if (off + 4 > b.size) return 0
        return ((b[off].toInt() and 0x7F) shl 21) or
            ((b[off + 1].toInt() and 0x7F) shl 14) or
            ((b[off + 2].toInt() and 0x7F) shl 7) or
            (b[off + 3].toInt() and 0x7F)
    }

    private fun readInt(b: ByteArray, off: Int): Int {
        if (off + 4 > b.size) return 0
        return ((b[off].toInt() and 0xFF) shl 24) or
            ((b[off + 1].toInt() and 0xFF) shl 16) or
            ((b[off + 2].toInt() and 0xFF) shl 8) or
            (b[off + 3].toInt() and 0xFF)
    }

    private fun readLEInt(b: ByteArray, off: Int): Int {
        if (off + 4 > b.size) return 0
        return (b[off].toInt() and 0xFF) or
            ((b[off + 1].toInt() and 0xFF) shl 8) or
            ((b[off + 2].toInt() and 0xFF) shl 16) or
            ((b[off + 3].toInt() and 0xFF) shl 24)
    }

    private fun indexOfZero(b: ByteArray, from: Int, to: Int): Int {
        var p = from.coerceAtLeast(0)
        while (p < to) {
            if (b[p] == 0.toByte()) return p
            p++
        }
        return -1
    }
}
