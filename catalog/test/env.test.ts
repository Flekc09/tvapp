import { describe, it, expect } from 'vitest';
import { envNumber, trimBase } from '../src/env.js';

describe('envNumber', () => {
  it('uses the fallback for unset, blank, zero, negative or non-numeric values', () => {
    for (const v of [undefined, '', '  ', '0', '-3', 'abc']) expect(envNumber(v, 50)).toBe(50);
  });
  it('reads a positive number', () => {
    expect(envNumber('8', 50)).toBe(8);
  });
});

describe('trimBase', () => {
  it('drops trailing slashes so paths do not get a double slash', () => {
    expect(trimBase('https://u.github.io/r/')).toBe('https://u.github.io/r');
    expect(trimBase('https://u.github.io/r')).toBe('https://u.github.io/r');
  });
});
