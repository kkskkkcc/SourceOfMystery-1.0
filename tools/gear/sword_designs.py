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
    register('ss_jade', 'plate', rgb('D5DBE3'), shade=rgb('7C8794'), light=rgb('F7FAFD'), vein=3, gloss=30)
    register('ss_edge', 'metal', rgb('E4EAF1'), shade=rgb('7D8896'), light=rgb('FFFFFF'), rim_light=60)
    register('ss_wood', 'cloth', rgb('2C3E5C'), shade=rgb('0F1726'), weave=2.4)
    register('ss_vine', 'lacquer', rgb('2F6FC8'), shade=rgb('123067'), gloss=30)
    register('ss_ivory', 'metal', rgb('BFC8D3'), shade=rgb('5E6875'), light=rgb('F4F8FC'))
    register('ss_gem', 'gem', rgb('6CC0FF'), shade=rgb('14508E'), light=rgb('EAF7FF'), facets=4)
    register('gold', 'metal', rgb('C9A566'), shade=rgb('6E5126'), light=rgb('F5E2B0'))
    green = (110, 190, 255)
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
    register('ds_obsidian', 'lacquer', rgb('1D1925'), shade=rgb('07060A'), gloss=36, rim_light=26)
    register('ds_edge', 'metal', rgb('8B6BC2'), shade=rgb('35214F'), light=rgb('D2BEF5'), rim_light=50)
    register('ds_bone', 'lacquer', rgb('241D30'), shade=rgb('0A080F'), gloss=26)
    register('ds_iron', 'metal', rgb('4A3466'), shade=rgb('170F22'), light=rgb('9C82C4'))
    register('ds_void', 'gem', rgb('C27BFF'), shade=rgb('4A1478'), light=rgb('F6E6FF'), facets=5)
    register('gold', 'metal', rgb('C9A566'), shade=rgb('6E5126'), light=rgb('F5E2B0'))
    violet = (200, 120, 255)
    m, r = sword_model('dark_source_sword')
    blade(m, r, 2.3, 23, 3.1, .65, 'ds_obsidian', 'ds_edge', tip=4.0, segments=7,
          profile=[1.0, .86, 1.02, .84, .98, .8, .9], edge_w=.34,
          fuller=lambda i, n: [veins(violet, 30 + i, 2, .05), glow_line(violet, .07, ((.5, 1), (.5, 0)), .7)])
    for i in range(5):
        for s in (-1, 1):
            m.box(f'hook_{i}_{s}', r, (s * 1.7, 4.4 + i * 3.4, 0), (.9, .6, .45), 'ds_edge', rotation=(0, 0, s * -35))
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
    """始源龙剑 v4: dark bronze blade with ember edges, dragon-head guard in orange scale."""
    register('od_bone', 'metal', rgb('3A2A20'), shade=rgb('140D08'), light=rgb('7A5638'))
    register('od_edge', 'metal', rgb('FF9442'), shade=rgb('A8400C'), light=rgb('FFD29A'), rim_light=60)
    register('od_scale', 'scale', rgb('DC5E16'), shade=rgb('6E2408'), light=rgb('FF9A48'))
    register('od_hide', 'plate', rgb('E2661A'), shade=rgb('8C300A'), light=rgb('FF9446'), vein=4)
    register('od_eye', 'gem', rgb('FFB24C'), shade=rgb('B0420E'), light=rgb('FFF2CC'), facets=3)
    register('gold', 'metal', rgb('C4904F'), shade=rgb('5E3B1C'), light=rgb('F5D6A2'))
    pink = (255, 150, 60)
    m, r = sword_model('origin_dragon_sword')
    blade(m, r, 3.2, 24, 3.0, .7, 'od_bone', 'od_edge', tip=4.4, segments=6, taper=.18, edge_w=.36,
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
    """神威天罚 v4: slender white blade, black fuller, silver edges, taiji guard."""
    register('dp_white', 'plate', rgb('F2F1EC'), shade=rgb('A3A8B0'), light=rgb('FFFFFF'), vein=3, gloss=24)
    register('dp_edge', 'metal', rgb('DDE2E8'), shade=rgb('7B828C'), light=rgb('FFFFFF'), rim_light=60)
    register('dp_silver', 'metal', rgb('C6CBD2'), shade=rgb('6A707A'), light=rgb('F4F6FA'), rim_light=50)
    register('dp_black', 'lacquer', rgb('1B1C21'), shade=rgb('07080A'), gloss=34)
    register('dp_gold', 'metal', rgb('D6B46C'), shade=rgb('80602A'), light=rgb('FFF0C2'))
    register('dp_pearl', 'gem', rgb('F4F6FF'), shade=rgb('9AA2B5'), light=rgb('FFFFFF'), facets=4)
    white = (236, 242, 255)

    def fuller(i, n):
        return [{'kind': 'lines', 'style': 'inlay', 'material': 'dp_black', 'width': .3, 'paths': [[(.5, 0), (.5, 1)]]},
                {'kind': 'lines', 'style': 'inlay', 'material': 'dp_black', 'width': .07, 'paths': [[(.5, 0), (.5, 1)]],
                 'glow': white, 'glow_alpha': .9, 'tint': .9}]
    m, r = sword_model('divine_punishment_sword')
    blade(m, r, 2.4, 25, 3.0, .6, 'dp_white', 'dp_edge', tip=4.4, segments=7, taper=.18, edge_w=.32, fuller=fuller)
    # Taiji guard: a slim silver ring with a black-white face on both sides.
    m.ring('guard_ring', r, (0, 1.6, 0), 1.15, 16, .28, .8, 'dp_silver', bevel=.04)
    for face, z in (('north', -.26), ('south', .26)):
        m.box(f'guard_face_{face}', r, (0, 1.6, z), (2.1, 2.1, .1), 'dp_black',
              hide=tuple(f for f in ALL if f != face), density=110, bevel=0, ao=False,
              decals={face: [{'kind': 'trigrams', 'style': 'inlay', 'material': 'dp_silver', 'r': .43, 'width': .025},
                             {'kind': 'taiji', 'style': 'taiji', 'r': .58}]})
    for s in (-1, 1):
        # Thin swept quillons: white blade-like arms with silver tips.
        m.box(f'quillon_{s}', r, (s * 2.65, 1.3, 0), (2.9, .5, .45), 'dp_white', trim=('dp_silver', .1), bevel=.05)
        m.box(f'quillon_tip_{s}', r, (s * 4.3, 1.3, 0), (.62, .62, .5), 'dp_silver', rotation=(0, 0, 45))
    m.box('ricasso', r, (0, 2.9, 0), (1.5, .7, .7), 'dp_black', trim=('dp_silver', .1))
    m.box('grip', r, (0, -2.7, 0), (1.05, 6.2, 1.05), 'dp_black', decals=wrap('dp_silver', 8, .08))
    m.box('grip_collar', r, (0, .25, 0), (1.45, .5, 1.45), 'dp_silver')
    m.box('pommel_cap', r, (0, -5.95, 0), (1.35, .45, 1.35), 'dp_silver')
    m.box('pommel', r, (0, -6.75, 0), (1.0, 1.0, 1.0), 'dp_pearl', rotation=(0, 0, 45), glow_face=True)
    m.box('pommel_gold', r, (0, -7.55, 0), (.5, .5, .5), 'dp_gold', rotation=(0, 45, 0))
    return m


DESIGNS = {'spirit_source_sword': spirit_source_sword, 'dark_source_sword': dark_source_sword,
           'origin_dragon_sword': origin_dragon_sword, 'divine_punishment_sword': divine_punishment_sword}
