import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { playlistsApi } from '../api';
import { useAuthStore } from '../store/auth';
import type { Song, Playlist } from '../types';

interface AddToPlaylistModalProps {
  song: Song;
  onClose: () => void;
}

/** 加入播放列表弹窗 */
export default function AddToPlaylistModal({ song, onClose }: AddToPlaylistModalProps) {
  const navigate = useNavigate();
  const user = useAuthStore((s) => s.user);
  const [playlists, setPlaylists] = useState<Playlist[]>([]);
  const [newName, setNewName] = useState('');
  const [msg, setMsg] = useState<string | null>(null);

  useEffect(() => {
    if (user) {
      playlistsApi.mine().then((res) => setPlaylists(res.playlists)).catch(() => {});
    }
  }, [user]);

  const addTo = async (playlistId: number) => {
    try {
      await playlistsApi.addSong(playlistId, song.id);
      setMsg('已加入播放列表 ✓');
      setTimeout(onClose, 800);
    } catch (e) {
      setMsg(e instanceof Error ? e.message : '操作失败');
    }
  };

  const createAndAdd = async () => {
    const name = newName.trim();
    if (!name) return;
    try {
      const res = await playlistsApi.create(name);
      await playlistsApi.addSong(res.id, song.id);
      setMsg('已创建并加入 ✓');
      setTimeout(onClose, 800);
    } catch (e) {
      setMsg(e instanceof Error ? e.message : '操作失败');
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4" onClick={onClose}>
      <div className="w-full max-w-sm rounded-2xl bg-surface2 p-6 shadow-2xl" onClick={(e) => e.stopPropagation()}>
        <div className="mb-4 flex items-center justify-between">
          <h3 className="text-lg font-semibold">加入播放列表</h3>
          <button onClick={onClose} className="text-muted hover:text-text">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
              <path d="M18.3 5.7a1 1 0 0 1 0 1.4L13.4 12l4.9 4.9a1 1 0 1 1-1.4 1.4L12 13.4l-4.9 4.9a1 1 0 1 1-1.4-1.4l4.9-4.9-4.9-4.9a1 1 0 0 1 1.4-1.4l4.9 4.9 4.9-4.9a1 1 0 0 1 1.4 0z" />
            </svg>
          </button>
        </div>

        <p className="mb-3 truncate text-sm text-muted">「{song.title}」</p>

        {msg ? (
          <p className="py-4 text-center text-sm text-primary">{msg}</p>
        ) : (
          <>
            <div className="max-h-52 space-y-1 overflow-y-auto">
              {playlists.length === 0 && (
                <p className="py-3 text-center text-sm text-muted">还没有播放列表</p>
              )}
              {playlists.map((p) => (
                <button
                  key={p.id}
                  className="flex w-full items-center justify-between rounded-lg px-3 py-2 text-left text-sm hover:bg-surface3"
                  onClick={() => addTo(p.id)}
                >
                  <span className="truncate">{p.name}</span>
                  <span className="text-xs text-muted">{p.song_count ?? 0} 首</span>
                </button>
              ))}
            </div>

            <div className="mt-4 flex gap-2">
              <input
                className="input-base flex-1"
                placeholder="新播放列表名称"
                value={newName}
                onChange={(e) => setNewName(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && createAndAdd()}
              />
              <button className="btn-primary shrink-0" onClick={createAndAdd} disabled={!newName.trim()}>
                新建
              </button>
            </div>

            {!user && (
              <button
                className="mt-3 w-full text-center text-sm text-primary"
                onClick={() => navigate('/login')}
              >
                登录后管理播放列表
              </button>
            )}
          </>
        )}
      </div>
    </div>
  );
}
