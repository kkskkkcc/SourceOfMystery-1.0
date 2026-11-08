"""Contact report for the authored ascension CG track.

python tools/divine/cg_contacts.py
Reads cinematic/divine_ascension.json, rebuilds the body with cg_body (the same kinematics
as DivineCGPlayer) and prints the worst values per beat. Gaps are metres; negative values
are penetration.
"""
import json
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import cg_body as B  # noqa: E402

TRACK = B.ASSETS / 'cinematic/divine_ascension.json'
EQUIP = 24.7
SELF = (('rightArm', 'body'), ('leftArm', 'body'), ('rightArm', 'head'), ('leftArm', 'head'),
        ('rightArm', 'rightLeg'), ('leftArm', 'leftLeg'), ('rightArm', 'leftArm'))


def frames():
    data = json.loads(TRACK.read_text(encoding='utf8'))
    cols = data['body_columns']
    for row in data['body']:
        r = dict(zip(cols, row))
        pose = {p: [r[f'{p}.{a}'] for a in 'xyz'] for p in data['parts']}
        lift = (r.get('rightLeg.lift', 0.0), r.get('leftLeg.lift', 0.0))
        yield r['t'], B.pose_boxes((r['x'], r['y'], r['z']), r['yaw'], pose, lift, armor=r['t'] >= EQUIP)


def worst(rows, label):
    if rows:
        t, v = min(rows, key=lambda x: x[1])
        print(f'  {label:<34} worst {v:+.3f} at {t:.2f}')


def main():
    selfhit, feet_planted, feet_clip, reach, seat_leg, seat_back, rest = [], [], [], [], [], [], []
    for t, b in frames():
        for a, c in SELF:
            depth = B.penetration(B.limb(b[a]) if a.endswith('Arm') else b[a], b[c])
            if depth > .002:
                selfhit.append((t, -depth, a, c))
        walking = 12 <= t < 18.5 or 29.5 <= t < 33.0
        if walking or 18.5 <= t < 24.1:
            g = sorted(B.ground_gap(b[k]) for k in ('rightLeg', 'leftLeg'))
            feet_clip.append((t, g[0]))
            feet_planted.append((t, -g[0]))
        if 22.5 <= t < 24.0:
            reach.append((t, min(c[2] for k in ('rightArm', 'leftArm') for c in b[k].corners()) - B.ARMOR_FRONT))
        if t >= 34.0:
            legs = [p for k in ('rightLeg', 'leftLeg', 'rightLeg.armor', 'leftLeg.armor') for p in B.box_samples(b[k], 3)]
            seat_leg.append((t, B.clearance(legs, (0, -1, 0))))
            back = [p for k in ('body', 'body.armor', 'head') for p in B.box_samples(b[k], 3)]
            seat_back.append((t, B.clearance(back, (0, 0, -1), .4)))
            # Forearm half only: the shoulder joint sits behind the torso against the backrest.
            arm = B.sub(B.outer(b, 'rightArm'), 1, 0, 1)
            rest.append((t, B.clearance(B.box_samples(arm, 3), (0, -1, 0))))
        if 32.5 <= t < 34.0:
            body = [p for k in b for p in B.box_samples(b[k], 3)]
            seat_leg.append((t, B.clearance(body, (0, -1, 0))))
    print('self intersection (arm vs body/head/leg), depth > 2 mm')
    if selfhit:
        for t, v, a, c in selfhit[::max(1, len(selfhit) // 12)]:
            print(f'  {t:6.2f} {a}-{c} {v:+.3f}')
    else:
        print('  none')
    print('feet')
    worst(feet_clip, 'lowest foot below tread')
    worst(feet_planted, 'planted foot hover (negated)')
    print('floating armor')
    worst(reach, 'hands to suit front')
    print('throne')
    worst(seat_leg, 'body above seat / props')
    worst(seat_back, 'back and head to backrest')
    worst(rest, 'right arm above armrest')
    if rest:
        print(f'  armrest contact at end {rest[-1][1]:+.3f}')


if __name__ == '__main__':
    main()
