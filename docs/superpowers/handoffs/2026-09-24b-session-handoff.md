# Session handoff — 2026-09-24 (second session)

For the next session. Read this, then `CLAUDE.md`, then the spec. It supersedes `2026-09-24-session-handoff.md`, whose "first thing to do" and "decisions owed" are both done. Everything below was true when this file was committed (about 04:15 UTC on 2026-09-24).

## 1. Where things stand in one paragraph

The catalog job is live on GitHub Pages and has published once. Every item of the Opus adversarial review is resolved: the five owner decisions were made and recorded, the catalog-side and app-side majors are applied, and the minors are applied in PR #4, which is **open, not merged**. The app still has no code and its plan has never been compiled; the next real step is executing the app plan on a machine with Android Studio.

## 2. First things to do next session

1. **Merge PR #4** (https://github.com/Flekc09/tvapp/pull/4, branch `review-minors`, one commit plus this handoff) if the owner has not already, then `git checkout main && git pull` and delete the local branch. This file lives on that branch, so it reaches `main` with the merge.
2. **Check the first scheduled catalog run.** The cron is `17 6 * * *` (06:17 UTC). The first scheduled run is the first with (a) the M15 `hasUp` change and (b) non-empty history, so the publish guard is live for the first time.
   ```
   gh run list --repo Flekc09/tvapp --workflow catalog.yml --limit 3
   curl -s https://flekc09.github.io/tvapp/latest.json      # version should be newer than 1790217586
   gh issue list --repo Flekc09/tvapp --state open          # a catalog-failure issue means the run failed
   ```
   If the guard refused (up-rate fell more than 25 points from 0.70), read the run log before anything else; do not use the `force` input for an unexplained drop.
3. **Ask the owner** whether to start executing the app plan (needs the tools in §6), or first regenerate the two stale mockup boards (§5), or do the seven deferred catalog minors (§7).

## 3. Repository and GitHub state

