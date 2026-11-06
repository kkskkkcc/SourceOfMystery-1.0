"""Ascension CG stage: arrival dais, five celestial steps and the throne landing.

python tools/divine/generate_cg_stage.py
Writes geo/cinematic/divine_stage.geo.json and textures/cinematic/divine/stage(_glowmask).png.
Geometry matches the walking heights used by the CG keyframes: step i top = (i+1)*0.36 m,
back edge = 2.65 - 1.4 i, depth 1.4 m; landing top 1.8 m from z=-10.8 to -2.95.
Model units are 1/16 m; bone names: dais, step_0..step_4, landing.
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'gear'))
from forge3d import ASSETS, ROOT, Model  # noqa: E402
from paint import register, rgb  # noqa: E402

U = 16  # units per metre
register('st_jade', 'plate', rgb('EEEAE2'), shade=rgb('A7AEB8'), light=rgb('FFFFFC'), vein=12, gloss=14, bevel=.04)
register('st_jade_dark', 'plate', rgb('D7D6D4'), shade=rgb('8F96A0'), light=rgb('F3F3F1'), vein=10, gloss=8, bevel=.04)
register('gold', 'metal', rgb('D9B468'), shade=rgb('86601F'), light=rgb('FFF1BC'), rim_light=55, bevel=.05)
register('st_glow', 'glow', rgb('FFE7A6'), light=rgb('FFFFFF'))
GLOW = (255, 236, 190)


def m3(x, y, z):
    return (x * U, y * U, z * U)


def cloud(at, width=.03):
    return {'kind': 'cloud', 'style': 'inlay', 'material': 'gold', 'width': width, 'at': at}


def build():
    m = Model('divine_stage', density=2.5, atlas_width=2048, bounds=(14, 6, 1))
    # Bones pivot at their own resting origin so the director can raise them.
    m.bone('dais', (0, 0, 0))
    for i in range(5):
        m.bone(f'step_{i}', (0, 0, 0))
    m.bone('landing', (0, 0, 0))

    def box(name, bone, lo, hi, mat, **kw):
        c = tuple((a + b) / 2 for a, b in zip(lo, hi))
        s = tuple(abs(b - a) for a, b in zip(lo, hi))
        return m.box(name, bone, m3(*c), m3(*s), mat, **kw)

    # Arrival dais with a taiji inlay on top.
    box('dais_base', 'dais', (-2.5, -.34, 3.25), (2.5, -.04, 5.8), 'st_jade_dark', trim=('gold', .6))
    box('dais_top', 'dais', (-2.45, -.06, 3.3), (2.45, 0, 5.75), 'st_jade', density=4,
        decals={'up': [{'kind': 'circle', 'style': 'inlay', 'material': 'gold', 'r': .4, 'width': .012},
                       {'kind': 'rays', 'style': 'inlay', 'material': 'gold', 'count': 24, 'r0': .25, 'r1': .38,
                        'width': .006, 'glow': GLOW, 'glow_alpha': .5, 'tint': .25},
                       {'kind': 'taiji', 'style': 'taiji', 'r': .45, 'dark': (60, 62, 68), 'light': (246, 244, 238)}]})
    for s in (-1, 1):
        box(f'dais_rim_{s}', 'dais', (s * 2.5 - .04, -.05, 3.25), (s * 2.5 + .04, .02, 5.8), 'gold')

    for i in range(5):
        top, back = (i + 1) * .36, 2.65 - i * 1.4
        front, half = back + 1.4, 2.4 - i * .06
        b = f'step_{i}'
        box(f'{b}_riser', b, (-half, top - .36, back), (half, top - .02, front), 'st_jade_dark',
            decals={'south': [cloud([(.14, .55, .85, 1), (.86, .55, .85, -1)], .05)]})
        box(f'{b}_tread', b, (-half - .03, top - .05, back), (half + .03, top, front + .03), 'st_jade', density=3,
            decals={'up': [{'kind': 'lines', 'style': 'inlay', 'material': 'st_glow', 'width': .012,
                            'paths': [[(.5, .06), (.5, .94)]], 'glow': GLOW, 'glow_alpha': .7}]})
        box(f'{b}_nosing', b, (-half - .04, top - .06, front - .07), (half + .04, top + .01, front + .03), 'gold')
        for s in (-1, 1):
            x0 = s * half
            box(f'{b}_cheek_{s}', b, (x0 - .09, top - .36, back), (x0 + .09, top + .1, front), 'st_jade',
                trim=('gold', .3), trim_faces=('up',))
            # Baluster at the front of every step, capped in gold with a glowing pearl.
            box(f'{b}_post_{s}', b, (x0 - .08, top, front - .26), (x0 + .08, top + .62, front - .1), 'st_jade',
                decals={f: [cloud([(.5, .5, .7, s)], .08)] for f in ('south', 'north', 'east', 'west')})
            box(f'{b}_post_cap_{s}', b, (x0 - .11, top + .62, front - .29), (x0 + .11, top + .68, front - .07), 'gold')
            box(f'{b}_pearl_{s}', b, (x0 - .045, top + .68, front - .225), (x0 + .045, top + .77, front - .135),
                'st_glow', glow_face=True)
            if i < 4:
                # Rail from this post to the next step's post.
                y0, y1 = top + .58, top + .94
                z0, z1 = front - .18, front - 1.58
                length = math.hypot(y1 - y0, z1 - z0)
                ang = math.degrees(math.atan2(y1 - y0, z0 - z1))
                c = (x0, (y0 + y1) / 2, (z0 + z1) / 2)
                m.box(f'{b}_rail_{s}', b, m3(*c), m3(.07, .07, length), 'gold', rotation=(ang, 0, 0))

    # Throne landing with a gold border and a mandala pointing to the throne.
    box('landing_body', 'landing', (-3.25, 1.44, -10.8), (3.25, 1.74, -2.95), 'st_jade_dark',
        decals={'south': [cloud([(.1, .5, .9, 1), (.3, .5, .9, -1), (.7, .5, .9, 1), (.9, .5, .9, -1)], .05)]})
    box('landing_floor', 'landing', (-3.2, 1.72, -10.75), (3.2, 1.8, -3.0), 'st_jade', density=3,
        decals={'up': [{'kind': 'border', 'style': 'inlay', 'material': 'gold', 'inset': .05, 'width': .006},
                       {'kind': 'lines', 'style': 'inlay', 'material': 'st_glow', 'width': .006,
                        'paths': [[(.5, .98), (.5, .3)]], 'glow': GLOW, 'glow_alpha': .6}]})
    box('landing_mandala', 'landing', (-1.6, 1.8, -9.3), (1.6, 1.805, -6.1), 'st_jade', density=10,
        hide=('north', 'south', 'east', 'west', 'down'), bevel=0,
        decals={'up': [{'kind': 'circle', 'style': 'inlay', 'material': 'gold', 'r': .47, 'width': .008},
                       {'kind': 'circle', 'style': 'inlay', 'material': 'gold', 'r': .34, 'width': .006},
                       {'kind': 'trigrams', 'style': 'inlay', 'material': 'gold', 'r': .43, 'width': .01},
                       {'kind': 'rays', 'style': 'inlay', 'material': 'gold', 'count': 36, 'r0': .1, 'r1': .32,
                        'width': .004, 'glow': GLOW, 'glow_alpha': .55, 'tint': .3}]})
    for s in (-1, 1):
        box(f'landing_rim_{s}', 'landing', (s * 3.25 - .05, 1.74, -10.8), (s * 3.25 + .05, 1.86, -2.95), 'gold')
        for k in range(4):
            z = -3.4 - k * 2.3
            box(f'landing_post_{s}_{k}', 'landing', (s * 3.1 - .1, 1.8, z - .1), (s * 3.1 + .1, 2.55, z + .1), 'st_jade',
                decals={f: [cloud([(.5, .5, .7, s)], .08)] for f in ('south', 'north', 'east', 'west')})
            box(f'landing_cap_{s}_{k}', 'landing', (s * 3.1 - .13, 2.55, z - .13), (s * 3.1 + .13, 2.62, z + .13), 'gold')
            box(f'landing_pearl_{s}_{k}', 'landing', (s * 3.1 - .05, 2.62, z - .05), (s * 3.1 + .05, 2.72, z + .05),
                'st_glow', glow_face=True)
    box('landing_front_rim', 'landing', (-3.3, 1.74, -2.99), (3.3, 1.84, -2.91), 'gold')
    return m


if __name__ == '__main__':
    model = build()
    out = model.export(ASSETS / 'geo/cinematic/divine_stage.geo.json',
                       ASSETS / 'textures/cinematic/divine/stage.png',
                       ASSETS / 'textures/cinematic/divine/stage_glowmask.png', seed=2405,
                       manifest_path=ROOT / 'tools/gear/out/divine_stage.parts.json')
    print(out)
