"""Bake authored locomotion, leg IK and append bones at 60 Hz.

This is a scoped offline adapter for Outsider, not a complete MMD physics engine.
The output contains resolved local bone rotations/positions. Runtime blends
local poses and reconstructs the hierarchy before applying inverse bind poses.
"""
import json
import struct
import unicodedata
from functools import lru_cache

import numpy as np
from scipy.spatial.transform import Rotation as R
import smooth_gait


def vec(value):
    return np.array(list(value), dtype=float)


def slerp(a, b, t):
    return a * R.from_rotvec((a.inv() * b).as_rotvec() * t)


def bezier(t, data, channel):
    x1, y1, x2, y2 = [data[channel + n] / 127 for n in (0, 4, 8, 12)]
    lo, hi = 0., 1.
    for _ in range(18):
        u = (lo + hi) / 2
        x = 3 * (1-u)**2 * u * x1 + 3 * (1-u) * u*u * x2 + u**3
        if x < t:
            lo = u
        else:
            hi = u
    u = (lo + hi) / 2
    return 3 * (1-u)**2 * u * y1 + 3 * (1-u) * u*u * y2 + u**3


def read_tracks(path):
    data = path.read_bytes()
    assert data.startswith(b'Vocaloid Motion Data 0002')
    count, = struct.unpack_from('<I', data, 50)
    tracks = {}
    for i in range(count):
        offset = 54 + i * 111
        name = data[offset:offset+15].split(b'\0')[0].decode('shift_jis')
        name = unicodedata.normalize('NFKC', name)
        frame, = struct.unpack_from('<I', data, offset+15)
        values = struct.unpack_from('<7f', data, offset+19)
        tracks.setdefault(name, []).append((frame, np.array(values[:3]), R.from_quat(values[3:]), data[offset+47:offset+111]))
    return {name: sorted(keys, key=lambda k: k[0]) for name, keys in tracks.items()}


def sample(keys, frame):
    if frame <= keys[0][0]:
        return keys[0][1].copy(), keys[0][2]
    for a, b in zip(keys, keys[1:]):
        if frame <= b[0]:
            t = (frame-a[0]) / (b[0]-a[0])
            blend = np.array([bezier(t, b[3], ch) for ch in range(3)])
            return a[1] + (b[1]-a[1]) * blend, slerp(a[2], b[2], bezier(t, b[3], 3))
    return keys[-1][1].copy(), keys[-1][2]


