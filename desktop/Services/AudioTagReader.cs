namespace FamousMusic.Desktop.Services;

/// <summary>音频标签（从文件里读出来的元数据，用于上传时自动填表）</summary>
public sealed class AudioTags
{
    public string? Title { get; set; }
    public string? Artist { get; set; }
    public string? Genre { get; set; }
    public string? Album { get; set; }
    /// <summary>时长（秒）</summary>
    public int DurationSec { get; set; }
    public byte[]? CoverBytes { get; set; }
    public string? CoverMime { get; set; }
    /// <summary>内嵌歌词（ID3 USLT / FLAC LYRICS），有的话可直接当 lyricsName 上传</summary>
    public string? Lyrics { get; set; }
}

/// <summary>
/// 读音频文件的 ID3 标签。
///
/// 移植自 Android 端 <c>util/AudioTagReader.kt</c>（那边已验证可用），只保留
/// 桌面端真正需要的部分：
///   - MP3：ID3v2.3 / ID3v2.4（TIT2/TPE1/TALB/TCON/USLT/APIC）
///   - FLAC：Vorbis comment（TITLE/ARTIST/ALBUM/GENRE/LYRICS）+ PICTURE
///   - 其它格式（m4a/wav/ogg）：不解析标签，由 UI 用文件名兜底
///
/// 时长单独用 <see cref="TryGetDurationAsync"/>（走 MediaFoundation，各格式都支持）。
/// 全部方法都不抛异常：解析失败返回 null，UI 用文件名兜底。
/// </summary>
public static class AudioTagReader
{
    public static AudioTags Read(string path)
    {
        var tags = new AudioTags();
        var ext = Path.GetExtension(path).ToLowerInvariant();

        try
        {
            using var fs = File.OpenRead(path);
            switch (ext)
            {
                case ".mp3":
                case ".mp2":
                    ReadId3v2(fs, tags);
                    break;
                case ".flac":
                    ReadFlac(fs, tags);
                    break;
            }
        }
        catch (Exception ex)
        {
            Diag.Log($"AudioTagReader.Read 失败 {path}: {ex.Message}");
        }

        return tags;
    }

    /// <summary>
    /// 用 MediaFoundation 读时长（秒）。各格式通用，失败返回 0。
    /// 注意：必须在 UI 线程或有 DispatcherQueue 的线程调用（MediaSource 要求），
    /// 所以这里用 TaskCompletionSource 把异步转同步也没用 —— 直接暴露 async 版本。
    /// </summary>
    public static async Task<int> TryGetDurationAsync(string path)
    {
        try
        {
            var file = await Windows.Storage.StorageFile.GetFileFromPathAsync(path);
            var props = await file.Properties.GetMusicPropertiesAsync();
            return (int)Math.Round(props.Duration.TotalSeconds);
        }
        catch (Exception ex)
        {
            Diag.Log($"读时长失败 {path}: {ex.Message}");
            return 0;
        }
    }

    /// <summary>按字节头判断图片 MIME</summary>
    public static string SniffImageMime(byte[] bytes)
    {
        if (bytes.Length >= 8 && bytes[0] == 0x89 && bytes[1] == (byte)'P') return "image/png";
        if (bytes.Length >= 4 && bytes[0] == (byte)'R' && bytes[1] == (byte)'I' &&
            bytes[2] == (byte)'F' && bytes[3] == (byte)'F') return "image/webp";
        if (bytes.Length >= 2 && bytes[0] == 0xFF && bytes[1] == 0xD8) return "image/jpeg";
        return "image/jpeg";
    }

    /// <summary>按扩展名给音频猜 MIME（OSS PUT 的 Content-Type 必须匹配，否则部分浏览器/播放器会拒）</summary>
    public static string MimeForAudio(string pathOrName) => Path.GetExtension(pathOrName).ToLowerInvariant() switch
    {
        ".mp3" => "audio/mpeg",
        ".flac" => "audio/flac",
        ".m4a" or ".aac" => "audio/mp4",
        ".wav" => "audio/wav",
        ".ogg" => "audio/ogg",
        ".wma" => "audio/x-ms-wma",
        _ => "application/octet-stream",
    };

    // ============================================================
    // MP3: ID3v2.3 / ID3v2.4
    // ============================================================

