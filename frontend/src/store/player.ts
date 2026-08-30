import { create } from 'zustand';
import type { Song } from '../types';
import { streamUrl } from '../api';

// 全局唯一的音频元素（浏览器端创建）
const audio: HTMLAudioElement | null = typeof window !== 'undefined' ? new Audio() : null;

interface PlayerState {
  current: Song | null;
  queue: Song[];
  index: number;
  playing: boolean;
  currentTime: number;
  duration: number;
  volume: number;

  playSong: (song: Song, queue?: Song[]) => void;
  playQueue: (queue: Song[], startIndex?: number) => void;
  toggle: () => void;
  next: () => void;
  prev: () => void;
  seek: (time: number) => void;
  setVolume: (v: number) => void;
  setCurrentTime: (t: number) => void;
  setDuration: (d: number) => void;
}

export const usePlayerStore = create<PlayerState>((set, get) => {
  // 绑定音频事件（只在浏览器环境执行一次）
  if (audio) {
    audio.addEventListener('timeupdate', () => {
      set({ currentTime: audio.currentTime, duration: audio.duration || 0 });
    });
    audio.addEventListener('ended', () => {
      get().next();
    });
    audio.addEventListener('loadedmetadata', () => {
      set({ duration: audio.duration || 0 });
    });
    audio.addEventListener('play', () => set({ playing: true }));
    audio.addEventListener('pause', () => set({ playing: false }));
  }

  const loadSong = (song: Song) => {
    if (!audio) return;
    audio.src = streamUrl(song.id);
    audio.play().catch(() => {
      /* 自动播放被浏览器阻止时忽略 */
    });
  };

  return {
    current: null,
    queue: [],
    index: -1,
    playing: false,
    currentTime: 0,
    duration: 0,
    volume: 0.8,

    playSong: (song, queue) => {
      const q = queue && queue.length > 0 ? queue : [song];
      const idx = q.findIndex((s) => s.id === song.id);
      set({
        current: song,
        queue: q,
        index: idx === -1 ? 0 : idx,
        currentTime: 0,
        duration: song.duration || 0,
      });
      loadSong(song);
    },

    playQueue: (queue, startIndex = 0) => {
      const safe = Math.max(0, Math.min(startIndex, queue.length - 1));
      const song = queue[safe];
      if (!song) return;
      set({ queue, index: safe, current: song, currentTime: 0, duration: song.duration || 0 });
      loadSong(song);
    },

    toggle: () => {
      if (!audio || !get().current) return;
      if (audio.paused) audio.play();
      else audio.pause();
    },

    next: () => {
      const { queue, index } = get();
      if (queue.length === 0) return;
      const nextIndex = (index + 1) % queue.length;
      const song = queue[nextIndex];
      set({ index: nextIndex, current: song, currentTime: 0 });
      loadSong(song);
    },

    prev: () => {
      const { queue, index } = get();
      if (queue.length === 0) return;
      // 若已播放超过 3 秒，则回到开头
      if (audio && audio.currentTime > 3) {
        audio.currentTime = 0;
        return;
      }
      const prevIndex = (index - 1 + queue.length) % queue.length;
      const song = queue[prevIndex];
      set({ index: prevIndex, current: song, currentTime: 0 });
      loadSong(song);
    },

    seek: (time) => {
      if (audio && Number.isFinite(time)) {
        audio.currentTime = Math.max(0, time);
        set({ currentTime: audio.currentTime });
      }
    },

    setVolume: (v) => {
      const vol = Math.max(0, Math.min(1, v));
      if (audio) audio.volume = vol;
      set({ volume: vol });
    },

    setCurrentTime: (t) => set({ currentTime: t }),
    setDuration: (d) => set({ duration: d }),
  };
});
