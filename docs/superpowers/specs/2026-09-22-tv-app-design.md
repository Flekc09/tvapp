# TV App — Design Spec

**Date:** 2026-09-22
**Status:** Draft for owner review
**Owner:** Corey Payne

## 1. Purpose

An Android TV / Google TV app that turns the full iptv-org catalog of free, publicly available live-TV streams into something that feels like clean, fast, uncluttered cable. Every channel in the catalog is in scope. The app installs on any Android TV device as a sideloaded APK and runs standalone: no accounts, no sync, no server the owner has to operate.

**The one task the app is built around:** get to a specific channel and have it playing well in under two seconds, using only a remote.

### 1.1 Success criteria

| Measure | Target |
|---|---|
| Tune time, median, on verified streams | under 2 seconds to first frame |
| Tune time, 95th percentile | under 5 seconds |
| Cold launch to Guide on screen | under 3 seconds with a cached catalog |
| Channel failover | automatic, no viewer action, no endless spinner |
| Presses from any screen to a playing channel | 3 or fewer |
| Owner infrastructure cost | $0 per month |

### 1.2 Out of scope for version 1

Program guide (EPG) data, program-first Home, sports hub, team favorites, profiles, Simple Mode, multiview, rewind or timeshift, DVR, cross-device sync, voice beyond platform search, Play Store distribution, Roku, Samsung Tizen, LG webOS, Apple TV.

## 2. Source facts that shaped the design

Measured from the live iptv-org API on 2026-09-22:

| Measure | Count |
|---|---|
| Stream URLs | 17,498 |
| Distinct channels with at least one stream | 9,968 |
| Streams with no channel record | 1,944 |
| Countries | 177 |
| Streams on raw IP addresses | 2,020 |
| Streams needing a custom referrer or user agent | 954 |

Consequences:

- Many channels have 5 to 30 duplicate feeds. Grouping and ranking them is the core backend job.
- The US network and sports feeds the owner cares most about are community-submitted restreams on bare IPs. They are the least stable part of the catalog and can disappear from iptv-org at any time. Failover and user-added sources are therefore core features, not extras.
- Nearly a thousand streams will not play without per-stream headers. The player must honor them from day one.
- Ten thousand channels cannot be processed on a $20 stick at every refresh. Cleanup runs off-device.

## 3. Architecture

Two components in one repository. Nothing runs on a server the owner owns.

```
iptv-org API ──▶ catalog job (GitHub Actions, nightly) ──▶ catalog.json.gz on GitHub Pages
                                                                     │
                              TV app (Kotlin, Compose for TV, ExoPlayer) ◀── downloads on launch
                                     │
                              plays streams directly from their hosts
```

### 3.1 Catalog job (`catalog/`)

TypeScript, run by a scheduled GitHub Actions workflow once a day. Output is one gzipped JSON file plus a tiny version file, published to GitHub Pages at a fixed URL. Estimated runtime 15 to 20 minutes with 50 concurrent stream tests.

### 3.2 TV app (`app/`)

Kotlin, Jetpack Compose for TV, Media3 ExoPlayer, Room database. Downloads the catalog when the version changes, stores it locally, renders everything from the local copy. Favorites, recents, settings and user sources live only on the device.

### 3.3 Deployment model

Installing on a new TV: sideload the APK, open it, it fetches the catalog on first launch. Ten TVs or a thousand read the same static file. If the nightly job ever stops, TVs keep using their last catalog and the app's own failover skips dead streams at play time. Moving to a fuller backend later means changing one download URL in the app.

## 4. Catalog job

### 4.1 Steps

1. **Fetch.** Download channels, feeds, streams, categories, countries, subdivisions and logos from the iptv-org API. Roughly 25 MB raw.
2. **Group.** Join every stream to its channel by id. Streams with no channel record become synthetic channels named from their playlist title, with country guessed from the URL domain where possible and category `Unsorted`. Nothing is dropped.
3. **Test.** One request per stream URL with a 10 second timeout, sending the stream's referrer and user agent if present. Record HTTP status, whether the body parses as an HLS playlist, and response time. A valid playlist means `up`. Anything else is `down` with a reason. Results are retained for 7 days.
4. **Rank.** Within each channel, order streams by 7-day uptime, then declared quality, then response time. Raw IP hosts receive a small tiebreaker penalty.
5. **Write.** `catalog.json.gz` with a version stamp and generation time, and `latest.json` holding only version and byte size so the TV can check for updates with a request under 1 KB.

### 4.2 Geo-blocked streams

The test runs from US datacenters. A stream whose host responds with HTTP 403 or 451, or with a 200 whose body is not an HLS playlist, is marked `unverified`, not `down`, since those are the signatures of geo-blocking. Timeouts, refused connections, 404 and 5xx responses are `down`. Unverified streams stay in the catalog and rank last within their channel, so a TV in the stream's home region can still try them.

