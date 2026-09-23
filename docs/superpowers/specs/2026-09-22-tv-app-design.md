# TV App — Design Spec

**Date:** 2026-09-22
**Status:** Draft for owner review, revised after adversarial review
**Owner:** Corey Payne

## 1. Purpose

An Android TV / Google TV app that turns the full iptv-org catalog of free, publicly available live-TV streams into something that feels like clean, fast, uncluttered cable. Every channel in the catalog is in scope. The app installs on any Android TV device as a sideloaded APK and runs standalone: no accounts, no sync, no server the owner has to operate.

**The one task the app is built around:** get to a specific channel and have it playing well in under two seconds, using only a remote.

### 1.1 Success criteria

| Measure | Target |
|---|---|
| Tune time, median, on `up` streams over normal broadband | under 2 seconds to first frame |
| Tune time, 95th percentile, same conditions | under 5 seconds |
| Cold launch to Guide on screen | under 3 seconds with a cached catalog |
| Channel failover | automatic, no viewer action, no endless spinner |
| From a focused channel row | one press (OK) plays it |
| From any screen | a favorite or recent channel plays in 3 presses or fewer |
| Owner infrastructure cost | $0 per month |

Tune-time targets apply to streams marked `up` in the catalog on a normal home broadband link. Slow origins exist and no client setting fixes them. Diagnostics shows the measured number.

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
- Most of the catalog is plain HTTP, and raw-IP hosts cannot have valid TLS certificates. The app must allow cleartext traffic.
- Nearly a thousand streams will not play without per-stream headers. The player must honor them from day one.
- Not every URL is HLS. The catalog also contains raw MPEG-TS over HTTP, DASH manifests, and dead hosts returning HTML. Format must be detected, not assumed.
- Ten thousand channels cannot be processed on a $20 stick at every refresh. Cleanup runs off-device.

## 3. Architecture

Two components in one repository. Nothing runs on a server the owner owns.

```
iptv-org API ──▶ catalog job (GitHub Actions, nightly) ──▶ catalog.json.gz + history.json on GitHub Pages
                       ▲                                              │
                       └──── reads previous history.json ─────────────┘
                                                                      │
                              TV app (Kotlin, Compose for TV, ExoPlayer) ◀── downloads on launch
                                     │
                              plays streams directly from their hosts
```

### 3.1 Catalog job (`catalog/`)

TypeScript, run by a scheduled GitHub Actions workflow once a day. Publishes to GitHub Pages using the Pages artifact deploy action, never by committing files to a branch, so the repository does not grow. Output is one gzipped catalog, one history file, and one tiny version file at a fixed URL.

Expected runtime is 30 to 45 minutes at 50 total concurrency with a per-host cap of 2, well inside the 6-hour job limit. The earlier 15 to 20 minute estimate assumed few timeouts, which this catalog does not deliver.

Two GitHub platform rules the job must respect:

- Scheduled workflows in a public repository are disabled after 60 days without repository activity. The workflow includes a step that touches a timestamp file via the API so it counts as activity, and the README notes the rule.
- GitHub Pages has a soft bandwidth limit of 100 GB per month. At 3 MB per download that is roughly 1,000 TVs downloading a new catalog every day, which is far beyond this project's use. The spec makes no claim beyond tens of TVs.

### 3.2 TV app (`app/`)

Kotlin, Jetpack Compose for TV, Media3 ExoPlayer, Room database. Downloads the catalog when the version changes, stores it locally, renders everything from the local copy. Favorites, recents, settings and user sources live only on the device.

### 3.3 Deployment model

Installing on a new TV: sideload the APK, open it, it fetches the catalog on first launch. Every TV reads the same static file. If the nightly job ever stops, TVs keep using their last catalog and the app's own failover skips dead streams at play time.

The catalog base URL is a setting in the app with the GitHub Pages address as the built-in default. Moving to a different host later, or recovering if the Pages site is taken down, means changing that setting on each TV, not rebuilding the app.

## 4. Catalog job

### 4.1 Steps

