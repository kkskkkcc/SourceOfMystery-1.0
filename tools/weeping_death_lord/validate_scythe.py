"""Validate the two scythe exports and the attack animation hand attachment."""

import json
import math
import os

import numpy as np
from PIL import Image

from anims import ANIMS
from anim_lib import GEO, ASSETS, _scythe_frame, bone_world, mirror, solve_scythe
from geo_render import load
from scythe_design import SWATCH_UV, scythe_cubes


def main():
    held = next(b for b in GEO['bones'] if b['name'] == 'Scythe')
    thrown = load(os.path.join(ASSETS, 'geo/entity/weeping_scythe.geo.json'))['bones'][0]
    expected = len(scythe_cubes())
    assert len(held['cubes']) == len(thrown['cubes']) == expected
    for a, b in zip(held['cubes'], thrown['cubes']):
        held_source = np.array(a['origin']) - (0, -13, 0)
        thrown_source = np.array(b['origin']) + (-7.9, 26, -12) - (0, 8, 0)
        assert np.allclose(held_source, thrown_source, atol=1e-4)
        assert a['size'] == b['size'] and a['uv'] == b['uv']

    tex = np.array(Image.open(os.path.join(ASSETS, 'textures/entity/weeping_death_lord.png')))
    for u, v in SWATCH_UV.values():
        assert np.all(tex[v:v + 8, u:u + 8, 3] == 255)

    exported = json.load(open(os.path.join(ASSETS, 'animations/entity/weeping_death_lord.animation.json')))
    assert all(a.name in exported['animations'] for a in ANIMS)
    checked = 0
    for anim in ANIMS:
        if anim.name == 'wl_blink':
            continue
        for t in np.linspace(0, anim.length, 9):
            pose = anim.fn(float(t))
            spec = pose.get('@scythe')
            if not spec:
                continue
            solved = solve_scythe(pose)
            _, world = bone_world(GEO, solved)
            pivot = world('Scythe') @ np.append(mirror(held['pivot']), 1)
            A, hand = _scythe_frame(pose, spec)
            # The blade roll does not affect the haft's z axis.
            expected_pivot = hand + A[:, 2] * spec['slide']
            assert np.linalg.norm(pivot[:3] - expected_pivot) < 1e-4, (anim.name, t)
            assert all(math.isfinite(x) for x in solved['Scythe']['rot'])
            checked += 1
    print(f'validated {expected} shared cubes and {checked} held attack/idle poses')


if __name__ == '__main__':
    main()
