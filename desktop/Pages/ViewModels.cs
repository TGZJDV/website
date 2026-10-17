using System.Windows.Input;
using FamousMusic.Desktop.Data;
using FamousMusic.Desktop.Models;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Media.Imaging;

namespace FamousMusic.Desktop.Pages;

/// <summary>
/// 极简 ICommand 实现。XAML 里的 DataTemplate 不能挂事件处理器
/// （资源字典中的模板没有 code-behind），所以模板内的交互统一走 Command，
/// 真正的处理逻辑留在各页面的 code-behind 里。
/// </summary>
public sealed class RelayCommand : ICommand
{
    private readonly Action _execute;
    private readonly Func<bool>? _canExecute;

    public RelayCommand(Action execute, Func<bool>? canExecute = null)
    {
        _execute = execute;
        _canExecute = canExecute;
    }

    public event EventHandler? CanExecuteChanged;

    public bool CanExecute(object? parameter) => _canExecute?.Invoke() ?? true;

    public void Execute(object? parameter) => _execute();

    public void RaiseCanExecuteChanged() => CanExecuteChanged?.Invoke(this, EventArgs.Empty);
}

/// <summary>封面画笔工厂：有 cover_key 用 ImageBrush，否则返回 null（模板显示渐变占位）</summary>
internal static class CoverBrushes
{
    /// <summary>歌曲封面（按 decodeWidth 解码，列表用小图、卡片用大图）</summary>
    public static Brush? ForSong(Song song, int decodeWidth)
    {
        var url = ApiClient.CoverUrl(song);
        if (string.IsNullOrWhiteSpace(url)) return null;
        try
        {
            var bmp = new BitmapImage { DecodePixelWidth = decodeWidth, UriSource = new Uri(url) };
            return new ImageBrush { ImageSource = bmp, Stretch = Stretch.UniformToFill };
        }
        catch
        {
            return null;
        }
    }
}

/// <summary>发现页的歌曲卡片（SongCardTemplate 的 DataType）</summary>
public sealed class SongCardVm
{
    private Brush? _coverBrush;
    private bool _coverResolved;

    public SongCardVm(SongItem item, Action play)
    {
        Item = item;
        PlayCommand = new RelayCommand(play);
    }

    public SongItem Item { get; }
    public string Title => Item.Title;
    public string Artist => Item.Artist;
    public ICommand PlayCommand { get; }

    /// <summary>绑定到封面 Border 的 Background；null 时露出底层渐变占位</summary>
    public Brush? CoverBrush
    {
        get
        {
            if (_coverResolved) return _coverBrush;
            _coverResolved = true;
            _coverBrush = CoverBrushes.ForSong(Item.Song, 320);
            return _coverBrush;
        }
    }
}

/// <summary>歌曲行（SongRowTemplate 的 DataType）：搜索 / 分类 / 歌单 / 收藏 共用</summary>
public sealed class SongRowVm
{
    private Brush? _coverBrush;
    private bool _coverResolved;

    public SongRowVm(SongItem item, int index, Action play)
    {
        Item = item;
        Index = index;
        PlayCommand = new RelayCommand(play);
    }

    public SongItem Item { get; }
    public int Index { get; }
    public string IndexText => Index.ToString();
    public string Title => Item.Title;
    public string Subtitle => $"{Item.Artist} · {Item.Genre}";
    public string DurationText => Item.DurationText;
    public ICommand PlayCommand { get; }

    public Brush? CoverBrush
    {
        get
        {
            if (_coverResolved) return _coverBrush;
            _coverResolved = true;
            _coverBrush = CoverBrushes.ForSong(Item.Song, 120);
            return _coverBrush;
        }
    }
}

/// <summary>分类胶囊（GenreChipTemplate 的 DataType）</summary>
public sealed class GenreChipVm : ObservableObject
{
    private Brush _chipBackground = (Brush)Application.Current.Resources["AppSurface3Brush"];
    private Brush _chipForeground = (Brush)Application.Current.Resources["AppTextBrush"];

    public GenreChipVm(GenreItem item, Action<GenreChipVm> select)
    {
        Item = item;
        SelectCommand = new RelayCommand(() => select(this));
    }

    public GenreItem Item { get; }
    public string Text => $"{Item.Name} · {Item.Count}";
    public string Name => Item.Name;
    public int Count => Item.Count;
    public ICommand SelectCommand { get; }

    public Brush ChipBackground
    {
        get => _chipBackground;
        set => SetProperty(ref _chipBackground, value);
    }

    public Brush ChipForeground
    {
        get => _chipForeground;
        set => SetProperty(ref _chipForeground, value);
    }
}

/// <summary>歌单行（PlaylistRowTemplate 的 DataType）</summary>
public sealed class PlaylistRowVm
{
    public PlaylistRowVm(PlaylistItem item, Action<PlaylistRowVm> open)
    {
        Item = item;
        OpenCommand = new RelayCommand(() => open(this));
    }

    public PlaylistItem Item { get; }
    public int Id => Item.Id;
    public string Name => Item.Name;
    public string InfoText => Item.InfoText;
    public ICommand OpenCommand { get; }
}