1. **Fetch previous state.** Download `history.json` from the Pages URL. It holds, per stream URL, the last 7 daily test results. On the first ever run it is absent and history starts empty.
2. **Fetch source data.** Download channels, feeds, streams, categories, countries, subdivisions and logos from the iptv-org API. Roughly 25 MB raw.
3. **Group.** Join every stream to its channel by id. Streams with no channel record become synthetic channels named from their playlist title, with country guessed from the URL domain where possible and category `Unsorted`. Nothing is dropped.
4. **Detect format and test.** See 4.2.
5. **Rank.** Within each channel, compute a catalog score from 7-day uptime, then declared quality, then median response time, with a small penalty for raw IP hosts. Order streams by that score.
6. **Guard.** See 4.4.
7. **Write.** `catalog.json.gz`, updated `history.json`, and `latest.json` holding only version and byte size so the TV can check for updates with a request under 1 KB. `version` is the run's Unix epoch seconds, so a same-day re-run always produces a new version.

### 4.2 Format detection and health test

Each stream gets one GET with a 10 second timeout, sending its referrer and user agent if present, otherwise sending the exact default user agent the TV app uses. Per-host concurrency is capped at 2 to avoid rate limiting.

Format is decided from Content-Type and URL suffix first, then body:

| Detected | Format field | Health test |
|---|---|---|
| HLS master playlist | `hls` | Follow to the first media playlist. `up` only if it lists segments and has no `#EXT-X-ENDLIST`. |
| HLS media playlist | `hls` | `up` if it lists segments and has no `#EXT-X-ENDLIST`. |
| MPEG-TS (`video/mp2t` or `.ts`) | `ts` | `up` if the first bytes are a valid TS sync pattern. |
| DASH (`.mpd` or `application/dash+xml`) | `dash` | `up` if the body parses as an MPD with at least one period. |
| Anything else with 200 | `unknown` | `unverified` |

Status rules:

- **`up`**: the format-specific test passed.
- **`unverified`**: HTTP 403 or 451, or a 200 body that does not match any known format. These are the signatures of geo-blocking, missing tokens, or a host that answers but is not playable from the runner. Unverified streams stay in the catalog and rank last within their channel, so a TV in the stream's home region can still try them.
- **`down`**: timeout, refused connection, DNS failure, 404, or any 5xx.

The runner's egress region is logged per run, since GitHub-hosted runners are Azure hosts in unspecified regions and results depend on it.

### 4.3 Catalog file format

Four lists. Estimated 2 to 3 MB gzipped, 15 to 20 MB uncompressed, about 12 MB in the TV database.

```
{
  "version": 1758520800,
  "generatedAt": "2026-09-22T06:00:00Z",
  "countries":  [{ "code": "US", "name": "United States", "flag": "🇺🇸" }],
  "categories": [{ "id": "sports", "name": "Sports" }],
  "channels":   [{ "id": "SECNetwork.us", "name": "SEC Network", "altNames": [],
                   "country": "US", "categories": ["sports"], "network": "ESPN",
                   "logo": "https://..." }],
  "streams":    [{ "channel": "SECNetwork.us", "url": "http://...", "format": "hls",
                   "quality": "720p", "referrer": null, "userAgent": null,
                   "health": "up" | "down" | "unverified",
                   "uptime7d": 0.86, "responseMs": 412, "score": 0.81, "checkedAt": "..." }]
}
```

Logos are validated by the job with a HEAD request. Dead logo URLs are replaced with null so the app never requests them.

### 4.4 Safety rules

- Any API fetch failure aborts the run. Nothing is published.
- A run whose `up` rate is more than 25 percentage points below the previous published catalog is treated as a runner or network problem, not a catalog change. It is not published and history is not updated, so one bad night cannot poison 7-day uptime.
- Either failure opens a GitHub issue automatically.
- The previous catalog stays live until a new one publishes successfully. Partial files are never published.

## 5. TV app

### 5.1 Platform requirements

