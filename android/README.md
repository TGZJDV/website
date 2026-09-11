# TGZJDV's Music Android 客户端

基于 [famousmusic.asia](https://music.famousmusic.asia) 的音乐网站做的原生 Android 客户端。
Kotlin + Jetpack Compose + Media3 (ExoPlayer)，直接调用网站同一套 REST API。

## 发布新版本（Release）

签名密钥 `keystore/famousmusic-release.jks` **不入库**，密码配置在同目录的
`keystore.properties`（同样不入库，格式见 `keystore.properties.example`）。
丢了密钥就永远无法更新同包名 App，务必离线备份。

```powershell
# 构建已签名的 release 包（必须 JDK 17，AGP 8.9 不兼容 JDK 25）
cd G:\website\android
$env:JAVA_HOME="G:\jdk-17.0.1"
.\gradlew.bat :app:assembleRelease

# 校验签名（日志里出现 validateSigningRelease 通过即说明密钥配置正确）
& "C:\Android\build-tools\37.0.0\apksigner.bat" verify --print-certs `
  app\build\outputs\apk\release\app-release.apk
```

发到网站下载页分三步：

```powershell
# 1) 改 app/build.gradle.kts 的 versionCode / versionName
# 2) APK 放进前端静态目录（文件名带版本号，便于长缓存）
Copy-Item app\build\outputs\apk\release\app-release.apk `
  ..\frontend\public\downloads\TGZJDVsMusic-<版本>.apk
# 3) 取大小和 SHA-256，填进 api/src/routes/download.ts 的 RELEASES
(Get-Item ..\frontend\public\downloads\TGZJDVsMusic-<版本>.apk).Length
(Get-FileHash ..\frontend\public\downloads\TGZJDVsMusic-<版本>.apk -Algorithm SHA256).Hash.ToLower()
```

最后部署后端 + 构建部署前端即可，下载页无需改代码。

> ⚠️ **为什么 APK 不放 OSS**：阿里云禁止通过 OSS 默认域名分发 `.apk`，会返回
> `ApkDownloadForbidden`（"please use CNAME instead"）。该限制是**按文件内容嗅探**的
> —— 改成 `.bin` 后缀 + `application/octet-stream` 依然被拦（APK 本质就是带
> `AndroidManifest.xml` 的 ZIP）。唯一解法是给桶绑自定义域名，而中国内地区域的
> 自定义域名需要 ICP 备案。所以安装包由 Cloudflare Pages 同源托管
> （代价是国内下载速度约 111 KB/s，比 OSS 的 662 KB/s 慢）。

## 功能

| 模块 | 说明 |
| --- | --- |
| 发现 | 横幅、音乐分类、最新上传（点卡片即播） |
| 搜索 | 歌名/歌手搜索，实时结果列表 |
| 分类 | 按流派筛选歌曲 |
| 歌单 | 我的歌单、新建、删除、歌单详情、移除歌曲 |
| 个人中心 | 头像上传、头衔/管理员徽章、我的上传（可删除）、我的收藏（可取消） |
| 登录注册 | 邮箱验证码注册、登录、忘记密码重置 |
| 歌曲详情 | 播放、收藏、加入歌单、删除自己的歌曲、评论区（发表/删除） |
| 全屏播放 | 大封面、LRC 歌词同步高亮滚动、进度拖动、上下曲、收藏 |
| 上传音乐 | 音频/封面/LRC 歌词，直传阿里云 OSS（预签名 URL） |
| 管理员面板 | 用户列表、封禁/解封、修改头衔、修改用户名、设为管理员、删除用户 |
| 后台播放 | 播放服务 + 通知栏控制 + 锁屏/蓝牙/车机（MediaSession） |

## 技术栈

- Kotlin 2.1 + Jetpack Compose (Material 3)
- Media3 ExoPlayer + MediaSessionService（后台播放、通知栏、锁屏控制）
- OkHttp + kotlinx.serialization（REST 客户端，自动注入 JWT）
- Coil（封面/头像加载）
- Navigation Compose
- 后端 API：`https://api.famousmusic.asia/api`（在 `app/build.gradle.kts` 的 `buildConfigField` 中配置）

## 构建

前置：JDK 17、Android SDK（platform 35 / build-tools 35.0.0）。

```powershell
# 指定 JDK 17（AGP 8.9 不支持过新的 JDK）
$env:JAVA_HOME="G:\jdk-17.0.1"

# 调试包
.\gradlew.bat :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk

# 安装到已连接设备
.\gradlew.bat :app:installDebug

# 发布包（未签名，需自行配置签名）
.\gradlew.bat :app:assembleRelease
```

`local.properties` 里的 `sdk.dir` 指向本机 Android SDK（该文件不入库）。

## 项目结构

```
app/src/main/java/com/famousmusic/app/
├── data/            # 数据层：模型、JSON、会话存储、API 客户端
├── playback/        # 播放层：MediaSessionService、播放控制器、LRC 解析
├── session/         # 全局登录状态
├── ui/
│   ├── theme/       # 配色（与网站一致）
│   ├── components/  # 通用组件（封面、头像、歌曲行、迷你播放条）
│   ├── screens/     # 各页面
│   └── AppNav.kt    # 路由 + 底部导航
├── MainActivity.kt
└── FamousMusicApp.kt
```

## 关于 vivo 原子随身听

原子随身听能读取的，是应用通过 **MediaSession** 暴露的播放信息（标题、歌手、专辑、封面、时长、播放状态、进度）。
本项目已用 Media3 的 `MediaSessionService` 提供完整的标准实现（`playback/PlaybackService.kt`），
因此在通知栏、锁屏、蓝牙/车机等系统媒体控制中心都能正常显示与操作。

### App 侧已做的（标准 MediaSession，全部实测可见）

| 能力 | 实现 | `dumpsys media_session` 结果 |
| --- | --- | --- |
| 播放/暂停/上下曲/进度 | Media3 `MediaSessionService` | ✅ |
| 元数据（标题/歌手/专辑/流派/封面） | `MediaMetadata` | ✅ `metadata: size=17` |
| **收藏** | `HeartRating`（user + overall rating） | ✅ **`rating type=1`**（与酷狗一致） |
| **播放列表** | `player.playlistMetadata` + 队列 | ✅ `queueTitle=TGZJDV's Music 播放列表, size=N` |
| **循环模式** | `PlaybackState` actions（`SET_REPEAT_MODE`） | ✅ |

设备侧还需把包名加入音源白名单（否则原子随身听列表里看不到）：

```powershell
adb shell settings get system musicwidget_list_pkg_type_key
# 期望包含 "com.famousmusic.app"
```

或参考 MT 论坛「蓝厂原子随身听音源白名单添加办法」一类教程 / 蓝河工具箱。

### 面板里的「歌词 / 收藏 / 循环 / 播放列表」为什么仍显示不支持

这几项属于 **vivo 私有授权体系**，第三方无法通过标准 API 接入。反编译 `com.vivo.musicwidgetmix`
（`/system/app/VivoMusicWidgetMix/VivoMusicWidgetMix.apk`）可见：

- 存在 `music_app_white_list` / `music_app_white_list_version` / `lock_music_app_white_list`（secure 设置）
- 相关类与常量：`AuthorityManager`、`AuthorsBean`、`setMusicAppAuthState`、`APP_NOT_AUTH`、`ACTION_START_AUTH`
- assets 里有 `panel_lyric_loading.json` —— 歌词面板是内置功能，**仅对已授权应用点亮**

> ⚠️ **不要手改 `music_app_white_list`**：它要的是**对象数组**（`[{"...":...}]`），
> 写成字符串数组 `["pkg"]` 会让原子随身听抛 `Gson JsonSyntaxException` 反复崩溃（实测踩过，需 `settings put secure music_app_white_list '[]'` 回滚）。
> 条目字段结构未公开，需 vivo 侧授权，不是改设置或改 App 能做到的。

歌词本身在 Android 上**没有任何标准通道**，App 内歌词由自己解析 LRC 展示（全屏播放页已实现）。


## 备注

- API 无需为 Android 做额外改动，音频流（`/songs/:id/stream`）与歌词（`/songs/:id/lyrics`）走 OSS 预签名 307 重定向，ExoPlayer 直接可播。
- 管理员账号与网站一致（同一个用户体系，`is_admin=1` 的用户登录后会出现「管理后台」入口）。
