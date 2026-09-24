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
