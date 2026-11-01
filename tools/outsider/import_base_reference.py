from PIL import Image,ImageFilter
from pathlib import Path
import numpy as np
src=Path(r'C:\Users\ADMINI~1\AppData\Local\Temp\codex-clipboard-eef815b0-04bc-42ac-b83e-c829a60a8467.png')
out=Path('src/main/resources/assets/sourceofmystery/textures/entity/outsider/ritual_circle_base_reference.png')
im=Image.open(src).convert('L')
a=np.asarray(im,dtype=np.float32)
# Keep the dark engraved/ornamental ink and mask the screenshot checkerboard
# by the circular reference silhouette.
yy,xx=np.mgrid[0:im.height,0:im.width];cx,cy=im.width/2,im.height/2
radius=np.sqrt((xx-cx)**2+(yy-cy)**2)
alpha=np.clip((190-a)*3.5,0,225)
alpha[radius>72]=0
alpha[alpha<18]=0
# resize into a square without stretching the circular seal
ink=Image.fromarray(alpha.astype('uint8'),'L')
canvas=Image.new('L',(180,180),0);canvas.paste(ink,(0,0));canvas=canvas.resize((1024,1024),Image.Resampling.LANCZOS)
arr=np.asarray(canvas).copy();arr[:10,:]=arr[-10:,:]=arr[:,:10]=arr[:,-10:]=0;canvas=Image.fromarray(arr,'L').filter(ImageFilter.UnsharpMask(radius=1.0,percent=180,threshold=3))
line=Image.new('RGBA',(1024,1024),(112,34,190,0));line.putalpha(canvas.point(lambda v:int(v*.90)))
glow=Image.new('RGBA',(1024,1024),(102,18,180,0));glow.putalpha(canvas.filter(ImageFilter.GaussianBlur(3)).point(lambda v:min(42,int(v*.18))))
Image.alpha_composite(glow,line).save(out,optimize=True)
print('alpha',canvas.getextrema(),sum(v>0 for v in canvas.getdata()),out.stat().st_size)
