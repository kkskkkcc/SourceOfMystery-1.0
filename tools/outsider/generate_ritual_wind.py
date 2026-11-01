"""Original seamless gale: broad air noise, moving bands, low pressure rumble."""
from pathlib import Path
import sys,json
import numpy as np
from scipy.signal import butter,sosfilt
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'build/outsider-audio-deps'))
import soundfile as sf

def main():
    rate=24000;duration=8;n=rate*duration;t=np.arange(n)/rate;rng=np.random.default_rng(199715)
    raw=rng.normal(0,1,n)
    body=sosfilt(butter(3,[110,2200],'bandpass',fs=rate,output='sos'),raw)
    rush=sosfilt(butter(3,[600,4900],'bandpass',fs=rate,output='sos'),rng.normal(0,1,n))
    low=sosfilt(butter(3,[40,190],'bandpass',fs=rate,output='sos'),rng.normal(0,1,n))
    # All modulation frequencies complete integer cycles over the loop.
    gust=.70+.18*np.sin(2*np.pi*t/4)+.12*np.sin(2*np.pi*t/1.6)
    x=(body*.8+rush*(.11+.05*np.sin(2*np.pi*t/2.0))+low*2.7)*gust
    # Circular crossfade makes the held loop continuous without a periodic click.
    overlap=rate//2
    blend=np.linspace(0,1,overlap,endpoint=False)
    x[:overlap]=x[-overlap:]*(1-blend)+x[:overlap]*blend
    x=x[:-overlap];x=np.tanh(x*.9);x*=.16/max(1e-8,float(np.sqrt(np.mean(x*x))))
    x*=min(1,.78/max(abs(x)))
    path=ROOT/'src/main/resources/assets/sourceofmystery/sounds/archmage/ritual_wind.ogg'
    sf.write(path,x,rate,format='OGG',subtype='VORBIS')
    decoded,sr=sf.read(path);assert sr==rate and decoded.ndim==1 and abs(decoded).max()<.95
    file=ROOT/'src/main/resources/assets/sourceofmystery/sounds.json'
    sounds=json.loads(file.read_text(encoding='utf8'))
    sounds['archmage.ritual_wind']={'subtitle':'subtitles.sourceofmystery.archmage.ritual_wind','sounds':[
        {'name':'sourceofmystery:archmage/ritual_wind','stream':False,'attenuation_distance':48}]}
    file.write_text(json.dumps(sounds,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    for language,text in [('zh_cn','域外魔导师：魔力狂风汇聚'),('en_us','Outer Archmage: Gathering arcane gale')]:
        file=ROOT/f'src/main/resources/assets/sourceofmystery/lang/{language}.json';data=json.loads(file.read_text(encoding='utf8'))
        data['subtitles.sourceofmystery.archmage.ritual_wind']=text
        file.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    print('RITUAL_WIND_OK',len(x)/rate,'seconds',float(abs(decoded).max()),'peak')

if __name__=='__main__':main()
