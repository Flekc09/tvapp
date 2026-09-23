# Screen 4 — Search

**Status:** Approved by owner 2026-09-23 (all four decisions in §5; Task 14 updated) · Playbook C.4
**Inputs:** `docs/ui/screens-and-flows.md` #8 · `docs/ui/design-tokens.md` · `docs/ui/component-states.md` (text field, channel row, microphone button) · spec §6 · plan Task 14
**Primary action:** OK on a result plays it full screen.
**Mockup:** the Design artifact "TV App screens" (https://claude.ai/artifact/19F9iWwZnDqLst7nm6NcBy), page "Search", four artboards.

## 1. Layout (960 × 540 dp, safe area x 48–912, y 27–513)

Pushed from the Channels column; the picture stays in the inset. The left region is one panel: **560 + 16 + 288 = 864**.

| Region | Position | Size | Contents |
|---|---|---|---|
| Search panel | x 48, y 27 | 560 × 486 | `panel`, `radius-md`, padding `space-4`: the field block, a result count line, then result rows at `row-h` 64 |
| Inset | x 624, y 27 | `inset-w` | the player surface, unchanged |
| Details panel | x 624, y 205 | `detail-w` 288 × 308 | as on Channels: logo, full name, region · country, categories, status, key hints |

**Field block (528 dp inner):** label "Search channels" `text-sm` `color-text-muted` above; the field `text-md` `color-text` on a 48 dp row with a `border-hair` `color-line` underline, placeholder "Channel, network, city or category" in `color-text-muted`; the microphone button (32 dp icon + "Speak" `text-sm`) at the right end of the same row, hidden when the platform has no recognizer. The field's focus state is the ring only, no scale (matrix).

**Result count line:** `text-sm` `color-text-muted`, "1,204 results for “a”", tabular, from `searchCount` (the list itself shows the first 200; a viewer who needs the 201st types one more letter). Hidden when the query is empty.

**Result row (528 dp inner, `row-h` 64):** `flag` 24 × 16 · `space-2` · `logo-sm` 48 · `space-3` · two lines: name `text-md`; second line `text-sm`: region (if any) · first category, ellipsising, then the fixed 112 dp status slot (mark + word, never cut). Results keep the flag because they mix countries (02-channels §5 decision 3). When the match is not in the display name, the second line begins with why: "Matches ESPN (network)" or "Also known as Al Jazeera Documentary", so a result never looks wrong.

**The system keyboard.** Android TV draws its own keyboard over the bottom of the screen, roughly y 320–540 on this canvas. The app handles it with the ime inset (`WindowInsets.ime`): while the keyboard is up the search panel shrinks to end at the keyboard's top edge (y ≈ 304) and the details panel is hidden; the inset stays. The results list scrolls inside the shorter panel so the focused result is never under the keyboard. When the keyboard closes the panel grows back over `t-normal`. The keyboard is the platform's; the mockup shows a labelled placeholder for it.

**Key hints (details panel):** **OK** Watch · **Hold OK** Add to favorites · **Back** Close.

## 2. Focus and movement

- Opening: focus on the field, keyboard closed, query empty (a query is not remembered between visits; Recent covers "what I watched", not "what I typed").
- OK on the field: keyboard opens. Typing updates results 250 ms after the last keystroke; no spinner, layout stable. The keyboard's Done key (or Down from the field) closes the keyboard and moves focus to the first result.
- Up from the first result returns to the field without opening the keyboard; OK on the field reopens it with the query kept.
- Back with the keyboard open: closes the keyboard, keeps the query and results. Back with it closed: leaves Search, back to Channels with its filter untouched.
- OK on a result: overlay closes, plays full screen. Long-press OK: favorite toggled with the toast.
- Right from a result: nothing (the details panel is passive). Left: nothing.
- Digits: typed into the field while the field has focus (they are letters on the keyboard too); in the results list they do nothing. Jump-by-letter makes no sense over a list the viewer just filtered.
- The microphone button is focusable to the right of the field; OK starts the platform recognizer, whose UI takes over the screen; on return the recognized text is the query and results show with the keyboard closed.
- GUIDE: back to Channels (global key).

## 3. The four states

### Empty
- **No query:** below the field, centred in the results area, "Type a channel name, network, city or category" `text-md` and, when the microphone exists, "or press Speak" `text-sm` muted. Details panel shows only the key hints.
- **No matches:** count line hidden; "No channels match “xyz”" `text-md` and "Try a shorter word, or a city or network name" `text-sm` muted. The query stays in the field.

### Loading
None visible. Results arrive under the 300 ms threshold; if a query ever takes longer the previous results stay until the new ones replace them (never a blank flash).

### Error
Only the recognizer: if it returns nothing, the field is unchanged and no message shows (the platform already told the viewer). A missing catalog replaces the overlay with the No-catalog screen.

### Populated — worst case
Query “a”: "1,204 results for “a”"; rows for "Al Jazeera English Documentary Channel Europe" (ellipsised), "ABC · Baton Rouge, Louisiana", a placeholder-logo row, an "Also known as" row, and each of the three status words. With the keyboard up only the count line and two to three rows are visible, which is the state the viewer actually types in.

## 4. Ten-foot checks

- Field text `text-md` 20 sp on `surface-overlay` 12.15:1; placeholder `color-text-muted` 8.03:1; result rows as on Channels.
- With the keyboard up, the focused element (field or a result) is never under the keyboard: the panel ends at the keyboard's top and the list scrolls.
- The count line uses tabular figures so "1,204" and "12" do not shift the rows below.
- Emulator: type “a” with the Gboard TV keyboard at 720p and confirm two rows remain visible above it; then check the mic button is hidden on the emulator image without a recognizer.

## 5. Owner decisions 2026-09-23 (all four accepted)

1. **The panel shrinks above the keyboard rather than letting the keyboard cover it.** Compose does this with the ime inset; the plan's Task 14 does not mention it, and without it the first results sit under the keys.
2. **A "why it matched" line** on results matched through alternate names, network, region or category, so a result whose name does not contain the query does not look like a bug.
3. **The query is not remembered between visits.** Fresh field each time; Back inside Search keeps it until the overlay closes.
4. **Digits do not jump-by-letter in results.** They type while the field has focus and are ignored in the list.
