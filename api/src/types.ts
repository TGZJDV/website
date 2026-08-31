// 全局环境绑定类型
export type Env = {
  DB: D1Database;
  JWT_SECRET: string;
  JWT_EXPIRES?: string; // 秒
  RESEND_API_KEY?: string; // 邮件服务（可选，本地开发打印验证码）
  EMAIL_FROM?: string;
  // 阿里云 OSS 存储（S3 兼容，替换原 B2）
  OSS_ACCESS_KEY: string; // AccessKey ID
  OSS_SECRET_KEY: string; // AccessKey Secret
  OSS_BUCKET: string; // 桶名（Private）
  OSS_REGION?: string; // 区域，如 cn-hangzhou
  OSS_ENDPOINT: string; // 外网 Endpoint，如 oss-cn-hangzhou.aliyuncs.com
};

// 用户
export type User = {
  id: number;
  email: string;
  username: string;
  avatar_key: string | null;
  title: string | null; // 头衔（管理员可设置）
  is_admin: number; // 0 或 1
  banned: number; // 0 或 1
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
  uploader_title?: string | null;
  created_at: string;
};

// Hono 变量（中间件注入）
export type AppVariables = {
  user: User;
};
