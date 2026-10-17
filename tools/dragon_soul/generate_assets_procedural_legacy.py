"""Build Dragon Soul from the user's 1ba source rig and video reference.
Body, face, legs and authored hair geometry are imported, never procedurally replaced.
"""
import json, math, copy, sys
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw
ROOT=Path(__file__).resolve().parents[2]; SRC=Path(__file__).parent/'source'; AS=ROOT/'src/main/resources/assets/sourceofmystery'
g=json.loads((SRC/'1ba/models/main.json').read_text(encoding='utf-8-sig'));bones=g['minecraft:geometry'][0]['bones'];by={b['name']:b for b in bones}
sys.path.insert(0,str(ROOT/'tools/weeping_death_lord'))
from geo_render import rot_matrix, affine, mirror, bone_world
source_texture=Image.open(SRC/'1ba/textures/default.png').convert('RGBA')
arr=np.asarray(source_texture).copy();r=arr[:,:,0].astype(float);gr=arr[:,:,1].astype(float);b=arr[:,:,2].astype(float)
blue=(b>r*1.25)&(b>gr*1.2)&(b>75)
# Purple eyes and accents: preserve pixel detail and alpha from the authored atlas.
arr[blue,0]=np.minimum(220,b[blue]*.8);arr[blue,1]=np.minimum(150,gr[blue]*.7+35);arr[blue,2]=np.minimum(225,b[blue]*.9)
# Pale pink visible skin, as in the video close-up. Keep highlights and outlines.
skin=(r>170)&(r>gr*1.04)&(gr>b*1.06)&(~blue)
arr[skin,0]=np.minimum(240,r[skin]*.95);arr[skin,1]=np.minimum(220,gr[skin]*.96+10);arr[skin,2]=np.minimum(220,b[skin]*.95+20)
im=Image.new('RGBA',(512,576));im.paste(Image.fromarray(arr),(0,0))
hair=arr.copy();value=hair[:,:,:3].astype(float).mean(axis=2);neutral=(hair[:,:,:3].max(axis=2)-hair[:,:,:3].min(axis=2)<65)&(hair[:,:,3]>0)
hair[neutral,0]=np.minimum(110,value[neutral]*.58);hair[neutral,1]=np.minimum(75,value[neutral]*.35);hair[neutral,2]=np.minimum(102,value[neutral]*.52)
im.paste(Image.fromarray(hair),(256,0))
armor=arr.copy();value=armor[:,:,:3].astype(float).mean(axis=2);neutral=(armor[:,:,:3].max(axis=2)-armor[:,:,:3].min(axis=2)<45)&(value<200)&(armor[:,:,3]>0)
armor[neutral,0]=value[neutral]*.58;armor[neutral,1]=value[neutral]*.55;armor[neutral,2]=value[neutral]*.73
im.paste(Image.fromarray(armor),(0,256))
weapon_texture=armor.copy();weapon_texture[blue,0]=np.minimum(115,b[blue]*.39);weapon_texture[blue,1]=np.minimum(85,gr[blue]*.6+25);weapon_texture[blue,2]=np.minimum(150,b[blue]*.52)
black=(weapon_texture[:,:,:3].max(axis=2)<20)&(weapon_texture[:,:,3]>0);weapon_texture[black,:3]=[24,22,34]
im.paste(Image.fromarray(weapon_texture),(256,256))
# Hair gets its own copied atlas tile so recoloring never alters skin/armor sharing UV pixels.
for bone in bones:
 n=bone['name'];anc=n
 while by[anc].get('parent') and anc!='Hair':anc=by[anc]['parent']
 if anc=='Hair':
  for c in bone.get('cubes',[]):
   if isinstance(c['uv'],list):c['uv'][0]+=256
   else:
    for f in c['uv'].values():f['uv'][0]+=256
