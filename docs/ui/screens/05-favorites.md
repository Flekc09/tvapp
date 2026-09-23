# Screen 5 — Favorites and the row menu

**Status:** Approved by owner 2026-09-23 (all four points in §5; the three-press reading of "one press from anywhere" accepted, no long-press GUIDE gesture) · Playbook C.4
**Inputs:** `docs/ui/screens-and-flows.md` #9, #10, C8 · `docs/ui/screens/02-channels.md` · `docs/ui/component-states.md` (menu item, channel row) · spec §6 · plan Task 14
**Primary action:** OK on a favorite plays it. Reordering is the secondary job and lives behind long-press.
**Mockup:** the Design artifact "TV App screens" (https://claude.ai/artifact/19F9iWwZnDqLst7nm6NcBy), page "Favorites", three artboards; the populated and empty states are on the Channels page.

## 1. What Favorites is

Favorites is not a separate overlay. It is the Channels overlay with the Favorites collection selected, so the layout, focus rules and inset are the ones in 02-channels. Three things differ in this list:

1. **Numbers.** Every row starts with its 1-based position in a 32 dp column (`text-md` tabular), room for "50". The number is the channel number the strip shows and the digit keys honour.
2. **Digits play, not jump.** In this list digit 1–9 plays that favorite directly, as on the player. Jump-by-letter is what digits do in every other list (D5). The numbers on screen make the meaning unambiguous. Positions past 9 keep their number (it is the list position, and Move up/down needs it) but have no digit route: digits are CEC-only accelerators now, so a multi-digit buffer for a key most remotes lack is not worth its state (review major 12); favorites past 9 are reached by hold-Back then Down, or from the strip.
3. **Long-press opens the row menu** instead of toggling the favorite, because the only toggle here is Remove, and the menu has it.

## 2. The row menu (#10)

Long-press OK on a row replaces the details panel with the menu, in the same 288 × 308 dp `panel` at x 624, y 205; focus moves into it and the row keeps the selected treatment (accent bar) so the viewer sees which channel the menu is about.

| Element | Tokens |
|---|---|
| Heading | the number and name, "3 · ABC" `text-lg`, one line ellipsis; region `text-sm` muted beneath |
| Move up | menu item `row-h-dense`, `text-md`; on the first row: label kept, reason "Already first" `text-sm` muted, does nothing |
| Move down | same; on the last row: "Already last" |
| Remove | menu item, last, separated by a `border-hair` divider and `space-3` (distance from the two safe actions) |
| Hints | pinned to the bottom: **OK** Choose · **Back** Close |

Behaviour:
- Move up / Move down apply on OK within `t-immediate`: the row moves in the list (animated over `t-fast`), its number and its neighbour's swap, focus stays on the same menu item so a held sequence of OK presses walks the row up or down. The heading's number updates.
- Remove applies on OK: the row leaves the list, the menu closes, the toast "Removed from favorites" shows, focus goes to the row that took its place (or the previous one at the end, or the column when the list is now empty). No confirmation and no undo (review minor 25; owner accepted the risk 2026-09-23): a toast cannot take focus, and a confirm step would make the common case slower to protect a mistake that costs a long-press on the channel plus Move up to restore the number. Remove sits below a divider for that reason.
- Back closes the menu and returns focus to the row. Left/Right do nothing inside the menu.
- A "no longer available" row has the same menu; moving it keeps the numbers of the others stable, which is the reason it is still listed.

## 3. The four states

- **Empty:** the Channels empty state, "No favorites yet · Hold OK on any channel to add it" (Channels page).
- **Loading:** none; the list is local.
- **Error:** none of its own.
- **Populated, worst case:** 50 favorites, two-digit numbers; the list scrolled to the middle with rows 22–28 visible; a "no longer available" row at 24; a 45-character name; the menu open on row 25 showing both moves enabled. First-row variant: menu with "Move up · Already first".

## 4. Ten-foot checks

- The number column is `text-md` 20 sp `color-text` tabular: 12.15:1 on the overlay; "50" fits 32 dp at 20 sp Medium.
- While the menu is open there is exactly one focus ring (in the menu); the row shows the selected bar, not a ring.
- Remove sits below a divider with `space-3` above it, so a fast OK after Move down cannot land on it without a visible extra Down.
- Emulator: reorder a row from 7 to 1 by holding OK on Move up and confirm the list follows without losing focus; remove the last row and confirm focus lands on the new last row.

## 5. Owner decisions 2026-09-23 (all four accepted)

1. **Favorites reuses the Channels overlay** rather than being a second list. The plan's Task 14 had a separate composable; the update makes it the same one with the Favorites filter preselected.
2. **The row menu opens in the details panel,** not as a floating popover over the list. The layout stays stable and the ring stays in one predictable place.
3. **Digits play by number in this list.** Everywhere else they jump by letter.
4. **The spec's "Favorites one press from anywhere"** is met by holding Back: one gesture from every surface opens the Favorites list with favorite 1 focused (flow map D10, adversarial review 2026-09-23). Earlier text here assumed GUIDE or digit keys, which the Chromecast and onn remotes do not have; that assumption is withdrawn.
