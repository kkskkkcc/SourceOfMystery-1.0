"""Actual PMX review render, including material multipliers, skinning and halo VMD poses."""
import sys
from pathlib import Path
sys.path.insert(0, 'C:/Temp/outsider-preview-deps')
import moderngl
import numpy as np
from PIL import Image,ImageDraw,ImageFont
from scipy.spatial.transform import Rotation as R
from pymeshio.pmx import reader
from void_boss_review import OUT


def main():
    combat=sys.argv[sys.argv.index('--combat')+1] if '--combat' in sys.argv else None
    daily=sys.argv[sys.argv.index('--daily')+1] if '--daily' in sys.argv else None
    if daily:
        combat=daily
    asset_dir=OUT/'combat' if combat else OUT
    m=reader.read_from_file(str(asset_dir/('outsider_combat.pmx' if combat else 'outsider_void_boss_v1.pmx')))
    frames=np.load(asset_dir/(combat+'.npz' if combat else 'review_motion.npz'))['frames']
    pos=np.array([list(v.position) for v in m.vertices]); normals=np.array([list(v.normal) for v in m.vertices])
    from generate_face_morphs import NAMES,flatten
    from face_motion import compose,preview_blink
    face_weights=np.load(asset_dir/(combat+'.face.npz'))['weights'] if combat else None
    morphs=np.zeros((len(NAMES),len(pos),3))
    by_name={str(v.name):i for i,v in enumerate(m.morphs)}
    for i,name in enumerate(NAMES):
        for vertex,delta in flatten(m,by_name[name]).items():morphs[i,vertex]=delta
    uv=np.array([[v.uv.x,v.uv.y] for v in m.vertices])
    nb=len(m.bones); nv=len(pos)
    bi=np.zeros((nv,4),int);bw=np.zeros((nv,4))
    for i,v in enumerate(m.vertices):
        d=v.deform
        if type(d).__name__=='Bdef1':bi[i,0]=d.index0;bw[i,0]=1
        elif type(d).__name__ in ('Bdef2','Sdef'):
            bi[i,:2]=[d.index0,d.index1];bw[i,:2]=[d.weight0,1-d.weight0]
        else:
            bi[i]=[d.index0,d.index1,d.index2,d.index3];bw[i]=[d.weight0,d.weight1,d.weight2,d.weight3]
    bi=bi.clip(0)
    rest=np.array([list(b.position) for b in m.bones])
    ctx=moderngl.create_standalone_context()
    prog=ctx.program(vertex_shader='''#version 330
    uniform mat4 mvp; in vec3 position; in vec3 normal; in vec2 uv;
    out vec3 n; out vec2 st; out vec3 p;
    void main(){gl_Position=mvp*vec4(position,1);n=normal;st=uv;p=position;}
    ''',fragment_shader='''#version 330
    uniform sampler2D tex; uniform vec3 tint; uniform float glow; uniform vec3 specular;
    uniform float overlay_alpha;
    in vec3 n; in vec2 st; in vec3 p; out vec4 color;
    void main(){vec4 t=texture(tex,st);if(t.a<.1)discard;
    vec3 N=normalize(n); float diffuse=.43+.62*max(dot(N,normalize(vec3(-.4,.7,-.8))),0.);
    float rim=pow(1.-abs(dot(N,vec3(0,0,-1))),3.);
    float spec=pow(max(dot(reflect(-normalize(vec3(-.4,.7,-.8)),N),vec3(0,0,-1)),0.),24.);
    vec3 base=t.rgb*tint;vec3 lit=base*diffuse+vec3(.1,.065,.16)*rim+specular*spec;
    if(glow>.5)lit=base*1.35;
    // Hair/clothes use the game's cutout treatment. Only eye overlay planes
    // require continuous transparency; blending unsorted hair gives triangle seams.
    color=vec4(pow(clamp(lit,0.,1.),vec3(.82)),overlay_alpha>=0.?t.a*overlay_alpha:1.);}
    ''')
    textures=[]
    for path in m.textures:
        im=Image.open(asset_dir/path).convert('RGBA');tex=ctx.texture(im.size,4,im.tobytes());tex.filter=(moderngl.LINEAR,moderngl.LINEAR);textures.append(tex)
    white=ctx.texture((1,1),4,bytes([255]*4))
    ibo=ctx.buffer(np.array(m.indices,'u4').tobytes());vbo=ctx.buffer(reserve=nv*32)
    vao=ctx.vertex_array(prog,[(vbo,'3f 3f 2f','position','normal','uv')],ibo)
    staff_indices=[]; staff_groups=[];offset=0
    all_indices=np.array(m.indices).reshape(-1,3)
    inspect_skirt='--inspect-skirt' in sys.argv
    review_indices=[];review_groups=[];skirt_indices=[]
    review_offset=0
    for mat in m.materials:
        faces=all_indices[review_offset//3:(review_offset+mat.vertex_count)//3];review_offset+=mat.vertex_count
        visible=faces[~np.any(np.isin(bi[faces,0],[554,555,556]),axis=1)]
        review_groups.append((len(review_indices),visible.size));review_indices.extend(visible.flatten())
        if combat:skirt_indices.extend(faces[np.all((faces>=35118)&(bi[faces,0]==558),axis=1)].flatten())
    review_ibo=ctx.buffer(np.asarray(review_indices,'u4').tobytes())
    review_vao=ctx.vertex_array(prog,[(vbo,'3f 3f 2f','position','normal','uv')],review_ibo)
    skirt_ibo=ctx.buffer(np.asarray(skirt_indices if skirt_indices else [0,0,0],'u4').tobytes())
    skirt_vao=ctx.vertex_array(prog,[(vbo,'3f 3f 2f','position','normal','uv')],skirt_ibo)
    for mat in m.materials:
        faces=all_indices[offset//3:(offset+mat.vertex_count)//3];offset+=mat.vertex_count
        mask=np.all((faces>=35118)&(bi[faces,0]==(557 if combat else 65)),axis=1)
        selected=faces[mask].flatten()
        staff_groups.append((len(staff_indices),len(selected)));staff_indices.extend(selected)
    staff_ibo=ctx.buffer(np.array(staff_indices,'u4').tobytes())
    staff_vao=ctx.vertex_array(prog,[(vbo,'3f 3f 2f','position','normal','uv')],staff_ibo)
    ctx.enable(moderngl.DEPTH_TEST)
    ctx.enable(moderngl.BLEND)
    ctx.blend_func=(moderngl.SRC_ALPHA,moderngl.ONE_MINUS_SRC_ALPHA)
    cache={}
    def deform(frame,index):
        rot=R.from_quat(frame[:,3:]).as_matrix();world=frame[:,:3].copy()
        for i,b in enumerate(m.bones):
            if b.parent_index>=0:
                world[i]=world[b.parent_index]+rot[b.parent_index]@world[i]
                rot[i]=rot[b.parent_index]@rot[i]
        p=np.zeros_like(pos);n=np.zeros_like(normals)
        source=pos
        if face_weights is not None:
            weights=compose(face_weights[index],preview_blink(index/60))
            source=pos+np.einsum('i,ijk->jk',weights,morphs)
        for j in range(4):
            ids=bi[:,j];weight=bw[:,j,None]
            p+=weight*(np.einsum('nij,nj->ni',rot[ids],source-rest[ids])+world[ids])
            n+=weight*np.einsum('nij,nj->ni',rot[ids],normals)
        return np.c_[p,n,uv].astype('f4')
    def render(angle=0,frame=0,size=(900,1100),center=(0,12),halfheight=14,staff_only=False):
        key=(size)
        if key not in cache: cache[key]=ctx.simple_framebuffer(size)
        fbo=cache[key];fbo.use()
        vbo.write(deform(frames[frame],frame).tobytes())
        rotate=np.eye(4);rotate[:3,:3]=R.from_euler('y',angle,degrees=True).as_matrix()
        view=np.diag([-1.,1.,-1.,1.]);view[0,3]=-center[0];view[1,3]=-center[1];view[2,3]=-60
        proj=np.diag([1/(halfheight*size[0]/size[1]),1/halfheight,-2/150,1.]);proj[2,3]=-1
        prog['mvp'].write((proj@view@rotate).astype('f4').T.tobytes())
        fbo.clear(.075,.084,.11,1)
        start=0
        for mi,mat in enumerate(m.materials):
            (textures[mat.texture_index] if mat.texture_index>=0 else white).use()
            c=mat.diffuse_color;prog['tint'].value=(c.r,c.g,c.b)
            s=mat.specular_color;prog['specular'].value=(s.r,s.g,s.b)
            prog['overlay_alpha'].value=mat.alpha if mi in [9,10] else -1.
            prog['glow'].value=1. if mat.name in ['Void bright','Void rune'] else 0.
            if staff_only:
                first,count=staff_groups[mi]
                if count:staff_vao.render(moderngl.TRIANGLES,vertices=count,first=first)
            elif inspect_skirt:
                first,count=review_groups[mi]
                if count:review_vao.render(moderngl.TRIANGLES,vertices=count,first=first)
            else:vao.render(moderngl.TRIANGLES,vertices=mat.vertex_count,first=start)
            start+=mat.vertex_count
        if inspect_skirt and skirt_indices:
            white.use();prog['tint'].value=(1.,.35,.035);prog['glow'].value=1.;prog['overlay_alpha'].value=-1.
            ctx.depth_func='<=';skirt_vao.render(moderngl.TRIANGLES);ctx.depth_func='<'
        return Image.frombytes('RGB',size,fbo.read(components=3)).transpose(Image.Transpose.FLIP_TOP_BOTTOM)
    fontpath='C:/Windows/Fonts/msyh.ttc'
    font=ImageFont.truetype(fontpath,22);small=ImageFont.truetype(fontpath,16)
    if inspect_skirt:
        sheet=Image.new('RGB',(1680,1040),(19,21,28));draw=ImageDraw.Draw(sheet)
        title='裙下部件确认 · 橙色为疑似固定长摆 · 背环已隐藏' if skirt_indices else '已删除确认的两侧长摆 · 背环已隐藏'
        draw.text((24,14),title,font=font,fill='white')
        for i,(angle,label) in enumerate([(0,'正面'),(-135,'侧后'),(180,'背面')]):
            sheet.paste(render(angle,0,(560,900),center=(0,11),halfheight=13),(i*560,65))
            draw.text((i*560+20,975),label,font=font,fill=(230,205,180))
        output=OUT.parent/('v2/skirt-parts-confirmation.png' if skirt_indices else 'v2/skirt-parts-removed.png');output.parent.mkdir(parents=True,exist_ok=True)
        sheet.save(output);print(output,flush=True);return
    if '--beauty' in sys.argv:
        frame=min(60,len(frames)-1)
        # Supersampled renders of the final weighted mesh, including current face curves.
        hero=render(-18,frame,size=(3072,3840),center=(0,12),halfheight=14)
        hero.resize((2048,2560),Image.Resampling.LANCZOS).save(asset_dir/'model-render-hd.png')
        sheet=Image.new('RGB',(3072,1440),(19,21,28));draw=ImageDraw.Draw(sheet)
        title=ImageFont.truetype(fontpath,34)
        draw.text((32,20),'域外人 · 虚空 BOSS / 当前模型渲染',font=title,fill=(232,220,246))
        for i,(angle,label) in enumerate([(0,'正面'),(-55,'侧面 · 法杖'),(180,'背面 · 三层符文环')]):
            im=render(angle,frame,size=(1536,1920),center=(0,12),halfheight=16)
            sheet.paste(im.resize((1024,1280),Image.Resampling.LANCZOS),(i*1024,80))
            draw.text((i*1024+30,1375),label,font=title,fill=(211,194,234))
        sheet.save(asset_dir/'model-render-views.png')
        print('Rendered HD model and three views',asset_dir,flush=True)
        return
    if combat:
        import json
        closeup='face' if '--face' in sys.argv else 'hands' if '--hands' in sys.argv else None
        suffix='-'+closeup if closeup else ''
        profile='--profile' in sys.argv
        if profile:suffix+='-profile'
        def review_render(angle,frame,size):
            if not closeup:return render(angle,frame,size,center=(0,15) if combat=='annihilation' else (0,12),halfheight=19 if combat=='annihilation' else 15)
            from combat_review import worlds
            _,wp=worlds(m,frames[frame])
            if closeup=='face':center=wp[11]+[0,.7,0];zoom=1.7
            else:center=(wp[35]+wp[65])/2;zoom=6.5
            # Project the camera center with the same model rotation as the mesh.
            center=R.from_euler('y',angle,degrees=True).apply(center)
            return render(angle,frame,size,center=(-center[0],center[1]),halfheight=zoom)
        meta_path=asset_dir/(combat+'.events.json')
        meta=json.loads(meta_path.read_text(encoding='utf-8')) if meta_path.exists() else {'label':combat,'keys':np.linspace(0,(len(frames)-1)/60,8).tolist()}
        if closeup=='face' and combat=='annihilation':meta['keys']=[0,1.4,2.1,2.52,3.4,4.2,6.5,8]
        sheet=Image.new('RGB',(1680,1160),(19,21,28));d=ImageDraw.Draw(sheet)
        d.text((20,12),meta['label']+' · 动作审核 / 实际 PMX 蒙皮',font=font,fill='white')
        for j,t in enumerate(meta['keys']):
            im=review_render(90 if profile else 0 if closeup=='face' else 30,min(round(t*60),len(frames)-1),(420,510))
            x=(j%4)*420;y=50+(j//4)*550;sheet.paste(im,(x,y))
            d.text((x+12,y+513),f'{t:.2f} s',font=small,fill=(220,200,240))
        sheet.save(asset_dir/(combat+suffix+'-poses.png'))
        if '--stills' in sys.argv:return
        movie=[]
        for frame in np.round(np.arange(0,len(frames)-1,2.5)).astype(int):
            im=Image.new('RGB',(840,700),(19,21,28));draw=ImageDraw.Draw(im)
            im.paste(review_render(0,frame,(420,650)),(0,40))
            im.paste(review_render(35 if closeup else 55,frame,(420,650)),(420,40))
            draw.text((12,8),meta['label']+f'  {frame/60:.2f} s  · 正面 / 侧面',font=font,fill=(230,217,245))
            movie.append(im)
        durations=([42,41,42]*((len(movie)+2)//3))[:len(movie)]
        movie[0].save(asset_dir/(combat+suffix+'.webp'),save_all=True,append_images=movie[1:],duration=durations,loop=0,quality=84)
        print('Rendered combat',combat,flush=True)
        return
    hero=render(-15,size=(1260,1540));hero.save(OUT/'review_hero.png')
    sheet=Image.new('RGB',(1920,960),(19,21,28));d=ImageDraw.Draw(sheet)
    d.text((24,14),'域外人 · 虚空摄政者 / 外观审核 v1',font=font,fill=(225,214,243))
    for j,(a,label) in enumerate([(0,'正面 / 三层法阵'),(-38,'斜侧 / 法杖与肩甲'),(180,'背面 / 法阵层次')]):
        sheet.paste(render(a,size=(640,820)),(j*640,60));d.text((j*640+24,896),label,font=font,fill=(209,192,232))
    d.text((24,935),'实际 PMX 渲染 · 待审核 · 原模型：元空Ame / miHoYo · 本机使用',font=small,fill=(148,141,160))
    sheet.save(OUT/'review_views.png')
    render(0,size=(900,900),center=(0,19),halfheight=4.5).save(OUT/'review_face.png')
    render(0,size=(1200,800),center=(0,18.1),halfheight=1.4).save(OUT/'review_eyes.png')
    render(0,size=(1000,1200),center=(2.77,20.5),halfheight=3.4,staff_only=True).save(OUT/'review_staff_crown.png')
    render(0,size=(600,1500),center=(2.77,12),halfheight=12.5,staff_only=True).save(OUT/'review_staff_full.png')
    render(0,size=(900,1000),center=(2.77,10.3),halfheight=2.,staff_only=True).save(OUT/'review_staff_grip.png')
    if '--stills' in sys.argv:
        print('Rendered stills',OUT,flush=True)
        return
    movie=[]
    for f in np.round(np.arange(0,720,1.25)).astype(int):
        movie.append(render(-15,f,size=(640,780)))
        if f%120==0:print('animation frame',f,flush=True)
    movie[0].save(OUT/'review_halo_motion.webp',save_all=True,append_images=movie[1:],duration=[42,41,42]*192,loop=0,quality=82)
    print('Rendered',OUT,flush=True)


if __name__=='__main__':main()
