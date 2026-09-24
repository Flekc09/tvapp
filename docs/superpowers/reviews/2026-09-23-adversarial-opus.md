# Adversarial review — TV App docs — 2026-09-23 — reviewer: Opus 5.5

## Verdict (3 sentences max)

The catalog job is in good shape: I extracted it from the plan, ran it against the live iptv-org API, and it passes 74 of 74 tests with a clean typecheck. The app plan cannot ship as written, for three reasons. The catalog it will download fails the app's own parser on every real import, the pinned toolchain cannot build Compose BOM 2026.09 or Media3 1.11, and several listed files do not compile. Behind those sit about twenty second-order holes, mostly in `PlayerController`/`AppViewModel` state handling (stale import ids, `cancel()` not touching the player, Back swallowed at the activity) and in the Browse/Search/Favorites filters, and the existing tests catch none of them.

Method: I read every line of items 1–5. I extracted the catalog plan's code and ran it (Node 25, live fixture). I pulled the live API files (17,498 streams) to test the grouping assumptions, probed a random 400 streams plus 40 on jmp2.uk with the plan's own `probeStream`, and read the AAR metadata of the pinned AndroidX/Media3 artifacts on Google Maven. I also read Media3 1.11.0's `DefaultLoadErrorHandlingPolicy` source. `python3 docs/ui/tools/contrast.py` prints no line whose verdict is FAIL, but see minor m1.

---

## Blockers

> Status 2026-09-23: B1, B2 and B3 applied to both plans (annotated "Opus adversarial review 2026-09-23"). B1 was re-verified by running the patched catalog code (typecheck clean, 74/74). B2 and B3 are metadata- and text-verified only: no JDK or Android SDK on this machine, so the Kotlin side is confirmed at Task 1 step 7 and Task 4 step 4 when the plan is executed.
>
> Status 2026-09-24: M12, M13 (catalog half; the `countryCounts` half is in the app plan and still open) and M14 applied to the catalog plan and spec with the same annotation. Re-verified by extracting the edited plan and running it: typecheck clean, 76/76, workflow YAML parses. The ccTLD change was measured on the 2026-09-23 API snapshot: guessed countries went from UK 777 / TV 390 / IO 28 to none of those, with CN 47, MN 11, RU 5 unchanged. Everything else in this review is still open.

### B1. Every real catalog import fails: the job writes `responseMs` as a float and the app reads it with `nextInt()`
- Where:
  - `docs/superpowers/plans/2026-09-22-catalog-job.md:1028` — `const now = opts.now ?? (() => performance.now());`
  - `:993` — `const ms = now() - t0;`, carried into the catalog unrounded at `:1607`.
  - `docs/superpowers/plans/2026-09-22-tv-app.md:882` — `"responseMs" -> responseMs = r.nextIntOrNull()`.
  - The spec's format (`specs/...:159`) shows an int, `"responseMs": 412`.
- What breaks: I ran the plan's `probeStream` + `buildCatalog` against a live URL and the catalog line came out as `"responseMs":694.155667`. Gson's `JsonReader.nextInt()` throws `NumberFormatException("Expected an int but was 694.155667")` whenever the value is not an exact int. `CatalogParser` wraps that as `CatalogFormatException`, the importer deletes the partial import, and first launch shows "Can't reach the channel list" on every try. The idle sync fails the same way, forever.
- Why the reviews missed it: every test on both sides uses hand-written integer millis. That covers the catalog `build.test` (300, 200), the app `CatalogParserTest` (412) and the importer docs (`"responseMs":1`). Nothing ever parses a catalog produced by the job with the app's parser.
- Minimal fix: `responseMs: Math.round(...)` in `probe.ts` `get()`, which covers the history `ms` too. Add a cross-component contract test: the catalog's `pipeline.test` output is saved as a fixture that `CatalogParserTest` parses. Optionally make the parser tolerant with `nextDouble().roundToInt()`.

