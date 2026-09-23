# Screen 1 — Player with the banner

**Status:** Approved by owner 2026-09-23 (star after the name, clock per device setting, reserved status row) · Playbook C.4
**Inputs:** `docs/ui/screens-and-flows.md` #1, C1–C3, C5–C7 · `docs/ui/design-tokens.md` · `docs/ui/component-states.md`
**Primary action:** Up / Down changes channel. The screen exists to show a picture; everything drawn on it is temporary and self-dismissing.
**Mockup:** the Design artifact "TV App screens" (https://claude.ai/artifact/19F9iWwZnDqLst7nm6NcBy, private to the owner), page "Player", shows six artboards at 960 × 540 dp with the real tokens: playing over a white frame and over a dark one, tuning, the card, the notices, and empty. Video frames in it are flat stand-ins, not real stills.

## 1. Layout (960 × 540 dp canvas)

Video fills the full frame, aspect preserved (`video`, Fit by default; Zoom from Settings). Pillarbox bars are `color-bg`.

Everything else sits inside the safe area, x 48–912, y 27–513.

| Element | Position (dp) | Size | Tokens |
|---|---|---|---|
| Banner panel | x 48, y 381 (bottom edge at 513) | 864 × 132 | `panel` (no scrim), `radius-md`, `blur` where supported, inner padding `space-4` |
| Logo | x 64, y 399 | `logo-lg` 96 × 96 | `radius-sm` clip; placeholder shows the channel's first letter |
| Name row | x 176, y 399 | up to 480 wide, 32 line (the clock and list position need the rest) | `text-lg` `color-text`, one line, ellipsis; favorite star 32 dp immediately after the name text, `space-2` gap |
| Region row | x 176, y 439 | 20 line | `text-sm` `color-text-muted`: `flag` 24 × 16, then "Baton Rouge, Louisiana · United States"; no region: just flag and country |
| Status row | x 176, y 467 | 20 line, reserved even when empty | `text-sm` `color-text`: measured resolution ("1080p") once known; "Trying source 2 of 5" while alternatives are tried; "Paused" while paused; otherwise empty, height kept so rows never jump |
| Clock | right edge x 896, y 391 | 56 line | `text-2xl` tabular `color-text`, 12-hour with AM/PM per device locale |
| List position | right edge x 896, y 447 | 20 line | `text-sm` `color-text-muted`: "Favorites · 3 of 12" (list name, middle dot, position); counts tabular |
| Previous hint | right edge x 896, y 471 | 20 line | `text-sm` `color-text-muted`: "Previous: SEC Network"; absent when there is no previous channel |
| Toast | centred, y 27 | 40 high, width to text + `space-5` × 2 | `panel`, `radius-sm`, `text-sm` `color-text` |
| Poor-signal prompt | right edge x 912, y 27 | 320 × 72 | `panel`, `radius-md`; "Picture is struggling" `text-md`, "Press OK to try another source" `text-sm` `color-text-muted` with an OK glyph in `color-accent` |

The left column (logo, name, region, status) and the right column (clock, list position, previous) are separated by at least `space-6`; the name row ellipsises before it reaches the clock. The right column is right-aligned so the clock's tabular digits never move.

## 2. The four states

### Empty — no channel to play
Only after an interrupted first launch or a cleared database. The player draws `color-bg` and the root opens the Channels overlay (flow map, red route 1 step 1). No banner. The viewer never sees a black frame with nothing to do.

### Loading — tuning
- The previous picture holds (last frame; `color-bg` on a cold launch) and the banner shows within `t-immediate` of the keypress, before the tune starts (300 ms `debounce-tune`).
- Status row: empty while the first source is tried; "Trying source 2 of 5" from the first skip; the resolution replaces it on first frame.
- Surfing through ten channels: name, region, star, list position and previous hint update on every press; only the final channel tunes.
- No spinner anywhere. The banner's presence is the progress indicator; the status row is the detail.
- `banner-hold` starts at first frame, not at keypress, so a slow origin never leaves the viewer with a bare picture and no name (plan Task 12 arms the hide timer on the first frame, not in `showBanner`).

### Error — the channel isn't working
- Budget exhausted: the last frame stays behind `scrim`; the card (#13) opens centred: `panel-strong` 480 × 200, `radius-md`, `logo-lg` left, "This channel isn't working right now" `text-lg`, two buttons `row-h-dense`: **Next channel** (primary, `color-accent` fill, focused) and Try again (white 12 % fill). The banner hides when the card opens.
- Mid-play failover is not an error state: the toast "Switching source" shows for `banner-hold`, the picture holds, and the banner does not appear (spec §5.5 rule 3 asks for the one line only).
- Poor signal is not an error state: the prompt shows top-right for `prompt-hold`, takes no focus, and Up/Down still surf.

### Populated — playing, banner up
Worst-case data for the mockup and the emulator check:
- Name: "Al Jazeera English Documentary Channel Europe" (45 characters) ellipsised at one line with the star still visible after the ellipsis.
- Region: "Baton Rouge, Louisiana · United States" with the flag.
- List: "United States · Entertainment · 1,204 of 2,731" (four-digit tabular counts).
- Previous: "Previous: SEC Network".
- Resolution: "1080p". Clock: "12:41 PM" (widest 12-hour string).
- Behind the banner: a solid white frame (contrast worst case) in one mockup artboard and a dark sports frame in another.

## 3. Behaviour on this screen (fixed by spec, listed so the design honours it)

| Input | Result on screen |
|---|---|
| Up / Down, CHANNEL_UP/DOWN | banner updates within one frame; tune 300 ms after the last press |
| OK, INFO | banner shows for `banner-hold`; OK while the poor-signal prompt shows accepts it instead |
| Long-press OK | context menu (#2) opens over a `scrim`; banner hides |
| Double OK, LAST_CHANNEL | previous channel; banner shows |
| Left / Right | channel strip (#4) opens along the bottom in place of the banner |
| GUIDE, or Back while the banner shows and no tune is in flight | Channels overlay; the picture animates to the inset over `t-move` (flow map D11: during a tune Back cancels the tune instead) |
| Back with no banner | toast "Press Back again to exit" for `exit-arm`; second press exits |
| Play/Pause | picture holds and mutes; status row "Paused"; on resume toast "Back to live" |
| Digit 1–9 | favorite by number, banner shows; out of range: banner of the current channel (D4) |
| Any key during a tune | Back cancels the tune and keeps the last picture; a channel key starts the next tune |
| Hold Back | Favorites: Channels opens on the Favorites collection with favorite 1 focused (flow map D10) |
| On the card (#13) | OK presses the focused button (Next channel by default); Left/Right move between the two; Up/Down surf away and the card closes; long-press OK opens the context menu; Back closes the card and shows the banner with "Not working" in the status row |

## 4. Ten-foot checks

- Banner text at `text-lg` and `text-sm` on `surface-banner`: 9.40:1 and 6.21:1 against a white frame; over a dark picture, higher.
- Nothing inside the outer 5 %: the banner's bottom edge is at 513 dp and the toast's top at 27 dp.
- Focus: none on this screen while only the banner shows (the banner is passive); the card and prompt follow the matrix.
- Emulator: check at 720p and 1080p with the populated worst case, then a 4:3 channel to see the pillarbox behind the banner.

## 5. Owner decisions 2026-09-23

1. Clock format follows the device's 12/24-hour setting.
2. The star sits after the name so it is read with the name.
3. The status row keeps its height when empty so the banner never jumps.

Blur is on where the device supports it; on the emulator it may render as a plain panel, and every contrast ratio assumes no blur.
