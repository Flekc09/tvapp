# TV App — Design Spec

**Date:** 2026-09-22
**Status:** Approved by owner 2026-09-22, revised after engineering and viewer adversarial reviews
**Owner:** Corey Payne

## 1. Purpose

An Android TV / Google TV app that turns the full iptv-org catalog of free, publicly available live-TV streams into something that feels like clean, fast, uncluttered cable. Every channel in the catalog is in scope. The app installs on any Android TV device as a sideloaded APK and runs standalone: no accounts, no sync, no server the owner has to operate.

**The one task the app is built around:** get to a specific channel and have it playing well in under two seconds, using only a remote.

**The one feeling the app is built around:** turning on the TV shows a picture, not a menu.

### 1.1 Success criteria

| Measure | Target |
|---|---|
| Cold launch to first frame of the last channel, cached catalog | under 4 seconds |
| Tune time, median, on `up` streams over normal broadband | under 2 seconds to first frame |
| Tune time, 95th percentile, same conditions | under 5 seconds |
| Worst case before the viewer sees "not working" on a dead channel | 10 seconds, cancellable instantly |
| Channel change feedback | banner within one frame of the keypress |
| Channel failover | automatic, no viewer action, no endless spinner |
| From a focused channel row | one press (OK) plays it |
| From any screen | a favorite or recent channel plays in 3 presses or fewer |
| Previous channel | one gesture from the player |
| Owner infrastructure cost | $0 per month |

Tune-time targets apply to streams marked `up` in the catalog on a normal home broadband link. Slow origins exist and no client setting fixes them. Diagnostics shows the measured number.

### 1.2 Out of scope for version 1

Program guide (EPG) data, program-first Home, sports hub, team favorites, profiles, Simple Mode, multiview, rewind or timeshift, DVR, cross-device sync, voice beyond platform search, Google TV launcher rows, Play Store distribution, Roku, Samsung Tizen, LG webOS, Apple TV.

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
| Streams whose feed is a city- or state-level affiliate | 1,329 |

Consequences:

- Many channels have 5 to 30 duplicate feeds. Grouping and ranking them is the core backend job.
- iptv-org models local affiliates as feeds of one network channel. "ABC" has 33 streams, most of them different cities' stations. The viewer needs those as separate channels, and failover must stay within one station's feeds.
- The US network and sports feeds the owner cares most about are community-submitted restreams on bare IPs. They are the least stable part of the catalog and can disappear from iptv-org at any time. Failover and user-added sources are therefore core features, not extras.
- Most of the catalog is plain HTTP, and raw-IP hosts cannot have valid TLS certificates. The app must allow cleartext traffic.
- Nearly a thousand streams will not play without per-stream headers. The player must honor them from day one.
- Not every URL is HLS. The catalog also contains raw MPEG-TS over HTTP, DASH manifests, and dead hosts returning HTML. Format must be detected, not assumed.
- The catalog carries adult channels (an `xxx` category and an `is_nsfw` flag). They must be hidden by default.
- Ten thousand channels cannot be processed on a $20 stick at every refresh. Cleanup runs off-device.

## 3. Architecture

Two components in one repository. Nothing runs on a server the owner owns.

```
iptv-org API ──▶ catalog job (GitHub Actions, nightly) ──▶ catalog.json.gz + history.json on GitHub Pages
                       ▲                                              │
                       └──── reads previous history.json ─────────────┘
                                                                      │
                              TV app (Kotlin, Compose for TV, ExoPlayer) ◀── downloads when idle
                                     │
                              plays streams directly from their hosts
```

### 3.1 Catalog job (`catalog/`)

TypeScript, run by a scheduled GitHub Actions workflow once a day. Publishes to GitHub Pages using the Pages artifact deploy action, never by committing files to a branch, so the repository does not grow. Output is one gzipped catalog, one history file, and one tiny version file at a fixed URL.

Expected runtime is 30 to 45 minutes at 50 total concurrency with a per-host cap of 2, well inside the 6-hour job limit.

Two GitHub platform rules the job must respect:

- Scheduled workflows in a public repository are disabled after 60 days without repository activity. The workflow includes a keepalive step that re-enables the schedule through the API.
- GitHub Pages has a soft bandwidth limit of 100 GB per month. At 3 MB per download that is roughly 1,000 TVs downloading a new catalog every day, far beyond this project's use. The spec makes no claim beyond tens of TVs.

