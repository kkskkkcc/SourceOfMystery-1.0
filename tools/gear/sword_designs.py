"""Sword designs for tiers 2-5; tier 1 lives in swords.py. Imported by swords.py."""
import math

from kit import blade, sword_model
from paint import register, rgb

ALL = ('north', 'south', 'east', 'west', 'up', 'down')


def glow_line(color, width=.3, path=((.5, .95), (.5, .05)), alpha=1.0):
    return {'kind': 'lines', 'style': 'inlay', 'material': 'gold', 'width': width, 'paths': [list(path)],
            'glow': color, 'glow_alpha': alpha, 'tint': .95}


def veins(color, seed, count=3, width=.05, alpha=.9):
    return {'kind': 'veins', 'style': 'inlay', 'material': 'gold', 'seed': seed, 'count': count, 'width': width,
            'glow': color, 'glow_alpha': alpha, 'tint': .9}


def wrap(material, count, width=.14, style='inlay', slope=.12):
    return {f: [{'kind': 'lines', 'style': style, 'material': material, 'width': width, 'depth': 45,
                 'paths': [[(0, k / count), (1, k / count + slope)] for k in range(count + 1)]}]
            for f in ('north', 'south', 'east', 'west')}


def spirit_source_sword():
    register('ss_jade', 'plate', rgb('6CC596'), shade=rgb('1F6B4A'), light=rgb('D8FBE6'), vein=18, gloss=26)
    register('ss_edge', 'plate', rgb('E9F3DF'), shade=rgb('9DB59A'), light=rgb('FFFFFF'), vein=4)
    register('ss_wood', 'bone', rgb('7A5636'), shade=rgb('35210F'), light=rgb('B88B5E'))
    register('ss_vine', 'plate', rgb('4E9A55'), shade=rgb('1C4D25'), light=rgb('9AD99A'), vein=6)
    register('ss_ivory', 'plate', rgb('EEE8D2'), shade=rgb('A9A288'), light=rgb('FFFFF6'), vein=5)
    register('ss_gem', 'gem', rgb('8CFFB6'), shade=rgb('1A8A4B'), light=rgb('F0FFF4'), facets=4)
    register('gold', 'metal', rgb('C9A566'), shade=rgb('6E5126'), light=rgb('F5E2B0'))
    green = (150, 255, 185)
    m, r = sword_model('spirit_source_sword')

    def midrib(i, n):
        return [glow_line(green, .1, ((.5, 1.0), (.5, 0.0)), .95),
                {'kind': 'lines', 'style': 'inlay', 'material': 'ss_vine', 'width': .06,
                 'paths': [[(.5, .85), (.12, .35)], [(.5, .85), (.88, .35)], [(.5, .4), (.2, -.1)], [(.5, .4), (.8, -.1)]],
                 'glow': green, 'glow_alpha': .45, 'tint': .5}]
    blade(m, r, 2.0, 21, 3.6, .65, 'ss_jade', 'ss_edge', tip=4.2, segments=7,
          profile=[.7, .92, 1.05, 1.1, 1.05, .92, .74], edge_w=.28, fuller=midrib)
    for s in (-1, 1):
        for k, (ang, ln, w) in enumerate(((58, 4.2, 1.5), (30, 3.0, 1.1))):
            m.box(f'guard_leaf_{s}_{k}', r, (s * (.8 + ln / 2), 1.3 + k * .5, 0), (ln, w, .45), 'ss_ivory',
                  rotation=(0, 0, s * (90 - ang) * .6), pivot=(s * .8, 1.3 + k * .5, 0), bevel=.08,
                  decals={f: [{'kind': 'lines', 'style': 'inlay', 'material': 'ss_vine', 'width': .12,
                               'paths': [[(.05, .5), (.95, .5)]], 'glow': green, 'glow_alpha': .5, 'tint': .4}]
                          for f in ('north', 'south')})
    m.box('collar', r, (0, 1.2, 0), (2.4, 1.6, 1.5), 'ss_wood', trim=('ss_vine', .12))
    m.box('collar_gem', r, (0, 1.3, 0), (1.0, 1.0, 1.65), 'ss_gem', rotation=(0, 0, 45), glow_face=True)
    m.box('grip', r, (0, -2.6, 0), (1.15, 6.4, 1.15), 'ss_wood', decals=wrap('ss_vine', 5, .16, slope=.16))
    for k in range(3):
        m.box(f'vine_coil_{k}', r, (0, -.6 - k * 2.0, 0), (1.4, .35, 1.4), 'ss_vine', rotation=(0, 25 * k, 0))
    m.box('pommel_seed', r, (0, -6.4, 0), (1.1, 1.6, 1.1), 'ss_gem', rotation=(0, 45, 0), glow_face=True)
    for s in (-1, 1):
        m.box(f'pommel_leaf_{s}', r, (s * .7, -6.1, 0), (1.2, .5, .3), 'ss_vine', rotation=(0, 0, s * -30))
    return m


