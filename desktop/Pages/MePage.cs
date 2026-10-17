using FamousMusic.Desktop.Models;
using FamousMusic.Desktop.Services;
using Microsoft.UI;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;

namespace FamousMusic.Desktop.Pages;

/// <summary>我的页：登录 / 注销 + 我的收藏</summary>
public sealed class MePage : PageBase
{
    private readonly StackPanel _root = new() { Spacing = 16 };
    private readonly StackPanel _authHost = new() { Spacing = 10 };
    private readonly StackPanel _favHost = new() { Spacing = 2 };
    private readonly InfoBar _info = new() { IsClosable = true, IsOpen = false };
    private readonly TextBlock _status = UiKit.Text("", 12.5, false, UiKit.MutedBrush);
    private readonly List<SongItem> _items = new();

    private readonly TextBox _email = new()
    {
        PlaceholderText = "邮箱",
        Height = 40,
        FontSize = 14,
        VerticalContentAlignment = VerticalAlignment.Center,
    };
    private readonly PasswordBox _password = new()
    {
        PlaceholderText = "密码",
        Height = 40,
        FontSize = 14,
        VerticalContentAlignment = VerticalAlignment.Center,
    };
    private readonly Button _loginBtn = UiKit.PillButton("登录", "\uE72E", primary: true);
    private readonly ProgressRing _busy = new() { IsActive = false, Width = 18, Height = 18, Foreground = UiKit.PrimaryBrush };

    public MePage()
    {
        Margin = new Thickness(28, 24, 28, 18);
        Content = new ScrollViewer
        {
            VerticalScrollBarVisibility = ScrollBarVisibility.Auto,
            HorizontalScrollBarVisibility = ScrollBarVisibility.Disabled,
            Content = _root,
        };

        _root.Children.Add(UiKit.Header("我的", "账号、收藏与本地状态"));
        _root.Children.Add(_info);
        _root.Children.Add(_authHost);
        _root.Children.Add(_status);
        _root.Children.Add(_favHost);

        _loginBtn.Click += async (_, _) => await DoLoginAsync();
        _password.KeyDown += async (_, e) =>
        {
            if (e.Key == Windows.System.VirtualKey.Enter) await DoLoginAsync();
        };

        Loaded += async (_, _) =>
        {
            RenderAuthArea();
            if (Session.IsLoggedIn) await LoadFavoritesAsync();
        };
    }

    // ---------------- 登录区 ----------------

    private void RenderAuthArea()
    {
        _authHost.Children.Clear();

        if (Session.IsLoggedIn && Session.User != null)
        {
            var user = Session.User;
            var card = new Grid { ColumnSpacing = 14, Padding = new Thickness(18) };
            card.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            card.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            card.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

            var avatar = new Border
            {
                Width = 56,
                Height = 56,
                CornerRadius = new CornerRadius(28),
                Background = UiKit.PrimaryBrush,
                Child = new TextBlock
                {
                    Text = string.IsNullOrEmpty(user.Username) ? "?" : user.Username[..1].ToUpperInvariant(),
                    FontSize = 22,
                    FontWeight = Microsoft.UI.Text.FontWeights.SemiBold,
                    Foreground = new SolidColorBrush(Colors.White),
                    HorizontalAlignment = HorizontalAlignment.Center,
                    VerticalAlignment = VerticalAlignment.Center,
                },
            };
            Grid.SetColumn(avatar, 0);
            card.Children.Add(avatar);

            var info = new StackPanel { VerticalAlignment = VerticalAlignment.Center, Spacing = 2 };
            info.Children.Add(UiKit.Text(user.Username, 18, true));
            info.Children.Add(UiKit.Text(user.Email, 12.5, false, UiKit.MutedBrush));
            var sub = string.IsNullOrWhiteSpace(user.Title) ? "普通用户" : user.Title!;
            if (user.IsAdmin == 1) sub += " · 管理员";
            info.Children.Add(UiKit.Text(sub, 11.5, false, UiKit.MutedBrush));
            Grid.SetColumn(info, 1);
            card.Children.Add(info);

            var logout = UiKit.PillButton("退出登录", "\uE7E8");
            logout.Click += (_, _) =>
            {
                Session.Logout();
                _items.Clear();
                _favHost.Children.Clear();
                _status.Text = "";
                RenderAuthArea();
                Host?.UpdateUserBadgePublic();
            };
            Grid.SetColumn(logout, 2);
            logout.VerticalAlignment = VerticalAlignment.Center;
            card.Children.Add(logout);

            _authHost.Children.Add(UiKit.Card(card, 0, 14, UiKit.Surface2Brush));
        }
        else
        {
            var form = new StackPanel { Spacing = 10, MaxWidth = 380, HorizontalAlignment = HorizontalAlignment.Left };
            form.Children.Add(UiKit.Text("登录 TGZJDV's Music", 16, true));
            form.Children.Add(UiKit.Text("账号与网站 / Android 版通用", 12, false, UiKit.MutedBrush));
            form.Children.Add(_email);
            form.Children.Add(_password);

            var row = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 10 };
            row.Children.Add(_loginBtn);
            row.Children.Add(_busy);
            form.Children.Add(row);

            _authHost.Children.Add(UiKit.Card(form, 20, 14, UiKit.Surface2Brush));
        }
    }

    private async Task DoLoginAsync()
    {
        if (Session.IsLoggedIn) return;
        var email = _email.Text.Trim();
        var password = _password.Password;
        if (email.Length == 0 || password.Length == 0)
        {
            ShowInfo(_info, "请填写邮箱和密码");
            return;
        }

        _info.IsOpen = false;
        _busy.IsActive = true;
        _loginBtn.IsEnabled = false;
        try
        {
            await Session.LoginAsync(email, password);
            _password.Password = "";
            RenderAuthArea();
            Host?.UpdateUserBadgePublic();
            ShowInfo(_info, "登录成功，欢迎回来！", "成功");
            await LoadFavoritesAsync();
        }
        catch (Exception ex)
        {
            ShowError(_info, DescribeError(ex));
        }
        finally
        {
            _busy.IsActive = false;
            _loginBtn.IsEnabled = true;
        }
    }

    // ---------------- 收藏 ----------------

    private async Task LoadFavoritesAsync()
    {
        _favHost.Children.Clear();
        _items.Clear();
        if (!Session.IsLoggedIn) return;

        _status.Text = "正在加载我的收藏…";
        _favHost.Children.Add(UiKit.Loading());
        try
        {
            var res = await Session.Api.FavoritesAsync();
            _favHost.Children.Clear();
            _items.Clear();

            var head = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 12, Margin = new Thickness(0, 6, 0, 6) };
            head.Children.Add(UiKit.Text($"我的收藏 · {res.Songs.Count} 首", 16, true));
            if (res.Songs.Count > 0)
            {
                var playAll = UiKit.PillButton("播放全部", "\uE768", primary: true);
                playAll.Click += (_, _) => PlayFrom(_items, 0);
                head.Children.Add(playAll);
            }
            _favHost.Children.Add(head);

            if (res.Songs.Count == 0)
            {
                _status.Text = "还没有收藏的歌曲";
                return;
            }

            _status.Text = "";
            foreach (var song in res.Songs)
            {
                var item = new SongItem(song);
                var index = _items.Count;
                _items.Add(item);
                _favHost.Children.Add(UiKit.SongRow(item, index + 1, () => PlayFrom(_items, index)));
            }
        }
        catch (Exception ex)
        {
            _favHost.Children.Clear();
            _status.Text = "";
            ShowError(_info, DescribeError(ex));
        }
    }
}

