import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import SongRow from '../components/SongRow';
import { playlistsApi } from '../api';
import { useAuthStore } from '../store/auth';
import { usePlayerStore } from '../store/player';
import type { Playlist, Song } from '../types';

/** 播放列表详情 */
export default function PlaylistDetailPage() {
  const { id } = useParams();
  const playlistId = Number(id);
  const navigate = useNavigate();
  const user = useAuthStore((s) => s.user);
  const playQueue = usePlayerStore((s) => s.playQueue);

  const [playlist, setPlaylist] = useState<Playlist | null>(null);
  const [songs, setSongs] = useState<Song[]>([]);
  const [loading, setLoading] = useState(true);

  const load = () => {
    playlistsApi
      .get(playlistId)
      .then((res) => {
        setPlaylist(res.playlist);
        setSongs(res.songs);
      })
      .catch(() => {})
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    if (Number.isFinite(playlistId)) load();
  }, [playlistId]);

  const isOwner = !!(user && playlist && user.id === playlist.user_id);

  const removeSong = async (songId: number) => {
    try {
      await playlistsApi.removeSong(playlistId, songId);
      load();
    } catch (e) {
      alert(e instanceof Error ? e.message : '操作失败');
    }
  };

  const deletePlaylist = async () => {
    if (!window.confirm(`确定删除歌单「${playlist?.name}」吗？`)) return;
    try {
      await playlistsApi.remove(playlistId);
      navigate('/playlists');
    } catch (e) {
      alert(e instanceof Error ? e.message : '删除失败');
    }
  };

  if (loading) return <p className="py-20 text-center text-muted">加载中…</p>;

  if (!playlist) {
    return (
      <div className="py-20 text-center">
        <p className="text-muted">歌单不存在</p>
        <button className="btn-ghost mt-4" onClick={() => navigate('/playlists')}>
          返回歌单
        </button>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-4xl px-4 py-6">
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">{playlist.name}</h1>
          <p className="mt-1 text-sm text-muted">
            {playlist.owner_name} 创建 · {songs.length} 首
          </p>
        </div>
        <div className="flex gap-2">
          {songs.length > 0 && (
            <button className="btn-primary" onClick={() => playQueue(songs)}>
              播放全部
            </button>
          )}
          {isOwner && (
            <button className="btn-ghost !text-red-400" onClick={deletePlaylist}>
              删除歌单
            </button>
          )}
        </div>
      </div>

      {songs.length === 0 ? (
        <div className="rounded-xl border border-dashed border-surface3 py-16 text-center">
          <p className="text-muted">歌单还是空的，去添加歌曲吧！</p>
          <button className="btn-ghost mt-4" onClick={() => navigate('/search')}>
            去搜索
          </button>
        </div>
      ) : (
        <div className="space-y-0.5">
          {songs.map((song, i) => (
            <SongRow
              key={song.id}
              song={song}
              index={i + 1}
              showRemove={isOwner}
              onRemoved={() => removeSong(song.id)}
            />
          ))}
        </div>
      )}
    </div>
  );
}
