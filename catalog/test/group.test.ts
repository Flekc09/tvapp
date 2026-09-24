import { describe, it, expect } from 'vitest';
import { groupStreams, pickLogo, syntheticId } from '../src/group.js';
import type { SourceData } from '../src/types.js';

const st = (channel: string | null, feed: string | null, title: string, url: string, extra: Partial<SourceData['streams'][number]> = {}) =>
  ({ channel, feed, title, url, quality: null, labels: [], user_agent: null, referrer: null, ...extra });

const base: SourceData = {
  channels: [
    { id: 'ABC.us', name: 'ABC', alt_names: ['ABC East'], network: 'ABC', country: 'US', categories: ['general'], is_nsfw: false, closed: null },
    { id: 'Old.us', name: 'Old', alt_names: [], network: null, country: 'US', categories: [], is_nsfw: false, closed: '2020-01-01' },
    { id: 'NoStreams.us', name: 'No Streams', alt_names: [], network: null, country: 'US', categories: [], is_nsfw: false, closed: null },
    { id: 'Adult.us', name: 'Adult', alt_names: [], network: null, country: 'US', categories: ['xxx'], is_nsfw: false, closed: null },
    { id: 'NoCat.us', name: 'No Cat', alt_names: [], network: null, country: 'US', categories: [], is_nsfw: false, closed: null },
    { id: 'Nsfw.us', name: 'Nsfw', alt_names: [], network: null, country: 'US', categories: ['general'], is_nsfw: true, closed: null },
  ],
  streams: [
    st('ABC.us', 'East', 'ABC', 'http://a/1.m3u8', { quality: '1080p' }),
    st('ABC.us', 'West', 'ABC W', 'http://a/2.m3u8', { user_agent: 'UA', referrer: 'http://r' }),
    st('ABC.us', 'WSOCTV', 'WSOC', 'http://wsoc/1.m3u8'),
    st('ABC.us', 'WSOCTV', 'WSOC alt', 'http://wsoc/2.m3u8'),
    st('ABC.us', 'KATC', 'KATC', 'http://katc/1.m3u8'),
    st('ABC.us', 'MissingFeed', 'x', 'http://missing/1.m3u8'),
    st('ABC.us', 'MIXED', 'Mixed', 'http://mixed/1.m3u8'),
    st('Nsfw.us', null, 'Nsfw', 'http://nsfw/1.m3u8'),
    st('Old.us', null, 'Old', 'http://old/1.m3u8'),
    st('Adult.us', null, 'Adult', 'http://adult/1.m3u8'),
    st('NoCat.us', null, 'No Cat', 'http://nocat/1.m3u8'),
    st(null, null, 'TV Publica', 'http://playcom.trapemn.tv:1935/x/playlist.m3u8', { quality: '1080p' }),
    st(null, null, 'Mystery', 'http://1.2.3.4:8080/y/index.m3u8'),
    st(null, null, 'Deutsche Welle', 'http://live.example.de/x.m3u8'),
    st(null, null, 'Redirected', 'http://jmp2.uk/abc-123.m3u8'),
    st('Ghost.xx', null, 'Ghost', 'http://ghost/1.m3u8'),
  ],
  categories: [{ id: 'general', name: 'General' }],
  countries: [{ name: 'United States', code: 'US', flag: '🇺🇸', languages: [] }, { name: 'Tuvalu', code: 'TV', flag: '🇹🇻', languages: [] }, { name: 'Germany', code: 'DE', flag: '🇩🇪', languages: [] }, { name: 'United Kingdom', code: 'UK', flag: '🇬🇧', languages: [] }],
  logos: [
    { channel: 'ABC.us', feed: 'West', in_use: true, width: 100, height: 100, format: 'PNG', url: 'http://l/west.png' },
    { channel: 'ABC.us', feed: null, in_use: true, width: 300, height: 200, format: 'PNG', url: 'http://l/main.png' },
    { channel: 'ABC.us', feed: null, in_use: false, width: 300, height: 200, format: 'PNG', url: 'http://l/old.png' },
  ],
  feeds: [
    { channel: 'ABC.us', id: 'East', name: 'East', is_main: true, broadcast_area: ['c/US'] },
    { channel: 'ABC.us', id: 'West', name: 'West', is_main: false, broadcast_area: ['c/US'] },
    { channel: 'ABC.us', id: 'WSOCTV', name: 'WSOC-TV', is_main: false, broadcast_area: ['ct/USCLT'] },
    { channel: 'ABC.us', id: 'KATC', name: 'KATC', is_main: false, broadcast_area: ['s/US-LA'] },
    { channel: 'ABC.us', id: 'MIXED', name: 'Mixed', is_main: false, broadcast_area: ['c/US', 'ct/USCLT'] },
  ],
  subdivisions: [{ country: 'US', code: 'US-LA', name: 'Louisiana' }],
  cities: [{ country: 'US', subdivision: 'US-NC', code: 'USCLT', name: 'Charlotte' }],
};

