# 云音乐 · Cloudflare 全栈音乐网站

一个部署在 Cloudflare 生态上的音乐分享网站：用户可以**邮箱验证码注册**、**忘记密码**、**上传头像**、上传音乐（含封面与 LRC 歌词）、创建播放列表、收藏、评论，并支持流式播放与歌词同步显示。

**上传体验**：选择音频文件后，前端会自动读取 **ID3 标签**（MP3/FLAC/M4A/OGG 等）并填入歌名、歌手、分类；若音频内嵌封面会自动作为封面图；若标签内带歌词（USLT 帧）还会自动作为歌词文件 —— 基本无需手动填写。

## 技术栈

| 部分 | 技术 |
| --- | --- |
| 前端 | React 18 + Vite + TypeScript + Tailwind CSS + Zustand + React Router |
| 后端 API | Cloudflare Workers（wrangler 4.x）+ Hono |
| 数据库 | Cloudflare D1（SQLite） |
| 文件存储 | Cloudflare R2（音频 / 封面 / 歌词） |
| 认证 | JWT（HS256）+ PBKDF2 密码哈希（Web Crypto） |
| 邮件验证码 | Resend（注册验证码 / 忘记密码重置） |
| 元数据 | music-metadata-browser（浏览器解析 ID3/FLAC/M4A 等标签） |

> ⚠️ **版本要求**：本机若使用 Node 20+ 建议搭配 **wrangler 4.x**（`npm i -D wrangler@latest`）。wrangler 3.x 在 Node 25 上会导致 `wrangler dev` 卡死。`api/wrangler.toml` 已配置 `send_metrics = false`，避免在无外网环境因遥测上报而阻塞。

## 项目结构

```
website/
├── api/                  # 后端（Cloudflare Workers）
│   └── src/
│       ├── index.ts      # Hono 入口
│       ├── auth.ts       # JWT / 密码哈希 / 鉴权中间件
│       ├── utils.ts      # 工具函数
│       ├── types.ts      # 环境绑定类型
│       └── routes/       # auth / songs / playlists / comments
├── frontend/             # 前端（React + Vite）
│   └── src/
│       ├── api.ts        # API 客户端
│       ├── store/        # 认证 / 播放器状态
│       ├── components/   # 布局、播放条、歌曲卡片等
│       └── pages/        # 发现 / 搜索 / 分类 / 详情 / 歌单 / 上传等
├── schema.sql            # D1 数据库表结构
└── README.md
```

## 一、初始化 Cloudflare 资源

