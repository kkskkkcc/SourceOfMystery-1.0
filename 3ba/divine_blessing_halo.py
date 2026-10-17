"""神威天佑胸甲背后的光环特效贴图。

设计思路：
- 4 圈黑白交替的环带 + 中心深色 + 白色「神」字
- 每圈带各向一圈符文 / 短线装饰
- 每张图 256x256，环带按 inner / outer 比例画在边缘，靠 alpha 让 quad 重叠时显示出正确的环

输出：
  out/textures/entity/divine_blessing_halo_center.png
  out/textures/entity/divine_blessing_halo_ring_{0..3}.png
"""
import math
import os

from PIL import Image, ImageDraw

W = 256
HALF = W // 2

# 配色：参考现有 heaven_sigil / 祭坛的暖白色调，但换成黑白对比
DARK = (15, 15, 25)         # 深底色（圆心 + 黑环）
LIGHT = (240, 240, 248)     # 亮底色（白环）
GLOW_W = (255, 248, 230)     # 暖白色（圆心外圈、神字）


def rgba(c, a=255):
    return (c[0], c[1], c[2], a)


def draw_god_char(d, cx, cy, size, color):
    """简易像素「神」字（礻 + 申），用矩形块拼出。

    比例参照楷书结构：
      礻：横、竖钩、左点、右点
      申：上横、左竖、右竖、中横、下横
    """
    s = size
    # 礻
    d.rectangle([cx - s * 0.85, cy - s * 0.95, cx - s * 0.05, cy - s * 0.83], fill=color)  # 横
    d.rectangle([cx - s * 0.55, cy - s * 0.83, cx - s * 0.40, cy + s * 0.95], fill=color)  # 竖钩
    d.rectangle([cx - s * 0.90, cy - s * 0.55, cx - s * 0.72, cy - s * 0.40], fill=color)  # 左点
    d.rectangle([cx - s * 0.28, cy - s * 0.30, cx - s * 0.12, cy - s * 0.15], fill=color)  # 右点
    # 申
    d.rectangle([cx - s * 0.05, cy - s * 0.95, cx + s * 0.88, cy - s * 0.78], fill=color)  # 上横
    d.rectangle([cx + s * 0.05, cy - s * 0.83, cx + s * 0.22, cy + s * 0.95], fill=color)  # 左竖
    d.rectangle([cx + s * 0.65, cy - s * 0.83, cx + s * 0.82, cy + s * 0.95], fill=color)  # 右竖
    d.rectangle([cx + s * 0.05, cy - s * 0.05, cx + s * 0.82, cy + s * 0.10], fill=color)  # 中横
    d.rectangle([cx + s * 0.05, cy + s * 0.65, cx + s * 0.82, cy + s * 0.80], fill=color)  # 下横


