"""Audit the reduced windup / damage-timed cuts, preserving unrelated production entries."""
from pathlib import Path
import hashlib,json,shutil,subprocess,zipfile
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'build/eastern-fast-release'
BASE=ROOT/'build/eastern-gameplay-execution/sourceofmystery-staff-gameplay-6f98b85b8ecf.jar'
RUN=ROOT/'build/eastern-rite-smoke/fast-release-hit-sync-final-native'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    assert sha(BASE)=='6f98b85b8ecfbf335d42817367d2de216d5aa21761a8c7e8a75430b7bec72ab1'
    assert 'BUILD SUCCESSFUL' in (OUT/'build-final.log').read_text('utf8')
    result=json.loads((RUN/'eastern-verification.json').read_text('utf8'))
    assert result['master_volume']==0 and result['press_to_launch_server_ticks']<=80
    assert all(v for k,v in result.items() if isinstance(v,bool) and k not in ('shader_enabled','real_two_client_multiplayer'))
    log=(RUN/'launch.log').read_text('utf8',errors='replace')
    assert 'EASTERN_RITE_OK' in log and 'AssertionError' not in log and 'GL_INVALID_' not in log
    jar=ROOT/'build/libs/sourceofmystery-1.0.0.jar'
    allowed=('client/DomainGeometry','client/DomainVisuals','client/EasternGeometry','client/EasternStaffVfx',
             'client/EasternVisuals','client/GuardianSpiritRenderer','client/staff/StaffItemExtensions',
             'entity/GuardianSpirit','item/DimensionBreakerStaffItem','network/ModNetwork','staff/EasternRite','staff/StaffConfig',
             'staff/StaffDomains','staff/StaffSpell')
    with zipfile.ZipFile(BASE) as a,zipfile.ZipFile(jar) as b,zipfile.ZipFile(RUN/'mods/sourceofmystery-shader-check.jar') as tested:
        old={x for x in a.namelist() if not x.endswith('/')};new={x for x in b.namelist() if not x.endswith('/')}
        assert old-new=={'com/sourceofmystery/client/GuardianSpiritRenderer$MagicCircleLayer.class'}
        assert new-old=={'com/sourceofmystery/client/DomainVisuals$Strike.class','com/sourceofmystery/client/DomainVisuals$StrikeKey.class',
                         'com/sourceofmystery/network/DomainSlashPacket.class','com/sourceofmystery/network/DomainSlashPacket$Target.class'}
        changed={x for x in old&new if a.read(x)!=b.read(x)}
        assert all(x.endswith('.class') and x.startswith(tuple('com/sourceofmystery/'+p for p in allowed)) for x in changed),sorted(changed)
        assets={x for x in old if x.startswith('assets/')};assert not changed&assets
        assert all(b.read(x)==tested.read(x) for x in new)
        assert not any('Check' in x or 'Smoke' in x for x in new)
        for x in new:
            if x.endswith('.json'):json.loads(b.read(x))
    javap='C:/Program Files/Eclipse Adoptium/jdk-17.0.19.10-hotspot/bin/javap.exe'
    methods=subprocess.check_output([javap,'-private','-classpath',str(jar),'com.sourceofmystery.entity.GuardianSpirit'],text=True)
    assert 'fireOrb(' in methods and 'castLightning(' not in methods and 'prayerCooldown' not in methods
    digest=sha(jar);candidate=OUT/f'sourceofmystery-combat-timing-{digest[:12]}.jar'
    if candidate.exists():assert sha(candidate)==digest
    else:shutil.copy2(jar,candidate)
    report={'status':'TIMING_NATIVE_PASS_GUARDIAN_CODE_BUILD_PASS','protocol':16,
            'jar':{'path':candidate.relative_to(ROOT).as_posix(),'sha256':digest,'bytes':candidate.stat().st_size},
            'production_entries':len(new),'production_matches_native_run':True,'preserved_asset_entries':len(assets),
            'changed_entries':sorted(changed),'added_entries':sorted(new-old),'removed_entries':sorted(old-new),
            'native_evidence':RUN.relative_to(ROOT).as_posix(),'native_checks':result,
            'guardian_checks':{'compiled_lightning_removed':True,'compiled_orb_retained':True,'prayer_render_layer_removed':True,'in_game_retest':False},
            'installed':False,'pushed_to_github':False,
            'limits':['Silent single native client, no shader/full-modpack/real two-client run.',
                      'Guardian skill removal is source/build/bytecode checked; guardian combat was not separately retested.',
                      '78 simulation ticks means3.9 seconds at20TPS; server slowdown or altered time magic changes wall-clock duration.',
                      'Slashes start on damage-packet arrival; normal network latency remains.',
                      'Audio is scheduled with damage; MASTER0 means no subjective listening test.']}
    for p in (OUT/'release-verification.json',ROOT/'docs/releases/2026-11-09-combat-timing.json'):
        p.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n','utf8')
    print(json.dumps({k:report[k] for k in ('status','jar','production_entries','preserved_asset_entries')},ensure_ascii=False,indent=2))
if __name__=='__main__':main()
