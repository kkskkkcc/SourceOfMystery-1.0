"""Original procedural score and cues for the 神威天佑 ascension CG.

The story: the player rises into space, sees the divine armor, climbs to it,
is granted it and becomes a god on the throne. The harmony follows that arc:
D minor while approaching, the dominant before the touch, D major at the bestowal.

Run from the repository root:  python tools/divine/generate_cg_audio.py
Writes sounds/divine_cg/*.ogg, the sounds.json entries, subtitles and a full
preview mix in art/divine_cinematic. Cue times live in cinematic/divine_ascension.json
(written by author_ascension.py); this script reads them for the preview mix only.
"""
import json
import math
import subprocess
import sys
import wave
from pathlib import Path

import numpy as np
from scipy.signal import butter, fftconvolve, sosfilt

ROOT = Path(__file__).resolve().parents[2]
import shutil  # noqa: E402


def find_ffmpeg():
    # Prefer ffmpeg on PATH; fall back to the imageio-ffmpeg build used by older asset scripts.
    found = shutil.which('ffmpeg')
    if found:
        return found
    sys.path.insert(0, str(ROOT / 'build/outsider-audio-deps'))
    import imageio_ffmpeg
    return imageio_ffmpeg.get_ffmpeg_exe()


FFMPEG = find_ffmpeg()
ASSETS = ROOT / 'src/main/resources/assets/sourceofmystery'
SND = ASSETS / 'sounds/divine_cg'
ART = ROOT / 'art/divine_cinematic'
RATE = 44100
rng = np.random.default_rng(20261107)

# Real seconds of the CG for each story second (same table as DivineCGTimeline).
REAL = [0, 8, 14, 18, 23, 35, 40, 47, 51, 59, 65, 68, 68.35, 68.7]
STORY = [0, 4, 8, 10, 12, 18.5, 21, 24.1, 25.5, 29.5, 32, 35, 35.35, 35.7]


def real(story):
    return float(np.interp(story, STORY, REAL))


def hz(note):
    """Note name like 'D3', 'F#4', 'Bb2' to frequency."""
    names = {'C': 0, 'D': 2, 'E': 4, 'F': 5, 'G': 7, 'A': 9, 'B': 11}
    n = names[note[0]]
    rest = note[1:]
    if rest[0] in '#b':
        n += 1 if rest[0] == '#' else -1
        rest = rest[1:]
    return 440.0 * 2 ** ((n + 12 * (int(rest) + 1) - 69) / 12)


# ---------------------------------------------------------------- building blocks
def times(seconds):
    return np.arange(int(round(seconds * RATE))) / RATE


def band(x, lo, hi, order=2):
    return sosfilt(butter(order, [lo, hi], btype='bandpass', fs=RATE, output='sos'), x)


def low(x, cut, order=2):
    return sosfilt(butter(order, cut, btype='lowpass', fs=RATE, output='sos'), x)


def high(x, cut, order=2):
    return sosfilt(butter(order, cut, btype='highpass', fs=RATE, output='sos'), x)


def env(t, attack, hold_end, release):
    """Smooth attack to 1, hold until hold_end, then release to 0."""
    a = np.clip(t / max(attack, 1e-4), 0, 1)
    r = np.clip(1 - (t - hold_end) / max(release, 1e-4), 0, 1)
    return (a * a * (3 - 2 * a)) * (r * r * (3 - 2 * r))


VOWELS = {'a': [(800, 80), (1150, 90), (2900, 120)], 'o': [(450, 70), (800, 80), (2830, 100)],
          'u': [(325, 50), (700, 60), (2530, 170)], 'e': [(400, 60), (1600, 80), (2700, 120)]}


