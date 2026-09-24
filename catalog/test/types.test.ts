import { describe, it, expect } from 'vitest';
import type { Catalog } from '../src/types.js';

describe('types', () => {
  it('a minimal catalog object satisfies the Catalog type', () => {
    const c: Catalog = {
      version: 1, generatedAt: '2026-09-22T06:00:00Z',
      countries: [], categories: [], channels: [], streams: [],
    };
    expect(c.version).toBe(1);
  });
});
