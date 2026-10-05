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


def solve_scythe(pose):
    """把 pose['@scythe'] 换算成 Scythe 骨骼的 rot / pos / scale"""
    spec = pose.get('@scythe')
    if not spec:
        return pose
    hand = spec.get('hand', 'R')
    grip = spec.get('grip', (0, 0, 0))
    slide = spec.get('slide', 0.0)
    scale = spec.get('scale', 1.0)
    bones, world = bone_world(GEO, {k: v for k, v in pose.items() if not k.startswith('@')})
    H = world('RightHand' if hand == 'R' else 'LeftHand')
    Rh = H[:3, :3]
    # 去掉手臂链上的缩放（一般没有）
    Rh = Rh / np.cbrt(np.linalg.det(Rh))
    # spin：先绕握柄（镰刀局部 z 轴）转，决定刀刃朝向
    Rg = rot_matrix(*grip) @ rot_matrix(0, 0, spec.get('spin', 0.0))
    R = Rh @ Rg
    g = H @ np.append(mirror(hand_grip(hand)), 1)
    # 握点沿握柄向刀头方向滑动 slide 像素（刀头在镰刀局部 -z 端）
    pivot_world = g[:3] + R @ np.array([0.0, 0.0, slide])
    off = pivot_world - mirror(SCYTHE_PIVOT)
    p = dict(pose)
    p['Scythe'] = {'rot': _euler_from(R), 'pos': (-off[0], off[1], off[2]),
                   'scale': (scale, scale, scale)}
    return p


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
    def __init__(self, name, length, fn, loop=False, fps=20, hold=False, always=()):
        """loop: 循环；hold: 播完停在最后一帧（GeckoLib 的 hold_on_last_frame）；
        always: 即使全程等于静止姿势也要输出的骨骼（让这个动画完全接管它们）"""
        self.name, self.length, self.fn = name, length, fn
        self.loop, self.fps, self.hold, self.always = loop, fps, hold, set(always)

    def sample(self, t):
        return solve_scythe(self.fn(t))

    def to_json(self):
        n = max(1, int(round(self.length * self.fps)))
        times = [round(i * self.length / n, 4) for i in range(n + 1)]
        frames = [self.sample(t) for t in times]
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


def preview(anim, times, path, yaws=(0, -50), size=260, extra=None):
    global _TEX
    if _TEX is None:
        _TEX = Image.open(os.path.join(ASSETS, 'textures/entity/weeping_death_lord.png'))
    hide = ('DarkOrb',)
    cols = []
    center, span = np.array([0, 14, 0]), 120
    for t in times:
        p = anim.sample(t) if isinstance(anim, Anim) else solve_scythe(anim(t))
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
