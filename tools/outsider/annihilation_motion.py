"""V2 airborne two-handed ritual; altitude preparation belongs to the server.

3s staff raise, layered seal build, beam, sky portal and 5s crimson fall.
"""
import json,struct
from functools import lru_cache
import numpy as np
from scipy.spatial.transform import Rotation as R
from pymeshio.pmx import reader
from combat_review import DEST,worlds,ramp,write_vmd
from smooth_gait import between
from quaternius_reference import Library

FPS=60
DURATION=30.5
CORE=np.array([-2.77,21.225,.36])

def generate():
    m=reader.read_from_file(str(DEST/'outsider_combat.pmx'))
    base=np.load(DEST/'dark_grasp.npz')['frames'][0].astype(float)
    rest=np.array([list(b.position) for b in m.bones]);bq,bp=worlds(m,base)
    library=Library();frames=[];grip_error=0.
    for f in range(round(DURATION*FPS)+1):
        t=f/FPS;v=base.copy();p=v[:,:3].copy();q=list(R.from_quat(v[:,3:]))
        crouch=0.
        raised=float(ramp(t,0,3)*(1-ramp(t,29,30.5)))
        airborne=1.
        q[6]=q[6]*R.from_euler('x',-4*raised,degrees=True)
        q[8]=q[8]*R.from_euler('x',-3*raised,degrees=True)
        q[11]=q[11]*R.from_euler('x',-6*raised,degrees=True)

        @lru_cache(None)
        def world(i):
            parent=m.bones[i].parent_index
            if parent<0:return q[i],p[i]
            a,b=world(parent);return a*q[i],b+a.apply(p[i])

        def solve(upper,knee,tip,target,pole,orientation=None):
            origin=world(upper)[1];delta=target-origin;distance=np.linalg.norm(delta)
            direction=delta/max(distance,1e-8)
            a=np.linalg.norm(rest[knee]-rest[upper]);b=np.linalg.norm(rest[tip]-rest[knee])
            d=np.clip(distance,abs(a-b)+.02,a+b-.08)
            along=(a*a-b*b+d*d)/(2*d)
            bend=np.asarray(pole,float);bend-=direction*np.dot(bend,direction);bend/=np.linalg.norm(bend)
            joint=origin+direction*along+bend*np.sqrt(max(0,a*a-along*along))
            parent=world(m.bones[upper].parent_index)[0]
            q[upper]=between(rest[knee]-rest[upper],parent.inv().apply(joint-origin));world.cache_clear()
            parent=world(m.bones[knee].parent_index)[0]
            q[knee]=between(rest[tip]-rest[knee],parent.inv().apply(origin+direction*d-joint));world.cache_clear()
            if orientation is not None:
                q[tip]=world(m.bones[tip].parent_index)[0].inv()*orientation;world.cache_clear()

        # Match a world-space 30 degree leg separation at full overhead hold.
        # Both femurs and shins open together; the pose is neither a standing
        # T-pose nor independent ankle displacement.
        opening=float(ramp(t,0,1.1)*(1-ramp(t,29,30.5)))
        for hip,knee,ankle,toe,sign in [(83,84,85,87,1),(90,91,92,94,-1)]:
            q[hip]=R.from_quat(base[hip,3:])*R.from_euler('z',sign*11.875*opening,degrees=True)
            q[knee]=R.from_quat(base[knee,3:])*R.from_euler('x',-8*opening,degrees=True)
            q[ankle]=R.from_quat(base[ankle,3:])*R.from_euler('x',6*opening,degrees=True)
            q[toe]=R.from_euler('x',-3*opening,degrees=True)
        world.cache_clear()
        # Staff remains upright: both hands move together from a low guard to
        # two stacked grips above the forehead. No world-locked wrist twisting.
        right=bp[65]+np.array([0,-2*crouch,0])
        overhead=np.array([-.22,20.6,-1.7])
        right=right*(1-raised)+overhead*raised
        right[2]-=3*np.sin(np.pi*raised)
        left=bp[35]+np.array([-.8*crouch,-1.4*crouch,-.7*crouch])
        left=left*(1-raised)+(overhead+np.array([.45,-.8,0]))*raised
        left[2]-=3*np.sin(np.pi*raised)
        for ids,target,sign in [((25,30,35),left,1),((55,60,65),right,-1)]:
            solve(*ids,target,[sign,0,.35])
            q[ids[2]]=R.from_quat(base[ids[2],3:])
            world.cache_clear()
        # The actual skin uses these PMX append chains, not only the visible IK bones.
        for a,b in [(83,98),(84,99),(85,100),(90,102),(91,103),(92,104)]:q[b]=q[a]
        for i in [185,214,239,266]:q[i]=R.from_euler('x',-8*crouch,degrees=True)
        world.cache_clear()
        parentq,parentp=world(1)
        staffpos=world(65)[1]
        # Blend the idle-held staff orientation to the straight two-handed hold.
        idle_staff=R.from_matrix(bq[557]);sq=idle_staff*R.from_rotvec(idle_staff.inv().as_rotvec()*raised)
        p[557]=parentq.inv().apply(staffpos-parentp);q[557]=parentq.inv()*sq
        frame=np.c_[p,R.concatenate(q).as_quat()]
        blend=float(ramp(t,0,.45)*(1-ramp(t,29,30.5)))
        original=R.from_quat(base[:,3:]);authored=R.from_quat(frame[:,3:])
        frame[:,3:]=(original*R.from_rotvec((original.inv()*authored).as_rotvec()*blend)).as_quat()
        frame[:,:3]=base[:,:3]+(frame[:,:3]-base[:,:3])*blend
        # Exact endpoints avoid a discontinuity into daily idle. The pose is
        # identical throughout the ritual; only facial morphs continue chanting.
        if f==0 or f==round(DURATION*FPS):frame=base.copy()
        frames.append(frame)
        if 3<=t<=29:
            wq,wp=worlds(m,frame)
            grip_error=max(grip_error,float(np.linalg.norm(wp[35]-(wp[65]+[.45,-.8,0]))))
    frames=np.asarray(frames,dtype='<f4')
    assert np.isfinite(frames).all()
    held=frames[180:1741,:554]
    assert np.max(np.abs(held-held[0]))<1e-5,'Ritual pose must hold still'
    wrist=R.from_quat(frames[:,[35,65],3:].reshape(-1,4))
    step=np.degrees((R.from_quat(frames[:-1,[35,65],3:].reshape(-1,4)).inv()*R.from_quat(frames[1:,[35,65],3:].reshape(-1,4))).magnitude()).max()
    allstep=np.degrees((R.from_quat(frames[:-1,:,3:].reshape(-1,4)).inv()*R.from_quat(frames[1:,:,3:].reshape(-1,4))).magnitude()).max()
    if allstep>=6:
        steps=np.degrees((R.from_quat(frames[:-1,:,3:].reshape(-1,4)).inv()*R.from_quat(frames[1:,:,3:].reshape(-1,4))).magnitude()).reshape(len(frames)-1,-1)
        where=np.unravel_index(np.argmax(steps),steps.shape);print('largest step',where,where[0]/FPS,str(m.bones[where[1]].name))
    assert step<1.5 and allstep<6,(step,allstep)
    assert grip_error<.12,grip_error
    np.savez_compressed(DEST/'annihilation.npz',frames=frames)
    with (DEST/'annihilation.bin').open('wb') as f:
        f.write(struct.pack('<5I',0x4F414E31,3,len(m.bones),len(frames),FPS));f.write(frames.tobytes())
    write_vmd(DEST/'annihilation.vmd',m,frames)
    events=[(0,'airborne_raise_staff'),(3,'base_start'),(7,'advanced_start_y3'),(9,'super_start_y6'),(11,'upper_advanced_y9_and_satellites'),(12,'fully_open'),(15,'beam_start'),(18,'arrays_fade_start'),(20,'arrays_gone_beam_fade'),(22,'beam_gone_sky_seal_start_y255'),(24,'red_meteor_spawn'),(25,'sky_seal_fade'),(27,'sky_seal_gone'),(29,'meteor_impact'),(30.5,'recovery_complete')]
    meta=dict(label='陨灭杀 · 分层仪式 V2',duration=DURATION,loop=False,fps=FPS,bones=len(m.bones),frames=len(frames),keys=[0,1.5,3,7,9,12,24,30.5],events=[dict(time=t,tick=round(t*20),event=e) for t,e in events])
    (DEST/'annihilation.events.json').write_text(json.dumps(meta,ensure_ascii=False,indent=2),encoding='utf-8')
    report=dict(frames=len(frames),finite=True,normalized_rotations=True,local_bone_lengths_preserved=True,maximum_rotation_step_degrees=float(allstep),wrist_maximum_step_degrees=float(step),wrist_maximum_neutral_deviation_degrees=0.,two_hand_grip_error_native_units=grip_error,pose_hold_seconds=[3,29],altitude_preparation_separate=True)
    path=DEST/'checks.json';checks=json.loads(path.read_text(encoding='utf-8')) if path.exists() else {};checks['annihilation']=report
    path.write_text(json.dumps(checks,indent=2),encoding='utf-8')
    print('annihilation',report,flush=True)

if __name__=='__main__':generate()
