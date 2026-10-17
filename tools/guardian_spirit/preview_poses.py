"""Render representative Guardian Spirit poses with the local software previewer."""
import importlib.util
import json
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("geo_render", ROOT / "tools/weeping_death_lord/geo_render.py")
renderer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(renderer)
model = renderer.load(ROOT / "src/main/resources/assets/sourceofmystery/geo/entity/guardian_spirit.geo.json")
texture = Image.open(ROOT / "src/main/resources/assets/sourceofmystery/textures/entity/guardian_spirit.png")
animations = json.loads((ROOT / "src/main/resources/assets/sourceofmystery/animations/entity/guardian_spirit.animation.json").read_text())['animations']
samples = [("idle", 2), ("water_float", 2.5), ("love", 1.4), ("attack_charge", 1.2), ("attack_prayer", 1.4)]
sheet = Image.new("RGB", (340 * len(samples), 390), (27, 35, 52))
drawer = ImageDraw.Draw(sheet)
for index, (name, time) in enumerate(samples):
    animation = animations[f"animation.guardian_spirit.{name}"]
    pose = {}
    for bone, channels in animation['bones'].items():
        entry = {}
        for channel, value in channels.items():
            if isinstance(value, dict):
                timestamp = min(value, key=lambda key: abs(float(key) - time))
                vector = value[timestamp]
            else:
                vector = value
            entry[{"rotation": "rot", "position": "pos", "scale": "scale"}[channel]] = vector
        pose[bone] = entry
    frame, _, _ = renderer.render(model, texture, pose=pose, size=340, bg=(27, 35, 52, 255))
    sheet.paste(frame.convert("RGB"), (index * 340, 30))
    drawer.text((index * 340 + 12, 8), name, fill=(200, 225, 255))
output = ROOT / "tools/guardian_spirit/pose_preview.png"
sheet.save(output)
print(output)

# Side views expose hair/torso clipping that the front view hides.
side_samples = [("idle", 2.0), ("water_float", 2.5), ("love", 1.5), ("attack_prayer", 1.5)]
side_sheet = Image.new("RGB", (360 * len(side_samples), 390), (27, 35, 52))
side_draw = ImageDraw.Draw(side_sheet)
for index, (name, time) in enumerate(side_samples):
    animation = animations[f"animation.guardian_spirit.{name}"]
    pose = {}
    for bone, channels in animation['bones'].items():
        pose[bone] = {}
        for channel, keys in channels.items():
            if isinstance(keys, dict):
                stamp = min(keys, key=lambda key: abs(float(key) - time))
                vector = keys[stamp]
            else:
                vector = keys
            pose[bone][{"rotation": "rot", "position": "pos", "scale": "scale"}[channel]] = vector
    frame, _, _ = renderer.render(model, texture, pose=pose, yaw=90, size=360, bg=(27, 35, 52, 255))
    side_sheet.paste(frame.convert("RGB"), (index * 360, 30))
    side_draw.text((index * 360 + 12, 8), name + " side", fill=(200, 225, 255))
side_output = ROOT / "tools/guardian_spirit/hair_clearance_preview.png"
side_sheet.save(side_output)
print(side_output)
