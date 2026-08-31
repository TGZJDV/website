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
  content: string;
  created_at: string;
}

export interface GenreCount {
  genre: string;
  count: number;
}
