"""Audit actual native domain runs and production bytes. Does not install or publish anything."""
from pathlib import Path
import hashlib
import json
import runpy
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[2]
ASSET_HOME = Path('F:/ChatGPT/我的世界神秘之源模组开发/modeling/staff_domains')


def digest(path):
    value = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            value.update(block)
    return value.hexdigest()


def read_json(path):
    return json.loads(path.read_text(encoding='utf-8'))


def main():
    production = ROOT / 'build/libs/sourceofmystery-1.0.0.jar'
    full = ROOT / 'build/staff-domain-smoke/final-full'
    visual = [ROOT / 'build/staff-domain-smoke/actor-final-vanilla',
              ROOT / 'build/staff-domain-smoke/actor-final-shaders']
    full_result = read_json(full / 'domain-verification.json')
    assert full_result['real_5_minute_expiry'] is True
    required = ['five_shift_right_click_choices', 'frost_root_and_3hp_per_second',
                'moving_domain_membership', 'block_break_cancellation_respected',
                'unloaded_debris_cleanup', 'gravity_lifts_and_glows', 'gravity_cleanup',
                'cg_10_seconds_restore_staff_and_immunity', 'cg_early_stop_and_stale_token_guard',
                'binding_fixed_diameter_200']
    reports = []
    with zipfile.ZipFile(production) as prod:
        entries = [n for n in prod.namelist() if not n.endswith('/')]
        assert not any('SmokeCheck' in n or 'VisualCheck' in n or 'MovieCapture' in n for n in entries)
        for directory in [full] + visual:
            result = read_json(directory / 'domain-verification.json')
            assert all(result[key] is True for key in required), directory
            assert result['master_volume'] == 0
            assert result['cg_measured_max_fov'] == 130
            assert 4.5 < result['black_hole_travel_in_one_second'] < 5.6
            assert result['execution_true_damage_through_resistance'] > 0
            assert 'STAFF_DOMAINS_OK' in (directory / 'launch.log').read_text(encoding='utf-8')
            with zipfile.ZipFile(directory / 'mods/sourceofmystery-shader-check.jar') as test:
                changes = [n for n in entries if prod.read(n) != test.read(n)]
            if directory == full:
                # Only client close-up framing/adult actor corrections followed the full duration run.
                assert changes == ['com/sourceofmystery/client/DomainActor.class',
                                   'com/sourceofmystery/client/DomainDirector.class'], changes
            else:
                assert not changes, (directory, changes)
                assert result['cg_face_center_max_angle_degrees'] < 8
            reports.append({'directory': str(directory), 'result': result,
                            'production_entries_differing_from_run': changes})
        assert not reports[1]['result']['shader_enabled'] and reports[2]['result']['shader_enabled']
        runtime = 'assets/sourceofmystery/animations/item/dimension_breaker_staff.animation.json'
        animation = json.loads(prod.read(runtime))
        before_jar = ROOT / 'build/staff-environment-candidate/sourceofmystery-1.0.0.jar'
        with zipfile.ZipFile(before_jar) as old:
            existing_changes = [n for n in old.namelist() if not n.endswith('/') and old.read(n) != prod.read(n)]
            asset_exceptions = {runtime, 'assets/sourceofmystery/lang/en_us.json',
                                'assets/sourceofmystery/lang/zh_cn.json', 'assets/sourceofmystery/sounds.json'}
            old_assets = [n for n in old.namelist() if n.startswith('assets/') and not n.endswith('/') and n not in asset_exceptions]
            assert all(prod.read(n) == old.read(n) for n in old_assets)
            old_animation = json.loads(old.read(runtime))
            assert all(animation['animations'][k] == v for k, v in old_animation['animations'].items())
            preserved = [n for n in old.namelist() if n.startswith((
                'com/sourceofmystery/staff/StaffEnvironment',
                'com/sourceofmystery/staff/EnvironmentPalette',
                'com/sourceofmystery/client/StaffEnvironmentGeometry',
                'com/sourceofmystery/client/StaffArcaneMaterial',
                'com/sourceofmystery/staff/StaffWheelLayout',
                'assets/sourceofmystery/geo/item/dimension_breaker_staff',
                'assets/sourceofmystery/textures/item/dimension_breaker_staff')) and not n.endswith('/')]
            assert preserved and all(prod.read(n) == old.read(n) for n in preserved)
            for name in ['lang/en_us.json', 'lang/zh_cn.json', 'sounds.json']:
                path = 'assets/sourceofmystery/' + name
                old_data = json.loads(old.read(path)); new_data = json.loads(prod.read(path))
                assert all(new_data[k] == v for k, v in old_data.items()), name
        geo = json.loads(prod.read('assets/sourceofmystery/geo/cinematic/domain_shrine.geo.json'))
        cube_count = sum(len(b.get('cubes', [])) for g in geo['minecraft:geometry'] for b in g['bones'])
        assert cube_count == 97
        for name in entries:
            if name.endswith('.json'):
                json.loads(prod.read(name))

    generated = runpy.run_path(str(ROOT / 'tools/staff/generate_assets.py'))['animations']()
    assert generated == animation
    with zipfile.ZipFile(ROOT / 'build/staff-domain/baseline/source-before-domain.zip') as baseline:
        bbpath = 'tools/staff/dimension_breaker_staff.bbmodel'
        before = json.loads(baseline.read(bbpath))
        after = read_json(ROOT / bbpath)
        assert after['animations'][:len(before['animations'])] == before['animations']
        before.pop('animations'); after.pop('animations'); assert before == after
        sources = [n for n in baseline.namelist() if n.endswith((
            'StaffEnvironment.java', 'EnvironmentPalette.java', 'StaffEnvironmentGeometry.java',
            'StaffArcaneMaterial.java', 'StaffWheelLayout.java'))]
        assert len(sources) == 5
        assert all((ROOT / n).read_bytes() == baseline.read(n) for n in sources)

    assert 'BLENDER_DOMAIN_SHRINE_SOURCE_OK' in (ROOT / 'build/staff-domain/blender-source.log').read_text(encoding='utf-8')
    model_files = [ASSET_HOME / 'blender/domain-sanctum.blend', ASSET_HOME / 'export/domain-sanctum-source.glb']
    assert all(p.is_file() and p.stat().st_size > 1000 for p in model_files)
    installed = []
    for instance in ['Integrated MC', '1.20.1-Forge_47.4.20']:
        path = Path('F:/AAAAmc/versions') / instance / 'mods/sourceofmystery-1.0.0.jar'
        with zipfile.ZipFile(path) as jar:
            present = 'com/sourceofmystery/staff/StaffDomains.class' in jar.namelist()
        assert not present
        installed.append({'path': str(path), 'sha256': digest(path),
                          'domain_feature_present': present, 'written_by_this_task': False})

    report = {
        'status': 'TECHNICAL_CHECKS_PASS_APPEARANCE_PENDING_USER',
        'source_base': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
        'all_five_domain_diameter': 200, 'all_five_domain_radius': 100,
        'default_energy_cost': 5000, 'default_duration_ticks': 6000,
        'execution_cg_ticks': 200, 'execution_attacks_front_semicircle': True,
        'production_jar': {'path': str(production), 'sha256': digest(production), 'bytes': production.stat().st_size},
        'production_entry_count': len(entries), 'test_classes_excluded': True,
        'native_tests': reports,
        'five_minute_test_note': 'All final server/function bytes match the full run; only DomainActor.class and DomainDirector.class changed afterward. Both final short runs verify every production entry and close-up framing.',
        'environment_and_old_staff_entries_preserved': preserved,
        'previous_runtime_assets_unchanged': len(old_assets),
        'existing_entry_changes': existing_changes,
        'previous_animation_count_preserved': len(old_animation['animations']),
        'runtime_animation_matches_generator': True,
        'shrine_gecko_cubes': cube_count, 'editable_model_files': [str(p) for p in model_files],
        'installed_instances': installed,
        'limits': ['No full modpack or actual two-client multiplayer acceptance.',
                   'Audio remained muted; subjective audio quality not accepted.',
                   'Native logs retain inherited world mappings and Oculus/Embeddium warnings; no zero-error-log claim.',
                   '60 fps video encoding is not a measured runtime frame-rate guarantee.']
    }
    destination = ROOT / 'build/staff-domain/release-verification.json'
    destination.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps({'status': report['status'], 'production': report['production_jar'],
                      'native_tests': len(reports), 'shrine_cubes': cube_count}, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
