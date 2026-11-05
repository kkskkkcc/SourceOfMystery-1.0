"""Legacy star authoring helpers; default entry point generates the current outer glyph proposal.

Runtime uses the project's existing GeoQuad mesh-adapter approach; .bbmodel sources are
Generic Model meshes. No rejected equipment drafts or existing armor assets are touched.
"""
from pathlib import Path
import base64, json, math, struct, uuid
import numpy as np
from PIL import Image

ROOT=Path(__file__).resolve().parents[2]
A=ROOT/'src/main/resources/assets/sourceofmystery'
ART=ROOT/'art/divine_satellites';ART.mkdir(parents=True,exist_ok=True)
COLORS={'wood':(44,201,131),'metal':(237,208,125),'water':(43,139,246),'fire':(244,65,51),'earth':(229,143,52)}

def write(p,data):
 p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf8')

def sphere():
 triangles=[];segments=32;rings=16
 def vertex(y,x):
  latitude=y/rings*math.pi;longitude=x/segments*math.tau
  n=np.array([math.sin(latitude)*math.cos(longitude),math.cos(latitude),math.sin(latitude)*math.sin(longitude)])
  return (*list(n*.155),*list(n),x/segments,y/rings)
 for y in range(rings):
  for x in range(segments):
   a,b,c,d=vertex(y,x),vertex(y+1,x),vertex(y+1,x+1),vertex(y,x+1)
   if y>0:triangles.append([a,d,b])
   if y<rings-1:triangles.append([d,c,b])
 return triangles

def star():
 triangles=[]
 for axis in range(2):
  outline=[]
  for i in range(16):
   angle=math.tau*i/16
   radius=.19 if i%4==0 else .083 if i%2==0 else .033
   p=np.array([math.cos(angle)*radius*.75,math.sin(angle)*radius,0.0])
   if axis:p=np.array([p[2],p[1],p[0]])
   outline.append(p)
  for side in [-1,1]:
   peak=np.array([0,0,side*.065]) if not axis else np.array([side*.065,0,0])
   for i in range(16):
    ps=[peak,outline[i],outline[(i+1)%16]]
    n=np.cross(ps[1]-ps[0],ps[2]-ps[0]);n/=np.linalg.norm(n)
    outward=np.array([0,0,side]) if not axis else np.array([side,0,0])
    if np.dot(n,outward)<0:ps=[ps[0],ps[2],ps[1]];n=-n
    face=[]
    for p in ps:
     u=.5+(p[0] if not axis else p[2])/.38;v=.5-p[1]/.38
     face.append((*p,*n,u,v))
    triangles.append(face)
 return triangles

def texture(name):
 w,h=(512,256) if name in COLORS else (256,256)
 y,x=np.mgrid[:h,:w];u=x/w;v=y/h;lon=u*math.tau;lat=v*math.pi
 if name in COLORS:
  # All longitude functions have integer period, with pole fading, so the sphere seam closes.
  color=np.array(COLORS[name],float)
  latitude=np.sin(lat)
  light=.47+.38*(.5+.5*np.cos(lon-.7))*latitude+.12*np.cos(lat-.7)
  grain=(np.sin(lon*71+np.cos(lat*23))*np.sin(lat*63))*.015
  rgb=color[None,None,:]*(light+grain)[...,None]
  highlight=np.exp(-((np.arctan2(np.sin(lon-.8),np.cos(lon-.8))/.32)**2+((v-.3)/.16)**2))
  rgb+=highlight[...,None]*np.array([150,148,131])
  if name=='wood':
   field=np.sin(lon*5+3.1*np.sin(lat*4))+.34*np.sin(lon*11-lat*7)
   veins=np.exp(-(field/.1)**2)*latitude**2
   leaves=(np.maximum(0,np.cos(lon*7+lat*15))**18)*np.maximum(0,np.sin(lat*9))**8*latitude
   rgb+=veins[...,None]*np.array([57,115,71])+leaves[...,None]*np.array([81,77,18])
  elif name=='metal':
   arcs=np.exp(-(np.sin(lon*6+np.sin(lat*4))/.045)**2)*latitude**2
   band=np.exp(-((v-.5)/.045)**2)
   rgb=rgb*(1-.35*band[...,None])+arcs[...,None]*np.array([30,36,33])+band[...,None]*np.array([64,61,38])
   rgb+=np.exp(-((v-.5)/.007)**2)[...,None]*np.array([92,92,77])
  elif name=='water':
   wave=np.sin(lat*15+np.sin(lon*4)*1.7)
   crest=np.exp(-(wave/.16)**2)*latitude
   rgb+=crest[...,None]*np.array([44,106,112])
   rgb+=((.5+.5*np.sin(lon*7-lat*19))**10*latitude)[...,None]*np.array([13,32,43])
  elif name=='fire':
   flame=np.sin(lon*7+np.sin(lat*5)*2+v*10)
   fissure=np.exp(-(flame/.2)**2)*latitude
   rgb*=.63
   rgb+=fissure[...,None]*np.array([147,111,25])
   rgb+=(np.maximum(0,np.cos(lon*11+lat*23))**16*latitude)[...,None]*np.array([57,21,4])
  else:
   rock=.5+.5*np.sin(lon*9+np.sin(lat*7)*2)*np.sin(lat*17+np.cos(lon*5))
   cracks=np.exp(-((rock-.47)/.025)**2)*latitude**2
   strata=.5+.5*np.cos(lat*25+np.sin(lon*3)*1.3)
   rgb=rgb*(.7+.3*strata[...,None])+cracks[...,None]*np.array([91,57,18])
  # A small equatorial elemental crest, repeated on both sides, is painted onto the sphere.
  dx=np.arctan2(np.sin(lon),np.cos(lon))/math.tau;dy=v-.5
  seal=np.exp(-((np.sqrt((dx*2)**2+dy**2)-.105)/.0035)**2)
  rgb+=seal[...,None]*np.array([63,66,55])*latitude[...,None]
 else:
  r=np.sqrt(((u-.5)*2)**2+((v-.5)*2)**2)
  if name=='white_star':rgb=np.stack([255-r*15,255-r*9,255-r*3],-1)
  else:
   edge=np.clip((r-.08)/.6,0,1)**2
   rgb=np.stack([9+edge*158,10+edge*166,18+edge*181],-1)
 array=np.dstack([np.uint8(np.clip(rgb,0,255)),np.full((h,w),255,np.uint8)])
 if name not in COLORS:
  array[...,3]=np.uint8(np.clip((1.14-r)/.26,0,1)*255)
 path=A/f'textures/effects/divine_satellites/{name}.png';path.parent.mkdir(parents=True,exist_ok=True);Image.fromarray(array).save(path)
 write(path.with_suffix('.png.mcmeta'),{'texture':{'blur':True,'clamp':False}})
 return path,w,h

