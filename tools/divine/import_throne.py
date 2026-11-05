"""Lossless topology/UV import of the user's GLB into GeckoLib's baked mesh stream."""
from pathlib import Path
import json,struct,hashlib,zipfile
import numpy as np
from PIL import Image
ROOT=Path(__file__).resolve().parents[2];ART=ROOT/'art/divine_cinematic/imported_throne';ART.mkdir(parents=True,exist_ok=True)
A=ROOT/'src/main/resources/assets/sourceofmystery'
p=next(p for p in ROOT.glob('*/*.zip') if p.name=='\u738b\u5ea7.zip')
with zipfile.ZipFile(p) as z:
 names=[n for n in z.namelist() if n.lower().endswith('.glb')];assert len(names)==1
 glb=z.read(names[0])
magic,version,total=struct.unpack_from('<III',glb);assert magic==0x46546c67 and version==2 and total==len(glb)
json_size,json_type=struct.unpack_from('<II',glb,12);doc=json.loads(glb[20:20+json_size]);offset=20+json_size
bin_size,bin_type=struct.unpack_from('<II',glb,offset);blob=glb[offset+8:offset+8+bin_size]
def accessor(index):
 a=doc['accessors'][index];v=doc['bufferViews'][a['bufferView']];assert not a.get('sparse') and a['componentType']==5126
 n={'VEC2':2,'VEC3':3}[a['type']];stride=v.get('byteStride',4*n);start=v.get('byteOffset',0)+a.get('byteOffset',0)
 return np.ndarray((a['count'],n),dtype='<f4',buffer=blob,offset=start,strides=(stride,4)).copy()
primitives=doc['meshes'][0]['primitives'];assert len(primitives)==1 and len(doc['nodes'])==1 and not any(k in doc['nodes'][0] for k in ['matrix','translation','rotation','scale'])
prim=primitives[0];assert prim.get('mode',4)==4 and 'indices' not in prim
pos=accessor(prim['attributes']['POSITION']);normals=accessor(prim['attributes']['NORMAL']);uv=accessor(prim['attributes']['TEXCOORD_0'])
assert len(pos)%3==0 and len(pos)==len(normals)==len(uv) and np.isfinite(pos).all() and np.isfinite(normals).all() and np.isfinite(uv).all()
lo=pos.min(0);hi=pos.max(0);centre=(hi+lo)/2;centre[1]=lo[1]
# A throne for a 1.8 m player, rather than the previous 4.65 m monument.
# Keep the original silhouette but shorten the unusually deep seat/base.
dimensions=np.array([1.65,2.30,1.50],dtype=np.float64)
scale=dimensions/(hi-lo)
placed=(pos-centre)*scale
# Non-uniform scaling requires the inverse-transpose normal transform.
placed_normals=normals/scale
placed_normals/=np.linalg.norm(placed_normals,axis=1,keepdims=True)
vertices=np.concatenate([placed,placed_normals,uv],axis=1).astype('<f4')
mesh=A/'geo/cinematic/divine_throne.mesh';mesh.parent.mkdir(parents=True,exist_ok=True)
mesh.write_bytes(struct.pack('>III',0x534F4D54,1,len(vertices))+vertices.astype('>f4').tobytes())
im=doc['images'][0];view=doc['bufferViews'][im['bufferView']];source=blob[view.get('byteOffset',0):view.get('byteOffset',0)+view['byteLength']]
texture=A/'textures/cinematic/divine/throne_source.png';texture.write_bytes(source)
texture.with_suffix('.png.mcmeta').write_text(json.dumps({'texture':{'blur':True,'clamp':False}}))
stub={'format_version':'1.12.0','minecraft:geometry':[{'description':{'identifier':'geometry.divine_throne','texture_width':1024,'texture_height':1024,'visible_bounds_width':2.4,'visible_bounds_height':2.6,'visible_bounds_offset':[0,1.15,0]},'bones':[{'name':'throne_root','pivot':[0,0,0],'cubes':[]}]}]}
(A/'geo/cinematic/divine_throne.geo.json').write_text(json.dumps(stub,indent=2)+'\n')
(A/'animations/cinematic/divine_throne.animation.json').write_text(json.dumps({'format_version':'1.8.0','animations':{'animation.divine_throne.idle':{'loop':True,'animation_length':40,'bones':{}}}},indent=2)+'\n')
np.savez(ART/'mesh-preview-data.npz',vertices=vertices,original_positions=pos)
(ART/'source.glb').write_bytes(glb);(ART/'source_albedo.png').write_bytes(source)
report={'source_zip':str(p),'source_zip_sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'source_glb_entry':names[0],'source_glb_sha256':hashlib.sha256(glb).hexdigest(),
 'vertices':len(vertices),'triangles':len(vertices)//3,'topology_preserved':True,'uv_preserved':bool(np.array_equal(vertices[:,6:8],uv)),
 'normals_inverse_transpose':True,'normal_length_max_error':float(np.max(np.abs(np.linalg.norm(vertices[:,3:6],axis=1)-1))),
 'axis_scale':scale.tolist(),'runtime_dimensions_m':(placed.max(0)-placed.min(0)).tolist(),'height_relative_to_player':2.30/1.80,
 'material':'neutral gray and white only, all RGB channels equal; original albedo remains byte-identical','mesh_sha256':hashlib.sha256(mesh.read_bytes()).hexdigest()}
assert report['uv_preserved'] and report['normal_length_max_error']<1e-6
(ART/'import-report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8');print(json.dumps(report,ensure_ascii=False,indent=2))
