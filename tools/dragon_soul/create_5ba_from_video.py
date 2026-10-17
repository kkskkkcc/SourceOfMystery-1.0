"""Standalone Dragon Soul YSM design built from the supplied video reference only.

This generator deliberately does not read the Minecraft project, 1ba, 2ba, 4ba,
or any earlier model. It writes a review-only package to F:\\AAAmc模型自动化\\5ba.
"""
import json
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw

OUT = Path(r"F:\AAAmc模型自动化\5ba")
ROOT = Path(__file__).resolve().parents[2]
PREVIEW = ROOT / "build" / "dragon_soul_5ba_video_only_preview.png"
sys.path.insert(0, str(ROOT / "tools" / "weeping_death_lord"))
from geo_render import render


COLORS = [
    (35, 17, 43, 255),     # hair dark
    (26, 20, 42, 255),     # wing membrane
    (48, 49, 66, 255),     # armor graphite
    (143, 132, 163, 255),  # silver rim
    (113, 35, 124, 255),   # purple
    (205, 78, 184, 255),   # magenta
    (245, 215, 208, 255),  # skin
    (226, 67, 220, 255),   # iris
    (9, 7, 14, 255),       # black
    (115, 22, 45, 255),    # wine red
    (92, 80, 130, 255),    # blue purple
    (206, 196, 219, 255),  # bright trim
]


def uv(mat):
    u = mat * 20 + 2
    return {face: {"uv": [u, 2], "uv_size": [16, 16]}
            for face in ("north", "south", "east", "west", "up", "down")}


def cube(origin, size, mat=2, pivot=None, rotation=None):
    out = {"origin": list(origin), "size": list(size), "uv": uv(mat)}
    if pivot is not None:
        out["pivot"] = list(pivot)
    if rotation is not None:
        out["rotation"] = list(rotation)
    return out


def euler(R):
    beta = math.asin(max(-1.0, min(1.0, float(-R[2, 0]))))
    if abs(math.cos(beta)) > 1e-7:
        a = math.atan2(R[2, 1], R[2, 2]); g = math.atan2(R[1, 0], R[0, 0])
    else:
        a = math.atan2(-R[1, 2], R[1, 1]); g = 0
    return [-math.degrees(a), -math.degrees(beta), math.degrees(g)]


def beam(a, b, width, mat=2, depth=None):
    import numpy as np
    a, b = np.asarray(a, float), np.asarray(b, float)
    v = (b - a) * [-1, 1, 1]; length = float(np.linalg.norm(v)); v /= length
    up = np.array([0.0, 1.0, 0.0]); axis = np.cross(up, v); dot = float(np.dot(up, v))
    K = np.array([[0, -axis[2], axis[1]], [axis[2], 0, -axis[0]], [-axis[1], axis[0], 0]])
    R = np.eye(3) + K + K @ K / (1 + dot) if dot > -0.9999 else np.diag([1, -1, -1])
    dep = depth if depth is not None else width
    c = cube([a[0] - width / 2, a[1], a[2] - dep / 2], [width, length, dep], mat)
    c["pivot"] = list(a); c["rotation"] = euler(R)
    return c


def bone(name, parent, pivot, cubes):
    return {"name": name, "parent": parent, "pivot": list(pivot), "cubes": cubes}


def track(keys):
    return {f"{t:g}": list(v) for t, v in keys}


