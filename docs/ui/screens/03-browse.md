# Screen 3 — Browse (countries, then categories)

**Status:** Approved by owner 2026-09-23 (all four decisions in §5) · Playbook C.4
**Inputs:** `docs/ui/screens-and-flows.md` #6 and #7 · `docs/ui/design-tokens.md` · `docs/ui/component-states.md` · spec §6 · `docs/ui/screens/02-channels.md` §5 decision 1 (the Channels column no longer lists every country, so Browse is the route to 176 of them)
**Primary action:** OK on a category row (or "All categories") opens Channels filtered to it. Countries on the left are a selector, not a destination.
**Mockup:** the Design artifact "TV App screens" (https://claude.ai/artifact/19F9iWwZnDqLst7nm6NcBy), page "Browse", four artboards.

## 1. Layout (960 × 540 dp, safe area x 48–912, y 27–513)

Browse is pushed from the Channels column, so the picture is already in the inset and stays there. Same frame as Channels; the two left regions become two equal panels: **272 + 16 + 272 + 16 + 288 = 864**.

| Region | Position | Size | Contents |
|---|---|---|---|
| Countries panel | x 48, y 27 | 272 × 486 | `panel`, `radius-md`, padding `space-4`; heading "Browse" `text-xl` inside the panel; rows at `row-h-dense` 48 |
| Categories panel | x 336, y 27 | 272 × 486 | `panel`, `radius-md`, padding `space-4`; heading = the focused country's name `text-xl`, one line ellipsis; then the chip, then rows at `row-h-dense` |
| Inset | x 624, y 27 | `inset-w` 288 × 162 | the player surface, unchanged from Channels |
| Details panel | x 624, y 205 | `detail-w` 288 × 308 | `panel`: `flag` at 48 × 32, country name `text-lg`, "2,731 channels" `text-sm` muted, then the key hints pinned to the bottom |

**Country row (240 dp inner):** `flag` 24 × 16 · `space-2` · name `text-md` `color-text` one line ellipsis · count `text-sm` `color-text-muted` tabular right-aligned. The device's country is first, followed by a `border-hair` divider; the rest alphabetical by display name. Countries with no visible channels are omitted, so no row leads to an empty list by construction.

**Categories panel, top to bottom:** heading (country name) · **chip** "Country: All" (`radius-full`, `text-sm`; on: `color-accent` fill, `color-panel` label, text "Country: All ✓") · **"All categories"** row with the country's total · the categories alphabetically, each with its count · "Other" last. With the chip on, the heading reads "All countries", the "All categories" row is hidden (that is the Channels "All" collection), and every count is worldwide.

**Key hints (details panel):** **OK** Open · **2–9** Jump to a letter · **Back** Channels.

## 2. Focus and movement

- Opening: focus on the pinned device country; the categories panel already shows that country.
- Up/Down in the countries panel: the categories panel and the details panel follow the focused country as focus rests (`t-fast`), like the Channels column. No press needed to look.
- OK or Right on a country: focus moves to the categories panel, landing on "All categories" (the most likely target), not on the chip.
- Up from "All categories" reaches the chip; OK on the chip toggles it and keeps focus there.
- OK on "All categories" or a category: the stack is cleared and one Channels overlay is pushed with the new filter (`closeAll()` then `push(CHANNELS)`, so Back goes to the player, never to a stale Browse) (`ListFilter(COUNTRY, c)`, `ListFilter(COUNTRY, c, cat)`, or `ListFilter(CATEGORY, null, cat)` with the chip on); the Channels column shows that filter as its extra entry (02-channels §1).
- Left from the categories panel returns to the country that was focused. Back from categories: same. Back from countries: back to Channels.
- Digits 2–9 in the countries panel: jump to the next country starting with a letter of that key, with the "5 · JKL" toast. In the categories panel digits do nothing (about 20 rows; the list is short enough to scroll).
- GUIDE anywhere here: back to Channels with the remembered filter (global key).

## 3. The four states

### Empty
Countries: never empty while a catalog exists (zero-count countries are omitted). Categories: never empty either, because the counts use exactly the filters the lists use (`countryCounts` and `categoryStrings` take the same adult, no-up and broken-here arguments as `channelList`, review major 15), so a listed country always has at least one visible channel. The earlier "No working channels in Andorra" copy is withdrawn; the empty state is unreachable by construction and the flow map's #7 row is amended.

### Loading
None. Both panels read Room; counts are precomputed per import. The inset keeps whatever is playing.

### Error
None of its own. A missing catalog replaces the whole overlay with the No-catalog screen (#18).

### Populated — worst case
Countries: "United States 2,731" pinned; then "Afghanistan", "Albania", …, "Bosnia and Herzegovina" (ellipsised at 240 dp with its count intact), …, "United Kingdom". Categories for the United States: All categories 2,731 · Animation 38 · Auto 12 · Business 41 · Classic 27 · Comedy 19 · Cooking 22 · Culture 30 · Documentary 55 · Education 44 · Entertainment 312 · Family 26 · General 604 · Kids 48 · Legislative 71 · Lifestyle 39 · Movies 88 · Music 97 · News 402 · Outdoor 15 · Public 133 · Relax 9 · Religious 218 · Science 11 · Series 34 · Shop 21 · Sports 146 · Travel 18 · Weather 24 · Other 107. Thirty rows at 48 dp scroll inside the panel; the focused row stays centred.

## 4. Ten-foot checks

- Rows `text-md` 20 sp and counts `text-sm` 16 sp on `surface-overlay`: 12.15:1 and 8.03:1. The chip's on-state label `color-panel` on `color-accent`: 9.52:1.
- Two focus styles never coexist: when focus is in the categories panel, the countries panel shows the chosen country with the selected treatment (accent label + 3 dp accent bar), not a focus ring.
- The chip's state is text as well as colour ("✓").
- The two 272 dp panels each hold a 30-character row without wrapping; check "Bosnia and Herzegovina" and "Legislative 71" at 720p.

## 5. Owner decisions 2026-09-23 (all four accepted)

1. **"All categories" as the first row** of the categories panel. It is the only route to a non-pinned country's full list now that the Channels column is short. Not in the spec; a direct consequence of the Channels decision. Task 13 already carries it.
2. **Categories fill in as focus rests on a country,** with OK or Right moving over, instead of OK opening a second screen. Halves the presses to reach a category and lets the viewer scan counts across countries. The flow map counted Browse as two screens; this makes it one.
3. **The chip hides "All categories" when on,** because worldwide-all is the Channels "All" collection and a duplicate route would confuse the column.
4. **Zero-count countries are omitted** rather than listed at 0, so no row leads to an empty list.
