using FamousMusic.Desktop.Data;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Media.Imaging;

namespace FamousMusic.Desktop.Models;

/// <summary>歌曲在 UI 上的展示模型（封面走 BitmapImage 异步下载）</summary>
public sealed class SongItem
{
    private ImageSource? _cover;
    private bool _coverResolved;

    public SongItem(Song song) => Song = song;

    public Song Song { get; }

    public int Id => Song.Id;
    public string Title => Song.Title;
    public string Artist => Song.DisplayArtist;
    public string Genre => Song.Genre;
    public string DurationText => Format.Duration(Song.Duration);
    public string RelativeText => Format.Relative(Song.CreatedAt);
    public string Subtitle => $"{Artist} · {Genre}";

    /// <summary>封面（无 cover_key 时为 null，界面显示占位渐变）</summary>
    public ImageSource? Cover
    {
        get
        {
            if (_coverResolved) return _cover;
            _coverResolved = true;
            var url = ApiClient.CoverUrl(Song);
            if (string.IsNullOrWhiteSpace(url)) return null;
            try
            {
                var bmp = new BitmapImage { DecodePixelWidth = 320 };
                bmp.UriSource = new Uri(url);
                _cover = bmp;
            }
            catch
            {
                _cover = null;
            }
            return _cover;
        }
    }
}

/// <summary>分类展示模型</summary>
public sealed class GenreItem
{
    public GenreItem(GenreCount g)
    {
        Name = g.Genre;
        Count = g.Count;
    }

    public string Name { get; }
    public int Count { get; }
    public string CountText => $"{Count} 首";
}

/// <summary>歌单展示模型</summary>
public sealed class PlaylistItem
{
    public PlaylistItem(Playlist p) => Playlist = p;
    public Playlist Playlist { get; }
    public int Id => Playlist.Id;
    public string Name => Playlist.Name;
    public string OwnerName => Playlist.OwnerName ?? "我";
    public string InfoText => $"{Playlist.SongCount} 首 · {OwnerName} · {Format.Relative(Playlist.CreatedAt)}";
}
