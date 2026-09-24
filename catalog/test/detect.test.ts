import { describe, it, expect } from 'vitest';
import { detectFormat, parseHls, isTsSync, isMpd } from '../src/detect.js';

const enc = (s: string) => new TextEncoder().encode(s);
const MASTER = '#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=5800000,RESOLUTION=1280x720\ntracks-v1a1/mono.m3u8\n';
const MEDIA = '#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:10\n#EXT-X-MEDIA-SEQUENCE:4287341\n#EXTINF:10,\n20260922_194010.ts\n#EXTINF:9,\n20260922_194020.ts\n';
const ENDED = MEDIA + '#EXT-X-ENDLIST\n';

describe('parseHls', () => {
  it('recognises a master playlist and returns the first media uri', () => {
    expect(parseHls(MASTER)).toEqual({ kind: 'master', mediaUri: 'tracks-v1a1/mono.m3u8' });
  });
  it('recognises a live media playlist with segments', () => {
    expect(parseHls(MEDIA)).toEqual({ kind: 'media', segments: 2, ended: false });
  });
  it('flags an ended playlist', () => {
    expect(parseHls(ENDED)).toEqual({ kind: 'media', segments: 2, ended: true });
  });
  it('a media playlist with no segments has segments 0', () => {
    expect(parseHls('#EXTM3U\n#EXT-X-TARGETDURATION:10\n')).toEqual({ kind: 'media', segments: 0, ended: false });
  });
  it('anything without #EXTM3U is invalid', () => {
    expect(parseHls('<html>Not found</html>')).toEqual({ kind: 'invalid' });
    expect(parseHls('Not found')).toEqual({ kind: 'invalid' });
  });
  it('a master with EXT-X-STREAM-INF but no uri line is master with null mediaUri', () => {
    expect(parseHls('#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\n')).toEqual({ kind: 'master', mediaUri: null });
  });
});

describe('detectFormat', () => {
  it('uses content-type first', () => {
    expect(detectFormat('http://x/a', 'application/vnd.apple.mpegurl', enc('<html>'))).toBe('hls');
    expect(detectFormat('http://x/a', 'video/mp2t', enc(''))).toBe('ts');
    expect(detectFormat('http://x/a', 'application/dash+xml', enc(''))).toBe('dash');
  });
  it('falls back to url suffix', () => {
    expect(detectFormat('http://x/a.m3u8?token=1', 'application/octet-stream', enc('#EXTM3U'))).toBe('hls');
    expect(detectFormat('http://x/a.m3u', null, enc('#EXTM3U'))).toBe('hls');
    expect(detectFormat('http://x/a.ts', null, enc(''))).toBe('ts');
    expect(detectFormat('http://x/a.mpd', null, enc(''))).toBe('dash');
  });
  it('falls back to body sniffing', () => {
    expect(detectFormat('http://x/live', 'application/octet-stream', enc('#EXTM3U\n#EXT-X-VERSION:3'))).toBe('hls');
    const ts = new Uint8Array(400); ts[0] = 0x47; ts[188] = 0x47;
    expect(detectFormat('http://x/live', 'application/octet-stream', ts)).toBe('ts');
    expect(detectFormat('http://x/live', 'text/xml', enc('<?xml version="1.0"?><MPD><Period></Period></MPD>'))).toBe('dash');
  });
  it('returns unknown for html and json bodies', () => {
    expect(detectFormat('http://x/live', 'text/html', enc('<html>'))).toBe('unknown');
    expect(detectFormat('http://x/live', 'application/json', enc('{"error":1}'))).toBe('unknown');
  });
});

describe('isTsSync', () => {
  it('requires 0x47 at byte 0 and 188', () => {
    const ok = new Uint8Array(200); ok[0] = 0x47; ok[188] = 0x47;
    expect(isTsSync(ok)).toBe(true);
    const short = new Uint8Array(10); short[0] = 0x47;
    expect(isTsSync(short)).toBe(false);
  });
});

describe('isMpd', () => {
  it('needs MPD and Period elements', () => {
    expect(isMpd('<MPD><Period/></MPD>')).toBe(true);
    expect(isMpd('<MPD></MPD>')).toBe(false);
  });
});
