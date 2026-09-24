# Catalog Job Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A nightly GitHub Actions job that turns the raw iptv-org API into one cleaned, health-tested, ranked catalog file the TV app downloads.

**Architecture:** A TypeScript CLI in `catalog/` runs as a pipeline of pure modules: fetch source, group streams under channels, probe every stream with a global and per-host concurrency limiter, merge results into a 7-day history, score and rank, apply publish guards, write `catalog.json.gz`, `history.json` and `latest.json` to `catalog/out/`. A workflow deploys `catalog/out/` to GitHub Pages with the Pages artifact action. Every module takes its I/O (fetch, clock) as a parameter so tests never touch the network.

**Tech Stack:** Node 22, TypeScript 5, ESM, vitest, tsx. No runtime dependencies beyond Node built-ins (`fetch`, `zlib`, `crypto`, `fs`).

**Spec:** `docs/superpowers/specs/2026-09-22-tv-app-design.md`, sections 3.1, 4, 7 and 8.

## Global Constraints

- Node 22 in CI (`actions/setup-node` with `node-version: 22`). Local Node 25 is fine.
- ESM only (`"type": "module"`), TypeScript `strict: true`.
- No network in unit tests. Every module that does I/O takes a `fetch`-compatible function as a parameter.
- Probe timeout: 10 seconds per request. Global concurrency 50. Per-host concurrency 2.
- Probe user agent when the stream has none: `TVApp/1.0 (Android TV; Media3)`. The TV app must send the same string (app plan, Global Constraints).
- History window: 7 daily entries per stream URL.
- Publish guard: refuse if up-rate is more than 25 percentage points below the previous run.
- `version` is Unix epoch seconds of the run.
- Output directory: `catalog/out/`. Files: `catalog.json.gz`, `history.json`, `latest.json`.
- Publish via `actions/upload-pages-artifact` and `actions/deploy-pages`. Never commit output files.
- Commit after every task. Commit messages end with `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.

## Review Focus

Inputs the spec implies but a first implementation is likely to break on. Each has a pinned test in the task named.

1. **A stream URL that redirects from HTTPS to HTTP, or across hosts.** Node `fetch` follows redirects by default, so the probe must detect the format from the final URL, record the final host for the raw-IP penalty, and treat a redirect to a login page as `unverified`. The per-host limiter is keyed on the original host; that is accepted. Test in Task 6.
2. **A master playlist whose media playlist URI is relative.** Resolving it against the master's URL, not the original stream URL after redirects, gives a 404 and a false `down`. Test in Task 6.
3. **A stream that appears twice in `streams.json` with the same URL under different channels.** History is keyed by URL, so both channels share one health record. Grouping must keep both channel links. Test in Task 3.
4. **A history file from a previous run that contains URLs no longer in the catalog.** They must be dropped, not carried forever. Test in Task 7.
5. **The very first run, with no previous `history.json` or `latest.json` on Pages (404).** The guard must pass and history must start empty, not crash. Tests in Tasks 7 and 9.

---

### Task 1: Scaffold the package

**Files:**
- Create: `catalog/package.json`
- Create: `catalog/tsconfig.json`
- Create: `catalog/vitest.config.ts`
- Create: `catalog/.gitignore`
- Create: `catalog/src/types.ts`
- Test: `catalog/test/types.test.ts`

**Interfaces:**
- Produces: every type the rest of the pipeline uses. Later tasks import from `../src/types.js`.

- [ ] **Step 1: Create package.json**

```json
{
  "name": "tv-catalog",
  "version": "1.0.0",
  "private": true,
  "type": "module",
  "engines": { "node": ">=22" },
  "scripts": {
    "build": "tsc -p tsconfig.json",
    "typecheck": "tsc --noEmit -p tsconfig.json",
    "test": "vitest run",
    "run": "tsx src/main.ts",
    "fixture": "tsx scripts/make-fixture.ts"
  },
  "devDependencies": {
    "@types/node": "^22.0.0",
    "tsx": "^4.19.0",
    "typescript": "^5.6.0",
    "vitest": "^3.0.0"
  }
}
```

- [ ] **Step 2: Create tsconfig.json**

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "module": "NodeNext",
    "moduleResolution": "NodeNext",
    "strict": true,
    "esModuleInterop": true,
    "skipLibCheck": true,
    "outDir": "dist",
    "rootDir": ".",
    "types": ["node"]
  },
  "include": ["src/**/*.ts", "scripts/**/*.ts", "test/**/*.ts"]
}
```

- [ ] **Step 3: Create vitest.config.ts and .gitignore**

`catalog/vitest.config.ts`:
```ts
import { defineConfig } from 'vitest/config';
export default defineConfig({ test: { include: ['test/**/*.test.ts'], testTimeout: 20000 } });
```

`catalog/.gitignore`:
```
node_modules/
dist/
out/
```

- [ ] **Step 4: Write the types file**

`catalog/src/types.ts`:
```ts
// Shapes of the iptv-org API files we consume. Only the fields we use.
export interface ApiChannel {
  id: string; name: string; alt_names: string[]; network: string | null;
  country: string; categories: string[]; is_nsfw: boolean; closed: string | null;
}
export interface ApiStream {
  channel: string | null; feed: string | null; title: string | null; url: string;
  quality: string | null; labels: string[]; user_agent: string | null; referrer: string | null;
}
export interface ApiCategory { id: string; name: string; description?: string }
export interface ApiCountry { name: string; code: string; flag: string; languages: string[] }
export interface ApiLogo {
  channel: string; feed: string | null; in_use: boolean; width: number; height: number;
  format: string; url: string;
}
// broadcast_area entries look like "c/US" (country), "r/EUR" (region), "s/US-NC" (subdivision), "ct/USCLT" (city).
export interface ApiFeed { channel: string; id: string; name: string; is_main: boolean; broadcast_area: string[] }
export interface ApiSubdivision { country: string; code: string; name: string }
export interface ApiCity { country: string; subdivision: string | null; code: string; name: string }
export interface SourceData {
  channels: ApiChannel[]; streams: ApiStream[]; categories: ApiCategory[];
  countries: ApiCountry[]; logos: ApiLogo[]; feeds: ApiFeed[];
  subdivisions: ApiSubdivision[]; cities: ApiCity[];
}

// Output catalog (spec section 4.3).
export type Health = 'up' | 'down' | 'unverified';
export type Format = 'hls' | 'ts' | 'dash' | 'unknown';

export interface CatalogCountry { code: string; name: string; flag: string }
export interface CatalogCategory { id: string; name: string }
export interface CatalogChannel {
  id: string; name: string; altNames: string[]; country: string | null; region: string | null;
  categories: string[]; network: string | null; logo: string | null; adult: boolean; hasUp: boolean;
}
export interface CatalogStream {
  channel: string; url: string; format: Format; quality: string | null;
  referrer: string | null; userAgent: string | null; health: Health;
  uptime7d: number; responseMs: number | null; score: number; checkedAt: string;
}
export interface Catalog {
  version: number; generatedAt: string;
  countries: CatalogCountry[]; categories: CatalogCategory[];
  channels: CatalogChannel[]; streams: CatalogStream[];
}

// Intermediate: a stream joined to a channel, before probing.
export interface GroupedStream {
  channel: string; url: string; quality: string | null;
  referrer: string | null; userAgent: string | null;
}
export interface Grouped { channels: CatalogChannel[]; streams: GroupedStream[] }

// Probe result for one URL.
export interface ProbeResult {
  url: string; health: Health; format: Format; responseMs: number | null;
  reason: string; finalHost: string | null;
}

// History file (spec section 4.1 step 1).
export interface HistoryEntry { d: string; s: Health; ms: number | null }
export interface History {
  generatedAt: string | null; upRate: number | null;
  streams: Record<string, HistoryEntry[]>;
}

export interface Latest { version: number; bytes: number }

export type FetchFn = typeof fetch;
```

- [ ] **Step 5: Write a smoke test that the types compile and vitest runs**

`catalog/test/types.test.ts`:
```ts
import { describe, it, expect } from 'vitest';
import type { Catalog } from '../src/types.js';

describe('types', () => {
  it('a minimal catalog object satisfies the Catalog type', () => {
    const c: Catalog = {
      version: 1, generatedAt: '2026-09-22T06:00:00Z',
      countries: [], categories: [], channels: [], streams: [],
    };
    expect(c.version).toBe(1);
  });
});
```

- [ ] **Step 6: Install and run**

Run: `cd catalog && npm install && npm run typecheck && npm test`
Expected: typecheck clean, 1 test passes. Every later task's test step is followed by `npm run typecheck`; strict mode is a constraint only if something enforces it.

- [ ] **Step 7: Commit**

