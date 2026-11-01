"""Original five-second mono meteor air-rush; deterministic procedural audio.

OGG/Vorbis through libsndfile; no sampled third-party recording is used.
"""
from pathlib import Path
import sys,json
import numpy as np
from scipy.signal import butter,sosfilt

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'build/outsider-audio-deps'))
import soundfile as sf

def main():
    rate=44100;t=np.arange(rate*5)/rate;rng=np.random.default_rng(734119)
    wind=sosfilt(butter(3,[110,4400],btype='bandpass',fs=rate,output='sos'),rng.normal(0,1,len(t)))
    roar=sosfilt(butter(3,[35,250],btype='bandpass',fs=rate,output='sos'),rng.normal(0,1,len(t)))
    whistle=sosfilt(butter(2,[650,1900],btype='bandpass',fs=rate,output='sos'),rng.normal(0,1,len(t)))
    phase=2*np.pi*(58*t+11*t*t+2.5*np.sin(t*2))
    tonal=np.sin(phase)+.28*np.sin(phase*1.49)
    envelope=np.minimum(1,t/.24)*np.minimum(1,(5-t)/.08)*(.60+.40*(t/5)**1.5)
    audio=(wind*1.4+roar*4.5+whistle*.55+tonal*.055)*envelope
    audio=np.tanh(audio*1.35);audio*=.88/np.max(np.abs(audio))
    output=ROOT/'src/main/resources/assets/sourceofmystery/sounds/archmage/meteor_fall.ogg'
    output.parent.mkdir(parents=True,exist_ok=True)
    sf.write(output,audio,rate,format='OGG',subtype='VORBIS')
    decoded,sr=sf.read(output)
    # Lossy reconstruction can overshoot the PCM peak; leave real headroom
    # in the decoded file rather than trusting only the pre-encode waveform.
    if np.max(np.abs(decoded))>.95:
        audio*=.90/np.max(np.abs(decoded))
        sf.write(output,audio,rate,format='OGG',subtype='VORBIS');decoded,sr=sf.read(output)
    assert decoded.ndim==1 and len(decoded)==5*rate and sr==rate and np.isfinite(decoded).all()
    assert np.max(np.abs(decoded))<.98,'Encoded meteor sound clips'
    report={'file':str(output),'seconds':len(decoded)/sr,'sample_rate':sr,'channels':1,
            'peak':float(np.max(np.abs(decoded))),'rms':float(np.sqrt(np.mean(decoded**2))),
            'original_procedural_audio':True,'source_world_y':255,'runtime_volume':32,'runtime_pitch':1}
    (ROOT/'build/meteor-fall-audio-check.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
    print(json.dumps(report,indent=2))

if __name__=='__main__':main()
