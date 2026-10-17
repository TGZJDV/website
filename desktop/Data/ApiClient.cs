using System.Net;
using System.Net.Http;
using System.Text;
using System.Text.Json;
using FamousMusic.Desktop.Services;

namespace FamousMusic.Desktop.Data;

/// <summary>API 调用异常（Message 可直接展示给用户）</summary>
public sealed class ApiException : Exception
{
    public int Status { get; }
    public ApiException(string message, int status = 0) : base(message) => Status = status;
}

/// <summary>
/// 后端 API 客户端。端点、请求体、响应字段全部对照 Android 端 ApiClient.kt / Models.kt。
/// 音频/封面地址同样按 ApiClient.streamUrl / coverUrl 的规则拼接。
/// </summary>
public sealed class ApiClient
{
    public const string Base = "https://api.famousmusic.asia/api";

    private static readonly JsonSerializerOptions JsonOpts = new()
    {
        PropertyNameCaseInsensitive = true,
        PropertyNamingPolicy = null,
    };

    /// <summary>共享 HttpClient（流地址解析也用它）</summary>
    public static HttpClient Http { get; } = CreateHttp();

    private static HttpClient CreateHttp()
    {
        var handler = new HttpClientHandler
        {
            AutomaticDecompression = DecompressionMethods.GZip | DecompressionMethods.Deflate,
            AllowAutoRedirect = true,
        };
        var c = new HttpClient(handler) { Timeout = TimeSpan.FromSeconds(40) };
        c.DefaultRequestHeaders.Add("User-Agent", "FamousMusic-Desktop/1.0 (Windows)");
        c.DefaultRequestHeaders.Add("Accept", "application/json");
        return c;
    }

    /// <summary>当前 JWT，登录后由 App 注入；为 null 时不带 Authorization 头</summary>
    public string? Token { get; set; }

    // ---------- 资源地址（对照 Kotlin） ----------

    public static string StreamUrl(int songId) => $"{Base}/songs/{songId}/stream";
    public static string LyricsUrl(int songId) => $"{Base}/songs/{songId}/lyrics";
    public static string? CoverUrl(Song song) =>
        string.IsNullOrWhiteSpace(song.CoverKey) ? null : $"{Base}/songs/{song.Id}/cover";
    public static string AvatarUrl(User user) =>
        $"{Base}/users/{user.Id}/avatar?v={Uri.EscapeDataString(user.AvatarKey ?? "default")}";

    // ---------- 底层请求 ----------

    private HttpRequestMessage Build(HttpMethod method, string path, string? bodyJson = null)
    {
        var req = new HttpRequestMessage(method, Base + path);
        if (!string.IsNullOrEmpty(Token))
            req.Headers.TryAddWithoutValidation("Authorization", "Bearer " + Token);
        if (bodyJson != null)
            req.Content = new StringContent(bodyJson, Encoding.UTF8, "application/json");
        return req;
    }

    private async Task<string> SendAsync(HttpMethod method, string path, string? bodyJson = null)
    {
        // 幂等的 GET 网络抖动自动重试两次（播放/搜索偶发超时多半是瞬时网络问题）
        var maxAttempts = method == HttpMethod.Get ? 3 : 1;

        for (var attempt = 1; ; attempt++)
        {
            HttpResponseMessage res;
            try
            {
                res = await Http.SendAsync(Build(method, path, bodyJson));
            }
            catch (Exception ex)
            {
                if (attempt < maxAttempts && IsTransient(ex))
                {
                    Diag.Log($"retry {attempt}/{maxAttempts} {method} {path}: {ex.GetType().Name}");
                    await Task.Delay(400 * attempt);
                    continue;
                }
                // 记录到底层日志，方便排查（用户只看到友好文案）
                LogNetFailure(method, path, ex);
                if (IsTransient(ex))
                    throw new ApiException("网络连接失败，请检查网络后重试", 0);
                throw new ApiException("请求异常：" + ex.Message, 0);
            }

            using (res)
            {
                var text = await res.Content.ReadAsStringAsync();

                // 5xx / 429 也当作可重试
                if (!res.IsSuccessStatusCode && attempt < maxAttempts
                    && ((int)res.StatusCode >= 500 || (int)res.StatusCode == 429))
                {
                    Diag.Log($"retry {attempt}/{maxAttempts} {method} {path}: HTTP {(int)res.StatusCode}");
                    await Task.Delay(400 * attempt);
                    continue;
                }

                if (!res.IsSuccessStatusCode)
                {
                    string? msg = null;
                    try { msg = JsonSerializer.Deserialize<ErrorBody>(text, JsonOpts)?.Error; } catch { }
                    throw new ApiException(string.IsNullOrWhiteSpace(msg) ? $"请求失败 ({(int)res.StatusCode})" : msg!, (int)res.StatusCode);
                }
                return text;
            }
        }
    }

    private static bool IsTransient(Exception ex) =>
        ex is HttpRequestException || ex is TaskCanceledException || ex is IOException;

