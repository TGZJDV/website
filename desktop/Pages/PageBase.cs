using FamousMusic.Desktop.Services;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;

namespace FamousMusic.Desktop.Pages;

/// <summary>所有页面的基类：持有宿主窗口引用与一组通用小工具</summary>
public abstract class PageBase : UserControl
{
    /// <summary>宿主窗口，用于跳转和刷新用户信息</summary>
    public MainWindow Host { get; set; } = null!;

    protected PlayerService Player => PlayerService.Current;

    protected Session Session => Session.Current;

    protected static readonly int DefaultPageSize = 50;

    /// <summary>显示一条错误信息（InfoBar 由页面 XAML 提供，可为 null）</summary>
    protected static void ShowError(InfoBar? bar, string message)
    {
        if (bar == null) return;
        bar.Title = "出错了";
        bar.Message = message;
        bar.Severity = InfoBarSeverity.Error;
        bar.IsOpen = true;
    }

    protected static void ShowInfo(InfoBar? bar, string message, string title = "提示")
    {
        if (bar == null) return;
        bar.Title = title;
        bar.Message = message;
        bar.Severity = InfoBarSeverity.Informational;
        bar.IsOpen = true;
    }

    /// <summary>播放一组歌曲中的第 index 首</summary>
    protected void PlayFrom(IReadOnlyList<Models.SongItem> items, int index)
    {
        if (items.Count == 0) return;
        Player.PlayQueue(items.Select(i => i.Song), Math.Clamp(index, 0, items.Count - 1));
    }

    /// <summary>统一处理异步加载错误的文案</summary>
    protected static string DescribeError(Exception ex) =>
        ex is Data.ApiException api ? api.Message : "加载失败：" + ex.Message;
}