### B2. The pinned toolchain cannot build the pinned libraries, and the plan forbids the fix
- Where: `plans/2026-09-22-tv-app.md:110` `agp = "8.7.3"`, `:113` `composeBom = "2026.09.00"`, `:117` `media3 = "1.11.0"`, `:179` `compileSdk = 35`. `:24` says "does not raise majors mid-plan".
- What breaks: I read the AAR metadata from Google Maven.
  - Compose BOM 2026.09.00 resolves `androidx.compose.ui:ui-android:1.12.1`, whose metadata says `minCompileSdk=37`, `minAndroidGradlePluginVersion=9.1.0`.
  - `media3-exoplayer:1.11.0` says `minCompileSdk=36`.
  - `checkDebugAarMetadata` therefore fails in Task 1 Step 7, before any test runs.
  - The only fix is AGP 9.x plus compileSdk 37. That is a major raise the executor is told not to make, and it probably pulls in a newer Kotlin/KSP too: media3-ui 1.11.0 already depends on kotlin-stdlib 2.2.10 against the pinned Kotlin 2.1.0. It also likely invalidates the `kotlinOptions {}` / `kotlin-android` plugin block at `:161,192`.
- Why the reviews missed it: CLAUDE.md says the library *calls* were checked against sources. Nobody checked the build-compatibility metadata of the versions against AGP and compileSdk.
- Minimal fix: re-pin as a set before Task 1. One option is AGP 9.1+, compileSdk 37 and a matching Kotlin/KSP. The other is an older Compose BOM whose `ui` needs compileSdk ≤ 35 plus Media3 ≤ the last version needing 35. Record whichever set actually builds. Change `:24` to "the executor may move AGP/compileSdk/Kotlin together if AAR metadata demands it".

### B3. Several files in the app plan do not compile as written
- Where:
  - `plans/2026-09-22-tv-app.md:558,561` — `@Insert suspend fun insertStat(...)` and `@Insert suspend fun insertFailure(...)` sit inside `abstract class LocalDao` with neither a body nor `abstract`. Kotlin rejects this: "Function without a body must be abstract".
  - `:2258` declares `download(onProgress: (Long, Long, Int) -> Unit)`, but `:2175`, `:2184` and `:2300` call it with two-parameter lambdas (`{ _, _ -> }`, `{ read, _ -> ... }`).
  - `:2270` calls `sink.importStream(counting) { n -> ... }`, but `ImportSink.importStream` takes one parameter (`:2220`). `ImporterSink` (`:2277`) calls `importer.import(input)` without `onChannels`, so even once the call compiles, the "4,200 so far" count stays 0.
  - `:2678` reads `player.state.value.tuning`, but `PlayerState` (`:1918-1921`) has no `tuning` field. It is referenced only at `:2468` and `:2678`.
  - `:3232` assigns `showAddressDialog = true`, which is declared nowhere in `MainActivity`.
- What breaks: Tasks 2, 10, 12 and 16 each stop at their "Expected: BUILD SUCCESSFUL / N pass" step.
- Why the reviews missed it: the app plan was never executed. The catalog plan was, and it passes.
- Minimal fix:
  - Mark the two DAO methods `abstract`.
  - Make the three callers pass `{ _, _, _ -> }`.
  - Add `onChannels: (Int) -> Unit` to `ImportSink.importStream` and thread it to `importer.import`.
  - Add `val tuning: Boolean get() = attempting != null && playing == null` to `PlayerState`.
  - Add `var showAddressDialog by mutableStateOf(false)` to `MainActivity` and render the dialog.

---

## Majors

### M1. After any catalog flip, lists go empty and every favorite shows "no longer available"
- Where:
  - `plans/...tv-app.md:2653` — `flow { emit(c.importer.activeImportIds()) }.flatMapLatest { ids -> ... }` reads the active id once per `Params` change.
  - `:1052` — the flip deletes the previous import's rows.
  - `:2935` — "Refresh channel list now … allowed even while playing". The spec requires "lists never flicker or go empty" (`spec:191`).
- What breaks: Room invalidates the `channels` table, and `channelList` re-runs with the *old* import id, which now matches no rows. The Channels list and the strip go empty and surf stops working. The Favorites path joins against the old id, so at the next favorites change every favorite becomes a placeholder. This lasts until the filter or a setting changes, or the process restarts. It is triggered both by the viewer's manual refresh and by the idle sync.
- Missed because: `CatalogImporterTest` checks the DB, not the view model. No test runs an import while the VM is alive.
- Fix: expose `activeImportIds` as a Flow on `settingFlow("active_import_id")` and `combine` it into `Params`.

