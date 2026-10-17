using FamousMusic.Desktop.Models;
using Microsoft.UI;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;

namespace FamousMusic.Desktop.Pages;

/// <summary>发现页：欢迎区 + 分类胶囊 + 全部歌曲网格</summary>
public sealed class DiscoverPage : PageBase
{
    private readonly StackPanel _root = new() { Spacing = 18 };
    private readonly WrapPanel _grid = new() { };
    private readonly InfoBar _info = new() { IsClosable = true, IsOpen = false, Margin = new Thickness(0, 4, 0, 0) };
    private readonly TextBlock _status = UiKit.Text("", 12.5, false, UiKit.MutedBrush);
    private readonly List<SongItem> _items = new();
    private bool _loaded;

    public DiscoverPage()
    {
        Margin = new Thickness(28, 24, 28, 18);
        var scroll = new ScrollViewer
        {
            VerticalScrollBarVisibility = ScrollBarVisibility.Auto,
            HorizontalScrollBarVisibility = ScrollBarVisibility.Disabled,
            Content = _root,
        };
        Content = scroll;

        BuildShell();
        Loaded += OnLoaded;
    }

    private void BuildShell()
    {
        var head = new Grid();
        head.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        head.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

        var titleBlock = UiKit.Header("发现音乐", "TGZJDV's Music · 全部曲库");

        var actions = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8, VerticalAlignment = VerticalAlignment.Center };
        var playAll = UiKit.PillButton("播放全部", "\uE768", primary: true);
        playAll.Click += (_, _) =>
        {
            if (_items.Count == 0) return;
            PlayFrom(_items, 0);
        };
        var shuffle = UiKit.PillButton("随机播放", "\uE8B1");
        shuffle.Click += (_, _) =>
        {
            if (_items.Count == 0) return;
            PlayFrom(_items, Random.Shared.Next(_items.Count));
        };
        var refresh = UiKit.PillButton("刷新", "\uE72C");
        refresh.Click += async (_, _) => await LoadAsync(force: true);
        actions.Children.Add(playAll);
        actions.Children.Add(shuffle);
        actions.Children.Add(refresh);

        Grid.SetColumn(titleBlock, 0);
        Grid.SetColumn(actions, 1);
        actions.HorizontalAlignment = HorizontalAlignment.Right;
        head.Children.Add(titleBlock);
        head.Children.Add(actions);
        _root.Children.Add(head);

        _root.Children.Add(_info);
        _root.Children.Add(_status);

        _grid.HorizontalSpacing = 6;
        _grid.VerticalSpacing = 10;
        _root.Children.Add(_grid);
    }

    private async void OnLoaded(object sender, RoutedEventArgs e)
    {
        if (_loaded) return;
        _loaded = true;
        await LoadAsync(force: false);
    }

    private async Task LoadAsync(bool force)
    {
        if (force) { _grid.Children.Clear(); _items.Clear(); }
        _info.IsOpen = false;
        _status.Text = "正在加载曲库…";
        _grid.Children.Add(UiKit.Loading());

        try
        {
            // 先拉一页快速出内容，再翻页补齐（后端 limit 上限 50）
            var first = await Session.Api.ListSongsAsync(page: 1, limit: 50);
            ShowSongs(first.Songs, first.Total);

            if (first.Total > first.Songs.Count)
            {
                var all = await Session.Api.ListAllSongsAsync(pageSize: 50, onProgress: (songs, total) =>
                {
                    // onProgress 在后台线程回调，切回 UI 线程刷新
                    DispatcherQueue.TryEnqueue(() => ShowSongs(songs, total));
                });
                ShowSongs(all.Songs, all.Total);
            }
        }
        catch (Exception ex)
        {
            _status.Text = "";
            ShowError(_info, DescribeError(ex));
        }
    }

    private void ShowSongs(List<Data.Song> songs, int total)
    {
        _grid.Children.Clear();
        _items.Clear();

        if (songs.Count == 0)
        {
            _status.Text = "曲库还是空的";
            return;
        }

        foreach (var song in songs)
        {
            var item = new SongItem(song);
            var index = _items.Count;
            _items.Add(item);
            _grid.Children.Add(UiKit.SongCard(item, () => PlayFrom(_items, index)));
        }

        _status.Text = $"共 {total} 首 · 已加载 {songs.Count} 首";
    }
}
