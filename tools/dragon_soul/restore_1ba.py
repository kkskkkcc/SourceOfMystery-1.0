import copy
import json
import math
import shutil
import sys
from pathlib import Path

import numpy as np


ROOT = Path(__file__).resolve().parents[2]
SRC = Path(r"F:\AAAmc模型自动化\1ba")
AS = ROOT / "src/main/resources/assets/sourceofmystery"
sys.path.insert(0, str(ROOT / "tools/weeping_death_lord"))
from geo_render import affine, mirror, rot_matrix


def write_json(path: Path, value: dict) -> None:
    path.write_text(
        json.dumps(value, ensure_ascii=False, separators=(",", ":")) + "\n",
        encoding="utf-8",
    )


def euler(rotation_matrix: np.ndarray) -> list[float]:
    """Convert the baked rotation matrix to Bedrock's Euler convention."""
    beta = math.asin(float(np.clip(-rotation_matrix[2, 0], -1, 1)))
    if abs(math.cos(beta)) > 1.0e-7:
        alpha = math.atan2(rotation_matrix[2, 1], rotation_matrix[2, 2])
        gamma = math.atan2(rotation_matrix[1, 0], rotation_matrix[0, 0])
    else:
        alpha = math.atan2(-rotation_matrix[1, 2], rotation_matrix[1, 1])
        gamma = 0
    return [-math.degrees(alpha), -math.degrees(beta), math.degrees(gamma)]


# 1ba is the authoritative authored Dragon Soul model. Keep all source geometry
# (face, body, legs, hair, wing and skirt/tail structures) intact.
geometry = json.loads((SRC / "models/main.json").read_text(encoding="utf-8-sig"))
geometry_root = geometry["minecraft:geometry"][0]
geometry_root["description"]["identifier"] = "geometry.dragon_soul"
bones = {bone["name"]: bone for bone in geometry_root["bones"]}

# The source file stores the authored spear in a distant display pivot. Preserve
# every spear cube and UV, but bake its rest pose onto the actual right-hand grip
# so animation clips can move the arm naturally without the weapon orbiting.
held = copy.deepcopy(bones["longspear"])
grip = [-4.125, 19.575, 0.025]
weapon_cubes = []
bone_transform = affine(
    rot_matrix(*held.get("rotation", [0, 0, 0])),
    mirror(held["pivot"]),
)
for cube in held.get("cubes", []):
    transformed = copy.deepcopy(cube)
    cube_transform = affine(
        rot_matrix(*transformed.get("rotation", [0, 0, 0])),
        mirror(transformed.get("pivot", [0, 0, 0])),
    )
    transform = bone_transform @ cube_transform
    center = mirror(np.asarray(transformed["origin"], dtype=float) + np.asarray(transformed["size"], dtype=float) / 2)
    center = (transform @ np.append(center, 1))[:3]
    center = mirror(center)
    transformed["origin"] = (center - np.asarray(transformed["size"], dtype=float) / 2).tolist()
    transformed["pivot"] = center.tolist()
    transformed["rotation"] = euler(transform[:3, :3])
    weapon_cubes.append(transformed)
bones["longspear"].update(
    parent="RightHand",
    pivot=grip,
    rotation=[0, 0, 0],
    cubes=weapon_cubes,
)

write_json(AS / "geo/entity/dragon_soul.geo.json", geometry)
shutil.copyfile(SRC / "textures/default.png", AS / "textures/entity/dragon_soul.png")

# The thrown spear uses the same authored mesh, with its baked hand pose as the
# local rest pose. A projectile has a root pivot at its entity position, so
# subtract the hand grip from every cube while preserving size, UV and rotation.
projectile_cubes = copy.deepcopy(weapon_cubes)
for cube in projectile_cubes:
    cube["origin"] = (np.asarray(cube["origin"], dtype=float) - np.asarray(grip, dtype=float)).tolist()
    if "pivot" in cube:
        cube["pivot"] = (np.asarray(cube["pivot"], dtype=float) - np.asarray(grip, dtype=float)).tolist()
projectile_geometry = {
    "description": {
        "identifier": "geometry.dragon_spear",
        "texture_width": 256,
        "texture_height": 256,
        "visible_bounds_width": 4,
        "visible_bounds_height": 4,
        "visible_bounds_offset": [0, 0, 0],
    },
    "bones": [{"name": "root", "pivot": [0, 0, 0], "cubes": projectile_cubes}],
}
write_json(
    AS / "geo/entity/dragon_spear.geo.json",
    {"format_version": "1.12.0", "minecraft:geometry": [projectile_geometry]},
)

# Keep only tracks targeting real 1ba bones. Remove old procedural controls and
# standalone spear motion; the reanchored spear now follows the right-hand arm.
animation_path = AS / "animations/entity/dragon_soul.animation.json"
animations = json.loads(animation_path.read_text(encoding="utf-8"))
source_names = set(bones)
for clip in animations.get("animations", {}).values():
    for name in list(clip.get("bones", {})):
        if name not in source_names or name in {"dragon_tail", "fly_tips", "fly2_tips", "Mouth"}:
            del clip["bones"][name]
        elif name == "longspear":
            channels = clip["bones"][name]
            if isinstance(channels, dict):
                channels.pop("rotation", None)
                channels.pop("position", None)
write_json(animation_path, animations)

print(
    "restored 1ba source",
    len(geometry_root["bones"]),
    "bones",
    sum(len(bone.get("cubes", [])) for bone in geometry_root["bones"]),
    "cubes",
)
