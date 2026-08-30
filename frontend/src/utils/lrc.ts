// LRC 歌词解析与同步

export interface LrcLine {
  time: number; // 秒
  text: string;
}

/** 解析 LRC 歌词文本 */
export function parseLrc(lrc: string): LrcLine[] {
  const lines = lrc.split(/\r?\n/);
  const result: LrcLine[] = [];
  const timeRe = /\[(\d{1,2}):(\d{1,2})(?:[.:](\d{1,3}))?\]/g;

  for (const raw of lines) {
    const matches = [...raw.matchAll(timeRe)];
    if (matches.length === 0) continue;
    const text = raw.replace(timeRe, '').trim();
    for (const m of matches) {
      const min = parseInt(m[1], 10);
      const sec = parseInt(m[2], 10);
      let ms = parseInt(m[3] || '0', 10);
      // 两位毫秒补成三位
      if (m[3] && m[3].length === 2) ms *= 10;
      result.push({ time: min * 60 + sec + ms / 1000, text });
    }
  }

  result.sort((a, b) => a.time - b.time);
  return result;
}

/** 根据当前播放时间返回当前歌词行的索引 */
export function currentLrcIndex(lines: LrcLine[], time: number): number {
  if (lines.length === 0) return -1;
  let index = -1;
  for (let i = 0; i < lines.length; i++) {
    if (lines[i].time <= time) index = i;
    else break;
  }
  return index;
}
