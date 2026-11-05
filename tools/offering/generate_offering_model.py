"""Original GeckoLib display relic: reproducible geometry and material atlas.
Appearance-first desktop Forge prop; static rigid geometry, one 512 atlas + mask.
"""
from pathlib import Path
import json,math,random
import numpy as np
from PIL import Image,ImageDraw,ImageFilter
from scipy.spatial.transform import Rotation
ROOT=Path(__file__).resolve().parents[2]
A=ROOT/'src/main/resources/assets/sourceofmystery'
D=ROOT/'src/main/resources/data/sourceofmystery'
SOURCE=ROOT/'art/dark_dimensional_offering';SOURCE.mkdir(parents=True,exist_ok=True)
SIZE=512;DENSITY=8
bones=[{'name':'reliquary','pivot':[0,0,0]}];cube_count=0;uv_checks=[]
crystal_faces=[]

def write(path,data):
 path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
def group(name,rotation=None,pivot=(0,9.3,0)):
 b={'name':name,'parent':'reliquary','pivot':list(pivot),'cubes':[]}
 if rotation:b['rotation']=[-rotation[0],rotation[1],-rotation[2]]
 bones.append(b);return b

def cube(b,origin,size,material=0,rotation=None,pivot=None,hide=()):
 global cube_count
 assert min(size)>0
 dims={'north':(size[0],size[1]),'south':(size[0],size[1]),'east':(size[2],size[1]),'west':(size[2],size[1]),'up':(size[0],size[2]),'down':(size[0],size[2])}
 tx=(material%4)*128;ty=(material//4)*128;uv={}
 for fi,(face,(w,h)) in enumerate(dims.items()):
  if face in hide:continue
  w*=DENSITY;h*=DENSITY
  u=tx+5+(fi*11+cube_count*7)%max(1,int(116-w));v=ty+5+(fi*7+cube_count*5)%max(1,int(116-h))
  uv[face]={'uv':[round(u,5),round(v,5)],'uv_size':[round(w,5),round(h,5)]};uv_checks.append((w/max(dims[face][0],1e-6),h/max(dims[face][1],1e-6)))
 c={'origin':[round(x,5) for x in origin],'size':[round(x,5) for x in size],'uv':uv}
 if rotation:c.update(rotation=[round(-rotation[0],5),round(rotation[1],5),round(-rotation[2],5)],pivot=[float(x) for x in (pivot if pivot is not None else np.array(origin)+np.array(size)/2)])
 b['cubes'].append(c);cube_count+=1

def box(b,center,size,mat=0,rotation=None,hide=()):cube(b,np.array(center)-np.array(size)/2,size,mat,rotation,center,hide)
def rod(b,a,z,width,mat=1):
 a=np.array(a);z=np.array(z);d=z-a;l=float(np.linalg.norm(d));mid=(a+z)/2
 tilt=math.degrees(math.acos(float(d[1])/l));heading=math.degrees(math.atan2(float(d[0]),float(d[2])))
 box(b,mid,[width,l,width],mat,[tilt,heading,0])
def ring(b,r,y,thickness,depth,mat=1,n=40,vertical=False):
 for i in range(n):
  t=2*math.pi*(i+.5)/n;span=2*r*math.tan(math.pi/n)*1.015
  if vertical:box(b,[r*math.cos(t),y+r*math.sin(t),0],[span,thickness,depth],mat,[0,0,math.degrees(t)+90])
  else:box(b,[r*math.cos(t),y,r*math.sin(t)],[span,depth,thickness],mat,[0,-math.degrees(t)+90,0])

base=group('obsidian_plinth',pivot=(0,0,0))
box(base,[0,.35,0],[11.5,.7,11.5],0)
box(base,[0,.83,0],[10.7,.3,10.7],1)
box(base,[0,1.18,0],[10.2,.4,10.2],0)
box(base,[0,1.48,0],[8.2,.22,8.2],2,[0,45,0])
box(base,[0,1.8,0],[8.5,.42,8.5],0)
ring(base,4.05,2.10,.26,.12,2,32)
ring(base,3.55,2.16,.16,.12,4,32)
# Engraved radial inlays and eight genuinely open support prongs.
for i in range(8):
 a=i*math.pi/4
 box(base,[3.1*math.cos(a),2.15,3.1*math.sin(a)],[.16,.11,1.05],1,[0,90-math.degrees(a),0])
 x,z=4.7*math.cos(a),4.7*math.sin(a)
 box(base,[x,.28,z],[1.05,.55,1.05],1,[0,math.degrees(a),0])
 claws=group('talon_'+str(i),pivot=(0,0,0))
 points=[]
 for k in range(6):
  t=k/5;r=3.4+1.35*math.sin(t*math.pi)-1.9*t*t;y=2.3+4.25*t
  points.append([r*math.cos(a),y,r*math.sin(a)])
 for p,q in zip(points,points[1:]):rod(claws,p,q,.29,1)
 box(claws,points[-1],[.42,.60,.42],2,[0,0,30])

for number,(rot,rad,mat) in enumerate([([0,28,-12],5.15,1),([18,-54,25],4.75,2)]):
 b=group('armillary_'+str(number),rot)
 ring(b,rad,9.3,.24,.30,mat,48,True)
 ring(b,rad-.23,9.3,.065,.16,4 if number==0 else 5,48,True)
 for k in range(8):
  t=k*math.pi/4
  box(b,[rad*math.cos(t),9.3+rad*math.sin(t),0],[.55,.30,.48],0,[0,0,math.degrees(t)+90])
  box(b,[rad*math.cos(t),9.3+rad*math.sin(t),-.255],[.12,.19,.05],4 if number==0 else 5,[0,0,math.degrees(t)])
 # Short etched crossbars are geometry only where they cross the open rim.
 for k in range(12):
  t=k*math.pi/6+.12
  box(b,[rad*math.cos(t),9.3+rad*math.sin(t),0],[.12,.58,.32],mat,[0,0,math.degrees(t)+90])

core=group('void_crystal',pivot=(0,9.3,0))
# A closed octagonal bipyramid. Each real planar facet uses a tight alpha
# triangle on a single Gecko quad, retaining depth and a clean pointed outline
# without a stack of visible slice ledges. Two-sided cutout avoids blend sorting.
for half,apex_y in enumerate((12.2,6.5)):
 for i in range(8):
  angle=i*math.pi/4+.13
  a=np.array([1.8*math.cos(angle),9.35,1.8*math.sin(angle)])
  b=np.array([1.8*math.cos(angle+math.pi/4),9.35,1.8*math.sin(angle+math.pi/4)])
  mid=(a+b)/2;apex=np.array([0,apex_y,0]);right=(b-a)/np.linalg.norm(b-a);up=(apex-mid)/np.linalg.norm(apex-mid)
  normal=np.cross(right,up);rotation=Rotation.from_matrix(np.column_stack((right,up,normal))).as_euler('xyz',degrees=True)
  width=float(np.linalg.norm(b-a));height=float(np.linalg.norm(apex-mid));center=mid+up*height/2
  box(core,center,[width,height,.004],3,list(rotation),hide=('south','east','west','up','down'))
  index=half*8+i;u=(index%4)*128+8;v=256+(index//4)*64+4
  w=width*16;h=height*16;core['cubes'][-1]['uv']={'north':{'uv':[u,v],'uv_size':[w,h]}}
  crystal_faces.append((u,v,w,h,index))
for i in range(4):
 a=i*math.pi/2+math.pi/4
 rod(core,[1.05*math.cos(a),7.8,1.05*math.sin(a)],[.5*math.cos(a),10.65,.5*math.sin(a)],.065,5)
# Small counter-crystals give a readable separation from the mounting cradle.
for i in range(4):
 a=i*math.pi/2
 b=group('shard_'+str(i),pivot=(0,9.3,0))
 box(b,[2.6*math.cos(a),9.1+(i%2)*.7,2.6*math.sin(a)],[.52,1.05,.52],3,[12,math.degrees(a),35])

finial=group('finial',pivot=(0,0,0))
rod(finial,[0,13.75,0],[0,15.1,0],.32,1)
box(finial,[0,15.15,0],[.65,.65,.65],2,[0,45,45])
rod(finial,[-.75,14.9,0],[0,15.8,0],.14,1)
rod(finial,[.75,14.9,0],[0,15.8,0],.14,1)

# Original shared materials at a fixed eight atlas texels per model unit.
image=np.zeros((SIZE,SIZE,4),dtype=np.uint8);glow=np.zeros_like(image)
colors=[(24,20,35),(112,105,133),(149,112,70),(93,49,173),(75,200,248),(160,76,250),(15,13,23),(49,36,69)]
rng=np.random.default_rng(44911)
for m,col in enumerate(colors):
 x=(m%4)*128;y=(m//4)*128;yy,xx=np.mgrid[:128,:128]
 noise=rng.normal(0,1.3,(128,128));fine=np.sin(xx*.8+yy*.13)*1.4
 factor=.83+.14*np.sin((xx+yy)*.019)+.06*np.cos(xx*.05)
 if m==3:factor=.75+.35*(1-xx/128)+.13*np.sin(xx*.11+yy*.035)
 rgb=np.clip(np.array(col)[None,None,:]*factor[:,:,None]+(noise+fine)[:,:,None],0,255)
 image[y:y+128,x:x+128,:3]=rgb;image[y:y+128,x:x+128,3]=255
 if m in (3,4,5):glow[y:y+128,x:x+128]=image[y:y+128,x:x+128]
# Fine runic engraving in the obsidian material, distributed to read at any UV crop.
im=Image.fromarray(image);draw=ImageDraw.Draw(im)
for cy in (18,48,78,108):
 for cx in (16,46,76,106):
  draw.line([(cx-4,cy+5),(cx-4,cy-5),(cx+3,cy-1),(cx-4,cy+2)],fill=(68,52,96,255),width=1)
for i in range(8):
 x=128+(i*17)%120;draw.line((x,4,x+4,121),fill=(140,133,159,255),width=1)
for u,v,w,h,index in crystal_faces:
 # Transparent borders stay transparent in both albedo and emissive layers.
 x0,y0=int(u),int(v);x1,y1=math.ceil(u+w),math.ceil(v+h)
 mask=Image.new('L',(x1-x0,y1-y0),0);md=ImageDraw.Draw(mask)
 md.polygon([(w/2,0),(0,h),(w,h)],fill=255)
 patch=Image.new('RGBA',mask.size);px=patch.load()
 for yy in range(patch.height):
  for xx in range(patch.width):
   if mask.getpixel((xx,yy)):
    brightness=.72+.21*math.sin(index*1.7)+.34*(1-xx/max(1,w))+.1*math.sin(yy*.10+index)
    px[xx,yy]=(int(98*brightness),int(56*brightness),int(185*brightness),255)
 pd=ImageDraw.Draw(patch);pd.line([(w/2,0),(1,h-1)],fill=(158,113,239,255),width=1)
 patch.putalpha(mask);im.alpha_composite(patch,(x0,y0))
 glow_patch=np.array(patch);glow_patch[:,:,:3]=(glow_patch[:,:,:3]*.50).astype('uint8')
 glow[y0:y1,x0:x1]=glow_patch
texture=A/'textures/block/dark_dimensional_offering.png';texture.parent.mkdir(parents=True,exist_ok=True);im.save(texture)
Image.fromarray(glow).save(texture.with_name('dark_dimensional_offering_glowmask.png'))
im.crop((0,0,128,128)).resize((32,32)).save(texture.with_name('dark_dimensional_offering_particle.png'))
geo={'format_version':'1.12.0','minecraft:geometry':[{'description':{'identifier':'geometry.dark_dimensional_offering','texture_width':SIZE,'texture_height':SIZE,'visible_bounds_width':2,'visible_bounds_height':2,'visible_bounds_offset':[0,.5,0]},'bones':bones}]}
write(A/'geo/block/dark_dimensional_offering.geo.json',geo)
write(SOURCE/'dark_dimensional_offering.geo.json',geo)
write(A/'animations/block/dark_dimensional_offering.animation.json',{'format_version':'1.8.0','animations':{}})
write(A/'blockstates/dark_dimensional_offering.json',{'variants':{f'facing={d}':{'model':'sourceofmystery:block/dark_dimensional_offering'} for d in ('north','south','east','west')}})
write(A/'models/block/dark_dimensional_offering.json',{'textures':{'particle':'sourceofmystery:block/dark_dimensional_offering_particle'}})
write(A/'models/item/dark_dimensional_offering.json',{'parent':'builtin/entity','gui_light':'side','display':{
 'gui':{'rotation':[25,225,0],'translation':[0,-.5,0],'scale':[.82]*3},
 'ground':{'rotation':[0,0,0],'translation':[0,2,0],'scale':[.4]*3},
 'fixed':{'rotation':[0,0,0],'translation':[0,0,-1],'scale':[.7]*3},
 'thirdperson_righthand':{'rotation':[0,0,0],'translation':[0,1,0],'scale':[.52]*3},
 'thirdperson_lefthand':{'rotation':[0,0,0],'translation':[0,1,0],'scale':[.52]*3},
 'firstperson_righthand':{'rotation':[0,25,0],'translation':[1,1,-1],'scale':[.55]*3},
 'firstperson_lefthand':{'rotation':[0,25,0],'translation':[1,1,-1],'scale':[.55]*3}}})
write(D/'loot_tables/blocks/dark_dimensional_offering.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'sourceofmystery:dark_dimensional_offering'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
for lang in ('zh_cn','en_us'):
 path=A/'lang'/f'{lang}.json';data=json.loads(path.read_text(encoding='utf8'))
 data['block.sourceofmystery.dark_dimensional_offering']='黑暗次元祭品' if lang=='zh_cn' else 'Dark Dimensional Offering'
 data['tooltip.sourceofmystery.offering.place']='右键放置观赏 · 空手即可挖回' if lang=='zh_cn' else 'Place as a display piece; recover instantly by hand.'
 data['tooltip.sourceofmystery.offering.ritual']='右键未激活的标准黑曜石门召唤域外魔导师' if lang=='zh_cn' else 'Use on an unlit standard obsidian frame to summon the archmage.'
 write(path,data)
report={'geometry_preference':'appearance first, explicitly selected by user','target':'Minecraft Java Forge 1.20.1 + GeckoLib 4.8.4, desktop','shading':'opaque cutout albedo plus emissive core/inlays; no PBR dependency','bones':len(bones),'cubes':cube_count,'quad_faces':sum(len(c['uv']) for b in bones for c in b.get('cubes',[])),'texture_size':[SIZE,SIZE],'texels_per_model_unit':DENSITY,'crystal_texels_per_model_unit':16,'crystal_planar_facets':len(crystal_faces),'uv_density_verified':all(abs(x-DENSITY)<1e-6 and abs(y-DENSITY)<1e-6 for x,y in uv_checks),'animation':'static display prop; no server ticker','palette':'obsidian, tarnished silver, antique gold, violet crystal, cyan inlays','source':'original procedural asset; no third-party artwork'}
write(SOURCE/'asset-plan.json',report);write(ROOT/'build/offering-gecko-asset-checks.json',report);print(json.dumps(report,indent=2))

if __name__=='__main__':pass