# Dedicated armor tile preserves authored texels while adjusting the graphite/purple material.
armor_bones={'RightArm','LeftArm','RightForeArm','LeftForeArm','RightLeg','LeftLeg','RightLowerLeg','LeftLowerLeg','RightFoot','LeftFoot','Skirt','RightSkirt','LeftSkirt','FrontSkirt','BackSkirt','bone26','bone27','bone28','bone38','UpperBody','Breast','Collar'}
for bone in bones:
 if bone['name'] in armor_bones:
  for c in bone.get('cubes',[]):
   if isinstance(c['uv'],list):c['uv'][1]+=256
   else:
    for f in c['uv'].values():f['uv'][1]+=256
for c in by['longspear']['cubes']:
 if isinstance(c['uv'],list):c['uv'][0]+=256;c['uv'][1]+=256
 else:
  for f in c['uv'].values():f['uv'][0]+=256;f['uv'][1]+=256
colors=[(24,22,35),(53,47,67),(141,123,150),(72,39,86),(140,65,139),(198,127,172),(93,79,113)]
d=ImageDraw.Draw(im)
for i,c in enumerate(colors):d.rectangle((i*32,520,i*32+30,550),fill=(*c,255))
def cube(o,s,m=0):return {'origin':list(o),'size':list(s),'uv':{f:{'uv':[m*32+2,522],'uv_size':[20,20]} for f in ['north','south','east','west','up','down']}}
def euler(R):
 beta=math.asin(float(np.clip(-R[2,0],-1,1)))
 if abs(math.cos(beta))>1e-7:alpha=math.atan2(R[2,1],R[2,2]);gamma=math.atan2(R[1,0],R[0,0])
 else:alpha=math.atan2(-R[1,2],R[1,1]);gamma=0
 return [-math.degrees(alpha),-math.degrees(beta),math.degrees(gamma)]
def beam(a,b,w,m=0,depth=None):
 a=np.asarray(a,dtype=float);b=np.asarray(b,dtype=float);v=(b-a)*[-1,1,1];L=np.linalg.norm(v);v/=L;u=np.array([0,1,0]);k=np.cross(u,v);c=np.dot(u,v);K=np.array([[0,-k[2],k[1]],[k[2],0,-k[0]],[-k[1],k[0],0]]);R=np.eye(3)+K+K@K/(1+c) if c>-.9999 else np.diag([1,-1,-1]);dep=depth or w
 q=cube([a[0]-w/2,a[1],a[2]-dep/2],[w,L,dep],m);q.update(pivot=a.tolist(),rotation=euler(R));return q
def add(name,parent,pivot,cubes):
 b={'name':name,'parent':parent,'pivot':pivot,'cubes':cubes};bones.append(b);by[name]=b;return b
# Bake the original spear's distant bind rotation into its authored cubes. World-space rest mesh is unchanged.
weapon=by['longspear'];B=affine(rot_matrix(*weapon.get('rotation',[0,0,0])),mirror(weapon['pivot']));wc=[]
for c in weapon['cubes']:
 c=copy.deepcopy(c);C=affine(rot_matrix(*c.get('rotation',[0,0,0])),mirror(c.get('pivot',[0,0,0])));M=B@C
 center=mirror(np.asarray(c['origin'])+np.asarray(c['size'])/2);center=(M@np.append(center,1))[:3];center=mirror(center)
 c['origin']=(center-np.asarray(c['size'])/2).tolist();c['pivot']=center.tolist();c['rotation']=euler(M[:3,:3]);wc.append(c)
grip=by['RightHand']['pivot'][:];weapon.update(parent='RightHand',pivot=grip,rotation=[0,0,0],cubes=wc)
# Keep the source's waist appendage and hair bones. Replace only the wing equipment surfaces to match video views.
for n in ['fly','fly2','bone32','bone33','bone34','bone35','bone36','bone37']:
 by[n]['cubes']=[];by[n].pop('rotation',None)
