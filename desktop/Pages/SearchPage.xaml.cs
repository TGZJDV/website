using System.Collections.ObjectModel;
using FamousMusic.Desktop.Models;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Input;

namespace FamousMusic.Desktop.Pages;

/// <summary>
/// 搜索页 code-behind：搜索框事件 + 拉取结果填充集合。
/// 结果行外观见 Styles/Templates.xaml 的 SongRowTemplate。
/// </summary>
public sealed partial class SearchPage : PageBase
{
    private readonly ObservableCollection<SongRowVm> _rows = new();
    private readonly List<SongItem> _items = new();
    private CancellationTokenSource? _cts;

    public SearchPage()
    {
        InitializeComponent();
        ResultsList.ItemsSource = _rows;
    }

    // ---------------- 事件 ----------------

    /// <summary>
    /// AutoSuggestBox 会自己处理 Enter 键并触发 QuerySubmitted（它内部的 TextBox
    /// 会把回车消费掉），所以「回车搜索」必须挂这里，挂 KeyDown 是收不到的。
    /// </summary>
    private async void SearchBox_QuerySubmitted(AutoSuggestBox sender, AutoSuggestBoxQuerySubmittedEventArgs args)
        => await RunSearchAsync();

    private async void SearchButton_Click(object sender, RoutedEventArgs e) => await RunSearchAsync();

    // ---------------- 搜索 ----------------

    private async Task RunSearchAsync()
    {
        var q = SearchBox.Text.Trim();
        if (q.Length == 0)
        {
            StatusText.Text = "请输入关键词";
            return;
        }

        _cts?.Cancel();
        _cts = new CancellationTokenSource();
        var token = _cts.Token;

        Info.IsOpen = false;
        _rows.Clear();
        _items.Clear();
        StatusText.Text = $"正在搜索「{q}」…";
        LoadingPanel.Visibility = Visibility.Visible;

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
            _rows.Clear();
            StatusText.Text = "";
            ShowError(Info, DescribeError(ex));
        }
        finally
        {
            if (!token.IsCancellationRequested) LoadingPanel.Visibility = Visibility.Collapsed;
        }
    }

    private void Render(string q, List<Data.Song> songs, int total)
    {
        _rows.Clear();
        _items.Clear();

        if (songs.Count == 0)
        {
            StatusText.Text = $"没有找到与「{q}」相关的歌曲";
            return;
        }

        foreach (var song in songs)
        {
            var item = new SongItem(song);
            var index = _items.Count;
            _items.Add(item);
            _rows.Add(new SongRowVm(item, index + 1, () => PlayFrom(_items, index)));
        }

        StatusText.Text = $"「{q}」共 {total} 首 · 已加载 {songs.Count} 首";
    }
}
