"""Audit release against the previously installed offering model release; optional install."""
from pathlib import Path
import argparse, hashlib, json, shutil, zipfile

ROOT=Path(__file__).resolve().parents[2]
TARGET=Path('F:/AAAAmc/versions/1.20.1-Forge_47.4.20/mods/sourceofmystery-1.0.0.jar')
BASE_HASH='89d8ac5df82f8aeea8eb92c31131b497eacef2bbb0eaea8a224ec4b9fa32ebde'
BACKUP=ROOT/'build/release-backups'/('sourceofmystery-before-offering-lottery-'+BASE_HASH+'.jar')
RELEASE=ROOT/'build/libs/sourceofmystery-1.0.0.jar'
ALLOWED=('com/sourceofmystery/SourceOfMystery.class',
         'com/sourceofmystery/client/ClientModEvents.class',
         'com/sourceofmystery/item/DarkDimensionalOfferingItem',
         'com/sourceofmystery/block/DarkDimensionalOfferingBlock.class',
         'com/sourceofmystery/network/ModNetwork.class',
         'assets/sourceofmystery/lang/')

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--install',action='store_true');args=parser.parse_args()
    baseline=BACKUP if BACKUP.exists() else TARGET
    assert sha(baseline)==BASE_HASH,'Baseline changed; inspect before installing'
    with zipfile.ZipFile(baseline) as old,zipfile.ZipFile(RELEASE) as new,zipfile.ZipFile(ROOT/'build/reobfShaderCheckJar/output.jar') as test:
        changed=[n for n in old.namelist() if not n.endswith('/') and (n not in new.namelist() or old.read(n)!=new.read(n))]
        unexpected=[n for n in changed if not n.startswith(ALLOWED)]
        assert not unexpected,unexpected
        for n in new.namelist():
            if not n.endswith('/'):
                assert n in test.namelist() and new.read(n)==test.read(n),('Smoke/release mismatch',n)
        assert not any('lotterycompat' in n or 'SmokeCheck' in n or 'GameTests' in n or 'CompatibilityFixture' in n for n in new.namelist())
        assert 'com/sourceofmystery/offering/OfferingMenu.class' in new.namelist()
        assert 'com/sourceofmystery/client/OfferingLotteryScreen.class' in new.namelist()
        assets=[n for n in old.namelist() if n.startswith(('assets/sourceofmystery/geo/','assets/sourceofmystery/animations/','assets/sourceofmystery/textures/','assets/sourceofmystery/sounds/')) and not n.endswith('/')]
        assert all(old.read(n)==new.read(n) for n in assets)
    report={'baseline_sha256':BASE_HASH,'release_sha256':sha(RELEASE),'bytes':RELEASE.stat().st_size,
            'changed_existing_entries':changed,'preserved_model_texture_animation_audio_entries':len(assets),
            'release_matches_real_client_test_classes':True,'test_fixtures_excluded':True,'installed':False}
    if args.install:
        assert TARGET.parent.resolve()==Path('F:/AAAAmc/versions/1.20.1-Forge_47.4.20/mods').resolve()
        if not BACKUP.exists():BACKUP.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(TARGET,BACKUP)
        assert sha(BACKUP)==BASE_HASH
        shutil.copy2(RELEASE,TARGET);assert sha(TARGET)==sha(RELEASE)
        report.update(installed=True,installed_path=str(TARGET),backup=str(BACKUP))
    (ROOT/'build/offering-lottery-release-checks.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(report,ensure_ascii=False,indent=2))

if __name__=='__main__':main()
