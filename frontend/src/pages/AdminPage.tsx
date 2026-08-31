import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { adminApi } from '../api';
import { useAuthStore } from '../store/auth';
import type { AdminUser } from '../types';

/** 管理员面板：用户管理（列表 / 封禁 / 头衔 / 用户名 / 删除 / 管理员） */
export default function AdminPage() {
  const navigate = useNavigate();
  const me = useAuthStore((s) => s.user);
  const [users, setUsers] = useState<AdminUser[]>([]);
  const [loading, setLoading] = useState(true);
  const [msg, setMsg] = useState<string | null>(null);
  const [editUser, setEditUser] = useState<AdminUser | null>(null);

  const load = () => {
    setLoading(true);
    adminApi
      .listUsers()
      .then((res) => setUsers(res.users))
      .catch((e) => setMsg(e instanceof Error ? e.message : '加载失败'))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    if (!me) {
      navigate('/login');
      return;
    }
    if (me.is_admin !== 1) {
      navigate('/');
      return;
    }
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [me]);

  const flash = (e: unknown) => setMsg(e instanceof Error ? e.message : '操作失败');

  const toggleBanned = async (u: AdminUser) => {
    try {
      await adminApi.updateUser(u.id, { banned: u.banned ? 0 : 1 });
      if (u.id === me?.id) return; // 理论上不会触发（后端拦截）
      load();
    } catch (e) {
      flash(e);
    }
  };

  const remove = async (u: AdminUser) => {
    if (!window.confirm(`确定删除用户「${u.username}」吗？\n将同时删除其上传的歌曲、歌单、收藏和评论，且不可恢复。`)) return;
    try {
      await adminApi.deleteUser(u.id);
      setMsg('用户已删除');
      load();
    } catch (e) {
      flash(e);
    }
  };

  return (
    <div className="mx-auto max-w-5xl px-4 py-6">
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">管理员面板</h1>
          <p className="mt-1 text-sm text-muted">用户管理 · 封禁 / 头衔 / 用户名 / 删除</p>
        </div>
        <button className="btn-ghost" onClick={() => navigate('/me')}>
          返回
        </button>
      </div>

      {msg && (
        <div className="mb-4 rounded-lg border border-accent/30 bg-accent/10 px-4 py-2 text-sm text-accent" onClick={() => setMsg(null)}>
          {msg}
        </div>
      )}

      {loading ? (
        <p className="py-10 text-center text-muted">加载中…</p>
      ) : (
        <div className="space-y-2">
          {users.map((u) => {
            const isSelf = u.id === me?.id;
            return (
              <div key={u.id} className="card flex flex-wrap items-center gap-3 !p-3">
                {/* 头像 + 用户名/邮箱 */}
                <div className="flex min-w-0 flex-1 items-center gap-3">
                  <div className="flex h-10 w-10 shrink-0 items-center justify-center overflow-hidden rounded-full bg-primary text-base font-bold text-white">
                    {u.avatar_key ? (
                      <img
                        src={`${import.meta.env.VITE_API_URL || '/api'}/users/${u.id}/avatar`}
                        alt={u.username}
                        className="h-full w-full object-cover"
                        loading="lazy"
                      />
                    ) : (
                      u.username.charAt(0).toUpperCase()
                    )}
                  </div>
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-1.5">
                      <span className="font-medium">{u.username}</span>
                      {u.is_admin === 1 && (
                        <span className="rounded-full bg-accent/15 px-1.5 py-0.5 text-[11px] text-accent">管理员</span>
                      )}
                      {u.banned === 1 && (
                        <span className="rounded-full bg-red-500/15 px-1.5 py-0.5 text-[11px] text-red-400">已封禁</span>
                      )}
                      {u.title && (
                        <span className="rounded-full bg-primary/15 px-1.5 py-0.5 text-[11px] text-primary">{u.title}</span>
                      )}
                    </div>
                    <div className="truncate text-xs text-muted">
                      {u.email} · {u.songs_count} 首上传
                    </div>
                  </div>
                </div>

                {/* 操作 */}
                <div className="flex shrink-0 flex-wrap items-center gap-2">
                  <button className="btn-ghost !px-3 !py-1.5 text-xs" onClick={() => setEditUser(u)}>
                    编辑
                  </button>
                  {!isSelf && (
                    <button
                      className={`btn-ghost !px-3 !py-1.5 text-xs ${u.banned === 1 ? '!text-primary' : '!text-red-400'}`}
                      onClick={() => toggleBanned(u)}
                    >
                      {u.banned === 1 ? '解封' : '封禁'}
                    </button>
                  )}
                  {!isSelf && (
                    <button
                      className="btn-ghost !px-3 !py-1.5 text-xs !text-red-400"
                      onClick={() => remove(u)}
                    >
                      删除
                    </button>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}

      {editUser && <EditUserModal user={editUser} meId={me?.id} onClose={() => setEditUser(null)} onSaved={load} onFlash={flash} />}
    </div>
  );
}

/** 编辑用户弹窗：用户名 / 头衔 / 管理员 / 封禁 */
function EditUserModal({
  user,
  meId,
  onClose,
  onSaved,
  onFlash,
}: {
  user: AdminUser;
  meId?: number;
  onClose: () => void;
  onSaved: () => void;
  onFlash: (e: unknown) => void;
}) {
  const [username, setUsername] = useState(user.username);
  const [title, setTitle] = useState(user.title ?? '');
  const [isAdmin, setIsAdmin] = useState(user.is_admin === 1);
  const [banned, setBanned] = useState(user.banned === 1);
  const [saving, setSaving] = useState(false);
  const isSelf = user.id === meId;

  const save = async () => {
    setSaving(true);
    try {
      await adminApi.updateUser(user.id, {
        username: username.trim(),
        title: title.trim() ? title.trim() : null,
        is_admin: isAdmin ? 1 : 0,
        banned: banned ? 1 : 0,
      });
      onClose();
      onSaved();
    } catch (e) {
      onFlash(e);
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4" onClick={onClose}>
      <div className="w-full max-w-sm rounded-2xl bg-surface2 p-5 shadow-2xl" onClick={(e) => e.stopPropagation()}>
        <div className="mb-4 flex items-center justify-between">
          <h3 className="text-lg font-semibold">编辑用户</h3>
          <button onClick={onClose} className="text-muted hover:text-text">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
              <path d="M18.3 5.7a1 1 0 0 1 0 1.4L13.4 12l4.9 4.9a1 1 0 1 1-1.4 1.4L12 13.4l-4.9 4.9a1 1 0 1 1-1.4-1.4l4.9-4.9-4.9-4.9a1 1 0 0 1 1.4-1.4l4.9 4.9 4.9-4.9a1 1 0 0 1 1.4 0z" />
            </svg>
          </button>
        </div>

        <div className="space-y-4">
          <div>
            <label className="mb-1 block text-sm font-medium">用户名</label>
            <input className="input-base" value={username} onChange={(e) => setUsername(e.target.value)} />
          </div>
          <div>
            <label className="mb-1 block text-sm font-medium">头衔（留空清除）</label>
            <input
              className="input-base"
              placeholder="如：站长、认证歌手…"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
            />
          </div>

          <div className="space-y-2">
            <label className="flex items-center justify-between rounded-lg bg-surface3/50 px-3 py-2.5">
              <span className="text-sm">管理员</span>
              <input
                type="checkbox"
                checked={isAdmin}
                disabled={isSelf}
                onChange={(e) => setIsAdmin(e.target.checked)}
                className="h-4 w-4 accent-primary"
              />
            </label>
            <label className="flex items-center justify-between rounded-lg bg-surface3/50 px-3 py-2.5">
              <span className="text-sm">封禁（禁止登录）</span>
              <input
                type="checkbox"
                checked={banned}
                disabled={isSelf}
                onChange={(e) => setBanned(e.target.checked)}
                className="h-4 w-4 accent-red-500"
              />
            </label>
            {isSelf && <p className="text-xs text-muted">不能修改自己的管理员身份或封禁自己</p>}
          </div>
        </div>

        <button className="btn-primary mt-5 w-full" onClick={save} disabled={saving || !username.trim()}>
          {saving ? '保存中…' : '保存'}
        </button>
      </div>
    </div>
  );
}
