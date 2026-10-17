using System.Collections.ObjectModel;
using FamousMusic.Desktop.Models;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;

namespace FamousMusic.Desktop.Pages;

/// <summary>
/// 歌单页 code-behind：我的歌单 / 歌单详情两视图切换。
/// 行外观见 Styles/Templates.xaml 的 PlaylistRowTemplate / SongRowTemplate。
/// </summary>
public sealed partial class PlaylistsPage : PageBase
{
    private readonly ObservableCollection<PlaylistRowVm> _playlists = new();
    private readonly ObservableCollection<SongRowVm> _detailRows = new();
    private readonly List<SongItem> _items = new();

    public PlaylistsPage()
    {
        InitializeComponent();
        PlaylistsList.ItemsSource = _playlists;
        DetailSongsList.ItemsSource = _detailRows;
    }

    public override async void OnNavigatedTo()
    {
        // 每次进入都刷新（登录状态可能变了）
        await LoadPlaylistsAsync();
    }

    // ---------------- 新建 ----------------

    private async void CreateButton_Click(object sender, RoutedEventArgs e)
    {
        if (!Session.IsLoggedIn)
        {
            ShowInfo(Info, "请先在「我的」页面登录后再创建歌单");
            return;
        }

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
            ShowDetail(false);
            await LoadPlaylistsAsync();
            ShowInfo(Info, $"已创建歌单「{name}」", "成功");
        }
        catch (Exception ex)
        {
            ShowError(Info, DescribeError(ex));
        }
    }

    // ---------------- 歌单列表 ----------------

    private async Task LoadPlaylistsAsync()
    {
        _playlists.Clear();
        _items.Clear();
        Info.IsOpen = false;
        ShowDetail(false);

        if (!Session.IsLoggedIn)
        {
            StatusText.Text = "";
            ShowInfo(Info, "登录后即可查看我的歌单");
            return;
        }

        StatusText.Text = "正在加载歌单…";
        LoadingPanel.Visibility = Visibility.Visible;
        try
        {
            var res = await Session.Api.MyPlaylistsAsync();
            _playlists.Clear();
            if (res.Playlists.Count == 0)
            {
                StatusText.Text = "还没有歌单，点右上角「新建歌单」创建";
                return;
            }
            StatusText.Text = $"共 {res.Playlists.Count} 个歌单";
            foreach (var pl in res.Playlists)
                _playlists.Add(new PlaylistRowVm(new PlaylistItem(pl), OpenPlaylistAsync));
        }
        catch (Exception ex)
        {
            _playlists.Clear();
            StatusText.Text = "";
            ShowError(Info, DescribeError(ex));
        }
        finally
        {
            LoadingPanel.Visibility = Visibility.Collapsed;
        }
    }

    private async void OpenPlaylistAsync(PlaylistRowVm row)
    {
        _items.Clear();
        _detailRows.Clear();
        Info.IsOpen = false;
        StatusText.Text = $"正在加载歌单「{row.Name}」…";
        LoadingPanel.Visibility = Visibility.Visible;

        try
        {
            var res = await Session.Api.PlaylistDetailAsync(row.Id);

            DetailTitle.Text = $"{row.Name} · {(res.Playlist?.SongCount ?? res.Songs.Count)} 首";
            DetailPlayAllButton.IsEnabled = res.Songs.Count > 0;

            foreach (var song in res.Songs)
            {
                var item = new SongItem(song);
                var index = _items.Count;
                _items.Add(item);
                _detailRows.Add(new SongRowVm(item, index + 1, () => PlayFrom(_items, index)));
            }

            ShowDetail(true);

            if (res.Songs.Count == 0) StatusText.Text = "这个歌单还没有歌曲";
            else StatusText.Text = "";
        }
        catch (Exception ex)
        {
            _detailRows.Clear();
            StatusText.Text = "";
            ShowError(Info, DescribeError(ex));
        }
        finally
        {
            LoadingPanel.Visibility = Visibility.Collapsed;
        }
    }

    private void ShowDetail(bool show)
    {
        PlaylistsList.Visibility = show ? Visibility.Collapsed : Visibility.Visible;
        DetailPanel.Visibility = show ? Visibility.Visible : Visibility.Collapsed;
    }

    // ---------------- 详情事件 ----------------

    private async void BackToPlaylists_Click(object sender, RoutedEventArgs e)
    {
        ShowDetail(false);
        await LoadPlaylistsAsync();
    }

    private void DetailPlayAll_Click(object sender, RoutedEventArgs e)
    {
        if (_items.Count == 0) return;
        PlayFrom(_items, 0);
    }
}
