"""Authored ancient-script inspired five-element glyphs, on transparent GeckoLib mesh cards.

The glyph outlines are original vector artwork, informed by CUHK's oracle/bronze script
references. They are artistic reconstructions, not facsimiles of a specific inscription.
No downloaded fonts or images, sphere geometry, or inner-star changes are used.
"""
from pathlib import Path
import base64, hashlib, json, math, struct, uuid
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT=Path(__file__).resolve().parents[2]
A=ROOT/'src/main/resources/assets/sourceofmystery'
ART=ROOT/'art/divine_satellites';ART.mkdir(parents=True,exist_ok=True)
COLORS={'wood':(44,220,130),'metal':(247,214,104),'water':(62,159,255),'fire':(255,77,48),'earth':(240,156,55)}
CARD_SIZE=.465  # 75% of the previous .62 m glyph proposal, per the user's size adjustment.
CHARS=dict(zip(COLORS,'木金水火土'))
# Stroke centre lines on a 512-square authoring canvas. Deliberate asymmetry avoids a modern typeface.
STROKES={
 'wood':[
  (17,[(255,87),(252,173),(258,253),(253,337),(256,422)]),
  (17,[(255,196),(184,166),(125,112)]),(17,[(255,196),(330,160),(390,111)]),
  (16,[(257,259),(185,235),(135,203)]),(16,[(257,259),(327,231),(384,199)]),
  (17,[(254,333),(206,374),(144,408)]),(17,[(254,333),(307,374),(365,409)])],
 'metal':[
  (17,[(124,207),(187,162),(255,100),(323,161),(387,204)]),
  (16,[(181,233),(255,230),(331,232)]),(17,[(255,230),(253,311),(256,396)]),
  (16,[(171,311),(255,309),(341,311)]),(18,[(137,407),(255,403),(376,406)]),
  (18,[(149,263),(177,280)]),(18,[(361,263),(334,281)]),
  (18,[(163,350),(185,366)]),(18,[(348,348),(328,367)])],
 'water':[
  (18,[(268,85),(242,151),(270,218),(243,284),(269,352),(245,426)]),
  (17,[(137,138),(172,180),(142,225)]),(17,[(375,140),(341,181),(371,225)]),
  (17,[(140,284),(175,328),(139,377)]),(17,[(373,282),(338,329),(371,374)])],
 'fire':[
  (17,[(263,87),(234,160),(265,222),(250,287)]),
  (18,[(131,183),(164,235),(159,281),(192,319)]),
  (18,[(384,180),(350,237),(355,280),(321,319)]),
  (18,[(252,268),(229,329),(199,372),(144,418)]),
  (18,[(252,280),(278,336),(313,376),(370,414)])],
 'earth':[
  (19,[(155,299),(179,222),(216,178),(256,137),(297,179),(333,227),(353,298)]),
  (18,[(255,161),(257,259),(253,364)]),
  (18,[(158,300),(255,297),(352,299)]),
  (20,[(116,405),(254,400),(396,404)])]
}

def write(p,data):
 p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf8')

def outline(points,width):
 # Catmull-Rom curves with subtly tapered, carved ends; one exact outline serves SVG and PNG.
 ps=[np.array(p,dtype=float) for p in points];curve=[]
 for i in range(len(ps)-1):
  p0=ps[max(0,i-1)];p1=ps[i];p2=ps[i+1];p3=ps[min(len(ps)-1,i+2)]
  for t in np.linspace(0,1,32,endpoint=False):
   curve.append(.5*((2*p1)+(-p0+p2)*t+(2*p0-5*p1+4*p2-p3)*t*t+(-p0+3*p1-3*p2+p3)*t*t*t))
 curve.append(ps[-1]);left=[];right=[]
 for i,p in enumerate(curve):
  tangent=curve[min(i+1,len(curve)-1)]-curve[max(i-1,0)];tangent/=max(np.linalg.norm(tangent),1e-9)
  normal=np.array([-tangent[1],tangent[0]])
  t=i/(len(curve)-1);half=width*.5*(.65+.35*math.sin(math.pi*t)**.45)
  left.append(p+normal*half);right.append(p-normal*half)
 return [tuple(p) for p in left+right[::-1]]

def texture(name):
 size=512;scale=4
 mask=Image.new('L',(size*scale,size*scale));draw=ImageDraw.Draw(mask);polygons=[]
 for width,points in STROKES[name]:
  polygon=outline(points,width);polygons.append(polygon)
  draw.polygon([(round(x*scale),round(y*scale)) for x,y in polygon],fill=255)
 mask=mask.resize((size,size),Image.Resampling.LANCZOS)
 stroke=np.asarray(mask,dtype=float)/255
 halo=np.asarray(mask.filter(ImageFilter.GaussianBlur(3.0)),dtype=float)/255*.45
 alpha=np.maximum(stroke,halo)
 # RGB remains colored in transparent padding, avoiding dark fringes with linear sampling.
 color=np.array(COLORS[name],float)
 y,x=np.mgrid[:size,:size];sheen=.86+.14*np.sin((x+y)*.009)**2
 rgb=np.clip(color[None,None,:]*sheen[...,None]+stroke[...,None]*np.array([10,9,5]),0,255)
 image=Image.fromarray(np.uint8(np.dstack([rgb,alpha*255])),'RGBA')
 path=A/f'textures/effects/divine_satellites/{name}.png';path.parent.mkdir(parents=True,exist_ok=True);image.save(path)
 write(path.with_suffix('.png.mcmeta'),{'texture':{'blur':True,'clamp':True}})
 hexcolor='#'+''.join(f'{c:02x}' for c in COLORS[name])
 svg=['<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 512 512">',f'<title>{CHARS[name]} — ancient-script inspired elemental glyph</title>']
 for polygon in polygons:
  svg.append('<polygon fill="'+hexcolor+'" points="'+' '.join(f'{x:.3f},{y:.3f}' for x,y in polygon)+'"/>')
 svg.append('</svg>');(ART/f'glyph-{name}.svg').write_text('\n'.join(svg)+'\n',encoding='utf8')
 return path,image

