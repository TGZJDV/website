// ============================================================
// 客户端下载：发布表（元数据）
//
// 安装包本体由 Cloudflare Pages 静态托管（frontend/public/downloads/），
// 与前端同源，前端直接链到 /downloads/<file> 即可。
//
// 为什么不用 OSS：阿里云禁止通过 OSS 默认域名分发 .apk 文件，会返回
// ApkDownloadForbidden（"please use CNAME instead"）。
// 若将来给 OSS 桶绑定了自定义域名，可把 file 换成完整 URL。
//
// 新增平台 / 发新版本：改 RELEASES 表 + 把 APK 放进 public/downloads/，
// 后端重新部署即可，前端页面自动跟随（无需改前端代码）。
// ============================================================
import { Hono } from 'hono';
import type { Env } from '../types';

const downloadRoutes = new Hono<{ Bindings: Env }>();

/** 单个平台的发布信息 */
interface Release {
  /** 平台标识，同时作为下载路由参数 */
  id: string;
  /** 展示名 */
  name: string;
  /** 一句话说明 */
  subtitle: string;
  /** 是否可下载 */
  available: boolean;
  /** 版本号（available=false 时可为空） */
  version?: string;
  /** 内部版本号 */
  versionCode?: number;
  /** 安装包字节数 */
  size?: number;
  /** 最低系统要求 */
  minOs?: string;
  /** 发布日期 YYYY-MM-DD */
  publishedAt?: string;
  /** 安装包文件名（位于前端 /downloads/ 目录下） */
  file?: string;
  /** 安装包 SHA-256（供用户校验） */
  sha256?: string;
  /** 多卷分发的其余文件（第 1 卷放在 file，这里放剩下几卷） */
  extraFiles?: string[];
  /** 额外的安装说明（多卷解压、安装步骤等） */
  installNote?: string;
  /** 不可下载时的说明文案 */
  note?: string;
}

/** 发布表：改这里即可上线新版本 */
const RELEASES: Release[] = [
  {
    id: 'android',
    name: 'Android',
    subtitle: '原生客户端 · Kotlin + Jetpack Compose',
    available: true,
    version: '1.1.1',
    versionCode: 3,
    size: 17175268,
    minOs: 'Android 7.0 (API 24) 及以上',
    publishedAt: '2026-10-17',
    file: 'TGZJDVsMusic-1.1.1.apk',
    sha256: '320e16566a8c6581125506573e39cc2d3c0d88c1cdf346bd0ed20036e342fd4a',
  },
  {
    id: 'windows',
    name: 'Windows',
    subtitle: '桌面客户端 · WinUI 3 + .NET 8',
    available: true,
    version: '1.1.1',
    versionCode: 1,
    // 两卷合计大小
    size: 32313076,
    minOs: 'Windows 10 1809 (x64) 及以上',
    publishedAt: '2026-10-18',
    file: 'FamousMusic-1.1.1-win-x64-part1.zip',
    extraFiles: ['FamousMusic-1.1.1-win-x64-part2.zip'],
    sha256: 'f027973f4fef4f2b418ef5158bf05e66c3ddc1669dc228d98f1f36fcd3d43840',
    installNote:
      '分两卷下载：两个 zip 都下载后，把 part2 里的文件解压到 part1 解压出的同一目录（覆盖合并），再双击 FamousMusic.exe。已内置 Windows App SDK 运行时，无需另外安装。',
  },
  {
    id: 'ios',
    name: 'iOS',
    subtitle: 'iPhone / iPad 客户端',
    available: false,
    note: '开发中',
  },
];

/** 字节数 -> 人类可读 */
function formatSize(bytes?: number): string | null {
  if (!bytes) return null;
  return `${(bytes / 1048576).toFixed(2)} MB`;
}

/** 发布列表（供下载页渲染） */
downloadRoutes.get('/releases', (c) => {
  return c.json({
    releases: RELEASES.map((r) => ({
      id: r.id,
      name: r.name,
      subtitle: r.subtitle,
      available: r.available,
      version: r.version ?? null,
      versionCode: r.versionCode ?? null,
      size: r.size ?? null,
      sizeText: formatSize(r.size),
      minOs: r.minOs ?? null,
      publishedAt: r.publishedAt ?? null,
      sha256: r.sha256 ?? null,
      note: r.note ?? null,
      // 安装包文件名（前端拼成同源 /downloads/<file>）
      file: r.available ? (r.file ?? null) : null,
      extraFiles: r.available ? (r.extraFiles ?? []) : [],
      installNote: r.installNote ?? null,
    })),
  });
});

export default downloadRoutes;
