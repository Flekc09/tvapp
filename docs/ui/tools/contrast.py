"""WCAG 2.2 contrast for TV App tokens, computed against the worst-case picture behind a translucent panel.

Run: python3 docs/ui/tools/contrast.py
Every pair printed here must appear in docs/ui/design-tokens.md with the same ratio. Change a token, re-run, paste.
"""
def hx(c): c = c.lstrip('#'); return tuple(int(c[i:i+2], 16) for i in (0, 2, 4))
def h(rgb): return '#%02X%02X%02X' % rgb
def lum(rgb):
    def ch(v):
        v /= 255; return v / 12.92 if v <= 0.03928 else ((v + 0.055) / 1.055) ** 2.4
    r, g, b = rgb; return 0.2126 * ch(r) + 0.7152 * ch(g) + 0.0722 * ch(b)
def cr(a, b):
    la, lb = lum(a), lum(b); return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
def over(fg, alpha, bg): return tuple(round(alpha * f + (1 - alpha) * b) for f, b in zip(fg, bg))

WHITE, BLACK = hx('#FFFFFF'), hx('#000000')
PANEL = hx('#1B2027')
SCRIM_A, PANEL_A, PANEL_STRONG_A, FOCUS_TINT_A, TRACK_A = 0.45, 0.85, 0.95, 0.10, 0.24
TEXT, MUTED, ACCENT = hx('#F3F5F8'), hx('#C3CAD3'), hx('#A8C7FA')
MARK_OK, MARK_WARN, MARK_BAD, FOCUS_BORDER = hx('#7FD1A0'), hx('#F5CF6B'), hx('#F49A9A'), hx('#FFFFFF')

def surface(scrim_a, panel_a, picture): return over(PANEL, panel_a, over(BLACK, scrim_a, picture))

if __name__ == '__main__':
    for picture, pname in ((WHITE, 'white frame (worst case)'), (BLACK, 'black frame')):
        print(f'\n== behind the panel: {pname} ==')
        surfaces = {
            'surface-banner (panel only)': surface(0, PANEL_A, picture),
            'surface-overlay (scrim + panel)': surface(SCRIM_A, PANEL_A, picture),
            'surface-dialog (scrim + strong panel)': surface(SCRIM_A, PANEL_STRONG_A, picture),
        }
        surfaces['surface-overlay, focused row'] = over(WHITE, FOCUS_TINT_A, surfaces['surface-overlay (scrim + panel)'])
        for sname, bg in surfaces.items():
            print(f'{sname} -> {h(bg)}')
            for lbl, fg, floor in (('text', TEXT, 4.5), ('text-muted', MUTED, 4.5), ('accent', ACCENT, 4.5),
                                   ('mark-ok', MARK_OK, 3), ('mark-warn', MARK_WARN, 3), ('mark-bad', MARK_BAD, 3), ('focus-border', FOCUS_BORDER, 3)):
                r = cr(fg, bg); print(f'   {lbl:13s} {h(fg)}  {r:5.2f}:1  {"ok" if r >= floor else "FAIL"} (floor {floor})')
    ov = surface(SCRIM_A, PANEL_A, WHITE)
    print('\nprogress fill (accent) vs track (white .24 over overlay):', f'{cr(ACCENT, over(WHITE, TRACK_A, ov)):.2f}:1 (floor 3)')
    sc = over(BLACK, SCRIM_A, WHITE)
    print(f'\nscrim alone over white -> {h(sc)}: text {cr(TEXT, sc):.2f}:1, muted {cr(MUTED, sc):.2f}:1 -> no text may sit on the scrim without a panel (these are informational, not FAIL lines)')
