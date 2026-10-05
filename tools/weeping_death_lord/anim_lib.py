"""
程序化动画工具：每个动画写成 t(秒) -> 姿势 的 Python 函数，按固定帧率采样后输出 GeckoLib 线性关键帧，
并去掉线性插值能还原的多余关键帧。

姿势格式：{骨骼名: {'rot': (x, y, z), 'pos': (x, y, z), 'scale': (x, y, z) 或 标量}}，数值与 Blockbench 动画面板一致
（旋转为度，叠加在模型自带旋转上；位移为像素）。

镰刀（根骨骼 Scythe）有两种写法：
- pose['@scythe'] = {'hand': 'R'/'L', 'grip': (rx, ry, rz), 'slide': 像素, 'scale': s}
  -> 按手的正向运动学算出镰刀的世界姿势，保证每一帧都握在手里
- 直接给 pose['Scythe'] 设 rot / pos（漂浮、飞行时）
"""
import json
import math
import os

import numpy as np

from geo_render import load, bone_world, rot_matrix, mirror, render
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, '../../src/main/resources/assets/sourceofmystery')
GEO = load(os.path.join(ASSETS, 'geo/entity/weeping_death_lord.geo.json'))
BONES = {b['name']: b for b in GEO['bones']}
SCYTHE_PIVOT = BONES['Scythe']['pivot']


def hand_grip(hand):
    """握拳中心（模型坐标，手骨骼枢轴下方一点）"""
    p = BONES['RightHand' if hand == 'R' else 'LeftHand']['pivot']
    return [p[0], p[1] - 1.1, p[2]]


# ---------------------------------------------------------------- 数学工具

def lerp(a, b, u):
    if isinstance(a, (tuple, list)):
        return tuple(lerp(x, y, u) for x, y in zip(a, b))
    return a + (b - a) * u


def clamp01(u):
    return max(0.0, min(1.0, u))


def smooth(u):
    u = clamp01(u)
    return u * u * (3 - 2 * u)


def ease_in(u, p=2):
    return clamp01(u) ** p


def ease_out(u, p=2):
    return 1 - (1 - clamp01(u)) ** p


def ease_in_out(u):
    return 0.5 - 0.5 * math.cos(math.pi * clamp01(u))


def back_out(u, s=1.7):
    u = clamp01(u) - 1
    return u * u * ((s + 1) * u + s) + 1


def seg(t, t0, t1):
    """t 在 [t0, t1] 中的进度 0~1"""
    if t1 <= t0:
        return 1.0 if t >= t1 else 0.0
    return clamp01((t - t0) / (t1 - t0))


def keys(t, frames, ease=ease_in_out):
    """frames: [(时间, 值), ...]，相邻关键帧之间用 ease 过渡"""
    if t <= frames[0][0]:
        return frames[0][1]
    for (t0, v0), (t1, v1) in zip(frames, frames[1:]):
        if t <= t1:
            return lerp(v0, v1, ease(seg(t, t0, t1)))
    return frames[-1][1]


def wave(t, period, phase=0.0):
    return math.sin(2 * math.pi * (t / period + phase))


def add(pose, bone, rot=None, pos=None, scale=None):
    """在已有姿势上叠加"""
    e = pose.setdefault(bone, {})
    if rot is not None:
        r = e.get('rot', (0, 0, 0))
        e['rot'] = tuple(a + b for a, b in zip(r, rot))
    if pos is not None:
        p = e.get('pos', (0, 0, 0))
        e['pos'] = tuple(a + b for a, b in zip(p, pos))
    if scale is not None:
        if not isinstance(scale, (tuple, list)):
            scale = (scale, scale, scale)
        s = e.get('scale', (1, 1, 1))
        e['scale'] = tuple(a * b for a, b in zip(s, scale))
    return pose


def merge(*poses):
    out = {}
    for p in poses:
        for bone, e in p.items():
            if bone.startswith('@'):
                out[bone] = e
                continue
            add(out, bone, e.get('rot'), e.get('pos'), e.get('scale'))
    return out


# ---------------------------------------------------------------- 镰刀正向运动学

def _euler_from(R):
    """R = Rz(c) Ry(b) Rx(a) -> Blockbench 角度 (rx, ry, rz)"""
    b = math.asin(max(-1.0, min(1.0, -R[2][0])))
    if abs(math.cos(b)) > 1e-6:
        a = math.atan2(R[2][1], R[2][2])
        c = math.atan2(R[1][0], R[0][0])
    else:
        a = math.atan2(-R[1][2], R[1][1])
        c = 0.0
    return (-math.degrees(a), -math.degrees(b), math.degrees(c))


