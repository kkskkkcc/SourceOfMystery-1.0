"""Production character voice: Qwen Base with the user's approved synthetic reference.

Run using build/archmage-tts/venv/Scripts/python.exe. Models stay build-only.
The approved ultimate master is copied unchanged, including its two-second tail.
"""
from pathlib import Path
import argparse,hashlib,json,math,shutil,struct,time
import numpy as np
import soundfile as sf
from scipy.ndimage import uniform_filter1d
from scipy.signal import butter,sosfilt
from generate_archmage_voice_v2 import LINES as PREVIOUS_LINES,ROOT,ASSETS

OUT=ROOT/'art/outsider_void_boss/voice/v3'
APPROVED=ROOT/'art/outsider_void_boss/voice/v2/faster-higher-tail/ultimate-faster-higher-tail2s.wav'
APPROVED_SHA='b304f65262a3cee6f2d46f410239ab624bfd7ef38bcbc85249a40e0b41045099'
LINES=[(name,ja,zh) for name,ja,zh,_ in PREVIOUS_LINES]
for i in [9,10]:LINES[i]=(LINES[i][0],'これが、伝説の超位魔法！いん、めつ、さつ！','这就是，传说中的超位魔法！陨！灭！杀！！')
LINES += [
 ('idle_ground','静かね。少し、ここで休みましょう。','真安静。就在这里稍作歇息吧。'),
 ('idle_flight','この風、悪くないわね。','这阵风，倒也不坏。'),
 ('takeoff','行くわ。風よ、我を運べ。','出发吧。风啊，载吾前行。'),
 ('landing','少し、地に降りましょう。','先落地片刻吧。'),
]
OCCASIONS=['交战','黑暗之握 A','黑暗之握 B','黑暗寂灭 A','黑暗寂灭 B','陨灭杀宣言 A','陨灭杀宣言 B','吟唱 A','吟唱 B','陨灭杀正式喊招','陨灭杀同版备用','击杀','死亡','地面日常','空中巡游','起飞','落地']
EMOTIONS=['自信、轻挑嘴角','果断、专注','锐利、专注','威压、蹙眉','严厉命令','自信逐渐严肃','从容宣言','专注蓄力','专注、用力','自信宣言转为爆发','自信宣言转为爆发','克制敬意','痛苦、不屈','放松、轻微笑意','平静、轻微笑意','坚定、专注','放松、舒缓']

def clean(x,sr):
    x=sosfilt(butter(2,[70,9500],'bandpass',fs=sr,output='sos'),np.asarray(x,dtype='float64'))
    envelope=np.sqrt(uniform_filter1d(x*x,size=480)+1e-10)
    active=np.flatnonzero(envelope>.008);assert len(active)
    x=x[max(0,int(active[0])-1200):min(len(x),int(active[-1])+2880)]
    rms=np.sqrt(np.mean(x[abs(x)>.012]**2))
    x*=min(.15/max(rms,1e-8),.74/max(abs(x)))
    x[:180]*=np.linspace(0,1,180);x[-480:]*=np.linspace(1,0,480)
    return x

def ultimate_dry():
    # Reconstruct the dry stem from the reviewed edit's saved intermediate;
    # never regenerate or modify the user-approved finished master.
    base,sr=sf.read(APPROVED.parent.parent/'consistent-voice-candidates/a-complete.wav')
    call,_=sf.read(APPROVED.parent/'call-processed.wav');call[:240]*=np.linspace(0,1,240)
    if abs(call).max()>.78:call*=.78/abs(call).max()
    return np.concatenate([base[:round(3.1*sr)],call])