describe('groupStreams', () => {
  const g = groupStreams(base);
  const byId = (id: string) => g.channels.find(c => c.id === id);
  it('keeps only open channels that have streams; adult channels are kept and flagged', () => {
    const ids = g.channels.map(c => c.id);
    expect(ids).not.toContain('Old.us');
    expect(ids).not.toContain('NoStreams.us');
    expect(ids).toContain('ABC.us');
    expect(byId('Adult.us')!.adult).toBe(true);
    expect(byId('ABC.us')!.adult).toBe(false);
  });
  it('maps channel fields and picks the main in-use logo', () => {
    expect(byId('ABC.us')).toEqual({ id: 'ABC.us', name: 'ABC', altNames: ['ABC East'], country: 'US', region: null, categories: ['general'], network: 'ABC', logo: 'http://l/main.png', adult: false, hasUp: false });
  });
  it('country-level feeds and unknown feed ids stay on the plain channel', () => {
    const urls = g.streams.filter(s => s.channel === 'ABC.us').map(s => s.url);
    expect(urls).toEqual(['http://a/1.m3u8', 'http://a/2.m3u8', 'http://missing/1.m3u8']);
    expect(g.streams.find(s => s.url === 'http://a/2.m3u8')).toEqual({ channel: 'ABC.us', url: 'http://a/2.m3u8', quality: null, referrer: 'http://r', userAgent: 'UA' });
  });
  it('a city-level feed becomes its own channel with region from cities, inheriting parent fields', () => {
    const wsoc = byId('ABC.us@WSOCTV')!;
    expect(wsoc).toEqual({ id: 'ABC.us@WSOCTV', name: 'ABC · WSOC-TV', altNames: ['ABC East'], country: 'US', region: 'Charlotte', categories: ['general'], network: 'ABC', logo: 'http://l/main.png', adult: false, hasUp: false });
    expect(g.streams.filter(s => s.channel === 'ABC.us@WSOCTV').map(s => s.url)).toEqual(['http://wsoc/1.m3u8', 'http://wsoc/2.m3u8']);
  });
  it('a state-level feed gets its region from subdivisions', () => {
    expect(byId('ABC.us@KATC')!.region).toBe('Louisiana');
  });
  it('channels with no categories get other', () => {
    expect(byId('NoCat.us')!.categories).toEqual(['other']);
  });
  it('drops streams whose channel is closed, but keeps unknown channel ids as synthetic channels', () => {
    const urls = g.streams.map(s => s.url);
    expect(urls).not.toContain('http://old/1.m3u8');
    expect(g.streams.find(s => s.url === 'http://ghost/1.m3u8')!.channel).toBe(syntheticId('http://ghost/1.m3u8'));
  });
  it('flags is_nsfw channels as adult even without the xxx category', () => {
    expect(byId('Nsfw.us')!.adult).toBe(true);
  });
  it('a feed with both a country and a city area is regional (any s/ or ct/ entry splits)', () => {
    expect(byId('ABC.us@MIXED')!.region).toBe('Charlotte');
    expect(g.streams.find(s => s.url === 'http://mixed/1.m3u8')!.channel).toBe('ABC.us@MIXED');
  });
  it('creates a synthetic channel for streams with no channel, guessing country from ccTLD except generic-use TLDs and redirector hosts', () => {
    const syn = g.channels.filter(c => c.id.startsWith('synthetic:'));
    expect(syn).toHaveLength(5);
    const publica = syn.find(c => c.name === 'TV Publica')!;
    expect(publica.country).toBeNull(); // .tv is sold as a generic domain; guessing would file 390 live channels under Tuvalu (Opus adversarial review 2026-09-23, major 13)
    expect(publica.categories).toEqual(['other']);
    expect(publica.adult).toBe(false);
    expect(publica.id).toBe(syntheticId('http://playcom.trapemn.tv:1935/x/playlist.m3u8'));
    expect(syn.find(c => c.name === 'Mystery')!.country).toBeNull();
    expect(syn.find(c => c.name === 'Deutsche Welle')!.country).toBe('DE');
    expect(syn.find(c => c.name === 'Redirected')!.country).toBeNull(); // jmp2.uk redirects to FAST channels from anywhere; 777 of them are not British
  });
  it('the same URL under two channels keeps both links', () => {
    const src: SourceData = { ...base, streams: [
      st('ABC.us', null, 'x', 'http://same/x.m3u8'),
      st('NoStreams.us', null, 'x', 'http://same/x.m3u8'),
    ] };
    expect(groupStreams(src).streams.map(s => s.channel).sort()).toEqual(['ABC.us', 'NoStreams.us']);
  });
});

describe('pickLogo', () => {
  it('returns null when no logo exists', () => { expect(pickLogo([], 'X.us')).toBeNull(); });
  it('falls back to any in-use logo when no feed-less one exists', () => {
    expect(pickLogo([{ channel: 'X.us', feed: 'HD', in_use: true, width: 1, height: 1, format: 'PNG', url: 'u' }], 'X.us')).toBe('u');
  });
});