def choir(t, notes, vowel='a', voices=5, brightness=1.0, vibrato=.0045):
    """Additive choir: detuned voices with vibrato, harmonics shaped by vowel formants."""
    out = np.zeros((len(t), 2))
    formants = VOWELS[vowel]
    for note in notes:
        f0 = hz(note) if isinstance(note, str) else note
        for v in range(voices):
            detune = 2 ** ((rng.uniform(-7, 7)) / 1200)
            rate = rng.uniform(4.6, 5.6)
            phase_v = rng.uniform(0, 2 * math.pi)
            drift = 1 + vibrato * np.sin(2 * math.pi * rate * t + phase_v) * np.clip(t / 1.2, 0, 1)
            inst = f0 * detune * drift
            phase = 2 * math.pi * np.cumsum(inst) / RATE
            voice = np.zeros(len(t))
            for h in range(1, 24):
                fh = f0 * h
                if fh > 5000:
                    break
                gain = sum(math.exp(-((fh - fc) / bw) ** 2 / 2) for fc, bw in formants) + .35 / h ** 1.6
                gain *= brightness ** (h / 6)
                voice += np.sin(h * phase + rng.uniform(0, 6.28)) * gain
            pan = rng.uniform(-.6, .6)
            out[:, 0] += voice * (1 - pan) * .5
            out[:, 1] += voice * (1 + pan) * .5
    breath = band(rng.normal(size=len(t)), 2500, 6000) * .005
    out += breath[:, None]
    return out / max(1, len(notes) * voices)


BELL_PARTIALS = [(.5, 1.0, 1.1), (1.0, .9, .8), (1.19, .55, .62), (1.56, .38, .5), (2.0, .3, .42),
                 (2.74, .22, .33), (3.76, .15, .24), (5.4, .09, .16)]


def bell(t, f0, decay=3.0, gain=1.0):
    x = np.zeros(len(t))
    for ratio, g, d in BELL_PARTIALS:
        x += np.sin(2 * math.pi * f0 * ratio * t) * g * np.exp(-t / (decay * d))
    strike = high(rng.normal(size=len(t)), 2500) * np.exp(-t * 60) * .25
    return (x + strike) * gain * np.clip(t / .002, 0, 1)


def at(total, offset, clip):
    """Place a mono or stereo clip into a stereo buffer of length total at offset seconds."""
    out = np.zeros((total, 2))
    n = int(round(offset * RATE))
    if n >= total:
        return out
    c = clip if clip.ndim == 2 else np.stack([clip, clip], 1)
    c = c[:total - n]
    out[n:n + len(c)] += c
    return out


def reverb(x, seconds=3.2, wet=.35, tone=6000, pre=.025):
    """Stereo convolution reverb with a decaying, darkening noise tail."""
    n = int(seconds * RATE)
    tt = np.arange(n) / RATE
    tails = []
    for ch in range(2):
        noise = rng.normal(size=n) * np.exp(-tt * 6.9 / seconds)
        noise = low(noise, tone)
        noise[:int(pre * RATE)] = 0
        tails.append(noise / np.sqrt(np.sum(noise ** 2)))
    wet_l = fftconvolve(x[:, 0], tails[0])[:len(x)]
    wet_r = fftconvolve(x[:, 1], tails[1])[:len(x)]
    return x * (1 - wet) + np.stack([wet_l, wet_r], 1) * wet * 2.2


def stereo(x):
    return x if x.ndim == 2 else np.stack([x, x], 1)


# ---------------------------------------------------------------- cues
def c_ascent():
    t = times(8.8)
    # Rising wind: noise bands climbing in pitch and strength, plus a sub swell.
    rise = np.clip(t / 8, 0, 1)
    wind = np.zeros(len(t))
    noise = rng.normal(size=len(t))
    for lo, hi, w in ((120, 500, 1.0), (400, 1400, .7), (1200, 4200, .45)):
        wind += band(noise, lo, hi) * w
    wind *= (.15 + .85 * rise ** 2) * env(t, .6, 7.6, 1.0)
    sweep = np.sin(2 * math.pi * np.cumsum(30 + 40 * rise ** 2) / RATE) * .55 * rise ** 1.5 * env(t, .3, 7.4, 1.2)
    breath = choir(t, ['D3', 'A3'], 'u', 3, .6) * (rise ** 3)[:, None] * 2.2
    x = stereo(wind * .6 + sweep) + breath
    # Space break: the wind cuts and a single high bell opens the void.
    x[int(7.6 * RATE):] *= np.linspace(1, .15, len(x) - int(7.6 * RATE))[:, None]
    x += at(len(t), 7.7, bell(times(1.1), hz('D6'), 1.6, .25))
    return reverb(x, 2.8, .3)


