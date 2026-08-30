import { useEffect, useState } from 'react';
import { songsApi } from '../api';
import { useAuthStore } from '../store/auth';

/**
 * 收藏状态 hook：根据歌曲 id 管理"是否已收藏 / 收藏数 / 切换收藏"
 * 未登录时 toggle 无操作，favorited 恒为 false
 */
export function useFavorite(songId?: number) {
  const user = useAuthStore((s) => s.user);
  const [favorited, setFavorited] = useState(false);
  const [favoriteCount, setFavoriteCount] = useState(0);

  useEffect(() => {
    setFavorited(false);
    setFavoriteCount(0);
    if (!songId) return;
    let cancelled = false;
    songsApi
      .get(songId)
      .then((res) => {
        if (cancelled) return;
        setFavoriteCount(res.favoriteCount);
        setFavorited(!!res.favorited);
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [songId, user?.id]);

  const toggle = async () => {
    if (!songId || !user) return false;
    try {
      const res = favorited
        ? await songsApi.unfavorite(songId)
        : await songsApi.favorite(songId);
      setFavorited(res.favorite);
      setFavoriteCount(res.favoriteCount);
      return true;
    } catch {
      return false;
    }
  };

  return { favorited, favoriteCount, toggle };
}
