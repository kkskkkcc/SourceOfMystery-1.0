"""Painted materials and vector decals for gear atlases.

Each face is painted at its true pixel size: material body, bevel light, soft
occlusion, optional trim band, then decals (inlay, engraving, glow lines).
Minecraft already shades faces by direction, so only in-face light is baked.
"""
from __future__ import annotations

import math

import numpy as np
from PIL import Image, ImageDraw
from scipy.ndimage import gaussian_filter

MATERIALS: dict[str, dict] = {}


def register(name, style, base, shade=None, light=None, **extra):
    """style: plate | metal | lacquer | cloth | gem | scale | glow | bone"""
    MATERIALS[name] = {'style': style, 'base': np.array(base, np.float32),
                       'shade': np.array(shade if shade else [c * .55 for c in base], np.float32),
                       'light': np.array(light if light else [min(255, c * 1.25 + 18) for c in base], np.float32),
                       **extra}


def rgb(hex_value):
    hex_value = hex_value.lstrip('#')
    return [int(hex_value[i:i + 2], 16) for i in (0, 2, 4)]


# ---------------- noise ----------------
def fbm(h, w, rng, scale=12.0, octaves=4, aspect=(1, 1)):
    out = np.zeros((h, w), np.float32)
    amp, total = 1.0, 0.0
    for o in range(octaves):
        s = max(.6, scale / (2 ** o))
        field = rng.normal(0, 1, (h, w)).astype(np.float32)
        out += amp * gaussian_filter(field, (s * aspect[0], s * aspect[1]), mode='wrap')
        total += amp
        amp *= .5
    out /= total
    std = out.std() or 1
    return out / std


def coords(h, w):
    y, x = np.mgrid[:h, :w].astype(np.float32)
    return (x + .5) / w, (y + .5) / h


def lerp(a, b, t):
    t = np.asarray(t, np.float32)[..., None]
    return a * (1 - t) + b * t


# ---------------- bevel and occlusion ----------------
def bevel(h, w, width):
    """Return (light, dark) masks for a raised rim: lit top-left, shaded bottom-right."""
    x, y = coords(h, w)
    px, py = x * w, y * h
    width = max(1.0, width)
    top = np.clip(1 - py / width, 0, 1)
    left = np.clip(1 - px / width, 0, 1)
    bottom = np.clip(1 - (h - py) / width, 0, 1)
    right = np.clip(1 - (w - px) / width, 0, 1)
    light = np.maximum(top, left * .7)
    dark = np.maximum(bottom, right * .8)
    return light ** 1.5, dark ** 1.3


def edge_distance(h, w):
    x, y = coords(h, w)
    return np.minimum(np.minimum(x * w, (1 - x) * w), np.minimum(y * h, (1 - y) * h))


