using System.ComponentModel;
using System.Runtime.CompilerServices;

namespace FamousMusic.Desktop;

/// <summary>
/// 极简 INotifyPropertyChanged 基类。
/// 重构后 UI 走 XAML DataTemplate 绑定，所以展示模型需要能通知变更
/// （旧版是直接用 C# 摆控件，不需要这一层）。
/// </summary>
public abstract class ObservableObject : INotifyPropertyChanged
{
    public event PropertyChangedEventHandler? PropertyChanged;

    protected void OnPropertyChanged([CallerMemberName] string? name = null)
        => PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(name));

    protected bool SetProperty<T>(ref T field, T value, [CallerMemberName] string? name = null)
    {
        if (EqualityComparer<T>.Default.Equals(field, value)) return false;
        field = value;
        OnPropertyChanged(name);
        return true;
    }
}