def _scythe_frame(pose, spec):
    """镰刀在 spin = 0 时的朝向 A（R = A @ Rz(spin)）和握点（世界坐标）"""
    hand = spec.get('hand', 'R')
    bones, world = bone_world(GEO, {k: v for k, v in pose.items() if not k.startswith('@')})
    H = world('RightHand' if hand == 'R' else 'LeftHand')
    Rh = H[:3, :3]
    Rh = Rh / np.cbrt(np.linalg.det(Rh))  # 去掉手臂链上的缩放（一般没有）
    A = Rh @ rot_matrix(*spec.get('grip', (0, 0, 0)))
    g = H @ np.append(mirror(hand_grip(hand)), 1)
    return A, g[:3]


def solve_scythe(pose, spin=None):
    """把 pose['@scythe'] 换算成 Scythe 骨骼的 rot / pos / scale。
    spin：绕握柄（镰刀局部 z 轴）转的角度，决定刀刃朝向；spec 里写 'auto' 时由 Anim 按挥动方向算好传进来"""
    spec = pose.get('@scythe')
    if not spec:
        return pose
    if spin is None:
        spin = spec.get('spin', 0.0)
        if spin == 'auto':
            spin = spec.get('spin0', -90.0)
    A, g = _scythe_frame(pose, spec)
    R = A @ rot_matrix(0, 0, spin)
    scale = spec.get('scale', 1.0)
    # 握点沿握柄向刀头方向滑动 slide 像素（刀头在镰刀局部 -z 端）
    pivot_world = g + R @ np.array([0.0, 0.0, spec.get('slide', 0.0)])
    off = pivot_world - mirror(SCYTHE_PIVOT)
    p = dict(pose)
    p['Scythe'] = {'rot': _euler_from(R), 'pos': (-off[0], off[1], off[2]),
                   'scale': (scale, scale, scale)}
    return p


# 镰刀局部坐标（GeckoLib 镜像后，相对枢轴）：刀头末端在 -z，刀刃从握柄向 -y 伸出
SCYTHE_HEAD = np.array([0.0, 0.0, -51.6])
BLADE_DIR = math.atan2(-1.0, 0.0)  # 刀刃方向在局部 xy 平面里的角度


def _head_world(pose, spec):
    """刀头末端（握柄上，和 spin 无关）的世界坐标"""
    A, g = _scythe_frame(pose, spec)
    s = spec.get('scale', 1.0)
    pivot_world = g + A @ np.array([0.0, 0.0, spec.get('slide', 0.0)])
    return pivot_world + A @ (SCYTHE_HEAD * s), A


SPIN_AHEAD = 0.3       # 秒：提前这么久就把刀刃转到即将挥动的方向（出刀前先摆好刃口）
SPIN_BEHIND = 0.5      # 秒：挥完之后刀刃朝向保持的时间（不会一砍完就拧回去）
SPIN_REST_WEIGHT = 140.0  # 像素/秒：刀头比这个慢时刀刃回到 spin0


def auto_spins(fn, times, length, loop, settle=False):
    """'spin': 'auto' 的帧：刀刃（刀尖）朝着刀头挥动的方向，看起来是用刀刃在劈砍而不是拿棍子抡"""
    h = 1 / 120

    def raw(t):
        if loop:
            t %= length
        else:
            t = min(max(t, 0.0), length)
        return fn(t)

    fine = np.arange(-0.3, length + 0.3 + 1e-9, 1 / 60)
    samples = []
    for tf in fine:
        p = raw(tf)
        spec = p.get('@scythe')
        if not spec or spec.get('spin') != 'auto':
            samples.append(None)
            continue
        pa, pb = raw(tf - h), raw(tf + h)
        sa, sb = pa.get('@scythe'), pb.get('@scythe')
        if not sa or not sb or sa.get('hand') != sb.get('hand'):
            samples.append(None)
            continue
        ha, _ = _head_world(pa, sa)
        hb, _ = _head_world(pb, sb)
        _, A = _head_world(p, spec)
        v = (hb - ha) / (2 * h)
        axis = A[:, 2]
        v_perp = v - axis * (v @ axis)
        d = A.T @ v_perp
        speed = float(np.linalg.norm(v_perp))
        samples.append((tf, math.atan2(d[1], d[0]) - BLADE_DIR, speed))
    out = []
    for t in times:
        spec = raw(t).get('@scythe')
        if not spec or spec.get('spin') != 'auto':
            out.append(None)
            continue
        s0 = math.radians(spec.get('spin0', -90.0))
        rest = SPIN_REST_WEIGHT
        if settle:
            # 收招回到待机的动画：最后一秒把刀刃转回默认朝向，和下一个动画的开头对上
            rest += 1e6 * smooth(seg(t, length - 1.2, length - 0.2)) ** 3
        cx, cy = rest * math.cos(s0), rest * math.sin(s0)
        for smp in samples:
            if smp is None:
                continue
            tf, s, w = smp
            sigma = SPIN_AHEAD if tf >= t else SPIN_BEHIND
            k = math.exp(-((tf - t) / sigma) ** 2 / 2)
            if k < 1e-3:
                continue
            cx += w * k * math.cos(s)
            cy += w * k * math.sin(s)
        out.append(math.degrees(math.atan2(cy, cx)))
    return out


