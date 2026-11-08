"""Four tiered chestplates designed by name and rank. python tools/gear/armors.py [id ...]

All are light armor built from light.py (fitted suit, slim plates):
mystery        秘源甲   gray-silver riveted brigandine, one source crystal
spirit_source  灵源秘甲 blue-silver plates, enamel inlays, feather spaulders, back ribbons
dark_source    暗源之甲 black lacquer, violet void cracks, left horn, ragged half-cape
origin_dragon  始源龙甲 orange-glow scales on bronze leather, swept horns, dorsal fins
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
import light as L  # noqa: E402


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
    """秘源甲 v4: gray steel brigandine light armor with silver rivets and one source crystal."""
    register('my_steel', 'plate', rgb('8E959E'), shade=rgb('464C54'), light=rgb('CDD2D8'), vein=4, gloss=24)
    register('my_silver', 'metal', rgb('C3C8CF'), shade=rgb('646A73'), light=rgb('F2F4F7'), rim_light=50)
    register('my_suit', 'cloth', rgb('34373C'), shade=rgb('131417'), weave=2.6)
    register('my_seam', 'lacquer', rgb('1D1F22'), shade=rgb('0A0B0C'))
    register('my_leather', 'cloth', rgb('4A4C50'), shade=rgb('1C1D20'), weave=3.2)
    register('my_crystal', 'gem', rgb('7FE9F0'), shade=rgb('1A6E7A'), light=rgb('E8FFFF'), facets=3)
    rivets = lambda cols, rows: [{'kind': 'rivets', 'style': 'inlay', 'material': 'my_silver', 'r': .055,
                                  'at': [((x + .5) / cols, (y + .5) / rows) for x in range(cols) for y in range(rows)]}]
    m, body, arms, legs = armor_model('mystery', density=26)
    L.suit(m, body, 'my_suit', 'my_seam', 'my_leather', 'my_silver')
    L.straps(m, body, 'my_leather', 'my_silver', 'my_silver')
    L.pectorals(m, body, 'my_steel', 'my_silver', decal=lambda s: rivets(3, 4))
    L.ribs(m, body, 'my_steel', 'my_silver', decal=rivets(2, 4))
    m.box('sternum', body, (0, 19.6, -2.6), (1.0, 5.6, .34), 'my_silver', decals={'north': rivets(1, 5)})
    L.abdomen(m, body, 'my_steel', 'my_silver', count=3, decal=lambda i: rivets(5, 1))
    L.sash(m, body, 'my_leather', 'my_silver', 'my_silver', gem='my_crystal', gem_glow=True)
    # Crossed belt over the back plate.
    L.back_plate(m, body, 'my_steel', 'my_silver', 'my_silver', decal=rivets(5, 3))
    for s in (-1, 1):
        m.box(f'cross_belt_{s}', body, (0, 16.4, 2.62), (.7, 5.4, .2), 'my_leather', rotation=(0, 0, s * 36),
              decals={'south': [L.line([[(.5, 0), (.5, 1)]], .1, 'my_silver')]})
    for s, label in ((-1, 'Right'), (1, 'Left')):
        arm = arms[s]
        L.spaulder(m, arm, s, label, 'my_steel', 'my_silver', 'my_suit', cap_decal=rivets(3, 2))
        L.forearm(m, arm, s, label, 'my_suit', 'my_steel', 'my_silver', 'my_silver', 'my_leather', sleeve_band='my_silver',
                  outer_decal=rivets(1, 4), face_decal=lambda f: rivets(2, 3))
        L.faulds(m, legs[s], s, label, 'my_steel', 'my_silver', 'my_leather', 'my_silver', decal=lambda s: rivets(4, 2))
    return m


def spirit_source():
    """灵源秘甲 v4: silver light armor with blue enamel inlays, feather spaulders and ribbons."""
    register('sp_silver', 'plate', rgb('D5DBE3'), shade=rgb('7C8794'), light=rgb('F7FAFD'), vein=3, gloss=30)
    register('sp_edge', 'metal', rgb('BFC8D3'), shade=rgb('5E6875'), light=rgb('F4F8FC'), rim_light=55)
    register('sp_blue', 'lacquer', rgb('2F6FC8'), shade=rgb('123067'), gloss=30)
    register('sp_suit', 'cloth', rgb('2C3E5C'), shade=rgb('0F1726'), weave=2.4)
    register('sp_seam', 'lacquer', rgb('1A2638'), shade=rgb('090E16'))
    register('sp_ribbon', 'cloth', rgb('8FB8EA'), shade=rgb('3D6496'), weave=2.0)
    register('sp_glow', 'glow', rgb('7CC4FF'), light=rgb('E4F4FF'))
    register('sp_gem', 'gem', rgb('6CC0FF'), shade=rgb('14508E'), light=rgb('EAF7FF'), facets=4)
    blue = (110, 190, 255)
    inlay = lambda paths, w=.05: L.line(paths, w, 'sp_blue')
    m, body, arms, legs = armor_model('spirit_source', density=26)
    L.suit(m, body, 'sp_suit', 'sp_seam', 'sp_suit', 'sp_edge')
    L.straps(m, body, 'sp_suit', 'sp_edge', 'sp_edge')
    L.pectorals(m, body, 'sp_silver', 'sp_edge', inner='sp_blue',
                decal=lambda s: [inlay([[(.5, .9), (.5, .12)]], .07),
                                  inlay([[(.5, .7), (.25, .55)], [(.5, .7), (.75, .55)], [(.5, .45), (.25, .3)], [(.5, .45), (.75, .3)]], .045)])
    L.ribs(m, body, 'sp_silver', 'sp_edge', decal=[inlay([[(.5, .9), (.5, .1)]], .08)])
    # Blue crystal heart in a silver diamond, with a glowing stem.
    m.box('heart_setting', body, (0, 21.15, -2.9), (1.8, 1.8, .3), 'sp_edge', rotation=(0, 0, 45))
    m.box('heart', body, (0, 21.15, -3.1), (1.0, 1.0, .22), 'sp_gem', rotation=(0, 0, 45), glow_face=True)
    m.box('sternum', body, (0, 18.4, -2.6), (.9, 3.6, .32), 'sp_suit',
          decals={'north': [L.line([[(.5, .95), (.5, .05)]], .12, 'sp_glow', blue)]})
    L.abdomen(m, body, 'sp_silver', 'sp_edge', count=2, decal=lambda i: [inlay([[(.1, .5), (.9, .5)]], .1)])
    L.sash(m, body, 'sp_ribbon', 'sp_blue', 'sp_edge', gem='sp_gem', gem_glow=True)
    L.back_plate(m, body, 'sp_silver', 'sp_edge', 'sp_suit',
                 decal=[inlay([[(.15, .2), (.5, .6), (.85, .2)], [(.3, .85), (.5, .6), (.7, .85)]], .05)])
    # Two long ribbons from the back strap clasps.
    for s in (-1, 1):
        for k, (dx, h, tilt) in enumerate(((0, 12.5, 3), (.75, 10.5, 6))):
            x = s * (2.6 + dx)
            m.box(f'ribbon_{s}_{k}', body, (x, 23.3 - h / 2, 3.0 + .05 * k), (.7, h, .12), 'sp_ribbon',
                  rotation=(-5, 0, s * tilt), pivot=(x, 23.3, 3.0),
                  decals={'south': [L.line([[(.5, 0), (.5, 1)]], .12, 'sp_blue')]})
    for s, label in ((-1, 'Right'), (1, 'Left')):
        arm = arms[s]
        o = outer(s)
        L.spaulder(m, arm, s, label, 'sp_silver', 'sp_edge', 'sp_suit',
                   cap_decal=[inlay([[(.1, .5), (.9, .5)]], .08)])
        # Three feather plates fanned back from the shoulder.
        root = (s * 7.4, 24.2, .9)
        for j, (fan, ln) in enumerate(((10, 3.4), (30, 3.0), (50, 2.5))):
            m.box(f'{label}_feather_{j}', arm, (root[0], root[1] + ln / 2, root[2]), (.24, ln, .9), 'sp_silver',
                  rotation=(fan + 25, 0, -s * 28), pivot=root, trim=('sp_edge', .1),
                  decals={o: [L.line([[(.5, .95), (.5, .05)]], .14, 'sp_glow', blue)]})
        L.forearm(m, arm, s, label, 'sp_suit', 'sp_silver', 'sp_edge', 'sp_edge', 'sp_blue', sleeve_band='sp_ribbon',
                  outer_decal=[L.line([[(.5, .86), (.5, .14)]], .1, 'sp_glow', blue)],
                  face_decal=lambda f: [inlay([[(.2, .2), (.5, .5), (.8, .2)], [(.2, .6), (.5, .9), (.8, .6)]], .06)])
        L.faulds(m, legs[s], s, label, 'sp_silver', 'sp_edge', 'sp_suit', 'sp_ribbon',
                 decal=lambda s: [inlay([[(.15, .3), (.5, .75), (.85, .3)]], .07)])
    return m


def dark_source():
    """暗源之甲 v4: black lacquer light armor with violet void cracks and a ragged half-cape."""
    register('dk_plate', 'lacquer', rgb('1D1925'), shade=rgb('07060A'), gloss=34)
    register('dk_edge', 'metal', rgb('8B6BC2'), shade=rgb('35214F'), light=rgb('D2BEF5'), rim_light=50)
    register('dk_suit', 'cloth', rgb('2A1F3A'), shade=rgb('0D0913'), weave=2.4)
    register('dk_seam', 'lacquer', rgb('120E18'), shade=rgb('050407'))
    register('dk_cape', 'cloth', rgb('3A2457'), shade=rgb('120A1E'), weave=2.0)
    register('dk_void', 'glow', rgb('B45CFF'), light=rgb('F0DCFF'))
    register('dk_gem', 'gem', rgb('B06CFF'), shade=rgb('3E1478'), light=rgb('F2E4FF'), facets=4)
    violet = (180, 90, 255)
    crack = lambda seed, n=2: [{**veins(violet, seed, n, .035, .9), 'material': 'dk_void'}]
    m, body, arms, legs = armor_model('dark_source', density=26)
    L.suit(m, body, 'dk_suit', 'dk_seam', 'dk_plate', 'dk_edge')
    L.straps(m, body, 'dk_plate', 'dk_edge', 'dk_edge')
    L.pectorals(m, body, 'dk_plate', 'dk_edge', rivet='dk_edge', decal=lambda s: crack(11 + s))
    L.ribs(m, body, 'dk_plate', 'dk_edge', decal=crack(21))
    # Void gem in a violet bezel over the sternum.
    m.ring('gem_ring', body, (0, 21.1, -2.95), .95, 14, .22, .3, 'dk_edge', bevel=.04)
    m.box('gem', body, (0, 21.1, -3.0), (1.3, 1.3, .3), 'dk_gem', rotation=(0, 0, 45), glow_face=True)
    m.box('sternum', body, (0, 18.4, -2.6), (.9, 3.6, .32), 'dk_plate',
          decals={'north': [L.line([[(.5, .95), (.5, .05)]], .12, 'dk_void', violet)]})
    L.abdomen(m, body, 'dk_plate', 'dk_edge', count=2, decal=lambda i: crack(31 + i, 1))
    L.sash(m, body, 'dk_cape', 'dk_edge', 'dk_edge', gem='dk_gem', gem_glow=True)
    L.back_plate(m, body, 'dk_plate', 'dk_edge', 'dk_plate', decal=crack(41, 3))
    # Ragged half-cape hanging from the right shoulder blade.
    for i, (x, h, tilt) in enumerate(((-3.3, 9.6, 4), (-2.2, 10.8, 2), (-1.1, 9.2, -1), (0.0, 7.6, -3), (1.0, 5.6, -5))):
        m.box(f'cape_{i}', body, (x, 23.6 - h / 2, 3.05 + .04 * i), (1.15, h, .14), 'dk_cape', rotation=(-4, 0, tilt),
              pivot=(x, 23.6, 3.0), decals={'south': [L.line([[(.5, 0), (.5, 1)]], .05, 'dk_seam')]})
    m.box('cape_clasp', body, (-2.2, 23.4, 2.9), (3.6, .6, .3), 'dk_edge')
    for s, label in ((-1, 'Right'), (1, 'Left')):
        arm = arms[s]
        L.spaulder(m, arm, s, label, 'dk_plate', 'dk_edge', 'dk_suit', cap_decal=crack(51 + s, 1))
        L.forearm(m, arm, s, label, 'dk_suit', 'dk_plate', 'dk_edge', 'dk_edge', 'dk_plate', sleeve_band='dk_edge',
                  outer_decal=[L.line([[(.5, .86), (.5, .14)]], .1, 'dk_void', violet)],
                  face_decal=lambda f: crack(61, 1))
        L.faulds(m, legs[s], s, label, 'dk_plate', 'dk_edge', 'dk_suit', 'dk_edge', decal=lambda s: crack(71 + s, 1))
    # One curved horn on the left spaulder: three segments bending back.
    arm = arms[1]
    base = (7.0, 24.6, -.2)
    for k, (h, ang, w) in enumerate(((1.4, 15, .55), (1.3, 40, .45), (1.1, 68, .34))):
        y = base[1] + sum(hh * math.cos(math.radians(aa)) for hh, aa, _ in ((1.4, 15, 0), (1.3, 40, 0), (1.1, 68, 0))[:k])
        z = base[2] + sum(hh * math.sin(math.radians(aa)) for hh, aa, _ in ((1.4, 15, 0), (1.3, 40, 0), (1.1, 68, 0))[:k])
        m.box(f'horn_{k}', arm, (base[0], y + h / 2, z), (w, h, w), 'dk_plate' if k < 2 else 'dk_edge',
              rotation=(ang, 0, 0), pivot=(base[0], y, z), bevel=.05)
    return m


def origin_dragon():
    """始源龙甲 v4: orange-glow dragon light armor over dark bronze leather."""
    register('dr_plate', 'plate', rgb('E2661A'), shade=rgb('8C300A'), light=rgb('FF9446'), vein=4, gloss=30)
    register('dr_scale', 'scale', rgb('DC5E16'), shade=rgb('6E2408'), light=rgb('FF9A48'))
    register('dr_leather', 'cloth', rgb('2C1F18'), shade=rgb('0F0A07'), weave=2.6)
    register('dr_dark', 'lacquer', rgb('241A15'), shade=rgb('0A0705'), gloss=28)
    register('dr_bronze', 'metal', rgb('C4904F'), shade=rgb('5E3B1C'), light=rgb('F5D6A2'), rim_light=50)
    register('dr_ember', 'glow', rgb('FF9A3C'), light=rgb('FFE4B4'))
    register('dr_eye', 'gem', rgb('FFB24C'), shade=rgb('B0420E'), light=rgb('FFF2CC'), facets=5)
    ember = (255, 150, 60)
    m, body, arms, legs = armor_model('origin_dragon', density=26)
    L.suit(m, body, 'dr_leather', 'dr_dark', 'dr_dark', 'dr_bronze')
    L.straps(m, body, 'dr_dark', 'dr_bronze', 'dr_bronze')
    for s in (-1, 1):
        m.box(f'pectoral_{s}', body, (s * 2.55, 21.3, -2.6), (3.0, 3.8, .42), 'dr_scale', rotation=(4, s * 14, -s * 1.5),
              trim=('dr_bronze', .12), trim_faces=('north',), scale_rows=1.5, scale_cols=1.3)
        m.box(f'pectoral_rim_{s}', body, (s * 2.6, 23.3, -2.45), (2.9, .32, .45), 'dr_bronze', rotation=(18, s * 13, 0))
    L.ribs(m, body, 'dr_plate', 'dr_bronze', decal=[L.line([[(.5, .9), (.5, .1)]], .1, 'dr_ember', ember)])
    # Dragon-eye gem in a bronze diamond setting with glowing claw strokes.
    m.box('eye_setting', body, (0, 21.2, -2.9), (2.0, 2.0, .3), 'dr_bronze', rotation=(0, 0, 45), trim=('dr_dark', .1))
    m.box('eye_gem', body, (0, 21.2, -3.1), (1.1, 1.6, .2), 'dr_eye', glow_face=True)
    m.box('sternum', body, (0, 18.3, -2.6), (.9, 3.4, .32), 'dr_dark',
          decals={'north': [L.line([[(.5, .95), (.5, .05)]], .12, 'dr_ember', ember)]})
    L.abdomen(m, body, 'dr_plate', 'dr_bronze', count=2,
              decal=lambda i: [L.line([[(.1, .5), (.35, .5)], [(.65, .5), (.9, .5)]], .1, 'dr_ember', ember)])
    L.sash(m, body, 'dr_leather', 'dr_bronze', 'dr_bronze', gem='dr_eye', gem_glow=True,
           buckle_decal=[{'kind': 'cloud', 'style': 'engrave', 'width': .08, 'at': [(.5, .55, .8, 1)]}])
    # Back: scaled shoulder plate and a row of dorsal fins with glowing edges.
    m.box('back_plate', body, (0, 21.2, 2.55), (6.8, 4.6, .4), 'dr_scale', rotation=(-6, 0, 0),
          trim=('dr_bronze', .12), trim_faces=('south',), scale_rows=1.5, scale_cols=1.3)
    for i, (y, h) in enumerate(((23.0, 1.5), (21.4, 1.35), (19.8, 1.2), (18.2, 1.05), (16.6, .9), (15.1, .75))):
        m.box(f'dorsal_{i}', body, (0, y, 3.0 + .05 * (5 - i)), (.32, h, 1.3 - .1 * i), 'dr_plate', rotation=(-38, 0, 0),
              trim=('dr_bronze', .1), decals={f: [L.line([[(.15, .9), (.85, .1)]], .12, 'dr_ember', ember)] for f in ('east', 'west')})
    for s, label in ((-1, 'Right'), (1, 'Left')):
        arm = arms[s]
        o = outer(s)
        L.spaulder(m, arm, s, label, 'dr_plate', 'dr_bronze', 'dr_dark',
                   cap_decal=[L.line([[(.1, .5), (.9, .5)]], .1, 'dr_ember', ember)])
        # Two small horns swept back from the spaulder.
        for k, (z, h) in enumerate(((-.8, 2.6), (.8, 1.9))):
            m.box(f'{label}_horn_{k}', arm, (s * 6.9, 24.6 + h / 2, z + .4), (.3, h, .34), 'dr_bronze',
                  rotation=(-52, 0, -s * 18), pivot=(s * 6.9, 24.6, z + .4), bevel=.05)
            m.box(f'{label}_horn_tip_{k}', arm, (s * 6.9, 24.6 + h + .35, z + .4), (.2, .7, .22), 'dr_ember',
                  rotation=(-52, 0, -s * 18), pivot=(s * 6.9, 24.6, z + .4), glow_face=True)
        L.forearm(m, arm, s, label, 'dr_leather', 'dr_plate', 'dr_bronze', 'dr_bronze', 'dr_dark', sleeve_band='dr_bronze',
                  outer_decal=[L.line([[(.5, .86), (.5, .14)]], .1, 'dr_ember', ember)],
                  face_decal=lambda f: [{'kind': 'scale' if False else 'lines', 'style': 'inlay', 'material': 'dr_bronze',
                                         'width': .06, 'paths': [[(.2, .25), (.5, .1), (.8, .25)], [(.2, .55), (.5, .4), (.8, .55)],
                                                                 [(.2, .85), (.5, .7), (.8, .85)]]}])
        m.box(f'{label}_fin', arm, (s * 8.55, 14.9, .6), (.26, 2.4, 1.0), 'dr_bronze', rotation=(-20, 0, -s * 6))
        L.faulds(m, legs[s], s, label, 'dr_scale', 'dr_bronze', 'dr_leather', 'dr_bronze')
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
