"""Verify native crosshair/meteor runs and their exact production entries. Never installs a mod."""
from pathlib import Path
import hashlib
import json
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[2]


def digest(path):
    h = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            h.update(block)
    return h.hexdigest()


def main():
    production = ROOT / 'build/libs/sourceofmystery-1.0.0.jar'
    baseline = ROOT / 'build/staff-domain-candidate/sourceofmystery-1.0.0.jar'
    assert digest(baseline) == 'b4bf4fb0b524eb2541886fd05ecec92e7b7a44bc8961b85b4ca8adb93296a08c'
    expected = {
        'assets/sourceofmystery/lang/en_us.json', 'assets/sourceofmystery/lang/zh_cn.json',
        'com/sourceofmystery/entity/OutsiderSpell.class', 'com/sourceofmystery/network/ModNetwork.class',
        'com/sourceofmystery/staff/StaffCasting$Cast.class', 'com/sourceofmystery/staff/StaffCasting.class',
        'sourceofmystery.mixins.json', 'sourceofmystery.refmap.json'
    }
    checks = [
        'target_switch_resets_dwell', 'solid_occlusion_and_area_selection',
        'forty_tick_lock_and_private_red_outline', 'miss_clears_lock_without_auto_selection',
        'in_flight_target_updates_and_uuid_stability', 'moving_impact_and_old_area_untouched',
        'area_cast_has_no_homing_target', 'fixed_area_and_no_target_no_cost',
        'dead_target_falls_to_last_position', 'switch_spell_cleanup'
    ]
    runs = []
    with zipfile.ZipFile(production) as prod, zipfile.ZipFile(baseline) as old:
        entries = [n for n in prod.namelist() if not n.endswith('/')]
        assert not any('SmokeCheck' in n or 'MovieCapture' in n or 'VisualCheck' in n for n in entries)
        changed = {n for n in old.namelist() if not n.endswith('/') and prod.read(n) != old.read(n)}
        assert changed == expected, changed ^ expected
        for lang in ['zh_cn', 'en_us']:
            name = 'assets/sourceofmystery/lang/' + lang + '.json'
            a, b = json.loads(old.read(name)), json.loads(prod.read(name))
            assert all(b[k] == v for k, v in a.items())
        name = 'sourceofmystery.mixins.json'
        a, b = json.loads(old.read(name)), json.loads(prod.read(name))
        assert a['mixins'] == b['mixins'] and set(a['client']).issubset(b['client'])
        assert set(b['client']) - set(a['client']) == {'MeteorAimColorMixin', 'MeteorAimGlowMixin'}
        name = 'sourceofmystery.refmap.json'
        a, b = json.loads(old.read(name)), json.loads(prod.read(name))
        assert all(b['mappings'][k] == v for k, v in a['mappings'].items())
        for name in entries:
            if name.endswith('.json'):
                json.loads(prod.read(name))
        for label in ['final-native', 'area-final-shaders']:
            directory = ROOT / 'build/staff-meteor-smoke' / label
            result = json.loads((directory / 'meteor-verification.json').read_text(encoding='utf-8'))
            assert all(result[key] is True for key in checks)
            assert result['master_volume'] == 0
            assert result['shipped_default_cast_ticks'] == 480 and result['fixture_cast_ticks'] == 60
            log = (directory / 'launch.log').read_text(encoding='utf-8')
            assert 'STAFF_METEOR_OK' in log and 'AssertionError' not in log
            with zipfile.ZipFile(directory / 'mods/sourceofmystery-shader-check.jar') as test:
                assert all(prod.read(n) == test.read(n) for n in entries), label
            runs.append({'directory': str(directory), 'all_production_entries_identical': True, 'result': result})
    assert not runs[0]['result']['shader_enabled'] and runs[1]['result']['shader_enabled']
    installed = []
    for instance in ['Integrated MC', '1.20.1-Forge_47.4.20']:
        path = Path('F:/AAAAmc/versions') / instance / 'mods/sourceofmystery-1.0.0.jar'
        with zipfile.ZipFile(path) as jar:
            present = 'com/sourceofmystery/staff/MeteorTargeting.class' in jar.namelist()
        installed.append({'path': str(path), 'sha256': digest(path), 'targeting_feature_present': present, 'written_by_this_task': False})
    report = {
        'status': 'TECHNICAL_CHECKS_PASS_APPEARANCE_PENDING_USER',
        'source_base': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
        'production_jar': {'path': str(production), 'sha256': digest(production), 'bytes': production.stat().st_size},
        'production_entry_count': len(entries), 'existing_entries_changed': sorted(changed),
        'old_models_sounds_environment_domain_and_boss_ai_preserved': True,
        'test_classes_excluded': True, 'target_range_blocks': 128, 'lock_ticks': 40,
        'protocol_version': 11, 'native_runs': runs, 'installed_instances': installed,
        'limits': [
            'Owner-only entity outline and unchanged server glowing/team state verified in one actual client; real two-client privacy acceptance not run.',
            'Fixture cast time is 60 ticks; default 480 ticks and original audio/model resources are unchanged.',
            'Full modpack and subjective audio acceptance not run; both native runs are muted.',
            'Inherited world template and Oculus/Embeddium warnings remain; no zero-error-log claim.'
        ]
    }
    path = ROOT / 'build/staff-meteor-targeting/release-verification.json'
    path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps({'status': report['status'], 'production': report['production_jar'], 'native_runs': len(runs)}, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