for s,name in [(-1,'fly'),(1,'fly2')]:
 P=lambda x,y,z:[s*x,y,z]
 by[name]['pivot']=P(3.1,30.5,6);cs=[]
 for a,b,w,m in [((3,30,6),(11,41.5,7),2.2,1),((11,41.5,7),(14,54,7),1.7,1),((11,41.5,7),(29,34,9),1.8,2),((29,34,9),(46,19,10),1.4,2)]:cs.append(beam(P(*a),P(*b),w,m))
 # Stacked silver and dark plates on the wing joint, traced from the reference turntable.
 for i in range(4):
  cs.append(beam(P(8+i*2,39.5-i*.3,6.4),P(10+i*2,43-i*.4,6.7),1.45,2))
  cs.append(beam(P(8+i*2,40-i*.3,6.0),P(11+i*2,38-i*.3,6.1),1.3,1))
 by[name]['cubes']=cs;cs=[]
 # Four overlapping pointed panels rather than a regular scalloped bat silhouette.
 for j in range(4):
  a=np.array(P(11+j*3.9,40.4-j*1.65,8.2+j*.2));end=np.array(P(17+j*9.2,8.8+j*3.0,9.4+j*.2))
  for k in range(16):
   u=k/16;v=(k+1)/16;aa=a+(end-a)*u;bb=a+(end-a)*v;w=(8.3-j*.35)*(1-u)+.28
   cs.append(beam(aa,bb,w,0 if u<.38 else (6 if u<.58 else (3 if u<.8 else 4)),.38))
   if k>6 and k%3!=0:
    offset=np.array([s*w*.17,0,-.27]);cs.append(beam(aa+offset,bb+offset,w*.17,4 if k<13 else 5,.13))
  cs.append(beam(a,end,.55,1,.6))
 add(name+'_tips',name,P(11,40,8),cs)
# Face voice rig: original head texture includes the mouth; its tiny deformation uses a control-only bone.
add('Mouth','Head',[0,33.95,-3.55],[])
g['minecraft:geometry'][0]['description'].update(identifier='geometry.dragon_soul',texture_width=512,texture_height=576,visible_bounds_width=8,visible_bounds_height=6,visible_bounds_offset=[0,1.7,0])
def save(path,obj):path.write_text(json.dumps(obj,ensure_ascii=False,separators=(',',':'))+'\n',encoding='utf8')
save(AS/'geo/entity/dragon_soul.geo.json',g);im.save(AS/'textures/entity/dragon_soul.png')
projectile=copy.deepcopy(g);pg=projectile['minecraft:geometry'][0];pg['description']['identifier']='geometry.dragon_spear';pc=copy.deepcopy(wc)
for c in pc:
 for k in ['origin','pivot']:
  if k in c:c[k]=[c[k][i]-grip[i] for i in range(3)]
pg['bones']=[{'name':'root','pivot':[0,0,0],'cubes':pc}];save(AS/'geo/entity/dragon_spear.geo.json',projectile);save(AS/'animations/entity/dragon_spear.animation.json',{'format_version':'1.8.0','animations':{}})
# Full-body choreography. All rotations remain continuous and weapon stays at its hand pivot.
old=json.loads((SRC/'animations.json').read_text());out={k:v for k,v in old['animations'].items() if k.startswith('boss_voice') or k in ['boss_blink','boss_spear_hidden']}
stance={'AllBody':[0,-8,0],'UpperBody':[0,8,-3],'RightArm':[-12,0,-23],'RightForeArm':[-18,0,0],'LeftArm':[-12,0,-12],'LeftForeArm':[-58,0,5],'RightLeg':[-6,-8,-5],'LeftLeg':[4,8,5],'RightLowerLeg':[7,0,0],'LeftLowerLeg':[0,0,0],'longspear':[40,55,-15],'fly':[0,-8,-4],'fly2':[0,8,4]}
def pose(**kw):p=copy.deepcopy(stance);p.update(kw);return p
def clip(name,length,keys,loop=False,bob=0):
 names=set(stance)|set().union(*(set(p) for _,p in keys));tracks={n:{'rotation':{}} for n in names};tracks['Root']={'position':{}};N=round(length*40)
 for i in range(N+1):
  t=length*i/N;k=next((j for j in range(len(keys)-1) if t<=keys[j+1][0]),len(keys)-2);ta,a=keys[k];tb,b=keys[k+1];u=max(0,min(1,(t-ta)/(tb-ta)));u=u*u*(3-2*u);key=f'{t:.4f}';wave=math.sin(t/length*math.tau)
  for n in names:
   v=np.array(a.get(n,stance.get(n,[0,0,0])))*(1-u)+np.array(b.get(n,stance.get(n,[0,0,0])))*u
   tracks[n]['rotation'][key]=[round(float(x),3) for x in v]
  tracks['Root']['position'][key]=[0,round(bob*(1-wave),3),0]
 for n,amp,phase in [('LongHair',3,.7),('FrontSkirt',2,.4),('BackSkirt',3,1.2),('fly_tips',3,.5),('fly2_tips',-3,.5),('bone26',1,1.0)]:
  tracks[n]={'rotation':{f'{length*i/N:.4f}':[round(amp*math.sin(i/N*math.tau+phase),3),0,0] for i in range(N+1)}}
 out[name]={'loop':loop,'animation_length':length,'bones':tracks}
