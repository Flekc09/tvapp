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
