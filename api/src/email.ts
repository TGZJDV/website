// ============================================================
// 邮件服务：通过 Resend 发送验证码邮件
// 未配置 RESEND_API_KEY 时（本地开发）把验证码打印到控制台
// ============================================================
import type { Env } from './types';

export type CodePurpose = 'register' | 'reset';

export async function sendVerificationEmail(
  env: Env,
  to: string,
  code: string,
  purpose: CodePurpose
): Promise<void> {
  const subject = purpose === 'register' ? '云音乐 · 注册验证码' : '云音乐 · 重置密码验证码';
  const text =
    purpose === 'register'
      ? `你的注册验证码是：${code}，5 分钟内有效。如果不是你本人操作请忽略。`
      : `你的重置密码验证码是：${code}，5 分钟内有效。如果不是你本人操作请忽略。`;

  const apiKey = env.RESEND_API_KEY;
  if (!apiKey || apiKey === 'dev-no-key') {
    // 本地开发模式：打印验证码方便测试
    console.log(`[验证码 ${purpose}] ${text} (收件人: ${to})`);
    return;
  }

  const from = env.EMAIL_FROM || 'CloudMusic <onboarding@resend.dev>';
  await fetch('https://api.resend.com/emails', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${apiKey}` },
    body: JSON.stringify({ from, to: [to], subject, text }),
  });
}
