import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Cover from '../components/Cover';
import { songsApi } from '../api';
import { usePlayerStore } from '../store/player';
import { useFavorite } from '../hooks/useFavorite';
import { parseLrc, currentLrcIndex, type LrcLine } from '../utils/lrc';
import { formatDuration } from '../utils/format';

/**
 * 全屏播放界面：
 * 左侧大封面 + 下方歌名/歌手；右侧歌词（当前行放大）；底部播放控制
 */
export default function NowPlayingPage() {
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

  const [lyrics, setLyrics] = useState<LrcLine[]>([]);
  const [plainLyrics, setPlainLyrics] = useState<string | null>(null);

  const songId = current?.id;
  const { favorited, toggle: toggleFavorite } = useFavorite(songId);
  const progress = duration > 0 ? Math.min(100, (currentTime / duration) * 100) : 0;

  // 加载当前歌曲歌词
  useEffect(() => {
    setLyrics([]);
    setPlainLyrics(null);
    if (!songId) return;
    songsApi
      .lyrics(songId)
      .then((text) => {
        const lines = parseLrc(text);
        if (lines.length > 0) setLyrics(lines);
        else if (text.trim()) setPlainLyrics(text.trim());
      })
      .catch(() => {});
  }, [songId]);

  if (!current) {
    return (
      <div className="flex h-screen flex-col items-center justify-center gap-4 bg-surface text-muted">
        <p className="text-lg">当前没有正在播放的歌曲</p>
        <button className="btn-primary" onClick={() => navigate('/')}>
          去发现音乐
        </button>
      </div>
    );
  }

  const lineIndex = currentLrcIndex(lyrics, currentTime);

  return (
    <div className="flex h-screen flex-col overflow-hidden bg-gradient-to-br from-surface2 via-surface to-black text-text">
      {/* 顶栏 */}
      <header className="flex h-14 shrink-0 items-center gap-3 px-4">
        <button
          onClick={() => navigate(-1)}
          className="flex h-9 w-9 items-center justify-center rounded-full text-muted transition hover:bg-surface3 hover:text-text"
          title="返回"
        >
          <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
            <path d="M20 11H7.8l5.6-5.6L12 4l-8 8 8 8 1.4-1.4L7.8 13H20v-2z" />
          </svg>
        </button>
        <span className="text-sm text-muted">正在播放</span>
        <span className="ml-auto text-sm text-muted">{current.genre}</span>
      </header>

      {/* 主体：左封面 / 右歌词 */}
      <div className="grid min-h-0 flex-1 grid-cols-1 gap-4 overflow-hidden px-4 pb-2 md:grid-cols-2 md:px-12">
        {/* 左：封面 + 歌曲信息 */}
        <div className="flex flex-col items-center justify-center gap-5">
          <Cover
            song={current}
            title={current.title}
            className="h-44 w-44 rounded-2xl shadow-2xl md:h-72 md:w-72 lg:h-80 lg:w-80"
          />
          <div className="max-w-sm text-center">
            <h1 className="truncate px-2 text-xl font-bold md:text-2xl">{current.title}</h1>
            <p className="mt-1 truncate text-muted">{current.artist || current.uploader_name}</p>
          </div>
        </div>

        {/* 右：歌词（只显示当前行附近，上下各 4 条） */}
        <div className="flex min-h-0 items-center justify-center">
          {plainLyrics ? (
            <div className="max-h-full w-full overflow-x-hidden overflow-y-auto whitespace-pre-wrap break-words px-4 text-center text-base leading-loose text-muted">
              {plainLyrics}
            </div>
          ) : lyrics.length > 0 ? (
            // 渲染全部行，但只显示当前行上下各 4 条；
            // 不可见行用 opacity+高度 控制，实现出现/消失的淡入淡出
            // 外层可垂直滚动，歌词换行超高时不会被裁剪
            <div className="h-full w-full overflow-x-hidden overflow-y-auto px-4 py-6 text-center">
              <div className="flex min-h-full flex-col items-center justify-center">
                {lyrics.map((l, i) => {
                  const isActive = i === lineIndex;
                  const visible = Math.abs(i - lineIndex) <= 4;
                  return (
                    <div
                      key={i}
                      className={`w-full break-words overflow-hidden transition-all duration-150 ${
                        visible ? 'my-1 opacity-100' : 'my-0 max-h-0 opacity-0'
                      } ${
                        isActive
                          ? 'text-xl font-bold leading-relaxed text-white'
                          : 'text-sm leading-relaxed text-muted'
                      }`}
                    >
                      {l.text || '♪'}
                    </div>
                  );
                })}
              </div>
            </div>
          ) : (
            <p className="text-muted">暂无歌词</p>
          )}
        </div>
      </div>

      {/* 底部播放控制：控制按钮 | 进度条 | 音量（一行，移动端隐藏音量） */}
      <div className="flex h-24 shrink-0 items-center gap-3 border-t border-surface3 bg-surface/70 px-4 backdrop-blur sm:gap-6 sm:px-6">
        {/* 控制按钮 + 收藏 */}
        <div className="flex shrink-0 items-center gap-1 sm:gap-4">
          <button
            className={`flex items-center justify-center rounded-full p-2 transition ${
              favorited ? 'text-primary' : 'text-muted hover:text-text'
            }`}
            onClick={toggleFavorite}
            title={favorited ? '取消收藏' : '收藏'}
          >
            <svg
              width="22"
              height="22"
              viewBox="0 0 24 24"
              fill={favorited ? 'currentColor' : 'none'}
              stroke="currentColor"
              strokeWidth="1.5"
            >
              <path d="M12 21s-6.7-4.3-9.3-8.5C.6 9.3 2.4 5.5 5.9 5.1c2-.2 3.9.8 6.1 3 2.2-2.2 4.1-3.2 6.1-3 3.5.4 5.3 4.2 3.2 7.4C18.7 16.7 12 21 12 21z" />
            </svg>
          </button>
          <button
            className="text-muted transition hover:text-text"
            onClick={prev}
            title="上一首"
          >
            <svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor">
              <path d="M6 6h2v12H6V6zm3.5 6l8.5 6V6l-8.5 6z" />
            </svg>
          </button>
          <button
            className="flex h-12 w-12 items-center justify-center rounded-full bg-text text-surface shadow-lg transition hover:scale-105"
            onClick={toggle}
            title={playing ? '暂停' : '播放'}
          >
            {playing ? (
              <svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor">
                <path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z" />
              </svg>
            ) : (
              <svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor">
                <path d="M8 5v14l11-7z" />
              </svg>
            )}
          </button>
          <button
            className="text-muted transition hover:text-text"
            onClick={next}
            title="下一首"
          >
            <svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor">
              <path d="M16 6h2v12h-2V6zM6 18l8.5-6L6 6v12z" />
            </svg>
          </button>
        </div>

        {/* 进度条（中间） */}
        <div className="flex flex-1 items-center gap-3">
          <span className="text-xs tabular-nums text-muted">{formatDuration(currentTime)}</span>
          <input
            type="range"
            min={0}
            max={duration || 0}
            step={0.1}
            value={Math.min(currentTime, duration || 0)}
            onChange={(e) => seek(Number(e.target.value))}
            className="h-1 flex-1 cursor-pointer appearance-none rounded-full accent-primary"
            style={{
              background: `linear-gradient(to right, #0ea5e9 ${progress}%, #2a2a2a ${progress}%)`,
            }}
          />
          <span className="text-xs tabular-nums text-muted">{formatDuration(duration)}</span>
        </div>

        {/* 音量（桌面端显示） */}
        <div className="hidden shrink-0 items-center gap-2 sm:flex">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" className="text-muted">
            <path d="M3 9v6h4l5 5V4L7 9H3zm13.5 3a4.5 4.5 0 0 0-2.5-4v8a4.5 4.5 0 0 0 2.5-4z" />
          </svg>
          <input
            type="range"
            min={0}
            max={1}
            step={0.01}
            value={volume}
            onChange={(e) => setVolume(Number(e.target.value))}
            className="h-1 w-24 cursor-pointer appearance-none rounded-full bg-surface3 accent-primary"
          />
        </div>
      </div>
    </div>
  );
}
