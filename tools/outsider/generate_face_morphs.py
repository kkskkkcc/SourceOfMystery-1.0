"""Export the useful face vertex morphs from the supplied PMX model.

The game renderer keeps this small sparse morph set separate from the skinned
mesh. Group morphs are flattened recursively so the Java side only has to add
vertex offsets before applying the existing bone matrices.
"""
from __future__ import annotations

import struct
from pathlib import Path

from pymeshio.pmx import reader

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "mmd模型" / "林德薇恩v1.04_by_元空Ame_1b22ac0572fa447c72945fd8e226f0ab" / "林德薇恩v1.04"
OUT = ROOT / "src" / "main" / "resources" / "assets" / "sourceofmystery" / "meshes" / "entity" / "outsider_face.bin"

# The channel order is shared by the baked phase tracks and the game renderer.
NAMES = ["まばたき", "にこり", "びっくり", "真面目", "ウィンク", "ウィンク右",
         "にやり", "あ", "お", "怒り目", "じと目", "怒り"]


def flatten(model, index, scale=1.0, out=None, visiting=None):
    if out is None:
        out = {}
    if visiting is None:
        visiting = set()
    if index in visiting:
        raise ValueError("cyclic PMX morph group")
    morph = model.morphs[index]
    if morph.morph_type == 1:  # vertex morph
        for item in morph.offsets:
            p = item.position_offset
            d = out.setdefault(item.vertex_index, [0.0, 0.0, 0.0])
            d[0] += float(p[0]) * scale
            d[1] += float(p[1]) * scale
            d[2] += float(p[2]) * scale
    elif morph.morph_type == 0:  # group morph
        visiting.add(index)
        for item in morph.offsets:
            flatten(model, item.morph_index, scale * float(item.value), out, visiting)
        visiting.remove(index)
    return out


def main():
    model = reader.read_from_file(str(SOURCE / "林德薇恩v1.04.pmx"))
    by_name = {str(m.name): i for i, m in enumerate(model.morphs)}
    with OUT.open("wb") as stream:
        stream.write(struct.pack("<5I", 0x4F554D31, 1, len(model.vertices), len(NAMES), 0))
        for morph_id, name in enumerate(NAMES):
            values = flatten(model, by_name[name])
            stream.write(struct.pack("<2I", morph_id, len(values)))
            for vertex, delta in sorted(values.items()):
                stream.write(struct.pack("<I3f", vertex, *delta))
        print(f"wrote {OUT} ({len(NAMES)} morphs)")


if __name__ == "__main__":
    main()
