"""Install the explicitly approved, audited satellite candidate with verified backups."""
from pathlib import Path
import hashlib,json,os,shutil,zipfile

ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'build/divine-satellites'
TARGETS=[Path('F:/AAAAmc/versions')/v/'mods/sourceofmystery-1.0.0.jar' for v in ['Integrated MC','1.20.1-Forge_47.4.20']]
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def main():
    release=OUT/'sourceofmystery-1.0.0.jar'
    reviewed=json.loads((OUT/'review-verification.json').read_text(encoding='utf8'))
    expected=reviewed['candidate_sha256'];base=reviewed['candidate_audit']['baseline_sha256']
    assert expected=='d73ce1d5d75b31b8cf6313155922c4f93747ec406931b8e171da552adf303f37'
    assert sha(release)==expected
    assert reviewed['proposal']=='inner_gecko_stars_original_outer_particles'
    assert reviewed['checks']['exactly_five_outer_particles_restored']
    for target in TARGETS:assert sha(target) in (base,expected),str(target)
    backup=ROOT/'build/release-backups'/('sourceofmystery-before-inner-star-satellites-'+base+'.jar')
    backup.parent.mkdir(parents=True,exist_ok=True)
    if not backup.exists():
        original=next((p for p in TARGETS if sha(p)==base),OUT/('baseline-'+base+'.jar'))
        assert sha(original)==base;shutil.copy2(original,backup)
    assert sha(backup)==base
    result={'status':'INSTALLING','sha256':expected,'bytes':release.stat().st_size,'user_authorized':True,
            'backup':str(backup),'baseline_sha256':base,'installed_paths':[],'pending_paths':[]}
    for target in TARGETS:
        if sha(target)==expected:result['installed_paths'].append(str(target));continue
        staged=target.with_name(target.name+'.updating');shutil.copy2(release,staged);assert sha(staged)==expected
        try:os.replace(staged,target)
        except PermissionError:result['pending_paths'].append(str(staged));continue
        assert sha(target)==expected;result['installed_paths'].append(str(target))
    result['status']='INSTALLED' if not result['pending_paths'] else 'PENDING_FILE_UNLOCK'
    (OUT/'installation.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    print(json.dumps(result,ensure_ascii=False,indent=2))
    return 2 if result['pending_paths'] else 0
if __name__=='__main__':raise SystemExit(main())
