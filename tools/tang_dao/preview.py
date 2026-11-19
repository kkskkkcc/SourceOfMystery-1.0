"""Contact sheets of the first-person Tang Dao clips, rendered from the rig without the game.

python tools/tang_dao/preview.py draw diagonal rising overhead --step 1 --out build/tang-dao-preview
Each clip becomes one PNG of frames at 854x480 with the hand pass FOV (70 degrees).
"""
import argparse
import math
import sys
from functools import lru_cache
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
import rig as R  # noqa: E402

W, H, FOV = 854, 480, 70
SKY, SKIN, SLEEVE = (170, 200, 235), (196, 150, 110), (120, 90, 70)
STEEL, HILT, SHEATH = (200, 205, 210), (90, 60, 40), (40, 36, 34)
ARM_BOX = {1: ((-3, -2, -2), (1, 10, 2)), -1: ((-1, -2, -2), (3, 10, 2))}


@lru_cache(maxsize=None)
def mesh(name):
    verts, faces = [], []
    for line in (R.ASSETS / f'models/item/{name}.obj').read_text().splitlines():
        if line.startswith('v '):
            verts.append([float(x) for x in line.split()[1:4]])
        elif line.startswith('f '):
            idx = [int(p.split('/')[0]) - 1 for p in line.split()[1:]]
            for k in range(1, len(idx) - 1):
                faces.append((idx[0], idx[k], idx[k + 1]))
    return np.array(verts) - .5, np.array(faces)


def project(p):
    f = 1 / math.tan(math.radians(FOV) / 2)
    z = np.minimum(p[..., 2], -1e-3)
    x = p[..., 0] * f / (-z) * H / 2 + W / 2
    y = -p[..., 1] * f / (-z) * H / 2 + H / 2
    return np.stack([x, y], -1)


def box_tris(lo, hi, pivot, rot):
    lo, hi = np.array(lo) / 16, np.array(hi) / 16
    c = np.array([[x, y, z] for x in (lo[0], hi[0]) for y in (lo[1], hi[1]) for z in (lo[2], hi[2])])
    w = c @ rot.T + pivot
    quads = [(0, 1, 3, 2), (4, 6, 7, 5), (0, 4, 5, 1), (2, 3, 7, 6), (0, 2, 6, 4), (1, 5, 7, 3)]
    return [w[list(q)] for q in quads]


def shade(color, normal):
    light = .55 + .45 * max(0, float(normal @ np.array([.3, .8, .5]) / np.linalg.norm([.3, .8, .5])))
    return tuple(int(c * light) for c in color)


def render(fr, rig, side=1, label=''):
    polys = []
    for f, (p, r) in ((side, fr['main']), (-side, fr['off'])):
        lo, hi = ARM_BOX[f]
        for q in box_tris(lo, hi, p, r):
            n = np.cross(q[1] - q[0], q[2] - q[0])
            polys.append((q, shade(SKIN, n / (np.linalg.norm(n) + 1e-9))))
    for name, frame, color in (('tang_dao', fr['blade'], None), ('tang_dao_scabbard', fr['scabbard'], SHEATH)):
        if frame is None:
            continue
        v, faces = mesh(name)
        origin, item = frame
        w = (v * rig.scale) @ item.T + origin
        for tri in faces[::2]:
            q = w[list(tri)]
            if color is None:
                y = v[tri[0], 1]
                c = STEEL if y > .13 else HILT
            else:
                c = color
            n = np.cross(q[1] - q[0], q[2] - q[0])
            polys.append((q, shade(c, n / (np.linalg.norm(n) + 1e-9))))
    img = Image.new('RGB', (W, H), SKY)
    d = ImageDraw.Draw(img)
    d.rectangle([0, H * .62, W, H], fill=(150, 150, 150))
    polys.sort(key=lambda pc: pc[0][:, 2].mean())
    for q, c in polys:
        if (q[:, 2] > -.02).any():
            continue
        d.polygon([tuple(x) for x in project(q)], fill=c)
    d.line([W / 2 - 8, H / 2, W / 2 + 8, H / 2], fill=(255, 255, 255))
    d.line([W / 2, H / 2 - 8, W / 2, H / 2 + 8], fill=(255, 255, 255))
    d.text((8, 8), label, fill=(0, 0, 0))
    return img


def sheet(images, cols=4):
    cols = min(cols, len(images))
    rows = math.ceil(len(images) / cols)
    s = .5
    out = Image.new('RGB', (int(W * s) * cols, int(H * s) * rows), (30, 30, 30))
    for i, im in enumerate(images):
        out.paste(im.resize((int(W * s), int(H * s))), ((i % cols) * int(W * s), (i // cols) * int(H * s)))
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('clips', nargs='+')
    ap.add_argument('--step', type=float, default=1)
    ap.add_argument('--side', type=int, default=1)
    ap.add_argument('--out', default=str(R.ROOT / 'build/tang-dao-preview'))
    a = ap.parse_args()
    data = R.load()
    rig = R.Rig(data)
    out = Path(a.out); out.mkdir(parents=True, exist_ok=True)
    for name in a.clips:
        player = R.Player(data, rig, name, a.side)
        end = player.times[-1]
        frames = [render(player.frame(t), rig, a.side, f'{name} t={t:.1f}')
                  for t in np.arange(0, end + 1e-6, a.step)]
        sheet(frames).save(out / f'{name}.png')
        print(out / f'{name}.png', len(frames))


if __name__ == '__main__':
    main()
