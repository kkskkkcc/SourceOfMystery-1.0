import json,sys
from pathlib import Path
from PIL import Image,ImageDraw
sys.path.insert(0,str(Path('tools/weeping_death_lord').resolve()));from geo_render import render
A=Path('src/main/resources/assets/sourceofmystery');g=json.loads((A/'geo/entity/dragon_soul.geo.json').read_text())['minecraft:geometry'][0];tex=Image.open(A/'textures/entity/dragon_soul.png')
sheet=Image.new('RGBA',(1800,600),(35,35,42,255));d=ImageDraw.Draw(sheet)
for i,yaw in enumerate([0,-45,180,45]):
 im,_,_=render(g,tex,{},yaw=yaw,size=600,center=[0,20,0],span=70);sheet.paste(im,(i*450,0));d.text((i*450+8,8),f'yaw {yaw}',fill='white')
sheet.save('build/dragon_soul_1ba_views.png')
