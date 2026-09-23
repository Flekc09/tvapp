# Front-End Brief — TV App (Android TV / Google TV)

**Status:** Gate B passed 2026-09-23 · Playbook A complete
**Owner:** Corey Payne · **Source spec:** `docs/superpowers/specs/2026-09-22-tv-app-design.md` (approved 2026-09-22)

Goal line: A household viewer with only a TV remote needs to get a specific live channel playing well in under two seconds, so that turning on the TV feels like clean, fast cable rather than an app, measured by median tune time to first frame on `up` streams over home broadband (target under 2 s; formula and source: spec §1.1 and §5.6, read from Diagnostics; owner: Corey Payne).

Surface class: workflow (daily user, task speed, progressive disclosure) — **written deviation:** the surface is a ten-foot, lean-back TV app driven by a D-pad, not a pointer or touch. The workflow row's psychology emphasis (Hick's, Fitts's, response-time thresholds) applies; its density rule does not. Density here is *low*: one root player surface, overlays over still-playing video, and nothing on screen the viewer did not ask for. Fitts's law is read as "focus travel in D-pad presses", not pointer distance.

Evidence for validated need: owner statement recorded verbatim in spec §1 — "The one task the app is built around: get to a specific channel and have it playing well in under two seconds, using only a remote. The one feeling the app is built around: turning on the TV shows a picture, not a menu." Spec approved by the owner 2026-09-22 and revised after engineering, viewer-psychology and cold-executor reviews 2026-09-23.

Red routes:
1. **Turn on to a picture.** Launch (cold, from standby, or after a crash or CEC power-off) → last channel playing full screen, no menu in the way. Failure branch: no cached catalog and no network → one screen, "Can't reach the channel list. Check your connection." with Retry.
2. **Change channel.** From the player, Up/Down surfs the current list with the banner moving instantly; from Channels, Favorites, Search or Browse, OK on any focused row plays it; a favorite or recent channel is reachable from any surface in three presses or fewer. Failure branch: tune budget exhausted → "This channel isn't working right now" with Try again and Next channel; never a spinner.
3. **Keep watching when a feed dies.** Mid-play death → automatic failover with a one-line "Switching source" notice, no viewer action. Failure branch: all sources exhausted → the route-2 "isn't working" card; the poor-signal prompt offers "Try another source" without taking focus.

Supporting flow (not a red route, but the first thing a new install shows): first launch → progress screen "Loading channels… 4,200 of 10,000. This only happens once." → Channels overlay filtered to the device country, working channels first.

Out of scope (spec §1.2 and §10): program guide data, program-first Home, sports hub, team favorites, profiles, Simple Mode, multiview, rewind or timeshift, DVR, cross-device sync, voice beyond platform search, Google TV launcher rows, Play Store distribution, other platforms. Segment preload is a V1.1 spike. Automatic switching on degradation ships off by default and is not designed in V1. Any visual for a screen not in spec §6 is out of scope for this brief.

Brand: **Direction B, "Google TV glass", approved by Corey Payne 2026-09-23.** Translucent frosted panels over video, cool neutral greys, the platform system font (Roboto on Google TV), soft rounded rows; reads as native to the launcher it sits beside. No logo or icon exists yet. Rules that hold regardless of direction: focus is always scale plus a border, never colour alone (spec §6 display rules); every text/background pair lists a passing 4.5:1 ratio in the Token Sheet before any screen is designed, and because panels are translucent the ratio is measured against the worst-case picture behind them (a white frame), so panel opacity is a token, not a taste.

Rejected directions, recorded so they are not re-proposed: A "Set-top box" (black scrim, amber accent, condensed digits); C "Signal" (flat black and white, green accent, monospace).

> TAILOR (open, decider: Corey Payne): **product name.** Owner chose on 2026-09-23 to keep the working title "TV App" for now. The launcher label, 320 × 180 banner and Diagnostics header stay placeholders until a name is approved; nothing else in the UI depends on it.

