-- ============================================================
-- 音乐网站数据库 Schema（Cloudflare D1 / SQLite）
-- 创建命令：
--   本地:  wrangler d1 execute music-db --local --file=../schema.sql
--   线上:  wrangler d1 execute music-db --remote --file=../schema.sql
-- ============================================================

-- 用户表
CREATE TABLE IF NOT EXISTS users (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  email         TEXT    NOT NULL UNIQUE,
  username      TEXT    NOT NULL UNIQUE,
  password_hash TEXT    NOT NULL,
  password_salt TEXT    NOT NULL,
  avatar_key    TEXT,                    -- B2 头像 key
  created_at    TEXT    NOT NULL DEFAULT (datetime('now'))
);

-- 邮箱验证码表（注册验证 / 忘记密码）
CREATE TABLE IF NOT EXISTS email_codes (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  email      TEXT    NOT NULL,
  code       TEXT    NOT NULL,
  purpose    TEXT    NOT NULL,           -- 'register' | 'reset'
  expires_at TEXT    NOT NULL,
  created_at TEXT    NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_email_codes_email ON email_codes(email);
CREATE INDEX IF NOT EXISTS idx_email_codes_email_purpose ON email_codes(email, purpose);

-- 歌曲表
CREATE TABLE IF NOT EXISTS songs (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  title       TEXT    NOT NULL,
  artist      TEXT    NOT NULL DEFAULT '',
  genre       TEXT    NOT NULL DEFAULT '其他',
  cover_key   TEXT,                    -- B2 封面图 key
  audio_key   TEXT    NOT NULL,        -- B2 音频文件 key
  lyrics_key  TEXT,                    -- B2 歌词文件 key (LRC)
  duration    INTEGER NOT NULL DEFAULT 0,  -- 秒
  uploader_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at  TEXT    NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_songs_title   ON songs(title);
CREATE INDEX IF NOT EXISTS idx_songs_genre   ON songs(genre);
CREATE INDEX IF NOT EXISTS idx_songs_uploader ON songs(uploader_id);

-- 播放列表表
CREATE TABLE IF NOT EXISTS playlists (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  name       TEXT    NOT NULL,
  user_id    INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at TEXT    NOT NULL DEFAULT (datetime('now'))
);

-- 播放列表 - 歌曲关联
CREATE TABLE IF NOT EXISTS playlist_songs (
  playlist_id INTEGER NOT NULL REFERENCES playlists(id) ON DELETE CASCADE,
  song_id     INTEGER NOT NULL REFERENCES songs(id) ON DELETE CASCADE,
  position    INTEGER NOT NULL DEFAULT 0,
  added_at    TEXT    NOT NULL DEFAULT (datetime('now')),
  PRIMARY KEY (playlist_id, song_id)
);

-- 收藏表
CREATE TABLE IF NOT EXISTS favorites (
  user_id    INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  song_id    INTEGER NOT NULL REFERENCES songs(id) ON DELETE CASCADE,
  created_at TEXT    NOT NULL DEFAULT (datetime('now')),
  PRIMARY KEY (user_id, song_id)
);

CREATE INDEX IF NOT EXISTS idx_favorites_song ON favorites(song_id);

-- 评论表
CREATE TABLE IF NOT EXISTS comments (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  song_id    INTEGER NOT NULL REFERENCES songs(id) ON DELETE CASCADE,
  user_id    INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  content    TEXT    NOT NULL,
  created_at TEXT    NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_comments_song ON comments(song_id);