    private static void ReadId3v2(Stream input, AudioTags tags)
    {
        var header = new byte[10];
        if (!ReadFully(input, header)) return;
        if (header[0] != 'I' || header[1] != 'D' || header[2] != '3') return;

        var major = header[3];
        var flags = header[4];
        var tagSize = SyncSafeInt(header, 6);
        if (tagSize <= 0 || tagSize > 20 * 1024 * 1024) return;

        var tag = new byte[tagSize];
        if (!ReadFully(input, tag)) return;

        var pos = 0;
        // 扩展头
        if ((flags & 0x40) != 0 && tag.Length >= 6)
            pos += (major >= 4 ? SyncSafeInt(tag, 0) : ReadInt(tag, 0)) + 4;
        if (pos < 0 || pos >= tag.Length) return;

        while (pos + 10 <= tag.Length)
        {
            var id = System.Text.Encoding.ASCII.GetString(tag, pos, 4);
            if (id[0] == '\0' || string.IsNullOrWhiteSpace(id)) break;

            var size = major >= 4 ? SyncSafeInt(tag, pos + 4) : ReadInt(tag, pos + 4);
            if (size <= 0 || pos + 10 + size > tag.Length) break;

            var start = pos + 10;
            switch (id)
            {
                case "TIT2": tags.Title ??= TextFrame(tag, start, size); break;
                case "TPE1": tags.Artist ??= TextFrame(tag, start, size); break;
                case "TALB": tags.Album ??= TextFrame(tag, start, size); break;
                case "TCON": tags.Genre ??= CleanGenre(TextFrame(tag, start, size)); break;
                case "USLT": tags.Lyrics ??= UsltFrame(tag, start, size); break;
                case "APIC":
                    if (tags.CoverBytes == null)
                    {
                        var (mime, data) = ApicFrame(tag, start, size);
                        if (data != null)
                        {
                            tags.CoverBytes = data;
                            tags.CoverMime = mime;
                        }
                    }
                    break;
            }
            pos = start + size;
        }
    }

    private static string? TextFrame(byte[] buf, int off, int len)
    {
        if (len <= 1) return null;
        var enc = buf[off];
        return Decode(buf, off + 1, off + len, enc)?.Trim('\0', ' ', '\r', '\n') is { Length: > 0 } s ? s : null;
    }

    private static string? UsltFrame(byte[] buf, int off, int len)
    {
        var end = off + len;
        if (len < 5) return null;
        var enc = buf[off];
        // 跳过 编码(1) + 语言(3) + 内容描述
        var p = SkipTerminated(buf, off + 4, end, enc);
        if (p >= end) return null;
        return Decode(buf, p, end, enc)?.Trim('\0') is { Length: > 0 } s ? s : null;
    }

    private static (string mime, byte[]? data) ApicFrame(byte[] buf, int off, int len)
    {
        var end = off + len;
        if (len < 4) return ("image/jpeg", null);

        var enc = buf[off];
        var mimeEnd = IndexOfZero(buf, off + 1, end);
        if (mimeEnd < 0) return ("image/jpeg", null);

        var mime = System.Text.Encoding.ASCII.GetString(buf, off + 1, mimeEnd - off - 1);
        if (string.IsNullOrWhiteSpace(mime)) mime = "image/jpeg";

        // 跳过 mime 的 \0 + pictureType(1)
        var p = SkipTerminated(buf, mimeEnd + 2, end, enc);
        if (p >= end) return (mime, null);

        var data = new byte[end - p];
        Array.Copy(buf, p, data, 0, data.Length);
        return (mime, data);
    }

    /// <summary>TCON 可能是 "(17)" 这类 ID3v1 数值形式</summary>
    private static string? CleanGenre(string? g)
    {
        if (string.IsNullOrWhiteSpace(g)) return null;
        var cleaned = System.Text.RegularExpressions.Regex.Replace(g, @"^\((\d+)\)\s*", "").Trim();
        return cleaned.Length > 0 ? cleaned : null;
    }

    // ============================================================
    // FLAC: Vorbis comment + PICTURE
    // ============================================================

    private static void ReadFlac(Stream input, AudioTags tags)
    {
        var magic = new byte[4];
        if (!ReadFully(input, magic)) return;
        if (System.Text.Encoding.ASCII.GetString(magic) != "fLaC") return;

        while (true)
        {
            var head = new byte[4];
            if (!ReadFully(input, head)) break;

            var last = (head[0] & 0x80) != 0;
            var type = head[0] & 0x7F;
            var size = ((head[1] & 0xFF) << 16) | ((head[2] & 0xFF) << 8) | (head[3] & 0xFF);
            if (size < 0 || size > 16 * 1024 * 1024) break;

            var block = new byte[size];
            if (!ReadFully(input, block)) break;

            if (type == 4)
            {
                var c = ParseVorbisComments(block);
                if (c.TryGetValue("TITLE", out var v)) tags.Title ??= v;
                if (c.TryGetValue("ARTIST", out v)) tags.Artist ??= v;
                if (c.TryGetValue("ALBUM", out v)) tags.Album ??= v;
                if (c.TryGetValue("GENRE", out v)) tags.Genre ??= v;
                if (tags.Lyrics == null)
                {
                    if (c.TryGetValue("LYRICS", out var l)) tags.Lyrics = l;
                    else if (c.TryGetValue("UNSYNCEDLYRICS", out l)) tags.Lyrics = l;
                    else if (c.TryGetValue("UNSYNCED LYRICS", out l)) tags.Lyrics = l;
                }
            }
            else if (type == 6 && tags.CoverBytes == null)
            {
                var (mime, data) = ParseFlacPicture(block);
                if (data != null)
                {
                    tags.CoverBytes = data;
                    tags.CoverMime = mime;
                }
            }

            if (last) break;
        }
    }

