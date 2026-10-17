using FamousMusic.Desktop.Models;
using Microsoft.UI;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Input;
using Microsoft.UI.Xaml.Media;

namespace FamousMusic.Desktop.Pages;

/// <summary>搜索页：搜索框 + 结果列表（一次性翻页取全）</summary>
public sealed class SearchPage : PageBase
{
    private readonly StackPanel _root = new() { Spacing = 14 };
    private readonly StackPanel _list = new() { Spacing = 2 };
    private readonly InfoBar _info = new() { IsClosable = true, IsOpen = false };
    private readonly TextBlock _status = UiKit.Text("", 12.5, false, UiKit.MutedBrush);
    private readonly TextBox _box = new()
    {
        PlaceholderText = "搜索歌曲 / 歌手…",
        FontSize = 14,
        Height = 40,
        VerticalContentAlignment = VerticalAlignment.Center,
    };
    private readonly List<SongItem> _items = new();
    private CancellationTokenSource? _cts;

    public SearchPage()
    {
        Margin = new Thickness(28, 24, 28, 18);
        Content = new ScrollViewer
        {
            VerticalScrollBarVisibility = ScrollBarVisibility.Auto,
            HorizontalScrollBarVisibility = ScrollBarVisibility.Disabled,
            Content = _root,
        };

        BuildShell();
    }

    private void BuildShell()
    {
        _root.Children.Add(UiKit.Header("搜索", "输入关键词，回车开始搜索"));

        var bar = new Grid { ColumnSpacing = 10, Height = 40 };
        bar.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        bar.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

        _box.KeyDown += async (_, e) =>
        {
            if (e.Key == Windows.System.VirtualKey.Enter) await RunSearchAsync();
        };

        var searchBtn = UiKit.PillButton("搜索", "\uE721", primary: true);
        searchBtn.Click += async (_, _) => await RunSearchAsync();

        Grid.SetColumn(_box, 0);
        Grid.SetColumn(searchBtn, 1);
        bar.Children.Add(_box);
        bar.Children.Add(searchBtn);
        _root.Children.Add(bar);

        _root.Children.Add(_info);
        _root.Children.Add(_status);
        _root.Children.Add(_list);
    }

    private async Task RunSearchAsync()
    {
        var q = _box.Text.Trim();
        if (q.Length == 0)
        {
            _status.Text = "请输入关键词";
            return;
        }

        _cts?.Cancel();
        _cts = new CancellationTokenSource();
        var token = _cts.Token;

        _info.IsOpen = false;
        _list.Children.Clear();
        _items.Clear();
        _status.Text = $"正在搜索「{q}」…";
        _list.Children.Add(UiKit.Loading());

        try
        {
            // 后端 limit 上限 50，用自动翻页保证结果完整
            var res = await Session.Api.ListAllSongsAsync(q: q, pageSize: 50, onProgress: (songs, total) =>
            {
                if (token.IsCancellationRequested) return;
                DispatcherQueue.TryEnqueue(() => Render(q, songs, total));
            });

            if (token.IsCancellationRequested) return;
            Render(q, res.Songs, res.Total);
        }
        catch (Exception ex)
        {
            if (token.IsCancellationRequested) return;
            _list.Children.Clear();
            _status.Text = "";
            ShowError(_info, DescribeError(ex));
        }
    }

    private void Render(string q, List<Data.Song> songs, int total)
    {
        _list.Children.Clear();
        _items.Clear();

        if (songs.Count == 0)
        {
            _status.Text = $"没有找到与「{q}」相关的歌曲";
            return;
        }

        foreach (var song in songs)
        {
            var item = new SongItem(song);
            var index = _items.Count;
            _items.Add(item);
            _list.Children.Add(UiKit.SongRow(item, index + 1, () => PlayFrom(_items, index)));
        }

        _status.Text = $"「{q}」共 {total} 首 · 已加载 {songs.Count} 首";
    }
}
