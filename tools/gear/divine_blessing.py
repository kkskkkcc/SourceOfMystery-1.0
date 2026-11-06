"""神威天佑 v3: celestial jade-and-gold war plate with a taiji sun mirror.

Run from the repository root:  python tools/gear/divine_blessing.py
Writes the runtime geo, albedo and glow mask, plus a part manifest.
Rotations are right-handed degrees in geo space (see forge3d.Model.box).
Side s = -1 is the wearer's right (geo -X), s = +1 the left.
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from forge3d import ASSETS, ROOT, Model  # noqa: E402
from paint import register, rgb  # noqa: E402

ID = 'divine_blessing'
register('jade', 'plate', rgb('EDE8DC'), shade=rgb('9AA3AD'), light=rgb('FFFDF8'), vein=9, gloss=18)
register('jade_scale', 'scale', rgb('E4DFD2'), shade=rgb('8E96A0'), light=rgb('FFFCF4'))
register('gold', 'metal', rgb('D9B468'), shade=rgb('86601F'), light=rgb('FFF1BC'), rim_light=55, bevel=.08)
register('ink', 'lacquer', rgb('1A1D25'), shade=rgb('07080B'), gloss=30, rim_light=22, rim_dark=20)
register('silk', 'cloth', rgb('242A3A'), shade=rgb('0B0D14'))
register('sunstone', 'gem', rgb('FFF4D2'), shade=rgb('D9A44A'), light=rgb('FFFFFF'), facets=4)
register('sunglow', 'glow', rgb('FFE7A6'), light=rgb('FFFFFF'))

GLOW = (255, 238, 196)
TRIM = ('gold', .16)
ALL = ('north', 'south', 'east', 'west', 'up', 'down')


def cloud(at, width=.05, style='inlay', material='gold'):
    return {'kind': 'cloud', 'style': style, 'material': material, 'width': width, 'at': at}


def fret(y0=0.0, y1=1.0, width=.07):
    return {'kind': 'fret', 'style': 'inlay', 'material': 'gold', 'width': width, 'y0': y0, 'y1': y1}


def glow_line(width=.18):
    return {'kind': 'lines', 'style': 'inlay', 'material': 'sunglow', 'width': width,
            'paths': [[(.5, .93), (.5, .1)]], 'glow': GLOW, 'glow_alpha': .9}


def torso(m, body):
    m.box('core', body, (0, 18.2, 0), (8.9, 11.4, 4.9), 'ink',
          decals={f: [{'kind': 'rivets', 'style': 'inlay', 'material': 'gold', 'r': .05,
                       'at': [(x / 5, y / 8) for x in range(1, 5) for y in range(1, 8)]}] for f in ('east', 'west')})

    def side(s):
        outer = 'west' if s < 0 else 'east'
        # Convex pectoral: main plate, upper bevel toward the collar, lower bevel toward the waist.
        m.box(f'pectoral_{s}', body, (s * 2.3, 20.45, -2.82), (4.1, 4.4, .8), 'jade', rotation=(0, -s * 16, 0),
              trim=TRIM, trim_faces=('north',), decals={'north': [cloud([(.5 - s * .05, .7, .46, -s)], .05)]})
        m.box(f'pectoral_top_{s}', body, (s * 2.3, 23.1, -2.45), (4.0, 1.25, .75), 'jade', rotation=(34, -s * 14, 0),
              trim=('gold', .1), trim_faces=('north', 'up'))
        m.box(f'pectoral_low_{s}', body, (s * 2.3, 17.95, -2.62), (3.95, 1.0, .7), 'jade', rotation=(-24, -s * 15, 0),
              trim=('gold', .1), trim_faces=('north',))
        m.box(f'pectoral_wing_{s}', body, (s * 4.3, 20.3, -1.7), (.85, 5.2, 2.2), 'jade', rotation=(0, -s * 52, 0),
              trim=TRIM, trim_faces=('north', outer))
        m.box(f'flank_{s}', body, (s * 4.62, 18.9, .6), (.7, 8.0, 3.6), 'jade', rotation=(0, 0, -s * 2),
              trim=TRIM, decals={outer: [cloud([(.5, .45, .62, s)], .05)]})
        m.box(f'strap_{s}', body, (s * 2.9, 24.15, .2), (1.4, .5, 5.2), 'gold')
    m.mirror(side)
    m.box('collar_front', body, (0, 24.05, -2.55), (5.8, .6, .9), 'gold', rotation=(25, 0, 0))
    m.box('collar_back', body, (0, 24.15, 2.55), (6.6, .8, .9), 'jade', rotation=(-30, 0, 0), trim=('gold', .1))

    # Sun mirror: eight tapered rays behind a round bezel and the taiji face.
    cx, cy, cz = 0, 20.6, -3.95
    for i in range(12):
        a = i / 12 * math.tau
        for k, (r, w, h) in enumerate(((2.25, .5, .7), (2.7, .26, .5))):
            m.box(f'ray_{i}_{k}', body, (cx + r * math.cos(a), cy + r * math.sin(a), cz + .28 + .06 * k), (w, h, .24),
                  'gold', rotation=(0, 0, math.degrees(a) - 90), bevel=.05)
    m.ring('bezel', body, (cx, cy, cz - .06), 1.88, 20, .44, .5, 'gold', bevel=.05)
    m.ring('bezel_inner', body, (cx, cy, cz + .05), 1.6, 20, .18, .35, 'jade', bevel=.03)
    m.bone('taiji_emblem', (cx, cy, cz), parent=body)
    m.box('mirror_face', 'taiji_emblem', (cx, cy, cz), (3.5, 3.5, .12), 'ink',
          hide=('south', 'east', 'west', 'up', 'down'), density=96, bevel=0, ao=False,
          decals={'north': [
              {'kind': 'rays', 'style': 'inlay', 'material': 'gold', 'count': 32, 'r0': .31, 'r1': .45, 'width': .014,
               'glow': GLOW, 'glow_alpha': .5, 'tint': .15},
              {'kind': 'trigrams', 'style': 'inlay', 'material': 'gold', 'r': .43, 'width': .02},
              {'kind': 'circle', 'style': 'inlay', 'material': 'gold', 'r': .3, 'width': .016},
              {'kind': 'taiji', 'style': 'taiji', 'r': .54}]})

    for i, (y, w) in enumerate(((16.45, 7.7), (15.1, 7.35), (13.8, 7.0))):
        m.box(f'abdomen_{i}', body, (0, y, -2.72 - .06 * i), (w, 1.45, .55), 'jade', rotation=(12, 0, 0),
              trim=('gold', .12), trim_faces=('north',),
              decals={'north': [cloud([(.2, .55, .95, 1), (.8, .55, .95, -1)], .07)]} if i == 0 else None)
        m.box(f'lumbar_{i}', body, (0, y, 2.72 + .06 * i), (w, 1.45, .55), 'jade', rotation=(-12, 0, 0),
              trim=('gold', .12), trim_faces=('south',))

    m.box('belt', body, (0, 12.75, 0), (9.3, 1.15, 5.3), 'ink',
          decals={f: [fret(.14, .86, .08)] for f in ('north', 'south', 'east', 'west')})
    m.box('buckle', body, (0, 12.85, -2.95), (2.8, 2.0, .5), 'gold',
          decals={'north': [cloud([(.5, .64, .95, 1)], .07, style='engrave')]})
    m.box('buckle_gem', body, (0, 12.95, -3.25), (.8, .8, .22), 'sunstone', glow_face=True)
    for s in (-1, 1):
        m.box(f'belt_plaque_{s}', body, (s * 3.05, 12.8, -2.85), (1.2, 1.3, .3), 'gold',
              decals={'north': [{'kind': 'circle', 'style': 'engrave', 'r': .3, 'width': .12}]})

    m.box('back_plate', body, (0, 20.0, 2.72), (8.0, 7.6, .7), 'jade', trim=TRIM, trim_faces=('south',),
          decals={'south': [cloud([(.2, .84, .36, 1), (.8, .84, .36, -1)], .05)]})
    m.ring('back_ring', body, (0, 21.0, 3.2), 1.45, 16, .34, .32, 'gold', bevel=.05)
    m.box('back_face', body, (0, 21.0, 3.24), (2.7, 2.7, .1), 'ink', hide=('north', 'east', 'west', 'up', 'down'),
          density=96, bevel=0, ao=False,
          decals={'south': [{'kind': 'rays', 'style': 'inlay', 'material': 'gold', 'count': 16, 'r0': .14, 'r1': .45,
                             'width': .035, 'glow': GLOW, 'glow_alpha': .6, 'tint': .2}]})


def arms(m):
    for s, label in ((-1, 'Right'), (1, 'Left')):
        arm = m.bone('armor' + label + 'Arm', (s * 5, 22, 0))
        out = 'west' if s < 0 else 'east'
        lean = lambda deg: (0, 0, -s * deg)  # positive deg drops the outer edge
        m.box(f'{label}_cap', arm, (s * 6.25, 24.45, 0), (4.9, 1.1, 5.9), 'jade', rotation=lean(9),
              trim=('gold', .12), trim_faces=('up', 'north', 'south', out), decals={'up': [cloud([(.5, .5, .62, s)], .05)]})
        for i in range(3):
            slope = 24 + 10 * i
            m.box(f'{label}_lame_{i}', arm, (s * (7.05 + .55 * i), 23.35 - 1.2 * i, 0), (4.2, 1.55, 6.1 + .2 * i),
                  'jade', rotation=lean(slope), trim=('gold', .15), trim_faces=('north', 'south', out, 'up'))
        # Front and rear cheek plates hide the lame steps and round the shoulder.
        for z, tag in ((-1, 'front'), (1, 'rear')):
            m.box(f'{label}_cheek_{tag}', arm, (s * 7.35, 22.75, z * 3.2), (3.4, 3.0, .4), 'jade',
                  rotation=(0, s * z * 10, -s * 28), trim=TRIM, trim_faces=('north' if z < 0 else 'south',),
                  decals={('north' if z < 0 else 'south'): [cloud([(.5, .55, .7, -s * z)], .055)]})
        m.box(f'{label}_lame_hem', arm, (s * 8.2, 20.95, 0), (2.6, .35, 6.7), 'gold', rotation=lean(44))
        # Feather crest fanned from one root on the shoulder.
        root = (s * 7.55, 24.9, 0)
        for j, (fan, height) in enumerate(((-30, 3.0), (0, 4.3), (30, 3.2))):
            for layer, (mat, thick, wid, shrink) in enumerate((('gold', .42, 1.25, 0), ('jade', .2, .7, .55))):
                hh = height - shrink
                m.box(f'{label}_feather_{j}_{layer}', arm,
                      (root[0] + s * .12 * layer, root[1] + hh / 2, root[2]), (thick, hh, wid), mat,
                      rotation=(fan, 0, -s * 34), pivot=root, bevel=.06,
                      decals={out: [glow_line(.2)]} if layer == 0 else None)
        m.box(f'{label}_crest_root', arm, root, (1.0, .8, 1.6), 'gold', rotation=lean(24))
        # Sun medallion facing outward and slightly up.
        mc = (s * 9.05, 22.15, 0)
        m.ring(f'{label}_medal_ring', arm, mc, .8, 12, .24, .25, 'gold', plane='yz', tilt=(0, 0, s * 32), bevel=.05)
        m.box(f'{label}_medal_face', arm, mc, (.1, 1.42, 1.42), 'ink', rotation=(0, 0, s * 32),
              hide=tuple(f for f in ALL if f != out), density=96, bevel=0, ao=False,
              decals={out: [{'kind': 'taiji', 'style': 'taiji', 'r': .62},
                            {'kind': 'circle', 'style': 'inlay', 'material': 'gold', 'r': .4, 'width': .05}]})
        m.box(f'{label}_sleeve', arm, (s * 6, 18.6, 0), (4.75, 4.6, 4.75), 'silk',
              decals={f: [fret(.62, .92, .05)] for f in ('north', 'south', out)})
        m.box(f'{label}_bracer', arm, (s * 6, 14.7, 0), (4.6, 4.1, 4.6), 'jade', trim=('gold', .14),
              decals={f: [cloud([(.5, .52, .64, s)], .05)] for f in ('north', 'south', out)})
        m.box(f'{label}_bracer_rim', arm, (s * 6, 16.9, 0), (4.8, .4, 4.8), 'gold')
        m.box(f'{label}_cuff', arm, (s * 6, 12.6, 0), (5.0, .7, 5.0), 'gold')
        m.box(f'{label}_bracer_fin', arm, (s * 8.45, 14.9, 0), (.4, 3.0, 1.3), 'gold', rotation=lean(-8),
              decals={out: [glow_line(.22)]})


def tassets(m):
    for s, label in ((-1, 'Right'), (1, 'Left')):
        leg = m.bone('armor' + label + 'Leg', (s * 2, 12, 0))
        out = 'west' if s < 0 else 'east'
        opts = dict(scale_rows=1.6, scale_cols=1.3)
        m.box(f'{label}_tasset_front', leg, (s * 2.15, 9.55, -2.75), (4.1, 5.1, .5), 'jade_scale', rotation=(9, 0, s * 3),
              trim=TRIM, trim_faces=('north',), **opts)
        m.box(f'{label}_tasset_side', leg, (s * 4.5, 9.7, 0), (.5, 4.7, 4.6), 'jade_scale', rotation=(0, 0, s * 9),
              trim=TRIM, trim_faces=(out,), **opts)
        m.box(f'{label}_tasset_back', leg, (s * 2.15, 9.55, 2.75), (4.1, 5.1, .5), 'jade_scale', rotation=(-9, 0, s * 3),
              trim=TRIM, trim_faces=('south',), **opts)
        m.box(f'{label}_tasset_hem', leg, (s * 2.15, 6.98, -3.17), (4.1, .35, .35), 'gold', rotation=(9, 0, s * 3))
        m.box(f'{label}_tasset_jewel', leg, (s * 2.15, 10.7, -3.08), (.6, .6, .2), 'sunstone', glow_face=True)


def build():
    m = Model(ID, density=24, atlas_width=2048, bounds=(3, 3.2, 1.4))
    body = m.bone('armorBody', (0, 24, 0))
    m.bone('armorHead', (0, 24, 0))
    torso(m, body)
    arms(m)
    tassets(m)
    return m


if __name__ == '__main__':
    model = build()
    out = model.export(ASSETS / 'geo/armor/divine_blessing.geo.json',
                       ASSETS / 'textures/armor/divine_blessing.png',
                       ASSETS / 'textures/armor/divine_blessing_glowmask.png', seed=1105,
                       manifest_path=ROOT / 'tools/gear/out/divine_blessing.parts.json')
    print(out)
