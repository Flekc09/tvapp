# Screen 7 — Settings, Advanced, Diagnostics

**Status:** Approved by owner 2026-09-23 (all five decisions in §7; Diagnostics keeps the format word) · Playbook C.4
**Inputs:** `docs/ui/screens-and-flows.md` #11, #14, #16 · spec §6 (two tiers), §5.1, display rules · `docs/ui/component-states.md` (settings rows, button, progress bar) · plan Task 15
**Primary actions:** Settings, change startup behaviour (first row); Advanced, refresh the channel list now; Diagnostics, Back.
**Mockup:** the Design artifact "TV App screens" (https://claude.ai/artifact/19F9iWwZnDqLst7nm6NcBy), page "Settings", six artboards. The PIN and channel-list-address dialogs are the next screen.

## 1. Frame

Pushed from the Channels column, so the picture stays in the inset. Same regions as Search: a 560 dp panel on the left, the inset and a 288 dp details panel on the right. The details panel explains whichever row has focus: heading = the row's label `text-lg`, a two-to-four line description `text-sm` `color-text-muted`, the current value `text-sm` `color-text`, and the key hints. Rows stay short; the explanation lives where there is room to read it.

**Row (528 dp inner, `row-h` 64):** label `text-md` `color-text` · trailing: the value `text-sm` `color-text-muted` for pickers, the switch plus "On"/"Off" `text-sm` for toggles, "Hold OK" for Advanced, a chevron for rows that open a sub-panel. The panel heading ("Settings", "Advanced", "Diagnostics") is `text-xl` at the top.

**Pickers open in the details panel.** OK on a picker row moves focus to the details panel, which lists the options as menu items (`row-h-dense`) with the current one marked in `color-accent`; OK chooses, writes the setting, and returns focus to the row with its new value; Back returns without changing. This is the pattern Favorites' row menu established. The key hints stay pinned at the panel's bottom and the option list scrolls above them (with the Startup picker's favorites the list is longer than the panel; review major 14).

**Long lists scroll.** Advanced has six rows and Diagnostics nine; both exceed 486 dp, so the left panel scrolls with the focused row kept visible and a 1 dp `color-line` fade at the edges when rows continue, as the Channels list does. Nothing is ever clipped.

## 2. Settings (#11)

| Row | Trailing | Details description | Options |
|---|---|---|---|
| Startup | "Last channel" | "What the TV shows when the app opens." | Last channel · Channels list · then one entry per favorite, "3 · ABC", so a favorite can be chosen without typing |
| Sleep timer | "Off" | "Pauses the picture after the time you choose and opens the channel list. The app stays open." | Off · 30 minutes · 60 minutes · 90 minutes; while a timer runs the trailing text shows the remaining time, "42 min left" |
| Picture size | "Fit" | "Fit shows the whole picture with bars if needed. Zoom fills the screen and trims the edges." | Fit · Zoom |
| Show channels that don't work here | switch, "Off" | "Channels that have failed on this TV three days running are hidden. Turn this on to see them anyway." | toggle |
| Show adult channels | switch, "Off" | "Hidden by default. Turning this on asks for a 4-digit PIN; the first time, you choose it." | toggle; OK opens the PIN dialog (next screen); the switch flips only after the PIN is accepted |
| Advanced | "Hold OK" | "Channel list address, refresh, and technical details. Hold OK to open." | hold 500 ms; a `track` bar fills under the label during the hold |

Focus on open: Startup. Back: to Channels.

## 3. Advanced (#14)

| Row | Trailing | Details description |
|---|---|---|
| Channel list address | the URL, ellipsised at the start so the host shows | "Where the channel list is downloaded from. Change only if you were given a new address." OK opens the address dialog (a text field dialog; next screen). |
| Refresh channel list now | "Last: today 03:12" | "Downloads the latest list now, even while watching." OK runs it: the row's trailing text becomes a `track` + `color-accent` progress bar (by compressed bytes) with "4,200 so far" tabular beside it; on success "Last: just now"; on failure the details panel reads "Couldn't download the channel list. Your current list is unchanged." with focus kept on the row. |
| Channel list version | "2026-09-23 · 9,968 channels" | "The date the list was built and how many channels it has." Read-only; focusable so its description can be read. |
| Show channels that weren't working at the last check | switch, "Off" | "The nightly check marks channels that had no working source. They are hidden unless this is on." |
| Switch source automatically on poor signal | switch, "Off" | "When the picture struggles, switch to another source without asking. Off shows a prompt instead." |
| Diagnostics | › | "Live details about what is playing." |

Focus on open: Channel list address. Back: to Settings. (The "My playlists" rows and the Add-a-playlist dialog were cut 2026-09-23: the backend is the only source.)

## 4. Diagnostics (#16)

Read-only rows, `row-h-dense` 48, label `text-sm` `color-text-muted` on the left (160 dp), value `text-md` `color-text` tabular on the right; the address row is taller (up to four lines of `text-xs` 12 sp for a 200-character address at 368 dp, the one place the spec allows 12 sp). Values update live while the overlay is open.

Channel · Source address · Format · Picture (1920 × 1080) · Bitrate (4.2 Mb/s) · Buffer (12.4 s) · Tune time (1.8 s) · App version · Channel list version.

Nothing playing: every value is "—". The details panel shows "Diagnostics" and one line: "These update while a channel plays." plus **Back** Close. Back: to Advanced.

## 5. The four states

- **Empty:** Diagnostics with nothing playing shows dashes; Advanced has no empty state.
- **Loading:** Refresh in progress (progress bar in the row); the hold-to-open bar on Advanced.
- **Error:** Refresh failure copy in the details panel; a PIN cancelled leaves the switch Off with no message.
- **Populated, worst case:** Settings with a sleep timer running ("42 min left") and a favorite chosen as startup ("3 · ABC"); Advanced with a 90-character channel list address ellipsised at the start; Diagnostics with a 200-character source address wrapping to three lines.

## 6. Ten-foot checks

- Rows `text-md` on `surface-overlay` 12.15:1; descriptions `text-sm` muted 8.03:1; Diagnostics `text-xs` 12 sp `color-text` 12.15:1 and never smaller.
- Switch state is a word as well as a colour; the hold-to-open bar is motion plus the words "Hold OK".
- Exactly one ring: on a row, or in the details panel while a picker is open.
- Emulator: hold OK on Advanced and release at 300 ms to confirm nothing opens and the hint pulses; run Refresh while a channel plays and confirm the picture never stops.

## 7. Owner decisions 2026-09-23 (all five accepted)

1. **Descriptions live in the details panel, not under each row.** Rows stay one line at 20 sp; the explanation gets 288 dp of room at 16 sp. Both the plan and the spec are silent on where descriptions go.
2. **Pickers open in the details panel** (the Favorites pattern), not as a dialog.
3. **Startup's favorite choice lists the favorites by number and name** in the picker, so choosing one never needs a second screen.
4. **Diagnostics shows the format word** (the spec asks for "format" in Diagnostics and also says nothing on screen says HLS, TS or DASH). Diagnostics is the one owner-facing technical screen, a plain row inside Advanced, which is itself entered only by holding OK, so showing the real word there is the reading this design takes. If you would rather keep the rule absolute, the row is dropped and Diagnostics shows the address only.
5. Withdrawn 2026-09-23: user playlists were cut from the product, so no on-screen name is needed.