### 3.2 TV app (`app/`)

Kotlin, Jetpack Compose for TV, Media3 ExoPlayer, Room database. Downloads the catalog when the version changes and the device is idle, stores it locally, renders everything from the local copy. Favorites, recents, settings and user sources live only on the device.

### 3.3 Deployment model

Installing on a new TV: sideload the APK, open it, it fetches the catalog on first launch with a progress screen, and from then on turns on to a picture. Every TV reads the same static file. If the nightly job ever stops, TVs keep using their last catalog and the app's own failover skips dead streams at play time.

The catalog base URL is an Advanced setting with the GitHub Pages address as the built-in default. Moving to a different host later, or recovering if the Pages site is taken down, means changing that setting on each TV, not rebuilding the app.

## 4. Catalog job

### 4.1 Steps

1. **Fetch previous state.** Download `history.json` from the Pages URL. It holds, per stream URL, the last 7 daily test results. On the first ever run it is absent and history starts empty.
2. **Fetch source data.** Download channels, feeds, streams, categories, countries, subdivisions, cities and logos from the iptv-org API. Roughly 30 MB raw.
3. **Group.** See 4.2.
4. **Detect format and test.** See 4.3.
5. **Rank.** Within each catalog channel, compute a catalog score from 7-day uptime, then declared quality, then median response time, with a small penalty for raw IP hosts. Order streams by that score.
6. **Guard.** See 4.5.
7. **Write.** `catalog.json.gz`, updated `history.json`, and `latest.json` holding only version and byte size so the TV can check for updates with a request under 1 KB. `version` is the run's Unix epoch seconds, so a same-day re-run always produces a new version.

### 4.2 Grouping rules

The unit the viewer tunes, and the unit failover operates within, is a **catalog channel**. It is built from iptv-org's channel plus feed:

- A stream whose feed has a country-level or wider broadcast area (`c/US`, `r/EUR`), or no feed, belongs to the catalog channel with the iptv-org channel id, for example `ABC.us`.
- A stream whose feed has a city- or state-level broadcast area (`ct/USCLT`, `s/US-NC`) belongs to a **split** catalog channel with id `<channel>@<feed>`, for example `ABC.us@WSOCTV`, named `<channel name> · <feed name>` with a `region` field holding the city or state name from the cities or subdivisions file, for example "Charlotte". It inherits the parent's country, categories, network and logo.
- Streams with no channel record become synthetic channels named from their playlist title, with country guessed from the URL's ccTLD where possible and category `other`. Nothing is dropped.
- Closed channels (`closed` set) are excluded with their streams.
- Channels with `is_nsfw` or the `xxx` category are kept and flagged `adult: true`. The app hides them by default.
- Channels with no categories get `other`. The category id `other` is displayed as "Other".

### 4.3 Format detection and health test

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

### 4.4 Catalog file format

Four lists. Estimated 2 to 3 MB gzipped, 15 to 20 MB uncompressed, about 12 MB in the TV database.

```
{
  "version": 1758520800,
  "generatedAt": "2026-09-22T06:00:00Z",
  "countries":  [{ "code": "US", "name": "United States", "flag": "🇺🇸" }],
  "categories": [{ "id": "sports", "name": "Sports" }],
  "channels":   [{ "id": "ABC.us@WSOCTV", "name": "ABC · WSOC-TV", "altNames": [],
                   "country": "US", "region": "Charlotte", "categories": ["general"],
                   "network": "ABC", "logo": "https://...", "adult": false,
                   "hasUp": true }],
  "streams":    [{ "channel": "ABC.us@WSOCTV", "url": "https://...", "format": "hls",
                   "quality": "720p", "referrer": null, "userAgent": null,
                   "health": "up" | "down" | "unverified",
                   "uptime7d": 0.86, "responseMs": 412, "score": 0.81, "checkedAt": "..." }]
}
```

`hasUp` is true when at least one of the channel's streams is `up` or `unverified`. The app uses it as the default list filter without joining the streams table.

Logos are validated by the job with a HEAD request. Dead logo URLs are replaced with null so the app never requests them.

