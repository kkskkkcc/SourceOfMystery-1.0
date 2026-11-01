"""Encode actual Forge screenshots with their game-clock timestamps (no mock frames)."""
from pathlib import Path
import argparse,subprocess,sys,json
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'build/offering-preview-deps'))
import imageio_ffmpeg

def main():
    p=argparse.ArgumentParser();p.add_argument('label');p.add_argument('--output',default='outsider-intro-ingame.mp4');a=p.parse_args()
    source=ROOT/'build/offering-smoke'/a.label/'screenshots'
    verification=json.loads((source.parent/'verification.json').read_text())
    fps=verification.get('capture_fps',10)
    video=verification.get('video_capture',False)
    duration=verification.get('duration_seconds',32)
    frames=sorted(source.glob('intro-*.png'))
    if not video:assert len(frames)>250
    out=ROOT/'build/offering-preview';out.mkdir(exist_ok=True)
    command=[imageio_ffmpeg.get_ffmpeg_exe(),'-hide_banner','-loglevel','error','-y']
    if video:
        command+=['-i',str(source.parent/'capture-silent.mp4')]
    else:
        lines=[]
        for i,f in enumerate(frames):
            tick=int(f.stem.split('-')[-1]);next_tick=int(frames[i+1].stem.split('-')[-1]) if i+1<len(frames) else duration*fps
            lines+=['file '+"'"+f.as_posix()+"'",'option framerate 30','duration '+str(max(1,next_tick-tick)/fps)]
        lines+=['file '+"'"+frames[-1].as_posix()+"'",'option framerate 30']
        manifest=out/(a.label+'.ffconcat');manifest.write_text('\n'.join(lines),encoding='utf-8')
        command+=['-f','concat','-safe','0','-i',str(manifest)]
    audio=ROOT/'src/main/resources/assets/sourceofmystery/sounds/archmage'
    for name in ['void_wake','void_break','heel_step_01','heel_step_02','heel_step_03','finger_snap','void_seal']:command+=['-i',str(audio/(name+'.ogg'))]
    filters=['[1:a]volume=0.55,adelay=8000:all=1[w]','[2:a]volume=0.90,adelay=13000:all=1[b]','[3:a]volume=0.90,asplit=2[h1][h4]',
             '[h1]adelay=15900:all=1[s1]','[4:a]volume=0.90,adelay=18200:all=1[s2]','[5:a]volume=0.90,adelay=20200:all=1[s3]','[h4]adelay=22000:all=1[s4]',
             '[6:a]volume=0.95,adelay=24900:all=1[snap]','[7:a]volume=0.70,adelay=25150:all=1[seal]',
             '[w][b][s1][s2][s3][s4][snap][seal]amix=inputs=8:normalize=0,apad,atrim=duration='+str(duration)+'[audio]']
    command+=['-filter_complex',';'.join(filters),'-map','0:v','-map','[audio]','-t',str(duration)]
    command+=['-c:v','copy'] if video else ['-r','30','-c:v','libx264','-crf','19','-pix_fmt','yuv420p']
    command+=['-c:a','aac','-b:a','160k','-movflags','+faststart',str(out/a.output)]
    subprocess.run(command,check=True)
    report={'source':'real Forge 1.20.1 client PBO/NVENC capture' if video else 'real Forge 1.20.1 client screenshots','capture':a.label,'output':a.output,'frames':duration*60 if video else len(frames),'duration_seconds':duration,'capture_fps':fps,'timestamp_source':'game ticks','audio':'shipped CC0 OGG cues synchronized to server timings and cinematic gains; heel variants selected for preview; not system audio recording','interpolation':False,'missed_frame_policy':'repeat a captured frame to preserve game-clock timing; no generated frames'}
    performance=source.parent/'performance.json'
    if performance.exists():report['measured_performance']=json.loads(performance.read_text())
    (out/'capture-source.json').write_text(json.dumps(report,indent=2))
    print(out/a.output)

if __name__=='__main__':main()
