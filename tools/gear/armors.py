"""Four tiered chestplates designed by name and rank. python tools/gear/armors.py [id ...]

mystery        秘源甲   tempered blue steel brigandine with cyan source-ore studs
spirit_source  灵源秘甲 living jade leaf plates, ivory veins, emerald heart
dark_source    暗源之甲 obsidian plate, violet void cracks, horned pauldrons, wither skull
origin_dragon  始源龙甲 dragon-scale cuirass, dragon-head pauldrons, folded back wings
神威天佑 (divine_blessing) has its own script: divine_blessing.py
"""
import math
import sys
import zlib
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from forge3d import ASSETS, ROOT  # noqa: E402
from kit import ALL, armor_model, disc, outer, sleeve_and_bracer, tasset_set, tiered_pauldron  # noqa: E402
from paint import register, rgb  # noqa: E402


def glow_line(color, width=.18, path=((.5, .93), (.5, .1)), alpha=.9, material='gold'):
    return {'kind': 'lines', 'style': 'inlay', 'material': material, 'width': width, 'paths': [list(path)],
            'glow': color, 'glow_alpha': alpha, 'tint': .9}


def veins(color, seed, count=3, width=.04, alpha=.85):
    return {'kind': 'veins', 'style': 'inlay', 'material': 'gold', 'seed': seed, 'count': count, 'width': width,
            'glow': color, 'glow_alpha': alpha, 'tint': .9}


def chest_core(m, body, plate, under, trim, pec_decals=None, rib_decals=None, belt=None, belt_decals=None):
    m.box('core', body, (0, 18.2, 0), (8.9, 11.4, 4.9), under)
    for s in (-1, 1):
        o = outer(s)
        m.box(f'pectoral_{s}', body, (s * 2.3, 20.45, -2.82), (4.1, 4.4, .8), plate, rotation=(0, -s * 16, 0),
              trim=trim, trim_faces=('north',), decals={'north': pec_decals(s)} if pec_decals else None)
        m.box(f'pectoral_top_{s}', body, (s * 2.3, 23.1, -2.45), (4.0, 1.25, .75), plate, rotation=(34, -s * 14, 0),
              trim=trim, trim_faces=('north', 'up'))
        m.box(f'pectoral_low_{s}', body, (s * 2.3, 17.95, -2.62), (3.95, 1.0, .7), plate, rotation=(-24, -s * 15, 0),
              trim=trim, trim_faces=('north',))
        m.box(f'flank_{s}', body, (s * 4.62, 18.9, .6), (.7, 8.0, 3.6), plate, trim=trim,
              decals={o: rib_decals(s)} if rib_decals else None)
    for i, (y, w) in enumerate(((16.45, 7.7), (15.1, 7.35), (13.8, 7.0))):
        m.box(f'abdomen_{i}', body, (0, y, -2.72 - .06 * i), (w, 1.45, .55), plate, rotation=(12, 0, 0),
              trim=trim, trim_faces=('north',))
        m.box(f'lumbar_{i}', body, (0, y, 2.72 + .06 * i), (w, 1.45, .55), plate, rotation=(-12, 0, 0),
              trim=trim, trim_faces=('south',))
    m.box('back_plate', body, (0, 20.0, 2.72), (8.0, 7.6, .7), plate, trim=trim, trim_faces=('south',))
    m.box('belt', body, (0, 12.75, 0), (9.3, 1.15, 5.3), belt or under,
          decals={f: belt_decals for f in ('north', 'south', 'east', 'west')} if belt_decals else None)


