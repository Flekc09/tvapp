# Design Token Sheet — TV App, direction B "Google TV glass"

**Status:** Approved by owner 2026-09-23 (taste values signed off; Gate S token items pass) · Playbook C.1
**Inputs:** `docs/ui/frontend-brief.md` (direction B approved 2026-09-23), spec §6 display rules, `docs/ui/screens-and-flows.md`.
**Rule of this sheet:** screen specs name tokens, never raw values. Every text/background pair below lists a contrast ratio computed by `docs/ui/tools/contrast.py` against the **worst case behind a translucent panel: a solid white frame of video**. Change a value, re-run the script, paste the numbers. Zero FAIL lines is the condition for using this sheet.

## 1. Canvas and safe area (fixed by spec §6)

| Token | Value | Use |
|---|---|---|
| `canvas` | 960 × 540 dp | the one logical layout; 1.5× on 720p, 2× on 1080p, 4K upscales |
| `safe-x` | 48 dp | left and right inset for anything interactive or informational |
| `safe-y` | 27 dp | top and bottom inset, same rule |
| `video` | full frame | video ignores the safe area; nothing else does |

## 2. Type scale

Face: **Roboto**, the Google TV system font, loaded as `FontFamily.Default`; no bundled font, no fallback stack needed. Numbers that change while the viewer looks at them (clock, channel number, "3 of 12", counts, Diagnostics values) use tabular figures (`FontFeature "tnum"`) so they do not jitter.

One ramp, six sizes, base 16 sp. Line height is 1.25 × size, rounded to a 4 dp multiple.