def dark_source_sword():
    register('ds_obsidian', 'lacquer', rgb('211A2E'), shade=rgb('08060D'), gloss=34, rim_light=26)
    register('ds_edge', 'metal', rgb('6E6480'), shade=rgb('241F2E'), light=rgb('B9AECB'))
    register('ds_bone', 'bone', rgb('3A3540'), shade=rgb('121015'), light=rgb('6C6575'))
    register('ds_iron', 'metal', rgb('4A4552'), shade=rgb('17141B'), light=rgb('8D8698'))
    register('ds_void', 'gem', rgb('C27BFF'), shade=rgb('4A1478'), light=rgb('F6E6FF'), facets=5)
    register('gold', 'metal', rgb('C9A566'), shade=rgb('6E5126'), light=rgb('F5E2B0'))
    violet = (200, 120, 255)
    m, r = sword_model('dark_source_sword')
    blade(m, r, 2.3, 23, 3.6, .75, 'ds_obsidian', 'ds_edge', tip=4.0, segments=7,
          profile=[1.0, .86, 1.02, .84, .98, .8, .9], edge_w=.34,
          fuller=lambda i, n: [veins(violet, 30 + i, 2, .05), glow_line(violet, .07, ((.5, 1), (.5, 0)), .7)])
    for i in range(5):
        for s in (-1, 1):
            m.box(f'hook_{i}_{s}', r, (s * 1.95, 4.4 + i * 3.4, 0), (.9, .6, .45), 'ds_edge', rotation=(0, 0, s * -35))
    eyes = [[(.26, .36), (.37, .36), (.35, .5), (.28, .5)], [(.63, .36), (.74, .36), (.72, .5), (.65, .5)]]
    m.box('skull', r, (0, 1.4, 0), (2.8, 2.4, 1.9), 'ds_bone', trim=('ds_iron', .08),
          decals={f: [{'kind': 'polys', 'style': 'paint', 'color': (10, 6, 14),
                       'shapes': [[(.18, .3), (.44, .3), (.4, .56), (.22, .56)], [(.56, .3), (.82, .3), (.78, .56), (.6, .56)],
                                  [(.45, .66), (.55, .66), (.5, .8)]]},
                      {'kind': 'polys', 'style': 'paint', 'color': (210, 140, 255), 'glow': violet, 'shapes': eyes}]
                  for f in ('north', 'south')})
    for s in (-1, 1):
        m.box(f'horn_{s}', r, (s * 1.9, 2.9, 0), (.55, 2.2, .55), 'ds_iron', rotation=(0, 0, -s * 35))
        m.box(f'horn_tip_{s}', r, (s * 2.75, 4.0, 0), (.35, 1.2, .35), 'ds_edge', rotation=(0, 0, -s * 58))
        for k, (ang, ln) in enumerate(((24, 3.8), (-14, 2.9))):
            m.box(f'wing_{s}_{k}', r, (s * (1.4 + ln / 2), 1.2 - k * .5, 0), (ln, 1.0 - .2 * k, .4), 'ds_iron',
                  rotation=(0, 0, s * ang), pivot=(s * 1.4, 1.2 - k * .5, 0), bevel=.06)
    m.box('grip', r, (0, -2.6, 0), (1.1, 6.0, 1.1), 'ds_bone', decals=wrap('ds_iron', 6, .14, 'engrave', 0))
    m.box('pommel', r, (0, -6.0, 0), (1.5, 1.2, 1.5), 'ds_iron')
    m.box('pommel_core', r, (0, -6.0, 0), (.8, .8, 1.6), 'ds_void', glow_face=True)
    m.box('pommel_spike', r, (0, -7.2, 0), (.5, 1.4, .5), 'ds_edge', rotation=(0, 45, 0))
    return m


