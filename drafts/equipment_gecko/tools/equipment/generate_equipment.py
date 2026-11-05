"""Authored tier silhouettes, continuous material atlases and editable GeckoLib sources.
Original pixel icons are deliberately not used: the user's latest direction is name/rank driven.
"""
from pathlib import Path
import base64,copy,json,math,uuid
import numpy as np
from PIL import Image
from scipy.ndimage import gaussian_filter

ROOT=Path(__file__).resolve().parents[2]
A=ROOT/'src/main/resources/assets/sourceofmystery'
G=ROOT/'src/generated/resources/assets/sourceofmystery'
ART=ROOT/'art/equipment_gecko';ART.mkdir(parents=True,exist_ok=True)
DENSITY=8
TIERS={
 'mystery':{'name':'秘源','theme':'mineral reliquary, recessed cyan ore, tempered teal steel','colors':[(42,69,76),(130,155,162),(182,161,101),(19,32,37),(10,21,27),(70,211,219),(103,139,144),(136,176,175)]},
 'spirit_source':{'name':'灵源','theme':'living emerald, ivory leaf plates, branching vines','colors':[(212,223,211),(46,104,76),(194,174,115),(25,49,37),(15,30,25),(103,237,168),(96,157,117),(174,206,183)]},
 'dark_source':{'name':'暗源','theme':'ink-black void armor, amethyst eye, broken star edges','colors':[(25,26,43),(55,48,83),(125,121,158),(18,17,30),(9,9,18),(164,108,243),(89,77,135),(124,110,169)]},
 'origin_dragon':{'name':'始源龙','theme':'primeval dragon scales, bronze horns, amber ember heart','colors':[(58,43,35),(104,55,36),(199,148,78),(26,20,17),(16,13,13),(255,150,46),(139,82,41),(188,127,65)]},
 'divine_punishment':{'name':'神威天罚','theme':'divine judgement, black-white blades and platinum lightning','colors':[(35,37,44),(222,225,228),(186,176,146),(19,20,24),(9,10,13),(230,245,255),(139,145,164),(208,211,220)]}
}
def write(path,obj):
 path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(obj,ensure_ascii=False,indent=2)+'\n',encoding='utf8')

