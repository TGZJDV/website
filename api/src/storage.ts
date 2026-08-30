// ============================================================
// Backblaze B2 存储封装（S3 兼容）
// 替换原 Cloudflare R2 绑定：写入/删除/读取均通过 AWS Signature V4 签名。
// 桶保持 **Private（私有）**：B2 的 Public 公开桶要求绑定付款方式（1 美元验证费），
// 而私有桶无需绑卡。读取时 Worker 生成**预签名 URL**（query-parameter 签名），
// 再 307 重定向给浏览器直连 B2（支持 Range 流式播放）。
// ============================================================
import type { Env } from './types';

const encoder = new TextEncoder();

/** Uint8Array -> ArrayBuffer（复制，避免 SharedArrayBuffer 类型问题） */
function toArrayBuffer(data: Uint8Array): ArrayBuffer {
  const copy = new Uint8Array(data.byteLength);
  copy.set(data);
  return copy.buffer;
}

function bufToHex(buf: ArrayBuffer): string {
  return [...new Uint8Array(buf)]
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('');
}

async function sha256Hex(data: ArrayBuffer | Uint8Array | string): Promise<string> {
  let bytes: ArrayBuffer;
  if (typeof data === 'string') bytes = encoder.encode(data).buffer as ArrayBuffer;
  else if (data instanceof ArrayBuffer) bytes = data;
  else bytes = toArrayBuffer(data);
  const hash = await crypto.subtle.digest('SHA-256', bytes);
  return bufToHex(hash);
}

async function hmacSha256(key: ArrayBuffer, data: string): Promise<ArrayBuffer> {
  const k = await crypto.subtle.importKey(
    'raw',
    key,
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['sign']
  );
  return crypto.subtle.sign(
    'HMAC',
    k,
    encoder.encode(data).buffer as ArrayBuffer
  );
}

async function getSignatureKey(
  secret: string,
  dateStamp: string,
  region: string,
  service: string
): Promise<ArrayBuffer> {
  const kDate = await hmacSha256(
    encoder.encode('AWS4' + secret).buffer as ArrayBuffer,
    dateStamp
  );
  const kRegion = await hmacSha256(kDate, region);
  const kService = await hmacSha256(kRegion, service);
  return hmacSha256(kService, 'aws4_request');
}

function amzDateOf(date: Date): string {
  return date.toISOString().replace(/[:-]|\.\d{3}/g, '');
}

/**
 * B2 存储操作。
 * 依赖 env：
 *   B2_ACCESS_KEY  - Application Key ID（KeyID）
 *   B2_SECRET_KEY  - Application Key
 *   B2_BUCKET      - 桶名（需公开 Public）
 *   B2_REGION      - 桶所在区域，如 us-west-004（可选，默认 us-west-004）
 */
export class B2Storage {
  private env: Env;

  constructor(env: Env) {
    this.env = env;
  }

  private get region(): string {
    return this.env.B2_REGION || 'us-west-004';
  }

  /** 读取/上传/删除共用主机（virtual-hosted style：bucket.s3.region） */
  private get publicHost(): string {
    return `${this.env.B2_BUCKET}.s3.${this.region}.backblazeb2.com`;
  }

  /**
   * 生成预签名 GET URL（query-parameter Signature V4）。
   * 私有桶对象需带签名才能读取；URL 默认 1 小时有效，浏览器直连 B2，支持 Range。
   */
  async getSignedUrl(key: string, expiresSeconds = 3600): Promise<string> {
    const amzDate = amzDateOf(new Date());
    const dateStamp = amzDate.slice(0, 8);
    const host = this.publicHost;
    const scope = `${dateStamp}/${this.region}/s3/aws4_request`;
    const credential = encodeURIComponent(`${this.env.B2_ACCESS_KEY}/${scope}`);
    const canonicalQuery = [
      'X-Amz-Algorithm=AWS4-HMAC-SHA256',
      `X-Amz-Credential=${credential}`,
      `X-Amz-Date=${amzDate}`,
      `X-Amz-Expires=${expiresSeconds}`,
      'X-Amz-SignedHeaders=host',
    ].join('&');

    const canonicalRequest = [
      'GET',
      '/' + key,
      canonicalQuery,
      `host:${host}\n`,
      'host',
      'UNSIGNED-PAYLOAD',
    ].join('\n');

    const stringToSign = [
      'AWS4-HMAC-SHA256',
      amzDate,
      scope,
      await sha256Hex(canonicalRequest),
    ].join('\n');

    const signingKey = await getSignatureKey(
      this.env.B2_SECRET_KEY,
      dateStamp,
      this.region,
      's3'
    );
    const signature = bufToHex(await hmacSha256(signingKey, stringToSign));

    return `https://${host}/${key}?${canonicalQuery}&X-Amz-Signature=${signature}`;
  }

