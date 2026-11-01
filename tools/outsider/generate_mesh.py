"""Convert the supplied PMX model into the compact mesh used by the test entity.

Preserves the source UVs, material texture references and skin weights.
Animation baking is handled separately by bake_animation.py.
"""
from __future__ import annotations

import shutil
import struct
from pathlib import Path

from pymeshio.pmx import reader


ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "mmd模型" / "林德薇恩v1.04_by_元空Ame_1b22ac0572fa447c72945fd8e226f0ab" / "林德薇恩v1.04"
OUT = ROOT / "src" / "main" / "resources" / "assets" / "sourceofmystery"
MESH_OUT = OUT / "meshes" / "entity" / "outsider.bin"
TEX_OUT = OUT / "textures" / "entity" / "outsider"
ANIM_OUT = OUT / "animations" / "entity" / "outsider"
REFERENCE = Path(__file__).resolve().parent / "reference"

CONTROL_BONES = ["頭", "左腕", "右腕", "左足", "右足", "左ひざ", "右ひざ", "上半身"]


def copy_textures() -> None:
    TEX_OUT.mkdir(parents=True, exist_ok=True)
    for source_name, target_name in {
        "textures/Lindverne_Face_D.png": "face.png",
        "textures/Lindverne_Body_Map1_D.png": "body_map1.png",
        "textures/Lindverne_Body_Map2_D.png": "body_map2.png",
    }.items():
        shutil.copyfile(SOURCE / source_name, TEX_OUT / target_name)


def write_mesh() -> None:
    model = reader.read_from_file(str(SOURCE / "林德薇恩v1.04.pmx"))
    MESH_OUT.parent.mkdir(parents=True, exist_ok=True)

    # Header: magic, version, vertex count, index count, material-group count.
    with MESH_OUT.open("wb") as stream:
        stream.write(struct.pack("<5I", 0x4F555431, 3, len(model.vertices), len(model.indices), len(model.materials)))
        for vertex in model.vertices:
            p = vertex.position
            n = vertex.normal
            uv = vertex.uv
            stream.write(struct.pack(
                "<8f", float(p[0]), float(p[1]), float(p[2]),
                float(n[0]), float(n[1]), float(n[2]),
                float(uv[0]), float(uv[1]),
            ))
            deform = vertex.deform
            if deform.__class__.__name__ == "Bdef1":
                bone_indices = [deform.index0, 0, 0, 0]
                weights = [1.0, 0.0, 0.0, 0.0]
            elif deform.__class__.__name__ == "Bdef2":
                bone_indices = [deform.index0, deform.index1, 0, 0]
                weights = [float(deform.weight0), 1.0 - float(deform.weight0), 0.0, 0.0]
            elif deform.__class__.__name__ == "Sdef":
                bone_indices = [deform.index0, deform.index1, 0, 0]
                weights = [float(deform.weight0), 1.0 - float(deform.weight0), 0.0, 0.0]
            else:
                bone_indices = [deform.index0, deform.index1, deform.index2, deform.index3]
                weights = [float(deform.weight0), float(deform.weight1), float(deform.weight2), float(deform.weight3)]
            assert all(w >= 0 for w in weights) and abs(sum(weights) - 1) < 1e-4
            assert all(0 <= b < len(model.bones) for b,w in zip(bone_indices, weights) if w > 0)
            stream.write(struct.pack("<4i4f", *bone_indices, *weights))

        start = 0
        for material_index, material in enumerate(model.materials):
            count = int(material.vertex_count)
            # Slots in OutsiderRenderer.TEXTURES, resolved from PMX references.
            filename = str(model.textures[material.texture_index]).replace('\\', '/').split('/')[-1]
            slot = {"Lindverne_Face_D.png": 0, "Lindverne_Body_Map2_D.png": 1,
                    "Lindverne_Body_Map1_D.png": 2}[filename]
            assert count % 3 == 0
            stream.write(struct.pack("<3I", slot, start, count))
            start += count

        stream.write(struct.pack("<I", len(model.bones)))
        for bone in model.bones:
            stream.write(struct.pack(
                "<i3f", int(bone.parent_index), float(bone.position[0]),
                float(bone.position[1]), float(bone.position[2]),
            ))
        lookup = {str(bone.name): i for i, bone in enumerate(model.bones)}
        stream.write(struct.pack("<I", len(CONTROL_BONES)))
        stream.write(struct.pack(f"<{len(CONTROL_BONES)}i", *[lookup.get(name, -1) for name in CONTROL_BONES]))

        stream.write(struct.pack(f"<{len(model.indices)}I", *[int(index) for index in model.indices]))



if __name__ == "__main__":
    copy_textures()
    model = reader.read_from_file(str(SOURCE / "林德薇恩v1.04.pmx"))
    write_mesh()
    from bake_animation import bake_all
    bake_all(model, REFERENCE, ANIM_OUT)
    print(f"wrote {MESH_OUT}")
