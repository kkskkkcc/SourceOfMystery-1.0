"""Asset contracts for Dragon Soul's rig, synchronized attacks and landing."""
import json, math
from pathlib import Path
R=Path(__file__).resolve().parents[2];A=R/'src/main/resources/assets/sourceofmystery'
g=json.loads((A/'geo/entity/dragon_soul.geo.json').read_text())['minecraft:geometry'][0];bones={b['name']:b for b in g['bones']};an=json.loads((A/'animations/entity/dragon_soul.animation.json').read_text())['animations']
assert len(bones)==115 and sum(len(b.get('cubes',[])) for b in bones.values())==512
assert (g['description']['texture_width'],g['description']['texture_height'])==(512,576)
assert bones['fly_tips']['parent']=='fly' and bones['fly2_tips']['parent']=='fly2' and bones['Mouth']['parent']=='Head'
assert bones['DragonTailDetail']['parent']=='AllBody' and bones['HairFaceDetail']['parent']=='Hair'
assert bones['DragonTailMid']['parent']=='DragonTailDetail'
assert bones['DragonTailTip']['parent']=='DragonTailMid'
assert bones['DragonTailEnd']['parent']=='DragonTailTip'
assert bones['ChestArmorDetail']['parent']=='UpperBody'
assert bones['longspear']['parent']=='RightHand'
for b in bones.values():
 assert b.get('parent') is None or b['parent'] in bones,b['name']
 seen=set();n=b
 while n.get('parent'):
  assert n['name'] not in seen
  seen.add(n['name']);n=bones[n['parent']]
for name,a in an.items():
 for n,channels in a['bones'].items():
  assert n in bones,(name,n)
  for ch,track in channels.items():
   if isinstance(track,dict):
    for t,v in track.items():
     assert 0<=float(t)<=a['animation_length']+.0001,(name,t)
     v=v.get('post',v) if isinstance(v,dict) else v
     assert all(isinstance(x,(float,int)) and math.isfinite(x) for x in v),(name,n,ch)
# Final frame exactly hands over to stance, including the grounded root offset.
land=an['boss_intro_point']['bones'];stance=an['boss_stance']['bones']
for n in ['AllBody','UpperBody','RightArm','RightForeArm','longspear','RightLeg','LeftLeg','Root',
          'DragonTailDetail','DragonTailMid','DragonTailTip','DragonTailEnd']:
 for ch,tr in land[n].items():
  assert tr[max(tr,key=float)]==stance[n][ch][min(stance[n][ch],key=float)],(n,ch)
for name,hit in [('boss_cleave',.85),('boss_thrust',.7),('boss_slash',.55),('boss_cleave_fast',.25),('boss_thrust_fast',.25),('boss_slash_fast',.25)]:
 assert any(abs(float(t)-hit)<.013 for t in an[name]['bones']['RightArm']['rotation']),name
pg=json.loads((A/'geo/entity/dragon_spear.geo.json').read_text())['minecraft:geometry'][0]['bones'][0]['cubes'];held=bones['longspear']['cubes'];grip=bones['longspear']['pivot']
assert len(pg)==len(held)
for a,b in zip(held,pg):
 assert a['size']==b['size'] and a['uv']==b['uv']
 assert all(abs(float(a['origin'][i])-float(b['origin'][i])-float(grip[i]))<1e-5 for i in range(3))
 if 'pivot' in a or 'pivot' in b:
  assert 'pivot' in a and 'pivot' in b
  assert all(abs(float(a['pivot'][i])-float(b['pivot'][i])-float(grip[i]))<1e-5 for i in range(3))
 assert a.get('rotation',[0,0,0])==b.get('rotation',[0,0,0])
print(f'PASS: {len(bones)} bones, {len(an)} clips; valid rig/tracks, hit timing, grounded handover, matching thrown weapon.')
