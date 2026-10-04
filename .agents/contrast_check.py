"""WCAG 2.1 contrast check for both palettes in ui/theme/Theme.kt.

Mirrors the token values by hand; keep in sync if the palettes change.
AA needs 4.5:1 for body text, 3.0:1 for large text (>=18.66sp bold / 24sp)
and for non-text UI (icons, borders, focus rings).
"""


def lin(c):
    c /= 255
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def lum(hexstr):
    h = hexstr.lstrip("#")
    if len(h) == 8:  # AARRGGBB -> ignore alpha
        h = h[2:]
    r, g, b = int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)
    return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)


def ratio(fg, bg):
    a, b = lum(fg), lum(bg)
    hi, lo = max(a, b), min(a, b)
    return (hi + 0.05) / (lo + 0.05)


DARK = {
    "midnight": "#0B0E1A", "surface1": "#141829", "surface2": "#1C2138",
    "violet": "#8F79FF", "violetDeep": "#8168FF", "cyan": "#4DD0E1", "mint": "#34D399",
    "coral": "#FF6B81", "amber": "#FFC46B",
    "textPrimary": "#F2F4FF", "textSecondary": "#8A91B4",
    "onAccent": "#0B0E1A",
}

LIGHT = {
    "midnight": "#F5F6FC", "surface1": "#FFFFFF", "surface2": "#EDEFF9",
    "violet": "#5B3DF5", "violetDeep": "#4026D6", "cyan": "#0E7C8C", "mint": "#0F7A57",
    "coral": "#C2334A", "amber": "#8A6212",
    "textPrimary": "#14172B", "textSecondary": "#5A6180",
    "onAccent": "#FFFFFF",
}

# (foreground token, background token, minimum required, human label)
PAIRS = [
    ("textPrimary", "midnight", 4.5, "body text on page"),
    ("textPrimary", "surface1", 4.5, "body text on card"),
    ("textPrimary", "surface2", 4.5, "body text on raised surface"),
    ("textSecondary", "midnight", 4.5, "muted text on page"),
    ("textSecondary", "surface1", 4.5, "muted text on card"),
    ("textSecondary", "surface2", 4.5, "muted text on raised surface"),
    ("coral", "midnight", 4.5, "coral on page"),
    ("amber", "midnight", 4.5, "amber on page"),
    ("mint", "midnight", 4.5, "mint on page"),
    ("cyan", "midnight", 4.5, "cyan on page"),
    ("violet", "midnight", 4.5, "violet on page"),
    ("cyan", "surface1", 4.5, "cyan accent on card"),
    ("mint", "surface1", 4.5, "mint accent on card"),
    ("violet", "surface1", 4.5, "violet accent on card"),
    ("coral", "surface1", 4.5, "coral accent on card"),
    ("amber", "surface1", 4.5, "amber accent on card"),
    ("onAccent", "cyan", 4.5, "button label on filled accent"),
    ("coral", "surface2", 3.0, "coral icon on raised surface"),
    ("amber", "surface2", 3.0, "amber icon on raised surface"),
    # Glyphs inside filled accent containers (FABs, buttons, the share card). These are
    # icons, not body copy, and the default 24dp ones clear 3:1 — but the 18-20dp ones
    # used inside buttons fall under the "large text" test, so 4.5:1 is the honest bar.
    ("onAccent", "violet", 4.5, "glyph on filled violet (FAB/button)"),
    ("onAccent", "violetDeep", 4.5, "glyph on deep violet"),
    ("onAccent", "amber", 4.5, "glyph on filled amber (share card)"),
]


def run(name, pal):
    print(f"\n=== {name} ===")
    fails = 0
    for fg, bg, need, label in PAIRS:
        r = ratio(pal[fg], pal[bg])
        ok = r >= need
        fails += 0 if ok else 1
        print(f"  {'PASS' if ok else 'FAIL'}  {r:5.2f}:1  (need {need})  {label}"
              f"  [{fg} on {bg}]")
    return fails


if __name__ == "__main__":
    total = run("DARK", DARK) + run("LIGHT", LIGHT)
    print(f"\nRESULT: {'PASS' if total == 0 else str(total) + ' FAILING PAIR(S)'}")