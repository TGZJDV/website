// ============================================================
// 评论路由
// ============================================================
import { Hono } from 'hono';
import { z } from 'zod';
import { zValidator } from '@hono/zod-validator';
import type { Env, AppVariables } from '../types';
import { authRequired } from '../auth';

const app = new Hono<{ Bindings: Env; Variables: AppVariables }>();

// ---------- 获取歌曲评论列表 ----------
app.get('/song/:songId', async (c) => {
  const songId = Number(c.req.param('songId'));
  const { results } = await c.env.DB.prepare(
    `SELECT c.id, c.song_id, c.content, c.created_at, u.id AS user_id, u.username
     FROM comments c JOIN users u ON c.user_id = u.id
     WHERE c.song_id = ? ORDER BY c.created_at ASC`
  )
    .bind(songId)
    .all();
  return c.json({ comments: results });
});

// ---------- 发表评论 ----------
const createSchema = z.object({
  songId: z.number().int().positive(),
  content: z.string().min(1, '评论不能为空').max(500, '评论最多 500 字'),
});

app.post('/', authRequired, zValidator('json', createSchema), async (c) => {
  const user = c.get('user');
  const { songId, content } = c.req.valid('json');

  const song = await c.env.DB.prepare('SELECT id FROM songs WHERE id = ?').bind(songId).first();
  if (!song) return c.json({ error: '歌曲不存在' }, 404);

  const res = await c.env.DB.prepare('INSERT INTO comments (song_id, user_id, content) VALUES (?, ?, ?)')
    .bind(songId, user.id, content.trim())
    .run();

  const comment = await c.env.DB.prepare(
    `SELECT c.id, c.song_id, c.content, c.created_at, u.id AS user_id, u.username
     FROM comments c JOIN users u ON c.user_id = u.id WHERE c.id = ?`
  )
    .bind(Number(res.meta.last_row_id))
    .first();

  return c.json({ success: true, comment }, 201);
});

// ---------- 删除评论（本人） ----------
app.delete('/:id', authRequired, async (c) => {
  const user = c.get('user');
  const id = Number(c.req.param('id'));
  const comment = await c.env.DB.prepare('SELECT * FROM comments WHERE id = ?').bind(id).first();
  if (!comment) return c.json({ error: '评论不存在' }, 404);
  if (Number(comment.user_id) !== user.id) return c.json({ error: '只能删除自己的评论' }, 403);

  await c.env.DB.prepare('DELETE FROM comments WHERE id = ?').bind(id).run();
  return c.json({ success: true });
});

export default app;
