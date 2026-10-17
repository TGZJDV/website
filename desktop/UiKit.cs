using FamousMusic.Desktop.Models;
using Microsoft.UI;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Windows.UI;
using Microsoft.UI.Text;

namespace FamousMusic.Desktop;

/// <summary>
/// 纯代码构建的 UI 组件工厂。列表项动态生成，避免 DataTemplate 在复杂交互下的绑定限制。
/// </summary>
public static class UiKit
{
    public static readonly Brush SurfaceBrush = new SolidColorBrush(Color.FromArgb(0xFF, 0x12, 0x12, 0x12));
    public static readonly Brush Surface2Brush = new SolidColorBrush(Color.FromArgb(0xFF, 0x1E, 0x1E, 0x1E));
    public static readonly Brush Surface3Brush = new SolidColorBrush(Color.FromArgb(0xFF, 0x2A, 0x2A, 0x2A));
    public static readonly Brush PrimaryBrush = new SolidColorBrush(Color.FromArgb(0xFF, 0x0E, 0xA5, 0xE9));
    public static readonly Brush AccentBrush = new SolidColorBrush(Color.FromArgb(0xFF, 0xE9, 0x1E, 0x63));
    public static readonly Brush TextBrush = new SolidColorBrush(Color.FromArgb(0xFF, 0xF5, 0xF5, 0xF5));
    public static readonly Brush MutedBrush = new SolidColorBrush(Color.FromArgb(0xFF, 0xA0, 0xA0, 0xA0));
    public static readonly Brush HairlineBrush = new SolidColorBrush(Color.FromArgb(0x1F, 0xFF, 0xFF, 0xFF));

    public static Brush CoverFallback =>
        new LinearGradientBrush
        {
            StartPoint = new Windows.Foundation.Point(0, 0),
            EndPoint = new Windows.Foundation.Point(1, 1),
            GradientStops =
            {
                new GradientStop { Color = Color.FromArgb(0xFF, 0x1B, 0x3B, 0x4A), Offset = 0 },
                new GradientStop { Color = Color.FromArgb(0xFF, 0x2A, 0x2A, 0x2A), Offset = 0.6 },
                new GradientStop { Color = Color.FromArgb(0xFF, 0x17, 0x31, 0x3D), Offset = 1 },
            },
        };

    public static TextBlock Text(string text, double size = 14, bool bold = false, Brush? brush = null,
        bool wrap = false, bool ellipsis = true)
    {
        var tb = new TextBlock
        {
            Text = text,
            FontSize = size,
            Foreground = brush ?? TextBrush,
            TextTrimming = ellipsis ? TextTrimming.CharacterEllipsis : TextTrimming.None,
            TextWrapping = wrap ? TextWrapping.Wrap : TextWrapping.NoWrap,
        };
        if (bold) tb.FontWeight = FontWeights.SemiBold;
        return tb;
    }

    public static FontIcon Icon(string glyph, double size = 16, Brush? brush = null) =>
        new() { Glyph = glyph, FontSize = size, Foreground = brush ?? TextBrush };

    /// <summary>封面图：有封面用 ImageBrush，无封面用渐变 + 音符图标</summary>
    public static Border Cover(SongItem item, double width, double height, double radius = 8)
    {
        var border = new Border
        {
            Width = width,
            Height = height,
            CornerRadius = new CornerRadius(radius),
            Background = CoverFallback,
        };

        var src = item.Cover;
        if (src != null)
        {
            border.Background = new ImageBrush
            {
                ImageSource = src,
                Stretch = Stretch.UniformToFill,
            };
        }

        border.Child = new FontIcon
        {
            Glyph = "\uE8D6",
            FontSize = Math.Max(16, Math.Min(width, height) * 0.3),
            Foreground = new SolidColorBrush(Color.FromArgb(0x88, 0xFF, 0xFF, 0xFF)),
            HorizontalAlignment = HorizontalAlignment.Center,
            VerticalAlignment = VerticalAlignment.Center,
        };
        return border;
    }

    /// <summary>圆角卡片容器</summary>
    public static Border Card(UIElement child, double padding = 0, double radius = 12, Brush? background = null)
    {
        return new Border
        {
            CornerRadius = new CornerRadius(radius),
            Background = background ?? Surface2Brush,
            Padding = new Thickness(padding),
            Child = child,
        };
    }

    /// <summary>
    /// 歌曲卡片（发现页网格）：封面 + 标题 + 歌手 + 悬浮播放按钮。
    /// </summary>
    public static Grid SongCard(SongItem item, Action play)
    {
        var root = new Grid { Width = 168, Height = 224, Padding = new Thickness(8) };

        var stack = new StackPanel { Spacing = 6 };

        // 封面 + 播放按钮
        var coverHost = new Grid { Width = 152, Height = 152, HorizontalAlignment = HorizontalAlignment.Center };
        var cover = Cover(item, 152, 152, 10);
        coverHost.Children.Add(cover);

        var playBtn = new Button
        {
            Content = Icon("\uE768", 18, new SolidColorBrush(Colors.White)),
            Width = 42,
            Height = 42,
            CornerRadius = new CornerRadius(21),
            Background = new SolidColorBrush(Color.FromArgb(0xE6, 0x0E, 0xA5, 0xE9)),
            BorderThickness = new Thickness(0),
            Padding = new Thickness(0),
            HorizontalAlignment = HorizontalAlignment.Center,
            VerticalAlignment = VerticalAlignment.Center,
            Opacity = 0,
        };
        ToolTipService.SetToolTip(playBtn, "播放");
        playBtn.Click += (_, _) => play();
        coverHost.Children.Add(playBtn);

        coverHost.PointerEntered += (_, _) => playBtn.Opacity = 1;
        coverHost.PointerExited += (_, _) => playBtn.Opacity = 0;

        stack.Children.Add(coverHost);
        stack.Children.Add(Text(item.Title, 13, true));
        stack.Children.Add(Text(item.Artist, 11.5, false, MutedBrush));
        root.Children.Add(stack);

        root.PointerEntered += (_, _) => root.Background = Surface2Brush;
        root.PointerExited += (_, _) => root.Background = new SolidColorBrush(Colors.Transparent);

        return root;
    }