class Model:
 def __init__(self,name,size):self.name=name;self.size=size;self.cell=size//4;self.bones=[];self.parts=[];self.faces=0
 def bone(self,name,pivot):
  b={'name':name,'pivot':pivot,'cubes':[]};self.bones.append(b);return b
 def box(self,bone,name,c,size,mat=0,rot=(0,0,0),hide=()):
  dims={'north':(size[0],size[1]),'south':(size[0],size[1]),'east':(size[2],size[1]),'west':(size[2],size[1]),'up':(size[0],size[2]),'down':(size[0],size[2])}
  uv={};i=len(self.parts);cell=self.cell
  for k,(face,(w,h)) in enumerate(dims.items()):
   if face in hide:continue
   pw,ph=w*DENSITY,h*DENSITY
   assert pw<=cell-8 and ph<=cell-8,(name,size,cell)
   x=4+(i*17+k*13)%max(1,int(cell-8-pw));y=4+(i*11+k*23)%max(1,int(cell-8-ph))
   uv[face]={'uv':[round(mat%4*cell+x,5),round(mat//4*cell+y,5)],'uv_size':[round(pw,5),round(ph,5)]};self.faces+=1
  cube={'origin':[round(c[j]-size[j]/2,5) for j in range(3)],'size':size,'uv':uv}
  if any(rot):cube.update(pivot=c,rotation=[-rot[0],rot[1],-rot[2]])
  bone['cubes'].append(cube);self.parts.append({'name':name,'bone':bone['name'],'material':mat,'cube':cube})
 def ring(self,bone,name,c,r,segments=8,mat=2,thickness=.24,depth=.35):
  for i in range(segments):
   a=(i+.5)*math.tau/segments
   self.box(bone,name+str(i),[c[0]+r*math.cos(a),c[1]+r*math.sin(a),c[2]],[2*r*math.tan(math.pi/segments)*1.01,thickness,depth],mat,(0,0,math.degrees(a)+90))
 def geo(self):return {'format_version':'1.12.0','minecraft:geometry':[{'description':{'identifier':'geometry.'+self.name,'texture_width':self.size,'texture_height':self.size,'visible_bounds_width':3,'visible_bounds_height':4,'visible_bounds_offset':[0,1,0]},'bones':self.bones}]}
 def export(self,tier,armor=False):
  kind='armor' if armor else 'item';data=self.geo();write(A/f'geo/{kind}/{self.name}.geo.json',data)
  write(A/f'animations/{kind}/{self.name}.animation.json',{'format_version':'1.8.0','animations':{}})
  if armor:
   displayed=copy.deepcopy(data)
   for b in displayed['minecraft:geometry'][0]['bones']:
    b['pivot'][1]-=16.5
    for c in b['cubes']:
     c['origin'][1]-=16.5
     if 'pivot' in c:c['pivot'][1]-=16.5
   displayed['minecraft:geometry'][0]['description']['identifier']+='_display'
   write(A/f'geo/item/{self.name}_chestplate.geo.json',displayed)
  tex=A/f'textures/{kind}/{self.name}.png';make_materials(tier,self.size,tex)
  folder=ART/self.name;folder.mkdir(exist_ok=True);write(folder/'parts.json',self.parts);write(folder/(self.name+'.geo.json'),data)
  elements=[];groups=[]
  for b in self.bones:
   children=[]
   for p in [p for p in self.parts if p['bone']==b['name']]:
    c=p['cube'];uid=str(uuid.uuid5(uuid.NAMESPACE_URL,self.name+'/'+p['name']));children.append(uid)
    elements.append({'name':p['name'],'uuid':uid,'from':c['origin'],'to':[c['origin'][i]+c['size'][i] for i in range(3)],'origin':c.get('pivot',b['pivot']),
      'rotation':[-c.get('rotation',[0,0,0])[0],c.get('rotation',[0,0,0])[1],-c.get('rotation',[0,0,0])[2]],'box_uv':False,
      'faces':{f:{'uv':[v['uv'][0],v['uv'][1],v['uv'][0]+v['uv_size'][0],v['uv'][1]+v['uv_size'][1]],'texture':0} for f,v in c['uv'].items()}})
   groups.append({'name':b['name'],'uuid':str(uuid.uuid5(uuid.NAMESPACE_URL,self.name+'/bone/'+b['name'])),'origin':b['pivot'],'children':children})
  write(folder/(self.name+'.bbmodel'),{'meta':{'format_version':'4.10','model_format':'bedrock','box_uv':False},'name':self.name,'model_identifier':'geometry.'+self.name,
    'resolution':{'width':self.size,'height':self.size},'elements':elements,'outliner':groups,'textures':[{'name':tex.name,'id':'0','uuid':str(uuid.uuid5(uuid.NAMESPACE_URL,self.name+'/texture')),
    'source':'data:image/png;base64,'+base64.b64encode(tex.read_bytes()).decode(),'uv_width':self.size,'uv_height':self.size}]})
  for p in self.parts:
   for f,v in p['cube']['uv'].items():
    assert all(0<=v['uv'][i] and v['uv'][i]+v['uv_size'][i]<=self.size for i in range(2))
  assert len(self.parts)<=180
  return {'id':self.name,'cubes':len(self.parts),'bones':len(self.bones),'quad_faces':self.faces,'triangles':self.faces*2,'texture':self.size,
          'density_px_per_model_unit':DENSITY,'uv_density_verified':True,'theme':TIERS[tier]['theme'],'runtime':str(A/f'geo/{kind}/{self.name}.geo.json')}

def make_materials(tier,size,path):
 cell=size//4;rng=np.random.default_rng(74219+list(TIERS).index(tier));atlas=np.zeros((size,size,4),dtype=np.uint8);glow=np.zeros_like(atlas)
 y,x=np.mgrid[:cell,:cell]
 for i,color in enumerate(TIERS[tier]['colors']):
  soft=gaussian_filter(rng.normal(size=(cell,cell)),4);soft/=max(soft.std(),1e-6)
  fine=gaussian_filter(rng.normal(size=(cell,cell)),.6);grain=np.sin(y*.47+x*.025)*1.2
  sheen=np.exp(-((x/cell-.35)/.32)**2)*9-4
  rgb=np.array(color)+soft[...,None]*2.2+fine[...,None]*1.7+grain[...,None]+sheen[...,None]
  if i==3:rgb+=((np.sin(x*.85)*np.sin(y*.85))[...,None])*1.8
  if i==5:rgb*=.87+.13*np.sin((x+y)*.018)[...,None]**2
  ox=i%4*cell;oy=i//4*cell;atlas[oy:oy+cell,ox:ox+cell,:3]=np.uint8(np.clip(rgb,0,255));atlas[oy:oy+cell,ox:ox+cell,3]=255
  if i==5:glow[oy:oy+cell,ox:ox+cell]=atlas[oy:oy+cell,ox:ox+cell]
 path.parent.mkdir(parents=True,exist_ok=True);Image.fromarray(atlas).save(path);Image.fromarray(glow).save(path.with_name(path.stem+'_glowmask.png'))
 # Albedo stays readable under the standard renderer, independent of PBR/shaders.
 path.with_suffix('.png.mcmeta').write_text(json.dumps({'texture':{'blur':True,'clamp':False}}))

def armor(tier):
 m=Model(tier,512);body=m.bone('armorBody',[0,24,0]);rank=list(TIERS).index(tier)
 def b(n,c,s,mat=0,rot=(0,0,0)):m.box(body,n,c,s,mat,rot)
 b('tailored_underplate',[0,18,0],[8.35,11.65,4.65],3)
 b('breast_foundation',[0,18.35,0],[8.5,10.8,4.95])
 for side in [-1,1]:
  b('breast_facet'+str(side),[side*2.05,20.6,-2.73],[4.05,4.9,.73],0,(0,side*8,side*-3))
  b('clavicle_trim'+str(side),[side*2.08,22.98,-3.1],[4.05,.27,.24],2,(0,side*8,side*-5))
  b('back_shell'+str(side),[side*2.1,19.75,2.73],[4.12,7.7,.64],1,(0,side*-7,0))
  b('scapula_trim'+str(side),[side*2.1,23.17,3.03],[4.1,.23,.24],2,(0,side*-7,0))
  b('collar'+str(side),[side*3.55,23.65,0],[1.1,1.1,4.9])
  for i,y in enumerate([17.2,15.4,13.6]):
   b('rib_lame'+str((side,i)),[side*2.0,y,-2.72],[3.93,1.6,.55],1 if i%2==0 else 0,(3,side*5,0))
   b('rib_trim'+str((side,i)),[side*2.0,y-.67,-3.02],[3.93,.18,.15],2)
 b('belt_frame',[0,12.5,-2.72],[7.5,.52,.55],2)
 b('belt_core',[0,12.5,-3.05],[1.75,.8,.2],5)
 b('spinal_ridge',[0,19.6,3.1],[.6,7.9,.4],2)
 for side,label in [(-1,'Right'),(1,'Left')]:
  arm=m.bone('armor'+label+'Arm',[side*5,22,0])
  def a(n,c,s,mat=0,rot=(0,0,0)):m.box(arm,label+'_'+n,c,s,mat,rot)
  a('closed_shoulder_liner',[side*6,20.9,0],[5,7.1,5],3)
  a('pauldron',[side*6.45,23.1,0],[5.25,1.4,5.55],0,(0,0,side*9))
  a('pauldron_front_rim',[side*6.45,23.1,-2.88],[5.28,.25,.24],2,(0,0,side*9))
  a('pauldron_rear_rim',[side*6.45,23.1,2.88],[5.28,.25,.24],2,(0,0,side*9))
  a('bicep_front',[side*6,19.6,-2.55],[4.0,3.45,.45],1)
  a('bracer',[side*6,15.5,0],[4.5,4.6,4.65])
  a('bracer_inlay',[side*6,15.65,-2.58],[2.7,3.4,.45],1)
  a('wrist_trim',[side*6,13.23,0],[4.62,.25,4.75],2)
  a('bracer_gem',[side*6,15.65,-2.86],[.45,2.45,.14],5)
  if tier=='mystery':
   a('ore_setting',[side*7.7,23.6,0],[1.5,1.2,2.7],2,(0,0,side*15))
   a('ore_crystal',[side*7.9,24.15,0],[1.0,1.5,1.65],5,(0,0,side*-18))
  if tier=='spirit_source':
   for i in range(3):
    a('leaf'+str(i),[side*(6.5+i*.6),23.65+i*.55,0],[2.4-i*.35,.43,4.45-i*.65],1,(0,0,side*(20+i*8)))
    a('leaf_vein'+str(i),[side*(6.5+i*.6),23.85+i*.55,-.1],[2.15-i*.35,.12,.22],2,(0,0,side*(20+i*8)))
  if tier=='dark_source':
   for i in range(3):
    a('void_fin'+str(i),[side*(7.0+i*.55),23.9+i*.4,.6-i*.65],[1.3,2.4-i*.35,.48],0,(0,0,side*(-18-i*8)))
    a('void_fin_light'+str(i),[side*(7.0+i*.55),23.95+i*.4,.32-i*.65],[.22,1.85-i*.35,.11],5,(0,0,side*(-18-i*8)))
  if tier=='origin_dragon':
   for i in range(3):
    a('scale'+str(i),[side*(7.2+.15*i),22.25-i*.8,0],[3.75-i*.25,1.2,5.0-i*.25],1,(0,0,side*15))
   for i in range(2):
    a('swept_horn'+str(i),[side*(7.4+i*.65),24.3+i*.9,.6],[.7,2.5-i*.5,1.1],2,(0,0,side*(-21-i*9)))
   a('dragon_eye',[side*8.85,22.2,-1.3],[.2,.36,1.1],5,(0,0,side*14))
  leg=m.bone('armor'+label+'Leg',[side*2,12,0])
  for z,label2 in [(-2.95,'front'),(2.95,'rear')]:
   m.box(leg,label+'_tasset_'+label2,[side*2.05,10.45,z],[3.85,3.8,.6],0,(z*2.3,0,side*-2))
   m.box(leg,label+'_tasset_trim_'+label2,[side*2.05,8.8,z+(-.32 if z<0 else .32)],[3.85,.22,.18],2)
  m.box(leg,label+'_side_tasset',[side*4.13,10.6,0],[.7,3.45,5.1],1,(0,0,side*7))
  if rank>=2:m.box(leg,label+'_hip_inlay',[side*2.1,10.4,-3.34],[.4,2.7,.2],5)
 # Every tier has a different three-dimensional chest motif.
 if tier=='mystery':
  m.ring(body,'reliquary_', [0,20.4,-3.32],1.72,8)
  b('ore_socket',[0,20.4,-3.35],[1.9,1.9,.55],4,(0,0,45))
  b('mineral_heart',[0,20.4,-3.72],[1.25,1.25,.65],5,(0,0,45))
  b('mineral_high_facet',[0,20.4,-4.04],[.55,.85,.12],1,(0,0,45))
 elif tier=='spirit_source':
  for side in [-1,1]:
   for i in range(3):b('living_leaf'+str((side,i)),[side*(.65+i*.44),19.2+i*.8,-3.4],[.7,1.9,.33],1,(0,0,side*(28+i*7)))
  b('living_stem',[0,20.25,-3.62],[.25,4,.17],2)
  b('spirit_heart',[0,21.3,-3.83],[.85,1.65,.35],5,(0,0,45))
 elif tier=='dark_source':
  m.ring(body,'broken_star_', [0,20.4,-3.4],1.72,8,1,.35,.45)
  b('void_eye_socket',[0,20.4,-3.55],[2.3,2.3,.34],4,(0,0,45))
  b('void_eye',[0,20.4,-3.79],[.5,2.15,.28],5)
  for side in [-1,1]:b('eye_lid'+str(side),[side*.65,20.4,-3.95],[.23,2.0,.17],2,(0,0,side*35))
 else:
  b('dragon_forehead',[0,21.15,-3.28],[2.5,2.0,.65],2)
  b('dragon_muzzle',[0,20.05,-3.9],[1.75,1.15,.95],1)
  b('dragon_lower_jaw',[0,19.35,-3.7],[1.7,.4,.7],2)
  for side in [-1,1]:
   b('dragon_chest_horn'+str(side),[side*1.35,22.0,-3.6],[.55,2.0,.65],2,(0,0,side*-20))
   b('dragon_chest_eye'+str(side),[side*.9,20.9,-3.67],[.75,.28,.15],5,(0,0,side*12))
   for i in range(3):b('chest_scale'+str((side,i)),[side*(2.3+i*.52),18.8-i*.65,-3.2],[1.3,.75,.45],1,(0,0,side*25))
 return m.export(tier,True)

def sword(tier):
 rank=list(TIERS).index(tier);name=tier+'_sword';m=Model(name,1024);root=m.bone('weapon',[0,0,0])
 def b(n,c,s,mat=0,rot=(0,0,0)):m.box(root,n,c,s,mat,rot)
 length=16+rank*1.6;width=1.65+rank*.13;end=5+length
 b('leather_grip',[0,0,0],[1.2,5.5,1.15],3)
 for i in range(4):b('grip_binding'+str(i),[0,-1.8+i*1.1,0],[1.28,.18,1.24],2,(0,0,7))
 b('grip_collar',[0,2.8,0],[1.6,.55,1.55],2)
 b('pommel',[0,-3.45,0],[1.9,1.9,1.5],2,(0,0,45))
 b('pommel_core',[0,-3.45,-.85],[.8,.8,.25],5,(0,0,45))
 b('blade_steel',[0,5+length/2,0],[width,length,.36],1)
 b('blade_ridge',[0,5+length/2,-.1],[width*.35,length,.55],0)
 for side in [-1,1]:
  b('bevel'+str(side),[side*width*.37,5+length/2,-.04],[width*.34,length,.24],1,(0,side*24,0))
  b('blade_inlay'+str(side),[side*width*.17,5+length*.49,-.405],[.10,length*.84,.06],5)
 # A rotated square's upper half makes a continuous triangular tip; no pixel staircase.
 b('tempered_point',[0,end,0],[width/math.sqrt(2),width/math.sqrt(2),.36],1,(0,0,45))
 b('guard_bridge',[0,4.0,0],[4.7,.8,1.4],2)
 b('guard_dark_core',[0,4.1,-.8],[2.0,1.4,.42],0)
 b('guard_jewel',[0,4.1,-1.1],[.9,.9,.4],5,(0,0,45))
 for side in [-1,1]:
  if tier=='mystery':
   b('mineral_guard'+str(side),[side*2.9,4.1,0],[2.35,.7,1.0],0,(0,0,side*18))
   b('crystal_tip'+str(side),[side*3.9,4.45,0],[.7,1.05,.8],5,(0,0,side*-20))
  elif tier=='spirit_source':
   for i in range(3):b('leaf_guard'+str((side,i)),[side*(2.5+i*.7),4.4+i*.65,0],[1.8-i*.25,.46,.95-i*.1],1,(0,0,side*(23+i*12)))
   b('root_sweep'+str(side),[side*1.7,6.6,-.2],[.42,4.1,.62],2,(0,0,side*-24))
  elif tier=='dark_source':
   for i in range(2):
    b('fractured_guard'+str((side,i)),[side*(2.6+i*.95),4.6+i*.9,0],[1.95-i*.4,.85,1.05],0,(0,0,side*(30+i*19)))
    b('void_edge'+str((side,i)),[side*(2.6+i*.95),4.88+i*.9,-.56],[1.6-i*.3,.13,.14],5,(0,0,side*(30+i*19)))
   b('void_fang'+str(side),[side*1.28,9.4,0],[.4,5.8,.55],0,(0,0,side*-12))
  elif tier=='origin_dragon':
   for i in range(3):b('dragon_wing'+str((side,i)),[side*(2.3+i*.9),4.4+i*.5,0],[2.0-i*.2,.9-i*.1,1.0],2,(0,0,side*(17+i*14)))
   b('dragon_horn'+str(side),[side*1.55,7.5,0],[.55,4.3,.8],2,(0,0,side*-20))
   b('dragon_guard_eye'+str(side),[side*1.0,5.1,-.93],[.6,.3,.17],5,(0,0,side*12))
   for i in range(4):b('blade_scale'+str((side,i)),[side*.83,7.5+i*1.12,-.4],[.76,.8,.2],2,(0,0,side*28))
  else:
   for i in range(3):
    b('judgement_wing'+str((side,i)),[side*(2.45+i*.9),4.5+i*.8,0],[1.75-i*.15,.58,1.15-i*.15],1,(0,0,side*(15+i*19)))
    b('wing_gold'+str((side,i)),[side*(2.45+i*.9),4.66+i*.8,-.62+i*.08],[1.65-i*.15,.14,.13],2,(0,0,side*(15+i*19)))
   for i in range(3):b('lightning'+str((side,i)),[side*(.6+(i%2)*.28),10.5+i*3.8,-.47],[.13,3.9,.1],5,(0,0,side*(15 if i%2==0 else -15)))
 if tier=='origin_dragon':b('dragon_snout',[0,6.0,-1.06],[1.3,.7,.85],0)
 if tier=='divine_punishment':
  m.ring(root,'judgement_seal_', [0,5.2,-1.0],1.4,12,2,.2,.24)
  b('white_judgement_core',[0,5.2,-1.3],[1.2,1.2,.42],5,(0,0,45))
 return m.export(tier)

def displays(name,armor=False):
 def pose(rotation,translation,scale):return {'rotation':rotation,'translation':translation,'scale':[scale]*3}
 if armor:
  display={'gui':pose([15,30,0],[0,0,0],.67),'ground':pose([0,0,0],[0,5,0],.38),'fixed':pose([0,180,0],[0,0,0],.7)}
  for side in ['right','left']:
   display['thirdperson_'+side+'hand']=pose([0,0,0],[0,1,0],.5);display['firstperson_'+side+'hand']=pose([0,20,0],[0,0,-1],.55)
 else:
  display={'gui':pose([0,0,-38],[0,-4,0],.48),'ground':pose([0,0,0],[0,5,0],.35),'fixed':pose([0,0,-35],[0,-5,0],.48)}
  for side in ['right','left']:
   sign=1 if side=='right' else -1
   display['thirdperson_'+side+'hand']=pose([0,0,0],[0,4,0],.72)
   display['firstperson_'+side+'hand']=pose([0,sign*7,sign*-12],[-1,0,0],.62)
 parent='equipment_armor_display' if armor else 'equipment_sword_display'
 write(A/f'models/item/{parent}.json',{'parent':'builtin/entity','gui_light':'front','display':display})
 write(G/f'models/item/{name}.json',{'parent':'sourceofmystery:item/'+parent,'textures':{'particle':'sourceofmystery:item/'+name}})

def main():
 report=[]
 for tier in TIERS:
  if tier!='divine_punishment':report.append(armor(tier));displays(tier+'_chestplate',True)
  report.append(sword(tier));displays(tier+'_sword')
 plan={'user_direction':'fresh designs by equipment name and progression rank; no inherited pixel-art motifs','preference':'appearance first',
   'target':'Java 1.20.1 Forge / GeckoLib 4.8.4, desktop; 1-8 visible wearers','shading':'opaque/cutout painted metal, cloth and emissive inlays; no PBR dependency',
   'render_passes':'1 base + 1 masked emissive, excluding native enchantment glint','rig':'worn armor follows native torso, arms, and independent leg-bound tassets; rigid weapons follow native hand swing',
   'budget_cubes_per_model':180,'texel_density':DENSITY,'assets':report,'excluded':'all divine_blessing armor, halo and CG; existing high-detail dimension_breaker_staff retained'}
 write(ART/'asset-plan.json',plan);write(ROOT/'build/equipment-gecko/asset-plan.json',plan)
 print(json.dumps(plan,ensure_ascii=False,indent=2))
if __name__=='__main__':main()