> 前置条件：注册 [Cloudflare](https://dash.cloudflare.com/) 账号，本地安装 Node.js 18+。

```bash
# 1. 进入后端目录并安装依赖
cd api
npm install

# 2. 登录 Cloudflare
npx wrangler login

# 3. 创建 D1 数据库（记下返回的 database_id）
npx wrangler d1 create music-db

# 4. 创建 R2 存储桶
npx wrangler r2 bucket create music-files

# 5. 设置 JWT 密钥（生产环境必须修改！）
npx wrangler secret put JWT_SECRET

# 6. 设置邮件服务（注册验证码 / 忘记密码需要）
 npx wrangler secret put RESEND_API_KEY
# 在 https://resend.com 注册并创建 API Key
# 可选：设置发件人（需在 Resend 验证域名）
 npx wrangler secret put EMAIL_FROM
把第 3 步得到的 `database_id` 填到 `api/wrangler.toml` 的 `database_id` 字段。

## 二、初始化数据库

```bash
# 本地（开发）
npm run db:local

# 线上（生产）
npm run db:migrate
```

## 三、本地开发

需要两个终端：

```bash
# 终端 1：启动后端（默认 http://localhost:8787）
cd api
npm run dev

# 终端 2：启动前端（http://localhost:5173，已配置 /api 代理）
cd frontend
npm install
npm run dev
```

打开 http://localhost:5173 即可体验。

## 四、部署

### 1. 部署后端 API

```bash
cd api
npm run deploy
```

部署完成后你会得到类似 `https://music-api.<你的子域>.workers.dev` 的地址。

### 2. 部署前端

```bash
cd frontend
# 指定后端 API 地址（部署到 Pages 后必须设置）
# 方法 A：构建时写入
npm run build -- --mode production  # 或用环境变量
# 方法 B：在 Cloudflare Pages 的“环境变量”中设置 VITE_API_URL
```

推荐在 Cloudflare Pages 控制台配置：

1. 打开 [Cloudflare Dashboard](https://dash.cloudflare.com/) → **Workers & Pages** → **创建应用** → **Pages** → 连接你的 Git 仓库（或直接上传 `frontend/dist`）。
2. 构建配置：
   - 构建命令：`npm run build`
   - 输出目录：`dist`
3. **环境变量** 添加：
   - `VITE_API_URL` = 你部署的 Workers 地址（例如 `https://music-api.xxx.workers.dev/api`）

> ⚠️ `VITE_API_URL` 必须包含 `/api` 前缀。例如 Workers 地址是 `https://music-api.abc.workers.dev`，则填 `https://music-api.abc.workers.dev/api`。

### 3. 绑定自定义域名（可选）

在 Workers 或 Pages 的 **自定义域** 里绑定你自己的域名即可。

## 五、API 一览

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| POST | `/api/auth/send-code` | 发送邮箱验证码（`purpose`=register/reset） | 公开 |
| POST | `/api/auth/register` | 注册（需 `code` 验证码） | 公开 |
| POST | `/api/auth/login` | 登录 | 公开 |
| POST | `/api/auth/forgot` | 忘记密码发送重置验证码 | 公开 |
| POST | `/api/auth/reset-password` | 用验证码重置密码 | 公开 |
| GET | `/api/auth/me` | 当前用户 | 登录 |
| POST | `/api/users/avatar` | 上传头像（multipart） | 登录 |
| GET | `/api/users/:id/avatar` | 获取头像 | 公开 |
| GET | `/api/songs` | 歌曲列表（`q`/`genre`/`page`/`limit`） | 公开 |
| GET | `/api/songs/genres` | 分类统计 | 公开 |
| GET | `/api/songs/:id` | 歌曲详情 | 公开 |
| GET | `/api/songs/:id/stream` | 流式播放（支持 Range） | 公开 |
| GET | `/api/songs/:id/lyrics` | LRC 歌词文本 | 公开 |
| GET | `/api/songs/:id/cover` | 封面图 | 公开 |
| POST | `/api/songs` | 上传（multipart） | 登录 |
| DELETE | `/api/songs/:id` | 删除 | 上传者 |
| POST/DELETE | `/api/songs/:id/favorite` | 收藏 / 取消 | 登录 |
| GET | `/api/songs/favorites` | 我的收藏 | 登录 |
| GET/POST | `/api/comments/...` | 评论列表 / 发表 | 评论发表需登录 |
| DELETE | `/api/comments/:id` | 删除评论 | 本人 |
| POST | `/api/playlists` | 创建歌单 | 登录 |
| GET | `/api/playlists/mine` | 我的歌单 | 登录 |
| GET | `/api/playlists/:id` | 歌单详情 | 公开 |
| POST/DELETE | `/api/playlists/:id/songs` | 添加 / 移除歌曲 | 歌单主人 |
| DELETE | `/api/playlists/:id` | 删除歌单 | 歌单主人 |

## 六、安全与限制

- 音频文件最大 **100MB**，封面最大 **10MB**，歌词 **512KB**（Workers 免费版请求体上限）。
- 头像最大 **5MB**。
- 密码使用 PBKDF2（12 万次迭代）哈希，JWT 有效期默认 7 天。
- 邮箱验证码有效期 **5 分钟**，使用一次后即失效。
- 生产环境务必通过 `wrangler secret put` 设置 `JWT_SECRET` 和 `RESEND_API_KEY`。
- 本地开发未配置 `RESEND_API_KEY` 时，验证码会打印在后端终端。
- R2 桶默认私有，音频/封面/头像均通过 Worker 代理返回，支持 Range 请求。
- 如需限制只有特定邮箱可注册，可在 `api/src/routes/auth.ts` 的 register 中自行加白名单。

## 七、常见问题

- **上传大文件失败**：Workers 免费版请求体限制 100MB，如需更大请升级付费计划或改用 R2 预签名直传。
- **前端 404**：确认 `VITE_API_URL` 填了完整地址且包含 `/api`。
- **歌词不同步**：确认歌词为 LRC 格式（`[mm:ss.xx] 文本`），编码建议 UTF-8。
