"""Render geometry exported by the actual Forge client buffer regression.

This is an offline OpenGL review, not an in-game screenshot.
"""
import sys,json,gzip,struct
from pathlib import Path
sys.path.insert(0,'C:/Temp/outsider-preview-deps')
import moderngl
import numpy as np
from PIL import Image

ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'art/outsider_void_boss/v2'
TEXTURES=ROOT/'src/main/resources/assets/sourceofmystery/textures/entity/outsider'

def look_at(eye,center):
    forward=np.asarray(center,float)-eye;forward/=np.linalg.norm(forward)
    right=np.cross(forward,[0,1,0]);right/=np.linalg.norm(right)
    up=np.cross(right,forward)
    m=np.eye(4);m[:3,:3]=[right,up,-forward];m[:3,3]=-m[:3,:3]@eye
    return m

def operation():
    """Review exactly the shipped array geometry at 12–18s, with beam hidden."""
    data=gzip.decompress((ROOT/'build/ritual-v2-operation.bin.gz').read_bytes())
    cursor=0
    def unpack(fmt):
        nonlocal cursor
        result=struct.unpack_from(fmt,data,cursor);cursor+=struct.calcsize(fmt)
        return result
    magic,count,fps=unpack('>3I');assert magic==0x4F525631
    frames=[]
    for _ in range(count):
        time,batches=unpack('>fI');result=[]
        for _ in range(batches):
            length,=unpack('>H');texture=data[cursor:cursor+length].decode();cursor+=length
            vertices,=unpack('>I')
            rows=np.frombuffer(data,dtype='>f4',count=vertices*9,offset=cursor).astype('f4').reshape(-1,4,9)
            cursor+=vertices*9*4
            assert np.isfinite(rows).all()
            result.append((texture,rows[:,[0,1,2,0,2,3]].reshape(-1,9)))
        frames.append((time,result))
    assert cursor==len(data)
    ctx=moderngl.create_standalone_context()
    prog=ctx.program(vertex_shader='''#version 330
uniform mat4 mvp;in vec3 position;in vec4 tint;in vec2 uv;out vec4 color;out vec2 st;
void main(){gl_Position=mvp*vec4(position,1);color=tint;st=uv;}
''',fragment_shader='''#version 330
uniform sampler2D tex;in vec4 color;in vec2 st;out vec4 fragment;
void main(){fragment=texture(tex,st)*color;}
''')
    ctx.enable(moderngl.BLEND);ctx.blend_func=moderngl.SRC_ALPHA,moderngl.ONE_MINUS_SRC_ALPHA
    textures={}
    for texture in {name for _,batches in frames for name,_ in batches}:
        im=Image.open(TEXTURES/texture).convert('RGBA')
        tex=ctx.texture(im.size,4,im.tobytes());tex.filter=moderngl.LINEAR,moderngl.LINEAR;textures[texture]=tex
    metadata={'source':'Forge client vertex capture, current installed renderer','seconds':[12,18],'fps':fps,
              'frames':count,'beam_hidden_for_review':True,'core_radial_band_rates_degrees':[16,-38,27,-19],
              'satellite_orbit_radians_per_second':.38,'satellite_spin_degrees_per_second':12,
              'semantic_medallion_independent_spin':False}
    OUT.mkdir(parents=True,exist_ok=True)
    for name,size,center,eye,half in [
            ('super-core-operation',(960,960),[0,6,0],[0,44,.001],10.5),
            ('super-array-operation',(1152,864),[0,4.5,0],[28,24,33],15.5)]:
        view=look_at(np.asarray(eye,float),np.asarray(center,float))
        proj=np.diag([1/(half*size[0]/size[1]),1/half,-2/150,1]);proj[2,3]=-1
        prog['mvp'].write((proj@view).astype('f4').T.tobytes())
        fbo=ctx.simple_framebuffer(size);fbo.use();movie=[]
        vbo=ctx.buffer(reserve=max(len(rows) for _,batches in frames for _,rows in batches)*36)
        vao=ctx.vertex_array(prog,[(vbo,'3f 4f 2f','position','tint','uv')])
        for index,(time,batches) in enumerate(frames):
            fbo.clear(.028,.035,.06,1)
            for texture,rows in batches:
                if name=='super-core-operation' and texture!='ritual_v2_super.png':continue
                textures[texture].use();vbo.write(rows.tobytes());vao.render(moderngl.TRIANGLES,vertices=len(rows))
            image=Image.frombytes('RGB',size,fbo.read(components=3)).transpose(Image.Transpose.FLIP_TOP_BOTTOM)
            movie.append(image)
            if index in [0,90,179]:image.save(OUT/(name+f'-{index:03}.png'))
            if index%60==0:print(name,'frame',index,flush=True)
        durations=[round((i+1)*1000/fps)-round(i*1000/fps) for i in range(count)]
        movie[0].save(OUT/(name+'.webp'),save_all=True,append_images=movie[1:],duration=durations,loop=0,quality=90,method=4)
        print('Saved',name,flush=True);vao.release();vbo.release();fbo.release()
    (OUT/'operation-preview.json').write_text(json.dumps(metadata,indent=2),encoding='utf-8')

def main():
    OUT.mkdir(parents=True,exist_ok=True)
    batches=json.loads((ROOT/'build/ritual-v2-geometry.json').read_text())
    ctx=moderngl.create_standalone_context()
    prog=ctx.program(vertex_shader='''#version 330
uniform mat4 mvp;in vec3 position;in vec4 tint;in vec2 uv;out vec4 color;out vec2 st;
void main(){gl_Position=mvp*vec4(position,1);color=tint;st=uv;}
''',fragment_shader='''#version 330
uniform sampler2D tex;in vec4 color;in vec2 st;out vec4 fragment;
void main(){fragment=texture(tex,st)*color;}
''')
    size=(1920,1440);fbo=ctx.simple_framebuffer(size);fbo.use()
    fbo.clear(.028,.035,.06,1);ctx.enable(moderngl.BLEND)
    ctx.blend_func=moderngl.SRC_ALPHA,moderngl.ONE_MINUS_SRC_ALPHA
    center=np.array([0,4.5,0]);eye=np.array([28,24,33.])
    view=look_at(eye,center)
    proj=np.diag([1/(18*size[0]/size[1]),1/18,-2/150,1]);proj[2,3]=-1
    prog['mvp'].write((proj@view).astype('f4').T.tobytes())
    # Preserve the same batch order and alpha/tint emitted by Java.
    for batch in batches:
        vertices=np.asarray(batch['vertices'],dtype='f4').reshape(-1,4,9)
        tri=vertices[:,[0,1,2,0,2,3]].reshape(-1,9)
        im=Image.open(TEXTURES/batch['texture']).convert('RGBA')
        tex=ctx.texture(im.size,4,im.tobytes());tex.filter=moderngl.LINEAR,moderngl.LINEAR;tex.use()
        vbo=ctx.buffer(tri.tobytes());vao=ctx.vertex_array(prog,[(vbo,'3f 4f 2f','position','tint','uv')])
        vao.render(moderngl.TRIANGLES);vao.release();vbo.release();tex.release()
    Image.frombytes('RGB',size,fbo.read(components=3)).transpose(Image.Transpose.FLIP_TOP_BOTTOM).save(OUT/'fully-open-offline.png')
    print('Rendered',OUT/'fully-open-offline.png')

if __name__=='__main__':
    if '--operation' in sys.argv:operation()
    else:main()
