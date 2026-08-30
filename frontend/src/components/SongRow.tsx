import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Cover from './Cover';
import AddToPlaylistModal from './AddToPlaylistModal';
import { formatDuration } from '../utils/format';
import { usePlayerStore } from '../store/player';
import { useAuthStore } from '../store/auth';
import type { Song } from '../types';

interface SongRowProps {
  song: Song;
  index?: number;
  showIndex?: boolean;
  onRemoved?: () => void;
  showRemove?: boolean;
}

/** 列表行：序号 + 封面 + 标题 + 歌手 + 时长 + 操作 */
export default function SongRow({ song, index, showIndex = true, onRemoved, showRemove = false }: SongRowProps) {
  const navigate = useNavigate();
  const { current, playing, playSong, toggle } = usePlayerStore();
  const user = useAuthStore((s) => s.user);
  const [menuOpen, setMenuOpen] = useState(false);
  const [showAddModal, setShowAddModal] = useState(false);

  const isCurrent = current?.id === song.id;
  const isPlaying = isCurrent && playing;

  const handlePlay = () => {
    if (isCurrent) toggle();
    else playSong(song);
  };

  return (
    <>
      <div
        className={`group flex items-center gap-3 rounded-lg px-3 py-2.5 transition ${
          isCurrent ? 'bg-surface3' : 'hover:bg-surface2'
        }`}
      >
        {/* 序号 / 播放状态 */}
        <div className="w-6 text-center text-sm text-muted">
          {isCurrent ? (
            <button onClick={handlePlay} className="text-primary" title={isPlaying ? '暂停' : '播放'}>
              {isPlaying ? (
                <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z" />
                </svg>
              ) : (
                <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M8 5v14l11-7z" />
                </svg>
              )}
            </button>
          ) : showIndex ? (
            <span className="group-hover:hidden">{index}</span>
          ) : null}
          {!isCurrent && showIndex && (
            <button onClick={handlePlay} className="hidden text-primary group-hover:block">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
                <path d="M8 5v14l11-7z" />
              </svg>
            </button>
          )}
        </div>

        {/* 封面 + 标题 */}
        <button
          className="flex min-w-0 flex-1 items-center gap-3 text-left"
          onClick={() => navigate(`/song/${song.id}`)}
        >
          <Cover song={song} title={song.title} />
          <div className="min-w-0">
            <div className={`truncate text-sm font-medium ${isCurrent ? 'text-primary' : 'text-text'}`}>
              {song.title}
            </div>
            <div className="truncate text-xs text-muted">{song.artist || song.uploader_name}</div>
          </div>
        </button>

        {/* 流派 */}
        <span className="hidden w-20 truncate text-xs text-muted sm:block">{song.genre}</span>

        {/* 时长 */}
        <span className="w-12 text-right text-xs text-muted">{formatDuration(song.duration)}</span>

        {/* 操作按钮 */}
        <div className="relative flex items-center gap-1">
          <button
            className="rounded p-1.5 text-muted hover:text-text"
            title="加入播放列表"
            onClick={() => setShowAddModal(true)}
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
              <path d="M12 4a1 1 0 0 1 1 1v6h6a1 1 0 1 1 0 2h-6v6a1 1 0 1 1-2 0v-6H5a1 1 0 1 1 0-2h6V5a1 1 0 0 1 1-1z" />
            </svg>
          </button>
          <button
            className="rounded p-1.5 text-muted hover:text-text"
            title="更多"
            onClick={() => setMenuOpen(!menuOpen)}
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
              <circle cx="12" cy="5" r="1.6" />
              <circle cx="12" cy="12" r="1.6" />
              <circle cx="12" cy="19" r="1.6" />
            </svg>
          </button>

          {menuOpen && (
            <>
              <div className="fixed inset-0 z-40" onClick={() => setMenuOpen(false)} />
              <div className="absolute right-0 top-8 z-50 w-40 overflow-hidden rounded-lg border border-surface3 bg-surface2 py-1 shadow-xl">
                <button
                  className="block w-full px-4 py-2 text-left text-sm hover:bg-surface3"
                  onClick={() => {
                    setMenuOpen(false);
                    navigate(`/song/${song.id}`);
                  }}
                >
                  查看详情
                </button>
                <button
                  className="block w-full px-4 py-2 text-left text-sm hover:bg-surface3"
                  onClick={() => {
                    setMenuOpen(false);
                    setShowAddModal(true);
                  }}
                >
                  加入播放列表
                </button>
                {showRemove && user && song.uploader_id === user.id && (
                  <button
                    className="block w-full px-4 py-2 text-left text-sm text-red-400 hover:bg-surface3"
                    onClick={() => {
                      setMenuOpen(false);
                      onRemoved?.();
                    }}
                  >
                    移除
                  </button>
                )}
              </div>
            </>
          )}
        </div>
      </div>

      {showAddModal && (
        <AddToPlaylistModal song={song} onClose={() => setShowAddModal(false)} />
      )}
    </>
  );
}
