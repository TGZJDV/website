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
  const subject = purpose === 'register' ? "TGZJDV's Music · 注册验证码" : "TGZJDV's Music · 重置密码验证码";
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
  const res = await fetch('https://api.resend.com/emails', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${apiKey}` },
    body: JSON.stringify({ from, to: [to], subject, text }),
  });

  // 关键：检查响应，否则发失败也会被当作成功（之前就是这里静默忽略）
  if (!res.ok) {
    const detail = await res.text().catch(() => '');
    console.error(`[Resend ${res.status}] from=${from} to=${to} :: ${detail}`);
    throw new Error(describeResendError(res.status, detail));
  }
}

/** 把 Resend 的错误翻译成看得懂的提示 */
function describeResendError(status: number, detail: string): string {
  const lower = detail.toLowerCase();
  if (lower.includes('testing emails') || lower.includes('own email address')) {
    return '邮件服务未配置完成：当前发件人是 Resend 测试地址，只能发往注册 Resend 的邮箱。请在 Resend 验证域名后，把 EMAIL_FROM 改成本域名地址（如 TGZJDV\'s Music <noreply@famousmusic.asia>）。';
  }
  if (status === 401) return '邮件服务鉴权失败：RESEND_API_KEY 无效，请重新设置。';
  if (status === 403) return '邮件服务拒绝发送：域名未验证或权限不足。';
  if (status === 429) return '发送太频繁了，请稍后再试。';
  if (status === 422) return `邮件参数被拒绝：${detail.slice(0, 160)}`;
  return `邮件发送失败(${status})${detail ? '：' + detail.slice(0, 160) : ''}`;
}
