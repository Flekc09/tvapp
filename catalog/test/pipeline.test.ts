import { describe, it, expect } from 'vitest';
import { mkdtemp, readFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { gunzipSync } from 'node:zlib';
import { runPipeline } from '../src/pipeline.js';
import type { Catalog } from '../src/types.js';

const FIX = join(import.meta.dirname, 'fixtures', 'api');
const MEDIA = '#EXTM3U\n#EXT-X-TARGETDURATION:10\n#EXTINF:10,\na.ts\n';

// Serves fixture API files, a previous history, and fakes every stream host:
// hosts containing "dead" 404, hosts containing "geo" 403, everything else is a live media playlist.
function fakeFetch(prevHistory: unknown | null, calls: Map<string, number> = new Map()) {
  return (async (input: string | URL | Request, init?: RequestInit) => {
    const url = String(input);
    calls.set(url, (calls.get(url) ?? 0) + 1);
    if (url.startsWith('https://api.test/')) {
      const name = url.split('/').pop()!;
      return new Response(await readFile(join(FIX, name), 'utf8'), { status: 200 });
    }
    if (url === 'https://pages.test/history.json') {
      return prevHistory ? new Response(JSON.stringify(prevHistory), { status: 200 }) : new Response('', { status: 404 });
    }
    if (init?.method === 'HEAD') return new Response(null, { status: 200, headers: { 'content-type': 'image/png' } });
    if (url.includes('dead')) return new Response('nf', { status: 404 });
    if (url.includes('geo')) return new Response('', { status: 403 });
    return new Response(MEDIA, { status: 200, headers: { 'content-type': 'application/vnd.apple.mpegurl' } });
  }) as typeof fetch;
}

describe('runPipeline (offline, fixture API)', () => {
  it('first run publishes a catalog with every fixture stream and starts history', async () => {
    const outDir = await mkdtemp(join(tmpdir(), 'pipe-'));
    const calls = new Map<string, number>();
    const r = await runPipeline({ fetchFn: fakeFetch(null, calls), apiBase: 'https://api.test', pagesBase: 'https://pages.test', outDir, now: () => new Date('2026-09-22T06:00:00Z'), concurrency: 8, perHost: 2, timeoutMs: 1000, log: () => {} });
    expect(r.published).toBe(true);
    expect(r.stats.streams).toBeGreaterThan(10);
    expect(r.stats.up).toBeGreaterThan(0);
    const cat = JSON.parse(gunzipSync(await readFile(join(outDir, 'catalog.json.gz'))).toString()) as Catalog;
    expect(cat.version).toBe(Math.floor(Date.parse('2026-09-22T06:00:00Z') / 1000));
    expect(cat.streams).toHaveLength(r.stats.streams);
    expect(cat.channels.some(c => c.id.startsWith('synthetic:'))).toBe(true);
    expect(cat.channels.some(c => c.id.includes('@') && c.region !== null)).toBe(true);
    expect(cat.channels.every(c => typeof c.hasUp === 'boolean' && typeof c.adult === 'boolean')).toBe(true);
    expect(cat.categories.some(c => c.id === 'other')).toBe(true);
    const hist = JSON.parse(await readFile(join(outDir, 'history.json'), 'utf8'));
    const uniqueUrls = new Set(cat.streams.map(s => s.url));
    expect(Object.keys(hist.streams)).toHaveLength(uniqueUrls.size);
    for (const u of uniqueUrls) expect(calls.get(u) ?? 0, `probe count for ${u}`).toBe(1); // each URL probed once, even if linked from two channels
    expect(hist.streams[cat.streams[0].url]).toHaveLength(1);
    const latest = JSON.parse(await readFile(join(outDir, 'latest.json'), 'utf8'));
    expect(latest.version).toBe(cat.version);
  });
  // Every stream host 404s; the API, history and logo HEADs still answer.
  const allDead = (prev: unknown) => (async (input: string | URL | Request, init?: RequestInit) => {
    const url = String(input);
    if (url.startsWith('https://api.test/') || url.endsWith('history.json') || init?.method === 'HEAD') return fakeFetch(prev)(input, init);
    return new Response('nf', { status: 404 });
  }) as typeof fetch;
  it('refuses to publish when the up rate collapses versus the previous run', async () => {
    const outDir = await mkdtemp(join(tmpdir(), 'pipe-'));
    const prev = { generatedAt: '2026-09-21', upRate: 0.99, streams: {} };
    const r = await runPipeline({ fetchFn: allDead(prev), apiBase: 'https://api.test', pagesBase: 'https://pages.test', outDir, now: () => new Date(), concurrency: 8, perHost: 2, timeoutMs: 1000, log: () => {} });
    expect(r.published).toBe(false);
    expect(r.reason).toMatch(/up rate fell/);
    await expect(readFile(join(outDir, 'catalog.json.gz'))).rejects.toThrow();
    await expect(readFile(join(outDir, 'history.json'))).rejects.toThrow(); // history must not advance on a failed guard
    await expect(readFile(join(outDir, 'latest.json'))).rejects.toThrow();
  });
  it('force publishes through a collapsed up rate and makes the new rate the baseline', async () => {
    // The guard compares against the last published rate and refusal never writes history, so a permanent drop of more than
    // 25 points (runner region change, a big host blocking Azure) would block every later run (Opus adversarial review 2026-09-23, major 14).
    const outDir = await mkdtemp(join(tmpdir(), 'pipe-'));
    const prev = { generatedAt: '2026-09-21', upRate: 0.99, streams: {} };
    const r = await runPipeline({ fetchFn: allDead(prev), apiBase: 'https://api.test', pagesBase: 'https://pages.test', outDir, now: () => new Date('2026-09-22T06:00:00Z'), concurrency: 8, perHost: 2, timeoutMs: 1000, log: () => {}, force: true });
    expect(r.published).toBe(true);
    expect(r.stats.up).toBe(0);
    const hist = JSON.parse(await readFile(join(outDir, 'history.json'), 'utf8'));
    expect(hist.upRate).toBe(0); // next night's guard compares against this, not the stale 0.99
  });
  it('second run extends history to two entries', async () => {
    const outDir = await mkdtemp(join(tmpdir(), 'pipe-'));
    const first = await runPipeline({ fetchFn: fakeFetch(null), apiBase: 'https://api.test', pagesBase: 'https://pages.test', outDir, now: () => new Date('2026-09-22T06:00:00Z'), concurrency: 8, perHost: 2, timeoutMs: 1000, log: () => {} });
    const h1 = JSON.parse(await readFile(join(outDir, 'history.json'), 'utf8'));
    const second = await runPipeline({ fetchFn: fakeFetch(h1), apiBase: 'https://api.test', pagesBase: 'https://pages.test', outDir, now: () => new Date('2026-09-23T06:00:00Z'), concurrency: 8, perHost: 2, timeoutMs: 1000, log: () => {} });
    expect(first.published && second.published).toBe(true);
    const h2 = JSON.parse(await readFile(join(outDir, 'history.json'), 'utf8'));
    const anyUrl = Object.keys(h2.streams)[0];
    expect(h2.streams[anyUrl]).toHaveLength(2);
  });
});