  /** 生成 S3 签名请求头（AWS Signature V4） */
  private async signedHeaders(
    method: string,
    key: string,
    headers: Record<string, string>,
    payloadHash: string,
    date: Date
  ): Promise<Record<string, string>> {
    const amzDate = amzDateOf(date);
    const dateStamp = amzDate.slice(0, 8);
    // B2 要求 x-amz-date 必须参与签名，故统一加入签名 header 集合
    const allHeaders: Record<string, string> = { ...headers, 'x-amz-date': amzDate };

    // 规范请求（canonical headers 必须按 header 名 ASCII 排序）
    const canonicalUri = '/' + key;
    const canonicalQuery = '';
    const sortedEntries = Object.entries(allHeaders).sort(([a], [b]) =>
      a.toLowerCase() < b.toLowerCase() ? -1 : 1
    );
    const canonicalHeaders = sortedEntries
      .map(([k, v]) => `${k.toLowerCase()}:${v.trim()}\n`)
      .join('');
    const signedHeadersList = sortedEntries
      .map(([k]) => k.toLowerCase())
      .join(';');
    const canonicalRequest = [
      method,
      canonicalUri,
      canonicalQuery,
      canonicalHeaders,
      signedHeadersList,
      payloadHash,
    ].join('\n');

    const scope = `${dateStamp}/${this.region}/s3/aws4_request`;
    const stringToSign = [
      'AWS4-HMAC-SHA256',
      amzDate,
      scope,
      await sha256Hex(canonicalRequest),
    ].join('\n');

    const signingKey = await getSignatureKey(
      this.env.B2_SECRET_KEY,
      dateStamp,
      this.region,
      's3'
    );
    const signature = bufToHex(await hmacSha256(signingKey, stringToSign));

    return {
      authorization: `AWS4-HMAC-SHA256 Credential=${this.env.B2_ACCESS_KEY}/${scope}, SignedHeaders=${signedHeadersList}, Signature=${signature}`,
      'x-amz-date': amzDate,
      'x-amz-content-sha256': payloadHash,
    };
  }

  /** 上传对象（data 为文件字节） */
  async put(
    key: string,
    data: ArrayBuffer | Uint8Array,
    contentType: string
  ): Promise<void> {
    const bytes = data instanceof ArrayBuffer ? new Uint8Array(data) : data;
    // S3 上传采用 UNSIGNED-PAYLOAD（AWS 标准做法，无需预计算 body hash）
    const payloadHash = 'UNSIGNED-PAYLOAD';
    const date = new Date();
    // virtual-hosted style：host = bucket.s3.region，canonical URI = /key
    const host = this.publicHost;
    const signingHeaders: Record<string, string> = {
      host,
      'content-type': contentType,
      'x-amz-content-sha256': payloadHash,
    };
    const signed = await this.signedHeaders('PUT', key, signingHeaders, payloadHash, date);

    const res = await fetch(`https://${host}/${key}`, {
      method: 'PUT',
      headers: { 'content-type': contentType, ...signed },
      body: new Blob([toArrayBuffer(bytes)], { type: contentType }),
    });
    if (!res.ok) {
      const errText = await res.text().catch(() => '');
      throw new Error(`B2 上传失败(${res.status}): ${errText}`);
    }
  }

  /** 删除对象（404 视为已删除，不报错） */
  async remove(key: string): Promise<void> {
    const date = new Date();
    const payloadHash = await sha256Hex(''); // 空 body
    const host = this.publicHost;
    const headers: Record<string, string> = {
      host,
      'content-type': 'application/xml',
      'x-amz-content-sha256': payloadHash,
    };
    const signed = await this.signedHeaders('DELETE', key, headers, payloadHash, date);

    const res = await fetch(`https://${host}/${key}`, {
      method: 'DELETE',
      headers: { ...headers, ...signed },
    });
    if (res.status !== 204 && res.status !== 200 && res.status !== 404) {
      const errText = await res.text().catch(() => '');
      throw new Error(`B2 删除失败(${res.status}): ${errText}`);
    }
  }

  /** 服务器端读取对象文本（如歌词，用短时预签名 URL） */
  async getText(key: string): Promise<string | null> {
    const res = await fetch(await this.getSignedUrl(key, 600));
    if (!res.ok) return null;
    return res.text();
  }
}

/** 便捷工厂：从 env 构造 B2 存储 */
export function b2(env: Env): B2Storage {
  return new B2Storage(env);
}