def c_void():
    t = times(17)
    drone = choir(t, ['D2', 'A2', 'D3'], 'u', 4, .5) * env(t, 3, 14, 3)[:, None]
    hum = stereo(np.sin(2 * math.pi * hz('D1') * t) * .25 * env(t, 4, 13, 4))
    x = drone * 1.4 + hum
    # Sparse star glints.
    for k in range(9):
        when = rng.uniform(1.5, 15)
        note = rng.choice(['A5', 'D6', 'E6', 'F6', 'A6'])
        g = bell(times(2.5), hz(note), 1.2, .05)
        pan = rng.uniform(-.8, .8)
        x += at(len(t), when, np.stack([g * (1 - pan), g * (1 + pan)], 1))
    return reverb(x, 4.5, .45)


def c_call():
    """The armor calls: a three-note bell motif, then a thin answering voice."""
    t = times(6.5)
    x = np.zeros((len(t), 2))
    for k, (when, note) in enumerate(((0, 'A4'), (.55, 'D5'), (1.1, 'E5'), (2.4, 'A4'), (2.95, 'D5'), (3.5, 'F5'))):
        x += at(len(t), when, bell(times(3), hz(note), 2.2, .22 if k < 3 else .16))
    voice = choir(times(4.5), ['D5', 'A5'], 'o', 3, 1.1) * env(times(4.5), 1.2, 3.0, 1.5)[:, None] * .8
    x += at(len(t), 1.6, voice)
    return reverb(x, 4.0, .45)


def c_stairs():
    """Steps condense from light: a rising crystalline arpeggio over a stone bloom."""
    t = times(6)
    x = np.zeros((len(t), 2))
    for k, note in enumerate(['D4', 'F4', 'A4', 'D5', 'F5', 'A5', 'D6']):
        pan = -.5 + k / 6
        g = bell(times(3), hz(note), 1.4, .12)
        x += at(len(t), .15 + k * .21, np.stack([g * (1 - pan), g * (1 + pan)], 1))
    bloom = low(rng.normal(size=len(t)), 300) * env(t, .4, 1.4, 2.5) * .5
    x += stereo(bloom + np.sin(2 * math.pi * hz('D2') * t) * env(t, .5, 2, 3) * .25)
    return reverb(x, 3.5, .4)


def c_step():
    """One footstep on a celestial stair: soft stone thump with a faint glass ring."""
    t = times(1.0)
    thump = np.sin(2 * math.pi * (95 * t + 20 * (1 - np.exp(-t * 40)))) * np.exp(-t * 26) * .9
    scuff = band(rng.normal(size=len(t)), 300, 2400) * np.exp(-t * 45) * .35
    ring = bell(t, hz('A5'), .5, .05)
    return reverb(stereo(thump + scuff + ring), 1.6, .25)


def c_approach():
    """Approach bed from the first step to the bestowal: D minor, Bb, G minor, then A."""
    t = times(25)
    x = np.zeros((len(t), 2))
    chords = [(0, 7, ['D3', 'A3', 'D4', 'F4']), (6, 13, ['Bb2', 'F3', 'D4', 'F4']),
              (12, 19, ['G2', 'D3', 'Bb3', 'G4']), (18, 25, ['A2', 'E3', 'C#4', 'A4', 'E5'])]
    for start, end, notes in chords:
        seg = times(end - start)
        rise = .55 + .45 * (start / 18)
        c = choir(seg, notes, 'a' if start >= 12 else 'o', 4, .8 + .4 * start / 18) * env(seg, 2.2, end - start - 2, 2.2)[:, None]
        x += at(len(t), start, c * rise)
    swell = np.clip((t - 16) / 9, 0, 1) ** 2
    x += stereo(band(rng.normal(size=len(t)), 3000, 7000) * swell * .025)
    x *= np.clip(t / 3, 0, 1)[:, None]
    return reverb(x, 4.5, .4)


def c_heart():
    """Heartbeat while the hand rises to the chest, quickening."""
    t = times(4.5)
    x = np.zeros(len(t))
    when = 0.0
    period = .95
    while when < 3.8:
        for dt, g in ((0, 1.0), (.17, .65)):
            q = times(.4)
            beat = np.sin(2 * math.pi * (48 * q + 14 * (1 - np.exp(-q * 30)))) * np.exp(-q * 18) * g
            x += at(len(t), when + dt, low(beat, 160))[:, 0]
        when += period
        period *= .86
    return reverb(stereo(x), 1.8, .2)


