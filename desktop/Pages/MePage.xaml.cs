using System.Collections.ObjectModel;
using FamousMusic.Desktop.Models;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Input;

namespace FamousMusic.Desktop.Pages;

/// <summary>
/// 我的页 code-behind：登录 / 退出 + 收藏列表。
/// 登录卡片与收藏行外观见 Pages/MePage.xaml 与 Styles/Templates.xaml。
/// </summary>
public sealed partial class MePage : PageBase
{
    private readonly ObservableCollection<SongRowVm> _favorites = new();
    private readonly List<SongItem> _items = new();

    public MePage()
    {
        InitializeComponent();
        FavoritesList.ItemsSource = _favorites;
    }

    public override async void OnNavigatedTo()
    {
        RenderAuthArea();
        if (Session.IsLoggedIn) await LoadFavoritesAsync();
    }

    // ---------------- 登录区 ----------------

    private void RenderAuthArea()
    {
        var loggedIn = Session.IsLoggedIn && Session.User != null;
        LoginCard.Visibility = loggedIn ? Visibility.Collapsed : Visibility.Visible;
        UserCard.Visibility = loggedIn ? Visibility.Visible : Visibility.Collapsed;

        if (!loggedIn) return;

        var user = Session.User!;
        AvatarText.Text = string.IsNullOrEmpty(user.Username) ? "?" : user.Username[..1].ToUpperInvariant();
        UserName.Text = user.Username;
        UserEmail.Text = user.Email;
        var sub = string.IsNullOrWhiteSpace(user.Title) ? "普通用户" : user.Title!;
        if (user.IsAdmin == 1) sub += " · 管理员";
        UserTitle.Text = sub;
    }

    private async void LoginButton_Click(object sender, RoutedEventArgs e) => await DoLoginAsync();

    private async void PasswordBox_KeyDown(object sender, KeyRoutedEventArgs e)
    {
        if (e.Key == Windows.System.VirtualKey.Enter)
        {
            e.Handled = true;
            await DoLoginAsync();
        }
    }

    private async Task DoLoginAsync()
    {
        if (Session.IsLoggedIn) return;

        var email = EmailBox.Text.Trim();
        var password = PasswordBox.Password;
        if (email.Length == 0 || password.Length == 0)
        {
            ShowInfo(Info, "请填写邮箱和密码");
            return;
        }

        Info.IsOpen = false;
        LoginBusy.IsActive = true;
        LoginButton.IsEnabled = false;
        try
        {
            await Session.LoginAsync(email, password);
            PasswordBox.Password = "";
            RenderAuthArea();
            Host?.UpdateUserBadgePublic();
            ShowInfo(Info, "登录成功，欢迎回来！", "成功");
            await LoadFavoritesAsync();
        }
        catch (Exception ex)
        {
            ShowError(Info, DescribeError(ex));
        }
        finally
        {
            LoginBusy.IsActive = false;
            LoginButton.IsEnabled = true;
        }
    }

    private void LogoutButton_Click(object sender, RoutedEventArgs e)
    {
        Session.Logout();
        _favorites.Clear();
        _items.Clear();
        FavoritesHeader.Visibility = Visibility.Collapsed;
        StatusText.Text = "";
        RenderAuthArea();
        Host?.UpdateUserBadgePublic();
    }

    // ---------------- 收藏 ----------------

    private async Task LoadFavoritesAsync()
    {
        _favorites.Clear();
        _items.Clear();
        FavoritesHeader.Visibility = Visibility.Collapsed;
        if (!Session.IsLoggedIn) return;

        StatusText.Text = "正在加载我的收藏…";
        LoadingPanel.Visibility = Visibility.Visible;
        try
        {
            var res = await Session.Api.FavoritesAsync();
            _favorites.Clear();
            _items.Clear();

            FavoritesTitle.Text = $"我的收藏 · {res.Songs.Count} 首";
            FavoritesHeader.Visibility = Visibility.Visible;
            FavoritesPlayAll.IsEnabled = res.Songs.Count > 0;

            if (res.Songs.Count == 0)
            {
                StatusText.Text = "还没有收藏的歌曲";
                return;
            }

            StatusText.Text = "";
            foreach (var song in res.Songs)
            {
                var item = new SongItem(song);
                var index = _items.Count;
                _items.Add(item);
                _favorites.Add(new SongRowVm(item, index + 1, () => PlayFrom(_items, index)));
            }
        }
        catch (Exception ex)
        {
            _favorites.Clear();
            StatusText.Text = "";
            ShowError(Info, DescribeError(ex));
        }
        finally
        {
            LoadingPanel.Visibility = Visibility.Collapsed;
        }
    }

    private void FavoritesPlayAll_Click(object sender, RoutedEventArgs e)
    {
        if (_items.Count == 0) return;
        PlayFrom(_items, 0);
    }
}
