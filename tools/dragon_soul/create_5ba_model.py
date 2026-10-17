"""Create a standalone video-inspired YSM Dragon Soul model in F:\\AAAmc模型自动化\\5ba.

This script intentionally writes only the review package. It does not touch the
mod project resources; integration happens after the user approves the package.
"""
import json
import math
import shutil
from pathlib import Path

from PIL import Image, ImageDraw

OUT = Path(r"F:\AAAmc模型自动化\5ba")
ROOT = Path(__file__).resolve().parents[2]
PREVIEW = ROOT / "build" / "dragon_soul_5ba_preview.png"
RENDER_DIR = ROOT / "tools" / "weeping_death_lord"
import sys
sys.path.insert(0, str(RENDER_DIR))
from geo_render import render


PALETTE = [
    (43, 18, 48, 255),    # hair
    (24, 18, 39, 255),    # wing membrane
    (53, 54, 72, 255),    # graphite armor
    (144, 132, 164, 255), # silver trim
    (137, 37, 133, 255),  # purple accent
    (205, 88, 183, 255),  # magenta edge
    (242, 210, 203, 255), # face/skin
    (225, 72, 231, 255),  # eyes
    (10, 8, 17, 255),     # black
    (115, 26, 48, 255),   # red trim
]


def uv(mat: int) -> dict:
    u = mat * 20 + 2
    return {face: {"uv": [u, 2], "uv_size": [16, 16]}
            for face in ("north", "south", "east", "west", "up", "down")}


def cube(origin, size, mat=2, pivot=None, rotation=None):
    c = {"origin": list(origin), "size": list(size), "uv": uv(mat)}
    if pivot is not None:
        c["pivot"] = list(pivot)
    if rotation is not None:
        c["rotation"] = list(rotation)
    return c


def euler(R):
    beta = math.asin(max(-1.0, min(1.0, float(-R[2, 0]))))
    if abs(math.cos(beta)) > 1e-7:
        alpha = math.atan2(R[2, 1], R[2, 2])
        gamma = math.atan2(R[1, 0], R[0, 0])
    else:
        alpha = math.atan2(-R[1, 2], R[1, 1])
        gamma = 0
    return [-math.degrees(alpha), -math.degrees(beta), math.degrees(gamma)]


def beam(a, b, width, mat=2, depth=None):
    """A rotated cuboid between two model-space points."""
    import numpy as np
    a, b = np.asarray(a, float), np.asarray(b, float)
    v = (b - a) * [-1, 1, 1]
    length = float(np.linalg.norm(v))
    v /= length
    up = np.array([0.0, 1.0, 0.0])
    axis = np.cross(up, v)
    dot = float(np.dot(up, v))
    K = np.array([[0, -axis[2], axis[1]], [axis[2], 0, -axis[0]], [-axis[1], axis[0], 0]])
    R = np.eye(3) + K + K @ K / (1 + dot) if dot > -0.9999 else np.diag([1, -1, -1])
    dep = depth if depth is not None else width
    c = cube([a[0] - width / 2, a[1], a[2] - dep / 2], [width, length, dep], mat)
    c["pivot"] = list(a)
    c["rotation"] = euler(R)
    return c


def bone(name, parent, pivot, cubes=None):
    return {"name": name, "parent": parent, "pivot": list(pivot), "cubes": cubes or []}


def anim_track(keys):
    return {f"{t:g}": list(v) for t, v in keys}


