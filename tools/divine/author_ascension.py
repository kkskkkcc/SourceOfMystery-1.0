"""Author the ascension CG performance: player motion, root path and camera.

python tools/divine/author_ascension.py [--preview]
Writes assets/sourceofmystery/cinematic/divine_ascension.json, sampled at RATE Hz in
story time (DivineCGTimeline.storyTime). Coordinates are stage-local metres; the player's
root is the feet position, yaw 0 faces stage -Z, 180 faces +Z. Part rotations use vanilla
ModelPart radians (xRot negative swings a limb forward, zRot positive moves the right arm out).
Camera frames with equal timestamps mark a cut.
"""
import argparse
import json
import math
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import cg_body as B  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'src/main/resources/assets/sourceofmystery/cinematic/divine_ascension.json'
RATE = 20
START, END = 4.0, 35.7
PARTS = ('head', 'body', 'rightArm', 'leftArm', 'rightLeg', 'leftLeg')
LIFTS = ('rightLeg.lift', 'leftLeg.lift')
STEP = 1.0833333
EQUIP = 24.7
SIT_LEGS = -math.pi / 2
# Contact-driven values. solve() replaces them from the stage geometry before frames are built;
# the defaults are only starting points.
TUNE = {
    'stand': -3.8,                    # root z before the floating armor
    'reach': (-1.32, -1.22),          # arm xRot at full reach, right and left
    'heart': (-.75, -.55, -.05),      # right arm rotation for the hand at the heart
    'front': -6.95,                   # root z standing just in front of the seat
    'seat': (1.975, -7.3),            # root y, z when seated
    'rest': (-.5, 0.0, .1),           # right arm rotation with the hand on the armrest
    'gaze': -.05,                     # seated head pitch
    'inspect': -.62,                  # right arm yRot raising the gauntlet before the eyes
}
CLEAR = .004                          # target contact clearance in metres


def smooth(x):
    x = min(1.0, max(0.0, x))
    return x * x * x * (x * (6 * x - 15) + 10)


def ramp(t, a, b):
    return smooth((t - a) / (b - a))


def mix(a, b, u):
    return a + (b - a) * u


class Track:
    """Sparse keys (t, value) with smoothstep easing between neighbours."""

    def __init__(self, *keys):
        self.keys = sorted(keys)

    def __call__(self, t):
        k = self.keys
        if t <= k[0][0]:
            return k[0][1]
        for (t0, v0), (t1, v1) in zip(k, k[1:]):
            if t <= t1:
                return mix(v0, v1, smooth((t - t0) / (t1 - t0)))
        return k[-1][1]


# ---------------------------------------------------------------- root path
def climb_root(t):
    q = max(0.0, min(5.0, (t - 12) / STEP))
    step = min(4, int(q))
    phase = q - step
    if q >= 5:
        z = mix(-2.25, TUNE['stand'], ramp(t, 17.416, 18.5))
        return 0.0, 1.8, z
    start = 4.2 if step == 0 else 3.35 - (step - 1) * 1.4
    landing = 3.35 - step * 1.4
    lift = math.sin(math.pi * phase) * .05
    return 0.0, (step + smooth(phase)) * .36 + lift, mix(start, landing, smooth(phase))


def root(t):
    """Feet position and yaw in degrees."""
    if t < 12:
        return (0.0, 0.0, 4.2), 0.0
    if t < 18.5:
        return climb_root(t), 0.0
    lift = .3 * ramp(t, 24.1, 24.75) * (1 - ramp(t, 25.6, 26.4))
    if t < 29.5:
        return (0.0, 1.8 + lift, TUNE['stand']), 0.0
    if t < 31.6:
        z = mix(TUNE['stand'], -6.5, (t - 29.5) / 2.1)
        bob = abs(math.sin((t - 29.5) / .7 * math.pi)) * .035
        return (0.0, 1.8 + bob, z), 0.0
    yaw = 180 * ramp(t, 31.6, 32.5)
    if t < 32.5:
        return (0.0, 1.8, -6.5), yaw
    # One step back to the seat edge, then up and back onto the seat.
    seat_y, seat_z = TUNE['seat']
    z = mix(-6.5, TUNE['front'], ramp(t, 32.5, 33.0))
    z = mix(z, seat_z, ramp(t, 33.3, 33.95))
    return (0.0, mix(1.8, seat_y, ramp(t, 33.0, 33.55)), z), 180.0