def c_touch():
    """Fingers meet the armor: a glass chime, then an inhaled swell into the bestowal."""
    t = times(4.7)
    chime = bell(times(3), hz('E6'), 1.8, .3) + bell(times(3), hz('A6'), 1.4, .18)
    x = at(len(t), 0, chime)
    rise = np.clip(t / 4.6, 0, 1) ** 2.5
    air = band(rng.normal(size=len(t)), 800, 7000) * rise * .35
    voices = choir(t, ['A3', 'E4', 'A4', 'C#5'], 'a', 4, 1.3) * (rise * 1.6)[:, None]
    x += stereo(air) + voices
    x[-int(.06 * RATE):] *= np.linspace(1, 0, int(.06 * RATE))[:, None]
    return reverb(x, 3.0, .35)


def c_bestowal():
    """The armor is granted: D major bloom, sub drop, bells and a bright shimmer."""
    t = times(7)
    x = choir(t, ['D3', 'A3', 'D4', 'F#4', 'A4', 'D5', 'F#5'], 'a', 5, 1.4) * env(t, .05, 4.5, 2.5)[:, None] * 1.6
    sub = np.sin(2 * math.pi * (hz('D1') * t + 6 * (1 - np.exp(-t * 5)))) * np.exp(-t * .9) * .7
    burst = band(rng.normal(size=len(t)), 2000, 8000) * np.exp(-t * 4) * .18
    x += stereo(sub + burst)
    for k, note in enumerate(['D5', 'A5', 'D6', 'F#6', 'A6']):
        x += at(len(t), k * .09, bell(times(4), hz(note), 2.5, .14))
    return reverb(x, 5.0, .45, tone=8000)


def c_armor():
    """Plates lock onto the body: a run of small metal clicks and a resonant ring."""
    t = times(2.4)
    x = np.zeros(len(t))
    for k in range(7):
        when = .08 + k * .15 + rng.uniform(-.02, .02)
        q = times(.25)
        click = band(rng.normal(size=len(q)), 2500, 9000) * np.exp(-q * 90)
        tone = sum(np.sin(2 * math.pi * f * q) * np.exp(-q * d) for f, d in ((1850 + 120 * k, 40), (2950 + 90 * k, 55)))
        x += at(len(t), when, click * .6 + tone * .25)[:, 0]
    x += at(len(t), 1.15, bell(times(1.25), hz('D5'), 1.0, .4))[:, 0]
    return reverb(stereo(x), 1.8, .28)


def c_divinity():
    """Becoming a god: a sustained major hymn, D, G over D, D, with slow bells."""
    t = times(17)
    x = np.zeros((len(t), 2))
    for start, end, notes in ((0, 7, ['D3', 'A3', 'D4', 'F#4', 'A4']), (6, 12, ['D3', 'G3', 'B3', 'D4', 'G4']),
                              (11, 17, ['D3', 'A3', 'D4', 'F#4', 'A4', 'D5'])):
        seg = times(end - start)
        x += at(len(t), start, choir(seg, notes, 'a', 4, 1.2) * env(seg, 2, end - start - 2, 2)[:, None])
    for when, note in ((1, 'D5'), (5, 'A5'), (9, 'F#5'), (13, 'D6')):
        x += at(len(t), when, bell(times(4), hz(note), 3.0, .12))
    return reverb(x, 5.0, .45)


def c_throne():
    """Seated on the throne: a deep final chord and one last bell."""
    t = times(5)
    x = choir(t, ['D2', 'A2', 'D3', 'F#3', 'A3'], 'o', 4, .9) * env(t, .8, 3.5, 1.5)[:, None] * 1.3
    x += stereo(np.sin(2 * math.pi * hz('D1') * t) * env(t, .5, 3, 2) * .4)
    x += at(len(t), .6, bell(times(4.4), hz('D5'), 3.5, .25))
    return reverb(x, 4.5, .4)