# ---------------------------------------------------------------- 裙甲防穿模

LEGS = ('LeftLeg', 'RightLeg', 'LeftLowerLeg', 'RightLowerLeg')
TORSO = ('Breast',)  # 腰部和上身的铰接处本来就贴在一起，只防止掀进胸口
# 每块裙甲：(骨骼, 障碍物, 候选转角) —— 前挡往前掀（x 负，可以带一点左右偏），左右两片往外张（z）
SKIRT_STEP = 3.0
SKIRT_MAX = 120.0
SKIRT_CLEARANCE = 0.25  # 像素


def _front_candidates():
    out = []
    for dz in (0, 8, -8, 16, -16, 24, -24, 32, -32):
        d = 0.0
        while d <= SKIRT_MAX:
            out.append((-d, 0.0, float(dz)))
            d += SKIRT_STEP
    return sorted(out, key=lambda c: abs(c[0]) + 0.8 * abs(c[2]))


def _side_candidates(sign):
    out = []
    for dx in (0, -10, 10, -20, 20):
        d = 0.0
        while d <= SKIRT_MAX:
            out.append((float(dx), 0.0, sign * d))
            d += SKIRT_STEP
    return sorted(out, key=lambda c: abs(c[2]) + 0.8 * abs(c[0]))


SKIRT_PLATES = (('SkirtFront', LEGS + TORSO, _front_candidates()),
                ('SkirtLeft', LEGS, _side_candidates(-1)),
                ('SkirtRight', LEGS, _side_candidates(1)))


def _skirt_needed(pose, bone, obstacles, candidates):
    """穿模时：按转角从小到大找第一个不穿的；都不行就取穿得最浅的"""
    from collide import cube_boxes, surface_points, depth_inside
    obs = cube_boxes(GEO, pose, obstacles)
    base = pose.get(bone, {}).get('rot', (0, 0, 0))
    best = (1e9, (0.0, 0.0, 0.0))
    for c in candidates:
        p = dict(pose)
        e = dict(p.get(bone, {}))
        e['rot'] = tuple(b + d for b, d in zip(base, c))
        p[bone] = e
        pts = surface_points(cube_boxes(GEO, p, [bone]), 4)
        depth = float(np.max(depth_inside(pts, obs, -SKIRT_CLEARANCE)))
        if depth <= 0:
            return c
        if depth < best[0] - 0.05:
            best = (depth, c)
    return best[1]


def _smooth_needs(vals, loop, r=2):
    """vals: 每帧的修正量（同号）。先取邻域里幅度最大的（提前掀开），再做滑动平均"""
    n = len(vals)

    def at(arr, i):
        return arr[i % n] if loop else arr[min(max(i, 0), n - 1)]
    mx = [max((at(vals, i + k) for k in range(-r, r + 1)), key=abs) for i in range(n)]
    return [sum(at(mx, i + k) for k in range(-r, r + 1)) / (2 * r + 1) for i in range(n)]


def fix_skirts(frames, loop):
    """逐帧算出每块裙甲需要额外转开多少才不穿过腿（前挡也不能掀进身体），平滑后叠加上去"""
    for bone, obstacles, candidates in SKIRT_PLATES:
        need = [_skirt_needed(f, bone, obstacles, candidates) for f in frames]
        if all(c == (0.0, 0.0, 0.0) for c in need):
            continue
        axes = [_smooth_needs([c[i] for c in need], loop) for i in range(3)]
        for k, f in enumerate(frames):
            d = (axes[0][k], axes[1][k], axes[2][k])
            if any(abs(x) > 1e-6 for x in d):
                add(f, bone, rot=d)
    return frames


# ---------------------------------------------------------------- 采样与输出

CHANNEL_TOL = {'rot': 0.25, 'pos': 0.03, 'scale': 0.004}
REST = {'rot': (0, 0, 0), 'pos': (0, 0, 0), 'scale': (1, 1, 1)}


def _simplify(times, values, tol):
    """保留能让线性插值误差 < tol 的最少关键帧（贪心向前延伸）"""
    n = len(times)
    if n <= 2:
        return list(range(n))
    keep = [0]
    i = 0
    while i < n - 1:
        j = i + 1
        while j + 1 < n:
            ok = True
            for k in range(i + 1, j + 1):
                u = (times[k] - times[i]) / (times[j + 1] - times[i])
                est = values[i] + (values[j + 1] - values[i]) * u
                if np.max(np.abs(est - values[k])) > tol:
                    ok = False
                    break
            if not ok:
                break
            j += 1
        keep.append(j)
        i = j
    return keep


