import { describe, it, expect } from 'vitest';
import { checkGuard } from '../src/guard.js';

describe('checkGuard', () => {
  it('passes on first run with no previous rate', () => { expect(checkGuard(null, 0.6)).toEqual({ ok: true }); });
  it('passes when the drop is within 25 points, including exactly 25', () => { expect(checkGuard(0.6, 0.36)).toEqual({ ok: true }); expect(checkGuard(0.6, 0.35)).toEqual({ ok: true }); });
  it('fails when the drop exceeds 25 points', () => {
    const r = checkGuard(0.6, 0.30);
    expect(r.ok).toBe(false); if (!r.ok) expect(r.reason).toMatch(/0\.60.*0\.30/);
  });
  it('fails when nothing was probed', () => { expect(checkGuard(0.6, null).ok).toBe(false); });
  it('passes when the rate rises', () => { expect(checkGuard(0.3, 0.9)).toEqual({ ok: true }); });
});
