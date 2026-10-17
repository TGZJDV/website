using FamousMusic.Desktop.Models;
using Microsoft.UI;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;

namespace FamousMusic.Desktop.Pages;

/// <summary>分类页：分类胶囊 + 该分类歌曲列表</summary>
public sealed class GenresPage : PageBase
{
    private readonly StackPanel _root = new() { Spacing = 16 };
    private readonly WrapPanel _chips = new() { HorizontalSpacing = 8, VerticalSpacing = 8 };
    private readonly StackPanel _list = new() { Spacing = 2 };
    private readonly InfoBar _info = new() { IsClosable = true, IsOpen = false };
    private readonly TextBlock _status = UiKit.Text("", 12.5, false, UiKit.MutedBrush);
    private readonly List<SongItem> _items = new();
    private string? _selected;
    private bool _loaded;

    public GenresPage()
    {
        Margin = new Thickness(28, 24, 28, 18);
        Content = new ScrollViewer
        {
            VerticalScrollBarVisibility = ScrollBarVisibility.Auto,
            HorizontalScrollBarVisibility = ScrollBarVisibility.Disabled,
            Content = _root,
        };

        _root.Children.Add(UiKit.Header("分类", "按曲风浏览全部歌曲"));
        _root.Children.Add(_info);
        _root.Children.Add(_chips);
        _root.Children.Add(_status);
        _root.Children.Add(_list);

        Loaded += async (_, _) =>
        {
            if (_loaded) return;
            _loaded = true;
            await LoadGenresAsync();
        };
    }

    private async Task LoadGenresAsync()
    {
        _status.Text = "正在加载分类…";
        try
        {
            var res = await Session.Api.GenresAsync();
            _chips.Children.Clear();

            if (res.Genres.Count == 0)
            {
                _status.Text = "还没有分类";
                return;
            }

            foreach (var g in res.Genres.OrderByDescending(g => g.Count))
                _chips.Children.Add(BuildChip(g.Genre, g.Count));

            await SelectGenreAsync(res.Genres.OrderByDescending(g => g.Count).First().Genre);
        }
        catch (Exception ex)
        {
            _status.Text = "";
            ShowError(_info, DescribeError(ex));
        }
    }

    private Button BuildChip(string genre, int count)
    {
        var btn = UiKit.PillButton($"{genre} · {count}");
        btn.Click += async (_, _) => await SelectGenreAsync(genre);
        return btn;
    }

    private async Task SelectGenreAsync(string genre)
    {
        _selected = genre;
        _info.IsOpen = false;
        _list.Children.Clear();
        _items.Clear();
        _status.Text = $"正在加载「{genre}」…";
        _list.Children.Add(UiKit.Loading());

        // 分类胶囊高亮
        foreach (var child in _chips.Children)
        {
            if (child is Button b)
                b.Background = UiKit.Surface3Brush;
        }
        foreach (var child in _chips.Children)
        {
            if (child is Button b && b.Content is StackPanel sp && sp.Children.Count > 0
                && sp.Children[0] is TextBlock tb && tb.Text.StartsWith(genre))
            {
                b.Background = UiKit.PrimaryBrush;
            }
        }

        try
        {
            var res = await Session.Api.ListAllSongsAsync(genre: genre, pageSize: 50);
            Render(genre, res.Songs, res.Total);
        }
        catch (Exception ex)
        {
            if (_selected != genre) return;
            _list.Children.Clear();
            _status.Text = "";
            ShowError(_info, DescribeError(ex));
        }
    }

    private void Render(string genre, List<Data.Song> songs, int total)
    {
        if (_selected != genre) return;
        _list.Children.Clear();
        _items.Clear();

        if (songs.Count == 0)
        {
            _status.Text = $"「{genre}」下暂无歌曲";
            return;
        }

        var head = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 12, Margin = new Thickness(0, 4, 0, 6) };
        head.Children.Add(UiKit.Text($"「{genre}」共 {total} 首", 15, true));
        var playAll = UiKit.PillButton("播放全部", "\uE768", primary: true);
        playAll.Click += (_, _) => { if (_items.Count > 0) PlayFrom(_items, 0); };
        head.Children.Add(playAll);
        _list.Children.Add(head);

        foreach (var song in songs)
        {
            var item = new SongItem(song);
            var index = _items.Count;
            _items.Add(item);
            _list.Children.Add(UiKit.SongRow(item, index + 1, () => PlayFrom(_items, index)));
        }

        _status.Text = "";
    }
}
