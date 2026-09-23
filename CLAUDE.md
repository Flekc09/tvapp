# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## State of the repository

Planning is complete; no product code exists yet. The repo holds an approved design spec, two implementation plans, and two framework agents. Read the spec before touching anything, and execute only from the plans.

- `docs/superpowers/specs/2026-09-22-tv-app-design.md` — the approved spec. Every decision, with the rejected alternatives, is in its section 11.
- `docs/superpowers/plans/2026-09-22-catalog-job.md` — 13 tasks, TypeScript. Build this first; the app consumes its output.
- `docs/superpowers/plans/2026-09-22-tv-app.md` — 19 tasks, Kotlin / Android TV. Task 18 is a throwaway spike, Task 19 is the hardware release checklist.
- `.claude/agents/sdlc.md` governs the engineering lifecycle; `.claude/agents/ui-ux.md` guides screen design. The owner designs the UI screen by screen with Claude using the ui-ux playbooks; do not generate all screens in one pass.

The spec and both plans have been through adversarial reviews (engineering, viewer psychology, cold-executor). The catalog plan's tasks were executed verbatim in a scratch directory and pass (69 tests) with type-checking. The app plan's library calls were verified against Media3, Room and Compose sources at the pinned versions. Do not "fix" the plans from memory; if a step fails, record the exact error and fix minimally, then update the plan.

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

Single activity. Room holds the imported catalog plus local state. Catalog imports are staged under a new `importId` and flipped atomically. There are no user-added M3U sources (removed 2026-09-23; extras go into the catalog job). `playback/FailoverEngine.kt` is a pure decision class (queue, 10 s tune budget, per-stream cutoffs, 2 s retry gap, fresh budget on every mid-play death); `PlayerController` applies its decisions to one ExoPlayer and records failures. The UI is one root player surface with overlays drawn over the still-playing video; the Channels inset is the same view animated to a corner, never a second view or decoder. All logic that can run on the JVM (queue ordering, budget, broken-here, the catalog parser, key routing, first-launch state machine) has injected clocks and no Android imports.

### Invariants the two halves share

- User agent string `TVApp/1.0 (Android TV; Media3)`: the job's `DEFAULT_UA` and the app's `BuildConfig.USER_AGENT` must be identical, because health is tested with it.
- Catalog file format is spec section 4.4; the app's streaming parser reads exactly those field names.
- Nothing on screen says HLS, TS, DASH, unverified, demoted, unsorted, or a stream count. Status words are Working, Not checked, Not working.
- Cleartext HTTP is allowed (the sports restreams need it); HTTPS is validated normally with no self-signed exceptions.

## Commands

None run yet. Once the plans are executed:

Catalog job (Node 22 in CI, ESM, vitest):
```
cd catalog
npm install
npm run typecheck && npm test            # strict TypeScript is only enforced by running this
npx vitest run test/probe.test.ts        # one file
npm run fixture                          # regenerate test/fixtures/api from the live API (rarely)
PAGES_BASE=https://<owner>.github.io/<repo> ALLOW_EMPTY_HISTORY=1 OUT_DIR=out npm run run   # local full run; never set ALLOW_EMPTY_HISTORY in CI
```

TV app (Android Studio, Android TV emulator "Television (1080p)" API 34):
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
