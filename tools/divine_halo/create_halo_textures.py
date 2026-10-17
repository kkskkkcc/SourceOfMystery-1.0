from __future__ import annotations

import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont, ImageFilter


SIZE = 256
SCALE = 4
ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "src/main/resources/assets/sourceofmystery/textures/entity"


def xy(cx: float, cy: float, radius: float, angle: float) -> tuple[float, float]:
    return cx + radius * math.cos(angle), cy + radius * math.sin(angle)


def draw_arc(draw: ImageDraw.ImageDraw, box: tuple[float, float, float, float], start: float,
             end: float, fill: tuple[int, int, int, int], width: int) -> None:
    draw.arc(box, start=start, end=end, fill=fill, width=width)


def draw_ring(index: int) -> None:
    image = Image.new("RGBA", (SIZE * SCALE, SIZE * SCALE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    cx = cy = SIZE * SCALE / 2
    outer = (112 + index * 2) * SCALE
    inner = (88 - index * 2) * SCALE
    line = (235, 242, 255, 150)
    white = (255, 255, 255, 215)
    black = (8, 10, 18, 205)
    faint = (178, 192, 220, 105)

    # Broken outer and inner circles. The intentional gaps make the ring read as a magic array.
    for radius, width in ((outer, 2), (inner, 2)):
        box = (cx - radius, cy - radius, cx + radius, cy + radius)
        for segment in range(12):
            start = segment * 30 + 4 + index * 3
            end = segment * 30 + 24 - index * 2
            color = white if (segment + index) % 2 == 0 else black
            draw_arc(draw, box, start, end, color, width * SCALE)

    # Alternating segmented bands and radial marks.
    band_radius = (outer + inner) / 2
    half_band = (outer - inner) / 2
    for segment in range(16):
        start = math.radians(segment * 22.5 + 2 + index * 4)
        end = math.radians(segment * 22.5 + 16 - index)
        color = white if (segment + index) % 2 == 0 else black
        draw_arc(draw, (cx - band_radius, cy - band_radius, cx + band_radius, cy + band_radius),
                 math.degrees(start), math.degrees(end), color, max(2, int(half_band * 0.24)))

        # A small triangle/chevron in each active sector gives the ring a ritual-array pattern.
        mid = (start + end) / 2
        p1 = xy(cx, cy, inner + 4 * SCALE, start)
        p2 = xy(cx, cy, outer - 4 * SCALE, mid)
        p3 = xy(cx, cy, inner + 4 * SCALE, end)
        draw.line((p1, p2, p3), fill=faint, width=max(1, SCALE // 2), joint="curve")

    # Small dots on the inner orbit, with four larger cardinal seals.
    for dot in range(24):
        angle = math.radians(dot * 15 + index * 7)
        radius = inner + 8 * SCALE
        x, y = xy(cx, cy, radius, angle)
        size = (2.0 if dot % 6 else 3.5) * SCALE
        color = white if (dot + index) % 2 == 0 else black
        draw.ellipse((x - size, y - size, x + size, y + size), fill=color)

    # A faint offset circle makes the white parts translucent instead of a solid plate.
    offset = (index - 1.5) * 2 * SCALE
    draw.ellipse((cx - outer + offset, cy - outer, cx + outer + offset, cy + outer),
                 outline=(220, 230, 255, 70), width=SCALE)
    image.resize((SIZE, SIZE), Image.Resampling.LANCZOS).save(OUT / f"divine_blessing_halo_ring_{index}.png")


def draw_center() -> None:
    image = Image.new("RGBA", (SIZE * SCALE, SIZE * SCALE), (0, 0, 0, 0))
    glow = Image.new("RGBA", image.size, (0, 0, 0, 0))
    glow_draw = ImageDraw.Draw(glow)
    cx = cy = SIZE * SCALE / 2
    glow_draw.ellipse((cx - 52 * SCALE, cy - 52 * SCALE, cx + 52 * SCALE, cy + 52 * SCALE),
                      fill=(235, 245, 255, 60))
    glow = glow.filter(ImageFilter.GaussianBlur(10 * SCALE))
    image.alpha_composite(glow)

    draw = ImageDraw.Draw(image)
    radius = 46 * SCALE
    draw.ellipse((cx - radius, cy - radius, cx + radius, cy + radius),
                 outline=(255, 255, 255, 175), width=2 * SCALE)
    draw.ellipse((cx - 38 * SCALE, cy - 38 * SCALE, cx + 38 * SCALE, cy + 38 * SCALE),
                 outline=(12, 16, 24, 150), width=2 * SCALE)

    font_path = Path("C:/Windows/Fonts/simhei.ttf")
    font = ImageFont.truetype(str(font_path), 84 * SCALE)
    glyph = "神"
    bbox = draw.textbbox((0, 0), glyph, font=font, stroke_width=0)
    x = cx - (bbox[2] - bbox[0]) / 2 - bbox[0]
    y = cy - (bbox[3] - bbox[1]) / 2 - bbox[1] - 4 * SCALE
    # Dark outline keeps the character legible over the layered rings.
    draw.text((x, y), glyph, font=font, fill=(255, 255, 255, 245),
              stroke_width=3 * SCALE, stroke_fill=(6, 8, 14, 220))
    draw.text((x, y), glyph, font=font, fill=(255, 255, 255, 255))
    image.resize((SIZE, SIZE), Image.Resampling.LANCZOS).save(OUT / "divine_blessing_halo_center.png")


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    for ring_index in range(4):
        draw_ring(ring_index)
    draw_center()
