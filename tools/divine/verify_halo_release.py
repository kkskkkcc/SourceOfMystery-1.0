"""Check the chest armor decoration release against the installed enchanter/luck build."""
from pathlib import Path
import argparse,hashlib,json,shutil,zipfile
ROOT=Path(__file__).resolve().parents[2]
BASE='0620b93e0d9893a290380bb21cb0f1bcd2ff6f2f70ae71e18c869954a9b558ab'
BACKUP=ROOT/'build/release-backups'/('sourceofmystery-before-halo-'+BASE+'.jar')
RELEASE=ROOT/'build/libs/sourceofmystery-1.0.0.jar'
TARGETS=[Path('F:/AAAAmc/versions')/name/'mods/sourceofmystery-1.0.0.jar' for name in ['Integrated MC','1.20.1-Forge_47.4.20']]
EXPECTED={'com/sourceofmystery/client/ClientModEvents.class','com/sourceofmystery/client/DivineBlessingHaloRenderer.class'}
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    p=argparse.ArgumentParser();p.add_argument('--install',action='store_true');a=p.parse_args()
    baseline=BACKUP if BACKUP.exists() else TARGETS[0];assert sha(baseline)==BASE
    with zipfile.ZipFile(baseline) as old,zipfile.ZipFile(RELEASE) as new,zipfile.ZipFile(ROOT/'build/reobfShaderCheckJar/output.jar') as test:
        names={n for n in old.namelist() if not n.endswith('/')}
        assert names=={n for n in new.namelist() if not n.endswith('/')},'Unexpected added/deleted release entries'
        changed={n for n in names if old.read(n)!=new.read(n)};assert changed==EXPECTED,changed
        for n in names:assert new.read(n)==test.read(n),('Production/test mismatch',n)
        assert not any('SmokeCheck' in n or 'CompatibilityFixture' in n for n in names)
        assets=[n for n in names if n.startswith('assets/')]
        assert all(new.read(n)==old.read(n) for n in assets)
    report={'baseline_sha256':BASE,'sha256':sha(RELEASE),'bytes':RELEASE.stat().st_size,
        'changed_entries':sorted(changed),'all_other_release_entries_unchanged':True,
        'all_assets_preserved':len(assets),'production_matches_tested_classes':True,'test_classes_excluded':True,'installed_paths':[]}
    if a.install:
        BACKUP.parent.mkdir(parents=True,exist_ok=True)
        if not BACKUP.exists():shutil.copy2(baseline,BACKUP)
        assert sha(BACKUP)==BASE
        for target in TARGETS:
            assert sha(target) in [BASE,sha(RELEASE)],('Unexpected installed version',str(target))
            shutil.copy2(RELEASE,target);assert sha(target)==sha(RELEASE);report['installed_paths'].append(str(target))
        report['backup']=str(BACKUP)
    (ROOT/'build/divine-halo-release-checks.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    print(json.dumps(report,ensure_ascii=False,indent=2))
if __name__=='__main__':main()