# ------------------------------------------------------------------ 秘源甲
def mystery():
    register('my_steel', 'plate', rgb('9AAAB8'), shade=rgb('4A5866'), light=rgb('E2ECF4'), vein=6, gloss=24)
    register('my_dark', 'metal', rgb('56636F'), shade=rgb('222A31'), light=rgb('9DACB9'))
    register('my_edge', 'metal', rgb('C9D5DE'), shade=rgb('6B7884'), light=rgb('FFFFFF'))
    register('my_cloth', 'cloth', rgb('2B3946'), shade=rgb('0F151B'))
    register('my_leather', 'cloth', rgb('4A3829'), shade=rgb('1C140D'), weave=5)
    register('my_crystal', 'gem', rgb('7FF6F2'), shade=rgb('147A86'), light=rgb('E8FFFF'), facets=3)
    register('gold', 'metal', rgb('C9A566'), shade=rgb('6E5126'), light=rgb('F5E2B0'))
    cyan = (120, 250, 245)
    trim = ('my_edge', .12)
    m, body, arms, legs = armor_model('mystery')
    studs = [{'kind': 'rivets', 'style': 'inlay', 'material': 'my_edge', 'r': .05,
              'at': [(.12, .12), (.88, .12), (.12, .88), (.88, .88)]}]
    chest_core(m, body, 'my_steel', 'my_cloth', trim, pec_decals=lambda s: studs,
               rib_decals=lambda s: studs, belt='my_leather',
               belt_decals=[{'kind': 'rivets', 'style': 'inlay', 'material': 'my_edge', 'r': .12,
                             'at': [(k / 8, .5) for k in range(1, 8)]}])
    # Source-ore crystal set into the sternum, with glowing channels.
    m.box('sternum_plate', body, (0, 20.4, -3.3), (2.2, 3.4, .45), 'my_dark', trim=trim)
    m.box('sternum_crystal', body, (0, 20.5, -3.6), (1.15, 1.15, .5), 'my_crystal', rotation=(0, 0, 45), glow_face=True)
    for s in (-1, 1):
        m.box(f'channel_{s}', body, (s * 1.8, 21.6, -3.32), (1.6, .22, .15), 'my_crystal', rotation=(0, 0, s * 22),
              glow_face=True)
    m.box('buckle', body, (0, 12.8, -2.9), (2.0, 1.6, .4), 'my_dark', trim=trim)
    m.box('buckle_gem', body, (0, 12.8, -3.15), (.7, .7, .2), 'my_crystal', glow_face=True)
    for s, arm in arms.items():
        label = 'Right' if s < 0 else 'Left'
        tiered_pauldron(m, label, arm, s, 'my_steel', trim, count=2, cheeks=False, slope0=20, slope_step=12,
                        hem='my_edge')
        m.box(f'{label}_ore', arm, (s * 8.35, 22.6, 0), (.4, 1.0, 1.0), 'my_crystal', rotation=(45, 0, -s * 30),
              glow_face=True)
        sleeve_and_bracer(m, label, arm, s, 'my_cloth', 'my_steel', 'my_dark', trim=trim,
                          bracer_decals=lambda s: [glow_line(cyan, .05, ((.15, .5), (.85, .5)), .7)])
    for s, leg in legs.items():
        label = 'Right' if s < 0 else 'Left'
        tasset_set(m, label, leg, s, 'my_leather', ('my_dark', .14), hem='my_edge', length=4.4)
    return m