- Manifest declares `LEANBACK_LAUNCHER`, `android.software.leanback`, touchscreen not required, and a 320x180 banner, so the app appears in the TV launcher after sideloading.
- Cleartext HTTP is enabled through a network security config, and the HTTP data source allows cross-protocol redirects. Self-signed HTTPS on raw-IP hosts is accepted for stream traffic only.
- One shared OkHttp client is used for catalog sync, playlist prefetch, and ExoPlayer's data source, so connection pooling and DNS caching carry across, and the app's user agent matches what the catalog job sends.
- `largeHeap` is set. Minimum target device is 1 GB RAM.

### 5.2 Layers

Each layer is independently testable and holds no logic belonging to another.

**Data layer.** Room database. Catalog tables (`countries`, `categories`, `channels`, `streams`) carry a `source` column. User-added M3U content lives in the same tables with `source = 'user:<id>'`. Local-only tables: `favorites`, `recents`, `user_sources`, `stream_stats`, `stream_failures`. The sync worker checks `latest.json` on launch and every 24 hours. On a new version it stream-parses the gzipped JSON with a streaming reader, never holding the whole document in memory, and inserts in batches of 1,000 inside one transaction that first deletes only rows where `source = 'iptv'`. User rows are untouched. Favorites whose channel id no longer exists after a sync are kept and shown as "no longer in catalog" until the user removes them.

**Source layer.** One interface, two implementations: the iptv-org catalog and user-added M3U URLs. The M3U parser handles standard `#EXTINF` attributes including logo and group title, plus `#EXTVLCOPT` header lines for referrer and user agent. Both feed the same tables tagged by source, so favorites, search and the Guide behave identically regardless of origin.

**Playback layer.** One player controller wrapping Media3 ExoPlayer, taking a channel id and owning stream selection, failover, preload and measurement. Per-stream referrer and user agent go into the data source factory. `BehindLiveWindowException` is handled by re-seeking to the live edge, not as a stream failure.

**UI layer.** Compose for TV screens reading from view models that read from the database. No business logic. Channel logos load through Coil with a disk cache, a hard size cap, and a placeholder on error. Built screen by screen by the owner with Claude using the `ui-ux` agent's playbooks.

### 5.3 Buffer settings

| Setting | Value |
|---|---|
| Media required before first frame | ~1 second |
| Target buffer ahead of playback | 15 seconds |
| Maximum buffer | 30 seconds |
| Back buffer | none |
| Adaptive bitrate | on, prefer highest rendition once throughput is proven |

The 1-second start threshold applies once a segment is arriving. Live HLS start still requires fetching the master playlist, a media playlist, and a segment at the live-edge offset, so real tune time is governed by the origin's speed as much as by this setting.

### 5.4 Fast tune

- **Prefetch on focus.** Highlighting a channel in the Guide, Favorites, or the player's channel strip fetches that channel's top-ranked stream playlist (about 1 KB) through the shared client, debounced by 300 ms. Same for the next and previous channels in the current list while watching.
- **Preload without decoding.** Media3's preload manager loads the playlist and first segments of the highlighted channel (in lists) or the adjacent channels (while watching) into the buffer without starting a decoder. On OK or channel up/down the preloaded source is handed to the player, saving the network round-trips. No second video decoder is ever opened. Preload is skipped when the candidate stream is on the same host as the currently playing stream, to respect single-connection hosts. Setting: preload count 0, 1 or 2, default 1, and the first release test on real hardware decides whether the default stays.
- **Rank by measured startup.** Stream order within a channel incorporates locally measured time-to-first-frame (5.6), so the fastest-starting source from this TV goes first.

Continuously buffering every favorite is rejected: it multiplies bandwidth, trips single-connection limits on many hosts, produces stale live segments, and exceeds the device's decoder count.

### 5.5 Stream selection and failover

**Sort key.** Within a channel, streams are ordered by this tuple, ascending:

1. `demoted` (failed on this TV within the last hour: 1, else 0)
2. `health != up` (unverified or down: 1, else 0)
3. `-(localScore ?: catalogScore)`

