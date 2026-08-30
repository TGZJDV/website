-- ============================================================
-- 迁移：为已存在的数据库添加头像字段和验证码表
-- 执行：wrangler d1 execute music-db --local --file=./migration-avatar-code.sql
-- ============================================================

ALTER TABLE users ADD COLUMN avatar_key TEXT;

CREATE TABLE IF NOT EXISTS email_codes (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  email      TEXT    NOT NULL,
  code       TEXT    NOT NULL,
  purpose    TEXT    NOT NULL,
  expires_at TEXT    NOT NULL,
  created_at TEXT    NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_email_codes_email ON email_codes(email);
CREATE INDEX IF NOT EXISTS idx_email_codes_email_purpose ON email_codes(email, purpose);
