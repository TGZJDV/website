import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { authApi } from '../api';

/** 忘记密码：邮箱验证码重置密码 */
export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [code, setCode] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [countdown, setCountdown] = useState(0);
  const [sending, setSending] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (countdown <= 0) return;
    const t = setTimeout(() => setCountdown((c) => c - 1), 1000);
    return () => clearTimeout(t);
  }, [countdown]);

  const emailValid = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);

  const sendCode = async () => {
    setError(null);
    setMessage(null);
    if (!emailValid) {
      setError('请填写正确的邮箱');
      return;
    }
    setSending(true);
    try {
      await authApi.forgot(email);
      setCountdown(60);
      setMessage('重置验证码已发送，请查收邮箱');
    } catch (err) {
      setError(err instanceof Error ? err.message : '发送失败');
    } finally {
      setSending(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setMessage(null);
    if (!code) {
      setError('请输入验证码');
      return;
    }
    setLoading(true);
    try {
      const res = await authApi.resetPassword(email, code, newPassword);
      setMessage(res.message || '密码已重置，请使用新密码登录');
      setCode('');
      setNewPassword('');
    } catch (err) {
      setError(err instanceof Error ? err.message : '重置失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="mx-auto flex min-h-[70vh] max-w-sm flex-col justify-center px-4">
      <h1 className="mb-6 text-center text-2xl font-bold">找回密码</h1>

      <form className="space-y-4" onSubmit={handleSubmit}>
        <div>
          <label className="mb-1 block text-sm text-muted">邮箱</label>
          <input
            type="email"
            className="input-base"
            placeholder="注册时使用的邮箱"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          />
        </div>

        <div>
          <label className="mb-1 block text-sm text-muted">重置验证码</label>
          <div className="flex gap-2">
            <input
              className="input-base flex-1"
              placeholder="6 位数字验证码"
              value={code}
              onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
              inputMode="numeric"
              maxLength={6}
              required
            />
            <button
              type="button"
              className="btn-ghost shrink-0"
              onClick={sendCode}
              disabled={sending || countdown > 0 || !emailValid}
            >
              {countdown > 0 ? `${countdown}s` : sending ? '发送中…' : '获取验证码'}
            </button>
          </div>
        </div>

        <div>
          <label className="mb-1 block text-sm text-muted">新密码</label>
          <input
            type="password"
            className="input-base"
            placeholder="至少 6 位"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            minLength={6}
            required
          />
        </div>

        {message && (
          <p className="rounded-lg bg-primary/10 px-3 py-2 text-sm text-primary">{message}</p>
        )}
        {error && <p className="text-sm text-red-400">{error}</p>}

        <button type="submit" className="btn-primary w-full" disabled={loading}>
          {loading ? '重置中…' : '重置密码'}
        </button>
      </form>

      <p className="mt-6 text-center text-sm text-muted">
        想起来了？{' '}
        <Link to="/login" className="text-primary hover:underline">
          去登录
        </Link>
      </p>
    </div>
  );
}
