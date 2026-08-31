import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { songsApi, uploadToPresigned } from '../api';
import { useAuthStore } from '../store/auth';

const GENRES = ['流行', '摇滚', '民谣', '电子', '嘻哈', '古典', '爵士', '国风', '纯音乐', '其他'];

/** 上传页：音频 + 封面 + LRC 歌词 */
export default function UploadPage() {
  const navigate = useNavigate();
  const user = useAuthStore((s) => s.user);

  const [title, setTitle] = useState('');
  const [artist, setArtist] = useState('');
  const [genre, setGenre] = useState(GENRES[0]);
  const [audioFile, setAudioFile] = useState<File | null>(null);
  const [coverFile, setCoverFile] = useState<File | null>(null);
  const [lyricsFile, setLyricsFile] = useState<File | null>(null);
  const [duration, setDuration] = useState(0);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<number | null>(null);

  if (!user) {
    return (
      <div className="py-20 text-center">
        <p className="text-muted">登录后即可上传音乐</p>
        <Link to="/login" className="btn-primary mt-4">
          去登录
        </Link>
      </div>
    );
  }

  /** 选择音频文件：读取时长 + 自动解析 ID3 标签填入表单 */
  const handleAudio = async (file: File) => {
    if (file.size > 100 * 1024 * 1024) {
      setError('音频文件不能超过 100MB');
      return;
    }
    setAudioFile(file);
    setError(null);

    // 读取时长（音频元素，作为兜底）
    const url = URL.createObjectURL(file);
    const audio = new Audio();
    audio.preload = 'metadata';
    audio.onloadedmetadata = () => {
      setDuration((prev) => prev || Math.round(audio.duration || 0));
      URL.revokeObjectURL(url);
    };
    audio.src = url;

    // 自动读取 ID3 标签（MP3/FLAC/M4A/OGG/WAV 等）
    try {
      const mod = await import('music-metadata-browser');
      const parseBlob = (mod.default?.parseBlob ?? mod.parseBlob) as (f: File) => Promise<{
        common: {
          title?: string;
          artist?: string;
          genre?: string[];
          picture?: { format: string; data: Uint8Array }[];
        };
        format: { duration?: number };
        native?: Record<string, { id: string; value?: { text?: string } }[]>;
      }>;
      const metadata = await parseBlob(file);
      const { common, format } = metadata;
      const metaTitle = common.title;
      const metaArtist = common.artist;
      if (metaTitle) setTitle((prev) => prev || metaTitle);
      if (metaArtist) setArtist((prev) => prev || metaArtist);
      if (common.genre && common.genre.length > 0) {
        const g = common.genre[0];
        if (g && GENRES.includes(g)) {
          setGenre((prev) => (prev === GENRES[0] ? g : prev));
        }
      }
      const metaDuration = format.duration;
      if (metaDuration) setDuration((prev) => prev || Math.round(metaDuration));
      // 内嵌封面自动作为封面图
      if (common.picture && common.picture.length > 0) {
        const pic = common.picture[0];
        const bytes = Uint8Array.from(pic.data);
        const blob = new Blob([bytes], { type: pic.format || 'image/jpeg' });
        const cover = new File([blob], 'cover', { type: pic.format || 'image/jpeg' });
        setCoverFile((prev) => prev || cover);
      }
      // 自动读取 ID3 歌词（USLT 帧）并作为歌词文件附加
      const native = metadata.native;
      if (native) {
        let lyricsText: string | undefined;
        for (const frames of Object.values(native)) {
          const uslt = frames?.find((f) => f.id === 'USLT');
          if (uslt?.value?.text) {
            lyricsText = uslt.value.text;
            break;
          }
        }
        if (lyricsText) {
          setLyricsFile((prev) => prev || new File([lyricsText], 'lyrics.txt', { type: 'text/plain' }));
        }
      }
    } catch {
      // 读取失败时静默忽略，用户可手动填写
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!audioFile) {
      setError('请选择音频文件');
      return;
    }
    if (!title.trim()) {
      setError('请填写歌曲标题');
      return;
    }

    setUploading(true);
    try {
      // 1. 向后端获取预签名 PUT URL（含音频/封面/歌词）
      const presigned = await songsApi.presign({
        title: title.trim(),
        artist: artist.trim(),
        genre,
        duration,
        audioName: audioFile.name,
        coverName: coverFile ? coverFile.name : undefined,
        lyricsName: lyricsFile ? lyricsFile.name : undefined,
      });

      // 2. 浏览器直传文件到 OSS（绕过 Worker 中转，国内节点快）
      await uploadToPresigned(presigned.audioUrl, audioFile);
      if (coverFile && presigned.coverUrl) {
        await uploadToPresigned(presigned.coverUrl, coverFile);
      }
      if (lyricsFile && presigned.lyricsUrl) {
        await uploadToPresigned(presigned.lyricsUrl, lyricsFile);
      }

      // 3. 登记歌曲到数据库
      const res = await songsApi.complete({
        title: title.trim(),
        artist: artist.trim(),
        genre,
        duration,
        audioKey: presigned.audioKey,
        coverKey: presigned.coverKey,
        lyricsKey: presigned.lyricsKey,
      });
      setSuccess(res.id);
    } catch (err) {
      setError(err instanceof Error ? err.message : '上传失败');
    } finally {
      setUploading(false);
    }
  };

  if (success) {
    return (
      <div className="mx-auto max-w-md px-4 py-20 text-center">
        <div className="mb-4 text-5xl">🎉</div>
        <h1 className="mb-2 text-2xl font-bold">上传成功！</h1>
        <p className="mb-6 text-muted">你的歌曲已经发布到云音乐</p>
        <div className="flex justify-center gap-3">
          <button className="btn-primary" onClick={() => navigate(`/song/${success}`)}>
            查看歌曲
          </button>
          <button
            className="btn-ghost"
            onClick={() => {
              setSuccess(null);
              setTitle('');
              setArtist('');
              setAudioFile(null);
              setCoverFile(null);
              setLyricsFile(null);
              setDuration(0);
            }}
          >
            再传一首
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-2xl px-4 py-6">
      <h1 className="mb-6 text-2xl font-bold">上传音乐</h1>

      <form onSubmit={handleSubmit} className="space-y-5">
        {/* 音频文件 */}
        <div className="card">
          <label className="mb-2 block text-sm font-medium">音频文件 *</label>
          <input
            type="file"
            accept="audio/*,.mp3,.m4a,.flac,.wav,.ogg"
            className="block w-full text-sm text-muted file:mr-3 file:rounded-full file:border-0 file:bg-surface3 file:px-4 file:py-2 file:text-sm file:font-medium file:text-text hover:file:bg-primary"
            onChange={(e) => e.target.files?.[0] && handleAudio(e.target.files[0])}
            required
          />
          {audioFile && (
            <p className="mt-2 text-xs text-muted">
              {audioFile.name} · {(audioFile.size / 1024 / 1024).toFixed(1)}MB
              {duration > 0 && ` · ${Math.floor(duration / 60)}:${String(duration % 60).padStart(2, '0')}`}
            </p>
          )}
        </div>

        {/* 基本信息 */}
        <div className="card space-y-4">
          <div>
            <label className="mb-1 block text-sm font-medium">歌曲标题 *</label>
            <input className="input-base" value={title} onChange={(e) => setTitle(e.target.value)} placeholder="歌名" required />
          </div>
          <div>
            <label className="mb-1 block text-sm font-medium">歌手 / 艺术家</label>
            <input className="input-base" value={artist} onChange={(e) => setArtist(e.target.value)} placeholder="留空则显示上传者" />
          </div>
          <div>
            <label className="mb-1 block text-sm font-medium">分类</label>
            <select className="input-base" value={genre} onChange={(e) => setGenre(e.target.value)}>
              {GENRES.map((g) => (
                <option key={g} value={g}>
                  {g}
                </option>
              ))}
            </select>
          </div>
        </div>

        {/* 封面与歌词 */}
        <div className="card space-y-4">
          <div>
            <label className="mb-2 block text-sm font-medium">封面图（可选）</label>
            <input
              type="file"
              accept="image/*"
              className="block w-full text-sm text-muted file:mr-3 file:rounded-full file:border-0 file:bg-surface3 file:px-4 file:py-2 file:text-sm file:font-medium file:text-text hover:file:bg-primary"
              onChange={(e) => setCoverFile(e.target.files?.[0] || null)}
            />
            <p className="mt-1 text-xs text-muted">建议 500x500，最大 10MB；可留空自动使用音频内嵌封面</p>
            {coverFile && (
              <p className="mt-1 text-xs text-primary">✓ 封面已选择：{coverFile.name} · {(coverFile.size / 1024).toFixed(0)}KB</p>
            )}
          </div>
          <div>
            <label className="mb-2 block text-sm font-medium">LRC 歌词文件（可选，支持同步滚动）</label>
            <input
              type="file"
              accept=".lrc,.txt"
              className="block w-full text-sm text-muted file:mr-3 file:rounded-full file:border-0 file:bg-surface3 file:px-4 file:py-2 file:text-sm file:font-medium file:text-text hover:file:bg-primary"
              onChange={(e) => setLyricsFile(e.target.files?.[0] || null)}
            />
            <p className="mt-1 text-xs text-muted">
              格式：<code className="text-primary">[00:12.34] 歌词内容</code>；可留空自动使用音频 ID3 内嵌歌词
            </p>
            {lyricsFile && (
              <p className="mt-1 text-xs text-primary">
                ✓ 歌词已选择：{lyricsFile.name} · {(lyricsFile.size / 1024).toFixed(0)}KB
                {lyricsFile.name === 'lyrics.txt' && '（自动提取自音频标签）'}
              </p>
            )}
          </div>
        </div>

        {error && <p className="text-sm text-red-400">{error}</p>}

        <button type="submit" className="btn-primary w-full" disabled={uploading}>
          {uploading ? '上传中…' : '发布歌曲'}
        </button>
      </form>
    </div>
  );
}
