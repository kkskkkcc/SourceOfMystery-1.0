"""神威天佑 v4: black-and-white light armor.

A fitted black under-suit carries slim white enamel plates with silver edges.
The taiji mirror sits between the pectorals; gold is limited to a few accents.
Run from the repository root:  python tools/gear/divine_blessing.py
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
register('enamel', 'plate', rgb('F3F2EE'), shade=rgb('A7ABB3'), light=rgb('FFFFFF'), vein=3, gloss=26)
register('silver', 'metal', rgb('C9CDD4'), shade=rgb('6E737C'), light=rgb('F6F8FB'), rim_light=50)
register('gold', 'metal', rgb('D6B46C'), shade=rgb('80602A'), light=rgb('FFF0C2'), rim_light=50)
register('lacquer', 'lacquer', rgb('1C1D22'), shade=rgb('08090B'), gloss=34)
register('suit', 'cloth', rgb('25262C'), shade=rgb('0E0E11'), weave=2.4)
register('sash', 'cloth', rgb('EDEBE6'), shade=rgb('9EA1A8'), weave=2.0)
register('pearl', 'glow', rgb('F4F7FF'), light=rgb('FFFFFF'))

GLOW = (236, 242, 255)
EDGE = ('silver', .12)
THIN = ('silver', .08)
ALL = ('north', 'south', 'east', 'west', 'up', 'down')


def cloud(at, width=.045, material='silver', style='inlay'):
    return {'kind': 'cloud', 'style': style, 'material': material, 'width': width, 'at': at}


def line(paths, width=.05, material='silver', glow=False):
    spec = {'kind': 'lines', 'style': 'inlay', 'material': material, 'width': width, 'paths': paths}
    if glow:
        spec.update(glow=GLOW, glow_alpha=.85)
    return spec


def torso(m, body):
    # Fitted under-suit: only slightly larger than the skin's jacket layer.
    quilt = [[(x / 6, .02), (x / 6, .98)] for x in range(1, 6)] + [[(.02, y / 8), (.98, y / 8)] for y in range(1, 8)]
    m.box('suit', body, (0, 18.1, 0), (8.7, 11.9, 4.7), 'suit',
          decals={f: [line(quilt, .012, 'lacquer')] for f in ('north', 'south', 'east', 'west')})
    m.box('collar', body, (0, 24.25, 0), (6.4, .9, 4.9), 'lacquer', trim=THIN, trim_faces=('up',))

    def side(s):
        outer = 'west' if s < 0 else 'east'
        # Slim pectoral plate angled toward the sternum; inner edge dips into a V.
        m.box(f'pectoral_{s}', body, (s * 2.55, 21.3, -2.6), (3.0, 3.8, .42), 'enamel',
              rotation=(4, s * 14, -s * 1.5), trim=EDGE, trim_faces=('north',),
              decals={'north': [{'kind': 'border', 'style': 'inlay', 'material': 'silver', 'inset': .14, 'width': .03},
                                cloud([(.5 - s * .05, .58, .46, -s)])]})
        m.box(f'pectoral_rim_{s}', body, (s * 2.6, 23.3, -2.45), (2.9, .32, .45), 'silver', rotation=(18, s * 13, 0))
        m.box(f'pectoral_rivet_{s}', body, (s * 3.7, 22.7, -2.95), (.36, .36, .14), 'gold', rotation=(0, s * 14, 0))
        # Side plate following the ribs, open toward the back for a light silhouette.
        m.box(f'rib_{s}', body, (s * 4.42, 19.6, -.55), (.4, 5.2, 3.0), 'enamel', rotation=(0, 0, -s * 3),
              trim=THIN, trim_faces=(outer,), decals={outer: [line([[(.2, .2), (.8, .2)], [(.2, .8), (.8, .8)]], .04)]})
        # Shoulder strap and the strap buckle on the back.
        m.box(f'strap_{s}', body, (s * 2.6, 24.2, .25), (1.2, .42, 5.0), 'lacquer', trim=THIN, trim_faces=('up',))
        m.box(f'strap_clasp_{s}', body, (s * 2.6, 23.5, 2.55), (1.0, .9, .32), 'silver')
    m.mirror(side)

    # Sternum ridge and the taiji mirror between the pectorals.
    m.box('sternum', body, (0, 18.9, -2.6), (.9, 4.6, .32), 'lacquer', decals={'north': [line([[(.5, .95), (.5, .05)]], .12, 'pearl', glow=True)]})
    cx, cy, cz = 0, 21.2, -3.0
    m.ring('bezel', body, (cx, cy, cz), 1.28, 18, .26, .34, 'silver', bevel=.04)
    m.ring('bezel_gold', body, (cx, cy, cz - .1), 1.05, 18, .1, .2, 'gold', bevel=.02)
    m.bone('taiji_emblem', (cx, cy, cz), parent=body)
    m.box('mirror_face', 'taiji_emblem', (cx, cy, cz + .04), (2.3, 2.3, .1), 'lacquer',
          hide=('south', 'east', 'west', 'up', 'down'), density=110, bevel=0, ao=False,
          decals={'north': [
              {'kind': 'trigrams', 'style': 'inlay', 'material': 'silver', 'r': .43, 'width': .022},
              {'kind': 'circle', 'style': 'inlay', 'material': 'silver', 'r': .31, 'width': .018},
              {'kind': 'taiji', 'style': 'taiji', 'r': .56}]})

    # Two narrow abdominal lames over the suit.
    for i, (y, w) in enumerate(((17.0, 6.6), (15.75, 6.1))):
        m.box(f'abdomen_{i}', body, (0, y, -2.52 - .05 * i), (w, 1.05, .36), 'enamel', rotation=(10, 0, 0),
              trim=EDGE, trim_faces=('north',),
              decals={'north': [line([[(.08, .5), (.38, .5)], [(.62, .5), (.92, .5)]], .08, 'silver'),
                                {'kind': 'circle', 'style': 'inlay', 'material': 'silver', 'r': .22, 'width': .1}]})
    # Cinched sash with a silver buckle and a small gold pearl.
    m.box('sash', body, (0, 13.4, 0), (8.95, 1.25, 4.95), 'sash',
          decals={f: [line([[(0, .18), (1, .18)], [(0, .82), (1, .82)]], .06, 'lacquer')] for f in ('north', 'south', 'east', 'west')})
    m.box('buckle', body, (0, 13.4, -2.62), (1.9, 1.5, .34), 'silver',
          decals={'north': [{'kind': 'circle', 'style': 'engrave', 'r': .32, 'width': .1}]})
    m.box('buckle_pearl', body, (0, 13.4, -2.84), (.55, .55, .16), 'gold')
    # Hanging sash tail on the left hip.
    m.box('sash_tail', body, (2.9, 11.7, -2.6), (1.2, 2.6, .18), 'sash', rotation=(6, 0, 6),
          decals={'north': [line([[(.5, 0), (.5, 1)]], .1, 'lacquer')]})

    # Back: a slim white shoulder-blade plate with a small rayed mark.
    m.box('back_plate', body, (0, 21.2, 2.55), (6.8, 4.6, .4), 'enamel', rotation=(-6, 0, 0), trim=EDGE,
          trim_faces=('south',),
          decals={'south': [{'kind': 'rays', 'style': 'inlay', 'material': 'silver', 'count': 12, 'r0': .14, 'r1': .34,
                             'width': .03},
                            {'kind': 'circle', 'style': 'inlay', 'material': 'silver', 'r': .12, 'width': .03}]})
    m.box('back_spine', body, (0, 16.6, 2.5), (.8, 4.2, .3), 'lacquer', trim=THIN, trim_faces=('south',))


def arms(m):
    for s, label in ((-1, 'Right'), (1, 'Left')):
        arm = m.bone('armor' + label + 'Arm', (s * 5, 22, 0))
        out = 'west' if s < 0 else 'east'
        lean = lambda deg: (0, 0, -s * deg)  # positive deg drops the outer edge
        # Small two-lame spaulder that stays close to the arm.
        m.box(f'{label}_cap', arm, (s * 6.15, 24.35, 0), (4.4, .55, 4.9), 'enamel', rotation=lean(10),
              trim=EDGE, trim_faces=('up', out), decals={'up': [cloud([(.5, .5, .55, s)], .05)]})
        m.box(f'{label}_lame', arm, (s * 6.95, 23.3, 0), (3.5, 1.25, 5.1), 'enamel', rotation=lean(30),
              trim=EDGE, trim_faces=(out, 'north', 'south'))
        m.box(f'{label}_lame_edge', arm, (s * 7.55, 22.65, 0), (2.0, .22, 5.2), 'silver', rotation=lean(34))
        m.box(f'{label}_under', arm, (s * 6.6, 23.55, 0), (3.6, 1.3, 4.75), 'lacquer', rotation=lean(20))
        m.box(f'{label}_lip', arm, (s * 7.6, 22.15, 0), (2.3, .7, 4.6), 'enamel', rotation=lean(42),
              trim=THIN, trim_faces=(out,))
        # Black upper sleeve with a white cuff stripe.
        m.box(f'{label}_sleeve', arm, (s * 6, 19.4, 0), (4.55, 4.4, 4.55), 'suit',
              decals={f: [line([[(0, .9), (1, .9)]], .06, 'sash')] for f in ('north', 'south', out)})
        # White forearm guard with silver rims and a glowing pearl line.
        m.box(f'{label}_wrap', arm, (s * 6, 14.6, 0), (4.4, 3.8, 4.4), 'suit')
        m.box(f'{label}_guard_out', arm, (s * 8.28, 14.5, 0), (.32, 3.2, 3.6), 'enamel', trim=EDGE, trim_faces=(out,),
              decals={out: [line([[(.5, .86), (.5, .14)]], .1, 'pearl', glow=True)]})
        for z, f in ((-1, 'north'), (1, 'south')):
            m.box(f'{label}_guard_{f}', arm, (s * 6.75, 14.5, z * 2.28), (2.3, 3.0, .3), 'enamel', trim=EDGE,
                  trim_faces=(f,), decals={f: [cloud([(.5, .5, .62, -s * z)], .045)]})
        m.box(f'{label}_bracer_rim', arm, (s * 6, 16.6, 0), (4.65, .32, 4.65), 'silver')
        m.box(f'{label}_cuff', arm, (s * 6, 12.65, 0), (4.7, .5, 4.7), 'lacquer', trim=THIN, trim_faces=(out, 'north', 'south'))



def legs(m):
    for s, label in ((-1, 'Right'), (1, 'Left')):
        leg = m.bone('armor' + label + 'Leg', (s * 2, 12, 0))
        out = 'west' if s < 0 else 'east'
        # Short white plate over a black cloth panel: front and side only.
        m.box(f'{label}_fauld', leg, (s * 2.05, 10.9, -2.5), (3.7, 2.3, .34), 'enamel', rotation=(8, 0, s * 3),
              trim=EDGE, trim_faces=('north',), decals={'north': [cloud([(.5, .5, .55, s)], .05)]})
        m.box(f'{label}_panel', leg, (s * 2.05, 8.6, -2.42), (3.5, 2.6, .16), 'suit', rotation=(5, 0, s * 3),
              decals={'north': [line([[(.08, 0), (.08, .92), (.92, .92), (.92, 0)]], .05, 'sash')]})
        m.box(f'{label}_side', leg, (s * 4.32, 10.4, 0), (.3, 3.0, 3.6), 'enamel', rotation=(0, 0, s * 7),
              trim=THIN, trim_faces=(out,))
        m.box(f'{label}_back_panel', leg, (s * 2.05, 9.4, 2.42), (3.5, 3.6, .16), 'suit', rotation=(-5, 0, s * 3))


def build():
    m = Model(ID, density=26, atlas_width=2048, bounds=(3, 3.2, 1.4))
    body = m.bone('armorBody', (0, 24, 0))
    m.bone('armorHead', (0, 24, 0))
    torso(m, body)
    arms(m)
    legs(m)
    return m


if __name__ == '__main__':
    model = build()
    out = model.export(ASSETS / 'geo/armor/divine_blessing.geo.json',
                       ASSETS / 'textures/armor/divine_blessing.png',
                       ASSETS / 'textures/armor/divine_blessing_glowmask.png', seed=1107,
                       manifest_path=ROOT / 'tools/gear/out/divine_blessing.parts.json')
    print(out)
