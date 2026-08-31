// ============================================================
// 认证模块：JWT 签发/校验、密码哈希（Web Crypto，无需额外依赖）
// ============================================================
import { createMiddleware } from 'hono/factory';
import type { Context } from 'hono';
import type { Env, User, AppVariables } from './types';

const encoder = new TextEncoder();
const decoder = new TextDecoder();

/** Base64URL 编码（兼容 Unicode） */
export function base64UrlEncode(data: Uint8Array | string): string {
  const bytes = typeof data === 'string' ? encoder.encode(data) : data;
  let binary = '';
  bytes.forEach((b) => (binary += String.fromCharCode(b)));
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

/** Base64URL 解码 */
export function base64UrlDecode(str: string): Uint8Array<ArrayBuffer> {
  const padded = str.replace(/-/g, '+').replace(/_/g, '/');
  const binary = atob(padded);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return bytes;
}

/** 生成随机盐 */
export function randomSalt(): string {
  return base64UrlEncode(crypto.getRandomValues(new Uint8Array(16)));
}

/** PBKDF2 密码哈希 */
export async function hashPassword(password: string, salt: string): Promise<string> {
  const key = await crypto.subtle.importKey(
    'raw',
    encoder.encode(password),
    { name: 'PBKDF2', hash: 'SHA-256' },
    false,
    ['deriveBits']
  );
  const bits = await crypto.subtle.deriveBits(
    { name: 'PBKDF2', salt: encoder.encode(salt), iterations: 60000, hash: 'SHA-256' },
    key,
    256
  );
  return base64UrlEncode(new Uint8Array(bits));
}

/** 签发 JWT（HS256） */
export async function signJwt(
  payload: Record<string, unknown>,
  secret: string,
  expiresInSec: number
): Promise<string> {
  const header = { alg: 'HS256', typ: 'JWT' };
  const now = Math.floor(Date.now() / 1000);
  const body = { ...payload, iat: now, exp: now + expiresInSec };
  const data = `${base64UrlEncode(JSON.stringify(header))}.${base64UrlEncode(JSON.stringify(body))}`;
  const key = await crypto.subtle.importKey('raw', encoder.encode(secret), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']);
  const sig = await crypto.subtle.sign('HMAC', key, encoder.encode(data));
  return `${data}.${base64UrlEncode(new Uint8Array(sig))}`;
}

/** 校验 JWT，成功返回 payload，失败返回 null */
export async function verifyJwt(token: string, secret: string): Promise<Record<string, unknown> | null> {
  const parts = token.split('.');
  if (parts.length !== 3) return null;
  const data = `${parts[0]}.${parts[1]}`;
  const key = await crypto.subtle.importKey('raw', encoder.encode(secret), { name: 'HMAC', hash: 'SHA-256' }, false, ['verify']);
  const sigBytes = base64UrlDecode(parts[2]);
  const valid = await crypto.subtle.verify('HMAC', key, sigBytes, encoder.encode(data));
  if (!valid) return null;
  try {
    const payload = JSON.parse(decoder.decode(base64UrlDecode(parts[1]))) as Record<string, unknown>;
    if (typeof payload.exp === 'number' && payload.exp < Math.floor(Date.now() / 1000)) return null;
    return payload;
  } catch {
    return null;
  }
}

/** 从请求中解析当前用户（无 token 返回 null） */
export async function getAuthUser<T extends { Bindings: Env }>(c: Context<T>, env: Env): Promise<User | null> {
  const header = c.req.header('Authorization');
  if (!header?.startsWith('Bearer ')) return null;
  const payload = await verifyJwt(header.slice(7), env.JWT_SECRET);
  if (!payload || typeof payload.sub !== 'number') return null;
  const user = await env.DB.prepare('SELECT id, email, username, avatar_key, created_at FROM users WHERE id = ?')
    .bind(payload.sub)
    .first<User>();
  return user ?? null;
}

/** 登录鉴权中间件：未登录返回 401 */
export const authRequired = createMiddleware<{ Bindings: Env; Variables: AppVariables }>(async (c, next) => {
  const user = await getAuthUser(c, c.env);
  if (!user) return c.json({ error: '请先登录' }, 401);
  c.set('user', user);
  await next();
});
