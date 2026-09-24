import { describe, it, expect } from 'vitest';
import { buildCatalog } from '../src/build.js';
import type { Grouped, History, ProbeResult, SourceData } from '../src/types.js';

const src: SourceData = {
  channels: [], streams: [], logos: [], feeds: [], subdivisions: [], cities: [],
  categories: [{ id: 'news', name: 'News' }],
  countries: [{ code: 'US', name: 'United States', flag: '🇺🇸', languages: [] }, { code: 'FR', name: 'France', flag: '🇫🇷', languages: [] }],
};
const grouped: Grouped = {
  channels: [
    { id: 'A.us', name: 'A', altNames: [], country: 'US', region: null, categories: ['news'], network: null, logo: null, adult: false, hasUp: false },
    { id: 'B.us', name: 'B', altNames: [], country: 'US', region: null, categories: ['news'], network: null, logo: null, adult: false, hasUp: false },
    { id: 'C.us', name: 'C', altNames: [], country: 'US', region: null, categories: ['news'], network: null, logo: null, adult: false, hasUp: false },
  ],
  streams: [
    { channel: 'A.us', url: 'http://1.2.3.4/x.m3u8', quality: '1080p', referrer: null, userAgent: null },
    { channel: 'A.us', url: 'http://cdn/x.m3u8', quality: '720p', referrer: 'r', userAgent: 'u' },
    { channel: 'A.us', url: 'http://unv/x.m3u8', quality: '1080p', referrer: null, userAgent: null },
    { channel: 'B.us', url: 'http://dead/x.m3u8', quality: null, referrer: null, userAgent: null },
    { channel: 'C.us', url: 'http://geo/x.m3u8', quality: null, referrer: null, userAgent: null },
  ],
};
const results = new Map<string, ProbeResult>([
  ['http://1.2.3.4/x.m3u8', { url: 'http://1.2.3.4/x.m3u8', health: 'down', format: 'hls', responseMs: 300, reason: 'x', finalHost: '1.2.3.4' }],
  ['http://cdn/x.m3u8', { url: 'http://cdn/x.m3u8', health: 'up', format: 'hls', responseMs: 200, reason: 'ok', finalHost: 'cdn' }],
  ['http://dead/x.m3u8', { url: 'http://dead/x.m3u8', health: 'down', format: 'unknown', responseMs: null, reason: 'timeout', finalHost: null }],
  ['http://unv/x.m3u8', { url: 'http://unv/x.m3u8', health: 'unverified', format: 'unknown', responseMs: 50, reason: 'http 403', finalHost: 'unv' }],
  ['http://geo/x.m3u8', { url: 'http://geo/x.m3u8', health: 'unverified', format: 'unknown', responseMs: 50, reason: 'http 403', finalHost: 'geo' }],
]);
const history: History = { generatedAt: '2026-09-22', upRate: 0.5, streams: {
  'http://1.2.3.4/x.m3u8': [{ d: '2026-09-21', s: 'up', ms: 1 }, { d: '2026-09-22', s: 'down', ms: 300 }],
  'http://cdn/x.m3u8': [{ d: '2026-09-22', s: 'up', ms: 200 }],
  'http://unv/x.m3u8': Array.from({ length: 7 }, (_, i) => ({ d: `2026-09-${16 + i}`, s: 'up' as const, ms: 50 })),
} };

describe('buildCatalog', () => {
  const cat = buildCatalog({ grouped, results, history, src, version: 1758520800, generatedAt: '2026-09-22T06:00:00Z' });
  it('carries version and time', () => { expect(cat.version).toBe(1758520800); expect(cat.generatedAt).toBe('2026-09-22T06:00:00Z'); });
  it('lists only referenced countries and adds the other category', () => {
    expect(cat.countries).toEqual([{ code: 'US', name: 'United States', flag: '🇺🇸' }]);
    expect(cat.categories).toEqual([{ id: 'news', name: 'News' }, { id: 'other', name: 'Other' }]);
  });
  it('sets hasUp when any stream is up or unverified, false when all are down', () => {
    expect(cat.channels.find(c => c.id === 'A.us')!.hasUp).toBe(true);
    expect(cat.channels.find(c => c.id === 'B.us')!.hasUp).toBe(false);
    expect(cat.channels.find(c => c.id === 'C.us')!.hasUp).toBe(true);
  });
  it('orders streams within a channel by health first, then score, and fills fields', () => {
    const a = cat.streams.filter(s => s.channel === 'A.us');
    expect(a.map(s => s.url)).toEqual(['http://cdn/x.m3u8', 'http://unv/x.m3u8', 'http://1.2.3.4/x.m3u8']);
    const [cdn, unv, ip] = a;
    expect(cdn).toMatchObject({ format: 'hls', quality: '720p', referrer: 'r', userAgent: 'u', health: 'up', uptime7d: 1, responseMs: 200 });
    expect(unv.score).toBeGreaterThan(cdn.score); // higher score, but unverified ranks below up
    expect(ip.uptime7d).toBe(0.5);
  });
  it('scores with the median response time from history, not just tonight', () => {
    const h = { generatedAt: null, upRate: null, streams: { 'http://cdn/x.m3u8': [{ d: '1', s: 'up' as const, ms: 100 }, { d: '2', s: 'up' as const, ms: 4000 }, { d: '3', s: 'up' as const, ms: 4500 }] } };
    const c2 = buildCatalog({ grouped: { channels: grouped.channels.slice(0, 1), streams: grouped.streams.filter(s => s.url === 'http://cdn/x.m3u8') }, results, history: h, src, version: 1, generatedAt: 'x' });
    const c3 = buildCatalog({ grouped: { channels: grouped.channels.slice(0, 1), streams: grouped.streams.filter(s => s.url === 'http://cdn/x.m3u8') }, results, history: { ...h, streams: { 'http://cdn/x.m3u8': [{ d: '1', s: 'up', ms: 100 }] } }, src, version: 1, generatedAt: 'x' });
    expect(c2.streams[0].responseMs).toBe(200); // tonight's value is what the file carries
    expect(c2.streams[0].score).toBeLessThan(c3.streams[0].score); // but the median 4000 drags the score down
  });
  it('a stream with no probe result is down with unknown format', () => {
    const g2: Grouped = { ...grouped, streams: [{ channel: 'A.us', url: 'http://none', quality: null, referrer: null, userAgent: null }] };
    const c2 = buildCatalog({ grouped: g2, results: new Map(), history: { generatedAt: null, upRate: null, streams: {} }, src, version: 1, generatedAt: 'x' });
    expect(c2.streams[0]).toMatchObject({ health: 'down', format: 'unknown', uptime7d: 0, responseMs: null });
  });
});