| Token | Size / line | Weight | Use | Spec floor |
|---|---|---|---|---|
| `text-xs` | 12 / 16 sp | Regular 400 | Diagnostics values only; nowhere else | never below 12 sp |
| `text-sm` | 16 / 20 sp | Regular 400 | body: settings descriptions, hints, toasts, status words, "Previous:" hint | body ≥ 16 sp |
| `text-md` | 20 / 24 sp | Medium 500 | list rows (channel name), menu items, settings rows, search results | rows 20–24 sp |
| `text-lg` | 24 / 32 sp | Medium 500 | banner channel name, dialog body, card title | banner 20–24 sp |
| `text-xl` | 32 / 40 sp | Medium 500 | overlay headings ("Channels", "Settings"), collection column title | headings 28–32 sp |
| `text-2xl` | 48 / 56 sp (the one size whose line height is not 1.25 ×; 56 keeps the banner's right column inside 100 dp) | Regular 400, tabular | clock in the banner, first-launch count | — |

Text rules: sentence case everywhere; labels front-loaded ("Show adult channels", not "Adult channel visibility"); the language rule from the brief (no HLS, TS, DASH, unverified, unsorted, demoted, stream count; status words exactly Working, Not checked, Not working; positions within a tune and the Diagnostics format row are allowed, see spec §6). Truncate with an ellipsis at one line for rows, two lines for banner names and dialog bodies; never shrink text to fit.

## 3. Spacing, sizing, shape

Base unit 4 dp; multiples only.

| Token | Value | Use |
|---|---|---|
| `space-1` … `space-8` | 4 · 8 · 12 · 16 · 24 · 32 · 48 · 64 dp | the only spacings allowed |
| `row-h` | 64 dp | channel row, menu item, settings row (logo-sm + `space-2` above and below) |
| `row-h-dense` | 48 dp | collection column entries, Sources rows, Diagnostics rows |
| `logo-sm` | 48 × 48 dp | logo in rows and the strip; the image is always requested at `logo-lg` and scaled (spec §6) |
| `logo-lg` | 96 × 96 dp | logo in the banner and the "isn't working" card |
| `flag` | 24 × 16 dp | country flag, always beside text, never alone |
| `panel-w` | 400 dp | width of the context menu and Sources (the bottom-left player panel) |
| `list-w` | 320 dp | the Channels list panel (widened column after the review: 224 + 16 + 320 + 16 + 288 = 864) |
| `column-w` | 224 dp | Channels collection column, on its own `panel`; wide enough for "United States · 2,731" at `text-md` + `text-sm` without ellipsis |
| `panel-wide` | 560 dp | the single left panel of Search, Settings, Advanced, Diagnostics |
| `panel-half` | 272 dp | each of Browse's two panels |
| `dialog-w` | 480 dp | every dialog and the "isn't working" card; `dialog-h-pin` 320, `dialog-h-text` 236, `dialog-h-card` 200 |
| `card` | 136 × 84 dp | a channel card in the strip |
| `strip-h` | 108 dp | the strip panel |
| `key` | 56 × 48 dp | a PIN keypad key |
| `prompt` | 320 × 72 dp | the poor-signal prompt |
| `label-w` | 160 dp | the label column in Diagnostics |
| `detail-w` | 288 dp | the details panel under the inset on Channels; same width as the inset |
| `inset-w` | 288 × 162 dp | live preview inset, top-right, inside the safe area. Settled in the Channels screen design 2026-09-23: `column-w` 224 + `list-w` 320 + `inset-w` 288 + two `space-4` gaps = 864 dp, the whole safe width. The plan's 480 × 270 could not fit beside the two columns; the plan is updated. On a 65-inch panel this inset is about 50 cm wide. |
| `banner-h` | 132 dp | banner panel height at the bottom of the frame, inside `safe-y` |
| `radius-sm` | 8 dp | rows, chips, buttons |
| `radius-md` | 16 dp | panels, the banner, dialogs, the inset |
| `radius-full` | 999 dp | status marks, the PIN dots |
| `border-focus` | 3 dp | focus ring (spec §6) |
| `border-hair` | 1 dp | dividers, at `color-line` |

## 4. Layers and opacity

Direction B is translucent panels over video. Opacity is therefore a contrast decision, not a taste, and these are the only alphas allowed.

| Token | Value | Use | Composite over a white frame |
|---|---|---|---|
| `scrim` | black at 45 % | full-frame dim under every overlay that has focusable rows (Channels, Browse, Search, Favorites, Settings, Advanced, Diagnostics, Sources, context menu, dialogs, the card); the preview inset is cut out of it. **Never under the banner, the toasts or the strip**: the picture stays undimmed there. **No text ever sits on the scrim alone**: over a white frame the scrim gives `#8C8C8C`, on which `color-text` is 3.08:1 and fails; every text element sits on `panel` or `panel-strong` (adversarial review 2026-09-23, major 4; `contrast.py` prints the scrim-only surface as a reminder). | `#8C8C8C`, text forbidden |
| `panel` | `#1B2027` at 85 % | banner, toasts, the strip, list panels, menus | `#3D4147` alone; `#2C3036` with scrim |
| `panel-strong` | `#1B2027` at 95 % | dialogs: PIN, "isn't working" card, No-catalog screen, channel list address | `#21252C` with scrim |
| `blur` | 24 dp | backdrop blur behind `panel` and `panel-strong` where the device supports `RenderEffect` (API 31+); on older devices the panel alone carries the look, and every ratio below already assumes no blur |
| `focus-tint` | white at 10 % | background of the focused row, on top of the row's panel | `#41454A` on an overlay |
| `track` | white at 24 % | progress track, slider track | — |

Surfaces used by the ratios below:
- `surface-banner` = `panel` over the picture, no scrim → `#3D4147` worst case
- `surface-overlay` = `scrim` + `panel` → `#2C3036`
- `surface-dialog` = `scrim` + `panel-strong` → `#21252C`
- `surface-focused` = `focus-tint` over `surface-overlay` → `#41454A`

## 5. Colour

Cool neutral greys, one blue accent, three status hues used only for marks. **Colour never carries meaning alone** (spec §6, WCAG 1.4.1): every status has a word, every focus has scale and a border, the favorite star has a filled/outline shape.

| Token | Value | Use | Contrast pair + ratio (worst case, white frame behind) |
|---|---|---|---|
| `color-bg` | `#000000` | window background before video, letterbox bars | — (video area) |
| `color-panel` | `#1B2027` | base of `panel` and `panel-strong` | see §4 |
| `color-text` | `#F3F5F8` | all primary text; status words; disabled labels (see rule below) | on `surface-banner` 9.40 ✓ · `surface-overlay` 12.15 ✓ · `surface-dialog` 14.08 ✓ · `surface-focused` 8.84 ✓ |
| `color-text-muted` | `#C3CAD3` | secondary text: region, category, "3 of 12", hints, descriptions | banner 6.21 ✓ · overlay 8.03 ✓ · dialog 9.31 ✓ · focused 5.84 ✓ |
| `color-accent` | `#A8C7FA` | favorite star (filled), progress fill, the selected collection entry's label, the current source's label, link-like affordances | banner 5.97 ✓ · overlay 7.72 ✓ · dialog 8.94 ✓ · focused 5.62 ✓ · fill vs `track` 3.57 ✓ (non-text floor 3) |
| `color-focus-border` | `#FFFFFF` | the 3 dp focus ring | banner 10.27 ✓ · overlay 13.26 ✓ · dialog 15.38 ✓ (non-text floor 3) |
| `color-line` | white at 12 % | hairline dividers | decorative, no floor |
| `mark-ok` | `#7FD1A0` | ● beside "Working" | banner 5.64 ✓ · overlay 7.28 ✓ · dialog 8.44 ✓ (non-text floor 3) |
| `mark-warn` | `#F5CF6B` | ◐ beside "Not checked" | banner 6.85 ✓ · overlay 8.86 ✓ · dialog 10.27 ✓ |
| `mark-bad` | `#F49A9A` | ○ beside "Not working" and "no longer available" | banner 4.86 ✓ · overlay 6.28 ✓ · dialog 7.28 ✓ |

Rules that follow from the numbers:
- **Status words are always `color-text`, never tinted.** The mark carries the hue, the word carries the meaning, and each mark has its own shape (filled, half, hollow) so the three are distinct in greyscale.
- **Disabled items keep `color-text` for the label and show a reason in `color-text-muted`** ("Already first", "Already last"). A dimmer disabled grey was tested (`#A5ADB8`: 4.26:1 on a focused row) and fails, so it is not a token.
- **No text is ever drawn on bare video.** Even the one-line toasts sit on `panel`.
- Over a black frame every ratio is higher (text 15.82:1 on overlay), so the white-frame numbers above are the only ones that need checking.

## 6. Focus

| Token | Value |
|---|---|
| `focus-scale` | 1.10 for compact targets (cards, keypad keys, buttons, chips: anything up to 160 dp wide) |
| `focus-grow` | 8 dp per side for wide rows (list rows, column entries, menu items, settings rows): the row's padding animates outward by `space-2` instead of scaling, so a 528 dp row grows 8 dp, not 26 dp. With the 3 dp ring that is 11 dp, inside every panel's 16 dp padding and never inside the overscan area (adversarial review 2026-09-23, major 10) |
| `focus-border` | 3 dp `color-focus-border`, outside the row's shape at `radius-sm` |
| `focus-tint` | white at 10 % (see §4) |
| `focus-duration` | 150 ms, standard easing |

Focus is scale (or grow, for wide rows) + border + tint, all three, always; a focused element is recognisable with the picture behind it pure white, pure black, or a still of the same grey as the panel. Focus is never lost when a list updates: the focused row's key is restored, or the nearest row if it is gone.

## 7. Motion and timing

| Token | Value | Rule |
|---|---|---|
| `t-immediate` | 0 ms | banner appears within one frame of a keypress; pressed state on any focused element |
| `t-fast` | 150 ms | focus move, tint change |
| `t-normal` | 200 ms | banner and toast fade-out, overlay fade-in over the scrim |
| `t-move` | 250 ms | the single player surface animating between full frame and the inset, and back |
| `banner-hold` | 3 000 ms | banner auto-hide after video is up (spec) |
| `prompt-hold` | 8 000 ms | poor-signal prompt (spec) |
| `exit-arm` | 2 000 ms | "Press Back again to exit" (spec) |
| `debounce-tune` | 300 ms | after the last surf press (spec) |
| `dwell-preview` | 700 ms | before the inset tunes to the highlighted row (spec) |

Every animation reads the system animator duration scale, so a viewer who turned animations off in Android accessibility settings gets cuts instead of fades. Nothing loops, nothing auto-plays except video.

## 8. Iconography

Material Symbols Rounded (bundled with Compose Material icons) at 24 dp in rows and 32 dp in the banner, tinted `color-text` or `color-text-muted`, never a status hue except the three marks above. Icons always sit beside a label; the only icon-only element is the favorite star, whose meaning is carried by filled versus outline shape and by the row's label reading "Favorite" in the context menu.

## 9. Compose mapping (for the restyle after plan Task 12)

Tokens live in one file, `app/src/main/java/com/tvapp/ui/theme/Tokens.kt`, as `object Tokens` with `Dp`, `TextUnit`, `Color` and `Long` constants named exactly as above (`Tokens.spaceFour`, `Tokens.textMd`, `Tokens.colorTextMuted`), plus a `TvAppTheme` that feeds `color-text`, `color-panel` and `color-accent` into `tv-material`'s `ColorScheme` and the type ramp into its `Typography`. Screen code references `Tokens` and `MaterialTheme`; a raw `Color(0x…)`, `.dp` or `.sp` literal in a screen file fails review. This file is not in the app plan (the plan leaves look to the owner); it is created in the first restyle commit after Task 12 and its creation is noted in the plan's Task 12 at that time.

## 10. Gate S token items

- [x] Token Sheet exists; type ramp of six sizes on one base; spacing on one 4 dp unit, multiples only.
- [x] Every text/background pair lists a passing ratio, computed against the worst-case picture, by a checked-in script.
- [x] Every non-text indicator (marks, focus ring, progress fill) lists a passing 3:1 ratio.
- [x] Owner sign-off 2026-09-23 of the four values that are taste rather than spec: panel base `#1B2027`, accent `#A8C7FA`, `radius-md` 16 dp, `blur` 24 dp. Everything else is derived from spec §6 or from the contrast floors.
- [x] `blur` (accepted as a verify-or-drop item): Compose can blur a composable's own content but not a `SurfaceView` behind it, so the frosted effect may not render over video at all (review minor 17). Verified on the Task 12 build; if it cannot, the token is dropped and nothing else changes, since every ratio already assumes no blur.

Next: Component State Matrix (`docs/ui/component-states.md`), then the first screen, Player with the banner.