```bash
git add catalog/package.json catalog/package-lock.json catalog/tsconfig.json catalog/vitest.config.ts catalog/.gitignore catalog/src/types.ts catalog/test/types.test.ts
git commit -m "catalog: scaffold TypeScript package and shared types

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 2: Fetch the iptv-org source data

**Files:**
- Create: `catalog/src/fetch-source.ts`
- Test: `catalog/test/fetch-source.test.ts`

**Interfaces:**
- Produces: `fetchSource(fetchFn: FetchFn, baseUrl?: string): Promise<SourceData>`. Downloads eight files: channels, streams, categories, countries, logos, feeds, subdivisions, cities. Throws on any non-2xx or invalid JSON. Default base URL `https://iptv-org.github.io/api`.

- [ ] **Step 1: Write the failing tests**

`catalog/test/fetch-source.test.ts`:
```ts
import { describe, it, expect } from 'vitest';
import { fetchSource } from '../src/fetch-source.js';

function fakeFetch(files: Record<string, unknown>, failUrl?: string) {
  return (async (input: string | URL | Request) => {
    const url = String(input);
    const name = url.split('/').pop()!.replace('.json', '');
    if (url === failUrl) return new Response('nope', { status: 503 });
    if (!(name in files)) return new Response('missing', { status: 404 });
    return new Response(JSON.stringify(files[name]), { status: 200, headers: { 'content-type': 'application/json' } });
  }) as typeof fetch;
}

const ok = { channels: [], streams: [], categories: [], countries: [], logos: [], feeds: [], subdivisions: [], cities: [] };

describe('fetchSource', () => {
  it('downloads the eight files and returns them keyed', async () => {
    const data = await fetchSource(fakeFetch(ok), 'https://x/api');
    expect(Object.keys(data).sort()).toEqual(['categories', 'channels', 'cities', 'countries', 'feeds', 'logos', 'streams', 'subdivisions']);
  });
  it('throws if any file is non-2xx', async () => {
    await expect(fetchSource(fakeFetch(ok, 'https://x/api/streams.json'), 'https://x/api'))
      .rejects.toThrow(/streams\.json.*503/);
  });
  it('throws if a file is not JSON', async () => {
    const f = (async () => new Response('<html>', { status: 200 })) as typeof fetch;
    await expect(fetchSource(f, 'https://x/api')).rejects.toThrow(/JSON/);
  });
});
```

- [ ] **Step 2: Run to verify failure**

Run: `cd catalog && npx vitest run test/fetch-source.test.ts`
Expected: FAIL, cannot find module `../src/fetch-source.js`.

- [ ] **Step 3: Implement**

`catalog/src/fetch-source.ts`:
```ts
import type { FetchFn, SourceData } from './types.js';

const FILES = ['channels', 'streams', 'categories', 'countries', 'logos', 'feeds', 'subdivisions', 'cities'] as const;

export async function fetchSource(
  fetchFn: FetchFn,
  baseUrl = 'https://iptv-org.github.io/api',
): Promise<SourceData> {
  const out: Partial<SourceData> = {};
  for (const name of FILES) {
    const url = `${baseUrl}/${name}.json`;
    const res = await fetchFn(url);
    if (!res.ok) throw new Error(`fetchSource: ${name}.json returned ${res.status}`);
    let body: unknown;
    try { body = await res.json(); } catch { throw new Error(`fetchSource: ${name}.json is not valid JSON`); }
    if (!Array.isArray(body)) throw new Error(`fetchSource: ${name}.json is not a JSON array`);
    (out as Record<string, unknown>)[name] = body;
  }
  return out as SourceData;
}
```

- [ ] **Step 4: Run to verify pass**

Run: `cd catalog && npx vitest run test/fetch-source.test.ts`
Expected: 3 pass.

- [ ] **Step 5: Commit**

```bash
git add catalog/src/fetch-source.ts catalog/test/fetch-source.test.ts
git commit -m "catalog: fetch iptv-org source files

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 3: Group streams under channels

**Files:**
- Create: `catalog/src/group.ts`
- Test: `catalog/test/group.test.ts`

**Interfaces:**
- Produces: `groupStreams(src: SourceData): Grouped`, `pickLogo(logos: ApiLogo[], channelId: string): string | null`, `syntheticId(url: string): string`. Rules (spec 4.2):
  - Only catalog channels with at least one stream are returned. `hasUp` is left `false` here; Task 11 fills it.
  - A stream whose feed (looked up in `feeds` by channel id and feed id) has any `s/` or `ct/` broadcast area belongs to a split channel `<channel>@<feedId>` named `<channel name> · <feed name>` with `region` from the cities file (`ct/` code) or the subdivisions file (`s/` code), inheriting the parent's country, categories, network and logo. Any other feed, a missing feed, or a feed id not found in `feeds` maps to the plain channel id.
  - Streams with no channel get a synthetic channel with id `synthetic:<10 hex chars of sha1(url)>`, category `other`, country from the URL's ccTLD if it matches a known country code, else null. No guess for ccTLDs marketed as generic domains (`.tv`, `.io`, `.me`, `.co`, `.cc`, `.fm`, `.am`, `.to`, `.ly`, `.ws`, `.nu`, `.ai`, `.gg`, `.la`, `.sh`, `.st`, `.su`, `.tk`, `.ml`, `.ga`, `.cf`, `.gq`) or for known redirector hosts (`jmp2.uk`): measured on the live API 2026-09-23, the plain guess filed 777 `jmp2.uk` FAST channels under the United Kingdom and 390 `.tv` channels from 260 unrelated hosts under Tuvalu, which has 2 real channels (Opus adversarial review 2026-09-23, major 13).
  - Closed channels are excluded with their streams. A stream whose channel id is not in `channels.json` is kept as a synthetic channel, exactly like a stream with no channel. A feed is regional when any of its broadcast areas is `s/` or `ct/`, even if a `c/` entry is also present. Channels with `is_nsfw` or the `xxx` category are kept with `adult: true`. Channels with no categories get `['other']`.

- [ ] **Step 1: Write the failing tests**

`catalog/test/group.test.ts`:
```ts
import { describe, it, expect } from 'vitest';
import { groupStreams, pickLogo, syntheticId } from '../src/group.js';
import type { SourceData } from '../src/types.js';

const st = (channel: string | null, feed: string | null, title: string, url: string, extra: Partial<SourceData['streams'][number]> = {}) =>
  ({ channel, feed, title, url, quality: null, labels: [], user_agent: null, referrer: null, ...extra });

const base: SourceData = {
  channels: [
    { id: 'ABC.us', name: 'ABC', alt_names: ['ABC East'], network: 'ABC', country: 'US', categories: ['general'], is_nsfw: false, closed: null },
    { id: 'Old.us', name: 'Old', alt_names: [], network: null, country: 'US', categories: [], is_nsfw: false, closed: '2020-01-01' },
    { id: 'NoStreams.us', name: 'No Streams', alt_names: [], network: null, country: 'US', categories: [], is_nsfw: false, closed: null },
    { id: 'Adult.us', name: 'Adult', alt_names: [], network: null, country: 'US', categories: ['xxx'], is_nsfw: false, closed: null },
    { id: 'NoCat.us', name: 'No Cat', alt_names: [], network: null, country: 'US', categories: [], is_nsfw: false, closed: null },
    { id: 'Nsfw.us', name: 'Nsfw', alt_names: [], network: null, country: 'US', categories: ['general'], is_nsfw: true, closed: null },
  ],
  streams: [
    st('ABC.us', 'East', 'ABC', 'http://a/1.m3u8', { quality: '1080p' }),
    st('ABC.us', 'West', 'ABC W', 'http://a/2.m3u8', { user_agent: 'UA', referrer: 'http://r' }),
    st('ABC.us', 'WSOCTV', 'WSOC', 'http://wsoc/1.m3u8'),
    st('ABC.us', 'WSOCTV', 'WSOC alt', 'http://wsoc/2.m3u8'),
    st('ABC.us', 'KATC', 'KATC', 'http://katc/1.m3u8'),
    st('ABC.us', 'MissingFeed', 'x', 'http://missing/1.m3u8'),
    st('ABC.us', 'MIXED', 'Mixed', 'http://mixed/1.m3u8'),
    st('Nsfw.us', null, 'Nsfw', 'http://nsfw/1.m3u8'),
    st('Old.us', null, 'Old', 'http://old/1.m3u8'),
    st('Adult.us', null, 'Adult', 'http://adult/1.m3u8'),
    st('NoCat.us', null, 'No Cat', 'http://nocat/1.m3u8'),
    st(null, null, 'TV Publica', 'http://playcom.trapemn.tv:1935/x/playlist.m3u8', { quality: '1080p' }),
    st(null, null, 'Mystery', 'http://1.2.3.4:8080/y/index.m3u8'),
    st(null, null, 'Deutsche Welle', 'http://live.example.de/x.m3u8'),
    st(null, null, 'Redirected', 'http://jmp2.uk/abc-123.m3u8'),
    st('Ghost.xx', null, 'Ghost', 'http://ghost/1.m3u8'),
  ],
  categories: [{ id: 'general', name: 'General' }],
  countries: [{ name: 'United States', code: 'US', flag: '🇺🇸', languages: [] }, { name: 'Tuvalu', code: 'TV', flag: '🇹🇻', languages: [] }, { name: 'Germany', code: 'DE', flag: '🇩🇪', languages: [] }, { name: 'United Kingdom', code: 'UK', flag: '🇬🇧', languages: [] }],
  logos: [
    { channel: 'ABC.us', feed: 'West', in_use: true, width: 100, height: 100, format: 'PNG', url: 'http://l/west.png' },
    { channel: 'ABC.us', feed: null, in_use: true, width: 300, height: 200, format: 'PNG', url: 'http://l/main.png' },
    { channel: 'ABC.us', feed: null, in_use: false, width: 300, height: 200, format: 'PNG', url: 'http://l/old.png' },
  ],
  feeds: [
    { channel: 'ABC.us', id: 'East', name: 'East', is_main: true, broadcast_area: ['c/US'] },
    { channel: 'ABC.us', id: 'West', name: 'West', is_main: false, broadcast_area: ['c/US'] },
    { channel: 'ABC.us', id: 'WSOCTV', name: 'WSOC-TV', is_main: false, broadcast_area: ['ct/USCLT'] },
    { channel: 'ABC.us', id: 'KATC', name: 'KATC', is_main: false, broadcast_area: ['s/US-LA'] },
    { channel: 'ABC.us', id: 'MIXED', name: 'Mixed', is_main: false, broadcast_area: ['c/US', 'ct/USCLT'] },
  ],
  subdivisions: [{ country: 'US', code: 'US-LA', name: 'Louisiana' }],
  cities: [{ country: 'US', subdivision: 'US-NC', code: 'USCLT', name: 'Charlotte' }],
};

