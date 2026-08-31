import type { Song, SongListResponse, Playlist, Comment, GenreCount, User, AdminUser } from './types';

// 后端 API 基础地址：可通过环境变量覆盖
export const API_URL = (import.meta.env.VITE_API_URL as string) || '/api';

const TOKEN_KEY = 'music_token';

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string | null) {
  if (token) localStorage.setItem(TOKEN_KEY, token);
  else localStorage.removeItem(TOKEN_KEY);
}

class ApiError extends Error {
  status: number;
  constructor(message: string, status: number) {
    super(message);
    this.status = status;
  }
}

/** 通用请求封装（自动附带 token、处理 JSON） */
async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers: Record<string, string> = {};
  const token = getToken();
  if (token) headers['Authorization'] = `Bearer ${token}`;
  if (options.body && !(options.body instanceof FormData)) {
    headers['Content-Type'] = 'application/json';
  }

  let res: Response;
  try {
    res = await fetch(`${API_URL}${path}`, { ...options, headers });
  } catch {
    throw new ApiError('无法连接服务器，请确认后端已启动', 0);
  }
  if (!res.ok) {
    const data = await res.json().catch(() => ({}));
    throw new ApiError(data.error || `请求失败 (${res.status})`, res.status);
  }
  return res.json() as Promise<T>;
}

/** 文本响应请求（用于歌词等 text/plain 端点） */
async function requestText(path: string): Promise<string> {
  const headers: Record<string, string> = {};
  const token = getToken();
  if (token) headers['Authorization'] = `Bearer ${token}`;

  let res: Response;
  try {
    res = await fetch(`${API_URL}${path}`, { headers });
  } catch {
    throw new ApiError('无法连接服务器，请确认后端已启动', 0);
  }
  if (!res.ok) {
    const data = await res.json().catch(() => ({}));
    throw new ApiError(data.error || `请求失败 (${res.status})`, res.status);
  }
  return res.text();
}

/** 音频流地址 */
export function streamUrl(songId: number): string {
  return `${API_URL}/songs/${songId}/stream`;
}

/** 直传文件到预签名 URL（浏览器 PUT 到 OSS，绕过 Worker 中转） */
export async function uploadToPresigned(url: string, file: File | Blob): Promise<void> {
  let res: Response;
  try {
    res = await fetch(url, { method: 'PUT', body: file });
  } catch {
    throw new ApiError('无法连接存储服务，请检查网络', 0);
  }
  if (!res.ok) {
    throw new ApiError(`上传到存储失败(${res.status})`, res.status);
  }
}

/** 用户头像地址（带版本参数，上传新头像后立即生效） */
export function avatarUrl(user: { id: number; avatar_key?: string | null }): string {
  const v = user.avatar_key ? encodeURIComponent(user.avatar_key) : 'default';
  return `${API_URL}/users/${user.id}/avatar?v=${v}`;
}

/** 封面图地址（无封面时返回 null） */
export function coverUrl(song: Pick<Song, 'cover_key' | 'id'>): string | null {
  if (!song.cover_key) return null;
  // 通过 API 代理获取封面（Worker 307 重定向到 B2 公开 URL）
  return `${API_URL}/songs/${song.id}/cover`;
}

// ---------- 认证 ----------
export const authApi = {
  sendCode: (email: string, purpose: 'register' | 'reset') =>
    request<{ success: boolean; message: string }>('/auth/send-code', {
      method: 'POST',
      body: JSON.stringify({ email, purpose }),
    }),
  register: (email: string, username: string, password: string, code: string) =>
    request<{ token: string; user: User }>('/auth/register', {
      method: 'POST',
      body: JSON.stringify({ email, username, password, code }),
    }),
  login: (email: string, password: string) =>
    request<{ token: string; user: User }>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    }),
  me: () => request<{ user: User }>('/auth/me'),
  forgot: (email: string) =>
    request<{ success: boolean; message: string }>('/auth/forgot', {
      method: 'POST',
      body: JSON.stringify({ email }),
    }),
  resetPassword: (email: string, code: string, newPassword: string) =>
    request<{ success: boolean; message: string }>('/auth/reset-password', {
      method: 'POST',
      body: JSON.stringify({ email, code, newPassword }),
    }),
  uploadAvatar: (form: FormData) =>
    request<{ success: boolean; avatar_key: string }>('/users/avatar', {
      method: 'POST',
      body: form,
    }),
  avatarPresign: (name: string) =>
    request<{ success: boolean; key: string; url: string }>('/users/avatar-presign', {
      method: 'POST',
      body: JSON.stringify({ name }),
    }),
  avatarComplete: (key: string) =>
    request<{ success: boolean; avatar_key: string }>('/users/avatar-complete', {
      method: 'POST',
      body: JSON.stringify({ key }),
    }),
};

