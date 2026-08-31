// ============================================================
// 歌曲路由：列表/搜索/详情/流播放/歌词/上传/收藏/删除
// ============================================================
import { Hono } from 'hono';
import { z } from 'zod';
import { zValidator } from '@hono/zod-validator';
import type { Env, AppVariables } from '../types';
import { authRequired, verifyJwt } from '../auth';
import { toPublicSong, getExtension, makeKey } from '../utils';
import { s3 } from '../storage';

const app = new Hono<{ Bindings: Env; Variables: AppVariables }>();

// ---------- 歌曲列表（分页 / 搜索 / 分类） ----------
app.get('/', async (c) => {
  const q = (c.req.query('q') || '').trim();
  const genre = (c.req.query('genre') || '').trim();
  const page = Math.max(1, Number(c.req.query('page') || 1));
  const limit = Math.min(50, Math.max(1, Number(c.req.query('limit') || 20)));
  const offset = (page - 1) * limit;

  let where = '1=1';
  const params: unknown[] = [];
  if (q) {
    where += ' AND (s.title LIKE ? OR s.artist LIKE ?)';
    params.push(`%${q}%`, `%${q}%`);
  }
  if (genre) {
    where += ' AND s.genre = ?';
    params.push(genre);
  }

  const { results } = await c.env.DB.prepare(
    `SELECT s.*, u.username AS uploader_name, u.title AS uploader_title
     FROM songs s JOIN users u ON s.uploader_id = u.id
     WHERE ${where} ORDER BY s.created_at DESC LIMIT ? OFFSET ?`
  )
    .bind(...params, limit, offset)
    .all();

  const countRow = await c.env.DB.prepare(
    `SELECT COUNT(*) AS total FROM songs s WHERE ${where}`
  )
    .bind(...params)
    .first<{ total: number }>();

  const songs = results.map((r) => toPublicSong(r as Record<string, unknown>));
  return c.json({ songs, total: Number(countRow?.total || 0), page, limit });
});

// ---------- 流派列表 ----------
app.get('/genres', async (c) => {
  const { results } = await c.env.DB.prepare(
    'SELECT genre, COUNT(*) AS count FROM songs GROUP BY genre ORDER BY count DESC'
  ).all();
  return c.json({ genres: results });
});

// ---------- 封面图（B2 私有桶，预签名 URL 307 重定向） ----------
app.get('/:id/cover', async (c) => {
  const id = Number(c.req.param('id'));
  const song = await c.env.DB.prepare('SELECT cover_key FROM songs WHERE id = ?').bind(id).first<{ cover_key: string | null }>();
  if (!song || !song.cover_key) return c.body(null, 204);

  return Response.redirect(await s3(c.env).getSignedUrl(song.cover_key), 307);
});

// ---------- 我的收藏列表（需登录） ----------
app.get('/favorites', authRequired, async (c) => {
  const user = c.get('user');
  const { results } = await c.env.DB.prepare(
    `SELECT s.*, u.username AS uploader_name, u.title AS uploader_title
     FROM favorites f
     JOIN songs s ON f.song_id = s.id
     JOIN users u ON s.uploader_id = u.id
     WHERE f.user_id = ? ORDER BY f.created_at DESC`
  )
    .bind(user.id)
    .all();
  const songs = results.map((r) => toPublicSong(r as Record<string, unknown>));
  return c.json({ songs });
});

// ---------- 歌曲详情 ----------
app.get('/:id', async (c) => {
  const id = Number(c.req.param('id'));
  if (!Number.isFinite(id)) return c.json({ error: '参数错误' }, 400);

  const row = await c.env.DB.prepare(
    `SELECT s.*, u.username AS uploader_name, u.title AS uploader_title
     FROM songs s JOIN users u ON s.uploader_id = u.id WHERE s.id = ?`
  )
    .bind(id)
    .first();
  if (!row) return c.json({ error: '歌曲不存在' }, 404);

  const favCount = await c.env.DB.prepare('SELECT COUNT(*) AS n FROM favorites WHERE song_id = ?').bind(id).first<{ n: number }>();

  // 当前用户（可选登录）是否已收藏
  let favorited = false;
  const authHeader = c.req.header('Authorization');
  if (authHeader?.startsWith('Bearer ')) {
    const payload = await verifyJwt(authHeader.slice(7), c.env.JWT_SECRET);
    if (payload && typeof payload.sub === 'number') {
      const fav = await c.env.DB.prepare('SELECT 1 FROM favorites WHERE user_id = ? AND song_id = ?')
        .bind(payload.sub, id)
        .first();
      favorited = !!fav;
    }
  }

  return c.json({
    song: toPublicSong(row as Record<string, unknown>),
    favoriteCount: Number(favCount?.n || 0),
    favorited,
  });
});