### 4.5 Safety rules

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
- The player holds the screen on (`FLAG_KEEP_SCREEN_ON`) while video is playing and releases it when an overlay is open with no video, or on "not working". On resume from standby the current channel is re-tuned to the live edge. On Home or standby the player is released promptly so bandwidth stops.
- Remote keys handled: D-pad, OK (short and long), Back, Play/Pause, and the CEC pass-through keys a TV's own remote sends: `CHANNEL_UP`, `CHANNEL_DOWN`, `LAST_CHANNEL`, `GUIDE`, `INFO`, `MEDIA_PLAY_PAUSE`, and digits 0 to 9. The app never requires a Menu button, which the onn stick and Chromecast remotes do not have.

### 5.2 Layers

Each layer is independently testable and holds no logic belonging to another.

**Data layer.** Room database. Catalog tables (`countries`, `categories`, `channels`, `streams`) carry a `source` column and an `import_id`. User-added M3U content lives in the same tables with `source = 'user:<id>'`. Local-only tables: `favorites` (with a `position` column), `recents`, `user_sources`, `stream_stats`, `stream_failures`, `channel_status`, `settings`.

**Sync.** On launch and every 24 hours the app fetches `latest.json` (under 1 KB) and records whether a newer version exists. The download and import run only when idle: no video playing for 10 minutes, or the device otherwise unused, scheduled through WorkManager. Import stream-parses the gzipped JSON, never holding the whole document in memory, inserting in batches of 1,000 under a new `import_id`. When the import completes, one small transaction flips the active `import_id` and deletes the old iptv rows, so lists never flicker or go empty. User rows are untouched. Favorites whose channel id no longer exists after a sync are kept and shown as "no longer available" until the user removes them. The only exception to "idle only" is first launch, which imports immediately behind a progress screen.

**Source layer.** One interface, two implementations: the iptv-org catalog and user-added M3U URLs. The M3U parser handles standard `#EXTINF` attributes including logo and group title, plus `#EXTVLCOPT` header lines for referrer and user agent. Both feed the same tables tagged by source, so favorites, search and lists behave identically regardless of origin.

**Playback layer.** One player controller wrapping Media3 ExoPlayer, taking a channel id and owning stream selection, failover, preload and measurement. Per-stream referrer and user agent go into the data source factory. `BehindLiveWindowException` is handled by re-seeking to the live edge, not as a stream failure.

**UI layer.** Compose for TV, built as a single player surface with overlays. No business logic. Channel logos load through Coil with a disk cache, a hard size cap, and a placeholder on error. Built screen by screen by the owner with Claude using the `ui-ux` agent's playbooks.

### 5.3 Buffer settings

| Setting | Value |
|---|---|
| Media required before first frame | ~1 second |
| Target buffer ahead of playback | 15 seconds |
| Maximum buffer | 30 seconds |
| Back buffer | none |
| Adaptive bitrate | on, prefer highest rendition once throughput is proven |

The 1-second start threshold applies once a segment is arriving. Live HLS start still requires fetching the master playlist, a media playlist, and a segment at the live-edge offset, so real tune time is governed by the origin's speed as much as by this setting.

**Pause** holds the current picture and mutes. Play rejoins at the live edge with a one-line "Back to live" note. There is no rewind.

### 5.4 Fast tune

- **Prefetch on focus.** Highlighting a channel in any list fetches that channel's top-ranked stream playlist (about 1 KB) through the shared client, debounced by 300 ms. Same for the next and previous channels in the current list while watching.
- **Preload without decoding.** Media3's preload manager loads the playlist and first segments of the highlighted channel (in lists) or the adjacent channels (while watching) into the buffer without starting a decoder. On OK or channel up/down the preloaded source is handed to the player, saving the network round-trips. No second video decoder is ever opened. Preload is skipped when the candidate stream is on the same host as the currently playing stream, to respect single-connection hosts. Setting: preload count 0, 1 or 2, default 1, and the first release test on real hardware decides whether the default stays.
- **Live preview in lists.** While the Channels overlay is open, the currently playing channel keeps playing in an inset. After the highlight rests on a different channel for 700 ms, the inset switches to that channel, reusing the preloaded source. The decoder is only ever used by one stream at a time. Moving the highlight again cancels the switch. This is the cable-box guide viewers already know, and without program data the picture is the guide.
- **Rank by measured startup.** Stream order within a channel incorporates locally measured time-to-first-frame (5.6), so the fastest-starting source from this TV goes first.

