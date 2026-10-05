"""Tiny orthographic software renderer for Bedrock/GeckoLib geo models (preview only)."""
import json, math, sys
import numpy as np
from PIL import Image


def rot_matrix(rx, ry, rz):
    # GeckoLib: x mirrored, rotations (-rx, -ry, rz), applied as Rz*Ry*Rx
    ax, ay, az = math.radians(-rx), math.radians(-ry), math.radians(rz)
    cx, sx, cy, sy, cz, sz = math.cos(ax), math.sin(ax), math.cos(ay), math.sin(ay), math.cos(az), math.sin(az)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Rz @ Ry @ Rx


def mirror(p):
    return np.array([-p[0], p[1], p[2]], dtype=float)


def affine(R, pivot, offset=(0, 0, 0), scale=(1, 1, 1)):
    """4x4: translate(offset) * translate(pivot) * R * S * translate(-pivot)"""
    M = np.eye(4)
    M[:3, :3] = R @ np.diag(scale)
    piv = np.array(pivot)
    M[:3, 3] = piv - M[:3, :3] @ piv + np.array(offset)
    return M


def face_quads(cube):
    o = np.array(cube['origin'], float)
    s = np.array(cube['size'], float)
    inf = cube.get('inflate', 0)
    o = o - inf
    s2 = s + 2 * inf
    x0, y0, z0 = o
    x1, y1, z1 = o + s2
    # corners in file coordinates; each face: (top-left, top-right, bottom-left) as seen from outside
    faces = {
        'north': ((x1, y1, z0), (x0, y1, z0), (x1, y0, z0)),
        'south': ((x0, y1, z1), (x1, y1, z1), (x0, y0, z1)),
        'east': ((x1, y1, z1), (x1, y1, z0), (x1, y0, z1)),
        'west': ((x0, y1, z0), (x0, y1, z1), (x0, y0, z0)),
        'up': ((x1, y1, z1), (x0, y1, z1), (x1, y1, z0)),
        'down': ((x1, y0, z0), (x0, y0, z0), (x1, y0, z1)),
    }
    uv = cube['uv']
    sx, sy, sz = s
    out = []
    if isinstance(uv, list):
        u, v = uv
        fx, fy, fz = sx, sy, sz
        rects = {
            'east': (u, v + fz, fz, fy),
            'north': (u + fz, v + fz, fx, fy),
            'west': (u + fz + fx, v + fz, fz, fy),
            'south': (u + 2 * fz + fx, v + fz, fx, fy),
            'up': (u + fz, v, fx, fz),
            'down': (u + fz + fx, v + fz, fx, -fz),
        }
        if cube.get('mirror'):
            rects = {k: (r[0] + r[2], r[1], -r[2], r[3]) for k, r in rects.items()}
            rects['east'], rects['west'] = rects['west'], rects['east']
    else:
        rects = {}
        for k, f in uv.items():
            if 'uv' in f:
                rects[k] = (f['uv'][0], f['uv'][1], f['uv_size'][0], f['uv_size'][1])
    for k, (tl, tr, bl) in faces.items():
        if k in rects:
            out.append((np.array(tl), np.array(tr), np.array(bl), rects[k]))
    return out


def load(path):
    m = json.load(open(path))
    g = m['minecraft:geometry'][0]
    return g


def bone_world(g, pose):
    bones = {b['name']: b for b in g['bones']}
    cache = {}

    def world(name):
        if name in cache:
            return cache[name]
        b = bones[name]
        rot = list(b.get('rotation', [0, 0, 0]))
        p = pose.get(name, {})
        r = p.get('rot', (0, 0, 0))
        rot = [rot[i] + r[i] for i in range(3)]
        off = p.get('pos', (0, 0, 0))
        off = (-off[0], off[1], off[2])
        sc = p.get('scale', (1, 1, 1))
        if not isinstance(sc, (tuple, list)):
            sc = (sc, sc, sc)
        M = affine(rot_matrix(*rot), mirror(b['pivot']), off, sc)
        if b.get('parent'):
            M = world(b['parent']) @ M
        cache[name] = M
        return M
    return bones, world


