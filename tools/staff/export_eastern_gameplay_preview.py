"""Keep the new no-CG native recording beside the existing, clearly older previews."""
from pathlib import Path
import hashlib
import json
import shutil
import subprocess
import sys

ROOT=Path(__file__).resolve().parents[2]
RUN=ROOT/'build/eastern-rite-smoke/gameplay-execution-native'
EXPORT=Path('F:/ChatGPT/我的世界神秘之源模组开发/modeling/eastern_rite/export/preview-20261109-gameplay')
def main():
    release=json.loads((ROOT/'build/eastern-gameplay-execution/release-verification.json').read_text('utf8'))
    native=json.loads((RUN/'eastern-verification.json').read_text('utf8'))
    assert native['master_volume']==0 and native['no_eastern_camera_takeover']
    sys.path.insert(0,str(ROOT/'build/offering-preview-deps'))
    import imageio_ffmpeg
    ffmpeg=imageio_ffmpeg.get_ffmpeg_exe()
    EXPORT.mkdir(parents=True,exist_ok=True);manifest=[]
    for source in list((RUN/'screenshots').glob('*.png'))+[RUN/'eastern-gameplay-silent.mp4']:
        digest=hashlib.sha256(source.read_bytes()).hexdigest();target=EXPORT/source.name
        if target.exists():assert hashlib.sha256(target.read_bytes()).hexdigest()==digest
        else:shutil.copy2(source,target)
        if target.suffix=='.mp4':
            info=subprocess.run([ffmpeg,'-hide_banner','-i',str(target)],capture_output=True,text=True,encoding='utf8',errors='replace').stderr
            assert 'Audio:' not in info and 'Video: h264' in info and '00:00:24.00' in info
            subprocess.run([ffmpeg,'-v','error','-i',str(target),'-f','null','-'],stdout=subprocess.DEVNULL,check=True)
        manifest.append({'path':str(target),'sha256':digest,'bytes':target.stat().st_size})
    report={'source':str(RUN),'production_jar':release['jar'],'native_forge_capture':True,'shader_enabled':False,
            'cg':False,'fixture_camera_note':'Only the fixture switches F5 view to show the ordinary third-person staff; the spell never owns the camera.',
            'seconds':24,'video_encoding_fps':60,'actual_render_frame_ms_p50':native['render_frame_ms_p50'],
            'actual_render_frame_ms_p95':native['render_frame_ms_p95'],'visual_acceptance':'pending user review','files':manifest}
    (EXPORT/'preview-info.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n','utf8')
    print(json.dumps({'preview':str(EXPORT),'files':len(manifest)},ensure_ascii=False))
if __name__=='__main__':main()