def build_center():
    """圆心：整张图都是实心深色 + 白色「神」字。

    quad 大小由 OUTER_R 决定；纹理半径外的 alpha=0 不会被看见。
    """
    n = W
    img = Image.new('RGBA', (n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    cx, cy = n // 2, n // 2

    # 实心深色圆
    d.ellipse([cx - 128, cy - 128, cx + 128, cy + 128], fill=rgba(DARK))
    # 外圈亮边（两层，做出轻微辉光）
    d.ellipse([cx - 128, cy - 128, cx + 128, cy + 128], outline=rgba(GLOW_W), width=5)
    d.ellipse([cx - 122, cy - 122, cx + 122, cy + 122], outline=rgba(GLOW_W), width=1)

    # 「神」字（暖白色）
    draw_god_char(d, cx, cy, 130, rgba(GLOW_W))

    return img


def build_ring(inner_frac, outer_frac, is_white):
    """画一帧环带。

    inner_frac / outer_frac 是相对 quad 边缘的比例；
    比如 outer_frac=1.0、inner_frac=0.7 表示环带占纹理最外 30% 的环带。
    """
    n = W
    img = Image.new('RGBA', (n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    cx, cy = n // 2, n // 2
    max_r = n // 2 - 1

    inner_r = int(max_r * inner_frac)
    outer_r = int(max_r * outer_frac)

    main_color = rgba(LIGHT) if is_white else rgba(DARK)
    detail_color = rgba(DARK) if is_white else rgba(LIGHT)

    # 填充环带（先填外圆，再挖空内圆）
    d.ellipse([cx - outer_r, cy - outer_r, cx + outer_r, cy + outer_r], fill=main_color)
    d.ellipse([cx - inner_r, cy - inner_r, cx + inner_r, cy + inner_r], fill=(0, 0, 0, 0))

    # 内外圈细线
    d.ellipse([cx - outer_r, cy - outer_r, cx + outer_r, cy + outer_r], outline=detail_color, width=2)
    d.ellipse([cx - inner_r, cy - inner_r, cx + inner_r, cy + inner_r], outline=detail_color, width=2)

    band = outer_r - inner_r
    mid_r = (inner_r + outer_r) / 2

    # 径向短线装饰
    n_dashes = max(8, int(2 * math.pi * mid_r / 14))
    for i in range(n_dashes):
        angle = 2 * math.pi * i / n_dashes
        ca, sa = math.cos(angle), math.sin(angle)
        x0 = cx + (mid_r - band * 0.30) * ca
        y0 = cy + (mid_r - band * 0.30) * sa
        x1 = cx + (mid_r + band * 0.30) * ca
        y1 = cy + (mid_r + band * 0.30) * sa
        d.line([(x0, y0), (x1, y1)], fill=detail_color, width=2)

    # 一圈小十字符文
    glyph_r = outer_r - band * 0.42
    n_glyphs = max(6, int(2 * math.pi * glyph_r / 20))
    for i in range(n_glyphs):
        angle = 2 * math.pi * (i + 0.5) / n_glyphs
        ca, sa = math.cos(angle), math.sin(angle)
        x = cx + glyph_r * ca
        y = cy + glyph_r * sa
        sz = max(2, int(band * 0.18))
        d.rectangle([x - sz * 1.5, y - sz * 0.5, x + sz * 1.5, y + sz * 0.5], fill=detail_color)
        d.rectangle([x - sz * 0.5, y - sz * 1.5, x + sz * 0.5, y + sz * 1.5], fill=detail_color)

    return img


def main():
    out_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'out', 'textures', 'entity')
    os.makedirs(out_dir, exist_ok=True)

    # 中心：半径 0.08 块 → quad 0.16，整张图都是实心
    center = build_center()
    center.save(os.path.join(out_dir, 'divine_blessing_halo_center.png'))
    print('Saved center')

    # 4 圈：内→外，颜色交替（白、黑、白、黑）
    # 设计：要让光环明显大于玩家上半身（身宽 0.6，上半身约 1.0），
    # 所以外径放到 0.80（直径 1.60 块，是上半身的 1.6 倍以上）。
    # 同时每圈的环带宽度递增（外圈更宽），从内到外视觉上"展开"。
    rings = [
        # (inner_frac, outer_frac, is_white) — outer_frac=1.0 表示占满 quad 边缘
        (0.25 / 0.32, 1.00, True),   # Ring 0 (white, innermost)
        (0.39 / 0.48, 1.00, False),  # Ring 1 (black)
        (0.54 / 0.64, 1.00, True),   # Ring 2 (white)
        (0.70 / 0.80, 1.00, False),  # Ring 3 (black, outermost)
    ]
    for i, (inner_f, outer_f, is_white) in enumerate(rings):
        ring = build_ring(inner_f, outer_f, is_white)
        ring.save(os.path.join(out_dir, f'divine_blessing_halo_ring_{i}.png'))
        print(f'Saved ring {i}: inner_frac={inner_f:.3f}, outer_frac={outer_f:.3f}, white={is_white}')

    print(f'All halo textures saved to {out_dir}')


if __name__ == '__main__':
    main()
