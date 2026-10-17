using System.Text.RegularExpressions;
using FamousMusic.Desktop.Data;

namespace FamousMusic.Desktop.Services;

/// <summary>
/// 一行歌词。<see cref="Time"/> 为 <see cref="TimeSpan.Zero"/> 且 <see cref="IsTimed"/> 为 false
/// 时表示「无时间轴的纯文本歌词」（只有一行整段文本）。
/// </summary>
public sealed class LyricLine
{
    public TimeSpan Time { get; init; }
    public string Text { get; init; } = "";

    /// <summary>该行是否是纯文本歌词（没有时间轴）</summary>
    public bool IsTimed { get; init; } = true;

    public override string ToString() => $"[{Time:mm\\:ss\\.ff}] {Text}";
}

/// <summary>一个歌词文档</summary>
public sealed class Lyrics
{
    public List<LyricLine> Lines { get; init; } = new();

    /// <summary>原始 LRC 文本（解析失败时可用于直接展示或排查）</summary>
    public string Raw { get; init; } = "";

    /// <summary>是否带时间轴。纯文本歌词（无 [mm:ss] 标签）时为 false。</summary>
    public bool IsTimed => Lines.Any(l => l.IsTimed);

    public bool IsEmpty => Lines.Count == 0;

    /// <summary>
    /// 按播放位置找当前应高亮的行索引。
    /// 返回 -1 表示「还没到第一句」或「纯文本歌词」。逐行线性扫描即可（歌词最多几百行）。
    /// </summary>
    public int IndexAt(TimeSpan position)
    {
        if (!IsTimed || Lines.Count == 0) return -1;
        var idx = -1;
        for (var i = 0; i < Lines.Count; i++)
        {
            if (Lines[i].Time <= position) idx = i;
            else break;
        }
        return idx;
    }
}

/// <summary>
/// 歌词服务：从 <c>GET /songs/{id}/lyrics</c> 拉 LRC 文本 → 解析成 <see cref="Lyrics"/>。
///
/// 解析规则（对照 Android 端 Lrc.kt，并兼容参考实现 MusicPlayer.cs 的正则）：
///   - 时间标签 <c>[mm:ss]</c> / <c>[mm:ss.xx]</c> / <c>[mm:ss.xxx]</c> / <c>[mm:ss:xx]</c>
///   - **一行多标签**：<c>[00:12.00][01:20.00]同一句</c> 展开成多行
///   - 无时间标签的行：整段没有标签时当作纯文本歌词；混在带标签文件里则丢弃
///   - 元信息行 <c>[ti:]</c> <c>[ar:]</c> <c>[al:]</c> <c>[by:]</c> <c>[offset:]</c> 忽略
/// 纯文本歌词与带时间轴歌词分别缓存；接口 404（无歌词）返回空 <see cref="Lyrics"/> 且缓存住，
/// 避免反复对同一首歌打 404（后端对无歌词歌曲直接返回 404 "暂无歌词"）。
/// </summary>
public static class LyricsService
{
    // [mm:ss] / [mm:ss.xx] / [mm:ss:xx] / [mm:ss.xxx]；分秒允许 1-2 位，参考实现用固定 2 位，这里放宽
    private static readonly Regex TimeTag =
        new(@"\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?\]", RegexOptions.Compiled);

    // 参考实现 MusicPlayer.cs 用的整行正则（保留以对齐行为，实际用 TimeTag 支持一行多标签）
    private static readonly Regex StrictLine =
        new(@"^\[(\d{2}):(\d{2})[\.\:](\d{2})\]\s*(.*)$", RegexOptions.Compiled);

    private static readonly Regex MetaTag =
        new(@"^\[(ti|ar|al|au|by|offset|length|re|ve):.*\]$", RegexOptions.Compiled | RegexOptions.IgnoreCase);

    /// <summary>歌词缓存：songId → Lyrics（含「确认无歌词」的负缓存）</summary>
    private static readonly Dictionary<int, Lyrics> Cache = new();

    private static readonly SemaphoreSlim Gate = new(1, 1);

    private static readonly Lyrics Empty = new() { Lines = new List<LyricLine>(), Raw = "" };

    /// <summary>清空缓存（重新登录 / 上传新歌后可选）</summary>
    public static void ClearCache()
    {
        lock (Cache) Cache.Clear();
    }

    /// <summary>
    /// 取歌词。命中缓存直接返回；404（无歌词）返回空 Lyrics 并缓存；
    /// 其它错误（网络 / 5xx）**不缓存**，下次可重试。
    /// </summary>
    public static async Task<Lyrics> GetAsync(int songId, bool forceReload = false)
    {
        if (!forceReload)
        {
            lock (Cache)
            {
                if (Cache.TryGetValue(songId, out var hit)) return hit;
            }
        }

        await Gate.WaitAsync();
        try
        {
            if (!forceReload)
            {
                lock (Cache)
                {
                    if (Cache.TryGetValue(songId, out var hit)) return hit;
                }
            }

            Lyrics result;
            try
            {
                var raw = await Session.Current.Api.LyricsAsync(songId);
                result = Parse(raw);
            }
            catch (ApiException ex) when (ex.Status == 404)
            {
                // 后端对「没有 lyrics_key」的歌返回 404 {"error":"暂无歌词"}：这是正常状态，不算错误
                result = Empty;
            }

            lock (Cache) Cache[songId] = result;
            return result;
        }
        finally
        {
            Gate.Release();
        }
    }