- Remote `https://github.com/Flekc09/tvapp` (public). `main` is at `09db913` (merge of PR #3). Local branch `review-minors` = PR #4.
- PRs: #1 catalog job, #2 owner decisions + M15, #3 app-side majors, all merged. #4 minors, open.
- Pages: source "GitHub Actions", site `https://flekc09.github.io/tvapp/`. Repository variable `PAGES_BASE = https://flekc09.github.io/tvapp`. No secrets.
- Published once, by manual run 35948171914 (2026-09-24, build 21m41s): 12,160 channels, 17,432 streams, up 12,220 / down 4,043 / unverified 1,169 (up-rate about 0.70). `latest.json` = `{"version":1790217586,"bytes":977098}`. `history.json` and `catalog.json.gz` both serve 200. That run predates the M15 change.
- No open issues. The cancelled run from the previous session opened none.
- Annotations on that run, harmless for now: actions on Node 20 are being forced to Node 24; `ubuntu-latest` moves to Ubuntu 26 from 2026-10-19. Neither needs action unless a run breaks.

## 4. What was done this session, in order

1. Ran the catalog workflow (force off) and confirmed Pages serves all three files.
2. Owner decisions, all recorded in spec §11 with rejected alternatives:
   - **M15**: a channel stays `hasUp` if any stream had `uptime7d > 0`. Code in `catalog/src/build.ts`, test in `test/build.test.ts`, plan Task 11 updated to match byte for byte. Suite: 78 tests.
   - **M19**: no backend extras input. Feeds iptv-org lacks are submitted upstream to iptv-org by pull request. The owner asked twice *why* this mattered technically; the honest answer is that it doesn't, it is a legal-exposure choice (the public repo republishes iptv-org's list, US sports restreams included, under the owner's account). Explain it that way if it comes up again.
   - **m4**: LAST live everywhere; digits jump by letter in Channels; CHANNEL± ignored on list overlays. Key table in `docs/ui/screens-and-flows.md` §2 fixed.
   - **m9**: the "stream count" ban covers totals ("5 sources"), not positions ("Trying source 2 of 5").
   - **m14**: Diagnostics shows the format word; the invariant in CLAUDE.md carries the exception.
3. Applied app-side majors M1–M11, M13 (app half), M16–M18, M20 to the app plan (PR #3). The plan's Self-review section lists each with its task and test. Library calls were checked against class files, not memory: `onInterceptKeyBeforeSoftKeyboard` is stable in Compose UI 1.12.1; `DefaultLoadErrorHandlingPolicy` in Media3 1.11.0 is open with overridable `getRetryDelayMsFor` / `getMinimumLoadableRetryCount`; `DefaultMediaSourceFactory.setLoadErrorHandlingPolicy` exists; `androidx.room` Gradle plugin 2.8.5 exists on Google Maven.
4. A fresh reviewer agent traced the PR #3 diff as a compiler and test-runner: no compile errors, all counts hold, four behaviour bugs found and fixed before merge (a preview outliving the Channels overlay; Back in Channels cancelling a preview tune; the card re-pushing on every state emission; Compose consuming Back DOWN as `FocusDirection.Exit`, which is why `MainActivity` now consumes Back DOWN and calls `onBackPressedDispatcher.onBackPressed()` on UP).
5. Minors (PR #4). Owner decisions: **m2** `press-tint` 15 % (was 20 %) and the strip on `panel-strong`; **m12** PIN dialog 480 × 416; **m19** inset corners checked on the stick in Task 18 step 2a, square if the video will not clip. **m23** (Node fetch sends `sec-fetch-mode` and `accept-language`, OkHttp does not) accepted and recorded in spec §4.2. The rest applied; the PR body lists each.

## 5. Things that are stale or unverified

- **The app plan has never been compiled.** No JDK or Android SDK on this Mac. Blockers B2/B3 and everything in PRs #3 and #4 are text- and class-file-verified only. The first Gradle sync (Task 1 step 7) is the real test; follow the plan's rule: record the exact error, fix minimally, update the plan.
- **Mockups are behind the screen specs.** The Design artifact "TV App screens" (https://claude.ai/artifact/19F9iWwZnDqLst7nm6NcBy) does not show the 480 × 416 PIN dialog, the strip on `panel-strong`, or the banner name capped at 440 dp. The generator still exists at `/private/tmp/claude-501/-Users-frazen-Documents-TV-App/6337ee1a-2f3f-435c-b4e9-c01b924ee1eb/scratchpad/player-canvas/gen.py` (temp; may be cleaned). The screen specs are the source of truth. Offer to regenerate only the "Dialogs & strip" and "Player" boards; read `project/canvas.json` first.
- **Hardware-only risks** (Task 18 spike and Task 19 checklist cover them): hold Back reaching the app on the onn/Chromecast launcher; auto-repeat on the stick remote; `SurfaceView` corner clipping; Back key dispatch relies on `android:enableOnBackInvokedCallback="false"` (now pinned in the manifest). The owner has only a cast-only Chromecast, which cannot run apps: release testing needs an onn Google TV stick or equivalent.

## 6. Executing the app plan (when the owner is ready)

- Needs Android Studio with AGP 9.4 support (September 2026 or later), a JDK, and the "Television (1080p)" API 34 emulator. Task 1 step 1 now says: generate the template in a scratch directory and copy only the Gradle wrapper into `app/`; `app/` is a single-project build.
- Replace `BuildConfig.CATALOG_BASE_URL`'s `https://OWNER.github.io/REPO` with `https://flekc09.github.io/tvapp`.
- Use superpowers:subagent-driven-development or executing-plans, one commit per task, on a branch, PR at the end (the owner prefers branch + PR).
- UI tasks 12–17: the owner designs with Claude screen by screen (memory `ui-built-collaboratively`); the screen specs in `docs/ui/screens/` win over the plan's prose when they disagree.
- Expected counts to watch: FailoverEngineTest 17, PrefetcherTest 4, PlayerFactoryTest 3, KeyRouterTest 6, DeviceCountryTest 1, Task 17 instrumented 17 (5 failover + 12 navigation).

## 7. Deferred catalog minors (unchanged; cheap)

From `docs/superpowers/reviews/2026-09-24-catalog-branch-fable.md`: blank `CONCURRENCY`/`PER_HOST` hangs the limiter; no request timeout in `fetch-source.ts` and `history.ts`; post-deploy curl has no cache-buster and a trailing slash in `PAGES_BASE` yields `//`; URL de-dup keeps the last record's headers; playlist byte budget chosen by Content-Type only; a 200 HTML variant is `down` not `unverified`; `{"streams": null}` passes history validation. The first three touch the bad-night path.

## 8. Easy to get wrong

- Execute from the plans, not from memory. Every edit this session is annotated "Opus adversarial review 2026-09-23, major/minor N" or "owner decision 2026-09-24".
- `python3 docs/ui/tools/contrast.py | grep -c FAIL` must print 0 after any colour or opacity change; it now also measures the pressed row and the strip.
- `cd catalog && npm run typecheck && npm test` = 78 tests.
- Don't set `ALLOW_EMPTY_HISTORY` in CI; don't `force` a publish to get past an unexplained drop.
- `catalog/out/` is gitignored local output; never commit it.
- Commit and PR only when asked; the owner has asked for branch + PR each time so far.