def package():
    curves=[];records=[];all_audio=[];assets=ASSETS/'sounds/archmage/voice';assets.mkdir(parents=True,exist_ok=True)
    dry_ultimate=ultimate_dry()
    for i,(name,ja,zh) in enumerate(LINES):
        x,sr=sf.read(OUT/(name+'.wav'));assert sr==24000 and x.ndim==1 and np.isfinite(x).all() and 0<len(x)<sr*15
        sf.write(assets/(name+'.ogg'),x,sr,format='OGG',subtype='VORBIS')
        decoded,dsr=sf.read(assets/(name+'.ogg'));assert dsr==sr and abs(decoded).max()<.95
        frames=math.ceil(len(decoded)/1200);stem=dry_ultimate if i in [9,10] else decoded
        rms=np.array([np.sqrt(np.mean(stem[j*1200:min((j+1)*1200,len(stem))]**2)) if j*1200<len(stem) else 0 for j in range(frames)])
        active=np.flatnonzero(rms>.012);speech=min(frames,int(active[-1])+3)
        climax=-1
        if i in [9,10]:
            speech=math.ceil(4.945208333333333*20)
            climax=next(j for j in range(62,frames) if rms[j]>.04)
            rms[speech:]=0
        curve=np.clip((rms-.008)/.182,0,1).astype('<f4')*(.86 if i in [9,10] else .65)
        curves.append(curve)
        row=dict(id=name,ja=ja,zh=zh,spoken_text=ja,seconds=round(len(decoded)/sr,3),ticks=frames,
            speech_ticks=speech,climax_tick=climax,peak=round(float(abs(decoded).max()),4),
            rms=round(float(np.sqrt(np.mean(decoded**2))),4),source='Qwen3-TTS-1.7B-Base/approved-synthetic-reference',
            performance=EMOTIONS[i],reference_sha256=APPROVED_SHA,occasion=OCCASIONS[i])
        if i in [9,10]:row.update(user_approved=True,master_sha256=APPROVED_SHA,tail_seconds=2.0,call_tempo=1.15,call_pitch_semitones=1.5)
        records.append(row);all_audio.extend([x,np.zeros(sr//2)])
    with (ASSETS/'meshes/entity/outsider_voice.bin').open('wb') as file:
        file.write(struct.pack('<4I',0x4f564f31,1,len(curves),20))
        for curve in curves:file.write(struct.pack('<I',len(curve)));file.write(curve.tobytes())
    java='package com.sourceofmystery.entity;\n\n/** Generated from production recordings; order matches the 20 Hz mouth envelopes. */\npublic final class OutsiderVoice {\n    private OutsiderVoice(){}\n'
    java+='    public static final int FIRST_DAILY=13, IDLE_GROUND=13, IDLE_FLIGHT=14, TAKEOFF=15, LANDING=16;\n'
    for field,key in [('IDS','id'),('TICKS','ticks'),('SPEECH_TICKS','speech_ticks'),('CLIMAX_TICKS','climax_tick')]:
        java+='    public static final '+('String' if field=='IDS' else 'int')+'[] '+field+'={'+','.join(json.dumps(row[key],ensure_ascii=False) for row in records)+'};\n'
    java+='}\n';(ROOT/'src/main/java/com/sourceofmystery/entity/OutsiderVoice.java').write_text(java,encoding='utf8')
    sounds_path=ASSETS/'sounds.json';sounds=json.loads(sounds_path.read_text(encoding='utf8'))
    for row in records:
        name=row['id'];sounds['archmage.voice.'+name]=dict(subtitle='subtitles.sourceofmystery.archmage.voice.'+name,
            sounds=[dict(name='sourceofmystery:archmage/voice/'+name,stream=False,attenuation_distance=32)])
    sounds_path.write_text(json.dumps(sounds,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    for language in ['zh_cn','en_us']:
        path=ASSETS/f'lang/{language}.json';lang=json.loads(path.read_text(encoding='utf8'))
        for row in records:lang['subtitles.sourceofmystery.archmage.voice.'+row['id']]=('域外魔导师：'+row['zh']) if language=='zh_cn' else 'Outer Archmage: '+row['ja'].replace('いん、めつ、さつ','隕！滅！殺')
        path.write_text(json.dumps(lang,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    data=json.dumps(records,ensure_ascii=False,indent=2)+'\n';(OUT/'lines.json').write_text(data,encoding='utf8');(OUT.parent/'lines.json').write_text(data,encoding='utf8')
    sf.write(OUT/'voice-v3-all-lines.wav',np.concatenate(all_audio),24000,subtype='PCM_16')
    for label,names in [('combat-sample',['engage','grasp_a','silence_a','chant_a','release_a']),('daily-sample',['idle_ground','idle_flight','takeoff','landing'])]:
        samples=[]
        for name in names:samples.extend([sf.read(OUT/(name+'.wav'))[0],np.zeros(12000)])
        sf.write(OUT/(label+'.wav'),np.concatenate(samples),24000,subtype='PCM_16')
    shutil.copy2(OUT/'voice-v3-all-lines.wav',OUT.parent/'archmage-voice-preview.wav')
    shutil.copy2(OUT/'combat-sample.wav',OUT.parent/'archmage-combat-sample.wav')
    for name,_,_ in LINES:shutil.copy2(OUT/(name+'.wav'),OUT.parent/(name+'.wav'))
    table=['# 域外魔导师：正式日语台词与表情','',
        '所有语音统一使用用户通过的合成角色声线作为Qwen Base参考。陨灭杀正式录音保持审核文件不变，备用事件使用同一录音。',
        '','| 场合 | 日文 | 中文 | 表情 |','|---|---|---|---|']
    for row in records:table.append('| '+' | '.join([row['occasion'],row['ja'].replace('いん、めつ、さつ','隕！滅！殺'),row['zh'],row['performance']])+' |')
    table+=['','日常语音仅在没有战斗目标时低频触发；战斗台词可立即打断日常台词。',
        '陨灭杀第12.2秒开始，约4.945秒干声结束后还有2秒拖尾；拖尾不驱动张嘴口型。','']
    for label,file in [('战斗台词试听','combat-sample.wav'),('日常台词试听','daily-sample.wav'),('完整试听','voice-v3-all-lines.wav')]:table+=['['+label+']('+str(OUT/file).replace('\\','/')+')','']
    (OUT.parent/'台词和试听.md').write_text('\n'.join(table),encoding='utf8')
    credits='''# 域外魔导师：正式角色语音 V3

用户于2026-10-19明确通过陨灭杀试听，并授权其他日常语言和招式统一采用该声线。
批准母带：v2/faster-higher-tail/ultimate-faster-higher-tail2s.wav
SHA256：'''+APPROVED_SHA+'''

模型：Qwen3-TTS-12Hz-1.7B-Base。使用已通过母带前3.1秒、本项目自行合成的角色声音作为统一参考，没有真实个人录音。
初始合成来源为Qwen3-TTS CustomVoice预置日语声线Ono_Anna；正式全套由Base的参考音频/文本流程生成，保持人物声线。
官方项目：https://github.com/QwenLM/Qwen3-TTS
官方Base模型：https://huggingface.co/Qwen/Qwen3-TTS-12Hz-1.7B-Base
官方镜像：https://modelscope.cn/models/Qwen/Qwen3-TTS-12Hz-1.7B-Base
模型及工具包许可Apache-2.0。下载文件SHA256清单位于build/archmage-tts/model-base/source-files.json。

全套17个语音事件、16个独立录音：交战、普通两招、大招宣言、吟唱、正式喊招、击杀、死亡及地面/巡游/起飞/落地日常。
陨灭杀两个备用事件都使用用户批准的同一母带，避免随机播出已否决版本；WAV逐字节保持母带不变。
普通与日常录音仅做带限、去静音和音量处理，未统一拉高音调；对应情绪由语句及参考声音自然生成。
陨灭杀维持已批准的后半段1.15倍速、+1.5半音，共振峰保留，末字2秒渐弱扩散回响。
该历史后处理使用Rubber Band4.0 R3，官方：https://www.breakfastquay.com/rubberband/ 。GPL工具、许可及来源在build/archmage-tts/rubberband；工具不打包进模组。

每句对应眉眼/嘴角表演，录音20Hz包络驱动嘴型并保留自然眨眼；陨灭杀使用干声包络，回响期间合口。
日常台词仅在无战斗目标时低频播放；战斗台词可立即打断日常，大招喊招/死亡有最高优先级，只打断同一BOSS。
狂风、雷声和施法声音会避让人声。狂风由本项目自行合成，generate_ritual_wind.py可重建。

生成入口：tools/outsider/generate_archmage_voice_v3.py；--package-only仅重新打包已生成录音。
运行环境：build/archmage-tts/venv，PyTorch2.8+cu128、qwen-tts0.1.1；模型和GPU依赖只用于构建，不进入游戏JAR。
运行时仅加载17条单声道24kHz OGG、小型口型数据和生成的时长表。旧候选与已否决录音保留在voice/v1、voice/v2，不能用于发布。
台词、时长和声线参照哈希：v3/lines.json；批准记录：v3/approval.json；完整试听：v3/voice-v3-all-lines.wav。
'''
    (OUT.parent/'CREDITS.md').write_text(credits,encoding='utf8')
    (ROOT/'src/main/resources/META-INF/licenses/outsider-japanese-voice.md').write_text(credits,encoding='utf8')
    print('VOICE_V3_PACKAGED',json.dumps(records,ensure_ascii=False),flush=True)

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--package-only',action='store_true');args=parser.parse_args()
    assert hashlib.sha256(APPROVED.read_bytes()).hexdigest()==APPROVED_SHA,'Approved master changed'
    OUT.mkdir(exist_ok=True)
    for name in ['release_a','release_b']:shutil.copy2(APPROVED,OUT/(name+'.wav'))
    approved,sr=sf.read(APPROVED);sf.write(OUT/'character-reference.wav',approved[:round(3.1*sr)],sr,subtype='PCM_16')
    (OUT/'approval.json').write_text(json.dumps(dict(approved=True,date='2026-10-19',master_sha256=APPROVED_SHA,
        user_instruction='可以，通过允许使用，其他日常语言和招式都换成这个模型的声音',reference='character-reference.wav'),ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    if not args.package_only:
        import torch
        from qwen_tts import Qwen3TTSModel
        torch.set_num_threads(4)
        model=Qwen3TTSModel.from_pretrained(str(ROOT/'build/archmage-tts/model-base'),device_map='cuda:0',dtype=torch.bfloat16,attn_implementation='sdpa')
        prompt=model.create_voice_clone_prompt(ref_audio=str(OUT/'character-reference.wav'),ref_text='これが、伝説の超位魔法！',x_vector_only_mode=False)
        for i,(name,text,zh) in enumerate(LINES):
            path=OUT/(name+'.wav')
            if path.exists():continue
            torch.manual_seed(120310+i);print('GENERATING',name,zh,flush=True);started=time.time()
            wavs,sr=model.generate_voice_clone(text=text,language='Japanese',voice_clone_prompt=prompt,
                temperature=.70,top_p=.90,repetition_penalty=1.10,max_new_tokens=512,non_streaming_mode=True)
            raw=np.asarray(wavs[0]);assert sr==24000 and len(raw)<sr*15
            sf.write(OUT/(name+'.raw.wav'),raw,sr,subtype='PCM_16')
            audio=clean(raw,sr);sf.write(path,audio,sr,subtype='PCM_16')
            print('GENERATED',name,'seconds',round(len(audio)/sr,3),'inference',round(time.time()-started,1),flush=True)
    package()

if __name__=='__main__':main()
