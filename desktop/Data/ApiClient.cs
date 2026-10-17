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

    /// <summary>需求验证码。purpose 只接受 "register" / "reset"（后端 zod enum 校验）</summary>
    public Task<OkResponse> SendCodeAsync(string email, string purpose) =>
        SendJsonAsync<SendCodeRequest, OkResponse>(HttpMethod.Post, "/auth/send-code",
            new SendCodeRequest { Email = email, Purpose = purpose });

    /// <summary>注册（需要先 SendCodeAsync(email, "register")）。成功后返回 token + user</summary>
    public Task<AuthResponse> RegisterAsync(string email, string username, string password, string code) =>
        SendJsonAsync<RegisterRequest, AuthResponse>(HttpMethod.Post, "/auth/register",
            new RegisterRequest { Email = email, Username = username, Password = password, Code = code });

    /// <summary>忘记密码：给邮箱发重置验证码</summary>
    public Task<OkResponse> ForgotAsync(string email) =>
        SendJsonAsync<SendCodeRequest, OkResponse>(HttpMethod.Post, "/auth/forgot",
            new SendCodeRequest { Email = email, Purpose = "reset" });

    public Task<OkResponse> ResetPasswordAsync(string email, string code, string newPassword) =>
        SendJsonAsync<ResetPasswordRequest, OkResponse>(HttpMethod.Post, "/auth/reset-password",
            new ResetPasswordRequest { Email = email, Code = code, NewPassword = newPassword });

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

    // ---------- 上传（OSS 预签名直传） ----------

    /// <summary>申请预签名 PUT 地址。后端返回 audioUrl/coverUrl/lyricsUrl 与对应 key。</summary>
    public Task<PresignResponse> PresignAsync(PresignRequest req) =>
        SendJsonAsync<PresignRequest, PresignResponse>(HttpMethod.Post, "/songs/presign", req);

    /// <summary>
    /// 把字节直接 PUT 到 OSS 预签名 URL。
    /// **绝对不能带 Authorization 头**：预签名 URL 自带签名，混入 JWT 会被 OSS 判为签名不匹配而 403。
    /// Content-Type 必须与写入时一致（这里只发 content-type 一个头，签名只覆盖 host）。
    /// </summary>
    public static async Task UploadToPresignedAsync(string url, byte[] bytes, string mimeType)
    {
        using var req = new HttpRequestMessage(HttpMethod.Put, url)
        {
            Content = new ByteArrayContent(bytes),
        };
        req.Content.Headers.ContentType = new System.Net.Http.Headers.MediaTypeHeaderValue(mimeType);

        HttpResponseMessage res;
        try
        {
            res = await Http.SendAsync(req);
        }
        catch (Exception ex) when (ex is HttpRequestException || ex is TaskCanceledException || ex is IOException)
        {
            throw new ApiException("上传到存储失败，请检查网络后重试", 0);
        }

        using (res)
        {
            if (!res.IsSuccessStatusCode)
            {
                var body = await res.Content.ReadAsStringAsync();
                if (body.Length > 300) body = body[..300];
                throw new ApiException($"上传到存储失败({(int)res.StatusCode})", (int)res.StatusCode);
            }
        }
    }

    /// <summary>上传完成后登记歌曲到数据库，返回新歌曲 id</summary>
    public Task<CompleteResponse> CompleteAsync(CompleteRequest req) =>
        SendJsonAsync<CompleteRequest, CompleteResponse>(HttpMethod.Post, "/songs/complete", req);

    // ---------- 评论 ----------

    public Task<CommentListResponse> CommentsAsync(int songId) =>
        GetAsync<CommentListResponse>($"/comments/song/{songId}");

    public Task<CommentCreateResponse> CreateCommentAsync(int songId, string content) =>
        SendJsonAsync<CreateCommentRequest, CommentCreateResponse>(HttpMethod.Post, "/comments",
            new CreateCommentRequest { SongId = songId, Content = content });

    public async Task DeleteCommentAsync(int commentId)
    {
        await SendAsync(HttpMethod.Delete, $"/comments/{commentId}");
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

    /// <summary>把歌曲加入歌单（后端只允许操作自己的歌单）</summary>
    public Task<OkResponse> AddSongToPlaylistAsync(int playlistId, int songId) =>
        SendJsonAsync<AddSongRequest, OkResponse>(HttpMethod.Post, $"/playlists/{playlistId}/songs",
            new AddSongRequest { SongId = songId });

    public async Task<OkResponse> RemoveSongFromPlaylistAsync(int playlistId, int songId)
    {
        var text = await SendAsync(HttpMethod.Delete, $"/playlists/{playlistId}/songs/{songId}");
        return JsonSerializer.Deserialize<OkResponse>(text, JsonOpts) ?? new OkResponse();
    }
}
