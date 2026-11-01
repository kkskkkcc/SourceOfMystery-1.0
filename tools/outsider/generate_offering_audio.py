"""Edit recorded CC0 Foley and a designed portal effect into cinematic cues."""
from pathlib import Path
import json,sys
import numpy as np
from scipy.signal import butter,sosfiltfilt,resample_poly,resample,stft,istft
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'build/outsider-audio-deps'))
import soundfile as sf
RATE=44100
OUT=ROOT/'src/main/resources/assets/sourceofmystery/sounds/archmage'
SOURCES=ROOT/'art/outsider_void_boss/audio_sources'
GAINS={'heel_step':.90,'finger_snap':.95,'void_wake':.55,'void_break':.90,'void_seal':.70}

def load(name):
    x,sr=sf.read(SOURCES/name)
    if x.ndim>1:x=x.mean(axis=1)
    if sr!=RATE:
        from math import gcd
        g=gcd(sr,RATE);x=resample_poly(x,RATE//g,sr//g)
    return x

def band(x,lo,hi):return sosfiltfilt(butter(2,[lo,hi],btype='bandpass',fs=RATE,output='sos'),x)
def fade(x,attack=.002,release=.04):
    x=x.copy();n=len(x);a=min(n,round(attack*RATE));b=min(n,round(release*RATE))
    x[:a]*=np.linspace(0,1,a);x[-b:]*=np.linspace(1,0,b);return x

def clean_foley(x):
    _,_,z=stft(x,fs=RATE,nperseg=512,noverlap=384)
    floor=np.quantile(abs(z),.16,axis=1,keepdims=True)
    gain=np.maximum(.35,1-.8*floor/(abs(z)+1e-10))
    _,y=istft(z*gain,fs=RATE,nperseg=512,noverlap=384)
    return y[:len(x)]

def generate():
    report={};clips={}
    def save(name,x,peak,source):
        x=fade(x);x*=peak/max(np.max(abs(x)),1e-8)
        file=OUT/(name+'.ogg');sf.write(file,x,RATE,format='OGG',subtype='VORBIS')
        decoded,sr=sf.read(file);assert decoded.ndim==1 and np.isfinite(decoded).all() and max(abs(decoded))<.96
        clips[name]=decoded
        report[name]={'seconds':len(decoded)/sr,'peak':float(max(abs(decoded))),'rms':float(np.sqrt(np.mean(decoded**2))),'source':source,'license':'CC0-1.0'}
    heels=load('707566-heels-wood.mp3')
    for i,at in enumerate((.82,1.48,5.22),1):
        x=heels[round((at-.035)*RATE):round((at+.345)*RATE)]
        x=clean_foley(band(x,100,11000))
        save(f'heel_step_{i:02d}',fade(x,.001,.065),.68,'lenayrossouw / Freesound 707566')
    (OUT/'heel_step.ogg').write_bytes((OUT/'heel_step_01.ogg').read_bytes())
    snap=load('109400-finger-snap.mp3')[:round(.38*RATE)]
    save('finger_snap',fade(clean_foley(band(snap,130,13000)),.0005,.12),.72,'SoundCollectah / Freesound 109400')
    portal=band(load('617761-portal-open.mp3'),38,11500)
    wake=resample(portal[::-1],5*RATE);t=np.arange(len(wake))/RATE
    wake*=np.clip(t/1.1,0,1)*(.22+.78*(t/5)**1.4)
    save('void_wake',fade(wake,.25,.025),.52,'CogFireStudios / Freesound 617761; reversed pressure rise')
    rupture=portal[round(.915*RATE):]
    save('void_break',fade(rupture,.0015,.42),.82,'CogFireStudios / Freesound 617761; aligned rupture and decay')
    seal=np.zeros(round(1.35*RATE))
    inward=resample(portal[round(.10*RATE):round(1.02*RATE)][::-1],round(.67*RATE))
    inward=band(inward,100,6500)*np.linspace(.06,1,len(inward))**.7
    seal[:len(inward)]+=fade(inward,.04,.015)*.7
    tail=band(portal[round(.94*RATE):round(1.62*RATE)],45,1200)
    tail*=np.exp(-np.arange(len(tail))/RATE*5)
    start=round(.65*RATE);seal[start:start+len(tail)]+=fade(tail,.003,.15)*1.2
    save('void_seal',seal,.70,'CogFireStudios / Freesound 617761; inward edit and low closure')
    assets=ROOT/'src/main/resources/assets/sourceofmystery'
    file=assets/'sounds.json';sounds=json.loads(file.read_text(encoding='utf-8'))
    labels={'void_wake':('虚空蓄势','The void gathers'),'void_break':('虚空破裂','The void ruptures'),'void_seal':('虚空闭合','The rift closes'),'heel_step':('高跟鞋踏地','High heels strike the floor'),'finger_snap':('魔导师打响指','The archmage snaps her fingers')}
    for name in labels:sounds['archmage.'+name]={'subtitle':'subtitles.sourceofmystery.archmage.'+name,'sounds':[{'name':'sourceofmystery:archmage/'+name,'stream':False,'attenuation_distance':32}]}
    sounds['archmage.heel_step']['sounds']=[{'name':f'sourceofmystery:archmage/heel_step_{i:02d}','stream':False,'attenuation_distance':32} for i in range(1,4)]
    file.write_text(json.dumps(sounds,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    for lang,index in [('zh_cn',0),('en_us',1)]:
        file=assets/'lang'/(lang+'.json');data=json.loads(file.read_text(encoding='utf-8'))
        for name,values in labels.items():data['subtitles.sourceofmystery.archmage.'+name]=values[index]
        file.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    preview=ROOT/'build/offering-preview';preview.mkdir(exist_ok=True)
    audition=np.zeros(12*RATE)
    for name,start,gain in [('heel_step_01',.25,.9),('heel_step_02',1.1,.9),('heel_step_03',1.95,.9),('finger_snap',3.1,.95),('void_break',4.3,.9),('void_seal',8.3,.7)]:
        x=clips[name]*gain;i=round(start*RATE);audition[i:i+len(x)]+=x
    sf.write(preview/'offering-new-sounds.wav',audition,RATE,subtype='PCM_16')
    report['cinematic_gains']=GAINS
    (ROOT/'build/offering-audio-checks.json').write_text(json.dumps(report,indent=2));print(json.dumps(report,indent=2))

if __name__=='__main__':generate()
