import { describe, it, expect } from 'vitest';
import { mkdtemp, readFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { gunzipSync } from 'node:zlib';
import { writeOutputs } from '../src/write.js';
import type { Catalog, History } from '../src/types.js';

describe('writeOutputs', () => {
  it('writes gzipped catalog, history and latest, and latest matches the gz size', async () => {
    const dir = await mkdtemp(join(tmpdir(), 'cat-'));
    const catalog: Catalog = { version: 7, generatedAt: 'g', countries: [], categories: [], channels: [], streams: [] };
    const history: History = { generatedAt: 'g', upRate: 1, streams: {} };
    const latest = await writeOutputs(dir, catalog, history);
    const gz = await readFile(join(dir, 'catalog.json.gz'));
    expect(JSON.parse(gunzipSync(gz).toString())).toEqual(catalog);
    expect(JSON.parse(await readFile(join(dir, 'history.json'), 'utf8'))).toEqual(history);
    expect(JSON.parse(await readFile(join(dir, 'latest.json'), 'utf8'))).toEqual({ version: 7, bytes: gz.length });
    expect(latest).toEqual({ version: 7, bytes: gz.length });
  });
});
