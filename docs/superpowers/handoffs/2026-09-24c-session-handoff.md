# Session handoff — 2026-09-24 (third session)

For the next session. Read this, then `CLAUDE.md`, then the app plan's Global Constraints. It supersedes `2026-09-24b-session-handoff.md` for the app; that file's catalog notes (§2 item 2, §7) still apply. Everything below was true when this file was committed (about 18:40 UTC on 2026-09-24).

## 1. Where things stand in one paragraph

The TV app is being built from `docs/superpowers/plans/2026-09-22-tv-app.md`, task by task, on branch **`app-plan`** (local only, **not pushed**). Tasks 1–13 are done, one commit each, all tests green: 58 JVM unit tests, 12 instrumented tests on the emulator, `lintDebug` at zero errors. The app runs on the Android TV emulator against the live catalog: it downloads `catalog.json.gz` from Pages, plays channels, surfs with the banner, fails over, shows the "isn't working" card, and has working Channels (live preview inset) and Browse overlays. Screens are a plain first pass that follows `docs/ui/screens/`; the owner will restyle them later (their choice, §5). Next is **Task 14, Search and Favorites**. Android Studio is not used: the JDK and SDK were installed from the command line this session.

## 2. First things to do next session

1. **Start the emulator** if it is not running: `emulator -avd tv_api34 &`, then wait for `adb shell getprop sys.boot_completed` to print `1` (about 30 s). It shows the Android TV home screen with network. The debug app is already installed on it with a catalog imported.
2. **Ask the owner about the open decision in §6** (the emulator decoder workaround) and whether to push `app-plan` (§3).
3. **Continue with Task 14** using superpowers:executing-plans. The owner drives one task at a time ("continue with task N"); report after each task and wait. Resume the ledger (§4); do not redo Tasks 1–13.

## 3. Repository and GitHub state

- Local branch **`app-plan`**, cut from `main` at `6c5bf2c`. 14 commits, **not pushed, no PR**. The owner prefers branch + PR, so offer `git push -u origin app-plan` and a PR when they are ready. Nothing has been pushed this session.
  ```
  bb25df8 Task 13  Channels overlay with live preview inset and Browse
  82dc784 Task 12  root player surface, key routing, banner, notices, context and sources menus
  3879aec Task 11  playlist prefetch
  5c43583 Task 10  catalog sync with version check, streaming download and idle worker
  f176736 Task 9   failover engine, measurement sink, player controller
  762a8bb Task 8   shared OkHttp client, network state, ExoPlayer factory
  c722b85 Task 7   tune budget and debounce
  a4d4a6e Task 6   stream queue, local score, broken-here
  14b9f0f Task 4   catalog importer (Task 5 is the removed stub, no commit)
  de61129 Task 3   streaming gzip catalog parser
  94717c1 Task 2   Room database
  4adf233 Task 1   scaffold
  b57eaaa          plan Task 1 rewritten for a build without Android Studio
  ```
  This handoff and the `CLAUDE.md` state update are one more commit on top.
- **PR #5** (`catalog-minors`, the catalog branch review's seven deferred minors) is **open, not merged**. It is independent of `app-plan`.
- Catalog on Pages is healthy: the first **scheduled** run (35995130169, 2026-09-24 11:47 UTC) succeeded, so the publish guard ran with real history for the first time. `latest.json` = `{"version":1790251626,"bytes":979840}`. No open issues.
- The pinned catalog numbers the app saw today: 178 countries, 31 categories, 12,160 channels (2,066 synthetic, 1 adult), 17,432 streams (12,396 up, 1,222 unverified, 3,814 down). The United States list is 1,431 channels; All is 7,088.

## 4. The execution ledger and how tasks are run

- Ledger: `.superpowers/sdd/2026-09-22-tv-app/progress.md` (gitignored). The first line names the plan; every task has a `Task N: complete (commits …, tests: … → …)` line, and every deviation has a `Task N: Ruling:` line with its cost if wrong. **Read it before Task 14.** If it is gone (`git clean -fdx`), `git log` is the record; this file lists the rulings in §7.
- Scripts (superpowers 6.4.1): `…/skills/executing-plans/scripts/task-start PLAN N` prints the brief path and BASE; `task-done PLAN N BASE -- bash -c 'cd app && ./gradlew …'` runs the suite and appends the completion line. Its result column records Gradle's last log line ("Consider enabling configuration cache…"); this session replaced that with the real counts using `sed` afterwards.
- Per task: read the brief; write the test from the brief first and watch it fail; write the code from the brief; run; compare with every `Expected:` line; fix plan defects minimally and write them back into the plan with an "executed 2026-09-24" note; commit; `task-done`.
- The **whole-branch final review** (fresh reviewer on the most capable model) has not run; it runs once after the last task, per the skill.
- Commit trailer used: `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>` (the executing model), not the plan's Fable 5.1 line. That is a recorded ruling.

