# TGZJDV's Music · Windows 桌面客户端

WinUI 3（Windows App SDK 1.6）+ .NET 8 写的 Windows 桌面音乐客户端，对接线上后端 `https://api.famousmusic.asia/api`。
界面中文、深色主题，配色抄 Android 版 `ui/theme/Theme.kt`（AppPrimary `#0EA5E9` / AppSurface `#121212` / AppSurface2 `#1E1E1E` / AppSurface3 `#2A2A2A` / AppText `#F5F5F5` / AppMuted `#A0A0A0`）。

---

## 构建

```powershell
cd G:\website\desktop
dotnet build -c Debug
```

首次构建会自动 `dotnet restore`（需要能访问 nuget.org）。

### 输出

```
G:\website\desktop\bin\Debug\net8.0-windows10.0.19041.0\win-x64\FamousMusic.exe
```

整个 `win-x64` 目录就是一个可分发单元（Windows App SDK 运行时已经随包复制进去）。

---

## 运行

**方式一：双击**

```
G:\website\desktop\bin\Debug\net8.0-windows10.0.19041.0\win-x64\FamousMusic.exe
```

**方式二：命令行**

```powershell
& "G:\website\desktop\bin\Debug\net8.0-windows10.0.19041.0\win-x64\FamousMusic.exe"
```

**方式三：开发期**

```powershell
cd G:\website\desktop
dotnet run -c Debug
```

### 运行时依赖

- Windows 10 1809（17763）或更高，x64。
- .NET 8 桌面运行时：**本构建是框架依赖**，需要机器装 `Microsoft.NETCore.App 8.0`。
  想做成完全免安装的单文件包，可另跑 `dotnet publish -c Release -r win-x64 --self-contained true`。
- **不需要**预装 Windows App Runtime —— 已设 `WindowsAppSDKSelfContained=true`，
  运行时会随 `win-x64` 目录一起分发。**不要**把这个属性改回 false：本机只注册了
  WindowsAppRuntime 1.3/1.4/1.5/1.7/1.8，没有 1.6，改回去会导致 exe 启动即弹
  “This application could not be started”。

---

## 目录结构

```
desktop/
├── FamousMusic.Desktop.csproj   手写工程文件（无 WinUI 模板，注意关键 MSBuild 属性，见文件内注释）
├── app.manifest                 PerMonitorV2 DPI 感知 + Win10/11 supportedOS
├── App.xaml / App.xaml.cs       应用入口；只保留最小 XamlControlsResources
├── MainWindow.xaml.cs           全部主界面布局（纯 C# 构建：左侧导航 + 内容区 + 底部播放条）
├── UiKit.cs                     主题色与控件工厂（歌曲卡片、歌曲行、胶囊按钮、封面占位）
├── WrapPanel.cs                 流式布局面板（WinUI 没有内置 WrapPanel），用于封面网格
├── Format.cs                    时长/相对时间格式化（对齐 Android util/Format.kt）
├── Data/
│   ├── Models.cs                全部 DTO，字段名严格照抄 Android Models.kt
│   └── ApiClient.cs             后端 API 客户端（端点、参数、错误处理对照 ApiClient.kt）
├── Models/SongItem.cs           UI 展示模型（封面 BitmapImage 惰性创建）
├── Services/
│   ├── Session.cs               登录会话，token 持久化到 %LOCALAPPDATA%\FamousMusic\session.json
│   └── PlayerService.cs         播放服务，包装 Windows.Media.Playback.MediaPlayer
└── Pages/
    ├── PageBase.cs              页面基类
    ├── DiscoverPage.cs          发现页（歌曲网格，自动翻页取全）
    ├── SearchPage.cs            搜索页（关键词搜索，自动翻页取全）
    ├── GenresPage.cs            分类页（曲风胶囊 + 歌曲列表）
    ├── PlaylistsPage.cs         歌单页（我的歌单 / 歌单详情 / 新建歌单）
    └── MePage.cs                我的页（登录 / 退出 / 我的收藏）
```

---

## 实现的功能

### 发现页
- 歌曲网格：封面 + 标题 + 歌手，悬停显示播放按钮，点击卡片区域播放
- 顶部「播放全部 / 随机播放 / 刷新」
- 首屏先拉第一页（50 首）快速出内容，再按 `page` 自动翻页补齐，状态栏显示「共 N 首 · 已加载 N 首」
- 后端 `/songs` 的 `limit` 被夹在 50，所以必须翻页才能拿到全部

### 搜索页
- 搜索框 + 搜索按钮，回车即搜
- 结果列表：序号 + 封面 + 标题 + 歌手·曲风 + 时长 + 播放按钮，点击整行播放
- 搜索同样走自动翻页，结果完整不截断；连续搜索会取消上一次请求

### 分类页
- 曲风胶囊（按歌曲数降序，带数量），点击切换
- 该分类下歌曲列表 + 「播放全部」

### 歌单页
- 「我的歌单」列表（名称 / 歌曲数 / 归属 / 创建时间）
- 点进歌单查看详情歌曲列表、播放全部
- 新建歌单（对话框输入名称，实时调后端）

