"""Forward kinematics and stage contacts for the ascension CG body.

Mirrors DivineCGPlayer: vanilla PlayerModel boxes (model pixels, y down, front -Z), the torso
rotating about the hip with head and arms attached at their pivots, optional leg lift (the
leg pivot slides up into the hip), and the divine armor's outer bounds after it is worn.
World space is the CG stage in metres; the root is the feet position, yaw 0 faces -Z.
"""
import math
import struct
from functools import lru_cache
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'src/main/resources/assets/sourceofmystery'
PX = 1 / 16
HIP = np.array([0.0, 12.0, 0.0])
ATTACHED = ('head', 'rightArm', 'leftArm')
# name: (pivot, skin box lo, hi, armor box lo, hi) in model pixels relative to the pivot.
PARTS = {
    'head': ((0, 0, 0), (-4, -8, -4), (4, 0, 4), None, None),
    'body': ((0, 0, 0), (-4, 0, -2), (4, 12, 2), (-4.62, -.7, -3.2), (4.62, 13.6, 2.75)),
    'rightArm': ((-5, 2, 0), (-3, -2, -2), (1, 10, 2), (-3.75, -2.62, -2.6), (1.35, 9.6, 2.6)),
    'leftArm': ((5, 2, 0), (-1, -2, -2), (3, 10, 2), (-1.35, -2.62, -2.6), (3.75, 9.6, 2.6)),
    'rightLeg': ((-1.9, 12, 0), (-2, 0, -2), (2, 12, 2), (-2.57, -.05, -2.67), (1.7, 4.7, 2.5)),
    'leftLeg': ((1.9, 12, 0), (-2, 0, -2), (2, 12, 2), (-1.7, -.05, -2.67), (2.57, 4.7, 2.5)),
}
# Floating armor (DivineCGScene.floatingArmor): suit centre z and its front-most plate.
ARMOR_Z = -4.55
ARMOR_FRONT = ARMOR_Z + 3.2 * PX
THRONE_AT = np.array([0.0, 1.8, -7.7])


def rot(x, y, z):
    """ModelPart rotation: Rz * Ry * Rx (JOML rotationZYX)."""
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return rz @ ry @ rx


def yaw_matrix(deg):
    a = math.radians(deg)
    return np.array([[math.cos(a), 0, math.sin(a)], [0, 1, 0], [-math.sin(a), 0, math.cos(a)]]) @ np.diag([-1, -1, 1])


class Box:
    """Oriented box in world metres."""

    def __init__(self, center, axes, half):
        self.center, self.axes, self.half = center, axes, half

    def corners(self):
        s = np.array([[a, b, c] for a in (-1, 1) for b in (-1, 1) for c in (-1, 1)], float)
        return self.center + (s * self.half) @ self.axes.T

    def end_face(self, sign=1):
        """Centre of the face along local +y (sign 1) or -y; for limbs +y is the far end."""
        return self.center + self.axes[:, 1] * self.half[1] * sign


def penetration(a, b):
    """Separating-axis test: overlap depth in metres (<= 0 means apart)."""
    axes = [a.axes[:, i] for i in range(3)] + [b.axes[:, i] for i in range(3)]
    axes += [np.cross(a.axes[:, i], b.axes[:, j]) for i in range(3) for j in range(3)]
    d = b.center - a.center
    depth = math.inf
    for n in axes:
        ln = np.linalg.norm(n)
        if ln < 1e-9:
            continue
        n = n / ln
        ra = np.sum(np.abs(a.axes.T @ n) * a.half)
        rb = np.sum(np.abs(b.axes.T @ n) * b.half)
        depth = min(depth, ra + rb - abs(d @ n))
        if depth <= 0:
            return depth
    return depth


def pose_boxes(root, yaw, pose, lift=(0.0, 0.0), armor=False):
    """World boxes per part: {'name': Box} plus {'name.armor': Box} when worn."""
    w = yaw_matrix(yaw)
    rb = rot(*pose['body'])
    root = np.asarray(root, float)
    out = {}
    for name, (pivot, lo, hi, alo, ahi) in PARTS.items():
        pivot = np.array(pivot, float)
        if name == 'rightLeg':
            pivot[1] -= lift[0]
        if name == 'leftLeg':
            pivot[1] -= lift[1]
        if name == 'body' or name in ATTACHED:
            pivot = HIP + rb @ (pivot - HIP)
        r = rot(*pose[name])
        for key, (l, h) in ((name, (lo, hi)), (name + '.armor', (alo, ahi))):
            if l is None or (key != name and not armor):
                continue
            l, h = np.array(l, float), np.array(h, float)
            center = pivot + r @ ((l + h) / 2)
            out[key] = Box((center * PX - [0, 1.501, 0]) @ w.T + root, w @ r, (h - l) / 2 * PX)
    return out


