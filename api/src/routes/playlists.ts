// ============================================================
// 播放列表路由
// ============================================================
import { Hono } from 'hono';
import { z } from 'zod';
import { zValidator } from '@hono/zod-validator';
import type { Env, AppVariables } from '../types';
import { authRequired } from '../auth';
import { toPublicSong } from '../utils';

const app = new Hono<{ Bindings: Env; Variables: AppVariables }>();

const createSchema = z.object({
  name: z.string().min(1, '请输入播放列表名称').max(50),
});

// ---------- 创建播放列表 ----------
app.post('/', authRequired, zValidator('json', createSchema), async (c) => {
  const user = c.get('user');
  const { name } = c.req.valid('json');
  const res = await c.env.DB.prepare('INSERT INTO playlists (name, user_id) VALUES (?, ?)')
    .bind(name.trim(), user.id)
    .run();
  return c.json({ success: true, id: Number(res.meta.last_row_id) }, 201);
});

// ---------- 我的播放列表 ----------
app.get('/mine', authRequired, async (c) => {
  const user = c.get('user');
  const { results } = await c.env.DB.prepare(
    `SELECT p.*, (SELECT COUNT(*) FROM playlist_songs ps WHERE ps.playlist_id = p.id) AS song_count
     FROM playlists p WHERE p.user_id = ? ORDER BY p.created_at DESC`
  )
    .bind(user.id)
    .all();
  return c.json({ playlists: results });
});

// ---------- 播放列表详情（含歌曲列表） ----------
app.get('/:id', async (c) => {
  const id = Number(c.req.param('id'));
  const playlist = await c.env.DB.prepare(
    'SELECT p.*, u.username AS owner_name FROM playlists p JOIN users u ON p.user_id = u.id WHERE p.id = ?'
  )
    .bind(id)
    .first();
  if (!playlist) return c.json({ error: '播放列表不存在' }, 404);

  const { results } = await c.env.DB.prepare(
    `SELECT s.*, u.username AS uploader_name
     FROM playlist_songs ps
     JOIN songs s ON ps.song_id = s.id
     JOIN users u ON s.uploader_id = u.id
     WHERE ps.playlist_id = ? ORDER BY ps.position ASC, ps.added_at ASC`
  )
    .bind(id)
    .all();

  return c.json({
    playlist: { ...playlist, songCount: results.length },
    songs: results.map((r) => toPublicSong(r as Record<string, unknown>)),
  });
});

// ---------- 向播放列表添加歌曲 ----------
const addSchema = z.object({ songId: z.number().int().positive() });

app.post('/:id/songs', authRequired, zValidator('json', addSchema), async (c) => {
  const user = c.get('user');
  const id = Number(c.req.param('id'));
  const { songId } = c.req.valid('json');

  const playlist = await c.env.DB.prepare('SELECT * FROM playlists WHERE id = ?').bind(id).first();
  if (!playlist) return c.json({ error: '播放列表不存在' }, 404);
  if (Number(playlist.user_id) !== user.id) return c.json({ error: '无权修改该播放列表' }, 403);

  const song = await c.env.DB.prepare('SELECT id FROM songs WHERE id = ?').bind(songId).first();
  if (!song) return c.json({ error: '歌曲不存在' }, 404);

  const countRow = await c.env.DB.prepare(
    'SELECT COUNT(*) AS n FROM playlist_songs WHERE playlist_id = ?'
  )
    .bind(id)
    .first<{ n: number }>();

  await c.env.DB.prepare(
    'INSERT OR IGNORE INTO playlist_songs (playlist_id, song_id, position) VALUES (?, ?, ?)'
  )
    .bind(id, songId, Number(countRow?.n || 0))
    .run();

  return c.json({ success: true });
});

// ---------- 从播放列表移除歌曲 ----------
app.delete('/:id/songs/:songId', authRequired, async (c) => {
  const user = c.get('user');
  const id = Number(c.req.param('id'));
  const songId = Number(c.req.param('songId'));

  const playlist = await c.env.DB.prepare('SELECT * FROM playlists WHERE id = ?').bind(id).first();
  if (!playlist) return c.json({ error: '播放列表不存在' }, 404);
  if (Number(playlist.user_id) !== user.id) return c.json({ error: '无权修改该播放列表' }, 403);

  await c.env.DB.prepare('DELETE FROM playlist_songs WHERE playlist_id = ? AND song_id = ?')
    .bind(id, songId)
    .run();
  return c.json({ success: true });
});

// ---------- 删除播放列表 ----------
app.delete('/:id', authRequired, async (c) => {
  const user = c.get('user');
  const id = Number(c.req.param('id'));
  const playlist = await c.env.DB.prepare('SELECT * FROM playlists WHERE id = ?').bind(id).first();
  if (!playlist) return c.json({ error: '播放列表不存在' }, 404);
  if (Number(playlist.user_id) !== user.id) return c.json({ error: '无权删除该播放列表' }, 403);

  await c.env.DB.prepare('DELETE FROM playlists WHERE id = ?').bind(id).run();
  return c.json({ success: true });
});

export default app;
