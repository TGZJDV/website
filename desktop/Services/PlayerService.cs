using FamousMusic.Desktop.Data;
using Microsoft.UI.Dispatching;
using Microsoft.UI.Xaml;
using Windows.Media.Core;
using Windows.Media.Playback;

namespace FamousMusic.Desktop.Services;

/// <summary>
/// 播放服务：包装 Windows.Media.Playback.MediaPlayer。
/// 管理播放队列、上一首/下一首、循环模式、进度上报。
/// </summary>
public sealed class PlayerService
{
    public static PlayerService Current { get; } = new();

    private readonly MediaPlayer _player = new()
    {
        AudioCategory = MediaPlayerAudioCategory.Media,
        AutoPlay = true,
    };

    private readonly DispatcherQueue _ui;
    private readonly DispatcherQueueTimer _timer;
    private readonly List<Song> _queue = new();
    private int _index = -1;
    private bool _suppressSeek;

    public event Action<Song>? TrackChanged;
    public event Action<bool>? PlayingChanged;
    public event Action<TimeSpan, TimeSpan>? PositionChanged;

    public IReadOnlyList<Song> Queue => _queue;
    public Song? CurrentSong => _index >= 0 && _index < _queue.Count ? _queue[_index] : null;
    public bool IsPlaying => _player.PlaybackSession.PlaybackState == MediaPlaybackState.Playing;
    public bool IsLoopOne { get; private set; }

    private PlayerService()
    {
        _ui = DispatcherQueue.GetForCurrentThread();
        _player.CommandManager.IsEnabled = false;
        _player.MediaEnded += (_, _) =>
        {
            Diag.Log("MediaEnded");
            _ui.TryEnqueue(() =>
            {
                if (IsLoopOne) { Restart(); return; }
                Next(auto: true);
            });
        };
        _player.MediaFailed += (_, e) =>
        {
            Diag.Log($"MediaFailed: {e.Error} / {e.ErrorMessage}");
            _ui.TryEnqueue(() => Failed?.Invoke(e.ErrorMessage ?? e.Error.ToString()));
        };
        _player.MediaOpened += (_, _) =>
        {
            var s = _player.PlaybackSession;
            Diag.Log($"MediaOpened dur={s.NaturalDuration} canSeek={s.CanSeek}");
        };
        _player.PlaybackSession.PlaybackStateChanged += (_, _) =>
        {
            Diag.Log($"State -> {_player.PlaybackSession.PlaybackState}");
            _ui.TryEnqueue(() => PlayingChanged?.Invoke(IsPlaying));
        };

        _timer = _ui.CreateTimer();
        _timer.Interval = TimeSpan.FromMilliseconds(250);
        _timer.IsRepeating = true;
        _timer.Tick += (_, _) =>
        {
            var s = _player.PlaybackSession;
            PositionChanged?.Invoke(s.Position, s.NaturalDuration);
        };
    }

    public event Action<string>? Failed;

    /// <summary>跳转到队列中的某首并播放</summary>
    public void PlayQueue(IEnumerable<Song> songs, int startIndex)
    {
        var list = songs.ToList();
        if (list.Count == 0) return;
        _queue.Clear();
        _queue.AddRange(list);
        _index = Math.Clamp(startIndex, 0, _queue.Count - 1);
        LoadCurrent();
        Play();
    }

    /// <summary>把单首插到队首并播放（不走整队列，保留当前队列上下文）</summary>
    public void PlaySingle(Song song)
    {
        var existing = _queue.FindIndex(s => s.Id == song.Id);
        if (existing >= 0)
        {
            _index = existing;
            LoadCurrent();
            Play();
            return;
        }
        _queue.Insert(0, song);
        _index = 0;
        LoadCurrent();
        Play();
    }

    private void LoadCurrent()
    {
        var song = CurrentSong;
        if (song == null) return;
        var url = ApiClient.StreamUrl(song.Id);
        Diag.Log($"LoadCurrent id={song.Id} title={song.Title} url={url}");
        _player.Source = MediaSource.CreateFromUri(new Uri(url));
        TrackChanged?.Invoke(song);
        _timer.Start();
    }

    public void Play() => _player.Play();

    public void Pause() => _player.Pause();

    public void TogglePlay()
    {
        if (CurrentSong == null) return;
        if (IsPlaying) Pause(); else Play();
    }

    public void Restart()
    {
        if (CurrentSong == null) return;
        _suppressSeek = true;
        _player.PlaybackSession.Position = TimeSpan.Zero;
        _suppressSeek = false;
        Play();
    }

    public void Next(bool auto = false)
    {
        if (_queue.Count == 0) return;
        _index = (_index + 1) % _queue.Count;
        LoadCurrent();
        Play();
    }

    public void Previous()
    {
        if (_queue.Count == 0) return;
        // 播放超过 3 秒时「上一首」先回到本曲开头（与 Android 端一致）
        if (_player.PlaybackSession.Position > TimeSpan.FromSeconds(3)) { Restart(); return; }
        _index = _index <= 0 ? _queue.Count - 1 : _index - 1;
        LoadCurrent();
        Play();
    }

    public void ToggleLoopOne() => IsLoopOne = !IsLoopOne;

    /// <summary>拖动进度条（0..1）</summary>
    public void SeekToFraction(double fraction)
    {
        if (_suppressSeek) return;
        var total = _player.PlaybackSession.NaturalDuration;
        if (total <= TimeSpan.Zero) return;
        fraction = Math.Clamp(fraction, 0, 1);
        _player.PlaybackSession.Position = TimeSpan.FromTicks((long)(total.Ticks * fraction));
    }

    public void SetVolume(double v) => _player.Volume = Math.Clamp(v, 0, 1);
}
