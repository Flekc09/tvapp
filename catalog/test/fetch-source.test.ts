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