# ---------------------------------------------------------------- body performance
def zero():
    return {p: [0.0, 0.0, 0.0] for p in PARTS}


def add(pose, part, x=0.0, y=0.0, z=0.0, w=1.0):
    pose[part][0] += x * w
    pose[part][1] += y * w
    pose[part][2] += z * w


def gait(pose, phase, lead, w, lift=.95, swing=.42, lean=.1):
    """One stair or flat stride; phase 0..1, lead +1 right leg leads."""
    s = math.sin(math.pi * phase)
    front = -lift * s
    back = .35 * s
    r, l = (front, back) if lead > 0 else (back, front)
    add(pose, 'rightLeg', x=r, w=w)
    add(pose, 'leftLeg', x=l, w=w)
    add(pose, 'rightArm', x=-r * swing, w=w)
    add(pose, 'leftArm', x=-l * swing, w=w)
    add(pose, 'body', x=lean + .04 * s, y=.06 * lead * s, w=w)
    add(pose, 'head', x=-lean * .6, y=-.05 * lead * s, w=w)


def pose(t):
    p = zero()
    breath = math.sin(t * 1.55)
    add(p, 'body', x=.012 * breath)
    add(p, 'rightArm', z=.06 + .015 * breath)
    add(p, 'leftArm', z=-.06 - .015 * breath)
    # A. arrival in space: glance down at the planet, then lift the gaze.
    look = ramp(t, 4.6, 5.8) * (1 - ramp(t, 7.0, 8.4))
    add(p, 'head', x=.32 * look, y=.42 * look)
    awe = ramp(t, 8.3, 9.6) * (1 - ramp(t, 12, 12.6))
    add(p, 'head', x=-.16 * awe)
    # B. the stairway rises: weight shifts back, the right hand starts to lift.
    rise = ramp(t, 10.1, 11.2) * (1 - ramp(t, 11.7, 12.2))
    add(p, 'head', x=-.26 * rise)
    add(p, 'body', x=-.05 * rise)
    add(p, 'rightArm', x=-.28 * rise, z=.08 * rise)
    # C. climbing the five steps, then two level strides onto the landing.
    if 12 <= t < 17.42:
        q = (t - 12) / STEP
        k = min(4, int(q))
        gait(p, q - k, 1 if k % 2 == 0 else -1, ramp(t, 12, 12.25) * (1 - ramp(t, 17.2, 17.42)))
    if 17.42 <= t < 18.5:
        u = (t - 17.42) / 1.08 * 2
        k = min(1, int(u))
        gait(p, u - k, 1 if k == 0 else -1, 1 - ramp(t, 18.2, 18.5), lift=.5, swing=.38, lean=.03)
    # E. before the floating armor: the right hand lifts first towards the light, chin lifts.
    heart = ramp(t, 18.8, 20.0) * (1 - ramp(t, 20.6, 21.3))
    hx, hy, hz = TUNE['heart']
    add(p, 'rightArm', x=hx * heart, y=hy * heart, z=(hz - .06) * heart)
    add(p, 'head', x=-.08 * ramp(t, 18.5, 19.4))
    # F. both hands reach out and rest on the suit's chest; the right leads.
    right = ramp(t, 20.9, 22.1) * (1 - ramp(t, 24.0, 24.35))
    left = ramp(t, 21.4, 22.5) * (1 - ramp(t, 24.0, 24.35))
    tremble = .015 * (1 + math.sin(t * 2.1))
    add(p, 'rightArm', x=TUNE['reach'][0] * right, z=(.1 + tremble) * right)
    add(p, 'leftArm', x=TUNE['reach'][1] * left, z=(-.1 - tremble) * left)
    add(p, 'body', x=.03 * right)
    add(p, 'head', x=.12 * right)
    # G. bestowal: lifted, arms open wide, head thrown back.
    open_ = ramp(t, 24.1, 24.7) * (1 - ramp(t, 25.6, 26.5))
    add(p, 'rightArm', x=-.3 * open_, z=1.25 * open_)
    add(p, 'leftArm', x=-.3 * open_, z=-1.25 * open_)
    add(p, 'head', x=-.5 * open_)
    add(p, 'body', x=-.13 * open_)
    add(p, 'rightLeg', x=.12 * open_, z=.06 * open_)
    add(p, 'leftLeg', x=-.04 * open_, z=-.06 * open_)
    # H. inspecting the armor: right gauntlet, then chest, then a firm stance.
    hand = ramp(t, 26.2, 26.9) * (1 - ramp(t, 27.5, 28.0))
    # Raise first, then bring the gauntlet across, so the arm never passes through the chest.
    across = ramp(t, 26.55, 27.05) * (1 - ramp(t, 27.5, 28.0))
    add(p, 'rightArm', x=-1.45 * hand, y=TUNE['inspect'] * across)
    add(p, 'head', x=.28 * hand, y=-.32 * hand)
    chest = ramp(t, 27.6, 28.2) * (1 - ramp(t, 28.7, 29.3))
    add(p, 'rightArm', x=-.85 * chest, z=.35 * chest)
    add(p, 'leftArm', x=-.85 * chest, z=-.35 * chest)
    add(p, 'head', x=.5 * chest)
    stance = ramp(t, 28.8, 29.4) * (1 - ramp(t, 29.5, 29.9))
    add(p, 'rightArm', z=.18 * stance)
    add(p, 'leftArm', z=-.18 * stance)
    add(p, 'rightLeg', z=.07 * stance)
    add(p, 'leftLeg', z=-.07 * stance)
    add(p, 'head', x=-.1 * stance)
    # I. three strides to the throne, an unhurried turn.
    if 29.5 <= t < 31.6:
        u = (t - 29.5) / .7
        k = int(u)
        gait(p, u - k, 1 if k % 2 == 0 else -1, ramp(t, 29.5, 29.75) * (1 - ramp(t, 31.4, 31.6)),
             lift=.55, swing=.5, lean=.04)
    if 31.6 <= t < 32.5:
        u = (t - 31.6) / .45
        k = min(1, int(u))
        gait(p, u - k, 1 if k == 0 else -1, 1, lift=.25, swing=.15, lean=0)
        add(p, 'head', y=.35 * math.sin(math.pi * (t - 31.6) / .9))
    if 32.5 <= t < 33.0:
        # A short step back towards the seat edge.
        s = math.sin(math.pi * (t - 32.5) / .5)
        add(p, 'rightLeg', x=.16 * s)
        add(p, 'leftLeg', x=-.04 * s)
        add(p, 'body', x=-.02 * s)
    # J. sitting: thighs level on the seat, hands on the armrests, a level regal gaze.
    sit = ramp(t, 33.05, 33.6)
    add(p, 'rightLeg', x=SIT_LEGS * sit, y=.1 * sit)
    add(p, 'leftLeg', x=SIT_LEGS * sit, y=-.1 * sit)
    rest = ramp(t, 32.85, 33.45)
    rx, ry, rz = TUNE['rest']
    add(p, 'rightArm', x=rx * rest, y=ry * rest, z=(rz - .06) * rest)
    add(p, 'leftArm', x=rx * rest, y=-ry * rest, z=(-rz + .06) * rest)
    add(p, 'head', x=TUNE['gaze'] * ramp(t, 34.0, 34.8))
    return p