    /// <summary>把网络失败详情写到 %LOCALAPPDATA%\FamousMusic\net.log（仅用于排查）</summary>
    private static void LogNetFailure(HttpMethod method, string path, Exception ex)
    {
        try
        {
            var dir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "FamousMusic");
            Directory.CreateDirectory(dir);
            File.AppendAllText(Path.Combine(dir, "net.log"),
                $"[{DateTime.Now:yyyy-MM-dd HH:mm:ss}] {method} {Base + path}\r\n{ex}\r\n\r\n");
        }
        catch
        {
            // 日志失败不影响主流程
        }
    }

    private async Task<T> GetAsync<T>(string path) =>
        JsonSerializer.Deserialize<T>(await SendAsync(HttpMethod.Get, path), JsonOpts)
        ?? throw new ApiException("服务器返回了空响应");

    private async Task<T> SendJsonAsync<B, T>(HttpMethod method, string path, B body) =>
        JsonSerializer.Deserialize<T>(await SendAsync(method, path, JsonSerializer.Serialize(body, JsonOpts)), JsonOpts)
        ?? throw new ApiException("服务器返回了空响应");

    private static string Enc(string s) => Uri.EscapeDataString(s);

    // ---------- 认证 ----------

    public Task<AuthResponse> LoginAsync(string email, string password) =>
        SendJsonAsync<LoginRequest, AuthResponse>(HttpMethod.Post, "/auth/login", new LoginRequest { Email = email, Password = password });

    public Task<MeResponse> MeAsync() => GetAsync<MeResponse>("/auth/me");

    // ---------- 歌曲 ----------

    private static string SongsPath(string? q, string? genre, int page, int limit)
    {
        var parts = new List<string>();
        if (!string.IsNullOrWhiteSpace(q)) parts.Add("q=" + Enc(q));
        if (!string.IsNullOrWhiteSpace(genre)) parts.Add("genre=" + Enc(genre));
        parts.Add("page=" + page);
        parts.Add("limit=" + limit);
        return "/songs?" + string.Join("&", parts);
    }

    public Task<SongListResponse> ListSongsAsync(string? q = null, string? genre = null, int page = 1, int limit = 20) =>
        GetAsync<SongListResponse>(SongsPath(q, genre, page, limit));

    /// <summary>
    /// 拉取**全部**歌曲（自动翻页）。后端 /songs 的 limit 被夹在 50，必须按 page 翻页。
    /// onProgress 每页回来时回调，用于边加载边刷新列表。
    /// </summary>
    public async Task<SongListResponse> ListAllSongsAsync(
        string? q = null, string? genre = null, int pageSize = 50,
        Action<List<Song>, int>? onProgress = null)
    {
        var collected = new List<Song>();
        var page = 1;
        var total = 0;
        while (true)
        {
            var res = await ListSongsAsync(q, genre, page, pageSize);
            total = res.Total;
            if (res.Songs.Count == 0) break;
            collected.AddRange(res.Songs);
            onProgress?.Invoke(new List<Song>(collected), total);
            if (collected.Count >= total) break;
            if (res.Songs.Count < pageSize) break;
            page++;
            if (page > 200) break; // 保险：避免 total 异常导致无限翻页
        }
        return new SongListResponse { Songs = collected, Total = total, Page = 1, Limit = pageSize };
    }

    public Task<SongDetailResponse> SongDetailAsync(int id) => GetAsync<SongDetailResponse>($"/songs/{id}");

    public async Task<string> LyricsAsync(int songId) => await SendAsync(HttpMethod.Get, $"/songs/{songId}/lyrics");

    public Task<GenreListResponse> GenresAsync() => GetAsync<GenreListResponse>("/songs/genres");

    public Task<SongListResponse> FavoritesAsync() => GetAsync<SongListResponse>("/songs/favorites");

    public async Task<FavoriteResponse> SetFavoriteAsync(int id, bool favorited)
    {
        var text = await SendAsync(favorited ? HttpMethod.Post : HttpMethod.Delete, $"/songs/{id}/favorite");
        return JsonSerializer.Deserialize<FavoriteResponse>(text, JsonOpts) ?? new FavoriteResponse();
    }

    // ---------- 歌单 ----------

    public Task<PlaylistListResponse> MyPlaylistsAsync() => GetAsync<PlaylistListResponse>("/playlists/mine");

    public Task<PlaylistDetailResponse> PlaylistDetailAsync(int id) => GetAsync<PlaylistDetailResponse>($"/playlists/{id}");

    public Task<CreateIdResponse> CreatePlaylistAsync(string name) =>
        SendJsonAsync<CreatePlaylistRequest, CreateIdResponse>(HttpMethod.Post, "/playlists", new CreatePlaylistRequest { Name = name });

    public async Task DeletePlaylistAsync(int id)
    {
        await SendAsync(HttpMethod.Delete, $"/playlists/{id}");
    }
}
