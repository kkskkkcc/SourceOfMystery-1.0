"""Build the staff's editable GeckoLib geometry, animation and pixel atlas."""
from pathlib import Path
import json
import math
import struct
import zlib

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/sourceofmystery"
MODEL = "dimension_breaker_staff"
SIZE = 64
PALETTE = [(200, 208, 219), (112, 115, 133), (228, 214, 249), (170, 143, 207)]


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def atlas():
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))
    raw = bytearray()
    for y in range(SIZE):
        raw.append(0)
        for x in range(SIZE):
            base = PALETTE[x // 16]
            delta = 12 if x % 16 in (2, 13) else (-9 if (x + y) % 11 == 0 else 0)
            raw.extend([max(0, min(255, c + delta)) for c in base] + [255])
    data = b"\x89PNG\r\n\x1a\n"
    data += chunk(b"IHDR", struct.pack(">IIBBBBB", SIZE, SIZE, 8, 6, 0, 0, 0))
    data += chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    path = ASSETS / "textures/item" / f"{MODEL}.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)


def cube(origin, size, material=0, pivot=None, rotation=None):
    value = {"origin": origin, "size": size,
             "uv": {face: {"uv": [material * 16 + 3, 3], "uv_size": [10, 10]}
                    for face in ("north", "south", "east", "west", "up", "down")}}
    if rotation:
        value.update(pivot=pivot, rotation=rotation)
    return value


def geometry():
    bones = [{"name": "staff", "pivot": [0, 0, 0], "cubes": [
        cube([-.38, -22, -.38], [.76, 44, .76]),
        cube([-.46, -3, -.46], [.92, 6, .92], 1),
        cube([-.10, -20, -.40], [.20, 40, .06], 2),
        cube([-.38, -23, -.38], [.76, 1.1, .76], 3, [0, -22.4, 0], [0, 45, 0])]}]
    root = bones[0]["cubes"]
    for y in (-20, -15, -3.3, 3.3, 17.5, 20.5):
        root += [cube([-.58, y, -.58], [1.16, .35, 1.16]),
                 cube([-.62, y + .35, -.62], [1.24, .12, 1.24], 2)]
    for quadrant in range(4):
        fin = {"name": f"crown_{quadrant}", "parent": "staff", "pivot": [0, 20, 0],
               "rotation": [0, 45 + quadrant * 90, 0], "cubes": []}
        for segment in range(9):
            t = segment / 9
            tn = (segment + 1) / 9
            x, y = .2 + 2.7 * math.sin(math.pi * t * .92), 20 + 11.2 * t
            nx, ny = .2 + 2.7 * math.sin(math.pi * tn * .92), 20 + 11.2 * tn
            thickness = .62 * (1 - t) + .15
            length = math.hypot(nx - x, ny - y) + .12
            tilt = math.degrees(math.atan2(nx - x, ny - y))
            fin["cubes"].append(cube([x - thickness / 2, y, -.3], [thickness, length, .6],
                                     0, [x, y, 0], [0, 0, tilt]))
            fin["cubes"].append(cube([x - .07, y + .08, -.34], [.14, length * .80, .07],
                                     2, [x, y, 0], [0, 0, tilt]))
        bones.append(fin)
    bones.append({"name": "crystal", "parent": "staff", "pivot": [0, 26, 0], "cubes": [
        cube([-.95, 24, -.95], [1.9, 4, 1.9], 3, [0, 26, 0], [0, 45, 0]),
        cube([-.7, 26.8, -.7], [1.4, 1.4, 1.4], 2, [0, 27.5, 0], [0, 45, 45]),
        cube([-.7, 23.8, -.7], [1.4, 1.4, 1.4], 2, [0, 24.5, 0], [0, 45, 45]),
        cube([-.12, 24.8, -1.04], [.24, 2.5, .16], 2)]})
    for index, tilt in enumerate((32, -32)):
        ring = {"name": f"orbit_{index}", "parent": "staff", "pivot": [0, 26, 0],
                "rotation": [tilt, 0, 0], "cubes": []}
        for segment in range(24):
            angle = segment * math.tau / 24
            x, z = 2.4 * math.cos(angle), 2.4 * math.sin(angle)
            ring["cubes"].append(cube([x - .10, 25.9, z - .36], [.2, .2, .72],
                                      2 if segment % 3 == 0 else 0, [x, 26, z], [0, -segment * 15, 0]))
        bones.append(ring)
    return {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": f"geometry.{MODEL}", "texture_width": SIZE,
                        "texture_height": SIZE, "visible_bounds_width": 3,
                        "visible_bounds_height": 5, "visible_bounds_offset": [0, .3, 0]},
        "bones": bones}]}


def animations():
    result = {}
    def animation(name, length, loop, bones):
        result[f"staff.{name}"] = {"loop": loop, "animation_length": length, "bones": bones}
    animation("idle", 6, True, {
        "crystal": {"rotation": {"0": [0, 0, 0], "6": [0, 360, 0]}},
        "orbit_0": {"rotation": {"0": [0, 0, 0], "6": [0, 360, 0]}},
        "orbit_1": {"rotation": {"0": [0, 0, 0], "6": [0, -360, 0]}}})
    animation("charge", 2, True, {
        "crystal": {"scale": {"0": [1, 1, 1], "1": [1.18, 1.08, 1.18], "2": [1, 1, 1]},
                    "rotation": {"0": [0, 0, 0], "2": [0, 360, 0]}},
        "orbit_0": {"rotation": {"0": [0, 0, 0], "2": [0, 360, 0]}, "scale": [1.25, 1, 1.25]},
        "orbit_1": {"rotation": {"0": [0, 0, 0], "2": [0, -360, 0]}, "scale": [1.4, 1, 1.4]}})
    for name, length in (("cleave", 3), ("uplift", 3), ("rift", 1), ("time_stop", 1)):
        animation(name, length, False, {
            "crystal": {"scale": {"0": [1, 1, 1], str(length * .6): [1.3, 1.15, 1.3], str(length): [1, 1, 1]}},
            "orbit_0": {"rotation": {"0": [0, 0, 0], str(length): [0, 360, 0]}}})
    result["staff.rift"]["bones"]["staff"] = {"rotation": {
        "0": [0, 0, 0], ".25": [90, 0, 0], ".85": [90, 0, 0], "1": [0, 0, 0]}}
    return {"format_version": "1.8.0", "animations": result}


def main():
    write_json(ASSETS / "geo/item" / f"{MODEL}.geo.json", geometry())
    write_json(ASSETS / "animations/item" / f"{MODEL}.animation.json", animations())
    displays = {
        "gui": {"rotation": [0, 0, -35], "translation": [0, -1.5, 0], "scale": [.39] * 3},
        "ground": {"translation": [0, 8, 0], "scale": [.3] * 3},
        "fixed": {"translation": [0, -1, 0], "scale": [.42] * 3},
        "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 4, 1], "scale": [.7] * 3},
        "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 4, 1], "scale": [.7] * 3},
        "firstperson_righthand": {"rotation": [0, 5, -9], "translation": [-1, 0, 0], "scale": [.47] * 3},
        "firstperson_lefthand": {"rotation": [0, -5, 9], "translation": [-1, 0, 0], "scale": [.47] * 3}}
    write_json(ASSETS / "models/item" / f"{MODEL}.json",
               {"parent": "builtin/entity", "gui_light": "front", "display": displays})
    atlas()
    print(f"Generated {MODEL}: {sum(len(b.get('cubes', [])) for b in geometry()['minecraft:geometry'][0]['bones'])} cubes, 6 animations")


if __name__ == "__main__":
    main()
