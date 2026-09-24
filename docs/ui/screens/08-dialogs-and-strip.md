# Screen 8 — Dialogs, startup screens and the channel strip

**Status:** Approved by owner 2026-09-23 (all five decisions in §10) · Playbook C.4
**Inputs:** `docs/ui/screens-and-flows.md` #4, #12, #15, #17, #18 · `docs/ui/component-states.md` (PIN field, text field, button, progress bar, channel row) · spec §6, §7 · plan Tasks 15, 16
**Mockup:** the Design artifact "TV App screens" (https://claude.ai/artifact/19F9iWwZnDqLst7nm6NcBy), page "Dialogs & strip", seven artboards.

## 1. The dialog pattern

One pattern for the three dialogs: `panel-strong` 480 dp wide, `radius-md`, padding `space-5`, over `scrim`, the picture (or inset) still behind. Title `text-lg`, body `text-sm` `color-text-muted`, then the dialog's content, then a button row with the primary button first and focused. Back cancels and changes nothing. Dialogs that contain a text field sit at the **top** of the safe area (y 27) so the system keyboard, which covers roughly the bottom 220 dp, never covers them; the PIN dialog and the card are centred.

## 2. PIN dialog (#12)

Neither the onn remote nor the Chromecast remote has number keys, so the dialog draws its own keypad. Remote digit keys still work where they exist.

| Element | Tokens |
|---|---|
| Title | "Set a 4-digit PIN" (first use) · "Enter it again" (confirm) · "Enter your PIN" (thereafter), `text-lg` |
| Body | "Use the number keys, or pick digits below." `text-sm` muted |
| Rings | four `radius-full` 16 dp rings `color-text-muted`, `space-4` apart; a filled ring is `color-text`; always passive (never a focus target), so exactly one ring exists, on the keypad |
| Keypad | 3 × 4 grid of `key` 56 × 48 dp keys, `radius-sm`, white 12 % fill, `text-md` tabular; last row: "Clear" · 0 · "⌫" (deletes one digit; never labelled Back, which is the remote key that cancels); focus starts on 1; each press fills a ring within `t-immediate`; the fourth digit submits |
| Error | rings shake once (±8 dp over `t-normal`), empty; "That's not the PIN. Try again." or "The PINs didn't match. Start again." in `text-sm` `color-text` under the rings |

No lockout in V1 (spec: household TV, the PIN guards content, not money). Back cancels; the toggle stays Off. Centred at x 240, y 62, 480 × `dialog-h-pin` 416: padding 24 + title 32 + 16 + body 20 + 16 + rings 16 + 16 + keypad 216 (4 × 48 + 3 × 8) + 16 + error line 20 + 24. The earlier 320 could not hold the keypad (owner decision 2026-09-24, Opus adversarial review 2026-09-23, minor 12).

## 3. Add a playlist (#15): removed

Cut 2026-09-23 with the user-playlist feature (the backend is the only source). The text-dialog pattern it used lives on in §4.

## 4. Channel list address

Top-anchored. Title "Channel list address". Body "Where the channel list is downloaded from. Change only if you were given a new address." Field prefilled with the current address, ellipsised from the start while unfocused, full when editing. Buttons: **Save** (primary) · **Use default** (restores the built-in address; shown only when the current value differs from it) · Cancel. Validation as above. Saving does not refresh; the Advanced row's details say "Takes effect at the next refresh. Use Refresh channel list now to fetch it."

## 5. First-launch progress (#17)

Full screen, `color-bg`, no video yet, nothing to focus. Centred column: the product name `text-xl` (the working title until one is chosen), then the count `text-2xl` tabular, then a `dialog-w` 480 dp `track` + `color-accent` bar (8 dp, `radius-full`), then one line `text-sm` `color-text-muted`. One determinate bar:

1. The bar runs on compressed bytes of the catalog download, which is the same streamed pass as the import, so it is determinate from the first byte to the flip.
2. The text is "Loading channels… 4,200 so far. This only happens once." with the running count of channels imported. The catalog carries no total, so "of 9,968" is not shown (review major 7); a count in the catalog header is a V1.1 catalog change if wanted.

On completion the Channels overlay opens over a black player with the device's country selected (red route 1). On failure the screen becomes the No-catalog screen below with the same layout, so nothing jumps.

## 6. No-catalog screen (#18)

Full screen, `color-bg`. Centred `panel-strong` 480 wide: "Can't reach the channel list." `text-lg`, "Check your connection, then try again." `text-sm` muted, buttons **Retry** (primary, focused; shows "Retrying…" with the in-button bar) · **Change address** (opens the channel list address dialog, the only route to fix a wrong address before any catalog exists). A second failure shows the same screen with the same copy; errors do not stack.

## 7. Channel strip (#4)

Left or Right on the player opens the strip along the bottom in place of the banner: a horizontal row of channel cards for the current list, the current channel centred and focused. The picture stays full frame and undimmed (no scrim: the strip is a peek, not a menu), with the strip on `panel-strong`, a touch more opaque than the banner, because it carries focused cards.

| Element | Tokens |
|---|---|
| Strip panel | x 48, y 405, 864 × `strip-h` 108, `panel-strong` (on `panel` a focused card's accent text was 4.44:1 over a bright picture; owner decision 2026-09-24, Opus adversarial review 2026-09-23, minor 2), `radius-md`, padding `space-3`, no scrim (tokens §4); a label at the left edge inside the panel, the first non-focusable 96 dp block: the list name and position, "Favorites · 3 of 12", `text-sm` muted |
| Card | `card` 136 × 84, `radius-sm`, gap `space-2`; `logo-sm` centred at the top, name `text-sm` one line ellipsis beneath, the favorite number `text-sm` tabular in the top-left corner when the list is Favorites; five cards visible with partial cards at both ends so the strip reads as scrollable |
| Focused card | ring + scale + tint; name in `color-text`; other cards' names `color-text-muted` |

Behaviour: Left/Right move focus one card and scroll the strip so the focused card stays centred; prefetch fires on focus; **the strip never tunes on focus** (unlike the Channels inset), so it is safe to skim. OK plays the focused channel and closes the strip with the banner. Up/Down close the strip and surf as on the bare player. Back closes it. It auto-closes 5 s after the last press (`AppViewModel` timer). CEC digits act as on the player. The key table in the flow map §2 is the source. Empty list: the strip shows one line, "No favorites yet. Hold OK on any channel to add it."

## 8. The four states, per surface

- **PIN:** empty (set), loading none, error (wrong / mismatch), populated (four filled rings for an instant before submit).
- **Address:** prefilled; saving is instant; invalid address error; "Use default" hidden when already default.
- **First launch:** the two phases are its loading state; error becomes the No-catalog screen; done is the Channels overlay.
- **No-catalog:** retrying; second failure identical.
- **Strip:** empty list line; no loading; no error; populated with 10,000 channels in All and a 45-character name ellipsised on a card.

## 9. Ten-foot checks

- Dialog text `text-lg` and `text-sm` on `surface-dialog`: 14.08:1 and 9.31:1; the primary button 9.52:1; keypad labels `text-md` on white 12 % over the dialog, 12:1 or better.
- With the keyboard up, both text dialogs end at y 263 (27 + `dialog-h-text` 236), well above the keyboard's top at about 320.
- The strip's cards are 136 dp wide: a 16 sp name ellipsises at about 14 characters; the banner after OK shows the full name.
- Emulator: enter a PIN with the D-pad only (no digit keys), mistype it once, and confirm the shake, the copy and that the toggle stays Off; open the strip on All and hold Right for five seconds to confirm nothing tunes.

## 10. Owner decisions 2026-09-23 (all five accepted)

1. **The PIN dialog has an on-screen keypad.** Neither target remote has digits; without it the adult-content toggle cannot be turned on at all. Task 15 updated.
2. **Text dialogs sit at the top of the safe area,** not centred, so the system keyboard never covers them.
3. **The No-catalog screen has a "Change address" button.** Without it a wrong built-in address on a fresh install is unrecoverable from the TV. Task 16 updated.
4. **First launch shows one determinate bar** by downloaded bytes, with the running channel count in the text (§5). Download and import are one streamed pass, so there are no separate phases; an earlier two-phase draft was withdrawn (Opus adversarial review 2026-09-23, minor 6).
5. **The strip never tunes on focus and has no scrim.** It is the one surface for skimming without committing; the Channels inset is where previews live.