# ------------------------------------------------------------------ 灵源秘甲
def spirit_source():
    register('sp_jade', 'plate', rgb('5FB98A'), shade=rgb('1E6646'), light=rgb('CFF5DE'), vein=18, gloss=24)
    register('sp_ivory', 'plate', rgb('EEE8D2'), shade=rgb('A39C80'), light=rgb('FFFFF6'), vein=5)
    register('sp_bark', 'bone', rgb('6E4C30'), shade=rgb('2E1C0D'), light=rgb('A47A51'))
    register('sp_moss', 'cloth', rgb('2F5A3A'), shade=rgb('112417'), weave=3)
    register('sp_leaf', 'scale', rgb('6CC596'), shade=rgb('1F5E3F'), light=rgb('C6F2D6'))
    register('sp_gem', 'gem', rgb('8CFFB6'), shade=rgb('1A8A4B'), light=rgb('F0FFF4'), facets=4)
    register('gold', 'metal', rgb('C9A566'), shade=rgb('6E5126'), light=rgb('F5E2B0'))
    green = (150, 255, 185)
    trim = ('sp_ivory', .14)
    vine = lambda seed: [{'kind': 'veins', 'style': 'inlay', 'material': 'sp_ivory', 'seed': seed, 'count': 2,
                          'width': .05}]
    m, body, arms, legs = armor_model('spirit_source')
    chest_core(m, body, 'sp_jade', 'sp_moss', trim, pec_decals=lambda s: vine(3 + s),
               rib_decals=lambda s: vine(9 + s), belt='sp_bark',
               belt_decals=[{'kind': 'lines', 'style': 'inlay', 'material': 'sp_jade', 'width': .2,
                             'paths': [[(k / 6, 0), (k / 6 + .12, 1)] for k in range(7)]}])
    # Emerald heart wrapped in ivory leaves.
    for k in range(6):
        a = k / 6 * math.tau + math.pi / 2
        m.box(f'heart_leaf_{k}', body, (math.cos(a) * 1.3, 20.6 + math.sin(a) * 1.3, -3.35), (.9, 1.8, .2), 'sp_ivory',
              rotation=(0, 0, math.degrees(a) - 90), bevel=.1,
              decals={'north': [{'kind': 'lines', 'style': 'inlay', 'material': 'sp_jade', 'width': .14,
                                 'paths': [[(.5, .1), (.5, .9)]]}]})
    m.box('heart_gem', body, (0, 20.6, -3.6), (1.3, 1.3, .5), 'sp_gem', rotation=(0, 0, 45), glow_face=True)
    m.box('buckle', body, (0, 12.8, -2.9), (2.2, 1.7, .45), 'sp_ivory', trim=('sp_jade', .1))
    m.box('buckle_gem', body, (0, 12.8, -3.15), (.7, .7, .2), 'sp_gem', glow_face=True)
    for s, arm in arms.items():
        label = 'Right' if s < 0 else 'Left'
        o = outer(s)
        m.box(f'{label}_cap', arm, (s * 6.2, 24.35, 0), (4.6, 1.0, 5.5), 'sp_jade', rotation=(0, 0, -s * 9),
              trim=trim, trim_faces=('up', 'north', 'south', o))
        # Layered leaf pauldron: long leaves fanning down over the shoulder.
        for i, (z, ang, ln) in enumerate(((-2.0, 34, 5.0), (-.7, 40, 5.6), (.7, 40, 5.6), (2.0, 34, 5.0))):
            m.box(f'{label}_leaf_{i}', arm, (s * (6.4 + ln * .32), 23.6 - ln * .22, z), (ln, .35, 1.6), 'sp_leaf',
                  rotation=(0, 0, -s * ang), bevel=.08, scale_rows=1, scale_cols=3,
                  decals={'up': [glow_line(green, .1, ((.05, .5), (.95, .5)), .6, 'sp_ivory')]})
        m.box(f'{label}_bud', arm, (s * 8.4, 22.4, 0), (.5, 1.0, 1.0), 'sp_gem', rotation=(45, 0, 0), glow_face=True)
        sleeve_and_bracer(m, label, arm, s, 'sp_moss', 'sp_bark', 'sp_ivory', trim=('sp_jade', .12),
                          bracer_decals=lambda s: vine(20 + s), fin='sp_ivory',
                          fin_decals=[glow_line(green, .2)])
    for s, leg in legs.items():
        label = 'Right' if s < 0 else 'Left'
        tasset_set(m, label, leg, s, 'sp_leaf', trim, hem='sp_ivory', length=5.0, jewel='sp_gem',
                   scale_rows=1.2, scale_cols=1.0)
    return m


