using System.Collections.ObjectModel;
using FamousMusic.Desktop.Models;
using Microsoft.UI.Xaml;

namespace FamousMusic.Desktop.Pages;

/// <summary>
/// 发现页 code-behind：只负责拉数据、填充 ObservableCollection。
/// 卡片外观见 Styles/Templates.xaml 的 SongCardTemplate。
/// </summary>
public sealed partial class DiscoverPage : PageBase
{
    private readonly ObservableCollection<SongCardVm> _cards = new();
    private readonly List<SongItem> _items = new();
    private bool _loaded;

    public DiscoverPage()
    {
        InitializeComponent();
        SongsGrid.ItemsSource = _cards;
    }

    public override async void OnNavigatedTo()
    {
        if (_loaded) return;
        _loaded = true;
        await LoadAsync(force: false);
    }

    private async Task LoadAsync(bool force)
    {
        if (force)
        {
            _cards.Clear();
            _items.Clear();
        }

        Info.IsOpen = false;
        StatusText.Text = "正在加载曲库…";
        LoadingPanel.Visibility = Visibility.Visible;

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
            StatusText.Text = "";
            ShowError(Info, DescribeError(ex));
        }
        finally
        {
            LoadingPanel.Visibility = Visibility.Collapsed;
        }
    }

    private void ShowSongs(List<Data.Song> songs, int total)
    {
        _cards.Clear();
        _items.Clear();

        if (songs.Count == 0)
        {
            StatusText.Text = "曲库还是空的";
            return;
        }

        foreach (var song in songs)
        {
            var item = new SongItem(song);
            var index = _items.Count;
            _items.Add(item);
            _cards.Add(new SongCardVm(item, () => PlayFrom(_items, index)));
        }

        StatusText.Text = $"共 {total} 首 · 已加载 {songs.Count} 首";
    }

    // ---------------- 事件 ----------------

    private void PlayAllButton_Click(object sender, RoutedEventArgs e)
    {
        if (_items.Count == 0) return;
        PlayFrom(_items, 0);
    }

    private void ShuffleButton_Click(object sender, RoutedEventArgs e)
    {
        if (_items.Count == 0) return;
        PlayFrom(_items, Random.Shared.Next(_items.Count));
    }

    private async void RefreshButton_Click(object sender, RoutedEventArgs e)
        => await LoadAsync(force: true);
}
