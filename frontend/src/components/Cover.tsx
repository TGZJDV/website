import { coverUrl } from '../api';
import type { Song } from '../types';

interface CoverProps {
  song: Pick<Song, 'cover_key' | 'id'>;
  title: string;
  className?: string;
}

/** 歌曲封面（无封面时显示默认音符占位图） */
export default function Cover({ song, title, className = 'w-12 h-12' }: CoverProps) {
  const src = coverUrl(song);
  return (
    <div className={`relative shrink-0 overflow-hidden rounded-md bg-surface3 ${className}`}>
      {src ? (
        <img src={src} alt={title} className="h-full w-full object-cover" loading="lazy" />
      ) : (
        <div className="flex h-full w-full items-center justify-center text-muted">
          <svg width="40%" height="40%" viewBox="0 0 24 24" fill="currentColor">
            <path d="M12 3v10.55A4 4 0 1 0 14 17V7h4V3h-6z" />
          </svg>
        </div>
      )}
    </div>
  );
}