# ---------------- material bodies ----------------
def body(mat, h, w, face, fw, fh, rng, options):
    style = mat['style']
    base, shade, light = mat['base'], mat['shade'], mat['light']
    x, y = coords(h, w)
    vertical = y if face not in ('up', 'down') else np.full_like(y, .5 if face == 'up' else .8)
    if options.get('grad') == 'x':
        # Across-the-width gradient keeps stacked segments (blades) seamless.
        vertical = np.abs(x - .5) * 2 * .9 + .05
        y = np.full_like(y, .5)
    if style == 'plate':
        n = fbm(h, w, rng, scale=max(4, w / 6))
        veins = np.abs(fbm(h, w, rng, scale=max(3, w / 10), aspect=(1, .6)))
        t = .18 + .45 * vertical + .07 * n
        col = lerp(light, shade, np.clip(t, 0, 1) * .55)
        col = col - (np.exp(-veins * mat.get('vein_sharp', 6)) * mat.get('vein', 14))[..., None]
        # A soft broad highlight as if the plate is gently convex.
        dome = np.exp(-(((x - .38) / .45) ** 2 + ((y - .3) / .5) ** 2))
        col = col + (dome * mat.get('gloss', 22))[..., None]
    elif style == 'metal':
        streak = fbm(h, w, rng, scale=max(2, w / 4), octaves=3, aspect=(.15, 2.5))
        band = np.exp(-((vertical - .32) / .16) ** 2) * .9 + np.exp(-((vertical - .78) / .1) ** 2) * .35
        t = np.clip(.55 - .5 * band + .35 * vertical + .06 * streak, 0, 1)
        col = lerp(light, shade, t)
        col = col + (np.clip(band - .55, 0, 1) * 70)[..., None]
    elif style == 'lacquer':
        n = fbm(h, w, rng, scale=max(5, w / 4))
        sheen = np.exp(-(((x + y * .7) - .55) / .16) ** 2)
        col = lerp(base, shade, np.clip(.35 * vertical + .12 * n, 0, 1))
        col = col + (sheen * mat.get('gloss', 26))[..., None]
    elif style == 'cloth':
        scale = mat.get('weave', 3.0)
        weave = (np.sin(x * w * math.pi / scale) * np.sin(y * h * math.pi / scale)) * .5
        folds = fbm(h, w, rng, scale=max(4, w / 5), aspect=(2.5, .4))
        col = lerp(base, shade, np.clip(.25 + .35 * vertical + .18 * folds, 0, 1))
        col = col + (weave * 7)[..., None]
    elif style == 'gem':
        cx, cy = x - .5, y - .5
        r = np.sqrt(cx * cx + cy * cy) * 2
        facet = np.abs(np.sin(np.arctan2(cy, cx) * mat.get('facets', 3)))
        col = lerp(light, shade, np.clip(r * .8 + facet * .25, 0, 1))
        col = col + (np.exp(-((cx + .18) ** 2 + (cy + .2) ** 2) / .01) * 120)[..., None]
    elif style == 'scale':
        rows = max(2, round(fh * options.get('scale_rows', 1.4)))
        cols = max(2, round(fw * options.get('scale_cols', 1.2)))
        sy = y * rows
        sx = x * cols + (np.floor(sy) % 2) * .5
        fx, fy = sx % 1 - .5, sy % 1
        arc = np.sqrt(fx * fx * 1.6 + (fy - .05) ** 2)
        inside = np.clip(1 - arc, 0, 1)
        rim = np.exp(-((arc - .55) / .07) ** 2)
        col = lerp(shade, light, np.clip(inside * 1.25, 0, 1))
        col = col + (rim * 45)[..., None] - (np.clip(fy - .75, 0, 1) * 160)[..., None]
    elif style == 'bone':
        n = fbm(h, w, rng, scale=max(3, w / 8), aspect=(.4, 2))
        col = lerp(light, shade, np.clip(.2 + .5 * vertical + .12 * n, 0, 1))
    else:  # glow
        col = lerp(light, base, np.clip(np.abs(y - .5) * 2, 0, 1))
    return col


# ---------------- vector decals ----------------
SS = 4


class Canvas:
    """Supersampled drawing in face-relative coordinates (0..1)."""

    def __init__(self, h, w):
        self.h, self.w = h, w
        self.img = Image.new('L', (w * SS, h * SS), 0)
        self.draw = ImageDraw.Draw(self.img)

    def p(self, x, y):
        return (x * self.w * SS, y * self.h * SS)

    def line(self, pts, width):
        self.draw.line([self.p(*q) for q in pts], fill=255, width=max(1, int(width * SS)), joint='curve')

    def poly(self, pts):
        self.draw.polygon([self.p(*q) for q in pts], fill=255)

    def ellipse(self, cx, cy, rx, ry, width=None):
        box = [self.p(cx - rx, cy - ry), self.p(cx + rx, cy + ry)]
        if width is None:
            self.draw.ellipse(box, fill=255)
        else:
            self.draw.ellipse(box, outline=255, width=max(1, int(width * SS)))

    def arc(self, cx, cy, rx, ry, a0, a1, width):
        box = [self.p(cx - rx, cy - ry), self.p(cx + rx, cy + ry)]
        self.draw.arc(box, a0, a1, fill=255, width=max(1, int(width * SS)))

    def mask(self):
        return np.asarray(self.img.resize((self.w, self.h), Image.Resampling.BOX), np.float32) / 255


def spiral(c: Canvas, cx, cy, r, turns, width, direction=1, aspect=1.0):
    pts = []
    for i in range(80):
        t = i / 79
        a = direction * t * turns * math.tau
        rr = r * (1 - .82 * t)
        pts.append((cx + rr * math.cos(a) * aspect, cy + rr * math.sin(a)))
    c.line(pts, width)


