import type { Format } from './types.js';

export type HlsParse =
  | { kind: 'master'; mediaUri: string | null }
  | { kind: 'media'; segments: number; ended: boolean }
  | { kind: 'invalid' };

export function parseHls(text: string): HlsParse {
  const lines = text.split(/\r?\n/).map(l => l.trim());
  if (!lines[0]?.startsWith('#EXTM3U')) return { kind: 'invalid' };
  const isMaster = lines.some(l => l.startsWith('#EXT-X-STREAM-INF'));
  if (isMaster) {
    let mediaUri: string | null = null;
    for (let i = 0; i < lines.length; i++) {
      if (lines[i].startsWith('#EXT-X-STREAM-INF')) {
        const next = lines.slice(i + 1).find(l => l.length > 0 && !l.startsWith('#'));
        if (next) { mediaUri = next; break; }
      }
    }
    return { kind: 'master', mediaUri };
  }
  const segments = lines.filter(l => l.startsWith('#EXTINF')).length;
  const ended = lines.some(l => l.startsWith('#EXT-X-ENDLIST'));
  return { kind: 'media', segments, ended };
}

export function isTsSync(head: Uint8Array): boolean {
  return head.length > 188 && head[0] === 0x47 && head[188] === 0x47;
}

export function isMpd(text: string): boolean {
  return /<MPD[\s>]/.test(text) && /<Period[\s/>]/.test(text);
}

function pathOf(url: string): string {
  try { return new URL(url).pathname.toLowerCase(); } catch { return url.toLowerCase(); }
}

export function detectFormat(url: string, contentType: string | null, head: Uint8Array): Format {
  const ct = (contentType ?? '').toLowerCase();
  if (ct.includes('mpegurl') || ct.includes('x-mpegurl')) return 'hls';
  if (ct.includes('mp2t')) return 'ts';
  if (ct.includes('dash+xml')) return 'dash';

  const p = pathOf(url);
  if (p.endsWith('.m3u8') || p.endsWith('.m3u')) return 'hls';
  if (p.endsWith('.ts')) return 'ts';
  if (p.endsWith('.mpd')) return 'dash';

  const text = new TextDecoder('utf-8', { fatal: false }).decode(head.slice(0, 512));
  if (text.trimStart().startsWith('#EXTM3U')) return 'hls';
  if (isTsSync(head)) return 'ts';
  if (isMpd(text)) return 'dash';
  return 'unknown';
}
