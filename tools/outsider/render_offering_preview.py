"""Create a lightweight offline storyboard for the Dark Dimensional Offering intro.

This is a visual timing preview, not a Minecraft capture. It uses the current
41-second OfferingPortalRitual timing and the reviewed Outsider takeoff render.
"""
from pathlib import Path
import math
from PIL import Image, ImageDraw, ImageFont, ImageFilter

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "build" / "offering-preview"
OUT.mkdir(parents=True, exist_ok=True)
W, H = 640, 360
FPS = 12
TOTAL = 41.0

FONT_PATH = "C:/Windows/Fonts/msyh.ttc"
FONT = ImageFont.truetype(FONT_PATH, 17)
SMALL = ImageFont.truetype(FONT_PATH, 12)
TINY = ImageFont.truetype(FONT_PATH, 10)

TAKEOFF = Image.open(ROOT / "art/outsider_void_boss/v1/combat/takeoff.webp").convert("RGB")
TAKEOFF_FRAMES = []
for i in range(getattr(TAKEOFF, "n_frames", 1)):
    TAKEOFF.seek(i)
    # The reviewed sheet contains front and side views; use the front half and
    # remove its review title strip.
    TAKEOFF_FRAMES.append(TAKEOFF.convert("RGB").crop((0, 42, 420, 695)))
MODEL = Image.open(ROOT / "art/outsider_void_boss/v1/combat/model-render-hd.png").convert("RGB")
FACE = MODEL.crop((540, 260, 1510, 1120))


def ease(u):
    u = max(0.0, min(1.0, u))
    return u * u * (3.0 - 2.0 * u)


def lerp(a, b, u):
    return a + (b - a) * u


def background(t):
    im = Image.new("RGB", (W, H))
    pix = im.load()
    pulse = 0.5 + 0.5 * math.sin(t * 1.7)
    for y in range(H):
        for x in range(W):
            r = int(7 + 14 * y / H + 5 * pulse)
            g = int(3 + 4 * y / H)
            b = int(18 + 23 * y / H + 15 * pulse)
            pix[x, y] = (r, g, b)
    draw = ImageDraw.Draw(im, "RGBA")
    for i in range(42):
        x = (i * 97 + 31) % W
        y = (i * 53 + 17) % (H - 55)
        a = 45 + int(35 * (0.5 + 0.5 * math.sin(t * 2.0 + i)))
        draw.ellipse((x, y, x + 1, y + 1), fill=(151, 88, 255, a))
    return im