for name,L,bob in [('boss_stance',2.4,.14),('boss_hover',3,.6),('boss_fly',1,.4),('boss_carry',.8,.25)]:
 p=pose();q=pose(UpperBody=[-2,8,-3],fly=[-5,-12,-10],fly2=[-5,12,10])
 if name in ['boss_fly','boss_carry']:p=pose(AllBody=[55,0,0],RightLeg=[-20,0,0],LeftLeg=[-12,0,0],longspear=[-25,0,0]);q=copy.deepcopy(p);q.update(fly=[-10,-22,-14],fly2=[-10,22,14])
 clip(name,L,[(0,p),(L/2,q),(L,p)],True,bob)
wind=pose(AllBody=[-8,-35,-8],UpperBody=[-12,-25,-8],RightArm=[-125,-15,-35],RightForeArm=[-45,0,0],longspear=[-25,0,8],fly=[0,-25,-15],fly2=[0,10,6])
strike=pose(AllBody=[18,25,8],UpperBody=[24,22,5],RightArm=[-60,10,12],RightForeArm=[-8,0,0],longspear=[-5,0,-8],LeftArm=[15,0,-30],fly=[0,12,6],fly2=[0,-20,-12])
for name,a in old['animations'].items():
 if name in out or name.startswith('boss_intro'):continue
 L=a['animation_length'];short=name.endswith('_fast');base=name.removeprefix('boss_').removesuffix('_fast');hit=.25 if short else {'cleave':.85,'thrust':.7,'slash':.55,'kick':.6,'grab':.6,'magic':.8,'spear_throw':.75,'ult_flurry':.5}.get(base,L*.52)
 w=copy.deepcopy(wind);s=copy.deepcopy(strike)
 if base in ['thrust','spear_throw']:w=pose(RightArm=[-95,-40,-20],RightForeArm=[-70,0,0],longspear=[-5,0,0],AllBody=[-10,-30,0]);s=pose(RightArm=[-90,0,-18],RightForeArm=[0,0,0],longspear=[68,-18,0],AllBody=[22,18,0])
 if base=='kick':w=pose(RightLeg=[-65,0,0],RightLowerLeg=[80,0,0]);s=pose(AllBody=[-20,0,0],RightLeg=[-105,0,0],RightLowerLeg=[5,0,0])
 if base in ['grab','grab_throw']:w=pose(LeftArm=[-45,0,-30],LeftForeArm=[-80,0,0]);s=pose(LeftArm=[-95,0,-10],LeftForeArm=[-5,0,0],AllBody=[15,20,0])
 if base in ['magic','taunt','pant','teleport_charge','teleport_arrive','spear_catch','flourish','ult_start','ult_finish']:
  w=pose(LeftArm=[-45,0,-35],LeftForeArm=[-65,0,0],UpperBody=[-8,-15,0]);s=pose(LeftArm=[-85,0,-55],LeftForeArm=[-15,0,0],fly=[0,-18,-12],fly2=[0,18,12])
 clip(name,L,[(0,stance),(max(.04,hit-.16),w),(hit,s),(min(L-.04,hit+.16),s),(L,stance)])