def draw_cloud(c: Canvas, cx, cy, s, width, flip=1):
    """Auspicious cloud: three curling lobes on a sweeping tail."""
    a = c.w / max(1, c.h)
    sx = s / a
    spiral(c, cx, cy, s * .32, 1.15, width, flip, 1 / a)
    spiral(c, cx - flip * sx * .42, cy + s * .1, s * .24, 1.0, width, -flip, 1 / a)
    spiral(c, cx + flip * sx * .38, cy + s * .14, s * .2, 1.0, width, flip, 1 / a)
    tail = [(cx - flip * sx * (.7 - .1 * i), cy + s * (.32 + .03 * math.sin(i))) for i in range(10)]
    tail += [(cx + flip * sx * (.2 + .09 * i), cy + s * (.36 - .022 * i * i)) for i in range(9)]
    c.line(tail, width)


def decal_mask(spec, h, w, fw, fh, rng):
    kind = spec['kind']
    c = Canvas(h, w)
    lw = spec.get('width', .045)  # in face-units (fraction of the shorter side)
    unit = min(fw, fh) or 1
    stroke = lw * min(c.w, c.h) / max(c.w, c.h) if False else lw
    sw = stroke * min(w, h) / max(1, 1)  # pixel width at 1x
    def px(v):  # convert fraction of min side to pixels at 1x
        return v * min(w, h)
    ax, ay = min(w, h) / w, min(w, h) / h  # keep round motifs round on oblong faces
    if kind == 'border':
        inset = spec.get('inset', .08)
        ix, iy = inset * min(fw, fh) / fw, inset * min(fw, fh) / fh
        c.line([(ix, iy), (1 - ix, iy), (1 - ix, 1 - iy), (ix, 1 - iy), (ix, iy)], px(lw))
    elif kind == 'fret':
        # Key-fret (回纹) band: square spirals hanging from a continuous baseline.
        y0, y1 = spec.get('y0', 0.0), spec.get('y1', 1.0)
        band_h = (y1 - y0) * fh
        n = max(1, round(fw / max(1e-3, band_h) * spec.get('density', 1.0)))
        cw = 1 / n
        Y = lambda t: y0 + (y1 - y0) * t
        c.line([(0, Y(.92)), (1, Y(.92))], px(lw))
        for i in range(n):
            a = i * cw
            q = [(a + .1 * cw, Y(.92)), (a + .1 * cw, Y(.1)), (a + .9 * cw, Y(.1)), (a + .9 * cw, Y(.72)),
                 (a + .35 * cw, Y(.72)), (a + .35 * cw, Y(.32)), (a + .66 * cw, Y(.32)), (a + .66 * cw, Y(.52))]
            c.line(q, px(lw))
    elif kind == 'cloud':
        for (cx, cy, s, flip) in spec.get('at', [(.5, .5, .7, 1)]):
            draw_cloud(c, cx, cy, s, px(lw), flip)
    elif kind == 'rays':
        n, r0, r1 = spec.get('count', 16), spec.get('r0', .2), spec.get('r1', .48)
        for i in range(n):
            a = (i + .5) / n * math.tau
            long = 1 if i % 2 == 0 else .78
            q = [(.5 + r0 * math.cos(a) * ax, .5 + r0 * math.sin(a) * ay),
                 (.5 + r1 * long * math.cos(a) * ax, .5 + r1 * long * math.sin(a) * ay)]
            c.line(q, px(lw))
    elif kind == 'circle':
        c.ellipse(.5, .5, spec.get('r', .45) * ax, spec.get('r', .45) * ay, None if spec.get('fill') else px(lw))
    elif kind == 'taiji':
        r = spec.get('r', .5)
        img = np.zeros((h * SS, w * SS), np.float32)
        yy, xx = np.mgrid[:h * SS, :w * SS].astype(np.float32)
        tx = ((xx + .5) / (w * SS) - .5) / (r * ax) * 2
        ty = ((yy + .5) / (h * SS) - .5) / (r * ay) * 2
        inside = tx * tx + ty * ty <= 1
        white = tx >= 0
        white = np.where(tx * tx + (ty + .5) ** 2 <= .25, True, white)
        white = np.where(tx * tx + (ty - .5) ** 2 <= .25, False, white)
        white = np.where(tx * tx + (ty + .5) ** 2 <= .13 ** 2, False, white)
        white = np.where(tx * tx + (ty - .5) ** 2 <= .13 ** 2, True, white)
        img = (inside & white).astype(np.float32)
        return np.asarray(Image.fromarray((img * 255).astype(np.uint8)).resize((w, h), Image.Resampling.BOX), np.float32) / 255, \
            np.asarray(Image.fromarray((inside * 255).astype(np.uint8)).resize((w, h), Image.Resampling.BOX), np.float32) / 255
    elif kind == 'trigrams':
        r = spec.get('r', .42)
        for i in range(8):
            a = i / 8 * math.tau - math.pi / 2
            code = [(i >> k) & 1 for k in range(3)]
            for k, solid in enumerate(code):
                rr = r - k * .045
                ux, uy = -math.sin(a) * ax, math.cos(a) * ay
                cx, cy = .5 + rr * math.cos(a) * ax, .5 + rr * math.sin(a) * ay
                half = .065
                if solid:
                    c.line([(cx - ux * half, cy - uy * half), (cx + ux * half, cy + uy * half)], px(lw))
                else:
                    for sgn in (-1, 1):
                        c.line([(cx + sgn * ux * half * .25, cy + sgn * uy * half * .25),
                                (cx + sgn * ux * half, cy + sgn * uy * half)], px(lw))
    elif kind == 'rivets':
        for (cx, cy) in spec.get('at', []):
            r = spec.get('r', .06)
            c.ellipse(cx, cy, r * min(fw, fh) / fw, r * min(fw, fh) / fh)
    elif kind == 'lines':
        for pts in spec['paths']:
            c.line(pts, px(lw))
    elif kind == 'polys':
        for pts in spec['shapes']:
            c.poly(pts)
    elif kind == 'runes':
        local = np.random.default_rng(spec.get('seed', 3))
        n = spec.get('count', 5)
        horizontal = fw >= fh
        for i in range(n):
            t0 = (i + .2) / n
            t1 = (i + .8) / n
            strokes = []
            for _ in range(3):
                a = (local.uniform(t0, t1), local.uniform(.2, .8))
                b = (local.uniform(t0, t1), local.uniform(.2, .8))
                strokes.append([a, b])
            strokes.append([(t0, .5), (t1, .5)] if local.random() < .5 else [((t0 + t1) / 2, .18), ((t0 + t1) / 2, .82)])
            for s in strokes:
                if not horizontal:
                    s = [(y, x) for x, y in s]
                c.line(s, px(lw))
    elif kind == 'veins':
        local = np.random.default_rng(spec.get('seed', 5))
        for _ in range(spec.get('count', 3)):
            x, y = local.uniform(.1, .9), 0.0
            pts = [(x, y)]
            while y < 1:
                y += local.uniform(.08, .16)
                x = np.clip(x + local.uniform(-.12, .12), .05, .95)
                pts.append((x, y))
            c.line(pts, px(lw))
    elif kind == 'leaf':
        for (cx, cy, s, ang) in spec.get('at', [(.5, .5, .8, 0)]):
            pts = []
            for i in range(25):
                t = i / 24
                pts.append((t, math.sin(t * math.pi) * .32))
            pts += [(t, -yv) for t, yv in reversed(pts)]
            ca, sa = math.cos(math.radians(ang)), math.sin(math.radians(ang))
            pts = [(cx + (s * (px_ - .5) * ca - s * py_ * sa) * min(fw, fh) / fw,
                    cy + (s * (px_ - .5) * sa + s * py_ * ca) * min(fw, fh) / fh) for px_, py_ in pts]
            c.poly(pts)
    else:
        raise ValueError('unknown decal ' + kind)
    m = c.mask()
    return m, None


