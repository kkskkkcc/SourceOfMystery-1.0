"""Preserve the accepted declaration; regenerate clean projected attack names.

Candidate audition only: does not package or replace game assets.
"""
from pathlib import Path
import json,sys
import numpy as np
import soundfile as sf
from scipy.signal import butter,sosfilt
import torch
from qwen_tts import Qwen3TTSModel
from generate_archmage_voice_v2 import ROOT,REVIEW

OUT=REVIEW/'clean-shout-candidates'
VARIANTS=[
 ('a','いん、めつ、さつ！','成人女性の魔導師。明るく張りのある高めの女声で、力強い必殺技の掛け声。三つの語を明瞭に、自然な一息の流れで発声し、最後は芯のある声で力強く伸ばす。',81013),
 ('b','いんめつさつ！','An adult Japanese female mage. A clear, bright, powerful high female battle call with supported breath and clean open vowels. Speak the attack name decisively in one natural connected phrase, with three strong accents and a firm sustained finish.',81014),
]

def main():
    OUT.mkdir(exist_ok=True)
    torch.set_num_threads(4)
    model=Qwen3TTSModel.from_pretrained(str(ROOT/'build/archmage-tts/model'),device_map='cuda:0',dtype=torch.bfloat16,attn_implementation='sdpa')
    front,sr=sf.read(REVIEW/'release_a.wav');front=front[:round(2.90*sr)].copy()
    # Preserve accepted words, speed and pitch. Only end the existing pause cleanly.
    front[-480:]*=np.linspace(1,0,480)
    metadata=[]
    for name,text,instruct,seed in VARIANTS:
        torch.manual_seed(seed)
        wavs,sr=model.generate_custom_voice(text=text,language='Japanese',speaker='Ono_Anna',instruct=instruct,
                temperature=.65,top_p=.9,repetition_penalty=1.08,max_new_tokens=256)
        raw=np.asarray(wavs[0]);sf.write(OUT/(name+'-raw.wav'),raw,sr,subtype='PCM_16')
        x=sosfilt(butter(2,75,'highpass',fs=sr,output='sos'),raw.astype('float64'))
        x=sosfilt(butter(2,9400,'lowpass',fs=sr,output='sos'),x)
        # Preserve the native performance without saturation or voice stretching.
        energy=np.convolve(x*x,np.ones(480)/480,mode='same')**.5
        active=np.flatnonzero(energy>.009)
        assert len(active)>0
        x=x[max(0,active[0]-1200):min(len(x),active[-1]+2880)]
        voiced=abs(x)>.012;rms=np.sqrt(np.mean(x[voiced]**2))
        x*=min(.17/max(rms,1e-8),.72/max(abs(x)))
        x[:180]*=np.linspace(0,1,180);x[-480:]*=np.linspace(1,0,480)
        assert .5<len(x)/sr<5 and np.isfinite(x).all()
        sf.write(OUT/(name+'-shout.wav'),x,sr,subtype='PCM_16')
        full=np.concatenate([front,np.zeros(round(.16*sr)),x,np.zeros(round(.2*sr))])
        sf.write(OUT/(name+'-complete.wav'),full,sr,subtype='PCM_16')
        metadata.append(dict(id=name,text=text,performance=instruct,shout_seconds=len(x)/sr,complete_seconds=len(full)/sr,peak=float(max(abs(x)))))
        print('CANDIDATE',name,json.dumps(metadata[-1],ensure_ascii=False),flush=True)
    (OUT/'candidates.json').write_text(json.dumps(metadata,ensure_ascii=False,indent=2),encoding='utf8')

if __name__=='__main__':main()
