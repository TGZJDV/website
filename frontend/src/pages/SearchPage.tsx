import { useEffect, useState } from 'react';
import SongRow from '../components/SongRow';
import { songsApi } from '../api';
import type { Song } from '../types';

/** 搜索页 */
export default function SearchPage() {
  const [q, setQ] = useState('');
  const [songs, setSongs] = useState<Song[]>([]);
  const [searched, setSearched] = useState(false);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);

  const doSearch = async (keyword: string) => {
    const kw = keyword.trim();
    if (!kw) {
      setSongs([]);
      setSearched(false);
      return;
    }
    setLoading(true);
    try {
      const res = await songsApi.list({ q: kw, limit: 50 });
      setSongs(res.songs);
      setTotal(res.total);
    } catch {
      setSongs([]);
    } finally {
      setSearched(true);
      setLoading(false);
    }
  };

  useEffect(() => {
    // 首次进入展示全部
    songsApi.list({ limit: 20 }).then((res) => {
      setSongs(res.songs);
      setTotal(res.total);
    });
  }, []);

  return (
    <div className="mx-auto max-w-4xl px-4 py-6">
      <h1 className="mb-4 text-2xl font-bold">搜索音乐</h1>

      <form
        className="mb-6 flex gap-2"
        onSubmit={(e) => {
          e.preventDefault();
          doSearch(q);
        }}
      >
        <input
          className="input-base flex-1"
          placeholder="输入歌名或歌手…"
          value={q}
          onChange={(e) => setQ(e.target.value)}
        />
        <button type="submit" className="btn-primary">
          搜索
        </button>
      </form>

      {loading ? (
        <p className="py-10 text-center text-muted">搜索中…</p>
      ) : searched && songs.length === 0 ? (
        <p className="py-10 text-center text-muted">没有找到「{q}」相关歌曲</p>
      ) : (
        <>
          <p className="mb-3 text-sm text-muted">
            {searched ? `「${q}」的搜索结果` : '全部歌曲'} · {total} 首
          </p>
          <div className="space-y-0.5">
            {songs.map((song, i) => (
              <SongRow key={song.id} song={song} index={i + 1} />
            ))}
          </div>
        </>
      )}
    </div>
  );
}
