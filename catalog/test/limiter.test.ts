import { describe, it, expect } from 'vitest';
import { createLimiter } from '../src/limiter.js';

function deferred() {
  let resolve!: () => void;
  const promise = new Promise<void>(r => { resolve = r; });
  return { promise, resolve };
}

describe('createLimiter', () => {
  it('never exceeds the global limit', async () => {
    const lim = createLimiter(2, 10);
    let running = 0, peak = 0;
    const gates = [deferred(), deferred(), deferred()];
    const tasks = gates.map((g, i) => lim.run(`h${i}`, async () => {
      running++; peak = Math.max(peak, running); await g.promise; running--;
    }));
    await new Promise(r => setTimeout(r, 10));
    expect(running).toBe(2);
    gates.forEach(g => g.resolve());
    await Promise.all(tasks);
    expect(peak).toBe(2);
  });
  it('never exceeds the per-host limit even when global has room', async () => {
    const lim = createLimiter(10, 1);
    let running = 0, peak = 0;
    const gates = [deferred(), deferred()];
    const tasks = gates.map(g => lim.run('same-host', async () => {
      running++; peak = Math.max(peak, running); await g.promise; running--;
    }));
    await new Promise(r => setTimeout(r, 10));
    expect(running).toBe(1);
    gates.forEach(g => g.resolve());
    await Promise.all(tasks);
    expect(peak).toBe(1);
  });
  it('returns the task result and propagates errors without deadlocking', async () => {
    const lim = createLimiter(1, 1);
    await expect(lim.run('h', async () => { throw new Error('boom'); })).rejects.toThrow('boom');
    expect(await lim.run('h', async () => 42)).toBe(42);
  });
});
