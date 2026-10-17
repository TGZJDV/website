using FamousMusic.Desktop.Services;
using Microsoft.UI.Xaml.Controls;

namespace FamousMusic.Desktop.Pages;

/// <summary>
/// 所有页面的基类：持有宿主窗口引用与一组通用小工具。
/// 注意：XAML 的根元素写的是 <c>&lt;local:PageBase&gt;</c>，
/// 这样 XamlCompiler 生成的基类与 code-behind 声明的基类一致（否则报 CS0263）。
/// 因此本类不能是 abstract —— XAML 需要能构造根元素。
/// </summary>
public class PageBase : UserControl
{
    /// <summary>宿主窗口，用于跳转和刷新用户信息</summary>
    public MainWindow Host { get; set; } = null!;

    protected PlayerService Player => PlayerService.Current;

    protected Session Session => Session.Current;

    /// <summary>页面被导航进入时调用（首次与后续每次进入都会调用）</summary>
    public virtual void OnNavigatedTo() { }

    /// <summary>显示一条错误信息</summary>
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

    /// <summary>取主题资源（页面 XAML 引用不到的动态画笔，在 code-behind 里用这个）</summary>
    protected static T Res<T>(string key) where T : class =>
        (T)Microsoft.UI.Xaml.Application.Current.Resources[key];
}
