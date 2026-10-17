using FamousMusic.Desktop.Data;
using Microsoft.UI.Dispatching;
using Windows.Media.Core;
using Windows.Media.Playback;

namespace FamousMusic.Desktop.Services;

/// <summary>循环模式（与参考实现 MusicPlayer.cs 的 _loopMode 一致）</summary>
public enum LoopMode
{
    /// <summary>0 = 列表循环（顺序播完回到第一首）</summary>
    List = 0,
    /// <summary>1 = 单曲循环</summary>
    One = 1,
    /// <summary>2 = 随机播放</summary>
    Shuffle = 2,
}

/// <summary>
/// 播放服务：包装 Windows.Media.Playback.MediaPlayer。
/// 管理播放队列、上一首/下一首、循环三态、进度上报。
///
/// 循环三态的语义（和 Android 端 / 参考实现 MusicPlayer.cs 对齐）：
///   0 列表循环：顺序播放，播完最后一首回到第一首
///   1 单曲循环：播完当前曲重新开始（手动点「下一首」仍然切歌）
///   2 随机播放：洗牌顺序播放，**一轮播完才重新洗牌**（避免同一首连着放）
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

    /// <summary>随机播放的洗牌顺序（元素是 _queue 的下标）。仅在 Shuffle 模式下使用。</summary>
    private List<int> _shuffleOrder = new();
    private int _shufflePos = -1;

    private readonly Random _rng = new();

    public event Action<Song>? TrackChanged;
    public event Action<bool>? PlayingChanged;
    public event Action<TimeSpan, TimeSpan>? PositionChanged;

    /// <summary>循环模式变化（UI 换 glyph / tooltip）</summary>
    public event Action<LoopMode>? LoopModeChanged;

    public IReadOnlyList<Song> Queue => _queue;
    public Song? CurrentSong => _index >= 0 && _index < _queue.Count ? _queue[_index] : null;
    public int CurrentIndex => _index;
    public bool IsPlaying => _player.PlaybackSession.PlaybackState == MediaPlaybackState.Playing;

    /// <summary>当前曲目地址（HTTP 流）。频谱服务需要它重建 AudioGraph 输入。</summary>
    public Uri? CurrentUri { get; private set; }

    /// <summary>当前播放位置（供歌词 / 频谱对齐用，不经过 UI 线程）</summary>
    public TimeSpan Position => _player.PlaybackSession.Position;

    public TimeSpan Duration => _player.PlaybackSession.NaturalDuration;

    public LoopMode LoopMode { get; private set; } = LoopMode.List;

    /// <summary>兼容旧调用（UI 重构前使用）。等价于 LoopMode == One。</summary>
    public bool IsLoopOne => LoopMode == LoopMode.One;

    // ---- 循环模式展示用常量（UI 层直接取，别在 XAML 里硬编码） ----
    /// <summary>0 列表 / 1 单曲 / 2 随机 的 Segoe Fluent Icons 字形</summary>
    public static readonly string[] LoopGlyphs = { "\uE8EE", "\uE8ED", "\uE8B7" };

    /// <summary>与 LoopGlyphs 对应的中文提示</summary>
    public static readonly string[] LoopTips = { "列表循环", "单曲循环", "随机播放" };

    public string LoopGlyph => LoopGlyphs[(int)LoopMode];
    public string LoopTip => LoopTips[(int)LoopMode];

    private PlayerService()
    {
        _ui = DispatcherQueue.GetForCurrentThread();
        _player.CommandManager.IsEnabled = false;
        _player.MediaEnded += (_, _) =>
        {
            Diag.Log("MediaEnded");
            _ui.TryEnqueue(() =>
            {
                if (LoopMode == LoopMode.One) { Restart(); return; }
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
        RebuildShuffleOrder(keepCurrentFirst: true);
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
            SyncShufflePosToCurrent();
            LoadCurrent();
            Play();
            return;
        }
        _queue.Insert(0, song);
        _index = 0;
        RebuildShuffleOrder(keepCurrentFirst: true);
        LoadCurrent();
        Play();
    }

    private void LoadCurrent()
    {
        var song = CurrentSong;
        if (song == null) return;
        var url = ApiClient.StreamUrl(song.Id);
        Diag.Log($"LoadCurrent id={song.Id} title={song.Title} url={url}");
        CurrentUri = new Uri(url);
        _player.Source = MediaSource.CreateFromUri(CurrentUri);
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

    /// <summary>下一首。auto=true 表示由 MediaEnded 触发（此时随机模式下必须切歌）。</summary>
    public void Next(bool auto = false)
    {
        if (_queue.Count == 0) return;

        if (LoopMode == LoopMode.Shuffle && _queue.Count > 1)
        {
            _shufflePos++;
            if (_shufflePos >= _shuffleOrder.Count)
            {
                // 一轮播完：重新洗牌，并保证第一首不是刚播过的那首，避免同一首连着放
                RebuildShuffleOrder(keepCurrentFirst: false);
                _shufflePos = 0;
            }
            _index = _shuffleOrder[Math.Clamp(_shufflePos, 0, _shuffleOrder.Count - 1)];
            LoadCurrent();
            Play();
            return;
        }

        _index = (_index + 1) % _queue.Count;
        SyncShufflePosToCurrent();
        LoadCurrent();
        Play();
    }

    public void Previous()
    {
        if (_queue.Count == 0) return;
        // 播放超过 3 秒时「上一首」先回到本曲开头（与 Android 端一致）
        if (_player.PlaybackSession.Position > TimeSpan.FromSeconds(3)) { Restart(); return; }

        if (LoopMode == LoopMode.Shuffle && _queue.Count > 1 && _shufflePos > 0)
        {
            _shufflePos--;
            _index = _shuffleOrder[_shufflePos];
            LoadCurrent();
            Play();
            return;
        }

        _index = _index <= 0 ? _queue.Count - 1 : _index - 1;
        SyncShufflePosToCurrent();
        LoadCurrent();
        Play();
    }

    /// <summary>
    /// 循环三态轮换：列表 → 单曲 → 随机 → 列表。
    /// 返回切换后的模式并广播 LoopModeChanged。
    /// </summary>
    public LoopMode CycleLoopMode()
    {
        LoopMode = (LoopMode)(((int)LoopMode + 1) % 3);
        Diag.Log($"LoopMode -> {LoopMode}");

        if (LoopMode == LoopMode.Shuffle) RebuildShuffleOrder(keepCurrentFirst: true);
        else SyncShufflePosToCurrent();

        LoopModeChanged?.Invoke(LoopMode);
        return LoopMode;
    }

    /// <summary>直接设置循环模式（便于从设置项/持久化恢复）</summary>
    public void SetLoopMode(LoopMode mode)
    {
        if (LoopMode == mode) return;
        LoopMode = mode;
        if (mode == LoopMode.Shuffle) RebuildShuffleOrder(keepCurrentFirst: true);
        else SyncShufflePosToCurrent();
        LoopModeChanged?.Invoke(mode);
    }

    /// <summary>兼容旧 UI：单曲循环开关。开=单曲循环，关=回到列表循环。</summary>
    public void ToggleLoopOne()
    {
        SetLoopMode(LoopMode == LoopMode.One ? LoopMode.List : LoopMode.One);
    }

    /// <summary>重新洗牌。keepCurrentFirst=true 时把当前曲放在洗牌结果第一位。</summary>
    private void RebuildShuffleOrder(bool keepCurrentFirst)
    {
        if (_queue.Count == 0)
        {
            _shuffleOrder.Clear();
            _shufflePos = -1;
            return;
        }

        var order = Enumerable.Range(0, _queue.Count).ToList();
        // Fisher-Yates
        for (var i = order.Count - 1; i > 0; i--)
        {
            var j = _rng.Next(i + 1);
            (order[i], order[j]) = (order[j], order[i]);
        }

        if (keepCurrentFirst && _index >= 0 && _index < _queue.Count)
        {
            order.Remove(_index);
            order.Insert(0, _index);
            _shufflePos = 0;
        }
        else
        {
            // 刚播完一首：把这一首挪到末尾，避免新一轮立刻重复
            if (_index >= 0 && _index < _queue.Count && order.Count > 1 && order[0] == _index)
            {
                order.Remove(_index);
                order.Add(_index);
            }
            _shufflePos = -1;
        }

        _shuffleOrder = order;
    }

    /// <summary>退出随机模式 / 顺序切歌后，把洗牌游标对齐到当前曲目，避免下次进随机时错位</summary>
    private void SyncShufflePosToCurrent()
    {
        if (_shuffleOrder.Count == 0) return;
        var pos = _shuffleOrder.IndexOf(_index);
        _shufflePos = pos >= 0 ? pos : -1;
    }

    /// <summary>拖动进度条（0..1）</summary>
    public void SeekToFraction(double fraction)
    {
        if (_suppressSeek) return;
        var total = _player.PlaybackSession.NaturalDuration;
        if (total <= TimeSpan.Zero) return;
        fraction = Math.Clamp(fraction, 0, 1);
        _player.PlaybackSession.Position = TimeSpan.FromTicks((long)(total.Ticks * fraction));
    }

    /// <summary>按绝对时间跳转（歌词点击 / 频谱对齐用）</summary>
    public void SeekTo(TimeSpan position)
    {
        var total = _player.PlaybackSession.NaturalDuration;
        if (total <= TimeSpan.Zero) return;
        if (position < TimeSpan.Zero) position = TimeSpan.Zero;
        if (position > total) position = total;
        _player.PlaybackSession.Position = position;
    }

    public void SetVolume(double v) => _player.Volume = Math.Clamp(v, 0, 1);
}
