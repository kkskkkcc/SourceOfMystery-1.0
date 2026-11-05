"""Audit the first-equip CG against the installed shoulder-fix release, then stage it."""
from pathlib import Path
import argparse,hashlib,json,os,shutil,struct,zipfile

ROOT=Path(__file__).resolve().parents[2]
BASE='3fd5ed68092364d959315c0e04f8c540fbce746844522548fa25eee90da7672a'
PREVIOUS='305aa53ace0ef89b0eaeead6b140de966370f868a1fabb0910024c908ee9f242'
RELEASE=ROOT/'build/libs/sourceofmystery-1.0.0.jar'
BACKUP=ROOT/'build/release-backups'/('sourceofmystery-before-divine-cg-'+BASE+'.jar')
PREVIOUS_BACKUP=ROOT/'build/release-backups'/('sourceofmystery-before-earth-ascent-'+PREVIOUS+'.jar')
TARGETS=[Path('F:/AAAAmc/versions')/n/'mods/sourceofmystery-1.0.0.jar' for n in ['Integrated MC','1.20.1-Forge_47.4.20']]
ASSET='assets/sourceofmystery/'
CODE='com/sourceofmystery/'

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def files(z):return {n for n in z.namelist() if not n.endswith('/')}
def contains_before(before,after):
    if isinstance(before,dict):return all(k in after and contains_before(v,after[k]) for k,v in before.items())
    return before==after

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--install',action='store_true');args=parser.parse_args()
    baseline=BACKUP if BACKUP.exists() else TARGETS[0];assert sha(baseline)==BASE
    allowed_changed={ASSET+'lang/'+n+'.json' for n in ['zh_cn','en_us']}
    allowed_changed|={ASSET+'sounds.json','sourceofmystery.mixins.json','sourceofmystery.refmap.json'}
    allowed_changed|={CODE+n+'.class' for n in ['SourceOfMystery','client/ClientModEvents','network/ModNetwork',
        'ritual/OfferingPortalRitual','ritual/OfferingPortalRitual$Active','ritual/OfferingPortalRitual$Frame']}
    prefixes=[ASSET+'animations/cinematic/',ASSET+'geo/cinematic/',ASSET+'sounds/divine_cg/',ASSET+'textures/cinematic/divine/',
        CODE+'cinematic/',CODE+'client/DivineCG',CODE+'client/DivineThrone',CODE+'client/DivineAscensionToast',
        CODE+'mixin/DivinePlayerLayersAccess',CODE+'mixin/DivineMouseInputMixin',CODE+'network/DivineCinematic']
    with zipfile.ZipFile(baseline) as old,zipfile.ZipFile(RELEASE) as new:
        old_names,new_names=files(old),files(new)
        assert not old_names-new_names,'Removed an existing resource'
        changed={n for n in old_names&new_names if old.read(n)!=new.read(n)}
        added=new_names-old_names
        assert changed==allowed_changed,changed^allowed_changed
        assert all(any(n.startswith(p) for p in prefixes) or n=='data/sourceofmystery/advancements/ascend_divinity.json' for n in added),added
        assert not any('SmokeCheck' in n or 'MovieCapture' in n for n in new_names),'Test fixtures in production'
        for name in [ASSET+'sounds.json',ASSET+'lang/zh_cn.json',ASSET+'lang/en_us.json','sourceofmystery.refmap.json']:
            assert contains_before(json.loads(old.read(name)),json.loads(new.read(name))),name
        mix_before=json.loads(old.read('sourceofmystery.mixins.json'));mix_after=json.loads(new.read('sourceofmystery.mixins.json'))
        assert mix_after['client'][-2:]==['DivinePlayerLayersAccess','DivineMouseInputMixin']
        mix_after['client']=mix_after['client'][:-2];assert mix_before==mix_after
        for n in added:
            if n.startswith(ASSET) or n.startswith('data/'):
                assert new.read(n)==(ROOT/'src/main/resources'/n).read_bytes(),n
        mesh=new.read(ASSET+'geo/cinematic/divine_throne.mesh')
        assert struct.unpack_from('>III',mesh)==(0x534f4d54,1,5139)
        assert len(mesh)==12+5139*32
        import_report=json.loads((ROOT/'art/divine_cinematic/imported_throne/import-report.json').read_text(encoding='utf8'))
        assert import_report['runtime_dimensions_m']==[1.65,2.3,1.5] and import_report['uv_preserved']
        assert hashlib.sha256(mesh).hexdigest()==import_report['mesh_sha256']
        preserved_assets=len([n for n in old_names if n.startswith(ASSET) and n not in changed])
    run=ROOT/'build/divine-cg-smoke/earth-ascent-silent-final'
    runtime=json.loads((run/'divine-cg-verification.json').read_text())
    assert runtime['real_client'] and not runtime['seek_diagnostics']
    assert runtime['surface_to_orbit_ascent_seconds']==8 and runtime['ascent_orbit_camera_continuous']
    assert runtime['test_client_master_volume']==0
    for flag in ['first_equip_server_trigger','real_player_position_unchanged','camera_type_and_yaw_pitch_restored',
                 'server_gravity_and_input_restored','second_equip_does_not_replay','ascend_divinity_awarded']:
        assert runtime[flag],flag
    # The exact test package must contain the same production resources and classes.
    with zipfile.ZipFile(ROOT/'build/reobfShaderCheckJar/output.jar') as tested,zipfile.ZipFile(RELEASE) as new:
        assert all(tested.read(n)==new.read(n) for n in files(new) if n!='META-INF/MANIFEST.MF')
    report={'baseline_sha256':BASE,'sha256':sha(RELEASE),'bytes':RELEASE.stat().st_size,
        'changed_entries':sorted(changed),'added_entries':sorted(added),'existing_asset_entries_preserved':preserved_assets,
        'existing_armor_halo_boss_audio_energy_lottery_enchanter_unchanged':True,
        'ritual_change':'mutual-exclusion guard with the local armor cinematic only',
        'network_protocol':8,'throne_dimensions_m':import_report['runtime_dimensions_m'],'throne_gray_white_only':True,
        'runtime_scope':'Forge + GeckoLib + Embeddium + Oculus (shaders disabled), isolated world',
        'full_modpack_tested':False,'ascent_seconds':8,'runtime':runtime,'installed_paths':[],'pending_paths':[]}
    if args.install:
        for target in TARGETS:assert sha(target) in (BASE,PREVIOUS,sha(RELEASE)),str(target)
        BACKUP.parent.mkdir(parents=True,exist_ok=True)
        if not BACKUP.exists():shutil.copy2(baseline,BACKUP)
        assert sha(BACKUP)==BASE
        previous=PREVIOUS_BACKUP if PREVIOUS_BACKUP.exists() else next((p for p in TARGETS if sha(p)==PREVIOUS),None)
        if previous:
            assert sha(previous)==PREVIOUS
            if not PREVIOUS_BACKUP.exists():shutil.copy2(previous,PREVIOUS_BACKUP)
            with zipfile.ZipFile(PREVIOUS_BACKUP) as old,zipfile.ZipFile(RELEASE) as new:
                throne_names=[n for n in files(old) if n.startswith(CODE+'client/DivineThrone') or n.startswith(CODE+'client/DivineCGThrone') or '/divine_throne.' in n or '/throne_source.' in n]
                assert throne_names and all(old.read(n)==new.read(n) for n in throne_names),'Compact gray/white throne changed'
                report['previous_compact_gray_throne_unchanged']=True
            report['previous_version_backup']=str(PREVIOUS_BACKUP)
        for target in TARGETS:
            staged=target.with_name(target.name+'.updating');shutil.copy2(RELEASE,staged);assert sha(staged)==sha(RELEASE)
            try:os.replace(staged,target)
            except PermissionError:report['pending_paths'].append(str(staged));continue
            assert sha(target)==sha(RELEASE);report['installed_paths'].append(str(target))
        report['backup']=str(BACKUP)
    (ROOT/'build/divine-cg-release-checks.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    print(json.dumps(report,ensure_ascii=False,indent=2))
    return 2 if report['pending_paths'] else 0
if __name__=='__main__':raise SystemExit(main())
