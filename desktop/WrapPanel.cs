using Microsoft.UI;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Windows.Foundation;

namespace FamousMusic.Desktop;

/// <summary>
/// 极简流式布局面板（WinUI 没有内置 WrapPanel）。
/// 按可用宽度逐行排列子元素，用于歌曲封面网格。
/// </summary>
public sealed class WrapPanel : Panel
{
    public double HorizontalSpacing { get; set; } = 0;
    public double VerticalSpacing { get; set; } = 0;

    protected override Size MeasureOverride(Size availableSize)
    {
        var width = double.IsInfinity(availableSize.Width) ? 1200 : availableSize.Width;
        double x = 0, y = 0, rowHeight = 0;

        foreach (var child in Children)
        {
            child.Measure(new Size(width, double.PositiveInfinity));
            var d = child.DesiredSize;

            if (x > 0 && x + d.Width > width)
            {
                x = 0;
                y += rowHeight + VerticalSpacing;
                rowHeight = 0;
            }

            x += d.Width + HorizontalSpacing;
            rowHeight = Math.Max(rowHeight, d.Height);
        }

        return new Size(width, y + rowHeight);
    }

    protected override Size ArrangeOverride(Size finalSize)
    {
        var width = finalSize.Width;
        double x = 0, y = 0, rowHeight = 0;

        foreach (var child in Children)
        {
            var d = child.DesiredSize;

            if (x > 0 && x + d.Width > width)
            {
                x = 0;
                y += rowHeight + VerticalSpacing;
                rowHeight = 0;
            }

            child.Arrange(new Rect(x, y, d.Width, d.Height));
            x += d.Width + HorizontalSpacing;
            rowHeight = Math.Max(rowHeight, d.Height);
        }

        return new Size(width, Math.Max(finalSize.Height, y + rowHeight));
    }
}
