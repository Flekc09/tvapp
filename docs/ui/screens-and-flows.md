# Screen Inventory & Flow Map — TV App

**Status:** Gate F passed 2026-09-23 · Playbook B complete
**Inputs:** `docs/ui/frontend-brief.md` (Gate B passed 2026-09-23), spec §5.1, §5.4–5.6, §6, §7; app plan Tasks 12–17.
**Rule of this document:** it inventories what the spec fixes and names one primary action per screen. It does not decide look, layout or copy beyond what the spec already states; those come screen by screen in Playbook C.

## 1. Navigation model

Spec §6 and §11 fix the model: **the player is the root and is always underneath; every other surface is an overlay pushed onto a stack over the still-playing, dimmed or inset video; Back pops the top overlay; audio never stops for navigation.** Playbook B.2's decision table (side nav, tab bar, breadcrumbs) is written for pointer and touch products and none of its rows fits a ten-foot D-pad surface, so the choice is recorded here with its evidence instead:

- The category's top products (cable and satellite boxes, Android TV's Live Channels app, YouTube TV, Pluto TV on TV) all keep video playing under a guide or menu. Jakob's law says follow that.
- Persistent visible navigation would cost picture area on every screen for destinations a viewer uses once a week.
- A D-pad has exactly one "escape" key, Back, so a stack with a single pop rule is the only model the viewer can predict without looking.

Top-level destinations reachable from the player: Channels, Browse, Search, Favorites, Settings. Spec §6 does not say how the viewer reaches Browse, Search, Favorites and Settings from the player: it maps GUIDE (and Back from the banner) to Channels, and long-press OK to a three-item context menu (Sources, favorite toggle, Previous channel). **Owner decision 2026-09-23:** the Channels overlay's collection column carries Browse, Search and Settings as its last three entries; the context menu stays at three items. Channels is the one door to everything, which is how a cable guide works. Recorded as deviation D1 below.

## 2. Keys, per surface (the one table the router implements)

The stick remote sends D-pad, OK, Back, Home and app keys; CEC keys (CHANNEL_UP/DOWN, LAST_CHANNEL, GUIDE, INFO, MEDIA_PLAY_PAUSE, digits) may arrive from a TV remote and are accelerators. Rows are "what the app does"; "Compose" means the focused element handles it.

| Surface (`Overlay`) | Up / Down | Left / Right | OK | Hold OK | Double OK | Back | Hold Back | CHANNEL ± / digits / LAST | GUIDE | Play/Pause |
|---|---|---|---|---|---|---|---|---|---|---|
| Player (`NONE`) | surf | open strip | banner; accepts the poor-signal prompt if showing | context menu | previous | banner up and no tune in flight: Channels; tune in flight: cancel it; else arm exit | Favorites | surf / favorite N / previous | Channels | pause / rejoin |
| Card (`NOT_WORKING`) | surf (card closes) | Compose: move between the two buttons | Compose: press the focused button | context menu | — | close the card, banner shows "Not working" | Favorites | surf / favorite N / previous | Channels | pause / rejoin |
| Strip (`STRIP`) | close the strip, then surf | Compose: move the card focus | Compose: play the card | — | — | close the strip | Favorites | close, then surf / favorite N / previous | Channels | pause / rejoin |
| Context menu, Sources (`CONTEXT_MENU`, `SOURCES`) | Compose | — (nothing) | Compose | — | — | pop one level (Sources → context menu → player with the banner) | Favorites | close the panel, then surf / favorite N / previous | Channels | pause / rejoin |
| Channels, Browse, Search, Favorites, Settings, Advanced, Diagnostics, dialogs | Compose | Compose | Compose | Compose (long-click where the screen defines one) | — | pop one level; Search with the keyboard open: close the keyboard first | Favorites | CHANNEL ±: ignored; digits: Compose (jump to a letter in Channels, typed in a text field, else nothing); LAST: previous (owner decision 2026-09-24, Opus review minor 4) | Channels | pause / rejoin |

Rules that follow: the strip auto-closes 5 s after the last key (`AppViewModel` owns the timer); digits type while a text field has focus; hold Back never also pops; exactly one focus ring exists on any surface. Section 7 of `docs/ui/screens/06-context-menu-and-sources.md` and §7 of `08-dialogs-and-strip.md` defer to this table.

