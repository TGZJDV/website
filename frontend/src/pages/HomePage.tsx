import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import SongCard from '../components/SongCard';
import { songsApi } from '../api';
import { usePlayerStore } from '../store/player';
import type { Song, GenreCount } from '../types';

/** 发现页：最新上传 + 流派导航 */
export default function HomePage() {
  const [latest, setLatest] = useState<Song[]>([]);
  const [genres, setGenres] = useState<GenreCount[]>([]);
  const [loading, setLoading] = useState(true);
  const playQueue = usePlayerStore((s) => s.playQueue);

  useEffect(() => {
    Promise.all([songsApi.list({ limit: 12 }), songsApi.genres()])
      .then(([list, genreRes]) => {
        setLatest(list.songs);
        setGenres(genreRes.genres);
      })
      .catch(() => {})
      .finally(() => setLoading(false));
  }, []);

  return (
    <div className="mx-auto max-w-6xl px-4 py-6">
      {/* 横幅 */}
      <div className="mb-8 flex items-center justify-between rounded-2xl bg-gradient-to-r from-primary/20 to-accent/20 p-6">
        <div>
          <h1 className="text-2xl font-bold">欢迎来到云音乐 🎵</h1>
          <p className="mt-1 text-sm text-muted">上传你自己的音乐，与大家分享好听的声音</p>
          <Link to="/upload" className="btn-primary mt-4">
            立即上传
          </Link>
        </div>
      </div>

      {/* 流派入口 */}
      <section className="mb-8">
        <h2 className="mb-3 text-lg font-semibold">音乐分类</h2>
        {genres.length === 0 ? (
          <p className="text-sm text-muted">暂无分类</p>
        ) : (
          <div className="flex flex-wrap gap-2">
            {genres.map((g) => (
              <Link
                key={g.genre}
                to={`/genres?genre=${encodeURIComponent(g.genre)}`}
                className="rounded-full border border-surface3 px-4 py-1.5 text-sm text-muted transition hover:border-primary hover:text-primary"
              >
                {g.genre} <span className="text-xs opacity-60">{g.count}</span>
              </Link>
            ))}
          </div>
        )}
      </section>

      {/* 最新上传 */}
      <section>
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-lg font-semibold">最新上传</h2>
          <Link to="/search" className="text-sm text-primary hover:underline">
            查看全部 →
          </Link>
        </div>

        {loading ? (
          <p className="py-10 text-center text-muted">加载中…</p>
        ) : latest.length === 0 ? (
          <div className="rounded-xl border border-dashed border-surface3 py-16 text-center">
            <p className="text-muted">还没有歌曲，来上传第一首吧！</p>
            <Link to="/upload" className="btn-primary mt-4">
              去上传
            </Link>
          </div>
        ) : (
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6">
            {latest.map((song) => (
              <SongCard key={song.id} song={song} />
            ))}
          </div>
        )}

        {latest.length > 0 && (
          <button className="btn-ghost mt-6 w-full" onClick={() => playQueue(latest)}>
            播放全部
          </button>
        )}
      </section>
    </div>
  );
}