describe('groupStreams', () => {
  const g = groupStreams(base);
  const byId = (id: string) => g.channels.find(c => c.id === id);
  it('keeps only open channels that have streams; adult channels are kept and flagged', () => {
    const ids = g.channels.map(c => c.id);
    expect(ids).not.toContain('Old.us');
    expect(ids).not.toContain('NoStreams.us');
    expect(ids).toContain('ABC.us');
    expect(byId('Adult.us')!.adult).toBe(true);
    expect(byId('ABC.us')!.adult).toBe(false);
  });
  it('maps channel fields and picks the main in-use logo', () => {
    expect(byId('ABC.us')).toEqual({ id: 'ABC.us', name: 'ABC', altNames: ['ABC East'], country: 'US', region: null, categories: ['general'], network: 'ABC', logo: 'http://l/main.png', adult: false, hasUp: false });
  });
  it('country-level feeds and unknown feed ids stay on the plain channel', () => {
    const urls = g.streams.filter(s => s.channel === 'ABC.us').map(s => s.url);
    expect(urls).toEqual(['http://a/1.m3u8', 'http://a/2.m3u8', 'http://missing/1.m3u8']);
    expect(g.streams.find(s => s.url === 'http://a/2.m3u8')).toEqual({ channel: 'ABC.us', url: 'http://a/2.m3u8', quality: null, referrer: 'http://r', userAgent: 'UA' });
  });
  it('a city-level feed becomes its own channel with region from cities, inheriting parent fields', () => {
    const wsoc = byId('ABC.us@WSOCTV')!;
    expect(wsoc).toEqual({ id: 'ABC.us@WSOCTV', name: 'ABC · WSOC-TV', altNames: ['ABC East'], country: 'US', region: 'Charlotte', categories: ['general'], network: 'ABC', logo: 'http://l/main.png', adult: false, hasUp: false });
    expect(g.streams.filter(s => s.channel === 'ABC.us@WSOCTV').map(s => s.url)).toEqual(['http://wsoc/1.m3u8', 'http://wsoc/2.m3u8']);
  });
  it('a state-level feed gets its region from subdivisions', () => {
    expect(byId('ABC.us@KATC')!.region).toBe('Louisiana');
  });
  it('channels with no categories get other', () => {
    expect(byId('NoCat.us')!.categories).toEqual(['other']);
  });
  it('drops streams whose channel is closed, but keeps unknown channel ids as synthetic channels', () => {
    const urls = g.streams.map(s => s.url);
    expect(urls).not.toContain('http://old/1.m3u8');
    expect(g.streams.find(s => s.url === 'http://ghost/1.m3u8')!.channel).toBe(syntheticId('http://ghost/1.m3u8'));
  });
  it('flags is_nsfw channels as adult even without the xxx category', () => {
    expect(byId('Nsfw.us')!.adult).toBe(true);
  });
  it('a feed with both a country and a city area is regional (any s/ or ct/ entry splits)', () => {
    expect(byId('ABC.us@MIXED')!.region).toBe('Charlotte');
    expect(g.streams.find(s => s.url === 'http://mixed/1.m3u8')!.channel).toBe('ABC.us@MIXED');
  });
  it('creates a synthetic channel for streams with no channel, guessing country from ccTLD except generic-use TLDs and redirector hosts', () => {
    const syn = g.channels.filter(c => c.id.startsWith('synthetic:'));
    expect(syn).toHaveLength(5);
    const publica = syn.find(c => c.name === 'TV Publica')!;
    expect(publica.country).toBeNull(); // .tv is sold as a generic domain; guessing would file 390 live channels under Tuvalu (Opus adversarial review 2026-09-23, major 13)
    expect(publica.categories).toEqual(['other']);
    expect(publica.adult).toBe(false);
    expect(publica.id).toBe(syntheticId('http://playcom.trapemn.tv:1935/x/playlist.m3u8'));
    expect(syn.find(c => c.name === 'Mystery')!.country).toBeNull();
    expect(syn.find(c => c.name === 'Deutsche Welle')!.country).toBe('DE');
    expect(syn.find(c => c.name === 'Redirected')!.country).toBeNull(); // jmp2.uk redirects to FAST channels from anywhere; 777 of them are not British
  });
  it('the same URL under two channels keeps both links', () => {
    const src: SourceData = { ...base, streams: [
      st('ABC.us', null, 'x', 'http://same/x.m3u8'),
      st('NoStreams.us', null, 'x', 'http://same/x.m3u8'),
    ] };
    expect(groupStreams(src).streams.map(s => s.channel).sort()).toEqual(['ABC.us', 'NoStreams.us']);
  });
});

describe('pickLogo', () => {
  it('returns null when no logo exists', () => { expect(pickLogo([], 'X.us')).toBeNull(); });
  it('falls back to any in-use logo when no feed-less one exists', () => {
    expect(pickLogo([{ channel: 'X.us', feed: 'HD', in_use: true, width: 1, height: 1, format: 'PNG', url: 'u' }], 'X.us')).toBe('u');
  });
});
```

- [ ] **Step 2: Run to verify failure**

Run: `cd catalog && npx vitest run test/group.test.ts`
Expected: FAIL, cannot find module.

- [ ] **Step 3: Implement**

`catalog/src/group.ts`:
```ts
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
```

- [ ] **Step 4: Run to verify pass**

Run: `cd catalog && npx vitest run test/group.test.ts`
Expected: 13 pass.

- [ ] **Step 5: Commit**

```bash
git add catalog/src/group.ts catalog/test/group.test.ts
git commit -m "catalog: group streams under channels with synthetic fallback

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 4: Format detection and playlist parsing

**Files:**
- Create: `catalog/src/detect.ts`
- Test: `catalog/test/detect.test.ts`

**Interfaces:**
- Produces:
  - `detectFormat(url: string, contentType: string | null, head: Uint8Array): Format`
  - `parseHls(text: string): { kind: 'master'; mediaUri: string | null } | { kind: 'media'; segments: number; ended: boolean } | { kind: 'invalid' }`
  - `isTsSync(head: Uint8Array): boolean` (0x47 at offsets 0 and 188)
  - `isMpd(text: string): boolean` (contains `<MPD` and `<Period`)

- [ ] **Step 1: Write the failing tests**