**Finding F1 for the plan (resolved 2026-09-23):** the router expresses this table; Task 12's `KeyRouterTest` covers hold-Back on every overlay and the card's keys, and Task 17 tests the three-press route from Diagnostics with D-pad, OK and Back only.

## 3. Screen inventory

Surface-count discipline: a "screen" is anything that takes focus. Banners, toasts, prompts and the preview inset are components of the screen underneath and are listed in §4, not here.

| # | Screen | Reached by | Primary action (one) | Empty | Loading | Error | Populated (worst case) | Forms | Red routes |
|---|---|---|---|---|---|---|---|---|---|
| 1 | **Player** (root) | app launch; Back from any overlay | Up/Down: change channel | No channel yet (fresh state after an interrupted first launch): open Channels instead of a black screen | Tuning: last frame held, banner shows the target channel within one frame, "Trying source 2 of 5" line after the first stream is skipped; no spinner | Tune budget exhausted: the "Isn't working" card (#13) | Full-frame video, 4:3 pillarboxed, banner over it with a 47-character channel name, region, flag, clock, star, "Favorites 12 / 12", resolution, previous-channel hint | none | 1, 2, 3 |
| 2 | **Context menu** | long-press OK on the player | Open Sources (first item) | n/a: three fixed items | n/a: local | n/a | Sources · Remove from favorites · Previous: SEC Network (longest item label) | none | 3 |
| 3 | **Sources** | Context menu → Sources | OK: play the focused source | n/a: every channel has at least one stream (spec §4.2) | n/a: list is local; the pick itself shows "Trying source" on the player | Manual pick fails: "Trying source" line, normal queue resumes from its top (spec §5.5 rule 10) | 30 rows, "Source 30 · 1080p · Not working", current source marked | none | 3 |
| 4 | **Channel strip** | Left/Right on the player | OK: play the focused channel | Current list empty (Favorites or Recent with nothing in them): one line teaching the fix, "No favorites yet. Hold OK on any channel to add it." | n/a: local | n/a | All: 10,000 channels, strip scrolls, number shown only for Favorites | none | 2 |
| 5 | **Channels** (main overlay) | GUIDE; Back from the banner; Browse → pick | OK: play the focused channel full screen (the collection column's Browse, Search and Settings entries open those overlays; they are navigation, not the primary action) | Filter yields nothing (empty Favorites or Recent, a country whose channels are all hidden): teach the fix and offer the collection column | Preview inset switching after 700 ms dwell: last frame held until the new stream's first frame (component #C4) | Catalog missing entirely: the No-catalog screen (#15) replaces this | All = 10,000 rows; 33 rows named "ABC" with different regions; a channel with no logo (placeholder); a row with status Not working shown only when the setting is on | none | 2 |
| 6 | **Browse: countries** | Channels collection column → Browse | OK: open that country's categories | n/a: catalog always has countries | n/a: local counts | n/a | 177 rows, device country pinned first, longest name "United Kingdom of Great Britain and Northern Ireland" if the catalog carries it, counts up to 4 digits | none | 2 |
| 7 | **Browse: categories** | Browse: countries → OK | OK: open Channels filtered to that category | n/a by construction: counts use the same filters as lists, so a listed country always has a visible channel (amended 2026-09-23) | n/a | n/a | ~20 categories with counts, "Other" last, "Country: All" chip | none | 2 |
| 8 | **Search** | Channels collection column → Search | OK on a result: play it | No query: empty results area with the prompt to type or speak; No matches: "No channels match 'xyz'" (the count line comes from `searchCount`, so "1,204 results" is real even though the list shows the first 200) | Results update ≤ 300 ms after typing (250 ms debounce): no spinner, layout stable | Voice not available: mic button hidden, never a failing button | Query "a": thousands of results, each with flag, region, first category, status; a result whose alternate name matched but whose display name does not contain the query | Text field (platform keyboard) | 2 |
| 9 | **Favorites** | Channels collection column → Favorites; digits (player) | OK: play the focused favorite | No favorites: teach "Hold OK on any channel to add it", offer Channels | n/a | n/a | 50 favorites numbered 1–50, two rows "no longer available" keeping their numbers, one with a 47-character name | none | 2 |
| 10 | **Favorite row menu** | long-press OK on a Favorites row | Move (up or down; Remove is last and visually distant) | n/a: three fixed items | n/a | Move at the top/bottom: item disabled with the reason visible ("Already first") | Move up · Move down · Remove | none | — |
| 11 | **Settings** | Channels collection column → Settings | Change startup behavior (first row) | n/a: fixed rows | n/a | n/a | Startup: Last channel / Favorite: <47-char name> / Channels · Sleep timer: Off / 30 / 60 / 90 · Show channels that don't work here · Show adult channels · Advanced (hold OK) | Startup picker, sleep timer picker, two toggles | — |
| 12 | **PIN dialog** | Settings → Show adult channels | Enter four digits (auto-submits on the fourth) | First use: "Set a 4-digit PIN" then "Enter it again" | n/a | Wrong PIN: "That's not the PIN. Try again." field cleared, toggle stays off; mismatch on set: "The PINs didn't match. Start again." | four filled dots | 4-digit field | — |
| 13 | **"Isn't working" card** (`Overlay.NOT_WORKING`, an overlay so its buttons take focus) | tune budget exhausted on the player | Next channel (default focus) | n/a | n/a | n/a: this is the error state of #1 | "This channel isn't working right now" · Next channel · Try again | none | 2, 3 |
| 14 | **Advanced** | Settings → hold OK on Advanced | Refresh channel list now (the one action a viewer takes here) | n/a: fixed rows | Refresh in progress: progress bar by bytes with the running count, allowed while playing | Refresh fails: "Couldn't download the channel list. Your current list is unchanged." with Retry | Channel list address ellipsised from the start · Refresh · Version 2026-09-23 · two toggles · Diagnostics | the address dialog's text field | — |
| 16 | **Diagnostics** | Advanced → Diagnostics | Back (read-only screen; the one action is leaving) | Nothing playing: rows show "—" | n/a: values are live | n/a | Channel id, 200-character stream URL wrapped, format, resolution, bitrate, buffer ms, last tune ms, app version; all ≥ 12 sp | none | — |
| 17 | **First-launch progress** | first ever launch | Wait (no input); Retry appears only on failure | n/a: this is the loading state of the app | "Loading channels… 4,200 of 10,000. This only happens once." with a real progress bar, never indeterminate | Download or import fails: becomes the No-catalog screen (#18) | count at 10,000 of 10,000 | none | 1 |
| 18 | **No-catalog screen** | launch with no cached catalog and no network; first-launch failure | Retry | n/a | Retrying: button shows progress in place | Retry fails again: same screen, same copy, no stacking of errors | "Can't reach the channel list. Check your connection." · Retry | none | 1 |

Amendment 2026-09-23 (Browse design, `docs/ui/screens/03-browse.md`): #6 and #7 are one overlay with two panels; the categories panel fills in as focus rests on a country, and an "All categories" row is the route to a non-pinned country's full list. The primary actions stand.

Screen #15 (Add source) was removed 2026-09-23 with the user-playlist feature; numbering is kept.

Screens with primary action "Back" or "Wait" (#16, #17) are read-only or blocking by spec; Gate F's "exactly one primary action" is satisfied because there is exactly one thing the viewer can do.

## 4. Components (no focus of their own)

| # | Component | Lives on | Behavior fixed by spec |
|---|---|---|---|
| C1 | Banner | Player | within one frame of any channel change and on OK; auto-hides 3 s after video is up; contents in §3 row 1 |
| C2 | "Trying source N of M" line | Player | while alternatives are being tried during a tune |
| C3 | "Switching source" toast | Player | mid-play failover; last frame held until the next first frame |
| C4 | Live preview inset | Channels | the single player surface animated to a corner; tunes to the highlighted channel after 700 ms dwell; moving the highlight cancels |
| C5 | Poor-signal prompt | Player | offers "Try another source"; takes no focus; only OK accepts; auto-dismisses in 8 s; not repeated within 10 min on the same channel |
| C6 | "Press Back again to exit" | Player | armed for 2 s on Back from the bare player |
| C7 | "Back to live" note | Player | one line on rejoining after pause |
| C8 | "No longer available" badge | Favorites, Channels | favorite whose channel left the catalog; keeps its number |
| C9 | Status word | every channel row, Sources | exactly Working / Not checked / Not working, with a mark that is not colour alone |
| C10 | Sleep-timer fired | Player → Channels | player paused and released, Channels overlay shown |

## 5. Red routes

Format: step → screen → user action → system response → **failure branch**.

**Red route 1: Turn on to a picture**
1. → Launcher → viewer opens the app (or CEC power-on relaunches it) → Player shows, banner for the last channel within one frame, tune starts → **no last channel recorded** → Player opens the Channels overlay filtered to the device country, working channels first (same as after first launch).
2. → Player → nothing → first frame under 4 s cold with a cached catalog; banner auto-hides 3 s later → **cached catalog missing and no network** → No-catalog screen (#18) with Retry; **catalog present but tune fails** → red route 3 from step 3.
3. → Player (standby resume) → nothing → the current channel is re-tuned to the live edge → **tune fails** → red route 3 from step 3.
4. First install only: → First-launch progress (#17) → viewer waits → count rises to completion → Channels overlay opens → **download or import fails** → No-catalog screen (#18); **import interrupted by standby** → next launch deletes the partial rows and shows #17 again from zero.

**Red route 2: Change channel**
A. Surf from the player
1. → Player → Up or Down (or CHANNEL_UP/DOWN) → banner moves to the next channel in the current list instantly; tune starts 300 ms after the last press → **list empty** → nothing moves; the banner shows the current channel with its list name so the viewer sees why.
2. → Player → viewer holds Down through ten channels → the banner shows every intermediate channel; only the final one tunes; abandoned tunes record nothing → **final tune exhausts its budget** → "Isn't working" card (#13).
B. Pick from a list
1. → Player → GUIDE, or OK then Back → Channels (#5) opens with the remembered filter; video moves to the inset without a blink; the current channel row is focused → **filter's list is empty** → Channels empty state with the collection column focused.
2. → Channels → D-pad to a row → playlist prefetch on focus; after 700 ms the inset tunes to that channel → **preview tune fails** → the inset holds the last frame and the row's status word is what the viewer sees; OK still attempts a full tune.
3. → Channels → OK → overlay closes, that channel plays full screen → **tune fails** → card (#13).
   Back at any step keeps whatever is playing, previewed or not.
C. By number
1. → Player → digit N → favorite N plays, banner within one frame → **N greater than the favorites count** → the banner for the current channel shows for 3 s and nothing else changes (owner decision 2026-09-23, deviation D4; the plan is updated to match).
D. Previous channel
1. → Player → double-press OK or LAST_CHANNEL → the previous channel plays → **no previous channel** → nothing changes; the banner's "Previous:" hint is absent so the viewer had no reason to expect one.
E. Three-press check from the deepest surfaces, with the stick remote only (D-pad, OK, Back)
- From anywhere, Diagnostics and Search-with-keyboard included: hold Back → Channels on Favorites, favorite 1 focused → OK = 2 presses; favorite 2 = 3 presses (Down, OK). Favorite N is N + 1 presses, and a CEC digit key makes it 1 where a TV remote has one.
- From the player: double-press OK = the previous (recent) channel, 1 gesture.
- From Favorites: OK on the focused row = 1.
- The rule is therefore met for favorites 1 and 2 from every surface and for the previous channel from the player, which is the honest reading of "a favorite or recent channel in three presses"; the spec's digit route stays as the accelerator.

**Red route 3: Keep watching when a feed dies**
1. → Player (playing) → nothing; the stream dies → "Switching source" toast, the last frame holds, the next source is tried with a fresh 10 s budget; rejoin at the live edge → **no untried source left** → sources that worked and then died are retried after a 2 s gap; **budget exhausted** → card (#13).
2. → Card (#13) → OK on Next channel (default focus) → next channel in the current list tunes → **also fails** → card again; after 3 consecutive failing channels within a minute, failures are no longer recorded (outage guard), the viewer sees the same card.
3. → Card (#13) → Try again → fresh queue for the same channel → **fails** → card.
4. Degraded rather than dead: → Player → prompt C5 appears after 3 rebuffers in a minute or 30 s at low resolution → OK → next source plays with the "Switching source" toast → **viewer ignores it** → gone in 8 s, not repeated for 10 min; Up and Down still surf throughout.
5. Manual: → Player → long-press OK → Context menu → Sources → OK on a source → it plays; automatic failover is suppressed for that attempt → **it fails** → "Trying source" line, normal queue resumes from the top.

## 6. Three-click discipline (B.4)

No red route needs more than two screens. Route 2B (Player → Channels → Player) is the longest and each screen earns its place: Channels is where the choice is made and the player is the result. Browse adds two screens (countries, categories) before Channels, which is why Browse is not on a red route and why the device country is pinned at the top and the filter is remembered: the common case never passes through Browse twice.

## 7. Convention deviations (B.5), each with its reason

| # | Deviation | Reason |
|---|---|---|
| D1 | Browse, Search and Settings have no dedicated remote key and are reached through the Channels overlay's collection column (owner decision 2026-09-23) | Spec §6 gives the player only two ways out, GUIDE and long-press OK. Growing the context menu past three items taxes every long-press with choices used weekly (Hick's law); a guide with a side column is the pattern cable boxes and Live Channels use. |
| D2 | Back from the banner opens Channels instead of dismissing the banner | Spec §6 mapping. Remotes without a GUIDE key (the onn stick's has none) need a one-press route to the guide, and the banner dismisses itself in 3 s anyway. |
| D3 | Double-press OK means previous channel | Spec §6. Consumer remotes lack a LAST key; the gesture is the cheapest one that cannot fire by accident during a single OK. |
| D4 | A digit greater than the favorites count produces no visible response | Spec and plan Review Focus 5 ("must do nothing visible, not crash or tune channel 0"). That would break the response-time rule that every input is acknowledged, so the owner decided 2026-09-23: the banner for the current channel shows for 3 s, which is "nothing changes" and still an acknowledgement. Task 12's `favoriteByNumber` and the Task 17 test now say "banner only". |
| D5 | Digits mean favorite number on the player and jump-by-letter in Channels | Spec §6 gives both. The two contexts never overlap on screen, and the strip shows favorite numbers only in the Favorites list so the number affordance is present exactly where it works. |
| D6 | Advanced is entered by holding OK, not by a visible row press | Spec §6. Progressive disclosure: Settings stays at four viewer-facing rows; Advanced holds the catalog URL and M3U sources, where a mis-press costs the channel list. The row is visible and says "hold OK", so nothing is hidden, only guarded. |
| D7 | Startup goes to a playing channel, never a home screen | Spec §11 decision. Cable convention; the brief's one feeling. |
| D8 | No rewind, pause holds a still picture | Spec §11 decision (rejected 60-second timeshift). The viewer sees "Back to live" on resume so the jump is explained. |
| D11 | Back while a tune is in flight cancels the tune, even though the banner is visible and "Back from the banner opens Channels" | Spec §5.5 ("Back cancels the tune instantly", "cancellable instantly" in §1.1) and spec §6 (Back from the banner → Channels) conflict for the 0.3 to 10 s a tune takes; the banner is up for that whole time. Cancel wins because it is the safety valve on a dead channel; Channels is one more press away once the tune is cancelled or done. Owner confirmed 2026-09-23 (adversarial review, major 6). |
| D12 | The text field and the PIN keypad group take focus with a ring and tint but no scale or grow | A scaled or grown field shifts the system keyboard and its caret; the PIN rings are passive and the keypad keys scale individually, so the focus rule (spec §6) is met by the element the viewer is actually operating. |
| D10 | Holding Back opens Favorites from every surface, focus on favorite 1 | The Chromecast and onn remotes send only D-pad, OK, Back, Home and app keys. Without a favorites gesture the three-press rule fails from any deep surface (adversarial review 2026-09-23, blocker 1). Long-press Back is the only unassigned gesture those remotes have; it is taught by a "Hold Back · Favorites" hint in every details panel. A short Back still pops one level. |
| D9 | The "Isn't working" card focuses Next channel first, Try again second (owner decision 2026-09-23) | Try again re-runs a queue that just spent 10 s failing; the viewer's likely goal is to watch something. Both stay one press away. The spec lists them in the other order without fixing focus. |

## 8. Gate F

- [x] Every screen has exactly one primary action (§3; #16 and #17 explained under the table).
- [x] Every red-route step has a failure branch (§5, bold at each step).
- [x] Every convention deviation has a written reason (§7).
- [x] Owner confirmed D1, D4 and D9 on 2026-09-23. D1 and D4 are written into the app plan (Tasks 12, 13, 17 and Review Focus 5); finding F1 is added to Task 17.

**Gate F: PASS (2026-09-23).**

Next: Playbook C, Design Token Sheet (`docs/ui/design-tokens.md`) for direction B, then the first screen: Player with the banner.
