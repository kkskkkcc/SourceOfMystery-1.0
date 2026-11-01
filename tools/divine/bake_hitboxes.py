"""Bake vanilla AABB envelopes from the existing GeckoLib geometry/animations.

Coordinates follow GeckoLib 4.8.4 BakedModelFactory.Builtin and RenderUtils:
mirror model X, negate rotation X/Y, apply Z/Y/X rotations about bone pivots.
Each action envelope includes every idle phase and the idle envelope, so a
controller transition cannot temporarily lose the head, hands or tail.
"""
import hashlib
import itertools
import json
from pathlib import Path

import numpy as np
from scipy.spatial.transform import Rotation

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'src/main/resources/assets/sourceofmystery'
GEO = ASSETS / 'geo/entity/divine_heavenly_dao_boss.geo.json'
ANIM = ASSETS / 'animations/entity/divine_heavenly_dao_boss.animation.json'
PROFILES = ['idle', 'attack', 'thunder_strike', 'slam', 'orb_charge', 'orb_throw', 'descend']
MIRROR = np.array([-1., 1., 1.])
ROTATION_SIGN = np.array([-1., -1., 1.])


def sample(channel, seconds):
    if isinstance(channel, list):
        return np.asarray(channel, dtype=float)
    keys = sorted((float(t), value) for t, value in channel.items())

    def value(index):
        v = keys[max(0, min(index, len(keys)-1))][1]
        return np.asarray(v.get('post', v.get('pre')) if isinstance(v, dict) else v, dtype=float)

    i = max(0, min(np.searchsorted([t for t, _ in keys], seconds, side='right')-1, len(keys)-1))
    if i == len(keys)-1 or seconds <= keys[0][0]:
        return value(i)
    u = np.clip((seconds-keys[i][0])/(keys[i+1][0]-keys[i][0]), 0, 1)
    a, b = value(i), value(i+1)
    if any(isinstance(keys[k][1], dict) and keys[k][1].get('lerp_mode') == 'catmullrom' for k in [i, i+1]):
        before, after = value(i-1), value(i+2)
        return .5*((2*a)+(-before+b)*u+(2*before-5*a+4*b-after)*u*u+(-before+3*a-3*b+after)*u*u*u)
    return a+(b-a)*u


def main():
    bones = json.loads(GEO.read_text(encoding='utf-8'))['minecraft:geometry'][0]['bones']
    clips = {k.rsplit('.', 1)[-1]: v for k, v in json.loads(ANIM.read_text(encoding='utf-8'))['animations'].items()}
    corners = np.asarray(list(itertools.product([0., 1.], repeat=3)))
    geometry = {}
    for bone in bones:
        points = []
        for cube in bone.get('cubes', []):
            inflate = cube.get('inflate', bone.get('inflate', 0))
            p = (np.asarray(cube['origin'])-inflate+corners*(np.asarray(cube['size'])+2*inflate))*MIRROR
            pivot = np.asarray(cube.get('pivot', [0, 0, 0]))*MIRROR
            rot = Rotation.from_euler('xyz', np.asarray(cube.get('rotation', [0, 0, 0]))*ROTATION_SIGN, degrees=True)
            points.extend(rot.apply(p-pivot)+pivot)
        geometry[bone['name']] = np.asarray(points).reshape(-1, 3)

    def pose(action, seconds, idle_seconds):
        matrices, all_points = {}, []
        for bone in bones:
            name = bone['name']
            channels = dict(clips['idle']['bones'].get(name, {}))
            # Separate channels: an action rotation does not erase idle position.
            for key, v in clips[action]['bones'].get(name, {}).items():
                channels[key] = v
            action_channels = clips[action]['bones'].get(name, {}) if action != 'idle' else {}
            values = {key: sample(v, seconds if key in action_channels else idle_seconds) for key, v in channels.items()}
            pivot = np.asarray(bone.get('pivot', [0, 0, 0]))*MIRROR
            angles = np.asarray(bone.get('rotation', [0, 0, 0]))+values.get('rotation', np.zeros(3))
            rot = Rotation.from_euler('xyz', angles*ROTATION_SIGN, degrees=True).as_matrix()
            mat = np.eye(4)
            mat[:3, :3] = rot @ np.diag(values.get('scale', np.ones(3)))
            mat[:3, 3] = pivot-mat[:3, :3]@pivot+values.get('position', np.zeros(3))*MIRROR
            if 'parent' in bone:
                mat = matrices[bone['parent']]@mat
            matrices[name] = mat
            if len(geometry[name]):
                all_points.append(geometry[name]@mat[:3, :3].T+mat[:3, 3])
        return np.concatenate(all_points)*12/16

    envelopes, report = {}, {}
    for name in PROFILES:
        low, high = np.full(3, np.inf), np.full(3, -np.inf)
        duration = clips[name]['animation_length']
        # Idle is continuous; actions may start at any point in its cycle.
        phases = [None] if name == 'idle' else np.arange(0, 4, .25)
        for t in np.linspace(0, duration, round(duration*60)+1):
            for phase in phases:
                points = pose(name, t, t if phase is None else (t+phase)%4)
                low = np.minimum(low, points.min(axis=0))
                high = np.maximum(high, points.max(axis=0))
        if name != 'idle':
            low = np.minimum(low, envelopes['idle'][:3])
            high = np.maximum(high, envelopes['idle'][3:])
        # Round outward to 1/4 block; allow interpolation/tick/float tolerance.
        bounds = np.r_[np.floor((low-.125)*4)/4, np.ceil((high+.125)*4)/4]
        envelopes[name] = bounds
        report[name] = dict(bounds=bounds.tolist(), duration=duration)
        print(name, bounds.tolist(), flush=True)
    idle = envelopes['idle']
    width = max(idle[3]-idle[0], idle[5]-idle[2])
    height = idle[4]-idle[1]
    lines = [
        'package com.sourceofmystery.entity;', '',
        '/** Generated by tools/divine/bake_hitboxes.py from the shipped GeckoLib model. */',
        'final class DivineModelBounds {',
        f'    static final float WIDTH = {width:.2f}f, HEIGHT = {height:.2f}f;',
        '    // Renderer-local block coordinates, including the negative-Y tail.',
        '    static final double[][] PROFILES = {'
    ]
    for name in PROFILES:
        lines.append('        {'+', '.join(f'{v:.2f}' for v in envelopes[name])+'}, // '+name)
    lines += ['    };', '    private DivineModelBounds() {}', '}', '']
    (ROOT/'src/main/java/com/sourceofmystery/entity/DivineModelBounds.java').write_text('\n'.join(lines), encoding='utf-8')
    out = ROOT/'art/divine_heavenly_dao/hitbox-checks.json'
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(dict(geometry_sha256=hashlib.sha256(GEO.read_bytes()).hexdigest(),
                                  animation_sha256=hashlib.sha256(ANIM.read_bytes()).hexdigest(),
                                  samples_per_second=60, idle_phase_samples=16, profiles=report), indent=2), encoding='utf-8')


if __name__ == '__main__':
    main()
