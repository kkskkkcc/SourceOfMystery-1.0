"""Original local Blender scene and matching GeckoLib cubes for the Alchemist's Table."""
from pathlib import Path
import bpy,json,math,random
import numpy as np
from mathutils import Vector

ROOT=Path(__file__).resolve().parents[2]
HOME=Path('F:/ChatGPT/我的世界神秘之源模组开发/modeling/alchemist_table')
ASSET=ROOT/'src/main/resources/assets/sourceofmystery'
for folder in ('source','reference','blender','export'):(HOME/folder).mkdir(parents=True,exist_ok=True)
def write(path,data):path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n','utf8')
def texture(path,glow=False):
    rng=np.random.default_rng(11611);palette=[(29,25,39),(179,132,57),(44,37,52),(194,190,177),
        (125,64,179),(49,159,157),(167,49,80),(200,184,143),(68,42,44),(45,42,64),(180,221,232),(207,167,80),
        (83,65,104),(47,81,91),(27,22,32),(107,88,51)]
    data=np.zeros((512,512,4),dtype=np.float32)
    yy,xx=np.mgrid[0:128,0:128]
    for mat,color in enumerate(palette):
        noise=rng.normal(0,.022,(128,128))+np.sin(xx*.13+yy*.035)*.018
        shade=.77+.21*(1-yy/128)+noise
        if mat==0:shade+=np.cos(xx*.08+yy*.16)*.05
        if mat==1:shade+=np.sin(yy*1.2)*.025
        cell=np.clip(np.array(color)/255*shade[:,:,None],0,1)
        row,col=mat//4*128,mat%4*128;data[row:row+128,col:col+128,:3]=cell
        data[row:row+128,col:col+128,3]=1 if not glow or mat in (4,5,10) else 0
    img=bpy.data.images.new(path.stem,512,512,alpha=True);img.pixels.foreach_set(data[::-1].ravel());img.filepath_raw=str(path);img.file_format='PNG';img.save();return img,palette

base,palette=texture(HOME/'source/alchemist_table.png')
mask,_=texture(HOME/'source/alchemist_table_glowmask.png',True)
bpy.ops.object.select_all(action='SELECT');bpy.ops.object.delete(use_global=False)
materials=[]
for i,color in enumerate(palette):
    mat=bpy.data.materials.new('alchemy_'+str(i));mat.use_nodes=True;mat.node_tree.nodes.clear()
    bsdf=mat.node_tree.nodes.new('ShaderNodeBsdfPrincipled');output=mat.node_tree.nodes.new('ShaderNodeOutputMaterial');mat.node_tree.links.new(bsdf.outputs['BSDF'],output.inputs['Surface'])
    bsdf.inputs['Base Color'].default_value=(*[v/255 for v in color],1)
    bsdf.inputs['Metallic'].default_value=.75 if i==1 else .12;bsdf.inputs['Roughness'].default_value=.32 if i==1 else .58
    if i in (4,5,10):bsdf.inputs['Emission Color'].default_value=(*[v/255 for v in color],1);bsdf.inputs['Emission Strength'].default_value=.65
    tex=mat.node_tree.nodes.new('ShaderNodeTexImage');tex.image=base;mat.node_tree.links.new(tex.outputs['Color'],bsdf.inputs['Base Color']);materials.append(mat)
bones=[{'name':'table','pivot':[0,0,0],'cubes':[]}];parts=[]
def box(name,center,size,material=0,roll=0,yaw=0):
    uv={};u=material%4*128+1;v=material//4*128+1
    dims={'north':(size[0],size[1]),'south':(size[0],size[1]),'east':(size[2],size[1]),'west':(size[2],size[1]),'up':(size[0],size[2]),'down':(size[0],size[2])}
    for face,(w,h) in dims.items():uv[face]={'uv':[u,v],'uv_size':[w*7.8,h*7.8]}
    cube={'origin':[round(center[i]-size[i]/2,5) for i in range(3)],'size':size,'uv':uv}
    if roll or yaw:cube.update({'pivot':center,'rotation':[0,-yaw,roll]})
    bones[0]['cubes'].append(cube);parts.append({'name':name,'material':material,'cube':cube})
    bpy.ops.mesh.primitive_cube_add(size=1,location=(center[0]/16,center[2]/16,center[1]/16))
    obj=bpy.context.object;obj.name=name;obj.dimensions=(size[0]/16,size[2]/16,size[1]/16);obj.rotation_euler=(0,math.radians(roll),math.radians(yaw));bpy.ops.object.transform_apply(location=False,rotation=False,scale=True);obj.data.materials.append(materials[material])
    layer=obj.data.uv_layers.active
    for poly in obj.data.polygons:
        for k,loop in enumerate(poly.loop_indices):layer.data[loop].uv=((u+(k in (1,2))*64)/512,1-(v+(k>=2)*64)/512)
    return obj

