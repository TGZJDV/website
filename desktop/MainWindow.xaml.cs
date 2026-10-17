using FamousMusic.Desktop.Data;
using FamousMusic.Desktop.Pages;
using FamousMusic.Desktop.Services;
using Microsoft.UI;
using Microsoft.UI.Composition.SystemBackdrops;
using Microsoft.UI.Windowing;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Input;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Media.Imaging;
using Windows.Graphics;
using Windows.UI;

namespace FamousMusic.Desktop;

public sealed partial class MainWindow : Window
{
    private readonly List<(string Key, string Label, Button Button, FontIcon Icon, TextBlock Text)> _navItems = new();
    private string _currentPage = "";
    private bool _userSeeking;

    // 底部播放条控件
    private readonly TextBlock _barTitle = new();
    private readonly TextBlock _barArtist = new();
    private readonly Border _barCover = new();
    private readonly FontIcon _playIcon = new();
    private readonly FontIcon _loopIcon = new();
    private readonly Slider _progress = new();
    private readonly TextBlock _posText = new();
    private readonly TextBlock _durText = new();
    private readonly Slider _volume = new();
    private readonly TextBlock _navUserName = new();
    private readonly TextBlock _navUserSub = new();
    private readonly StackPanel _navPanel = new() { Spacing = 3, Margin = new Thickness(12, 0, 12, 0) };
    private readonly Grid _contentHost = new();

    public MainWindow()
    {
        Title = "TGZJDV's Music";
        TrySetBackdrop();

        Content = BuildLayout();
        TryResize();

        Session.Current.Load();
        _volume.Value = 0.85;
        PlayerService.Current.SetVolume(_volume.Value);

        BuildNav();
        WirePlayer();
        UpdateUserBadge();
        Session.Current.Changed += UpdateUserBadge;

        Navigate("discover");
        _ = Session.Current.RefreshMeAsync();
    }

    // ================= 布局 =================

