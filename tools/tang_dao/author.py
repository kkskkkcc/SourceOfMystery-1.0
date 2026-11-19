"""Author the first-person Tang Dao clips and write first_person/tang_dao.json.

python tools/tang_dao/author.py
Keys are written in view terms for a right-handed player: where the main fist is, where the
tip points, which way the edge faces. The grip angle (dev) is picked per key so the forearm
shows as little as possible and its top stays out of view, like the vanilla first-person arm.
Run report.py afterwards for the frame-by-frame checks.
"""
import json
import math
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import rig as R  # noqa: E402

TAN = math.tan(math.radians(35))
ASPECT = 854 / 480
DEV = (-20, 88)
RIG = dict(blade_scale=0.85, guard=0.03, fist_front=0.125, tip=1.825, scabbard_mouth=0.12,
           scabbard_drop=[-0.25, -0.9, 0.25])


def at(sx, sy, depth):
    """View point from screen coordinates (-1..1, y up) and depth in blocks."""
    return np.array([sx * depth * TAN * ASPECT, sy * depth * TAN, -depth])


# Free arm resting out of view under the left of the screen; its arm rises from below.
OFF_REST = at(-.55, -1.55, .75)
OFF_FROM = np.array([-.45, -1.6, -.35])


def on_screen(p, margin=.12):
    if p[2] > -.05:
        return False
    sx, sy = p[0] / -p[2] / TAN / ASPECT, p[1] / -p[2] / TAN
    return abs(sx) < 1 + margin and abs(sy) < 1 + margin


def visible_arm(hand, a, length=.625):
    """Length of the forearm centre line in view; a top end in view costs extra."""
    steps = np.linspace(0, length, 26)
    inside = [on_screen(hand - s * a) for s in steps]
    seen = steps[max(i for i, x in enumerate(inside) if x)] if any(inside) else 0.0
    return seen + (10 if inside[-1] else 0)


def grip(hand, b, e):
    b = R.unit(b)
    e = np.asarray(e, float); e = R.unit(e - b * (e @ b))
    arm = lambda d: math.sin(math.radians(d)) * b + math.cos(math.radians(d)) * e
    dev = min(np.arange(DEV[0], DEV[1] + .1, .5), key=lambda d: (visible_arm(hand, arm(d)), -d))
    return float(dev), visible_arm(hand, arm(dev))


def key(t, hand, tip=None, edge=(0, 0, -1), blade=None, dev=None, off_hand=OFF_REST, off_from=OFF_FROM,
        off_roll=0.0, reach=0.0, along=0.12, travel=1.0, drop=1.0):
    hand = np.asarray(hand, float)
    b = R.unit(np.asarray(blade, float) if blade is not None else np.asarray(tip, float) - hand)
    seen = None
    if dev is None:
        dev, seen = grip(hand, b, edge)
    if seen is not None and seen > .5:
        print(f'  t={t}: forearm in view {seen:.2f}')
    r = lambda xs: [round(float(x), 4) for x in xs]
    return {'t': t, 'hand': r(hand), 'blade': r(b), 'edge': r(edge), 'dev': round(dev, 2),
            'off_hand': r(off_hand), 'off_from': r(off_from), 'off_roll': off_roll,
            'reach': reach, 'along': along, 'travel': travel, 'drop': drop}


