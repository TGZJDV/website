import { useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import Cover from './Cover';
import { formatDuration } from '../utils/format';
import { usePlayerStore } from '../store/player';
import { useFavorite } from '../hooks/useFavorite';

/** 底部固定播放条 */
export default function PlayerBar() {
  const navigate = useNavigate();
  const {
    current,
    playing,
    currentTime,
    duration,
    volume,
    toggle,
    next,
    prev,
    seek,
    setVolume,
  } = usePlayerStore();

  const barRef = useRef<HTMLInputElement>(null);
  const { favorited, toggle: toggleFavorite } = useFavorite(current?.id);
  const progress = duration > 0 ? Math.min(100, (currentTime / duration) * 100) : 0;

  if (!current) {
    return (
      <div className="fixed bottom-14 left-0 right-0 z-30 flex h-16 items-center justify-center border-t border-surface3 bg-surface text-xs text-muted sm:bottom-0">
        选择一首歌开始播放 🎵
      </div>
    );
  }

  const handleSeek = (e: React.ChangeEvent<HTMLInputElement>) => {
    const time = Number(e.target.value);
    seek(time);
  };

  return (
    <>
      {/* 桌面端播放条 */}
      <div className="fixed bottom-0 left-0 right-0 z-30 hidden h-16 items-center gap-3 border-t border-surface3 bg-surface px-4 sm:flex">
      {/* 当前歌曲：点击打开全屏播放界面 */}
      <button
        className="flex min-w-0 flex-1 items-center gap-3 text-left sm:flex-none sm:basis-64"
        onClick={() => navigate('/play')}
        title="打开播放界面"
      >
        <Cover song={current} title={current.title} />
        <div className="min-w-0">
          <div className="truncate text-sm font-medium">{current.title}</div>
          <div className="truncate text-xs text-muted">{current.artist || current.uploader_name}</div>
        </div>
      </button>

      {/* 控制按钮 + 收藏 */}
      <div className="flex shrink-0 items-center gap-3">
        <button
          className={`flex items-center justify-center rounded-full p-1.5 transition ${
            favorited ? 'text-primary' : 'text-muted hover:text-text'
          }`}
          onClick={toggleFavorite}
          title={favorited ? '取消收藏' : '收藏'}
        >
          {favorited ? (
            <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
              <path d="M12 21s-6.7-4.3-9.3-8.5C.6 9.3 2.4 5.5 5.9 5.1c2-.2 3.9.8 6.1 3 2.2-2.2 4.1-3.2 6.1-3 3.5.4 5.3 4.2 3.2 7.4C18.7 16.7 12 21 12 21z" />
            </svg>
          ) : (
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
              <path d="M12 21s-6.7-4.3-9.3-8.5C.6 9.3 2.4 5.5 5.9 5.1c2-.2 3.9.8 6.1 3 2.2-2.2 4.1-3.2 6.1-3 3.5.4 5.3 4.2 3.2 7.4C18.7 16.7 12 21 12 21z" />
            </svg>
          )}
        </button>
        <button className="text-muted hover:text-text" onClick={prev} title="上一首">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
            <path d="M6 6h2v12H6V6zm3.5 6l8.5 6V6l-8.5 6z" />
          </svg>
        </button>
        <button
          className="flex h-9 w-9 items-center justify-center rounded-full bg-text text-surface transition hover:scale-105"
          onClick={toggle}
          title={playing ? '暂停' : '播放'}
        >
          {playing ? (
            <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
              <path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z" />
            </svg>
          ) : (
            <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
              <path d="M8 5v14l11-7z" />
            </svg>
          )}
        </button>
        <button className="text-muted hover:text-text" onClick={next} title="下一首">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
            <path d="M16 6h2v12h-2V6zM6 18l8.5-6L6 6v12z" />
          </svg>
        </button>
      </div>

      {/* 进度条（控制按钮右侧） */}
      <div className="flex min-w-0 flex-1 items-center gap-2">
        <span className="text-[11px] tabular-nums text-muted">{formatDuration(currentTime)}</span>
        <input
          ref={barRef}
          type="range"
          min={0}
          max={duration || 0}
          step={0.1}
          value={Math.min(currentTime, duration || 0)}
          onChange={handleSeek}
          className="h-1 min-w-0 flex-1 cursor-pointer appearance-none rounded-full accent-primary"
          style={{
            background: `linear-gradient(to right, #0ea5e9 ${progress}%, #2a2a2a ${progress}%)`,
          }}
        />
        <span className="text-[11px] tabular-nums text-muted">{formatDuration(duration)}</span>
      </div>

      {/* 音量（进度条右侧，桌面端） */}
      <div className="flex shrink-0 items-center gap-2">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" className="text-muted">
          <path d="M3 9v6h4l5 5V4L7 9H3zm13.5 3a4.5 4.5 0 0 0-2.5-4v8a4.5 4.5 0 0 0 2.5-4z" />
        </svg>
        <input
          type="range"
          min={0}
          max={1}
          step={0.01}
          value={volume}
          onChange={(e) => setVolume(Number(e.target.value))}
          className="h-1 w-20 cursor-pointer appearance-none rounded-full bg-surface3 accent-primary"
        />
      </div>
      </div>

      {/* 移动端播放条：封面 + 标题 + 收藏 + 播放/暂停 + 顶部进度条 */}
      <div className="fixed bottom-14 left-0 right-0 z-30 flex h-16 items-center gap-2 border-t border-surface3 bg-surface px-3 sm:hidden">
        {/* 顶部进度细条 */}
        <div className="absolute inset-x-0 top-0 h-0.5 bg-surface3">
          <div className="h-full bg-primary" style={{ width: `${progress}%` }} />
        </div>
        {/* 当前歌曲：点击打开全屏播放界面 */}
        <button
          className="flex min-w-0 flex-1 items-center gap-3 text-left"
          onClick={() => navigate('/play')}
          title="打开播放界面"
        >
          <Cover song={current} title={current.title} />
          <div className="min-w-0">
            <div className="truncate text-sm font-medium">{current.title}</div>
            <div className="truncate text-xs text-muted">{current.artist || current.uploader_name}</div>
          </div>
        </button>
        {/* 收藏 */}
        <button
          className={`flex shrink-0 items-center justify-center rounded-full p-2 transition ${
            favorited ? 'text-primary' : 'text-muted'
          }`}
          onClick={toggleFavorite}
          title={favorited ? '取消收藏' : '收藏'}
        >
          {favorited ? (
            <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
              <path d="M12 21s-6.7-4.3-9.3-8.5C.6 9.3 2.4 5.5 5.9 5.1c2-.2 3.9.8 6.1 3 2.2-2.2 4.1-3.2 6.1-3 3.5.4 5.3 4.2 3.2 7.4C18.7 16.7 12 21 12 21z" />
            </svg>
          ) : (
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
              <path d="M12 21s-6.7-4.3-9.3-8.5C.6 9.3 2.4 5.5 5.9 5.1c2-.2 3.9.8 6.1 3 2.2-2.2 4.1-3.2 6.1-3 3.5.4 5.3 4.2 3.2 7.4C18.7 16.7 12 21 12 21z" />
            </svg>
          )}
        </button>
        {/* 播放 / 暂停 */}
        <button
          className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-text text-surface transition hover:scale-105 active:scale-95"
          onClick={toggle}
          title={playing ? '暂停' : '播放'}
        >
          {playing ? (
            <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
              <path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z" />
            </svg>
          ) : (
            <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
              <path d="M8 5v14l11-7z" />
            </svg>
          )}
        </button>
      </div>
    </>
  );
}
