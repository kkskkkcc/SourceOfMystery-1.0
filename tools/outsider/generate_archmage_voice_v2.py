"""Historical V2 audition generator. Production uses generate_archmage_voice_v3.py.

Run with build/archmage-tts/venv/Scripts/python.exe. GPU/model are build-only.
The native voice is kept at its generated pitch, speed, and natural rhythm.
"""
from pathlib import Path
import argparse,io,json,math,struct,shutil,time
import numpy as np
import soundfile as sf
from scipy.signal import butter,sosfilt,resample_poly
from scipy.ndimage import uniform_filter1d
import torch
from qwen_tts import Qwen3TTSModel
ROOT=Path(__file__).resolve().parents[2]
REVIEW=ROOT/'art/outsider_void_boss/voice/v2'
ASSETS=ROOT/'src/main/resources/assets/sourceofmystery'
PERSONA='A bold adult Japanese female mage in an anime battle. Cool, strong, commanding, with a resonant middle register and crisp articulation. Natural Japanese acting and breathing, never a flat narrator, never cute or squeaky. '
LINES=[
 ('engage','踏み込んだわね。覚悟はできている？','踏进来了啊。做好觉悟了吗？','Controlled confidence, a quietly challenging question. Conversational and natural, with a firm ending.'),
 ('grasp_a','逃がさない！闇に呑まれなさい！','休想逃走！被黑暗吞噬吧！','A sharp projected battle call, energetic and decisive. Strong rhythmic attack, with real emotion.'),
 ('grasp_b','闇よ、穿て！','黑暗啊，贯穿敌人！','A short, forceful combat command. Project the final word with power and a crisp stop.'),
 ('silence_a','跪け！黒き淵に沈め！','跪下！沉入漆黑深渊！','A fierce battle command with chest-supported projection, commanding and overwhelming.'),
 ('silence_b','砕け散れ！闇の底へ！','粉碎吧！坠入黑暗之底！','A vigorous female battle cry with a powerful first word and a strong final phrase.'),
 ('ultimate_a','見せてあげる。世界を覆す、真の魔法を！','让你见识，颠覆世界的真正魔法！','Begin low and confident, then build rising menace and strength through the last phrase. Dramatic but naturally connected.'),
 ('ultimate_b','ここから先は、神すら許さぬ領域よ！','此地，是连神明也不得踏入的领域！','A resonant, imposing declaration. Controlled power, rising intensity, a firm projected finish.'),
 ('chant_a','虚空よ、我が声に応えよ！','虚空啊，回应吾之呼唤！','A strong incantation, gathering force across the sentence. Natural breath and flowing connected rhythm.'),
 ('chant_b','星よ、時よ、すべてを我が手に！','群星啊，时间啊，将一切交予吾手！','A vigorous incantation, each invocation grows stronger. Build to a commanding crescendo, without staccato robotic pauses.'),
 ('release_a','これが、伝説の超位魔法！いん！めつ！さつ！！','这就是，传说中的超位魔法！陨！灭！杀！！','A powerful adult female SOPRANO ultimate-attack shout. Begin with an ethereal, luminous, resonant declaration, then soar into a ringing, commanding high female register. Shout IN! METSU! SATSU!! as three clearly separated, strongly accented beats with rising power, sustaining the final cry. Breathe and project naturally with full support. Brilliant and overwhelming, never squeaky, nasal or shrill; not a whispered narrator and not singing the sentence.'),
 ('release_b','見よ、これが伝説の超位魔法！いん！めつ！さつ！！','看吧，这就是传说中的超位魔法！陨！灭！杀！！','A commanding adult female SOPRANO battle cry. An airy, majestic opening grows into an explosive, ringing high-register ultimate shout. The last three beats IN! METSU! SATSU!! must each be distinct and forceful, ending in a sustained powerful cry. Natural emotional Japanese acting, supported breath and clear vowels, luminous head resonance without piercing sibilance. No cute voice, no low monotone, no operatic singing.'),
 ('kill','その覚悟、忘れはしない。','你的觉悟，我会铭记。','Calm, cool, resolute. A natural quiet line after a battle, firm and unhurried.'),
 ('death','まだ、終わってたまるか！','我岂能，就此终结！','A defiant, strained battle cry from an adult woman. Breath catches naturally, then a final burst of strength.'),
]

