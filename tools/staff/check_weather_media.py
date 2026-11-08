"""Decode original wind audio and native preview without playing any sound."""
from pathlib import Path
import hashlib
import json
import subprocess
import sys
import wave
import numpy as np

ROOT=Path(__file__).resolve().parents[2]
HOME=Path('F:/ChatGPT/我的世界神秘之源模组开发/modeling/staff_weather')
sys.path.insert(0,str(ROOT/'build/offering-preview-deps'))
import imageio_ffmpeg


def main():
    ffmpeg=imageio_ffmpeg.get_ffmpeg_exe()
    audio={}
    for name in ['normal','sand','water','fire']:
        path=ROOT/f'src/main/resources/assets/sourceofmystery/sounds/weather/{name}.ogg'
        decoded=subprocess.run([ffmpeg,'-v','error','-i',str(path),'-f','f32le','-acodec','pcm_f32le','-'],check=True,capture_output=True)
        samples=np.frombuffer(decoded.stdout,dtype='<f4')
        assert len(samples)>170000 and np.isfinite(samples).all()
        peak=float(np.max(np.abs(samples)));rms=float(np.sqrt(np.mean(samples**2)))
        assert peak<1 and .01<rms<.5
        original=HOME/f'source/tornado-{name}.wav'
        with wave.open(str(original)) as wav:assert wav.getnframes()==176400 and wav.getnchannels()==1 and wav.getframerate()==22050
        audio[name]={'ogg_sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'decoded_samples':len(samples),'decoded_peak':peak,'decoded_rms':rms,'original_wav_seconds':8}
    video=HOME/'export/weather-tornadoes-preview-silent.mp4'
    frames,seconds=imageio_ffmpeg.count_frames_and_secs(str(video))
    assert frames==960 and abs(seconds-16)<.1,(frames,seconds)
    result=subprocess.run([ffmpeg,'-hide_banner','-i',str(video),'-f','null','-'],capture_output=True,text=True)
    assert result.returncode==0 and 'Audio:' not in result.stderr
    assert '1280x800' in result.stderr
    report={'status':'MEDIA_DECODE_PASS_SUBJECTIVE_AUDIO_NOT_TESTED','audio':audio,
            'video':{'path':str(video),'sha256':hashlib.sha256(video.read_bytes()).hexdigest(),'decoded_frames':frames,'seconds':seconds,'size':[1280,800],'audio_stream':False,'encoded_fps':60,'native_fps_claim':False},
            'audio_played':False,'limits':'Encoding at 60fps does not establish native real-time performance or subjective audiovisual acceptance.'}
    (ROOT/'build/staff-weather/media-verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(report,ensure_ascii=False,indent=2))


if __name__=='__main__':main()
