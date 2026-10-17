using System.Collections.ObjectModel;
using FamousMusic.Desktop.Models;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Media;

namespace FamousMusic.Desktop.Pages;

/// <summary>
/// 分类页 code-behind：加载分类胶囊、切换分类、填充歌曲列表。
/// 胶囊 / 行外观见 Styles/Templates.xaml。
/// </summary>
public sealed partial class GenresPage : PageBase
{
    private readonly ObservableCollection<GenreChipVm> _chips = new();
    private readonly ObservableCollection<SongRowVm> _rows = new();
    private readonly List<SongItem> _items = new();
    private string? _selected;
    private bool _loaded;

    public GenresPage()
    {
        InitializeComponent();
        GenresRepeater.ItemsSource = _chips;
        SongsList.ItemsSource = _rows;
    }

    public override async void OnNavigatedTo()
    {
        if (_loaded) return;
        _loaded = true;
        await LoadGenresAsync();
    }

    // ---------------- 分类 ----------------

    private async Task LoadGenresAsync()
    {
        StatusText.Text = "正在加载分类…";
        try
        {
            var res = await Session.Api.GenresAsync();
            _chips.Clear();

            if (res.Genres.Count == 0)
            {
                StatusText.Text = "还没有分类";
                return;
            }

            var ordered = res.Genres.OrderByDescending(g => g.Count).ToList();
            foreach (var g in ordered)
                _chips.Add(new GenreChipVm(new GenreItem(g), OnChipSelected));

            await SelectGenreAsync(ordered[0].Genre);
        }
        catch (Exception ex)
        {
            StatusText.Text = "";
            ShowError(Info, DescribeError(ex));
        }
    }

    private async void OnChipSelected(GenreChipVm chip) => await SelectGenreAsync(chip.Name);

    private async Task SelectGenreAsync(string genre)
    {
        _selected = genre;
        Info.IsOpen = false;
        _rows.Clear();
        _items.Clear();
        StatusText.Text = $"正在加载「{genre}」…";
        LoadingPanel.Visibility = Visibility.Visible;

        // 分类胶囊高亮
        var normalBg = Res<Brush>("AppSurface3Brush");
        var primaryBg = Res<Brush>("AppPrimaryBrush");
        var normalFg = Res<Brush>("AppTextBrush");
        var activeFg = Res<Brush>("AppTextBrush");
        foreach (var chip in _chips)
        {
            var active = chip.Name == genre;
            chip.ChipBackground = active ? primaryBg : normalBg;
            chip.ChipForeground = active ? activeFg : normalFg;
        }

        try
        {
            var res = await Session.Api.ListAllSongsAsync(genre: genre, pageSize: 50);
            Render(genre, res.Songs, res.Total);
        }
        catch (Exception ex)
        {
            if (_selected != genre) return;
            _rows.Clear();
            StatusText.Text = "";
            ShowError(Info, DescribeError(ex));
        }
        finally
        {
            if (_selected == genre) LoadingPanel.Visibility = Visibility.Collapsed;
        }
    }

    private void Render(string genre, List<Data.Song> songs, int total)
    {
        if (_selected != genre) return;
        _rows.Clear();
        _items.Clear();

        if (songs.Count == 0)
        {
            StatusText.Text = $"「{genre}」下暂无歌曲";
            return;
        }

        foreach (var song in songs)
        {
            var item = new SongItem(song);
            var index = _items.Count;
            _items.Add(item);
            _rows.Add(new SongRowVm(item, index + 1, () => PlayFrom(_items, index)));
        }

        StatusText.Text = $"「{genre}」共 {total} 首";
    }
}
