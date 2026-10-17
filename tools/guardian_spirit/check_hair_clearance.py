"""Sample hair-vs-body intersections through all guardian animations.

This is a conservative geometry check; nearby hair pieces can overlap clothing
visually without penetrating solid torso cubes. All dimensions are model pixels.
"""
import importlib.util
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("geo_render", ROOT / "tools/weeping_death_lord/geo_render.py")
render = importlib.util.module_from_spec(spec)
sys.modules["geo_render"] = render
spec.loader.exec_module(render)
spec = importlib.util.spec_from_file_location("collide", ROOT / "tools/weeping_death_lord/collide.py")
collide = importlib.util.module_from_spec(spec)
spec.loader.exec_module(collide)

geo = render.load(ROOT / "src/main/resources/assets/sourceofmystery/geo/entity/guardian_spirit.geo.json")
bones = {bone["name"]: bone for bone in geo["bones"]}
animations = json.loads((ROOT / "src/main/resources/assets/sourceofmystery/animations/entity/guardian_spirit.animation.json").read_text(encoding="utf-8"))["animations"]


def descends(name, ancestor):
    while name in bones and bones[name].get("parent"):
        name = bones[name]["parent"]
        if name == ancestor:
            return True
    return False


HAIR = [name for name, bone in bones.items() if bone.get("cubes") and (name == "Hair" or descends(name, "Hair"))]
# Solid body; exclude the arms because crossing in front during affection and
# attack is intentional, but include chest and rear armor under the hair.
BODY = [name for name, bone in bones.items() if bone.get("cubes") and
        (name in {"UpperBody", "Breast2", "Breast3", "Skirt", "RightSkirt", "LeftSkirt", "BackSkirt"}
         or descends(name, "bone10") or descends(name, "bone"))]
BODY = [name for name in BODY if name not in HAIR and name not in {"bone4", "bone7"}]


def interpolate(keys, t):
    if isinstance(keys, list):
        return keys
    ordered = sorted((float(time), vec) for time, vec in keys.items())
    if t <= ordered[0][0]:
        return ordered[0][1]
    for (a, left), (b, right) in zip(ordered, ordered[1:]):
        if a <= t <= b:
            u = (t-a)/(b-a)
            return [l+(r-l)*u for l, r in zip(left, right)]
    return ordered[-1][1]


def pose_at(anim, t):
    pose = {}
    for name, channels in anim["bones"].items():
        pose[name] = {}
        for channel, keys in channels.items():
            pose[name][{"rotation": "rot", "position": "pos", "scale": "scale"}[channel]] = interpolate(keys, t)
    return pose


if __name__ == "__main__":
    if "--sweep" in sys.argv:
        for name, times in (("idle", (0.0, 2.0, 4.0, 6.0)),
                            ("water_float", (0.0, 2.5, 5.0, 7.5)),
                            ("love", (0.5, 1.5, 3.5)),
                            ("attack_charge", (0.0, 1.0)),
                            ("attack_prayer", (0.7, 2.9))):
            anim = animations["animation.guardian_spirit." + name]
            for t in times:
                base = pose_at(anim, t)
                scores = []
                for head_angle in range(-28, 19, 2):
                    pose = json.loads(json.dumps(base))
                    pose["Head"]["rot"][0] = head_angle
                    depth = max(collide.penetration(geo, pose, [hair], BODY, n=3, shrink=0.12) for hair in HAIR)
                    scores.append((round(depth, 2), head_angle))
                print(name, t, "best head pitch", min(scores))
                if name in {"love", "attack_prayer"} and t in {0.5, 2.9}:
                    print("  sample", [score for score in scores if score[1] in {-16, -12, -8, -4, 0, 8, 16}])
        sys.exit(0)
    baseline = max((collide.penetration(geo, {}, [hair], BODY, n=3, shrink=0.12), hair) for hair in HAIR)
    print("bind pose max penetration", round(baseline[0], 2), "px,", baseline[1])
    for name, anim in animations.items():
        worst = (-1, 0, "")
        for i in range(int(anim["animation_length"] * 10) + 1):
            t = min(anim["animation_length"], i/10)
            pose = pose_at(anim, t)
            for hair in HAIR:
                depth = collide.penetration(geo, pose, [hair], BODY, n=3, shrink=0.12)
                if depth > worst[0]:
                    worst = depth, t, hair
        print(name.split(".")[-1], "max penetration", round(worst[0], 2), "px at", worst[1], "s,", worst[2])