// ---------- 流式播放（B2 私有桶，预签名 URL 307 重定向，支持 Range 拖动进度） ----------
app.get('/:id/stream', async (c) => {
  const id = Number(c.req.param('id'));
  const song = await c.env.DB.prepare('SELECT audio_key FROM songs WHERE id = ?').bind(id).first<{ audio_key: string }>();
  if (!song) return c.json({ error: '歌曲不存在' }, 404);

  return Response.redirect(await s3(c.env).getSignedUrl(song.audio_key), 307);
});

// ---------- 歌词（返回纯文本，前端解析 LRC） ----------
app.get('/:id/lyrics', async (c) => {
  const id = Number(c.req.param('id'));
  const song = await c.env.DB.prepare('SELECT lyrics_key FROM songs WHERE id = ?').bind(id).first<{ lyrics_key: string | null }>();
  if (!song || !song.lyrics_key) return c.json({ error: '暂无歌词' }, 404);

  const text = await s3(c.env).getText(song.lyrics_key);
  if (text === null) return c.json({ error: '歌词文件不存在' }, 404);

  return new Response(text, { headers: { 'Content-Type': 'text/plain; charset=utf-8' } });
});

// ---------- 预签名上传：前端直传 OSS（避免 Worker 中转，上传更快） ----------
app.post('/presign', authRequired, async (c) => {
  const user = c.get('user');
  const body = await c.req.json();
  const title = String(body.title || '').trim();
  if (!title) return c.json({ error: '请填写歌曲标题' }, 400);

  const id = crypto.randomUUID();
  const audioKey = makeKey('songs', id, getExtension(String(body.audioName || 'audio.mp3')));
  const storage = s3(c.env);
  const audioUrl = await storage.getSignedPutUrl(audioKey, 900);

  let coverKey: string | null = null;
  let coverUrl: string | null = null;
  if (body.coverName) {
    coverKey = makeKey('covers', id, getExtension(String(body.coverName)));
    coverUrl = await storage.getSignedPutUrl(coverKey, 900);
  }
  let lyricsKey: string | null = null;
  let lyricsUrl: string | null = null;
  if (body.lyricsName) {
    lyricsKey = makeKey('lyrics', id, 'lrc');
    lyricsUrl = await storage.getSignedPutUrl(lyricsKey, 900);
  }

  return c.json({
    success: true,
    id,
    audioKey,
    audioUrl,
    coverKey,
    coverUrl,
    lyricsKey,
    lyricsUrl,
  });
});

// 前端直传完成后，登记歌曲到数据库
app.post('/complete', authRequired, async (c) => {
  const user = c.get('user');
  const body = await c.req.json();
  const title = String(body.title || '').trim();
  const audioKey = String(body.audioKey || '');
  if (!title) return c.json({ error: '请填写歌曲标题' }, 400);
  if (!audioKey.startsWith('songs/')) return c.json({ error: '非法音频 key' }, 400);

  const coverKey = body.coverKey ? String(body.coverKey) : null;
  const lyricsKey = body.lyricsKey ? String(body.lyricsKey) : null;
  const artist = String(body.artist || '').trim();
  const genre = String(body.genre || '其他').trim();
  const duration = Math.max(0, Math.floor(Number(body.duration) || 0));

  const res = await c.env.DB.prepare(
    'INSERT INTO songs (title, artist, genre, cover_key, audio_key, lyrics_key, duration, uploader_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)'
  )
    .bind(title, artist, genre, coverKey, audioKey, lyricsKey, duration, user.id)
    .run();

  return c.json({ success: true, id: Number(res.meta.last_row_id) }, 201);
});

// ---------- 上传歌曲（multipart，需登录） ----------
const MAX_AUDIO = 100 * 1024 * 1024; // 100MB
const MAX_COVER = 10 * 1024 * 1024;  // 10MB
const MAX_LYRICS = 512 * 1024;       // 512KB

