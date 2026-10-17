using FamousMusic.Desktop.Models;
using FamousMusic.Desktop.Services;
using Microsoft.UI;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;

namespace FamousMusic.Desktop.Pages;

/// <summary>歌单页：我的歌单 + 歌单详情（歌曲列表）</summary>
public sealed class PlaylistsPage : PageBase
{
    private readonly StackPanel _root = new() { Spacing = 14 };
    private readonly StackPanel _body = new() { Spacing = 8 };
    private readonly InfoBar _info = new() { IsClosable = true, IsOpen = false };
    private readonly TextBlock _status = UiKit.Text("", 12.5, false, UiKit.MutedBrush);
    private readonly List<SongItem> _items = new();
    private bool _loaded;

    public PlaylistsPage()
    {
        Margin = new Thickness(28, 24, 28, 18);
        Content = new ScrollViewer
        {
            VerticalScrollBarVisibility = ScrollBarVisibility.Auto,
            HorizontalScrollBarVisibility = ScrollBarVisibility.Disabled,
            Content = _root,
        };

        var head = new Grid();
        head.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        head.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        head.Children.Add(UiKit.Header("我的歌单", "登录后可创建、查看自己的歌单"));

        var backBtn = UiKit.PillButton("新建歌单", "\uE710", primary: true);
        backBtn.Click += async (_, _) => await CreatePlaylistAsync();
        Grid.SetColumn(backBtn, 1);
        backBtn.VerticalAlignment = VerticalAlignment.Center;
        head.Children.Add(backBtn);

        _root.Children.Add(head);
        _root.Children.Add(_info);
        _root.Children.Add(_status);
        _root.Children.Add(_body);

        Loaded += async (_, _) =>
        {
            if (_loaded) return;
            _loaded = true;
            await LoadPlaylistsAsync();
        };
    }

    private async Task CreatePlaylistAsync()
    {
        if (!Session.IsLoggedIn) { ShowInfo(_info, "请先在「我的」页面登录后再创建歌单"); return; }

        var input = new TextBox { PlaceholderText = "歌单名称", MaxLength = 40, Margin = new Thickness(0, 8, 0, 0) };
        var dialog = new ContentDialog
        {
            Title = "新建歌单",
            Content = input,
            PrimaryButtonText = "创建",
            CloseButtonText = "取消",
            DefaultButton = ContentDialogButton.Primary,
            XamlRoot = XamlRoot,
        };
        if (await dialog.ShowAsync() != ContentDialogResult.Primary) return;
        var name = input.Text.Trim();
        if (name.Length == 0) return;

        try
        {
            await Session.Api.CreatePlaylistAsync(name);
            await LoadPlaylistsAsync();
            ShowInfo(_info, $"已创建歌单「{name}」", "成功");
        }
        catch (Exception ex)
        {
            ShowError(_info, DescribeError(ex));
        }
    }

    private async Task LoadPlaylistsAsync()
    {
        _body.Children.Clear();
        _items.Clear();
        _info.IsOpen = false;

        if (!Session.IsLoggedIn)
        {
            _status.Text = "";
            ShowInfo(_info, "登录后即可查看我的歌单");
            return;
        }

        _status.Text = "正在加载歌单…";
        _body.Children.Add(UiKit.Loading());
        try
        {
            var res = await Session.Api.MyPlaylistsAsync();
            _body.Children.Clear();
            if (res.Playlists.Count == 0)
            {
                _status.Text = "还没有歌单，点右上角「新建歌单」创建";
                return;
            }
            _status.Text = $"共 {res.Playlists.Count} 个歌单";
            foreach (var pl in res.Playlists)
                _body.Children.Add(BuildPlaylistRow(new PlaylistItem(pl)));
        }
        catch (Exception ex)
        {
            _body.Children.Clear();
            _status.Text = "";
            ShowError(_info, DescribeError(ex));
        }
    }

    private Button BuildPlaylistRow(PlaylistItem pl)
    {
        var grid = new Grid { ColumnSpacing = 12, Padding = new Thickness(10, 8, 10, 8) };
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });

        var cover = new Border
        {
            Width = 46,
            Height = 46,
            CornerRadius = new CornerRadius(8),
            Background = UiKit.CoverFallback,
            Child = new FontIcon
            {
                Glyph = "\uE8FD",
                FontSize = 18,
                Foreground = UiKit.MutedBrush,
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center,
            },
        };
        Grid.SetColumn(cover, 0);
        grid.Children.Add(cover);

        var info = new StackPanel { VerticalAlignment = VerticalAlignment.Center, Spacing = 1 };
        info.Children.Add(UiKit.Text(pl.Name, 13.5, true));
        info.Children.Add(UiKit.Text(pl.InfoText, 11.5, false, UiKit.MutedBrush));
        Grid.SetColumn(info, 1);
        grid.Children.Add(info);

        var btn = new Button
        {
            Content = grid,
            Background = new SolidColorBrush(Colors.Transparent),
            BorderThickness = new Thickness(0),
            Padding = new Thickness(0),
            HorizontalAlignment = HorizontalAlignment.Stretch,
            HorizontalContentAlignment = HorizontalAlignment.Stretch,
            CornerRadius = new CornerRadius(10),
        };
        btn.Click += async (_, _) => await OpenPlaylistAsync(pl);
        return btn;
    }

    private async Task OpenPlaylistAsync(PlaylistItem pl)
    {
        _body.Children.Clear();
        _items.Clear();
        _info.IsOpen = false;
        _status.Text = $"正在加载歌单「{pl.Name}」…";
        _body.Children.Add(UiKit.Loading());

        try
        {
            var res = await Session.Api.PlaylistDetailAsync(pl.Id);
            _body.Children.Clear();

            var head = new StackPanel { Spacing = 6, Margin = new Thickness(0, 0, 0, 8) };
            var titleRow = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 10 };
            var back = UiKit.PillButton("返回歌单列表", "\uE72B");
            back.Click += async (_, _) => await LoadPlaylistsAsync();
            titleRow.Children.Add(back);
            if (res.Songs.Count > 0)
            {
                var playAll = UiKit.PillButton("播放全部", "\uE768", primary: true);
                var songs = res.Songs;
                playAll.Click += (_, _) =>
                {
                    _items.Clear();
                    _items.AddRange(songs.Select(s => new SongItem(s)));
                    PlayFrom(_items, 0);
                };
                titleRow.Children.Add(playAll);
            }
            head.Children.Add(titleRow);
            head.Children.Add(UiKit.Text(
                $"{pl.Name} · {(res.Playlist?.SongCount ?? res.Songs.Count)} 首", 16, true));
            _body.Children.Add(head);

            if (res.Songs.Count == 0)
            {
                _status.Text = "这个歌单还没有歌曲";
                return;
            }

            _status.Text = "";
            foreach (var song in res.Songs)
            {
                var item = new SongItem(song);
                var index = _items.Count;
                _items.Add(item);
                _body.Children.Add(UiKit.SongRow(item, index + 1, () => PlayFrom(_items, index)));
            }
        }
        catch (Exception ex)
        {
            _body.Children.Clear();
            _status.Text = "";
            ShowError(_info, DescribeError(ex));
        }
    }
}