`localScore` exists only once a stream has 3 or more measured plays on this TV, and is computed from median time-to-first-frame, rebuffers per hour, and achieved bitrate relative to declared quality. Until then the catalog's `score` is used. Two demoted streams keep their relative order from steps 2 and 3.

**Failover rules.**

1. Try the first stream. First-frame cutoff is 4 seconds for streams that have alternatives remaining, 8 seconds for the last stream in the queue and for single-stream channels. A player error fails the stream immediately.
2. A stream that exceeds its cutoff is marked `slow`, not `failed`, and is not demoted. It is skipped for this tune only. A player error marks it `failed` and demotes it.
3. If a stream dies mid-play after having worked, try the next. Within the current tune, a stream that has already failed is retried only after every other stream has also failed, so a channel whose feeds all drop and recover (token expiry) still comes back.
4. If every stream fails, show "Channel unavailable" with the number tried, and offer Retry and Next Channel. Retry starts a fresh queue.
5. Demotion is a local record with an `elapsedRealtime` timestamp, immune to wall-clock changes. It expires after one hour. Demoted means tried last, never skipped.
6. Failures are not recorded while the system reports no validated network, or after 3 consecutive channels fail within a minute, which indicates a network or shared-host outage rather than bad streams.
7. The app never switches away from a stream that is playing acceptably, even if a higher-ranked one recovers. The queue is rebuilt fresh on the next tune. "Current tune" means from the moment a channel is selected until another channel is selected.
8. Switching is silent where possible: the old surface holds its last frame until the new stream produces one.
9. The player's Sources menu lists every stream for the channel with format, quality and health. Picking one manually plays it, clears its demotion, and suppresses automatic failover for that attempt so the app does not override the viewer's choice. If the manual pick fails, the normal queue resumes.

Channel up and down move through the currently visible list (All, a country, a category, Favorites, Recent), so surfing stays within whatever was being browsed.

### 5.6 Measurement and the poor-signal prompt

**Passive measurement.** While playing, the controller records achieved bitrate, rebuffer count, and time-to-first-frame per stream into `stream_stats`. These feed `localScore` so each TV learns which feeds work from its own network. No stream is ever probed in the background for measurement.

**Poor-signal prompt.** If the current stream rebuffers 3 times within a minute, or plays at a resolution well below its declared quality for more than 30 seconds, a small prompt offers "Try another source" with one button. Automatic switching on degradation is a settings toggle, off by default, reserved for a later version once measurement data shows the right thresholds.

## 6. Screens

Fixed here: which screens exist, how the remote moves between them, and what each must do. Look, layout and feel are the owner's, worked out per screen with the `ui-ux` agent.

Persistent left rail: Guide, Browse, Search, Favorites, Settings. Remote-only: D-pad, OK, Back, Play/Pause do everything. Nothing requires a pointer, a phone, or typing except Search.

1. **Player.** Full-screen video. OK or Down shows an overlay with channel name, logo, source format, quality and health. Up and Down surf channels. Left or Right opens a quick channel strip along the bottom. Menu opens Sources and Add to Favorites. Back returns to the originating screen.
2. **Guide.** Default landing screen. Vertical channel list with logo, name and country, filtered by the selected collection: Favorites, Recent, All, or a country or category. Selecting a channel plays it. The filter is remembered between launches. Long lists support jump-by-letter so the All list is navigable.
3. **Browse.** Two columns, countries left and categories right, each with a channel count. Picking one opens the Guide filtered to it.
4. **Search.** Platform on-screen keyboard, live results matching channel names and alternate names. Voice search where the remote has a microphone.
5. **Favorites.** The Guide filtered to favorites, one press from anywhere.
6. **Settings.** Add or remove M3U sources, catalog base URL, force catalog refresh, show catalog version and date, choose startup screen, set preload count, toggle auto-switch. Diagnostics page: current stream URL, format, resolution, bitrate, buffer level, measured tune time.

