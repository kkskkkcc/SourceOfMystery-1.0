"""Render the packaged mesh/poses offscreen, independently of Minecraft.

python -m pip install --target build/outsider-preview-deps moderngl
python tools/outsider/preview.py
This verifies textures/topology and several animation poses, not Forge integration.
"""
import argparse
import json
import struct
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw
from scipy.spatial.transform import Rotation

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'build/outsider-preview-deps'))
import moderngl

ASSETS = ROOT / 'src/main/resources/assets/sourceofmystery'
OUT = ROOT / 'build/outsider-validation'
OUT.mkdir(parents=True, exist_ok=True)
MOTION_DIR = None
PREFIX = 'smooth'
CYCLES = 1


def load_mesh():
    data = (ASSETS / 'meshes/entity/outsider.bin').read_bytes()
    magic, version, nv, ni, ng = struct.unpack_from('<5I', data)
    assert magic == 0x4F555431 and version == 3
    dtype = np.dtype([('position','<f4',3),('normal','<f4',3),('uv','<f4',2),
                      ('bones','<i4',4),('weights','<f4',4)])
    vertices = np.frombuffer(data, dtype, nv, 20)
    offset = 20 + nv * dtype.itemsize
    groups = np.frombuffer(data, '<u4', ng*3, offset).reshape(-1,3)
    offset += ng*12
    nb, = struct.unpack_from('<I', data, offset)
    offset += 4
    bones = np.frombuffer(data, np.dtype([('parent','<i4'),('rest','<f4',3)]), nb, offset)
    offset += nb*16
    nc, = struct.unpack_from('<I', data, offset)
    offset += 4 + nc*4
    indices = np.frombuffer(data, '<u4', ni, offset)
    assert offset+ni*4 == len(data)
    assert indices.max() < nv
    assert groups[:,2].sum() == ni and np.all(groups[:,2] % 3 == 0)
    assert np.allclose(vertices['weights'].sum(axis=1),1)
    return vertices, bones, groups, indices


def load_motion(name, bone_count):
    path = ASSETS / f'animations/entity/outsider/{name}.bin'
    if MOTION_DIR is not None and (MOTION_DIR / f'{name}.bin').exists():
        path = MOTION_DIR / f'{name}.bin'
    data = path.read_bytes()
    magic, version, nb, nf, fps = struct.unpack_from('<5I', data)
    assert (magic,version,nb,fps) == (0x4F414E31,3,bone_count,60)
    return np.frombuffer(data,'<f4',offset=20).reshape(nf,nb,7)


def skin(vertices, bones, frame):
    rotations = Rotation.from_quat(frame[:,3:]).as_matrix()
    frame = frame.copy()
    for i, parent in enumerate(bones['parent']):
        if parent >= 0:
            frame[i,:3] = frame[parent,:3] + rotations[parent] @ frame[i,:3]
            rotations[i] = rotations[parent] @ rotations[i]
    positions = np.zeros((len(vertices),3))
    normals = positions.copy()
    for j in range(4):
        ids = vertices['bones'][:,j].clip(0)
        weights = vertices['weights'][:,j,None]
        positions += weights * (np.einsum('nij,nj->ni', rotations[ids], vertices['position']-bones['rest'][ids])+frame[ids,:3])
        normals += weights * np.einsum('nij,nj->ni', rotations[ids], vertices['normal'])
    return positions, normals


