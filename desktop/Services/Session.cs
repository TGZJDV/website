using System.Text.Json;
using System.Text.Json.Serialization;
using FamousMusic.Desktop.Data;

namespace FamousMusic.Desktop.Services;

/// <summary>
/// 登录会话：保存 JWT + 当前用户，并持久化到 %LOCALAPPDATA%\FamousMusic\session.json。
/// token 一旦存在就会注入 ApiClient 的 Authorization 头。
/// </summary>
public sealed class Session
{
    public static Session Current { get; } = new();

    private static readonly JsonSerializerOptions StoreJson = new() { WriteIndented = true };

    public ApiClient Api { get; } = new();
    public User? User { get; private set; }
    public bool IsLoggedIn => !string.IsNullOrEmpty(Api.Token);

    /// <summary>登录状态变化（登录 / 登出 / 用户信息刷新）</summary>
    public event Action? Changed;

    private static string StoreDir =>
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "FamousMusic");

    private static string StorePath => Path.Combine(StoreDir, "session.json");

    private sealed class Stored
    {
        [JsonPropertyName("token")] public string Token { get; set; } = "";
        [JsonPropertyName("user")] public User? User { get; set; }
    }

    public void Load()
    {
        try
        {
            if (!File.Exists(StorePath)) return;
            var stored = JsonSerializer.Deserialize<Stored>(File.ReadAllText(StorePath), StoreJson);
            if (stored == null) return;
            Api.Token = string.IsNullOrWhiteSpace(stored.Token) ? null : stored.Token;
            User = stored.User;
        }
        catch
        {
            // 本地凭据损坏就当作未登录，不影响启动
            Api.Token = null;
            User = null;
        }
    }

    private void Save()
    {
        try
        {
            Directory.CreateDirectory(StoreDir);
            File.WriteAllText(StorePath, JsonSerializer.Serialize(new Stored { Token = Api.Token ?? "", User = User }, StoreJson));
        }
        catch
        {
            // 写盘失败不影响使用
        }
    }

    public async Task LoginAsync(string email, string password)
    {
        var res = await Api.LoginAsync(email, password);
        if (string.IsNullOrWhiteSpace(res.Token)) throw new ApiException("登录失败：服务器未返回 token");
        Api.Token = res.Token;
        User = res.User;
        Save();
        Changed?.Invoke();
        _ = FavoriteService.Current.RefreshAsync();
    }

    /// <summary>注册（需要先 <see cref="SendCodeAsync"/> 拿验证码）。成功后自动登录。</summary>
    public async Task RegisterAsync(string email, string username, string password, string code)
    {
        var res = await Api.RegisterAsync(email, username, password, code);
        if (string.IsNullOrWhiteSpace(res.Token)) throw new ApiException("注册失败：服务器未返回 token");
        Api.Token = res.Token;
        User = res.User;
        Save();
        Changed?.Invoke();
        _ = FavoriteService.Current.RefreshAsync();
    }

    /// <summary>发验证码。purpose = "register" 或 "reset"</summary>
    public async Task SendCodeAsync(string email, string purpose)
    {
        var res = await Api.SendCodeAsync(email, purpose);
        if (!res.Success) throw new ApiException(res.Message ?? "验证码发送失败");
    }

    public async Task ForgotAsync(string email)
    {
        var res = await Api.ForgotAsync(email);
        if (!res.Success) throw new ApiException(res.Message ?? "验证码发送失败");
    }

    public async Task ResetPasswordAsync(string email, string code, string newPassword)
    {
        var res = await Api.ResetPasswordAsync(email, code, newPassword);
        if (!res.Success) throw new ApiException(res.Message ?? "重置密码失败");
    }

    /// <summary>用本地 token 拉一次 /auth/me，token 失效则自动登出</summary>
    public async Task RefreshMeAsync()
    {
        if (!IsLoggedIn) return;
        try
        {
            var me = await Api.MeAsync();
            if (me.User != null)
            {
                User = me.User;
                Save();
                Changed?.Invoke();
                // 冷启动恢复会话时顺手把收藏状态拉回来（失败不影响启动）
                if (!FavoriteService.Current.IsLoaded) _ = FavoriteService.Current.RefreshAsync();
            }
        }
        catch (ApiException ex) when (ex.Status == 401 || ex.Status == 403)
        {
            Logout();
        }
        catch
        {
            // 网络问题：保留本地状态
        }
    }

    public void Logout()
    {
        Api.Token = null;
        User = null;
        try { if (File.Exists(StorePath)) File.Delete(StorePath); } catch { }
        // 换账号/登出必须清掉「已收藏」本地集合，否则下一账号会看到上一个账号的红心
        FavoriteService.Current.Clear();
        LyricsService.ClearCache();
        Changed?.Invoke();
    }
}
