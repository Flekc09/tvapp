export function checkGuard(prevUpRate: number | null, newUpRate: number | null, maxDrop = 0.25): { ok: true } | { ok: false; reason: string } {
  if (newUpRate === null) return { ok: false, reason: 'no streams were probed' };
  if (prevUpRate === null) return { ok: true };
  if (prevUpRate - newUpRate > maxDrop) {
    return { ok: false, reason: `up rate fell from ${prevUpRate.toFixed(2)} to ${newUpRate.toFixed(2)}, more than ${maxDrop} allowed; runner network or IP block suspected` };
  }
  return { ok: true };
}