`catalog/test/detect.test.ts`:
```ts
import { describe, it, expect } from 'vitest';
import { detectFormat, parseHls, isTsSync, isMpd } from '../src/detect.js';

const enc = (s: string) => new TextEncoder().encode(s);
const MASTER = '#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=5800000,RESOLUTION=1280x720\ntracks-v1a1/mono.m3u8\n';
const MEDIA = '#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:10\n#EXT-X-MEDIA-SEQUENCE:4287341\n#EXTINF:10,\n20260922_194010.ts\n#EXTINF:9,\n20260922_194020.ts\n';
const ENDED = MEDIA + '#EXT-X-ENDLIST\n';

describe('parseHls', () => {
  it('recognises a master playlist and returns the first media uri', () => {
    expect(parseHls(MASTER)).toEqual({ kind: 'master', mediaUri: 'tracks-v1a1/mono.m3u8' });
  });
  it('recognises a live media playlist with segments', () => {
    expect(parseHls(MEDIA)).toEqual({ kind: 'media', segments: 2, ended: false });
  });
  it('flags an ended playlist', () => {
    expect(parseHls(ENDED)).toEqual({ kind: 'media', segments: 2, ended: true });
  });
  it('a media playlist with no segments has segments 0', () => {
    expect(parseHls('#EXTM3U\n#EXT-X-TARGETDURATION:10\n')).toEqual({ kind: 'media', segments: 0, ended: false });
  });
  it('anything without #EXTM3U is invalid', () => {
    expect(parseHls('<html>Not found</html>')).toEqual({ kind: 'invalid' });
    expect(parseHls('Not found')).toEqual({ kind: 'invalid' });
  });
  it('a master with EXT-X-STREAM-INF but no uri line is master with null mediaUri', () => {
    expect(parseHls('#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\n')).toEqual({ kind: 'master', mediaUri: null });
  });
});

describe('detectFormat', () => {
  it('uses content-type first', () => {
    expect(detectFormat('http://x/a', 'application/vnd.apple.mpegurl', enc('<html>'))).toBe('hls');
    expect(detectFormat('http://x/a', 'video/mp2t', enc(''))).toBe('ts');
    expect(detectFormat('http://x/a', 'application/dash+xml', enc(''))).toBe('dash');
  });
  it('falls back to url suffix', () => {
    expect(detectFormat('http://x/a.m3u8?token=1', 'application/octet-stream', enc('#EXTM3U'))).toBe('hls');
    expect(detectFormat('http://x/a.m3u', null, enc('#EXTM3U'))).toBe('hls');
    expect(detectFormat('http://x/a.ts', null, enc(''))).toBe('ts');
    expect(detectFormat('http://x/a.mpd', null, enc(''))).toBe('dash');
  });
  it('falls back to body sniffing', () => {
    expect(detectFormat('http://x/live', 'application/octet-stream', enc('#EXTM3U\n#EXT-X-VERSION:3'))).toBe('hls');
    const ts = new Uint8Array(400); ts[0] = 0x47; ts[188] = 0x47;
    expect(detectFormat('http://x/live', 'application/octet-stream', ts)).toBe('ts');
    expect(detectFormat('http://x/live', 'text/xml', enc('<?xml version="1.0"?><MPD><Period></Period></MPD>'))).toBe('dash');
  });
  it('returns unknown for html and json bodies', () => {
    expect(detectFormat('http://x/live', 'text/html', enc('<html>'))).toBe('unknown');
    expect(detectFormat('http://x/live', 'application/json', enc('{"error":1}'))).toBe('unknown');
  });
});

describe('isTsSync', () => {
  it('requires 0x47 at byte 0 and 188', () => {
    const ok = new Uint8Array(200); ok[0] = 0x47; ok[188] = 0x47;
    expect(isTsSync(ok)).toBe(true);
    const short = new Uint8Array(10); short[0] = 0x47;
    expect(isTsSync(short)).toBe(false);
  });
});

describe('isMpd', () => {
  it('needs MPD and Period elements', () => {
    expect(isMpd('<MPD><Period/></MPD>')).toBe(true);
    expect(isMpd('<MPD></MPD>')).toBe(false);
  });
});
```

- [ ] **Step 2: Run to verify failure**

Run: `cd catalog && npx vitest run test/detect.test.ts`
Expected: FAIL, cannot find module.

- [ ] **Step 3: Implement**

`catalog/src/detect.ts`:
```ts
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
```

- [ ] **Step 4: Run to verify pass**

Run: `cd catalog && npx vitest run test/detect.test.ts`
Expected: 12 pass.

- [ ] **Step 5: Commit**

```bash
git add catalog/src/detect.ts catalog/test/detect.test.ts
git commit -m "catalog: detect stream format and parse HLS playlists

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 5: Concurrency limiter with per-host cap

**Files:**
- Create: `catalog/src/limiter.ts`
- Test: `catalog/test/limiter.test.ts`

**Interfaces:**
- Produces: `createLimiter(global: number, perHost: number)` returning `{ run<T>(host: string, fn: () => Promise<T>): Promise<T> }`. At most `global` tasks run at once, and at most `perHost` tasks with the same host string.

- [ ] **Step 1: Write the failing tests**

`catalog/test/limiter.test.ts`:
```ts
import { describe, it, expect } from 'vitest';
import { createLimiter } from '../src/limiter.js';

function deferred() {
  let resolve!: () => void;
  const promise = new Promise<void>(r => { resolve = r; });
  return { promise, resolve };
}

describe('createLimiter', () => {
  it('never exceeds the global limit', async () => {
    const lim = createLimiter(2, 10);
    let running = 0, peak = 0;
    const gates = [deferred(), deferred(), deferred()];
    const tasks = gates.map((g, i) => lim.run(`h${i}`, async () => {
      running++; peak = Math.max(peak, running); await g.promise; running--;
    }));
    await new Promise(r => setTimeout(r, 10));
    expect(running).toBe(2);
    gates.forEach(g => g.resolve());
    await Promise.all(tasks);
    expect(peak).toBe(2);
  });
  it('never exceeds the per-host limit even when global has room', async () => {
    const lim = createLimiter(10, 1);
    let running = 0, peak = 0;
    const gates = [deferred(), deferred()];
    const tasks = gates.map(g => lim.run('same-host', async () => {
      running++; peak = Math.max(peak, running); await g.promise; running--;
    }));
    await new Promise(r => setTimeout(r, 10));
    expect(running).toBe(1);
    gates.forEach(g => g.resolve());
    await Promise.all(tasks);
    expect(peak).toBe(1);
  });
  it('returns the task result and propagates errors without deadlocking', async () => {
    const lim = createLimiter(1, 1);
    await expect(lim.run('h', async () => { throw new Error('boom'); })).rejects.toThrow('boom');
    expect(await lim.run('h', async () => 42)).toBe(42);
  });
});
```

- [ ] **Step 2: Run to verify failure**

Run: `cd catalog && npx vitest run test/limiter.test.ts`
Expected: FAIL, cannot find module.

- [ ] **Step 3: Implement**

`catalog/src/limiter.ts`:
```ts
interface Waiter { host: string; start: () => void }

export function createLimiter(global: number, perHost: number) {
  let active = 0;
  const perHostActive = new Map<string, number>();
  const queue: Waiter[] = [];

  function canStart(host: string): boolean {
    return active < global && (perHostActive.get(host) ?? 0) < perHost;
  }
  function pump() {
    for (let i = 0; i < queue.length; ) {
      const w = queue[i];
      if (canStart(w.host)) { queue.splice(i, 1); w.start(); } else i++;
      if (active >= global) break;
    }
  }
  function run<T>(host: string, fn: () => Promise<T>): Promise<T> {
    return new Promise<T>((resolve, reject) => {
      const start = () => {
        active++; perHostActive.set(host, (perHostActive.get(host) ?? 0) + 1);
        fn().then(resolve, reject).finally(() => {
          active--; perHostActive.set(host, (perHostActive.get(host) ?? 1) - 1);
          pump();
        });
      };
      if (canStart(host)) start(); else queue.push({ host, start });
    });
  }
  return { run };
}
```

- [ ] **Step 4: Run to verify pass**

Run: `cd catalog && npx vitest run test/limiter.test.ts`
Expected: 3 pass.

- [ ] **Step 5: Commit**

```bash
git add catalog/src/limiter.ts catalog/test/limiter.test.ts
git commit -m "catalog: concurrency limiter with global and per-host caps

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 6: Probe one stream

**Files:**
- Create: `catalog/src/probe.ts`
- Test: `catalog/test/probe.test.ts`

**Interfaces:**
- Consumes: `detectFormat`, `parseHls`, `isTsSync`, `isMpd` from Task 4.
- Produces: `probeStream(stream: GroupedStream, fetchFn: FetchFn, opts?: { timeoutMs?: number; now?: () => number }): Promise<ProbeResult>` and `DEFAULT_UA = 'TVApp/1.0 (Android TV; Media3)'` and `hostOf(url: string): string`.

Rules implemented (spec 4.3): GET with timeout, headers from the stream, else default UA. 401/403/429/451 → `unverified` (geo-block, missing token, rate limit), whether on the stream URL itself or on the media playlist a master points to (CDNs commonly serve the master publicly and geo-block the variant; Opus adversarial review 2026-09-23, major 12). 404/5xx, timeout before headers, network error → `down`. A body that stalls after headers is judged on the bytes received. 200 → detect format; HLS master → follow first media URI resolved against the *final* response URL, require segments and no ENDLIST; HLS media → same check; TS → sync bytes; DASH → MPD check; unknown → `unverified`. `responseMs` is time to first response headers, rounded to an integer (spec 4.4 shows an int and the app parses it as one; `performance.now()` is fractional). `finalHost` is the host of the final URL after redirects.

