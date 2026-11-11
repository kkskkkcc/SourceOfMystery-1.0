"""Match the gameplay changes to exact production entries in both silent native runs."""
from pathlib import Path
import hashlib
import json
import shutil
import subprocess
import zipfile

ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'build/eastern-gameplay-execution'
BASE=ROOT/'build/staff-followup-fixes/sourceofmystery-staff-fixes-7c1db7d10b67.jar'
RUNS=[('build/eastern-rite-smoke/gameplay-execution-native','eastern-verification.json','EASTERN_RITE_OK'),
      ('build/staff-time-smoke/indefinite-native','time-magic-verification.json','STAFF_TIME_MAGIC_OK')]

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def main():
    assert sha(BASE)=='7c1db7d10b67601c6fad9bcbafed378f5f007e1ce3508268bced37e53caa2543'
    assert 'BUILD SUCCESSFUL' in (OUT/'build.log').read_text('utf8')
    jar=ROOT/'build/libs/sourceofmystery-1.0.0.jar'
    allowed_classes=('client/DomainGeometry','client/DomainVisuals','client/EasternGeometry','client/EasternVisuals',
                     'client/EasternStaffVfx','client/staff/StaffItemExtensions','client/staff/StaffVisuals',
                     'client/staff/StaffClientEvents','client/staff/ClientTimeMagic','mixin/DivineMouseInputMixin',
                     'network/ModNetwork','network/EasternRitePacket','network/StaffSelectionPacket',
                     'network/TimeMagicSyncPacket','staff/StaffCasting','staff/EasternRite','staff/StaffDomains',
                     'staff/StaffConfig','staff/TimeMagicType','staff/TimeStop')
    with zipfile.ZipFile(BASE) as before,zipfile.ZipFile(jar) as after:
        old={n for n in before.namelist() if not n.endswith('/')}
        new={n for n in after.namelist() if not n.endswith('/')}
        removed=old-new;added=new-old
        assert all(n.startswith(('com/sourceofmystery/client/EasternDirector','com/sourceofmystery/client/EasternActor')) for n in removed)
        assert added=={'com/sourceofmystery/client/EasternStaffVfx.class'},added
        assert not any('Check' in n or 'Smoke' in n or 'EasternDirector' in n or 'EasternActor' in n for n in new)
        changed={n for n in old&new if before.read(n)!=after.read(n)}
        languages={'assets/sourceofmystery/lang/en_us.json','assets/sourceofmystery/lang/zh_cn.json'}
        for n in changed-languages:
            assert n.endswith('.class') and n.startswith(tuple('com/sourceofmystery/'+s for s in allowed_classes)),n
        for name in languages:
            a=json.loads(before.read(name));b=json.loads(after.read(name))
            assert a.keys()==b.keys() and {k for k in a if a[k]!=b[k]}=={'tooltip.sourceofmystery.staff.time_hint'}
        assets={n for n in old if n.startswith('assets/')}-languages
        assert not assets&changed
        for n in new:
            if n.endswith('.json'):json.loads(after.read(n))
        checks=[]
        for folder,filename,marker in RUNS:
            run=ROOT/folder;result=json.loads((run/filename).read_text('utf8'))
            assert result['master_volume']==0
            assert all(v for k,v in result.items() if isinstance(v,bool) and k not in ('shader_enabled','real_two_client_multiplayer'))
            log=(run/'launch.log').read_text('utf8',errors='replace')
            assert marker in log and 'AssertionError' not in log and 'GL_INVALID_' not in log
            with zipfile.ZipFile(run/'mods/sourceofmystery-shader-check.jar') as tested:
                assert all(after.read(n)==tested.read(n) for n in new)
            checks.append({'evidence':folder,'checks':result})
    digest=sha(jar);candidate=OUT/f'sourceofmystery-staff-gameplay-{digest[:12]}.jar'
    if candidate.exists():assert sha(candidate)==digest
    else:shutil.copy2(jar,candidate)
    report={'status':'GAMEPLAY_NATIVE_PASS_APPEARANCE_PENDING_USER','protocol':15,
            'source_base':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),
            'jar':{'path':candidate.relative_to(ROOT).as_posix(),'sha256':digest,'bytes':candidate.stat().st_size},
            'production_entries':len(new),'changed_entries':sorted(changed),'added_entries':sorted(added),'removed_entries':sorted(removed),
            'preserved_non_language_assets':len(assets),'production_matches_both_native_runs':True,
            'native_regressions':checks,'installed':False,'pushed_to_github':False,
            'limits':['Silent native clients without shaders; not a full modpack or real two-client test.',
                      'Time modes held for 400 normal ticks each, beyond old240; manual right-click and cooldown verified.',
                      'Execution keeps radius100, 5-minute lifetime and its own10-second CG; full5 minutes not rerun.',
                      'Eastern direction snaps to aim when charging completes, then gates remain fixed.',
                      'Audio scheduling is checked; MASTER=0 means no subjective listening acceptance.']}
    for target in (OUT/'release-verification.json',ROOT/'docs/releases/2026-11-09-staff-gameplay.json'):
        target.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n','utf8')
    print(json.dumps({k:report[k] for k in ('status','jar','production_entries','preserved_non_language_assets','removed_entries')},ensure_ascii=False,indent=2))

if __name__=='__main__':main()