# Distinct horizontal cuts and three explicitly timed ultimate strikes.
sidewind=pose(AllBody=[-4,-40,3],UpperBody=[-5,-25,0],RightArm=[-55,-55,-42],RightForeArm=[-50,0,0],longspear=[10,-30,-15])
sidehit=pose(AllBody=[12,32,-5],UpperBody=[12,25,-8],RightArm=[-65,48,15],RightForeArm=[-5,0,0],longspear=[40,45,-15])
for name,L,hit in [('boss_slash',1.2,.55),('boss_slash_fast',.9,.25)]:
 clip(name,L,[(0,stance),(hit-.12,sidewind),(hit,sidehit),(hit+.12,sidehit),(L,stance)])
clip('boss_ult_flurry',1.0,[(0,sidewind),(.15,sidehit),(.32,wind),(.50,strike),(.67,sidewind),(.80,sidehit),(1.0,sidewind)])
clip('boss_pant',2.0,[(0,stance),(.5,pose(UpperBody=[8,8,-3],RightArm=[-4,0,-20])),(1,stance),(1.5,pose(UpperBody=[6,8,-3])),(2,stance)])
clip('boss_intro_emerge',1.2,[(0,wind),(.45,pose(fly=[0,-25,-18],fly2=[0,25,18])),(1.2,stance)])
dive=pose(AllBody=[38,0,0],RightArm=[-35,0,-20],RightForeArm=[-35,0,0],RightLeg=[-30,0,0],RightLowerLeg=[65,0,0],LeftLeg=[20,0,0],longspear=[15,0,0],fly=[0,-55,-22],fly2=[0,55,22])
clip('boss_intro_dive',1.2,[(0,stance),(.35,dive),(1.2,dive)])
kneel=pose(AllBody=[20,0,0],UpperBody=[25,0,0],RightLeg=[-65,0,0],RightLowerLeg=[100,0,0],LeftLeg=[-25,0,0],LeftLowerLeg=[85,0,0],RightArm=[-35,0,-10],RightForeArm=[-30,0,0],longspear=[28,0,-12],fly=[0,-28,-15],fly2=[0,28,15])
clip('boss_intro_point',5.2,[(0,dive),(.13,kneel),(.7,kneel),(2.7,pose(RightArm=[-35,0,-25],RightForeArm=[-40,0,0],longspear=[30,0,-25])),(3.7,stance),(5.2,stance)])
# Compress the pelvis on impact, then plant the feet again during the slow rise.
r=out['boss_intro_point']['bones']['Root']['position']
for key in r:
 t=float(key)
 if t<.13:y=-6*t/.13
 elif t<.7:y=-6
 elif t<2.7:u=(t-.7)/2;y=-6*(1-u*u*(3-2*u))
 else:y=0
 r[key]=[0,round(y,3),0]
sys.path.insert(0,str(ROOT/'tools/weeping_death_lord'))
from geo_render import bone_world, mirror
for name in ['boss_stance','boss_intro_point']:
 a=out[name];rt=a['bones']['Root']['position']
 for key in rt:
  p={n:{'rot':b['rotation'][key]} for n,b in a['bones'].items() if 'rotation' in b}
  _,world=bone_world(g['minecraft:geometry'][0],p)
  ys=[]
  for n in ['RightFoot','LeftFoot','RightLowerLeg','LeftLowerLeg']:
   for c in by[n].get('cubes',[]):
    o=c['origin'];sz=c['size']
    for dx in [0,sz[0]]:
     for dz in [0,sz[2]]:
      ys.append((world(n)@np.append(mirror([o[0]+dx,o[1],o[2]+dz]),1))[1])
  rt[key]=[0,round(-min(ys),3),0]
save(AS/'animations/entity/dragon_soul.animation.json',{'format_version':'1.8.0','animations':out})
print('Generated',len(bones),'bones,',sum(len(b.get('cubes',[])) for b in bones),'cubes,',len(out),'animations')