def apply_decal(col, glow, spec, h, w, fw, fh, rng, mat):
    mask, aux = decal_mask(spec, h, w, fw, fh, rng)
    style = spec.get('style', 'inlay')
    if style == 'inlay':
        inlay = MATERIALS[spec.get('material', 'gold')]
        x, y = coords(h, w)
        tone = lerp(inlay['light'], inlay['shade'], np.clip(.25 + .55 * y, 0, 1))
        outline = np.clip(gaussian_filter(mask, max(.6, min(h, w) / 160)) - mask, 0, 1) * 2.2
        col = col * (1 - np.clip(outline, 0, .6))[..., None]
        col = lerp(col, tone, mask)
    elif style == 'engrave':
        depth = spec.get('depth', 70)
        shadow = np.roll(mask, 1, axis=0)
        highlight = np.roll(mask, -1, axis=0)
        col = col - (mask * depth)[..., None] + (np.clip(highlight - mask, 0, 1) * depth * .6)[..., None] \
            - (np.clip(shadow - mask, 0, 1) * depth * .2)[..., None]
    elif style == 'emboss':
        lit = np.clip(mask - np.roll(np.roll(mask, 1, 0), 1, 1), 0, 1)
        dark = np.clip(mask - np.roll(np.roll(mask, -1, 0), -1, 1), 0, 1)
        col = col + (mask * spec.get('lift', 12) + lit * 55 - dark * 55)[..., None]
    elif style == 'paint':
        color = np.array(spec['color'], np.float32)
        col = lerp(col, np.broadcast_to(color, col.shape), mask * spec.get('alpha', 1))
    if style == 'taiji':
        white, inside = mask, aux
        dark = np.array(spec.get('dark', (12, 14, 19)), np.float32)
        light = np.array(spec.get('light', (240, 241, 236)), np.float32)
        x, y = coords(h, w)
        sheen = (np.exp(-(((x - .38) / .3) ** 2 + ((y - .32) / .3) ** 2)) * 26)[..., None]
        disc = lerp(dark + sheen, light - sheen * .3, white)
        col = lerp(col, disc, inside)
    if spec.get('glow'):
        g = np.array(spec['glow'], np.float32)
        a = mask * spec.get('glow_alpha', 1.0)
        glow[..., :3] = np.maximum(glow[..., :3], g * a[..., None])
        glow[..., 3] = np.maximum(glow[..., 3], a * 255)
        col = lerp(col, np.broadcast_to(g, col.shape), a * spec.get('tint', .85))
    return col