def geometry(texture_path):
 # Two true triangles, transparent outside the strokes. +Y is up, front is +Z.
 half=CARD_SIZE/2
 p=[(-half,-half,0,0,0,1,0,1),(half,-half,0,0,0,1,1,1),(half,half,0,0,0,1,1,0),(-half,half,0,0,0,1,0,0)]
 triangles=[[p[0],p[1],p[2]],[p[0],p[2],p[3]]]
 name='divine_satellite_glyph';path=A/f'geo/effects/{name}.mesh';path.parent.mkdir(parents=True,exist_ok=True)
 with path.open('wb') as f:
  f.write(struct.pack('>iii',0x534f4d54,1,6))
  for tri in triangles:
   for v in tri:f.write(struct.pack('>8f',*v))
 write(A/f'geo/effects/{name}.geo.json',{'format_version':'1.12.0','minecraft:geometry':[{'description':{'identifier':'geometry.'+name,'texture_width':512,'texture_height':512,'visible_bounds_width':1,'visible_bounds_height':1,'visible_bounds_offset':[0,0,0]},'bones':[{'name':'root','pivot':[0,0,0],'cubes':[]}]}]})
 vertices={f'v{i}':[c*16 for c in v[:3]] for i,v in enumerate(p)}
 uv={f'v{i}':[v[6]*512,v[7]*512] for i,v in enumerate(p)}
 write(ART/(name+'.bbmodel'),{'meta':{'format_version':'4.10','model_format':'free','box_uv':False},'name':name,'resolution':{'width':512,'height':512},
  'elements':[{'name':'glyph_card','type':'mesh','uuid':str(uuid.uuid5(uuid.NAMESPACE_URL,name)),'origin':[0,0,0],'vertices':vertices,
    'faces':{'front':{'vertices':['v0','v1','v2','v3'],'uv':uv,'texture':0}}}],
  'textures':[{'name':texture_path.name,'id':'0','uuid':str(uuid.uuid5(uuid.NAMESPACE_URL,name+'/texture')),'source':'data:image/png;base64,'+base64.b64encode(texture_path.read_bytes()).decode(),'uv_width':512,'uv_height':512}]})
 (ART/(name+'.obj')).write_text('\n'.join(['# Transparent, camera-facing glyph card; units meters',*[f'v {v[0]} {v[1]} {v[2]}' for v in p],*[f'vt {v[6]} {1-v[7]}' for v in p],'f 1/1 2/2 3/3','f 1/1 3/3 4/4'])+'\n',encoding='utf8')

def main():
 images=[];wood_path=None
 for name in COLORS:
  path,image=texture(name);images.append((name,image));wood_path=wood_path or path
 geometry(wood_path)
 # Review sheet is generated from the same vector strokes as the in-game albedo maps.
 sheet=Image.new('RGBA',(1500,365),(20,24,33,255));labels=ImageDraw.Draw(sheet)
 font=ImageFont.truetype('C:/Windows/Fonts/simkai.ttf',30)
 for i,(name,image) in enumerate(images):
  sheet.alpha_composite(image.resize((300,300),Image.Resampling.LANCZOS),(i*300,0))
  labels.text((i*300+150,320),CHARS[name],font=font,fill=(*COLORS[name],255),anchor='mm')
 sheet.convert('RGB').save(ART/'glyphs-overview.png')
 plan={'scope':'Outer five satellites only; sphere version rejected by user; inner black/white stars retained byte-identically',
  'target':'Forge 1.20.1 / GeckoLib 4.8.4 desktop','representation':'transparent albedo glyph cards managed by GeckoLib; no sphere or backing plate',
  'glyphs':{n:{'character':CHARS[n],'color_rgb':COLORS[n],'svg':f'glyph-{n}.svg'} for n in COLORS},
  'historical_status':'Original artistic reconstructions inspired by oracle and bronze script; not exact facsimiles or one historical font',
  'references':{c:'https://humanum.arts.cuhk.edu.hk/Lexis/lexi-mf/search.php?word='+c for c in CHARS.values()},
  'model_triangles_per_outer_satellite':2,'outer_ring_triangles':10,'texture_size':[512,512],'card_size_meters':[CARD_SIZE,CARD_SIZE],
  'size_relative_to_initial_glyph_proposal':.75,
  'texel_density_px_per_meter':512/CARD_SIZE,'uv':'one square artwork on one square card, upright and unmirrored',
  'shading':'albedo-preserving, depth-tested entity translucency with a colored soft alpha edge; no opaque background',
  'orientation':'camera-facing yaw and pitch; each observer sees the glyph front upright, never an edge or reversed lettering',
  'animation':'existing position interpolation, 16-tick fade-out and 24-tick fade-in unchanged',
  'combat':'unchanged authoritative server state; models can be invisible without stopping attacks'}
 write(ART/'asset-plan.json',plan)
 print(json.dumps(plan,ensure_ascii=False,indent=2))
if __name__=='__main__':main()
