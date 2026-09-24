# Screen 2 — Channels overlay

**Status:** Approved by owner 2026-09-23 (all four decisions in §5) · Playbook C.4
**Inputs:** `docs/ui/screens-and-flows.md` #5, C4, C8, C9, D1, D5 · `docs/ui/design-tokens.md` · `docs/ui/component-states.md` · spec §5.4, §6
**Primary action:** OK on the focused channel row plays it full screen. The column's Browse, Search and Settings entries are navigation (D1).
**Mockup:** the Design artifact "TV App screens" (https://claude.ai/artifact/19F9iWwZnDqLst7nm6NcBy), page "Channels", four artboards.

## 1. Layout (960 × 540 dp canvas, safe area x 48–912, y 27–513)

The video keeps playing in the inset; everything else sits on `scrim` + `panel` (`surface-overlay`). Three regions across the full safe width, left to right, with `space-4` gaps: **224 + 16 + 320 + 16 + 288 = 864** (column widened after the adversarial review so "United States · 2,731" never ellipsises).

| Region | Position | Size | Contents |
|---|---|---|---|
| Collection column | x 48, y 27 | `column-w` 224 × 486 | on its own `panel` (never text on the scrim alone: 3.1:1 over a white frame); heading "Channels" `text-xl`, then entries at `row-h-dense` 48 |
| List panel | x 288, y 27 | `list-w` 320 × 486 | `panel`, `radius-md`, inner padding `space-4`; rows at `row-h` 64, the list scrolls with the focused row kept at the vertical centre once it can |
| Inset | x 624, y 27 | `inset-w` 288 × 162 | the one player surface, `radius-md`, white outline at 60 % 3 dp, cut out of the scrim. A `SurfaceView` may not clip to rounded corners: the Task 18 spike checks it on the stick, and if the video corners stay square, the inset and its outline become square too (owner decision 2026-09-24, Opus adversarial review 2026-09-23, minor 19) |
| Details panel | x 624, y 205 | `detail-w` 288 × 308 | `panel`, `radius-md`, padding `space-4`: what the highlighted row is, and the key hints |

Channels whose only category is "Other" appear under Browse → Other and in Search, never in a country list or All (spec §6); the list query excludes them when no category is chosen.

**Collection column entries, top to bottom:** Favorites · Recent · the device's country (pinned) · the current filter's country or category when it is neither of those (so a list opened from Browse shows where the viewer is) · All · divider · Browse › · Search › · Settings ›. Counts in `text-sm` `color-text-muted`, tabular, right-aligned. The other 176 countries are not in the column; Browse is the way to them (see §5, decision 1).

**Row anatomy (320 − 32 = 288 dp inner):** [Favorites only: number `text-md` tabular, 32 wide] · `logo-sm` 48 · `space-3` · two lines. Line one: name `text-md` `color-text`, one line, ellipsis. Line two: region `text-sm` `color-text-muted` that ellipsises, then a **fixed 112 dp status slot** at the right end holding the 12 dp mark and the word `text-sm` `color-text`; the slot never shrinks, so "Not working" is always whole and the region gives way first (review major 3). Name width is 228 dp (196 in Favorites); the details panel shows the full name. No flag in rows (the column already says the country; Search rows keep the flag because results mix countries).

**Details panel:** `logo-lg` 96 · name `text-lg` up to two lines (the full name the row had to cut) · region · country `text-sm` muted · categories `text-sm` muted ("News · Entertainment") · status mark + word `text-sm` · then, pinned to the bottom, the key hints in `text-sm` with key chips in `color-accent` outline: **OK** Watch · **Hold OK** Add to favorites · **Hold Back** Favorites · **2–9** Jump to a letter (CEC remotes only; the line is hidden when the last key event came from a remote without digits). The hints are why long-press, hold-Back and jump-by-letter are discoverable at all; every details panel in the app carries the **Hold Back** line.

## 2. Focus and movement

- Opening: focus lands on the **current channel's row** when it is in the list, else the first row. Opened by holding Back (the favorites gesture), the Favorites collection is selected and focus lands on **favorite 1** regardless of what is playing, so OK is always the next press. The column shows the remembered filter as selected (`color-accent` label + 3 dp accent bar).
- Left from the list moves to the column entry that is selected; Right from the column returns to the row that was focused. Up/Down in the column move between entries; the list **previews** the entry under focus as focus rests (`t-fast`), and the details panel follows the list's first row. Nothing is remembered until OK or Right **commits** the entry; Back from the column, or leaving the overlay, restores the committed filter, so passing through the column never changes the surf order (review major 11).
- Focus on a row: prefetch fires, `dwell-preview` starts, the details panel updates within `t-immediate`. After 700 ms the inset tunes to that channel; the inset holds its last frame until the new first frame.
- OK on a row: overlay closes, picture animates from the inset to full frame over `t-move`, banner shows. Long-press OK: favorite toggled, the star appears in the details panel and the Favorites count in the column changes; a toast "Added to favorites" / "Removed from favorites" confirms it.
- Back: overlay closes and keeps whatever is playing, previewed or not (spec §5.4).
- Digits 2–9 (CEC remotes): jump to the next row whose name starts with a letter of that key; a toast shows the key's letters, "5 · JKL", for `banner-hold` so the viewer learns the mapping. 0 and 1 do nothing here.
- Scroll position: the focused row stays vertically centred once the list is longer than the panel; the first and last rows pin to the edges. No scrollbar (nothing to grab), but the panel shows a 1 dp `color-line` fade at the top and bottom edges when rows continue beyond them.

## 3. The four states

### Empty — the filter has no visible channels
Favorites: the list panel shows, centred, "No favorites yet" `text-md` and "Hold OK on any channel to add it" `text-sm` muted; focus moves to the column so Up/Down do something. Recent: "Nothing watched yet". A country whose channels are all hidden: "No working channels in Andorra right now". The details panel shows only the key hints.

### Loading — the inset is switching
After `dwell-preview` the inset holds the previous picture while the new stream starts; the details panel's status word is the only text about it. Nothing spins. The rows never show a loading state: the list comes from Room.

### Error — a preview fails, or the catalog is missing
A preview that exhausts its budget leaves the inset on its last frame and the details status reads "Not working"; OK still tries a full tune. With no catalog at all this overlay is replaced by the No-catalog screen (#18).

### Populated — worst case for the mockup and emulator check
Collection "United States", count 2,731. Rows: "Al Jazeera English Documentary Channel Europe" (ellipsised), three consecutive "ABC" rows with regions "Baton Rouge, Louisiana", "Birmingham, Alabama", "Boise, Idaho", a row with no logo (placeholder letter), one "Not checked", one "Not working" (visible because the setting is on), and a favorite-numbered variant on the Favorites page with a "no longer available" placeholder row keeping its number.

## 4. Ten-foot checks

- Text: rows `text-md` 20 sp and `text-sm` 16 sp on `surface-overlay` (12.15:1 and 8.03:1 against a white picture); focused row 8.84:1 and 5.84:1.
- A focused row grows by `focus-grow` 8 dp per side plus the 3 dp ring, 11 dp in all, inside the panel's 16 dp padding; wide rows never scale (review major 10).
- The inset sits fully inside the safe area and is never covered by the scrim or the prompt; toasts sit top-centre, clear of it. The player's own source notices ("Switching source", "Trying source") are suppressed while Channels is open and a mid-play failover in the inset shows only as the held frame; app toasts ("Added to favorites", "5 · JKL") are not suppressed.
- Both emulator profiles, then the stick: the column's longest entry ("United States  2,731") must not wrap or ellipsise at 224 dp.

## 5. Owner decisions 2026-09-23 (all four accepted; Task 13 updated)

1. **The column lists five collections plus the three destinations, not all 177 countries.** Plan Task 13 says "then the countries with counts". With 177 countries the column would be a scrolling list of 185 entries and Browse, Search and Settings at its bottom would be unreachable, which breaks D1. Browse is country-first by spec, so nothing is lost. If confirmed, Task 13's column description is updated and `countryCounts` moves to Browse.
2. **The details panel under the inset.** Not in the spec; it uses the space the inset leaves and carries the key hints. Without it, long-press favorite and jump-by-letter have no on-screen teaching anywhere.
3. **No flag in rows.** Rows in Channels are already scoped by the column; the flag returns in Search results.
4. **A confirmation toast for favorite toggles** ("Added to favorites"). Long-press has no other visible acknowledgement while the overlay is open.
