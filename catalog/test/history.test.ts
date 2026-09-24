import { describe, it, expect } from 'vitest';
import { loadHistory, mergeHistory, uptime7d, emptyHistory } from '../src/history.js';
import type { History, ProbeResult } from '../src/types.js';

const pr = (url: string, s: ProbeResult['health'], ms: number | null = 100): ProbeResult =>
  ({ url, health: s, format: 'hls', responseMs: ms, reason: '', finalHost: 'h' });

describe('loadHistory', () => {
  it('returns empty history on 404', async () => {
    const f = (async () => new Response('', { status: 404 })) as typeof fetch;
    expect(await loadHistory(f, 'https://p')).toEqual(emptyHistory());
  });
  it('rethrows a network error rather than pretending it is the first run', async () => {
    const f = (async () => { throw new TypeError('fetch failed'); }) as typeof fetch;
    await expect(loadHistory(f, 'https://p')).rejects.toThrow(/fetch failed/);
  });
  it('throws on 500', async () => {
    const f = (async () => new Response('', { status: 500 })) as typeof fetch;
    await expect(loadHistory(f, 'https://p')).rejects.toThrow(/500/);
  });
  it('parses a stored file', async () => {
    const h: History = { generatedAt: 'x', upRate: 0.5, streams: { u: [{ d: '2026-09-21', s: 'up', ms: 1 }] } };
    const f = (async () => new Response(JSON.stringify(h), { status: 200 })) as typeof fetch;
    expect(await loadHistory(f, 'https://p')).toEqual(h);
  });
});

describe('mergeHistory', () => {
  it('appends today, keeps 7 most recent, replaces same-day, drops missing urls', () => {
    const prev: History = { generatedAt: null, upRate: null, streams: {
      a: Array.from({ length: 7 }, (_, i) => ({ d: `2026-09-${15 + i}`, s: 'up' as const, ms: 1 })),
      gone: [{ d: '2026-09-21', s: 'up', ms: 1 }],
      b: [{ d: '2026-09-22', s: 'down', ms: null }],
    } };
    const merged = mergeHistory(prev, [pr('a', 'down', 5), pr('b', 'up', 7), pr('c', 'unverified', 9)], '2026-09-22');
    expect(merged.streams.a).toHaveLength(7);
    expect(merged.streams.a[6]).toEqual({ d: '2026-09-22', s: 'down', ms: 5 });
    expect(merged.streams.a[0].d).toBe('2026-09-16');
    expect(merged.streams.b).toEqual([{ d: '2026-09-22', s: 'up', ms: 7 }]);
    expect(merged.streams.c).toEqual([{ d: '2026-09-22', s: 'unverified', ms: 9 }]);
    expect(merged.streams.gone).toBeUndefined();
  });
  it('records upRate as the fraction of results that are up', () => {
    const merged = mergeHistory(emptyHistory(), [pr('a', 'up'), pr('b', 'down'), pr('c', 'unverified'), pr('d', 'up')], '2026-09-22');
    expect(merged.upRate).toBeCloseTo(0.5);
    expect(merged.generatedAt).toBe('2026-09-22');
  });
});

describe('uptime7d', () => {
  it('is the up fraction, 0 when empty', () => {
    expect(uptime7d([])).toBe(0);
    expect(uptime7d([{ d: '1', s: 'up', ms: 1 }, { d: '2', s: 'down', ms: null }, { d: '3', s: 'unverified', ms: 1 }, { d: '4', s: 'up', ms: 1 }])).toBe(0.5);
  });
});
