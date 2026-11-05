"""Author the celestial throne as a real GeckoLib cube rig and editable Blockbench source."""
from pathlib import Path
import json,math,uuid,base64
import numpy as np
from PIL import Image,ImageDraw,ImageFilter
ROOT=Path(__file__).resolve().parents[2];A=ROOT/'src/main/resources/assets/sourceofmystery';ART=ROOT/'art/divine_cinematic/throne';ART.mkdir(parents=True,exist_ok=True)
SIZE=1024;DENSITY=3
bones=[{'name':'throne_root','pivot':[0,0,0],'cubes':[]}];parts=[]
def write(p,data):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def bone(name,pivot,parent='throne_root'):
 b={'name':name,'parent':parent,'pivot':pivot,'cubes':[]};bones.append(b);return b
def box(name,c,s,mat=0,rot=(0,0,0),owner=None,hide=()):
 uv={};index=len(parts)
 for i,(face,w,h) in enumerate([('north',s[0],s[1]),('south',s[0],s[1]),('east',s[2],s[1]),('west',s[2],s[1]),('up',s[0],s[2]),('down',s[0],s[2])]):
  if face in hide:continue
  uw=w*DENSITY;vh=h*DENSITY
  u=(mat%4)*256+5+(index*17+i*13)%max(1,int(246-uw));v=(mat//4)*256+5+(index*11+i*19)%max(1,int(246-vh))
  uv[face]={'uv':[round(u,4),round(v,4)],'uv_size':[round(uw,4),round(vh,4)]}
 cube={'origin':[round(c[i]-s[i]/2,4) for i in range(3)],'size':list(s),'uv':uv}
 if any(rot):cube.update(pivot=list(c),rotation=[-rot[0],rot[1],-rot[2]])
 b=bones[0] if owner is None else owner;b['cubes'].append(cube);parts.append({'name':name,'material':mat,'bone':b['name'],'cube':cube});return cube

# Three shallow, clipped-looking tiers and an open shadow gap make a ceremonial plinth.
for i,(y,w,d) in enumerate([(1.6,49,34),(4.1,45,31),(6.1,41,28)]):
 box('plinth_dark_'+str(i),[0,y,-1],[w,2.4,d],1,hide=('down',))
 box('plinth_pearl_'+str(i),[0,y+1.35,-1],[w-.8,.65,d-.8],0,hide=('down',))
 for side in (-1,1):
  box('plinth_front_gilt_'+str(i)+str(side),[side*w*.248,y+.1,d/2-.7],[w*.48,.45,.45],2,hide=('down','north'))
  box('plinth_corner_'+str(i)+str(side),[side*(w/2-1.4),y+.7,d/2-1.8],[2.8,.42,2.8],3,(0,45,0))
# Curved leg supports are segmented solely where they change silhouette.
for side in (-1,1):
 for j in range(4):
  box(f'front_leg_{side}_{j}',[side*(15.8+j*.22),8+j*1.7,8-j*.25],[3.3,2.1,4.2],0,(0,0,side*(7-j*3)))
 box(f'front_leg_inlay_{side}',[side*16.1,11.0,10.12],[.43,6.8,.16],2)
 box(f'arm_support_shadow_{side}',[side*18,19.0,-.6],[3.4,17.0,16],1)
 box(f'arm_support_plate_{side}',[side*18.7,19.0,.1],[2.1,15.0,15],0,(0,0,side*-3))
 box(f'armrest_under_{side}',[side*18.1,28.1,0],[6.0,2.3,20.8],1,(-4,0,0))
 box(f'armrest_upper_{side}',[side*18.1,29.35,0],[5.8,.65,21.0],0,(-4,0,0))
 box(f'armrest_gilt_{side}',[side*18.1,29.76,.2],[4.7,.22,20.1],2,(-4,0,0))
 for k in range(3):
  box(f'armrest_tip_{side}_{k}',[side*18.1,28.6+k*.7,10.5+k*.5],[5.4-k*.8,1.0,1.5],0,(12+k*12,0,0))
 box(f'arm_front_ornament_{side}',[side*18.5,23.8,8.1],[3.0,3.0,.26],2,(0,0,45))
 box(f'arm_front_gem_{side}',[side*18.5,23.8,8.3],[1.9,1.9,.16],5,(0,0,45))

box('seat_frame',[0,15.2,0],[32,4.4,23],1)
box('seat_silver_lip',[0,17.5,0],[32.5,.6,23.5],3)
box('seat_gilt_piping',[0,17.92,.3],[30.5,.32,22],2)
box('seat_cushion',[0,18.55,.3],[29,1.1,20.3],4)
# Recessed, stepped back panel with a pointed centre and tiered shoulder fans.
for i,(y,w) in enumerate([(28,29),(40,28),(51,23),(60,16),(66,7)]):
 box('back_shadow_'+str(i),[0,y,-11.4],[w+2.1,13.2,3.9],1)
 box('back_pearl_'+str(i),[0,y,-9.25],[w,12.6,.75],0,(-3,0,0))
 for side in (-1,1):box(f'back_gilt_{i}_{side}',[side*(w/2+.5),y,-8.85],[.5,12.8,.6],2,(-3,0,0))
box('back_cushion',[0,31,-8.54],[24,23,.52],4,(-3,0,0))
box('back_cushion_spine',[0,31,-8.13],[.42,22,.12],2,(-3,0,0))
for side in (-1,1):
 # Fanned, layered wings; pale outer feathers frame genuine negative space.
 for j in range(6):
  c=[side*(15.5+j*2.1),43+j*3.2,-12-j*.32];length=25-j*1.75;angle=side*(17+j*5.8)
  box(f'wing_shadow_{side}_{j}',c,[5.8,length,2.4],1,(0,side*8,angle))
  box(f'wing_ivory_{side}_{j}',[c[0],c[1]+.6,c[2]+1.4],[4.4,length-1.8,.9],0,(0,side*8,angle))
  box(f'wing_gilt_{side}_{j}',[c[0],c[1]+1.8,c[2]+1.98],[.47,length-4,.35],2,(0,side*8,angle))
  box(f'wing_tip_{side}_{j}',[c[0]+side*1.3,c[1]+length*.47,c[2]+1.6],[2.6,3.6,.8],3,(0,side*8,side*45))
 # Raised, stepped tracing on the wing root.
 for j in range(4):box(f'wing_root_chevron_{side}_{j}',[side*(12+j*1.8),36+j*2,-7.3],[8.3,.5,.42],2,(0,0,side*35))

# A free-standing corona behind the throne, with twenty-four separated facets.
halo=bone('corona',[0,55,-16.8])
for i in range(32):
 a=i*math.tau/32;r=18.3
 box('corona_segment_'+str(i),[math.cos(a)*r,55+math.sin(a)*r,-16.8],[r*2*math.tan(math.pi/32)*.89,.52,.8],2,(0,0,math.degrees(a)+90),halo)
for i in range(12):
 a=i*math.tau/12;r=20.0
 box('corona_ray_'+str(i),[math.cos(a)*r,55+math.sin(a)*r,-16.8],[.6,3.3,.7],3,(0,0,math.degrees(a)-90),halo)
sigil=bone('suspended_taiji',[0,55,-7.35])
for i in range(24):
 a=i*math.tau/24;r=8.5
 box('sigil_rim_'+str(i),[math.cos(a)*r,55+math.sin(a)*r,-7.35],[2*r*math.tan(math.pi/24)*1.01,.52,.8],2,(0,0,math.degrees(a)+90),sigil)
c=box('taiji_disc',[0,55,-6.90],[15.9,15.9,.04],6,owner=sigil,hide=('north','east','west','up','down'))
c['uv']['south']={'uv':[2*256+16,1*256+16],'uv_size':[224,224]}
for side in (-1,1):
 for j in range(3):
  shard=bone(f'crown_shard_{side}_{j}',[side*(6.0+j*5.0),73-j*2.6,-12.4])
  c=[side*(6+j*5),73-j*2.6,-12.4]
  box(f'crown_blade_{side}_{j}',c,[2.8,10-j,2],1,(0,0,side*(-13-j*5)),shard)
  box(f'crown_blade_gilt_{side}_{j}',[c[0],c[1]+.2,c[2]+1.1],[.7,9-j,.45],2,(0,0,side*(-13-j*5)),shard)
  box(f'crown_blade_tip_{side}_{j}',[c[0],c[1]+4.5-j*.5,c[2]],[2,2,2],3,(0,0,45),shard)

# Original 1024 atlas. Surface ornament, seams and highlights stay in the texture.
rng=np.random.default_rng(205516);y,x=np.mgrid[0:256,0:256];atlas=np.zeros((SIZE,SIZE,4),np.uint8)
palette=[(221,225,232),(22,26,35),(201,187,145),(176,189,205),(35,40,54),(205,229,248),(0,0,0),(196,180,133)]
for mat,base in enumerate(palette):
 n=rng.normal(0,1,(256,256));v=np.sin(x*.033+y*.041+np.sin(y*.018)*2)
 tile=np.zeros((256,256,4),float);tile[:,:,:3]=base;tile[:,:,3]=255
 if mat==0:tile[:,:,:3]+=n[:,:,None]*1.5+np.exp(-(v/.025)**2)[:,:,None]*np.array([18,16,10])
 elif mat==1:tile[:,:,:3]+=n[:,:,None]*1.2+np.exp(-(v/.028)**2)[:,:,None]*np.array([21,23,25])
 elif mat in (2,3,7):
  tile[:,:,:3]+=(np.sin(y*.048)*8+np.sin(y*.39)*1.4+n)[:,:,None]
  edge=np.minimum.reduce([x,y,255-x,255-y]);tile[:,:,:3]+=np.maximum(0,1-edge/8)[:,:,None]*np.array([23,22,19])
 elif mat==4:
  diamond=np.abs((x+y)%64-32)+np.abs((x-y)%64-32);seam=np.exp(-((diamond-32)/1.3)**2)
  tile[:,:,:3]+=seam[:,:,None]*np.array([18,19,22])+n[:,:,None]*.8
 elif mat==5:tile[:,:,:3]+=np.sin(x*.05)[:,:,None]*4
 if mat==6:tile[:]=0
 atlas[(mat//4)*256:(mat//4+1)*256,(mat%4)*256:(mat%4+1)*256]=np.clip(tile,0,255).astype('uint8')
im=Image.fromarray(atlas,'RGBA');d=ImageDraw.Draw(im)
ox,oy=512,256;d.ellipse((ox+16,oy+16,ox+240,oy+240),fill=(230,235,242,255))
d.pieslice((ox+16,oy+16,ox+240,oy+240),90,270,fill=(12,17,26,255))
d.ellipse((ox+72,oy+16,ox+184,oy+128),fill=(12,17,26,255));d.ellipse((ox+72,oy+128,ox+184,oy+240),fill=(230,235,242,255))
d.ellipse((ox+114,oy+58,ox+142,oy+86),fill=(230,235,242,255));d.ellipse((ox+114,oy+170,ox+142,oy+198),fill=(12,17,26,255))
texture=A/'textures/cinematic/divine/throne.png';texture.parent.mkdir(parents=True,exist_ok=True);im.save(texture);im.save(ART/'throne.png')
write(texture.with_suffix('.png.mcmeta'),{'texture':{'blur':True,'clamp':False}})
geo={'format_version':'1.12.0','minecraft:geometry':[{'description':{'identifier':'geometry.divine_throne','texture_width':SIZE,'texture_height':SIZE,'visible_bounds_width':6,'visible_bounds_height':6,'visible_bounds_offset':[0,2.5,0]},'bones':bones}]}
write(A/'geo/cinematic/divine_throne.geo.json',geo);write(ART/'divine_throne.geo.json',geo);write(ART/'parts.json',parts)
anim_bones={'corona':{'rotation':{'0':[0,0,0],'40':[0,0,360]}},'suspended_taiji':{'position':{str(t):[0,round(math.sin(t*math.tau/10)*.45,5),0] for t in range(41)}}}
for b in bones:
 if b['name'].startswith('crown_shard'):
  phase=len(anim_bones)*.65;anim_bones[b['name']]={'position':{str(t):[0,round(math.sin(t*math.tau/8+phase)*.65,5),0] for t in range(41)}}
write(A/'animations/cinematic/divine_throne.animation.json',{'format_version':'1.8.0','animations':{'animation.divine_throne.idle':{'loop':True,'animation_length':40,'bones':anim_bones}}})
elements=[];children={b['name']:[] for b in bones}
for p in parts:
 c=p['cube'];uid=str(uuid.uuid5(uuid.NAMESPACE_URL,'divine-throne/'+p['name']));children[p['bone']].append(uid)
 faces={f:{'uv':[0,0,0,0],'texture':None} for f in ('north','south','east','west','up','down')}
 faces.update({f:{'uv':[*v['uv'],v['uv'][0]+v['uv_size'][0],v['uv'][1]+v['uv_size'][1]],'texture':0} for f,v in c['uv'].items()})
 elements.append({'name':p['name'],'type':'cube','uuid':uid,'from':c['origin'],'to':[c['origin'][i]+c['size'][i] for i in range(3)],'origin':c.get('pivot',[0,0,0]),'rotation':c.get('rotation',[0,0,0]),'faces':faces,'box_uv':False})
groups={b['name']:{'name':b['name'],'origin':b['pivot'],'uuid':str(uuid.uuid5(uuid.NAMESPACE_URL,'divine-throne/'+b['name'])),'children':children[b['name']]} for b in bones};out=[]
for b in bones:
 if 'parent' in b:groups[b['parent']]['children'].append(groups[b['name']])
 else:out.append(groups[b['name']])
write(ART/'divine_throne.bbmodel',{'meta':{'format_version':'4.10','model_format':'bedrock','box_uv':False},'name':'Celestial Investiture - Divine Throne','resolution':{'width':SIZE,'height':SIZE},'elements':elements,'outliner':out,'textures':[{'name':'throne.png','id':'0','uuid':str(uuid.uuid5(uuid.NAMESPACE_URL,'divine-throne/texture')),'source':'data:image/png;base64,'+base64.b64encode(texture.read_bytes()).decode(),'uv_width':SIZE,'uv_height':SIZE}]})
for p in parts:
 for uv in p['cube']['uv'].values():assert all(0<=uv['uv'][i] and uv['uv'][i]+uv['uv_size'][i]<=SIZE for i in range(2))
assert len(parts)<=320,len(parts)
plan={'preference':'appearance first, reusing the user explicit appearance preference and sacred cinematic direction','destination':'Forge Java 1.20.1 / GeckoLib 4.8.4, single client-only GeoObjectRenderer','geometry':'segmented cube rig; surface seams and engravings in atlas','shading':'vanilla albedo with painted highlights; standard entity cutout, no required shader pack','texture_size':[SIZE,SIZE],'texels_per_model_unit':DENSITY,'cubes':len(parts),'bones':len(bones),'quad_faces':sum(len(p['cube']['uv']) for p in parts),'triangle_budget':3600,'animation':'rigid bone idle, corona rotation and crown/sigil float','collision':'none; scenery only'}
assert plan['quad_faces']*2<=plan['triangle_budget'];write(ART/'asset-plan.json',plan);print(json.dumps(plan,indent=2))