def animations():
    def c(length, bones, loop=False):
        return {"loop": loop, "animation_length": length, "bones": bones}

    idle = {
        "Torso": {"rotation": track([(0, [0, 0, 0]), (1.2, [2, 0, 0]), (2.4, [0, 0, 0])])},
        "HairLocks": {"rotation": track([(0, [0, 0, 0]), (1.2, [2, 0, -1]), (2.4, [0, 0, 0])])},
        "Tail": {"rotation": track([(0, [0, 0, 0]), (1.2, [0, 4, -2]), (2.4, [0, 0, 0])])},
        "WingLeft": {"rotation": track([(0, [0, 0, -3]), (1.2, [4, 0, -7]), (2.4, [0, 0, -3])])},
        "WingRight": {"rotation": track([(0, [0, 0, 3]), (1.2, [4, 0, 7]), (2.4, [0, 0, 3])])},
    }
    fly = {
        "Torso": {"rotation": track([(0, [-8, 0, 0]), (.7, [-3, 0, 0]), (1.4, [-8, 0, 0])])},
        "WingLeft": {"rotation": track([(0, [0, 0, -14]), (.35, [8, 0, -23]), (.7, [0, 0, -14]), (1.05, [-8, 0, -8]), (1.4, [0, 0, -14])])},
        "WingRight": {"rotation": track([(0, [0, 0, 14]), (.35, [8, 0, 23]), (.7, [0, 0, 14]), (1.05, [-8, 0, 8]), (1.4, [0, 0, 14])])},
        "Tail": {"rotation": track([(0, [0, 0, 0]), (.7, [0, -8, 4]), (1.4, [0, 0, 0])])},
    }
    slash = {
        "Torso": {"rotation": track([(0, [0, 0, 0]), (.22, [0, -22, 0]), (.48, [8, 25, 5]), (.82, [0, 0, 0])])},
        "ArmRight": {"rotation": track([(0, [0, 0, -18]), (.22, [-55, -35, -38]), (.48, [-105, 25, 28]), (.82, [0, 0, -18])])},
        "ForeArmRight": {"rotation": track([(0, [0, 0, 0]), (.22, [-45, 0, 0]), (.48, [-8, 0, 0]), (.82, [0, 0, 0])])},
        "Weapon": {"rotation": track([(0, [0, 0, 0]), (.22, [0, 0, -35]), (.48, [0, 0, 72]), (.82, [0, 0, 0])])},
    }
    thrust = {
        "Torso": {"rotation": track([(0, [0, 0, 0]), (.3, [-9, -20, 0]), (.58, [2, 0, 0]), (.95, [0, 0, 0])])},
        "ArmRight": {"rotation": track([(0, [0, 0, -18]), (.3, [-80, -35, -20]), (.58, [-98, 0, -8]), (.95, [0, 0, -18])])},
        "ForeArmRight": {"rotation": track([(0, [0, 0, 0]), (.3, [-70, 0, 0]), (.58, [-5, 0, 0]), (.95, [0, 0, 0])])},
        "Weapon": {"rotation": track([(0, [0, 0, 0]), (.3, [0, 0, -8]), (.58, [0, 0, 6]), (.95, [0, 0, 0])])},
    }
    dive = {
        "Torso": {"rotation": track([(0, [55, 0, 0]), (.9, [22, 0, 0]), (1.8, [0, 0, 0])])},
        "WingLeft": {"rotation": track([(0, [0, 0, -40]), (.9, [0, 0, -22]), (1.8, [0, 0, -5])])},
        "WingRight": {"rotation": track([(0, [0, 0, 40]), (.9, [0, 0, 22]), (1.8, [0, 0, 5])])},
        "Tail": {"rotation": track([(0, [0, 0, 12]), (.9, [0, -9, 5]), (1.8, [0, 0, 0])])},
    }
    land = {
        "Torso": {"rotation": track([(0, [24, 0, 0]), (.3, [42, 0, 0]), (1.1, [9, 0, 0]), (2.2, [0, 0, 0])])},
        "LegRight": {"rotation": track([(0, [-24, 0, 0]), (.3, [-70, 0, 0]), (1.1, [-10, 0, 0]), (2.2, [0, 0, 0])])},
        "LegLeft": {"rotation": track([(0, [20, 0, 0]), (.3, [-35, 0, 0]), (1.1, [-8, 0, 0]), (2.2, [0, 0, 0])])},
        "WingLeft": {"rotation": track([(0, [0, 0, -34]), (.3, [0, 0, -18]), (2.2, [0, 0, -5])])},
        "WingRight": {"rotation": track([(0, [0, 0, 34]), (.3, [0, 0, 18]), (2.2, [0, 0, 5])])},
    }
    return {"boss_idle": c(2.4, idle, True), "boss_fly": c(1.4, fly, True), "boss_slash": c(.82, slash), "boss_thrust": c(.95, thrust), "boss_intro_dive": c(1.8, dive), "boss_intro_land": c(2.2, land)}


