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
