"""Match the release to its silent native run and retain an uninstalled candidate."""
from pathlib import Path
import hashlib
import json
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'build/staff-time'
RUN = ROOT / 'build/staff-time-smoke/time-native-final'
BASE = ROOT / 'build/staff-environment-overwrite/sourceofmystery-environment-overwrite-bd0a88439669.jar'


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    assert digest(BASE) == 'bd0a88439669c0549c408d8521d4afc430d0189aff1cdc922b44d8c349034113'
    native = json.loads((RUN / 'time-magic-verification.json').read_text(encoding='utf-8'))
    assert native['master_volume'] == 0
    for key, value in native.items():
        if isinstance(value, bool):
            assert value, key
    for mode, count in [('stop', 0), ('slow', 10), ('haste', 500)]:
        row = native[mode]
        assert row['server_ticks'] == row['player_ticks'] == row['other_dimension_ticks'] == 100
        assert row['observer_player_ticks'] == (0 if mode == 'stop' else 100)
        assert row['world_ticks'] == row['mob_ticks'] == row['weather_ticks'] == count
        assert abs(row['furnace_progress'] - count) <= 12
        assert row['scheduled_redstone'] and row['forge_level_hooks']
    log = (RUN / 'launch.log').read_text(encoding='utf-8')
    assert 'STAFF_TIME_MAGIC_OK' in log and 'AssertionError' not in log and 'GL_INVALID_' not in log
    assert 'BUILD SUCCESSFUL' in (OUT / 'build-final.log').read_text(encoding='utf-8')
    jar = ROOT / 'build/libs/sourceofmystery-1.0.0.jar'
    with zipfile.ZipFile(BASE) as before, zipfile.ZipFile(jar) as after, zipfile.ZipFile(RUN / 'mods/sourceofmystery-shader-check.jar') as actual:
        old = {n for n in before.namelist() if not n.endswith('/')}
        new = {n for n in after.namelist() if not n.endswith('/')}
        assert not old - new
        changed = {n for n in old if before.read(n) != after.read(n)}
        assert all(after.read(n) == actual.read(n) for n in new)
        assert not any('SmokeCheck' in n or 'TimeMagicCheck' in n or 'MovieCapture' in n for n in new)
        assets = {n for n in old if n.startswith('assets/') and '/lang/' not in n}
        assert not assets & changed, sorted(assets & changed)
        for name in new:
            if name.endswith('.json'):
                json.loads(after.read(name))
        for lang in ['en_us', 'zh_cn']:
            name = f'assets/sourceofmystery/lang/{lang}.json'
            a, b = json.loads(before.read(name)), json.loads(after.read(name))
            assert a.keys() <= b.keys()
            assert {k for k in a if a[k] != b[k]} == {'message.sourceofmystery.staff.time_cooldown'}
    sha = digest(jar)
    candidate = OUT / f'sourceofmystery-time-magic-{sha[:12]}.jar'
    if candidate.exists():
        assert digest(candidate) == sha
    else:
        shutil.copy2(jar, candidate)
    result = {
        'status': 'TIME_MAGIC_NATIVE_REGRESSION_PASS',
        'source_base': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
        'protocol': 13,
        'production_jar': {'path': str(candidate), 'sha256': sha, 'bytes': candidate.stat().st_size},
        'native_regression': native,
        'native_directory': str(RUN),
        'all_production_entries_match_native_run': True,
        'production_entries': len(new),
        'preserved_old_entries': len(old - changed),
        'preserved_non_language_assets': len(assets),
        'changed_old_entries': sorted(changed),
        'added_entries': sorted(new - old),
        'test_classes_excluded': True,
        'installed': False,
        'pushed_to_github': False,
        'limits': [
            'One silent native Forge client, with a server-side FakePlayer observer; not actual two-client multiplayer.',
            'Active clocks were exercised in the overworld, with Nether clock isolation checks.',
            'No shader-enabled or full modpack regression in this run.',
            'Network and essential chunk IO remain normal. Independent real-time/global mod tasks are not universally controlled.',
            'Inherited template and Embeddium/Oculus warnings and FakePlayer info warnings remain.'
        ]
    }
    (OUT / 'release-verification.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    public = {k: v for k, v in result.items() if k not in ['production_jar', 'native_directory']}
    public['jar'] = {'path': candidate.relative_to(ROOT).as_posix(), 'sha256': sha, 'bytes': candidate.stat().st_size}
    public['local_runtime_evidence'] = (RUN / 'time-magic-verification.json').relative_to(ROOT).as_posix()
    public['local_release_audit'] = 'build/staff-time/release-verification.json'
    (ROOT / 'docs/releases/2026-11-08-time-magic.json').write_text(json.dumps(public, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({k: result[k] for k in ['status', 'production_jar', 'production_entries', 'preserved_old_entries', 'preserved_non_language_assets']}, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