def make_texture(path):
    image = Image.new("RGBA", (256, 256), (0, 0, 0, 0)); d = ImageDraw.Draw(image)
    for i, color in enumerate(COLORS):
        x = i * 20; d.rectangle((x, 0, x + 19, 19), fill=color); d.rectangle((x, 22, x + 19, 41), fill=tuple(max(0, v - 24) for v in color[:3]) + (255,))
    # Hand-painted magenta feather marks in the atlas for the wing ribs.
    for x in range(0, 240, 16): d.line((x, 62, x + 8, 80), fill=COLORS[5], width=3)
    image.save(path)
    return image


def main():
    for sub in ("models", "animations", "textures"):
        (OUT / sub).mkdir(parents=True, exist_ok=True)
    bones = [bone("Root", None, [0, 0, 0], [])]
    # Face, hair and torso follow the video close-up: pale angular face, purple eyes,
    # dark burgundy layered hair and a compact black/silver armored silhouette.
    bones.append(bone("Torso", "Root", [0, 21, 0], [
        cube([-3.9, 14.6, -1.8], [7.8, 4.2, 4.4], 2), cube([-3.4, 18.4, -2.3], [6.8, 3.2, 4.8], 8),
        cube([-3.8, 21.0, -2.5], [7.6, 7.4, 5.0], 2), cube([-3.0, 23.0, -3.35], [6.0, 2.0, .75], 3),
        cube([-2.2, 24.6, -3.65], [4.4, 2.2, .65], 11), cube([-1.0, 26.4, -4.0], [2.0, 1.6, .5], 5),
        cube([-4.9, 22.2, -1.7], [1.2, 3.4, 3.2], 3), cube([3.7, 22.2, -1.7], [1.2, 3.4, 3.2], 3),
        cube([-4.3, 26.0, -1.4], [1.5, 2.4, 2.8], 4), cube([2.8, 26.0, -1.4], [1.5, 2.4, 2.8], 4),
    ]))
    bones.append(bone("Head", "Torso", [0, 30, 0], [
        cube([-2.7, 28.9, -2.7], [5.4, 5.8, 2.8], 6), cube([-2.2, 28.4, -2.9], [4.4, 1.0, 2.7], 6),
        cube([-2.5, 34.0, -2.2], [5.0, 1.8, 3.7], 0), cube([-2.95, 32.2, -3.25], [.65, 2.9, .8], 0), cube([2.3, 32.2, -3.25], [.65, 2.9, .8], 0),
        cube([-2.05, 31.0, -3.55], [1.55, 1.0, .3], 7), cube([.5, 31.0, -3.55], [1.55, 1.0, .3], 7),
        cube([-1.65, 31.2, -3.78], [.42, .52, .2], 8), cube([.95, 31.2, -3.78], [.42, .52, .2], 8),
        cube([-2.35, 32.0, -3.72], [1.2, .24, .18], 0), cube([1.15, 32.0, -3.72], [1.2, .24, .18], 0),
        cube([-.25, 30.0, -3.85], [.5, .75, .25], 9), cube([-.75, 29.2, -3.78], [1.5, .25, .2], 8),
    ]))
    hair = []
    for x in (-2.35, -1.55, -.75, .05, .85, 1.65):
        hair.append(beam((x, 35.1, -3.0), (x - .25, 31.8, -3.8), .78, 0, .55))
    hair += [beam((-3.1, 34.8, -2.6), (-4.1, 27.0, -1.4), 1.15, 0, .9), beam((3.1, 34.8, -2.6), (4.1, 27.0, -1.4), 1.15, 0, .9)]
    bones.append(bone("HairLocks", "Head", [0, 34, 0], hair))
    rear = []
    for side in (-1, 1):
        rear += [beam((side*2.7, 34.0, 1.8), (side*3.2, 26.0, 3.4), 1.25, 0, 1.0), beam((side*2.2, 30.0, 3.0), (side*2.5, 21.2, 4.8), .95, 0, .8)]
    bones.append(bone("HairBack", "Head", [0, 34, 2], rear))
    # Arms and short armored legs.
    for side, arm, fore, leg in [(-1, "ArmRight", "ForeArmRight", "LegRight"), (1, "ArmLeft", "ForeArmLeft", "LegLeft")]:
        bones.append(bone(arm, "Torso", [side*4.2, 27.5, 0], [cube([side*4.2-(2 if side<0 else 0), 22.6, -1.65], [2, 6.0, 3.3], 2), cube([side*4.15-(1.4 if side<0 else 0), 22.2, -2.0], [1.4, 1.4, 3.8], 3), cube([side*4.8-(.65 if side<0 else 0), 26.0, -2.0], [.65, 2.2, 3.9], 5)]))
        bones.append(bone(fore, arm, [side*4.8, 22.5, 0], [cube([side*4.3-(1.8 if side<0 else 0), 16.8, -1.55], [1.8, 6.2, 3.0], 2), cube([side*4.25-(1.35 if side<0 else 0), 16.1, -2.0], [1.35, 1.4, 3.5], 3), cube([side*4.2-(1.1 if side<0 else 0), 18.5, -2.2], [1.1, 2.0, .55], 5)]))
        bones.append(bone(leg, "Torso", [side*1.9, 15, 0], [cube([side*1.9-(1.75 if side<0 else 0), 8.0, -1.75], [1.75, 7.0, 3.5], 2), cube([side*1.8-(1.9 if side<0 else 0), 4.2, -2.3], [1.9, 4.0, 4.2], 8), cube([side*1.8-(2.0 if side<0 else 0), 3.9, -3.3], [2.0, .7, 2.7], 3)]))

    # Layered skirt armor panels and side fins.
    skirt = []
    for side in (-1, 1):
        skirt += [cube([side*2.9-(1.2 if side<0 else 0), 16.5, -2.8], [1.2, 5.5, .75], 3), cube([side*3.9-(.7 if side<0 else 0), 17.0, -1.8], [.7, 4.8, 2.4], 4)]
    skirt += [cube([-3.5, 16.2, -3.0], [7.0, 1.1, .8], 11), cube([-3.0, 13.8, -2.7], [6.0, 2.2, .8], 2)]
    bones.append(bone("SkirtArmor", "Torso", [0, 19, 0], skirt))

    # Extra close-up panels: the reference video uses many small beveled plates
    # instead of one smooth torso box. They are deliberately separate cubes so
    # the silhouette reads at Blockbench zoom and in Minecraft lighting.
    torso_bone = next(b for b in bones if b["name"] == "Torso")
    torso_bone["cubes"] += [
        cube([-2.7, 26.8, -4.1], [1.0, .55, .35], 3), cube([-1.55, 26.8, -4.2], [1.0, .55, .35], 3), cube([.55, 26.8, -4.2], [1.0, .55, .35], 3), cube([1.7, 26.8, -4.1], [1.0, .55, .35], 3),
        cube([-2.6, 25.8, -4.05], [.75, .7, .35], 4), cube([-1.65, 25.8, -4.18], [.75, .7, .35], 5), cube([.9, 25.8, -4.18], [.75, .7, .35], 5), cube([1.85, 25.8, -4.05], [.75, .7, .35], 4),
        cube([-3.1, 24.7, -4.0], [.9, .7, .35], 11), cube([2.2, 24.7, -4.0], [.9, .7, .35], 11),
        cube([-2.2, 22.0, -3.3], [1.0, 1.0, .5], 3), cube([-1.0, 22.0, -3.4], [1.0, 1.0, .5], 3), cube([0.2, 22.0, -3.4], [1.0, 1.0, .5], 3), cube([1.4, 22.0, -3.3], [1.0, 1.0, .5], 3),
    ]
    head_bone = next(b for b in bones if b["name"] == "Head")
    head_bone["cubes"] += [
        cube([-2.15, 31.7, -3.86], [.35, .55, .2], 8), cube([-1.7, 31.7, -3.9], [.28, .55, .2], 7), cube([1.42, 31.7, -3.9], [.28, .55, .2], 7), cube([1.82, 31.7, -3.86], [.35, .55, .2], 8),
        cube([-2.35, 32.55, -3.85], [1.35, .2, .18], 0), cube([1.0, 32.55, -3.85], [1.35, .2, .18], 0),
        cube([-2.65, 30.55, -3.75], [.28, 1.2, .18], 0), cube([2.37, 30.55, -3.75], [.28, 1.2, .18], 0),
        cube([-.25, 30.25, -3.92], [.5, .45, .18], 6), cube([-.7, 29.0, -3.9], [1.4, .2, .18], 5),
    ]
    hair_bone = next(b for b in bones if b["name"] == "HairLocks")
    hair_bone["cubes"] += [
        cube([-3.55, 27.0, -3.65], [1.0, 7.8, .82], 0), cube([2.55, 27.0, -3.65], [1.0, 7.8, .82], 0),
        cube([-3.75, 23.0, -2.8], [.75, 5.4, .7], 0), cube([3.0, 23.0, -2.8], [.75, 5.4, .7], 0),
        cube([-3.1, 33.0, -3.95], [.8, 1.5, .5], 0), cube([-2.15, 33.4, -4.0], [.8, 1.1, .5], 0), cube([-1.15, 33.7, -4.0], [.8, .9, .5], 0),
        cube([.35, 33.7, -4.0], [.8, .9, .5], 0), cube([1.35, 33.4, -4.0], [.8, 1.1, .5], 0), cube([2.3, 33.0, -3.95], [.8, 1.5, .5], 0),
    ]
    for x, y, z, sx, sy, sz in [(-2.9, 35.9, -3.6, .55, 2.6, .65), (-2.2, 36.7, -3.85, .5, 2.8, .55), (-1.4, 37.1, -4.0, .48, 3.0, .5), (-.6, 37.3, -4.05, .45, 3.1, .48), (.2, 37.2, -4.05, .45, 3.0, .48), (1.0, 37.0, -3.98, .48, 2.9, .5), (1.8, 36.6, -3.8, .52, 2.7, .58), (2.6, 35.8, -3.55, .58, 2.6, .68)]:
        hair_bone["cubes"].append(cube([x, y, z], [sx, sy, sz], 0))
    hair_back = next(b for b in bones if b["name"] == "HairBack")
    for side in (-1, 1):
        for j in range(5):
            hair_back["cubes"].append(beam((side*(2.0+j*.25), 31.0-j*1.7, 3.2+j*.35), (side*(2.4+j*.3), 28.8-j*1.8, 3.8+j*.45), .8-j*.08, 0, .65))
    for arm_name in ("ArmRight", "ArmLeft", "ForeArmRight", "ForeArmLeft"):
        arm_bone = next(b for b in bones if b["name"] == arm_name)
        side = -1 if "Right" in arm_name else 1
        for j in range(4):
            x = side*(4.2 + (j%2)*.55)
            arm_bone["cubes"].append(cube([x-(.42 if side<0 else 0), 18.0+j*1.25, -2.28], [.42, .8, .45], 4 if j%2 else 3))

    # Four large struts plus many thin membrane ribs per wing, matching the video silhouette.
    for side, name in [(-1, "WingLeft"), (1, "WingRight")]:
        P=lambda p:[side*p[0],p[1],p[2]]; wing=[]
        for a,b,w,m in [((3,28,3),(11,38,5),2.3,3),((11,38,5),(22,44,7),1.8,3),((22,44,7),(35,40,8),1.35,3),((35,40,8),(44,28,9),.95,3)]: wing.append(beam(P(a),P(b),w,m,w*.72))
        for j in range(8):
            a=P((10+j*4,37-j*.8,7.5));b=P((17+j*4.6,19+j*.4,8.7));wing.append(beam(a,b,1.0-j*.07,5 if j%2 else 4,.55))
        bones.append(bone(name,"Torso",P((3,28,3)),wing))
        bones.append(bone(name+"Spine",name,P((9,38,6)),[beam(P((9,38,6)),P((9,52,7)),1.2,1,.9),beam(P((10,39,6.5)),P((12,48,7)),.65,4,.55)]))

    # Segmented curling tail visible in the back/side video shots.
    points=[(0,17.5,3.3),(-1,14.8,5.8),(-3.8,11.5,8.9),(-7.5,9.9,11.8),(-11.7,11.5,13.5),(-15.2,15.2,12.4),(-16.0,19.0,9.0),(-13.5,22.0,5.9),(-9.1,22.8,3.6)]
    tail=[]
    for i,(a,b) in enumerate(zip(points,points[1:])):
        w=3.0-i*.27;tail.append(beam(a,b,w,1 if i<5 else 4,w*.75))
        aa=[a[j]*.7+b[j]*.3 for j in range(3)];bb=[a[j]*.35+b[j]*.65 for j in range(3)];tail.append(beam(aa,bb,w+.28,5,w*.8))
        if i in (2,3,4,5,6):tail.append(beam(a,[a[0]-.4,a[1]+1.6,a[2]+.2],w*.35,5,w*.25))
    bones.append(bone("Tail","Torso",points[0],tail))

    # Heavy dark weapon with silver spine, purple guard and magenta edge.
    weapon=[beam((-5.1,21.0,-1.0),(-5.1,21.0,-16.0),.7,8,.7),beam((-5.1,21.0,-2.0),(-5.1,21.0,-5.5),2.5,3,1.6),beam((-5.1,21.0,-15.0),(-5.1,21.0,-26.0),3.0,2,1.5),beam((-5.1,21.0,-15.5),(-5.1,21.0,-26.5),.75,3,.75),beam((-5.1,21.0,-25.5),(-5.1,21.0,-29.0),1.25,5,.8)]
    bones.append(bone("Weapon","ForeArmRight",[-5.1,21.0,-1.0],weapon))

    geo={"format_version":"1.12.0","minecraft:geometry":[{"description":{"identifier":"geometry.dragon_soul_5ba","texture_width":256,"texture_height":256,"visible_bounds_width":10,"visible_bounds_height":8,"visible_bounds_offset":[0,2,0]},"bones":bones}]}
    (OUT/"models/main.json").write_text(json.dumps(geo,ensure_ascii=False,separators=(",",":"))+"\n",encoding="utf-8")
    an={"format_version":"1.8.0","animations":animations()};(OUT/"animations/boss.animation.json").write_text(json.dumps(an,ensure_ascii=False,separators=(",",":"))+"\n",encoding="utf-8");(OUT/"animations/main.animation.json").write_text(json.dumps(an,ensure_ascii=False,separators=(",",":"))+"\n",encoding="utf-8")
    tex=make_texture(OUT/"textures/default.png");tex.save(OUT/"textures/blue.png")
    ysm={"spec":2,"metadata":{"name":"龙魂·深紫翼装（视频独立设计）","tips":"只依据视频模型图创作的审核版","license":{"type":"All Rights Reserved"}},"properties":{"height_scale":0.7,"width_scale":0.7,"preview_animation":"boss_idle","default_texture":"default","free":True},"files":{"player":{"model":{"main":"models/main.json"},"animation":{"main":"animations/main.animation.json","boss":"animations/boss.animation.json"},"texture":[{"uv":"textures/default.png"},{"uv":"textures/blue.png"}]}}}
    (OUT/"ysm.json").write_text(json.dumps(ysm,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    (OUT/"README_审核说明.md").write_text("# 龙魂·深紫翼装（5ba 视频独立设计）\n\n本模型从空白几何开始，只参考 E:\\36107914408-1-192.mp4 的可见模型图和动作轮廓。\n没有读取 1ba、2ba、4ba，也没有读取 MCmodfind 项目资源。\n本文件夹仅用于审核，审核通过后再决定是否接入项目。\n",encoding="utf-8")
    sheet=Image.new("RGBA",(1800,600),(35,35,42,255));d=ImageDraw.Draw(sheet); 
    for i,yaw in enumerate((0,-45,180,45)):
        im,_,_=render(geo["minecraft:geometry"][0],tex,{},yaw=yaw,size=600,center=[0,25,0],span=88);sheet.paste(im,(i*450,0));d.text((i*450+8,8),f"5ba video-only yaw {yaw}",fill="white")
    PREVIEW.parent.mkdir(parents=True,exist_ok=True);sheet.save(PREVIEW)
    print("created",OUT,"bones",len(bones),"cubes",sum(len(b["cubes"]) for b in bones),"preview",PREVIEW)


if __name__ == "__main__": main()
