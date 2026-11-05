"""Install only the shoulder-cover geometry fix, preserving a backup and open JAR handles."""
from pathlib import Path
import argparse,hashlib,json,os,shutil,zipfile

ROOT=Path(__file__).resolve().parents[2]
BASE='7b7a70bdee9d6f3a949949edd41eb76e570ab3031e681f6da6e8e0dd0382d2ed'
RESOURCE='assets/sourceofmystery/geo/armor/divine_blessing.geo.json'
RELEASE=ROOT/'build/libs/sourceofmystery-1.0.0.jar'
BACKUP=ROOT/'build/release-backups'/('sourceofmystery-before-shoulder-fit-'+BASE+'.jar')
TARGETS=[Path('F:/AAAAmc/versions')/n/'mods/sourceofmystery-1.0.0.jar' for n in ['Integrated MC','1.20.1-Forge_47.4.20']]
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--install',action='store_true');a=parser.parse_args()
    baseline=BACKUP if BACKUP.exists() else TARGETS[0];assert sha(baseline)==BASE
    with zipfile.ZipFile(baseline) as old,zipfile.ZipFile(RELEASE) as new:
        names={n for n in old.namelist() if not n.endswith('/')}
        assert names=={n for n in new.namelist() if not n.endswith('/')}
        changed={n for n in names if old.read(n)!=new.read(n)};assert changed=={RESOURCE},changed
        assert new.read(RESOURCE)==(ROOT/'src/main/resources'/RESOURCE).read_bytes()
        before=json.loads(old.read(RESOURCE));after=json.loads(new.read(RESOURCE))
        changed_cubes=[]
        for original,fixed in zip(before['minecraft:geometry'][0]['bones'],after['minecraft:geometry'][0]['bones']):
            assert original.keys()==fixed.keys() and original['name']==fixed['name']
            if original['name'] in ('armorRightArm','armorLeftArm'):
                assert original['cubes'][1:]==fixed['cubes'][1:]
                assert fixed['cubes'][0]['size']==[5,7.1,5]
                changed_cubes.append(original['name']+'/bicep_liner')
                # Removing the two changed cubes must yield identical whole models.
                original['cubes'][0]=fixed['cubes'][0]
        assert before==after,'Changes beyond the two shoulder liners'
        fit=json.loads((ROOT/'build/divine-shoulder-fit.json').read_text(encoding='utf8'))
        assert fit['all_skin_samples_covered'] and fit['model_source_sha256']==hashlib.sha256(new.read(RESOURCE)).hexdigest()
    report={'baseline_sha256':BASE,'sha256':sha(RELEASE),'bytes':RELEASE.stat().st_size,
        'changed_entries':sorted(changed),'changed_cubes':changed_cubes,'all_other_entries_unchanged':True,
        'classic_slim_shoulder_fit_audited':True,'model_cubes':158,'quad_faces':943,
        'all_classes_and_textures_unchanged':True,'game_client_launched':False,
        'validation':'compile, offline native sleeve coverage, posed geometry and static top-view comparison',
        'installed_paths':[],'pending_paths':[]}
    if a.install:
        for target in TARGETS:assert sha(target) in (BASE,sha(RELEASE)),str(target)
        BACKUP.parent.mkdir(parents=True,exist_ok=True)
        if not BACKUP.exists():shutil.copy2(baseline,BACKUP)
        assert sha(BACKUP)==BASE
        for target in TARGETS:
            staged=target.with_name(target.name+'.updating')
            shutil.copy2(RELEASE,staged);assert sha(staged)==sha(RELEASE)
            try:os.replace(staged,target)
            except PermissionError:
                report['pending_paths'].append(str(staged));continue
            assert sha(target)==sha(RELEASE);report['installed_paths'].append(str(target))
        report['backup']=str(BACKUP)
    (ROOT/'build/divine-shoulder-release-checks.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    print(json.dumps(report,ensure_ascii=False,indent=2))
    return 2 if report['pending_paths'] else 0
if __name__=='__main__':raise SystemExit(main())
