# 云音乐 Android 客户端

基于 [famousmusic.asia](https://music.famousmusic.asia) 的音乐网站做的原生 Android 客户端。
Kotlin + Jetpack Compose + Media3 (ExoPlayer)，直接调用网站同一套 REST API。

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

另外，原子随身听对**音源应用有白名单限制**（这是系统侧配置，不是 App 能自行注册的）。
如果原子随身听里看不到本应用，需要在设备侧把本应用加入音源白名单（相关做法可参考 MT 论坛
「蓝厂原子随身听音源白名单添加办法」一类教程，属于需要 root / MT 管理器改系统文件的操作）。
App 侧能做的兼容已做完：标准 MediaSession + 完整元数据 + 封面 + 后台播放。

## 备注

- API 无需为 Android 做额外改动，音频流（`/songs/:id/stream`）与歌词（`/songs/:id/lyrics`）走 OSS 预签名 307 重定向，ExoPlayer 直接可播。
- 管理员账号与网站一致（同一个用户体系，`is_admin=1` 的用户登录后会出现「管理后台」入口）。