    /// <summary>歌曲行（列表）：序号 + 封面 + 标题/歌手 + 时长 + 播放按钮</summary>
    public static Grid SongRow(SongItem item, int index, Action play)
    {
        var root = new Grid
        {
            Padding = new Thickness(10, 7, 10, 7),
            ColumnSpacing = 12,
            CornerRadius = new CornerRadius(8),
            Background = new SolidColorBrush(Colors.Transparent),
        };
        root.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(28) });
        root.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        root.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        root.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(56) });
        root.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

        var num = Text(index.ToString(), 12.5, false, MutedBrush);
        num.HorizontalAlignment = HorizontalAlignment.Center;
        num.VerticalAlignment = VerticalAlignment.Center;
        Grid.SetColumn(num, 0);
        root.Children.Add(num);

        var cover = Cover(item, 42, 42, 6);
        Grid.SetColumn(cover, 1);
        root.Children.Add(cover);

        var info = new StackPanel { VerticalAlignment = VerticalAlignment.Center, Spacing = 1 };
        info.Children.Add(Text(item.Title, 13.5, true));
        info.Children.Add(Text($"{item.Artist} · {item.Genre}", 11.5, false, MutedBrush));
        Grid.SetColumn(info, 2);
        root.Children.Add(info);

        var dur = Text(item.DurationText, 12, false, MutedBrush);
        dur.VerticalAlignment = VerticalAlignment.Center;
        dur.HorizontalAlignment = HorizontalAlignment.Right;
        Grid.SetColumn(dur, 3);
        root.Children.Add(dur);

        var playBtn = new Button
        {
            Content = Icon("\uE768", 14, PrimaryBrush),
            Width = 32,
            Height = 32,
            CornerRadius = new CornerRadius(16),
            Background = new SolidColorBrush(Colors.Transparent),
            BorderThickness = new Thickness(0),
            Padding = new Thickness(0),
            VerticalAlignment = VerticalAlignment.Center,
        };
        ToolTipService.SetToolTip(playBtn, "播放");
        playBtn.Click += (_, _) => play();
        Grid.SetColumn(playBtn, 4);
        root.Children.Add(playBtn);

        root.PointerEntered += (_, _) => root.Background = Surface2Brush;
        root.PointerExited += (_, _) => root.Background = new SolidColorBrush(Colors.Transparent);
        root.PointerPressed += (_, e) =>
        {
            // 点击行任意位置（除播放按钮外）也播放
            if (e.OriginalSource is FrameworkElement fe && IsInsideButton(fe)) return;
            play();
        };

        return root;
    }

    private static bool IsInsideButton(DependencyObject? obj)
    {
        while (obj != null)
        {
            if (obj is Button) return true;
            obj = VisualTreeHelper.GetParent(obj);
        }
        return false;
    }

    /// <summary>页面标题 + 副标题</summary>
    public static StackPanel Header(string title, string subtitle)
    {
        var sp = new StackPanel { Spacing = 4 };
        sp.Children.Add(Text(title, 25, true));
        sp.Children.Add(Text(subtitle, 12.5, false, MutedBrush, wrap: true, ellipsis: false));
        return sp;
    }

    /// <summary>播放条上的圆形图标按钮</summary>
    public static Button PlayerIconButton(string glyph, string tooltip, RoutedEventHandler onClick)
    {
        var btn = new Button
        {
            Content = new FontIcon { Glyph = glyph, FontSize = 15, Foreground = TextBrush },
            Width = 34,
            Height = 34,
            Padding = new Thickness(0),
            CornerRadius = new CornerRadius(17),
            Background = new SolidColorBrush(Colors.Transparent),
            BorderThickness = new Thickness(0),
        };
        ToolTipService.SetToolTip(btn, tooltip);
        btn.Click += onClick;
        return btn;
    }

    public static Button PillButton(string text, string? glyph = null, bool primary = false)
    {
        var content = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 6 };
        if (glyph != null) content.Children.Add(Icon(glyph, 13, primary ? new SolidColorBrush(Colors.White) : TextBrush));
        content.Children.Add(Text(text, 12.5, true, primary ? new SolidColorBrush(Colors.White) : TextBrush));

        return new Button
        {
            Content = content,
            Background = primary ? PrimaryBrush : Surface3Brush,
            Foreground = new SolidColorBrush(Colors.White),
            BorderThickness = new Thickness(0),
            CornerRadius = new CornerRadius(16),
            Padding = new Thickness(14, 7, 14, 7),
        };
    }

    /// <summary>加载中的转圈 + 文案</summary>
    public static StackPanel Loading(string text = "加载中…")
    {
        var sp = new StackPanel
        {
            Orientation = Orientation.Horizontal,
            Spacing = 10,
            HorizontalAlignment = HorizontalAlignment.Center,
            Margin = new Thickness(0, 24, 0, 24),
        };
        sp.Children.Add(new ProgressRing
        {
            IsActive = true,
            Width = 18,
            Height = 18,
            Foreground = PrimaryBrush,
        });
        sp.Children.Add(Text(text, 12.5, false, MutedBrush));
        return sp;
    }
}

