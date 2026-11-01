"""LEGACY V1 Japanese VO (rejected by user); use generate_archmage_voice_v2.py now.

Offline Japanese character VO using the official VOICEVOX Core + Sora VVM.

Build-only dependencies live in build/voicevox; no synthesizer is shipped in JAR.
See art/outsider_void_boss/voice/CREDITS.md for source and reuse terms.
"""
from pathlib import Path
import sys, io, json, math, struct
ROOT = Path(__file__).resolve().parents[2]
sys.path[:0] = [str(ROOT/'build/voicevox/python'), str(ROOT/'build/outsider-audio-deps')]
import numpy as np
import soundfile as sf
from scipy.signal import butter, sosfilt
from voicevox_core.blocking import Onnxruntime, OpenJtalk, Synthesizer, VoiceModelFile

LINES = [
    ('engage', 'この領域に、踏み入るとはね。', '竟敢踏入我的领域。'),
    ('grasp_a', '逃げ場は、残さない。', '我不会留下退路。'),
    ('grasp_b', '闇よ。あれを、呑み込みなさい。', '黑暗啊，将其吞噬。'),
    ('silence_a', '跪きなさい。', '跪下。'),
    ('silence_b', 'その足元に、終わりを。', '终焉，就在你的脚下。'),
    ('ultimate_a', '見せてあげる。世界の、終わりを。', '让你见识，世界的终焉。'),
    ('ultimate_b', '虚空よ。その扉を、開きなさい。', '虚空啊，开启那道门扉。'),
    ('chant_a', '星も、時も、すべて虚無へ。', '群星与时间，尽归虚无。'),
    ('chant_b', '幾重の封印よ、今、解き放たれよ。', '重重封印，于此刻解放。'),
    ('release_a', '消えなさい。滅びの、空へ。', '消失吧，归于陨灭之空。'),
    ('release_b', 'これで、終わりよ。', '到此为止了。'),
    ('kill', '静かに、眠りなさい。', '安静地，长眠吧。'),
    ('death', '私が、この程度で。', '我竟然……会止步于此。'),
]

def main():
    runtime=ROOT/'build/voicevox/runtime'
    ort=Onnxruntime.load_once(filename=str(runtime/'onnxruntime/lib/voicevox_onnxruntime.dll'))
    synthesizer=Synthesizer(ort,OpenJtalk(str(runtime/'dict/open_jtalk_dic_utf_8-1.11')),
                            acceleration_mode='CPU',cpu_num_threads=4)
    with VoiceModelFile.open(runtime/'models/vvms/2.vvm') as model:
        synthesizer.load_voice_model(model)
    out=ROOT/'src/main/resources/assets/sourceofmystery/sounds/archmage/voice'
    review=ROOT/'art/outsider_void_boss/voice'
    out.mkdir(parents=True,exist_ok=True);review.mkdir(parents=True,exist_ok=True)
    metadata=[];preview=[];envelopes=[]
    for name,ja,zh in LINES:
        q=synthesizer.create_audio_query(ja,16)
        q.speed_scale=.94;q.pitch_scale=-.055;q.intonation_scale=.88
        q.pre_phoneme_length=.065;q.post_phoneme_length=.12
        q.volume_scale=1;q.output_stereo=False;q.output_sampling_rate=24000
        wav=synthesizer.synthesis(q,16)
        x,sr=sf.read(io.BytesIO(wav),dtype='float32')
        (review/(name+'.raw.wav')).write_bytes(wav)
        x=sosfilt(butter(2,80,'highpass',fs=sr,output='sos'),x)
        # Dynamic de-essing retains articulation instead of indiscriminately
        # suppressing every high frequency or dropping the voice an octave.
        high=sosfilt(butter(2,[4300,9500],'bandpass',fs=sr,output='sos'),x)
        envelope=np.sqrt(np.convolve(high*high,np.ones(240)/240,mode='same')+1e-10)
        reduction=np.clip((envelope-.025)/.065,0,.38)
        x-=high*reduction
        x=sosfilt(butter(2,7300,'lowpass',fs=sr,output='sos'),x)
        # Gentle soft knee compression then a consistent, comfortable level.
        x=np.tanh(x*1.25)/1.25
        voiced=np.abs(x)>.012
        rms=np.sqrt(np.mean(x[voiced]**2)) if voiced.any() else 1
        x*=min(.103/max(rms,1e-6),.79/max(np.max(np.abs(x)),1e-6))
        fade=min(240,len(x)//2);x[:fade]*=np.linspace(0,1,fade);x[-fade:]*=np.linspace(1,0,fade)
        sf.write(out/(name+'.ogg'),x,sr,format='OGG',subtype='VORBIS')
        sf.write(review/(name+'.wav'),x,sr,subtype='PCM_16')
        preview.extend([x,np.zeros(sr//2)])
        blocks=[float(np.sqrt(np.mean(x[i:i+1200]**2))) for i in range(0,len(x),1200)]
        mouth=np.clip(np.array(blocks)/.12,0,1)*.62
        envelopes.append(mouth.astype('<f4'))
        duration=len(x)/sr
        metadata.append(dict(id=name,ja=ja,zh=zh,seconds=round(duration,3),ticks=math.ceil(duration*20),
                             peak=round(float(np.abs(x).max()),4),rms=round(float(np.sqrt(np.mean(x*x))),4)))
        print(name,round(duration,2),zh,flush=True)
    sf.write(review/'archmage-voice-preview.wav',np.concatenate(preview),24000,subtype='PCM_16')
    (review/'lines.json').write_text(json.dumps(metadata,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    envfile=ROOT/'src/main/resources/assets/sourceofmystery/meshes/entity/outsider_voice.bin'
    with envfile.open('wb') as f:
        f.write(struct.pack('<4I',0x4f564f31,1,len(envelopes),20))
        for env in envelopes:f.write(struct.pack('<I',len(env)));f.write(env.tobytes())
    soundsfile=ROOT/'src/main/resources/assets/sourceofmystery/sounds.json'
    sounds=json.loads(soundsfile.read_text(encoding='utf8'))
    for line in metadata:
        name=line['id'];key='archmage.voice.'+name
        sounds[key]={'subtitle':'subtitles.sourceofmystery.'+key,'sounds':[
            {'name':'sourceofmystery:archmage/voice/'+name,'stream':False,'attenuation_distance':32}]}
    soundsfile.write_text(json.dumps(sounds,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    for language in ['zh_cn','en_us']:
        path=ROOT/f'src/main/resources/assets/sourceofmystery/lang/{language}.json'
        lang=json.loads(path.read_text(encoding='utf8'))
        for line in metadata:
            lang['subtitles.sourceofmystery.archmage.voice.'+line['id']]=('域外魔导师：'+line['zh']) if language=='zh_cn' else 'Outer Archmage: '+line['ja']
        path.write_text(json.dumps(lang,ensure_ascii=False,indent=2)+'\n',encoding='utf8')

if __name__=='__main__':main()