# ---------------------------------------------------------------- contacts
def boxes(t, lift=(0.0, 0.0), override=None):
    (x, y, z), yaw = root(t)
    p = pose(t)
    if override:
        for part, value in override.items():
            p[part] = list(value)
    return B.pose_boxes((x, y, z), yaw, p, lift, armor=t >= EQUIP)


def bisect(fn, lo, hi, target, steps=40):
    """Solve fn(v) = target for a monotonic fn on [lo, hi]."""
    flo = fn(lo) - target
    for _ in range(steps):
        mid = (lo + hi) / 2
        fm = fn(mid) - target
        if (fm > 0) == (flo > 0):
            lo, flo = mid, fm
        else:
            hi = mid
    return (lo + hi) / 2


def solve_reach():
    """Stand where the leading right hand meets the floating suit's chest, then bring the left to it."""
    def gap(arm):
        return min(c[2] for c in boxes(23.0)[arm].corners()) - B.ARMOR_FRONT
    TUNE['stand'] = bisect(lambda z: (TUNE.__setitem__('stand', z), gap('rightArm'))[1], -4.2, -3.4, CLEAR)
    right = TUNE['reach'][0]
    TUNE['reach'] = (right, bisect(lambda a: (TUNE.__setitem__('reach', (right, a)), gap('leftArm'))[1],
                                   -.8, -1.5, CLEAR))


