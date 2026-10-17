using FamousMusic.Desktop.Data;

namespace FamousMusic.Desktop.Services;

/// <summary>
/// 收藏状态管理。
///
/// 存在的原因：后端 <c>GET /songs</c> 列表**不返回** favorited 字段，只有
/// <c>GET /songs/{id}</c>（单曲详情）和 <c>GET /songs/favorites</c> 才有。
/// 所以 UI 上「这颗心亮不亮」必须自己在本地维护一份「已收藏 id 集合」：
///   - 启动/登录后拉一次 <see cref="RefreshAsync"/>（/songs/favorites）
///   - 每次 <see cref="ToggleAsync"/> 用服务端返回的 favoriteCount/favorite 校正本地状态
///   - 换账号时 <see cref="Clear"/>（<see cref="Session"/> 的 Changed 事件里调）
///
/// 所有查询都是 O(1)，可直接绑到 ItemTemplate 的绑定转换器上。
/// </summary>
public sealed class FavoriteService
{
    public static FavoriteService Current { get; } = new();

    private readonly HashSet<int> _favorited = new();
    private readonly Dictionary<int, int> _counts = new();
    private readonly SemaphoreSlim _gate = new(1, 1);
    private bool _loaded;

    /// <summary>某首歌收藏状态变化（songId, 是否已收藏, 收藏总数）</summary>
    public event Action<int, bool, int>? Changed;

    /// <summary>整体刷新完成（登录后、下拉刷新时）</summary>
    public event Action? Refreshed;

    /// <summary>是否已经和服务端同步过</summary>
    public bool IsLoaded => _loaded;

    public bool IsFavorited(int songId)
    {
        lock (_favorited) return _favorited.Contains(songId);
    }

    /// <summary>收藏总数；未知返回 -1（UI 可据此不显示数字）</summary>
    public int CountOf(int songId)
    {
        lock (_counts) return _counts.TryGetValue(songId, out var n) ? n : -1;
    }

    public int Count
    {
        get { lock (_favorited) return _favorited.Count; }
    }

    /// <summary>
    /// 切换收藏。乐观更新（先改本地再发请求），失败自动回滚并抛 <see cref="ApiException"/> 给 UI 提示。
    /// 未登录时抛 ApiException（UI 侧应提示「请先登录」）。
    /// </summary>
    public async Task<bool> ToggleAsync(int songId)
    {
        if (!Session.Current.IsLoggedIn)
            throw new ApiException("请先登录后再收藏");

        var wasFavorited = IsFavorited(songId);
        var target = !wasFavorited;

        // 乐观更新
        ApplyLocal(songId, target, CountOf(songId));

        try
        {
            await _gate.WaitAsync();
            try
            {
                var res = await Session.Current.Api.SetFavoriteAsync(songId, target);
                // 以服务端返回为准校正
                ApplyLocal(songId, res.Favorite, res.FavoriteCount);
                return res.Favorite;
            }
            finally
            {
                _gate.Release();
            }
        }
        catch
        {
            // 回滚
            ApplyLocal(songId, wasFavorited, CountOf(songId));
            throw;
        }
    }

    /// <summary>显式设置（UI 明确知道要收藏还是取消时用，避免切换语义歧义）</summary>
    public async Task<bool> SetAsync(int songId, bool favorited)
    {
        if (!Session.Current.IsLoggedIn)
            throw new ApiException("请先登录后再收藏");

        var wasFavorited = IsFavorited(songId);
        ApplyLocal(songId, favorited, CountOf(songId));

        try
        {
            await _gate.WaitAsync();
            try
            {
                var res = await Session.Current.Api.SetFavoriteAsync(songId, favorited);
                ApplyLocal(songId, res.Favorite, res.FavoriteCount);
                return res.Favorite;
            }
            finally
            {
                _gate.Release();
            }
        }
        catch
        {
            ApplyLocal(songId, wasFavorited, CountOf(songId));
            throw;
        }
    }

    /// <summary>
    /// 从服务端拉全量收藏列表，重建本地集合。
    /// 未登录直接清空返回。网络失败时保留旧状态（不抛异常，避免启动被打断）。
    /// </summary>
    public async Task RefreshAsync(bool throwOnError = false)
    {
        if (!Session.Current.IsLoggedIn)
        {
            Clear();
            return;
        }

        await _gate.WaitAsync();
        try
        {
            var res = await Session.Current.Api.FavoritesAsync();
            lock (_favorited)
            {
                _favorited.Clear();
                foreach (var s in res.Songs) _favorited.Add(s.Id);
            }
            _loaded = true;
            Refreshed?.Invoke();
        }
        catch when (!throwOnError)
        {
            // 网络问题：保留本地已有的收藏状态
            Diag.Log("FavoriteService.RefreshAsync 失败，保留本地状态");
        }
        finally
        {
            _gate.Release();
        }
    }

    /// <summary>把已知的收藏歌曲 id 灌进本地（例如详情接口返回了 favorited=true）</summary>
    public void Seed(int songId, bool favorited, int count = -1)
    {
        if (count < 0) count = CountOf(songId);
        ApplyLocal(songId, favorited, count);
    }

    /// <summary>登出/换账号时清空</summary>
    public void Clear()
    {
        lock (_favorited) _favorited.Clear();
        lock (_counts) _counts.Clear();
        _loaded = false;
        Refreshed?.Invoke();
    }

    private void ApplyLocal(int songId, bool favorited, int count)
    {
        lock (_favorited)
        {
            if (favorited) _favorited.Add(songId);
            else _favorited.Remove(songId);
        }
        if (count >= 0)
        {
            lock (_counts) _counts[songId] = count;
        }
        Changed?.Invoke(songId, favorited, count);
    }
}
