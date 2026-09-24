import { describe, it, expect } from 'vitest';
import { probeStream, DEFAULT_UA, hostOf } from '../src/probe.js';
import type { GroupedStream } from '../src/types.js';

const MASTER = '#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\nmedia/mono.m3u8\n';
const MEDIA = '#EXTM3U\n#EXT-X-TARGETDURATION:10\n#EXTINF:10,\na.ts\n';
const ENDED = MEDIA + '#EXT-X-ENDLIST\n';

type Route = { status: number; body?: string | Uint8Array; type?: string; url?: string; delayMs?: number };
function fake(routes: Record<string, Route>, seen: { url: string; headers: Record<string, string> }[] = []) {
  return (async (input: string | URL | Request, init?: RequestInit) => {
    const url = String(input);
    seen.push({ url, headers: Object.fromEntries(new Headers(init?.headers).entries()) });
    const r = routes[url];
    if (!r) return new Response('nf', { status: 404 });
    if (r.delayMs) await new Promise((res, rej) => {
      const t = setTimeout(res, r.delayMs);
      init?.signal?.addEventListener('abort', () => { clearTimeout(t); rej(new DOMException('aborted', 'AbortError')); });
    });
    const res = new Response((r.body ?? '') as BodyInit, { status: r.status, headers: { 'content-type': r.type ?? 'application/vnd.apple.mpegurl' } });
    Object.defineProperty(res, 'url', { value: r.url ?? url });
    return res;
  }) as typeof fetch;
}
const s = (url: string, extra: Partial<GroupedStream> = {}): GroupedStream =>
  ({ channel: 'C', url, quality: null, referrer: null, userAgent: null, ...extra });

