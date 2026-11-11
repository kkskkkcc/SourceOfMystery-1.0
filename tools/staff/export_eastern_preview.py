"""Publish native captures to the project's central asset directory without altering them."""
from pathlib import Path
import hashlib
import json
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[2]
RUN = ROOT / 'build/eastern-rite-smoke/eastern-white-ribbons-native'
EXPORT = Path('F:/ChatGPT/我的世界神秘之源模组开发/modeling/eastern_rite/export/preview-20261109-white-ribbons')


def main():
    report = json.loads((ROOT / 'build/eastern-rite/release-verification.json').read_text(encoding='utf-8'))
    native = json.loads((RUN / 'eastern-verification.json').read_text(encoding='utf-8'))
    assert native['master_volume'] == 0 and native['camera_returns_at_launch_orb_continues']
    EXPORT.mkdir(parents=True, exist_ok=True)
    import sys
    sys.path.insert(0, str(ROOT / 'build/offering-preview-deps'))
    import imageio_ffmpeg
    ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()
    files = list((RUN / 'screenshots').glob('*.png')) + [RUN / name for name in ['eastern-particle-cg-silent.mp4', 'eastern-flight-silent.mp4']]
    manifest = []
    for source in files:
        digest = hashlib.sha256(source.read_bytes()).hexdigest()
        target = EXPORT / source.name
        if target.exists():
            assert hashlib.sha256(target.read_bytes()).hexdigest() == digest
        else:
            shutil.copy2(source, target)
        if source.suffix == '.mp4':
            info = subprocess.run([ffmpeg, '-hide_banner', '-i', str(target)], capture_output=True, text=True, encoding='utf-8', errors='replace').stderr
            assert 'Audio:' not in info and 'Video: h264' in info
            subprocess.run([ffmpeg, '-v', 'error', '-i', str(target), '-f', 'null', '-'], stdout=subprocess.DEVNULL, check=True)
        manifest.append({'path': str(target), 'sha256': digest, 'bytes': target.stat().st_size})
    output = {'source': str(RUN), 'native_forge_capture': True, 'shader_enabled': False,
              'production_jar': report['jar'], 'cg_seconds': native['cg_seconds'],
              'video_encoding_fps': 60, 'actual_render_frame_ms_p50': native['render_frame_ms_p50'],
              'actual_render_frame_ms_p95': native['render_frame_ms_p95'],
              'visual_acceptance': 'pending user review', 'files': manifest}
    (EXPORT / 'preview-info.json').write_text(json.dumps(output, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'preview': str(EXPORT), 'files': len(files)}, ensure_ascii=False))


if __name__ == '__main__':
    main()
