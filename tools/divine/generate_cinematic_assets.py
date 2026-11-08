"""Deterministic original cosmic-stage textures and non-clipping cinematic sound cues."""
from pathlib import Path
import json,sys,math,wave,subprocess
import numpy as np
from PIL import Image,ImageDraw,ImageFilter
from scipy.ndimage import gaussian_filter
from scipy.signal import butter,sosfilt

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'build/outsider-audio-deps'))
sys.path.insert(0,str(ROOT/'build/offering-preview-deps'))
import imageio_ffmpeg
FFMPEG=imageio_ffmpeg.get_ffmpeg_exe()
ASSETS=ROOT/'src/main/resources/assets/sourceofmystery'
TEX=ASSETS/'textures/cinematic/divine';SND=ASSETS/'sounds/divine_cg'
TEX.mkdir(parents=True,exist_ok=True);SND.mkdir(parents=True,exist_ok=True)
ART=ROOT/'art/divine_cinematic';ART.mkdir(parents=True,exist_ok=True)
rng=np.random.default_rng(7491105)

def noise(w,h,scale):
    a=Image.fromarray((rng.random((max(3,h//scale),max(3,w//scale)))*255).astype('uint8'))
    return np.asarray(a.resize((w,h),Image.Resampling.BICUBIC),dtype=float)/255
def save(name,a):Image.fromarray(np.uint8(np.clip(a,0,255))).convert('RGBA').save(TEX/(name+'.png'))

def textures():
    w,h=1536,768;y,x=np.mgrid[0:h,0:w];u=x/w;v=y/h
    n=noise(w,h,90)*.56+noise(w,h,32)*.29+noise(w,h,9)*.15
    band=np.exp(-((v-.44-.11*np.sin(u*6.28*1.6))/.13)**2)*n**2
    space=np.zeros((h,w,3))+[1.5,2,5]
    space+=band[...,None]*np.array([13,20,33])
    img=Image.fromarray(space.astype('uint8'));d=ImageDraw.Draw(img)
    for i in range(3100):
        sx,sy=rng.integers(w),rng.integers(h);bright=int(rng.uniform(50,195));rad=1 if i%42==0 else 0
        d.ellipse((sx-rad,sy-rad,sx+rad,sy+rad),fill=(bright,int(bright*.94),int(bright*.88)))
        if i%170==0:
            d.line((sx-3,sy,sx+3,sy),fill=(bright//3,bright//3,bright//2));d.line((sx,sy-3,sx,sy+3),fill=(bright//3,bright//3,bright//2))
    img.convert('RGBA').save(TEX/'space.png')
    nasa=ART/'NASA/bluemarble-2048.png';nasa.parent.mkdir(parents=True,exist_ok=True)
    if not nasa.exists():
        from urllib.request import urlopen
        url='https://svs.gsfc.nasa.gov/vis/a000000/a002900/a002915/bluemarble-2048.png'
        with urlopen(url,timeout=45) as response:nasa.write_bytes(response.read())
    earth=Image.open(nasa).convert('RGBA');assert earth.size==(2048,1024)
    earth.save(TEX/'earth.png')
    n=noise(128,128,22)*.6+noise(128,128,4)*.4;y,x=np.mgrid[0:128,0:128]
    vein=np.exp(-((np.sin(x*.043+y*.065+n*4))/.085)**2)
    stone=np.zeros((128,128,3))+[209,216,228];stone+=n[...,None]*[22,20,17]+vein[...,None]*[12,12,12]
    save('chaos_stone',stone)
    metal=np.zeros((128,128,3))+[170,165,151];metal+=noise(128,128,5)[...,None]*[35,32,28]
    metal+=np.sin(y*.15)[...,None]*[6,6,6];save('metal',metal)
    Image.new('RGBA',(4,4),'white').save(TEX/'white.png')
    size=1024;im=Image.new('RGBA',(size,size));d=ImageDraw.Draw(im);cx=cy=size/2
    for radius,width,alpha in [(470,2,150),(454,3,175),(418,1,95),(382,2,135),(281,2,130),(267,1,100)]:
        d.ellipse((cx-radius,cy-radius,cx+radius,cy+radius),outline=(235,223,191,alpha),width=width)
    def point(a,r):return (cx+math.cos(a)*r,cy+math.sin(a)*r)
    for i in range(64):
        a=i*math.tau/64;d.line([point(a,422),point(a,450 if i%4==0 else 435)],fill=(240,226,187,140),width=2)
    for i in range(12):
        a=i*math.tau/12
        d.line([point(a,286),point(a+.24,380),point(a+.48,286)],fill=(229,224,205,105),width=2)
        d.line([point(a,310),point(a,365)],fill=(240,231,208,120),width=2)
    for i in range(8):
        a=i*math.tau/8;c=point(a,345)
        d.regular_polygon((c,13),4,rotation=i*45,outline=(246,235,201,140),width=2)
    im.save(TEX/'mandala.png')
    for name in ['space','earth','chaos_stone','metal','mandala','white']:
        (TEX/(name+'.png.mcmeta')).write_text(json.dumps({'texture':{'blur':True,'clamp':False}}))
    # Seamless multi-scale cloud density, animated in two lens-depth layers.
    cloud_rng=np.random.default_rng(3815906);field=np.zeros((512,512))
    for sigma,weight in [(52,.44),(23,.30),(9,.18),(3,.08)]:
        layer=gaussian_filter(cloud_rng.random((512,512)),sigma,mode='wrap')
        layer=(layer-layer.mean())/layer.std();field+=layer*weight
    density=np.clip((field+1.6)/3.4,0,1);luma=160+92*density**.7
    save('ascent_clouds',np.repeat(luma[...,None],3,axis=2))
    (TEX/'ascent_clouds.png.mcmeta').write_text(json.dumps({'texture':{'blur':True,'clamp':False}}))

RATE=44100
clips={}
def filtered(x,cut,kind='lowpass'):return sosfilt(butter(2,cut,btype=kind,fs=RATE,output='sos'),x)
def sound(name,seconds,make,peak=.65):
    t=np.arange(round(seconds*RATE))/RATE;x=make(t)
    if x.ndim==1:x=np.stack([x,x],1)
    # Short stereo late reflections create space without a harsh electronic tail.
    for delay,gain in [(.067,.12),(.143,.085),(.277,.055)]:
        n=round(delay*RATE);x[n:,0]+=x[:-n,1]*gain;x[n:,1]+=x[:-n,0]*gain
    envelope=np.minimum(t/.012,1)*np.minimum((seconds-t)/min(.35,seconds/4),1)
    x*=envelope[:,None];x*=peak/max(np.max(np.abs(x)),1e-8)
    raw=ART/(name+'.wav');write_wav(raw,x)
    subprocess.run([FFMPEG,'-y','-v','error','-i',str(raw),'-c:a','libvorbis','-q:a','5',str(SND/(name+'.ogg'))],check=True)
    out=subprocess.run([FFMPEG,'-v','error','-i',str(SND/(name+'.ogg')),'-f','f32le','-'],check=True,capture_output=True).stdout
    decoded=np.frombuffer(out,dtype='<f4').reshape(-1,2)
    assert np.isfinite(decoded).all() and np.max(np.abs(decoded))<.98
    clips[name]=decoded
    return {'seconds':seconds,'peak':float(np.max(np.abs(decoded))),'rms':float(np.sqrt(np.mean(decoded**2))),'original_procedural_audio':True}
def write_wav(path,x):
    with wave.open(str(path),'wb') as f:
        f.setnchannels(2);f.setsampwidth(2);f.setframerate(RATE);f.writeframes((np.clip(x,-1,1)*32767).astype('<i2').tobytes())
def chord(t,notes,attack=.5,release=2):
    env=(1-np.exp(-t/attack))*np.exp(-t/release)
    a=sum((np.sin(2*np.pi*f*t+np.sin(t*.6)*.08)+.22*np.sin(2*np.pi*f*2*t)) for f in notes)/len(notes)
    return a*env
def hymn(t):
    # Original slow harmonic procession, with choir-formant harmonics and a soft organ bed.
    result=np.zeros_like(t)
    progression=[(0,12,[65.406,130.813,164.814,196,293.665]),(9,23,[87.307,130.813,174.614,220,261.626]),
                 (20,34,[55,110,164.814,220,261.626]),(31,42,[73.416,146.832,196,261.626,293.665]),
                 (39,52,[65.406,130.813,164.814,196,261.626,392]),(49,61,[65.406,130.813,196,261.626])]
    for start,end,notes in progression:
        mask=(t>=start)&(t<=end);local=t[mask]
        q=local-start;env=np.clip(q/3,0,1)*np.clip((end-local)/3,0,1)
        for j,f in enumerate(notes):
            tone=np.zeros_like(local)
            for h in range(1,13):
                hz=f*h
                formant=.25/(h**1.3)+.12*np.exp(-((hz-750)/230)**2)+.08*np.exp(-((hz-1150)/260)**2)
                tone+=np.sin(2*np.pi*hz*local+np.sin(local*4.8+j)*.016*h)*formant
            result[mask]+=tone*env/len(notes)
    for at in [10,15,24,39.15]:
        q=np.maximum(t-at,0);env=(t>=at)*np.exp(-q*1.5)
        result+=sum(np.sin(2*np.pi*f*q)*np.exp(-q*(.6+i*.4)) for i,f in enumerate([523.25,1046.5,1569.8,2210]))*env*.045
    return result*(np.clip(t/3,0,1))*np.clip((61-t)/4,0,1)

def sounds():
    report={}
    report['ascent']=sound('ascent',8.6,lambda t:filtered(rng.normal(size=len(t)),4000)*(.02+.22*(np.minimum(t/8,1)**3))+np.sin(2*np.pi*(38*t+9*t*t))*.13*(t/8)**2,.68)
    report['cosmos']=sound('cosmos',60.7,hymn,.54)
    report['stairs']=sound('stairs',4.8,lambda t:chord(t,[130.81,196,261.63,392],.4,3)+filtered(rng.normal(size=len(t)),600)*.25*np.exp(-t*2),.47)
    report['step']=sound('step',.62,lambda t:filtered(rng.normal(size=len(t)),2600)*np.exp(-t*38)*.65+np.sin(2*np.pi*(115*t+12*(1-np.exp(-t*30))))*np.exp(-t*22)*.8+filtered(rng.normal(size=len(t)),450)*np.exp(-((t-.09)/.035)**2)*.25,.58)
    report['touch']=sound('touch',1.8,lambda t:chord(t,[523.251,783.991,1046.5],.012,.7)*.7,.42)
    def bestowal(t):
        shine=chord(t,[130.81,196,261.626,329.628,392,523.251,659.255],.055,2.5)
        shimmer=sum(np.sin(2*np.pi*f*t)*np.exp(-t*(.8+i*.12))/(i+3) for i,f in enumerate([1046.5,1318.5,1568,2093]))
        low=np.sin(2*np.pi*(43*t+3*(1-np.exp(-t*8))))*np.exp(-t*2.9)*.35
        burst=filtered(rng.normal(size=len(t)),6500)*np.exp(-t*7)*.2
        return shine+shimmer*.35+low+burst
    report['bestowal']=sound('bestowal',4.6,bestowal,.86)
    report['armor']=sound('armor',1.1,lambda t:sum(np.sin(2*np.pi*f*t)*np.exp(-t*k) for f,k in [(633,9),(955,12),(1472,18),(2180,24)])*.2+filtered(rng.normal(size=len(t)),4500)*np.exp(-t*16)*.15,.48)
    report['throne']=sound('throne',6.0,lambda t:chord(t,[49,65.406,98,130.81],.5,3)*.7+np.sin(2*np.pi*32.703*t)*np.exp(-t*.7)*.2,.47)
    mapping=json.loads((ASSETS/'sounds.json').read_text(encoding='utf-8'))
    labels={'ascent':('升入天外','Ascending beyond the world'),'cosmos':('寰宇低鸣','The cosmos resonates'),'stairs':('天阶凝结','Celestial steps form'),'step':('踏上天阶','Footsteps upon the celestial stair'),'touch':('触及神甲','Touching the divine armor'),'bestowal':('神光降临','Divine light descends'),'armor':('神甲合身','The divine armor binds'),'throne':('凝视王座','Gazing upon the throne')}
    for name in report:mapping['divine_cg.'+name]={'subtitle':'subtitles.sourceofmystery.divine_cg.'+name,'sounds':[{'name':'sourceofmystery:divine_cg/'+name,'stream':name=='cosmos'}]}
    (ASSETS/'sounds.json').write_text(json.dumps(mapping,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    for lang,i in [('zh_cn',0),('en_us',1)]:
        p=ASSETS/'lang'/(lang+'.json');d=json.loads(p.read_text(encoding='utf-8'))
        for name,pair in labels.items():d['subtitles.sourceofmystery.divine_cg.'+name]=pair[i]
        p.write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    mix=np.zeros((round(68.7*RATE),2))
    for name,start,gain in [('ascent',0,1),('cosmos',8,.58),('stairs',18,.7),*[("step",24.4031+i*2,.74) for i in range(5)],('step',33.8923,.65),('touch',42.37097,.65),('bestowal',47.142857,1),('armor',49.571429,.65),('throne',59,.8)]:
        n=round(start*RATE);a=clips[name][:len(mix)-n];mix[n:n+len(a)]+=a*gain
    # End fade matches the picture and avoids cutting the ambient tail.
    fade=np.clip((68.7-np.arange(len(mix))/RATE)/.7,0,1);mix*=fade[:,None]
    assert np.max(np.abs(mix))<1,'Soundtrack clips'
    write_wav(ART/'divine-first-equip-soundtrack.wav',mix)
    (ROOT/'build/divine-cg-audio-checks.json').write_text(json.dumps(report,indent=2))
    print('Generated NASA Earth + 6 original textures, 8 original cues and full 68.7s soundtrack')

# Sounds are now generated by generate_cg_audio.py; sounds() is kept for reference only.
if __name__=='__main__':textures()
