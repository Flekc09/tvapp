# Screen 6 — Context menu and Sources

**Status:** Approved by owner 2026-09-23 (all four decisions in §6) · Playbook C.4
**Inputs:** `docs/ui/screens-and-flows.md` #2, #3 · spec §5.5 rule 10, §6 · `docs/ui/component-states.md` (menu item) · plan Task 12 (`ContextMenu.kt`, `SourcesMenu.kt`)
**Primary actions:** context menu, open Sources (first item); Sources, OK plays the focused source.
**Mockup:** the Design artifact "TV App screens" (https://claude.ai/artifact/19F9iWwZnDqLst7nm6NcBy), page "Menus", four artboards.

## 1. One panel, two contents

Both menus are about the channel that is playing, so both live in one panel anchored bottom-left where the banner was, over `scrim` with the picture still moving behind it. The banner hides when the panel opens; the panel's header carries the channel identity instead.

| Element | Position | Size | Tokens |
|---|---|---|---|
| Panel | x 48, bottom edge at y 513 | `panel-w` 400 wide; context menu 300 high (top 213), Sources 486 high (top 27) | `panel`, `radius-md`, padding `space-4`, `blur` |
| Header | top of the panel | 64 high | `logo-sm` 48 · name `text-md` one line ellipsis · region `text-sm` muted; Sources adds a second header line, "Choose a source" `text-sm` muted, or the queue's progress while it is trying |
| Items | below a `border-hair` divider | `row-h-dense` 48 each | menu item per the matrix: label `text-md`, trailing detail `text-sm` muted, chevron on items that open something |
| Hints | pinned to the bottom | 20 high | **OK** Choose (context) / Play this source (Sources) · **Back** Close |

## 2. Context menu (#2)

Opened by long-press OK on the player. Three items, in this order:

1. **Sources ›** trailing "Source 2 · Working" (the one playing now). Focused on open.
2. **Add to favorites** / **Remove from favorites**, label from the current state, star icon outline or filled `color-accent` before the label. OK applies at once, the label flips, the toast shows, the menu stays open (the viewer may want Sources next).
3. **Previous channel** trailing the previous channel's name, "SEC Network". With no previous channel: label kept, reason "None yet", does nothing.

Behaviour: Back closes the menu and shows the banner for `banner-hold` so the viewer lands on something. Up on the first item and Down on the last do nothing, like every list in the app. Left/Right do nothing. CHANNEL_UP/DOWN and digits (CEC) close the panel and act on the player; the full key table is `docs/ui/screens-and-flows.md` §2.

## 3. Sources (#3)

Opened from the context menu; the panel grows to full height over `t-normal` and the items are replaced by the source list.

**Row (368 dp inner, `row-h-dense`):** "Source 3" `text-md` · trailing group right-aligned: quality "1080p" `text-sm` muted (blank when the catalog has none) · status mark + word `text-sm` `color-text`. The playing source shows **"Playing"** in `color-accent` in place of its status word, with the mark kept. Rows are in the queue's order (spec §5.5 sort key), so the top row is the app's first choice and the playing row may be lower.

**Picking:** OK on a row plays it at once. The panel stays open: the picked row's status becomes "Trying…" `color-text-muted`, then "Playing" on first frame, so the viewer can try several sources in a row and judge the picture behind the scrim without reopening the menu. On failure the row reads "Not working", and because the normal queue resumes from its top (spec rule 10), the header's second line reads "Trying source 1 of 5" until something plays, then "Choose a source" again; the row that ended up playing gets "Playing". Back returns to the context menu (one level, as everywhere); Back again closes the panel and shows the banner. CEC channel keys close the panel at once.

**Manual pick semantics the design must not hide:** a manual pick clears that source's demotion and suppresses automatic failover for that attempt (spec rule 10). Nothing on screen says "demoted"; the viewer only sees the order change on the next visit.

Focus on open: the playing source's row. Up/Down move; digits do nothing here (the numbers are labels, and there can be 30). Long-press OK does nothing.

## 4. The four states

- **Empty:** never: every channel has at least one source (spec §4.2).
- **Loading:** a picked row shows "Trying…"; the header's second line shows the queue's progress when the automatic queue takes over.
- **Error:** a picked row that failed shows "Not working"; if every source fails after a manual pick, the panel closes and the "isn't working" card takes over, as for any exhausted tune.
- **Populated, worst case:** 30 sources, scrolling inside the panel with the focused row centred; a source with no quality label; the playing source at row 7; a row with "Trying…". Context menu worst case: a 45-character channel name in the header, "Remove from favorites", and "Previous: Al Jazeera English Documentary Channel Europe" ellipsised in the trailing text.

## 5. Ten-foot checks

- Items `text-md` 20 sp on `surface-overlay` 12.15:1; trailing details `text-sm` 8.03:1; "Playing" in `color-accent` 7.72:1.
- The panel's left edge is at the safe area and its bottom edge at 513 dp, the same bottom line as the banner, so the eye does not have to move to find it.
- Exactly one ring at any time; the picked row's "Trying…" is text, not a spinner.
- Emulator: open Sources on a channel with 30 feeds and confirm the focused row stays centred while scrolling; pick a dead source and confirm the header's progress line and the final "Playing" row.

## 6. Owner decisions 2026-09-23 (all four accepted)

1. **Both menus share one bottom-left panel** anchored where the banner was, not a centred dialog. The context menu is about "this channel", so it sits where the channel's name just was.
2. **Sources stays open after a pick** and reports in the row, so the viewer can try sources back to back. The alternative, closing on OK, saves one Back but makes comparing sources a long-press per attempt.
3. **The favorite toggle keeps the menu open** and flips its own label, so add-then-Sources is one visit.
4. **The playing row shows "Playing" instead of its status word.** The status is still implied (it is playing), and the accent word is the only accent text in the panel, so the eye finds it first.