# ------------------------------------------------------------------ 暗源之甲
def dark_source():
    register('dk_plate', 'lacquer', rgb('241D31'), shade=rgb('09070E'), gloss=30, rim_light=30, rim_dark=30)
    register('dk_iron', 'metal', rgb('4E4858'), shade=rgb('17141B'), light=rgb('938BA0'))
    register('dk_edge', 'metal', rgb('7C7290'), shade=rgb('2A2433'), light=rgb('C8BEDA'))
    register('dk_bone', 'bone', rgb('3E3845'), shade=rgb('141117'), light=rgb('72697C'))
    register('dk_cloth', 'cloth', rgb('1B1622'), shade=rgb('07050A'))
    register('dk_void', 'gem', rgb('C27BFF'), shade=rgb('4A1478'), light=rgb('F6E6FF'), facets=5)
    register('gold', 'metal', rgb('C9A566'), shade=rgb('6E5126'), light=rgb('F5E2B0'))
    violet = (200, 120, 255)
    trim = ('dk_edge', .12)
    m, body, arms, legs = armor_model('dark_source')
    chest_core(m, body, 'dk_plate', 'dk_cloth', trim, pec_decals=lambda s: [veins(violet, 40 + s, 2)],
               rib_decals=lambda s: [veins(violet, 44 + s, 2)], belt='dk_iron',
               belt_decals=[{'kind': 'runes', 'style': 'paint', 'color': (190, 110, 250), 'seed': 8, 'count': 6,
                             'width': .08, 'glow': violet}])
    # Wither skull on the sternum, eyes burning.
    eyes = [[(.2, .34), (.42, .34), (.38, .56), (.24, .56)], [(.58, .34), (.8, .34), (.76, .56), (.62, .56)]]
    m.box('skull', body, (0, 20.5, -3.45), (2.6, 2.4, .7), 'dk_bone', trim=('dk_iron', .08),
          decals={'north': [{'kind': 'polys', 'style': 'paint', 'color': (8, 5, 12),
                             'shapes': eyes + [[(.44, .66), (.56, .66), (.5, .78)]]},
                            {'kind': 'polys', 'style': 'paint', 'color': (215, 150, 255), 'glow': violet,
                             'shapes': [[(.26, .4), (.36, .4), (.34, .5), (.28, .5)], [(.64, .4), (.74, .4), (.72, .5), (.66, .5)]]},
                            {'kind': 'lines', 'style': 'engrave', 'width': .05,
                             'paths': [[(.3, .86), (.3, .96)], [(.5, .86), (.5, .96)], [(.7, .86), (.7, .96)]]}]})
    for s in (-1, 1):
        m.box(f'skull_horn_{s}', body, (s * 1.6, 22.1, -3.4), (.4, 1.5, .4), 'dk_edge', rotation=(0, 0, -s * 40))
    m.box('back_spine', body, (0, 19.5, 3.15), (.8, 7.0, .4), 'dk_iron')
    for i in range(4):
        m.box(f'back_spike_{i}', body, (0, 22.3 - i * 2.0, 3.55), (.5, 1.1, .5), 'dk_edge', rotation=(-35, 45, 0))
    for s, arm in arms.items():
        label = 'Right' if s < 0 else 'Left'
        o = outer(s)
        tiered_pauldron(m, label, arm, s, 'dk_plate', trim, count=3, cheeks=False, slope0=26, slope_step=11,
                        hem='dk_edge')
        # Curved horns: three chained segments sweeping outward and up.
        p, a = (s * 7.6, 24.6), 20
        for k, (ln, th) in enumerate(((1.6, .8), (1.5, .6), (1.3, .42))):
            rad = math.radians(a)
            c = (p[0] + s * math.cos(rad) * ln / 2, p[1] + math.sin(rad) * ln / 2)
            m.box(f'{label}_horn_{k}', arm, (c[0], c[1], 0), (ln + .15, th, th), 'dk_bone' if k < 2 else 'dk_edge',
                  rotation=(0, 0, s * a), bevel=.08)
            p = (p[0] + s * math.cos(rad) * ln, p[1] + math.sin(rad) * ln)
            a += 28
        for k in range(3):
            m.box(f'{label}_spike_{k}', arm, (s * (8.3 + .2 * k), 22.4 - k * 1.1, (k - 1) * 1.8), (.45, 1.3, .45), 'dk_edge',
                  rotation=(0, 0, -s * 70))
        sleeve_and_bracer(m, label, arm, s, 'dk_cloth', 'dk_plate', 'dk_iron', trim=trim,
                          bracer_decals=lambda s: [veins(violet, 60 + s, 2)], fin='dk_edge',
                          fin_decals=[glow_line(violet, .2, material='dk_void')])
    for s, leg in legs.items():
        label = 'Right' if s < 0 else 'Left'
        tasset_set(m, label, leg, s, 'dk_plate', trim, hem='dk_edge', length=5.2, jewel='dk_void')
    return m