def origin_dragon_sword():
    register('od_bone', 'bone', rgb('E6DCC8'), shade=rgb('8A7C66'), light=rgb('FFFBF1'))
    register('od_edge', 'metal', rgb('CBB9E6'), shade=rgb('5E4D7A'), light=rgb('F7F0FF'), rim_light=60)
    register('od_scale', 'scale', rgb('3B2A52'), shade=rgb('140C1E'), light=rgb('7B5CA8'))
    register('od_hide', 'plate', rgb('2C2038'), shade=rgb('0E0914'), light=rgb('5B4675'), vein=8)
    register('od_eye', 'gem', rgb('F07BFF'), shade=rgb('7A1A8A'), light=rgb('FFE8FF'), facets=3)
    register('gold', 'metal', rgb('C9A566'), shade=rgb('6E5126'), light=rgb('F5E2B0'))
    pink = (240, 130, 255)
    m, r = sword_model('origin_dragon_sword')
    blade(m, r, 3.2, 24, 3.4, .8, 'od_bone', 'od_edge', tip=4.4, segments=6, taper=.18, edge_w=.36,
          fuller=lambda i, n: [glow_line(pink, .08, ((.5, 1), (.5, 0)), .75),
                               {'kind': 'runes', 'style': 'engrave', 'seed': 50 + i, 'count': 2, 'width': .05}])
    for z, f in ((-1, 'north'), (1, 'south')):
        m.box(f'spine_{f}', r, (0, 12.5, z * .45), (.55, 17, .2), 'od_scale', scale_rows=8, scale_cols=1)
    m.box('head', r, (0, 1.4, 0), (3.4, 2.4, 2.5), 'od_scale', scale_rows=2, scale_cols=2)
    m.box('snout', r, (0, 3.0, 0), (2.2, 1.6, 2.0), 'od_hide', trim=('od_bone', .06))
    m.box('jaw_front', r, (0, 3.7, -.75), (1.9, .5, .6), 'od_bone', rotation=(-15, 0, 0))
    m.box('jaw_back', r, (0, 3.7, .75), (1.9, .5, .6), 'od_bone', rotation=(15, 0, 0))
    for s in (-1, 1):
        for z, f in ((-1.22, 'front'), (1.22, 'back')):
            m.box(f'eye_{s}_{f}', r, (s * 1.0, 2.0, z), (.6, .45, .12), 'od_eye', glow_face=True)
        for k, (ln, ang, y, ln2) in enumerate(((3.2, 52, 2.2, 2.4), (2.4, 24, 1.0, 1.6))):
            th = .66 - .14 * k
            a1, a2 = math.radians(ang), math.radians(ang + 28)
            p0 = (s * 1.4, y)
            p1 = (p0[0] + s * math.cos(a1) * ln, p0[1] + math.sin(a1) * ln)
            m.box(f'horn_{s}_{k}', r, (p0[0] + s * math.cos(a1) * ln / 2, p0[1] + math.sin(a1) * ln / 2, .2),
                  (ln + .2, th, th), 'od_bone', rotation=(0, 0, s * ang), bevel=.08)
            m.box(f'horn_tip_{s}_{k}', r, (p1[0] + s * math.cos(a2) * ln2 / 2, p1[1] + math.sin(a2) * ln2 / 2, .2),
                  (ln2, th * .65, th * .65), 'od_edge', rotation=(0, 0, s * (ang + 28)), bevel=.06)
        m.box(f'brow_{s}', r, (s * 1.05, 2.55, 0), (1.3, .45, 2.6), 'od_bone', rotation=(0, 0, -s * 18))
        m.box(f'fin_{s}', r, (s * 2.1, .4, 0), (1.6, .5, .4), 'od_edge', rotation=(0, 0, s * -30))
    m.box('grip', r, (0, -2.6, 0), (1.2, 6.2, 1.2), 'od_scale', scale_rows=4, scale_cols=1.5)
    m.box('grip_ring', r, (0, .2, 0), (1.6, .5, 1.6), 'od_bone')
    m.box('pommel', r, (0, -6.1, 0), (1.5, 1.2, 1.5), 'od_hide', trim=('od_bone', .08))
    for k in range(3):
        a = (k - 1) * 40
        m.box(f'claw_{k}', r, (0, -7.0, 0), (.38, 1.3, .38), 'od_bone', rotation=(0, 0, a), pivot=(0, -6.4, 0))
    m.box('pommel_eye', r, (0, -6.1, -.78), (.7, .7, .1), 'od_eye', glow_face=True)
    return m


