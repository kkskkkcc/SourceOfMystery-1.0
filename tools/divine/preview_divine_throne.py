from pathlib import Path
import json,numpy as np
from PIL import Image
ROOT=Path(__file__).resolve().parents[2]
source=ROOT/'art/divine_cinematic/throne'
"""Static CPU model preview from the shipped geo/UV assets; does not launch Minecraft."""
from pathlib import Path
import json,math,hashlib,zipfile
import numpy as np
from PIL import Image,ImageFilter
from scipy.spatial.transform import Rotation

SOURCE=ROOT/'art/divine_blessing_armor'
A=ROOT/'src/main/resources/assets/sourceofmystery'
PARTS=json.loads((SOURCE/'parts.json').read_text(encoding='utf8'))
BASE=np.asarray(Image.open(A/'textures/armor/divine_blessing.png').convert('RGBA')).astype(float)/255
WHITE=np.asarray(Image.open(A/'textures/armor/divine_blessing_white_flow.png').convert('RGBA')).astype(float)/255
BLACK=np.asarray(Image.open(A/'textures/armor/divine_blessing_black_flow.png').convert('RGBA')).astype(float)/255

def normalize(v):return v/np.linalg.norm(v)
def sample(tex,uv,offset=(0,0)):
    # Flow sampling is seamless; the albedo remains inside each authored material tile.
    h,w=tex.shape[:2];xy=(uv+np.array(offset)*512)*np.array([w/1024,h/1024])
    x=np.floor(xy[...,0]).astype(int)%w;y=np.floor(xy[...,1]).astype(int)%h
    return tex[y,x]

def envelope(t,start,duration):
    u=(t-start)/duration
    return math.sin(u*math.pi)**2 if 0<u<1 else 0