Constraints:
- devices · Android TV / Google TV, sideloaded APK, minimum 1 GB RAM. Owner's real hardware is a cast-only Chromecast that cannot run apps; release testing needs an onn Google TV 4K stick or equivalent. Daily development on the "Television (720p)" and "Television (1080p)" emulator profiles; every screen is checked at both before it is called done, then on the largest TV in the house.
- input · **Two classes of keys, and the design must work with the first alone.** The stick's own remote (Chromecast with Google TV, onn 4K) sends D-pad, OK (short, long, double), Back (short, long), Home, Assistant, volume and app buttons: no GUIDE, no digits, no LAST_CHANNEL, no CHANNEL_UP/DOWN, no Menu. A TV's own remote over CEC may additionally send CHANNEL_UP/DOWN, LAST_CHANNEL, GUIDE, INFO, MEDIA_PLAY_PAUSE and digits 0–9; those are accelerators, never the only route. Every rule in this brief (three presses to a favorite, one press to play from a row) is met with D-pad, OK and Back. Voice search only where the remote has a microphone. No touch, no pointer.
- display · One logical canvas, 960 × 540 dp (1.5× on 720p, 2× on 1080p, 4K upscales the 1080p UI). Overscan safe area 48 dp sides, 27 dp top and bottom; video alone fills the full frame. Text: body 16 sp minimum, rows and banner 20–24 sp, headings 28–32 sp, never below 12 sp anywhere. Focus: about 1.1 scale plus a 3 dp high-contrast border. Contrast: WCAG AA 4.5:1 on a scrim over any picture. Video on a SurfaceView, aspect preserved, Fit default with Zoom available. Logos in a fixed 96 × 96 dp box with a placeholder on error.
- network · Home broadband, variable; roughly one stream in five is cleartext HTTP; slow origins exist and no UI fixes them. Catalog sync is background and idle-only, so the UI never waits on it except on first launch.
- language rule · Nothing on screen says HLS, TS, DASH, unverified, unsorted, demoted, or a stream count. Status words are exactly Working, Not checked, Not working.
- regulatory flags · **Minors:** the catalog carries adult channels; they are hidden by default and revealed only behind a 4-digit PIN set on first use (owner decision in spec §6 and §11). No `AGENTS.md` exists in this repo; the tripwire is recorded here and the PIN dialog is a Playbook D form with data-loss and error-copy rules. **Personal data:** none collected; no accounts, no analytics, everything stays on the device. **Payments:** none. **Public claims:** none on any surface.

Stack: Kotlin, Jetpack Compose for TV (`tv-material` 1.1.0, Compose BOM 2026.09), Media3 1.11, Room 2.8.5, Coil for logos — recorded in `docs/superpowers/plans/2026-09-22-tv-app.md` Task 1, versions checked against release pages 2026-09-23, owner sign-off by plan approval 2026-09-23. Rationale: Compose for TV is the platform's supported ten-foot toolkit with built-in focus handling; a single Activity with one player surface is a spec decision (§11, "Player root with overlays"), not a UI preference.

Playbook adaptations for a ten-foot surface (written once here so later playbooks do not re-argue them):
- Playbook E's screen-reader pass runs with Android TalkBack on the emulator; the "200 % zoom + 320 px reflow" pass is replaced by the two emulator profiles plus the display rules above, since the canvas is fixed.
- Playbook F's Core Web Vitals are replaced by spec §1.1 targets: banner within one frame of a keypress, cold launch to first frame under 4 s, tune median under 2 s and p95 under 5 s.
- Playbook C's "hover" state is "same as default" for every component; there is no pointer. The state that matters is focus-visible.
- Playbook G's cross-device check is 720p emulator, 1080p emulator, and the real stick on the largest TV in the house.

## Gate B
- [x] Goal line names one user, one task, one metric with formula, source and owner.
- [x] Evidence for the validated need: owner statement in spec §1, quoted above.
- [x] Surface class assigned with a written deviation; red routes listed; out-of-scope list non-empty.
- [x] Brand TAILOR resolved: direction B approved by owner 2026-09-23. Product name logged as open (owner, 2026-09-23), no name invented.

**Gate B: PASS (2026-09-23).** Next: Playbook B, Screen Inventory & Flow Map (`docs/ui/screens-and-flows.md`), then the Token Sheet before the first screen is designed.
