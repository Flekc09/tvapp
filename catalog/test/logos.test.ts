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