# ------------------------------------------------------------------ 始源龙甲
def origin_dragon():
    register('dr_scale', 'scale', rgb('3E2D57'), shade=rgb('130C1D'), light=rgb('8163B0'))
    register('dr_hide', 'plate', rgb('3A2A52'), shade=rgb('120B1B'), light=rgb('7A60A3'), vein=10, gloss=26)
    register('dr_bone', 'bone', rgb('E6DCC8'), shade=rgb('8A7C66'), light=rgb('FFFBF1'))
    register('dr_edge', 'metal', rgb('CBB9E6'), shade=rgb('5E4D7A'), light=rgb('F7F0FF'), rim_light=60)
    register('dr_membrane', 'cloth', rgb('4A2E66'), shade=rgb('1A0F26'), weave=6)
    register('dr_eye', 'gem', rgb('F07BFF'), shade=rgb('7A1A8A'), light=rgb('FFE8FF'), facets=3)
    register('gold', 'metal', rgb('C9A566'), shade=rgb('6E5126'), light=rgb('F5E2B0'))
    pink = (240, 130, 255)
    trim = ('dr_bone', .12)
    m, body, arms, legs = armor_model('origin_dragon')
    chest_core(m, body, 'dr_hide', 'dr_scale', trim, belt='dr_hide',
               pec_decals=lambda s: [veins(pink, 80 + s, 2, .035, .7)],
               belt_decals=[{'kind': 'rivets', 'style': 'inlay', 'material': 'dr_bone', 'r': .14,
                             'at': [(k / 7, .5) for k in range(1, 7)]}])
    # Rib cage of dragon bone over the scales, and the end-crystal heart.
    for i in range(3):
        for s in (-1, 1):
            m.box(f'rib_{i}_{s}', body, (s * 2.0, 21.6 - i * 1.5, -3.35), (2.6, .38, .3), 'dr_bone',
                  rotation=(0, 0, s * (14 + 8 * i)))
    m.box('sternum', body, (0, 19.9, -3.35), (.6, 5.4, .35), 'dr_bone')
    m.box('heart', body, (0, 21.9, -3.55), (1.1, 1.1, .45), 'dr_eye', rotation=(0, 0, 45), glow_face=True)
    m.box('buckle', body, (0, 12.8, -2.9), (2.2, 1.8, .45), 'dr_bone', trim=('dr_edge', .08))
    m.box('buckle_eye', body, (0, 12.8, -3.15), (.9, .5, .2), 'dr_eye', glow_face=True)
    # Folded wings on the back.
    for s in (-1, 1):
        root = (s * 1.4, 22.5, 3.3)
        m.box(f'wing_arm_{s}', body, (s * 3.0, 23.6, 3.6), (3.6, .55, .55), 'dr_bone', rotation=(0, -s * 15, s * 32))
        for k, (ln, ang) in enumerate(((7.5, -78), (6.4, -64), (5.2, -50))):
            m.box(f'wing_finger_{s}_{k}', body, (root[0] + s * (1.2 + k * .9), root[1] - ln / 2 + 1.2, 3.65 + .05 * k),
                  (.32, ln, .3), 'dr_bone', rotation=(0, 0, s * (90 + ang) * .35))
        m.box(f'wing_membrane_{s}', body, (s * 2.9, 19.4, 3.55), (3.0, 6.6, .15), 'dr_membrane',
              rotation=(0, -s * 10, s * 8), decals={'south': [veins(pink, 70 + s, 2, .03, .5)]})
    for s, arm in arms.items():
        label = 'Right' if s < 0 else 'Left'
        o = outer(s)
        # Dragon-head pauldron: skull dome, snout pointing outward and down, horns back.
        m.box(f'{label}_skull', arm, (s * 6.6, 23.9, 0), (5.0, 2.4, 5.6), 'dr_scale', rotation=(0, 0, -s * 12),
              trim=trim, trim_faces=('north', 'south', o), scale_rows=1.5, scale_cols=1.5)
        m.box(f'{label}_snout', arm, (s * 9.6, 22.8, 0), (3.2, 1.8, 2.8), 'dr_hide', rotation=(0, 0, -s * 22),
              trim=trim, trim_faces=('up', o, 'north', 'south'))
        m.box(f'{label}_jaw', arm, (s * 9.3, 21.55, 0), (3.0, .6, 2.5), 'dr_bone', rotation=(0, 0, -s * 32))
        for z in (-1, 1):
            f = 'north' if z < 0 else 'south'
            m.box(f'{label}_eye_{f}', arm, (s * 8.0, 23.9, z * 1.45), (.7, .4, .15), 'dr_eye', rotation=(0, 0, -s * 15),
                  glow_face=True)
            m.box(f'{label}_tooth_{f}', arm, (s * 10.6, 21.75, z * .8), (.3, .8, .3), 'dr_bone', rotation=(0, 0, -s * 22))
            p, a = (s * 6.4, 24.9, z * 1.6), 35
            for k, (ln, th) in enumerate(((2.6, .65), (2.2, .45))):
                rad = math.radians(a)
                c = (p[0] - s * math.cos(rad) * ln / 2, p[1] + math.sin(rad) * ln / 2, p[2])
                m.box(f'{label}_horn_{f}_{k}', arm, c, (ln + .1, th, th), 'dr_bone' if k == 0 else 'dr_edge',
                      rotation=(0, 0, -s * a), bevel=.08)
                p = (p[0] - s * math.cos(rad) * ln, p[1] + math.sin(rad) * ln, p[2])
                a += 25
        m.box(f'{label}_crest', arm, (s * 6.4, 25.3, 0), (2.6, .5, .4), 'dr_edge', rotation=(0, 0, -s * 8))
        sleeve_and_bracer(m, label, arm, s, 'dr_hide', 'dr_scale', 'dr_bone', trim=trim, fin='dr_edge',
                          fin_decals=[glow_line(pink, .2, material='dr_eye')])
        for k in range(3):
            m.box(f'{label}_claw_{k}', arm, (s * 8.5, 13.3 + k * .9, (k - 1) * 1.2), (.9, .3, .3), 'dr_bone',
                  rotation=(0, 0, -s * 25))
    for s, leg in legs.items():
        label = 'Right' if s < 0 else 'Left'
        tasset_set(m, label, leg, s, 'dr_scale', trim, hem='dr_edge', length=5.2, jewel='dr_eye',
                   scale_rows=2.0, scale_cols=1.6)
    return m


DESIGNS = {'mystery': mystery, 'spirit_source': spirit_source, 'dark_source': dark_source,
           'origin_dragon': origin_dragon}


def export(name):
    m = DESIGNS[name]()
    info = m.export(ASSETS / f'geo/armor/{name}.geo.json', ASSETS / f'textures/armor/{name}.png',
                    ASSETS / f'textures/armor/{name}_glowmask.png', seed=zlib.crc32(name.encode()) & 0xFFFF,
                    manifest_path=ROOT / f'tools/gear/out/{name}.parts.json')
    print(name, info)


if __name__ == '__main__':
    for n in sys.argv[1:] or DESIGNS:
        export(n)