### 我的页
- 邮箱 + 密码登录，token + 用户信息持久化到本地，下次启动免登录
- 登录态展示头像首字母、用户名、邮箱、头衔、管理员标记
- 退出登录（清本地凭据）
- 「我的收藏」歌曲列表 + 播放全部

### 播放
- 播放 / 暂停、上一首 / 下一首、单曲循环
- 进度条可拖动（按下时暂停刷新，松开后 seek 到目标位置）
- 进度条实时走时（250ms 刷新），显示当前位置 / 总时长
- 音量滑杆
- 底部播放条显示当前封面、标题、歌手
- 播放队列：从当前列表/网格上下文播放，上一首/下一首在队列内循环
- 「上一首」在播放超过 3 秒时先回到本曲开头（与 Android 版一致）
- 音频源直接用 `Windows.Media.Playback.MediaPlayer` + `MediaSource.CreateFromUri(streamUrl)`；
  `GET /songs/{id}/stream` 是 307 跳转到 OSS 预签名地址，MediaPlayer 会自动跟随

### 界面
- 左侧导航：发现 / 搜索 / 分类 / 歌单 / 我的，选中项主色高亮
- 底部播放条常驻，左下角显示当前登录用户
- 深色主题（`RequestedTheme="Dark"`），Mica(BaseAlt) 背景，不支持时回退 Acrylic
- 中文界面，错误统一用 InfoBar 展示后端返回的 `{error}` 文案

---

## 未实现 / 与 Android 版的差距

| 功能 | 状态 | 说明 |
|---|---|---|
| 歌词显示 | 未做 | `GET /songs/{id}/lyrics` 的 LRC 文本 API 已在 `ApiClient/LyricsAsync` 里，但没有歌词面板 UI |
| 动画封面 / 逐字歌词 | 未做 | — |
| 液态玻璃质感 | 未做 | WinUI 无对应库，用 Mica / Acrylic 代替 |
| 歌曲上传 | 未做 | 需要 `POST /songs/presign` + OSS 直传 + `/songs/complete`，Android 端 UploadScreen 有完整实现可参考 |
| 注册 / 找回密码 | 未做 | 需邮箱验证码流程（`/auth/send-code`、`/auth/register`、`/auth/reset-password`） |
| 歌曲详情页 / 评论 | 未做 | `/songs/{id}`、`/comments/song/{id}` 可用 |
| 收藏开关按钮 | 未做 | 能看收藏列表，但列表/播放条上没有「加心」按钮（`POST/DELETE /songs/{id}/favorite` 已在 `ApiClient` 里） |
| 歌单里加歌 / 删歌 / 删歌单 | 未做 | API 客户端已封装 `AddSong` 相关端点，UI 没接 |
| 均衡器 | 未做 | Android 版用系统 AudioEffect，Windows 端需 WASAPI/APO，成本高 |
| 桌面歌词 / 迷你模式 | 未做 | — |
| 全局媒体键 / SMTC | 未做 | 可用 `SystemMediaTransportControls` 补 |
| 自动更新 | 未做 | — |

---

## 技术备注（踩坑记录）

1. **没有 WinUI 项目模板**：`dotnet new list` 里没有 winui/winui3，VS 模板目录也是空的。所以 csproj 是手写的。

2. **`AppxMSBuildToolsPath` 必须显式指定**：`dotnet build` 用的 .NET SDK 不带 AppxPackage 的
   MSBuild 任务（`Microsoft.Build.Packaging.Pri.Tasks.dll` 只随 VS 提供）。不指定会报
   `MSB4062: 未能从程序集 ...\AppxPackage\Microsoft.Build.Packaging.Pri.Tasks.dll 加载任务 ExpandPriContent`。
   注意 `AppxMSBuildToolsPath` **结尾必须带反斜杠** —— WindowsAppSDK 的 targets 里是字符串拼接，
   少了反斜杠会拼出 `...\AppxPackageMicrosoft.Build.AppxPackage.dll`。

3. **`WindowsAppSDKSelfContained=true` 且必须显式给 RID**：否则报
   `WindowsAppSDKSelfContained requires a supported Windows architecture.`
   需要 `<PlatformTarget>x64</PlatformTarget>` + `<RuntimeIdentifier>win-x64</RuntimeIdentifier>`。

4. **XamlCompiler 会静默崩溃**：如果 `_logs/` 之类的子目录里放了 .xaml 草稿文件，
   会被 SDK 默认 glob 当成 `Page`（XamlPage）收进去，XamlCompiler.exe 直接 exit 1，
   且不给任何错误信息（`output.json` 里也是空的）。csproj 里用
   `<Page Remove="_logs\**" />` 排掉。

5. **主界面用纯 C# 构建**：`MainWindow.xaml.cs` 里没有任何 DataTemplate / x:Name 依赖，
   避开了 XAML 编译器在这个环境下的问题，也让动态列表（歌曲卡片/歌曲行）更好写。

6. **API 字段名全部照抄 Kotlin**：`cover_key` / `audio_key` / `lyrics_key` / `uploader_id` /
   `uploader_name` / `created_at` / `avatar_key` / `is_admin` 等，用 `[JsonPropertyName]` 显式映射。
   封面 URL = `{BASE}/songs/{id}/cover`（`cover_key` 为空时无封面），
   音频 URL = `{BASE}/songs/{id}/stream`（307 → OSS 预签名）。
