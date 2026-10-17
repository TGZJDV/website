namespace FamousMusic.Desktop.Services;

/// <summary>
/// 轻量诊断日志：写 %LOCALAPPDATA%\FamousMusic\diag.log。
/// 只在排查问题时用，正常路径没有额外开销（一次文件追加）。
/// </summary>
public static class Diag
{
    private static readonly object Gate = new();

    public static string LogPath
    {
        get
        {
            var dir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "FamousMusic");
            Directory.CreateDirectory(dir);
            return Path.Combine(dir, "diag.log");
        }
    }

    public static void Log(string message)
    {
        try
        {
            lock (Gate)
            {
                File.AppendAllText(LogPath, $"[{DateTime.Now:HH:mm:ss.fff}] {message}\r\n");
            }
        }
        catch
        {
            // 日志失败不能影响主流程
        }
    }
}
