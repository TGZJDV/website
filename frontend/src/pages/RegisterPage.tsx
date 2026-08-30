import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuthStore } from '../store/auth';
import { authApi } from '../api';

/** 注册页（需邮箱验证码） */
export default function RegisterPage() {
  const navigate = useNavigate();
  const register = useAuthStore((s) => s.register);
  const [email, setEmail] = useState('');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [code, setCode] = useState('');
  const [sending, setSending] = useState(false);
  const [countdown, setCountdown] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  // 验证码发送倒计时
  useEffect(() => {
    if (countdown <= 0) return;
    const t = setTimeout(() => setCountdown((c) => c - 1), 1000);
    return () => clearTimeout(t);
  }, [countdown]);

  const emailValid = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);

  const sendCode = async () => {
    setError(null);
    if (!emailValid) {
      setError('请先填写正确的邮箱');
      return;
    }
    setSending(true);
    try {
      await authApi.sendCode(email, 'register');
      setCountdown(60);
    } catch (err) {
      setError(err instanceof Error ? err.message : '验证码发送失败');
    } finally {
      setSending(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (password !== confirm) {
      setError('两次输入的密码不一致');
      return;
    }
    if (!code) {
      setError('请输入邮箱验证码');
      return;
    }
    setLoading(true);
    try {
      await register(email, username, password, code);
      navigate('/');
    } catch (err) {
      setError(err instanceof Error ? err.message : '注册失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="mx-auto flex min-h-[70vh] max-w-sm flex-col justify-center px-4">
      <h1 className="mb-6 text-center text-2xl font-bold">注册云音乐</h1>

      <form className="space-y-4" onSubmit={handleSubmit}>
        <div>
          <label className="mb-1 block text-sm text-muted">邮箱</label>
          <input
            type="email"
            className="input-base"
            placeholder="you@example.com"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          />
        </div>
        <div>
          <label className="mb-1 block text-sm text-muted">用户名</label>
          <input
            className="input-base"
            placeholder="2-30 个字符"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            minLength={2}
            maxLength={30}
            required
          />
        </div>
        <div>
          <label className="mb-1 block text-sm text-muted">密码</label>
          <input
            type="password"
            className="input-base"
            placeholder="至少 6 位"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            minLength={6}
            required
          />
        </div>
        <div>
          <label className="mb-1 block text-sm text-muted">确认密码</label>
          <input
            type="password"
            className="input-base"
            placeholder="再次输入密码"
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
            required
          />
        </div>
        <div>
          <label className="mb-1 block text-sm text-muted">邮箱验证码</label>
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
          <p className="mt-1 text-xs text-muted">验证码将发送到邮箱，5 分钟内有效（本地开发打印在后端终端）</p>
        </div>

        {error && <p className="text-sm text-red-400">{error}</p>}

        <button type="submit" className="btn-primary w-full" disabled={loading}>
          {loading ? '注册中…' : '注册'}
        </button>
      </form>

      <p className="mt-6 text-center text-sm text-muted">
        已有账号？{' '}
        <Link to="/login" className="text-primary hover:underline">
          去登录
        </Link>
      </p>
    </div>
  );
}
