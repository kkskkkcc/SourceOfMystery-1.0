"""Adapt a user's own VMD for local testing; never writes into src/resources.

Example:
 python tools/outsider/import_local_vmd.py --vmd path/to/own.vmd --output build/private-motion
The source author may forbid redistribution. Keep generated tracks local too.
"""
import argparse
import json
import struct
from pathlib import Path

import numpy as np
from scipy.spatial.transform import Rotation as R
from pymeshio.pmx import reader
from bake_animation import read_tracks, pose
from generate_mesh import SOURCE


def retarget(model, translations, rotations):
    lookup = {str(b.name): i for i,b in enumerate(model.bones)}
    # This PMX's groove pivot is at the ground, whereas the reference motion
    # expects it at the pelvis. Preserve the rotation but move its pivot.
    groove = lookup['グルーブ']
    offset = np.array(list(model.bones[lookup['腰']].position)) - np.array(list(model.bones[groove].position))
    translations[groove] += offset - rotations[groove].apply(offset)
    # Accommodate this model's downward A-pose and shoulder accessories.
    for name, angle in [('左腕', -7), ('右腕', 7)]:
        i = lookup[name]
        rotations[i] = R.from_euler('z', angle, degrees=True) * rotations[i]


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--vmd',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    root=Path(__file__).resolve().parents[2]
    output=args.output.resolve()
    if output.is_relative_to((root/'src').resolve()):
        raise ValueError('Local motion must stay outside packaged source/resources')
    output.mkdir(parents=True,exist_ok=True)
    model=reader.read_from_file(str(SOURCE/'林德薇恩v1.04.pmx'))
    tracks=read_tracks(args.vmd)
    # This source contains two complete 42-frame cycles; 0 and 84 match.
    end=max(k[0] for keys in tracks.values() for k in keys)
    frames=[]; errors=[]
    for t in np.arange(0,end+.1,.5):
        values,err=pose(model,tracks,float(t),retarget=retarget)
        frames.append(values);errors.extend(err)
    frames=np.array(frames,dtype='<f4')
    seam=float(np.max(np.abs(frames[0,:,:3]-frames[-1,:,:3])))
    q0=R.from_quat(frames[0,:,3:]);q1=R.from_quat(frames[-1,:,3:])
    seam_angle=float(np.degrees((q0.inv()*q1).magnitude()).max())
    assert seam<.001 and seam_angle<.01,(seam,seam_angle)
    with (output/'walk.bin').open('wb') as f:
        f.write(struct.pack('<5I',0x4F414E31,3,len(model.bones),len(frames),60))
        f.write(frames.tobytes())
    report=dict(source='Hotaru7MMD - Genshin girl walk cycle',
                source_url='https://bowlroll.net/file/287330',
                redistribution='Not permitted; local use only, including adapted tracks',
                frames=len(frames),fps=60,bones=len(model.bones),
                loop_position_error=seam,loop_rotation_degrees=seam_angle,
                maximum_ik_error_model_units=max(errors))
    (output/'source.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(report,ensure_ascii=False),flush=True)


if __name__=='__main__':main()
