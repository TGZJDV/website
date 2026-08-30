// 全局环境绑定类型
export type Env = {
  DB: D1Database;
  R2: R2Bucket;
  JWT_SECRET: string;
  JWT_EXPIRES?: string; // 秒
  RESEND_API_KEY?: string; // 邮件服务（可选，本地开发打印验证码）
  EMAIL_FROM?: string;
};

// 用户
export type User = {
  id: number;
  email: string;
  username: string;
  avatar_key: string | null;
  created_at: string;
};

// 歌曲（含上传者信息）
export type Song = {
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
  created_at: string;
};

// Hono 变量（中间件注入）
export type AppVariables = {
  user: User;
};