def mesh(kind,triangles):
 name='divine_satellite_'+kind
 path=A/f'geo/effects/{name}.mesh';path.parent.mkdir(parents=True,exist_ok=True)
 with path.open('wb') as f:
  f.write(struct.pack('>iii',0x534f4d54,1,len(triangles)*3))
  for tri in triangles:
   for vert in tri:f.write(struct.pack('>8f',*vert))
 write(A/f'geo/effects/{name}.geo.json',{'format_version':'1.12.0','minecraft:geometry':[{'description':{'identifier':'geometry.'+name,'texture_width':512 if kind=='orb' else 256,'texture_height':256,'visible_bounds_width':1,'visible_bounds_height':1,'visible_bounds_offset':[0,0,0]},'bones':[{'name':'root','pivot':[0,0,0],'cubes':[]}]}]})
 # Preserve editable arbitrary mesh topology, UVs and materials in Generic Model format.
 variant='wood' if kind=='orb' else 'white_star';tex,w,h=texture(variant)
 vertices={};faces={};obj=['# Units: meters; +Y up. Authored divine satellite.']
 for i,tri in enumerate(triangles):
  keys=[];uv={}
  for j,v in enumerate(tri):
   key=f'v{i*3+j}';keys.append(key);vertices[key]=[p*16 for p in v[:3]];uv[key]=[v[6]*w,v[7]*h]
   obj.append('v '+' '.join(f'{p:.8f}' for p in v[:3]))
  faces['f'+str(i)]={'vertices':keys,'uv':uv,'texture':0}
 for i,tri in enumerate(triangles):
  for v in tri:obj.append(f'vt {v[6]:.8f} {1-v[7]:.8f}')
 for i in range(len(triangles)):
  n=i*3+1;obj.append(f'f {n}/{n} {n+1}/{n+1} {n+2}/{n+2}')
 (ART/(name+'.obj')).write_text('\n'.join(obj)+'\n',encoding='utf8')
 write(ART/(name+'.bbmodel'),{'meta':{'format_version':'4.10','model_format':'free','box_uv':False},'name':name,'resolution':{'width':w,'height':h},
  'elements':[{'name':name,'type':'mesh','uuid':str(uuid.uuid5(uuid.NAMESPACE_URL,name)),'origin':[0,0,0],'vertices':vertices,'faces':faces}],
  'textures':[{'name':tex.name,'id':'0','uuid':str(uuid.uuid5(uuid.NAMESPACE_URL,name+'/texture')),'source':'data:image/png;base64,'+base64.b64encode(tex.read_bytes()).decode(),'uv_width':w,'uv_height':h}]})
 # Each valid sphere face must point outward and have nonzero area.
 if kind=='orb':
  for t in triangles:
   p=np.array([v[:3] for v in t]);normal=np.cross(p[1]-p[0],p[2]-p[0])
   assert np.dot(normal,p.mean(0))>0, 'Inverted sphere triangle'
 return {'name':name,'triangles':len(triangles),'mesh_bytes':path.stat().st_size}

def main():
 # The user restored the outer ring to its original particles. Only inner-star assets are active.
 for name in ['white_star','black_star']:texture(name)
 plan={'outer_visual':'original_five_color_particles','inner_mesh':mesh('star',star()),'outer_model_generation':False}
 write(ART/'asset-plan.json',plan);print(json.dumps(plan,indent=2))
if __name__=='__main__':main()