Continuously buffering every favorite is rejected: it multiplies bandwidth, trips single-connection limits on many hosts, produces stale live segments, and exceeds the device's decoder count.

### 5.5 Stream selection and failover

**Sort key.** Within a channel, streams are ordered by this tuple, ascending:

1. `demoted` (failed on this TV within the last hour: 1, else 0)
2. `health != up` (unverified or down: 1, else 0)
3. `-(localScore ?: catalogScore)`

`localScore` exists only once a stream has 3 or more measured plays on this TV, and is computed from median time-to-first-frame, rebuffers per hour, and achieved bitrate relative to declared quality. Until then the catalog's `score` is used. Two demoted streams keep their relative order from steps 2 and 3.

**Tune budget.** A tune has a total budget of 10 seconds across all its streams. Any channel change or Back cancels the tune instantly. A tune the viewer abandons records nothing.

**Failover rules.**

1. Try the first stream. First-frame cutoff is 4 seconds for streams that have alternatives remaining, and the remaining budget for the last stream in the queue and for single-stream channels. A player error fails the stream immediately.
2. A stream that exceeds its cutoff is marked `slow`, not `failed`, and is not demoted. It is skipped for this tune only. A player error marks it `failed` and demotes it.
3. If a stream dies mid-play after having worked, try the next, showing a one-line "Switching source" toast, and rejoin at the live edge. Within the current tune, a stream that has already failed is retried only after every other stream has also failed, so a channel whose feeds all drop and recover (token expiry) still comes back.
4. While trying alternatives the banner shows "Trying source 2 of 5". If the budget runs out, show "This channel isn't working right now" with "Try again" and "Next channel". Try again starts a fresh queue.
5. Demotion is a local record with an `elapsedRealtime` timestamp, immune to wall-clock changes. It expires after one hour. Demoted means tried last, never skipped.
6. Failures are not recorded while the system reports no validated network, or after 3 consecutive channels fail within a minute, which indicates a network or shared-host outage rather than bad streams.
7. **Doesn't work here.** A channel with no successful play in at least 3 attempts spread across 3 separate days gets `channel_status = broken_here`. It is hidden from lists and surf order, stays in Favorites with a plain "Not working" badge, and comes back through the setting "Show channels that don't work here". One successful play clears the status.
8. The app never switches away from a stream that is playing acceptably, even if a higher-ranked one recovers. The queue is rebuilt fresh on the next tune. "Current tune" means from the moment a channel is selected until another channel is selected.
9. Switching is silent where possible: the old surface holds its last frame until the new stream produces one.
10. The Sources menu lists every stream for the channel as "Source 1 · 720p · Working", "Not checked", or "Not working". Picking one manually plays it, clears its demotion, and suppresses automatic failover for that attempt so the app does not override the viewer's choice. If the manual pick fails, the normal queue resumes.

**Surf order.** Channel up and down move through the currently visible list (a country, a category, Favorites, Recent, or All), skipping hidden channels, so surfing stays within whatever was being browsed.

### 5.6 Measurement and the poor-signal prompt

**Passive measurement.** While playing, the controller records achieved bitrate, rebuffer count, and time-to-first-frame per stream into `stream_stats`. These feed `localScore` so each TV learns which feeds work from its own network. No stream is ever probed in the background for measurement.

**Poor-signal prompt.** If the current stream rebuffers 3 times within a minute, or plays at a resolution well below its declared quality for more than 30 seconds, a small prompt offers "Try another source". It does not take focus: Up and Down still change channel, and only pressing OK while it shows accepts it. It auto-dismisses after 8 seconds and does not repeat within 10 minutes on the same channel. Automatic switching on degradation is an Advanced setting, off by default, reserved for a later version once measurement data shows the right thresholds.

## 6. Screens

Fixed here: which surfaces exist, how the remote moves between them, and what each must do. Look, layout and feel are the owner's, worked out per screen with the `ui-ux` agent.

