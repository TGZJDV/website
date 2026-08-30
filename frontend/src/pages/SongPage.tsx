import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import Cover from '../components/Cover';
import AddToPlaylistModal from '../components/AddToPlaylistModal';
import { songsApi, commentsApi, streamUrl } from '../api';
import { useAuthStore } from '../store/auth';
import { usePlayerStore } from '../store/player';
import { formatDuration, formatRelative } from '../utils/format';
import type { Song, Comment } from '../types';

/** 歌曲详情页：播放、收藏、评论 */
export default function SongPage() {
  const { id } = useParams();
  const songId = Number(id);
  const navigate = useNavigate();

  const { user } = useAuthStore();
  const player = usePlayerStore();

  const [song, setSong] = useState<Song | null>(null);
  const [favoriteCount, setFavoriteCount] = useState(0);
  const [favorited, setFavorited] = useState(false);
  const [comments, setComments] = useState<Comment[]>([]);
  const [newComment, setNewComment] = useState('');
  const [showAddModal, setShowAddModal] = useState(false);
  const [loading, setLoading] = useState(true);
  const [actionMsg, setActionMsg] = useState<string | null>(null);

  // 是否正在播放这首歌
  const isCurrent = player.current?.id === songId;
  const isPlaying = isCurrent && player.playing;

  // 加载歌曲与评论
  useEffect(() => {
    if (!Number.isFinite(songId)) return;
    setLoading(true);
    setComments([]);

    Promise.all([
      songsApi.get(songId).catch(() => null),
      commentsApi.list(songId).catch(() => null),
    ]).then(([songRes, commentRes]) => {
      if (songRes) {
        setSong(songRes.song);
        setFavoriteCount(songRes.favoriteCount);
      }
      if (commentRes) setComments(commentRes.comments);
      setLoading(false);
    });
  }, [songId]);

  const handlePlay = () => {
    if (!song) return;
    if (isCurrent) player.toggle();
    else player.playSong(song, [song]);
  };

  const toggleFavorite = async () => {
    if (!user) {
      navigate('/login');
      return;
    }
    try {
      const res = favorited
        ? await songsApi.unfavorite(songId)
        : await songsApi.favorite(songId);
      setFavorited(res.favorite);
      setFavoriteCount(res.favoriteCount);
    } catch {
      /* ignore */
    }
  };

  const submitComment = async () => {
    const content = newComment.trim();
    if (!content) return;
    if (!user) {
      navigate('/login');
      return;
    }
    try {
      const res = await commentsApi.create(songId, content);
      setComments((prev) => [...prev, res.comment]);
      setNewComment('');
    } catch (e) {
      setActionMsg(e instanceof Error ? e.message : '评论失败');
    }
  };

  const deleteComment = async (commentId: number) => {
    try {
      await commentsApi.remove(commentId);
      setComments((prev) => prev.filter((c) => c.id !== commentId));
    } catch (e) {
      setActionMsg(e instanceof Error ? e.message : '删除失败');
    }
  };

  const deleteSong = async () => {
    if (!song || !user || song.uploader_id !== user.id) return;
    if (!window.confirm(`确定删除《${song.title}》吗？此操作不可恢复。`)) return;
    try {
      await songsApi.remove(song.id);
      navigate('/me');
    } catch (e) {
      setActionMsg(e instanceof Error ? e.message : '删除失败');
    }
  };

  if (loading) {
    return <p className="py-20 text-center text-muted">加载中…</p>;
  }

  if (!song) {
    return (
      <div className="py-20 text-center">
        <p className="text-muted">歌曲不存在</p>
        <button className="btn-ghost mt-4" onClick={() => navigate('/')}>
          返回首页
        </button>
      </div>
    );
  }

  return (    <div className="mx-auto max-w-5xl px-4 py-6">
      {actionMsg && (
        <div className="mb-4 rounded-lg border border-accent/30 bg-accent/10 px-4 py-2 text-sm text-accent">
          {actionMsg}
        </div>
      )}

      {/* 头部信息 */}
      <div className="flex flex-col gap-6 sm:flex-row">
        <div className="flex items-center gap-6 sm:flex-col sm:items-start">
          <Cover song={song} title={song.title} className="h-40 w-40 rounded-2xl sm:h-52 sm:w-52" />
          <div className="sm:hidden">
            <SongMeta song={song} favoriteCount={favoriteCount} />
          </div>
        </div>

        <div className="min-w-0 flex-1">
          <div className="hidden sm:block">
            <SongMeta song={song} favoriteCount={favoriteCount} />
          </div>

          <div className="mt-4 flex flex-wrap items-center gap-2">
            <button className="btn-primary" onClick={handlePlay}>
              {isPlaying ? '暂停' : '播放'}
            </button>
            <button className="btn-ghost" onClick={toggleFavorite}>
              {favorited ? '♥ 已收藏' : '♡ 收藏'}
            </button>
            <button className="btn-ghost" onClick={() => setShowAddModal(true)}>
              加入歌单
            </button>
            {user && song.uploader_id === user.id && (
              <button
                className="btn-ghost !border-red-400/40 !text-red-400 hover:!border-red-400"
                onClick={deleteSong}
              >
                删除
              </button>
            )}
          </div>

          <audio className="mt-4 w-full" controls src={streamUrl(song.id)} preload="none" />
        </div>
      </div>

      {/* 评论区 */}
      <div className="card mt-8">
          <h2 className="mb-3 text-lg font-semibold">
            评论 <span className="text-sm text-muted">({comments.length})</span>
          </h2>

          <div className="mb-4 flex gap-2">
            <input
              className="input-base flex-1"
              placeholder={user ? '说说你的看法…' : '登录后参与评论'}
              value={newComment}
              onChange={(e) => setNewComment(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && submitComment()}
              readOnly={!user}
            />
            <button className="btn-primary shrink-0" onClick={submitComment} disabled={!newComment.trim()}>
              发布
            </button>
          </div>

          <div className="max-h-80 space-y-3 overflow-y-auto pr-1">
            {comments.length === 0 ? (
              <p className="py-8 text-center text-sm text-muted">还没有评论，来抢沙发～</p>
            ) : (
              comments.map((c) => (
                <div key={c.id} className="border-b border-surface3 pb-3">
                  <div className="mb-1 flex items-center justify-between">
                    <span className="text-sm font-medium text-primary">{c.username}</span>
                    <span className="flex items-center gap-2 text-xs text-muted">
                      {formatRelative(c.created_at)}
                      {user && c.user_id === user.id && (
                        <button className="hover:text-red-400" onClick={() => deleteComment(c.id)}>
                          删除
                        </button>
                      )}
                    </span>
                  </div>
                  <p className="text-sm leading-relaxed">{c.content}</p>
                </div>
              ))
            )}
          </div>
        </div>

      {showAddModal && <AddToPlaylistModal song={song} onClose={() => setShowAddModal(false)} />}
    </div>
  );
}

function SongMeta({ song, favoriteCount }: { song: Song; favoriteCount: number }) {
  return (
    <div>
      <h1 className="text-2xl font-bold">{song.title}</h1>
      <p className="mt-1 text-muted">
        歌手：{song.artist || song.uploader_name || '未知'} · {song.genre} ·{' '}
        {formatDuration(song.duration)}
      </p>
      <p className="mt-1 text-xs text-muted">
        上传者：{song.uploader_name || '未知'} · {formatRelative(song.created_at)} · ♥ {favoriteCount}
      </p>
    </div>
  );
}
