"""Qwen Base speech continuation from the user's accepted synthetic declaration.

Uses only our Qwen-generated reference, no real person's recording. Audition
files stay separate from runtime assets until the user has assessed the result.
"""
import json
import numpy as np
import soundfile as sf
from scipy.signal import butter,sosfilt
from scipy.ndimage import uniform_filter1d
import torch
from qwen_tts import Qwen3TTSModel
from generate_archmage_voice_v2 import ROOT,REVIEW

OUT=REVIEW/'consistent-voice-candidates'

def clean(x,sr):
    x=sosfilt(butter(2,[70,9500],'bandpass',fs=sr,output='sos'),np.asarray(x,dtype='float64'))
    envelope=np.sqrt(uniform_filter1d(x*x,size=480)+1e-10)
    active=np.flatnonzero(envelope>.008)
    assert len(active)
    x=x[max(0,active[0]-1200):min(len(x),active[-1]+3600)]
    rms=np.sqrt(np.mean(x[abs(x)>.012]**2))
    x*=min(.17/max(rms,1e-8),.74/max(abs(x)))
    x[:180]*=np.linspace(0,1,180);x[-480:]*=np.linspace(1,0,480)
    return x

def main():
    OUT.mkdir(exist_ok=True);torch.set_num_threads(4)
    front,sr=sf.read(REVIEW/'release_a.wav');front=front[:round(2.9*sr)]
    sf.write(OUT/'accepted-reference.wav',front,sr,subtype='PCM_16')
    model=Qwen3TTSModel.from_pretrained(str(ROOT/'build/archmage-tts/model-base'),device_map='cuda:0',dtype=torch.bfloat16,attn_implementation='sdpa')
    prompt=model.create_voice_clone_prompt(ref_audio=(front.astype('float32'),sr),ref_text='これが、伝説の超位魔法！',x_vector_only_mode=False)
    records=[]
    lines=[('a','これが、伝説の超位魔法！いん、めつ、さつ！',90171),
           ('b','これが、伝説の超位魔法！隕滅殺！',90172)]
    for name,text,seed in lines:
        print('GENERATING',name,text,flush=True);torch.manual_seed(seed)
        wavs,sr=model.generate_voice_clone(text=text,language='Japanese',voice_clone_prompt=prompt,
                temperature=.70,top_p=.90,repetition_penalty=1.10,max_new_tokens=512,non_streaming_mode=True)
        raw=np.asarray(wavs[0]);sf.write(OUT/(name+'-raw.wav'),raw,sr,subtype='PCM_16')
        x=clean(raw,sr);assert 2<len(x)/sr<15
        sf.write(OUT/(name+'-complete.wav'),x,sr,subtype='PCM_16')
        records.append(dict(id=name,text=text,seconds=len(x)/sr,peak=float(max(abs(x))),
            source='Qwen3-TTS-1.7B-Base',reference='Our accepted Qwen synthetic declaration, no real person'))
        print('GENERATED',json.dumps(records[-1],ensure_ascii=False),flush=True)
    (OUT/'candidates.json').write_text(json.dumps(records,ensure_ascii=False,indent=2),encoding='utf8')

if __name__=='__main__':main()