# Open-legged workbench, with an inlaid brass band and a recessed obsidian writing surface.
for x in (-5.8,5.8):
 for z in (-5.8,5.8):
    box('obsidian_leg', [x,5.8,z],[2,10.8,2]);box('brass_foot',[x,.65,z],[2.8,1.3,2.8],1);box('leg_collar',[x,9.3,z],[2.3,.6,2.3],1)
for center,size,mat in [([0,10.5,0],[15,1.1,15],0),([0,11.2,0],[16,.38,16],1),([0,12,0],[15.8,1.2,15.8],0),([0,12.7,0],[14.8,.35,14.8],2)]:box('worktop',center,size,mat)
for z in (-7,7):box('top_inlay',[0,12.94,z],[14,.08,.14],1)
for x in (-7,7):box('top_inlay',[x,12.94,0],[.14,.08,14],1)
box('front_drawer',[0,9.1,-6.5],[8.8,1.35,.7],2);box('drawer_handle',[0,9.15,-7],[2.4,.32,.35],1)
for x in (-4.7,4.7):box('corner_seal',[x,11.2,-8.02],[1.4,1.5,.15],1,45)
for x in (-5,5):box('leg_brace',[x,3.4,0],[.7,.7,11.2],1)
box('rear_brace',[0,3.4,5.5],[10.5,.7,.7],1)

# A faceted offering core, suspended in two slender gold gimbals.
cx,cy,cz=2.8,20.1,2.4
box('core_plinth',[cx,13.6,cz],[4.8,1.3,4.8]);box('core_gold_base',[cx,14.35,cz],[4.2,.25,4.2],1)
for dx in (-2.2,2.2):box('gimbal_support',[cx+dx,16.1,cz],[.4,3.3,.45],1)
for ring in range(2):
    radius=4.1-ring*.6
    for i in range(32):
        a=i*math.tau/32
        if ring==0:box('gold_meridian',[cx+math.cos(a)*radius,cy+math.sin(a)*radius,cz],[.27,.88,.27],1,-math.degrees(a))
        else:box('gold_equator',[cx+math.cos(a)*radius,cy,cz+math.sin(a)*radius],[.74,.22,.22],1,yaw=math.degrees(a)+90)
box('dark_crystal',[cx,cy,cz],[2.25,4.4,2.25],0,25,yaw=45)
box('violet_heart',[cx,cy,cz-1.17],[.6,2.7,.12],4,25)
for yy in (-2.15,2.15):box('crystal_tip',[cx+yy*.35,cy+yy,cz],[.8,.8,.8],4,45,yaw=45)

# Reagent bottles and a stoppered retort use broad facets, not painted bottle silhouettes.
for x,z,height,mat in [(-4.9,3.5,3.3,5),(-5.4,-.1,2.6,6),(-2.7,2.8,2.7,4)]:
    box('reagent_bottle',[x,13.2+height/2,z],[1.9,height,1.9],mat)
    box('bottle_shoulder',[x,13.2+height,z],[1.3,.65,1.3],3)
    box('bottle_neck',[x,13.85+height,z],[.75,.7,.75],3)
    box('bottle_stop',[x,14.3+height,z],[1,.35,1],1)
    box('bottle_label',[x,13.4+height/2,z-1],[1,.9,.05],7)
box('retort_pipe',[-4.9,18.4,3.5],[.4,2.7,.4],1)
box('retort_bridge',[-2.25,19.6,3.5],[5.6,.35,.35],1)
box('retort_elbow',[.35,19.05,3.5],[.35,1.4,.35],1)