def main():
    vertices,bones,groups,indices = load_mesh()
    idle = load_motion('idle',len(bones))
    walk = load_motion('walk',len(bones))
    half = (len(walk)-1)//(2*CYCLES)
    # Verify actual skin deformation, including the D-bone bound lower limbs.
    poses = [skin(vertices,bones,f)[0] for f in [idle[0],walk[0],walk[half//2],walk[half],walk[half+half//2],walk[-1]]]
    assert max(np.linalg.norm(poses[1]-poses[3],axis=1)) > 1
    assert np.max(np.abs(poses[1]-poses[-1])) < 1e-3
    # A,B,C,C generates ABC + ACC under the vanilla quad triangulation.
    quad = np.repeat(indices.reshape(-1,3), [1,1,2],axis=1)
    assert np.array_equal(quad[:,:3].flatten(), indices)
    assert np.array_equal(quad[:,2],quad[:,3])
    ctx = moderngl.create_standalone_context()
    prog = ctx.program(vertex_shader='''#version 330
        uniform mat4 mvp;
        in vec3 position; in vec3 normal; in vec2 uv;
        out vec2 texcoord; out float light;
        void main() {
            gl_Position=mvp*vec4(position,1);
            texcoord=uv;
            light=0.65+0.35*max(dot(normalize(normal),normalize(vec3(-0.2,0.7,-1))),0);
        }''',fragment_shader='''#version 330
        uniform sampler2D diffuse;
        in vec2 texcoord; in float light; out vec4 color;
        void main() {
            vec4 t=texture(diffuse,texcoord);
            if(t.a<0.1) discard;
            color=vec4(t.rgb*light,1);
        }''')
    textures=[]
    for name in ['face','body_map2','body_map1']:
        im=Image.open(ASSETS/f'textures/entity/outsider/{name}.png').convert('RGBA')
        tex=ctx.texture(im.size,4,im.tobytes())
        tex.filter=(moderngl.LINEAR,moderngl.LINEAR)
        textures.append(tex)
    fbo=ctx.simple_framebuffer((500,650))
    fbo.use()
    ctx.enable(moderngl.DEPTH_TEST)
    ibo=ctx.buffer(indices.tobytes())
    vbo=ctx.buffer(reserve=len(vertices)*32)
    vao=ctx.vertex_array(prog,[(vbo,'3f 3f 2f','position','normal','uv')],ibo)

    def render(frame, angle=0):
        p,n=skin(vertices,bones,frame)
        data=np.c_[p,n,vertices['uv']].astype('f4')
        vbo.write(data.tobytes())
        # Front is -Z in the unconverted PMX. Right-handed orthographic camera.
        rotate=np.eye(4); rotate[:3,:3]=Rotation.from_euler('y',angle,degrees=True).as_matrix()
        view=np.diag([-1.,1.,-1.,1.]);view[1,3]=-11;view[2,3]=-40
        proj=np.diag([1/8.5,1/11.1,-2/100,1.]);proj[2,3]=-1
        prog['mvp'].write((proj@view@rotate).astype('f4').T.tobytes())
        fbo.clear(0.12,0.16,0.21,1)
        for slot,start,count in groups:
            textures[int(slot)].use()
            vao.render(moderngl.TRIANGLES,vertices=int(count),first=int(start))
        return Image.frombytes('RGB',fbo.size,fbo.read(components=3)).transpose(Image.Transpose.FLIP_TOP_BOTTOM)

    sheet=Image.new('RGB',(1500,1360),(24,29,36))
    draw=ImageDraw.Draw(sheet)
    for i,(label,frame,angle) in enumerate([
        ('Idle / front',idle[0],0),('Walk / step 1',walk[0],0),('Walk / step 2',walk[half],0),
        ('Idle / side',idle[0],70),('Walk / step 1 / side',walk[0],70),('Walk / step 2 / side',walk[half],70)]):
        x,y=(i%3)*500,(i//3)*680
        sheet.paste(render(frame,angle),(x,y+30));draw.text((x+15,y+8),label,fill='white')
    sheet.save(OUT/f'{PREFIX}-poses.png')
    movie=[render(frame,30) for frame in walk[:-1]]
    movie[0].save(OUT/f'{PREFIX}-walk.webp',save_all=True,append_images=movie[1:],
                  duration=([16,17,17]*len(movie))[:len(movie)],loop=0,quality=85)
    movie[0].save(OUT/f'{PREFIX}-walk.gif',save_all=True,append_images=movie[2::2],
                  duration=([30,30,40]*len(movie))[:len(movie[::2])],loop=0)
    # Skinning-relevant regression: the D-chain follows the authored leg, and
    # local-pose blends preserve the knee/ankle bone lengths throughout a cycle.
    for a,b in [(83,98),(84,99),(85,100),(90,102),(91,103),(92,104)]:
        assert np.allclose(walk[:,a],walk[:,b],atol=1e-4)
    for bone in [84,85,91,92,99,100,103,104]:
        lengths = np.linalg.norm(walk[:,bone,:3],axis=1)
        assert lengths.max()-lengths.min()<1e-5
        assert np.allclose(lengths,np.linalg.norm(idle[0,bone,:3]),atol=1e-5)
    # Check resolved local rotations between adjacent samples for solver pops.
    qa=Rotation.from_quat(walk[:-1,:,3:].reshape(-1,4))
    qb=Rotation.from_quat(walk[1:,:,3:].reshape(-1,4))
    max_step=float(np.degrees((qa.inv()*qb).magnitude()).max())
    assert max_step < 8, max_step
    report=dict(vertices=len(vertices),triangles=len(indices)//3,quad_submissions=len(quad),
                all_triangles_preserved=True,degenerate_extra_triangles=True,
                loop_max_vertex_error=float(np.max(np.abs(poses[1]-poses[-1]))),
                stride_max_vertex_motion=float(np.max(np.linalg.norm(poses[1]-poses[3],axis=1))),
                max_adjacent_bone_rotation_degrees=max_step, local_bone_lengths_preserved=True)
    (OUT/f'{PREFIX}-checks.json').write_text(json.dumps(report,indent=2))
    print(report)
    print(OUT/f'{PREFIX}-poses.png')


if __name__=='__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('--motion-dir',type=Path)
    parser.add_argument('--prefix',default='smooth')
    parser.add_argument('--cycles',type=int,default=1)
    args=parser.parse_args()
    MOTION_DIR=args.motion_dir
    PREFIX=args.prefix
    CYCLES=args.cycles
    main()