### M2. The idle catalog sync never runs while the process lives
- Where: `cancel()` at `:1997` only clears `attempting`/`notice`. `onStop` at `:3258` calls `cancel()` and sets `playWhenReady = false`. The worker gate at `:2296` is `if (c.playerController.state.value.playing != null) return Result.retry()`. The VM idle trigger at `:2722` keys on `playing == null`.
- What breaks: once anything has played, `playing` stays non-null through Home and standby. The worker retries forever with backoff, the VM never enqueues `runWhenIdle`, and TVs only pick up a new catalog after process death.
- Missed because: `CatalogSyncTest` fakes the gate away. No test covers onStop followed by the worker.
- Fix: set `playing = null` (and write `last_playing_wall`) in `cancel()`/`onStop`, or gate the worker on `player.isPlaying` plus the lifecycle state.

### M3. The sleep timer neither stops playback nor lets the screen sleep
- Where: the interface says the timer "pauses and releases the player" (`:2475`), and flows C10 (`screens-and-flows.md:76`) and `07-settings.md:23` agree. The code is `delay(m * 60_000L); player.cancel(); push(Overlay.CHANNELS)` (`:2731`), and `cancel()` never touches ExoPlayer (`:2012`). Screen-on follows `playing != null` (`:3049`).
- What breaks: at 90 minutes the Channels overlay opens over a still-playing, still-audible stream. `FLAG_KEEP_SCREEN_ON` stays set, so the TV never blanks. Even if implemented per the copy ("pause… the app stays open"), `pause()` keeps `playing` set, so the screen still never sleeps.
- Fix: stop the player, set `playing = null`, clear the screen-on flag, and suppress the Channels live preview after the timer fires.

### M4. "Back cancels the tune" is not implemented, and `cancel()` leaves the abandoned stream loading
- Where:
  - D11 (`screens-and-flows.md:130`) and `plans:2468` say the activity passes `bannerVisible && !tuning`. `MainActivity` passes the raw value (`:3263`, `:3272`), and the banner is always visible during a tune because `armBannerHide` returns while tuning (`:2678`). So Back during a tune → `OpenChannels`, never `vm.back()`/`cancel()`.
  - Separately, `cancel()`/`abandon()` (`:1997`, `:2012`) cancel the engine but never call `player.stop()`/`clearMediaItems()`.
- What breaks: the spec's instant-cancel (`spec:232`, §1.1 "cancellable instantly") cannot be reached from the player. Where `cancel()` does run (onStop, the sleep timer), ExoPlayer keeps preparing the abandoned source. It may later render and play with audio, with no engine, no failover and no state. That contradicts `01-player.md:68`, "Back cancels the tune and keeps the last picture".
- Missed because: `NavigationTest.backFromTheBannerOpensChannels` asserts the wrong-in-a-tune path, and `cancelDuringTuneRecordsNothing` calls the controller directly.
- Fix: pass `bannerVisible && !tuning` from `MainActivity`. In `cancel()`, call `player.stop()` (keeping the surface frame) or re-prepare the last playing candidate.

### M5. Holding Down does not surf; the red route and Review Focus 3 assume it does
- Where: `MainActivity.dispatchKeyEvent` routes non-BACK/CENTER keys only on `ACTION_UP`: `if (event.action != KeyEvent.ACTION_UP) return super.dispatchKeyEvent(event)` (`:3289`). The red route says "viewer holds Down through ten channels → the banner shows every intermediate channel" (`screens-and-flows.md:91`), echoed at `plans:35` and in the release checklist at `:3695`.
- What breaks: auto-repeat produces DOWN events only, so a held key moves one channel, on release. The banner also moves at key-up, not "within one frame of the keypress".
- Missed because: the tests send `sendKeyDownUpSync`.
- Fix: surf on `ACTION_DOWN`, including repeats, for Up/Down/CHANNEL keys, and ignore their UP.

