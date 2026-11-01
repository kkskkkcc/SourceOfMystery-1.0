"""Package the approved PMX, material tints, staff and all nine runtime clips.

Reports stay outside assets/**/animations: GeckoLib scans JSON there globally.
"""
import json,struct,shutil
from pathlib import Path
import numpy as np
from PIL import Image
from pymeshio.pmx import reader
from generate_mesh import OUT,MESH_OUT,TEX_OUT,ANIM_OUT
from combat_review import DEST,worlds

CLIPS=['flight_idle','flight_move','descend','landing','grounded_idle','takeoff',
       'dark_grasp','dark_silence','annihilation','offering_intro']

def main():
    m=reader.read_from_file(str(DEST/'outsider_combat.pmx'))
    from export_springs import export
    export(m)
    TEX_OUT.mkdir(parents=True,exist_ok=True);ANIM_OUT.mkdir(parents=True,exist_ok=True)
    texture_slots={'Lindverne_Face_D.png':(0,'face.png'),'Lindverne_Body_Map2_D.png':(1,'body_map2.png'),
                   'Lindverne_Body_Map1_D.png':(2,'body_map1.png'),'void_staff_metal.png':(3,'staff.png')}
    for texture in m.textures:
        source=(DEST/texture).resolve()
        if source.name in texture_slots:shutil.copyfile(source,TEX_OUT/texture_slots[source.name][1])
    Image.new('RGBA',(1,1),(255,255,255,255)).save(TEX_OUT/'white.png')
    with MESH_OUT.open('wb') as f:
        f.write(struct.pack('<5I',0x4F555431,4,len(m.vertices),len(m.indices),len(m.materials)))
        for v in m.vertices:
            f.write(struct.pack('<8f',*v.position,*v.normal,*v.uv))
            d=v.deform;kind=type(d).__name__
            if kind=='Bdef1':indices=[d.index0,0,0,0];weights=[1,0,0,0]
            elif kind in ['Bdef2','Sdef']:indices=[d.index0,d.index1,0,0];weights=[d.weight0,1-d.weight0,0,0]
            else:indices=[d.index0,d.index1,d.index2,d.index3];weights=[d.weight0,d.weight1,d.weight2,d.weight3]
            assert abs(sum(weights)-1)<1e-4
            f.write(struct.pack('<4i4f',*indices,*weights))
        offset=0
        for i,mat in enumerate(m.materials):
            slot=texture_slots[Path(m.textures[mat.texture_index]).name][0] if mat.texture_index>=0 else 4
            c=mat.diffuse_color
            flags=(1 if i in [9,10] else 0)|(2 if mat.name in ['Void bright','Void rune'] else 0)
            f.write(struct.pack('<3I4fI',slot,offset,mat.vertex_count,c.r,c.g,c.b,mat.alpha,flags));offset+=mat.vertex_count
        f.write(struct.pack('<I',len(m.bones)))
        for i,b in enumerate(m.bones):
            assert b.parent_index<i
            f.write(struct.pack('<i3f',b.parent_index,*b.position))
        f.write(struct.pack('<I',0))
        f.write(np.asarray(m.indices,dtype='<u4').tobytes())
    report={'vertices':len(m.vertices),'triangles':len(m.indices)//3,'materials':len(m.materials),'bones':len(m.bones),'clips':{}}
    for clip in CLIPS:
        frames=np.load(DEST/(clip+'.npz'))['frames']
        assert frames.shape[1]==len(m.bones) and np.isfinite(frames).all()
        shutil.copyfile(DEST/(clip+'.bin'),ANIM_OUT/(clip+'.bin'))
        report['clips'][clip]={'frames':len(frames),'seconds':(len(frames)-1)/60}
    # Server socket data is the exact same final pose used by the client, in model units.
    frames=np.load(DEST/'dark_grasp.npz')['frames']
    # Rest-space palm normal is -Z. Rotate it with the wrist; a world +Y
    # offset only happened to look correct for one palm-up keyframe.
    sockets=[]
    for frame in frames:
        wq,wp=worlds(m,frame);sockets.append(wp[35]+wq[35]@np.array([0,0,-1.]))
    sockets=np.asarray(sockets,dtype='<f4')
    socket_path=OUT/'meshes/entity/outsider_grasp_socket.bin'
    with socket_path.open('wb') as f:
        f.write(struct.pack('<3I',0x4F534B31,len(frames),60));f.write(sockets.tobytes())
    # Core of the staff crystal, transformed by the actual final staff bone.
    from annihilation_motion import CORE
    frames=np.load(DEST/'annihilation.npz')['frames']
    rest=np.array(list(m.bones[557].position))
    cores=[]
    for frame in frames:
        wq,wp=worlds(m,frame);cores.append(wp[557]+wq[557]@(CORE-rest))
    with (OUT/'meshes/entity/outsider_ritual_socket.bin').open('wb') as f:
        f.write(struct.pack('<3I',0x4F534B31,len(frames),60));f.write(np.asarray(cores,dtype='<f4').tobytes())
    report['mesh_bytes']=MESH_OUT.stat().st_size
    from face_motion import main as export_expressions
    export_expressions()
    report['expression_channels']=12
    (DEST/'runtime-export.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
    print(json.dumps(report,indent=2))

if __name__=='__main__':main()