def pose(model, tracks, frame, authored=None, retarget=None):
    bones = model.bones
    count = len(bones)
    rest = np.array([vec(b.position) for b in bones])
    translations = np.zeros((count, 3))
    rotations = [R.identity() for _ in bones]
    for i, bone in enumerate(bones):
        keys = tracks.get(unicodedata.normalize('NFKC', str(bone.name)))
        if keys:
            translations[i], rotations[i] = sample(keys, frame)

    if retarget is not None:
        retarget(model, translations, rotations)

    targets = None
    if authored is not None:
        seconds, walking = authored
        targets = smooth_gait.apply(model, translations, rotations, seconds, walking)

    @lru_cache(None)
    def local(i):
        b = bones[i]
        q, t = rotations[i], translations[i].copy()
        if b.effect_index >= 0 and b.flag & 0x300:
            source_q, source_t = local(b.effect_index)
            if b.flag & 0x100:
                q = q * R.from_rotvec(source_q.as_rotvec() * b.effect_factor)
            if b.flag & 0x200:
                t += source_t * b.effect_factor
        return q, t

    @lru_cache(None)
    def world(i):
        q, t = local(i)
        parent = bones[i].parent_index
        if parent < 0:
            return q, rest[i] + t
        pq, pt = world(parent)
        return pq * q, pt + pq.apply(rest[i] - rest[parent] + t)

    # PMX CCD targets and link limits, including the knee's X-only hinge.
    errors = []
    if targets is not None:
        def invalidate():
            local.cache_clear()
            world.cache_clear()
        for ids, target in targets:
            errors.append(smooth_gait.solve_leg(ids, target, bones, rest, rotations, world, invalidate))
    for controller, bone in enumerate(bones):
        ik = bone.ik
        if ik is None or targets is not None:
            continue
        target = world(controller)[1].copy()
        for _ in range(min(ik.loop, 64)):
            if np.linalg.norm(world(ik.target_index)[1] - target) < 1e-4:
                break
            for link in ik.link:
                index = link.bone_index
                q, point = world(index)
                a = q.inv().apply(world(ik.target_index)[1] - point)
                b = q.inv().apply(target - point)
                if np.linalg.norm(a) < 1e-8 or np.linalg.norm(b) < 1e-8:
                    continue
                a /= np.linalg.norm(a)
                b /= np.linalg.norm(b)
                if link.limit_angle and abs(link.limit_max[1]) + abs(link.limit_max[2]) < 1e-7:
                    # A signed planar correction avoids the straight-knee CCD singularity.
                    delta = np.arctan2(a[1]*b[2]-a[2]*b[1], a[1]*b[1]+a[2]*b[2])
                    old = rotations[index].as_rotvec()[0]
                    angle = np.clip(old + np.clip(delta, -ik.limit_radian, ik.limit_radian),
                                    link.limit_min[0], link.limit_max[0])
                    rotations[index] = R.from_rotvec([angle, 0, 0])
                else:
                    axis = np.cross(a, b)
                    length = np.linalg.norm(axis)
                    if length < 1e-8:
                        continue
                    angle = min(np.arccos(np.clip(np.dot(a, b), -1, 1)), ik.limit_radian)
                    rotations[index] = rotations[index] * R.from_rotvec(axis / length * angle)
                local.cache_clear()
                world.cache_clear()
        errors.append(float(np.linalg.norm(world(ik.target_index)[1] - target)))
    # Store resolved LOCAL transforms. Blending world joint positions shortened
    # the limbs during transitions; local rotation blending keeps bone lengths.
    values = np.array([np.r_[rest[i]-(rest[b.parent_index] if b.parent_index >= 0 else 0)+local(i)[1],
                              local(i)[0].as_quat()] for i,b in enumerate(bones)])
    assert np.isfinite(values).all()
    return values, errors


def bake_all(model, reference, output):
    output.mkdir(parents=True, exist_ok=True)
    report = {}
    for name in ('idle', 'walk'):
        # Preserve relaxed finger posing from the reference idle, author all
        # locomotion/body channels explicitly for this model.
        tracks = read_tracks(reference / 'idle.vmd')
        fps = smooth_gait.FPS
        end = round((4 if name == 'idle' else smooth_gait.PERIOD) * fps)
        frames, errors = [], []
        for frame in range(end+1):
            values, error = pose(model, tracks, 0, (frame/fps, name=='walk'))
            frames.append(values)
            errors.extend(error)
        frames = np.array(frames, dtype='<f4')
        # Assert loop closure in position and quaternion orientation.
        seam_position = float(np.max(np.abs(frames[0,:,:3]-frames[-1,:,:3])))
        seam_rotation = float(np.max(1-np.abs(np.sum(frames[0,:,3:]*frames[-1,:,3:],axis=1))))
        assert seam_position < 0.03 and seam_rotation < 0.001, (name, seam_position, seam_rotation)
        with (output / f'{name}.bin').open('wb') as stream:
            stream.write(struct.pack('<5I', 0x4F414E31, 3, len(model.bones), len(frames), fps))
            stream.write(frames.tobytes())
        report[name] = dict(frames=len(frames), fps=fps, bones=len(model.bones),
                            loop_position_error=seam_position, loop_rotation_error=seam_rotation,
                            max_ik_error_model_units=max(errors, default=0))
        print(name, report[name], flush=True)
    (output / 'bake-report.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