def render(name,azimuth=23,elevation=10,flow=None,checker=False,stride=False,parts_override=None,shoulder_view=False):
    W=1800;H=2000;scale=20
    if shoulder_view:W=2000;H=1400;scale=95
    az=math.radians(azimuth);el=math.radians(elevation)
    back=np.array([math.sin(az)*math.cos(el),math.sin(el),-math.cos(az)*math.cos(el)])
    right=normalize(np.cross([0,1,0],back));up=np.cross(back,right)
    center=np.array([0,38,-2]);light=normalize(np.array([-1,2,-2]))
    if shoulder_view:center=np.array([0,22,0])
    image=np.zeros((H,W,3),float);image[:]=[.82,.84,.87]
    yy=np.linspace(0,1,H)[:,None,None];image[:]*=.94+.06*(1-yy)
    depth=np.full((H,W),-np.inf)
    # Source-space corners and outward normals; same dimensions and UV spans as runtime.
    sides={'north':([4,5,1,0],[0,0,-1]),'south':([7,6,2,3],[0,0,1]),
        'west':([7,4,0,3],[-1,0,0]),'east':([5,6,2,1],[1,0,0]),
        'up':([7,6,5,4],[0,1,0]),'down':([0,1,2,3],[0,-1,0])}
    for part in (PARTS if parts_override is None else parts_override):
        c=part['cube'];o=np.array(c['origin']);s=np.array(c['size'])
        corners=o+np.array([[0,0,0],[1,0,0],[1,0,1],[0,0,1],[0,1,0],[1,1,0],[1,1,1],[0,1,1]])*s
        r=c.get('rotation',[0,0,0]);rot=Rotation.from_euler('xyz',[-r[0],r[1],-r[2]],degrees=True).as_matrix()
        pivot=np.array(c.get('pivot',[0,0,0]));corners=(corners-pivot)@rot.T+pivot
        if stride:
            rig={'armorRightLeg':([-2,12,0],-25),'armorLeftLeg':([2,12,0],25),
                 'armorRightArm':([-5,22,0],16),'armorLeftArm':([5,22,0],-16)}
            if part['bone'] in rig:
                joint,angle=rig[part['bone']];joint=np.array(joint)
                posed=Rotation.from_euler('x',angle,degrees=True).as_matrix()
                corners=(corners-joint)@posed.T+joint;rot=posed@rot
        for face,uvdef in c['uv'].items():
            ids,normal=sides[face];normal=rot@normal
            if np.dot(normal,back)<=0:continue
            verts=corners[ids]-center
            screen=np.column_stack((verts@right*scale+W/2,-(verts@up)*scale+H/2,verts@back))
            u,v=uvdef['uv'];w,h=uvdef['uv_size'];uv=np.array([[u,v],[u+w,v],[u+w,v+h],[u,v+h]])
            for indices in ((0,1,2),(0,2,3)):
                p=screen[list(indices)];tuv=uv[list(indices)]
                lo=np.maximum(np.floor(p[:,:2].min(0)).astype(int),[0,0]);hi=np.minimum(np.ceil(p[:,:2].max(0)).astype(int),[W-1,H-1])
                if np.any(hi<lo):continue
                x,y=np.meshgrid(np.arange(lo[0],hi[0]+1)+.5,np.arange(lo[1],hi[1]+1)+.5)
                den=(p[1,1]-p[2,1])*(p[0,0]-p[2,0])+(p[2,0]-p[1,0])*(p[0,1]-p[2,1])
                if abs(den)<1e-8:continue
                a=((p[1,1]-p[2,1])*(x-p[2,0])+(p[2,0]-p[1,0])*(y-p[2,1]))/den
                b=((p[2,1]-p[0,1])*(x-p[2,0])+(p[0,0]-p[2,0])*(y-p[2,1]))/den;cc=1-a-b
                bary=np.stack((a,b,cc),axis=-1);z=bary@p[:,2];local_uv=bary@tuv
                ys=slice(lo[1],hi[1]+1);xs=slice(lo[0],hi[0]+1)
                mask=(a>=-1e-7)&(b>=-1e-7)&(cc>=-1e-7)&(z>depth[ys,xs])
                if not mask.any():continue
                tex=sample(BASE,local_uv);mask&=tex[:,:,3]>.1
                brightness=.43+.57*max(float(np.dot(normal,light)),0)
                color=tex[:,:,:3]*brightness
                if 'debug_color' in part:
                    color=np.broadcast_to(np.array(part['debug_color'])*brightness,color.shape)
                elif checker:
                    check=((np.floor(local_uv[:,:,0]/8)+np.floor(local_uv[:,:,1]/8))%2)[:,:,None]
                    color=(np.array([.75,.75,.75])*check+np.array([.22,.27,.35])*(1-check))*brightness
                elif flow is not None and part['name']!='taiji_face':
                    black=sample(BLACK,local_uv,(flow*.085,-flow*.12));alpha=black[:,:,3:]*envelope(flow,0,2.7)*.72
                    color=color*(1-alpha)+black[:,:,:3]*alpha
                    white=sample(WHITE,local_uv,(-flow*.065,flow*.10));alpha=white[:,:,3:]*envelope(flow,.35,2.65)*.78
                    color=np.clip(color+white[:,:,:3]*alpha,0,1)
                image[ys,xs][mask]=color[mask];depth[ys,xs][mask]=z[mask]
    # Subtle ambient contact shadow, behind the exact rendered silhouette.
    silhouette=np.isfinite(depth)
    blurred=np.asarray(Image.fromarray((silhouette*80).astype('uint8')).filter(ImageFilter.GaussianBlur(14))).astype(float)/255
    image[~silhouette]*=(1-blurred[~silhouette,None]*.35)
    target=(1200,840) if shoulder_view else (1200,1200)
    out=Image.fromarray(np.clip(image*255,0,255).astype('uint8')).resize(target,Image.Resampling.LANCZOS)
    out.save(SOURCE/name)

SOURCE=ROOT/'art/divine_cinematic/throne'
PARTS=json.loads((SOURCE/'parts.json').read_text(encoding='utf-8'))
BASE=np.asarray(Image.open(SOURCE/'throne.png').convert('RGBA')).astype(float)/255
render('preview_throne.png',202,10)
render('preview_throne_back.png',22,10)
render('preview_throne_side.png',100,10)
