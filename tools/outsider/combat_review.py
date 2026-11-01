"""Author combat against the existing PMX rig; reuse its idle, IK math and VMD pipeline.

Local review assets only. Gameplay movement, damage and effects are separate events.
"""
import json
import struct
from functools import lru_cache
from pathlib import Path
import numpy as np
from scipy.spatial.transform import Rotation as R
from scipy.interpolate import PchipInterpolator
from scipy.ndimage import gaussian_filter1d
from pymeshio import pmx, common
from pymeshio.pmx import reader, writer
from smooth_gait import between
from void_boss_review import OUT, SOURCE
from quaternius_reference import Library

DEST=OUT/'combat'
FPS=60


def curve(t, keys):
    times,values=zip(*keys)
    return np.asarray(PchipInterpolator(times,values,axis=0)(np.clip(t,times[0],times[-1])))


def ramp(t,a,b):
    u=np.clip((t-a)/(b-a),0,1)
    return u*u*u*(10+u*(-15+6*u))


def worlds(model, frame):
    q=R.from_quat(frame[:,3:]).as_matrix();p=frame[:,:3].copy()
    for i,b in enumerate(model.bones):
        if b.parent_index>=0:
            p[i]=p[b.parent_index]+q[b.parent_index]@p[i]
            q[i]=q[b.parent_index]@q[i]
    return q,p

def remove_fixed_panels(model):
    """Delete the user-confirmed generated long panels; keep rig/morph indices stable."""
    mantle=next((i for i,b in enumerate(model.bones) if str(b.name)=='VoidMantle'),None)
    if mantle is None:return 0
    cursor=0;kept=[];removed=0
    for material in model.materials:
        faces=np.asarray(model.indices[cursor:cursor+material.vertex_count]).reshape(-1,3)
        cursor+=material.vertex_count
        drop=np.asarray([all(int(v)>=35118 and type(model.vertices[int(v)].deform).__name__=='Bdef1'
                         and model.vertices[int(v)].deform.index0==mantle for v in face) for face in faces],dtype=bool)
        removed+=int(drop.sum());selected=faces[~drop].flatten().tolist()
        material.vertex_count=len(selected);kept.extend(selected)
    model.indices=kept
    return removed


