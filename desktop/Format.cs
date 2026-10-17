using System.Globalization;

namespace FamousMusic.Desktop;

/// <summary>格式化工具（与 Android 端 util/Format.kt 行为一致）</summary>
public static class Format
{
    /// <summary>秒 → mm:ss</summary>
    public static string Duration(int seconds)
    {
        if (seconds <= 0) return "0:00";
        return $"{seconds / 60}:{seconds % 60:00}";
    }

    /// <summary>毫秒 → mm:ss</summary>
    public static string DurationMs(long ms) => ms <= 0 ? "0:00" : Duration((int)(ms / 1000));

    /// <summary>Timespan → m:ss 或 h:mm:ss</summary>
    public static string Clock(TimeSpan t)
    {
        if (t < TimeSpan.Zero) t = TimeSpan.Zero;
        return t.TotalHours >= 1
            ? $"{(int)t.TotalHours}:{t.Minutes:00}:{t.Seconds:00}"
            : $"{t.Minutes}:{t.Seconds:00}";
    }

    /// <summary>相对时间（后端时间按 UTC 解析）</summary>
    public static string Relative(string? iso)
    {
        if (string.IsNullOrWhiteSpace(iso)) return "";
        if (!TryParseServerTime(iso, out var parsed))
            return iso.Length > 16 ? iso[..16].Replace('T', ' ') : iso;

        var diff = DateTimeOffset.UtcNow - parsed;
        var min = (long)diff.TotalMinutes;
        return min switch
        {
            < 1 => "刚刚",
            < 60 => $"{min} 分钟前",
            < 60 * 24 => $"{min / 60} 小时前",
            < 60 * 24 * 30 => $"{min / 60 / 24} 天前",
            < 60L * 24 * 365 => $"{min / 60 / 24 / 30} 个月前",
            _ => $"{min / 60 / 24 / 365} 年前",
        };
    }

    private static bool TryParseServerTime(string value, out DateTimeOffset result)
    {
        // 后端返回 "YYYY-MM-DD HH:MM:SS"（UTC），也兼容 ISO8601
        var patterns = new[] { "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss" };
        foreach (var p in patterns)
        {
            if (DateTimeOffset.TryParseExact(value, p, CultureInfo.InvariantCulture,
                    DateTimeStyles.AssumeUniversal | DateTimeStyles.AdjustToUniversal, out result))
                return true;
        }
        result = default;
        return false;
    }
}
