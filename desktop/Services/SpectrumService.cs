using System.Runtime.InteropServices;
using FamousMusic.Desktop.Data;
using WinRT;
using Windows.Media;
using Windows.Media.Audio;
using Windows.Media.Core;
using Windows.Media.MediaProperties;
using Windows.Media.Render;

namespace FamousMusic.Desktop.Services;

/// <summary>
/// 把 AudioBuffer 的原生内存读成 float 数组的 COM 接口（Windows.Media 的说法）。
/// 注意：CsWinRT 下**不能**直接 `(IMemoryBufferByteAccess)reference` 强转，
/// 会抛 "Invalid cast from 'WinRT.IInspectable'"；必须用 `reference.As&lt;T&gt;()`（WinRT 扩展方法）。
/// </summary>
[ComImport]
[Guid("5B0D3235-4DBA-4D44-865E-8F1D0E4FD04D")]
[InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
internal interface IMemoryBufferByteAccess
{
    void GetBuffer(out IntPtr buffer, out uint capacity);
}

/// <summary>频谱数据快照（32 个柱子的归一化电平 0..1）</summary>
public sealed class SpectrumFrame
{
    public float[] Bars { get; init; } = Array.Empty<float>();
    /// <summary>本次音频来源是否是真实音频（false = 退化的模拟频谱）</summary>
    public bool IsReal { get; init; }
}

/// <summary>
/// 频谱服务：把「正在播放的音频」实时抓成 32 个 FFT 柱。
///
/// ## 关键实测结论（2026-10，本机 Win11 + 参考 MusicPlayer.cs 的原始写法对照）
///
/// 参考实现用 <c>CreateFileInputNodeAsync(本地文件)</c>，但我们的音频是
/// <c>https://api.famousmusic.asia/api/songs/{id}/stream</c> 的 **HTTP 流**，
/// 而 `CreateFileInputNodeAsync` 只接受 <c>StorageFile</c>（本地文件）。
///
/// 实测两条路都通了，选择**不下载临时文件**的方案：
///
/// 1) **<c>CreateMediaSourceAudioInputNodeAsync(MediaSource.CreateFromUri(httpUrl))</c>
///    —— 成功**（推荐，已实测）**。AudioGraph 能直接吃 HTTP 流，
///    返回 Status=Success，duration 正确（00:01:15.52，与文件一致）。
///    省掉 30MB 临时文件的下载 + 落盘 + 清理。
/// 2) 下载到本地临时文件 + `CreateFileInputNodeAsync` —— 也成功，但要下载整首歌、
///    占磁盘、还得管清理，已放弃。
///
/// ## 拓扑（这个很关键，踩过坑）
/// <code>
/// MediaSourceAudioInputNode ─┬─→ AudioFrameOutputNode      （取 PCM 做 FFT）
///                            └─→ DeviceOutputNode(gain=0)   （实时时钟 + 让 graph 推进）
/// </code>
/// - **只接 FrameOutput**：能拿到数据，但 graph 会以 ~100× 速度把整首解码完
///   （实测 4 秒吃进 20543 帧），和 MediaPlayer 的播放位置完全脱节，频谱会瞬间跑完。
/// - **接了 DeviceOutput 但不静音**：声音会出两遍（MediaPlayer 一遍 + AudioGraph 一遍）。
/// - **DeviceOutput.OutgoingGain = 0**：graph 按设备时钟实时推进（实测 4 秒 401 帧 ≈ 100 帧/秒），
///   且不出声。这是最终采用的方案。
///
/// 注意：MediaPlayer 的 <c>Volume</c> **不需要**改成 0（参考实现改了，那是它用 FileInput
/// 自己出声的老写法）。这里声音始终由 MediaPlayer 负责，AudioGraph 全程静音。
///
/// ## 退化方案
/// 任何一步失败（AudioGraph 不可用 / 无音频设备 / MediaSource 创建失败），自动退化到
/// <see cref="SimulatedMode"/>：由 <c>PlayerService.Position</c> 驱动的函数合成波形，
/// 视觉上仍有起伏但**不是真实频谱**。<see cref="SpectrumFrame.IsReal"/> 会如实为 false。
/// </summary>
public sealed class SpectrumService : IDisposable
{
    public static SpectrumService Current { get; } = new();

    /// <summary>柱子数量（与参考实现一致）</summary>
    public const int BarCount = 32;

    /// <summary>FFT 窗口大小（与参考实现一致）</summary>
    private const int FftSize = 1024;

    /// <summary>刷新间隔（参考实现 30ms）</summary>
    public static readonly TimeSpan RefreshInterval = TimeSpan.FromMilliseconds(30);

    /// <summary>平滑系数（参考实现 0.2）</summary>
    private const float Smoothing = 0.2f;

    private readonly object _gate = new();
    private readonly float[] _latest = new float[BarCount];
    private readonly float[] _smoothed = new float[BarCount];

    // ---- FFT 工作区（复用，避免每帧分配） ----
    private readonly float[] _win = new float[FftSize];
    private readonly float[] _re = new float[FftSize];
    private readonly float[] _im = new float[FftSize];

    /// <summary>
    /// 复用的大小足够的采样缓冲。每帧约 100 次调用，绝不能每次 new 数组，
    /// 否则 32MB/分钟的 GC 压力会直接拖累播放。
    /// </summary>
    private float[] _sampleBuffer = new float[4096];

    private AudioGraph? _graph;
    private MediaSourceAudioInputNode? _input;
    private AudioFrameOutputNode? _frameOut;
    private AudioDeviceOutputNode? _deviceOut;

    private bool _running;

    private long _frameCount;

    /// <summary>音频线程写、UI 线程读 → volatile，否则 UI 可能迟迟看不到 true</summary>
    private volatile bool _realDataSeen;

    private float _lastPeak;

    /// <summary>最近一次抓到的真实音频峰值（用于 UI 判断是否有真实数据）</summary>
    public float LastPeak => Volatile.Read(ref _lastPeak);

    /// <summary>是否拿到过真实音频帧</summary>
    public bool HasRealData => _realDataSeen;

    /// <summary>当前是否运行中</summary>
    public bool IsRunning => _running;

    /// <summary>是否处于退化（模拟）模式</summary>
    public bool SimulatedMode { get; private set; }

    /// <summary>诊断信息（初始化失败原因等），便于界面/日志排查</summary>
    public string? LastError { get; private set; }

    private SpectrumService()
    {
        // 汉宁窗（与参考实现一致）：0.5 * (1 - cos(2πi/(N-1)))
        for (var i = 0; i < FftSize; i++)
            _win[i] = (float)(0.5 * (1 - Math.Cos(2 * Math.PI * i / (FftSize - 1))));
    }

    /// <summary>
    /// 开始抓取某个 HTTP 音频流的频谱。
    /// 切歌时先 <see cref="Stop"/> 再 Start，或直接重复 Start（内部会先清理旧 graph）。
    /// </summary>
    public async Task StartAsync(Uri streamUri)
    {
        if (streamUri == null) return;

        await StopAsync();

        lock (_gate)
        {
            Array.Clear(_latest);
            Array.Clear(_smoothed);
            _realDataSeen = false;
            _frameCount = 0;
            Volatile.Write(ref _lastPeak, 0f);
            LastError = null;
            SimulatedMode = false;
        }

        AudioGraph? graph = null;
        try
        {
            var settings = new AudioGraphSettings(AudioRenderCategory.Media);
            var create = await AudioGraph.CreateAsync(settings);
            if (create.Status != AudioGraphCreationStatus.Success)
            {
                Fallback($"AudioGraph.CreateAsync 失败: {create.Status} {create.ExtendedError?.Message}");
                return;
            }

            graph = create.Graph;

            // ★ 核心：HTTP 流直接进 MediaSourceAudioInputNode（已实测可用，无需下载临时文件）
            var mediaSource = MediaSource.CreateFromUri(streamUri);
            var nodeResult = await graph.CreateMediaSourceAudioInputNodeAsync(mediaSource);
            if (nodeResult.Status != MediaSourceAudioInputNodeCreationStatus.Success)
            {
                graph.Dispose();
                Fallback($"CreateMediaSourceAudioInputNodeAsync 失败: {nodeResult.Status} {nodeResult.ExtendedError?.Message}");
                return;
            }

            var input = nodeResult.Node;

            // 取 PCM 的出口
            var frameOut = graph.CreateFrameOutputNode();
            input.AddOutgoingConnection(frameOut);

            // 实时时钟：静音的 device sink。不加这个的话 graph 会以远快于实时的速度解码，
            // 频谱和实际播放位置脱节（实测 4 秒跑完 20543 帧 = 整首）。
            AudioDeviceOutputNode? deviceOut = null;
            try
            {
                var dev = await graph.CreateDeviceOutputNodeAsync();
                if (dev.Status == AudioDeviceNodeCreationStatus.Success)
                {
                    deviceOut = dev.DeviceOutputNode;
                    deviceOut.OutgoingGain = 0; // 静音：声音由 MediaPlayer 出，这里只要时钟
                    input.AddOutgoingConnection(deviceOut);
                }
                else
                {
                    Diag.Log($"Spectrum: 无设备输出节点 {dev.Status}（频谱仍可用，但可能超前）");
                }
            }
            catch (Exception ex)
            {
                Diag.Log("Spectrum: CreateDeviceOutputNodeAsync 异常 " + ex.Message);
            }

            _graph = graph;
            _input = input;
            _frameOut = frameOut;
            _deviceOut = deviceOut;

            graph.QuantumProcessed += OnQuantumProcessed;
            graph.Start();
            input.Start();

            _running = true;
            Diag.Log($"Spectrum: started (http MediaSource) deviceSink={deviceOut != null} dur={input.Duration}");
        }
        catch (Exception ex)
        {
            // 初始化中途失败必须把已创建的 graph 释放掉，否则切歌重试会累积泄漏 AudioGraph
            try { graph?.Dispose(); } catch { }
            Fallback("Spectrum 初始化异常: " + ex.Message);
        }
    }

    /// <summary>停止并释放 AudioGraph</summary>
    public Task StopAsync()
    {
        _running = false;
        var graph = _graph;
        _graph = null;
        _input = null;
        _frameOut = null;
        _deviceOut = null;

        try { graph?.QuantumProcessed -= OnQuantumProcessed; } catch { }
        try { graph?.Stop(); } catch { }
        try { graph?.Dispose(); } catch { }

        lock (_gate)
        {
            Array.Clear(_latest);
            Array.Clear(_smoothed);
        }
        return Task.CompletedTask;
    }

    private void Fallback(string reason)
    {
        LastError = reason;
        SimulatedMode = true;
        Diag.Log("Spectrum 退化到模拟模式: " + reason);
        // 留下空 graph，UI 侧仍会调用 ReadBars（模拟模式由 ReadBars 的 position 参数驱动）
        _graph = null;
        _input = null;
        _frameOut = null;
        _deviceOut = null;
    }

    /// <summary>
    /// 读取当前频谱（已做 0.2 平滑）。UI 定时器每 30ms 调用一次。
    /// 模拟模式（AudioGraph 不可用）时用 <paramref name="position"/> 合成波形，
    /// 保证 UI 仍有东西可画，但 <see cref="SpectrumFrame.IsReal"/> 为 false。
    /// </summary>
    public SpectrumFrame ReadBars(TimeSpan position, bool isPlaying)
    {
        var bars = new float[BarCount];

        if (SimulatedMode || !_realDataSeen)
        {
            // 退化：用播放位置合成一条随音乐「假装」起伏的曲线。
            // 明确不是真实频谱，UI 可据此显示提示。
            // 注意 !_realDataSeen 也走这里：AudioGraph 启动到第一批音频帧到达之间
            // 有几百毫秒空窗，先给 UI 一点东西画，避免柱子全部贴底闪烁。
            var t = position.TotalSeconds;
            lock (_gate)
            {
                for (var i = 0; i < BarCount; i++)
                {
                    var phase = t * (2.2 + i * 0.15) + i * 0.7;
                    var v = 0.5 + 0.5 * Math.Sin(phase);
                    var envelope = Math.Exp(-i / 14.0);              // 低频高、高频低，像真的
                    var target = isPlaying ? (float)(0.15 + 0.55 * v * envelope) : 0f;
                    _smoothed[i] += (target - _smoothed[i]) * Smoothing;
                    bars[i] = _smoothed[i];
                }
            }
            return new SpectrumFrame { Bars = bars, IsReal = false };
        }

        lock (_gate)
        {
            for (var i = 0; i < BarCount; i++)
            {
                var target = isPlaying ? _latest[i] : 0f;
                _smoothed[i] += (target - _smoothed[i]) * Smoothing;
                bars[i] = Math.Clamp(_smoothed[i], 0, 1);
            }
        }
        return new SpectrumFrame { Bars = bars, IsReal = true };
    }

    // ============ AudioGraph 每 quantum 回调（非 UI 线程） ============

    private void OnQuantumProcessed(AudioGraph sender, object args)
    {
        var frameOut = _frameOut;
        if (frameOut == null) return;

        try
        {
            using var frame = frameOut.GetFrame();
            if (frame == null) return;

            using var buffer = frame.LockBuffer(AudioBufferAccessMode.Read);
            using var reference = buffer.CreateReference();

            // ★ CsWinRT 必须用 As<T>()，直接强转会抛 Invalid cast
            var access = reference.As<IMemoryBufferByteAccess>();
            access.GetBuffer(out var ptr, out var capacity);

            var sampleCount = (int)(capacity / sizeof(float));
            if (sampleCount < 2) return;

            // 立体声交织：左右取平均 → 单声道
            var pairs = sampleCount / 2;
            var len = Math.Min(pairs, FftSize);

            // 用 Marshal.Copy 把原生 float 缓冲读出来（等价于 unsafe 指针遍历，
            // 但不需要 csproj 开 AllowUnsafeBlocks，避免动别人的构建配置）。
            // 缓冲复用：仅当容量不足时扩容一次。
            if (_sampleBuffer.Length < sampleCount)
                _sampleBuffer = new float[Math.Max(sampleCount, _sampleBuffer.Length * 2)];
            var rawSamples = _sampleBuffer;
            Marshal.Copy(ptr, rawSamples, 0, sampleCount);

            var peak = 0f;
            for (var i = 0; i < len; i++)
            {
                var mono = (rawSamples[i * 2] + rawSamples[i * 2 + 1]) * 0.5f;
                var abs = mono < 0 ? -mono : mono;
                if (abs > peak) peak = abs;
                _re[i] = mono * _win[i];
                _im[i] = 0f;
            }
            // 不足 FftSize 的部分补零
            for (var i = len; i < FftSize; i++)
            {
                _re[i] = 0f;
                _im[i] = 0f;
            }

            Volatile.Write(ref _lastPeak, peak);

            Fft(_re, _im, FftSize);

            // 32 个频段：每个 band 取一段幅度谱的平均（与参考实现同构）
            var binsPerBar = (FftSize / 2) / BarCount;
            if (binsPerBar < 1) binsPerBar = 1;

            Span<float> levels = stackalloc float[BarCount];
            for (var b = 0; b < BarCount; b++)
            {
                var sum = 0f;
                for (var j = 0; j < binsPerBar; j++)
                {
                    var idx = b * binsPerBar + j + 1;
                    if (idx >= FftSize / 2) break;
                    sum += (float)Math.Sqrt(_re[idx] * _re[idx] + _im[idx] * _im[idx]);
                }
                var mag = sum / binsPerBar * 5000f; // 与参考实现同一标定
                var db = mag > 0 ? (float)(20 * Math.Log10(mag)) - 40f : -80f;
                levels[b] = Math.Clamp((db + 40f) / 40f, 0f, 1f);
            }

            lock (_gate)
            {
                for (var b = 0; b < BarCount; b++) _latest[b] = levels[b];
                _frameCount++;
                _realDataSeen = true;
            }
        }
        catch (Exception ex)
        {
            // 单帧失败不能打断播放：只记一次
            if (_frameCount == 0) Diag.Log("Spectrum quantum error: " + ex.Message);
        }
    }

    /// <summary>
    /// 迭代版基 2 FFT（原地）。逐字对照参考实现 MusicPlayer.cs 的 Fft()：
    /// 位反转重排 → 蝶形运算。未做幅度归一化（靠上面的 5000 标定）。
    /// </summary>
    private static void Fft(float[] re, float[] im, int n)
    {
        // 位反转重排
        for (int a = 1, b = 0; a < n; a++)
        {
            var bit = n >> 1;
            while ((b & bit) != 0) { b ^= bit; bit >>= 1; }
            b ^= bit;
            if (a < b)
            {
                (re[a], re[b]) = (re[b], re[a]);
                (im[a], im[b]) = (im[b], im[a]);
            }
        }

        // 蝶形
        for (var len = 2; len <= n; len <<= 1)
        {
            double ang = 2 * Math.PI / len, wr = Math.Cos(ang), wi = -Math.Sin(ang);
            for (var c = 0; c < n; c += len)
            {
                double cr = 1, ci = 0;
                for (var k = 0; k < len / 2; k++)
                {
                    int i1 = c + k, i2 = c + k + len / 2;
                    double tr = cr * re[i2] - ci * im[i2];
                    double ti = cr * im[i2] + ci * re[i2];
                    re[i2] = (float)(re[i1] - tr);
                    im[i2] = (float)(im[i1] - ti);
                    re[i1] += (float)tr;
                    im[i1] += (float)ti;
                    var nr = cr * wr - ci * wi;
                    var ni = cr * wi + ci * wr;
                    cr = nr; ci = ni;
                }
            }
        }
    }

    public void Dispose()
    {
        _ = StopAsync();
    }
}

