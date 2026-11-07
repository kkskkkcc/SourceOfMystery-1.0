"""Shared light-armor parts: a fitted under-suit with slim plates.

Every part takes material names, so each series supplies its own palette and
adds signature pieces on top. Geo space, side s = -1 is the wearer's right.
Pieces stay within about 0.9 px of the skin's outer layer to keep a light silhouette.
"""
from kit import outer


def line(paths, width=.05, material='silver', glow=None, alpha=.85):
    spec = {'kind': 'lines', 'style': 'inlay', 'material': material, 'width': width, 'paths': paths}
    if glow:
        spec.update(glow=glow, glow_alpha=alpha, tint=.9)
    return spec


def border(material, inset=.14, width=.03):
    return {'kind': 'border', 'style': 'inlay', 'material': material, 'inset': inset, 'width': width}


def quilt(material, cols=6, rows=8, width=.012):
    paths = [[(x / cols, .02), (x / cols, .98)] for x in range(1, cols)]
    paths += [[(.02, y / rows), (.98, y / rows)] for y in range(1, rows)]
    return line(paths, width, material)


def suit(m, body, cloth, seam, collar, edge):
    m.box('suit', body, (0, 18.1, 0), (8.7, 11.9, 4.7), cloth,
          decals={f: [quilt(seam)] for f in ('north', 'south', 'east', 'west')})
    m.box('collar', body, (0, 24.25, 0), (6.4, .9, 4.9), collar, trim=(edge, .08), trim_faces=('up',))


def pectorals(m, body, plate, edge, inner=None, rivet=None, decal=None, width=3.0, height=3.8, z_tilt=1.5):
    for s in (-1, 1):
        decals = [border(inner or edge)] + (decal(s) if decal else [])
        m.box(f'pectoral_{s}', body, (s * 2.55, 21.3, -2.6), (width, height, .42), plate,
              rotation=(4, s * 14, -s * z_tilt), trim=(edge, .12), trim_faces=('north',), decals={'north': decals})
        m.box(f'pectoral_rim_{s}', body, (s * 2.6, 23.3, -2.45), (width - .1, .32, .45), edge, rotation=(18, s * 13, 0))
        if rivet:
            m.box(f'pectoral_rivet_{s}', body, (s * 3.7, 22.7, -2.95), (.36, .36, .14), rivet, rotation=(0, s * 14, 0))


def ribs(m, body, plate, edge, decal=None):
    for s in (-1, 1):
        o = outer(s)
        m.box(f'rib_{s}', body, (s * 4.42, 19.6, -.55), (.4, 5.2, 3.0), plate, rotation=(0, 0, -s * 3),
              trim=(edge, .08), trim_faces=(o,), decals={o: decal} if decal else None)


def straps(m, body, strap, clasp, edge):
    for s in (-1, 1):
        m.box(f'strap_{s}', body, (s * 2.6, 24.2, .25), (1.2, .42, 5.0), strap, trim=(edge, .08), trim_faces=('up',))
        m.box(f'strap_clasp_{s}', body, (s * 2.6, 23.5, 2.55), (1.0, .9, .32), clasp)


def abdomen(m, body, plate, edge, count=2, decal=None):
    for i, (y, w) in enumerate(((17.0, 6.6), (15.75, 6.1), (14.55, 5.6))[:count]):
        m.box(f'abdomen_{i}', body, (0, y, -2.52 - .05 * i), (w, 1.05, .36), plate, rotation=(10, 0, 0),
              trim=(edge, .12), trim_faces=('north',), decals={'north': decal(i)} if decal else None)


def sash(m, body, cloth, band, buckle, gem=None, gem_glow=False, buckle_decal=None):
    m.box('sash', body, (0, 13.4, 0), (8.95, 1.25, 4.95), cloth,
          decals={f: [line([[(0, .18), (1, .18)], [(0, .82), (1, .82)]], .06, band)] for f in ('north', 'south', 'east', 'west')})
    m.box('buckle', body, (0, 13.4, -2.62), (1.9, 1.5, .34), buckle,
          decals={'north': buckle_decal or [{'kind': 'circle', 'style': 'engrave', 'r': .32, 'width': .1}]})
    if gem:
        m.box('buckle_gem', body, (0, 13.4, -2.84), (.55, .55, .16), gem, glow_face=gem_glow)