app.post('/', authRequired, async (c) => {
  const user = c.get('user');
  const form = await c.req.formData();

  const title = String(form.get('title') || '').trim();
  const artist = String(form.get('artist') || '').trim();
  const genre = String(form.get('genre') || '其他').trim();
  const duration = Number(form.get('duration') || 0);
  const audio = form.get('audio');
  const cover = form.get('cover');
  const lyrics = form.get('lyrics');

  if (!title) return c.json({ error: '请填写歌曲标题' }, 400);
  if (!(audio instanceof File)) return c.json({ error: '请选择音频文件' }, 400);
  if (audio.size === 0) return c.json({ error: '音频文件为空' }, 400);
  if (audio.size > MAX_AUDIO) return c.json({ error: '音频文件不能超过 100MB' }, 400);

  const id = crypto.randomUUID();
  const audioKey = makeKey('songs', id, getExtension(audio.name || 'mp3'));
  await s3(c.env).put(audioKey, await audio.arrayBuffer(), audio.type || 'audio/mpeg');

  let coverKey: string | null = null;
  if (cover instanceof File && cover.size > 0) {
    if (cover.size > MAX_COVER) return c.json({ error: '封面图片不能超过 10MB' }, 400);
    coverKey = makeKey('covers', id, getExtension(cover.name || 'jpg'));
    await s3(c.env).put(coverKey, await cover.arrayBuffer(), cover.type || 'image/jpeg');
  }

  let lyricsKey: string | null = null;
  if (lyrics instanceof File && lyrics.size > 0) {
    if (lyrics.size > MAX_LYRICS) return c.json({ error: '歌词文件不能超过 512KB' }, 400);
    lyricsKey = makeKey('lyrics', id, 'lrc');
    await s3(c.env).put(lyricsKey, await lyrics.arrayBuffer(), 'text/plain; charset=utf-8');
  }

  const res = await c.env.DB.prepare(
    'INSERT INTO songs (title, artist, genre, cover_key, audio_key, lyrics_key, duration, uploader_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)'
  )
    .bind(title, artist, genre, coverKey, audioKey, lyricsKey, Math.max(0, Math.floor(duration)), user.id)
    .run();

  return c.json({ success: true, id: Number(res.meta.last_row_id) }, 201);
});

// ---------- 收藏歌曲 ----------
app.post('/:id/favorite', authRequired, async (c) => {
  const user = c.get('user');
  const id = Number(c.req.param('id'));
  const song = await c.env.DB.prepare('SELECT id FROM songs WHERE id = ?').bind(id).first();
  if (!song) return c.json({ error: '歌曲不存在' }, 404);

  await c.env.DB.prepare('INSERT OR IGNORE INTO favorites (user_id, song_id) VALUES (?, ?)').bind(user.id, id).run();
  const favCount = await c.env.DB.prepare('SELECT COUNT(*) AS n FROM favorites WHERE song_id = ?').bind(id).first<{ n: number }>();
  return c.json({ success: true, favorite: true, favoriteCount: Number(favCount?.n || 0) });
});

// ---------- 取消收藏 ----------
app.delete('/:id/favorite', authRequired, async (c) => {
  const user = c.get('user');
  const id = Number(c.req.param('id'));
  await c.env.DB.prepare('DELETE FROM favorites WHERE user_id = ? AND song_id = ?').bind(user.id, id).run();
  const favCount = await c.env.DB.prepare('SELECT COUNT(*) AS n FROM favorites WHERE song_id = ?').bind(id).first<{ n: number }>();
  return c.json({ success: true, favorite: false, favoriteCount: Number(favCount?.n || 0) });
});

// ---------- 删除歌曲（仅上传者） ----------
app.delete('/:id', authRequired, async (c) => {
  const user = c.get('user');
  const id = Number(c.req.param('id'));
  const song = await c.env.DB.prepare('SELECT * FROM songs WHERE id = ?').bind(id).first();
  if (!song) return c.json({ error: '歌曲不存在' }, 404);
  if (Number(song.uploader_id) !== user.id) return c.json({ error: '只能删除自己上传的歌曲' }, 403);

  // 清理 B2 对象
  const keys = [String(song.audio_key)];
  if (song.cover_key) keys.push(String(song.cover_key));
  if (song.lyrics_key) keys.push(String(song.lyrics_key));
  const storage = s3(c.env);
  for (const key of keys) {
    await storage.remove(key).catch(() => {});
  }

  await c.env.DB.prepare('DELETE FROM songs WHERE id = ?').bind(id).run();
  return c.json({ success: true });
});

export default app;
