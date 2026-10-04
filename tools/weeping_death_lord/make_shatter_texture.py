"""
生成「斩碎空间」特效贴图 textures/entity/space_shatter.png（256x256，带透明度）：
一块像玻璃一样碎开的空间——从中心放射的裂纹 + 几圈参差的环形裂纹，碎片本身是半透明的暗紫色，
裂纹发白光，中心有一道斜向的斩痕。
放射裂纹的角度必须和 client.SpaceShatterRenderer.CRACK_ANGLES 一致（渲染器沿这些裂纹把贴图切成碎片飞散）。
用法：python3 make_shatter_texture.py <仓库根目录>
"""
import math
import os
import sys

import numpy as np
from PIL import Image

ROOT = sys.argv[1] if len(sys.argv) > 1 else '.'
OUT = os.path.join(ROOT, 'src/main/resources/assets/sourceofmystery/textures/entity/space_shatter.png')
CRACK_ANGLES = [0, 27, 61, 88, 117, 149, 178, 206, 239, 268, 297, 331]
RINGS = [0.22, 0.48, 0.74]
N = 256


def main():
    rng = np.random.default_rng(42)
    y, x = np.mgrid[0:N, 0:N]
    u = (x + 0.5) / N * 2 - 1
    v = 1 - (y + 0.5) / N * 2
    r = np.sqrt(u * u + v * v)
    a = (np.degrees(np.arctan2(v, u)) + 360) % 360

    # 每条放射裂纹轻微弯折：角度随半径小幅摆动
    crack = np.zeros((N, N))
    for i, ang in enumerate(CRACK_ANGLES):
        wobble = 1.2 * np.sin(r * 7 + i * 1.7)
        d = np.abs(((a - ang - wobble + 180) % 360) - 180)  # 角度差（度）
        dist = np.radians(d) * r * N / 2                     # 换算成像素距离
        width = 1.6 + 1.2 * (1 - r)
        crack = np.maximum(crack, np.clip(1 - dist / width, 0, 1) * (r < 0.98))

    # 环形裂纹：相邻放射裂纹之间的直线段（像玻璃那样折线状），部分段缺失，参差不齐
    for ring in RINGS:
        radii = ring * rng.uniform(0.85, 1.15, len(CRACK_ANGLES))
        for i, ang in enumerate(CRACK_ANGLES):
            if rng.random() < 0.25:
                continue
            j = (i + 1) % len(CRACK_ANGLES)
            a0, a1 = math.radians(ang), math.radians(CRACK_ANGLES[j] + (360 if j == 0 else 0))
            p0 = np.array([math.cos(a0), math.sin(a0)]) * radii[i]
            p1 = np.array([math.cos(a1), math.sin(a1)]) * radii[j]
            seg = p1 - p0
            t = np.clip(((u - p0[0]) * seg[0] + (v - p0[1]) * seg[1]) / (seg @ seg), 0, 1)
            dx = u - (p0[0] + t * seg[0])
            dy = v - (p0[1] + t * seg[1])
            dist = np.sqrt(dx * dx + dy * dy) * N / 2
            crack = np.maximum(crack, np.clip(1 - dist / 1.3, 0, 1))

    # 中心斩痕：一道斜向的亮线
    slash_dir = np.array([math.cos(math.radians(-35)), math.sin(math.radians(-35))])
    along = u * slash_dir[0] + v * slash_dir[1]
    across = -u * slash_dir[1] + v * slash_dir[0]
    slash_w = 0.06 * np.clip(1 - np.abs(along) / 0.95, 0, 1)
    slash = np.clip(1 - np.abs(across) / np.maximum(slash_w, 1e-4), 0, 1) * (np.abs(along) < 0.95)

    glow = np.clip(np.maximum(crack, slash), 0, 1)
    halo = np.clip(1 - r, 0, 1) ** 2

    noise = rng.random((N, N)) * 0.15
    img = np.zeros((N, N, 4))
    # 碎片：半透明暗紫，靠中心更亮
    img[..., 0] = 0.20 + 0.25 * halo + noise * 0.3
    img[..., 1] = 0.05 + 0.10 * halo + noise * 0.1
    img[..., 2] = 0.35 + 0.35 * halo + noise * 0.4
    img[..., 3] = (0.42 + 0.25 * halo) * (r < 0.98) * np.clip((0.98 - r) / 0.06, 0, 1)
    # 裂纹：白中带紫的光
    for c, col in enumerate((0.95, 0.88, 1.0)):
        img[..., c] = img[..., c] * (1 - glow) + col * glow
    img[..., 3] = np.maximum(img[..., 3], glow)
    Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8)).save(OUT)
    print('wrote', OUT)


if __name__ == '__main__':
    main()