- [ ] **Step 1: Write the failing tests**

`catalog/test/probe.test.ts`:
```ts
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
});

describe('hostOf', () => {
  it('returns hostname or the raw string on bad urls', () => {
    expect(hostOf('http://1.2.3.4:8080/x')).toBe('1.2.3.4');
    expect(hostOf('garbage')).toBe('garbage');
  });
});
```

- [ ] **Step 2: Run to verify failure**

Run: `cd catalog && npx vitest run test/probe.test.ts`
Expected: FAIL, cannot find module.

- [ ] **Step 3: Implement**

`catalog/src/probe.ts`:
```ts
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
```

- [ ] **Step 4: Run to verify pass**

Run: `cd catalog && npx vitest run test/probe.test.ts`
Expected: 15 pass.

- [ ] **Step 5: Commit**

```bash
git add catalog/src/probe.ts catalog/test/probe.test.ts
git commit -m "catalog: probe a stream with format-aware health test

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 7: History load and merge

**Files:**
- Create: `catalog/src/history.ts`
- Test: `catalog/test/history.test.ts`

**Interfaces:**
- Produces:
  - `loadHistory(fetchFn: FetchFn, baseUrl: string): Promise<History>` returns empty history only on 404; throws on a network error or any other non-2xx, so a blip never resets history or disables the guard.
  - `mergeHistory(prev: History, results: ProbeResult[], today: string): History` keeps the last 7 dated entries per URL, replaces an entry with the same date, drops URLs not in `results`.
  - `uptime7d(entries: HistoryEntry[]): number` = fraction of entries that are `up`, 0 for empty.
  - `emptyHistory(): History`.

- [ ] **Step 1: Write the failing tests**

`catalog/test/history.test.ts`:
```ts
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
```

- [ ] **Step 2: Run to verify failure**

Run: `cd catalog && npx vitest run test/history.test.ts`
Expected: FAIL, cannot find module.

- [ ] **Step 3: Implement**

`catalog/src/history.ts`:
```ts
import type { FetchFn, History, HistoryEntry, ProbeResult } from './types.js';

export const HISTORY_DAYS = 7;

export function emptyHistory(): History {
  return { generatedAt: null, upRate: null, streams: {} };
}

export async function loadHistory(fetchFn: FetchFn, baseUrl: string): Promise<History> {
  // Only a 404 means "first run". A network error must propagate: treating it as first run would wipe 7-day history and disable the guard (spec 4.5).
  const res = await fetchFn(`${baseUrl}/history.json`, { cache: 'no-store' });
  if (res.status === 404) return emptyHistory();
  if (!res.ok) throw new Error(`loadHistory: history.json returned ${res.status}`);
  const body = (await res.json()) as History;
  if (!body || typeof body !== 'object' || typeof body.streams !== 'object') throw new Error('loadHistory: malformed history.json');
  return body;
}

export function uptime7d(entries: HistoryEntry[]): number {
  if (entries.length === 0) return 0;
  return entries.filter(e => e.s === 'up').length / entries.length;
}

export function mergeHistory(prev: History, results: ProbeResult[], today: string): History {
  const streams: Record<string, HistoryEntry[]> = {};
  let up = 0;
  for (const r of results) {
    if (r.health === 'up') up++;
    const old = (prev.streams[r.url] ?? []).filter(e => e.d !== today);
    const next = [...old, { d: today, s: r.health, ms: r.responseMs }];
    next.sort((a, b) => a.d.localeCompare(b.d));
    streams[r.url] = next.slice(-HISTORY_DAYS);
  }
  return { generatedAt: today, upRate: results.length ? up / results.length : null, streams };
}
```

- [ ] **Step 4: Run to verify pass**

Run: `cd catalog && npx vitest run test/history.test.ts`
Expected: 7 pass.

- [ ] **Step 5: Commit**

```bash
git add catalog/src/history.ts catalog/test/history.test.ts
git commit -m "catalog: load and merge 7-day stream history

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 8: Score and rank streams

**Files:**
- Create: `catalog/src/rank.ts`
- Test: `catalog/test/rank.test.ts`

**Interfaces:**
- Consumes: `uptime7d` from Task 7, `hostOf` from Task 6.
- Produces: `scoreStream(input: { uptime: number; quality: string | null; responseMs: number | null; host: string }): number` in [0, 1], and `qualityRank(q: string | null): number`. Formula (spec 4.1 step 5): `score = clamp((uptime + 0.3 * qualityRank + 0.2 * speed - ipPenalty) / 1.5, 0, 1)` where `speed = 1 - min(ms, 5000) / 5000` (0.5 when ms is null), `ipPenalty = 0.05` for raw IPv4 hosts. `qualityRank`: 1080p and above 1.0, 720p 0.8, 576p 0.6, 480p 0.5, 360p 0.3, anything else including null 0.4.

- [ ] **Step 1: Write the failing tests**

`catalog/test/rank.test.ts`:
```ts
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
```

- [ ] **Step 2: Run to verify failure**

Run: `cd catalog && npx vitest run test/rank.test.ts`
Expected: FAIL, cannot find module.

- [ ] **Step 3: Implement**

`catalog/src/rank.ts`:
```ts
export function qualityRank(q: string | null): number {
  const m = /^(\d{3,4})[pi]$/.exec(q ?? '');
  if (!m) return 0.4;
  const n = Number(m[1]);
  if (n >= 1080) return 1;
  if (n >= 720) return 0.8;
  if (n >= 576) return 0.6;
  if (n >= 480) return 0.5;
  if (n >= 360) return 0.3;
  return 0.4;
}

const RAW_IPV4 = /^\d{1,3}(\.\d{1,3}){3}$/;

export function scoreStream(i: { uptime: number; quality: string | null; responseMs: number | null; host: string }): number {
  const speed = i.responseMs === null ? 0.5 : 1 - Math.min(i.responseMs, 5000) / 5000;
  const ipPenalty = RAW_IPV4.test(i.host) ? 0.05 : 0;
  const raw = (i.uptime + 0.3 * qualityRank(i.quality) + 0.2 * speed - ipPenalty) / 1.5;
  return Math.max(0, Math.min(1, raw));
}
```

- [ ] **Step 4: Run to verify pass**

Run: `cd catalog && npx vitest run test/rank.test.ts`
Expected: 5 pass.

- [ ] **Step 5: Commit**

```bash
git add catalog/src/rank.ts catalog/test/rank.test.ts
git commit -m "catalog: score streams from uptime, quality, speed and host

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 9: Publish guard

**Files:**
- Create: `catalog/src/guard.ts`
- Test: `catalog/test/guard.test.ts`

**Interfaces:**
- Produces: `checkGuard(prevUpRate: number | null, newUpRate: number | null, maxDrop?: number): { ok: true } | { ok: false; reason: string }`. Default `maxDrop` 0.25. Passes when there is no previous rate (first run). Fails when `prev - new > maxDrop`. Also fails when `newUpRate` is null (zero streams probed).

- [ ] **Step 1: Write the failing tests**

`catalog/test/guard.test.ts`:
```ts
import { describe, it, expect } from 'vitest';
import { checkGuard } from '../src/guard.js';

describe('checkGuard', () => {
  it('passes on first run with no previous rate', () => { expect(checkGuard(null, 0.6)).toEqual({ ok: true }); });
  it('passes when the drop is within 25 points, including exactly 25', () => { expect(checkGuard(0.6, 0.36)).toEqual({ ok: true }); expect(checkGuard(0.6, 0.35)).toEqual({ ok: true }); });
  it('fails when the drop exceeds 25 points', () => {
    const r = checkGuard(0.6, 0.30);
    expect(r.ok).toBe(false); if (!r.ok) expect(r.reason).toMatch(/0\.60.*0\.30/);
  });
  it('fails when nothing was probed', () => { expect(checkGuard(0.6, null).ok).toBe(false); });
  it('passes when the rate rises', () => { expect(checkGuard(0.3, 0.9)).toEqual({ ok: true }); });
});
```

- [ ] **Step 2: Run to verify failure**

Run: `cd catalog && npx vitest run test/guard.test.ts`
Expected: FAIL, cannot find module.

- [ ] **Step 3: Implement**

`catalog/src/guard.ts`:
```ts
export function checkGuard(prevUpRate: number | null, newUpRate: number | null, maxDrop = 0.25): { ok: true } | { ok: false; reason: string } {
  if (newUpRate === null) return { ok: false, reason: 'no streams were probed' };
  if (prevUpRate === null) return { ok: true };
  if (prevUpRate - newUpRate > maxDrop) {
    return { ok: false, reason: `up rate fell from ${prevUpRate.toFixed(2)} to ${newUpRate.toFixed(2)}, more than ${maxDrop} allowed; runner network or IP block suspected` };
  }
  return { ok: true };
}
```

- [ ] **Step 4: Run to verify pass**

Run: `cd catalog && npx vitest run test/guard.test.ts`
Expected: 5 pass.

- [ ] **Step 5: Commit**

```bash
git add catalog/src/guard.ts catalog/test/guard.test.ts
git commit -m "catalog: publish guard on up-rate collapse

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 10: Logo validation

