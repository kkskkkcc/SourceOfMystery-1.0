"""Shared held/thrown scythe geometry and its reserved texture swatches.

Coordinates match the original YSM model so the hand grip, blade locator, and
attack reach remain compatible with the existing boss animations.
"""

import numpy as np


SWATCHES = {
    'obsidian': (28, 23, 36),
    'haft': (52, 43, 64),
    'iron': (73, 68, 83),
    'steel': (116, 111, 127),
    'silver': (179, 177, 190),
    'edge': (224, 221, 232),
    'violet': (113, 53, 139),
    'amethyst': (190, 105, 211),
    'leather': (77, 40, 68),
    'pale': (151, 143, 168),
}
SWATCH_UV = {name: (8 * i, 160) for i, name in enumerate(SWATCHES)}


def cube(origin, size, material, **extra):
    u, v = SWATCH_UV[material]
    face = {'uv': [u, v], 'uv_size': [8, 8]}
    return {'origin': [round(x, 3) for x in origin],
            'size': [round(x, 3) for x in size],
            'uv': {side: dict(face) for side in ('north', 'south', 'east', 'west', 'up', 'down')},
            **extra}


def scythe_cubes():
    c = []
    add = lambda origin, size, material, **kw: c.append(cube(origin, size, material, **kw))

    # Octagonal-looking long haft: the dark core and two narrow reflective rails
    # keep a readable silhouette even when the blade turns edge-on.
    add((-8.65, 31.15, -37.6), (1.5, 1.7, 64.0), 'obsidian')
    add((-8.81, 31.42, -37.2), (0.26, 1.16, 62.9), 'haft')
    add((-7.31, 31.42, -37.2), (0.26, 1.16, 62.9), 'haft')
    add((-8.35, 32.85, -36.9), (0.9, 0.24, 62.0), 'iron')
    add((-8.35, 31.05, -36.9), (0.9, 0.22, 62.0), 'iron')

    # Narrow bands give the shaft a forged, wrapped rhythm; the middle grip is
    # deliberately slim because it passes through the hand bone.
    for z in (-25.0, -14.0, -3.0, 5.5, 21.0):
        add((-8.85, 31.0, z), (1.9, 2.0, 0.8), 'steel')
        add((-8.9, 31.64, z + 0.15), (2.0, 0.33, 0.5), 'violet')
    for z in (8.5, 11.0, 13.5, 16.0, 18.5):
        add((-8.79, 31.08, z), (1.78, 1.82, 0.58), 'leather')
        add((-8.92, 32.57, z + 0.1), (2.04, 0.25, 0.3), 'pale')

    # Butt cap and taper: a visible counterweight balances the massive head.
    add((-9.0, 30.84, 25.8), (2.2, 2.35, 1.8), 'steel')
    add((-8.8, 31.05, 27.45), (1.8, 1.9, 1.8), 'obsidian')
    add((-8.52, 31.33, 29.2), (1.25, 1.35, 2.0), 'silver')
    add((-8.18, 31.65, 30.8), (0.55, 0.7, 1.4), 'amethyst')

    # Reinforced head socket and its purple inset.
    add((-9.45, 30.2, -40.0), (3.1, 3.5, 7.5), 'obsidian')
    add((-9.57, 30.45, -39.4), (3.34, 3.0, 2.0), 'iron')
    add((-9.57, 30.45, -34.9), (3.34, 3.0, 1.8), 'iron')
    add((-9.72, 31.07, -37.8), (0.35, 1.3, 2.7), 'violet')
    add((-6.38, 31.07, -37.8), (0.35, 1.3, 2.7), 'violet')
    add((-8.28, 33.1, -38.2), (0.75, 0.7, 2.6), 'amethyst')
    add((-8.35, 33.2, -41.2), (0.9, 1.0, 2.8), 'steel')
    add((-8.15, 33.43, -43.0), (0.5, 0.55, 1.4), 'edge')

    # Curved, tapering blade in the y/z plane. Each tier has a dark outer
    # backbone, broad steel face, silver bevel and bright cutting lip.
    # Interlocking tiers form a single continuous crescent without a flat slab.
    tiers = [
        (29.0, 31.0, -38.4, -28.1),
        (27.0, 29.0, -38.0, -25.5),
        (25.0, 27.0, -37.3, -23.5),
        (23.0, 25.0, -36.2, -22.1),
        (21.0, 23.0, -34.9, -21.1),
        (19.0, 21.0, -33.1, -20.8),
        (17.0, 19.0, -31.1, -20.9),
        (15.0, 17.0, -28.8, -21.2),
        (13.0, 15.0, -26.3, -21.5),
        (11.0, 13.0, -24.1, -21.9),
        (9.0, 11.0, -22.9, -22.0),
    ]
    for i, (y0, y1, back, lip) in enumerate(tiers):
        width = lip - back
        add((-9.25, y0, back), (2.7, y1 - y0 + 0.08, min(1.25, width * 0.33)), 'obsidian')
        if width > 3:
            add((-8.92, y0 + 0.08, back + 0.85), (2.05, y1 - y0 - 0.08, width - 1.75),
                'steel' if i < 6 else 'silver')
            # Raised spine on both sides stops the blade looking paper-thin.
            add((-9.14, y0 + 0.38, back + 1.45), (0.3, max(0.45, y1 - y0 - 0.75), width * 0.56), 'iron')
            add((-6.85, y0 + 0.38, back + 1.45), (0.3, max(0.45, y1 - y0 - 0.75), width * 0.56), 'iron')
            add((-8.78, y0 + 0.1, lip - 1.15), (1.78, y1 - y0 - 0.1, 1.2), 'silver')
        add((-8.35, y0, lip - 0.34), (0.9, y1 - y0 + 0.08, 0.44), 'edge')

    # Inlaid runes remain legible on both broad sides; they follow the curve.
    for y, z in ((28.0, -32.2), (24.0, -30.8), (20.0, -28.0), (16.0, -25.0)):
        add((-9.37, y, z), (0.24, 1.35, 1.25), 'violet')
        add((-6.67, y, z), (0.24, 1.35, 1.25), 'violet')
        add((-9.48, y + 0.42, z + 0.4), (0.16, 0.5, 0.45), 'amethyst')
        add((-6.51, y + 0.42, z + 0.4), (0.16, 0.5, 0.45), 'amethyst')
    return c


def paint_scythe_swatches(a):
    """Write only to the unused y=160..168 atlas strip."""
    rng = np.random.default_rng(817)
    for name, rgb in SWATCHES.items():
        u, v = SWATCH_UV[name]
        for y in range(8):
            for x in range(8):
                grain = int(rng.integers(-6, 7))
                highlight = int((x - y) * 0.9)
                a[v + y, u + x, :3] = np.clip(np.array(rgb) + grain + highlight, 0, 255)
                a[v + y, u + x, 3] = 255
