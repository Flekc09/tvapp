// Environment parsing for main.ts. `Number('')` is 0, and a limiter with 0 slots never runs anything, so a blank
// CONCURRENCY or PER_HOST would hang the job until its 120-minute timeout (catalog branch review 2026-09-24, minor 4).
export function envNumber(value: string | undefined, fallback: number): number {
  const n = Number(value);
  return value?.trim() && Number.isFinite(n) && n > 0 ? n : fallback;
}

// A trailing slash in PAGES_BASE would make `${base}/history.json` a `//` path (minor 5).
export function trimBase(url: string): string {
  return url.replace(/\/+$/, '');
}
