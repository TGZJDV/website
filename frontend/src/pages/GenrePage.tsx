import { useEffect, useState } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import SongRow from '../components/SongRow';
import { songsApi } from '../api';
import type { Song, GenreCount } from '../types';

/** 分类页：选择流派，查看该流派歌曲 */
export default function GenrePage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const genre = searchParams.get('genre') || '';
  const [genres, setGenres] = useState<GenreCount[]>([]);
  const [songs, setSongs] = useState<Song[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    songsApi.genres().then((res) => setGenres(res.genres)).catch(() => {});
  }, []);

  useEffect(() => {
    if (!genre) {
      setSongs([]);
      return;
    }
    setLoading(true);
    songsApi
      .list({ genre, limit: 50 })
      .then((res) => setSongs(res.songs))
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [genre]);

  const selectGenre = (g: string) => {
    setSearchParams(g ? { genre: g } : {});
  };

  return (
    <div className="mx-auto max-w-4xl px-4 py-6">
      <h1 className="mb-4 text-2xl font-bold">音乐分类</h1>

      {/* 流派选择 */}
      <div className="mb-6 flex flex-wrap gap-2">
        <button
          className={`rounded-full border px-4 py-1.5 text-sm transition ${
            !genre ? 'border-primary text-primary' : 'border-surface3 text-muted hover:text-text'
          }`}
          onClick={() => selectGenre('')}
        >
          全部
        </button>
        {genres.map((g) => (
          <button
            key={g.genre}
            className={`rounded-full border px-4 py-1.5 text-sm transition ${
              genre === g.genre ? 'border-primary text-primary' : 'border-surface3 text-muted hover:text-text'
            }`}
            onClick={() => selectGenre(g.genre)}
          >
            {g.genre} <span className="text-xs opacity-60">{g.count}</span>
          </button>
        ))}
      </div>

      {!genre ? (
        <div className="rounded-xl border border-dashed border-surface3 py-16 text-center">
          <p className="text-muted">请选择一个分类</p>
        </div>
      ) : loading ? (
        <p className="py-10 text-center text-muted">加载中…</p>
      ) : songs.length === 0 ? (
        <p className="py-10 text-center text-muted">该分类下暂无歌曲</p>
      ) : (
        <div className="space-y-0.5">
          {songs.map((song, i) => (
            <SongRow key={song.id} song={song} index={i + 1} />
          ))}
        </div>
      )}

      <div className="mt-6 text-center">
        <Link to="/upload" className="text-sm text-primary hover:underline">
          没有想要的歌？去上传 →
        </Link>
      </div>
    </div>
  );
}