### M6. The activity swallows every Back, so all in-overlay Back behaviour collapses to "pop the whole overlay"
- Where: `:3265-3275` returns `true` for every KEYCODE_BACK without calling `super`, so Compose `BackHandler`/`OnBackPressedDispatcher` never fire. The specs that need a nested Back:
  - `05-favorites.md:31`: "Back closes the menu and returns focus to the row".
  - `07-settings.md:14`: pickers, "Back returns without changing".
  - `03-browse.md:32`: "Back from categories: [returns to the country]".
  - `02-channels.md:30`: "Back from the column … restores the committed filter".
  - `08-dialogs-and-strip.md:9`: dialogs, "Back cancels".
  - None of these has an `Overlay` enum value (`:2550`).
- What breaks: Back in the Favorites row menu closes Channels, and Back in a Settings picker closes Settings. If the PIN or address dialogs are Compose `Dialog` windows, the activity never sees their keys, so hold-Back (D10, "every surface … dialogs", `screens-and-flows.md:27`) does not work there. With the Search keyboard up, the IME consumes Back first, so the claim "Search-with-keyboard included: hold Back → Favorites" (`:102`) is false.
- Fix: for short Back, call `super.dispatchKeyEvent` first and fall back to `vm.back()` only if nothing consumed it (an `OnBackPressedCallback` at the root for the overlay pop). Model dialogs as `Overlay` values, not windows.

### M7. Live preview pops the modal "isn't working" card over Channels, and rewrites Recent and Previous
- Where: the card sync pushes `NOT_WORKING` whenever `notice` becomes `NotWorking` and `top != NOT_WORKING` (`:2471`). The preview calls `vm.player.tune(id)` (`:2770`). `tune()` sets `previousChannelId` (`:1977`) and `upsertRecent` (`:1993`) on every call. `02-channels.md:46` says a failed preview only sets the details status to "Not working".
- What breaks: resting on a dead row for 10 s throws a focus-stealing card over the guide. Every row rested on for 700 ms joins Recent. "Previous:" and double-OK then point at the last *previewed* row, not the channel watched before opening the guide.
- Fix: add a `preview: Boolean` to `tune()` that skips the recents write, the previous-channel update and the card push. Commit recents and previous only on `select`/OK.

### M8. Adult channels leak through Favorites, Recent, the strip, digit keys and startup when the setting is off
- Where: the FAVORITES and RECENT branches (`:2658-2662`) call `channelsByIds` with no adult filter. `applyStartup` tunes `lastTwo().first()` unconditionally (`:3251`). The spec says "Every list hides channels that are adult…" (`spec:263`) and "adult channels never appear with the setting off" (`spec:323`).
- What breaks: someone turns adult on, favorites or watches a channel, then turns it off. The channel still appears in Favorites, Recent and the strip, plays on digit N or hold-Back+OK, and auto-plays at cold launch with no PIN.
- Missed because: `adultChannelsNeverAppearWithSettingOff` (`:3633`) only checks `ListFilter.ALL`.
- Fix: filter `adult` in both branches (placeholder rows for hidden adult favorites), and skip adult channels in startup and `favoriteByNumber` when `show_adult` is off.

### M9. Search shows channels with no working stream
- Where: `search` and `searchCount` (`:502-506`, `:522-523`) lack `(:showNoUp OR hasUp = 1)`. The spec's default filters apply to "Every list" (`spec:263`). `04-search.md:53` even shows "Not working" results in the default worst case.
- What breaks: every all-down channel is searchable and tunable by default, straight into the card. `searchCount` and the list also disagree, because broken-here is filtered client-side after `LIMIT 200` (`:2860`).
- Fix: add the hasUp predicate and the `hiddenIds` exclusion to `search` in SQL.

### M10. Mid-play failure handling has no bound: a livelock loop and no stall watchdog
- Where: `onEvent` treats `Ended` like `Error`, and after a first frame starts a fresh tune (`:1779-1782`). Worked streams are always re-eligible (`:1765`). `onTick` returns `Continue` whenever `hadFirstFrame` (`:1791`).
- What breaks:
  - A single-stream channel whose stream plays a few seconds and dies, or whose playlist carries `#EXT-X-ENDLIST` (the catalog keeps these as `down`, `catalog-job.md:1060`), cycles Retry(2 s) → play → die → "Switching source…" indefinitely. It never reaches the card. The flow map's "no endless spinner" (`frontend-brief.md:14`) and spec §1.1 are violated by design.
  - A stream that stalls after its first frame has no engine timeout. Media3's retries (3 for HLS chunks, 6 for progressive live), each bounded only by OkHttp's 10 s read timeout, can hold a frozen picture well past 30 s.
