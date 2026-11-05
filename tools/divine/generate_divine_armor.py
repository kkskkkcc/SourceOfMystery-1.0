"""Original war-god chestplate item: articulated armor, chaos maps and taiji emblem."""
from pathlib import Path
import base64,json,math,uuid
import numpy as np
from PIL import Image,ImageDraw
from scipy.ndimage import gaussian_filter

ROOT=Path(__file__).resolve().parents[2]
ASSETS=ROOT/'src/main/resources/assets/sourceofmystery'
SOURCE=ROOT/'art/divine_blessing_armor';SOURCE.mkdir(parents=True,exist_ok=True)
SIZE=512;DENSITY=8
bones=[{'name':'armorBody','pivot':[0,24,0],'cubes':[]}]
parts=[];checks=[]

def write(path,data):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf8')

def box(name,center,size,material=0,rotation=(0,0,0),hide=(),density=DENSITY,bone=None):
    dims={'north':(size[0],size[1]),'south':(size[0],size[1]),
          'east':(size[2],size[1]),'west':(size[2],size[1]),
          'up':(size[0],size[2]),'down':(size[0],size[2])}
    uv={};index=len(parts)
    for i,(face,(w,h)) in enumerate(dims.items()):
        if face in hide:continue
        u=(material%4)*128+4+(index*13+i*19)%max(1,int(120-w*density))
        v=(material//4)*128+4+(index*17+i*11)%max(1,int(120-h*density))
        uv[face]={'uv':[round(u,5),round(v,5)],'uv_size':[round(w*density,5),round(h*density,5)]}
        checks.append({'part':name,'face':face,'surface':[w,h],'sampled_pixels':[w*density,h*density],'density':density})
    cube={'origin':[round(center[i]-size[i]/2,5) for i in range(3)],'size':size,'uv':uv}
    if any(rotation):cube.update(pivot=center,rotation=[-rotation[0],rotation[1],-rotation[2]])
    owner=bones[0] if bone is None else bone
    owner['cubes'].append(cube);parts.append({'name':name,'material':material,'bone':owner['name'],'cube':cube})
    return cube

# Torso. The item remains a chestplate; the user also permits articulated extensions.
box('woven_armature',[0,18,0],[8.3,11.6,4.6],4)
box('thorax_core',[0,18.4,0],[8.45,10.55,4.9],0)
for side in (-1,1):
    # Convex breast and scapular plates. Silver borders keep the silhouette readable.
    box(f'pectoral_{side}',[side*2.12,20.35,-2.72],[4.14,5.25,.88],0,(0,side*9,side*-3))
    box(f'clavicle_edge_{side}',[side*2.1,22.95,-3.08],[4.12,.34,.24],2,(0,side*9,side*-8))
    box(f'outer_pectoral_border_{side}',[side*4.12,20.25,-2.73],[.3,4.65,.3],3,(0,side*15,side*-4))
    box(f'pectoral_lower_edge_{side}',[side*2.12,17.91,-2.89],[3.94,.25,.28],2,(0,side*9,side*3))
    box(f'breast_facet_{side}',[side*3.66,20.5,-2.49],[1.13,4.9,.6],1,(0,side*28,side*-3))
    box(f'back_plate_{side}',[side*2.04,19.75,2.67],[4.05,7.9,.69],0,(0,side*-8,side*3))
    box(f'back_top_edge_{side}',[side*2.03,23.23,2.97],[4.1,.3,.26],2,(0,side*-8,side*7))
    box(f'back_outer_edge_{side}',[side*4.02,19.8,2.7],[.28,6.9,.29],3,(0,side*-12,side*3))
    # Three rib plates on each flank, fitted within the chest width.
    for i,y in enumerate((21.3,18.45,15.6)):
        box(f'rib_plate_{side}_{i}',[side*4.2,y,0],[.49,2.68,4.66],0 if i!=1 else 1,(0,0,side*-2))
        box(f'rib_rim_{side}_{i}',[side*4.37,y-1.19,0],[.18,.22,4.72],2)
    box(f'neck_flank_{side}',[side*3.35,23.42,0],[1.35,.86,4.9],0)
    box(f'neck_rim_{side}',[side*3.35,23.84,0],[1.43,.16,5.02],2)

# Overlapping abdominal lames end at the waist, never below it.
for i,(y,width) in enumerate(((16.7,7.85),(14.95,7.45),(13.3,6.95))):
    box(f'abdominal_center_{i}',[0,y,-2.77],[3.35,1.77,.52],0,(3,0,0))
    box(f'abdominal_center_rim_{i}',[0,y-.73,-3.07],[3.38,.2,.17],2)
    for side in (-1,1):
        w=(width-3.1)/2
        box(f'abdominal_wing_{i}_{side}',[side*(1.55+w/2),y+.08,-2.64],[w,1.65,.48],0,(3,side*16,side*3))
        box(f'abdominal_wing_rim_{i}_{side}',[side*(1.55+w/2),y-.64,-2.91],[w,.18,.17],3,(0,side*16,side*3))
for i,(y,w) in enumerate(((14.85,7.65),(13.15,7.1))):
    box(f'lumbar_plate_{i}',[0,y,2.67],[w,1.65,.5],0,(-3,0,0))
    box(f'lumbar_rim_{i}',[0,y-.69,2.95],[w,.21,.15],2)

# Low open gorget, central keels and the user's circular yin-yang crest.
box('front_gorget',[0,23.45,-2.64],[5.6,.68,.72],0)
box('front_gorget_rim',[0,23.75,-2.98],[5.74,.16,.18],2)
box('rear_gorget',[0,23.5,2.65],[5.65,.71,.65],0)
box('rear_gorget_rim',[0,23.83,2.95],[5.78,.16,.18],2)
box('sternum_keel',[0,18.95,-3.12],[.66,8.3,.43],3)
box('sternum_dark_inlay',[0,18.9,-3.36],[.29,7.95,.13],5)
for i in range(16):
    angle=(i+.5)*math.tau/16;r=1.82
    box(f'taiji_metal_rim_{i}',[r*math.cos(angle),20.55+r*math.sin(angle),-3.72],
        [2*r*math.tan(math.pi/16)*1.015,.23,.45],3,(0,0,math.degrees(angle)+90))
face=box('taiji_face',[0,20.55,-3.89],[3.5,3.5,.04],8,
         hide=('south','east','west','up','down'),density=16)
face['uv']['north']={'uv':[4,260],'uv_size':[56,56]}
bones[0]['cubes'].remove(face)
bones.append({'name':'taiji_emblem','parent':'armorBody','pivot':[0,20.55,-3.89],'cubes':[face]})
parts[-1]['bone']='taiji_emblem'
box('spinal_keel',[0,19.0,3.05],[.69,7.8,.51],5)
for side in (-1,1):
    box(f'spine_edge_{side}',[side*.43,19,3.3],[.16,7.85,.16],2)
    box(f'back_chevron_{side}',[side*1.27,21.7,3.19],[2.78,.37,.29],1,(0,0,side*29))
    box(f'back_chevron_rim_{side}',[side*1.27,21.97,3.2],[2.8,.12,.18],3,(0,0,side*29))
box('waist_front_edge',[0,12.35,-2.59],[6.8,.28,.29],2)
box('waist_back_edge',[0,12.35,2.62],[6.8,.28,.29],2)

# Broad layered pauldrons and bracers copy each arm's native shoulder pivot.
for side,label in ((-1,'Right'),(1,'Left')):
    arm={'name':'armor'+label+'Arm','pivot':[side*5,22,0],'cubes':[]};bones.append(arm)
    def arm_box(name,center,size,material=0,rotation=(0,0,0)):
        return box(label.lower()+'_'+name,center,size,material,rotation,bone=arm)
    cx=side*6.1
    # Close the shoulder socket beneath the sloped pauldron. The classic arm plus
    # its 0.25-unit skin sleeve reaches Y=24.25 and X/Z beyond the bare 4x4 arm.
    # Keep this cover on the same arm bone so its clearance survives arm motion.
    arm_box('bicep_liner',[side*6,20.9,0],[5.0,7.1,5.0],0)
    arm_box('pauldron_core',[side*6.35,22.5,0],[5.25,2.15,5.55],0,(0,0,side*9))
    arm_box('pauldron_crown',[side*6.45,23.65,0],[5.2,.58,5.6],0,(0,0,side*12))
    for z in (-2.88,2.88):
        arm_box('crown_rim_'+str(z),[side*6.45,23.65,z],[5.2,.22,.22],2,(0,0,side*12))
    for i,y in enumerate((22.0,20.92,19.9)):
        arm_box('shoulder_lame_'+str(i),[side*(7.5+.12*i),y,0],[3.7-i*.15,1.04,5.3-i*.25],0,(0,0,side*13))
        arm_box('shoulder_lame_rim_'+str(i),[side*(7.5+.12*i),y-.42,-2.72+i*.125],[3.72-i*.15,.18,.18],2,(0,0,side*13))
    arm_box('outer_shoulder_shield',[side*8.08,21.55,0],[1.5,3.8,4.75],0,(0,0,side*15))
    arm_box('crown_blade_base',[side*8.13,24.08,.9],[1.6,.4,2.15],2,(0,0,side*25))
    arm_box('crown_blade',[side*8.7,24.7,.9],[.58,1.35,1.9],0,(0,0,side*-20))
    arm_box('bicep_cuff',[cx,18.9,0],[4.3,1.5,4.6],0)
    arm_box('bicep_cuff_rim',[cx,18.22,0],[4.42,.2,4.78],3)
    arm_box('bicep_front',[cx,19.65,-2.4],[3.65,3.2,.38],0)
    arm_box('bracer_core',[cx,15.55,0],[4.35,4.55,4.5],0)
    arm_box('bracer_front',[cx,15.55,-2.38],[3.7,4.23,.55],0,(4,0,0))
    arm_box('bracer_outer_facet',[side*8.14,15.65,0],[.46,4.1,4.35],1)
    arm_box('wrist_rim',[cx,13.12,0],[4.48,.32,4.78],2)
    arm_box('bracer_keel',[cx,15.55,-2.74],[1.12,2.8,.24],3)
    arm_box('bracer_inlay',[cx,15.55,-2.89],[.59,2.37,.12],5)
    arm_box('bracer_back',[cx,15.6,2.4],[3.67,4.13,.32],0)
    for y in (13.65,17.55):arm_box('bracer_back_rim_'+str(y),[cx,y,2.6],[3.72,.18,.15],2)

# Separate hip guards use the vanilla leg pivots. They do not create leg armor items.
for side,label in ((-1,'Right'),(1,'Left')):
    leg={'name':'armor'+label+'Leg','pivot':[side*2,12,0],'cubes':[]};bones.append(leg)
    def hip_box(name,center,size,material=0,rotation=(0,0,0)):
        return box(label.lower()+'_'+name,center,size,material,rotation,bone=leg)
    cx=side*2.15
    hip_box('hip_front',[cx,10.4,-3.1],[3.8,4.65,.62],0,(-8,0,side*-2))
    for y in (12.45,8.4):hip_box('hip_front_rim_'+str(y),[cx,y,-3.4],[3.82,.23,.19],2)
    hip_box('hip_front_outer_trim',[side*3.9,10.5,-3.37],[.2,4.12,.21],3,(-8,0,side*-2))
    hip_box('hip_front_keel',[cx,10.6,-3.55],[.35,3.45,.18],3,(-8,0,side*-2))
    hip_box('hip_side',[side*4.25,10.65,.1],[.58,3.7,5.9],0,(0,0,side*10))
    hip_box('hip_side_rim',[side*4.55,9.08,.1],[.23,.2,5.87],2)
    hip_box('hip_back',[cx,10.6,3.07],[3.8,4.18,.53],0,(8,0,side*2))
    hip_box('hip_back_rim',[cx,8.78,3.41],[3.81,.21,.2],2)

# The chaos is original domain-warped marbling, painted into the albedo, not a PBR dependency.
atlas=np.zeros((SIZE,SIZE,4),dtype=np.uint8)
rng=np.random.default_rng(110508)
yy,xx=np.mgrid[:128,:128]
for material in range(6):
    x=xx/128*math.tau;y=yy/128*math.tau
    a=x+.34*np.sin(2*y)+.17*np.sin(3*x-y)
    b=y+.32*np.sin(2*x+y)+.12*np.cos(3*y)
    turbulence=np.sin(4*a+2*b+.8*np.sin(3*b-a))
    threads=np.exp(-np.square(np.sin(7*a-3*b+.9*np.sin(2*a+b))/.065))
    dust=rng.normal(0,1.4,(128,128))
    if material==0:
        shade=36+18*turbulence+26*np.power(.5+.5*np.sin(3*a+4*b),3)+115*threads
        rgb=np.stack((shade*.91,shade*.96,shade*1.03),axis=-1)
    elif material==1:
        shade=177+19*turbulence-107*np.power(.5+.5*np.sin(4*a-2*b),9)-48*threads
        rgb=np.stack((shade*1.02,shade*1.01,shade*.98),axis=-1)
    elif material in (2,3):
        color=np.array((169,178,185) if material==2 else (155,131,87))
        grain=1+.09*np.sin(xx*.45+yy*.07)+.07*np.cos(yy*.12)
        rgb=color[None,None,:]*grain[:,:,None]
        rgb-=np.exp(-np.square(np.sin(x*11+y*.4)/.07))[:,:,None]*17
    elif material==4:
        shade=19+np.sin(xx*math.pi/2)*np.sin(yy*math.pi/2)*2
        rgb=np.stack((shade*.8,shade*.88,shade),axis=-1)
    else:
        shade=14+9*np.power(.5+.5*np.sin(x+y*.7),8)
        rgb=np.stack((shade*.86,shade*.94,shade*1.08),axis=-1)
    rgb=np.clip(rgb+dust[:,:,None],0,255).astype('uint8')
    ox=(material%4)*128;oy=(material//4)*128
    atlas[oy:oy+128,ox:ox+128,:3]=rgb;atlas[oy:oy+128,ox:ox+128,3]=255

# Minute forged engraving belongs in the surface, not extra cubes.
im=Image.fromarray(atlas);draw=ImageDraw.Draw(im)
for x in (9,41,73,105):
    for y in (14,47,80,113):
        draw.line([(x,y+4),(x,y-4),(x+5,y),(x+1,y+2)],fill=(91,97,105,255),width=1)
# Crisp original yin-yang artwork in a unique hero UV island, supersampled for its round edge.
n=56;factor=8
sy,sx=np.mgrid[:n*factor,:n*factor];tx=(sx+.5)/(n*factor)*2-1;ty=(sy+.5)/(n*factor)*2-1
inside=tx*tx+ty*ty<=1
white=tx>=0
white=np.where(tx*tx+np.square(ty+.5)<=.25,True,white)
white=np.where(tx*tx+np.square(ty-.5)<=.25,False,white)
white=np.where(tx*tx+np.square(ty+.5)<=.115**2,False,white)
white=np.where(tx*tx+np.square(ty-.5)<=.115**2,True,white)
symbol=np.zeros((n*factor,n*factor,4),dtype='uint8')
symbol[:,:,:3]=np.where(white[:,:,None],np.array([234,238,239]),np.array([10,13,17]))
symbol[:,:,3]=inside*255
badge=Image.fromarray(symbol).resize((n,n),Image.Resampling.LANCZOS)
im.paste(badge,(4,260));badge.save(SOURCE/'taiji_emblem.png')
texture_path=ASSETS/'textures/armor/divine_blessing.png';texture_path.parent.mkdir(parents=True,exist_ok=True)
im.save(texture_path);im.save(SOURCE/'divine_blessing.png')

# Seamless, sparse opposing wisps; UV offsets animate only these dedicated maps.
yy,xx=np.mgrid[:SIZE,:SIZE];x=xx/SIZE*math.tau;y=yy/SIZE*math.tau
a=x+.18*np.sin(2*y)+.12*np.sin(x-3*y)
b=y+.16*np.sin(3*x-y)+.12*np.cos(2*y)
for kind in ('white','black'):
    field=np.sin((6*a-4*b if kind=='white' else 4*a+5*b)+.75*np.sin(3*a+2*b))
    ridge=np.exp(-np.square(field/(.065 if kind=='white' else .16)))
    gate=np.power(.5+.5*np.sin(2*x+3*y),3)
    alpha=np.clip(ridge*(.25+.75*gate)*255,0,255).astype('uint8')
    rgba=np.empty((SIZE,SIZE,4),dtype='uint8')
    rgba[:,:,:3]=[239,244,250] if kind=='white' else [2,3,5];rgba[:,:,3]=alpha
    p=ASSETS/f'textures/armor/divine_blessing_{kind}_flow.png'
    Image.fromarray(rgba).save(p);Image.fromarray(rgba).save(SOURCE/p.name)
    write(p.with_suffix('.png.mcmeta'),{'texture':{'blur':True,'clamp':False}})

geo={'format_version':'1.12.0','minecraft:geometry':[{
    'description':{'identifier':'geometry.divine_blessing','texture_width':SIZE,'texture_height':SIZE,
                   'visible_bounds_width':2,'visible_bounds_height':2.5,'visible_bounds_offset':[0,1,0]},
    'bones':bones}]}
write(ASSETS/'geo/armor/divine_blessing.geo.json',geo);write(SOURCE/'divine_blessing.geo.json',geo)
write(ASSETS/'animations/armor/divine_blessing.animation.json',{'format_version':'1.8.0','animations':{}})

# Native editable Blockbench Bedrock source, using the same exported cube/UV data.
elements=[];children_by_bone={b['name']:[] for b in bones}
for p in parts:
    c=p['cube'];uid=str(uuid.uuid5(uuid.NAMESPACE_URL,'divine-chestplate/'+p['name']))
    children_by_bone[p['bone']].append(uid)
    faces={f:{'uv':[0,0,0,0],'texture':None} for f in ('north','south','east','west','up','down')}
    faces.update({f:{'uv':[*v['uv'],v['uv'][0]+v['uv_size'][0],v['uv'][1]+v['uv_size'][1]],'texture':0} for f,v in c['uv'].items()})
    elements.append({'name':p['name'],'type':'cube','uuid':uid,'from':c['origin'],
        'to':[c['origin'][i]+c['size'][i] for i in range(3)],'origin':c.get('pivot',[0,24,0]),
        'rotation':c.get('rotation',[0,0,0]),'faces':faces,'box_uv':False,'visibility':True})
groups={b['name']:{'name':b['name'],'origin':b['pivot'],'uuid':str(uuid.uuid5(uuid.NAMESPACE_URL,'divine-chestplate/'+b['name'])),
        'children':children_by_bone[b['name']]} for b in bones}
outliner=[]
for b in bones:
    if 'parent' in b:groups[b['parent']]['children'].append(groups[b['name']])
    else:outliner.append(groups[b['name']])
write(SOURCE/'divine_blessing.bbmodel',{'meta':{'format_version':'4.10','model_format':'bedrock','box_uv':False},
    'name':'Divine Blessing - articulated chestplate','model_identifier':'geometry.divine_blessing',
    'resolution':{'width':SIZE,'height':SIZE},'elements':elements,
    'outliner':outliner,
    'textures':[{'name':'divine_blessing.png','id':'0','uuid':str(uuid.uuid5(uuid.NAMESPACE_URL,'divine-chestplate/texture')),
        'source':'data:image/png;base64,'+base64.b64encode(texture_path.read_bytes()).decode(),'uv_width':SIZE,'uv_height':SIZE}]})
write(SOURCE/'parts.json',parts)

for c in checks:
    assert all(abs(c['sampled_pixels'][i]/c['surface'][i]-c['density'])<1e-6 for i in range(2))
for part in parts:
    for uv in part['cube']['uv'].values():
        assert all(0<=uv['uv'][i] and uv['uv'][i]+uv['uv_size'][i]<=SIZE for i in range(2))
assert len(parts)<=180,len(parts)
plan={'scope':'one chestplate item; breast/back armor, pauldrons, arm guards and independently leg-bound hip guards',
    'preference':'appearance first; latest user clarification explicitly permits articulated extensions',
    'target':'Minecraft Java 1.20.1 Forge, GeckoLib 4.8.4, desktop',
    'copies_budget':'hero wearable, typically 1-8 visible; runtime frame time not measured',
    'geometry_for':'breast/scapular plating, layered pauldrons and bracers, leg-bound hip guards, recessed circular taiji crest with faceted metal bezel',
    'baked_details':'chaos marbling, hairline engraving, metal grain, cloth weave',
    'cubes':len(parts),'bones':len(bones),'quad_faces':len(checks),'triangles':len(checks)*2,
    'cube_budget':180,'texture_size':[SIZE,SIZE],'density_px_per_model_unit':DENSITY,
    'intentional_hero_detail_density':'taiji emblem 16 px/model unit; all other faces 8 px/model unit',
    'uv_density_audited_faces':len(checks),'uv_density_passed':True,'uv_strategy':'shared material atlas; proportional per-face UVs',
    'shading':'opaque/cutout color atlas; no PBR required',
    'flow':'black alpha-blended shadow + white emissive flow, same armor mesh, 3s within each 13.6s cycle; cutout taiji face excluded',
    'render_passes':'1 base normally; up to 3 while flow active, excluding enchant glint and existing halo',
    'animation':'torso/arms/hip guards copy native body/arm/leg poses; no independent physics or server ticker',
    'shoulder_fit':'closed arm-bound shoulder liner; top Y=24.45, covers classic and slim skin sleeves with clearance',
    'runtime_test':'not run, per user instruction','artwork':'original procedural design, no third-party artwork'}
write(SOURCE/'asset-plan.json',plan);write(ROOT/'build/divine-armor-asset-plan.json',plan)
print(json.dumps(plan,ensure_ascii=False,indent=2))
