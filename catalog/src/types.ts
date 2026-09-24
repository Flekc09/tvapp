// Shapes of the iptv-org API files we consume. Only the fields we use.
export interface ApiChannel {
  id: string; name: string; alt_names: string[]; network: string | null;
  country: string; categories: string[]; is_nsfw: boolean; closed: string | null;
}
export interface ApiStream {
  channel: string | null; feed: string | null; title: string | null; url: string;
  quality: string | null; labels: string[]; user_agent: string | null; referrer: string | null;
}
export interface ApiCategory { id: string; name: string; description?: string }
export interface ApiCountry { name: string; code: string; flag: string; languages: string[] }
export interface ApiLogo {
  channel: string; feed: string | null; in_use: boolean; width: number; height: number;
  format: string; url: string;
}
// broadcast_area entries look like "c/US" (country), "r/EUR" (region), "s/US-NC" (subdivision), "ct/USCLT" (city).
export interface ApiFeed { channel: string; id: string; name: string; is_main: boolean; broadcast_area: string[] }
export interface ApiSubdivision { country: string; code: string; name: string }
export interface ApiCity { country: string; subdivision: string | null; code: string; name: string }
export interface SourceData {
  channels: ApiChannel[]; streams: ApiStream[]; categories: ApiCategory[];
  countries: ApiCountry[]; logos: ApiLogo[]; feeds: ApiFeed[];
  subdivisions: ApiSubdivision[]; cities: ApiCity[];
}

// Output catalog (spec section 4.3).
export type Health = 'up' | 'down' | 'unverified';
export type Format = 'hls' | 'ts' | 'dash' | 'unknown';

export interface CatalogCountry { code: string; name: string; flag: string }
export interface CatalogCategory { id: string; name: string }
export interface CatalogChannel {
  id: string; name: string; altNames: string[]; country: string | null; region: string | null;
  categories: string[]; network: string | null; logo: string | null; adult: boolean; hasUp: boolean;
}
export interface CatalogStream {
  channel: string; url: string; format: Format; quality: string | null;
  referrer: string | null; userAgent: string | null; health: Health;
  uptime7d: number; responseMs: number | null; score: number; checkedAt: string;
}
export interface Catalog {
  version: number; generatedAt: string;
  countries: CatalogCountry[]; categories: CatalogCategory[];
  channels: CatalogChannel[]; streams: CatalogStream[];
}

// Intermediate: a stream joined to a channel, before probing.
export interface GroupedStream {
  channel: string; url: string; quality: string | null;
  referrer: string | null; userAgent: string | null;
}
export interface Grouped { channels: CatalogChannel[]; streams: GroupedStream[] }

// Probe result for one URL.
export interface ProbeResult {
  url: string; health: Health; format: Format; responseMs: number | null;
  reason: string; finalHost: string | null;
}

// History file (spec section 4.1 step 1).
export interface HistoryEntry { d: string; s: Health; ms: number | null }
export interface History {
  generatedAt: string | null; upRate: number | null;
  streams: Record<string, HistoryEntry[]>;
}

export interface Latest { version: number; bytes: number }

export type FetchFn = typeof fetch;