CUES = {'ascent': c_ascent, 'void': c_void, 'call': c_call, 'stairs': c_stairs, 'step': c_step,
        'approach': c_approach, 'heart': c_heart, 'touch': c_touch, 'bestowal': c_bestowal, 'armor': c_armor,
        'divinity': c_divinity, 'throne': c_throne}
LABELS = {'ascent': ('升入天外', 'Ascending beyond the world'), 'void': ('寰宇寂静', 'The silent cosmos'),
          'call': ('神甲召唤', 'The divine armor calls'), 'stairs': ('天阶凝结', 'Celestial steps form'),
          'step': ('踏上天阶', 'Footsteps upon the celestial stair'), 'approach': ('趋近神甲', 'Approaching the divine armor'),
          'heart': ('心跳', 'Heartbeat'), 'touch': ('触及神甲', 'Touching the divine armor'),
          'bestowal': ('神光降临', 'Divine light descends'), 'armor': ('神甲合身', 'The divine armor binds'),
          'divinity': ('登神', 'Ascension to divinity'), 'throne': ('端坐王座', 'Seated upon the throne')}
STREAM = {'void', 'approach', 'divinity'}


def write_wav(path, x):
    with wave.open(str(path), 'wb') as f:
        f.setnchannels(2)
        f.setsampwidth(2)
        f.setframerate(RATE)
        f.writeframes((np.clip(x, -1, 1) * 32767).astype('<i2').tobytes())


def render(name, peak=.8):
    x = CUES[name]()
    # Gentle high cut keeps the score warm; cues are mixed under the game's own sound.
    x = np.stack([low(x[:, 0], 11000), low(x[:, 1], 11000)], 1)
    fade = min(int(.25 * RATE), len(x) // 4)
    x[-fade:] *= np.linspace(1, 0, fade)[:, None]
    x *= peak / max(np.max(np.abs(x)), 1e-9)
    ART.mkdir(parents=True, exist_ok=True)
    raw = ART / f'cg_{name}.wav'
    write_wav(raw, x)
    SND.mkdir(parents=True, exist_ok=True)
    subprocess.run([FFMPEG, '-y', '-v', 'error', '-i', str(raw), '-c:a', 'libvorbis', '-q:a', '6',
                    str(SND / f'{name}.ogg')], check=True)
    return x


def register_assets():
    path = ASSETS / 'sounds.json'
    mapping = json.loads(path.read_text(encoding='utf-8'))
    for key in [k for k in mapping if k.startswith('divine_cg.')]:
        del mapping[key]
    for name in CUES:
        mapping['divine_cg.' + name] = {'subtitle': 'subtitles.sourceofmystery.divine_cg.' + name,
                                        'sounds': [{'name': 'sourceofmystery:divine_cg/' + name, 'stream': name in STREAM}]}
    path.write_text(json.dumps(mapping, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    for lang, i in (('zh_cn', 0), ('en_us', 1)):
        p = ASSETS / 'lang' / f'{lang}.json'
        d = json.loads(p.read_text(encoding='utf-8'))
        for key in [k for k in d if k.startswith('subtitles.sourceofmystery.divine_cg.')]:
            del d[key]
        for name, pair in LABELS.items():
            d['subtitles.sourceofmystery.divine_cg.' + name] = pair[i]
        p.write_text(json.dumps(d, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    for old in SND.glob('*.ogg'):
        if old.stem not in CUES:
            old.unlink()


def preview(clips):
    """Full-length mix at the authored cue times, for listening outside the game."""
    track = json.loads((ASSETS / 'cinematic/divine_ascension.json').read_text(encoding='utf8'))
    total = int(68.7 * RATE)
    mix = np.zeros((total, 2))
    for story, name, volume in [(0.0, 'ascent', 1.0)] + [tuple(c) for c in track.get('cues', [])]:
        mix += at(total, real(story), clips[name] * volume)
    mix *= .9 / max(np.max(np.abs(mix)), 1e-9)
    write_wav(ART / 'divine-first-equip-soundtrack.wav', mix)


if __name__ == '__main__':
    clips = {name: render(name) for name in CUES}
    register_assets()
    preview(clips)
    print('Generated', len(clips), 'cues:', ', '.join(f'{k} {len(v) / RATE:.1f}s' for k, v in clips.items()))
