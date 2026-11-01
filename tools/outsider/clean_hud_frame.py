from pathlib import Path
from PIL import Image
import numpy as np
from scipy import ndimage
p=Path('src/main/resources/assets/sourceofmystery/textures/gui/archmage_boss_frame.png')
im=np.array(Image.open(p).convert('RGBA'))
a=im[:,:,3]
mask=a>=20
labels,n=ndimage.label(mask,structure=np.ones((3,3)))
sizes=np.bincount(labels.ravel());sizes[0]=0
main=labels==int(sizes.argmax())
keep=ndimage.binary_dilation(main,iterations=2)
im[~keep,3]=0
Image.fromarray(im,'RGBA').save(p,optimize=True)
print('components',n,'kept_alpha',int((im[:,:,3]>0).sum()),'bytes',p.stat().st_size)
