"""Non-destructive PMX design branch for user review. Original rig/UV/morph indices stay stable.

Native PMX materials recolour the existing UV textures; new ornaments are real geometry.
Review motion has resolved local transforms, so append/IK flags are disabled in this copy.
The model author's original comment and local-only distribution restrictions are retained.
"""
import copy
import json
import math
import shutil
import struct
from pathlib import Path

import numpy as np
from scipy.spatial.transform import Rotation as R
from pymeshio import pmx, common
from pymeshio.pmx import reader, writer
from generate_face_morphs import flatten

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'art/outsider_void_boss/v1'
SOURCE = next((ROOT / 'mmd模型').rglob('林德薇恩v1.04.pmx'))
ASSETS = ROOT / 'src/main/resources/assets/sourceofmystery'
V3 = common.Vector3


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    m = reader.read_from_file(str(SOURCE))
    original_counts = (len(m.vertices), len(m.indices)//3, len(m.bones), len(m.morphs))
    original_comment = m.comment
    m.name = 'Void Regent v1'
    m.english_name = 'Outsider - Void Regent - review v1'
    m.comment = original_comment + '\n\n本机审核改色/配饰版本：虚空摄政者 v1。原模型：元空Ame。不得二次配布。'
    # Preserve every source image unmodified; PMX material multipliers are editable.
    for tex in m.textures:
        src = SOURCE.parent / str(tex).replace('\\', '/')
        dest = OUT / str(tex).replace('\\', '/')
        if src.exists():
            dest.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(src, dest)
    for i, mat in enumerate(m.materials):
        tint = (1., 1., 1.)
        if i in [11,12]: tint = (1.,1.,1.)  # restore the source's gray-white hair
        if i in [14,15]: tint = (.26,.19,.34)
        if i in [16,17]: tint = (.19,.12,.30)
        if i == 13: tint = (1.,1.,1.)  # preserve natural exposed skin, per user review
        if i in [0,1]: tint = (.97,.935,.91)  # warm ivory face, without purple cast
        if i == 5: tint = (.035,.03,.03)  # black eyebrows against gray-white hair
        if i == 7: tint = (.86,.84,.82)  # sclera: reduce the porcelain-white stare
        if i == 8: tint = (.79,.77,.75)  # retain heterochromia with quieter saturation/value
        if i == 10: tint = (.58,.58,.58)  # soften painted eye highlights
        mat.diffuse_color = common.RGB(*tint)
        mat.ambient_color = common.RGB(*[v*.2 for v in tint])
        mat.specular_color = common.RGB(.18,.12,.25)
        mat.specular_factor = 24.
        mat.edge_size = .3
        if i in [0,1,13]:
            mat.specular_color=common.RGB(.025,.022,.02)
            mat.specular_factor=8.
        if i<=10:
            mat.edge_size=.12
        if i==9:
            mat.alpha=.35  # eye shadow stays translucent, without masking the iris
    # Dress the legs in dark stockings without changing source vertices/UVs.
    cursor = 0
    reordered = []
    materials = []
    for i, mat in enumerate(m.materials):
        faces = np.array(m.indices[cursor:cursor+mat.vertex_count]).reshape(-1,3)
        cursor += mat.vertex_count
        if i == 13:
            mask = np.array([max(m.vertices[int(v)].position.y for v in f)<10.5 for f in faces])
            mat.vertex_count = int((~mask).sum()*3)
            materials.append(mat); reordered.extend(faces[~mask].flatten().tolist())
            dark = copy.deepcopy(mat)
            dark.name = 'Void stockings'; dark.diffuse_color = common.RGB(.038,.026,.065)
            dark.ambient_color = common.RGB(.01,.006,.02)
            dark.vertex_count = int(mask.sum()*3)
            materials.append(dark); reordered.extend(faces[mask].flatten().tolist())
        else:
            materials.append(mat); reordered.extend(faces.flatten().tolist())
    m.materials = materials; m.indices = reordered
    idle_data = (ASSETS/'animations/entity/outsider/idle.bin').read_bytes()
    _,_,nb,nf,fps = struct.unpack_from('<5I', idle_data)
    idle = np.frombuffer(idle_data, '<f4', offset=20).reshape(nf,nb,7)
    rest = np.array([list(b.position) for b in m.bones])
    def worlds(frame):
        rots=R.from_quat(frame[:,3:]).as_matrix(); pos=frame[:,:3].copy()
        for i,b in enumerate(m.bones[:nb]):
            if b.parent_index>=0:
                pos[i]=pos[b.parent_index]+rots[b.parent_index]@pos[i]
                rots[i]=rots[b.parent_index]@rots[i]
        return rots,pos
    base_rot,base_pos=worlds(idle[0])
    # Keep visible helpers skinned to the familiar rig. Separate ring pivots provide animation.
    def bone(name, position, parent=1):
        idx=len(m.bones)
        m.bones.append(pmx.Bone(name,name,V3(*position),parent,0,0x001e,tail_position=V3(0,1,0)))
        return idx
    rings=[bone('VoidHalo'+str(i),[0,14,3.2+i*.55]) for i in range(3)]
    buckets={}
    palette={'iron':(.026,.021,.043),'edge':(.17,.09,.25),'silver':(.36,.24,.48),
             'rune':(.38,.12,.72),'bright':(.71,.37,1.),'crystal':(.26,.055,.48),
             'staff_metal':(.95,.92,1.),'staff_silver':(.43,.36,.52),'staff_crystal':(.34,.09,.62)}
    def tri(points, material, b, posed=False):
        p=np.array(points,float)
        # Real UV coordinates for the AI-generated engraved metal surface.
        texcoords=[((v[0]-hx+1.5)*.30,v[1]*.32) for v in p] if material=='staff_metal' else [(0,0)]*3
        if posed:
            p=(p-base_pos[b])@base_rot[b]+rest[b]
        n=np.cross(p[1]-p[0],p[2]-p[0]); length=np.linalg.norm(n)
        if length<1e-9:return
        n/=length
        ids=[]
        for v,uv in zip(p,texcoords):
            ids.append(len(m.vertices))
            m.vertices.append(pmx.Vertex(V3(*v),V3(*n),common.Vector2(*uv),pmx.Bdef1(b),1.))
        buckets.setdefault(material,[]).extend(ids)
    def tube(path,radius,material,b,sides=5,posed=False):
        path=np.array(path,float)
        for a,z in zip(path[:-1],path[1:]):
            d=z-a; d/=np.linalg.norm(d)
            u=np.cross(d,[0,0,1] if abs(d[2])<.9 else [0,1,0]);u/=np.linalg.norm(u)
            v=np.cross(d,u)
            ra=[a+radius*(math.cos(t)*u+math.sin(t)*v) for t in np.linspace(0,2*math.pi,sides,endpoint=False)]
            rz=[x+z-a for x in ra]
            for k in range(sides):
                j=(k+1)%sides
                tri([ra[k],rz[k],rz[j]],material,b,posed);tri([ra[k],rz[j],ra[j]],material,b,posed)
    def circle(center,radius,width,material,b,start=0,end=2*math.pi,steps=96):
        c=np.array(center)
        # Rings are two-sided annular ribbons, not high-sided tubes. The three
        # separate Z planes supply depth; this saves 75% of their triangles.
        angles=np.linspace(start,end,steps+1)
        for a,z in zip(angles[:-1],angles[1:]):
            points=[c+[r*math.cos(t),r*math.sin(t),0] for r,t in
                    [(radius-width,a),(radius+width,a),(radius+width,z),(radius-width,z)]]
            tri(points[:3],material,b);tri([points[0],points[2],points[3]],material,b)
    def shard(center, width, length, depth, material, b, posed=False):
        x,y,z=center
        eq=[[x-width,y,z],[x,y,z-depth],[x+width,y,z],[x,y,z+depth]]
        for k in range(4):
            tri([[x,y+length*.6,z],eq[k],eq[(k+1)%4]],material,b,posed)
            tri([[x,y-length*.4,z],eq[(k+1)%4],eq[k]],material,b,posed)
    # Three physical planes, black rims and fine violet engravings. Rotation periods 24/-12/8 sec.
    for layer,(b,r) in enumerate(zip(rings,[8.8,7.0,5.2])):
        z=3.2+layer*.55; c=[0,14,z]
        circle(c,r,.14,'iron',b)
        circle([0,14,z-.13],r-.13,.028,'bright',b)
        circle(c,r-.72,.065,'edge',b)
        circle([0,14,z-.12],r-.69,.02,'rune',b)
        count=[32,24,16][layer]
        # A small consistent glyph alphabet, arranged in the local tangent/radial frame.
        glyphs=[[(0,-.22),(0,.22),(-.15,.05),(.15,-.08)],
                [(-.14,-.16),(-.14,.18),(.14,.18),(.14,-.16)],
                [(-.16,.2),(.16,.0),(-.16,-.2),(0,0),(.16,-.2)]]
        for k in range(count):
            a=2*math.pi*k/count;rad=np.array([math.cos(a),math.sin(a),0]);t=np.array([-math.sin(a),math.cos(a),0])
            origin=np.array([0,14,z-.17])+rad*(r-.38)
            tube([origin+t*x+rad*y for x,y in glyphs[k%3]],.025,'bright',b,4)
        for k in range(8):
            a=2*math.pi*k/8
            p=np.array(c)+[math.cos(a)*r,math.sin(a)*r,0]
            if layer==0:
                tip=np.array(c)+[math.cos(a)*(r+1.0),math.sin(a)*(r+1.0),0]
                tangent=np.array([-math.sin(a),math.cos(a),0])*.2
                tri([p-tangent,tip,p+tangent],'edge',b)
            # Radial broken arc segments emphasize distinct depths without filling the disk.
            circle([0,14,z-.2],r-.95,.032,'rune',b,a,a+.36,8)
        if layer==2:
            for offset in [0,math.pi]:
                points=[np.array(c)+[4.2*math.cos(a+offset),4.2*math.sin(a+offset),-.12] for a in np.linspace(0,2*math.pi,4)]
                tube(points,.038,'rune',b,4)
    # Staff follows right wrist. Geometry authored in the actual approval idle pose, then inverse skinned.
    hand=65; hx,hy,hz=base_pos[hand]+[-.22,-.35,-.45]
    staff_x=float(hx); staff_z=float(hz)
    tube([[hx,.35,hz],[hx,19.2,hz]],.145,'staff_metal',hand,12,True)
    tube([[hx-.08,.6,hz-.14],[hx-.08,18.9,hz-.14]],.014,'rune',hand,4,True)
    for y in [1.1,3.5,hy-1.2,hy+1.2,16.7,18.4]:
        tube([[hx,y-.10,hz],[hx,y+.10,hz]],.23,'staff_metal',hand,12,True)
        for dy in [-.11,.11]:
            tube([[hx,y+dy-.018,hz],[hx,y+dy+.018,hz]],.25,'staff_silver',hand,12,True)
        shard([hx,y,hz-.25],.075,.15,.035,'rune',hand,True)
    # Continuous leather/metal braid around the grip, alternating raised ridges.
    for phase in [0,math.pi]:
        path=[[hx+.175*math.cos(a+phase),hy-1.05+a/(8*math.pi)*2.1,hz+.175*math.sin(a+phase)] for a in np.linspace(0,8*math.pi,90)]
        tube(path,.026,'edge' if phase==0 else 'staff_silver',hand,4,True)
    # Tapered four-bladed crown: faceted broad faces with bevels and a raised spine.
    for quadrant in range(4):
        angle=math.pi*.5*quadrant+math.pi/4  # open the front view onto the crystal
        radial=np.array([math.cos(angle),0,math.sin(angle)])
        tangent=np.array([-math.sin(angle),0,math.cos(angle)])
        centers=[]; sections=[]
        for t in np.linspace(0,1,17):
            radius=.1+1.2*math.sin(math.pi*t*.92)
            c=np.array([hx,18.3+5.1*t,hz])+radial*radius
            width=(.24+.15*math.sin(t*math.pi))*(1-t)**.6+.006
            centers.append(c)
            depth=.115*(1-t)**.6+.004
            sections.append([c-tangent*width,c+radial*depth,c+tangent*width,c-radial*depth])
        for k in range(len(sections)-1):
            for j in range(4):
                n=(j+1)%4
                tri([sections[k][j],sections[k+1][j],sections[k+1][n]],'staff_metal',hand,True)
                tri([sections[k][j],sections[k+1][n],sections[k][n]],'staff_metal',hand,True)
        tube([s[1] for s in sections[:-2]],.022,'staff_silver',hand,4,True)
        tube([c-radial*.11 for c in centers[2:-3]],.015,'rune',hand,4,True)
        for k in [4,7,10]:
            c=centers[k]+radial*.13
            tube([c-tangent*.11,c+np.array([0,.11,0]),c+tangent*.11],.015,'staff_silver',hand,4,True)
    # Multi-faceted crystal with a luminous narrow core; not a flat purple diamond.
    bands=[(19.9,0.0),(20.55,.56),(21.35,.62),(22.55,0.0)]
    for (y0,r0),(y1,r1) in zip(bands[:-1],bands[1:]):
        for k in range(8):
            a=k*math.pi/4;z=(k+1)*math.pi/4
            p0=[hx+r0*math.cos(a),y0,hz+r0*math.sin(a)]
            p1=[hx+r1*math.cos(a),y1,hz+r1*math.sin(a)]
            p2=[hx+r1*math.cos(z),y1,hz+r1*math.sin(z)]
            p3=[hx+r0*math.cos(z),y0,hz+r0*math.sin(z)]
            material='staff_crystal' if k%2 else 'crystal'
            if r1>0:tri([p0,p1,p2],material,hand,True)
            if r0>0:tri([p0,p2,p3],material,hand,True)
    shard([hx,21.2,hz-.58],.055,1.4,.04,'bright',hand,True)
    # Two gimbal rings, inset dotted glyphs, and a pointed metal ferrule.
    for offset,tilt in [(0,.55),(.3,-.55)]:
        path=[[hx+1.04*math.cos(a),21.1+offset+.5*math.sin(a),hz+tilt*math.sin(a)] for a in np.linspace(0,2*math.pi,49)]
        tube(path,.052,'staff_silver',hand,5,True)
        for k in range(12):
            a=2*math.pi*k/12
            shard([hx+1.04*math.cos(a),21.1+offset+.5*math.sin(a),hz+tilt*math.sin(a)-.045],.035,.09,.025,'rune',hand,True)
    shard([hx,.55,hz],.24,1.05,.22,'staff_metal',hand,True)
    # Layered pauldrons, jagged collar and a long split overskirt change the silhouette.
    for side,b in [(-1,53),(1,15)]:
        for k in range(3):
            shard([side*(2.2+k*.42),16.2-k*.16,.35],.38,1.3+k*.32,.65,'iron',b)
            tube([[side*(2.2+k*.42),16.9-k*.16,-.1],[side*(2.5+k*.42),16.1-k*.16,-.4]],.035,'silver',b)
    for side in [-1,1]:
        for k in range(3):
            x=side*(1.5+k*.42)
            p=[[x,10.3,1.2],[x+side*.9,2.0+k*.5,1.9],[x-side*.5,3.3+k*.3,2.1]]
            tri(p,'iron',9)
            tube([p[0],p[1]],.038,'rune',9,4)
        # Crown shards rise above the existing horns.
        shard([side*.65,20.8,.2],.22,2.0,.32,'iron',11)
        tube([[side*.65,20.3,-.13],[side*.65,21.7,-.05]],.028,'rune',11)
    # Pendant / rune clasps, attached to chest and pelvis.
    for y,b in [(15.4,6),(11.9,9)]:
        shard([0,y,-1.02],.3,.95,.13,'crystal',b)
        tube([[-.32,y,-1.15],[0,y+.55,-1.15],[.32,y,-1.15],[0,y-.4,-1.15],[-.32,y,-1.15]],.027,'bright',b)
    for name,ids in buckets.items():
        mat=copy.deepcopy(m.materials[0]);mat.name='Void '+name;mat.english_name=mat.name
        mat.diffuse_color=common.RGB(*palette[name]);mat.ambient_color=common.RGB(*[x*.35 for x in palette[name]])
        mat.texture_index=-1;mat.sphere_texture_index=-1;mat.sphere_mode=0;mat.toon_sharing_flag=1;mat.toon_texture_index=0
        if name=='staff_metal':
            mat.texture_index=len(m.textures);m.textures.append('textures/void_staff_metal.png')
        mat.flag=1;mat.edge_size=0;mat.vertex_count=len(ids);mat.comment='Generated review ornament; '+name
        m.materials.append(mat);m.indices.extend(ids)
    # Weld only generated ornament vertices; keep all original vertex indices
    # stable so 62 existing face morphs and original skin weights remain valid.
    original_n=original_counts[0]
    merged=m.vertices[:original_n]; remap={}; shared={}
    for i in range(original_n,len(m.vertices)):
        v=m.vertices[i]
        key=tuple(round(x,6) for x in list(v.position)+list(v.normal)+[v.uv.x,v.uv.y])+(v.deform.index0,)
        if key not in shared:
            shared[key]=len(merged);merged.append(v)
        remap[i]=shared[key]
    m.vertices=merged
    m.indices=[remap.get(i,i) for i in m.indices]
    # Resolved skeletal animation avoids applying append/IK twice in MMD.
    for b in m.bones:
        b.flag &= ~(0x100|0x200|0x20)
        b.ik=None
    # No secondary physics in this approval copy (retain source physics in original PMX).
    m.rigidbodies=[];m.joints=[]
    # pymeshio only writes vertex morphs. Reuse our tested group flattener;
    # preserve names/indices and identical additive vertex displacement.
    flattened={i:flatten(m,i) for i,x in enumerate(m.morphs) if x.morph_type==0}
    for i,values in flattened.items():
        m.morphs[i].morph_type=1
        m.morphs[i].offsets=[pmx.VertexMorphOffset(v,V3(*d)) for v,d in values.items()]
    output=OUT/'outsider_void_boss_v1.pmx'
    writer.write_to_file(m,str(output))
    loaded=reader.read_from_file(str(output))
    assert len(loaded.vertices)==len(m.vertices) and len(loaded.morphs)==original_counts[3]
    assert sum(x.vertex_count for x in loaded.materials)==len(loaded.indices)
    # A 24-second loop: original in-project idle plus three continuous counter-rotating rings.
    frames=[]
    rest_all=np.array([list(b.position) for b in m.bones])
    for f in range(721):
        v=np.zeros((len(m.bones),7));v[:nb]=idle[(f*2)%240];v[nb:,6]=1
        for j,b in enumerate(rings):
            v[b,:3]=rest_all[b]-rest_all[m.bones[b].parent_index]
            v[b,3:]=R.from_euler('z',f/720*360*[1,-2,3][j],degrees=True).as_quat()
        frames.append(v)
    frames=np.array(frames,dtype='<f4')
    np.savez_compressed(OUT/'review_motion.npz',frames=frames)
    with (OUT/'review_idle_halo.vmd').open('wb') as f:
        f.write(b'Vocaloid Motion Data 0002'.ljust(30,b'\0'));f.write(b'Void Regent v1'.ljust(20,b'\0'))
        records=[]
        for i,b in enumerate(m.bones):
            offset=rest_all[i]-(rest_all[b.parent_index] if b.parent_index>=0 else 0)
            changing=np.max(np.abs(frames[:,i]-frames[0,i]))>1e-6
            samples=range(0,721,3) if changing else [0,720]
            for t in samples:
                records.append((b.name,t,frames[t,i,:3]-offset,frames[t,i,3:]))
        f.write(struct.pack('<I',len(records)))
        interp=bytes([20,20,20,20,20,20,20,20,107,107,107,107,107,107,107,107]*4)
        for name,t,translation,rotation in records:
            f.write(str(name).encode('shift_jis',errors='replace')[:15].ljust(15,b'\0'))
            f.write(struct.pack('<I7f',t,*translation,*rotation));f.write(interp)
        f.write(struct.pack('<5I',0,0,0,0,0))
    report={'source':str(SOURCE),'original_counts':original_counts,'review_vertices':len(m.vertices),
            'review_triangles':len(m.indices)//3,'review_bones':len(m.bones),'morphs':len(m.morphs),
            'halo_bones':rings,'staff_bone':hand,'staff_pos':[float(hx),float(hy),float(hz)],
            'original_textures_unmodified':True,'runtime_installed':False,'motion_seconds':24,
            'local_review_only':True,'source_credit':'元空Ame / miHoYo'}
    (OUT/'review_manifest.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(report,ensure_ascii=False,indent=2))


if __name__=='__main__': main()