def outer(boxes, part):
    """The worn shell when present, otherwise the skin."""
    return boxes.get(part + '.armor', boxes[part])


# ---------------------------------------------------------------- stage
def tread(z):
    """Walking surface height under stage z (generate_cg_stage.py)."""
    for i in range(5):
        back = 2.65 - 1.4 * i
        if back <= z <= back + 1.4:
            return (i + 1) * .36
    return 1.8 if -10.8 <= z < -2.95 else 0.0


def ground_gap(box):
    """Lowest clearance of a box above the treads under each of its bottom corners and edges."""
    pts = box.corners()
    # Also sample along the box so a riser between two corners is not missed.
    s = np.linspace(-1, 1, 5)
    grid = np.array([[a, b, c] for a in (-1, 1) for b in s for c in s], float)
    pts = np.vstack([pts, box.center + (grid * box.half) @ box.axes.T])
    return min(p[1] - tread(p[2]) for p in pts)


@lru_cache(maxsize=1)
def throne_mesh():
    data = (ASSETS / 'geo/cinematic/divine_throne.mesh').read_bytes()
    n = struct.unpack('>III', data[:12])[2]
    v = np.frombuffer(data[12:], dtype='>f4').reshape(n, 8).astype(float)
    return v[:, :3].reshape(-1, 3, 3) + THRONE_AT


def ray(origin, direction):
    """Distance along a unit ray to the throne mesh (Moller-Trumbore), inf if no hit."""
    tri = throne_mesh()
    e1, e2 = tri[:, 1] - tri[:, 0], tri[:, 2] - tri[:, 0]
    p = np.cross(direction, e2)
    det = np.einsum('ij,ij->i', e1, p)
    ok = np.abs(det) > 1e-12
    inv = np.where(ok, 1 / np.where(ok, det, 1), 0)
    s = origin - tri[:, 0]
    u = np.einsum('ij,ij->i', s, p) * inv
    q = np.cross(s, e1)
    v = (q @ direction) * inv
    t = np.einsum('ij,ij->i', e2, q) * inv
    hit = ok & (u >= 0) & (v >= 0) & (u + v <= 1) & (t > -1e-6)
    return float(t[hit].min()) if hit.any() else math.inf


def face_samples(box, axis, sign, n=4):
    """Points on one face of a box: local axis index and side."""
    s = np.linspace(-1, 1, n)
    out = []
    for a in s:
        for b in s:
            local = np.zeros(3)
            others = [i for i in range(3) if i != axis]
            local[axis] = sign
            local[others[0]], local[others[1]] = a, b
            out.append(box.center + box.axes @ (local * box.half))
    return out


def box_samples(box, n=4):
    """Points over all six faces of a box."""
    return [p for axis in range(3) for sign in (-1, 1) for p in face_samples(box, axis, sign, n)]


def clearance(points, direction, reach=.6):
    """Smallest free distance from points to the throne along a world direction.

    Each ray starts `reach` metres behind its point, so a surface between that start and the
    point (the point is under or inside the prop) gives a negative clearance.
    """
    direction = np.asarray(direction, float) / np.linalg.norm(direction)
    return min(ray(np.asarray(p) - direction * reach, direction) - reach for p in points)


def sub(box, axis, a, b):
    """Part of a box between local fractions a..b (-1..1) along one of its axes."""
    half = box.half.copy()
    half[axis] *= (b - a) / 2
    return Box(box.center + box.axes[:, axis] * box.half[axis] * (a + b) / 2, box.axes, half)


def limb(box):
    """An arm below its shoulder joint (local y from 1 px to the hand), for self-contact tests.

    Vanilla arms pivot 2 px under the shoulder top, so the top of a raised arm always dips
    into the torso; that joint region is not counted as a collision.
    """
    return sub(box, 1, -.5, 1)
