import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import SongRow from '../components/SongRow';
import Avatar from '../components/Avatar';
import { songsApi, playlistsApi, authApi, uploadToPresigned } from '../api';
import { useAuthStore } from '../store/auth';
import { usePlayerStore } from '../store/player';
import type { Song, Playlist } from '../types';

/** 个人中心：我的上传 / 我的收藏 / 我的歌单 */
export default function MePage() {
  const user = useAuthStore((s) => s.user);
  const fetchMe = useAuthStore((s) => s.fetchMe);
  const playQueue = usePlayerStore((s) => s.playQueue);

  const [uploaded, setUploaded] = useState<Song[]>([]);
  const [favorites, setFavorites] = useState<Song[]>([]);
  const [playlists, setPlaylists] = useState<Playlist[]>([]);
  const [loading, setLoading] = useState(true);
  const [tab, setTab] = useState<'uploaded' | 'favorites'>('uploaded');

  useEffect(() => {
    if (!user) {
      setLoading(false);
      return;
    }
    setLoading(true);
    Promise.all([songsApi.list({ limit: 100 }), songsApi.favorites(), playlistsApi.mine()])
      .then(([res, favRes, plRes]) => {
        setUploaded(res.songs.filter((s) => s.uploader_id === user.id));
        setFavorites(favRes.songs);
        setPlaylists(plRes.playlists);
      })
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [user]);

  if (!user) {
    return (
      <div className="py-20 text-center">
        <p className="text-muted">请先登录</p>
        <Link to="/login" className="btn-primary mt-4">
          去登录
        </Link>
      </div>
    );
  }

  const handleAvatarChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    try {
      // 预签名直传：先拿 URL，浏览器直接 PUT 到 OSS
      const presigned = await authApi.avatarPresign(file.name);
      await uploadToPresigned(presigned.url, file);
      await authApi.avatarComplete(presigned.key);
      await fetchMe();
    } catch (err) {
      alert(err instanceof Error ? err.message : '头像上传失败');
    }
    e.target.value = '';
  };

  return (
    <div className="mx-auto max-w-4xl px-4 py-6">
      {/* 用户信息 */}
      <div className="mb-6 flex items-center gap-4">
        <div className="relative">
          <Avatar user={user} size="lg" />
          <label
            className="absolute -bottom-1 -right-1 flex h-7 w-7 cursor-pointer items-center justify-center rounded-full bg-primary text-white shadow-lg transition hover:bg-primaryDark"
            title="更换头像"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor">
              <path d="M9 3l1.5-2h3L15 3h4a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1h4zm3 13a5 5 0 1 0 0-10 5 5 0 0 0 0 10zm0-2a3 3 0 1 1 0-6 3 3 0 0 1 0 6z" />
            </svg>
            <input type="file" accept="image/*" className="hidden" onChange={handleAvatarChange} />
          </label>
        </div>
        <div>
          <h1 className="text-2xl font-bold">{user.username}</h1>
          <p className="text-sm text-muted">{user.email}</p>
        </div>
        <div className="ml-auto">
          <Link to="/upload" className="btn-primary">
            上传音乐
          </Link>
        </div>
      </div>

      {/* 我的歌单 */}
      <section className="mb-8">
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-lg font-semibold">我的歌单</h2>
          <Link to="/playlists" className="text-sm text-primary hover:underline">
            管理 →
          </Link>
        </div>
        {playlists.length === 0 ? (
          <p className="text-sm text-muted">还没有歌单</p>
        ) : (
          <div className="flex gap-3 overflow-x-auto pb-2">
            {playlists.map((p) => (
              <Link key={p.id} to={`/playlists/${p.id}`} className="card w-40 shrink-0 hover:bg-surface3">
                <div className="mb-2 flex h-16 items-center justify-center rounded-lg bg-surface3">
                  <svg width="28" height="28" viewBox="0 0 24 24" fill="currentColor" className="text-muted">
                    <path d="M4 6h16v2H4zm0 5h16v2H4zm0 5h10v2H4z" />
                  </svg>
                </div>
                <div className="truncate text-sm font-medium">{p.name}</div>
                <div className="text-xs text-muted">{p.song_count ?? 0} 首</div>
              </Link>
            ))}
          </div>
        )}
      </section>

      {/* 我的上传 / 收藏 */}
      <section>
        <div className="mb-3 flex items-center gap-4">
          <button
            className={`text-lg font-semibold ${tab === 'uploaded' ? 'text-primary' : 'text-muted hover:text-text'}`}
            onClick={() => setTab('uploaded')}
          >
            我的上传
          </button>
          <button
            className={`text-lg font-semibold ${tab === 'favorites' ? 'text-primary' : 'text-muted hover:text-text'}`}
            onClick={() => setTab('favorites')}
          >
            我的收藏
          </button>
        </div>

        {loading ? (
          <p className="py-8 text-center text-muted">加载中…</p>
        ) : (
          <div className="space-y-0.5">
            {tab === 'uploaded' &&
              (uploaded.length === 0 ? (
                <p className="py-8 text-center text-muted">还没有上传过歌曲</p>
              ) : (
                uploaded.map((s, i) => (
                  <SongRow key={s.id} song={s} index={i + 1} showRemove={true} onRemoved={() => setUploaded((prev) => prev.filter((x) => x.id !== s.id))} />
                ))
              ))}
            {tab === 'favorites' &&
              (favorites.length === 0 ? (
                <p className="py-8 text-center text-muted">还没有收藏歌曲</p>
              ) : (
                favorites.map((s, i) => (
                  <SongRow key={s.id} song={s} index={i + 1} onRemoved={() => setFavorites((prev) => prev.filter((x) => x.id !== s.id))} />
                ))
              ))}
          </div>
        )}

        {tab === 'uploaded' && uploaded.length > 0 && (
          <button className="btn-ghost mt-6 w-full" onClick={() => playQueue(uploaded)}>
            播放全部
          </button>
        )}
      </section>
    </div>
  );
}
