import { Link } from 'react-router-dom';
import Cover from './Cover';
import { formatDuration, formatRelative } from '../utils/format';
import { usePlayerStore } from '../store/player';
import type { Song } from '../types';

interface SongCardProps {
  song: Song;
}

/** 网格卡片：封面 + 标题 + 歌手 + 时长 */
export default function SongCard({ song }: SongCardProps) {
  const playSong = usePlayerStore((s) => s.playSong);

  return (
    <div
      className="group card cursor-pointer hover:bg-surface3"
      onClick={() => playSong(song)}
    >
      <div className="relative">
        <Cover song={song} title={song.title} className="w-full aspect-square rounded-lg" />
        <button
          className="absolute bottom-2 right-2 flex h-10 w-10 items-center justify-center rounded-full bg-primary text-white opacity-100 shadow-lg transition sm:opacity-0 sm:group-hover:opacity-100"
          onClick={(e) => {
            e.stopPropagation();
            playSong(song);
          }}
          title="播放"
        >
          <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
            <path d="M8 5v14l11-7z" />
          </svg>
        </button>
      </div>
      <Link
        to={`/song/${song.id}`}
        className="mt-2 block truncate text-sm font-medium hover:text-primary"
        onClick={(e) => e.stopPropagation()}
      >
        {song.title}
      </Link>
      <div className="mt-0.5 flex items-center justify-between text-xs text-muted">
        <span className="truncate">{song.artist || song.uploader_name}</span>
        <span>{formatDuration(song.duration)}</span>
      </div>
      <div className="text-[11px] text-muted/70">{formatRelative(song.created_at)}</div>
    </div>
  );
}