### 4.3 Safety rules

- Any API fetch failure aborts the run. Nothing is published.
- A run that finds fewer than half the channels of the previous published catalog is treated as suspicious and is not published.
- Either failure opens a GitHub issue automatically.
- The previous catalog stays live until a new one publishes successfully. Partial files are never published.

### 4.4 Catalog file format

Four lists. Estimated 2 to 3 MB gzipped, about 12 MB in the TV database.

```
{
  "version": 20260922,
  "generatedAt": "2026-09-22T06:00:00Z",
  "countries":  [{ "code": "US", "name": "United States", "flag": "🇺🇸" }],
  "categories": [{ "id": "sports", "name": "Sports" }],
  "channels":   [{ "id": "SECNetwork.us", "name": "SEC Network", "altNames": [],
                   "country": "US", "categories": ["sports"], "network": "ESPN",
                   "logo": "https://..." }],
  "streams":    [{ "channel": "SECNetwork.us", "url": "http://...", "quality": "720p",
                   "referrer": null, "userAgent": null,
                   "health": "up" | "down" | "unverified",
                   "uptime7d": 0.86, "responseMs": 412, "checkedAt": "..." }]
}
```

## 5. TV app

### 5.1 Layers

Each layer is independently testable and holds no logic belonging to another.

**Data layer.** Room database with the four catalog tables plus three local-only tables: `favorites`, `recents`, `user_sources`, and one measurement table `stream_stats` (see 5.4). A sync worker checks `latest.json` on launch and every 24 hours, downloads the catalog when the version changes, and swaps the catalog tables in a single transaction so the Guide never shows a half-loaded state.

**Source layer.** One interface, two implementations: the iptv-org catalog and user-added M3U URLs. The M3U parser handles standard `#EXTINF` attributes including logo, group title, and `#EXTVLCOPT` header lines. Both feed the same channel and stream tables tagged by source, so favorites, search and the Guide behave identically regardless of origin.

**Playback layer.** One player controller wrapping Media3 ExoPlayer, taking a channel id and owning stream selection, failover, preload and measurement. Per-stream referrer and user agent go into the data source factory.

**UI layer.** Compose for TV screens reading from view models that read from the database. No business logic. Built screen by screen by the owner with Claude using the `ui-ux` agent's playbooks.

### 5.2 Buffer settings

| Setting | Value |
|---|---|
| Media required before first frame | ~1 second |
| Target buffer ahead of playback | 15 seconds |
| Maximum buffer | 30 seconds |
| Back buffer | none |
| Adaptive bitrate | on, prefer highest rendition once throughput is proven |

### 5.3 Fast tune

- **Prefetch on focus.** Highlighting a channel in the Guide, Favorites, or the player's channel strip fetches that channel's top-ranked stream playlist (about 1 KB). Same for the next and previous channels in the current list while watching.
- **Preload.** A second ExoPlayer instance, muted and undrawn, preloads the highlighted channel (in lists) or the adjacent channels (while watching). On OK or channel up/down the video surface is swapped, giving first frame in a few hundred milliseconds. Setting: preload count 0, 1 or 2, default 1.
- **Rank by measured startup.** Stream order within a channel incorporates locally measured time-to-first-frame (5.4), so the fastest-starting source from this TV goes first.

Preloading is deliberately limited to one or two channels. Continuously buffering every favorite is rejected: it multiplies bandwidth, trips single-connection limits on many hosts, produces stale live segments, and overloads the device.

### 5.4 Failover and stream selection

The controller treats a channel as an ordered queue of its streams.

1. Try stream 1. If no first frame within 4 seconds, or the player reports an error, try the next.
2. If a stream dies mid-play after having worked, try the next and do not return to the dead one during this viewing.
3. If every stream fails, show "Channel unavailable" with the number tried, and offer Retry and Next Channel.
4. Every failure is recorded locally with a timestamp. A stream that failed on this TV in the last hour ranks last within its channel. Demoted means tried last, never skipped. The demotion expires after one hour.
5. The app never switches away from a stream that is playing acceptably, even if a higher-ranked one recovers. The queue is rebuilt fresh on the next tune.
6. Switching is silent where possible: the old surface holds its last frame until the new stream produces one.
7. The player's Sources menu lists every stream for the channel with quality and health. Picking one manually plays it and clears its demotion.

**Passive measurement.** While playing, the controller records achieved bitrate, rebuffer count, and time-to-first-frame per stream into `stream_stats`. These feed local ranking so each TV learns which feeds work from its own network. No stream is ever probed in the background for measurement.