def paint_face(material, w, h, face, fw, fh, decals, options, rng, index):
    mat = MATERIALS[material]
    local = np.random.default_rng(int(rng.integers(1 << 30)) + index)
    col = body(mat, h, w, face, fw, fh, local, options)
    glow = np.zeros((h, w, 4), np.float32)
    density = w / max(fw, 1e-3)
    # Raised rim for every plate edge; width relative to the plate, not the atlas.
    bw = options.get('bevel', mat.get('bevel', .12)) * density
    lit, dark = bevel(h, w, bw)
    if options.get('grad') == 'x' and face not in ('up', 'down'):
        xx, _ = coords(h, w)
        lit = np.clip(1 - xx * w / max(bw, 1), 0, 1) ** 1.5
        dark = np.clip(1 - (1 - xx) * w / max(bw, 1), 0, 1) ** 1.3
    col = col + (lit * mat.get('rim_light', 38))[..., None] - (dark * mat.get('rim_dark', 46))[..., None]
    trim = options.get('trim')
    if trim and face in options.get('trim_faces', ('north', 'south', 'east', 'west', 'up', 'down')):
        tmat, tw = MATERIALS[trim[0]], trim[1] * density
        d = edge_distance(h, w)
        band = np.clip((tw - d) / max(1, density * .03), 0, 1)
        x, y = coords(h, w)
        tcol = lerp(tmat['light'], tmat['shade'], np.clip(.15 + .6 * y + .25 * (d / max(tw, 1)), 0, 1))
        inner = np.exp(-((d - tw) / max(1, density * .035)) ** 2)
        col = lerp(col, tcol, band) - (inner * 55)[..., None]
    for spec in (decals or []):
        col = apply_decal(col, glow, spec, h, w, fw, fh, local, mat)
    if mat['style'] == 'glow':
        g = np.clip(col, 0, 255)
        glow[..., :3] = g
        glow[..., 3] = 255
    if options.get('glow_face'):
        glow[..., :3] = np.clip(col, 0, 255)
        glow[..., 3] = 255
    # Gentle occlusion where plates meet the body (bottom of side faces).
    if face not in ('up', 'down') and options.get('ao', True) and options.get('grad') != 'x':
        x, y = coords(h, w)
        col = col * (1 - .16 * np.clip((y - .7) / .3, 0, 1))[..., None]
    alpha = np.full((h, w, 1), 255, np.float32)
    out = np.concatenate([np.clip(col, 0, 255), alpha], axis=-1)
    has_glow = glow[..., 3].max() > 0
    return out, (glow if has_glow else None)
