import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { downloadApi, downloadFileUrl } from '../api';
import type { ClientRelease } from '../types';

/** 各平台图标 */
function PlatformIcon({ id }: { id: string }) {
  const cls = 'h-7 w-7';
  switch (id) {
    case 'android':
      return (
        <svg className={cls} viewBox="0 0 24 24" fill="currentColor">
          <path d="M17.6 9.48l1.84-3.18a.4.4 0 0 0-.7-.4l-1.86 3.22a11.4 11.4 0 0 0-9.76 0L5.26 5.9a.4.4 0 0 0-.7.4L6.4 9.48A10.8 10.8 0 0 0 1 18h22a10.8 10.8 0 0 0-5.4-8.52zM7 15.25a1.05 1.05 0 1 1 0-2.1 1.05 1.05 0 0 1 0 2.1zm10 0a1.05 1.05 0 1 1 0-2.1 1.05 1.05 0 0 1 0 2.1z" />
        </svg>
      );
    case 'windows':
      return (
        <svg className={cls} viewBox="0 0 24 24" fill="currentColor">
          <path d="M3 5.5l7.2-1v7H3v-6zm0 13l7.2 1v-6.9H3v5.9zm8.4 1.1L21 21V12.5h-9.6v7.1zm0-15.2v7.2H21V3l-9.6 1.4z" />
        </svg>
      );
    case 'ios':
      return (
        <svg className={cls} viewBox="0 0 24 24" fill="currentColor">
          <path d="M16.4 12.7c0-2.4 2-3.6 2.1-3.6-1.1-1.7-2.9-1.9-3.5-1.9-1.5-.2-2.9.9-3.7.9-.8 0-1.9-.9-3.2-.8-1.6 0-3.1 1-4 2.4-1.7 3-.4 7.4 1.2 9.8.8 1.2 1.8 2.5 3 2.4 1.2 0 1.7-.8 3.1-.8s1.9.8 3.2.8 2.2-1.2 3-2.4c.9-1.4 1.3-2.7 1.3-2.8 0 0-2.4-1-2.5-3.9zM14 5.5c.7-.8 1.1-1.9 1-3-1 .1-2.1.6-2.8 1.4-.6.7-1.1 1.9-1 2.9 1.1.1 2.2-.5 2.8-1.3z" />
        </svg>
      );
    default:
      return (
        <svg className={cls} viewBox="0 0 24 24" fill="currentColor">
          <path d="M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm6.9 6h-2.6a15 15 0 0 0-1.2-2.7A8 8 0 0 1 18.9 8zM12 4.1c.7 1 1.3 2.3 1.7 3.9h-3.4c.4-1.6 1-2.9 1.7-3.9zM4.3 14a7.8 7.8 0 0 1 0-4h3a17 17 0 0 0 0 4h-3zm.8 2h2.6c.3 1 .8 1.9 1.2 2.7A8 8 0 0 1 5.1 16zm2.6-8H5.1a8 8 0 0 1 3.8-2.7A15 15 0 0 0 7.7 8zM12 19.9c-.7-1-1.3-2.3-1.7-3.9h3.4c-.4 1.6-1 2.9-1.7 3.9zM14.2 14H9.8a15 15 0 0 1 0-4h4.4a15 15 0 0 1 0 4zm.9 4.7c.4-.8.9-1.7 1.2-2.7h2.6a8 8 0 0 1-3.8 2.7zm1.6-4.7a17 17 0 0 0 0-4h3a7.8 7.8 0 0 1 0 4h-3z" />
        </svg>
      );
  }
}

