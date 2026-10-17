import cv2
from pathlib import Path
from PIL import Image,ImageDraw
p=Path('build/dragon_video');p.mkdir(parents=True,exist_ok=True)
c=cv2.VideoCapture(r'E:\36107914408-1-192.mp4');fps=c.get(cv2.CAP_PROP_FPS);count=c.get(cv2.CAP_PROP_FRAME_COUNT);duration=count/fps
print({'fps':fps,'frames':count,'seconds':duration,'width':c.get(3),'height':c.get(4)})
frames=[]
for i in range(24):
 t=duration*i/24;c.set(cv2.CAP_PROP_POS_MSEC,t*1000);ok,f=c.read()
 if ok:
  im=Image.fromarray(cv2.cvtColor(f,cv2.COLOR_BGR2RGB));im.save(p/f'{t:06.2f}.png');im.thumbnail((400,250));frames.append((t,im))
sheet=Image.new('RGB',(1600,6*275),(32,32,38));d=ImageDraw.Draw(sheet)
for i,(t,im) in enumerate(frames):x=i%4*400;y=i//4*275;sheet.paste(im,(x,y+22));d.text((x+8,y+4),f'{t:.2f}s',fill='white')
sheet.save(p/'contact.png');c.release()
