import { createHash } from 'node:crypto';
import type { ApiChannel, ApiFeed, ApiLogo, CatalogChannel, Grouped, GroupedStream, SourceData } from './types.js';

export function syntheticId(url: string): string {
  return 'synthetic:' + createHash('sha1').update(url).digest('hex').slice(0, 10);
}

export function pickLogo(logos: ApiLogo[], channelId: string): string | null {
  const mine = logos.filter(l => l.channel === channelId && l.in_use);
  const main = mine.find(l => l.feed === null) ?? mine[0];
  return main ? main.url : null;
}

// ccTLDs sold as generic domains say nothing about a channel's audience, and redirector hosts front channels from anywhere.
// Measured on the live API 2026-09-23: without these lists, 777 jmp2.uk channels landed under the United Kingdom and
// 390 .tv channels under Tuvalu, which has 2 real channels (Opus adversarial review 2026-09-23, major 13).
const GENERIC_CCTLDS = new Set(['TV', 'IO', 'ME', 'CO', 'CC', 'FM', 'AM', 'TO', 'LY', 'WS', 'NU', 'AI', 'GG', 'LA', 'SH', 'ST', 'SU', 'TK', 'ML', 'GA', 'CF', 'GQ']);
const REDIRECTOR_HOSTS = new Set(['jmp2.uk']);

function ccTldCountry(url: string, codes: Set<string>): string | null {
  let host: string;
  try { host = new URL(url).hostname; } catch { return null; }
  if (/^\d+\.\d+\.\d+\.\d+$/.test(host)) return null;
  if (REDIRECTOR_HOSTS.has(host)) return null;
  const tld = host.split('.').pop()?.toUpperCase() ?? '';
  if (GENERIC_CCTLDS.has(tld)) return null;
  return tld.length === 2 && codes.has(tld) ? tld : null;
}

function isRegional(feed: ApiFeed): boolean {
  return feed.broadcast_area.some(a => a.startsWith('s/') || a.startsWith('ct/'));
}

function regionName(feed: ApiFeed, cityName: Map<string, string>, subName: Map<string, string>): string | null {
  for (const a of feed.broadcast_area) if (a.startsWith('ct/')) { const n = cityName.get(a.slice(3)); if (n) return n; }
  for (const a of feed.broadcast_area) if (a.startsWith('s/')) { const n = subName.get(a.slice(2)); if (n) return n; }
  return null;
}

export function groupStreams(src: SourceData): Grouped {
  const codes = new Set(src.countries.map(c => c.code));
  const cityName = new Map(src.cities.map(c => [c.code, c.name]));
  const subName = new Map(src.subdivisions.map(s => [s.code, s.name]));
  const feedByKey = new Map(src.feeds.map(f => [`${f.channel}|${f.id}`, f]));
  const open = new Map(src.channels.filter(c => !c.closed).map(c => [c.id, c]));
  const closedIds = new Set(src.channels.filter(c => !!c.closed).map(c => c.id));
  const channels = new Map<string, CatalogChannel>();
  const streams: GroupedStream[] = [];

  const baseOf = (c: ApiChannel): CatalogChannel => ({
    id: c.id, name: c.name, altNames: c.alt_names ?? [], country: c.country ?? null, region: null,
    categories: c.categories?.length ? c.categories : ['other'], network: c.network ?? null,
    logo: pickLogo(src.logos, c.id), adult: !!c.is_nsfw || (c.categories ?? []).includes('xxx'), hasUp: false,
  });

  for (const s of src.streams) {
    const stream = (channel: string): GroupedStream =>
      ({ channel, url: s.url, quality: s.quality, referrer: s.referrer, userAgent: s.user_agent });

    const synthetic = () => {
      const id = syntheticId(s.url);
      if (!channels.has(id)) {
        channels.set(id, {
          id, name: s.title?.trim() || s.url, altNames: [], country: ccTldCountry(s.url, codes), region: null,
          categories: ['other'], network: null, logo: null, adult: false, hasUp: false,
        });
      }
      streams.push(stream(id));
    };
    if (!s.channel) { synthetic(); continue; }
    if (closedIds.has(s.channel)) continue; // closed channel: excluded with its streams
    const c = open.get(s.channel);
    if (!c) { synthetic(); continue; } // unknown channel id: kept as a synthetic channel (spec 4.2, nothing is dropped)
    const feed = s.feed ? feedByKey.get(`${s.channel}|${s.feed}`) : undefined;
    if (feed && isRegional(feed)) {
      const id = `${c.id}@${feed.id}`;
      if (!channels.has(id)) {
        channels.set(id, { ...baseOf(c), id, name: `${c.name} · ${feed.name}`, region: regionName(feed, cityName, subName) });
      }
      streams.push(stream(id));
    } else {
      if (!channels.has(c.id)) channels.set(c.id, baseOf(c));
      streams.push(stream(c.id));
    }
  }
  return { channels: [...channels.values()], streams };
}
