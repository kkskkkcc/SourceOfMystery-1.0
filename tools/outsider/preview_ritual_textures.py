from PIL import Image,ImageEnhance
from pathlib import Path
root=Path('src/main/resources/assets/sourceofmystery/textures/entity/outsider')
out=Path('build/ritual-reference-preview.png')
bg=Image.new('RGBA',(1200,1200),(8,5,18,255))
# Enlarged layers, counter-rotated like the in-game stack.
base=Image.open(root/'ritual_circle_base.png').convert('RGBA').resize((1000,1000),Image.Resampling.LANCZOS).rotate(0,Image.Resampling.BICUBIC,expand=False)
inner=Image.open(root/'ritual_circle_inner.png').convert('RGBA').resize((780,780),Image.Resampling.LANCZOS).rotate(18,Image.Resampling.BICUBIC,expand=False)
sigil=Image.open(root/'ritual_circle_sigil.png').convert('RGBA').resize((560,560),Image.Resampling.LANCZOS).rotate(-27,Image.Resampling.BICUBIC,expand=False)
for layer in [base,inner,sigil]:
 x=(1200-layer.width)//2;y=(1200-layer.height)//2
 bg.alpha_composite(layer,(x,y))
# soft center bloom to make the emissive layering obvious
bg.convert('RGB').save(out)
print(out, out.stat().st_size)