def portal(draw, t, center=(320, 195), scale=1.0, alpha=255):
    cx, cy = center
    blocks = []
    for u, v in [(0, 0), (1, 0), (2, 0), (3, 0), (3, 1), (3, 2),
                 (3, 3), (3, 4), (2, 4), (1, 4), (0, 4), (0, 3),
                 (0, 2), (0, 1)]:
        x = cx + (u - 1.5) * 17 * scale
        y = cy + (2 - v) * 17 * scale
        blocks.append((x, y))
    start = max(0.0, min(1.0, (t - 6.0) / 7.0))
    e = ease(start)
    for i, (x, y) in enumerate(blocks):
        ang = i * math.tau / 14.0
        tx = cx + math.cos(ang) * 78 * scale
        ty = cy + math.sin(ang) * 78 * scale
        x = lerp(x, tx, e)
        y = lerp(y, ty, e)
        wobble = 2.4 * math.sin(t * 11 + i * .8) if t < 13 else 0
        x += wobble * math.cos(ang)
        y += wobble * math.sin(ang)
        s = 14 * scale
        draw.rounded_rectangle((x - s / 2, y - s / 2, x + s / 2, y + s / 2),
                               radius=2, fill=(18, 7, 31, alpha), outline=(122, 31, 185, alpha), width=2)
        draw.rectangle((x - s / 2 + 3, y - s / 2 + 3, x - 2, y - 2), fill=(54, 17, 76, alpha // 2))
    if e > .75:
        draw.ellipse((cx - 82 * scale, cy - 82 * scale, cx + 82 * scale, cy + 82 * scale),
                     outline=(130, 32, 210, int(alpha * .25)), width=2)


def trails(draw, t):
    u = max(0.0, min(1.0, (t - 16.0) / 4.0))
    for i in range(22):
        a = i * math.tau / 22 + t * .16
        r = 122 + (i * 31 % 55)
        x0, y0 = 320 + math.cos(a) * r, 195 + math.sin(a) * r
        x1, y1 = 320 + math.cos(a) * 18, 195 + math.sin(a) * 18
        col = (86, 14, 160, int(150 * u)) if i % 3 else (7, 3, 18, int(180 * u))
        draw.line((x0, y0, x1, y1), fill=col, width=2)


def rift(draw, t):
    open_u = max(0.0, min(1.0, (t - 20.0) / 4.0))
    for i in range(12):
        a = i * math.tau / 12 + t * .22
        r = 10 + 78 * open_u
        pts = [(320, 195)]
        for j in range(1, 6):
            rr = r * j / 5
            jitter = 8 * math.sin(i * 7 + j * 4)
            pts.append((320 + math.cos(a) * rr + jitter, 195 + math.sin(a) * rr - jitter))
        draw.line(pts, fill=(170, 35, 255, int(215 * open_u)), width=2)
    if t >= 24:
        u = max(0.0, min(1.0, (t - 24.0) / 3.0))
        r = 14 + 70 * u
        draw.ellipse((320 - r, 195 - r, 320 + r, 195 + r), fill=(3, 1, 12, int(205 * u)),
                     outline=(175, 33, 255, 230), width=3)


def model_panel(canvas, t):
    if t < 27:
        return
    if t < 34:
        u = ease((t - 27) / 7)
        frame = TAKEOFF_FRAMES[min(len(TAKEOFF_FRAMES) - 1, int(u * (len(TAKEOFF_FRAMES) - 1)))]
        size = (240, 330)
        frame = frame.resize(size, Image.Resampling.LANCZOS)
        # Reveal upward through the portal while leaving the portal silhouette
        # behind it. This mirrors the game renderer's staged reveal.
        mask = Image.new("L", size, 0)
        md = ImageDraw.Draw(mask)
        md.rectangle((0, int(size[1] * (1 - u)), size[0], size[1]), fill=255)
        canvas.paste(frame, (200, 30), mask)
    elif t < 38:
        frame = TAKEOFF_FRAMES[-1].resize((240, 330), Image.Resampling.LANCZOS)
        canvas.paste(frame, (200, 30))
        d = ImageDraw.Draw(canvas, "RGBA")
        pulse = 0.65 + 0.35 * math.sin(t * 7)
        d.ellipse((430, 190, 462, 222), outline=(190, 55, 255, int(230 * pulse)), width=3)
        d.ellipse((437, 197, 455, 215), outline=(92, 14, 170, int(190 * pulse)), width=2)
    else:
        face = FACE.resize((260, 230), Image.Resampling.LANCZOS)
        canvas.paste(face, (190, 72))


def labels(draw, t):
    if t < 4: text = "第一人称 → 玩家身后上方"
    elif t < 6: text = "未激活的黑曜石传送门特写"
    elif t < 13: text = "14 个黑曜石块拆分、漂浮成圆面边框"
    elif t < 16: text = "镜头切到玩家面部"
    elif t < 20: text = "黑色与紫色拖影汇聚"
    elif t < 24: text = "蜘蛛网状空间裂隙扩大"
    elif t < 27: text = "虚空传送门破开 / 空间震动"
    elif t < 34: text = "域外魔导师踏出虚空"
    elif t < 38: text = "回望传送门 / 左手小法阵"
    elif t < 41: text = "表情特写定格 3 秒"
    else: text = "镜头回归玩家"
    draw.text((18, 14), "黑暗次元祭品 · 域外魔导师召唤预览", font=FONT, fill=(238, 225, 255))
    draw.text((18, 40), text, font=SMALL, fill=(190, 160, 230))
    draw.text((18, H - 25), f"{t:05.1f}s / 41.0s  ·  离线分镜预览", font=TINY, fill=(170, 150, 190))
    x0, y = 170, H - 21
    segments = [(4, (88, 57, 150)), (2, (108, 54, 160)), (7, (132, 51, 180)), (3, (160, 50, 191)),
                (4, (184, 47, 201)), (4, (203, 44, 215)), (3, (226, 39, 226)), (7, (145, 43, 200)),
                (4, (112, 47, 175)), (3, (205, 72, 230))]
    total = sum(n for n, _ in segments)
    cur = x0
    for n, col in segments:
        w = 450 * n / total
        draw.rectangle((cur, y, cur + w, y + 5), fill=col)
        cur += w
    marker = x0 + 450 * min(t, TOTAL) / TOTAL
    draw.ellipse((marker - 3, y - 2, marker + 3, y + 8), fill=(255, 240, 255))


def render_frame(t):
    canvas = background(t)
    draw = ImageDraw.Draw(canvas, "RGBA")
    if t < 6:
        # A simple player silhouette makes the first camera move readable.
        draw.ellipse((295, 150, 345, 200), fill=(145, 120, 165, 190))
        draw.rectangle((300, 198, 340, 265), fill=(56, 34, 83, 210))
        draw.line((320, 264, 305, 310), fill=(116, 75, 150, 220), width=7)
        draw.line((320, 264, 337, 310), fill=(116, 75, 150, 220), width=7)
        portal(draw, 6.0 if t > 4 else 0.0, center=(430, 195), scale=.72, alpha=230)
    elif t < 13:
        portal(draw, t)
    elif t < 16:
        draw.ellipse((270, 88, 370, 188), fill=(190, 170, 200, 230), outline=(214, 99, 255, 230), width=3)
        draw.arc((292, 130, 314, 148), 190, 340, fill=(26, 10, 36, 230), width=3)
        draw.arc((326, 130, 348, 148), 200, 350, fill=(26, 10, 36, 230), width=3)
        draw.arc((296, 142, 344, 176), 15, 165, fill=(85, 20, 110, 230), width=3)
    elif t < 20:
        portal(draw, 13)
        trails(draw, t)
    elif t < 27:
        portal(draw, 13, alpha=110)
        rift(draw, t)
    else:
        portal(draw, 13, alpha=100 if t < 34 else 0)
        if t < 34:
            rift(draw, 26.9)
        model_panel(canvas, t)
    labels(draw, t)
    return canvas


def main():
    frames = [render_frame(i / FPS) for i in range(int(TOTAL * FPS) + 1)]
    webp = OUT / "dark-dimensional-offering-preview.webp"
    gif = OUT / "dark-dimensional-offering-preview.gif"
    durations = [round(1000 / FPS)] * len(frames)
    frames[0].save(webp, save_all=True, append_images=frames[1:], duration=durations, loop=0, quality=82, method=4)
    frames[0].save(gif, save_all=True, append_images=frames[1:], duration=durations, loop=0, optimize=False)
    # A contact sheet helps inspect the complete storyboard without playing it.
    sheet = Image.new("RGB", (960, 540), (8, 3, 18))
    for i, t in enumerate([0, 6, 13, 16, 20, 24, 27, 34, 38, 41]):
        im = render_frame(min(t, 40.99)).resize((240, 135), Image.Resampling.LANCZOS)
        sheet.paste(im, ((i % 4) * 240, (i // 4) * 180))
    sheet.save(OUT / "dark-dimensional-offering-storyboard.png")
    print(webp)
    print(gif)
    print(OUT / "dark-dimensional-offering-storyboard.png")


if __name__ == "__main__":
    main()