def _unwrap(vals):
    out = [np.array(vals[0], float)]
    for v in vals[1:]:
        v = np.array(v, float)
        prev = out[-1]
        v = v + 360.0 * np.round((prev - v) / 360.0)
        out.append(v)
    return out


class Anim:
    def __init__(self, name, length, fn, loop=False, fps=20, hold=False, always=(), settle=False):
        """loop: 循环；hold: 播完停在最后一帧（GeckoLib 的 hold_on_last_frame）；
        always: 即使全程等于静止姿势也要输出的骨骼（让这个动画完全接管它们）"""
        self.name, self.length, self.fn = name, length, fn
        self.loop, self.fps, self.hold, self.always = loop, fps, hold, set(always)
        self.settle = settle  # 结尾回到待机：刀刃朝向最后转回默认

    def frames(self, times, skirts=True):
        spins = auto_spins(self.fn, times, self.length, self.loop, self.settle)
        frames = [solve_scythe(self.fn(t), s) for t, s in zip(times, spins)]
        if skirts:
            fix_skirts(frames, self.loop and len(times) > 1 and abs(times[-1] - self.length) < 1e-6)
        return frames

    def sample(self, t):
        return self.frames([t], skirts=False)[0]

    def to_json(self):
        n = max(1, int(round(self.length * self.fps)))
        times = [round(i * self.length / n, 4) for i in range(n + 1)]
        frames = self.frames(times)
        self.sampled = (times, frames)
        bones = []
        for f in frames:
            for b in f:
                if not b.startswith('@') and b not in bones:
                    bones.append(b)
        out_bones = {}
        for b in bones:
            entry = {}
            for ch in ('rotation', 'position', 'scale'):
                key = {'rotation': 'rot', 'position': 'pos', 'scale': 'scale'}[ch]
                vals = []
                for f in frames:
                    v = f.get(b, {}).get(key, REST[key])
                    if not isinstance(v, (tuple, list)):
                        v = (v, v, v)
                    vals.append(np.array(v, float))
                if key == 'rot':
                    vals = _unwrap(vals)
                rest = np.array(REST[key], float)
                if b not in self.always and all(np.max(np.abs(v - rest)) < CHANNEL_TOL[key] for v in vals):
                    continue
                if all(np.max(np.abs(v - vals[0])) < CHANNEL_TOL[key] for v in vals):
                    entry[ch] = [round(float(x), 3) for x in vals[0]]
                    continue
                idx = _simplify(times, vals, CHANNEL_TOL[key])
                entry[ch] = {f'{times[i]:.4g}': [round(float(x), 3) for x in vals[i]] for i in idx}
            if entry:
                out_bones[b] = entry
        a = {'animation_length': self.length, 'bones': out_bones}
        if self.loop:
            a['loop'] = True
        elif self.hold:
            a['loop'] = 'hold_on_last_frame'
        return a


def write(anims, path):
    data = {'format_version': '1.8.0', 'animations': {a.name: a.to_json() for a in anims}}
    with open(path, 'w') as f:
        json.dump(data, f, indent='\t', ensure_ascii=False)
    return data


# ---------------------------------------------------------------- 预览

_TEX = None


def preview(anim, times, path, yaws=(0, -50), size=260, extra=None, span=120, cy=14):
    global _TEX
    if _TEX is None:
        _TEX = Image.open(os.path.join(ASSETS, 'textures/entity/weeping_death_lord.png'))
    hide = ('DarkOrb',)
    cols = []
    center = np.array([0, cy, 0])
    sampled = anim.frames(list(times)) if isinstance(anim, Anim) else [solve_scythe(anim(t)) for t in times]
    for t, p in zip(times, sampled):
        p = {k: v for k, v in p.items() if not k.startswith('@')}
        if extra:
            p = merge(extra(t), p)
        col = []
        for y in yaws:
            im, _, _ = render(GEO, _TEX, pose=p, yaw=y, size=size, hide=hide,
                              center=_view_center(center, y), span=span)
            ImageDraw.Draw(im).text((4, 4), f'{getattr(anim, "name", "")} t={t:.2f} yaw={y}', fill=(255, 255, 0, 255))
            col.append(im)
        cols.append(col)
    W = Image.new('RGBA', (size * len(cols), size * len(yaws)))
    for i, col in enumerate(cols):
        for j, im in enumerate(col):
            W.paste(im, (i * size, j * size))
    W.save(path)


def _view_center(c, yaw):
    # render() 先旋转再取中心；这里把模型中心也转到视图坐标
    ya = math.radians(yaw)
    Vy = np.array([[math.cos(ya), 0, math.sin(ya)], [0, 1, 0], [-math.sin(ya), 0, math.cos(ya)]])
    return Vy @ np.array(c, float)
