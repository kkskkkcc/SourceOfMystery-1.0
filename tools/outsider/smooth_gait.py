"""Authored, restrained locomotion for this rig; units are PMX model units.

1.2 s cycle, 60% stance, 40% swing. Planted feet travel linearly relative to
the body; a Hermite return matches their velocity at lift-off and contact.
"""
import numpy as np
from scipy.spatial.transform import Rotation as R

FPS = 60
PERIOD = 1.2
STANCE = .60
HALF_STRIDE = 2.15
# Runtime distance clock uses this stride length / period, in blocks/second.
STRIDE_BLOCKS = (2 * HALF_STRIDE / STANCE) * .108


def foot(phase):
    phase %= 1.
    if phase <= STANCE:
        z = -HALF_STRIDE + 2 * HALF_STRIDE * phase / STANCE
        height = 0.
    else:
        u = (phase - STANCE) / (1 - STANCE)
        # Quintic Hermite: matching velocity AND acceleration at both contacts.
        tangent = 2 * HALF_STRIDE * (1 - STANCE) / STANCE
        z = (HALF_STRIDE + tangent*u
             + (-20*HALF_STRIDE-10*tangent)*u**3
             + (30*HALF_STRIDE+15*tangent)*u**4
             + (-12*HALF_STRIDE-6*tangent)*u**5)
        height = .8 * np.sin(np.pi * u)**3
    return z, height


def apply(model, translations, rotations, seconds, walking):
    lookup = {str(b.name): i for i, b in enumerate(model.bones)}
    phase = seconds / PERIOD
    cycle = phase * 2 * np.pi
    breath = np.sin(seconds * 2 * np.pi / 4)

    def rot(name, angles):
        if name in lookup:
            rotations[lookup[name]] = R.from_euler('xyz', angles, degrees=True)

    # Neutral A-pose -> relaxed arms. Avoid the large forward reach of the old VMD.
    for side, sign in [('左', 1), ('右', -1)]:
        swing = (-np.cos(cycle - .12) * sign if walking else 0)
        rot(side+'腕', [swing * 10 - 2, 0, -sign * 41])
        rot(side+'ひじ', [-4 - max(0, -swing) * 3, 0, 0])
        rot(side+'手首', [0, 0, sign * 2])
        rot(side+'肩P', [0, 0, 0])
    rot('首', [-.7 * breath if not walking else -1, 0, 0])
    rot('頭', [.5 * breath if not walking else .4, 0, 0])
    if walking:
        # Pelvis leads, rib cage counter-rotates and arms lag the torso.
        rot('下半身', [0, 2.2*np.cos(cycle), 1.1*np.sin(cycle)])
        rot('上半身', [1.2, -1.6*np.cos(cycle-.15), -.7*np.sin(cycle-.12)])
        rot('上半身2', [.3, -.8*np.cos(cycle-.22), 0])
        translations[lookup['グルーブ']] = [.10*np.sin(cycle), -.30-.065*np.cos(2*cycle), 0]
    else:
        rot('上半身', [.35*breath, 0, .2*np.sin(seconds*2*np.pi/4)])
        rot('上半身2', [.3*breath, 0, 0])
        translations[lookup['グルーブ']] = [0, -.10+.025*breath, 0]
    targets = []
    for side, shift in [('左', 0), ('右', .5)]:
        z, y = foot(phase+shift) if walking else (0., 0.)
        ids = [lookup[side+n] for n in ['足', 'ひざ', '足首']]
        ankle_rest = np.array(list(model.bones[ids[2]].position))
        target = ankle_rest + [0, y, z]
        targets.append((ids, target))
    return targets


def between(a, b):
    a, b = a / np.linalg.norm(a), b / np.linalg.norm(b)
    axis = np.cross(a, b)
    dot = np.clip(np.dot(a, b), -1, 1)
    if np.linalg.norm(axis) < 1e-10:
        if dot > 0:
            return R.identity()
        axis = np.cross(a, [1, 0, 0])
        if np.linalg.norm(axis) < 1e-10:
            axis = np.cross(a, [0, 1, 0])
    return R.from_rotvec(axis / np.linalg.norm(axis) * np.arccos(dot))


def solve_leg(ids, target, bones, rest, rotations, world, invalidate):
    hip, knee, ankle = ids
    origin = world(hip)[1]
    delta = target - origin
    length = np.linalg.norm(delta)
    direction = delta / length
    thigh = np.linalg.norm(rest[knee] - rest[hip])
    shin = np.linalg.norm(rest[ankle] - rest[knee])
    length = np.clip(length, abs(thigh-shin)+1e-5, thigh+shin-1e-5)
    target = origin + direction * length
    along = (thigh**2 - shin**2 + length**2) / (2*length)
    pole = np.array([0., 0., -1.])
    pole -= direction * np.dot(pole, direction)
    pole /= np.linalg.norm(pole)
    knee_target = origin + direction * along + pole * np.sqrt(max(0, thigh**2 - along**2))
    parent = world(bones[hip].parent_index)[0]
    rotations[hip] = between(rest[knee]-rest[hip], parent.inv().apply(knee_target-origin))
    invalidate()
    parent = world(hip)[0]
    rotations[knee] = between(rest[ankle]-rest[knee], parent.inv().apply(target-knee_target))
    invalidate()
    # Keep the foot's original high-heel orientation while ankle follows IK.
    rotations[ankle] = world(knee)[0].inv()
    invalidate()
    return float(np.linalg.norm(world(ankle)[1] - target))
