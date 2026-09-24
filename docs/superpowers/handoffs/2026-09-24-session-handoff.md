# Session handoff — 2026-09-24

For the next session (owner asked to continue with Opus 5.5). Read this, then `CLAUDE.md`, then the spec. Everything below was true when this file was committed.

## 1. Where things stand in one paragraph

The catalog job is built, reviewed, merged to `main`, and set up on GitHub, but it has **never completed a run on GitHub**: the first manual run was cancelled by the owner mid-probe, so nothing is on Pages yet. The app has no code. Fourteen app-side majors and all minors from the Opus adversarial review are still unapplied, and five owner decisions are owed before anyone edits the app plan for them.

## 2. Repository and GitHub state

- Remote: `https://github.com/Flekc09/tvapp.git` (origin). Created empty by the owner this session, made **public** by Claude at the owner's request.
- `main` = merge commit `0cfa864` (PR #1, branch `catalog-job`, 15 commits). The local `catalog-job` branch still exists and tracks origin; it can be deleted.
- Pages: source set to **GitHub Actions** (`build_type=workflow`), site URL `https://flekc09.github.io/tvapp/`. Nothing deployed yet.
- Repository variable `PAGES_BASE` = `https://flekc09.github.io/tvapp` (set via `gh variable set`).
- Workflow `catalog.yml`: state `active`. Run 35947671363 was triggered manually and then cancelled (`gh run cancel`). Its `keepalive` job had already succeeded, which proves the direct `gh api ... /enable` call works. Check whether the cancellation opened a `catalog-failure` issue; it should not (`failure()` is false for cancelled jobs), and if it did, close it.
- No secrets exist or are needed. The workflow uses `github.token` only.

## 3. What was done this session, in order

1. Committed the Opus review's three blockers (already applied the day before) and the review file.
2. Applied the review's catalog majors to the plan and spec, annotated "Opus adversarial review 2026-09-23":
   - M12: a 401/403/429/451 on the media playlist behind a public master → `unverified`.
   - M13 (catalog half): no ccTLD country guess for generic-use TLDs (`.tv .io .me .co .cc .fm .am .to .ly .ws .nu .ai .gg .la .sh .st .su .tk .ml .ga .cf .gq`) or the `jmp2.uk` redirector. Measured on the 2026-09-23 API snapshot: UK 777 / TV 390 / IO 28 misfilings → 0; CN 47, MN 11, RU 5 unchanged.
   - M14: `FORCE_PUBLISH` env + `workflow_dispatch` input `force`; deploy job verifies `latest.json` on Pages carries the built version; failures comment on an open `catalog-failure` issue instead of opening one a night.
3. Executed the catalog plan inline (superpowers:executing-plans), 13 tasks, one commit each, every test watched fail then pass at the plan's stated count. Live run from the owner's connection: 12,160 channels, 17,432 streams, up 12,771 / down 3,550 / unverified 1,111, 21 minutes, `catalog.json.gz` 985,246 bytes. The plan's "1.5 to 4 MB" was an estimate and was corrected.
4. Whole-branch review by a fresh Fable 5.1 subagent: 0 Critical, 2 Important (both fixed), 7 Minor (deferred). Report: `docs/superpowers/reviews/2026-09-24-catalog-branch-fable.md`.
   - Fix 1: `probeStream` never rejects (a variant URI that `new URL` cannot parse used to throw through `Promise.all` and cancel the night). Test: `never throws: a master whose media uri cannot be resolved is down`.
   - Fix 2: workflow permissions per job. While pinning the keepalive action, GitHub reported `gautamkrishnar/keepalive-workflow` **blocked (terms of service, since 2025-04-21)**, so the plan's keepalive could never have run. Replaced with `gh api -X PUT repos/<repo>/actions/workflows/catalog.yml/enable`.
   - Both fixes written back into plan Tasks 6 and 13.
5. Pushed `main` and `catalog-job`, opened and merged PR #1, did the GitHub setup, triggered the run, cancelled it on request.

Test count: 77 (`cd catalog && npm run typecheck && npm test`).

## 4. First thing to do next session

```
gh workflow run catalog.yml --repo Flekc09/tvapp        # leave force off
gh run watch <id> --repo Flekc09/tvapp --exit-status
curl -s https://flekc09.github.io/tvapp/latest.json      # must return {"version":...,"bytes":...}
```

Expect 30 to 60 minutes. The `deploy` job's last step retries for 5 minutes and fails if Pages does not serve the version just built; that failure means `PAGES_BASE` is wrong, not the catalog. The first run starts with empty history (Pages 404 → "first run"), so the guard passes by design.

If the run fails on `npm ci`: the lockfile was generated with npm 11.6.2 on Node 25; CI uses Node 22. It worked locally on both, but that is the one environment difference.

## 5. Owner decisions owed (put to the owner in one batch, before editing the app plan)

Recommended answers in parentheses. Record each in spec section 11 whichever way it goes.

| Item | Question | Recommendation |
|---|---|---|
| M15 | Runner-side `down` hides channels on every TV, including the owner's raw-IP restreams | Keep a channel in `hasUp` if any stream was `up` in the last 7 days (`uptime7d > 0`). Cheapest, no new app state. |
| M19 | The owner's public GitHub account now publishes a health-checked index; pay-TV restream extras would add DMCA exposure | Record the risk in the spec; keep pay-TV extras out of the V1 backend feed. The repo is public as of this session, so the docs are already visible. |
| m4 | Key table says LAST/digits are "ignored" on overlays; router and Channels spec disagree | Keep LAST live everywhere and digits as jump-by-letter in Channels; fix the key table. |
| m9 | Language rule bans "a stream count" but mandates "Trying source 2 of 5" | Define the ban as totals in lists and status lines; positions within a tune stay. |
| m14 | Owner decided Diagnostics keeps the format word; CLAUDE.md invariant says nothing on screen says HLS/TS/DASH | Keep the owner's decision; carve Diagnostics out of the invariant in spec and CLAUDE.md. |

## 6. Open Opus review items (app plan) — apply before executing the app plan

All are still open; none have been touched. Grouped by the fix they share. Line numbers in the review are pre-B3 and drift by about +40 after line 561.

- **Player state and `cancel()`** — M1 (`channelsFor` reads the import id once; lists go empty after a flip), M2 (idle sync never runs because `playing` stays set), M3 (sleep timer does not stop playback), M4 (`MainActivity` still passes raw `bannerVisible`, and `cancel()` never calls `player.stop()`; the interface prose at `back()` was updated but the activity code was not), M16 (`onStop` sets `playWhenReady=false` instead of stopping). Fix together.
- **Key dispatch** — M5 (surf routes on ACTION_UP only; a held Down moves one channel), M6 (every Back branch returns true without `super`; dialogs and pickers have no `Overlay` values).
- **List filtering** — M7 (no `preview` flag on `tune()`), M8 (`channelsByIds` has no adult filter for Favorites and Recent; startup and digit keys skip the check), M9 (`search` lacks the `hasUp` and `hiddenIds` predicates), and the app half of M13 (`countryCounts` counts Other-only channels that `channelList` excludes).
- **Playback policy** — M10 (no cap on mid-play restarts, no stall watchdog), M11 (no `LoadErrorHandlingPolicy`; Media3 retries a 404 three times), M18 (prefetch reads the whole body, no format check, picks by score not health).
- **Release and process** — M17 (`exportSchema = false`, no migration policy, no update procedure or keystore custody in RELEASE.md), M20 (no hold-Back item in the release checklist).
- **Minors** m1–m24: all open. m1 is half-done (contrast.py line 41 still contains the token FAIL). m2 adds two surfaces to contrast.py. m3, m8, m17, m24 are trivial text fixes. m15 and m18 were not fully verified this session.

## 7. Deferred catalog review minors (cheap; do when touching the files)

From `docs/superpowers/reviews/2026-09-24-catalog-branch-fable.md`: blank `CONCURRENCY`/`PER_HOST` hangs the limiter (`Number('')` is 0); no request timeout in `fetch-source.ts` and `history.ts`; post-deploy curl has no cache-buster and a trailing slash in `PAGES_BASE` yields `//`; URL de-dup keeps the last record's headers; playlist byte budget chosen by Content-Type only; a 200 HTML variant is `down` not `unverified`; `{"streams": null}` passes history validation. The first three touch the bad-night path and are the ones worth doing first.

## 8. Things that are easy to get wrong

- **Execute from the plans, not from memory.** If a step fails, record the exact error, fix minimally, update the plan (CLAUDE.md rule). This session's plan corrections are all annotated "Opus adversarial review 2026-09-23" or "final review 2026-09-24".
- The catalog plan's code blocks can be extracted mechanically (the reviewer's `extract.py` in a previous session's scratchpad did this) but it skips `package.json`, `tsconfig.json` and `.gitignore` because those blocks lack a path line or a language tag. The repo now has the real files, so this only matters if re-extracting to scratch.
- The app plan has never been compiled. No JDK or Android SDK on this machine. B2 and B3 are text-verified only; the first Gradle sync will be the real test.
- `catalog/out/` is gitignored and holds the local live-run output (version 1790214909). Do not commit it.
- Do not set `ALLOW_EMPTY_HISTORY` in CI. Do not use `force` for an unexplained up-rate drop.
- The UI is designed with the owner screen by screen (memory: `ui-built-collaboratively`); do not generate all screens in one pass.

## 9. Scratch material that may still exist (temp, not in the repo)

- `/private/tmp/claude-501/-Users-frazen-Documents-TV-App/4dc7d430-.../scratchpad/`: the Opus reviewer's scratch, including `extract.py`, `apply_blockers.py`, a 2026-09-23 snapshot of the eight iptv-org API files (`streams.json` 17,498 streams), and Media3/Room AAR metadata under `maven/`.
- `/private/tmp/claude-501/-Users-frazen-Documents-TV-App/a9ab0e16-.../scratchpad/`: this session's `apply_catalog_majors.py`, `run_tasks.py` (the RED/GREEN task driver) and `cat2/` (the extracted, verified plan code).

## 10. Commands

```
cd catalog && npm run typecheck && npm test              # 77 tests
cd catalog && npx vitest run test/probe.test.ts          # one file
PAGES_BASE=https://flekc09.github.io/tvapp OUT_DIR=out npm run run   # local run against the real Pages history (no ALLOW_EMPTY_HISTORY once Pages has history.json)
python3 docs/ui/tools/contrast.py                        # UI gate; zero FAIL lines (m1 still prints one informational line containing the word)
gh run list --repo Flekc09/tvapp --workflow catalog.yml
```
