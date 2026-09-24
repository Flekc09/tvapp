import { uptime7d } from './history.js';
import { hostOf } from './probe.js';
import { scoreStream } from './rank.js';
import type { Catalog, CatalogStream, Grouped, History, ProbeResult, SourceData } from './types.js';

export function buildCatalog(input: {
  grouped: Grouped; results: Map<string, ProbeResult>; history: History; src: SourceData;
  version: number; generatedAt: string;
}): Catalog {
  const { grouped, results, history, src, version, generatedAt } = input;
  const streams: CatalogStream[] = grouped.streams.map(s => {
    const r = results.get(s.url);
    const entries = history.streams[s.url] ?? [];
    const uptime = uptime7d(entries);
    const responseMs = r?.responseMs ?? null;
    // Spec 4.1 step 5: score uses the median response time over the history window, not just tonight's.
    const msHistory = entries.map(e => e.ms).filter((m): m is number => m !== null).sort((a, b) => a - b);
    const medianMs = msHistory.length ? msHistory[Math.floor(msHistory.length / 2)] : responseMs;
    const host = r?.finalHost ?? hostOf(s.url);
    return {
      channel: s.channel, url: s.url, format: r?.format ?? 'unknown', quality: s.quality,
      referrer: s.referrer, userAgent: s.userAgent, health: r?.health ?? 'down',
      uptime7d: uptime, responseMs, score: scoreStream({ uptime, quality: s.quality, responseMs: medianMs, host }),
      checkedAt: generatedAt,
    };
  });
  // Spec 4.3: unverified and down streams rank last within their channel regardless of score.
  const order = new Map(grouped.channels.map((c, i) => [c.id, i]));
  const healthRank = (h: string) => (h === 'up' ? 0 : h === 'unverified' ? 1 : 2);
  streams.sort((a, b) => (order.get(a.channel)! - order.get(b.channel)!) || (healthRank(a.health) - healthRank(b.health)) || (b.score - a.score));

  const withUp = new Set(streams.filter(s => s.health !== 'down').map(s => s.channel));
  const channels = grouped.channels.map(c => ({ ...c, hasUp: withUp.has(c.id) }));

  const used = new Set(channels.map(c => c.country).filter((c): c is string => !!c));
  const countries = src.countries.filter(c => used.has(c.code)).map(c => ({ code: c.code, name: c.name, flag: c.flag }));
  const categories = [...src.categories.map(c => ({ id: c.id, name: c.name })), { id: 'other', name: 'Other' }];
  return { version, generatedAt, countries, categories, channels, streams };
}
