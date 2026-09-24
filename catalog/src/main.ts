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