def solve_heart():
    """Right hand as close to the heart as a rigid arm allows without entering the body."""
    TUNE['heart'] = (0.0, 0.0, .06)
    base = pose(20.0)['rightArm']
    best = None
    for x in np.arange(-1.7, -.2, .05):
        for y in np.arange(-1.3, .31, .05):
            for z in np.arange(-.5, .31, .05):
                arm = (base[0] + x, base[1] + y, base[2] + z)
                b = boxes(20.0, override={'rightArm': arm})
                hand = B.limb(b['rightArm'])
                if B.penetration(hand, b['body']) > .001 or B.penetration(hand, b['head']) > .001:
                    continue
                target = b['body'].center + b['body'].axes @ (np.array([1.5, -2.0, -2.0]) * B.PX)
                d = np.linalg.norm(b['rightArm'].end_face() - target)
                if best is None or d < best[0]:
                    best = (d, x, y, z)
    TUNE['heart'] = (round(best[1], 3), round(best[2], 3), round(best[3] + .06, 3))
    return best[0]


def solve_seat():
    """Seat root against the throne mesh: back on the backrest, thighs on the cushion."""
    def parts(t, names):
        b = boxes(t)
        return [p for n in names for k in (n, n + '.armor') if k in b for p in B.box_samples(b[k])]
    for _ in range(3):
        y, z = TUNE['seat']
        TUNE['seat'] = (y, bisect(lambda v: (TUNE.__setitem__('seat', (y, v)),
                                             B.clearance(parts(35.0, ('body', 'head')), (0, 0, -1), .4))[1],
                                  -7.2, -8.1, CLEAR))
        y, z = TUNE['seat']
        TUNE['seat'] = (bisect(lambda v: (TUNE.__setitem__('seat', (v, z)),
                                          B.clearance(parts(35.0, ('rightLeg', 'leftLeg')), (0, -1, 0)))[1],
                               1.8, 2.3, CLEAR), z)
    # Standing at the seat edge: the back of the legs just clear of the cushion front.
    def edge(v):
        TUNE['front'] = v
        return min(min(B.clearance(pts, (0, 0, -1), .4), B.clearance(pts, (0, -1, 0)))
                   for pts in (parts(t, ('rightLeg', 'leftLeg', 'body')) for t in (32.7, 32.8, 32.9, 32.99)))
    TUNE['front'] = bisect(edge, -6.5, -7.3, .02)


def solve_rest():
    """Right arm (left mirrors it) with the hand lying on the armrest."""
    best = None
    for x in np.arange(-1.6, -.1, .05):
        for z in np.arange(0, 1.21, .05):
            for y in (-.2, 0.0, .2):
                TUNE['rest'] = (x, y, z)
                b = boxes(35.0)
                arm = B.outer(b, 'rightArm')
                hand = [arm.end_face()]
                under = B.clearance(hand, (0, -1, 0))
                if not (0 <= under < .15):
                    continue
                low = B.clearance(B.box_samples(B.sub(arm, 1, 0, 1), 3), (0, -1, 0))
                back = B.clearance(B.box_samples(B.limb(arm), 3), (0, 0, -1), .4)
                if low < 0 or back < 0 or any(B.penetration(B.limb(b['rightArm']), b[k]) > .002 for k in ('body', 'rightLeg')):
                    continue
                score = abs(low - CLEAR) + .02 * abs(x)
                if best is None or score < best[0]:
                    best = (score, x, y, z)
    _, x, y, z = best

    def low(v):
        TUNE['rest'] = (v, y, z)
        return B.clearance(B.box_samples(B.sub(B.outer(boxes(35.0), 'rightArm'), 1, 0, 1), 3), (0, -1, 0))
    TUNE['rest'] = (bisect(low, x + .05, x - .05, CLEAR), y, z)


def solve_inspect():
    """Bring the gauntlet as far across as the arm allows without entering the chest or head."""
    def depth(v):
        TUNE['inspect'] = v
        b = boxes(27.05)
        return max(B.penetration(B.limb(b['rightArm']), b[k]) for k in ('body', 'head'))
    limit = depth(0.0) + .002
    TUNE['inspect'] = bisect(depth, 0.0, -.62, limit) if depth(-.62) > limit else -.62


def solve():
    solve_reach()
    solve_inspect()
    solve_heart()
    solve_seat()
    solve_rest()
    print('tuned', {k: tuple(round(float(v), 4) for v in val) if isinstance(val, tuple) else round(float(val), 4)
                    for k, val in TUNE.items()})


