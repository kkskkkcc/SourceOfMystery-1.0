"""Match environment queue and meteor latch fixes to their silent native runs."""
from pathlib import Path
import hashlib
import json
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'build/staff-followup-fixes'
BASE = ROOT / 'build/eastern-rite/sourceofmystery-eastern-rite-0163871b553e.jar'
RUNS = [
    ('build/staff-environment-overwrite-smoke/queue-native', 'environment-overwrite-verification.json', 'STAFF_ENVIRONMENT_OVERWRITE_OK'),
    ('build/staff-meteor-smoke/sticky-lock-native', 'meteor-verification.json', 'STAFF_METEOR_OK'),
]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    assert sha(BASE) == '0163871b553e2d5cc0dab86dd601090a5894dec0f5c7062927268a77695c182a'
    assert 'BUILD SUCCESSFUL' in (OUT / 'build-fixes.log').read_text(encoding='utf-8')
    jar = ROOT / 'build/libs/sourceofmystery-1.0.0.jar'
    native = []
    for directory, filename, marker in RUNS:
        run = ROOT / directory
        result = json.loads((run / filename).read_text(encoding='utf-8'))
        assert result['master_volume'] == 0
        assert all(v for k, v in result.items() if isinstance(v, bool) and k != 'shader_enabled')
        log = (run / 'launch.log').read_text(encoding='utf-8', errors='replace')
        assert marker in log and 'AssertionError' not in log and 'GL_INVALID_' not in log
        with zipfile.ZipFile(jar) as release, zipfile.ZipFile(run / 'mods/sourceofmystery-shader-check.jar') as tested:
            assert all(release.read(n) == tested.read(n) for n in release.namelist() if not n.endswith('/'))
        native.append({'evidence': directory, 'checks': result})
    with zipfile.ZipFile(BASE) as before, zipfile.ZipFile(jar) as after:
        old = {n for n in before.namelist() if not n.endswith('/')}
        new = {n for n in after.namelist() if not n.endswith('/')}
        assert old == new
        changed = {n for n in old if before.read(n) != after.read(n)}
        allowed = {'com/sourceofmystery/client/EasternGeometry.class', 'com/sourceofmystery/client/MeteorAimClient.class'}
        assert all(n in allowed or n.startswith('com/sourceofmystery/staff/MeteorTargeting') or n.startswith('com/sourceofmystery/staff/StaffEnvironment') for n in changed), sorted(changed)
        assets = {n for n in old if n.startswith('assets/')}
        assert not assets & changed
    javap = Path('C:/Program Files/Eclipse Adoptium/jdk-17.0.19.10-hotspot/bin/javap.exe')
    constants = subprocess.check_output([str(javap), '-private', '-constants', '-classpath', str(jar), 'com.sourceofmystery.client.EasternGeometry'], text=True)
    assert 'CORE_RADIUS = 10.0f' in constants and 'SHELL_SCALE = 1.16f' in constants
    digest = sha(jar)
    candidate = OUT / f'sourceofmystery-staff-fixes-{digest[:12]}.jar'
    if candidate.exists():
        assert sha(candidate) == digest
    else:
        shutil.copy2(jar, candidate)
    report = {'status': 'ENVIRONMENT_QUEUE_AND_METEOR_LATCH_NATIVE_PASS', 'protocol': 14,
              'source_base': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
              'jar': {'path': candidate.relative_to(ROOT).as_posix(), 'sha256': digest, 'bytes': candidate.stat().st_size},
              'native_regressions': native, 'production_entries': len(new), 'changed_entries': sorted(changed),
              'preserved_asset_entries': len(assets), 'production_entries_match_both_runs': True,
              'compiled_core_radius': 10, 'compiled_shell_scale': 1.16,
              'installed': False, 'pushed_to_github': False,
              'limits': ['Silent native clients without shaders; not full-modpack or two-client multiplayer.',
                         'Queue admission used radius500; processing and queue promotion used smaller saved jobs.',
                         'Meteor fixture charges for60 ticks; shipped charge stays480 ticks.',
                         'The radius change is compiled and audited; the cinematic preview was not re-recorded.']}
    for path in [OUT / 'release-verification.json', ROOT / 'docs/releases/2026-11-09-staff-followup-fixes.json']:
        path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({k: report[k] for k in ['status', 'jar', 'changed_entries', 'preserved_asset_entries']}, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
