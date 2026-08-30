import { useEffect } from 'react';
import { NavLink, Outlet, Link, useNavigate } from 'react-router-dom';
import PlayerBar from './PlayerBar';
import Avatar from './Avatar';
import { useAuthStore } from '../store/auth';

const navItems = [
  { to: '/', label: '发现', icon: 'M12 2l9 4.9V9h-2v2h2v2h-2v2h2v2h-2v3h-9z' },
  { to: '/search', label: '搜索', icon: 'M15.5 14h-.79l-.28-.27a6.5 6.5 0 1 0-.7.7l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0A4.5 4.5 0 1 1 14 9.5 4.5 4.5 0 0 1 9.5 14z' },
  { to: '/genres', label: '分类', icon: 'M3 5h7v14H3zm9-1h9v4h-9zm0 6h9v4h-9zm0 6h9v4h-9z' },
  { to: '/playlists', label: '歌单', icon: 'M4 6h16v2H4zm0 5h16v2H4zm0 5h10v2H4z' },
  { to: '/upload', label: '上传', icon: 'M12 3a9 9 0 1 0 9 9h-2a7 7 0 1 1-7-7v4a1 1 0 0 0 1.7.7l4.3-4.3-4.3-4.3A1 1 0 0 0 12 3z' },
];

/** 主布局：顶部栏 + 左侧导航 + 内容区 + 底部播放条 */
export default function Layout() {
  const { user, initialized, fetchMe, logout } = useAuthStore();
  const navigate = useNavigate();

  useEffect(() => {
    if (!initialized) fetchMe();
  }, [initialized, fetchMe]);

  return (
    <div className="flex h-screen flex-col">
      {/* 顶部栏 */}
      <header className="flex h-14 shrink-0 items-center justify-between border-b border-surface3 bg-surface px-4">
        <Link to="/" className="flex items-center gap-2">
          <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary text-white">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
              <path d="M12 3v10.55A4 4 0 1 0 14 17V7h4V3h-6z" />
            </svg>
          </div>
          <span className="text-lg font-bold">云音乐</span>
        </Link>

        <nav className="flex items-center gap-1">
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                `flex items-center gap-1.5 rounded-full px-3 py-1.5 text-sm transition ${
                  isActive ? 'bg-surface3 text-primary' : 'text-muted hover:text-text'
                }`
              }
            >
              <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
                <path d={item.icon} />
              </svg>
              <span className="hidden sm:inline">{item.label}</span>
            </NavLink>
          ))}
        </nav>

        <div className="flex items-center gap-2">
          {initialized && user ? (
            <div className="flex items-center gap-2">
              <Link
                to="/me"
                className="flex items-center gap-2 rounded-full border border-surface3 px-3 py-1.5 text-sm hover:border-primary"
              >
                <Avatar user={user} size="sm" />
                <span className="hidden sm:inline">{user.username}</span>
              </Link>
              <button className="text-sm text-muted hover:text-text" onClick={() => { logout(); navigate('/'); }}>
                退出
              </button>
            </div>
          ) : (
            <div className="flex items-center gap-2">
              <Link to="/login" className="text-sm text-muted hover:text-text">
                登录
              </Link>
              <Link to="/register" className="btn-primary !py-1.5">
                注册
              </Link>
            </div>
          )}
        </div>
      </header>

      {/* 内容区（底部为播放条预留空间） */}
      <main className="min-h-0 flex-1 overflow-y-auto pb-20">
        <Outlet />
      </main>

      {/* 底部播放条 */}
      <PlayerBar />
    </div>
  );
}
