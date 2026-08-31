// ============================================================
// 通用工具函数
// ============================================================
import type { Song } from './types';

/** 将歌曲行数据映射为返回给前端的结构（去掉内部 key 路径，改为公开 URL） */
export function toPublicSong(row: Record<string, unknown>): Song {
  return {
    id: Number(row.id),
    title: String(row.title),
    artist: String(row.artist),
    genre: String(row.genre),
    cover_key: row.cover_key ? String(row.cover_key) : null,
    audio_key: String(row.audio_key),
    lyrics_key: row.lyrics_key ? String(row.lyrics_key) : null,
    duration: Number(row.duration),
    uploader_id: Number(row.uploader_id),
    uploader_name: row.uploader_name ? String(row.uploader_name) : undefined,
    uploader_title: row.uploader_title ? String(row.uploader_title) : null,
    created_at: String(row.created_at),
  };
}

/** 从上传文件名推断扩展名 */
export function getExtension(filename: string): string {
  const idx = filename.lastIndexOf('.');
  if (idx === -1) return 'bin';
  const ext = filename.slice(idx + 1).toLowerCase();
  return ext || 'bin';
}

/** 生成 B2 对象 key */
export function makeKey(prefix: string, id: string, ext: string): string {
  return `${prefix}/${id}.${ext}`;
}

/** 生成数字验证码 */
export function generateCode(length = 6): string {
  const chars = '0123456789';
  let code = '';
  for (let i = 0; i < length; i++) {
    code += chars[Math.floor(Math.random() * chars.length)];
  }
  return code;
}
