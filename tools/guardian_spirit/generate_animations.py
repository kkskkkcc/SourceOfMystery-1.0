"""Generate small, editable GeckoLib keyframe animations for the 4ba spirit rig.

Angles are animation offsets from the Blockbench bind pose, not copied bind rotations.
Run: python tools/guardian_spirit/generate_animations.py
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MODEL = ROOT / "src/main/resources/assets/sourceofmystery/geo/entity/guardian_spirit.geo.json"
OUTPUT = ROOT / "src/main/resources/assets/sourceofmystery/animations/entity/guardian_spirit.animation.json"
BONES = {bone["name"] for bone in json.loads(MODEL.read_text(encoding="utf-8"))["minecraft:geometry"][0]["bones"]}


def animation(length, loop, tracks):
    for bone in tracks:
        if bone not in BONES:
            raise ValueError(f"Missing model bone: {bone}")
    result = {"animation_length": length, "bones": tracks}
    if loop:
        result["loop"] = True
    return result


def frames(channel, *values):
    return {channel: {str(time): vector for time, vector in values}}


def swing(tracks, bone, axis, values):
    tracks[bone] = frames("rotation", *((time, [value if i == axis else 0 for i in range(3)]) for time, value in values))


def motion_cycle(length, water=False):
    # The root remains level; movement is driven in Java. The body leans from its
    # waist and the head counter-rotates to keep the face toward the path.
    tracks = {
        "AllBody": {
            "rotation": {"0": [10 if water else 15, 0, -3], str(length/4): [15 if water else 21, 0, 2],
                         str(length/2): [9 if water else 14, 0, 3], str(3*length/4): [15 if water else 20, 0, -2],
                         str(length): [10 if water else 15, 0, -3]},
            "position": {"0": [0, 0, 0], str(length/4): [0, 2.2 if water else 1.4, 0],
                         str(length/2): [0, 0.1, 0], str(3*length/4): [0, -1 if water else -0.5, 0],
                         str(length): [0, 0, 0]},
        },
        "Head": frames("rotation", (0, [-12, 0, 0]),
                       (length/4, [-10, 1, 0]),
                       (length/2, [-12, 3, 0]),
                       (3*length/4, [-18, -1, 0]),
                       (length, [-12, 0, 0])),
        "UpBody": frames("rotation", (0, [0, 0, 0]), (length/2, [2, 0, 0]), (length, [0, 0, 0])),
        "LeftArm": frames("rotation", (0, [-13, -5, -19]), (length/2, [-24, 3, -26]), (length, [-13, -5, -19])),
        "RightArm": frames("rotation", (0, [-20, 4, 24]), (length/2, [-11, -4, 16]), (length, [-20, 4, 24])),
        "LeftForeArm": frames("rotation", (0, [-18, 0, -7]), (length/2, [-9, 0, -11]), (length, [-18, 0, -7])),
        "RightForeArm": frames("rotation", (0, [-10, 0, 9]), (length/2, [-17, 0, 5]), (length, [-10, 0, 9])),
    }
    # Quiet expression: paired blink at two distinct points in the long idle loop.
    blink = {"0": [1, 1, 1], "1.8": [1, 1, 1], "1.88": [1, 0.12, 1], "1.96": [1, 1, 1],
             "5.12": [1, 1, 1], "5.20": [1, 0.12, 1], "5.28": [1, 1, 1], str(length): [1, 1, 1]}
    if water:
        blink = {"0": [1, 1, 1], "3.4": [1, 1, 1], "3.5": [1, 0.12, 1], "3.6": [1, 1, 1], str(length): [1, 1, 1]}
    for bone in ("Eyelid", "LeftEyelid", "RightEyelid"):
        tracks[bone] = {"scale": blink}
    # The four nested rear-hair bones must never each receive a full swing:
    # their rotations add together and drive the tip through the torso.
    for index, bone in enumerate(("Hair", "FLongHair", "MLongHair", "LongHair", "hl_side",
                                  "front_h1", "corner_h1", "Ribbon")):
        phase = index % 3
        if bone in ("FLongHair", "MLongHair", "LongHair", "hl_side"):
            amplitude = 1.0 if bone != "hl_side" else 1.5
            values = [(0, 0), (length/4, -amplitude), (length/2, 0),
                      (3*length/4, amplitude), (length, 0)]
        else:
            values = [(0, 0), (length/4, -2.5-phase*0.3), (length/2, 0),
                      (3*length/4, 2+phase*0.3), (length, 0)]
        swing(tracks, bone, 0, values)
    for index, bone in enumerate(("Skirt", "RightSkirt", "LeftSkirt", "FrontSkirt", "BackSkirt")):
        swing(tracks, bone, 0, [(0, 3+index), (length/4, 8+index), (length/2, 1-index),
                                 (3*length/4, -5-index), (length, 3+index)])
    # Long white-blue wing blades in this rig are bone7 and bone4, not ElytraLocator.
    # Their mirrored yaw gives a restrained, slow flap while hovering.
    swing(tracks, "bone7", 1, [(0, -3), (length/4, -9), (length/2, -2), (3*length/4, 5), (length, -3)])
    swing(tracks, "bone4", 1, [(0, 3), (length/4, 9), (length/2, 2), (3*length/4, -5), (length, 3)])
    for index, bone in enumerate(("Breast2", "Breast3")):
        swing(tracks, bone, 0, [(0, 0), (length/4, 1.2+index*0.3), (length/2, 0),
                                 (3*length/4, -0.8-index*0.2), (length, 0)])
    if water:
        # A slow contraction followed by a stronger upward pulse, then a long glide.
        tracks["AllBody"]["position"] = {"0": [0, -1, 0], "1.3": [0, -2, 0],
                                            "2.5": [0, 2.6, 0], "4": [0, 4, 0],
                                            "7": [0, 0, 0], "10": [0, -1, 0]}
        tracks["LeftArm"]["rotation"] = {"0": [-18, 0, -10], "1.3": [14, 0, -28],
                                             "2.5": [-36, 0, -42], "4": [-23, 0, -36],
                                             "7": [-10, 0, -18], "10": [-18, 0, -10]}
        tracks["RightArm"]["rotation"] = {"0": [-18, 0, 10], "1.3": [14, 0, 28],
                                              "2.5": [-36, 0, 42], "4": [-23, 0, 36],
                                              "7": [-10, 0, 18], "10": [-18, 0, 10]}
    return animation(length, True, tracks)


def pose_sequence(length, keys, smile=False):
    tracks = {}
    for bone in ("AllBody", "Head", "LeftArm", "RightArm", "LeftForeArm", "RightForeArm", "LeftHand", "RightHand"):
        tracks[bone] = frames("rotation", *((t, p[bone]) for t, p in keys))
    if smile:
        for bone in ("Eyelid", "LeftEyelid", "RightEyelid"):
            tracks[bone] = frames("scale", (0, [1, 1, 1]), (0.5, [1, 0.72, 1]),
                                  (3.5, [1, 0.72, 1]), (3.8, [1, 1, 1]))
        for bone in ("LeftEyebrow", "RightEyebrow"):
            tracks[bone] = frames("rotation", (0, [0, 0, 0]), (0.5, [-7, 0, 0]),
                                   (3.5, [-7, 0, 0]), (3.8, [0, 0, 0]))
    return animation(length, False, tracks)


neutral = {bone: [0, 0, 0] for bone in ("AllBody", "Head", "LeftArm", "RightArm", "LeftForeArm", "RightForeArm", "LeftHand", "RightHand")}

love = dict(neutral, AllBody=[7, 0, -5], Head=[-16, -12, 12],
            LeftArm=[-53, -25, -36], RightArm=[-67, -30, 38],
            LeftForeArm=[-74, 0, -34], RightForeArm=[-80, 0, 30],
            LeftHand=[-12, 0, -18], RightHand=[-12, 0, 18])
charge = dict(neutral, AllBody=[14, 0, 0], Head=[-8, 0, 0],
              LeftArm=[-68, 13, -22], RightArm=[-68, -13, 22],
              LeftForeArm=[-32, 0, -30], RightForeArm=[-32, 0, 30],
              LeftHand=[-15, 0, -12], RightHand=[-15, 0, 12])
prayer = dict(neutral, AllBody=[-4, 0, 0], Head=[-4, 0, 0],
              LeftArm=[-46, -12, -45], RightArm=[-46, 12, 45],
              LeftForeArm=[-83, 0, -22], RightForeArm=[-83, 0, 22],
              LeftHand=[-9, 0, -8], RightHand=[-9, 0, 8])

animations = {
    "animation.guardian_spirit.idle": motion_cycle(8),
    "animation.guardian_spirit.water_float": motion_cycle(10, water=True),
    "animation.guardian_spirit.love": pose_sequence(3.8, [(0, neutral), (0.5, love), (3.5, love), (3.8, neutral)], True),
    "animation.guardian_spirit.attack_charge": pose_sequence(2, [(0, neutral), (0.35, charge), (1.7, charge), (2, neutral)]),
    "animation.guardian_spirit.attack_prayer": pose_sequence(3, [(0, neutral), (0.7, prayer), (2.7, prayer), (3, neutral)]),
}
OUTPUT.write_text(json.dumps({"format_version": "1.8.0", "animations": animations}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(OUTPUT)
