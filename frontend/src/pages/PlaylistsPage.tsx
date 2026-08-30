import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { playlistsApi } from '../api';
import { useAuthStore } from '../store/auth';
import type { Playlist } from '../types';

/** 我的播放列表 */
export default function PlaylistsPage() {
  const navigate = useNavigate();
  const user = useAuthStore((s) => s.user);
  const [playlists, setPlaylists] = useState<Playlist[]>([]);
  const [newName, setNewName] = useState('');
  const [loading, setLoading] = useState(true);

  const load = () => {
    if (!user) return;
    playlistsApi
      .mine()
      .then((res) => setPlaylists(res.playlists))
      .catch(() => {})
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    if (user) load();
    else setLoading(false);
  }, [user]);

  const create = async () => {
    const name = newName.trim();
    if (!name) return;
    try {
      const res = await playlistsApi.create(name);
      setNewName('');
      navigate(`/playlists/${res.id}`);
    } catch (e) {
      alert(e instanceof Error ? e.message : '创建失败');
    }
  };

  const remove = async (id: number, name: string) => {
    if (!window.confirm(`确定删除歌单「${name}」吗？`)) return;
    try {
      await playlistsApi.remove(id);
      load();
    } catch (e) {
      alert(e instanceof Error ? e.message : '删除失败');
    }
  };

  if (!user) {
    return (
      <div className="py-20 text-center">
        <p className="text-muted">请先登录查看你的歌单</p>
        <Link to="/login" className="btn-primary mt-4">
          去登录
        </Link>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-4xl px-4 py-6">
      <h1 className="mb-4 text-2xl font-bold">我的歌单</h1>

      {/* 新建歌单 */}
      <div className="mb-6 flex gap-2">
        <input
          className="input-base flex-1"
          placeholder="新歌单名称"
          value={newName}
          onChange={(e) => setNewName(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && create()}
        />
        <button className="btn-primary shrink-0" onClick={create} disabled={!newName.trim()}>
          新建歌单
        </button>
      </div>

      {loading ? (
        <p className="py-10 text-center text-muted">加载中…</p>
      ) : playlists.length === 0 ? (
        <div className="rounded-xl border border-dashed border-surface3 py-16 text-center">
          <p className="text-muted">还没有歌单，创建第一个吧！</p>
        </div>
      ) : (
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-3">
          {playlists.map((p) => (
            <div key={p.id} className="card group cursor-pointer hover:bg-surface3" onClick={() => navigate(`/playlists/${p.id}`)}>
              <div className="mb-2 flex aspect-square items-center justify-center rounded-lg bg-surface3">
                <svg width="40" height="40" viewBox="0 0 24 24" fill="currentColor" className="text-muted">
                  <path d="M4 6h16v2H4zm0 5h16v2H4zm0 5h10v2H4z" />
                </svg>
              </div>
              <div className="truncate font-medium">{p.name}</div>
              <div className="flex items-center justify-between text-xs text-muted">
                <span>{p.song_count ?? 0} 首</span>
                <button
                  className="opacity-0 transition hover:text-red-400 group-hover:opacity-100"
                  onClick={(e) => {
                    e.stopPropagation();
                    remove(p.id, p.name);
                  }}
                >
                  删除
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
