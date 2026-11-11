"""Keep a stable candidate only after matching its entries to silent native runs."""
from pathlib import Path
import argparse
import hashlib
import json
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'build/eastern-rite'
BASE = ROOT / 'build/staff-time/sourceofmystery-time-magic-607386a2ce7a.jar'


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--run', required=True)
    parser.add_argument('--shader-run')
    parser.add_argument('--build-log', required=True)
    args = parser.parse_args()
    assert sha(BASE) == '607386a2ce7ac2f94e08c9ba89968875cb394672d922df8a2f9d76fe2897d7e4'
    assert 'BUILD SUCCESSFUL' in (OUT / args.build_log).read_text(encoding='utf-8')
    jar = ROOT / 'build/libs/sourceofmystery-1.0.0.jar'
    runs = [ROOT / 'build/eastern-rite-smoke' / name for name in [args.run, args.shader_run] if name]
    reports = []
    for run in runs:
        result = json.loads((run / 'eastern-verification.json').read_text(encoding='utf-8'))
        assert result['master_volume'] == 0
        for key, value in result.items():
            if isinstance(value, bool) and key not in ['real_two_client_multiplayer', 'shader_enabled']:
                assert value, key
        log = (run / 'launch.log').read_text(encoding='utf-8', errors='replace')
        assert 'EASTERN_RITE_OK' in log and 'AssertionError' not in log and 'GL_INVALID_' not in log
        if run.name == args.shader_run:
            assert result['shader_enabled'] and 'Using shaderpack: ComplementaryReimagined_r5.9.3.zip' in log
        else:
            assert not result['shader_enabled']
        with zipfile.ZipFile(jar) as release, zipfile.ZipFile(run / 'mods/sourceofmystery-shader-check.jar') as tested:
            assert all(release.read(n) == tested.read(n) for n in release.namelist() if not n.endswith('/'))
        result['evidence'] = str(run.relative_to(ROOT))
        reports.append(result)
    with zipfile.ZipFile(BASE) as before, zipfile.ZipFile(jar) as after:
        old = {n for n in before.namelist() if not n.endswith('/')}
        new = {n for n in after.namelist() if not n.endswith('/')}
        assert not old - new
        assert not any(any(t in n for t in ['RiteCheck', 'MagicCheck', 'MovieCapture', 'eastern_torii', 'gray_orb_script']) for n in new)
        changed = {n for n in old if before.read(n) != after.read(n)}
        allowed = {'assets/sourceofmystery/lang/zh_cn.json', 'assets/sourceofmystery/lang/en_us.json',
                   'assets/sourceofmystery/sounds.json',
                   'assets/sourceofmystery/animations/item/dimension_breaker_staff.animation.json'}
        assets = {n for n in old if n.startswith('assets/')}
        assert not (assets & changed) - allowed, sorted((assets & changed) - allowed)
        for name in new:
            if name.endswith('.json'):
                json.loads(after.read(name))
        for name in allowed:
            a, b = json.loads(before.read(name)), json.loads(after.read(name))
            if '/animations/' in name:
                a, b = a['animations'], b['animations']
            assert all(k in b and b[k] == v for k, v in a.items()), name
    digest = sha(jar)
    candidate = OUT / f'sourceofmystery-eastern-rite-{digest[:12]}.jar'
    if candidate.exists():
        assert sha(candidate) == digest
    else:
        shutil.copy2(jar, candidate)
    result = {
        'status': 'EASTERN_RITE_NATIVE_BEHAVIOR_PASS_VISUAL_ACCEPTANCE_PENDING',
        'source_base': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
        'protocol': 14,
        'jar': {'path': str(candidate.relative_to(ROOT)), 'sha256': digest, 'bytes': candidate.stat().st_size},
        'native_regressions': reports,
        'all_production_entries_match_tested_runs': True,
        'production_entries': len(new), 'preserved_old_entries': len(old - changed),
        'preserved_old_assets': len(assets - changed),
        'changed_old_entries': sorted(changed), 'added_entries': sorted(new - old),
        'test_classes_excluded': True, 'rejected_torii_mesh_excluded': True,
        'installed': False, 'pushed_to_github': False,
        'limits': [
            'Appearance awaits user acceptance. Reference video inaccessible; supplied screenshot was used.',
            'Silent native integrated Forge client; not actual two-client multiplayer or a full-modpack test.',
            'Damage and release use real game entities. Repeated collision probes skip the already-recorded CG warmup.',
            'The fixture loads the full path; production does not force-load chunks.',
            'Video is encoded at 60 fps; actual frame times are reported separately.',
            'No subjective sound acceptance; master volume remains zero.'
            ,'Inherited Oculus shader-uniform and Embeddium mapping/taint warnings remain in the shader run.'
            ,'The newest nested white sphere and ribbon shader run failed before casting due to native/graphics memory allocation; its no-shader run passed.'
        ]
    }
    for path in [OUT / 'release-verification.json', ROOT / 'docs/releases/2026-11-09-eastern-rite.json']:
        path.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({k: result[k] for k in ['status', 'jar', 'production_entries', 'preserved_old_assets']}, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
