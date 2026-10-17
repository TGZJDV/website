using FamousMusic.Desktop.Data;
using Windows.Storage;
using Windows.Storage.Pickers;

namespace FamousMusic.Desktop.Services;

/// <summary>上传进度阶段</summary>
public enum UploadStage
{
    Idle = 0,
    ReadingTags = 1,
    Presigning = 2,
    UploadingAudio = 3,
    UploadingCover = 4,
    UploadingLyrics = 5,
    Completing = 6,
    Done = 7,
    Failed = 8,
}

/// <summary>上传请求参数（UI 填好后交给 <see cref="UploadService.UploadAsync"/>）</summary>
public sealed class UploadRequest
{
    public StorageFile? AudioFile { get; set; }
    public string Title { get; set; } = "";
    public string Artist { get; set; } = "";
    public string Genre { get; set; } = "其他";
    public int DurationSec { get; set; }

    /// <summary>封面字节（可选，通常是从音频文件里读出来的内嵌封面）</summary>
    public byte[]? CoverBytes { get; set; }
    public string? CoverMime { get; set; }
    /// <summary>封面原文件名，后端只取扩展名；为空则按 MIME 推</summary>
    public string? CoverName { get; set; }

    /// <summary>歌词 LRC 文本（可选，来自内嵌歌词或用户粘贴）</summary>
    public string? LyricsText { get; set; }
}

/// <summary>上传进度</summary>
public sealed class UploadProgress
{
    public UploadStage Stage { get; init; }
    public double Fraction { get; init; }
    public string Message { get; init; } = "";
}

/// <summary>
/// 上传歌曲：完整走「选文件 → 读 ID3 → presign → PUT 到 OSS → complete」这条链。
///
/// ## 关键点（对照 Android 端 UploadScreen + ApiClient.kt）
/// 1. presign 返回 **audioUrl / coverUrl / lyricsUrl 三个独立预签名 PUT 地址**，
///    每个对象都要单独 PUT 一次，顺序随意但都必须成功。
/// 2. PUT 到预签名地址时**绝对不能带 Authorization**（OSS 不认 JWT，带了反而会
///    因为签名 header 集合不匹配而 403）。见 <see cref="ApiClient.UploadToPresignedAsync"/>。
/// 3. Content-Type 必须与实际内容一致，OSS 会把它记成对象的 Content-Type，
///    后续播放时浏览器/MediaPlayer 依赖它。
/// 4. 全部 PUT 成功后才调 /songs/complete 写库。中途失败不要 complete（否则库里
///    会留下指向不存在对象的记录）。
/// 5. 后端限制：音频 ≤ 100MB、封面 ≤ 10MB、歌词 ≤ 512KB。这里提前校验，避免
///    白传一遍大文件才被拒。
/// </summary>
public sealed class UploadService
{
    public static UploadService Current { get; } = new();

    public const long MaxAudioBytes = 100L * 1024 * 1024;
    public const long MaxCoverBytes = 10L * 1024 * 1024;
    public const int MaxLyricsBytes = 512 * 1024;

    public const string DefaultGenre = "其他";

    /// <summary>上传进度回调（可能在后台线程，UI 需自行 DispatcherQueue）</summary>
    public event Action<UploadProgress>? Progress;

    private void Report(UploadStage stage, double fraction, string message) =>
        Progress?.Invoke(new UploadProgress { Stage = stage, Fraction = Math.Clamp(fraction, 0, 1), Message = message });

    /// <summary>
    /// 用 Windows 文件选择器挑音频文件。
    /// WinUI3 非打包应用的 Picker 必须先用窗口句柄 InitializeWithWindow，
    /// 否则会抛 COMException（这是 WinUI3 的经典坑）。
    /// </summary>
    public static async Task<StorageFile?> PickAudioAsync(IntPtr hwnd)
    {
        var picker = new FileOpenPicker
        {
            SuggestedStartLocation = PickerLocationId.MusicLibrary,
            ViewMode = PickerViewMode.List,
        };
        picker.FileTypeFilter.Add(".mp3");
        picker.FileTypeFilter.Add(".flac");
        picker.FileTypeFilter.Add(".m4a");
        picker.FileTypeFilter.Add(".aac");
        picker.FileTypeFilter.Add(".wav");
        picker.FileTypeFilter.Add(".ogg");
        picker.FileTypeFilter.Add(".wma");

        WinRT.Interop.InitializeWithWindow.Initialize(picker, hwnd);
        return await picker.PickSingleFileAsync();
    }

    /// <summary>
    /// 从选中的音频文件读标签，填出建议值（UI 用来预填表单）。
    /// </summary>
    public static async Task<(AudioTags Tags, string FallbackTitle)> ReadTagsAsync(StorageFile file)
    {
        var tags = AudioTagReader.Read(file.Path);
        tags.DurationSec = tags.DurationSec > 0 ? tags.DurationSec : await AudioTagReader.TryGetDurationAsync(file.Path);
        var fallback = Path.GetFileNameWithoutExtension(file.Name);
        return (tags, fallback);
    }

