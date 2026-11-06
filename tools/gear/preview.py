"""Offline textured preview of a GeckoLib geo + atlas (rest pose, optional bone poses).

python tools/gear/preview.py <geo.json> <texture.png> <out.png> [--mannequin] [--views front,back,...]
Software rasterizer with Minecraft-like directional face shading. Not a game screenshot.
"""
import argparse
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image

MIRROR = np.array([-1.0, 1.0, 1.0])
SHADE = {'up': 1.0, 'down': .5, 'north': .8, 'south': .8, 'east': .6, 'west': .6}
# (yaw, pitch): yaw 0 = camera in front of the wearer, positive yaw orbits toward the wearer's left.
VIEWS = {'front': (0, 10), 'back': (180, 10), 'left': (90, 6), 'right': (-90, 6),
         'three': (-35, 16), 'three_l': (35, 16), 'top': (0, 60), 'back3': (150, 16), 'low': (-25, -12)}


def rot_matrix(rx, ry, rz):
    # GeckoLib: x and y negated from file values, applied Z then Y then X.
    x, y, z = math.radians(-rx), math.radians(-ry), math.radians(rz)
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    X = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    Y = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Z = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Z @ Y @ X


def cube_quads(cube):
    o, s = np.array(cube['origin'], float), np.array(cube['size'], float)
    x0, y0, z0 = o
    x1, y1, z1 = o + s
    # Corners ordered: top-left, top-right, bottom-right, bottom-left as seen from outside.
    faces = {
        'north': [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
        'south': [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
        'east': [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
        'west': [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
        'up': [(x1, y1, z1), (x0, y1, z1), (x0, y1, z0), (x1, y1, z0)],
        'down': [(x1, y0, z0), (x0, y0, z0), (x0, y0, z1), (x1, y0, z1)],
    }
    out = []
    for name, pts in faces.items():
        if name not in cube.get('uv', {}):
            continue
        out.append((name, np.array(pts, float), cube['uv'][name]))
    return out


def load(geo_path, poses=None):
    geo = json.loads(Path(geo_path).read_text(encoding='utf8'))['minecraft:geometry'][0]
    bones = {b['name']: b for b in geo['bones']}
    poses = poses or {}

    def world(name):
        b = bones[name]
        piv = np.array(b.get('pivot', [0, 0, 0]), float) * MIRROR
        r = np.array(b.get('rotation', [0, 0, 0]), float) + np.array(poses.get(name, [0, 0, 0]), float)
        R = rot_matrix(*r)
        local = lambda p: (R @ (p - piv).T).T + piv
        if b.get('parent'):
            parent = world(b['parent'])
            return lambda p: parent(local(p))
        return local

    quads = []
    for b in geo['bones']:
        f = world(b['name'])
        for c in b.get('cubes', []):
            R = rot_matrix(*c.get('rotation', [0, 0, 0]))
            piv = np.array(c.get('pivot', [0, 0, 0]), float) * MIRROR
            for name, pts, uv in cube_quads(c):
                pts = pts * MIRROR
                pts = (R @ (pts - piv).T).T + piv
                quads.append((name, f(pts), uv))
    return geo['description'], quads


def mannequin():
    boxes = [((-4, 24, -4), (8, 8, 8)), ((-4, 12, -2), (8, 12, 4)), ((-8, 12, -2), (4, 12, 4)),
             ((4, 12, -2), (4, 12, 4)), ((-4, 0, -2), (4, 12, 4)), ((0, 0, -2), (4, 12, 4))]
    quads = []
    for o, s in boxes:
        c = {'origin': o, 'size': s, 'uv': {k: None for k in SHADE}}
        for name, pts, _ in cube_quads(c):
            quads.append((name, pts * MIRROR, None))
    return quads


def render(desc, quads, tex, yaw, pitch, size=900, center=None, extent=None):
    tw, th = desc['texture_width'], desc['texture_height']
    tex = np.asarray(tex.convert('RGBA'), np.float32)
    allp = np.concatenate([q[1] for q in quads])
    if center is None:
        center = (allp.max(0) + allp.min(0)) / 2
    ya, pa = math.radians(yaw), math.radians(pitch)
    Ry = np.array([[math.cos(ya), 0, -math.sin(ya)], [0, 1, 0], [math.sin(ya), 0, math.cos(ya)]])
    Rx = np.array([[1, 0, 0], [0, math.cos(pa), math.sin(pa)], [0, -math.sin(pa), math.cos(pa)]])
    M = Rx @ Ry
    flip = np.array([-1.0, 1.0, 1.0])  # camera at -Z looking +Z: screen right is -X
    proj = lambda p: (M @ (p - center).T).T * flip
    if extent is None:
        e = proj(allp)
        extent = max(np.ptp(e[:, 0]), np.ptp(e[:, 1])) * .56
    scale = size / 2 / extent
    img = np.zeros((size, size, 3), np.float32) + np.array([236, 238, 241], np.float32)
    zbuf = np.full((size, size), np.inf, np.float32)
    for name, pts, uv in quads:
        p = proj(pts)
        sx = size / 2 + p[:, 0] * scale
        sy = size / 2 - p[:, 1] * scale
        z = p[:, 2]
        # Back-face cull using screen-space winding.
        area = (sx[1] - sx[0]) * (sy[2] - sy[0]) - (sx[2] - sx[0]) * (sy[1] - sy[0])
        if uv is None:
            uvs = np.array([[0, 0], [1, 0], [1, 1], [0, 1]], float)
        else:
            (u, v), (w, h) = uv['uv'], uv['uv_size']
            uvs = np.array([[u, v], [u + w, v], [u + w, v + h], [u, v + h]], float)
        for tri in ((0, 1, 2), (0, 2, 3)):
            raster(img, zbuf, sx[list(tri)], sy[list(tri)], z[list(tri)], uvs[list(tri)], tex if uv else None,
                   SHADE[name])
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))


def raster(img, zbuf, xs, ys, zs, uvs, tex, shade):
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
    inside = (a >= -1e-4) & (b >= -1e-4) & (c >= -1e-4)
    z = a * zs[0] + b * zs[1] + c * zs[2]
    region = zbuf[y0:y1 + 1, x0:x1 + 1]
    m = inside & (z < region)
    if not m.any():
        return
    if tex is None:
        col = np.array([150, 156, 163], np.float32) * shade
        img[y0:y1 + 1, x0:x1 + 1][m] = col
    else:
        u = a * uvs[0, 0] + b * uvs[1, 0] + c * uvs[2, 0]
        v = a * uvs[0, 1] + b * uvs[1, 1] + c * uvs[2, 1]
        ui = np.clip(u.astype(int), 0, tex.shape[1] - 1)
        vi = np.clip(v.astype(int), 0, tex.shape[0] - 1)
        texel = tex[vi, ui]
        m = m & (texel[..., 3] > 8)
        img[y0:y1 + 1, x0:x1 + 1][m] = texel[..., :3][m] * shade
    region[m] = z[m]


def sheet(geo, texture, out, views=('front', 'three', 'back', 'right'), with_mannequin=False, poses=None, size=700):
    desc, quads = load(geo, poses)
    all_quads = quads + (mannequin() if with_mannequin else [])
    allp = np.concatenate([q[1] for q in quads])
    center = (allp.max(0) + allp.min(0)) / 2
    extent = np.ptp(allp, axis=0).max() * .62
    tex = Image.open(texture)
    frames = [render(desc, all_quads, tex, *VIEWS[v], size=size, center=center, extent=extent) for v in views]
    W = Image.new('RGB', (size * len(frames), size), (236, 238, 241))
    for i, f in enumerate(frames):
        W.paste(f, (i * size, 0))
    Path(out).parent.mkdir(parents=True, exist_ok=True)
    W.save(out)


if __name__ == '__main__':
    ap = argparse.ArgumentParser()
    ap.add_argument('geo'); ap.add_argument('texture'); ap.add_argument('out')
    ap.add_argument('--mannequin', action='store_true')
    ap.add_argument('--views', default='front,three,back,right')
    ap.add_argument('--size', type=int, default=700)
    a = ap.parse_args()
    sheet(a.geo, a.texture, a.out, a.views.split(','), a.mannequin, size=a.size)


def load_bones(geo_path):
    """Rest-pose quads tagged with their bone name: (bone, face, points, uv)."""
    geo = json.loads(Path(geo_path).read_text(encoding='utf8'))['minecraft:geometry'][0]
    bones = {b['name']: b for b in geo['bones']}

    def world(name):
        b = bones[name]
        piv = np.array(b.get('pivot', [0, 0, 0]), float) * MIRROR
        R = rot_matrix(*b.get('rotation', [0, 0, 0]))
        local = lambda p: (R @ (p - piv).T).T + piv
        if b.get('parent'):
            parent = world(b['parent'])
            return lambda p: parent(local(p))
        return local

    out = []
    for b in geo['bones']:
        f = world(b['name'])
        for c in b.get('cubes', []):
            R = rot_matrix(*c.get('rotation', [0, 0, 0]))
            piv = np.array(c.get('pivot', [0, 0, 0]), float) * MIRROR
            for name, pts, uv in cube_quads(c):
                pts = (R @ (pts * MIRROR - piv).T).T + piv
                out.append((b['name'], name, f(pts), uv))
    return geo['description'], out