## 5. Owner decisions made this session

- **No Android Studio.** JDK and SDK from Homebrew and `sdkmanager`; plan Task 1 rewritten to match.
- **Emulator setup** (asked for, done): AVD `tv_api34`, Android TV (not Google TV) API 34 image, 1080p, `hw.keyboard=yes`.
- **UI tasks, option 1:** I build the behavior plus plain first-pass screens that follow `docs/ui/screens/`; the owner reviews on the emulator and restyles afterwards. (The standing memory says the owner builds UI screen by screen with Claude; this choice defers that to a restyle pass rather than dropping it.)
- **Task pacing:** one task per request, with a plain-language report after each. Not the skill's run-all-tasks default.

## 6. Open question for the owner

**Emulator decoder workaround?** The emulator's `c2.goldfish.h264.decoder` draws a new channel of a different resolution into a corner of the previous channel's frozen frame (seen as two pictures at once, e.g. a 1216 × 684 picture over a 1920 × 1080 one). It is an emulator flaw, not the app (one SurfaceView layer, the stale pixels are in the decoder's buffer). Option offered: a debug-only change that makes the emulator use Android's software decoder instead. It would not affect real hardware, but emulator video may be choppier. The owner has not answered. If yes, the smallest version is a `MediaCodecSelector` in `PlayerFactory.create` that drops decoder names containing `goldfish` when `Build.HARDWARE == "ranchu"`, behind `BuildConfig.DEBUG`, recorded as a ruling.

## 7. Plan defects found and fixed this session (all written back into the plan)

Each has an "executed 2026-09-24" note at its place in the plan.

| Task | Defect | Fix |
|---|---|---|
| 1 | KSP 2.2.10-2.0.2 fails under AGP 9 built-in Kotlin: "Using kotlin.sourceSets DSL to add Kotlin sources is not allowed with built-in Kotlin" | `android.disallowKotlinSourceSets=false` in `app/gradle.properties` (keeps the verified toolchain; AGP warns "experimental" on every build, expected) |
| 1 | `gradle.properties` content and `app/.gitignore` unspecified | written; see plan Task 1 Step 3 |
| 1 | Placeholder "TV App" text black on black | white |
| 4 | `connectedDebugAndroidTest --tests X` does not exist | `-Pandroid.testInstrumentationRunnerArguments.class=X[,Y]` in all six plan commands and `CLAUDE.md` |
| 4 | `CatalogImporterTest` did not import `StreamEntity` | import added |
| 8 | `playerBuilds` released the player off its thread | build and release inside `runOnMainSync` |
| 8 | The `-opt-in=…UnstableApi` compiler flag is a no-op; lint found 18 `UnsafeOptInUsageError` (would fail `lintVitalRelease`) | flag removed; **every file using Media3 `@UnstableApi` starts with `@file:OptIn(UnstableApi::class)`** (androidx.annotation.OptIn). New Global Constraint: `lintDebug` shows zero of them after any Media3 task |
| 10 | Commit step omitted `ui/state/FirstLaunchState.kt` | added |
| 12 | Lint `RestrictedApi` on `super.dispatchKeyEvent` in a `ComponentActivity` (androidx.core false positive) | `@SuppressLint("RestrictedApi")` on `dispatchKeyEvent`; **Task 16's `MainActivity` in the plan now carries it too** |
| 12 | `ContextCastToActivity` | `LocalActivity.current?.window` |
| 13 | Lint `StateFlowValueCalledInComposition` | collect as state |
| 13 | One `requestFocus()` after `scrollToItem` misses, overlays opened unfocused | `FocusRequester.requestWhenReady { landed }` in `ui/Tokens.kt`, retries each frame |

## 8. What Tasks 12–13 added beyond the plan's code (reconcile in 14–16)

- `AppContainer` in `TvApp.kt` **is Task 16's version already** (brought forward so Task 12 could run on the emulator). Task 16 should find it written and change nothing in it.
- `MainActivity.kt` is a **temporary** copy of Task 16's key dispatch without the first-launch screen. A debug build with no catalog downloads the live one through `CatalogSync.download` and opens on `deviceCountry()`'s list; startup resumes the last Recent. **Task 16 replaces this file**; keep its `@SuppressLint("RestrictedApi")`.
- `AppViewModel` members not in the plan's code block: `toast`/`toast(text)`, STRIP auto-close via `wake()`, `favoriteIds`, `sourcesFor(id)`, `countries`, `categoryNames`, `previousName()`, `clearFocusFirstRow()`, `channelsOf(filter)`, `brokenIds`, `countryCounts()`, `categoryCounts(country)`.
- New files: `ui/Tokens.kt` (design-token values, `tvFocus`, `requestWhenReady`), `ui/JumpByLetter.kt` (T9 groups), `ui/player/*` (banner, notices, card, context menu, Sources, strip, `PlayerSurface`), `ui/channels/*`, `ui/browse/*`.
- Overlays still drawn as a one-line placeholder: SEARCH, SETTINGS, ADVANCED, DIAGNOSTICS, PIN, ADDRESS (Tasks 14–16).
- `PoorSignal.kt` is the plan's never-prompting stub until Task 15 replaces it.

## 9. Things verified on the emulator (live catalog)

Banner on surf within a frame, tune after 300 ms debounce, last frame held while tuning; dead channels open the card with Next channel focused (real 404s and malformed playlists in the logs, not app faults); Back on the bare player arms exit; Back with the banner opens Channels with the picture in the 288 × 162 inset and the scrim cut out around it; long-press OK menu; Sources shows "Playing"; strip on Left; relaunch resumes the last channel; Channels focus on the current row, 700 ms preview, failed preview reads "Not working" without a card, Back keeps and commits the preview; column preview and commit; Browse counts, categories following focus, Right to "All categories", pick opens Channels with the country as the column's extra entry.

**Found on the emulator, recorded for hardware (no code change):**
- **GUIDE never reaches the app**: Android TV maps `KEYCODE_GUIDE` (and TV, DVR, TV_INPUT) to `com.google.android.tv/.receiver.GlobalKeyReceiver` (`dumpsys window`, `mKeyMapping`). The key table already treats GUIDE as a CEC accelerator. Task 19 confirms on the stick.
- The goldfish decoder artifact (§6). Task 18 checks that channel changes between resolutions render whole on the stick.

**Notes for the owner's restyle pass:** the card title wraps to two lines at the spec's 480 dp; the strip's list label wraps to three lines in its 96 dp block; Channels rows put "● Working" on line two even when there is no region.

## 10. Environment (this Mac)

- JDK: `brew install openjdk@21` (keg-only; the Temurin cask needs sudo, which this shell cannot give). `JAVA_HOME=/opt/homebrew/opt/openjdk@21`.
- SDK at `~/Library/Android/sdk`: `platform-tools`, `emulator`, `cmdline-tools;latest`, `platforms;android-37.0`, `build-tools;37.0.0`, `system-images;android-34;android-tv;arm64-v8a`. Also `brew install android-commandlinetools gradle` (Gradle 9.7.1, used only to generate the 9.6.0 wrapper).
- `~/.zshrc` exports `JAVA_HOME`, `ANDROID_HOME` and the PATH for `adb`, `emulator`, `sdkmanager`, `avdmanager`. **Tool-run shells do not source it**: prefix Gradle calls with `export JAVA_HOME=/opt/homebrew/opt/openjdk@21 ANDROID_HOME=~/Library/Android/sdk;` and call `~/Library/Android/sdk/platform-tools/adb` by path.
- Mac keys in the emulator window: arrows = D-pad, Return = OK, **⌘⌫ = Back**, **⌘⇧H = Home** (plain ⌘H hides the window; Esc sends nothing useful). From the shell: `adb shell input keyevent KEYCODE_DPAD_CENTER`, `--longpress` for a hold, `adb exec-out screencap -p > x.png` to look.
- A launch that shows a black screen for a few seconds is the tune; `adb logcat -d | grep -E "Caused by|Response code"` shows why a channel failed.

## 11. Easy to get wrong

- `cd app` for every Gradle command; `app/` is a single-project build.
- Instrumented runs: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.tvapp.…` (no `--tests`).
- Run `lintDebug` after any task that touches Media3 or an Activity; the plan's code does not always pass it.
- The screen specs in `docs/ui/screens/` win over the plan's prose for layout and wording; the plan wins for behavior and build order.
- Nothing on screen may say HLS, TS, DASH, unverified, demoted, unsorted, or a stream count; status words are Working, Not checked, Not working.
- Commit and push only when asked; the owner has asked for a commit per task (the plan's own steps) but has not asked to push `app-plan` yet.
