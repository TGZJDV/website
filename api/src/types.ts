// 全局环境绑定类型
export type Env = {
  DB: D1Database;
  JWT_SECRET: string;
  JWT_EXPIRES?: string; // 秒
  RESEND_API_KEY?: string; // 邮件服务（可选，本地开发打印验证码）
  EMAIL_FROM?: string;
  // Backblaze B2 存储（S3 兼容，替换原 R2）
  B2_ACCESS_KEY: string; // Application Key ID
  B2_SECRET_KEY: string; // Application Key
  B2_BUCKET: string; // 桶名（需 Public）
  B2_REGION?: string; // 桶区域，如 us-west-004
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
