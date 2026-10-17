"""Restore the stable Dragon Soul choreography and retarget it to the current rig.

The current model rebinds ``longspear`` to ``RightHand``.  The historical
animations were authored for an off-body weapon locator and therefore contain
large position tracks that throw the spear out of the hand.  This rebuild uses
the historical body choreography, keeps the current rig's weapon rotations,
and adds a dedicated ground impact / rise / ready sequence for the new intro.
"""

from __future__ import annotations

import copy
import json
import subprocess
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/sourceofmystery"
ANIM_PATH = ASSETS / "animations/entity/dragon_soul.animation.json"
CURRENT_BACKUP = ROOT / "build/dragon_soul_actions_current.json"
WEAPON_SOURCE = ROOT / "tools/dragon_soul/source/weapon_tracks.json"


def load_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def historical_animations() -> dict:
    raw = subprocess.run(
        [
            "git",
            "show",
            "HEAD:src/main/resources/assets/sourceofmystery/animations/entity/dragon_soul.animation.json",
        ],
        cwd=ROOT,
        check=True,
        capture_output=True,
    ).stdout
    return json.loads(raw.decode("utf-8"))["animations"]


def channel(animation: dict, bone: str, name: str = "rotation") -> dict | None:
    track = animation.get("bones", {}).get(bone, {}).get(name)
    return copy.deepcopy(track) if track is not None else None


def set_channel(animation: dict, bone: str, name: str, values: dict) -> None:
    animation.setdefault("bones", {}).setdefault(bone, {})[name] = values


def add_secondary_motion(animations: dict) -> None:
    """Add visible secondary motion to hair and the four tail segments."""
    tail_bones = [
        ("DragonTailDetail", 1.0),
        ("DragonTailMid", 1.8),
        ("DragonTailTip", 2.6),
        ("DragonTailEnd", 3.4),
    ]

    def tail_track(length: float, points: list[tuple[float, float, float]]) -> dict:
        return {
            f"{t * length:.3f}": [0.0, round(y, 3), round(z, 3)]
            for t, y, z in points
        }

    def set_tail(a: dict, points: list[tuple[float, float, float]], yaw: float, roll: float) -> None:
        length = float(a.get("animation_length", 1.0))
        for bone, weight in tail_bones:
            a.setdefault("bones", {})[bone] = {
                "rotation": tail_track(
                    length,
                    [(t, y * weight * yaw, z * weight * roll) for t, y, z in points],
                )
            }

    loops = {
        "boss_hover": (3.0, 2.2, 1.6, 1.0, 0.75),
        "boss_stance": (2.4, 1.5, 1.1, 0.82, 0.65),
        "boss_fly": (1.0, 3.0, 2.4, 1.55, 1.25),
        "boss_carry": (0.8, 2.5, 2.0, 1.25, 1.0),
    }
    for name, (length, hair_amp, tail_amp, yaw, roll) in loops.items():
        a = animations[name]
        a.setdefault("bones", {})["HairFaceDetail"] = {
            "rotation": {
                "0.0": [0.0, 0.0, 0.0],
                f"{length * 0.5:.3f}": [hair_amp, 0.0, -0.8],
                f"{length:.3f}": [0.0, 0.0, 0.0],
            }
        }
        set_tail(a, [(0.0, 0.0, 0.0), (0.25, tail_amp, -1.0),
                     (0.5, -tail_amp * 0.85, 0.8), (0.75, tail_amp * 0.65, -0.5),
                     (1.0, 0.0, 0.0)], yaw, roll)

    for name, a in animations.items():
        if name in loops or name in {"boss_blink", "boss_spear_hidden"} or name.startswith("boss_voice_"):
            continue
        if name == "boss_intro_dive":
            set_tail(a, [(0.0, 0.0, 0.0), (0.28, -10.0, 6.0),
                         (0.62, 18.0, -10.0), (0.84, 8.0, -4.0), (1.0, 0.0, 0.0)], 1.2, 1.0)
        elif name == "boss_intro_point":
            set_tail(a, [(0.0, 0.0, 0.0), (0.07, 7.0, -3.0),
                         (0.23, -9.0, 4.0), (0.5, 3.5, -1.5), (1.0, 0.0, 0.0)], 1.0, 0.8)
        elif "thrust" in name or "spear" in name:
            set_tail(a, [(0.0, 0.0, 0.0), (0.28, -8.0, 3.0),
                         (0.52, 13.0, -5.0), (0.78, 4.0, -2.0), (1.0, 0.0, 0.0)], 1.0, 0.85)
        elif "cleave" in name or "slash" in name or "ult" in name:
            set_tail(a, [(0.0, 0.0, 0.0), (0.28, 10.0, -4.0),
                         (0.52, -16.0, 7.0), (0.78, -5.0, 2.0), (1.0, 0.0, 0.0)], 1.0, 0.9)
        elif "kick" in name or "grab" in name:
            set_tail(a, [(0.0, 0.0, 0.0), (0.30, -7.0, 4.0),
                         (0.58, 11.0, -5.0), (0.80, 3.0, -1.0), (1.0, 0.0, 0.0)], 1.0, 0.9)
        else:
            set_tail(a, [(0.0, 0.0, 0.0), (0.35, -5.0, 2.0),
                         (0.62, 7.0, -3.0), (0.82, 2.0, -1.0), (1.0, 0.0, 0.0)], 0.85, 0.7)


