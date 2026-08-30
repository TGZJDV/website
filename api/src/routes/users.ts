// ============================================================
// 用户路由：头像上传 / 获取
// ============================================================
import { Hono } from 'hono';
import type { Env, AppVariables } from '../types';
import { authRequired } from '../auth';
import { getExtension, makeKey } from '../utils';
import { b2 } from '../storage';

const app = new Hono<{ Bindings: Env; Variables: AppVariables }>();

const MAX_AVATAR = 5 * 1024 * 1024; // 5MB

/** 上传头像（multipart，需登录） */
app.post('/avatar', authRequired, async (c) => {
  const user = c.get('user');
  const form = await c.req.formData();
  const avatar = form.get('avatar');

  if (!(avatar instanceof File) || avatar.size === 0) {
    return c.json({ error: '请选择头像图片' }, 400);
  }
  if (avatar.size > MAX_AVATAR) {
    return c.json({ error: '头像图片不能超过 5MB' }, 400);
  }

  const key = makeKey('avatars', String(user.id), getExtension(avatar.name || 'png'));
  await b2(c.env).put(key, await avatar.arrayBuffer(), avatar.type || 'image/png');
  await c.env.DB.prepare('UPDATE users SET avatar_key = ? WHERE id = ?').bind(key, user.id).run();

  return c.json({ success: true, avatar_key: key });
});

/** 获取头像（B2 公开桶，302 重定向） */
app.get('/:id/avatar', async (c) => {
  const id = Number(c.req.param('id'));
  const user = await c.env.DB.prepare('SELECT avatar_key FROM users WHERE id = ?').bind(id).first<{
    avatar_key: string | null;
  }>();
  if (!user || !user.avatar_key) return c.body(null, 204);

  return Response.redirect(b2(c.env).publicUrl(user.avatar_key), 307);
});

export default app;
