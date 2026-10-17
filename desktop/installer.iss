; ============================================================
;  TGZJDV's Music —— Windows 桌面客户端 Inno Setup 安装脚本
; ============================================================
;  使用方法（二选一）：
;
;  方式 A：命令行（推荐，可重复）
;     cd G:\website\desktop
;     dotnet publish -c Release -r win-x64 --self-contained true
;     "C:\Program Files (x86)\Inno Setup 6\ISCC.exe" installer.iss
;
;  方式 B：Inno Setup 图形界面打开本文件 → Compile
;
;  产物： Output\TGZJDV_Music_v1.0.0_安装程序.exe
;         （不写死绝对路径，输出到本仓库 desktop\Output\ 下，避免污染别的盘）
;
;  依赖： 只有 Inno Setup 6（https://jrsoftware.org/isdl.php）。
;         不需要装 .NET 运行时 —— 下面 Publish 用了 self-contained，
;         也不需要 Windows App SDK 运行时 —— csproj 里已
;         WindowsAppSDKSelfContained=true，运行时随程序复制。
; ============================================================

#define MyAppName "TGZJDV's Music"
#define MyAppShortName "FamousMusic"
#define MyAppVersion "1.0.0"
#define MyAppPublisher "TGZJDV"
#define MyAppURL "https://famousmusic.asia"
#define MyAppExeName "FamousMusic.exe"

; 程序集名是 FamousMusic（见 csproj 的 <AssemblyName>），不是项目名 FamousMusic.Desktop。
; 发布输出目录：先判断 publish/ 在不在，优先用它（自包含发布）。
#if FileExists(AddBackslash(SourcePath) + "bin\Release\net8.0-windows10.0.19041.0\win-x64\publish\" + MyAppExeName)
  #define PublishDir AddBackslash(SourcePath) + "bin\Release\net8.0-windows10.0.19041.0\win-x64\publish\"
#else
  ; 没 publish 过就用普通 build 输出（也能跑，前提是本机装了 Windows App SDK 运行时）
  #define PublishDir AddBackslash(SourcePath) + "bin\Release\net8.0-windows10.0.19041.0\win-x64\"
#endif

[Setup]
AppId={{8F3A2C71-5D64-4E1B-9A07-3C2E7B5F1D88}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppVerName={#MyAppName} {#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}
AppUpdatesURL={#MyAppURL}

; 每个用户装到自己目录，不需要管理员权限（安装到用户 AppData 下）
DefaultDirName={autopf}\{#MyAppName}
DefaultGroupName={#MyAppName}
DisableProgramGroupPage=yes

; 输出到仓库内的 Output\，用相对路径，换机器/换盘不用改
OutputDir=Output
OutputBaseFilename={#MyAppShortName}_v{#MyAppVersion}_安装程序

; 压缩
Compression=lzma2/max
SolidCompression=yes

; Windows 10 1809+（csproj 的 TargetPlatformMinVersion 是 10.0.17763）
MinVersion=10.0.17763

; 64 位程序：不允许装到 32 位系统
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible

; 权限：普通用户即可（不需要 admin）
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog

; 界面
WizardStyle=modern
DisableWelcomePage=no
ShowLanguageDialog=auto

; 卸载
UninstallDisplayIcon={app}\{#MyAppExeName}
UninstallDisplayName={#MyAppName}

[Languages]
Name: "chinesesimplified"; MessagesFile: "compiler:Languages\ChineseSimplified.isl"
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "创建桌面快捷方式"; GroupDescription: "快捷方式："; Flags: checkedonce

[Files]
; 主程序与全部依赖（自包含发布：含 .NET 运行时 + Windows App SDK 运行时）
Source: "{#PublishDir}*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"
Name: "{group}\卸载 {#MyAppName}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "运行 {#MyAppName}"; Flags: nowait postinstall skipifsilent

[UninstallRun]
; 卸载前先结束进程，否则 exe 被占用会删不掉
Filename: "{cmd}"; Parameters: "/c taskkill /f /im {#MyAppExeName} 2>nul"; Flags: runhidden; RunOnceId: "KillApp"

[UninstallDelete]
; 应用运行期写的会话/日志在 %LOCALAPPDATA%\FamousMusic（见 Services/Diag.cs、Session.cs），
; 不在 {app} 下，卸载**不删**用户数据（保留登录态与本地缓存）。
; 如需彻底清理，让用户手动删除 %LOCALAPPDATA%\FamousMusic。
; 这里只清 Inno 自己可能写进 {app} 的运行期文件（如崩溃日志）。
Type: filesandordirs; Name: "{app}\crash.log"

[Code]
// 启动前检查：主 exe 必须存在，否则说明忘记先 dotnet publish
function InitializeSetup(): Boolean;
var
  ExePath: String;
begin
  Result := True;
  ExePath := ExpandConstant('{#PublishDir}{#MyAppExeName}');
  if not FileExists(ExePath) then
  begin
    MsgBox('未找到发布输出：' + #13#10 + ExePath + #13#10 + #13#10 +
           '请先在 G:\website\desktop 下执行：' + #13#10 +
           '    dotnet publish -c Release -r win-x64 --self-contained true' + #13#10 + #13#10 +
           '然后重新编译本安装脚本。', mbCriticalError, MB_OK);
    Result := False;
  end;
end;