// ---------- 歌曲 ----------
export const songsApi = {
  list: (params: { q?: string; genre?: string; page?: number; limit?: number } = {}) => {
    const search = new URLSearchParams();
    if (params.q) search.set('q', params.q);
    if (params.genre) search.set('genre', params.genre);
    if (params.page) search.set('page', String(params.page));
    if (params.limit) search.set('limit', String(params.limit));
    const qs = search.toString();
    return request<SongListResponse>(`/songs${qs ? `?${qs}` : ''}`);
  },
  get: (id: number) =>
    request<{ song: Song; favoriteCount: number; favorited?: boolean }>(`/songs/${id}`),
  lyrics: (id: number) => requestText(`/songs/${id}/lyrics`),
  upload: (form: FormData) =>
    request<{ success: boolean; id: number }>('/songs', { method: 'POST', body: form }),
  presign: (data: {
    title: string;
    artist: string;
    genre: string;
    duration: number;
    audioName: string;
    coverName?: string;
    lyricsName?: string;
  }) =>
    request<{
      success: boolean;
      id: string;
      audioKey: string;
      audioUrl: string;
      coverKey: string | null;
      coverUrl: string | null;
      lyricsKey: string | null;
      lyricsUrl: string | null;
    }>('/songs/presign', {
      method: 'POST',
      body: JSON.stringify(data),
    }),
  complete: (data: {
    title: string;
    artist: string;
    genre: string;
    duration: number;
    audioKey: string;
    coverKey?: string | null;
    lyricsKey?: string | null;
  }) =>
    request<{ success: boolean; id: number }>('/songs/complete', {
      method: 'POST',
      body: JSON.stringify(data),
    }),
  remove: (id: number) =>
    request<{ success: boolean }>(`/songs/${id}`, { method: 'DELETE' }),
  favorite: (id: number) =>
    request<{ success: boolean; favorite: boolean; favoriteCount: number }>(`/songs/${id}/favorite`, {
      method: 'POST',
    }),
  unfavorite: (id: number) =>
    request<{ success: boolean; favorite: boolean; favoriteCount: number }>(`/songs/${id}/favorite`, {
      method: 'DELETE',
    }),
  genres: () => request<{ genres: GenreCount[] }>('/songs/genres'),
  favorites: () => request<{ songs: Song[] }>('/songs/favorites'),
};

// ---------- 播放列表 ----------
export const playlistsApi = {
  create: (name: string) =>
    request<{ success: boolean; id: number }>('/playlists', {
      method: 'POST',
      body: JSON.stringify({ name }),
    }),
  mine: () => request<{ playlists: Playlist[] }>('/playlists/mine'),
  get: (id: number) =>
    request<{ playlist: Playlist; songs: Song[] }>(`/playlists/${id}`),
  addSong: (playlistId: number, songId: number) =>
    request<{ success: boolean }>(`/playlists/${playlistId}/songs`, {
      method: 'POST',
      body: JSON.stringify({ songId }),
    }),
  removeSong: (playlistId: number, songId: number) =>
    request<{ success: boolean }>(`/playlists/${playlistId}/songs/${songId}`, { method: 'DELETE' }),
  remove: (playlistId: number) =>
    request<{ success: boolean }>(`/playlists/${playlistId}`, { method: 'DELETE' }),
};

// ---------- 评论 ----------
export const commentsApi = {
  list: (songId: number) => request<{ comments: Comment[] }>(`/comments/song/${songId}`),
  create: (songId: number, content: string) =>
    request<{ success: boolean; comment: Comment }>('/comments', {
      method: 'POST',
      body: JSON.stringify({ songId, content }),
    }),
  remove: (commentId: number) =>
    request<{ success: boolean }>(`/comments/${commentId}`, { method: 'DELETE' }),
};

// ---------- 管理员（用户管理） ----------
export const adminApi = {
  listUsers: () => request<{ users: AdminUser[] }>('/admin/users'),
  updateUser: (
    id: number,
    data: { username?: string; title?: string | null; is_admin?: 0 | 1; banned?: 0 | 1 }
  ) =>
    request<{ success: boolean; user: AdminUser }>(`/admin/users/${id}`, {
      method: 'PATCH',
      body: JSON.stringify(data),
    }),
  deleteUser: (id: number) =>
    request<{ success: boolean }>(`/admin/users/${id}`, { method: 'DELETE' }),
};
