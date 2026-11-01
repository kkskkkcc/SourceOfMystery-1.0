"""Contact-aware flight, touchdown and takeoff clips on the combat rig."""
import json,struct
import numpy as np
from scipy.spatial.transform import Rotation as R
from pymeshio.pmx import reader
from combat_review import DEST,write_vmd,worlds,ramp
from smooth_gait import between
from quaternius_reference import Library
FPS=60

def generate():
    m=reader.read_from_file(str(DEST/'outsider_combat.pmx'))
    base=np.load(DEST/'dark_grasp.npz')['frames'][0].astype(float)
    rest=np.array([list(b.position) for b in m.bones]); bq,bp=worlds(m,base)
    specs={'flight_idle':(4.,True,'悬浮待机'),'flight_move':(4.8,True,'空中巡游'),
           'descend':(2.,True,'下降准备'),'landing':(1.2,False,'落地缓冲'),
           'grounded_idle':(4.,True,'地面待机'),'takeoff':(1.6,False,'起飞')}
    reports={}
    reference=Library()
    for name,(duration,loop,label) in specs.items():
        frames=[]
        for f in range(round(duration*FPS)+1):
            t=f/FPS;v=base.copy();q=list(R.from_quat(v[:,3:]));cycle=2*np.pi*t/duration
            air=1. if name in ['flight_idle','flight_move','descend'] else 0.
            if name=='landing':air=1-ramp(t,0,.55)
            if name=='takeoff':air=ramp(t,.82,1.6)
            envelope=1. if loop else np.sin(np.pi*t/duration)**2
            float_wave=.24*np.sin(cycle)*air if loop else 0.
            contact=float(ramp(t,.04,.36)*(1-ramp(t,.55,1.18))) if name=='landing' else 0.
            launch=float(ramp(t,.62,.95)*(1-ramp(t,1.35,1.6))) if name=='takeoff' else 0.
            sink=-1.25*contact-.85*launch
            if name=='takeoff':
                # A visible compression plateau precedes the push-off. The
                # old clip only moved the root by a few tenths of a block.
                down=4.5*ramp(t,0,.35)*(1-ramp(t,.62,1.6))
                v[3,1]-=down
            v[3,1]+=float_wave+sink
            v[3,2]+=.42*contact+.24*launch
            moving=1. if name=='flight_move' else 0.
            # Takeoff is a whole-body action: compress, hold the loaded pose,
            # then drive the pelvis, rib cage and shoulders through the push.
            # These envelopes are deliberately separate from the airborne
            # blend so the launch has a visible anticipation and release.
            charge=(ramp(t,.02,.30)*(1-ramp(t,.48,.72))) if name=='takeoff' else 0.
            burst=(ramp(t,.60,.82)*(1-ramp(t,1.02,1.34))) if name=='takeoff' else 0.
            # Flight keeps the chest leading the velocity vector.  The head
            # counters a little so the face remains readable instead of
            # pitching with the rib cage.
            # Keep the cruise pose readable and only mildly nose-forward.  The
            # old 24 degree travel pitch made the whole character look as if
            # she was falling instead of flying; leg FK follows this target.
            torso_pitch=(3*air+12*moving+.8*np.sin(cycle)*air*envelope+23*contact+16*launch
                         +14*charge+8*burst)
            # Upper and lower body are sibling branches. Rotate their shared
            # waist first: legs must inherit the chest's lean and delayed bank.
            pelvis_pitch=(air*(.55*(3+12*moving)+.7*(np.sin(cycle-.45)+np.sin(.45))*envelope)
                          +6*contact+5*launch+10*charge+7*burst)
            pelvis_roll=.8*(np.sin(cycle-.3)+np.sin(.3))*air*envelope
            q[5]=q[5]*R.from_euler('xyz',[-pelvis_pitch,0,pelvis_roll],degrees=True)
            q[6]=q[6]*R.from_euler('z',2.2*np.sin(cycle)*air-pelvis_roll,degrees=True)
            q[8]=q[8]*R.from_euler('x',1.5*air-1.1*np.sin(cycle)*envelope,degrees=True)
            q[11]=q[11]*R.from_euler('x',2.5*moving+1.0*air-.9*np.sin(cycle)*envelope,degrees=True)
            if name=='flight_move':
                # Relaxed arms trail the chest with a soft elbow bend. The
                # opposite signs preserve the model's left/right symmetry.
                arm_swing=8*np.sin(cycle-.45)
                for bone,shoulder,elbow in [(25,1,-1),(30,1,-1),(55,1,1),(60,1,1)]:
                    q[bone]=q[bone]*R.from_euler('xyz',[
                        shoulder*(10+arm_swing),0,elbow*(4+2*np.sin(cycle))],degrees=True)
            for bone,amplitude in [(25,7),(30,5),(55,-3),(60,3)]:
                q[bone]=q[bone]*R.from_euler('z',amplitude*np.sin(cycle)*air*envelope,degrees=True)
            if name=='takeoff':
                crouch=1-ramp(t,.62,1.15)
                q[83]=q[83]*R.from_euler('x',-18*crouch,degrees=True)
                q[84]=q[84]*R.from_euler('x',28*crouch,degrees=True)
                q[90]=q[90]*R.from_euler('x',-12*crouch,degrees=True)
                q[91]=q[91]*R.from_euler('x',20*crouch,degrees=True)
                # Load the body before the feet leave, then swing both arms
                # through the same explosive extension as the chest. Keeping
                # left/right signs paired prevents a one-sided hop.
                q[5]=q[5]*R.from_euler('x',-8*charge+11*burst,degrees=True)
                q[8]=q[8]*R.from_euler('x',-16*charge+24*burst,degrees=True)
                q[11]=q[11]*R.from_euler('x',-10*charge+16*burst,degrees=True)
                for shoulder,elbow in [(25,30),(55,60)]:
                    q[shoulder]=q[shoulder]*R.from_euler('xyz',[-20*charge+30*burst,0,(-5 if shoulder==25 else 5)*charge],degrees=True)
                    q[elbow]=q[elbow]*R.from_euler('xyz',[28*charge-36*burst,0,(4 if shoulder==25 else -4)*charge],degrees=True)
                v[3,1]+=1.15*burst
            if name in ['landing','takeoff']:
                source='Jump_Land' if name=='landing' else 'Jump_Start'
                reference.apply_upper_body(m,q,bq,source,t/duration*reference.durations[source],.55*envelope)
            # Keep the reference's shoulder/neck follow-through, but explicitly
            # solve the final sagittal lean in PMX space. Its source rest axes
            # otherwise turn Jump_Land into a backwards arch on this character.
            v[:,3:]=R.concatenate(q).as_quat();wq,wp=worlds(m,v)
            spine=wp[10]-wp[9]
            measured=np.degrees(np.arctan2(-spine[2],spine[1]))
            parent=R.from_matrix(wq[m.bones[6].parent_index])
            q[6]=parent.inv()*R.from_euler('x',measured-torso_pitch,degrees=True)*parent*q[6]
            # Airborne legs are relaxed and slightly delayed instead of being
            # locked in the authored standing pose.  The two legs use an
            # opposite phase so the feet read as a natural suspended sway.
            for hip,knee,ankle,toe,dhip,dknee,dankle,sign in [
                    (83,84,85,87,98,99,100,1),(90,91,92,94,102,103,104,-1)]:
                v[:,3:]=R.concatenate(q).as_quat();wq,wp=worlds(m,v)
                phase=0 if sign==1 else np.pi
                origin=wp[hip]
                # Ground contact is solved first. Blend into an explicit FK
                # airborne pose rather than moving almost-straight IK feet by
                # a few centimetres and calling it flight.
                target=bp[ankle].copy()
                direction=target-origin;distance=np.linalg.norm(direction);direction/=distance
                a=np.linalg.norm(rest[knee]-rest[hip]);b=np.linalg.norm(rest[ankle]-rest[knee]);d=np.clip(distance,abs(a-b)+1e-5,a+b-1e-5)
                along=(a*a-b*b+d*d)/(2*d);pole=np.array([0.,0.,-1.]);pole-=direction*np.dot(pole,direction);pole/=np.linalg.norm(pole)
                joint=origin+direction*along+pole*np.sqrt(max(0,a*a-along*along))
                parent=R.from_matrix(wq[m.bones[hip].parent_index])
                q[hip]=between(rest[knee]-rest[hip],parent.inv().apply(joint-origin))
                parent=parent*q[hip];q[knee]=between(rest[ankle]-rest[knee],parent.inv().apply(origin+direction*d-joint))
                q[ankle]=(parent*q[knee]).inv()
                wave=np.sin(cycle+phase)
                # Keep both upper legs close together. Leave headroom below
                # 30 degrees for the small runtime bank/sway overlay.
                # Both thighs trail the same chest-driven pelvis. The shared
                # drag changes their direction without opening the leg gap.
                drag=-.18*torso_pitch+3.4*(np.sin(cycle-.6)+np.sin(.6))*envelope
                hip_x=((14+2*wave) if sign==1 else (-6+1.5*wave))+drag
                knee_x=(-98-2*moving+4*np.sin(cycle+phase-.3)) if sign==1 else (-28-3*moving+3*np.sin(cycle+phase-.3))
                ankle_x=(58+3*np.sin(cycle+phase-.6)) if sign==1 else (23+2*np.sin(cycle+phase-.6))
                for bone,rotation in [(hip,R.from_euler('xyz',[hip_x,sign*.5,-sign*(.5+.25*np.sin(cycle))],degrees=True)),
                                      (knee,R.from_euler('x',knee_x,degrees=True)),
                                      (ankle,R.from_euler('xyz',[ankle_x,0,sign*2],degrees=True))]:
                    q[bone]=q[bone]*R.from_rotvec((q[bone].inv()*rotation).as_rotvec()*air)
                q[toe]=R.from_euler('x',air*(-4+2*np.sin(cycle+phase-.6)),degrees=True)
                q[dhip]=q[hip];q[dknee]=q[knee];q[dankle]=q[ankle]
                q[101 if sign==1 else 105]=q[toe]
            for k in [185,214,239,266]:q[k]=R.from_euler('x',2*air+.8*np.sin(cycle)*air*envelope,degrees=True)
            v[:,3:]=R.concatenate(q).as_quat();wq,wp=worlds(m,v)
            sq=R.from_matrix(wq[65]@bq[65].T);pq=R.from_matrix(wq[1])
            v[557,:3]=pq.inv().apply(wp[65]-wp[1]);v[557,3:]=(pq.inv()*sq).as_quat()
            frames.append(v)
        frames=np.asarray(frames,dtype='<f4')
        if loop:frames[-1]=frames[0]
        np.savez_compressed(DEST/(name+'.npz'),frames=frames)
        with (DEST/(name+'.bin')).open('wb') as f:
            f.write(struct.pack('<5I',0x4F414E31,3,len(m.bones),len(frames),FPS));f.write(frames.tobytes())
        write_vmd(DEST/(name+'.vmd'),m,frames)
        (DEST/(name+'.events.json')).write_text(json.dumps(dict(label=label,duration=duration,loop=loop,fps=FPS,keys=np.linspace(0,duration,8).tolist()),ensure_ascii=False,indent=2),encoding='utf-8')
        reports[name]=dict(seconds=duration,loop=loop,finite=bool(np.isfinite(frames).all()),frames=len(frames))
        thigh_separation=[];whole_leg_separation=[];torso_angles=[];pelvis_angles=[]
        for frame in frames:
            _,wp=worlds(m,frame)
            spine=wp[10]-wp[9];torso_angles.append(float(np.degrees(np.arctan2(-spine[2],spine[1]))))
            pelvis_angles.append(float(R.from_quat(frame[5,3:]).as_euler('xyz',degrees=True)[0]))
            for destinations,result in [((99,103),thigh_separation),((100,104),whole_leg_separation)]:
                left=wp[destinations[0]]-wp[98];right=wp[destinations[1]]-wp[102]
                result.append(float(np.degrees(np.arccos(np.clip(np.dot(left,right)/(np.linalg.norm(left)*np.linalg.norm(right)),-1,1)))))
        assert max(thigh_separation)<27 and max(whole_leg_separation)<30,(name,max(thigh_separation),max(whole_leg_separation))
        reports[name].update(maximum_thigh_separation_degrees=max(thigh_separation),maximum_whole_leg_separation_degrees=max(whole_leg_separation))
        reports[name].update(torso_forward_pitch_degrees=[min(torso_angles),max(torso_angles)],pelvis_pitch_degrees=[min(pelvis_angles),max(pelvis_angles)])
        if name=='landing':
            assert min(torso_angles)>-.1 and max(torso_angles)>20,'Touchdown must lean forward, never backwards'
            planted=[]
            for frame in frames[34:]:
                _,wp=worlds(m,frame);planted.append(float(np.max(np.linalg.norm(wp[[100,104]]-bp[[100,104]],axis=1))))
            assert max(planted)<.01,'Feet slid after touchdown'
            reports[name]['planted_foot_error_native_units']=max(planted)
        if name in ['flight_idle','flight_move']:
            # Check the actual deform chain and its world-space knee bend.
            angles=[];feet=[]
            for frame in frames:
                _,wp=worlds(m,frame);row=[]
                for h,k,a in [(98,99,100),(102,103,104)]:
                    thigh=wp[k]-wp[h];shin=wp[a]-wp[k]
                    row.append(float(np.degrees(np.arccos(np.clip(np.dot(thigh,shin)/(np.linalg.norm(thigh)*np.linalg.norm(shin)),-1,1)))))
                angles.append(row);feet.append(wp[[100,104]])
            angles=np.asarray(angles);feet=np.asarray(feet)
            assert angles[:,0].min()>90 and 20<angles[:,1].min()<angles[:,1].max()<40
            assert (feet[:,0,1]-feet[:,1,1]).min()>2,'Left knee must stay raised'
            assert np.ptp(feet[:,0,2])>.15 and np.ptp(feet[:,1,2])>.1,'Feet must sway'
            reports[name].update(left_knee_bend_degrees=[float(angles[:,0].min()),float(angles[:,0].max())],right_knee_bend_degrees=[float(angles[:,1].min()),float(angles[:,1].max())],left_foot_lift_above_right_native_units=float((feet[:,0,1]-feet[:,1,1]).min()))
    data={k:np.load(DEST/(k+'.npz'))['frames'] for k in specs}
    for a,ai,b,bi in [('flight_idle',0,'landing',0),('landing',-1,'grounded_idle',0),('grounded_idle',0,'takeoff',0),('takeoff',-1,'flight_idle',0)]:
        av,bv=data[a][ai],data[b][bi];err=float(np.max(np.abs(av[:,:3]-bv[:,:3])));rot=float(np.max(1-np.abs(np.sum(av[:,3:]*bv[:,3:],axis=1))))
        assert err<.005 and rot<.0001,(a,b,err,rot)
    reports['transition_endpoints_match']=True
    (DEST/'daily-checks.json').write_text(json.dumps(reports,ensure_ascii=False,indent=2),encoding='utf-8');print(reports)

if __name__=='__main__':generate()
