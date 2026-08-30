// ============================================================
// 认证路由：发送验证码 / 注册 / 登录 / 当前用户 / 忘记密码
// ============================================================
import { Hono } from 'hono';
import { z } from 'zod';
import { zValidator } from '@hono/zod-validator';
import type { Env, AppVariables } from '../types';
import { hashPassword, randomSalt, signJwt, authRequired } from '../auth';
import { generateCode } from '../utils';
import { sendVerificationEmail } from '../email';

const app = new Hono<{ Bindings: Env; Variables: AppVariables }>();

const CODE_TTL = 5 * 60; // 验证码有效期（秒）

const sendCodeSchema = z.object({
  email: z.string().email('邮箱格式不正确'),
  purpose: z.enum(['register', 'reset']),
});

const registerSchema = z.object({
  email: z.string().email('邮箱格式不正确'),
  username: z.string().min(2, '用户名至少 2 个字符').max(30, '用户名最多 30 个字符'),
  password: z.string().min(6, '密码至少 6 位').max(100),
  code: z.string().length(6, '验证码为 6 位数字'),
});

const loginSchema = z.object({
  email: z.string().email('邮箱格式不正确'),
  password: z.string().min(1, '请输入密码'),
});

const forgotSchema = z.object({
  email: z.string().email('邮箱格式不正确'),
});

const resetSchema = z.object({
  email: z.string().email('邮箱格式不正确'),
  code: z.string().length(6, '验证码为 6 位数字'),
  newPassword: z.string().min(6, '密码至少 6 位').max(100),
});

/** 校验并消费验证码（成功返回 true） */
async function verifyCode(
  db: D1Database,
  email: string,
  code: string,
  purpose: 'register' | 'reset'
): Promise<boolean> {
  const row = await db
    .prepare(
      "SELECT id FROM email_codes WHERE email = ? AND code = ? AND purpose = ? AND expires_at > strftime('%s','now') ORDER BY created_at DESC LIMIT 1"
    )
    .bind(email, code, purpose)
    .first();
  if (!row) return false;
  await db.prepare('DELETE FROM email_codes WHERE email = ? AND purpose = ?').bind(email, purpose).run();
  return true;
}

/** 发送邮箱验证码（注册 / 忘记密码） */
app.post('/send-code', zValidator('json', sendCodeSchema), async (c) => {
  const { email, purpose } = c.req.valid('json');
  const normalized = email.toLowerCase().trim();

  if (purpose === 'register') {
    const existing = await c.env.DB.prepare('SELECT id FROM users WHERE email = ?').bind(normalized).first();
    if (existing) return c.json({ error: '该邮箱已注册，请直接登录' }, 409);
  } else {
    const existing = await c.env.DB.prepare('SELECT id FROM users WHERE email = ?').bind(normalized).first();
    if (!existing) return c.json({ error: '该邮箱尚未注册' }, 404);
  }

  const code = generateCode();
  const expiresAt = Math.floor(Date.now() / 1000) + CODE_TTL;
  await c.env.DB.prepare('DELETE FROM email_codes WHERE email = ? AND purpose = ?').bind(normalized, purpose).run();
  await c.env.DB.prepare('INSERT INTO email_codes (email, code, purpose, expires_at) VALUES (?, ?, ?, ?)')
    .bind(normalized, code, purpose, expiresAt)
    .run();
  await sendVerificationEmail(c.env, normalized, code, purpose);

  return c.json({ success: true, message: '验证码已发送' });
});

/** 注册（需邮箱验证码） */
app.post('/register', zValidator('json', registerSchema), async (c) => {
  const { email, username, password, code } = c.req.valid('json');
  const normalizedEmail = email.toLowerCase().trim();

  const ok = await verifyCode(c.env.DB, normalizedEmail, code, 'register');
  if (!ok) return c.json({ error: '验证码错误或已过期' }, 400);

  const existing = await c.env.DB.prepare('SELECT id FROM users WHERE email = ? OR username = ?')
    .bind(normalizedEmail, username)
    .first();
  if (existing) return c.json({ error: '邮箱或用户名已被注册' }, 409);

  const salt = randomSalt();
  const passwordHash = await hashPassword(password, salt);
  const res = await c.env.DB.prepare(
    'INSERT INTO users (email, username, password_hash, password_salt) VALUES (?, ?, ?, ?)'
  )
    .bind(normalizedEmail, username, passwordHash, salt)
    .run();

  const id = Number(res.meta.last_row_id);
  const expiresIn = Number(c.env.JWT_EXPIRES || 604800);
  const token = await signJwt({ sub: id, email: normalizedEmail, username }, c.env.JWT_SECRET, expiresIn);

  return c.json({ token, user: { id, email: normalizedEmail, username, avatar_key: null } }, 201);
});

/** 登录 */
app.post('/login', zValidator('json', loginSchema), async (c) => {
  const { email, password } = c.req.valid('json');
  const normalizedEmail = email.toLowerCase().trim();

  const row = await c.env.DB.prepare('SELECT * FROM users WHERE email = ?').bind(normalizedEmail).first();
  if (!row) return c.json({ error: '邮箱或密码错误' }, 401);

  const hash = await hashPassword(password, String(row.password_salt));
  if (hash !== String(row.password_hash)) return c.json({ error: '邮箱或密码错误' }, 401);

  const expiresIn = Number(c.env.JWT_EXPIRES || 604800);
  const token = await signJwt(
    { sub: Number(row.id), email: String(row.email), username: String(row.username) },
    c.env.JWT_SECRET,
    expiresIn
  );

  return c.json({
    token,
    user: {
      id: Number(row.id),
      email: String(row.email),
      username: String(row.username),
      avatar_key: row.avatar_key ? String(row.avatar_key) : null,
    },
  });
});

/** 当前登录用户 */
app.get('/me', authRequired, async (c) => {
  return c.json({ user: c.get('user') });
});

/** 忘记密码：发送重置验证码 */
app.post('/forgot', zValidator('json', forgotSchema), async (c) => {
  const { email } = c.req.valid('json');
  const normalized = email.toLowerCase().trim();
  const existing = await c.env.DB.prepare('SELECT id FROM users WHERE email = ?').bind(normalized).first();
  if (!existing) return c.json({ error: '该邮箱尚未注册' }, 404);

  const code = generateCode();
  const expiresAt = Math.floor(Date.now() / 1000) + CODE_TTL;
  await c.env.DB.prepare("DELETE FROM email_codes WHERE email = ? AND purpose = 'reset'").bind(normalized).run();
  await c.env.DB.prepare("INSERT INTO email_codes (email, code, purpose, expires_at) VALUES (?, ?, 'reset', ?)")
    .bind(normalized, code, expiresAt)
    .run();
  await sendVerificationEmail(c.env, normalized, code, 'reset');

  return c.json({ success: true, message: '重置验证码已发送' });
});

/** 重置密码 */
app.post('/reset-password', zValidator('json', resetSchema), async (c) => {
  const { email, code, newPassword } = c.req.valid('json');
  const normalized = email.toLowerCase().trim();

  const ok = await verifyCode(c.env.DB, normalized, code, 'reset');
  if (!ok) return c.json({ error: '验证码错误或已过期' }, 400);

  const salt = randomSalt();
  const passwordHash = await hashPassword(newPassword, salt);
  await c.env.DB.prepare('UPDATE users SET password_hash = ?, password_salt = ? WHERE email = ?')
    .bind(passwordHash, salt, normalized)
    .run();

  return c.json({ success: true, message: '密码已重置，请使用新密码登录' });
});

export default app;