**Files:**
- Create: `catalog/src/logos.ts`
- Test: `catalog/test/logos.test.ts`

**Interfaces:**
- Consumes: `createLimiter` from Task 5, `hostOf` from Task 6.
- Produces: `validateLogos(channels: CatalogChannel[], fetchFn: FetchFn, opts?: { timeoutMs?: number; limiter?: ReturnType<typeof createLimiter> }): Promise<CatalogChannel[]>`. Sends HEAD to each distinct logo URL. Keeps the URL only when the response is 2xx and content-type starts with `image/`. Otherwise sets `logo` to null. Same URL is checked once.

- [ ] **Step 1: Write the failing tests**

`catalog/test/logos.test.ts`:
```ts
import { describe, it, expect } from 'vitest';
import { validateLogos } from '../src/logos.js';
import type { CatalogChannel } from '../src/types.js';

const ch = (id: string, logo: string | null): CatalogChannel => ({ id, name: id, altNames: [], country: null, region: null, categories: [], network: null, logo, adult: false, hasUp: false });

describe('validateLogos', () => {
  it('keeps image responses, nulls everything else, and checks each url once', async () => {
    const calls: string[] = [];
    const f = (async (input: string | URL | Request, init?: RequestInit) => {
      const url = String(input); calls.push(`${init?.method} ${url}`);
      if (url === 'http://l/ok.png') return new Response(null, { status: 200, headers: { 'content-type': 'image/png' } });
      if (url === 'http://l/html') return new Response(null, { status: 200, headers: { 'content-type': 'text/html' } });
      throw new TypeError('fetch failed');
    }) as typeof fetch;
    const out = await validateLogos([ch('a', 'http://l/ok.png'), ch('b', 'http://l/ok.png'), ch('c', 'http://l/html'), ch('d', 'http://l/dead'), ch('e', null)], f);
    expect(out.map(c => c.logo)).toEqual(['http://l/ok.png', 'http://l/ok.png', null, null, null]);
    expect(calls.filter(c => c.endsWith('ok.png'))).toHaveLength(1);
    expect(calls[0]).toMatch(/^HEAD /);
  });
});
```

- [ ] **Step 2: Run to verify failure**

Run: `cd catalog && npx vitest run test/logos.test.ts`
Expected: FAIL, cannot find module.

- [ ] **Step 3: Implement**

`catalog/src/logos.ts`:
```ts
import { createLimiter } from './limiter.js';
import { hostOf } from './probe.js';
import type { CatalogChannel, FetchFn } from './types.js';

export async function validateLogos(
  channels: CatalogChannel[], fetchFn: FetchFn,
  opts: { timeoutMs?: number; limiter?: ReturnType<typeof createLimiter> } = {},
): Promise<CatalogChannel[]> {
  const timeoutMs = opts.timeoutMs ?? 8000;
  const limiter = opts.limiter ?? createLimiter(50, 4);
  const urls = [...new Set(channels.map(c => c.logo).filter((u): u is string => !!u))];
  const alive = new Map<string, boolean>();
  await Promise.all(urls.map(url => limiter.run(hostOf(url), async () => {
    const ac = new AbortController();
    const t = setTimeout(() => ac.abort(), timeoutMs);
    try {
      const res = await fetchFn(url, { method: 'HEAD', signal: ac.signal, redirect: 'follow' });
      alive.set(url, res.ok && (res.headers.get('content-type') ?? '').toLowerCase().startsWith('image/'));
    } catch { alive.set(url, false); }
    finally { clearTimeout(t); }
  })));
  return channels.map(c => ({ ...c, logo: c.logo && alive.get(c.logo) ? c.logo : null }));
}
```

- [ ] **Step 4: Run to verify pass**

Run: `cd catalog && npx vitest run test/logos.test.ts`
Expected: 1 pass.

- [ ] **Step 5: Commit**

