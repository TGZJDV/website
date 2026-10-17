using FamousMusic.Desktop.Pages;
using FamousMusic.Desktop.Services;
using Microsoft.UI.Composition.SystemBackdrops;
using Microsoft.UI.Windowing;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Media.Imaging;
using Windows.Graphics;

namespace FamousMusic.Desktop;

/// <summary>
/// 主窗口 code-behind。
/// 重构后这里只剩「事件处理 + 页面导航 + 播放条刷新」，
/// 所有布局与控件属性都在 MainWindow.xaml 里声明。
/// </summary>
public sealed partial class MainWindow : Window
{
    private bool _userSeeking;
    private string _currentKey = "";

    /// <summary>页面缓存：同一 key 复用实例，避免来回切换反复请求接口</summary>
    private readonly Dictionary<string, PageBase> _pages = new();

    public MainWindow()
    {
        InitializeComponent();

        Title = "TGZJDV's Music";
        TrySetBackdrop();
        TryResize();

        Session.Current.Load();
        PlayerService.Current.SetVolume(VolumeSlider.Value);

        WirePlayer();
        UpdateUserBadge();
        Session.Current.Changed += UpdateUserBadge;

        // 默认选中「发现」
        NavView.SelectedItem = NavDiscover;
        Navigate("discover");

        _ = Session.Current.RefreshMeAsync();
    }

    // ================= 窗口外观 =================

    private void TrySetBackdrop()
    {
        try { SystemBackdrop = new MicaBackdrop { Kind = MicaKind.BaseAlt }; }
        catch
        {
            try { SystemBackdrop = new DesktopAcrylicBackdrop(); } catch { }
        }
    }

    private void TryResize()
    {
        try
        {
            var appWindow = this.AppWindow;
            appWindow.Resize(new SizeInt32(1280, 840));
            var area = DisplayArea.GetFromWindowId(appWindow.Id, DisplayAreaFallback.Primary);
            var work = area.WorkArea;
            appWindow.Move(new PointInt32(
                work.X + Math.Max(0, (work.Width - 1280) / 2),
                work.Y + Math.Max(0, (work.Height - 840) / 2)));
        }
        catch { }
    }

    // ================= 导航 =================

    private void NavView_SelectionChanged(NavigationView sender, NavigationViewSelectionChangedEventArgs args)
    {
        if (args.SelectedItem is NavigationViewItem { Tag: string key }) Navigate(key);
    }

    /// <summary>切换页面。页面实例缓存起来复用，每次进入调用 OnNavigatedTo 刷新。</summary>
    private void Navigate(string key)
    {
        if (_currentKey == key && ContentFrame.Content is PageBase) return;
        _currentKey = key;

        if (!_pages.TryGetValue(key, out var page))
        {
            page = CreatePage(key);
            page.Host = this;
            _pages[key] = page;
        }

        ContentFrame.Content = page;
        page.OnNavigatedTo();
    }

    private static PageBase CreatePage(string key) => key switch
    {
        "search" => new SearchPage(),
        "genres" => new GenresPage(),
        "playlists" => new PlaylistsPage(),
        "me" => new MePage(),
        _ => new DiscoverPage(),
    };

    // ================= 播放器绑定 =================

    private void WirePlayer()
    {
        var player = PlayerService.Current;

        player.TrackChanged += song =>
        {
            BarTitle.Text = song.Title;
            BarArtist.Text = song.DisplayArtist;
            DurationText.Text = Format.Duration(song.Duration);

            var url = Data.ApiClient.CoverUrl(song);
            if (string.IsNullOrWhiteSpace(url))
            {
                BarCoverImage.Background = new SolidColorBrush(Microsoft.UI.Colors.Transparent);
            }
            else
            {
                try
                {
                    var bmp = new BitmapImage { DecodePixelWidth = 96, UriSource = new Uri(url) };
                    BarCoverImage.Background = new ImageBrush { ImageSource = bmp, Stretch = Stretch.UniformToFill };
                }
                catch
                {
                    BarCoverImage.Background = new SolidColorBrush(Microsoft.UI.Colors.Transparent);
                }
            }
            UpdateLoopIcon();
        };

        player.PlayingChanged += playing => PlayPauseIcon.Glyph = playing ? "\uE769" : "\uE768";

        player.PositionChanged += (pos, total) =>
        {
            if (!_userSeeking && total > TimeSpan.Zero)
                ProgressSlider.Value = Math.Clamp(pos.TotalMilliseconds / total.TotalMilliseconds * 1000.0, 0, 1000);
            PositionText.Text = Format.Clock(pos);
            if (total > TimeSpan.Zero) DurationText.Text = Format.Clock(total);
        };

        player.Failed += msg => BarArtist.Text = "播放失败：" + msg;

        UpdateLoopIcon();
    }

