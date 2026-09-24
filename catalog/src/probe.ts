import { detectFormat, parseHls, isTsSync, isMpd } from './detect.js';
import type { FetchFn, GroupedStream, ProbeResult, Health, Format } from './types.js';

export const DEFAULT_UA = 'TVApp/1.0 (Android TV; Media3)';
const HEAD_BYTES = 64 * 1024;

export function hostOf(url: string): string {
  try { return new URL(url).hostname; } catch { return url; }
}

interface Got { status: number; contentType: string | null; head: Uint8Array; finalUrl: string; ms: number }

async function get(url: string, headers: Record<string, string>, fetchFn: FetchFn, timeoutMs: number, now: () => number): Promise<Got> {
  const ac = new AbortController();
  const timer = setTimeout(() => ac.abort(), timeoutMs);
  const t0 = now();
  try {
    const res = await fetchFn(url, { headers, signal: ac.signal, redirect: 'follow' });
    const ms = Math.round(now() - t0); // integer: spec 4.4, and the app reads it with JsonReader.nextInt; performance.now() is fractional (Opus adversarial review 2026-09-23, blocker 1)
    let head = new Uint8Array(0);
    if (res.body) {
      // Playlists are read in full (capped at 1 MB) so an #EXT-X-ENDLIST past 64 KB is not missed; TS needs only two packets; everything else 64 KB.
      const ct = (res.headers.get('content-type') ?? '').toLowerCase();
      const wantBytes = ct.includes('mpegurl') ? 1_048_576 : ct.includes('mp2t') ? 2 * 188 + 1 : HEAD_BYTES;
      const reader = res.body.getReader();
      const chunks: Uint8Array[] = []; let total = 0;
      try {
        while (total < wantBytes) {
          const { done, value } = await reader.read();
          if (done) break;
          chunks.push(value); total += value.length;
        }
      } catch (e) {
        // Body stalled and the timeout fired: headers arrived, so judge the bytes we have instead of reporting a timeout.
        if ((e as Error).name !== 'AbortError') throw e;
      }
      await reader.cancel().catch(() => {});
      head = new Uint8Array(total);
      let off = 0; for (const c of chunks) { head.set(c, off); off += c.length; }
    }
    return { status: res.status, contentType: res.headers.get('content-type'), head, finalUrl: res.url || url, ms };
  } finally { clearTimeout(timer); }
}

function result(url: string, health: Health, format: Format, ms: number | null, reason: string, finalHost: string | null): ProbeResult {
  return { url, health, format, responseMs: ms, reason, finalHost };
}

export async function probeStream(
  stream: GroupedStream, fetchFn: FetchFn,
  opts: { timeoutMs?: number; now?: () => number } = {},
): Promise<ProbeResult> {
  // Never rejects: the pipeline awaits every probe in one Promise.all, so a single throw (a variant URI `new URL` cannot parse,
  // for example) would cancel the whole nightly run after 20 minutes of probing (final review 2026-09-24).
  try { return await probeOnce(stream, fetchFn, opts); }
  catch (e) { return result(stream.url, 'down', 'unknown', null, `error: ${(e as Error).message}`, null); }
}

async function probeOnce(
  stream: GroupedStream, fetchFn: FetchFn,
  opts: { timeoutMs?: number; now?: () => number },
): Promise<ProbeResult> {
  const timeoutMs = opts.timeoutMs ?? 10_000;
  const now = opts.now ?? (() => performance.now());
  const headers: Record<string, string> = { 'user-agent': stream.userAgent ?? DEFAULT_UA };
  if (stream.referrer) headers['referer'] = stream.referrer;
  const url = stream.url;

  let got: Got;
  try { got = await get(url, headers, fetchFn, timeoutMs, now); }
  catch (e) {
    const err = e as Error;
    const reason = err.name === 'AbortError' ? 'timeout' : `error: ${err.message}`;
    return result(url, 'down', 'unknown', null, reason, null);
  }
  const finalHost = hostOf(got.finalUrl);
  if ([401, 403, 429, 451].includes(got.status)) return result(url, 'unverified', 'unknown', got.ms, `http ${got.status}`, finalHost);
  if (got.status < 200 || got.status >= 300) return result(url, 'down', 'unknown', got.ms, `http ${got.status}`, finalHost);

  const format = detectFormat(got.finalUrl, got.contentType, got.head);
  const text = () => new TextDecoder('utf-8', { fatal: false }).decode(got.head);

  if (format === 'hls') {
    let parsed = parseHls(text());
    if (parsed.kind === 'invalid') return result(url, 'unverified', 'unknown', got.ms, 'not a playlist', finalHost);
    if (parsed.kind === 'master') {
      if (!parsed.mediaUri) return result(url, 'down', 'hls', got.ms, 'master has no media uri', finalHost);
      const mediaUrl = new URL(parsed.mediaUri, got.finalUrl).toString();
      let media: Got;
      try { media = await get(mediaUrl, headers, fetchFn, timeoutMs, now); }
      catch (e) { return result(url, 'down', 'hls', got.ms, `media ${(e as Error).name === 'AbortError' ? 'timeout' : 'error'}`, finalHost); }
      if ([401, 403, 429, 451].includes(media.status)) return result(url, 'unverified', 'hls', got.ms, `media http ${media.status}`, finalHost); // geo-blocked variant behind a public master (spec 4.3; Opus adversarial review 2026-09-23, major 12)
      if (media.status < 200 || media.status >= 300) return result(url, 'down', 'hls', got.ms, `media http ${media.status}`, finalHost);
      parsed = parseHls(new TextDecoder().decode(media.head));
      if (parsed.kind !== 'media') return result(url, 'down', 'hls', got.ms, 'media playlist invalid', finalHost);
    }
    if (parsed.ended) return result(url, 'down', 'hls', got.ms, 'playlist ended', finalHost);
    if (parsed.segments === 0) return result(url, 'down', 'hls', got.ms, 'no segments', finalHost);
    return result(url, 'up', 'hls', got.ms, 'ok', finalHost);
  }
  if (format === 'ts') {
    return isTsSync(got.head) ? result(url, 'up', 'ts', got.ms, 'ok', finalHost) : result(url, 'down', 'ts', got.ms, 'no ts sync', finalHost);
  }
  if (format === 'dash') {
    return isMpd(text()) ? result(url, 'up', 'dash', got.ms, 'ok', finalHost) : result(url, 'down', 'dash', got.ms, 'mpd invalid', finalHost);
  }
  return result(url, 'unverified', 'unknown', got.ms, 'unknown format', finalHost);
}