/** 单个平台卡片 */
function ReleaseCard({ release }: { release: ClientRelease }) {
  const [copied, setCopied] = useState(false);

  const copySha = async () => {
    if (!release.sha256) return;
    try {
      await navigator.clipboard.writeText(release.sha256);
      setCopied(true);
      setTimeout(() => setCopied(false), 1600);
    } catch {
      setCopied(false);
    }
  };

  const isAndroid = release.id === 'android';

  return (
    <div className="card flex flex-col">
      <div className="flex items-start gap-3">
        <div
          className={`flex h-12 w-12 shrink-0 items-center justify-center rounded-xl ${
            release.available ? 'bg-primary/15 text-primary' : 'bg-surface3 text-muted'
          }`}
        >
          <PlatformIcon id={release.id} />
        </div>

        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <h3 className="text-lg font-semibold">{release.name}</h3>
            {release.available && release.version ? (
              <span className="rounded-full bg-primary/15 px-2 py-0.5 text-xs font-medium text-primary">
                v{release.version}
              </span>
            ) : (
              <span className="rounded-full bg-surface3 px-2 py-0.5 text-xs text-muted">
                {release.note || '暂未提供'}
              </span>
            )}
          </div>
          <p className="mt-0.5 text-sm text-muted">{release.subtitle}</p>
        </div>
      </div>

      {release.available ? (
        <>
          <dl className="mt-4 grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
            {release.sizeText && (
              <div>
                <dt className="text-xs text-muted">安装包大小</dt>
                <dd>{release.sizeText}</dd>
              </div>
            )}
            {release.minOs && (
              <div>
                <dt className="text-xs text-muted">系统要求</dt>
                <dd>{release.minOs}</dd>
              </div>
            )}
            {release.publishedAt && (
              <div>
                <dt className="text-xs text-muted">发布日期</dt>
                <dd>{release.publishedAt}</dd>
              </div>
            )}
            <div>
              <dt className="text-xs text-muted">签名</dt>
              <dd>已使用 release 证书签名</dd>
            </div>
          </dl>

          {release.sha256 && (
            <details className="mt-4 rounded-lg border border-surface3 px-3 py-2 text-xs">
              <summary className="cursor-pointer text-muted select-none">
                校验信息（SHA-256）
              </summary>
              <div className="mt-2 flex items-start gap-2">
                <code className="min-w-0 flex-1 break-all text-[11px] leading-relaxed text-muted">
                  {release.sha256}
                </code>
                <button
                  className="shrink-0 rounded-md border border-surface3 px-2 py-1 transition hover:border-primary hover:text-primary"
                  onClick={copySha}
                >
                  {copied ? '已复制' : '复制'}
                </button>
              </div>
            </details>
          )}

          {isAndroid && (
            <p className="mt-3 rounded-lg bg-surface2 px-3 py-2 text-xs text-muted">
              安装提示：下载后点开 APK，系统可能提示「未知来源应用」。需在
              <span className="text-text"> 设置 → 安全 → 安装未知应用 </span>
              中允许你的浏览器安装。本包已用正式证书签名，非调试版本。
            </p>
          )}

          {/* 安装包由 Pages 同源托管（/downloads/），直接跳转即可 */}
          <a
            className="btn-primary mt-4 w-full text-center"
            href={release.file ? downloadFileUrl(release.file) : '#'}
          >
            下载 {release.name} 版
          </a>
        </>
      ) : (
        <p className="mt-4 text-sm text-muted">
          还没有发布。想要的话可以在「我的」页面留言催更 😄
        </p>
      )}
    </div>
  );
}

/** 下载页：按平台分列客户端 */
export default function DownloadPage() {
  const [releases, setReleases] = useState<ClientRelease[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    downloadApi
      .releases()
      .then((res) => setReleases(res.releases))
      .catch((err) => setError(err instanceof Error ? err.message : '加载失败'));
  }, []);

  const available = releases?.filter((r) => r.available) ?? [];
  const upcoming = releases?.filter((r) => !r.available) ?? [];

  return (
    <div className="mx-auto max-w-4xl px-4 py-6">
      <header className="mb-8">
        <h1 className="text-2xl font-bold">下载客户端</h1>
        <p className="mt-1 text-sm text-muted">
          同一个账号，全平台同步你的音乐、歌单和收藏。
        </p>
      </header>

      {/* 网页版 */}
      <section className="mb-8">
        <h2 className="mb-3 text-lg font-semibold">在线使用</h2>
        <Link
          to="/"
          className="card flex items-center justify-between transition hover:bg-surface3"
        >
          <div className="flex items-center gap-3">
            <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-accent/15 text-accent">
              <PlatformIcon id="web" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h3 className="text-lg font-semibold">网页版</h3>
                <span className="rounded-full bg-accent/15 px-2 py-0.5 text-xs font-medium text-accent">
                  免安装
                </span>
              </div>
              <p className="mt-0.5 text-sm text-muted">
                浏览器直接打开，手机电脑都能用
              </p>
            </div>
          </div>
          <span className="hidden text-sm text-primary sm:inline">去听歌 →</span>
        </Link>
      </section>

      {/* 桌面 / 移动客户端 */}
      <section>
        <h2 className="mb-3 text-lg font-semibold">客户端</h2>

        {error && (
          <div className="rounded-xl border border-dashed border-surface3 py-10 text-center">
            <p className="text-muted">{error}</p>
          </div>
        )}

        {!releases && !error && <p className="py-10 text-center text-muted">加载中…</p>}

        {releases && (
          <div className="grid gap-4 sm:grid-cols-2">
            {[...available, ...upcoming].map((r) => (
              <ReleaseCard key={r.id} release={r} />
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