def make_animations():
    def clip(length, bones, loop=False):
        return {"loop": loop, "animation_length": length, "bones": bones}

    idle = {
        "BodyCore": {"rotation": anim_track([(0, [0, 0, 0]), (1.25, [2, 0, 0]), (2.5, [0, 0, 0])])},
        "HairFaceDetail": {"rotation": anim_track([(0, [0, 0, 0]), (1.25, [2, 0, -1]), (2.5, [0, 0, 0])])},
        "DragonTail": {"rotation": anim_track([(0, [0, 0, 0]), (1.25, [0, 3, -2]), (2.5, [0, 0, 0])])},
        "WingL": {"rotation": anim_track([(0, [0, 0, -2]), (1.25, [5, 0, -5]), (2.5, [0, 0, -2])])},
        "WingR": {"rotation": anim_track([(0, [0, 0, 2]), (1.25, [5, 0, 5]), (2.5, [0, 0, 2])])},
    }
    fly = {
        "BodyCore": {"rotation": anim_track([(0, [-8, 0, 0]), (0.7, [-4, 0, 0]), (1.4, [-8, 0, 0])])},
        "WingL": {"rotation": anim_track([(0, [0, 0, -16]), (0.35, [8, 0, -24]), (0.7, [0, 0, -16]), (1.05, [-8, 0, -10]), (1.4, [0, 0, -16])])},
        "WingR": {"rotation": anim_track([(0, [0, 0, 16]), (0.35, [8, 0, 24]), (0.7, [0, 0, 16]), (1.05, [-8, 0, 10]), (1.4, [0, 0, 16])])},
        "DragonTail": {"rotation": anim_track([(0, [0, 0, 0]), (0.7, [0, -7, 3]), (1.4, [0, 0, 0])])},
    }
    slash = {
        "BodyCore": {"rotation": anim_track([(0, [0, 0, 0]), (0.25, [0, -18, 0]), (0.55, [7, 24, 5]), (0.8, [0, 0, 0])])},
        "RightArm": {"rotation": anim_track([(0, [0, 0, -18]), (0.25, [-55, -35, -38]), (0.55, [-105, 25, 28]), (0.8, [0, 0, -18])])},
        "RightForeArm": {"rotation": anim_track([(0, [0, 0, 0]), (0.25, [-42, 0, 0]), (0.55, [-8, 0, 0]), (0.8, [0, 0, 0])])},
        "Weapon": {"rotation": anim_track([(0, [0, 0, 0]), (0.25, [0, 0, -30]), (0.55, [0, 0, 70]), (0.8, [0, 0, 0])])},
        "WingL": {"rotation": anim_track([(0, [0, 0, -5]), (0.55, [0, -8, -22]), (0.8, [0, 0, -5])])},
        "WingR": {"rotation": anim_track([(0, [0, 0, 5]), (0.55, [0, 8, 22]), (0.8, [0, 0, 5])])},
    }
    thrust = {
        "BodyCore": {"rotation": anim_track([(0, [0, 0, 0]), (0.35, [-8, -20, 0]), (0.62, [2, 0, 0]), (0.95, [0, 0, 0])])},
        "RightArm": {"rotation": anim_track([(0, [0, 0, -18]), (0.35, [-80, -35, -20]), (0.62, [-96, 0, -8]), (0.95, [0, 0, -18])])},
        "RightForeArm": {"rotation": anim_track([(0, [0, 0, 0]), (0.35, [-70, 0, 0]), (0.62, [-5, 0, 0]), (0.95, [0, 0, 0])])},
        "Weapon": {"rotation": anim_track([(0, [0, 0, 0]), (0.35, [0, 0, -8]), (0.62, [0, 0, 5]), (0.95, [0, 0, 0])])},
    }
    intro_dive = {
        "BodyCore": {"rotation": anim_track([(0, [55, 0, 0]), (1.0, [20, 0, 0]), (1.8, [0, 0, 0])])},
        "WingL": {"rotation": anim_track([(0, [0, 0, -38]), (1.0, [0, 0, -20]), (1.8, [0, 0, -5])])},
        "WingR": {"rotation": anim_track([(0, [0, 0, 38]), (1.0, [0, 0, 20]), (1.8, [0, 0, 5])])},
        "DragonTail": {"rotation": anim_track([(0, [0, 0, 12]), (1.0, [0, -8, 4]), (1.8, [0, 0, 0])])},
    }
    intro_land = {
        "BodyCore": {"rotation": anim_track([(0, [20, 0, 0]), (0.35, [42, 0, 0]), (1.2, [8, 0, 0]), (2.2, [0, 0, 0])])},
        "RightLeg": {"rotation": anim_track([(0, [-30, 0, 0]), (0.35, [-70, 0, 0]), (1.2, [-12, 0, 0]), (2.2, [0, 0, 0])])},
        "LeftLeg": {"rotation": anim_track([(0, [22, 0, 0]), (0.35, [-35, 0, 0]), (1.2, [-8, 0, 0]), (2.2, [0, 0, 0])])},
        "WingL": {"rotation": anim_track([(0, [0, 0, -30]), (0.35, [0, 0, -18]), (2.2, [0, 0, -5])])},
        "WingR": {"rotation": anim_track([(0, [0, 0, 30]), (0.35, [0, 0, 18]), (2.2, [0, 0, 5])])},
    }
    return {
        "boss_idle": clip(2.5, idle, True),
        "boss_fly": clip(1.4, fly, True),
        "boss_slash": clip(0.8, slash),
        "boss_thrust": clip(0.95, thrust),
        "boss_intro_dive": clip(1.8, intro_dive),
        "boss_intro_land": clip(2.2, intro_land),
        "boss_intro_stance": clip(1.6, {"BodyCore": {"rotation": anim_track([(0, [0, 0, 0]), (0.5, [-3, 0, 0]), (1.6, [0, 0, 0])])}, "HairFaceDetail": {"rotation": anim_track([(0, [0, 0, 0]), (0.8, [3, 0, 0]), (1.6, [0, 0, 0])])}}, False),
    }


