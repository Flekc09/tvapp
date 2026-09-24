import type { FetchFn, History, HistoryEntry, ProbeResult } from './types.js';

export const HISTORY_DAYS = 7;

export function emptyHistory(): History {
  return { generatedAt: null, upRate: null, streams: {} };
}

export async function loadHistory(fetchFn: FetchFn, baseUrl: string, timeoutMs = 60_000): Promise<History> {
  // Only a 404 means "first run". A network error must propagate: treating it as first run would wipe 7-day history and disable the guard (spec 4.5).
  const res = await fetchFn(`${baseUrl}/history.json`, { cache: 'no-store', signal: AbortSignal.timeout(timeoutMs) }); // catalog branch review 2026-09-24, minor 6
  if (res.status === 404) return emptyHistory();
  if (!res.ok) throw new Error(`loadHistory: history.json returned ${res.status}`);
  const body = (await res.json()) as History;
  // `typeof null` is 'object', so `streams: null` needs its own check (catalog branch review 2026-09-24, minor 9).
  if (!body || typeof body !== 'object' || !body.streams || typeof body.streams !== 'object' || Array.isArray(body.streams)) throw new Error('loadHistory: malformed history.json');
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
