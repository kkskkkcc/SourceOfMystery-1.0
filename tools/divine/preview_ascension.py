"""Offline perspective preview of the ascension CG keyframes (not a game capture).

Renders the stage model, the throne mesh, the floating armor and a posed vanilla-proportion
player from each camera frame. Used by author_ascension.py --preview.
"""
import json
import math
import struct
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
A = ROOT / 'src/main/resources/assets/sourceofmystery'
sys.path.insert(0, str(ROOT / 'tools/gear'))
import preview as gp  # noqa: E402

EQUIP = 24.7
BG = np.array([14, 16, 26], np.float32)


def rx(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def ry(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def rz(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def part_matrix(rot):
    # Vanilla ModelPart rotation (ZYX) seen through the renderer's scale(-1,-1,1).
    x, y, z = rot
    return rz(z) @ ry(-y) @ rx(-x)


# Pivots in a Y-up, feet-origin frame (pixels), facing -Z.
PIVOTS = {'head': (0, 24, 0), 'body': (0, 24, 0), 'rightArm': (5, 22, 0), 'leftArm': (-5, 22, 0),
          'rightLeg': (1.9, 12, 0), 'leftLeg': (-1.9, 12, 0)}
BOXES = {'head': ((-4, 0, -4), (4, 8, 4)), 'body': ((-4, -12, -2), (4, 0, 2)),
         'rightArm': ((-1, -10, -2), (3, 2, 2)), 'leftArm': ((-3, -10, -2), (1, 2, 2)),
         'rightLeg': ((-2, -12, -2), (2, 0, 2)), 'leftLeg': ((-2, -12, -2), (2, 0, 2))}
COLORS = {'head': (205, 160, 125), 'body': (70, 150, 160), 'rightArm': (200, 155, 120), 'leftArm': (200, 155, 120),
          'rightLeg': (60, 60, 140), 'leftLeg': (60, 60, 140)}
ARMOR_BONES = {'armorHead': 'head', 'armorBody': 'body', 'armorRightArm': 'rightArm', 'armorLeftArm': 'leftArm',
               'armorRightLeg': 'rightLeg', 'armorLeftLeg': 'leftLeg', 'taiji_emblem': 'body'}


def box_quads(lo, hi):
    (x0, y0, z0), (x1, y1, z1) = lo, hi
    c = np.array([[x0, y0, z0], [x1, y0, z0], [x1, y1, z0], [x0, y1, z0],
                  [x0, y0, z1], [x1, y0, z1], [x1, y1, z1], [x0, y1, z1]], float)
    faces = [(3, 2, 1, 0, .8), (4, 5, 6, 7, .8), (0, 4, 7, 3, .6), (1, 2, 6, 5, .6), (3, 7, 6, 2, 1.0), (0, 1, 5, 4, .5)]
    return [(c[[a, b, cc, d]], s) for a, b, cc, d, s in faces]


class Scene:
    def __init__(self):
        self.stage_desc, stage = gp.load(A / 'geo/cinematic/divine_stage.geo.json')
        self.stage = [(n, q / 16.0, uv) for n, q, uv in stage]
        self.stage_tex = np.asarray(Image.open(A / 'textures/cinematic/divine/stage.png').convert('RGBA'), np.float32)
        b = (A / 'geo/cinematic/divine_throne.mesh').read_bytes()
        n = struct.unpack('>III', b[:12])[2]
        v = np.frombuffer(b[12:], '>f4').reshape(n, 8)
        self.throne = v[:, :3].reshape(-1, 3, 3) + np.array([0, 1.8, -7.7])
        self.throne_n = v[:, 3:6].reshape(-1, 3, 3).mean(1)
        self.armor_desc, armor = gp.load_bones(A / 'geo/armor/divine_blessing.geo.json')
        self.armor = armor
        self.armor_tex = np.asarray(Image.open(A / 'textures/armor/divine_blessing.png').convert('RGBA'), np.float32)


def render(scene, eye, look, fov, body, t, size=(480, 270)):
    W, H = size
    img = np.zeros((H, W, 3), np.float32) + BG
    zbuf = np.full((H, W), np.inf, np.float32)
    eye, look = np.array(eye, float), np.array(look, float)
    f = look - eye
    f /= np.linalg.norm(f)
    right = np.cross(f, [0, 1, 0])
    right /= np.linalg.norm(right)
    up = np.cross(right, f)
    focal = (H / 2) / math.tan(math.radians(fov) / 2)

    def project(pts):
        d = pts - eye
        x, y, z = d @ right, d @ up, d @ f
        z = np.maximum(z, 1e-3)
        return W / 2 + x / z * focal, H / 2 - y / z * focal, z

    def draw(pts, uvs, tex, shade, color=None):
        sx, sy, z = project(pts)
        if (z <= .05).any():
            return
        for tri in ((0, 1, 2), (0, 2, 3)) if len(pts) == 4 else ((0, 1, 2),):
            t3 = list(tri)
            gp.raster(img, zbuf, sx[t3], sy[t3], z[t3], uvs[t3] if uvs is not None else None,
                      tex if color is None else None, shade) if color is None else \
                raster_color(img, zbuf, sx[t3], sy[t3], z[t3], color, shade)

    for name, q, uv in scene.stage:
        (u, v), (w, h) = uv['uv'], uv['uv_size']
        draw(q, np.array([[u, v], [u + w, v], [u + w, v + h], [u, v + h]], float), scene.stage_tex, gp.SHADE[name])
    for tri, n in zip(scene.throne, scene.throne_n):
        draw(tri, None, None, .45 + .5 * max(0, n @ np.array([.3, .8, .5])), color=(205, 205, 205))
    # Floating armor until the bestowal.
    if t < EQUIP:
        bob = .032 * math.sin(t * 1.6)
        R = ry(math.pi)
        for bone, name, q, uv in scene.armor:
            pts = (R @ (q / 16.0).T).T + np.array([0, 1.98 + bob, -4.55])
            (u, v), (w, h) = uv['uv'], uv['uv_size']
            draw(pts, np.array([[u, v], [u + w, v], [u + w, v + h], [u, v + h]], float), scene.armor_tex,
                 gp.SHADE[name])
    # Player.
    x, y, z, yaw = body[1:5]
    rots = {p: body[5 + 3 * i:8 + 3 * i] for i, p in enumerate(PIVOTS)}
    Y = ry(math.radians(yaw))
    base = np.array([x, y, z])

    def to_world(p_px):
        return (Y @ (np.asarray(p_px) / 16.0).T).T + base

    for part, (lo, hi) in BOXES.items():
        M = part_matrix(rots[part])
        piv = np.array(PIVOTS[part], float)
        for quad, shade in box_quads(lo, hi):
            pts = to_world((M @ quad.T).T + piv)
            draw(pts, None, None, shade, color=COLORS[part])
    if t >= EQUIP:
        for bone, name, q, uv in scene.armor:
            part = ARMOR_BONES.get(bone)
            if part is None:
                continue
            M = part_matrix(rots[part])
            piv = np.array(PIVOTS[part], float)
            pts = to_world((M @ (q - piv).T).T + piv)
            (u, v), (w, h) = uv['uv'], uv['uv_size']
            draw(pts, np.array([[u, v], [u + w, v], [u + w, v + h], [u, v + h]], float), scene.armor_tex,
                 gp.SHADE[name])
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))


def raster_color(img, zbuf, xs, ys, zs, color, shade):
    h, w = zbuf.shape
    x0, x1 = int(max(0, math.floor(xs.min()))), int(min(w - 1, math.ceil(xs.max())))
    y0, y1 = int(max(0, math.floor(ys.min()))), int(min(h - 1, math.ceil(ys.max())))
    if x1 < x0 or y1 < y0:
        return
    gy, gx = np.mgrid[y0:y1 + 1, x0:x1 + 1].astype(np.float32) + .5
    d = (ys[1] - ys[2]) * (xs[0] - xs[2]) + (xs[2] - xs[1]) * (ys[0] - ys[2])
    if abs(d) < 1e-9:
        return
    a = ((ys[1] - ys[2]) * (gx - xs[2]) + (xs[2] - xs[1]) * (gy - ys[2])) / d
    b = ((ys[2] - ys[0]) * (gx - xs[2]) + (xs[0] - xs[2]) * (gy - ys[2])) / d
    c = 1 - a - b
    m = (a >= -1e-4) & (b >= -1e-4) & (c >= -1e-4)
    z = a * zs[0] + b * zs[1] + c * zs[2]
    region = zbuf[y0:y1 + 1, x0:x1 + 1]
    m &= z < region
    img[y0:y1 + 1, x0:x1 + 1][m] = np.array(color, np.float32) * shade
    region[m] = z[m]


def sample(frames, t):
    ts = [f[0] for f in frames]
    i = max(0, np.searchsorted(ts, t, side='right') - 1)
    a = frames[i]
    if i + 1 >= len(frames) or frames[i + 1][0] <= a[0]:
        return a
    b = frames[i + 1]
    u = (t - a[0]) / (b[0] - a[0])
    return [x + (y - x) * u for x, y in zip(a, b)]


def contact_sheet(data, out, times=None, cols=6, size=(400, 225)):
    scene = Scene()
    times = times or [4.5 + i for i in range(31)]
    rows = math.ceil(len(times) / cols)
    sheet = Image.new('RGB', (cols * size[0], rows * size[1]), (0, 0, 0))
    for k, t in enumerate(times):
        cam = sample(data['camera'], t)
        body = sample(data['body'], t)
        im = render(scene, cam[1:4], cam[4:7], cam[7], body, t, size)
        ImageDraw.Draw(im).text((6, 4), f'{t:.1f}s', fill=(255, 255, 255))
        sheet.paste(im, ((k % cols) * size[0], (k // cols) * size[1]))
    sheet.save(out)
    print('preview', out)


if __name__ == '__main__':
    data = json.loads((A / 'cinematic/divine_ascension.json').read_text(encoding='utf8'))
    ts = [float(x) for x in sys.argv[2:]] or None
    contact_sheet(data, sys.argv[1] if len(sys.argv) > 1 else ROOT / 'tools/gear/out/ascension_sheet.png', ts)