- Fix: cap mid-play restarts per channel (for example 3 in 2 minutes, then the card), treat `Ended` before N seconds of play as a pre-frame failure, and add a mid-play stall timeout driven by `STATE_BUFFERING` duration.

### M11. Media3's default retry policy turns "a player error fails the stream immediately" into about 3 s per dead stream
- Where: the spec says "A player error fails the stream immediately" (`spec:236`). `PlayerFactory.create` sets no `LoadErrorHandlingPolicy` (`:1539-1543`). Media3 1.11.0 `DefaultLoadErrorHandlingPolicy` (verified in source) retries `InvalidResponseCodeException`, which covers 404, 5xx and DNS/connect IO, 3 times with delays of 0, 1000 and 2000 ms before `onPlayerError`.
- What breaks: each 404-dead stream burns about 3 s of its 4 s cutoff. A channel whose working stream sits in fourth place behind three dead ones, which is common after a night's ranking goes stale, exhausts the 10 s budget before reaching it.
- Missed because: `allDeadShowsNotWorkingWithinBudget` passes at about 6 s with two streams.
- Fix: pass a policy with `minimumLoadableRetryCount = 0` and `TIME_UNSET` for 4xx during the pre-first-frame phase (via `mediaSourceFactory.setLoadErrorHandlingPolicy`).

### M12. The probe marks a geo-blocked *variant* playlist as `down`, but the spec says `unverified`
- Where: `catalog-job.md:1056` — `if (media.status < 200 || media.status >= 300) return result(url, 'down', ...)`. The spec says "unverified: HTTP 401, 403, 429 or 451" (`spec:137`).
- What breaks: CDNs that serve the master publicly and 403 the variant from datacenter IPs get `down`, so the channel is hidden (`hasUp` false). My live 400-stream sample hit this ("media http 403"). Only the top-level status is tested (`:900-905`).
- Fix: apply the same 401/403/429/451 → `unverified` mapping to the media follow-up.