def back_plate(m, body, plate, edge, spine, decal=None):
    m.box('back_plate', body, (0, 21.2, 2.55), (6.8, 4.6, .4), plate, rotation=(-6, 0, 0), trim=(edge, .12),
          trim_faces=('south',), decals={'south': decal} if decal else None)
    m.box('back_spine', body, (0, 16.6, 2.5), (.8, 4.2, .3), spine, trim=(edge, .08), trim_faces=('south',))


def spaulder(m, arm, s, label, plate, edge, under, cap_decal=None):
    o = outer(s)
    lean = lambda deg: (0, 0, -s * deg)
    m.box(f'{label}_cap', arm, (s * 6.15, 24.35, 0), (4.4, .55, 4.9), plate, rotation=lean(10),
          trim=(edge, .12), trim_faces=('up', o), decals={'up': cap_decal} if cap_decal else None)
    m.box(f'{label}_under', arm, (s * 6.6, 23.55, 0), (3.6, 1.3, 4.75), under, rotation=lean(20))
    m.box(f'{label}_lame', arm, (s * 6.95, 23.3, 0), (3.5, 1.25, 5.1), plate, rotation=lean(30),
          trim=(edge, .12), trim_faces=(o, 'north', 'south'))
    m.box(f'{label}_lame_edge', arm, (s * 7.55, 22.65, 0), (2.0, .22, 5.2), edge, rotation=lean(34))
    m.box(f'{label}_lip', arm, (s * 7.6, 22.15, 0), (2.3, .7, 4.6), plate, rotation=lean(42),
          trim=(edge, .08), trim_faces=(o,))


def forearm(m, arm, s, label, sleeve, plate, edge, rim, cuff, sleeve_band=None, outer_decal=None, face_decal=None):
    o = outer(s)
    m.box(f'{label}_sleeve', arm, (s * 6, 19.4, 0), (4.55, 4.4, 4.55), sleeve,
          decals={f: [line([[(0, .9), (1, .9)]], .06, sleeve_band)] for f in ('north', 'south', o)} if sleeve_band else None)
    m.box(f'{label}_wrap', arm, (s * 6, 14.6, 0), (4.4, 3.8, 4.4), sleeve)
    m.box(f'{label}_guard_out', arm, (s * 8.28, 14.5, 0), (.32, 3.2, 3.6), plate, trim=(edge, .12), trim_faces=(o,),
          decals={o: outer_decal} if outer_decal else None)
    for z, f in ((-1, 'north'), (1, 'south')):
        m.box(f'{label}_guard_{f}', arm, (s * 6.75, 14.5, z * 2.28), (2.3, 3.0, .3), plate, trim=(edge, .12),
              trim_faces=(f,), decals={f: face_decal(-s * z)} if face_decal else None)
    m.box(f'{label}_bracer_rim', arm, (s * 6, 16.6, 0), (4.65, .32, 4.65), rim)
    m.box(f'{label}_cuff', arm, (s * 6, 12.65, 0), (4.7, .5, 4.7), cuff, trim=(edge, .08), trim_faces=(o, 'north', 'south'))


def faulds(m, leg, s, label, plate, edge, panel, panel_band, decal=None):
    o = outer(s)
    m.box(f'{label}_fauld', leg, (s * 2.05, 10.9, -2.5), (3.7, 2.3, .34), plate, rotation=(8, 0, s * 3),
          trim=(edge, .12), trim_faces=('north',), decals={'north': decal(s)} if decal else None)
    m.box(f'{label}_panel', leg, (s * 2.05, 8.6, -2.42), (3.5, 2.6, .16), panel, rotation=(5, 0, s * 3),
          decals={'north': [line([[(.08, 0), (.08, .92), (.92, .92), (.92, 0)]], .05, panel_band)]})
    m.box(f'{label}_side', leg, (s * 4.32, 10.4, 0), (.3, 3.0, 3.6), plate, rotation=(0, 0, s * 7),
          trim=(edge, .08), trim_faces=(o,))
    m.box(f'{label}_back_panel', leg, (s * 2.05, 9.4, 2.42), (3.5, 3.6, .16), panel, rotation=(-5, 0, s * 3))
