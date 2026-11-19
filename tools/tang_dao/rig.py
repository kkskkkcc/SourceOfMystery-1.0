"""First-person Tang Dao rig, mirrored by TangDaoRig.java.

Hand-render space: camera at the origin, -Z forward, +Y up, units in blocks (the vanilla
hand pass, FOV 70). Both arms are the vanilla arm model at its native size; nothing is
stretched. Keys store where the main fist is, where the blade points and which way the edge
faces; every frame solves the arm from them:

  knuckles face the edge, so the forearm lies in the blade/edge plane:
      forearm = sin(dev) * blade + cos(dev) * edge
  dev 0 holds the blade square to the forearm, larger dev tips it out along the forearm.
  The arm's flat side faces the blade's flat, and its fist centre sits on the hand point.

The knife is drawn in the fist frame, so it always moves with the arm. Mirroring the x of
every authored vector gives the left-handed version; the same formulas apply to both sides.

Channels per key (right-handed view space):
  hand[3] blade[3] edge[3] dev         main fist, blade direction, edge direction, grip angle
  off_hand[3] off_from[3] off_roll     free fist and the point its arm comes from
  reach along travel drop              free hand onto scabbard/blade, scabbard slide and drop
"""
import json
import math
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'src/main/resources/assets/sourceofmystery'
CLIPS = ASSETS / 'first_person/tang_dao.json'
CHANNELS = (('hand', 3), ('blade', 3), ('edge', 3), ('dev', 1), ('off_hand', 3), ('off_from', 3),
            ('off_roll', 1), ('reach', 1), ('along', 1), ('travel', 1), ('drop', 1))
SLICE = {}
_i = 0
for _n, _w in CHANNELS:
    SLICE[_n] = slice(_i, _i + _w)
    _i += _w
WIDTH = _i
FIST = 8 / 16             # fist centre below the arm pivot


def unit(v):
    v = np.asarray(v, float)
    return v / np.linalg.norm(v)


def one(v, name):
    return float(v[SLICE[name]][0])


class Rig:
    def __init__(self, data):
        r = data['rig']
        self.scale = r['blade_scale']
        self.guard = r['guard']
        self.fist_front = r['fist_front']
        self.tip = r['tip']
        self.mouth = r['scabbard_mouth']
        self.drop = np.array(r['scabbard_drop'])

    @staticmethod
    def blade_frame(v):
        b = unit(v[SLICE['blade']])
        e = np.asarray(v[SLICE['edge']], float)
        e = unit(e - b * (e @ b))
        return e, b, np.cross(e, b)

    def main_arm(self, side, v):
        """Pivot and rotation (columns: arm x, y, z) of the main arm; side 1 right, -1 left."""
        e, b, f = self.blade_frame(v)
        d = math.radians(one(v, 'dev'))
        a = math.sin(d) * b + math.cos(d) * e
        x = -f
        rot = np.column_stack([x, a, np.cross(x, a)])
        hand = np.asarray(v[SLICE['hand']], float)
        return hand - rot @ np.array([-side / 16, FIST, 0]), rot

    def blade(self, side, v):
        """Item origin and rotation (columns: edge +X, blade +Y, flat +Z)."""
        e, b, f = self.blade_frame(v)
        origin = np.asarray(v[SLICE['hand']], float) + b * (self.fist_front - self.guard * self.scale)
        return origin, np.column_stack([e, b, f])

    def scabbard(self, side, v, anchor):
        origin, item = anchor
        mouth = origin + item[:, 1] * self.scale * (self.mouth + one(v, 'travel') * (self.tip + self.mouth))
        return mouth + self.drop * [side, 1, 1] * one(v, 'drop'), item

    def off_arm(self, side, v, target):
        hand = np.asarray(v[SLICE['off_hand']], float)
        w = one(v, 'reach')
        if target is not None and w > 0:
            hand = hand * (1 - w) + target * w
        a = unit(hand - np.asarray(v[SLICE['off_from']], float))
        x = np.cross(a, [0, 0, 1])
        x = unit(x if np.linalg.norm(x) > 1e-4 else [1, 0, 0])
        roll = math.radians(one(v, 'off_roll'))
        x = x * math.cos(roll) + np.cross(a, x) * math.sin(roll)
        rot = np.column_stack([x, a, np.cross(x, a)])
        return hand - rot @ np.array([-side / 16, FIST, 0]), rot


def mirror(v):
    """Left-handed channels: mirror x of every point and direction."""
    v = np.array(v, float)
    for name in ('hand', 'blade', 'edge', 'off_hand', 'off_from'):
        v[SLICE[name].start] *= -1
    return v


# ---------------------------------------------------------------- clips
def load():
    return json.loads(CLIPS.read_text(encoding='utf8'))


def key_vector(key):
    out = []
    for name, n in CHANNELS:
        value = key[name]
        out += list(value) if n > 1 else [value]
    return np.array(out, float)


def sample(times, values, t):
    """Hermite through the keys with Catmull-Rom tangents (zero at both ends)."""
    if t <= times[0]:
        return values[0]
    if t >= times[-1]:
        return values[-1]
    i = int(np.searchsorted(times, t, side='right')) - 1
    t0, t1 = times[i], times[i + 1]
    span = t1 - t0
    u = (t - t0) / span

    def tangent(j):
        if j == 0 or j == len(times) - 1:
            return np.zeros_like(values[j])
        return (values[j + 1] - values[j - 1]) / (times[j + 1] - times[j - 1])
    m0, m1 = tangent(i) * span, tangent(i + 1) * span
    h00 = 2 * u ** 3 - 3 * u ** 2 + 1; h10 = u ** 3 - 2 * u ** 2 + u
    h01 = -2 * u ** 3 + 3 * u ** 2; h11 = u ** 3 - u ** 2
    return h00 * values[i] + h10 * m0 + h01 * values[i + 1] + h11 * m1


class Player:
    """Samples one clip in view space for one side."""

    def __init__(self, data, rig, name, side=1, start=None):
        self.clip = data['clips'][name]
        self.times = np.array([k['t'] for k in self.clip['keys']], float)
        self.values = [key_vector(k) for k in self.clip['keys']]
        if start is not None:
            self.values[0] = start
        if side < 0:
            self.values = [mirror(v) for v in self.values]
        self.rig, self.side = rig, side

    def vector(self, t):
        return sample(self.times, self.values, t)

    def frame(self, t):
        v = self.vector(t)
        rig, side = self.rig, self.side
        blade = rig.blade(side, v)
        fixed = self.clip.get('scabbard_anchor')
        anchor = rig.blade(side, self.vector(fixed)) if fixed is not None else blade
        scab = rig.scabbard(side, v, anchor)
        along = one(v, 'along')
        if self.clip.get('reach_to', 'scabbard') == 'blade':
            target = blade[0] + blade[1][:, 1] * along
        else:
            target = scab[0] + scab[1][:, 1] * along
        show = not (one(v, 'travel') >= .999 and one(v, 'drop') >= .999)
        return dict(v=v, blade=blade, scabbard=scab if show else None,
                    main=rig.main_arm(side, v), off=rig.off_arm(-side, v, target))