def stance(t):
    """(stance leg, swing progress 0..1) during a stride, or None when both feet are planted."""
    def lead(k):
        return 'leftLeg' if k % 2 == 0 else 'rightLeg'
    if 12 <= t < 17.42:
        q = (t - 12) / STEP
        return lead(min(4, int(q))), q - min(4, int(q))
    if 17.42 <= t < 18.5:
        u = min(1.999, (t - 17.42) / 1.08 * 2)
        return lead(int(u)), u - int(u)
    if 29.5 <= t < 31.6:
        u = (t - 29.5) / .7
        return lead(int(u)), u - int(u)
    if 31.6 <= t < 32.5:
        u = min(1.999, (t - 31.6) / .45)
        return lead(int(u)), u - int(u)
    if 32.5 <= t < 33.0:
        return 'leftLeg', (t - 32.5) / .5
    return None


def ground(times):
    """Root drop and leg lifts (px) that plant the stance foot and keep the swing foot clear."""
    other = {'rightLeg': 'leftLeg', 'leftLeg': 'rightLeg'}
    drop, lifts = np.zeros(len(times)), np.zeros((len(times), 2))
    for i, t in enumerate(times):
        s = stance(t)
        if s is None:
            continue
        leg, phase = s
        b = boxes(t)
        gap = {k: B.ground_gap(b[k]) for k in ('rightLeg', 'leftLeg')}
        # Weight stays on the stance foot for the first half, then moves to the landing foot.
        w = smooth((phase - .5) / .5)
        drop[i] = -mix(gap[leg], gap[other[leg]], w)
        for j, k in enumerate(('rightLeg', 'leftLeg')):
            lifts[i, j] = max(0.0, -(gap[k] + drop[i]) + .01) * 16
    # Lift a little before a riser is reached and release it smoothly.
    win = int(.1 * RATE)
    held = np.array([lifts[max(0, i - win):i + win + 1].max(0) for i in range(len(times))])
    kernel = np.ones(2 * win + 1) / (2 * win + 1)
    smooth_lift = np.stack([np.convolve(np.pad(held[:, j], win, mode='edge'), kernel, 'valid') for j in range(2)], 1)
    return drop, smooth_lift


# ---------------------------------------------------------------- camera
def orbit_shot(t):
    """Keeps the previous 4-8 s orbit so the ascent handoff stays continuous."""
    (px, py, pz), _ = root(t)
    u = ramp(t, 4, 8)
    a, r = mix(-.95, 1.22, u), mix(6.4, 4.5, u)
    return (px + math.sin(a) * r, py + mix(2.9, 2.05, u), pz + math.cos(a) * r), (px, py + 1.0, pz), 48


def rel(t, eye, look):
    (px, py, pz), _ = root(t)
    return (px + eye[0], py + eye[1], pz + eye[2]), (px + look[0], py + look[1], pz + look[2])


def lerp3(a, b, u):
    return tuple(mix(x, y, u) for x, y in zip(a, b))


SHOTS = []


def shot(t0, t1):
    def deco(fn):
        SHOTS.append((t0, t1, fn))
        return fn
    return deco


@shot(4.0, 8.0)
def s_orbit(t, u):
    return orbit_shot(t)


@shot(8.0, 10.0)
def s_face(t, u):
    e, l = rel(t, lerp3((1.35, 1.66, -2.75), (1.0, 1.62, -2.25), u), (0, 1.5, 0))
    return e, l, mix(36, 32, u)


@shot(10.0, 12.0)
def s_reveal(t, u):
    return lerp3((2.2, .7, 7.4), (1.4, 1.6, 6.6), u), lerp3((0, 2.2, -3), (0, 2.6, -5), u), 50


@shot(12.0, 15.2)
def s_track(t, u):
    # Follow the climber with a slight lag so the frame breathes.
    lag = max(12.0, t - .25)
    e, _ = rel(lag, (3.25, 1.8, 1.15), (0, 0, 0))
    _, l = rel(t, (0, 0, 0), (0, 1.15, -.6))
    return e, l, 42


@shot(15.2, 18.6)
def s_hero(t, u):
    _, l = rel(t, (0, 0, 0), (0, 1.35, 0))
    return lerp3((2.55, 2.45, -4.4), (2.35, 2.6, -4.0), u), l, mix(38, 34, u)


@shot(18.6, 21.0)
def s_shoulder(t, u):
    e, _ = rel(t, lerp3((-1.45, 2.25, 2.5), (-1.25, 2.2, 2.1), u), (0, 0, 0))
    return e, (0, 2.9, -4.55), mix(38, 34, u)