Rules for every screen: focus is always visible from ten feet, OK on any focused channel row plays it, and a favorite or recent channel is reachable from any screen in three presses.

## 7. Error handling

| Layer | Condition | Behavior |
|---|---|---|
| Catalog job | API fetch fails, or batch error | Abort, keep previous catalog, open GitHub issue |
| Catalog job | Up rate drops more than 25 points vs previous | Do not publish, do not update history, open GitHub issue |
| TV sync | No network on launch | Use cached catalog silently |
| TV sync | No network and no cached catalog | One screen: "Can't reach the catalog, check your connection", Retry |
| TV sync | Corrupt or truncated download | Discard, keep old catalog |
| Playback | All streams fail | "Channel unavailable (tried N)", Retry, Next Channel |
| Playback | Network not validated | Play attempts continue, failures not recorded |
| M3U source | URL doesn't parse | Show the first failing line |

## 8. Testing

**Catalog job.** Unit tests for grouping, format detection, ranking, history merge, and M3U parsing against fixture files, including streams without a channel id, streams with headers, master and media playlists, an ended playlist, a TS stream, and a DASH manifest. One integration test runs the full pipeline against a saved snapshot of the iptv-org API so it is repeatable offline.

**TV app.** Unit tests for the sort key, demotion expiry with a fake elapsed clock, the outage guard, sync version logic, and the streaming catalog parser against a 20 MB fixture. Instrumented test on the Android TV emulator: load a fixture catalog, tune a channel, kill the stream via a local fake HLS server, assert failover to the next stream. Remote-navigation tests asserting every screen is reachable and every favorite is at most three presses from playing.

**Manual, before each release, on the real stick.** Cold launch time, tune time on five known channels, surfing through twenty channels, one hour of continuous play, memory use during a full catalog sync.

## 9. Development environment

- Android Studio with the Android TV emulator for daily development.
- One physical Google TV device for remote, decoder and network testing. Owner currently has a cast-only Chromecast, which cannot run apps. An onn Google TV 4K stick (about $20) or equivalent is needed before release testing.
- Framework agents in `.claude/agents/`: `sdlc` governs the engineering lifecycle, `ui-ux` guides screen design.

## 10. Release plan

> TAILOR: owner to confirm the V1 / V1.1 split below, recommended by adversarial review to keep the first release to a few weeks of solo work.

**Version 1.** Catalog job with format detection, health test, history and guards. Catalog sync with streaming parse. Player, Guide, Search, Favorites, Settings. User M3U sources. Failover with the sort key and one-hour demotion, outage guard, single-stream and manual-pick rules. Platform plumbing: Leanback, cleartext, shared client, logos.

**Version 1.1.** Preload without decoding, prefetch on focus, passive measurement and `localScore`, poor-signal prompt, Browse screen, Diagnostics page, fake HLS server instrumented test.

**Version 2.** Automatic switching on degradation, program guide data where obtainable, program-first Home, Continue Watching, custom collections.

**Version 3.** Sports and event matching, multiview, profiles, Fire TV packaging, and a fuller backend if the static catalog proves limiting.

## 11. Decisions recorded

| Decision | Choice | Rejected alternatives |
|---|---|---|
| Content source | iptv-org, whole catalog, plus user M3U | Family antenna network, paid IPTV provider |
| Platform | Android TV / Google TV, sideloaded | Roku, Tizen, webOS, Play Store |
| Backend | Nightly GitHub Actions job to static files via Pages artifact | Fully on-device processing, self-hosted server, committing to a branch |
| History persistence | `history.json` on Pages, read at start of each run | Actions cache, external database |
| Rewind | None, stability buffer only | 60-second local timeshift |
| Multi-source monitoring | Passive measurement, switch only on bad | Live probing and switching to best |
| Favorites pre-buffering | Preload playlist and segments for 1 to 2 channels, no decoding | Continuous buffering of all favorites, second decoding player |
| Catalog URL | Setting with built-in default | Baked into the APK |
| Users | One standalone TV at a time | Shared profiles and sync across houses |