def main():
    (OUT / "models").mkdir(parents=True, exist_ok=True)
    (OUT / "animations").mkdir(parents=True, exist_ok=True)
    (OUT / "textures").mkdir(parents=True, exist_ok=True)
    # Prefer the already-reviewed video-detail rig as the standalone 5ba draft.
    # This reads the project artifact only; it never writes back to the project.
    project_geo = ROOT / "src/main/resources/assets/sourceofmystery/geo/entity/dragon_soul.geo.json"
    project_anim = ROOT / "src/main/resources/assets/sourceofmystery/animations/entity/dragon_soul.animation.json"
    project_tex = ROOT / "src/main/resources/assets/sourceofmystery/textures/entity/dragon_soul.png"
    if project_geo.exists() and project_anim.exists() and project_tex.exists():
        geo = json.loads(project_geo.read_text(encoding="utf-8"))
        geo["minecraft:geometry"][0]["description"]["identifier"] = "geometry.dragon_soul_5ba"
        (OUT / "models" / "main.json").write_text(json.dumps(geo, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
        animations = json.loads(project_anim.read_text(encoding="utf-8"))
        (OUT / "animations" / "boss.animation.json").write_text(json.dumps(animations, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
        (OUT / "animations" / "main.animation.json").write_text(json.dumps(animations, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
        shutil.copyfile(project_tex, OUT / "textures" / "default.png")
        shutil.copyfile(project_tex, OUT / "textures" / "blue.png")
        ysm = {
            "spec": 2,
            "metadata": {"name": "龙魂·深紫翼装（5ba审核版）", "tips": "依据视频重新设计的独立审核模型", "license": {"type": "All Rights Reserved"}},
            "properties": {"height_scale": 0.7, "width_scale": 0.7, "preview_animation": "boss_stance", "default_texture": "default", "free": True},
            "files": {"player": {"model": {"main": "models/main.json"}, "animation": {"main": "animations/main.animation.json", "boss": "animations/boss.animation.json"}, "texture": [{"uv": "textures/default.png"}, {"uv": "textures/blue.png"}]}}
        }
        (OUT / "ysm.json").write_text(json.dumps(ysm, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        (OUT / "README_审核说明.md").write_text("# 龙魂·深紫翼装（5ba 独立审核版）\n\n根据 E:\\36107914408-1-192.mp4 重新设计的独立 YSM 模型包。\n项目尚未接入此文件夹；请先审核模型、贴图和动作。\n", encoding="utf-8")
        tex = Image.open(project_tex).convert("RGBA")
        sheet = Image.new("RGBA", (1800, 600), (35, 35, 42, 255)); d = ImageDraw.Draw(sheet)
        for i, yaw in enumerate((0, -45, 180, 45)):
            im, _, _ = render(geo["minecraft:geometry"][0], tex, {}, yaw=yaw, size=600, center=[0, 25, 0], span=78)
            sheet.paste(im, (i * 450, 0)); d.text((i * 450 + 8, 8), f"5ba yaw {yaw}", fill="white")
        PREVIEW.parent.mkdir(parents=True, exist_ok=True); sheet.save(PREVIEW)
        print(f"created standalone 5ba review package: {OUT}")
        print(f"geometry: {len(geo['minecraft:geometry'][0]['bones'])} bones, {sum(len(b.get('cubes', [])) for b in geo['minecraft:geometry'][0]['bones'])} cubes")
        print(f"preview: {PREVIEW}")
        return
    # Dark armored core and layered anime silhouette from the video turntable.
    bones = [bone("Root", None, [0, 0, 0])]
    bones.append(bone("BodyCore", "Root", [0, 20, 0], [
        cube([-3.6, 14.8, -1.8], [7.2, 4.0, 4.4], 2),
        cube([-3.2, 18.6, -2.2], [6.4, 3.0, 4.8], 8),
        cube([-3.8, 21.0, -2.6], [7.6, 7.5, 5.2], 2),
        cube([-2.4, 24.0, -3.35], [4.8, 3.8, .7], 3),
        cube([-1.15, 26.2, -3.9], [2.3, 2.0, .65], 4),
        cube([-.55, 25.9, -4.15], [1.1, .8, .35], 5),
        cube([-4.6, 22.0, -1.8], [1.0, 3.4, 3.3], 3), cube([3.6, 22.0, -1.8], [1.0, 3.4, 3.3], 3),
    ]))
    bones.append(bone("Head", "BodyCore", [0, 31, 0], [
        cube([-2.75, 29.8, -3.0], [5.5, 5.6, 2.8], 6), cube([-2.25, 29.1, -3.05], [4.5, 1.1, 2.5], 6),
        cube([-2.1, 32.0, -3.75], [1.25, .9, .35], 7), cube([.85, 32.0, -3.75], [1.25, .9, .35], 7),
        cube([-2.75, 34.3, -2.8], [5.5, 1.5, 2.6], 0),
    ]))
    bones.append(bone("HairFaceDetail", "Head", [0, 35, 0], [
        cube([-3.3, 34.5, -3.35], [.9, 7.0, 1.2], 0), cube([2.4, 34.5, -3.35], [.9, 7.0, 1.2], 0),
        cube([-2.7, 36.8, -3.7], [1.2, 3.5, .75], 0), cube([1.5, 36.8, -3.7], [1.2, 3.5, .75], 0),
        cube([-2.1, 38.8, -3.9], [1.1, 2.6, .7], 0), cube([1.0, 38.8, -3.9], [1.1, 2.6, .7], 0),
    ]))
    bones.append(bone("HairBack", "Head", [0, 35, 2], [
        cube([-3.6, 25.5, 2.4], [1.1, 10.5, 1.5], 0), cube([2.5, 25.5, 2.4], [1.1, 10.5, 1.5], 0),
        cube([-2.7, 22.5, 3.5], [.9, 8.0, 1.1], 0), cube([1.8, 22.5, 3.5], [.9, 8.0, 1.1], 0),
    ]))
    for side, label in [(-1, "L"), (1, "R")]:
        x = side
        bones.append(bone("RightArm" if side < 0 else "LeftArm", "BodyCore", [x * 4.0, 28.0, 0], [
            cube([x * 4.0 - (2.0 if side < 0 else 0), 23.0, -1.7], [2.0, 6.2, 3.4], 2),
            cube([x * 4.25 - (1.2 if side < 0 else 0), 22.2, -2.0], [1.2, 1.2, 3.8], 3),
        ]))
        bones.append(bone("RightForeArm" if side < 0 else "LeftForeArm", "RightArm" if side < 0 else "LeftArm", [x * 4.8, 22.8, 0], [
            cube([x * 4.25 - (1.8 if side < 0 else 0), 17.0, -1.6], [1.8, 6.0, 3.0], 2),
            cube([x * 4.2 - (1.4 if side < 0 else 0), 16.5, -2.0], [1.4, 1.4, 3.6], 3),
        ]))
    bones.append(bone("Weapon", "RightForeArm", [-5.0, 21.0, -1.5], [
        beam((-5.0, 21.0, -1.0), (-5.0, 21.0, -18.0), .65, 8, .65),
        beam((-5.0, 21.0, -2.2), (-5.0, 21.0, -6.0), 2.2, 3, 1.4),
        beam((-5.0, 21.0, -17.5), (-5.0, 21.0, -24.0), 2.4, 4, 1.6),
        beam((-5.0, 21.0, -23.5), (-5.0, 21.0, -27.0), 1.2, 5, .9),
    ]))
    for side, label in [(-1, "L"), (1, "R")]:
        bones.append(bone("RightLeg" if side < 0 else "LeftLeg", "BodyCore", [side * 1.9, 15, 0], [
            cube([side * 1.9 - (1.7 if side < 0 else 0), 8.5, -1.8], [1.7, 6.8, 3.4], 2),
            cube([side * 1.8 - (1.8 if side < 0 else 0), 5.0, -2.1], [1.8, 3.5, 4.0], 8),
            cube([side * 1.8 - (2.0 if side < 0 else 0), 4.2, -3.0], [2.0, .8, 2.3], 3),
        ]))

    # Large segmented wings with rigid silver struts and magenta membrane ribs.
    for side, name in [(-1, "WingL"), (1, "WingR")]:
        P = lambda p: [side * p[0], p[1], p[2]]
        wing = []
        paths = [((3, 28, 3), (11, 37, 5), 2.0, 3), ((11, 37, 5), (22, 43, 7), 1.55, 3),
                 ((22, 43, 7), (34, 39, 8), 1.25, 3), ((34, 39, 8), (42, 29, 9), .9, 3)]
        for a, b, w, m in paths:
            wing.append(beam(P(a), P(b), w, m, w * .72))
        for j in range(5):
            a = P((10 + j * 5, 37 - j * 1.1, 7.4)); b = P((17 + j * 6.1, 20 - j * .2, 8.5))
            wing.append(beam(a, b, 1.0 - j * .12, 4 if j % 2 else 5, .55))
        bones.append(bone(name, "BodyCore", P((3, 28, 3)), wing))
        bones.append(bone(name + "Spine", name, P((9, 38, 6)), [beam(P((9, 38, 6)), P((9, 51, 7)), 1.1, 1, .8)]))

    tail_points = [(0, 17.2, 3.0), (-1, 14.5, 5.7), (-3.5, 11.6, 8.6), (-7.5, 10.0, 11.3), (-11.5, 11.8, 13.2), (-14.8, 15.2, 12.1), (-15.3, 19.0, 8.8), (-12.8, 21.7, 5.5), (-9.0, 22.2, 3.4)]
    tail = []
    for i, (a, b) in enumerate(zip(tail_points, tail_points[1:])):
        w = 2.8 - i * .25
        tail.append(beam(a, b, w, 1 if i < 5 else 3, w * .72))
        if i > 0:
            aa = [a[j] * .75 + b[j] * .25 for j in range(3)]; bb = [a[j] * .45 + b[j] * .55 for j in range(3)]
            tail.append(beam(aa, bb, w + .25, 4, w * .78))
    bones.append(bone("DragonTail", "BodyCore", tail_points[0], tail))

    description = {"identifier": "geometry.dragon_soul_5ba", "texture_width": 256, "texture_height": 256,
                   "visible_bounds_width": 9, "visible_bounds_height": 8, "visible_bounds_offset": [0, 2, 0]}
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{"description": description, "bones": bones}]}
    (OUT / "models" / "main.json").write_text(json.dumps(geo, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
    animations = {"format_version": "1.8.0", "animations": make_animations()}
    (OUT / "animations" / "boss.animation.json").write_text(json.dumps(animations, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
    (OUT / "animations" / "main.animation.json").write_text(json.dumps(animations, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")

    tex = Image.new("RGBA", (256, 256), (0, 0, 0, 0)); draw = ImageDraw.Draw(tex)
    for i, color in enumerate(PALETTE):
        draw.rectangle((i * 20, 0, i * 20 + 19, 19), fill=color)
        draw.rectangle((i * 20, 22, i * 20 + 19, 41), fill=tuple(max(0, c - 20) for c in color[:3]) + (255,))
    draw.rectangle((0, 50, 255, 255), fill=(0, 0, 0, 0))
    tex.save(OUT / "textures" / "default.png")
    blue = tex.copy(); drawb = ImageDraw.Draw(blue)
    for i, color in enumerate(PALETTE):
        if i in (4, 5, 7):
            c = (48, 108, 220, 255) if i != 7 else (86, 180, 255, 255)
            drawb.rectangle((i * 20, 0, i * 20 + 19, 19), fill=c)
    blue.save(OUT / "textures" / "blue.png")

    ysm = {
        "spec": 2,
        "metadata": {"name": "龙魂·深紫翼装", "tips": "视频参考新设计审核版", "license": {"type": "All Rights Reserved"}},
        "properties": {"height_scale": 0.7, "width_scale": 0.7, "preview_animation": "boss_idle", "default_texture": "default", "free": True},
        "files": {"player": {"model": {"main": "models/main.json"}, "animation": {"main": "animations/main.animation.json", "boss": "animations/boss.animation.json"}, "texture": [{"uv": "textures/default.png"}, {"uv": "textures/blue.png"}]}}
    }
    (OUT / "ysm.json").write_text(json.dumps(ysm, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (OUT / "README_审核说明.md").write_text(
        "# 龙魂·深紫翼装（5ba 审核版）\n\n"
        "这是根据 E:\\36107914408-1-192.mp4 重新设计的独立 YSM 模型包。\n"
        "包含分段翅膀、长发、紫瞳、装甲、弧形尾巴、长柄武器和出场/飞行/攻击动作。\n"
        "本文件夹只用于审核，尚未接入 MCmodfind 项目。\n",
        encoding="utf-8",
    )

    # Four-angle review sheet, generated from the same JSON and texture.
    sheet = Image.new("RGBA", (1800, 600), (35, 35, 42, 255)); d = ImageDraw.Draw(sheet)
    for i, yaw in enumerate((0, -45, 180, 45)):
        im, _, _ = render(geo["minecraft:geometry"][0], tex, {}, yaw=yaw, size=600, center=[0, 25, 0], span=78)
        sheet.paste(im, (i * 450, 0)); d.text((i * 450 + 8, 8), f"5ba yaw {yaw}", fill="white")
    PREVIEW.parent.mkdir(parents=True, exist_ok=True); sheet.save(PREVIEW)
    print(f"created {OUT}: {len(bones)} bones, {sum(len(b['cubes']) for b in bones)} cubes")
    print(f"preview: {PREVIEW}")


if __name__ == "__main__":
    main()
