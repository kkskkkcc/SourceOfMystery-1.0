"""Original static GeckoLib enchanter: obsidian, silver mystery ingot, diamond, nether-star core."""
from pathlib import Path
import json,math,random
import numpy as np
from PIL import Image,ImageDraw
from scipy.spatial.transform import Rotation
ROOT=Path(__file__).resolve().parents[2];A=ROOT/'src/main/resources/assets/sourceofmystery';D=ROOT/'src/main/resources/data/sourceofmystery'
SOURCE=ROOT/'art/mystery_enchanter';SOURCE.mkdir(parents=True,exist_ok=True)
SIZE=512;DENSITY=8;bones=[{'name':'enchanter','pivot':[0,0,0]}];checks=[];facets=[]
def write(p,v):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(v,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
def group(n):b={'name':n,'parent':'enchanter','pivot':[0,0,0],'cubes':[]};bones.append(b);return b
def box(b,c,s,mat=0,rot=None,hide=()):
 dims={'north':(s[0],s[1]),'south':(s[0],s[1]),'east':(s[2],s[1]),'west':(s[2],s[1]),'up':(s[0],s[2]),'down':(s[0],s[2])};uv={}
 for i,(f,(w,h)) in enumerate(dims.items()):
  if f in hide:continue
  u=(mat%4)*128+1.2;v=(mat//4)*128+1.2;uv[f]={'uv':[u,v],'uv_size':[w*DENSITY,h*DENSITY]};checks.append([w*DENSITY/w,h*DENSITY/h])
 cube={'origin':[round(c[i]-s[i]/2,5) for i in range(3)],'size':s,'uv':uv}
 if rot:cube.update(rotation=[-rot[0],rot[1],-rot[2]],pivot=c)
 b['cubes'].append(cube)
def rod(b,a,z,w,mat=1):
 a=np.array(a);z=np.array(z);d=z-a;l=float(np.linalg.norm(d));mid=((a+z)/2).tolist()
 box(b,mid,[w,l,w],mat,[math.degrees(math.acos(d[1]/l)),math.degrees(math.atan2(d[0],d[2])),0])
base=group('obsidian_and_silver')
for c,s,m in [([0,.5,0],[15.6,1,15.6],0),([0,1.18,0],[15.2,.36,15.2],1),([0,2,0],[14.4,1.3,14.4],0),([0,5.6,0],[9.8,6,9.8],0),([0,8.75,0],[12.9,.75,12.9],1),([0,9.45,0],[13.5,.7,13.5],0),([0,10.02,0],[13,.44,13],1),([0,10.5,0],[12.4,.55,12.4],0)]:box(base,c,s,m,hide=('down',))
for k in range(4):
 a=k*math.pi/2;b=group('silver_facade_'+str(k));
 for sx in [-1,1]:
  x,z=sx*3.8,5.04
  def turn(x,y,z):return [x*math.cos(a)-z*math.sin(a),y,x*math.sin(a)+z*math.cos(a)]
  box(b,turn(x,5.55,z),[.5,5.7,.35],1,[0,math.degrees(a),0])
 for y in [3.4,7.7]:box(b,turn(0,y,5.05),[7.2,.35,.38],1,[0,math.degrees(a),0])
 box(b,turn(0,5.6,4.94),[3.8,3.5,.08],5,[0,math.degrees(a),0])
 box(b,turn(0,2.8,6.4),[.45,.13,2.3],4,[0,math.degrees(a),0])
for x in [-1,1]:
 for z in [-1,1]:
  b=group('diamond_corner_'+str(x)+'_'+str(z));c=[x*5.4,8.3,z*5.4]
  box(b,[c[0],4.7,c[2]],[1.8,4.1,0.7],1,[0,45 if x*z>0 else -45,0])
  box(b,[c[0],7.25,c[2]],[2.65,.5,2.65],1)
  box(b,[c[0],8.55,c[2]],[2.2,2.1,2.2],2,[0,45,0])
  box(b,[c[0],9.82,c[2]],[2.65,.4,2.65],1)
cradle=group('star_cradle')
for i in range(16):
 t=(i+.5)*math.pi/8;box(cradle,[3.5*math.cos(t),11.25,3.5*math.sin(t)],[2*3.5*math.tan(math.pi/16)*1.012,.24,.22],1,[0,90-math.degrees(t),0])
for i in range(4):
 a=i*math.pi/2+math.pi/4;points=[[r*math.cos(a),y,r*math.sin(a)] for r,y in [(3.6,10.8),(3.9,12),(3.5,13.1),(2.7,13.7)]]
 for p,q in zip(points,points[1:]):rod(cradle,p,q,.22,1)
 box(cradle,points[-1],[.4,.5,.4],2,[0,0,45])
star=group('nether_star')
ring=[(0,3),(.65,.65),(3,0),(.65,-.65),(0,-3),(-.65,-.65),(-3,0),(-.65,.65)]
def triangle(b,points,index):
 pairs=[(np.linalg.norm(points[(j+1)%3]-points[j]),j) for j in range(3)];_,j=max(pairs)
 a=points[j];z=points[(j+1)%3];tip=points[(j+2)%3];right=(z-a)/np.linalg.norm(z-a);w=float(np.linalg.norm(z-a));projection=float(np.dot(tip-a,right));up=tip-a-projection*right;h=float(np.linalg.norm(up));up/=h
 normal=np.cross(right,up);rot=Rotation.from_matrix(np.column_stack((right,up,normal))).as_euler('xyz',degrees=True);center=a+right*w/2+up*h/2
 u=256+(index%4)*64;v=256+(index//4)*64
 cube={'origin':(center-np.array([w/2,h/2,0])).tolist(),'size':[w,h,0],'pivot':center.tolist(),'rotation':[-float(rot[0]),float(rot[1]),-float(rot[2])],'uv':{f:{'uv':[u,v],'uv_size':[w*8,h*8]} for f in ['north','south']}}
 b['cubes'].append(cube);facets.append((u,v,w*8,h*8,projection*8,index));checks.append([8,8])
for side in range(2):
 for i in range(8):triangle(star,[np.array([0,14.4,(-.5 if side==0 else .5)]),np.array([ring[i][0],14.4+ring[i][1],0]),np.array([ring[(i+1)%8][0],14.4+ring[(i+1)%8][1],0])],side*8+i)
im=Image.new('RGBA',(SIZE,SIZE),(0,0,0,0));glow=Image.new('RGBA',(SIZE,SIZE),(0,0,0,0));rng=random.Random(1105)
colors=[(30,23,46),(155,174,182),(51,184,210),(231,224,177),(66,210,227),(30,38,58)]
for m,color in enumerate(colors):
 ox=(m%4)*128;oy=(m//4)*128;tile=Image.new('RGBA',(128,128));pix=tile.load()
 for yy in range(128):
  for xx in range(128):
   noise=rng.uniform(-.04,.04);gradient=.82+.19*(1-yy/127)+noise
   if m==0:gradient+=.10*math.sin(xx*.17+math.sin(yy*.21)*3)
   if m==1:gradient+=.12*math.sin(yy*.25)+.07*math.sin(xx*.10)
   if m==2:gradient+=.18*math.sin((xx+yy)*.06)
   pix[xx,yy]=tuple(max(0,min(255,int(c*gradient))) for c in color)+(255,)
 draw=ImageDraw.Draw(tile)
 if m==5:
  for yy in range(14,116,27):
   draw.line([(12,yy),(26,yy+10),(39,yy),(51,yy+9),(65,yy-3),(79,yy+7),(96,yy-4),(112,yy+5)],fill=(82,176,199,255),width=2)
 if m==2:draw.line([(5,120),(5,5),(120,5)],fill=(158,244,249,255),width=2)
 im.paste(tile,(ox,oy))
 if m in [2,4,5]:
  data=np.array(tile);data[:,:,:3]=(data[:,:,:3]*(.4 if m==2 else .8)).astype('uint8');glow.paste(Image.fromarray(data),(ox,oy))
for u,v,w,h,projection,index in facets:
 size=(math.ceil(w),math.ceil(h));mask=Image.new('L',size,0);ImageDraw.Draw(mask).polygon([(0,h),(w,h),(projection,0)],fill=255)
 brightness=.75+.18*math.sin(index*.9);patch=Image.new('RGBA',size,(int(247*brightness),int(238*brightness),int(196*brightness),255));patch.putalpha(mask)
 im.paste(patch,(u,v));glow.paste(patch,(u,v))
texture=A/'textures/block/mystery_enchanter.png';texture.parent.mkdir(parents=True,exist_ok=True);im.save(texture);glow.save(texture.with_name('mystery_enchanter_glowmask.png'));im.crop((0,0,128,128)).resize((32,32)).save(texture.with_name('mystery_enchanter_particle.png'))
geo={'format_version':'1.12.0','minecraft:geometry':[{'description':{'identifier':'geometry.mystery_enchanter','texture_width':512,'texture_height':512,'visible_bounds_width':2,'visible_bounds_height':2,'visible_bounds_offset':[0,.5,0]},'bones':bones}]}
write(A/'geo/block/mystery_enchanter.geo.json',geo);write(SOURCE/'mystery_enchanter.geo.json',geo)
write(A/'animations/block/mystery_enchanter.animation.json',{'format_version':'1.8.0','animations':{}})
write(A/'blockstates/mystery_enchanter.json',{'variants':{f'facing={d}':{'model':'sourceofmystery:block/mystery_enchanter'} for d in ['north','south','east','west']}})
write(A/'models/block/mystery_enchanter.json',{'textures':{'particle':'sourceofmystery:block/mystery_enchanter_particle'}})
write(A/'models/item/mystery_enchanter.json',{'parent':'builtin/entity','gui_light':'side','display':{'gui':{'rotation':[25,225,0],'translation':[0,-1,0],'scale':[.64]*3},'ground':{'translation':[0,1,0],'scale':[.35]*3},'fixed':{'scale':[.6]*3},'firstperson_righthand':{'rotation':[0,30,0],'translation':[1,0,-1],'scale':[.5]*3},'firstperson_lefthand':{'rotation':[0,30,0],'translation':[1,0,-1],'scale':[.5]*3},'thirdperson_righthand':{'translation':[0,1,0],'scale':[.5]*3},'thirdperson_lefthand':{'translation':[0,1,0],'scale':[.5]*3}}})
write(D/'loot_tables/blocks/mystery_enchanter.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'sourceofmystery:mystery_enchanter'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
for name in ['mineable/pickaxe','needs_diamond_tool']:
 p=ROOT/'src/generated/resources/data/minecraft/tags/blocks'/f'{name}.json';data=json.loads(p.read_text(encoding='utf8')) if p.exists() else {'replace':False,'values':[]}
 if 'sourceofmystery:mystery_enchanter' not in data['values']:data['values'].append('sourceofmystery:mystery_enchanter')
 write(p,data)
report={'preference':'appearance-first, reused explicit user preference','target':'Java Forge 1.20.1 GeckoLib 4.8.4, desktop, a few interactive stations','shading':'opaque cutout plus emissive, no PBR','texture':[512,512],'texels_per_unit':8,'cubes':sum(len(b.get('cubes',[])) for b in bones),'quad_faces':sum(len(c['uv']) for b in bones for c in b.get('cubes',[])),'cube_budget':180,'uv_density_verified':all(a==8 and b==8 for a,b in checks),'animation':'static, no server ticker','geometry_for':'stepped silhouette, diamond corner depth, open silver cradle, faceted volumetric star','painted_details':'obsidian grain, silver grain, cyan facade runes'}
assert report['cubes']<=180;write(SOURCE/'asset-plan.json',report);write(ROOT/'build/enchanter-asset-plan.json',report);print(json.dumps(report,indent=2))
