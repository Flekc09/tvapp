import { buildCatalog } from './build.js';
import { fetchSource } from './fetch-source.js';
import { groupStreams } from './group.js';
import { checkGuard } from './guard.js';
import { emptyHistory, loadHistory, mergeHistory } from './history.js';
import { createLimiter } from './limiter.js';
import { validateLogos } from './logos.js';
import { hostOf, probeStream } from './probe.js';
import type { FetchFn, GroupedStream, Latest, ProbeResult } from './types.js';
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
  const uniqueUrls = uniqueStreams(grouped.streams);
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

// One probe per URL. When streams.json lists a URL twice, probe with the record that carries a user agent or referrer:
// a server that needs them fails without them, and history is keyed by URL (catalog branch review 2026-09-24, minor 3).
export function uniqueStreams(streams: GroupedStream[]): GroupedStream[] {
  const byUrl = new Map<string, GroupedStream>();
  for (const s of streams) {
    const kept = byUrl.get(s.url);
    if (!kept || (!kept.userAgent && !kept.referrer && (s.userAgent || s.referrer))) byUrl.set(s.url, s);
  }
  return [...byUrl.values()];
}