describe('probeStream', () => {
  it('a master that leads to a live media playlist is up, and the media uri resolves against the final url', async () => {
    const seen: { url: string; headers: Record<string, string> }[] = [];
    const f = fake({
      'http://a/x.m3u8': { status: 200, body: MASTER, url: 'http://cdn/live/x.m3u8' },
      'http://cdn/live/media/mono.m3u8': { status: 200, body: MEDIA },
    }, seen);
    const r = await probeStream(s('http://a/x.m3u8'), f);
    expect(r.health).toBe('up'); expect(r.format).toBe('hls'); expect(r.finalHost).toBe('cdn');
    expect(seen[1].url).toBe('http://cdn/live/media/mono.m3u8');
  });
  it('a media playlist with ENDLIST is down', async () => {
    const r = await probeStream(s('http://a/x.m3u8'), fake({ 'http://a/x.m3u8': { status: 200, body: ENDED } }));
    expect(r.health).toBe('down'); expect(r.reason).toMatch(/ended/);
  });
  it('a master whose media playlist 404s is down', async () => {
    const r = await probeStream(s('http://a/x.m3u8'), fake({ 'http://a/x.m3u8': { status: 200, body: MASTER } }));
    expect(r.health).toBe('down'); expect(r.reason).toMatch(/404/);
  });
  it('a master whose media playlist is geo-blocked (403) is unverified, not down', async () => {
    // The master is public and the variant is blocked from datacenter IPs: a TV in the home region may still play it (spec 4.3; Opus adversarial review 2026-09-23, major 12).
    const r = await probeStream(s('http://a/x.m3u8'), fake({ 'http://a/x.m3u8': { status: 200, body: MASTER }, 'http://a/media/mono.m3u8': { status: 403 } }));
    expect(r.health).toBe('unverified'); expect(r.format).toBe('hls'); expect(r.reason).toMatch(/media http 403/);
  });
  it('401, 403, 429 and 451 are unverified; 404 and 503 are down', async () => {
    for (const [code, h] of [[401, 'unverified'], [403, 'unverified'], [429, 'unverified'], [451, 'unverified'], [404, 'down'], [503, 'down']] as const) {
      const r = await probeStream(s('http://a/x.m3u8'), fake({ 'http://a/x.m3u8': { status: code } }));
      expect(r.health, `status ${code}`).toBe(h);
    }
  });
  it('a 200 html body is unverified with format unknown', async () => {
    const r = await probeStream(s('http://a/live'), fake({ 'http://a/live': { status: 200, body: '<html>expired</html>', type: 'text/html' } }));
    expect(r.health).toBe('unverified'); expect(r.format).toBe('unknown');
  });
  it('format is detected from the final url after a redirect, not the original', async () => {
    const r = await probeStream(s('http://a/live'), fake({ 'http://a/live': { status: 200, body: MEDIA, type: 'application/octet-stream', url: 'http://cdn/x.m3u8' } }));
    expect(r.health).toBe('up'); expect(r.format).toBe('hls'); expect(r.finalHost).toBe('cdn');
  });
  it('a redirect to an html login page is unverified', async () => {
    const r = await probeStream(s('http://a/x.m3u8'), fake({ 'http://a/x.m3u8': { status: 200, body: '<html>login</html>', type: 'text/html', url: 'http://a/login' } }));
    expect(r.health).toBe('unverified');
  });
  it('a raw TS stream with sync bytes is up', async () => {
    const ts = new Uint8Array(400); ts[0] = 0x47; ts[188] = 0x47;
    const r = await probeStream(s('http://a/live.ts'), fake({ 'http://a/live.ts': { status: 200, body: ts, type: 'video/mp2t' } }));
    expect(r.health).toBe('up'); expect(r.format).toBe('ts');
  });
  it('a DASH manifest with a period is up', async () => {
    const r = await probeStream(s('http://a/m.mpd'), fake({ 'http://a/m.mpd': { status: 200, body: '<MPD><Period/></MPD>', type: 'application/dash+xml' } }));
    expect(r.health).toBe('up'); expect(r.format).toBe('dash');
  });
  it('timeout before any response is down with reason timeout', async () => {
    const r = await probeStream(s('http://a/x.m3u8'), fake({ 'http://a/x.m3u8': { status: 200, body: MEDIA, delayMs: 500 } }), { timeoutMs: 50 });
    expect(r.health).toBe('down'); expect(r.reason).toBe('timeout');
  });
  it('a TS stream that sends headers and one packet then stalls is judged on what arrived', async () => {
    const ts = new Uint8Array(400); ts[0] = 0x47; ts[188] = 0x47;
    const stall = (async (input: string | URL | Request, init?: RequestInit) => {
      const stream = new ReadableStream<Uint8Array>({ start(ctrl) { ctrl.enqueue(ts); /* never closes */ init?.signal?.addEventListener('abort', () => ctrl.error(new DOMException('aborted', 'AbortError'))); } });
      const res = new Response(stream, { status: 200, headers: { 'content-type': 'video/mp2t' } });
      Object.defineProperty(res, 'url', { value: String(input) }); return res;
    }) as typeof fetch;
    const r = await probeStream(s('http://a/live.ts'), stall, { timeoutMs: 100 });
    expect(r.health).toBe('up'); expect(r.format).toBe('ts'); expect(r.responseMs).not.toBeNull();
    expect(Number.isInteger(r.responseMs)).toBe(true); // contract with the app's parser (Opus adversarial review 2026-09-23, blocker 1)
  });
  it('a thrown network error is down', async () => {
    const f = (async () => { throw new TypeError('fetch failed'); }) as typeof fetch;
    const r = await probeStream(s('http://a/x.m3u8'), f);
    expect(r.health).toBe('down'); expect(r.reason).toMatch(/fetch failed/);
  });
  it('sends the stream headers when present and the default UA otherwise', async () => {
    const seen: { url: string; headers: Record<string, string> }[] = [];
    await probeStream(s('http://a/x.m3u8', { referrer: 'http://r/', userAgent: 'Custom' }), fake({ 'http://a/x.m3u8': { status: 200, body: MASTER }, 'http://a/media/mono.m3u8': { status: 200, body: MEDIA } }, seen));
    expect(seen[0].headers['user-agent']).toBe('Custom'); expect(seen[0].headers['referer']).toBe('http://r/');
    expect(seen[1].url).toBe('http://a/media/mono.m3u8'); // the media follow-up carries the same headers
    expect(seen[1].headers['user-agent']).toBe('Custom'); expect(seen[1].headers['referer']).toBe('http://r/');
    seen.length = 0;
    await probeStream(s('http://a/x.m3u8'), fake({ 'http://a/x.m3u8': { status: 200, body: MEDIA } }, seen));
    expect(seen[0].headers['user-agent']).toBe(DEFAULT_UA);
  });
  it('never throws: a master whose media uri cannot be resolved is down with the error as the reason', async () => {
    // One malformed variant URI must not reject through the pipeline's Promise.all and cancel the whole nightly run (final review 2026-09-24).
    const r = await probeStream(s('http://a/x.m3u8'), fake({ 'http://a/x.m3u8': { status: 200, body: '#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\nhttp://bad host:abc/x.m3u8\n' } }));
    expect(r.health).toBe('down'); expect(r.format).toBe('unknown'); expect(r.reason).toMatch(/Invalid URL/);
  });
});

describe('hostOf', () => {
  it('returns hostname or the raw string on bad urls', () => {
    expect(hostOf('http://1.2.3.4:8080/x')).toBe('1.2.3.4');
    expect(hostOf('garbage')).toBe('garbage');
  });
});
