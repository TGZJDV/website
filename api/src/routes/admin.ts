// ============================================================
// 管理员路由：用户管理（列表 / 封禁 / 头衔 / 用户名 / 删除）
// 所有端点需登录且是管理员（authRequired + requireAdmin）
// ============================================================
import { Hono } from 'hono';
import { z } from 'zod';
import { zValidator } from '@hono/zod-validator';
import type { Env, AppVariables } from '../types';
import { authRequired, requireAdmin } from '../auth';
import { s3 } from '../storage';

const app = new Hono<{ Bindings: Env; Variables: AppVariables }>();

/** 查询用户详情字段（供列表/更新返回复用） */
const USER_COLS = 'id, email, username, avatar_key, title, is_admin, banned, created_at';

// ---------- 用户列表 ----------
app.get('/users', authRequired, requireAdmin, async (c) => {
  const { results } = await c.env.DB.prepare(
    `SELECT u.id, u.email, u.username, u.avatar_key, u.title, u.is_admin, u.banned, u.created_at,
            (SELECT COUNT(*) FROM songs s WHERE s.uploader_id = u.id) AS songs_count
     FROM users u ORDER BY u.id ASC`
  ).all();
  return c.json({ users: results });
});

// ---------- 更新用户（封禁 / 头衔 / 用户名 / 管理员） ----------
const updateSchema = z.object({
  username: z.string().min(2, '用户名至少 2 个字符').max(30, '用户名最多 30 个字符').optional(),
  title: z.string().max(50, '头衔最多 50 个字符').nullable().optional(),
  is_admin: z.union([z.literal(0), z.literal(1)]).optional(),
  banned: z.union([z.literal(0), z.literal(1)]).optional(),
});

app.patch('/users/:id', authRequired, requireAdmin, zValidator('json', updateSchema), async (c) => {
  const admin = c.get('user');
  const id = Number(c.req.param('id'));
  const body = c.req.valid('json');

  const target = await c.env.DB.prepare('SELECT * FROM users WHERE id = ?').bind(id).first();
  if (!target) return c.json({ error: '用户不存在' }, 404);

  // 自我保护：不能封禁自己、不能取消自己的管理员身份
  if (id === admin.id) {
    if (body.banned === 1) return c.json({ error: '不能封禁自己' }, 400);
    if (body.is_admin === 0) return c.json({ error: '不能取消自己的管理员身份' }, 400);
  }

  // 用户名唯一性校验
  if (body.username && body.username !== String(target.username)) {
    const dup = await c.env.DB.prepare('SELECT id FROM users WHERE username = ? AND id != ?')
      .bind(body.username, id)
      .first();
    if (dup) return c.json({ error: '该用户名已被占用' }, 409);
  }

  const sets: string[] = [];
  const params: unknown[] = [];
  if (body.username !== undefined) {
    sets.push('username = ?');
    params.push(body.username);
  }
  if (body.title !== undefined) {
    sets.push('title = ?');
    params.push(body.title === null ? null : body.title);
  }
  if (body.is_admin !== undefined) {
    sets.push('is_admin = ?');
    params.push(body.is_admin);
  }
  if (body.banned !== undefined) {
    sets.push('banned = ?');
    params.push(body.banned);
  }
  if (sets.length === 0) return c.json({ error: '没有要更新的字段' }, 400);

  await c.env.DB.prepare(`UPDATE users SET ${sets.join(', ')} WHERE id = ?`)
    .bind(...params, id)
    .run();

  const updated = await c.env.DB.prepare(`SELECT ${USER_COLS} FROM users WHERE id = ?`).bind(id).first();
  return c.json({ success: true, user: updated });
});

// ---------- 删除用户（清理 OSS 对象 + 级联删除其歌曲/歌单/收藏/评论） ----------
app.delete('/users/:id', authRequired, requireAdmin, async (c) => {
  const admin = c.get('user');
  const id = Number(c.req.param('id'));
  if (id === admin.id) return c.json({ error: '不能删除自己的账号' }, 400);

  const target = await c.env.DB.prepare('SELECT * FROM users WHERE id = ?').bind(id).first();
  if (!target) return c.json({ error: '用户不存在' }, 404);

  // 清理该用户的所有 OSS 对象（歌曲音频/封面/歌词 + 头像）
  const { results: songs } = await c.env.DB.prepare(
    'SELECT audio_key, cover_key, lyrics_key FROM songs WHERE uploader_id = ?'
  )
    .bind(id)
    .all();
  const avatar = await c.env.DB.prepare('SELECT avatar_key FROM users WHERE id = ?')
    .bind(id)
    .first<{ avatar_key: string | null }>();

  const storage = s3(c.env);
  for (const song of songs) {
    const keys = [String(song.audio_key)];
    if (song.cover_key) keys.push(String(song.cover_key));
    if (song.lyrics_key) keys.push(String(song.lyrics_key));
    for (const key of keys) await storage.remove(key).catch(() => {});
  }
  if (avatar?.avatar_key) await storage.remove(String(avatar.avatar_key)).catch(() => {});

  // 级联删除：songs/playlists/favorites/comments 均 ON DELETE CASCADE
  await c.env.DB.prepare('DELETE FROM users WHERE id = ?').bind(id).run();
  return c.json({ success: true });
});

export default app;