def master(x,sr,name):
    x=np.asarray(x,dtype=np.float64)
    if sr!=24000:x=resample_poly(x,24000//math.gcd(sr,24000),sr//math.gcd(sr,24000));sr=24000
    x=sosfilt(butter(2,65,'highpass',fs=sr,output='sos'),x)
    high=sosfilt(butter(2,[4700,9700],'bandpass',fs=sr,output='sos'),x)
    env=np.sqrt(uniform_filter1d(high*high,size=240)+1e-10)
    x-=high*np.clip((env-.045)/.09,0,.28)
    x=sosfilt(butter(2,10300,'lowpass',fs=sr,output='sos'),x)
    # Remove model's leading/trailing silence, retaining natural attack breath.
    env=np.sqrt(uniform_filter1d(x*x,size=480)+1e-10)
    active=np.flatnonzero(env>.008)
    if len(active):x=x[max(0,int(active[0])-1200):min(len(x),int(active[-1])+2880)]
    x=np.tanh(x*1.12)/1.12
    voiced=np.abs(x)>.012;rms=np.sqrt(np.mean(x[voiced]**2)) if voiced.any() else 1
    target=.22 if name.startswith('release_') else .135
    x*=min(target/max(rms,1e-8),.83/max(np.max(np.abs(x)),1e-8))
    if name.startswith('release_'):
        # Restrained, filtered room reflections give the declaration space.
        # Keep the direct soprano voice dominant; do not shift pitch or speed.
        wet=sosfilt(butter(2,[240,4200],'bandpass',fs=sr,output='sos'),x)
        result=np.pad(x,(0,int(.7*sr)))
        for delay,gain in [(0.083,.095),(.149,.071),(.233,.050),(.347,.032),(.503,.018)]:
            start=round(delay*sr);result[start:start+len(wet)]+=wet*gain
        x=result; x*=min(1,.84/max(np.max(np.abs(x)),1e-8))
    fade=min(180,len(x)//2);x[:fade]*=np.linspace(0,1,fade);x[-fade:]*=np.linspace(1,0,fade)
    return x.astype('float32')

def preview():
    sample=[]
    for name in ['engage','grasp_a','chant_a','release_a']:
        p=REVIEW/(name+'.wav')
        if not p.exists():return
        x,sr=sf.read(p);sample.extend([x,np.zeros(sr//2)])
    sf.write(REVIEW/'voice-v2-combat-sample.wav',np.concatenate(sample),24000,subtype='PCM_16')

def package():
    records=[];curves=[];full=[];destination=ASSETS/'sounds/archmage/voice'
    destination.mkdir(parents=True,exist_ok=True)
    for name,ja,zh,instruct in LINES:
        x,sr=sf.read(REVIEW/(name+'.wav'));assert sr==24000 and x.ndim==1 and 0<len(x)<sr*15 and np.isfinite(x).all()
        sf.write(destination/(name+'.ogg'),x,sr,format='OGG',subtype='VORBIS')
        decoded,sr=sf.read(destination/(name+'.ogg'))
        assert abs(decoded).max()<.95,'Encoded voice peak clips'
        frames=np.array([np.sqrt(np.mean(decoded[i:i+1200]**2)) for i in range(0,len(decoded),1200)])
        curves.append(np.clip((frames-.008)/.182,0,1).astype('<f4')*(.86 if name.startswith('release_') else .65))
        active=np.flatnonzero(frames>.014);speech_ticks=min(len(frames),int(active[-1])+3)
        climax=-1
        if name.startswith('release_'):
            # The last long breath before IN / METSU / SATSU separates the
            # luminous declaration from the explosive three-beat expression.
            gaps=[];start=None
            for i in range(30,min(speech_ticks,round(len(frames)*.8))):
                if frames[i]<.018 and start is None:start=i
                elif frames[i]>=.018 and start is not None:
                    gaps.append((i-start,i));start=None
            assert gaps,'Ultimate line has no declaration-to-shout breath'
            climax=max(gaps)[1]
        records.append(dict(id=name,ja=ja,zh=zh,spoken_text=ja,seconds=round(len(decoded)/sr,3),ticks=math.ceil(len(decoded)/sr*20),
                            speech_ticks=speech_ticks,climax_tick=climax,
                            peak=round(float(abs(decoded).max()),4),rms=round(float(np.sqrt(np.mean(decoded**2))),4),
                            source='Qwen3-TTS-1.7B-CustomVoice/Ono_Anna',performance=instruct))
        full.extend([x,np.zeros(sr//2)])
    (REVIEW/'lines.json').write_text(json.dumps(records,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    occasions=['进入战斗','黑暗之握 A','黑暗之握 B','黑暗寂灭 A','黑暗寂灭 B','陨灭杀宣告 A','陨灭杀宣告 B','超位吟唱 A','超位吟唱 B','陨灭杀喊招 A','陨灭杀喊招 B','击杀','死亡']
    emotions=['自信、轻挑嘴角','果断、专注','锐利、专注','威压、蹙眉','愤怒、命令','从容自信，逐渐严肃','自信宣言','专注蓄力','专注、逐渐用力','空灵宣言转为女高音爆发','空灵宣言转为女高音爆发','克制、敬意','痛苦、不屈']
    table=['# 域外魔导师：当前日语台词与表情','',
           '声源：Qwen3-TTS / Ono_Anna。飒爽、有力量的成年女声；陨灭杀为女高音表演，带克制的空间混响。',
           '','| 场合 | 日文 | 中文 | 对应表情 |','|---|---|---|---|']
    for occasion,row,emotion in zip(occasions,records,emotions):
        table.append('| '+' | '.join([occasion,row['ja'].replace('いん！めつ！さつ','隕！滅！殺'),row['zh'],emotion])+' |')
    table.extend(['','陨灭杀第12.2秒开始喊招，宣言后换气处切换爆发表情，三个重音位于第15–18秒的冲天光束阶段。',
                  '所有表情与同步语音时钟一致，渐入渐出；20Hz录音包络驱动嘴型，保持自然眨眼。',
                  '','[女高音喊招试听]('+str(REVIEW/'release_a.wav').replace('\\','/')+')',
                  '','[全台词试听]('+str(REVIEW/'voice-v2-all-lines.wav').replace('\\','/')+')',
                  '','[台词与狂风时序试混，非游戏录音]('+str(REVIEW/'ultimate-performance-timing-demo.wav').replace('\\','/')+')',
                  '','[来源及重建说明]('+str(REVIEW.parent/'CREDITS.md').replace('\\','/')+')',''])
    (REVIEW.parent/'台词和试听.md').write_text('\n'.join(table),encoding='utf8')
    with (ASSETS/'meshes/entity/outsider_voice.bin').open('wb') as out:
        out.write(struct.pack('<4I',0x4f564f31,1,len(curves),20))
        for curve in curves:out.write(struct.pack('<I',len(curve)));out.write(curve.tobytes())
    ids=','.join(json.dumps(row['id']) for row in records);ticks=','.join(str(row['ticks']) for row in records)
    speech=','.join(str(row['speech_ticks']) for row in records);climax=','.join(str(row['climax_tick']) for row in records)
    java='package com.sourceofmystery.entity;\n\n/** Generated from final recordings; same order as the 20 Hz mouth envelopes. */\npublic final class OutsiderVoice {\n    private OutsiderVoice(){}\n    public static final String[] IDS={'+ids+'};\n    public static final int[] TICKS={'+ticks+'};\n    public static final int[] SPEECH_TICKS={'+speech+'};\n    public static final int[] CLIMAX_TICKS={'+climax+'};\n}\n'
    (ROOT/'src/main/java/com/sourceofmystery/entity/OutsiderVoice.java').write_text(java,encoding='utf8')
    for language in ['zh_cn','en_us']:
        path=ASSETS/f'lang/{language}.json';lang=json.loads(path.read_text(encoding='utf8'))
        for row in records:lang['subtitles.sourceofmystery.archmage.voice.'+row['id']]=('域外魔导师：'+row['zh']) if language=='zh_cn' else 'Outer Archmage: '+row['ja'].replace('いん！めつ！さつ','隕！滅！殺')
        path.write_text(json.dumps(lang,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    sf.write(REVIEW/'voice-v2-all-lines.wav',np.concatenate(full),24000,subtype='PCM_16')
    # A clearly labelled timing mock-up, using the real recorded clips and gale.
    mix=np.zeros(24*24000);duck=np.ones_like(mix)
    for when,name in [(0,'ultimate_a'),(8,'chant_a'),(12.2,'release_a')]:
        x,sr=sf.read(REVIEW/(name+'.wav'));start=round(when*sr);end=min(len(mix),start+len(x))
        mix[start:end]+=x[:end-start];duck[start:end]=.30
    wind,sr=sf.read(ASSETS/'sounds/archmage/ritual_wind.ogg');wind=np.resize(wind,len(mix));t=np.arange(len(mix))/24000
    def smooth(x):x=np.clip(x,0,1);return x*x*x*(10+x*(-15+6*x))
    envelope=smooth((t-3)/4)*(1-smooth((t-18)/4))
    mix+=wind*envelope*duck*.68;mix*=min(1,.94/max(abs(mix)))
    sf.write(REVIEW/'ultimate-performance-timing-demo.wav',mix,24000,subtype='PCM_16')
    preview();print('VOICE_V2_PACKAGED',json.dumps(records,ensure_ascii=False),flush=True)
    shutil.copy2(REVIEW/'lines.json',REVIEW.parent/'lines.json')
    shutil.copy2(REVIEW/'voice-v2-all-lines.wav',REVIEW.parent/'archmage-voice-preview.wav')
    shutil.copy2(REVIEW/'voice-v2-combat-sample.wav',REVIEW.parent/'archmage-combat-sample.wav')
    for name,_,_,_ in LINES:
        for suffix in ['.wav','.raw.wav']:
            shutil.copy2(REVIEW/(name+suffix),REVIEW.parent/(name+suffix))

def main():
    if (REVIEW.parent/'v3/approval.json').exists():
        raise SystemExit('V3 approved voice exists; use generate_archmage_voice_v3.py to preserve the reviewed recordings.')
    parser=argparse.ArgumentParser();parser.add_argument('--preview-only',action='store_true');parser.add_argument('--package-only',action='store_true');parser.add_argument('--regenerate',nargs='*',default=[]);args=parser.parse_args()
    REVIEW.mkdir(parents=True,exist_ok=True)
    if args.package_only:package();return
    for name in args.regenerate:
        assert name in [row[0] for row in LINES],name
        backup=REVIEW/'previous-candidates';backup.mkdir(exist_ok=True)
        for suffix in ['.wav','.raw.wav']:
            source=REVIEW/(name+suffix)
            if source.exists():shutil.move(str(source),str(backup/(str(time.time_ns())+'-'+name+suffix)))
    torch.set_num_threads(4)
    model=Qwen3TTSModel.from_pretrained(str(ROOT/'build/archmage-tts/model'),device_map='cuda:0',dtype=torch.bfloat16,attn_implementation='sdpa')
    priority=['engage','grasp_a','chant_a','release_a']
    order=priority+[x[0] for x in LINES if x[0] not in priority]
    if args.preview_only:order=priority
    for name in order:
        file=REVIEW/(name+'.wav')
        if file.exists():continue
        row=next(x for x in LINES if x[0]==name);_,text,zh,instruct=row
        torch.manual_seed(71039+next(i for i,x in enumerate(LINES) if x[0]==name))
        started=time.time();print('GENERATING',name,zh,flush=True)
        persona=PERSONA if not name.startswith('release_') else 'An adult Japanese female sorceress, bold, commanding and naturally expressive. '
        wavs,sr=model.generate_custom_voice(text=text,language='Japanese',speaker='Ono_Anna',instruct=persona+instruct,
                max_new_tokens=512,temperature=.85,top_p=.95,repetition_penalty=1.08)
        raw=np.asarray(wavs[0]);assert len(raw)/sr<18,'Voice generation exceeded phrase duration'
        sf.write(REVIEW/(name+'.raw.wav'),raw,sr,subtype='PCM_16')
        audio=master(raw,sr,name);sf.write(file,audio,24000,subtype='PCM_16')
        print('GENERATED',name,'seconds',len(audio)/24000,'inference_seconds',round(time.time()-started,1),flush=True);preview()
    if not args.preview_only:package()

if __name__=='__main__':main()