@shot(21.0, 24.1)
def s_hands(t, u):
    return lerp3((2.4, 3.35, -4.95), (2.15, 3.25, -4.7), u), (0, 3.05, -4.2), 40


@shot(24.1, 25.6)
def s_bestow(t, u):
    a = mix(.82, -.86, u)
    e, l = rel(t, (math.sin(a) * 2.6, mix(.35, .6, u), -math.cos(a) * 2.7), (0, 1.6, 0))
    return e, l, mix(50, 44, u)


@shot(25.6, 27.6)
def s_inspect(t, u):
    e, l = rel(t, lerp3((1.3, 1.55, -2.3), (.95, 1.6, -1.9), u), (0, 1.25, 0))
    return e, l, mix(40, 36, u)


@shot(27.6, 29.5)
def s_above(t, u):
    e, l = rel(t, lerp3((-1.35, 3.1, 1.85), (-1.15, 2.95, 1.55), u), (.05, 1.2, -.3))
    return e, l, 44


@shot(29.5, 32.4)
def s_follow(t, u):
    lag = max(29.5, t - .35)
    e, _ = rel(lag, (.75, 1.1, 3.0), (0, 0, 0))
    return e, (0, 3.0, -7.6), 46


@shot(32.4, 35.7)
def s_enthroned(t, u):
    u = ramp(t, 32.4, 35.35)
    return lerp3((0, 2.9, -1.7), (0, 3.4, -.6), u), lerp3((0, 3.0, -7.3), (0, 3.15, -7.3), u), mix(38, 33, u)


def camera_frames():
    frames = []
    for t0, t1, fn in SHOTS:
        n = max(2, int(round((t1 - t0) * RATE)) + 1)
        for i in range(n):
            t = t0 + (t1 - t0) * i / (n - 1)
            e, l, f = fn(t, (t - t0) / (t1 - t0))
            frames.append([round(t, 4), *[round(v, 4) for v in e], *[round(v, 4) for v in l], round(f, 3)])
    return frames


def body_frames():
    n = int(round((END - START) * RATE)) + 1
    times = [START + i / RATE for i in range(n)]
    drop, lifts = ground(times)
    out = []
    for i, t in enumerate(times):
        (x, y, z), yaw = root(t)
        p = pose(t)
        out.append([round(t, 4), round(x, 4), round(y + drop[i], 4), round(z, 4), round(yaw, 3),
                    *[round(v, 4) for part in PARTS for v in p[part]], *[round(v, 3) for v in lifts[i]]])
    return out


def cues():
    """Sound cues in story time: [t, sound, volume]. The ascent cue starts with the CG itself."""
    out = [[4.0, 'void', .55], [8.2, 'call', .7], [10.0, 'stairs', .75], [12.0, 'approach', .6]]
    out += [[round(12 + i * STEP, 4), 'step', .7] for i in range(5)]
    out += [[17.42, 'step', .62], [17.96, 'step', .55]]
    out += [[18.8, 'heart', .8], [22.05, 'touch', .75], [24.1, 'bestowal', 1.0], [25.0, 'armor', .7],
            [25.5, 'divinity', .6]]
    out += [[round(29.5 + .7 * k, 4), 'step', .55] for k in range(3)]
    out += [[31.6, 'step', .45], [32.05, 'step', .45], [32.5, 'throne', .8], [32.75, 'step', .35]]
    return sorted(out, key=lambda c: c[0])


def build():
    return {'version': 1, 'rate': RATE, 'start': START, 'end': END, 'parts': list(PARTS), 'cues': cues(),
            'body_columns': ['t', 'x', 'y', 'z', 'yaw'] + [f'{p}.{a}' for p in PARTS for a in 'xyz'] + list(LIFTS),
            'camera_columns': ['t', 'eye.x', 'eye.y', 'eye.z', 'look.x', 'look.y', 'look.z', 'fov'],
            'body': body_frames(), 'camera': camera_frames()}


if __name__ == '__main__':
    ap = argparse.ArgumentParser()
    ap.add_argument('--preview', action='store_true')
    args = ap.parse_args()
    solve()
    data = build()
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(data, separators=(',', ':')) + '\n', encoding='utf8')
    print(OUT.relative_to(ROOT), len(data['body']), 'body frames', len(data['camera']), 'camera frames')
    if args.preview:
        from preview_ascension import contact_sheet
        contact_sheet(data, ROOT / 'tools/gear/out/ascension_sheet.png')