def retarget_weapon_tracks(animations: dict, current: dict) -> None:
    """Use the current hand-bound weapon channels and discard old translations."""
    for name, a in animations.items():
        old_weapon = current.get(name, {}).get("bones", {}).get("longspear")
        if old_weapon is None:
            continue
        # Only current rotations are valid after the weapon was baked at the hand.
        rotation = old_weapon.get("rotation")
        if rotation:
            set_channel(a, "longspear", "rotation", rotation)
        else:
            a.setdefault("bones", {}).pop("longspear", None)
        a.get("bones", {}).get("longspear", {}).pop("position", None)
        a.get("bones", {}).get("longspear", {}).pop("scale", None)
    # The stable stance body leans back more than the new hand-bound rig.  A
    # lower diagonal ready angle keeps the spear clear of the face and matches
    # the reference silhouette while the historical attack arcs stay intact.
    stance = animations.get("boss_stance", {}).get("bones", {}).get("longspear", {}).get("rotation")
    if stance:
        for key in stance:
            stance[key] = [70.0, 20.0, -20.0]


def add_intro_dive(animations: dict, current: dict) -> None:
    """The body-first dive used by the server timeline before impactGround()."""
    if "boss_intro_dive" in current:
        animations["boss_intro_dive"] = copy.deepcopy(current["boss_intro_dive"])
        return
    animations["boss_intro_dive"] = {
        "animation_length": 1.2,
        "bones": {
            "AllBody": {"rotation": {"0.0": [45, 0, 0], "0.35": [68, 0, 0], "1.2": [68, 0, 0]}},
            "UpperBody": {"rotation": {"0.0": [0, 0, 0], "0.35": [-16, 0, 0], "1.2": [-16, 0, 0]}},
            "RightArm": {"rotation": {"0.0": [-25, 0, 12], "0.35": [-42, 0, -12], "1.2": [-42, 0, -12]}},
            "RightForeArm": {"rotation": {"0.0": [-28, 0, 0], "0.35": [-34, 0, 0], "1.2": [-34, 0, 0]}},
            "LeftArm": {"rotation": {"0.0": [40, 0, -15], "0.35": [56, 0, -34], "1.2": [56, 0, -34]}},
            "LeftForeArm": {"rotation": {"0.0": [-20, 0, 0], "0.35": [-28, 0, 0], "1.2": [-28, 0, 0]}},
            "RightLeg": {"rotation": {"0.0": [15, 0, 3], "0.35": [-34, 0, 7], "1.2": [-34, 0, 7]}},
            "RightLowerLeg": {"rotation": {"0.0": [30, 0, 0], "0.35": [72, 0, 0], "1.2": [72, 0, 0]}},
            "LeftLeg": {"rotation": {"0.0": [25, 0, -3], "0.35": [30, 0, -7], "1.2": [30, 0, -7]}},
            "LeftLowerLeg": {"rotation": {"0.0": [45, 0, 0], "0.35": [64, 0, 0], "1.2": [64, 0, 0]}},
            "fly": {"rotation": {"0.0": [0, 40, 30], "0.35": [0, -30, -60], "1.2": [0, -30, -60]}},
            "fly2": {"rotation": {"0.0": [0, -40, -30], "0.35": [0, 30, 60], "1.2": [0, 30, 60]}},
            "Root": {"position": {"0.0": [0, 0, 0], "0.35": [0, -1.5, 0], "1.2": [0, -2.0, 0]}},
        },
    }


