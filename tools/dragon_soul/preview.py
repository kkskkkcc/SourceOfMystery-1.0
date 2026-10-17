import sys,json
from pathlib import Path
from PIL import Image,ImageDraw
sys.path.insert(0,str(Path('tools/weeping_death_lord').resolve()))
from geo_render import render
A=Path('src/main/resources/assets/sourceofmystery');g=json.loads((A/'geo/entity/dragon_soul.geo.json').read_text())['minecraft:geometry'][0];tex=Image.open(A/'textures/entity/dragon_soul.png');an=json.loads((A/'animations/entity/dragon_soul.animation.json').read_text())['animations']
def sample(name,t):
 p={}
 for n,b in an[name]['bones'].items():
  p[n]={}
  for ch,track in b.items():
   if not isinstance(track,dict):v=track
   else:
    k=min(track,key=lambda k:abs(float(k)-t));v=track[k];v=v.get('post',v) if isinstance(v,dict) else v
   p[n][{'rotation':'rot','position':'pos','scale':'scale'}[ch]]=v
 return p
sheet=Image.new('RGBA',(1800,1200),(40,40,48,255));d=ImageDraw.Draw(sheet)
for i,(name,t) in enumerate([('boss_stance',0),('boss_cleave',.85),('boss_thrust',.7),('boss_intro_dive',1.1),('boss_intro_point',.7),('boss_intro_point',5.2)]):
 im,_,_=render(g,tex,sample(name,t),yaw=-20,size=600);sheet.paste(im,(i%3*600,i//3*600));d.text((i%3*600+15,i//3*600+15),name+' '+str(t),fill='white')
sheet.save('build/dragon_soul_poses.png')
