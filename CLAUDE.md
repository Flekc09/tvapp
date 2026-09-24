# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## State of the repository

Planning and UI design are complete. The catalog job (`catalog/`, `.github/workflows/catalog.yml`) was built from its plan on 2026-09-24 on branch `catalog-job`: 13 tasks, one commit each, 77 tests, a live run against the API, and a whole-branch review whose two Important findings were fixed (`docs/superpowers/reviews/2026-09-24-catalog-branch-fable.md`, deferred minors listed there). It is merged to `main`, and its first GitHub run published to `https://flekc09.github.io/tvapp/` on 2026-09-24. The M15 `hasUp` change the same day brought the suite to 78 tests. The app has no code yet. The repo also holds the approved design spec, two implementation plans, a UI design package, and two framework agents. Read the spec before touching anything, and execute only from the plans.

- `docs/superpowers/specs/2026-09-22-tv-app-design.md` — the approved spec. Every decision, with the rejected alternatives, is in its section 11.
- `docs/superpowers/plans/2026-09-22-catalog-job.md` — 13 tasks, TypeScript. Build this first; the app consumes its output.
- `docs/superpowers/plans/2026-09-22-tv-app.md` — 19 tasks, Kotlin / Android TV. Task 5 is a stub (user M3U playlists were cut 2026-09-23; numbering kept so cross-references hold), Task 18 is a throwaway spike, Task 19 is the hardware release checklist.
- `docs/ui/` — the UI design package, owner-approved and past the ui-ux agent's Gate S on 2026-09-23: `frontend-brief.md`, `screens-and-flows.md` (screen inventory, red routes, deviations D1–D12), `design-tokens.md`, `component-states.md`, and `screens/01`–`08`, one spec per surface with layout in dp, the four states, and the owner's decisions. Mockups are a private Design artifact, "TV App screens" (link in each screen spec). The UI tasks of the app plan (12–17) were rewritten to match this package; when the two disagree, the screen spec is the design and the plan is the build order.
- `docs/superpowers/reviews/` — review reports kept verbatim. `2026-09-23-adversarial-opus.md` is the Opus 5.5 adversarial review; its three blockers (B1 float `responseMs`, B2 toolchain re-pin to AGP 9.4.1 / compileSdk 37 / built-in Kotlin 2.2.10, B3 compile errors) were applied to the plans the same day and are annotated inline as "Opus adversarial review 2026-09-23". Its catalog-side majors M12 (geo-blocked media playlist is `unverified`), M13 (no ccTLD guess for generic-use TLDs or redirector hosts; the `countryCounts` half is app-side and still open) and M14 (`force` input, post-deploy `latest.json` check, issue de-duplication) were applied on 2026-09-24 with the same annotation. The app-side majors and all minors are still open. The five owner decisions that review asked for (M15, M19, m4, m9, m14) were made on 2026-09-24 and are recorded in spec section 11; M15 is applied in the catalog job, M19 removed the backend extras input (missing feeds go upstream to iptv-org), and m4, m9 and m14 are applied to the spec and UI docs but not yet to the app plan.
- `docs/superpowers/handoffs/` — session handoff notes. Read the newest one first when resuming: it says what is done, what is running on GitHub, and which owner decisions are owed.
- `.claude/agents/sdlc.md` governs the engineering lifecycle; `.claude/agents/ui-ux.md` guides screen design. The owner designs the UI screen by screen with Claude using the ui-ux playbooks; do not generate all screens in one pass.

The spec, both plans and the UI package have been through adversarial reviews (engineering, viewer psychology, cold-executor, and a UI review whose blockers, majors and minors were all applied). The catalog plan's tasks were executed verbatim in a scratch directory and pass (77 tests) with type-checking. The app plan's library calls were verified against Media3, Room and Compose sources at the pinned versions. Do not "fix" the plans from memory; if a step fails, record the exact error and fix minimally, then update the plan.

## What is being built

An Android TV / Google TV app, sideloaded, that plays the entire iptv-org free live-TV catalog (about 10,000 channels, 17,500 streams) with cable-like feel: turns on to a picture, tunes in under two seconds, fails over between duplicate feeds without the viewer touching the remote. One household, one TV at a time, no accounts, no server the owner operates.

Two components in one repo:

```
iptv-org API ─▶ catalog/ (nightly GitHub Actions job) ─▶ catalog.json.gz + history.json + latest.json on GitHub Pages
                                                                          │
                                                app/ (Kotlin, Compose for TV, Media3) ◀── downloads when idle
```

### Catalog job (`catalog/`)