def write_vmd(path,model,frames,face_weights=None):
    rest=np.array([list(b.position) for b in model.bones]);records=[]
    for i,b in enumerate(model.bones):
        offset=rest[i]-(rest[b.parent_index] if b.parent_index>=0 else 0)
        changing=np.max(np.abs(frames[:,i]-frames[0,i]))>1e-7
        samples=range(0,len(frames),2) if changing else [0,len(frames)-1]
        for k in samples: records.append((b.name,k//2,frames[k,i,:3]-offset,frames[k,i,3:]))
    with path.open('wb') as f:
        f.write(b'Vocaloid Motion Data 0002'.ljust(30,b'\0'));f.write(b'Outsider combat'.ljust(20,b'\0'))
        f.write(struct.pack('<I',len(records)))
        interp=bytes([20]*8+[107]*8)*4
        for name,k,p,q in records:
            f.write(str(name).encode('shift_jis')[:15].ljust(15,b'\0'))
            f.write(struct.pack('<I7f',k,*p,*q));f.write(interp)
        facial=[]
        if face_weights is not None:
            from generate_face_morphs import NAMES
            for channel,name in enumerate(NAMES):
                if np.max(face_weights[:,channel])<=0:continue
                for k in range(0,len(frames),2):facial.append((name,k//2,face_weights[k,channel]))
        f.write(struct.pack('<I',len(facial)))
        for name,k,weight in facial:
            f.write(name.encode('shift_jis')[:15].ljust(15,b'\0'));f.write(struct.pack('<If',k,weight))
        f.write(struct.pack('<4I',0,0,0,0))


def main():
    DEST.mkdir(exist_ok=True)
    model=reader.read_from_file(str(OUT/'outsider_void_boss_v1.pmx'))
    source=reader.read_from_file(str(SOURCE))
    base=np.load(OUT/'review_motion.npz')['frames'][0].astype(float)
    bq,bp=worlds(model,base)
    # An independent staff bone frees BOTH hands for the ground strike. Preserve
    # its exact approval-pose geometry, then animate between grip and levitation.
    staff=len(model.bones)
    for v in model.vertices[35118:]:
        if type(v.deform).__name__=='Bdef1' and v.deform.index0==65:
            p=bq[65]@(np.array(list(v.position))-np.array(list(model.bones[65].position)))+bp[65]
            n=bq[65]@np.array(list(v.normal))
            v.position=common.Vector3(*p);v.normal=common.Vector3(*n);v.deform=pmx.Bdef1(staff)
    model.bones.append(pmx.Bone('VoidStaff','VoidStaff',common.Vector3(*bp[65]),1,0,0x001e,tail_position=common.Vector3(0,1,0)))
    # Retain the former panel pivot so approved motion/bone indices stay stable.
    # The confirmed rigid panels themselves are removed below.
    mantle=len(model.bones)
    model.bones.append(pmx.Bone('VoidMantle','VoidMantle',common.Vector3(0,10.3,1.2),9,0,0x001e,tail_position=common.Vector3(0,-1,0)))
    for v in model.vertices[35118:]:
        if type(v.deform).__name__=='Bdef1' and v.deform.index0==9 and v.position.y<10.5:
            v.deform=pmx.Bdef1(mantle)
    remove_fixed_panels(model)
    model.textures=['../'+str(x).replace('\\','/') for x in model.textures]
    model.name='Outsider combat';model.english_name='Outsider combat review'
    writer.write_to_file(model,str(DEST/'outsider_combat.pmx'))
    rest=np.array([list(b.position) for b in model.bones])
    base=np.vstack([base,np.r_[bp[65]-rest[1],[0,0,0,1]],np.r_[rest[mantle]-rest[9],[0,0,0,1]]])
    initial={i:bp[i].copy() for i in [35,65,85,92]}
    clips={
      'dark_grasp':dict(label='黑暗之握',duration=7.,keys=[0,.55,1.4,2.,4.5,5.5,6.2,7.],
        events=[(0,'anticipation'),(1.4,'palm_orb_ready'),(2.,'barrage_start'),(5.5,'barrage_stop'),(7.,'recover_done')]),
      'dark_silence':dict(label='黑暗寂灭',duration=7.4,keys=[0,.5,1.4,2.,3.,4.,6.,7.4],
        events=[(.5,'landing'),(2.,'palms_impact_and_warning'),(3.,'pillars_start'),(6.,'pillars_stop'),(7.4,'recover_done')]),
      'annihilation':dict(label='陨灭杀',duration=9.,keys=[0,.7,1.4,2.4,4.4,5.2,7.5,9.],
        events=[(0,'visible_anticipation'),(1.4,'teleport_up_16_blocks_and_invulnerable'),(1.4,'13_rings_expand_start'),(4.4,'rings_max_and_spawn_orb'),(5.2,'release_gesture'),(6.8,'hold_until_actual_impact'),(9.,'recover_done')])}
    reports={}
    reference=Library()
    for name,meta in clips.items():
        if name=='annihilation':continue  # Dedicated two-hand staff ritual below.
        frames=[];sockets=[];staff_tracks=[];max_ik=0.;max_foot=0.
        for f in range(round(meta['duration']*FPS)+1):
            real_t=f/FPS;t=real_t;v=base.copy();q=list(R.from_quat(v[:,3:]));p=v[:,:3].copy()
            end=meta['duration']
            if name=='annihilation':
                t=real_t*.4/1.4 if real_t<=1.4 else real_t-1;end=8.
            active=ramp(t,0,.55)*(1-ramp(t,end-1,end))
            floating=0.;lift=0.;lean=0.;spread=0.;left=initial[35].copy();right=initial[65].copy()
            palm='up';openhand=active
            if name=='dark_grasp':
                left=curve(t,[(0,initial[35]),(.55,[1.8,10.8,.7]),(1.4,[2.5,14.5,-2.]),(2.,[3.7,16.3,-3.5]),(3.1,[4.5,15.5,-2.5]),(4.3,[3.,16.8,-3.8]),(5.5,[4.,15.6,-3.]),(6.2,[2.,12.,-.8]),(7.,initial[35])])
                pulse=ramp(t,1.8,2.1)*(1-ramp(t,5.3,5.5))
                wave=np.sin((t-2)*np.pi*2/1.15)
                left[2]+=.23*pulse*wave
                lean=active*6.+pulse*3*wave;spread=active*.3
                coil=curve(t,[(0,0),(.6,-18),(1.4,-12),(2.,12),(3.1,4),(4.3,16),(5.5,6),(end,0)])
                q[6]=R.from_euler('xyz',[-lean,coil,-5*active],degrees=True)
                q[8]=R.from_euler('xyz',[2*pulse*wave,4*active,2*active],degrees=True)
                q[9]=R.from_euler('xyz',[0,-coil*.35,2*active],degrees=True)
                q[11]=R.from_euler('xyz',[-3*active,-coil*.5,0],degrees=True)
                lift=.3*active;p[1,1]+=lift
            elif name=='dark_silence':
                floating=ramp(t,.05,.65)*(1-ramp(t,6.3,7.2))
                sink=float(curve(t,[(0,0),(.2,.6),(.5,-.7),(.85,-.25),(1.4,-1.4),(2.,-8.4),(2.18,-8.5),(2.65,-8.4),(3.2,-5.),(4.,-1.1),(6.,-.3),(end,0)]))
                lift=max(0,sink);p[1,1]+=lift;p[3,1]+=min(0,sink)
                lean=float(curve(t,[(0,0),(.5,7),(1.4,-14),(2.,82),(2.2,84),(2.65,82),(4.,9),(6.,3),(end,0)]))
                spread=active*.45
                q[6]=R.from_euler('x',-lean*.55,degrees=True);q[8]=R.from_euler('x',-lean*.45,degrees=True)
                q[10]=R.from_euler('x',lean*.35,degrees=True);q[11]=R.from_euler('x',lean*.2,degrees=True)
                for hand,sign in [(35,1),(65,-1)]:
                    target=curve(t,[(0,initial[hand]),(.65,[sign*3.5,11.,-1.5]),(1.4,[sign*3.3,17.,-2.]),(1.72,[sign*3.1,12.,-4.]),(2.,[sign*2.1,.95,-4.7]),(2.65,[sign*2.1,.95,-4.7]),(4.,[sign*3.4,10.,-2.6]),(6.,[sign*3.,11.,-.8]),(end,initial[hand])])
                    if hand==35:left=target
                    else:right=target
                palm='down'
                q[mantle]=R.from_euler('x',-min(100,max(0,-sink)*12),degrees=True)
            else:
                floating=ramp(t,.2,.9)*(1-ramp(t,6.9,7.7))
                lift=float(curve(t,[(0,0),(.4,1.7),(1.4,2.),(3.4,2.),(6.5,2.),(7.2,.3),(end,0)]))
                p[1,1]+=lift # Small local float; the 16-block teleport is a GAME event.
                if real_t<1.4:p[3,1]-=.9*np.sin(np.pi*real_t/1.4)**2
                q[6]=R.from_euler('xyz',[3*active,0,0],degrees=True)
                q[11]=R.from_euler('xyz',[-7*active,0,0],degrees=True)
                for hand,sign in [(35,1),(65,-1)]:
                    target=curve(t,[(0,initial[hand]),(.4,[sign*3.,13.5,-1.5]),(1.4,[sign*4.6,17.5,-1.7]),(3.4,[sign*3.4,20.5,-1.6]),(3.85,[sign*2.8,20.8,-2.]),(4.2,[sign*3.4,14.8,-4.3]),(5.,[sign*4.2,16.,-2.8]),(6.5,[sign*4.,16.,-2.]),(7.2,[sign*3.,12.5,-1.]),(end,initial[hand])])
                    target[1]+=lift
                    if hand==35:left=target
                    else:right=target
            # Add a restrained rib-cage breath during sustained poses.
            q[7]=R.from_euler('x',.22*np.sin(2*np.pi*t/3)*active,degrees=True)
            if name=='dark_grasp':
                ref_clip='Spell_Simple_Enter' if t<1.4 else 'Spell_Simple_Idle_Loop'
                ref_time=t/1.4*.5 if t<1.4 else (t-1.4)%reference.durations[ref_clip]
                reference.apply_upper_body(model,q,bq,ref_clip,ref_time,.65*active)
            elif name=='annihilation':
                ref_clip='Spell_Simple_Shoot' if 3.4<=t<=4.2 else 'Spell_Simple_Idle_Loop'
                ref_time=(t-3.4)/.8*.5 if ref_clip.endswith('Shoot') else t%reference.durations[ref_clip]
                reference.apply_upper_body(model,q,bq,ref_clip,ref_time,.5*active)

            @lru_cache(None)
            def world(i):
                parent=model.bones[i].parent_index
                if parent<0:return q[i],p[i]
                a,b=world(parent);return a*q[i],b+a.apply(p[i])

            def solve(ids,target,pole,orientation=None):
                nonlocal max_ik
                upper,middle,tip=ids;origin=world(upper)[1]
                delta=target-origin;distance=np.linalg.norm(delta);direction=delta/distance
                l1=np.linalg.norm(rest[middle]-rest[upper]);l2=np.linalg.norm(rest[tip]-rest[middle])
                reach=np.clip(distance,abs(l1-l2)+1e-5,l1+l2-1e-5)
                if tip in [35,65]:
                    # Soft extension limit avoids the square-root velocity spike
                    # when an elbow hits a perfectly straight IK singularity.
                    limit=l1+l2-.015;soft=.28*ramp(target[1],.95,3)
                    if soft>.0001:reach=max(abs(l1-l2)+1e-5,limit-soft*np.logaddexp(0,(limit-distance)/soft))
                along=(l1*l1-l2*l2+reach*reach)/(2*reach)
                bend=np.array(pole,float);bend-=direction*np.dot(bend,direction);bend/=np.linalg.norm(bend)
                joint=origin+direction*along+bend*np.sqrt(max(0,l1*l1-along*along))
                parent=world(model.bones[upper].parent_index)[0]
                q[upper]=between(rest[middle]-rest[upper],parent.inv().apply(joint-origin));world.cache_clear()
                parent=world(model.bones[middle].parent_index)[0]
                q[middle]=between(rest[tip]-rest[middle],parent.inv().apply(origin+direction*reach-joint));world.cache_clear()
                parent_index=model.bones[tip].parent_index
                parent_world=world(parent_index)[0]
                if orientation is None:
                    # During the ground strike the palm target becomes nearly
                    # collinear with the forearm.  Aligning two world vectors
                    # in that configuration leaves a free roll axis and can
                    # choose the opposite quaternion branch (the visible
                    # wrist "knot"). Preserve the wrist's authored local
                    # frame and let it follow the solved forearm instead.
                    rest_local=R.from_matrix(bq[parent_index]).inv()*R.from_matrix(bq[tip])
                    q[tip]=rest_local
                else:
                    q[tip]=parent_world.inv()*orientation
                world.cache_clear()
                error=np.linalg.norm(world(tip)[1]-target);max_ik=max(max_ik,float(error))
                return error

            for ids,sign in [([83,84,85],1),([90,91,92],-1)]:
                target=initial[ids[2]]+[sign*spread,lift,0]
                error=solve(ids,target,[0,0,-1],R.from_matrix(bq[ids[2]]))
                max_foot=max(max_foot,float(error))
            for ids,target,sign in [([25,30,35],left,1),([55,60,65],right,-1)]:
                finger=rest[43 if sign==1 else 73]-rest[ids[2]];finger/=np.linalg.norm(finger)
                hand_rot=R.align_vectors(np.array([[0,0,-1],[0,1 if palm=='up' else -1,0]]),np.array([finger,[0,0,-1]]))[0]
                blend=active if sign==1 or name!='dark_grasp' else 0.
                neutral=R.from_matrix(bq[ids[2]])
                hand_rot=neutral*R.from_rotvec((neutral.inv()*hand_rot).as_rotvec()*blend)
                # The impact pose uses a forearm-following wrist frame. The
                # hand still opens below, but never twists 90–180 degrees to
                # satisfy an unstable palm-down vector alignment.
                solve(ids,target,[sign,-.45,.15],None if name=='dark_silence' else hand_rot)
                if name=='annihilation':
                    # Overhead casting must not keep the palm locked to world-up:
                    # that required ~100 degrees of wrist bending. Pronation is
                    # distributed along forearm twist bones; wrists follow the arm.
                    forearm=rest[ids[2]]-rest[ids[1]];forearm/=np.linalg.norm(forearm)
                    bend_axis=np.cross(forearm,[0,0,-1.]);bend_axis/=np.linalg.norm(bend_axis)
                    roll=float(curve(t,[(0,0),(.8,20),(2.,32),(3.4,40),(4.2,12),(6.5,20),(7.4,0),(end,0)]))*sign
                    flex=float(curve(t,[(0,0),(.8,-8),(3.4,-12),(4.2,8),(5.2,-4),(6.5,-4),(end,0)]))
                    for twist,fraction in zip(range(31 if sign==1 else 61,35 if sign==1 else 65),[1,.25,.5,.75]):
                        q[twist]=R.from_rotvec(forearm*np.deg2rad(roll*fraction))
                    q[ids[2]]=R.from_quat(base[ids[2],3:])*R.from_rotvec(bend_axis*np.deg2rad(flex))
                    world.cache_clear()
                # Open fingers using the existing relaxed-hand reference rotations.
                for k in range(37 if sign==1 else 67,52 if sign==1 else 82):
                    q[k]=R.from_rotvec(q[k].as_rotvec()*(1-.85*blend))
            # Re-evaluate source append rotations: leg D, elbows and twist helpers.
            for i,b in enumerate(source.bones):
                if b.flag&0x100:q[i]=R.from_rotvec(q[b.effect_index].as_rotvec()*b.effect_factor)
            # Secondary hair roots: conservative authored sway, no physics simulation.
            for k in [185,214,239,266]:
                q[k]=R.from_euler('xyz',[lean*.8,0,1.2*active*np.sin(t*2.1-k*.3)],degrees=True)
            for k in range(321,337):
                q[k]=R.from_euler('x',max(0,lean)*.55,degrees=True)
            world.cache_clear()
            hq,hp=world(65)
            held_q=hq*R.from_matrix(bq[65]).inv()
            float_q=R.from_euler('xyz',[0,7*active*np.sin(t*.7),-7*active],degrees=True)
            sq=held_q*R.from_rotvec((held_q.inv()*float_q).as_rotvec()*floating)
            sp=hp*(1-floating)+(np.array([-5.6,11.5+lift,2.6]))*floating
            parentq,parentp=world(1);q[staff]=parentq.inv()*sq;p[staff]=parentq.inv().apply(sp-parentp)
            for j,k in enumerate([554,555,556]):q[k]=R.from_euler('z',t/24*360*[1,-2,3][j],degrees=True)
            world.cache_clear()
            frame=np.c_[p,R.concatenate(q).as_quat()]
            # Ease into/out of the authored pole directions without snapping out
            # of the existing idle pose. Never blend world-space joint positions.
            transition=ramp(t,0,.35)*(1-ramp(t,end-.45,end))
            ids=list(range(554))+[staff,mantle]
            old=R.from_quat(base[ids,3:]);new=R.from_quat(frame[ids,3:])
            frame[ids,3:]=(old*R.from_rotvec((old.inv()*new).as_rotvec()*transition)).as_quat()
            frame[ids,:3]=base[ids,:3]+(frame[ids,:3]-base[ids,:3])*transition
            final_positions=worlds(model,frame)[1]
            sockets.append({str(i):final_positions[i].tolist() for i in [35,65]})
            frames.append(frame)
            staff_tracks.append((floating,lift,active))
        frames=np.asarray(frames,dtype='<f4')
        # A 42 ms quaternion filter removes sharp elbow velocity changes around
        # extension. Preserve the exact ground-contact interval and clip ends.
        for bone in [25,30,35,55,60,65]:
            track=frames[:,bone,3:].astype(float)
            for f in range(1,len(track)):
                if np.dot(track[f-1],track[f])<0:track[f]*=-1
            smooth=gaussian_filter1d(track,4.0,axis=0,mode='nearest');smooth/=np.linalg.norm(smooth,axis=1)[:,None]
            times=np.arange(len(frames))/FPS
            amount=ramp(times,0,.2)*(1-ramp(times,meta['duration']-.2,meta['duration']))
            if name=='dark_silence':amount*=1-ramp(times,1.90,1.98)*(1-ramp(times,2.65,2.85))
            original=R.from_quat(track);filtered=R.from_quat(smooth)
            frames[:,bone,3:]=(original*R.from_rotvec((original.inv()*filtered).as_rotvec()*amount[:,None])).as_quat()
        # Wrists are visible against the skirt silhouette. Smooth their full
        # attack track, rather than only the clip endpoints, so an IK elbow
        # solve cannot flip a hand by a large angle in one render frame.
        for bone in [35,65]:
            track=frames[:,bone,3:].astype(float)
            for f in range(1,len(track)):
                if np.dot(track[f-1],track[f])<0:track[f]*=-1
            smooth=gaussian_filter1d(track,3.2,axis=0,mode='nearest');smooth/=np.linalg.norm(smooth,axis=1)[:,None]
            original=R.from_quat(track);filtered=R.from_quat(smooth)
            frames[:,bone,3:]=(original*R.from_rotvec((original.inv()*filtered).as_rotvec()*.82)).as_quat()
            # Hard cap the final per-frame wrist velocity after filtering. This
            # removes an IK branch flip while retaining the intended gesture.
            for f in range(1,len(frames)):
                prev=R.from_quat(frames[f-1,bone,3:]);cur=R.from_quat(frames[f,bone,3:])
                delta=prev.inv()*cur;angle=float(np.rad2deg(delta.magnitude()))
                if angle>6:
                    frames[f,bone,3:]=(prev*R.from_rotvec(delta.as_rotvec()*(6/angle))).as_quat()
        for f,frame in enumerate(frames):
            t=f/FPS;floating,lift,active=staff_tracks[f]
            for i,b in enumerate(source.bones):
                if b.flag&0x100:frame[i,3:]=R.from_rotvec(R.from_quat(frame[b.effect_index,3:]).as_rotvec()*b.effect_factor).as_quat()
            if name=='dark_grasp':
                for tip in [35,65]:
                    neutral=R.from_quat(base[tip,3:]);delta=neutral.inv()*R.from_quat(frame[tip,3:])
                    angle=float(delta.magnitude())
                    if angle>np.deg2rad(30):frame[tip,3:]=(neutral*R.from_rotvec(delta.as_rotvec()*np.deg2rad(30)/angle)).as_quat()
            if name=='dark_silence':
                # Effect/twist helper bones are applied after the IK pass. Keep
                # the visible wrist in the authored local frame once more at
                # this final stage; otherwise the helper update can reintroduce
                # the 180-degree palm-roll that looks like a tied wrist.
                current_wq,_=worlds(model,frame)
                for tip in [35,65]:
                    parent_index=model.bones[tip].parent_index
                    rest_local=R.from_matrix(bq[parent_index]).inv()*R.from_matrix(bq[tip])
                    frame[tip,3:]=rest_local.as_quat()
            wq,wp=worlds(model,frame)
            held=R.from_matrix(wq[65]@bq[65].T);floating_q=R.from_euler('xyz',[0,7*active*np.sin(t*.7),-7*active],degrees=True)
            sq=held*R.from_rotvec((held.inv()*floating_q).as_rotvec()*floating)
            sp=wp[65]*(1-floating)+np.array([-5.6,11.5+lift,2.6])*floating
            parent=R.from_matrix(wq[1]);frame[staff,:3]=parent.inv().apply(sp-wp[1]);frame[staff,3:]=(parent.inv()*sq).as_quat()
        sockets=[{str(i):worlds(model,v)[1][i].tolist() for i in [35,65]} for v in frames]
        assert np.isfinite(frames).all()
        assert np.max(np.abs(np.linalg.norm(frames[:,:,3:],axis=2)-1))<1e-5
        # All original local offsets remain fixed except explicitly animated roots.
        stable=[i for i in range(554) if i not in [1,3]]
        assert np.max(np.abs(frames[:,stable,:3]-base[stable,:3]))<1e-5
        np.savez_compressed(DEST/(name+'.npz'),frames=frames)
        with (DEST/(name+'.bin')).open('wb') as f:
            f.write(struct.pack('<5I',0x4F414E31,3,len(model.bones),len(frames),FPS));f.write(frames.tobytes())
        write_vmd(DEST/(name+'.vmd'),model,frames)
        meta.update(fps=FPS,bones=len(model.bones),frames=len(frames),loop=False,
                    events=[dict(time=t,tick=round(t*20),event=e) for t,e in meta['events']])
        (DEST/(name+'.events.json')).write_text(json.dumps(meta,ensure_ascii=False,indent=2),encoding='utf-8')
        np.savez_compressed(DEST/(name+'.sockets.npz'),left=np.array([s['35'] for s in sockets]),right=np.array([s['65'] for s in sockets]))
        reports[name]=dict(max_hand_or_foot_target_error=max_ik,max_foot_target_error=max_foot,
            finite=True,normalized_rotations=True,local_bone_lengths_preserved=True,frames=len(frames))
        changes=(R.from_quat(frames[:-1,:,3:].reshape(-1,4)).inv()*R.from_quat(frames[1:,:,3:].reshape(-1,4))).magnitude()
        reports[name]['maximum_rotation_step_degrees']=float(np.rad2deg(changes).max())
        wrists=frames[:,[35,65],3:]
        wrist_step=np.rad2deg((R.from_quat(wrists[:-1].reshape(-1,4)).inv()*R.from_quat(wrists[1:].reshape(-1,4))).magnitude())
        reports[name]['wrist_maximum_step_degrees']=float(wrist_step.max())
        assert wrist_step.max()<8,'Wrist motion still has a one-frame flip'
        if name=='annihilation':
            wrists=frames[:,[35,65],3:]
            step=np.rad2deg((R.from_quat(wrists[:-1].reshape(-1,4)).inv()*R.from_quat(wrists[1:].reshape(-1,4))).magnitude())
            deviation=np.rad2deg((R.from_quat(np.tile(base[[35,65],3:],(len(frames),1))).inv()*R.from_quat(wrists.reshape(-1,4))).magnitude())
            reports[name]['wrist_maximum_step_degrees']=float(step.max())
            reports[name]['wrist_maximum_neutral_deviation_degrees']=float(deviation.max())
            assert step.max()<1.5 and deviation.max()<15,'Overhead wrist constraint regressed'
        if name=='dark_silence':
            contact=worlds(model,frames[120])[1]
            reports[name]['palm_contact_y']=[float(contact[i,1]) for i in [35,65]]
            assert max(abs(contact[i,1]-.95) for i in [35,65])<.18
        print(name,reports[name],flush=True)
    (DEST/'checks.json').write_text(json.dumps(reports,indent=2),encoding='utf-8')
    from annihilation_motion import generate
    generate()


if __name__=='__main__':main()