def divine_punishment_sword():
    register('dp_jade', 'plate', rgb('EFEAE0'), shade=rgb('9AA3AD'), light=rgb('FFFDF8'), vein=9, gloss=20)
    register('dp_edge', 'metal', rgb('E9EEF2'), shade=rgb('8C97A2'), light=rgb('FFFFFF'), rim_light=60)
    register('gold', 'metal', rgb('D9B468'), shade=rgb('86601F'), light=rgb('FFF1BC'), rim_light=55, bevel=.08)
    register('dp_ink', 'lacquer', rgb('1A1D25'), shade=rgb('07080B'), gloss=30, rim_light=22, rim_dark=20)
    register('dp_sun', 'gem', rgb('FFF4D2'), shade=rgb('D9A44A'), light=rgb('FFFFFF'), facets=4)
    thunder = (205, 230, 255)
    sun = (255, 238, 196)

    def fuller(i, n):
        zig = [(.5 + (.16 if k % 2 else -.16), k / 4) for k in range(5)]
        return [{'kind': 'lines', 'style': 'inlay', 'material': 'gold', 'width': .16, 'paths': [[(.5, 0), (.5, 1)]]},
                {'kind': 'lines', 'style': 'inlay', 'material': 'gold', 'width': .07, 'paths': [zig],
                 'glow': thunder, 'glow_alpha': 1, 'tint': .95}]
    m, r = sword_model('divine_punishment_sword')
    blade(m, r, 2.6, 26, 4.2, .8, 'dp_jade', 'dp_edge', tip=4.8, segments=7, taper=.14, edge_w=.38, fuller=fuller)
    m.ring('guard_ring', r, (0, 1.7, 0), 1.55, 16, .4, 1.1, 'gold', bevel=.05)
    for face, z in (('north', -.36), ('south', .36)):
        m.box(f'guard_face_{face}', r, (0, 1.7, z), (2.9, 2.9, .12), 'dp_ink',
              hide=tuple(f for f in ALL if f != face), density=96, bevel=0, ao=False,
              decals={face: [{'kind': 'rays', 'style': 'inlay', 'material': 'gold', 'count': 24, 'r0': .32,
                              'r1': .46, 'width': .02, 'glow': sun, 'glow_alpha': .5, 'tint': .2},
                             {'kind': 'taiji', 'style': 'taiji', 'r': .6}]})
    for s in (-1, 1):
        for k, (ang, ln, w) in enumerate(((8, 4.6, 1.2), (34, 3.8, 1.0), (60, 2.8, .8))):
            m.box(f'wing_{s}_{k}', r, (s * (1.4 + ln / 2), 1.2, 0), (ln, w, .5), 'gold' if k != 1 else 'dp_jade',
                  rotation=(0, 0, s * ang), pivot=(s * 1.4, 1.2, 0), bevel=.07,
                  trim=('gold', .08) if k == 1 else None,
                  decals={f: [{'kind': 'lines', 'style': 'inlay', 'material': 'gold', 'width': .1,
                               'paths': [[(.08, .5), (.92, .5)]], 'glow': sun, 'glow_alpha': .8}]
                          for f in ('north', 'south')} if k == 0 else None)
    m.box('grip', r, (0, -2.8, 0), (1.2, 6.6, 1.2), 'dp_ink', decals=wrap('gold', 7, .1))
    m.box('grip_collar', r, (0, .1, 0), (1.7, .6, 1.7), 'gold')
    m.ring('pommel_ring', r, (0, -7.0, 0), .85, 12, .3, .55, 'gold', bevel=.05)
    m.box('pommel_gem', r, (0, -7.0, 0), (.9, .9, .9), 'dp_sun', rotation=(0, 0, 45), glow_face=True)
    m.box('pommel_cap', r, (0, -6.0, 0), (1.5, .5, 1.5), 'gold')
    return m


DESIGNS = {'spirit_source_sword': spirit_source_sword, 'dark_source_sword': dark_source_sword,
           'origin_dragon_sword': origin_dragon_sword, 'divine_punishment_sword': divine_punishment_sword}
