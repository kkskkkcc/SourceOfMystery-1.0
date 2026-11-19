"""Checks for the first-person Tang Dao clips.

python tools/tang_dao/report.py [clip ...]
Samples every clip at 0.1 tick for both hands and reports:
  - arm tops (either arm) inside the view, which would show a floating arm end;
  - the longest forearm stretch in view;
  - at each impact: where the blade crosses the screen centre and whether the edge leads.
"""
import math
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import rig as R  # noqa: E402

TAN = math.tan(math.radians(35))
ASPECT = 854 / 480


def screen(p):
    z = -min(p[2], -1e-3)
    return np.array([p[0] / z / TAN / ASPECT, p[1] / z / TAN])


def inside(p, margin=.05):
    return p[2] < -.05 and (abs(screen(p)) < 1 + margin).all()


def arm_top(pivot, rot, side):
    # Top corners of the arm box (2 px above the pivot, 4 px square).
    return [pivot + rot @ (np.array([x, -2, z]) / 16) for x in (-3 * side, side) for z in (-2, 2)]


def blade_point(rig, fr, along):
    origin, item = fr['blade']
    return origin + item[:, 1] * rig.scale * along


def edge_lead(rig, player, t, along=1.1, dt=.05):
    motion = blade_point(rig, player.frame(t + dt), along) - blade_point(rig, player.frame(t - dt), along)
    item = player.frame(t)['blade'][1]
    motion -= item[:, 1] * (motion @ item[:, 1])
    n = np.linalg.norm(motion)
    return float(item[:, 0] @ motion / n) if n > 1e-6 else 0.0


def centre_pass(rig, player, impact):
    """Smallest screen distance from the centre to the blade around the impact, and when."""
    best = (9, None)
    for t in np.arange(impact - 1, impact + 1.001, .05):
        fr = player.frame(t)
        for along in np.linspace(.3, rig.tip, 30):
            d = float(np.linalg.norm(screen(blade_point(rig, fr, along)) * [ASPECT, 1]))
            best = min(best, (d, round(float(t), 2)))
    return best


def main():
    data = R.load()
    rig = R.Rig(data)
    names = sys.argv[1:] or list(data['clips'])
    bad = 0
    for name in names:
        clip = data['clips'][name]
        for side in (1, -1):
            player = R.Player(data, rig, name, side)
            tops = []
            for t in np.arange(0, player.times[-1] + 1e-6, .1):
                fr = player.frame(t)
                for label, (p, r), s in (('main', fr['main'], side), ('free', fr['off'], -side)):
                    if any(inside(c) for c in arm_top(p, r, s)):
                        tops.append(f'{label}@{t:.1f}')
            if tops:
                bad += 1
                print(f'{name} side {side:+d}: arm top in view at {", ".join(tops[:8])}'
                      + (f' (+{len(tops) - 8})' if len(tops) > 8 else ''))
            if side == 1 and 'impact' in clip:
                imp = clip['impact']
                d, when = centre_pass(rig, player, imp)
                lead = edge_lead(rig, player, imp)
                flag = '' if d < .05 and lead > .8 else '  <-- check'
                print(f'{name}: impact {imp} centre miss {d:.3f} at {when}, edge lead {lead:+.2f}{flag}')
    print('ok' if not bad else f'{bad} clip sides with arm tops in view')


if __name__ == '__main__':
    main()
