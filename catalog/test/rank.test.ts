import { describe, it, expect } from 'vitest';
import { scoreStream, qualityRank } from '../src/rank.js';

describe('qualityRank', () => {
  it('maps declared quality to a rank', () => {
    expect(qualityRank('1080p')).toBe(1); expect(qualityRank('2160p')).toBe(1);
    expect(qualityRank('720p')).toBe(0.8); expect(qualityRank('576p')).toBe(0.6);
    expect(qualityRank('480p')).toBe(0.5); expect(qualityRank('360p')).toBe(0.3);
    expect(qualityRank(null)).toBe(0.4); expect(qualityRank('weird')).toBe(0.4);
  });
});

describe('scoreStream', () => {
  it('perfect stream scores 1', () => {
    expect(scoreStream({ uptime: 1, quality: '1080p', responseMs: 0, host: 'cdn.example.com' })).toBeCloseTo(1);
  });
  it('uptime dominates quality', () => {
    const reliable480 = scoreStream({ uptime: 1, quality: '480p', responseMs: 1000, host: 'a' });
    const flaky1080 = scoreStream({ uptime: 0.4, quality: '1080p', responseMs: 1000, host: 'a' });
    expect(reliable480).toBeGreaterThan(flaky1080);
  });
  it('raw ip host loses to an identical domain host', () => {
    const dom = scoreStream({ uptime: 0.8, quality: '720p', responseMs: 500, host: 'x.tv' });
    const ip = scoreStream({ uptime: 0.8, quality: '720p', responseMs: 500, host: '1.2.3.4' });
    expect(ip).toBeLessThan(dom); expect(dom - ip).toBeCloseTo(0.05 / 1.5);
  });
  it('null response time is treated as middling, and the result is clamped to [0,1]', () => {
    const s = scoreStream({ uptime: 0, quality: null, responseMs: null, host: '1.2.3.4' });
    expect(s).toBeGreaterThanOrEqual(0); expect(s).toBeLessThanOrEqual(1);
  });
});