    private static Dictionary<string, string> ParseVorbisComments(byte[] block)
    {
        var map = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
        if (block.Length < 8) return map;

        var p = 0;
        var vendorLen = ReadLeInt(block, p);
        if (vendorLen < 0 || p + 4 + vendorLen + 4 > block.Length) return map;
        p += 4 + vendorLen;

        var count = ReadLeInt(block, p);
        p += 4;

        for (var i = 0; i < Math.Clamp(count, 0, 500); i++)
        {
            if (p + 4 > block.Length) return map;
            var len = ReadLeInt(block, p);
            p += 4;
            if (len < 0 || p + len > block.Length) return map;

            var entry = System.Text.Encoding.UTF8.GetString(block, p, len);
            p += len;

            var idx = entry.IndexOf('=');
            if (idx <= 0) continue;
            var key = entry[..idx].Trim();
            var value = entry[(idx + 1)..].Trim();
            if (value.Length > 0) map.TryAdd(key, value);
        }
        return map;
    }

    private static (string mime, byte[]? data) ParseFlacPicture(byte[] block)
    {
        if (block.Length < 8) return ("image/jpeg", null);

        var p = 4; // picture type
        var mimeLen = ReadInt(block, p);
        p += 4;
        if (mimeLen < 0 || p + mimeLen > block.Length) return ("image/jpeg", null);

        var mime = System.Text.Encoding.ASCII.GetString(block, p, mimeLen);
        if (string.IsNullOrWhiteSpace(mime)) mime = "image/jpeg";
        p += mimeLen;

        var descLen = ReadInt(block, p);
        p += 4;
        if (descLen < 0 || p + descLen > block.Length) return (mime, null);
        p += descLen + 16; // width/height/depth/colors

        if (p + 4 > block.Length) return (mime, null);
        var dataLen = ReadInt(block, p);
        p += 4;
        if (dataLen < 0 || p + dataLen > block.Length) return (mime, null);

        var data = new byte[dataLen];
        Array.Copy(block, p, data, 0, dataLen);
        return (mime, data);
    }

    // ---------- 编解码 / 字节工具 ----------

    private static string? Decode(byte[] buf, int from, int to, int enc)
    {
        if (to <= from || from < 0 || to > buf.Length) return null;
        try
        {
            return enc switch
            {
                0 => System.Text.Encoding.Latin1.GetString(buf, from, to - from),
                1 => System.Text.Encoding.Unicode.GetString(buf, from, to - from), // 带 BOM 的 UTF-16
                2 => System.Text.Encoding.BigEndianUnicode.GetString(buf, from, to - from),
                _ => System.Text.Encoding.UTF8.GetString(buf, from, to - from),
            };
        }
        catch
        {
            return null;
        }
    }

    /// <summary>跳过以 \0（UTF-16 时是 \0\0）结尾的字符串，返回结束后的位置</summary>
    private static int SkipTerminated(byte[] buf, int start, int end, int enc)
    {
        var p = Math.Max(start, 0);
        if (enc is 1 or 2)
        {
            while (p + 1 < end)
            {
                if (buf[p] == 0 && buf[p + 1] == 0) return p + 2;
                p += 2;
            }
            return end;
        }
        while (p < end)
        {
            if (buf[p] == 0) return p + 1;
            p++;
        }
        return end;
    }

    private static bool ReadFully(Stream input, byte[] buf)
    {
        var read = 0;
        while (read < buf.Length)
        {
            var n = input.Read(buf, read, buf.Length - read);
            if (n <= 0) return false;
            read += n;
        }
        return true;
    }

    private static int SyncSafeInt(byte[] b, int off)
    {
        if (off + 4 > b.Length) return 0;
        return ((b[off] & 0x7F) << 21) | ((b[off + 1] & 0x7F) << 14) |
               ((b[off + 2] & 0x7F) << 7) | (b[off + 3] & 0x7F);
    }

    private static int ReadInt(byte[] b, int off)
    {
        if (off + 4 > b.Length) return 0;
        return ((b[off] & 0xFF) << 24) | ((b[off + 1] & 0xFF) << 16) |
               ((b[off + 2] & 0xFF) << 8) | (b[off + 3] & 0xFF);
    }

    private static int ReadLeInt(byte[] b, int off)
    {
        if (off + 4 > b.Length) return 0;
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8) |
               ((b[off + 2] & 0xFF) << 16) | ((b[off + 3] & 0xFF) << 24);
    }

    private static int IndexOfZero(byte[] b, int from, int to)
    {
        for (var p = Math.Max(from, 0); p < to; p++)
            if (b[p] == 0) return p;
        return -1;
    }
}
