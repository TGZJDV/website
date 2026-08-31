// ============================================================
// Workers 入口：Hono 应用
// ============================================================
import { Hono } from 'hono';
import { cors } from 'hono/cors';
import type { Env } from './types';
import authRoutes from './routes/auth';
import userRoutes from './routes/users';
import songRoutes from './routes/songs';
import playlistRoutes from './routes/playlists';
import commentRoutes from './routes/comments';
import adminRoutes from './routes/admin';

const app = new Hono<{ Bindings: Env }>();

// 跨域（前端部署在 Pages 时配置允许的源）
app.use('*', cors());

// 健康检查
app.get('/api/health', (c) => c.json({ ok: true, time: new Date().toISOString() }));

// 挂载路由
app.route('/api/auth', authRoutes);
app.route('/api/users', userRoutes);
app.route('/api/songs', songRoutes);
app.route('/api/playlists', playlistRoutes);
app.route('/api/comments', commentRoutes);
app.route('/api/admin', adminRoutes);

// 404
app.notFound((c) => c.json({ error: '接口不存在' }, 404));

// 错误处理
app.onError((err, c) => {
  console.error('API error:', err);
  return c.json({ error: '服务器内部错误' }, 500);
});

export default app;