A pipeline of pure TypeScript modules: fetch eight iptv-org API files → group streams into catalog channels (regional affiliates split into `<channel>@<feed>` with a `region`; channel-less and unknown-id streams become `synthetic:<sha>` channels; adult channels flagged, never dropped) → probe every URL with a global/per-host limiter and a format-aware health test (`up` / `unverified` / `down`) → merge into a 7-day history read back from Pages at the start of each run → score and rank (health first, then score) → guard (refuse to publish if the up-rate collapses) → write. Every module takes `fetch` as a parameter; tests never touch the network. Published with the Pages artifact action, never committed. State that must survive runs lives in `history.json` on Pages, nowhere else.

### TV app (`app/`)

Single activity. Room holds the imported catalog plus local state. Catalog imports are staged under a new `importId` and flipped atomically. There are no user-added M3U sources (removed 2026-09-23) and no backend extras (ruled out 2026-09-24; missing feeds go upstream to iptv-org). `playback/FailoverEngine.kt` is a pure decision class (queue, 10 s tune budget, per-stream cutoffs, 2 s retry gap, fresh budget on every mid-play death); `PlayerController` applies its decisions to one ExoPlayer and records failures. The UI is one root player surface with overlays drawn over the still-playing video; the Channels inset is the same view animated to a corner, never a second view or decoder. All logic that can run on the JVM (queue ordering, budget, broken-here, the catalog parser, key routing, first-launch state machine) has injected clocks and no Android imports.

### Invariants the two halves share

- User agent string `TVApp/1.0 (Android TV; Media3)`: the job's `DEFAULT_UA` and the app's `BuildConfig.USER_AGENT` must be identical, because health is tested with it.
- Catalog file format is spec section 4.4; the app's streaming parser reads exactly those field names.
- Nothing on screen says HLS, TS, DASH, unverified, demoted, unsorted, or a stream count (a total such as "5 sources"; positions within a tune such as "Trying source 2 of 5" are fine). Diagnostics is the one exception and shows the format word. Status words are Working, Not checked, Not working.
- Cleartext HTTP is allowed (the sports restreams need it); HTTPS is validated normally with no self-signed exceptions.

### UI rules that are easy to get wrong

- The stick's own remote (Chromecast with Google TV, onn) sends only D-pad, OK, Back, Home and app keys. GUIDE, digits, LAST_CHANNEL and CHANNEL_UP/DOWN arrive only as CEC pass-through from a TV remote and are accelerators. Every route must work with D-pad, OK and Back; **hold Back** is the favorites key on every surface (flow map D10), and the "isn't working" card is an overlay so its buttons take focus.
- `docs/ui/screens-and-flows.md` section 2 is the one per-surface key table; `KeyRouter` implements exactly that.
- Text never sits on the scrim alone, status words never ellipsise (fixed slot), wide rows grow 8 dp rather than scale, and `python3 docs/ui/tools/contrast.py` must print zero FAIL lines after any colour or opacity change.

## Commands

Catalog job (built; Node 22 in CI, ESM, vitest):
```
cd catalog
npm install
npm run typecheck && npm test            # strict TypeScript is only enforced by running this
npx vitest run test/probe.test.ts        # one file
npm run fixture                          # regenerate test/fixtures/api from the live API (rarely)
PAGES_BASE=https://<owner>.github.io/<repo> ALLOW_EMPTY_HISTORY=1 OUT_DIR=out npm run run   # local full run; never set ALLOW_EMPTY_HISTORY in CI
```

UI package:
```
python3 docs/ui/tools/contrast.py        # every text/background ratio; zero FAIL lines is the gate
```

TV app (not built yet; Android Studio, Android TV emulator "Television (1080p)" API 34):
```
cd app
./gradlew testDebugUnitTest                                              # JVM tests
./gradlew testDebugUnitTest --tests 'com.tvapp.playback.FailoverEngineTest'
./gradlew connectedDebugAndroidTest                                      # needs the emulator
./gradlew connectedDebugAndroidTest --tests 'com.tvapp.playback.FailoverInstrumentedTest'
./gradlew installDebug
```

Instrumented tests force a "validated" network through `SwitchableNetworkState` because the emulator has none; the failover test needs the activity launched so a video surface exists.

## Owner-facing setup that code cannot do

The catalog job needs, once: the repo public on GitHub, Pages source set to "GitHub Actions", and a repository variable `PAGES_BASE`. The app's `BuildConfig.CATALOG_BASE_URL` placeholder is replaced with that value; the Advanced setting overrides it per TV. The owner has a cast-only Chromecast, which cannot run apps; release testing needs an onn Google TV stick or equivalent.