    // ---- 播放条事件 ----

    private void PreviousButton_Click(object sender, RoutedEventArgs e) => PlayerService.Current.Previous();

    private void NextButton_Click(object sender, RoutedEventArgs e) => PlayerService.Current.Next();

    private void PlayPauseButton_Click(object sender, RoutedEventArgs e) => PlayerService.Current.TogglePlay();

    private void LoopButton_Click(object sender, RoutedEventArgs e)
    {
        PlayerService.Current.CycleLoopMode();
        UpdateLoopIcon();
    }

    /// <summary>循环按钮字形 / 提示语跟随 PlayerService 的三态模式（列表 / 单曲 / 随机）</summary>
    private void UpdateLoopIcon()
    {
        var player = PlayerService.Current;
        LoopIcon.Glyph = player.LoopGlyph;
        ToolTipService.SetToolTip(LoopButton, player.LoopTip);

        // 只有「列表循环」是默认态时用静音色，其余两态高亮
        LoopIcon.Foreground = player.LoopMode == LoopMode.List
            ? (Brush)Application.Current.Resources["AppMutedBrush"]
            : (Brush)Application.Current.Resources["AppPrimaryBrush"];
    }

    private void ProgressSlider_ValueChanged(object sender, Microsoft.UI.Xaml.Controls.Primitives.RangeBaseValueChangedEventArgs e)
    {
        if (!_userSeeking) return;
        PlayerService.Current.SeekToFraction(e.NewValue / 1000.0);
    }

    private void ProgressSlider_PointerPressed(object sender, Microsoft.UI.Xaml.Input.PointerRoutedEventArgs e)
        => _userSeeking = true;

    private void ProgressSlider_PointerReleased(object sender, Microsoft.UI.Xaml.Input.PointerRoutedEventArgs e)
        => _userSeeking = false;

    private void ProgressSlider_PointerCaptureLost(object sender, Microsoft.UI.Xaml.Input.PointerRoutedEventArgs e)
        => _userSeeking = false;

    private void VolumeSlider_ValueChanged(object sender, Microsoft.UI.Xaml.Controls.Primitives.RangeBaseValueChangedEventArgs e)
        => PlayerService.Current.SetVolume(e.NewValue);

    // ================= 用户信息 =================

    private void RefreshMeButton_Click(object sender, RoutedEventArgs e) => _ = RefreshMeAsync();

    private async Task RefreshMeAsync()
    {
        await Session.Current.RefreshMeAsync();
        UpdateUserBadge();
    }

    private void UpdateUserBadge()
    {
        var s = Session.Current;
        if (s.IsLoggedIn && s.User != null)
        {
            NavUserName.Text = s.User.Username;
            NavUserSub.Text = string.IsNullOrWhiteSpace(s.User.Title) ? s.User.Email : s.User.Title!;
        }
        else
        {
            NavUserName.Text = "未登录";
            NavUserSub.Text = "登录后可收藏 / 歌单";
        }
    }

    /// <summary>供页面在登录 / 登出后刷新左下角用户信息</summary>
    public void UpdateUserBadgePublic() => UpdateUserBadge();

    /// <summary>供页面跳转到其它页（如歌单页提示去「我的」登录）</summary>
    public void NavigateTo(string key)
    {
        var item = key switch
        {
            "search" => NavSearch,
            "genres" => NavGenres,
            "playlists" => NavPlaylists,
            "me" => NavMe,
            _ => NavDiscover,
        };
        NavView.SelectedItem = item;
        Navigate(key);
    }
}
