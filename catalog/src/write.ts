import { mkdir, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { gzipSync } from 'node:zlib';
import type { Catalog, History, Latest } from './types.js';

export async function writeOutputs(outDir: string, catalog: Catalog, history: History): Promise<Latest> {
  await mkdir(outDir, { recursive: true });
  const gz = gzipSync(Buffer.from(JSON.stringify(catalog)), { level: 9 });
  await writeFile(join(outDir, 'catalog.json.gz'), gz);
  await writeFile(join(outDir, 'history.json'), JSON.stringify(history));
  const latest: Latest = { version: catalog.version, bytes: gz.length };
  await writeFile(join(outDir, 'latest.json'), JSON.stringify(latest));
  return latest;
}
