"""Audition edit: preserve declaration, accelerate/raise the call, 2s last-word tail.

Uses the existing Qwen Base take and Rubber Band R3 with formants preserved.
Does not change runtime assets or install a JAR. Native utility stays build-only.
"""
from pathlib import Path
import argparse,hashlib,json,subprocess
import numpy as np
import soundfile as sf
from scipy.signal import butter,sosfilt,fftconvolve
from scipy.ndimage import uniform_filter1d

ROOT=Path(__file__).resolve().parents[2]
SOURCE=ROOT/'art/outsider_void_boss/voice/v2/consistent-voice-candidates/a-complete.wav'
OUT=SOURCE.parent.parent/'faster-higher-tail'
RUBBERBAND=ROOT/'build/archmage-tts/rubberband/rubberband-4.0.0-gpl-executable-windows/rubberband-r3.exe'

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--tempo',type=float,default=1.15)
    parser.add_argument('--semitones',type=float,default=1.5)
    args=parser.parse_args();OUT.mkdir(exist_ok=True)
    x,sr=sf.read(SOURCE);assert sr==24000 and x.ndim==1
    split=round(3.10*sr);word_source=4.66
    sf.write(OUT/'call-input.wav',x[split:],sr,subtype='FLOAT')
    command=[str(RUBBERBAND),'-q','-3','-F','--tempo',str(args.tempo),'--pitch',str(args.semitones),
             str(OUT/'call-input.wav'),str(OUT/'call-processed.wav')]
    subprocess.run(command,check=True,capture_output=True,text=True)
    call,output_sr=sf.read(OUT/'call-processed.wav');assert output_sr==sr
    assert abs(len(call)-round((len(x)-split)/args.tempo))<10
    # The edit falls inside the existing breath, leaving every declaration
    # sample intact. Only the attack-name portion enters the pitch/time engine.
    call[:240]*=np.linspace(0,1,240)
    if abs(call).max()>.78:call*=.78/abs(call).max()
    dry=np.concatenate([x[:split],call])
    word_start=split+round((word_source-3.10)/args.tempo*sr)
    energy=np.sqrt(uniform_filter1d(dry*dry,size=480)+1e-12)
    voiced=np.flatnonzero(energy>.012)
    speech_end=int(voiced[-1]+1)
    word=np.zeros_like(dry);word[word_start:speech_end]=dry[word_start:speech_end]
    word[word_start:word_start+480]*=np.linspace(0,1,480)

    # Dense, filtered reverberation rather than repeated intelligible words:
    # no repeating "sa-sa-sa" syllables that could be mistaken for laughter.
    tail_seconds=2.0;n=round(tail_seconds*sr)+1;t=np.arange(n)/sr
    rng=np.random.default_rng(191019)
    impulse=rng.normal(size=n)
    impulse=sosfilt(butter(2,[240,5000],'bandpass',fs=sr,output='sos'),impulse)
    impulse*=np.minimum(np.maximum((t-.065)/.08,0),1)*np.exp(-3.5*t)
    impulse[-round(.14*sr):]*=np.linspace(1,0,round(.14*sr))
    impulse*=.28/max(np.linalg.norm(impulse),1e-9)
    for delay,gain in [(.043,.085),(.079,.065),(.127,.045),(.181,.028)]:
        impulse[round(delay*sr)]+=gain
    wet=fftconvolve(word,impulse)
    end=speech_end+round(tail_seconds*sr)
    result=np.pad(dry,(0,max(0,end-len(dry))))[:end]
    result+=wet[:end]
    assert max(abs(result))<.93 and np.isfinite(result).all()
    destination=OUT/'ultimate-faster-higher-tail2s.wav'
    sf.write(destination,result,sr,subtype='PCM_16')
    sf.write(OUT/'call-only-tail2s.wav',result[split:],sr,subtype='PCM_16')
    decoded,check_sr=sf.read(destination)
    assert np.array_equal(decoded[:split],x[:split]),'Accepted declaration changed'
    assert check_sr==sr and decoded.ndim==1
    metadata=dict(source=str(SOURCE),source_sha256=hashlib.sha256(SOURCE.read_bytes()).hexdigest(),
        output=str(destination),tempo=args.tempo,pitch_semitones=args.semitones,
        formants_preserved=True,declaration_unchanged_seconds=split/sr,
        call_start_seconds=split/sr,last_word_start_seconds=word_start/sr,
        speech_end_seconds=speech_end/sr,tail_seconds=tail_seconds,
        total_seconds=len(result)/sr,peak=float(abs(decoded).max()),
        processor='Rubber Band 4.0 R3 + last-word-only diffuse reverb',
        processor_source='https://www.breakfastquay.com/rubberband/',
        review='User requested faster/higher call and 2s final-word tail; audition pending, not installed')
    (OUT/'settings.json').write_text(json.dumps(metadata,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    print(json.dumps(metadata,ensure_ascii=False,indent=2))

if __name__=='__main__':main()