def rebuild_intro_point(animations: dict, current: dict) -> None:
    """Use the long current intro pose and make its first rise readable."""
    source = current.get("boss_intro_point")
    if source is None:
        return
    a = copy.deepcopy(source)
    a["animation_length"] = 5.2
    # The generated intro already contains the impact-to-rise timing.  Hold the
    # final ready pose for the remaining cinematic time instead of snapping out.
    for bone, track in a.get("bones", {}).items():
        for kind, keys in track.items():
            if not isinstance(keys, dict) or not keys:
                continue
            last_key = max(keys, key=lambda k: float(k))
            last_value = copy.deepcopy(keys[last_key])
            keys.setdefault("5.2", last_value)
    # Add a small head settle and secondary layers so the final pose breathes.
    a.setdefault("bones", {})["Head"] = {
        "rotation": {"0.0": [8.0, -9.0, 0.0], "0.35": [4.0, -5.0, 0.0], "1.2": [2.0, -3.0, 0.0], "5.2": [2.0, -3.0, 0.0]}
    }
    a["bones"]["HairFaceDetail"] = {"rotation": {"0.0": [8, 0, 0], "0.35": [-2, 0, 0], "1.2": [1, 0, 0], "5.2": [0, 0, 0]}}
    a["bones"]["DragonTailDetail"] = {"rotation": {"0.0": [0, 0, 0], "0.35": [0, 5, -2], "1.2": [0, 2, -1], "5.2": [0, 0, 0]}}
    animations["boss_intro_point"] = a


def hand_off_intro_to_stance(animations: dict) -> None:
    """End the cinematic on the exact first frame of the stable stance loop."""
    land = animations["boss_intro_point"].setdefault("bones", {})
    stance = animations["boss_stance"].get("bones", {})
    for bone in ["AllBody", "UpperBody", "RightArm", "RightForeArm", "longspear", "RightLeg", "LeftLeg", "Root"]:
        if bone not in land or bone not in stance:
            continue
        for kind, keys in land[bone].items():
            if kind not in stance[bone] or not keys:
                continue
            first_key = min(stance[bone][kind], key=float)
            last_key = max(keys, key=float)
            keys[last_key] = copy.deepcopy(stance[bone][kind][first_key])


def validate(animations: dict) -> None:
    geo = load_json(ASSETS / "geo/entity/dragon_soul.geo.json")["minecraft:geometry"][0]
    model_bones = {b["name"] for b in geo["bones"]}
    missing = sorted({bone for a in animations.values() for bone in a.get("bones", {}) if bone not in model_bones})
    if missing:
        raise RuntimeError(f"animation bones missing from current model: {missing}")
    required = {"boss_hover", "boss_stance", "boss_fly", "boss_cleave", "boss_thrust", "boss_slash", "boss_intro_dive", "boss_intro_point"}
    if not required.issubset(animations):
        raise RuntimeError(f"missing rebuilt actions: {sorted(required - set(animations))}")


def clamp_keyframes(animations: dict) -> None:
    """Remove historical cloth settle keys that sat past their clip length."""
    for a in animations.values():
        length = float(a.get("animation_length", 0.0))
        for track in a.get("bones", {}).values():
            for kind, keys in list(track.items()):
                if not isinstance(keys, dict):
                    continue
                out = {}
                tail = None
                for key, value in sorted(keys.items(), key=lambda item: float(item[0])):
                    if float(key) <= length + 1.0e-6:
                        out[key] = value
                    else:
                        tail = value
                if tail is not None:
                    out[f"{length:g}"] = tail
                track[kind] = out


def main() -> None:
    historical = historical_animations()
    current = load_json(CURRENT_BACKUP)["animations"] if CURRENT_BACKUP.exists() else load_json(ANIM_PATH)["animations"]
    if WEAPON_SOURCE.exists():
        weapon_tracks = load_json(WEAPON_SOURCE)
        for name, track in weapon_tracks.items():
            current.setdefault(name, {}).setdefault("bones", {})["longspear"] = track
    rebuilt = copy.deepcopy(historical)
    # The enhanced rig exposes a visible UpperBody child in addition to the
    # historical UpBody controller.  Keep its authored breathing track for the
    # base stance so the intro can hand off to a real, matching frame.
    current_upper = current.get("boss_stance", {}).get("bones", {}).get("UpperBody")
    if current_upper:
        rebuilt["boss_stance"].setdefault("bones", {})["UpperBody"] = copy.deepcopy(current_upper)
    retarget_weapon_tracks(rebuilt, current)
    add_intro_dive(rebuilt, current)
    rebuild_intro_point(rebuilt, current)
    add_secondary_motion(rebuilt)
    hand_off_intro_to_stance(rebuilt)
    clamp_keyframes(rebuilt)
    validate(rebuilt)
    payload = {"format_version": "1.8.0", "animations": rebuilt}
    ANIM_PATH.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("rebuilt", len(rebuilt), "animations from historical choreography; weapon tracks retargeted to RightHand")


if __name__ == "__main__":
    main()
