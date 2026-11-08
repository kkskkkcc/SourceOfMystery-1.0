"""Audit the native overwrite regression and retain a stable, uninstalled repair candidate."""
from pathlib import Path
import hashlib
import json
import shutil
import subprocess
import zipfile

ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'build/staff-environment-overwrite'
RUN=ROOT/'build/staff-environment-overwrite-smoke/overwrite-native'
BASE=ROOT/'build/github-sync-20261108-180714/sourceofmystery-merged-fd7f1e1befee.jar'


def digest(path):
    result=hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda:stream.read(1024*1024),b''):result.update(block)
    return result.hexdigest()


def main():
    assert digest(BASE)=='fd7f1e1befeed41984dc03120755257a717ac99bfdc05f9b752da187e4b93230'
    result=json.loads((RUN/'environment-overwrite-verification.json').read_text(encoding='utf-8'))
    checks=['native_1000_diameter_recast_while_busy','same_centre_surface_server_and_client_biome',
            'partial_overlap_admitted_at_radius_500','independent_chunk_ticket_identities',
            'old_job_resumed_after_new_job_completed','non_overlap_preserved_and_overlap_not_reverted',
            'protected_structures_and_inventory','partial_overlap_client_biomes',
            'version2_saved_jobs_load_without_resetting_cursors',
            'covered_recast_at_capacity_and_atomic_limit_failure','checkpoint_keeps_exclusions_and_cursors']
    assert all(result[key] is True for key in checks)
    assert result['master_volume']==0 and result['production_radius']==500 and result['worker_radius_under_test']==48
    log=(RUN/'launch.log').read_text(encoding='utf-8')
    assert 'STAFF_ENVIRONMENT_OVERWRITE_OK' in log and 'AssertionError' not in log and 'GL_INVALID_' not in log
    jar=ROOT/'build/libs/sourceofmystery-1.0.0.jar'
    allowed={'com/sourceofmystery/staff/StaffEnvironment.class','com/sourceofmystery/staff/StaffEnvironment$Job.class',
             'assets/sourceofmystery/lang/en_us.json','assets/sourceofmystery/lang/zh_cn.json'}
    with zipfile.ZipFile(BASE) as before,zipfile.ZipFile(jar) as after,zipfile.ZipFile(RUN/'mods/sourceofmystery-shader-check.jar') as actual:
        old={name for name in before.namelist() if not name.endswith('/')}
        new={name for name in after.namelist() if not name.endswith('/')}
        assert old-new==set()
        assert new-old=={'com/sourceofmystery/staff/StaffEnvironment$Area.class'}
        changed={name for name in old if before.read(name)!=after.read(name)}
        assert changed==allowed,changed
        assert all(after.read(name)==actual.read(name) for name in new)
        assert not any('SmokeCheck' in name or 'OverwriteCheck' in name or 'MovieCapture' in name for name in new)
        for name in new:
            if name.endswith('.json'):json.loads(after.read(name))
        for language in ['en_us','zh_cn']:
            name=f'assets/sourceofmystery/lang/{language}.json'
            a,b=json.loads(before.read(name)),json.loads(after.read(name))
            assert a.keys()==b.keys()
            assert {key for key in a if a[key]!=b[key]}=={'message.sourceofmystery.staff.environment_busy'}
    sha=digest(jar);candidate=OUT/f'sourceofmystery-environment-overwrite-{sha[:12]}.jar'
    if candidate.exists():assert digest(candidate)==sha
    else:shutil.copy2(jar,candidate)
    report={'status':'FIXED_NATIVE_REGRESSION_PASS','source_base':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),
            'production_jar':{'path':str(candidate),'sha256':sha,'bytes':candidate.stat().st_size},
            'native_regression':result,'native_directory':str(RUN),'all_production_entries_match_native_run':True,
            'production_entries':len(new),'preserved_old_entries':len(old-allowed),
            'changed_old_entries':sorted(changed),'added_entries':sorted(new-old),'test_classes_excluded':True,
            'installed':False,'pushed_to_github':False,'limits':[
                'Admission and native item recasting used the production 500-block radius and 80-tick charge.',
                'Completed worker and checkpoint tests used restored 48-block-radius jobs; two full 1000-block conversions were not rerun to completion.',
                'One silent native Forge client. Full modpack, shader-enabled regression and actual two-client multiplayer not rerun.',
                'Inherited template and Embeddium/Oculus warnings remain; not a zero-warning or zero-error-log claim.']}
    (OUT/'release-verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({key:report[key] for key in ['status','production_jar','production_entries','preserved_old_entries','changed_old_entries']},ensure_ascii=False,indent=2))


if __name__=='__main__':main()