### M13. The ccTLD guess files 777 jmp2.uk FAST channels under United Kingdom and 390 `.tv` channels under Tuvalu; Browse then leads to empty lists
- Where: `ccTldCountry` (`catalog-job.md:469-474`); the test encodes it: "TV Publica" → `'TV'` (`:424`).
- Live data, 2026-09-23: of 2,066 synthetic channels, 777 sit on `jmp2.uk` and get country `UK` (iptv-org's code for the United Kingdom), and 390 sit on `.tv` hosts and get `TV` (Tuvalu). Tuvalu has 2 real channels.
- Also: `countryCounts` counts "Other"-only channels (`:518`) but `channelList` excludes them from country lists (`:491`). The plan's own test shows the disagreement: count 2 "a and o" (`:666`) while the list is `["a"]` (`:669`). That contradicts "Counts use exactly the filters channelList uses" (`:517`) and `03-browse.md:39` ("empty state is unreachable by construction").
- What breaks: Browse lists "Tuvalu ~390". Its "All categories" row opens an empty list. United Kingdom's count is inflated by about 777.
- Fix: never guess country for redirector or generic hosts (use a denylist, or drop ccTLD guessing for `.tv/.uk/.io/.me/.co`). Make `countryCounts` apply `categories != 'other'` the same way the list does.

### M14. The publish guard can ratchet shut forever, and a 404 history silently disables it
- Where: the guard compares against `prev.upRate`, and history is not written on failure (`catalog-job.md:1357-1363`, `:1860`). Nothing overrides it: no env var, no input on `workflow_dispatch` (`:1938-1941`). `loadHistory` returns empty history, and thus "first run", on any 404 (`:1184`).
- What breaks:
  - A legitimate step change in up-rate larger than 25 points leaves the same stale `upRate` in place forever. Causes include a runner-region change, a big host blocking Azure, or iptv-org bulk-adding dead feeds. The job then never publishes again and opens a new issue every night (`:2013`, no de-duplication).
  - Conversely, a wrong `PAGES_BASE` (typo, custom domain, repo rename) makes every night "first run": history never exceeds one day and the guard never fires. Nothing checks for this.
- Fix: add a `workflow_dispatch` input `force` (or a `MAX_DROP` env) that the README documents. After deploy, assert that `${PAGES_BASE}/latest.json` returns the version just built. Comment on an existing open issue instead of creating a new one.

### M15. "Down from the Azure runner" hides channels on every TV, including the owner's priority restreams
- Where: `down` covers timeouts, refused connections and DNS failures (`spec:138`). The spec admits "results depend on [runner region]" (`spec:140`). `hasUp` is false when all streams are down (`catalog-job.md:1624`), and the app hides `hasUp = 0` by default (`spec:263`). The restreams the owner cares about are raw-IP hosts (`spec:54-55`).
- What breaks: a raw-IP restream that drops datacenter traffic, a common policy, is `down` every night. Its channel is hidden on every TV although it plays at home, and the only recovery is an Advanced toggle. From a residential IP, 53 of my 400 sampled streams already failed by timeout or connection error.
- Fix: keep channels with any stream `up` in the last 7 days (`uptime7d > 0`) in `hasUp`. Or map timeouts on raw-IP hosts to `unverified`. Or let local play success override `hasUp` (a local "worked here" mark).

### M16. `onStop` does not release the player, which the spec requires
- Where: the spec says "On Home or standby the player is released promptly so bandwidth stops" (`spec:182`). The plan sets `playerController.cancel(); player.playWhenReady = false` (`plans:3258`).
- What breaks: with `playWhenReady=false` ExoPlayer keeps loading up to its 30 s buffer, keeps refreshing live playlists, and probably keeps the decoder, which switches to a placeholder surface when the SurfaceView is destroyed. On a one-4K-decoder stick, the next app (YouTube from Home) can fail to get a codec.
- Fix: `player.stop()` (or `release()` and recreate) in `onStop`. `onStart` already re-tunes.

### M17. There is no update path for sideloaded TVs and no Room migration policy
- Where: `version = 1, exportSchema = false` (`plans:591`) and `Room.databaseBuilder(...).build()` with no migrations or fallback (`:598-599`). Spec §3.3 describes install but never update. Signing is only "a local signing config" (`:3691`).
- What breaks: the first schema change in V1.1 either crashes every family TV on launch ("Room cannot verify the data integrity"), or, with a destructive fallback added under pressure, wipes favorites and PIN. A lost keystore forces an uninstall, which does the same.
- Fix: `exportSchema = true` from day one plus a committed schema directory and migration tests. Document the update procedure (adb, or a Downloader-app URL) and keystore custody in `RELEASE.md`.

### M18. Prefetch can download a live stream without end, and warms the wrong stream
- Where: `http.newCall(b.build()).execute().use { it.body?.bytes() }` (`plans:2426`). Coroutine cancellation does not interrupt a blocking `execute()`. `candidateOf` takes `streamsForChannel(...).firstOrNull()` (`:2716`), which is `ORDER BY score DESC` and ignores health (`:485`). The catalog deliberately gives some unverified streams a higher score than up ones (`catalog-job.md:1541`).
- What breaks:
  - Focusing a `ts` or unknown-format progressive channel starts an unbounded download that never completes. This holds one of the connections that "single-connection" restream hosts allow (the spec's own warning, `spec:220`).
  - For many channels the prefetched URL is not the one the queue tries first.
- Fix: cap the read (for example `body.source().request(64 KiB)`) and only prefetch `hls`/`dash`. Pick the candidate via `StreamQueue.order`, or `ORDER BY health rank, score`.

### M19. The legal exposure of republishing sports restreams from the owner's GitHub account is undocumented
- Where: spec §1 "free, publicly available live-TV streams" (`spec:9`) against "community-submitted restreams on bare IPs" of SEC Network, ESPNU and FS1 (`spec:54-55`). The owner-added "extras" go in via the backend (`spec:54`). Output is published from a public repo on Pages (`catalog-job.md:2031`).
- What breaks: the owner's GitHub account, not iptv-org, becomes the publisher of a curated, health-checked index of pay-TV restreams. A DMCA notice can take down the repo or Pages. §3.3's recovery (change the URL by hand on every TV) is the only mitigation, and nothing warns the owner.
- Fix: state the risk in the spec. Consider publishing only derived health data (URLs hashed, joined on the TV against iptv-org's own playlist), or excluding pay-TV networks from the backend extras.

### M20. The hold-Back gesture carries the three-press rule, but it is never verified on a real remote
- Where: D10 (`screens-and-flows.md:132`) and `05-favorites.md:53` make hold-Back the only three-press route. The Task 19 checklist (`plans:3693-3703`) has no hold-Back item. The only test synthesises events (`:3548-3551`).
- What breaks: if the Google TV/onn firmware or launcher intercepts long Back, or the BT remote does not auto-repeat BACK, the rule fails on the target hardware. The failure would only show after release.
- Fix: add "Hold Back from Diagnostics, Search-with-keyboard and a dialog opens Favorites" to `RELEASE.md`, and run it in the Task 18 spike window on the stick.

---

## Minors

- m1. `docs/ui/tools/contrast.py:41`: the informational scrim line contains the word "FAIL", so `contrast.py | grep -c FAIL` prints 1. The CLAUDE.md gate says "zero FAIL lines". Reword that line.
- m2. `contrast.py` omits two surfaces that `component-states.md:11` defines: the pressed tint (white 20 %) and the strip's focused card (tint over `surface-banner`). Computed: muted text on a pressed overlay row is 4.26:1, which fails. Add both surfaces.
- m3. `CLAUDE.md:15` says the catalog plan passes "69 tests". The plan now has 74, and all 74 pass with a clean typecheck in my run. `catalog-job.md:1077` says "15 pass", but Task 6 has 14.
- m4. Key table conflicts with the router: `screens-and-flows.md:27` says CHANNEL±/digits/LAST are "ignored" on overlays, but `KeyRouter` maps LAST → Previous everywhere (`plans:2565`, test `:2531`), and D5/`02-channels.md:34` make digits jump-by-letter in Channels. Pick one.
- m5. The "Hold Back · Favorites" hint that D10 promises "in every details panel" (`screens-and-flows.md:132`, `02-channels.md:25`) is missing from the Browse and Search hint lists (`03-browse.md:23`, `04-search.md:26`).
- m6. First launch is described three ways. `08-dialogs-and-strip.md:78` gives two phases and `Progress` gains a `phase`. `:35` gives one determinate bar. The plan's `Progress` has no `phase` (`plans:3112`).
- m7. "Loading channels… 4,200 of 10,000" survives in `spec:261`, `frontend-brief.md:17` and `screens-and-flows.md:54`, although `08:38` withdrew it ("so far").
- m8. D6 still cites "M3U sources" in Advanced (`screens-and-flows.md:127`). Spec §8 still lists catalog "M3U parsing" tests (`spec:321`).
- m9. The language rule bans "a stream count" (`spec:291`) while mandating "Trying source 2 of 5" (`spec:239`) and "Source 30" rows. Define what the ban covers.
- m10. The banner list position has three formats: "Favorites 3 / 12" (`spec:278`), "Favorites · 3 of 12" (`01-player.md:22`) and "List name · i / n" (`plans:2739`).
- m11. Banner geometry, estimated: the name runs to x 656, plus an 8 dp gap and the 32 dp star, ending at 696 (`01-player.md:18`). The clock "12:41 PM" at 48 sp is about 205 dp wide, so its left edge sits near x 691 (`:21`). They collide, against the stated `space-6` gutter (`:27`). Cap the name at about 440.
- m12. The PIN dialog does not fit its height: 272 inner height (320 minus 2 × 24) against title 32, body 20, rings 16 and a keypad of 4 × 48 plus gaps, before the error line (`08:20-23`, `design-tokens.md:50`).
- m13. Copy differs between the no-catalog screen ("Can't reach the channel list." / "Check your connection, then try again.", `08:44`) and the runner and test ("Can't reach the channel list. Check your connection.", `plans:3129,3084`).
- m14. The Diagnostics rationale says it is "reached by holding OK inside Advanced" (`07-settings.md:71`), but its row is a plain `›` (`:40`). The format word on screen also contradicts the absolute invariant in CLAUDE.md.
- m15. A manual source pick is still abandoned at the 4 s cutoff (`plans:1795` ignores `manual`). The spec says a manual pick "suppresses automatic failover for that attempt" (`spec:245`).
- m16. Android Studio's "New Project" into `app/` creates `app/app/src/...`, but every path in the plan assumes `app/src/...` and that `app/build.gradle.kts` is the module (`plans:103,168`).
- m17. CLAUDE.md says key routing has "no Android imports", but `KeyRouter` imports `android.view.KeyEvent` (`plans:2548`). It works because the constants are inlined; fix the wording.
- m18. A GB-locale TV opens first launch filtered to country "GB", but iptv-org uses "UK" (`plans:3241`), so the list is empty.
- m19. The inset's `radius-md` corners (`02-channels.md:16`) cannot clip a `SurfaceView`, so the video corners will be square inside a rounded outline. Accept the square corners or verify on the stick.
- m20. `PoorSignal` counts the `STATE_BUFFERING` that follows every pause/resume `seekToDefaultPosition` as a rebuffer (`plans:1955`, `:2004`). Three pauses in a minute show "Picture is struggling".
- m21. Back on the card is specified to show the banner with "Not working" (`screens-and-flows.md:24`). `vm.back()` only pops (`:2709`), and the card sync may re-push on the next state emission.
- m22. On networks where Google's connectivity check is blocked (DNS filtering), `NET_CAPABILITY_VALIDATED` is never true (`plans:1492-1494`), so no failure, demotion or broken-here mark is ever recorded, silently.
- m23. Node's fetch sends `sec-fetch-mode: cors` and `accept-language: *`, which OkHttp does not, so the runner's request fingerprint differs from the TV's beyond the UA the invariant protects.
- m24. The spec's example `version: 1758520800` (`spec:148`) is 2025-09-22, not 2026.

## Things I checked and found sound

- Catalog plan executed from the markdown: 74/74 tests, `tsc --noEmit` clean, fixture generated from the live API.
- `contrast.py`: every printed verdict is "ok"; the white-frame numbers match `design-tokens.md` exactly.
- UA string is byte-identical in `DEFAULT_UA` (`catalog-job.md:978`) and `BuildConfig.USER_AGENT` (`plans:187`). Node fetch really sends `user-agent` and `referer` as set (tested locally).
- Runtime is fine. jmp2.uk is the largest host with 2,346 streams; measured at per-host 2 it is about 18 min for that host alone, well inside `timeout-minutes: 120`.
- Live data has 0 duplicate (channel, url) pairs after grouping, 0 URLs shared across channels, and no ids with characters outside `[A-Za-z0-9.@:_-]` (max 52 chars). 1,115 split channels and 12,140 channels total.
- Every pinned version exists on Google Maven (Compose BOM 2026.09.00, Media3 1.11.0, Room 2.8.5, tv-material 1.1.0). The problem is only compatibility (B2).
- `loadHistory` 404-versus-throw semantics, limiter fairness, same-day history replace, stale-URL drop, and the guard's first-run pass.
- Import-id staging with a single-transaction flip, orphan cleanup at container init, and parser key-order independence.
- Network security config permits cleartext app-wide with system trust anchors only. OkHttp follows cross-protocol redirects.
- `DefaultLoadControl` arguments match spec §5.3. The `BehindLiveWindow` handling uses the documented `seekToDefaultPosition()` + `prepare()`.
- Failover-engine arithmetic: cutoffs, last-stream remaining budget, `Trying(0,0)` impossible, immediate stop when nothing is eligible.
- Layout sums: Channels, Browse and Search frames each total 864 dp. The strip (96 + 5 × 136 + gaps) fits 840 inner. The card fits 200 dp.
- GitHub Actions: permissions cover Pages, issues and keepalive. The failure job fires on build or deploy failure. The concurrency group prevents overlapping runs.

## Preferences, not findings (≤5 bullets)

- Add a `formatVersion` to the catalog header, plus one shared golden fixture that the Node writer produces and the Kotlin parser consumes in CI.
- Add a per-host collapse check (a host that was mostly `up` last night and is 100 % `down` tonight) alongside the global 25-point guard.
- Make `activeImportIds` and the settings flags a single `StateFlow<Snapshot>` in the container rather than scattered suspend reads.
- Consider recording a local "worked on this TV in the last 7 days" flag that overrides `hasUp` for lists.
- Run the hold-Back check and the SurfaceView inset animation on the real stick before any more UI work is layered on them.