    /// <summary>
    /// 执行完整上传。
    /// 返回新歌曲 id（&gt;0 表示成功）。
    /// 任何失败都抛 <see cref="ApiException"/>（Message 可直接展示）。
    /// </summary>
    public async Task<int> UploadAsync(UploadRequest req, CancellationToken ct = default)
    {
        if (!Session.Current.IsLoggedIn) throw new ApiException("请先登录后再上传");
        if (req.AudioFile == null) throw new ApiException("请选择音频文件");

        var audio = req.AudioFile;
        var title = string.IsNullOrWhiteSpace(req.Title)
            ? Path.GetFileNameWithoutExtension(audio.Name)
            : req.Title.Trim();

        try
        {
            // ---------- 1. 读文件 + 前置校验 ----------
            Report(UploadStage.ReadingTags, 0.02, "读取文件…");
            ct.ThrowIfCancellationRequested();

            var audioBytes = await File.ReadAllBytesAsync(audio.Path, ct);
            if (audioBytes.Length == 0) throw new ApiException("音频文件为空");
            if (audioBytes.Length > MaxAudioBytes)
                throw new ApiException($"音频文件不能超过 100MB（当前 {audioBytes.Length / 1024.0 / 1024.0:F1}MB）");

            if (req.CoverBytes != null && req.CoverBytes.Length > MaxCoverBytes)
                throw new ApiException("封面图片不能超过 10MB");

            byte[]? lyricsBytes = null;
            if (!string.IsNullOrWhiteSpace(req.LyricsText))
            {
                lyricsBytes = System.Text.Encoding.UTF8.GetBytes(req.LyricsText);
                if (lyricsBytes.Length > MaxLyricsBytes) throw new ApiException("歌词文件不能超过 512KB");
            }

            var duration = req.DurationSec > 0
                ? req.DurationSec
                : await AudioTagReader.TryGetDurationAsync(audio.Path);

            var uploader = Session.Current.Api;

            // ---------- 2. presign ----------
            Report(UploadStage.Presigning, 0.06, "申请上传地址…");
            ct.ThrowIfCancellationRequested();

            var presignReq = new PresignRequest
            {
                Title = title,
                Artist = req.Artist?.Trim() ?? "",
                Genre = string.IsNullOrWhiteSpace(req.Genre) ? DefaultGenre : req.Genre.Trim(),
                Duration = duration,
                AudioName = audio.Name,
                CoverName = req.CoverBytes != null ? CoverNameFor(req) : null,
                LyricsName = lyricsBytes != null ? "lyrics.lrc" : null,
            };

            var presign = await uploader.PresignAsync(presignReq);
            if (string.IsNullOrWhiteSpace(presign.AudioUrl))
                throw new ApiException("服务器未返回上传地址");

            // ---------- 3. PUT 音频 ----------
            Report(UploadStage.UploadingAudio, 0.15, "上传音频…");
            ct.ThrowIfCancellationRequested();
            await ApiClient.UploadToPresignedAsync(presign.AudioUrl, audioBytes, AudioTagReader.MimeForAudio(audio.Name));
            Report(UploadStage.UploadingAudio, 0.65, "音频已上传");

            // ---------- 4. PUT 封面（可选） ----------
            if (req.CoverBytes != null && !string.IsNullOrWhiteSpace(presign.CoverUrl))
            {
                Report(UploadStage.UploadingCover, 0.72, "上传封面…");
                ct.ThrowIfCancellationRequested();
                var mime = string.IsNullOrWhiteSpace(req.CoverMime) ? AudioTagReader.SniffImageMime(req.CoverBytes) : req.CoverMime!;
                await ApiClient.UploadToPresignedAsync(presign.CoverUrl!, req.CoverBytes, mime);
            }

            // ---------- 5. PUT 歌词（可选） ----------
            if (lyricsBytes != null && !string.IsNullOrWhiteSpace(presign.LyricsUrl))
            {
                Report(UploadStage.UploadingLyrics, 0.82, "上传歌词…");
                ct.ThrowIfCancellationRequested();
                await ApiClient.UploadToPresignedAsync(presign.LyricsUrl!, lyricsBytes, "text/plain; charset=utf-8");
            }

            // ---------- 6. complete ----------
            Report(UploadStage.Completing, 0.9, "登记歌曲…");
            ct.ThrowIfCancellationRequested();

            var complete = await uploader.CompleteAsync(new CompleteRequest
            {
                Title = title,
                Artist = presignReq.Artist,
                Genre = presignReq.Genre,
                Duration = duration,
                AudioKey = presign.AudioKey,
                // 只在真的 PUT 成功后才带上 key
                CoverKey = req.CoverBytes != null && !string.IsNullOrWhiteSpace(presign.CoverUrl) ? presign.CoverKey : null,
                LyricsKey = lyricsBytes != null && !string.IsNullOrWhiteSpace(presign.LyricsUrl) ? presign.LyricsKey : null,
            });

            Report(UploadStage.Done, 1.0, "上传完成");
            Diag.Log($"UploadAsync ok id={complete.Id} title={title}");

            // 新歌上传后清掉歌词缓存里可能的负缓存（同 id 不会重复，但保证干净）
            LyricsService.ClearCache();
            return complete.Id;
        }
        catch (OperationCanceledException)
        {
            Report(UploadStage.Failed, 0, "已取消");
            throw new ApiException("上传已取消");
        }
        catch (ApiException)
        {
            Report(UploadStage.Failed, 0, "上传失败");
            throw;
        }
        catch (Exception ex)
        {
            Report(UploadStage.Failed, 0, "上传失败：" + ex.Message);
            Diag.Log("UploadAsync 异常: " + ex);
            throw new ApiException("上传失败：" + ex.Message);
        }
    }

    private static string CoverNameFor(UploadRequest req)
    {
        if (!string.IsNullOrWhiteSpace(req.CoverName)) return req.CoverName!;
        // 后端只取扩展名，按 MIME 推一个合理后缀
        var mime = req.CoverMime ?? (req.CoverBytes != null ? AudioTagReader.SniffImageMime(req.CoverBytes) : "image/jpeg");
        return mime switch
        {
            "image/png" => "cover.png",
            "image/webp" => "cover.webp",
            _ => "cover.jpg",
        };
    }
}
