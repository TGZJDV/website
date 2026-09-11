// 与后端 API 对应的类型定义

export interface User {
  id: number;
  email: string;
  username: string;
  avatar_key?: string | null;
  title?: string | null;
  is_admin?: number;
  banned?: number;
  created_at?: string;
}

/** 管理员面板中的用户信息 */
export interface AdminUser {
  id: number;
  email: string;
  username: string;
  avatar_key: string | null;
  title: string | null;
  is_admin: number;
  banned: number;
  songs_count: number;
  created_at: string;
}

export interface Song {
  id: number;
  title: string;
  artist: string;
  genre: string;
  cover_key: string | null;
  audio_key: string;
  lyrics_key: string | null;
  duration: number;
  uploader_id: number;
  uploader_name?: string;
  uploader_title?: string | null;
  created_at: string;
}

export interface SongListResponse {
  songs: Song[];
  total: number;
  page: number;
  limit: number;
}

export interface Playlist {
  id: number;
  name: string;
  user_id: number;
  owner_name?: string;
  song_count?: number;
  created_at: string;
}

export interface Comment {
  id: number;
  song_id: number;
  user_id: number;
  username: string;
  title?: string | null;
  avatar_key?: string | null;
  content: string;
  created_at: string;
}

export interface GenreCount {
  genre: string;
  count: number;
}

/** 客户端发布信息（下载页用） */
export interface ClientRelease {
  id: string;
  name: string;
  subtitle: string;
  available: boolean;
  version: string | null;
  versionCode: number | null;
  size: number | null;
  sizeText: string | null;
  minOs: string | null;
  publishedAt: string | null;
  sha256: string | null;
  note: string | null;
  /** 安装包文件名（前端拼成 /downloads/<file>），available=false 时为 null */
  file: string | null;
}