**Poor-signal prompt.** If the current stream rebuffers 3 times within a minute, or plays at a resolution well below its declared quality for more than 30 seconds, a small prompt offers "Try another source" with one button. Automatic switching on degradation is a settings toggle, off by default in version 1, reserved for V2 once measurement data shows the right thresholds.

Channel up and down move through the currently visible list (All, a country, a category, Favorites, Recent), so surfing stays within whatever was being browsed.

## 6. Screens

Fixed here: which screens exist, how the remote moves between them, and what each must do. Look, layout and feel are the owner's, worked out per screen with the `ui-ux` agent.

Persistent left rail: Guide, Browse, Search, Favorites, Settings. Remote-only: D-pad, OK, Back, Play/Pause do everything. Nothing requires a pointer, a phone, or typing except Search.

1. **Player.** Full-screen video. OK or Down shows an overlay with channel name, logo, source quality and health. Up and Down surf channels. Left or Right opens a quick channel strip along the bottom. Menu opens Sources and Add to Favorites. Back returns to the originating screen.
2. **Guide.** Default landing screen. Vertical channel list with logo, name and country, filtered by the selected collection: Favorites, Recent, All, or a country or category. Selecting a channel plays it. The filter is remembered between launches.
3. **Browse.** Two columns, countries left and categories right, each with a channel count. Picking one opens the Guide filtered to it.
4. **Search.** Platform on-screen keyboard, live results matching channel names and alternate names. Voice search where the remote has a microphone.
5. **Favorites.** The Guide filtered to favorites, one press from anywhere.
6. **Settings.** Add or remove M3U sources, force catalog refresh, show catalog version and date, choose startup screen, set preload count, toggle auto-switch. Diagnostics page: current stream URL, resolution, bitrate, buffer level, measured tune time.

Rules for every screen: focus is always visible from ten feet, and a channel is never more than three presses from playing.

## 7. Error handling

| Layer | Condition | Behavior |
|---|---|---|
| Catalog job | API fetch fails, or batch error | Abort, keep previous catalog, open GitHub issue |
| Catalog job | Fewer than half the previous channel count | Do not publish, open GitHub issue |
| TV sync | No network on launch | Use cached catalog silently |
| TV sync | No network and no cached catalog | One screen: "Can't reach the catalog, check your connection", Retry |
| TV sync | Corrupt download | Discard, keep old catalog |
| Playback | All streams fail | "Channel unavailable (tried N)", Retry, Next Channel |
| M3U source | URL doesn't parse | Show the first failing line |

## 8. Testing

**Catalog job.** Unit tests for grouping, ranking and M3U parsing against fixture files, including streams without a channel id and streams with headers. One integration test runs the full pipeline against a saved snapshot of the iptv-org API so it is repeatable offline.

**TV app.** Unit tests for the stream queue, demotion expiry and sync version logic using a fake clock. Instrumented test on the Android TV emulator: load a fixture catalog, tune a channel, kill the stream via a local fake HLS server, assert failover to the next stream. Remote-navigation tests asserting every screen is reachable and every channel is at most three presses from playing.

**Manual, before each release, on the real stick.** Cold launch time, tune time on five known channels, surfing through twenty channels, one hour of continuous play.

## 9. Development environment

- Android Studio with the Android TV emulator for daily development.
- One physical Google TV device for remote and network testing. Owner currently has a cast-only Chromecast, which cannot run apps. An onn Google TV 4K stick (about $20) or equivalent is needed before release testing.
- Framework agents in `.claude/agents/`: `sdlc` governs the engineering lifecycle, `ui-ux` guides screen design.

## 10. Release plan

**Version 1 (this spec).** Catalog job, catalog sync, all six screens, favorites, recents, channel surfing, prefetch and preload, failover with local learning, poor-signal prompt, user M3U sources, diagnostics.

**Version 2.** Automatic switching on degradation, program guide data where obtainable, program-first Home, Continue Watching, custom collections.

**Version 3.** Sports and event matching, multiview, profiles, Fire TV packaging, and a fuller backend if the static catalog proves limiting.

## 11. Decisions recorded

| Decision | Choice | Rejected alternatives |
|---|---|---|
| Content source | iptv-org, whole catalog, plus user M3U | Family antenna network, paid IPTV provider |
| Platform | Android TV / Google TV, sideloaded | Roku, Tizen, webOS, Play Store |
| Backend | Nightly GitHub Actions job to static file | Fully on-device processing, self-hosted server |
| Rewind | None, stability buffer only | 60-second local timeshift |
| Multi-source monitoring | Passive measurement, switch only on bad | Live probing and switching to best |
| Favorites pre-buffering | Preload 1 to 2 focused or adjacent channels | Continuous buffering of all favorites |
| Users | One standalone TV at a time | Shared profiles and sync across houses |
