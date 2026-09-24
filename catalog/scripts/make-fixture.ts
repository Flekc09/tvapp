// Samples the live iptv-org API into small fixture files for the offline integration test.
// Run once: npm run fixture. Commit the result.
import { mkdir, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { fetchSource } from '../src/fetch-source.js';

const out = join(import.meta.dirname, '..', 'test', 'fixtures', 'api');
const src = await fetchSource(fetch);
const keepIds = new Set(['ABC.us', 'CBS.us', 'SECNetwork.us', 'FoxSports1.us', 'BBCNews.uk', 'NHKWorldJapan.jp']);
const channels = src.channels.filter(c => keepIds.has(c.id));
// All channel-linked streams for the kept ids, plus a slice of channel-less streams (the API lists those first, so a plain slice would contain nothing else).
const streams = [...src.streams.filter(s => s.channel && keepIds.has(s.channel)), ...src.streams.filter(s => s.channel === null).slice(0, 40)];
const logos = src.logos.filter(l => keepIds.has(l.channel));
const feeds = src.feeds.filter(f => keepIds.has(f.channel));
const areaCodes = new Set(feeds.flatMap(f => f.broadcast_area));
const cities = src.cities.filter(c => areaCodes.has(`ct/${c.code}`));
const subdivisions = src.subdivisions.filter(s => areaCodes.has(`s/${s.code}`));
await mkdir(out, { recursive: true });
const files = { channels, streams, logos, feeds, cities, subdivisions, categories: src.categories, countries: src.countries };
for (const [name, data] of Object.entries(files)) await writeFile(join(out, `${name}.json`), JSON.stringify(data, null, 1));
console.log(`fixture: ${channels.length} channels, ${streams.length} streams, ${feeds.length} feeds, ${cities.length} cities, ${subdivisions.length} subdivisions`);