```bash
git add catalog/src/logos.ts catalog/test/logos.test.ts
git commit -m "catalog: validate channel logos with HEAD requests

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 11: Build and write the catalog files

**Files:**
- Create: `catalog/src/build.ts`
- Create: `catalog/src/write.ts`
- Test: `catalog/test/build.test.ts`
- Test: `catalog/test/write.test.ts`

**Interfaces:**
- Consumes: `Grouped`, `ProbeResult`, `History`, `scoreStream`, `uptime7d`, `hostOf`.
- Produces:
  - `buildCatalog(input: { grouped: Grouped; results: Map<string, ProbeResult>; history: History; src: SourceData; version: number; generatedAt: string }): Catalog`. Streams within a channel are sorted by health (`up`, then `unverified`, then `down`) and then by score descending. The score uses the median of the history window's response times. Every channel's `hasUp` is set true when any of its streams is `up` or `unverified`. Countries list only codes referenced by a channel. Categories are the API list plus `{ id: 'other', name: 'Other' }`.
  - `writeOutputs(outDir: string, catalog: Catalog, history: History): Promise<Latest>` writes `catalog.json.gz` (gzip level 9), `history.json`, `latest.json` and returns the `Latest` object written.

- [ ] **Step 1: Write the failing tests**

`catalog/test/build.test.ts`:
```ts
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
```

`catalog/test/write.test.ts`:
```ts
import { describe, it, expect } from 'vitest';
import { mkdtemp, readFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { gunzipSync } from 'node:zlib';
import { writeOutputs } from '../src/write.js';
import type { Catalog, History } from '../src/types.js';

describe('writeOutputs', () => {
  it('writes gzipped catalog, history and latest, and latest matches the gz size', async () => {
    const dir = await mkdtemp(join(tmpdir(), 'cat-'));
    const catalog: Catalog = { version: 7, generatedAt: 'g', countries: [], categories: [], channels: [], streams: [] };
    const history: History = { generatedAt: 'g', upRate: 1, streams: {} };
    const latest = await writeOutputs(dir, catalog, history);
    const gz = await readFile(join(dir, 'catalog.json.gz'));
    expect(JSON.parse(gunzipSync(gz).toString())).toEqual(catalog);
    expect(JSON.parse(await readFile(join(dir, 'history.json'), 'utf8'))).toEqual(history);
    expect(JSON.parse(await readFile(join(dir, 'latest.json'), 'utf8'))).toEqual({ version: 7, bytes: gz.length });
    expect(latest).toEqual({ version: 7, bytes: gz.length });
  });
});
```

- [ ] **Step 2: Run to verify failure**

Run: `cd catalog && npx vitest run test/build.test.ts test/write.test.ts`
Expected: FAIL, cannot find modules.

- [ ] **Step 3: Implement**

`catalog/src/build.ts`:
```ts
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
```

`catalog/src/write.ts`:
```ts
import { mkdir, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { gzipSync } from 'node:zlib';
import type { Catalog, History, Latest } from './types.js';

export async function writeOutputs(outDir: string, catalog: Catalog, history: History): Promise<Latest> {
  await mkdir(outDir, { recursive: true });
  const gz = gzipSync(Buffer.from(JSON.stringify(catalog)), { level: 9 });
  await writeFile(join(outDir, 'catalog.json.gz'), gz);
  await writeFile(join(outDir, 'history.json'), JSON.stringify(history));
  const latest: Latest = { version: catalog.version, bytes: gz.length };
  await writeFile(join(outDir, 'latest.json'), JSON.stringify(latest));
  return latest;
}
```

- [ ] **Step 4: Run to verify pass**

Run: `cd catalog && npx vitest run test/build.test.ts test/write.test.ts`
Expected: 7 pass.

- [ ] **Step 5: Commit**

```bash
git add catalog/src/build.ts catalog/src/write.ts catalog/test/build.test.ts catalog/test/write.test.ts
git commit -m "catalog: assemble catalog and write gzipped outputs

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 12: Pipeline entry point and offline integration test

**Files:**
- Create: `catalog/src/pipeline.ts`
- Create: `catalog/src/main.ts`
- Create: `catalog/scripts/make-fixture.ts`
- Create: `catalog/test/fixtures/api/*.json` (generated by the script, then committed)
- Test: `catalog/test/pipeline.test.ts`

**Interfaces:**
- Consumes: everything above.
- Produces: `runPipeline(opts: { fetchFn: FetchFn; apiBase: string; pagesBase: string; outDir: string; now: () => Date; concurrency?: number; perHost?: number; timeoutMs?: number; log?: (s: string) => void }): Promise<{ published: boolean; reason?: string; latest?: Latest; stats: { channels: number; streams: number; up: number; down: number; unverified: number } }>`.
- `main.ts` reads env: `PAGES_BASE` (required, e.g. `https://user.github.io/tv-app`), `OUT_DIR` (default `out`), `API_BASE` (default iptv-org), `CONCURRENCY` (50), `PER_HOST` (2), `TIMEOUT_MS` (10000), `ALLOW_EMPTY_HISTORY` (`1` to tolerate an unreachable history file; local runs only), `FORCE_PUBLISH` (`1` to publish through the up-rate guard so the new rate becomes the baseline; set only by the workflow's manual `force` input). Exits 1 if not published.

- [ ] **Step 1: Write the fixture generator**

`catalog/scripts/make-fixture.ts`:
```ts
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
```

Run: `cd catalog && npm run fixture`
Expected: prints counts, eight files appear under `catalog/test/fixtures/api/`. Inspect `streams.json` and confirm it contains at least one stream with `"channel": null`, at least one with a `referrer`, and at least one `ABC.us` stream whose feed appears in `feeds.json` with a `ct/` broadcast area. The kept ids guarantee the affiliate case; if the channel-less cases are missing, raise the slice of 40.

- [ ] **Step 2: Write the failing integration test**

`catalog/test/pipeline.test.ts`:
```ts
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
```

- [ ] **Step 3: Run to verify failure**

Run: `cd catalog && npx vitest run test/pipeline.test.ts`
Expected: FAIL, cannot find module `../src/pipeline.js`.

- [ ] **Step 4: Implement the pipeline and main**

`catalog/src/pipeline.ts`:
```ts
import { buildCatalog } from './build.js';
import { fetchSource } from './fetch-source.js';
import { groupStreams } from './group.js';
import { checkGuard } from './guard.js';
import { emptyHistory, loadHistory, mergeHistory } from './history.js';
import { createLimiter } from './limiter.js';
import { validateLogos } from './logos.js';
import { hostOf, probeStream } from './probe.js';
import type { FetchFn, Latest, ProbeResult } from './types.js';
import { writeOutputs } from './write.js';

export interface PipelineOpts {
  fetchFn: FetchFn; apiBase: string; pagesBase: string; outDir: string; now: () => Date;
  concurrency?: number; perHost?: number; timeoutMs?: number; log?: (s: string) => void;
  allowEmptyHistory?: boolean; // local runs only; CI never sets it
  force?: boolean; // FORCE_PUBLISH: publish through the up-rate guard so the new rate becomes the baseline (Opus adversarial review 2026-09-23, major 14)
}
export interface PipelineResult {
  published: boolean; reason?: string; latest?: Latest;
  stats: { channels: number; streams: number; up: number; down: number; unverified: number };
}

export async function runPipeline(o: PipelineOpts): Promise<PipelineResult> {
  const log = o.log ?? console.log;
  const started = o.now();
  const today = started.toISOString().slice(0, 10);

  log('fetching previous history');
  const prev = o.allowEmptyHistory
    ? await loadHistory(o.fetchFn, o.pagesBase).catch(e => { log(`history unavailable (${(e as Error).message}); ALLOW_EMPTY_HISTORY set, starting empty`); return emptyHistory(); })
    : await loadHistory(o.fetchFn, o.pagesBase);
  log(`previous upRate: ${prev.upRate ?? 'none'}`);

  log('fetching source data');
  const src = await fetchSource(o.fetchFn, o.apiBase);
  const grouped = groupStreams(src);
  log(`grouped: ${grouped.channels.length} channels, ${grouped.streams.length} streams`);

  const limiter = createLimiter(o.concurrency ?? 50, o.perHost ?? 2);
  const uniqueUrls = [...new Map(grouped.streams.map(s => [s.url, s])).values()];
  const results = new Map<string, ProbeResult>();
  let done = 0;
  await Promise.all(uniqueUrls.map(s => limiter.run(hostOf(s.url), async () => {
    const r = await probeStream(s, o.fetchFn, { timeoutMs: o.timeoutMs ?? 10_000 });
    results.set(s.url, r);
    if (++done % 1000 === 0) log(`probed ${done}/${uniqueUrls.length}`);
  })));
  const counts = { up: 0, down: 0, unverified: 0 };
  for (const r of results.values()) counts[r.health]++;
  log(`probe results: ${JSON.stringify(counts)}`);

  const history = mergeHistory(prev, [...results.values()], today);
  if (o.force) log('FORCE_PUBLISH set: the up-rate drop guard is off for this run and the new rate becomes the baseline');
  const guard = checkGuard(prev.upRate, history.upRate, o.force ? Number.POSITIVE_INFINITY : undefined); // still refuses when nothing was probed
  const stats = { channels: grouped.channels.length, streams: grouped.streams.length, ...counts };
  if (!guard.ok) { log(`GUARD FAILED: ${guard.reason}`); return { published: false, reason: guard.reason, stats }; }

  log('validating logos');
  const channels = await validateLogos(grouped.channels, o.fetchFn, { limiter });

  const catalog = buildCatalog({
    grouped: { channels, streams: grouped.streams }, results, history, src,
    version: Math.floor(started.getTime() / 1000), generatedAt: started.toISOString(),
  });
  const latest = await writeOutputs(o.outDir, catalog, history);
  log(`wrote version ${latest.version}, ${latest.bytes} bytes gz`);
  return { published: true, latest, stats };
}
```

`catalog/src/main.ts`:
```ts
import { runPipeline } from './pipeline.js';

const pagesBase = process.env.PAGES_BASE;
if (!pagesBase) { console.error('PAGES_BASE is required, e.g. https://user.github.io/tv-app'); process.exit(2); }

const result = await runPipeline({
  fetchFn: fetch,
  apiBase: process.env.API_BASE ?? 'https://iptv-org.github.io/api',
  pagesBase,
  outDir: process.env.OUT_DIR ?? 'out',
  now: () => new Date(),
  concurrency: Number(process.env.CONCURRENCY ?? 50),
  perHost: Number(process.env.PER_HOST ?? 2),
  timeoutMs: Number(process.env.TIMEOUT_MS ?? 10_000),
  allowEmptyHistory: process.env.ALLOW_EMPTY_HISTORY === '1',
  force: process.env.FORCE_PUBLISH === '1',
});
console.log(JSON.stringify(result.stats));
if (!result.published) { console.error(`NOT PUBLISHED: ${result.reason}`); process.exit(1); }
```

- [ ] **Step 5: Run to verify pass**

Run: `cd catalog && npm run typecheck && npx vitest run`
Expected: typecheck clean, all tests pass, including the 4 pipeline tests.

- [ ] **Step 6: Run the real pipeline once locally against the live API**

Run: `cd catalog && PAGES_BASE=https://example.invalid ALLOW_EMPTY_HISTORY=1 OUT_DIR=out npm run run`
Expected: completes in under 60 minutes on home broadband (per-host cap slows it), prints stats with thousands of `up`, writes `catalog/out/catalog.json.gz` between 1.5 and 4 MB. `ALLOW_EMPTY_HISTORY=1` lets the DNS failure on `.invalid` start with empty history; without it the run aborts, which is the CI behavior. Record the stats and runtime in the commit message.

- [ ] **Step 7: Commit**

```bash
git add catalog/src/pipeline.ts catalog/src/main.ts catalog/scripts/make-fixture.ts catalog/test/fixtures catalog/test/pipeline.test.ts
git commit -m "catalog: pipeline entry point with offline integration test

Live run: <N> channels, <N> streams, up <N>, down <N>, unverified <N>, <M> minutes.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 13: GitHub Actions workflow and Pages deployment

**Files:**
- Create: `.github/workflows/catalog.yml`
- Create: `catalog/README.md`

**Interfaces:**
- Consumes: `npm run run` from Task 12.
- Produces: the published URLs `https://<owner>.github.io/<repo>/catalog.json.gz`, `/history.json`, `/latest.json`. The TV app plan takes the base URL from here.

Manual prerequisites the owner must do once, listed in the README: push the repo to GitHub as a public repository, then in Settings → Pages set Source to "GitHub Actions". Set a repository variable `PAGES_BASE` to `https://<owner>.github.io/<repo>`.

- [ ] **Step 1: Write the workflow**

`.github/workflows/catalog.yml`:
```yaml
name: catalog

on:
  schedule:
    - cron: '17 6 * * *'   # 06:17 UTC daily; off the hour to dodge the busiest scheduler minute
  workflow_dispatch:
    inputs:
      force:
        description: 'Publish even if the up-rate guard would refuse; the new rate becomes the baseline. Only after a confirmed permanent drop.'
        type: boolean
        default: false

permissions:
  contents: read
  pages: write
  id-token: write
  issues: write
  actions: write   # keepalive re-enables the schedule via API

concurrency:
  group: catalog
  cancel-in-progress: false

jobs:
  build:
    runs-on: ubuntu-latest
    timeout-minutes: 120
    outputs:
      version: ${{ steps.built.outputs.version }}
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: 22
          cache: npm
          cache-dependency-path: catalog/package-lock.json
      - name: Log runner egress
        run: curl -s --max-time 10 https://ipinfo.io/json || echo "egress lookup failed"
      - name: Install
        working-directory: catalog
        run: npm ci
      - name: Typecheck and test
        working-directory: catalog
        run: npm run typecheck && npm test
      - name: Build catalog
        working-directory: catalog
        env:
          PAGES_BASE: ${{ vars.PAGES_BASE }}
          OUT_DIR: out
          FORCE_PUBLISH: ${{ inputs.force == true && '1' || '0' }}   # false or absent (schedule) -> '0'
        run: npm run run
      - name: Record the built version
        id: built
        working-directory: catalog
        run: echo "version=$(jq -r .version out/latest.json)" >> "$GITHUB_OUTPUT"
      - name: Upload Pages artifact
        uses: actions/upload-pages-artifact@v3
        with:
          path: catalog/out

  deploy:
    needs: build
    runs-on: ubuntu-latest
    environment:
      name: github-pages
      url: ${{ steps.deployment.outputs.page_url }}
    steps:
      - id: deployment
        uses: actions/deploy-pages@v4
      - name: Verify Pages serves the version just built
        env:
          PAGES_BASE: ${{ vars.PAGES_BASE }}
          EXPECTED: ${{ needs.build.outputs.version }}
        run: |
          # A wrong PAGES_BASE (typo, rename, custom domain) makes every night a "first run": history 404s and the guard never fires. Fail here instead.
          for i in $(seq 1 20); do
            got=$(curl -fsS --max-time 20 "$PAGES_BASE/latest.json" | jq -r .version 2>/dev/null || echo none)
            if [ "$got" = "$EXPECTED" ]; then echo "Pages serves version $got"; exit 0; fi
            echo "attempt $i: Pages serves $got, expected $EXPECTED; waiting for propagation"; sleep 15
          done
          echo "PAGES_BASE=$PAGES_BASE does not serve the version just built; check the repository variable"; exit 1

  keepalive:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: gautamkrishnar/keepalive-workflow@v2
        with:
          use_api: true

  report-failure:
    needs: [build, deploy]
    if: failure()
    runs-on: ubuntu-latest
    steps:
      - name: Open issue
        env:
          GH_TOKEN: ${{ github.token }}
          GH_REPO: ${{ github.repository }}
        run: |
          gh label create catalog-failure --color B60205 --description "Nightly catalog run failed" --force
          run_url="${{ github.server_url }}/${{ github.repository }}/actions/runs/${{ github.run_id }}"
          existing=$(gh issue list --label catalog-failure --state open --json number --jq '.[0].number')
          if [ -n "$existing" ]; then
            gh issue comment "$existing" --body "Failed again $(date -u +%F): $run_url"
          else
            gh issue create --title "Catalog run failed $(date -u +%F)" --body "Run: $run_url" --label catalog-failure
          fi
```

The issue is opened by a separate job that depends on both `build` and `deploy`, so a failure in either opens one, and the label is created on the spot so the first failure is never swallowed. While a `catalog-failure` issue is open, later failures comment on it instead of opening one a night. The `force` input exists because the guard compares against the last *published* rate and a refused run never writes history: a permanent drop of more than 25 points would otherwise block every later run. The deploy job's last step fails when `latest.json` on Pages does not carry the version just built, which is what a wrong `PAGES_BASE` looks like; without it every night would be a silent "first run" with the guard off (Opus adversarial review 2026-09-23, major 14).

- [ ] **Step 2: Write the README**

`catalog/README.md`:
```markdown
# Catalog job

Nightly job that turns the iptv-org API into `catalog.json.gz`, `history.json` and `latest.json` for the TV app. Spec: `docs/superpowers/specs/2026-09-22-tv-app-design.md` section 4.

## One-time setup on GitHub

1. Push this repository to GitHub as a **public** repo (scheduled workflows and free Pages need it).
2. Settings → Pages → Source: **GitHub Actions**.
3. Settings → Secrets and variables → Actions → Variables → New: `PAGES_BASE` = `https://<owner>.github.io/<repo>`.
4. Actions → catalog → Run workflow (leave `force` off). First run takes 30 to 60 minutes. The deploy job's last step checks that `https://<owner>.github.io/<repo>/latest.json` returns the version it just built; if that step fails, `PAGES_BASE` is wrong.
5. Nothing else. The workflow creates the `catalog-failure` label itself the first time it needs it.

## Platform rules

- GitHub disables scheduled workflows after 60 days with no repository activity. The `keepalive` job re-enables it through the API each run.
- Output is deployed as a Pages artifact, never committed. The repo does not grow.
- Pages soft bandwidth limit is 100 GB/month. This project uses a tiny fraction.

## Local run

    cd catalog
    npm install
    npm test
    PAGES_BASE=https://<owner>.github.io/<repo> npm run run     # writes ./out

Env: `PAGES_BASE` (required), `OUT_DIR` (out), `API_BASE`, `CONCURRENCY` (50), `PER_HOST` (2), `TIMEOUT_MS` (10000), `ALLOW_EMPTY_HISTORY` (`1` for local runs when the Pages site is unreachable; never set in CI), `FORCE_PUBLISH` (`1` to publish through the up-rate guard; the workflow sets it from its `force` input).

## Guards

The run refuses to publish, and opens an issue (or comments on the open one), when the up-rate falls more than 25 points below the previous run or the API is unreachable. The previous catalog stays live.

The guard compares against the last *published* rate, so after a real, permanent drop (the runner moved region, a large host started blocking Azure, iptv-org bulk-added dead feeds) every later night would fail too. When you have confirmed the drop is genuine, run the workflow by hand with `force` on: it publishes and the new rate becomes the baseline. Never use it for a drop you cannot explain.

After each deploy the workflow fetches `latest.json` from `PAGES_BASE` and fails if it does not carry the version just built. That catches a wrong `PAGES_BASE`, which would otherwise make every night a "first run" with the guard silently off.
```

- [ ] **Step 3: Validate the workflow file parses**

Run: `npx --yes js-yaml .github/workflows/catalog.yml > /dev/null && echo yaml ok`
Expected: `yaml ok`. (`js-yaml` exits non-zero on a parse error; `@action-validator/cli` exits 0 on everything including a missing file, so it proves nothing.) If `actionlint` is installed, run it too for schema-level checks.

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/catalog.yml catalog/README.md
git commit -m "catalog: nightly GitHub Actions workflow with Pages deploy

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

- [ ] **Step 5: Owner action, outside this plan**

Push to GitHub, complete the README's one-time setup, trigger the workflow manually, and confirm `latest.json` is served. The TV app plan's sync task needs that URL.

---

## Self-review

**Spec coverage.** 3.1 runtime, artifact deploy, keepalive, bandwidth note: Task 13. 3.3 catalog base URL as an app setting: app plan. 4.1 steps 1 to 7: Tasks 7, 2, 3, 6, 8, 9, 11 and 12. 4.2 grouping rules, affiliate splitting, adult flag, `other`: Task 3. 4.3 format table and status rules: Tasks 4 and 6. Per-host cap and default UA: Tasks 5 and 6. Egress logging: Task 13. 4.4 file format including `region`, `adult`, `hasUp`: Tasks 1, 3 and 11. Logo validation: Task 10. 4.5 guards and issue on failure: Tasks 9, 12 and 13. Section 7 job rows: Tasks 9, 12, 13. Section 8 catalog tests, including master/media, ended, TS, DASH, no-channel-id, headers, offline snapshot: Tasks 3, 4, 6, 12.

**Gap found and fixed:** the spec's "Guard" step also requires that history is not updated on a failed guard. `runPipeline` returns before `writeOutputs`, so `history.json` is untouched. Covered by the second pipeline test asserting no catalog file is written.

**Type consistency.** `ProbeResult.finalHost` is produced in Task 6 and consumed in Task 11. `History.upRate` produced in Task 7, consumed in Tasks 9 and 12. `Latest` produced in Task 11, returned in Task 12. `createLimiter(...).run(host, fn)` used identically in Tasks 10 and 12.

**Placeholder scan.** The only bracketed values are in Task 12's commit message, which the executor fills from the live run output, and in the README's `<owner>/<repo>`, which are the owner's values.

**Review Focus.** Item 1, redirects: Task 6 tests "media uri resolves against the final url" and "redirect to an html login page". Item 2, relative media URI: same Task 6 test. Item 3, duplicate URL under two channels: Task 3 test "the same URL under two channels keeps both links"; Task 12 probes unique URLs once and `buildCatalog` looks each stream up by URL, so both channels get the result. Item 4, stale history URLs: Task 7 test asserts `gone` is dropped. Item 5, first run with 404s: Task 7 `loadHistory` 404 test, Task 9 null previous rate test, Task 12 first-run pipeline test.
