"""Audit actual native windstorms, prior asset preservation and production bytes. Never installs."""
from pathlib import Path
import hashlib
import json
import runpy
import shutil
import subprocess
import zipfile

ROOT=Path(__file__).resolve().parents[2]


def digest(path):
    h=hashlib.sha256()
    with path.open('rb') as f:
        for b in iter(lambda:f.read(1024*1024),b''):h.update(b)
    return h.hexdigest()


def main():
    production=ROOT/'build/libs/sourceofmystery-1.0.0.jar'
    baseline=ROOT/'build/staff-meteor-targeting/sourceofmystery-targeting-1ddf3b23fbda.jar'
    assert digest(baseline)=='1ddf3b23fbda57da1788cca0c4fcc112cab279951e58125bc3d2e4913b6967e7'
    checks=['four_shift_right_click_choices','vanilla_weather_clear','vanilla_weather_rain','vanilla_weather_thunder',
            'block_break_veto_respected','variant_normal_physics_and_damage','real_terrain_debris',
            'variant_sand_physics_and_damage','variant_water_physics_and_damage','variant_fire_physics_and_damage',
            'configured_natural_expiry','early_stop_fade_debris_audio_cleanup','unloaded_debris_recovery_cleanup',
            'ceiling_surface_ignores_roof','effective_clouds_suppressed_without_option_write']
    runs=[]
    with zipfile.ZipFile(production) as prod,zipfile.ZipFile(baseline) as old:
        entries=[n for n in prod.namelist() if not n.endswith('/')]
        assert not any('SmokeCheck' in n or 'VisualCheck' in n or 'MovieCapture' in n for n in entries)
        for label in ['release-native','release-shaders']:
            directory=ROOT/'build/staff-weather-smoke'/label
            result=json.loads((directory/'weather-verification.json').read_text(encoding='utf-8'))
            assert all(result[k] is True for k in checks),(label,result)
            assert result['master_volume']==0 and result['tornado_radius']==100 and result['default_tornado_duration']==6000
            log=(directory/'launch.log').read_text(encoding='utf-8')
            assert 'STAFF_WEATHER_OK' in log and 'AssertionError' not in log
            assert 'GL_INVALID_' not in log,label
            with zipfile.ZipFile(directory/'mods/sourceofmystery-shader-check.jar') as actual:
                assert all(prod.read(n)==actual.read(n) for n in entries),label
            runs.append({'directory':str(directory),'all_production_entries_identical':True,'result':result})
        assert not runs[0]['result']['shader_enabled'] and runs[1]['result']['shader_enabled']
        old_names=[n for n in old.namelist() if not n.endswith('/')]
        changed=[n for n in old_names if old.read(n)!=prod.read(n)]
        exempt={'assets/sourceofmystery/animations/item/dimension_breaker_staff.animation.json',
                'assets/sourceofmystery/lang/zh_cn.json','assets/sourceofmystery/lang/en_us.json','assets/sourceofmystery/sounds.json'}
        assets=[n for n in old_names if n.startswith('assets/') and n not in exempt]
        assert all(old.read(n)==prod.read(n) for n in assets)
        for path in ['assets/sourceofmystery/lang/zh_cn.json','assets/sourceofmystery/lang/en_us.json','assets/sourceofmystery/sounds.json']:
            a,b=json.loads(old.read(path)),json.loads(prod.read(path));assert all(b[k]==v for k,v in a.items()),path
        path='assets/sourceofmystery/animations/item/dimension_breaker_staff.animation.json'
        old_anim=json.loads(old.read(path));animation=json.loads(prod.read(path))
        assert all(animation['animations'][k]==v for k,v in old_anim['animations'].items())
        assert animation['animations']['staff.weather']['animation_length']==3
        mechanics=[n for n in old_names if n.startswith(('com/sourceofmystery/staff/StaffEnvironment',
                   'com/sourceofmystery/staff/StaffDomains','com/sourceofmystery/staff/MeteorTargeting',
                   'com/sourceofmystery/entity/OutsiderSpell','com/sourceofmystery/entity/OutsiderEntity'))]
        assert all(old.read(n)==prod.read(n) for n in mechanics)
        for name in entries:
            if name.endswith('.json'):json.loads(prod.read(name))
        for name in ['sourceofmystery.mixins.json','sourceofmystery.refmap.json']:
            a,b=json.loads(old.read(name)),json.loads(prod.read(name))
            if 'mappings' in a:assert all(b['mappings'][k]==v for k,v in a['mappings'].items())
            else:assert a['mixins']==b['mixins'] and set(a['client']).issubset(b['client'])
    assert runpy.run_path(str(ROOT/'tools/staff/generate_assets.py'))['animations']()==animation
    with zipfile.ZipFile(ROOT/'build/staff-weather/source-before-weather.zip') as source:
        name='tools/staff/dimension_breaker_staff.bbmodel';a=json.loads(source.read(name));b=json.loads((ROOT/name).read_text(encoding='utf-8'))
        assert b['animations'][:len(a['animations'])]==a['animations'];a.pop('animations');b.pop('animations');assert a==b
    installed=[]
    for instance in ['Integrated MC','1.20.1-Forge_47.4.20']:
        path=Path('F:/AAAAmc/versions')/instance/'mods/sourceofmystery-1.0.0.jar'
        with zipfile.ZipFile(path) as jar:present='com/sourceofmystery/staff/StaffWeather.class' in jar.namelist()
        assert not present
        installed.append({'path':str(path),'sha256':digest(path),'weather_feature_present':present,'written_by_this_task':False})
    media=json.loads((ROOT/'build/staff-weather/media-verification.json').read_text(encoding='utf-8'))
    assert media['status']=='MEDIA_DECODE_PASS_SUBJECTIVE_AUDIO_NOT_TESTED' and not media['audio_played']
    sha=digest(production);candidate=ROOT/f'build/staff-weather/sourceofmystery-weather-{sha[:12]}.jar'
    if candidate.exists():assert digest(candidate)==sha
    else:shutil.copy2(production,candidate)
    report={'status':'TECHNICAL_CHECKS_PASS_APPEARANCE_PENDING_USER',
            'source_base':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),
            'production_jar':{'path':str(candidate),'build_output':str(production),'sha256':sha,'bytes':production.stat().st_size},
            'production_entry_count':len(entries),'native_tests':runs,'tornado_radius':100,'base_damage_per_second':5,
            'default_regular_weather_ticks':12000,'default_tornado_ticks':6000,'default_regular_weather_cost':2000,'default_tornado_cost':5000,
            'network_protocol':12,'previous_asset_entries_unchanged':len(assets),'prior_mechanics_entries_unchanged':mechanics,
            'previous_staff_animations_unchanged':len(old_anim['animations']),'animation_generator_matches_runtime':True,
            'test_classes_excluded':True,'existing_entry_changes':changed,'installed_instances':installed,'media_checks':media,
            'limits':['One actual Forge client with and without Oculus/Complementary; full modpack and actual two-client multiplayer not tested.',
                      'Natural expiry exercised with 200 tick configured duration; default 6000 tick lifetime checked at activation, not waited in full.',
                      'Tests remained silent; audio decode/amplitude checks do not establish subjective audio quality.',
                      'Inherited template and Embeddium/Oculus warnings remain; no zero-error-log claim.',
                      'Game-scale artistic reference to El Reno, not a fluid dynamics simulation.']}
    path=ROOT/'build/staff-weather/release-verification.json';path.write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({'status':report['status'],'production':report['production_jar'],'native_runs':len(runs)},ensure_ascii=False,indent=2))


if __name__=='__main__':main()