    /// <summary>预取（不抛异常，后台调用用）</summary>
    public static void Prefetch(int songId)
    {
        _ = Task.Run(async () =>
        {
            try { await GetAsync(songId); }
            catch (Exception ex) { Diag.Log($"lyrics prefetch {songId} failed: {ex.Message}"); }
        });
    }

    /// <summary>
    /// 解析 LRC 文本。永不抛异常（脏数据最多解析出空列表）。
    /// </summary>
    public static Lyrics Parse(string? raw)
    {
        if (string.IsNullOrWhiteSpace(raw)) return Empty;

        var timed = new List<LyricLine>();
        var plain = new List<string>();

        foreach (var line in raw.Split('\n'))
        {
            var trimmed = line.Trim().TrimEnd('\r');
            if (trimmed.Length == 0) continue;

            var matches = TimeTag.Matches(trimmed);
            if (matches.Count == 0)
            {
                if (MetaTag.IsMatch(trimmed)) continue;
                // 去掉 [00:00] 之类残缺标签后还剩下内容的，当纯文本
                var text = trimmed.Trim();
                if (text.Length > 0 && !text.StartsWith('[') && !text.EndsWith(']'))
                    plain.Add(text);
                continue;
            }

            // 文本 = 最后一个时间标签之后的内容
            var last = matches[^1];
            var body = trimmed[(last.Index + last.Length)..].Trim();
            if (body.Length == 0) continue;

            foreach (Match m in matches)
            {
                if (!int.TryParse(m.Groups[1].Value, out var min)) continue;
                if (!int.TryParse(m.Groups[2].Value, out var sec)) continue;

                var ms = 0;
                var frac = m.Groups[3].Value;
                if (frac.Length > 0 && int.TryParse(frac, out var f))
                {
                    // 1 位 = 100ms，2 位 = 10ms，3 位 = 1ms（与 Lrc.kt 一致）
                    ms = frac.Length switch
                    {
                        1 => f * 100,
                        2 => f * 10,
                        _ => f,
                    };
                }

                if (min < 0 || sec is < 0 or > 59) continue;
                timed.Add(new LyricLine
                {
                    Time = TimeSpan.FromMilliseconds(min * 60_000L + sec * 1000L + ms),
                    Text = body,
                    IsTimed = true,
                });
            }
        }

        if (timed.Count > 0)
        {
            // 稳定排序：同一时间点的多行保持原顺序
            var sorted = timed.OrderBy(l => l.Time.Ticks).ToList();
            return new Lyrics { Lines = sorted, Raw = raw };
        }

        if (plain.Count > 0)
        {
            // 纯文本歌词：合并成一行整段（UI 直接按 TextWrapping 展示）
            return new Lyrics
            {
                Lines = new List<LyricLine> { new() { Time = TimeSpan.Zero, Text = string.Join("\n", plain), IsTimed = false } },
                Raw = raw,
            };
        }

        return new Lyrics { Lines = new List<LyricLine>(), Raw = raw };
    }

    /// <summary>
    /// 从 LRC 里读 <c>[offset:+/-123]</c>（毫秒）。正数表示歌词应延后。
    /// 解析不出返回 0。需要的话把结果加到每行 Time 上（本服务默认不自动应用）。
    /// </summary>
    public static int ParseOffsetMs(string? raw)
    {
        if (string.IsNullOrWhiteSpace(raw)) return 0;
        var m = Regex.Match(raw, @"\[offset:([+-]?\d+)\]", RegexOptions.IgnoreCase);
        return m.Success && int.TryParse(m.Groups[1].Value, out var v) ? v : 0;
    }

    /// <summary>严格模式解析（与参考实现 MusicPlayer.cs 的 ParseLyrics 完全一致，供回归比对用）</summary>
    public static List<LyricLine> ParseStrict(string? raw)
    {
        var result = new List<LyricLine>();
        if (string.IsNullOrWhiteSpace(raw)) return result;

        foreach (var line in raw.Split('\n', StringSplitOptions.RemoveEmptyEntries))
        {
            var trimmed = line.Trim();
            if (trimmed.Length == 0) continue;

            var match = StrictLine.Match(trimmed);
            if (match.Success)
            {
                var min = int.Parse(match.Groups[1].Value);
                var sec = int.Parse(match.Groups[2].Value);
                var cs = int.Parse(match.Groups[3].Value); // 百分秒
                var text = match.Groups[4].Value.Trim();
                if (text.Length > 0)
                {
                    result.Add(new LyricLine
                    {
                        Time = TimeSpan.FromMilliseconds(min * 60_000 + sec * 1000 + cs * 10),
                        Text = text,
                        IsTimed = true,
                    });
                }
            }
            else
            {
                var clean = Regex.Replace(trimmed, @"\[By:.+\]", "").Trim();
                if (clean.Length > 0 && !clean.StartsWith('[') && !clean.EndsWith(']'))
                    result.Add(new LyricLine { Time = TimeSpan.Zero, Text = clean, IsTimed = false });
            }
        }
        return result;
    }
}
