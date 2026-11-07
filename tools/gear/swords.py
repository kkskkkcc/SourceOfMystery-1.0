"""Five swords of the mod, designed by name and tier. python tools/gear/swords.py [id ...]

mystery_sword          秘源剑   gray-silver steel, small cyan source-ore accents
spirit_source_sword    灵源秘剑 silver leaf blade with blue veins
dark_source_sword      暗源之剑 black jagged blade with violet void cracks
origin_dragon_sword    始源龙剑 bronze blade with ember edges in a dragon's jaws
divine_punishment_sword 神威天罚 white blade, black fuller, taiji guard
"""
import math
import sys
import zlib
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from forge3d import ASSETS, ROOT  # noqa: E402
from kit import blade, sword_model  # noqa: E402
from paint import register, rgb  # noqa: E402


def glow_line(color, width=.3, path=((.5, .95), (.5, .05)), alpha=1.0):
    return {'kind': 'lines', 'style': 'inlay', 'material': 'gold', 'width': width, 'paths': [list(path)],
            'glow': color, 'glow_alpha': alpha, 'tint': .95}


def mystery_sword():
    register('ms_steel', 'metal', rgb('A7ADB5'), shade=rgb('4C5158'), light=rgb('EEF0F3'))
    register('ms_edge', 'metal', rgb('DCE6EE'), shade=rgb('7D8A96'), light=rgb('FFFFFF'), rim_light=60)
    register('ms_dark', 'metal', rgb('62676E'), shade=rgb('26292D'), light=rgb('A9AEB5'))
    register('ms_leather', 'cloth', rgb('34373C'), shade=rgb('131417'), weave=3)
    register('ms_crystal', 'gem', rgb('7FF6F2'), shade=rgb('147A86'), light=rgb('E8FFFF'), facets=3)
    register('gold', 'metal', rgb('C3C8CF'), shade=rgb('646A73'), light=rgb('F2F4F7'))
    cyan = (120, 250, 245)
    m, r = sword_model('mystery_sword')
    blade(m, r, 2.2, 22, 3.0, .7, 'ms_steel', 'ms_edge', tip=3.6, segments=5, taper=.22,
          fuller=lambda i, n: [glow_line(cyan, .16, ((.5, 1.0), (.5, 0.0 if i else .15)), .85)])
    m.box('guard', r, (0, 1.55, 0), (8.0, 1.0, 1.4), 'ms_dark', trim=('ms_edge', .08),
          decals={f: [{'kind': 'runes', 'style': 'engrave', 'seed': 4, 'count': 4, 'width': .08}] for f in ('north', 'south')})
    m.box('guard_core', r, (0, 1.6, 0), (2.0, 1.8, 1.7), 'ms_steel', trim=('ms_edge', .08))
    m.box('guard_gem', r, (0, 1.75, 0), (.9, .9, 1.85), 'ms_crystal', glow_face=True)
    for s in (-1, 1):
        m.box(f'guard_tip_{s}', r, (s * 4.4, 1.85, 0), (1.0, 1.0, 1.0), 'ms_crystal', rotation=(0, 0, 45), glow_face=True)
        m.box(f'guard_arm_{s}', r, (s * 3.0, 1.15, 0), (2.2, .45, 1.1), 'ms_dark', rotation=(0, 0, -s * 14))
    m.box('grip', r, (0, -2.4, 0), (1.15, 6.4, 1.15), 'ms_leather',
          decals={f: [{'kind': 'lines', 'style': 'engrave', 'width': .12, 'depth': 50,
                       'paths': [[(0, k / 8 + .03), (1, k / 8 + .1)] for k in range(9)]}] for f in ('north', 'south', 'east', 'west')})
    m.box('grip_ring', r, (0, .55, 0), (1.45, .5, 1.45), 'ms_dark')
    m.box('pommel_cap', r, (0, -5.85, 0), (1.6, .6, 1.6), 'ms_dark')
    m.box('pommel', r, (0, -6.75, 0), (1.25, 1.25, 1.25), 'ms_crystal', rotation=(45, 45, 0), glow_face=True)
    return m


from sword_designs import DESIGNS as HIGHER  # noqa: E402

DESIGNS = {'mystery_sword': mystery_sword, **HIGHER}


def export(name):
    m = DESIGNS[name]()
    info = m.export(ASSETS / f'geo/item/{name}.geo.json', ASSETS / f'textures/gear/{name}.png',
                    ASSETS / f'textures/gear/{name}_glowmask.png', seed=zlib.crc32(name.encode()) & 0xFFFF,
                    manifest_path=ROOT / f'tools/gear/out/{name}.parts.json')
    print(name, info)


if __name__ == '__main__':
    for n in sys.argv[1:] or DESIGNS:
        export(n)