def render(g, tex, pose=None, yaw=0, pitch=0, size=600, hide=(), center=None, span=None, bg=(40, 40, 48, 255)):
    pose = pose or {}
    bones, world = bone_world(g, pose)
    T = np.asarray(tex.convert('RGBA'), dtype=np.uint8)
    TH, TW = T.shape[:2]
    W, H = g['description']['texture_width'], g['description']['texture_height']
    # view rotation: camera looking along +z from -z (front), then yaw/pitch
    ya, pa = math.radians(yaw), math.radians(pitch)
    Vy = np.array([[math.cos(ya), 0, math.sin(ya)], [0, 1, 0], [-math.sin(ya), 0, math.cos(ya)]])
    Vx = np.array([[1, 0, 0], [0, math.cos(pa), -math.sin(pa)], [0, math.sin(pa), math.cos(pa)]])
    V = Vx @ Vy
    quads = []
    for name, b in bones.items():
        if name in hide or any(_ancestor(bones, name, h) for h in hide):
            continue
        M = world(name)
        for cube in b.get('cubes', []):
            Mc = M
            if 'rotation' in cube:
                Mc = M @ affine(rot_matrix(*cube['rotation']), mirror(cube.get('pivot', [0, 0, 0])))
            for tl, tr, bl, rect in face_quads(cube):
                pts = [Mc @ np.append(mirror(p), 1) for p in (tl, tr, bl)]
                pts = [V @ p[:3] for p in pts]
                quads.append((pts, rect))
    allp = np.array([p for q in quads for p in q[0]])
    if center is None:
        mn, mx = allp.min(0), allp.max(0)
        center = (mn + mx) / 2
        span = max(mx[0] - mn[0], mx[1] - mn[1]) * 1.05
    scale = size / span
    img = np.zeros((size, size, 4), np.uint8)
    img[:] = bg
    zbuf = np.full((size, size), np.inf)
    for (tl, tr, bl), (u, v, du, dv) in quads:
        # screen: x = -px (viewer at -z looking +z: right is -x), y down
        def scr(p):
            return np.array([size / 2 - (p[0] - center[0]) * scale, size / 2 - (p[1] - center[1]) * scale, p[2]])
        A, B, C = scr(tl), scr(tr), scr(bl)
        e1, e2 = B - A, C - A
        det = e1[0] * e2[1] - e1[1] * e2[0]
        if abs(det) < 1e-6:
            continue
        D = B + e2
        xs = [A[0], B[0], C[0], D[0]]
        ys = [A[1], B[1], C[1], D[1]]
        x0, x1 = max(int(min(xs)), 0), min(int(max(xs)) + 1, size)
        y0, y1 = max(int(min(ys)), 0), min(int(max(ys)) + 1, size)
        if x0 >= x1 or y0 >= y1:
            continue
        X, Y = np.meshgrid(np.arange(x0, x1) + 0.5, np.arange(y0, y1) + 0.5)
        dx, dy = X - A[0], Y - A[1]
        s = (dx * e2[1] - dy * e2[0]) / det
        t = (e1[0] * dy - e1[1] * dx) / det
        m = (s >= 0) & (s <= 1) & (t >= 0) & (t <= 1)
        if not m.any():
            continue
        z = A[2] + s * e1[2] + t * e2[2]
        tu = (u + s * du) * TW / W
        tv = (v + t * dv) * TH / H
        tu = np.clip(tu.astype(int), 0, TW - 1)
        tv = np.clip(tv.astype(int), 0, TH - 1)
        col = T[tv, tu]
        m &= col[..., 3] > 8
        sub = zbuf[y0:y1, x0:x1]
        m &= z < sub
        sub[m] = z[m]
        # simple shading by face normal facing
        n = np.cross(e1, e2)
        shade = 0.65 + 0.35 * abs(n[2]) / (np.linalg.norm(n) + 1e-9)
        c = col[..., :3].astype(float) * shade
        img[y0:y1, x0:x1][m, :3] = c[m].astype(np.uint8)
        img[y0:y1, x0:x1][m, 3] = 255
    return Image.fromarray(img), center, span


def _ancestor(bones, name, h):
    b = bones[name]
    while b.get('parent'):
        if b['parent'] == h:
            return True
        b = bones[b['parent']]
    return False


if __name__ == '__main__':
    g = load(sys.argv[1])
    tex = Image.open(sys.argv[2])
    out = sys.argv[3]
    views = [(0, 0), (90, 0), (180, 0), (-35, 15)]
    ims = [render(g, tex, yaw=y, pitch=p, size=500)[0] for y, p in views]
    W = Image.new('RGBA', (500 * len(ims), 500))
    for i, im in enumerate(ims):
        W.paste(im, (500 * i, 0))
    W.save(out)