def clips():
    out = {}
    K = key
    # ------------------------------------------------------------ guards
    # Drawn guard: fist low at the right edge, blade standing toward the upper centre, edge forward.
    ready = dict(hand=at(.48, -.72, .95), tip=at(.10, .62, 2.2), edge=(-.1, -.15, -1))
    # Sheathed: right fist on the hilt low in the centre, scabbard running to the lower left with
    # the left fist at its mouth, edge up for the draw.
    h0 = at(-.08, -.80, .80)
    sheath_dir = R.unit(at(-1.15, -1.25, 1.75) - h0)
    hold = dict(off_from=np.array([-.75, -1.3, -.2]))
    sheathed = dict(hand=h0, blade=sheath_dir, edge=(.1, 1, .2), reach=1, along=.10, travel=0, drop=0, **hold)
    out['ready'] = {'keys': [K(0, **ready)]}
    out['sheathed'] = {'keys': [K(0, **sheathed)]}
    b = sheath_dir

    # ------------------------------------------------------------ draw cut (iai, left to right)
    out['draw'] = {'impact': 4, 'chain': 6, 'scabbard_anchor': 0, 'reach_to': 'scabbard', 'keys': [
        K(0, **sheathed),
        K(1, h0 - .38 * b, blade=b, edge=(.2, 1, .1), reach=1, along=.10, travel=0, drop=0, **hold),
        K(2, h0 - .80 * b + [0, .04, 0], blade=b + [.2, .25, -.15], edge=(.6, .7, -.3),
          reach=.9, along=.10, travel=0, drop=.15, **hold),
        K(3, at(-.30, -.82, .88), tip=at(-.80, -.12, 1.9), edge=(.8, .1, -.6), reach=.4, travel=0, drop=.5, **hold),
        K(4, at(.05, -.86, .92), tip=at(.0, .0, 2.0), edge=(1, .05, 0), reach=0, travel=0, drop=.85),
        K(5, at(.45, -.88, .90), tip=at(.85, .05, 1.75), edge=(1, 0, .4), travel=0, drop=1),
        K(6, at(1.0, -1.1, .80), tip=at(1.4, -.05, 1.1), edge=(.6, 0, 1), travel=0, drop=1),
        K(9, at(.62, -.80, .90), tip=at(.40, .45, 2.0), edge=(.2, -.3, -1), travel=0, drop=1),
        K(12, **ready)]}

    # ------------------------------------------------------------ 1: diagonal cut (right high to left low)
    out['diagonal'] = {'impact': 5, 'chain': 7, 'keys': [
        K(0, **ready),
        K(3, at(1.05, .80, .62), tip=at(1.0, 1.5, 1.2), edge=(-.35, .45, -.8)),
        K(4, at(.80, .05, .85), tip=at(.30, .85, 2.0), edge=(-.6, -.2, -.8)),
        K(5, at(.42, -.62, .92), tip=at(.0, .0, 2.0), edge=(-.72, -.70, 0)),
        K(6, at(.02, -1.05, .90), tip=at(-.80, -.55, 1.7), edge=(-.6, -.75, .3)),
        K(7, at(-.40, -1.45, .80), tip=at(-1.3, -1.05, 1.1), edge=(-.3, -.7, .7)),
        K(10, at(.22, -.85, .90), tip=at(-.05, .40, 2.1), edge=(-.3, -.2, -1)),
        K(13, **ready)]}

    # ------------------------------------------------------------ 2: rising cut (left low to right high)
    out['rising'] = {'impact': 4, 'chain': 6, 'keys': [
        K(0, **ready),
        K(2, at(-.42, -1.1, .85), tip=at(-1.05, -.90, 1.45), edge=(.5, .8, -.2)),
        K(3, at(-.12, -.90, .90), tip=at(-.62, -.45, 1.95), edge=(.65, .75, -.1)),
        K(4, at(.25, -.72, .92), tip=at(.0, .0, 2.0), edge=(.72, .70, 0)),
        K(5, at(.70, -.62, .90), tip=at(.92, .78, 1.65), edge=(.6, .75, .3)),
        K(6, at(1.20, -.45, .80), tip=at(1.45, 1.10, 1.2), edge=(.3, .7, .7)),
        K(9, at(.60, -.70, .90), tip=at(.30, .65, 2.1), edge=(-.1, -.2, -1)),
        K(12, **ready)]}

    # ------------------------------------------------------------ 3: overhead cut, fist and blade fall together
    out['overhead'] = {'impact': 7, 'chain': 9, 'keys': [
        K(0, **ready),
        K(3, at(.86, -.28, .82), blade=(-.10, .85, .55), edge=(-.05, .3, -1)),
        K(5, at(.62, -.42, .88), tip=at(.30, 1.10, 1.6), edge=(-.1, .1, -1)),
        K(6, at(.36, -.66, .92), tip=at(.12, .55, 2.2), edge=(0, -.5, -1)),
        K(7, at(.16, -.88, .92), tip=at(.03, .0, 2.3), edge=(0, -1, -.3)),
        K(8, at(.08, -1.10, .90), tip=at(.0, -.65, 2.1), edge=(0, -1, .25)),
        K(10, at(.25, -1.55, .80), tip=at(.10, -1.25, 2.0), edge=(0, -.7, .7)),
        K(13, at(.45, -1.05, .90), tip=at(.15, .30, 2.1), edge=(-.1, -.2, -1)),
        K(17, **ready)]}

    # ------------------------------------------------------------ sheathe
    end = 11
    out['sheathe'] = {'scabbard_anchor': end, 'reach_to': 'scabbard', 'keys': [
        K(0, **ready),
        K(4, h0 - .95 * b + [.05, .06, 0], blade=b, edge=(.1, 1, .2), reach=.3, along=.10, travel=0, drop=.6, **hold),
        K(6, h0 - .85 * b, blade=b, edge=(.1, 1, .2), reach=.85, along=.10, travel=0, drop=.12, **hold),
        K(7, h0 - .70 * b, blade=b, edge=(.1, 1, .2), reach=1, along=.10, travel=0, drop=0, **hold),
        K(end, **sheathed)]}

    # ------------------------------------------------------------ inspect: show the flat, wipe, turn, flick
    face = dict(hand=at(.50, -.70, .80), tip=at(-.55, -.25, 1.35), edge=(0, -1, .15))
    wipe = dict(off_from=np.array([-.35, -1.4, -.5]))
    out['inspect'] = {'reach_to': 'blade', 'keys': [
        K(0, **ready),
        K(8, **face, reach=0, along=.25, **wipe),
        K(12, **face, reach=1, along=.25, **wipe),
        K(24, **face, reach=1, along=.95, **wipe),
        K(28, **face, reach=0, along=.95, **wipe),
        K(34, at(.50, -.70, .85), tip=at(-.25, .48, 1.5), edge=(-.2, .1, 1)),
        K(42, at(.50, -.70, .85), tip=at(-.25, .48, 1.5), edge=(-.2, .1, 1)),
        K(44, at(.62, -.80, .92), tip=at(.55, .20, 2.0), edge=(.5, .6, -.6)),
        K(46, at(.66, -1.0, .95), tip=at(.95, -.62, 2.0), edge=(.6, -.7, 0)),
        K(54, **ready)]}
    return out


def main():
    data = {'version': 1, 'rig': RIG, 'combo': ['diagonal', 'rising', 'overhead'], 'clips': clips()}
    R.CLIPS.write_text(json.dumps(data, indent=1, ensure_ascii=False) + '\n', encoding='utf8')
    print('wrote', R.CLIPS)


if __name__ == '__main__':
    main()
