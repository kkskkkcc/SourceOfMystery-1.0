"""Shared builder for GeckoLib gear: geometry, per-face UV packing, painted atlas and exports.

A design script creates a Model, adds bones and parts, then calls Model.export().
Every visible face receives its own UV island, so each surface is painted for its
real size instead of sharing a tiled swatch.
"""
from __future__ import annotations

import json
import math
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np
from PIL import Image

from paint import paint_face

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'src/main/resources/assets/sourceofmystery'
FACES = ('north', 'south', 'east', 'west', 'up', 'down')


@dataclass
class Part:
    name: str
    bone: str
    center: tuple
    size: tuple
    material: str
    rotation: tuple = (0, 0, 0)
    pivot: tuple | None = None
    hide: tuple = ()
    decals: dict = field(default_factory=dict)
    options: dict = field(default_factory=dict)
    density: float | None = None
    uv: dict = field(default_factory=dict)


@dataclass
class Bone:
    name: str
    pivot: tuple
    parent: str | None = None
    rotation: tuple = (0, 0, 0)


class Model:
    def __init__(self, identifier: str, density: float = 32, atlas_width: int = 2048,
                 bounds=(3, 3, 1.5)):
        self.identifier = identifier
        self.density = density
        self.atlas_width = atlas_width
        self.bounds = bounds
        self.bones: dict[str, Bone] = {}
        self.parts: list[Part] = []
        self.atlas_height = 0

    # ---------- geometry ----------
    def bone(self, name, pivot, parent=None, rotation=(0, 0, 0)):
        self.bones[name] = Bone(name, tuple(pivot), parent, tuple(rotation))
        return name

    def box(self, name, bone, center, size, material, rotation=(0, 0, 0), pivot=None,
            hide=(), decals=None, density=None, **options):
        size = tuple(max(1e-3, float(s)) for s in size)
        # Designs use right-handed rotations in geo space. GeckoLib mirrors X and
        # negates X/Y, so the file value that produces that rotation is (-x, y, -z).
        rotation = (-rotation[0], rotation[1], -rotation[2])
        part = Part(name, bone, tuple(map(float, center)), size, material, tuple(rotation),
                    None if pivot is None else tuple(pivot), tuple(hide), dict(decals or {}),
                    options, density)
        self.parts.append(part)
        return part

    def mirror(self, fn):
        """Call fn(side) for side -1 (model right, -X) and +1 (model left, +X)."""
        for side in (-1, 1):
            fn(side)

    def ring(self, name, bone, center, radius, segments, width, depth, material, plane='xy',
             start=0.0, sweep=360.0, tilt=(0, 0, 0), **options):
        """Polygonal ring of tangent segments lying in the given plane."""
        parts = []
        step = math.radians(sweep) / segments
        for i in range(segments):
            a = math.radians(start) + (i + .5) * step
            seg = 2 * radius * math.tan(step / 2) * 1.04
            c, s = math.cos(a), math.sin(a)
            if plane == 'xy':
                pos = (center[0] + radius * c, center[1] + radius * s, center[2])
                rot = (tilt[0], tilt[1], math.degrees(a) + 90 + tilt[2])
                size = (seg, width, depth)
            elif plane == 'xz':
                pos = (center[0] + radius * c, center[1], center[2] + radius * s)
                rot = (tilt[0], -(math.degrees(a) + 90) + tilt[1], tilt[2])
                size = (seg, depth, width)
            else:  # yz
                pos = (center[0], center[1] + radius * s, center[2] + radius * c)
                rot = (-(math.degrees(a) + 90) + tilt[0], tilt[1], tilt[2])
                size = (depth, width, seg)
            parts.append(self.box(f'{name}_{i}', bone, pos, size, material, rot, **options))
        return parts

    # ---------- UV packing ----------
    @staticmethod
    def face_dims(size, face):
        x, y, z = size
        return {'north': (x, y), 'south': (x, y), 'east': (z, y), 'west': (z, y),
                'up': (x, z), 'down': (x, z)}[face]

    def pack(self):
        islands = []
        for part in self.parts:
            density = part.density or self.density
            for face in FACES:
                if face in part.hide:
                    continue
                w, h = self.face_dims(part.size, face)
                pw, ph = max(2, math.ceil(w * density)), max(2, math.ceil(h * density))
                islands.append((ph, pw, part, face))
        islands.sort(key=lambda item: (-item[0], -item[1]))
        pad, x, y, row = 2, 0, 0, 0
        for ph, pw, part, face in islands:
            if x + pw + pad * 2 > self.atlas_width:
                x, y, row = 0, y + row, 0
            part.uv[face] = (x + pad, y + pad, pw, ph)
            x += pw + pad * 2
            row = max(row, ph + pad * 2)
        height = y + row
        # Any height is valid for entity textures; trimming saves video memory.
        self.atlas_height = max(64, math.ceil(height / 64) * 64)
        if self.atlas_height > 8192:
            raise ValueError(f'{self.identifier}: atlas too tall ({height}px); lower density')

    # ---------- painting ----------
    def paint(self, seed=1):
        albedo = np.zeros((self.atlas_height, self.atlas_width, 4), np.float32)
        glow = np.zeros((self.atlas_height, self.atlas_width, 4), np.float32)
        rng = np.random.default_rng(seed)
        for index, part in enumerate(self.parts):
            for face, (u, v, w, h) in part.uv.items():
                fw, fh = self.face_dims(part.size, face)
                color, light = paint_face(part.material, w, h, face, fw, fh, part.decals.get(face),
                                          part.options, rng, index)
                # Pad each island by edge extension to stop mip/filter bleeding.
                pad = 2
                big = np.pad(color, ((pad, pad), (pad, pad), (0, 0)), mode='edge')
                albedo[v - pad:v + h + pad, u - pad:u + w + pad] = big
                if light is not None:
                    glow[v:v + h, u:u + w] = light
        return albedo, glow

    # ---------- exports ----------
    def geo(self):
        bones = []
        for bone in self.bones.values():
            entry = {'name': bone.name, 'pivot': list(bone.pivot)}
            if bone.parent:
                entry['parent'] = bone.parent
            if any(bone.rotation):
                entry['rotation'] = list(bone.rotation)
            cubes = []
            for part in self.parts:
                if part.bone != bone.name:
                    continue
                origin = [round(part.center[i] - part.size[i] / 2, 4) for i in range(3)]
                cube = {'origin': origin, 'size': [round(s, 4) for s in part.size], 'uv': {}}
                if any(part.rotation):
                    cube['pivot'] = [round(c, 4) for c in (part.pivot or part.center)]
                    cube['rotation'] = [round(r, 3) for r in part.rotation]
                for face, (u, v, w, h) in part.uv.items():
                    cube['uv'][face] = {'uv': [u, v], 'uv_size': [w, h]}
                cubes.append(cube)
            if cubes:
                entry['cubes'] = cubes
            bones.append(entry)
        return {'format_version': '1.12.0', 'minecraft:geometry': [{
            'description': {'identifier': 'geometry.' + self.identifier,
                            'texture_width': self.atlas_width, 'texture_height': self.atlas_height,
                            'visible_bounds_width': self.bounds[0], 'visible_bounds_height': self.bounds[1],
                            'visible_bounds_offset': [0, self.bounds[2], 0]},
            'bones': bones}]}

    def export(self, geo_path, texture_path, glow_path=None, seed=1, manifest_path=None):
        self.pack()
        albedo, glow = self.paint(seed)
        write_json(geo_path, self.geo())
        save_png(albedo, texture_path)
        if glow_path is not None:
            # GeckoLib requires the glow mask to match the base texture size.
            img = Image.fromarray(np.clip(glow, 0, 255).astype(np.uint8), 'RGBA')
            Path(glow_path).parent.mkdir(parents=True, exist_ok=True)
            img.save(glow_path, optimize=True)
        if manifest_path is not None:
            write_json(manifest_path, {
                'identifier': self.identifier, 'cubes': len(self.parts),
                'faces': sum(len(p.uv) for p in self.parts), 'bones': list(self.bones),
                'atlas': [self.atlas_width, self.atlas_height], 'density': self.density,
                'parts': [{'name': p.name, 'bone': p.bone, 'material': p.material} for p in self.parts]})
        return {'cubes': len(self.parts), 'atlas': (self.atlas_width, self.atlas_height)}


def write_json(path, data):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=1) + '\n', encoding='utf8')


def save_png(array, path):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(np.clip(array, 0, 255).astype(np.uint8), 'RGBA').save(path, optimize=True)
