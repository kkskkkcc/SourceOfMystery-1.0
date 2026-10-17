"""Split Dragon Soul's baked tail into four animation-ready segments."""

import copy
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MODEL = ROOT / "src/main/resources/assets/sourceofmystery/geo/entity/dragon_soul.geo.json"


def main() -> None:
    payload = json.loads(MODEL.read_text(encoding="utf-8"))
    geometry = payload["minecraft:geometry"][0]
    bones = geometry["bones"]
    by_name = {b["name"]: b for b in bones}
    if "DragonTailMid" in by_name:
        print("tail segments already present")
        return
    base = by_name["DragonTailDetail"]
    cubes = base.get("cubes", [])
    if len(cubes) != 23:
        raise RuntimeError(f"unexpected DragonTailDetail cube count: {len(cubes)}")

    # The generated tail is ordered from the waist to the lower curl and back
    # up to the tip.  Keep each segment's armor band/spike with its main plate.
    base["cubes"] = copy.deepcopy(cubes[:4])
    segments = [
        ("DragonTailMid", [4, 10], "DragonTailDetail", [-2.7, 13.6, 7.1]),
        ("DragonTailTip", [10, 19], "DragonTailMid", [-8.0, 12.7, 9.8]),
        ("DragonTailEnd", [19, 23], "DragonTailTip", [-11.0, 20.1, 5.2]),
    ]
    for name, (start, end), parent, pivot in segments:
        bones.append({
            "name": name,
            "parent": parent,
            "pivot": pivot,
            "cubes": copy.deepcopy(cubes[start:end]),
        })
    MODEL.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("added tail segments: DragonTailMid, DragonTailTip, DragonTailEnd")


if __name__ == "__main__":
    main()
