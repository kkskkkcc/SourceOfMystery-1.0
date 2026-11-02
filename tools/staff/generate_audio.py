"""Original synthesized spell cues; deterministic sources and no sampled recordings."""
from pathlib import Path
import array
import json
import math
import random
import shutil
import subprocess
import wave

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/sourceofmystery"
SPELLS = ["annihilation", "earth_split", "earth_lift", "time_stop", "space_rift"]
CHARGE = [24, 3, 3, .3, 1]
NOTES = [(110, 164.81, 220, 261.63), (65.41, 98, 130.81), (146.83, 185, 220, 293.66),
         (261.63, 246.94, 196), (174.61, 220, 349.23)]


def main():
    ffmpeg = shutil.which("ffmpeg")
    if not ffmpeg:
        raise SystemExit("ffmpeg is required to encode the generated WAV sources as Vorbis")
    output = ASSETS / "sounds/staff"
    source = ROOT / "build/staff-audio"
    output.mkdir(parents=True, exist_ok=True)
    source.mkdir(parents=True, exist_ok=True)
    sounds_path = ASSETS / "sounds.json"
    sounds = json.loads(sounds_path.read_text(encoding="utf-8"))
    rate = 22050
    for index, spell in enumerate(SPELLS):
        for phase in ("charge", "release"):
            duration = CHARGE[index] if phase == "charge" else (7 if index == 1 else 3)
            samples = array.array("h")
            rng = random.Random(index * 10 + (phase == "release"))
            notes = NOTES[index]
            for n in range(int(duration * rate)):
                t = n / rate
                attack = min(1, t / .08)
                tail = min(1, (duration - t) / .5)
                position = t / duration
                frequency = notes[int(t * (2 if phase == "charge" else 3)) % len(notes)]
                if index == 2:
                    frequency *= 1 + .5 * position
                if index == 3:
                    frequency *= 1 + 3 * math.exp(-t * 7)
                voice = math.sin(math.tau * frequency * t) * .23
                voice += math.sin(math.tau * frequency * 1.5 * t) * .09
                voice += math.sin(math.tau * notes[0] * .5 * t) * .14
                pulse = (.65 + .35 * math.sin(math.tau * t * 2))
                noise = (rng.random() * 2 - 1) * (.07 if index in (0, 1, 3) else .025)
                if phase == "release":
                    voice += .35 * math.sin(math.tau * 44 * t) * math.exp(-t * 4)
                    noise *= 1 + 2 * math.exp(-t * 5)
                value = max(-.9, min(.9, (voice * pulse + noise) * attack * tail * .75))
                samples.append(round(value * 32767))
            wav = source / f"{spell}_{phase}.wav"
            with wave.open(str(wav), "wb") as stream:
                stream.setparams((1, 2, rate, 0, "NONE", "not compressed"))
                stream.writeframes(samples.tobytes())
            subprocess.run([ffmpeg, "-nostdin", "-y", "-loglevel", "error", "-i", str(wav), "-c:a", "libvorbis", "-q:a", "5",
                            str(output / f"{spell}_{phase}.ogg")], check=True)
            sounds[f"staff.{spell}.{phase}"] = {
                "subtitle": f"subtitles.sourceofmystery.staff.{spell}",
                "sounds": [{"name": f"sourceofmystery:staff/{spell}_{phase}", "stream": duration > 8, "attenuation_distance": 64}]}
    sounds_path.write_text(json.dumps(sounds, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("Generated 10 original spell cues")


if __name__ == "__main__":
    main()
