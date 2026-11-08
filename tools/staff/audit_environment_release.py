"""Compare production bytes with the real isolated clients; never install a mod or edit a user world."""
from pathlib import Path
import hashlib,json,runpy,subprocess,zipfile

ROOT=Path(__file__).resolve().parents[2]
def digest(path):
    value=hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda:stream.read(1024*1024),b''):value.update(block)
    return value.hexdigest()
def main():
    production=ROOT/'build/libs/sourceofmystery-1.0.0.jar'
    runs=ROOT/'build/staff-environment-smoke'
    full=runs/'diameter-1000-final'
    visual=[runs/'vfx-v5-vanilla',runs/'vfx-v4-shaders']
    core_report=json.loads((full/'environment-verification.json').read_text())
    assert core_report['diameter_1000_full_desert_conversion'] and core_report['radius_500_boundary']
    with zipfile.ZipFile(production) as prod:
        entries=prod.namelist()
        assert not any('SmokeCheck' in n or 'VisualCheck' in n or 'MovieCapture' in n for n in entries)
        reports=[]
        for directory in visual:
            with zipfile.ZipFile(directory/'mods/sourceofmystery-shader-check.jar') as test:
                assert all(prod.read(n)==test.read(n) for n in entries),directory
            result=json.loads((directory/'environment-visual-verification.json').read_text())
            assert result['theme_count']==6 and result['master_volume']==0 and result['native_charge_and_release']
            assert 'STAFF_ENVIRONMENT_VISUAL_OK' in (directory/'launch.log').read_text(encoding='utf-8')
            reports.append({'directory':str(directory),'result':result,'all_production_entries_identical':True})
        with zipfile.ZipFile(full/'mods/sourceofmystery-shader-check.jar') as old:
            functional=[n for n in entries if n.startswith('com/sourceofmystery/staff/') or n.startswith('com/sourceofmystery/network/StaffEffectPacket')
                        or n.startswith('com/sourceofmystery/item/DimensionBreakerStaffItem')]
            assert all(prod.read(n)==old.read(n) for n in functional),'Functional bytes changed since full-range test'
        runtime='assets/sourceofmystery/animations/item/dimension_breaker_staff.animation.json'
        animation=json.loads(prod.read(runtime))
    generated=runpy.run_path(str(ROOT/'tools/staff/generate_assets.py'))['animations']()
    assert generated==animation
    bbpath='tools/staff/dimension_breaker_staff.bbmodel'
    before=json.loads(subprocess.check_output(['git','show','HEAD:'+bbpath],cwd=ROOT))
    after=json.loads((ROOT/bbpath).read_text(encoding='utf-8'))
    assert after['animations'][:len(before['animations'])]==before['animations']
    before.pop('animations');after.pop('animations');assert before==after
    installed=[]
    for instance in ['Integrated MC','1.20.1-Forge_47.4.20']:
        path=Path('F:/AAAAmc/versions')/instance/'mods/sourceofmystery-1.0.0.jar'
        with zipfile.ZipFile(path) as jar:present='com/sourceofmystery/staff/StaffEnvironment.class' in jar.namelist()
        installed.append({'path':str(path),'sha256':digest(path),'environment_feature_present':present,'written_by_this_task':False})
    report={'status':'TECHNICAL_CHECKS_PASS_APPEARANCE_PENDING_USER','source_base':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),
            'diameter':1000,'radius':500,'production_jar':{'path':str(production),'sha256':digest(production),'bytes':production.stat().st_size},
            'production_entry_count':len(entries),'test_classes_excluded':True,'full_range_result':core_report,'full_range_directory':str(full),
            'functional_entries_unchanged_from_full_range_test':len(functional),'visual_tests':reports,'old_model_and_animations_preserved':True,
            'runtime_animation_matches_generator':True,'installed_instances':installed,
            'limits':['Full diameter 1000 tested with desert; ocean/jungle terrain tested through radius-50 restored jobs.',
                      'Full modpack, real multiplayer clients, all biomes in every dimension, and subjective audio acceptance not tested.',
                      'Native logs retain inherited template mappings and Oculus/Embeddium/shader-pack warnings; no zero-error-log claim.']}
    destination=ROOT/'build/staff-environment-release-checks.json'
    destination.write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({'status':report['status'],'production':report['production_jar'],'compared_functional_entries':len(functional),'visual_test_count':len(reports)},ensure_ascii=False,indent=2))
if __name__=='__main__':main()