    private UIElement BuildLayout()
    {
        var root = new Grid { Background = UiKit.SurfaceBrush };
        root.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });
        root.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });

        // ---- 主体：导航 + 内容 ----
        var body = new Grid();
        body.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(228) });
        body.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });

        // 左侧导航
        var navBorder = new Border
        {
            Background = new SolidColorBrush(Color.FromArgb(0xE6, 0x1E, 0x1E, 0x1E)),
            BorderBrush = UiKit.HairlineBrush,
            BorderThickness = new Thickness(0, 0, 1, 0),
        };
        var navGrid = new Grid();
        navGrid.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        navGrid.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });
        navGrid.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });

        var logo = new StackPanel { Margin = new Thickness(20, 22, 16, 22), Spacing = 3 };
        logo.Children.Add(UiKit.Text("TGZJDV's Music", 18, true));
        logo.Children.Add(UiKit.Text("电脑版 · Windows", 12, false, UiKit.MutedBrush));
        Grid.SetRow(logo, 0);
        navGrid.Children.Add(logo);

        Grid.SetRow(_navPanel, 1);
        navGrid.Children.Add(_navPanel);

        var userCard = new Border
        {
            Margin = new Thickness(12, 12, 12, 14),
            Padding = new Thickness(12),
            CornerRadius = new CornerRadius(10),
            Background = UiKit.Surface3Brush,
        };
        var userStack = new StackPanel { Spacing = 2 };
        _navUserName.Text = "未登录";
        _navUserName.FontSize = 13;
        _navUserName.FontWeight = Microsoft.UI.Text.FontWeights.SemiBold;
        _navUserName.Foreground = UiKit.TextBrush;
        _navUserName.TextTrimming = TextTrimming.CharacterEllipsis;
        _navUserSub.Text = "登录后可收藏 / 歌单";
        _navUserSub.FontSize = 12;
        _navUserSub.Foreground = UiKit.MutedBrush;
        _navUserSub.TextTrimming = TextTrimming.CharacterEllipsis;
        userStack.Children.Add(_navUserName);
        userStack.Children.Add(_navUserSub);
        userCard.Child = userStack;
        Grid.SetRow(userCard, 2);
        navGrid.Children.Add(userCard);

        navBorder.Child = navGrid;
        Grid.SetColumn(navBorder, 0);
        body.Children.Add(navBorder);

        Grid.SetColumn(_contentHost, 1);
        body.Children.Add(_contentHost);

        Grid.SetRow(body, 0);
        root.Children.Add(body);

        // ---- 底部播放条 ----
        var bar = BuildPlayerBar();
        Grid.SetRow(bar, 1);
        root.Children.Add(bar);

        return root;
    }

    private Border BuildPlayerBar()
    {
        var border = new Border
        {
            Background = new SolidColorBrush(Color.FromArgb(0xF2, 0x1A, 0x1A, 0x1A)),
            BorderBrush = UiKit.HairlineBrush,
            BorderThickness = new Thickness(0, 1, 0, 0),
            Padding = new Thickness(16, 10, 16, 12),
        };

        var grid = new Grid { ColumnSpacing = 16 };
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(270) });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(190) });

        // --- 左：当前曲目 ---
        var trackGrid = new Grid { ColumnSpacing = 12 };
        trackGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        trackGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });

        _barCover.Width = 48;
        _barCover.Height = 48;
        _barCover.CornerRadius = new CornerRadius(8);
        _barCover.Background = UiKit.CoverFallback;
        _barCover.Child = new FontIcon
        {
            Glyph = "\uE8D6",
            FontSize = 18,
            Foreground = UiKit.MutedBrush,
            HorizontalAlignment = HorizontalAlignment.Center,
            VerticalAlignment = VerticalAlignment.Center,
        };
        Grid.SetColumn(_barCover, 0);
        trackGrid.Children.Add(_barCover);

        var trackInfo = new StackPanel { VerticalAlignment = VerticalAlignment.Center, Spacing = 1 };
        _barTitle.Text = "未在播放";
        _barTitle.FontSize = 13.5;
        _barTitle.FontWeight = Microsoft.UI.Text.FontWeights.SemiBold;
        _barTitle.Foreground = UiKit.TextBrush;
        _barTitle.TextTrimming = TextTrimming.CharacterEllipsis;
        _barTitle.TextWrapping = TextWrapping.NoWrap;
        _barArtist.Text = "选择一首歌开始";
        _barArtist.FontSize = 12;
        _barArtist.Foreground = UiKit.MutedBrush;
        _barArtist.TextTrimming = TextTrimming.CharacterEllipsis;
        trackInfo.Children.Add(_barTitle);
        trackInfo.Children.Add(_barArtist);
        Grid.SetColumn(trackInfo, 1);
        trackGrid.Children.Add(trackInfo);

        Grid.SetColumn(trackGrid, 0);
        grid.Children.Add(trackGrid);

        // --- 中：控制 + 进度 ---
        var center = new Grid { RowSpacing = 1 };
        center.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        center.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });

        var controls = new StackPanel
        {
            Orientation = Orientation.Horizontal,
            HorizontalAlignment = HorizontalAlignment.Center,
            Spacing = 6,
        };
        controls.Children.Add(UiKit.PlayerIconButton("\uE892", "上一首", (_, _) => PlayerService.Current.Previous()));

        var playBtn = new Button
        {
            Background = UiKit.PrimaryBrush,
            Foreground = new SolidColorBrush(Colors.White),
            BorderThickness = new Thickness(0),
            Width = 38,
            Height = 38,
            Padding = new Thickness(0),
            CornerRadius = new CornerRadius(19),
            Content = _playIcon,
        };
        _playIcon.Glyph = "\uE768";
        _playIcon.FontSize = 15;
        _playIcon.Foreground = new SolidColorBrush(Colors.White);
        ToolTipService.SetToolTip(playBtn, "播放 / 暂停");
        playBtn.Click += (_, _) => PlayerService.Current.TogglePlay();
        controls.Children.Add(playBtn);

        controls.Children.Add(UiKit.PlayerIconButton("\uE893", "下一首", (_, _) => PlayerService.Current.Next()));

        var loopBtn = UiKit.PlayerIconButton("\uE8EE", "单曲循环", (_, _) => ToggleLoop());
        _loopIcon.Glyph = "\uE8EE";
        _loopIcon.FontSize = 15;
        _loopIcon.Foreground = UiKit.MutedBrush;
        loopBtn.Content = _loopIcon;
        controls.Children.Add(loopBtn);

        Grid.SetRow(controls, 0);
        center.Children.Add(controls);

        var progressRow = new Grid { ColumnSpacing = 10 };
        progressRow.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        progressRow.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        progressRow.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

        _posText.Text = "0:00";
        _posText.FontSize = 12;
        _posText.Foreground = UiKit.MutedBrush;
        _posText.VerticalAlignment = VerticalAlignment.Center;
        Grid.SetColumn(_posText, 0);
        progressRow.Children.Add(_posText);

        _progress.Minimum = 0;
        _progress.Maximum = 1000;
        _progress.Value = 0;
        _progress.StepFrequency = 1;
        _progress.IsThumbToolTipEnabled = false;
        _progress.VerticalAlignment = VerticalAlignment.Center;
        _progress.ValueChanged += Progress_ValueChanged;
        _progress.AddHandler(UIElement.PointerPressedEvent, new PointerEventHandler((_, _) => _userSeeking = true), true);
        _progress.AddHandler(UIElement.PointerReleasedEvent, new PointerEventHandler((_, _) => _userSeeking = false), true);
        _progress.AddHandler(UIElement.PointerCaptureLostEvent, new PointerEventHandler((_, _) => _userSeeking = false), true);
        Grid.SetColumn(_progress, 1);
        progressRow.Children.Add(_progress);

        _durText.Text = "0:00";
        _durText.FontSize = 12;
        _durText.Foreground = UiKit.MutedBrush;
        _durText.VerticalAlignment = VerticalAlignment.Center;
        Grid.SetColumn(_durText, 2);
        progressRow.Children.Add(_durText);

        Grid.SetRow(progressRow, 1);
        center.Children.Add(progressRow);

        Grid.SetColumn(center, 1);
        grid.Children.Add(center);

        // --- 右：音量 ---
        var volumeRow = new StackPanel
        {
            Orientation = Orientation.Horizontal,
            Spacing = 8,
            VerticalAlignment = VerticalAlignment.Center,
            HorizontalAlignment = HorizontalAlignment.Right,
        };
        volumeRow.Children.Add(new FontIcon { Glyph = "\uE767", FontSize = 15, Foreground = UiKit.MutedBrush });
        _volume.Width = 112;
        _volume.Minimum = 0;
        _volume.Maximum = 1;
        _volume.StepFrequency = 0.01;
        _volume.IsThumbToolTipEnabled = false;
        _volume.VerticalAlignment = VerticalAlignment.Center;
        _volume.ValueChanged += (_, e) => PlayerService.Current.SetVolume(e.NewValue);
        volumeRow.Children.Add(_volume);

        Grid.SetColumn(volumeRow, 2);
        grid.Children.Add(volumeRow);

        border.Child = grid;
        return border;
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

    private void BuildNav()
    {
        AddNavItem("discover", "发现", "\uE8F4");
        AddNavItem("search", "搜索", "\uE721");
        AddNavItem("genres", "分类", "\uE8FD");
        AddNavItem("playlists", "歌单", "\uE8FD");
        AddNavItem("me", "我的", "\uE77B");
    }

    private void AddNavItem(string key, string label, string glyph)
    {
        var icon = new FontIcon { Glyph = glyph, FontSize = 16, Foreground = UiKit.MutedBrush };
        var text = new TextBlock
        {
            Text = label,
            FontSize = 14,
            VerticalAlignment = VerticalAlignment.Center,
            Margin = new Thickness(12, 0, 0, 0),
            Foreground = UiKit.MutedBrush,
        };
        var content = new StackPanel { Orientation = Orientation.Horizontal };
        content.Children.Add(icon);
        content.Children.Add(text);

        var btn = new Button
        {
            Content = content,
            Tag = key,
            Background = new SolidColorBrush(Colors.Transparent),
            BorderThickness = new Thickness(0),
            Padding = new Thickness(12, 9, 12, 9),
            HorizontalAlignment = HorizontalAlignment.Stretch,
            HorizontalContentAlignment = HorizontalAlignment.Left,
            CornerRadius = new CornerRadius(8),
        };
        btn.Click += (s, _) => { if (s is Button b && b.Tag is string k) Navigate(k); };

        _navItems.Add((key, label, btn, icon, text));
        _navPanel.Children.Add(btn);
    }

    private void HighlightNav(string key)
    {
        foreach (var (k, _, btn, icon, text) in _navItems)
        {
            var selected = k == key;
            btn.Background = selected
                ? new SolidColorBrush(Color.FromArgb(0x33, 0x0E, 0xA5, 0xE9))
                : new SolidColorBrush(Colors.Transparent);
            icon.Foreground = selected ? UiKit.PrimaryBrush : UiKit.MutedBrush;
            text.Foreground = selected ? UiKit.PrimaryBrush : UiKit.MutedBrush;
        }
    }

    public void Navigate(string key)
    {
        if (_currentPage == key) return;
        _currentPage = key;
        HighlightNav(key);

        FrameworkElement page = key switch
        {
            "discover" => new DiscoverPage(),
            "search" => new SearchPage(),
            "genres" => new GenresPage(),
            "playlists" => new PlaylistsPage(),
            "me" => new MePage(),
            _ => new DiscoverPage(),
        };

        if (page is PageBase pb) pb.Host = this;
        _contentHost.Children.Clear();
        _contentHost.Children.Add(page);
    }

    // ================= 播放器绑定 =================

    private void WirePlayer()
    {
        var player = PlayerService.Current;

        player.TrackChanged += song =>
        {
            _barTitle.Text = song.Title;
            _barArtist.Text = song.DisplayArtist;
            var url = ApiClient.CoverUrl(song);
            if (string.IsNullOrWhiteSpace(url))
            {
                _barCover.Background = UiKit.CoverFallback;
            }
            else
            {
                var bmp = new BitmapImage { DecodePixelWidth = 96 };
                bmp.UriSource = new Uri(url);
                _barCover.Background = new ImageBrush { ImageSource = bmp, Stretch = Stretch.UniformToFill };
            }
            _durText.Text = Format.Duration(song.Duration);
        };

        player.PlayingChanged += playing => _playIcon.Glyph = playing ? "\uE769" : "\uE768";

        player.PositionChanged += (pos, total) =>
        {
            if (!_userSeeking && total > TimeSpan.Zero)
                _progress.Value = Math.Clamp(pos.TotalMilliseconds / total.TotalMilliseconds * 1000.0, 0, 1000);
            _posText.Text = Format.Clock(pos);
            if (total > TimeSpan.Zero) _durText.Text = Format.Clock(total);
        };

        player.Failed += msg => _barArtist.Text = "播放失败：" + msg;
    }

    private void Progress_ValueChanged(object sender, Microsoft.UI.Xaml.Controls.Primitives.RangeBaseValueChangedEventArgs e)
    {
        if (!_userSeeking) return;
        PlayerService.Current.SeekToFraction(e.NewValue / 1000.0);
    }

    private void ToggleLoop()
    {
        PlayerService.Current.ToggleLoopOne();
        _loopIcon.Foreground = PlayerService.Current.IsLoopOne ? UiKit.PrimaryBrush : UiKit.MutedBrush;
    }

    // ================= 用户信息 =================

    private void UpdateUserBadge()
    {
        var s = Session.Current;
        if (s.IsLoggedIn && s.User != null)
        {
            _navUserName.Text = s.User.Username;
            _navUserSub.Text = string.IsNullOrWhiteSpace(s.User.Title) ? s.User.Email : s.User.Title!;
        }
        else
        {
            _navUserName.Text = "未登录";
            _navUserSub.Text = "登录后可收藏 / 歌单";
        }
    }

    /// <summary>供页面在登录/登出后刷新左下角用户信息</summary>
    public void UpdateUserBadgePublic() => UpdateUserBadge();
}

