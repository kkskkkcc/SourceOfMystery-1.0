"""Original turbulence textures, noise-based wind loops and native GeckoLib staff keyframes."""
from pathlib import Path
import json
import math
import random
import shutil
import subprocess
import sys
import wave
import numpy as np
from PIL import Image

ROOT=Path(__file__).resolve().parents[2]
if not (ROOT/'build.gradle').exists():ROOT=Path('F:/神秘之源1.0/MCmodfind')
HOME=Path('F:/ChatGPT/我的世界神秘之源模组开发/modeling/staff_weather')
ASSETS=ROOT/'src/main/resources/assets/sourceofmystery'


def main():
    for name in ['source','reference','export','blender','lux3d_raw']:(HOME/name).mkdir(parents=True,exist_ok=True)
    out=ASSETS/'textures/effect/weather';out.mkdir(parents=True,exist_ok=True)
    rng=np.random.default_rng(57318);size=256
    noise=np.zeros((size,size),dtype=np.float32)
    for n,weight in [(4,.34),(8,.27),(16,.21),(32,.12),(64,.06)]:
        grid=(rng.random((n,n))*255).astype('uint8');layer=Image.fromarray(grid).resize((size,size),Image.Resampling.BICUBIC)
        noise+=np.asarray(layer).astype(np.float32)/255*weight
    yy,xx=np.mgrid[:size,:size];radius=np.sqrt(((xx-127.5)/127.5)**2+((yy-127.5)/127.5)**2)
    alpha=np.clip((1-radius)*3,0,1)**1.4*np.clip((noise-.16)*1.9,0,1)
    cloud=np.dstack([np.full_like(alpha,255)]*3+[alpha*255]).astype('uint8');Image.fromarray(cloud).save(out/'cloud.png')
    # Periodic Fourier turbulence avoids seams as the UVs stream around the funnel.
    sheet=np.zeros((size,size),dtype=np.float32)
    x=xx/size*math.tau;y=yy/size*math.tau
    for i in range(1,15):
        sheet+=np.sin(x*(i%5+1)+y*(i%3+1)+np.sin(y*2)*.55+i*2.399963)/i
    sheet=(sheet-sheet.min())/(sheet.max()-sheet.min());sheet=.20+.74*sheet
    Image.fromarray(np.dstack([np.full_like(sheet,255)]*3+[sheet*255]).astype('uint8')).save(out/'sheet.png')
    palettes={'normal':(69,79,86),'sand':(155,103,43),'water':(45,103,133),'fire':(99,37,16)}
    for name,rgb in palettes.items():
        for part in ['cloud','sheet']:
            image=Image.open(out/(part+'.png'));array=np.array(image)
            for channel,value in enumerate(rgb):array[:,:,channel]=value
            path=out/(name+'_'+part+'.png');Image.fromarray(array).save(path);shutil.copy2(path,HOME/'export'/path.name)
    sys.path.insert(0,str(ROOT/'build/offering-preview-deps'));import imageio_ffmpeg
    sample_rate=22050;n=sample_rate*8;t=np.arange(n)/sample_rate;f=np.fft.rfftfreq(n,1/sample_rate)
    spec=(rng.normal(size=len(f))+1j*rng.normal(size=len(f)))/np.maximum(f,18)**.56
    spec*=np.minimum(1,f/35)*np.exp(-f/6500);base=np.fft.irfft(spec,n);base/=max(np.abs(base))
    audio=ASSETS/'sounds/weather';audio.mkdir(parents=True,exist_ok=True)
    stats={}
    for i,name in enumerate(['normal','sand','water','fire']):
        gust=.75+.16*np.sin(math.tau*t/8)+.09*np.sin(math.tau*t*3/8+.5)
        rumble=.10*np.sin(math.tau*43*t+.3*np.sin(math.tau*t/8))+.06*np.sin(math.tau*69*t)
        extra=np.zeros(n)
        if name=='sand':extra=np.tanh(base*4)*.13*(.7+.3*np.sin(math.tau*t*23/8))
        if name=='water':extra=.13*np.sin(math.tau*170*t+12*np.sin(math.tau*t*3/8))*base+.10*np.sin(math.tau*t*11/8)*base
        if name=='fire':extra=np.maximum(0,base-.22)*.6+base*.06*np.sin(math.tau*110*t)
        signal=(base*.72*gust+rumble+extra);signal-=signal.mean();signal*=.83/max(np.abs(signal))
        # Endpoints taper over 10ms so the loop has no hard click.
        ramp=int(sample_rate*.01);signal[:ramp]*=np.linspace(0,1,ramp);signal[-ramp:]*=np.linspace(1,0,ramp)
        wav=HOME/'source'/('tornado-'+name+'.wav')
        with wave.open(str(wav),'wb') as output:output.setnchannels(1);output.setsampwidth(2);output.setframerate(sample_rate);output.writeframes((signal*32767).astype('<i2').tobytes())
        ogg=audio/(name+'.ogg');subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(),'-hide_banner','-loglevel','error','-y','-i',str(wav),'-c:a','libvorbis','-q:a','4',str(ogg)],check=True)
        stats[name]={'seconds':8,'sample_rate':sample_rate,'peak':float(np.max(np.abs(signal))),'rms':float(np.sqrt(np.mean(signal**2))),'bytes':ogg.stat().st_size}
        shutil.copy2(ogg,HOME/'export'/ogg.name)
    for name in ['cloud.png','sheet.png']:shutil.copy2(out/name,HOME/'export'/name)
    (HOME/'reference/audio-generation.json').write_text(json.dumps(stats,indent=2),encoding='utf-8')
    shutil.copy2(Path(__file__),HOME/'source/build_assets.py')
    print(json.dumps({'textures':10,'original_wind_loops':stats},indent=2))


if __name__=='__main__':main()