**Model.** The player is the root and is always underneath. Everything else is an overlay drawn over the still-playing, dimmed or inset video, with audio continuing. Back closes the topmost overlay. Back on the bare player shows "Press Back again to exit" and exits on a second press within 2 seconds. The app relaunches to the last channel after an exit, a crash, or a CEC power-off.

**Startup.** Default is the last channel, full screen, immediately. Settings offers Last channel, a chosen favorite, or Channels overlay. On a fresh install, the first launch shows a progress screen ("Loading channels… 4,200 of 10,000. This only happens once.") and then opens the Channels overlay filtered to the device locale's country, working channels first.

**Default filters.** Every list hides channels that are adult, have no working stream (`hasUp` false), or are `broken_here`, unless the corresponding setting is on. "Other" and country-less synthetic channels appear only under Browse → Other and in Search, never in the default country list.

**Remote mapping in the player.**

| Key | Action |
|---|---|
| Up / Down, `CHANNEL_UP` / `CHANNEL_DOWN` | Next / previous channel in the current list. Banner moves instantly; the tune starts 300 ms after the last press. |
| OK, `INFO` | Show the banner. |
| Long-press OK | Context menu: Sources, Add or remove favorite, Previous channel. |
| Double-press OK, `LAST_CHANNEL` | Previous channel. |
| Left / Right | Channel strip along the bottom. |
| `GUIDE`, or Back from the banner | Channels overlay. |
| Play/Pause, `MEDIA_PLAY_PAUSE` | Pause (hold picture, mute) / rejoin live. |
| Digits 0 to 9 | Favorite by position number. |

**Banner.** Appears within one frame of any channel change and on OK. Shows logo, name, region, country flag, clock, favorite star, list name and position ("Favorites 3 / 12"), measured resolution, and a "Previous: SEC Network" hint. Auto-hides 3 seconds after video is up.

**Surfaces.**

1. **Player.** Full-screen video, the banner, the context menu, the Sources list, the channel strip, the "Trying source" and "Switching source" lines, the poor-signal prompt, and the "isn't working" card.
2. **Channels.** The main overlay. A vertical channel list with logo, name, region and status, filtered by the selected collection: Favorites, Recent, a country, a country's category, or All. The current channel plays in an inset; the highlighted channel takes over the inset after 700 ms. OK plays full screen. Long-press OK toggles favorite. Jump-by-letter for long lists. The filter is remembered between launches.
3. **Browse.** Country first, with the device's country pinned at the top, then that country's categories with counts. A "Country: All" chip on a category list shows that category worldwide. Picking one opens Channels filtered to it. "Other" is a category here.
4. **Search.** Platform on-screen keyboard, live results matching channel name, alternate names, network, region and category. Each result shows flag, region, category and status. Voice search where the remote has a microphone.
5. **Favorites.** Channels filtered to favorites, one press from anywhere. Move up and down to reorder. Position becomes the channel number shown in the strip and honored by digit keys.
6. **Settings.** Two tiers.
   - *Settings:* startup behavior, sleep timer (30 / 60 / 90 minutes), show channels that don't work here, show adult channels (behind a 4-digit PIN set on first use), preload count.
   - *Advanced* (long-press to enter): M3U sources, catalog base URL, force catalog refresh, catalog version and date, auto-switch on poor signal, and Diagnostics: current stream URL, format, resolution, bitrate, buffer level, measured tune time.

**Language rule.** Nothing on screen says HLS, TS, DASH, unverified, unsorted, demoted, or a stream count. Status words are Working, Not checked, Not working.

Rules for every surface: focus is always visible from ten feet, OK on any focused channel row plays it, and a favorite or recent channel is reachable from any surface in three presses.

## 7. Error handling

| Layer | Condition | Behavior |
|---|---|---|
| Catalog job | API fetch fails, or batch error | Abort, keep previous catalog, open GitHub issue |
| Catalog job | Up rate drops more than 25 points vs previous | Do not publish, do not update history, open GitHub issue |
| TV sync | No network on launch | Use cached catalog silently |
| TV sync | No network and no cached catalog | One screen: "Can't reach the channel list. Check your connection." with Retry |
| TV sync | Corrupt or truncated download | Discard, keep old catalog, retry next idle window |
| TV sync | Import interrupted (standby, crash) | Partial `import_id` rows are deleted on next launch; old catalog stays active |
| Playback | Tune budget exhausted | "This channel isn't working right now", Try again, Next channel |
| Playback | Network not validated | Play attempts continue, failures not recorded |
| Playback | Standby resume | Re-tune current channel to live edge |
| M3U source | URL doesn't parse | Show the first failing line |

