"""
泣死之主的特效贴图（全部程序生成，可重复运行）：
  textures/entity/weeping_halo.png          背后的黑色神环（荆棘一样的尖刺、刻着符文的黑环，边缘是散开的黑雾）
  textures/entity/weeping_halo_glow.png     神环的发光层（加法混合：符文和内缘的暗紫色微光）
  textures/entity/weeping_magic_circle.png  大招的黑色魔法阵（双圈、符文环、六芒星、中心的眼）
  textures/entity/weeping_magic_circle_glow.png  魔法阵的发光层（白紫色的线条）
  textures/entity/weeping_void_orb.png      吸附的虚空黑球（纯黑核心、紫色的吸积旋涡、零星的星点）
  textures/entity/weeping_laser.png         白色激光（横向：白色核心 → 淡紫边缘；纵向：流动的条纹）
  textures/particle/death_skull_0~3.png     骷髅头粒子（下颌一张一合的四帧）
用法：python3 make_effect_textures.py <仓库根目录>
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = sys.argv[1] if len(sys.argv) > 1 else '.'
TEX = os.path.join(ROOT, 'src/main/resources/assets/sourceofmystery/textures')
RNG = np.random.default_rng(1337)


def save(img, *path):
    out = os.path.join(TEX, *path)
    img.save(out)
    print('wrote', out)


def polar(n):
    y, x = np.mgrid[0:n, 0:n]
    u = (x + 0.5) / n * 2 - 1
    v = 1 - (y + 0.5) / n * 2
    return np.sqrt(u * u + v * v), np.arctan2(v, u)


def rune_strokes(draw, cx, cy, r, count, size, width, fill, seed):
    """沿着半径 r 的圆排一圈"符文"：每个符文是几笔随机的折线"""
    rng = np.random.default_rng(seed)
    for i in range(count):
        a = 2 * math.pi * i / count
        px, py = cx + r * math.cos(a), cy + r * math.sin(a)
        # 符文的局部坐标：切向 t、径向 n
        t = (-math.sin(a), math.cos(a))
        nrm = (math.cos(a), math.sin(a))
        for _ in range(rng.integers(2, 4)):
            pts = []
            for _ in range(rng.integers(2, 4)):
                s = rng.uniform(-0.5, 0.5) * size
                q = rng.uniform(-0.5, 0.5) * size
                pts.append((px + t[0] * s + nrm[0] * q, py + t[1] * s + nrm[1] * q))
            draw.line(pts, fill=fill, width=width)


# ------------------------------------------------------------------ 神环

def halo():
    n = 256
    r, a = polar(n)
    img = np.zeros((n, n, 4))
    # 主环：粗黑环，带轻微起伏
    band = 0.70 + 0.012 * np.sin(a * 9)
    ring = np.clip(1 - np.abs(r - band) / 0.085, 0, 1) ** 0.6
    # 内细环
    inner = np.clip(1 - np.abs(r - 0.52) / 0.018, 0, 1)
    # 外侧荆棘尖刺：24 根长短不一、略带弯曲
    spikes = np.zeros_like(r)
    for i in range(24):
        ang = 2 * math.pi * i / 24 + RNG.uniform(-0.05, 0.05)
        length = 0.16 + 0.10 * (i % 2) + RNG.uniform(0, 0.05)
        d = np.angle(np.exp(1j * (a - ang - 0.25 * (r - 0.76))))
        along = (r - 0.76) / length
        width = 0.10 * (1 - along)
        spikes = np.maximum(spikes, ((along > 0) & (along < 1) & (np.abs(d) < width)).astype(float))
    # 内侧短刺
    for i in range(12):
        ang = 2 * math.pi * (i + 0.5) / 12
        d = np.angle(np.exp(1j * (a - ang)))
        along = (0.62 - r) / 0.08
        spikes = np.maximum(spikes, ((along > 0) & (along < 1) & (np.abs(d) < 0.05 * (1 - along))).astype(float))
    body = np.clip(ring + inner + spikes, 0, 1)
    # 黑雾：环的外侧散开的烟气
    noise = Image.fromarray((RNG.random((64, 64)) * 255).astype(np.uint8)).resize((n, n), Image.BICUBIC)
    noise = np.asarray(noise.filter(ImageFilter.GaussianBlur(4)), float) / 255
    mist = np.clip(1 - np.abs(r - 0.72) / 0.28, 0, 1) ** 2 * (0.35 + 0.65 * noise) * 0.55
    alpha = np.clip(np.maximum(body, mist), 0, 1)
    shade = 0.04 + 0.05 * noise
    img[..., 0] = shade * 0.9
    img[..., 1] = shade * 0.5
    img[..., 2] = shade * 1.2
    img[..., 3] = alpha
    im = Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8))
    # 刻在主环上的符文（暗紫色凹痕）
    d = ImageDraw.Draw(im)
    rune_strokes(d, n / 2, n / 2, 0.70 * n / 2, 18, 13, 2, (70, 20, 95, 255), 5)
    save(im, 'entity', 'weeping_halo.png')

    # 发光层：符文 + 内缘的暗紫色微光（加法混合，黑色 = 不发光）
    g = Image.new('RGBA', (n, n), (0, 0, 0, 255))
    gd = ImageDraw.Draw(g)
    rune_strokes(gd, n / 2, n / 2, 0.70 * n / 2, 18, 13, 2, (150, 70, 210, 255), 5)
    ga = np.asarray(g, float) / 255
    edge = np.clip(1 - np.abs(r - 0.61) / 0.03, 0, 1) * 0.45 + np.clip(1 - np.abs(r - 0.52) / 0.012, 0, 1) * 0.35
    glow = ga[..., :3] + edge[..., None] * np.array([0.45, 0.2, 0.65])
    glow = np.asarray(Image.fromarray((np.clip(glow, 0, 1) * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(0.8)), float) / 255
    out = np.dstack([glow, np.ones((n, n))])
    save(Image.fromarray((np.clip(out, 0, 1) * 255).astype(np.uint8)), 'entity', 'weeping_halo_glow.png')


# ------------------------------------------------------------------ 魔法阵

def magic_circle():
    n = 512
    c = n / 2

    def draw_circle(color, glow):
        im = Image.new('RGBA', (n, n), (0, 0, 0, 0 if not glow else 255))
        d = ImageDraw.Draw(im)
        w = 5 if not glow else 3
        for rr, ww in ((0.96, w + 2), (0.90, w), (0.70, w), (0.64, w - 1), (0.30, w)):
            R = rr * c
            d.ellipse((c - R, c - R, c + R, c + R), outline=color, width=max(1, ww))
        # 两圈之间的符文环
        rune_strokes(d, c, c, 0.80 * c, 28, 26, 3, color, 11)
        # 六芒星
        for k in (0, 1):
            pts = [(c + 0.70 * c * math.cos(math.pi / 2 + k * math.pi / 3 + 2 * math.pi * i / 3),
                    c - 0.70 * c * math.sin(math.pi / 2 + k * math.pi / 3 + 2 * math.pi * i / 3)) for i in range(3)]
            d.polygon(pts, outline=color, width=w)
        # 六个小圆
        for i in range(6):
            ang = math.pi / 2 + 2 * math.pi * i / 6
            px, py = c + 0.70 * c * math.cos(ang), c - 0.70 * c * math.sin(ang)
            R = 0.07 * c
            d.ellipse((px - R, py - R, px + R, py + R), outline=color, width=w - 1)
        # 中心的眼
        d.ellipse((c - 0.26 * c, c - 0.12 * c, c + 0.26 * c, c + 0.12 * c), outline=color, width=w)
        R = 0.08 * c
        d.ellipse((c - R, c - R, c + R, c + R), fill=color)
        # 放射的短线
        for i in range(36):
            ang = 2 * math.pi * i / 36
            r0, r1 = (0.90, 0.96) if i % 3 else (0.86, 1.0)
            d.line((c + r0 * c * math.cos(ang), c + r0 * c * math.sin(ang),
                    c + r1 * c * math.cos(ang), c + r1 * c * math.sin(ang)), fill=color, width=w - 1)
        return im

    base = draw_circle((8, 2, 12, 240), False)
    # 底色：半透明的暗紫色雾
    r, a = polar(n)
    fill = np.zeros((n, n, 4))
    fill[..., 0], fill[..., 1], fill[..., 2] = 0.10, 0.02, 0.16
    fill[..., 3] = np.clip(0.96 - r, 0, 1) / 0.96 * 0.35 * (r < 0.96)
    fill_im = Image.fromarray((fill * 255).astype(np.uint8))
    fill_im.alpha_composite(base)
    save(fill_im.resize((256, 256), Image.LANCZOS), 'entity', 'weeping_magic_circle.png')

    glow = draw_circle((170, 120, 255, 255), True).filter(ImageFilter.GaussianBlur(2.0))
    sharp = draw_circle((235, 220, 255, 255), True)
    ga = np.maximum(np.asarray(glow, float), np.asarray(sharp, float) * 0.8)
    ga[..., 3] = 255
    save(Image.fromarray(ga.astype(np.uint8)).resize((256, 256), Image.LANCZOS), 'entity', 'weeping_magic_circle_glow.png')


# ------------------------------------------------------------------ 虚空黑球

def void_orb():
    n = 128
    r, a = polar(n)
    img = np.zeros((n, n, 4))
    core = r < 0.55
    # 吸积旋涡：螺旋状的紫色光带
    spiral = 0.5 + 0.5 * np.sin(a * 3 + r * 14)
    disk = np.clip(1 - np.abs(r - 0.66) / 0.16, 0, 1) * (0.35 + 0.65 * spiral)
    img[..., 0] = 0.30 * disk
    img[..., 1] = 0.06 * disk
    img[..., 2] = 0.45 * disk
    img[..., 3] = np.clip(np.maximum(core.astype(float), disk * 0.9), 0, 1)
    # 外圈淡淡的黑雾
    mist = np.clip(1 - (r - 0.55) / 0.45, 0, 1) ** 2 * 0.6 * (r >= 0.55)
    img[..., 3] = np.maximum(img[..., 3], mist)
    # 核心里零星的星点
    stars = (RNG.random((n, n)) > 0.992) & (r < 0.45)
    img[stars, 0:3] = np.array([0.55, 0.45, 0.75])
    # 核心边缘一圈细细的事件视界光
    rim = np.clip(1 - np.abs(r - 0.55) / 0.02, 0, 1)
    img[..., 0] += 0.5 * rim
    img[..., 1] += 0.3 * rim
    img[..., 2] += 0.7 * rim
    save(Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8)), 'entity', 'weeping_void_orb.png')


# ------------------------------------------------------------------ 激光

def laser():
    w, h = 32, 128
    x = (np.arange(w) + 0.5) / w * 2 - 1
    y = np.arange(h) / h
    X, Y = np.meshgrid(x, y)
    streak = 0.75 + 0.25 * np.sin(Y * 2 * math.pi * 3 + np.sin(X * 3) * 2)
    core = np.clip(1 - np.abs(X) / 0.35, 0, 1) ** 0.7
    edge = np.clip(1 - np.abs(X), 0, 1) ** 2
    img = np.zeros((h, w, 4))
    img[..., 0] = np.clip(core * 1.0 + edge * 0.55 * streak, 0, 1)
    img[..., 1] = np.clip(core * 0.98 + edge * 0.40 * streak, 0, 1)
    img[..., 2] = np.clip(core * 1.0 + edge * 0.85 * streak, 0, 1)
    img[..., 3] = 1
    save(Image.fromarray((img * 255).astype(np.uint8)), 'entity', 'weeping_laser.png')


# ------------------------------------------------------------------ 骷髅头粒子

SKULL = [
    ".....######.....",
    "...##########...",
    "..############..",
    "..############..",
    ".##############.",
    ".###..####..###.",
    ".##....##....##.",
    ".##....##....##.",
    ".###..####..###.",
    "..#####.#.#####.",
    "..######.######.",
    "...##########...",
]
JAW = ["...#.#.#.#.#.#..", "....#########..."]


def skull_frames():
    for f in range(4):
        open_ = [0, 1, 2, 1][f]
        im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        px = im.load()
        rows = SKULL + ["................"] * open_ + JAW
        for yy, row in enumerate(rows[:16]):
            for xx, ch in enumerate(row):
                if ch == '#':
                    shade = 235 - yy * 4
                    px[xx, yy] = (shade, shade - 8, min(255, shade + 10), 255)
        # 暗紫色描边
        a = np.asarray(im, float)
        mask = a[..., 3] > 0
        grown = np.zeros_like(mask)
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            grown |= np.roll(np.roll(mask, dx, 1), dy, 0)
        outline = grown & ~mask
        a[outline] = (60, 15, 90, 200)
        save(Image.fromarray(a.astype(np.uint8)), 'particle', f'death_skull_{f}.png')


if __name__ == '__main__':
    halo()
    magic_circle()
    void_orb()
    laser()
    skull_frames()