# An open ledger and stylus make the front usable and readable from the player side.
for x,roll in [(-2,-6),(.4,6)]:
    box('ledger_cover',[x,13.15,-3.6],[2.55,.2,3.6],8,roll)
    box('ledger_pages',[x,13.36,-3.6],[2.4,.24,3.3],7,roll)
    for line in range(4):box('ink_line',[x,13.51,-4.65+line*.55],[1.65,.035,.075],9,roll)
box('ledger_spine',[-.8,13.35,-3.6],[.25,.32,3.7],1)
box('brass_stylus',[3.8,13.12,-4.4],[.18,.18,3.5],1,yaw=25)
for x in (-6.5,6.5):
 for z in (-6.5,6.5):box('corner_stud',[x,13.08,z],[.6,.35,.6],1,45)

geo={'format_version':'1.12.0','minecraft:geometry':[{'description':{'identifier':'geometry.alchemist_table','texture_width':512,'texture_height':512,'visible_bounds_width':2,'visible_bounds_height':3,'visible_bounds_offset':[0,.8,0]},'bones':bones}]}
assert len(parts)<200
write(ASSET/'geo/block/alchemist_table.geo.json',geo);write(HOME/'export/alchemist_table.geo.json',geo)
write(HOME/'source/model-plan.json',{'parts':parts,'palette':palette,'cubes':len(parts),'source':'Original local Blender build; no cloud generation','appearance':'Obsidian workbench, brass armillary offering core, reagent bottles and open ledger'})
for source in (base,mask):
    p=Path(source.filepath_raw);target=ASSET/'textures/block'/p.name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(p.read_bytes());(HOME/'export'/p.name).write_bytes(p.read_bytes())
write(ASSET/'animations/block/alchemist_table.animation.json',{'format_version':'1.8.0','animations':{}})
write(ASSET/'blockstates/alchemist_table.json',{'variants':{f'facing={direction}':{'model':'sourceofmystery:block/alchemist_table'} for direction in ('north','south','east','west')}})
write(ASSET/'models/block/alchemist_table.json',{'textures':{'particle':'sourceofmystery:block/alchemist_table'}})
write(ASSET/'models/item/alchemist_table.json',{'parent':'builtin/entity','gui_light':'side','display':{
    'gui':{'rotation':[23,225,0],'translation':[0,-3,0],'scale':[.53]*3},'ground':{'translation':[0,1,0],'scale':[.3]*3},
    'fixed':{'scale':[.5]*3},'firstperson_righthand':{'rotation':[0,30,0],'translation':[1,-1,-1],'scale':[.4]*3},
    'firstperson_lefthand':{'rotation':[0,30,0],'translation':[1,-1,-1],'scale':[.4]*3},
    'thirdperson_righthand':{'translation':[0,1,0],'scale':[.4]*3},'thirdperson_lefthand':{'translation':[0,1,0],'scale':[.4]*3}}})
scene=bpy.context.scene;scene.render.engine='CYCLES';scene.cycles.samples=32
scene.world.color=(.16,.16,.16);scene.render.resolution_x=900;scene.render.resolution_y=900;scene.render.resolution_percentage=100
def aim(obj,target):obj.rotation_euler=(Vector(target)-obj.location).to_track_quat('-Z','Y').to_euler()
bpy.ops.object.camera_add(location=(2.1,-2.7,2));camera=bpy.context.object;aim(camera,(0,0,.7));camera.data.type='ORTHO';camera.data.ortho_scale=2.25;scene.camera=camera
for pos,power,size in [((1.2,-2.2,3),450,3),((-2,1,2.3),300,2.5),((1.7,2,2.6),400,2)]:
    bpy.ops.object.light_add(type='AREA',location=pos);light=bpy.context.object;light.data.energy=power;light.data.shape='DISK';light.data.size=size;aim(light,(0,0,.7))
bpy.ops.wm.save_as_mainfile(filepath=str(HOME/'blender/alchemist-table.blend'))
bpy.ops.export_scene.gltf(filepath=str(HOME/'export/alchemist-table-source.glb'),export_format='GLB',use_selection=False)
scene.render.filepath=str(HOME/'export/alchemist-table-preview.png');bpy.ops.render.render(write_still=True)
print('ALCHEMIST_TABLE_ASSET_OK cubes='+str(len(parts)))