## 8. Testing

**Catalog job.** Unit tests for grouping including affiliate splitting and the adult flag, format detection, ranking, history merge, and M3U parsing against fixture files, including streams without a channel id, streams with headers, master and media playlists, an ended playlist, a TS stream, and a DASH manifest. One integration test runs the full pipeline against a saved snapshot of the iptv-org API so it is repeatable offline.

**TV app.** Unit tests for the sort key, demotion expiry with a fake elapsed clock, the outage guard, the tune budget and cancellation, the broken-here rule across fake days, sync version logic, the import-id flip, and the streaming catalog parser against a 20 MB fixture. Instrumented test on the Android TV emulator: load a fixture catalog, tune a channel, kill the stream via a local fake HLS server, assert failover to the next stream and the toast. Remote-navigation tests asserting every overlay is reachable, Back never exits on first press from an overlay, every favorite is at most three presses from playing, and adult channels never appear with the setting off.

**Manual, before each release, on the real stick.** Cold launch to picture, tune time on five known channels, surfing through twenty channels with the banner keeping up, previous-channel toggle, six hours of continuous play with the screensaver never appearing, standby and resume, and memory use during a full catalog import.

## 9. Development environment

- Android Studio with the Android TV emulator for daily development.
- One physical Google TV device for remote, decoder and network testing. Owner currently has a cast-only Chromecast, which cannot run apps. An onn Google TV 4K stick (about $20) or equivalent is needed before release testing.
- Framework agents in `.claude/agents/`: `sdlc` governs the engineering lifecycle, `ui-ux` guides screen design.

## 10. Release plan

**Version 1.** Everything in this spec. Owner decision 2026-09-22: ship the full feature set as one release. The implementation plan sequences the work so the core path (catalog job, sync, Player, Channels, failover) is working end to end first, and preload, live preview, measurement, the poor-signal prompt, Browse and Diagnostics are layered on afterward, but nothing ships until all of it is done.

**Version 2.** Google TV launcher rows (last channel and favorites on the home screen), automatic switching on degradation, program guide data where obtainable, program-first Home, Continue Watching, custom collections.

**Version 3.** Sports and event matching, multiview, profiles, Fire TV packaging, and a fuller backend if the static catalog proves limiting.

## 11. Decisions recorded

| Decision | Choice | Rejected alternatives |
|---|---|---|
| Content source | iptv-org, whole catalog, plus user M3U | Family antenna network, paid IPTV provider |
| Platform | Android TV / Google TV, sideloaded | Roku, Tizen, webOS, Play Store |
| Backend | Nightly GitHub Actions job to static files via Pages artifact | Fully on-device processing, self-hosted server, committing to a branch |
| History persistence | `history.json` on Pages, read at start of each run | Actions cache, external database |
| Tune unit | Channel plus regional feed, split in the catalog job | iptv-org channel as-is (mixes 30 cities' affiliates) |
| Startup | Last channel, full screen | Landing on a list |
| Navigation model | Player root with overlays, audio continues | Separate screens that tear down the player |
| Rewind | None; pause holds and rejoins live | 60-second local timeshift |
| Multi-source monitoring | Passive measurement, switch only on bad | Live probing and switching to best |
| Favorites pre-buffering | Preload playlist and segments for 1 to 2 channels, no decoding | Continuous buffering of all favorites, second decoding player |
| List preview | Live inset of the highlighted channel after 700 ms dwell | Static list with no what's-on |
| Dead channels | Hidden by default, "doesn't work here" learned over 3 days | Shown and tried every time |
| Adult content | Flagged in catalog, hidden behind PIN | Dropped from catalog, or shown |
| Catalog URL | Advanced setting with built-in default | Baked into the APK |
| Launcher rows | Version 2 | Version 1 |
| Users | One standalone TV at a time | Shared profiles and sync across houses |
