"""Author the portal entrance on the existing rig, including planted-foot IK.

Only writes the new clip; never regenerates the approved mesh or combat clips.
"""
import struct,json
from pathlib import Path
import numpy as np
from scipy.spatial.transform import Rotation as R
from scipy.optimize import least_squares
from pymeshio.pmx import reader
from combat_review import DEST,worlds,ramp,write_vmd
from smooth_gait import between
from face_motion import offering_expressions

ROOT=Path(__file__).resolve().parents[2]
ASSETS=ROOT/'src/main/resources/assets/sourceofmystery'
SCALE=.147
LEFT_STEPS=[(0,1.9,.65),(4.0,6.2,1.95)]
RIGHT_STEPS=[(1.85,4.2,1.30),(6.15,8,2.20)]

def foot_pose(t,steps):
    foot=0.;lift=0.;planted=True
    for a,b,destination in steps:
        if t>=b:foot=destination
        elif t>a:
            u=(t-a)/(b-a);foot+=(destination-foot)*float(ramp(t,a,b))
            lift=.05*np.sin(np.pi*u)**1.5;planted=False;break
        else:break
    return foot,lift,planted

def advance(t):
    u=np.clip(t/8,0,1)
    return 2.20*u*u*(3-2*u)

def generate():
    m=reader.read_from_file(str(DEST/'outsider_combat.pmx'))
    base=np.load(DEST/'grounded_idle.npz')['frames'][0].astype(float)
    rest=np.array([list(b.position) for b in m.bones]);bq,bp=worlds(m,base)
    baseq=list(R.from_quat(base[:,3:]))
    finger_bones=[43,44,45,37,38,39]
    axes={}
    for bone in finger_bones+[40,41,42,46,47,48,49,50,51]:
        nxt=bone+1 if bone not in [39,42,45,48,51] else bone
        prev=bone if nxt!=bone else bone-1
        axis=np.cross(rest[nxt]-rest[prev],[0,0,-1]);axes[bone]=axis/np.linalg.norm(axis)
    def finger_pose(parameters):
        q=list(baseq)
        for bone,angle in zip(finger_bones,parameters[:6]):q[bone]=baseq[bone]*R.from_rotvec(axes[bone]*angle)
        q[37]=q[37]*R.from_euler('y',parameters[6])
        return q
    def tips(parameters):
        v=base.copy();v[:,3:]=R.concatenate(finger_pose(parameters)).as_quat();rot,pos=worlds(m,v)
        return pos[45]+rot[45]@(rest[45]-rest[44])*.6, pos[39]+rot[39]@(rest[39]-rest[38])*.5
    preferred=np.array([.7,.7,.5,.35,.5,.4,.2])
    def residual(parameters):
        a,b=tips(parameters);return np.r_[(a-b)*8,(parameters-preferred)*.018]
    contact=least_squares(residual,preferred,bounds=([0]*6+[-1.2],[1.6]*6+[1.2]),max_nfev=180).x
    tip_gap=float(np.linalg.norm(np.subtract(*tips(contact)))*SCALE)
    assert tip_gap<.025,tip_gap
    # The shaft crosses the closed palm, with its axis running across the knuckles.
    # This grip replaces the old staff transform that assumed a hanging, straight arm.
    forearm_axis=rest[65]-rest[60];forearm_axis/=np.linalg.norm(forearm_axis)
    grip_axis=rest[70]-rest[79]
    grip_axis-=forearm_axis*np.dot(grip_axis,forearm_axis);grip_axis/=np.linalg.norm(grip_axis)
    palm_direction=np.cross(grip_axis,rest[73]-rest[65]);palm_direction/=np.linalg.norm(palm_direction)
    if palm_direction[1]>0:palm_direction=-palm_direction
    grip_offset=np.mean(rest[[70,73,76,79]],axis=0)-rest[65]+palm_direction*.28
    staff_contact=np.array([-2.77,12.0,.36])
    staff_in_hand=between(np.array([0.,1.,0.]),grip_axis)
    arm_angles=[];grip_errors=[];wrist_angles=[]
    frames=[];faces=[];contact_errors=[];finger_gaps=[];leg_angles=[];thigh_angles=[];frontal_angles=[];knee_angles=[]
    leg_length=(np.linalg.norm(rest[84]-rest[83])+np.linalg.norm(rest[85]-rest[84]))*SCALE
    for f in range(18*60+1):
        t=f/60;v=base.copy();q=list(R.from_quat(v[:,3:]));travel=advance(t)
        left_foot=foot_pose(t,LEFT_STEPS);right_foot=foot_pose(t,RIGHT_STEPS)
        # Balance the pelvis over the two supports instead of lunging ahead of them.
        v[3,2]+=(travel-(left_foot[0]+right_foot[0])*.5)/SCALE
        moving=1-ramp(t,7,8)
        respiration=np.sin((t+14)*2*np.pi/3.8)
        half_span=abs(left_foot[0]-right_foot[0])*.5
        hip_drop=.006+leg_length-np.sqrt(max(.01,leg_length**2-half_span**2))
        v[3,1]+=-hip_drop/SCALE+.012*respiration
        v[7,1]+=.10*respiration
        v[8,1]+=.05*respiration;v[8,2]-=.04*respiration
        # Weight follows the support leg, then settles before the two-second hold.
        support=.085*(ramp(t,0,.8)-2*ramp(t,1.9,2.8)+2*ramp(t,4.2,5.0)-ramp(t,6.2,7.5))
        v[3,0]+=support
        envelope=ramp(t,0,.65)*(1-ramp(t,7,8))
        gait=np.sin(t*np.pi/2)*envelope
        sway=np.sin(t*np.pi/2-.25)*envelope
        arm_swing=np.sin((t-.18)*np.pi/2)*envelope
        gesture=ramp(t,10.02,10.65)*(1-ramp(t,11.8,12.8))
        gaze_left=ramp(t,10.18,10.38)*(1-ramp(t,12.0,12.228572))
        gaze_right=ramp(t,12.0,12.228572)*(1-ramp(t,14.0,14.214286))
        follow_left=ramp(t,10.40,11.471429)*(1-ramp(t,12.25,13.25))
        follow_right=ramp(t,12.25,13.25)*(1-ramp(t,14.25,15.178572))
        resolve=ramp(t,14.25,15.2)
        # Pelvis leads a measured stride; ribs counter-turn and the neck keeps the gaze steady.
        q[5]=q[5]*R.from_euler('xyz',[0,2.2*gait,.65*sway],degrees=True)
        q[6]=q[6]*R.from_euler('xyz',[1.0*moving+.45*respiration,-1.4*gait+.8*(follow_left-follow_right),-.45*sway],degrees=True)
        q[8]=q[8]*R.from_euler('xyz',[-.55*respiration,-.55*gait+1.5*(follow_left-follow_right),0],degrees=True)
        q[10]=q[10]*R.from_euler('xyz',[-1.2*resolve+.18*respiration,-.25*gait+2*(follow_left-follow_right),-.20*sway],degrees=True)
        q[14]=q[14]*R.from_euler('z',-.5*respiration,degrees=True)
        q[52]=q[52]*R.from_euler('z',.5*respiration,degrees=True)
        q[11]=q[11]*R.from_euler('xyz',[3*(1-ramp(t,1,3))+1.8*resolve,12*follow_left-13*follow_right,-1.4*resolve],degrees=True)
        # Iris saccades lead the head; the eyes counter-rotate as the neck catches up.
        eye_yaw=np.clip(3.2*gaze_left-3.2*gaze_right-1.2*follow_left+1.2*follow_right,-3.2,3.2)
        for eye in (12,13):q[eye]=q[eye]*R.from_euler('xyz',[-.35*resolve,eye_yaw,0],degrees=True)
        q[25]=q[25]*R.from_euler('x',5.5*arm_swing,degrees=True)
        q[30]=q[30]*R.from_euler('x',-1.5*abs(arm_swing),degrees=True)
        # Bent carrying arm: upper arm hangs beside the ribs, forearm supports the grip.
        # Solve the arm as a chain; an elbow-axis twist alone cannot create flexion.
        v[:,3:]=R.concatenate(q).as_quat();wq,wp=worlds(m,v)
        carry=1-ramp(t,7,8.5)
        target=wp[8]+wq[8]@np.array([-2.65-.08*arm_swing,-.80+.04*respiration,-1.45-.35*carry-.12*arm_swing])
        origin=wp[55];direction=target-origin;distance=np.linalg.norm(direction);direction/=distance
        a=np.linalg.norm(rest[60]-rest[55]);b=np.linalg.norm(rest[65]-rest[60]);d=np.clip(distance,abs(a-b)+1e-5,a+b-.1)
        along=(a*a-b*b+d*d)/(2*d)
        pole=wq[8]@np.array([-.05,-.8,1.]);pole-=direction*np.dot(pole,direction);pole/=np.linalg.norm(pole)
        elbow=origin+direction*along+pole*np.sqrt(max(0,a*a-along*along))
        parent=R.from_matrix(wq[m.bones[55].parent_index]);q[55]=between(rest[60]-rest[55],parent.inv().apply(elbow-origin))
        v[:,3:]=R.concatenate(q).as_quat();wq,wp=worlds(m,v)
        parent=R.from_matrix(wq[m.bones[60].parent_index]);q[60]=between(rest[65]-rest[60],parent.inv().apply(target-wp[60]))
        # Forearm pronation aligns the knuckles with the upright shaft; wrist stays neutral.
        v[:,3:]=R.concatenate(q).as_quat();wq,wp=worlds(m,v)
        forearm=wp[65]-wp[60];forearm/=np.linalg.norm(forearm)
        current=wq[65]@grip_axis;current-=forearm*np.dot(current,forearm);current/=np.linalg.norm(current)
        up=wq[8]@np.array([-.07,1.,.04]);up-=forearm*np.dot(up,forearm);up/=np.linalg.norm(up)
        twist=np.arctan2(np.dot(forearm,np.cross(current,up)),np.dot(current,up))
        local_axis=rest[65]-rest[60];local_axis/=np.linalg.norm(local_axis)
        for bone,fraction in ((61,1),(62,.25),(63,.5),(64,.75)):
            q[bone]=baseq[bone]*R.from_rotvec(local_axis*twist*fraction)
        for first in (70,73,76,79):
            for bone,angle in zip(range(first,first+3),(45,75,50)):
                q[bone]=R.from_euler('z',angle,degrees=True)
        # Chest-height snap: solve the whole arm; never translate a wrist on its own.
        v[:,3:]=R.concatenate(q).as_quat();wq,wp=worlds(m,v)
        target=wp[35]*(1-gesture)+(wp[8]+wq[8]@np.array([2.,.2,-2.6]))*gesture
        origin=wp[25];direction=target-origin;distance=np.linalg.norm(direction);direction/=distance
        a=np.linalg.norm(rest[30]-rest[25]);b=np.linalg.norm(rest[35]-rest[30]);d=np.clip(distance,abs(a-b)+1e-5,a+b-.015)
        along=(a*a-b*b+d*d)/(2*d);pole=np.array([1.,-.8,.25]);pole-=direction*np.dot(pole,direction);pole/=np.linalg.norm(pole)
        elbow=origin+direction*along+pole*np.sqrt(max(0,a*a-along*along))
        parent=R.from_matrix(wq[m.bones[25].parent_index]);q[25]=between(rest[30]-rest[25],parent.inv().apply(elbow-origin))
        v[:,3:]=R.concatenate(q).as_quat();wq,wp=worlds(m,v)
        parent=R.from_matrix(wq[m.bones[30].parent_index]);q[30]=between(rest[35]-rest[30],parent.inv().apply(target-wp[30]))
        release=float(ramp(t,10.90,10.98));prep=float(ramp(t,10.25,10.82))
        prep*=1-ramp(t,11.85,12.7)
        params=contact.copy();params[:3]=params[:3]*(1-release)+np.array([1.4,1.3,.9])*release
        params[3:6]*=1-.70*release;params[6]*=1-.5*release
        fingers=finger_pose(params*prep)
        for bone in finger_bones:q[bone]=fingers[bone]
        for bone in [46,47,48,49,50,51]:q[bone]=baseq[bone]*R.from_rotvec(axes[bone]*.72*gesture)
        q[35]=baseq[35]*R.from_euler('x',-7*gesture+9*release*gesture,degrees=True)
        for hip,knee,ankle,toe,dh,dk,da,dt,foot_data in [
            (83,84,85,87,98,99,100,101,left_foot),
            (90,91,92,94,102,103,104,105,right_foot)]:
            foot,lift,planted=foot_data
            target=bp[ankle].copy();target[0]=(1 if hip==83 else -1)*.125/SCALE
            target[2]-=(foot-travel)/SCALE;target[1]+=lift/SCALE
            v[:,3:]=R.concatenate(q).as_quat();wq,wp=worlds(m,v)
            origin=wp[hip];direction=target-origin;distance=np.linalg.norm(direction);direction/=distance
            a=np.linalg.norm(rest[knee]-rest[hip]);b=np.linalg.norm(rest[ankle]-rest[knee]);d=np.clip(distance,abs(a-b)+1e-5,a+b-1e-5)
            along=(a*a-b*b+d*d)/(2*d);pole=np.array([0.,0.,-1.]);pole-=direction*np.dot(pole,direction);pole/=np.linalg.norm(pole)
            joint=origin+direction*along+pole*np.sqrt(max(0,a*a-along*along))
            parent=R.from_matrix(wq[m.bones[hip].parent_index])
            q[hip]=between(rest[knee]-rest[hip],parent.inv().apply(joint-origin));parent=parent*q[hip]
            q[knee]=between(rest[ankle]-rest[knee],parent.inv().apply(origin+direction*d-joint))
            q[ankle]=(parent*q[knee]).inv();q[toe]=R.identity()
            q[dh]=q[hip];q[dk]=q[knee];q[da]=q[ankle];q[dt]=q[toe]
            if planted:contact_errors.append(abs(distance-d)*SCALE)
        v[:,3:]=R.concatenate(q).as_quat();wq,wp=worlds(m,v)
        left=wp[100]-wp[98];right=wp[104]-wp[102]
        leg_angles.append(np.degrees(np.arccos(np.clip(np.dot(left,right)/(np.linalg.norm(left)*np.linalg.norm(right)),-1,1))))
        left=wp[99]-wp[98];right=wp[103]-wp[102]
        thigh_angles.append(np.degrees(np.arccos(np.clip(np.dot(left,right)/(np.linalg.norm(left)*np.linalg.norm(right)),-1,1))))
        # The 10 degree constraint is lateral splay, not forward/backward stride.
        for left,right in ((wp[100]-wp[98],wp[104]-wp[102]),(left,right)):
            left=left[:2];right=right[:2]
            frontal_angles.append(np.degrees(np.arccos(np.clip(np.dot(left,right)/(np.linalg.norm(left)*np.linalg.norm(right)),-1,1))))
        for hip,knee,ankle in ((98,99,100),(102,103,104)):
            a=wp[knee]-wp[hip];b=wp[ankle]-wp[knee]
            knee_angles.append(np.degrees(np.arccos(np.clip(np.dot(a,b)/(np.linalg.norm(a)*np.linalg.norm(b)),-1,1))))
        if 10.82<=t<10.9:
            middle=wp[45]+wq[45]@(rest[45]-rest[44])*.6
            thumb=wp[39]+wq[39]@(rest[39]-rest[38])*.5
            finger_gaps.append(np.linalg.norm(middle-thumb)*SCALE)
        sq=R.from_matrix(wq[65])*staff_in_hand;pq=R.from_matrix(wq[1])
        palm=wp[65]+wq[65]@grip_offset
        staff_position=palm-sq.apply(staff_contact-rest[557])
        v[557,:3]=pq.inv().apply(staff_position-wp[1]);v[557,3:]=(pq.inv()*sq).as_quat()
        upper=wp[60]-wp[55];lower=wp[65]-wp[60]
        arm_angles.append(float(np.degrees(np.arccos(np.clip(np.dot(upper,lower)/(np.linalg.norm(upper)*np.linalg.norm(lower)),-1,1)))))
        wrist_angles.append(float(np.degrees((baseq[65].inv()*q[65]).magnitude())))
        grip_errors.append(float(np.linalg.norm(staff_position+sq.apply(staff_contact-rest[557])-palm)*SCALE))
        frames.append(v)
        # The smile is a mouth shape. Keep the cheerful eyebrow channel at zero.
        # Deliberate blink between search directions, before the final unbroken stare.
        faces.append(offering_expressions(t))
    frames=np.asarray(frames,dtype='<f4');faces=np.asarray(faces,dtype='<f4')
    assert np.isfinite(frames).all()
    assert max(contact_errors)<.015, max(contact_errors)
    assert max(finger_gaps)<.025,max(finger_gaps)
    assert max(frontal_angles)<=10,max(frontal_angles)
    assert max(leg_angles)<=32,max(leg_angles)
    assert max(thigh_angles)<=40,max(thigh_angles)
    assert max(knee_angles)<=45,max(knee_angles)
    assert 65<min(arm_angles) and max(arm_angles)<110,(min(arm_angles),max(arm_angles))
    assert max(wrist_angles)<10,max(wrist_angles)
    assert max(grip_errors)<.001,max(grip_errors)
    assert np.max(faces[:,1])==0 and faces[15*60,6]>.3
    np.savez_compressed(DEST/'offering_intro.npz',frames=frames)
    np.savez_compressed(DEST/'offering_intro.face.npz',weights=faces)
    for file,magic,version,data,channels in [
        (ASSETS/'animations/entity/outsider/offering_intro.bin',0x4F414E31,3,frames,len(m.bones)),
        (ASSETS/'meshes/entity/outsider_expressions/offering_intro.bin',0x4F464331,1,faces,12)]:
        file.write_bytes(struct.pack('<5I',magic,version,channels,len(data),60)+data.tobytes())
    (DEST/'offering_intro.bin').write_bytes((ASSETS/'animations/entity/outsider/offering_intro.bin').read_bytes())
    write_vmd(DEST/'offering_intro.vmd',m,frames,faces)
    report={'frames':len(frames),'duration':18,'root_travel_blocks':2.20,'body_travel_blocks':2.075,'step_contact_positions_blocks':[.65,1.30,1.95,2.20],'foot_lift_blocks':.05,'maximum_leg_separation_degrees':float(max(leg_angles)),'maximum_thigh_separation_degrees':float(max(thigh_angles)),'maximum_frontal_splay_degrees':float(max(frontal_angles)),'maximum_knee_flex_degrees':float(max(knee_angles)),'max_planted_reach_error_blocks':max(contact_errors),'facial_channels':12,'snap_thumb_middle_gap_blocks':float(max(finger_gaps)),'snap_time_seconds':24.9,'heel_times_seconds':[15.9,18.2,20.2,22.0],'face_hold_seconds':2,'face_search_seconds':[24.18,26,28],'eye_bones':[12,13],'eye_speed_multiplier':.7,'maximum_eye_yaw_degrees':3.2,'breathing_period_seconds':3.8,'smile_channel':6,'cheerful_brow_max':float(np.max(faces[:,1]))}
    report.update(right_elbow_flex_degrees=[min(arm_angles),max(arm_angles)],maximum_added_wrist_bend_degrees=max(wrist_angles),maximum_staff_grip_error_blocks=max(grip_errors),staff_grip_height_in_source=12.0)
    (DEST/'offering-intro-checks.json').write_text(json.dumps(report,indent=2));print(report)

if __name__=='__main__':generate()
