"""Audit and install the GeckoLib divine chestplate; no Minecraft process is launched."""
from pathlib import Path
import argparse,hashlib,json,shutil,zipfile
from PIL import Image

ROOT=Path(__file__).resolve().parents[2]
BASE='1d07c7ff6a55987d1a65920f60c3beb9eec6c6738acab840d575e6a3d3f12661'
RELEASE=ROOT/'build/libs/sourceofmystery-1.0.0.jar'
BACKUP=ROOT/'build/release-backups'/('sourceofmystery-before-gecko-armor-'+BASE+'.jar')
TARGETS=[Path('F:/AAAAmc/versions')/n/'mods/sourceofmystery-1.0.0.jar' for n in ['Integrated MC','1.20.1-Forge_47.4.20']]
EXPECTED_CHANGED={'com/sourceofmystery/item/ModItems.class'}
EXPECTED_NEW={
    'com/sourceofmystery/item/DivineBlessingChestplateItem.class',
    'com/sourceofmystery/item/DivineBlessingChestplateItem$1.class',
    'com/sourceofmystery/client/DivineBlessingArmorRenderer.class',
    'com/sourceofmystery/client/DivineBlessingArmorRenderer$1.class',
    'com/sourceofmystery/client/DivineBlessingArmorRenderer$GloryLayer.class',
    'com/sourceofmystery/client/DivineBlessingArmorRenderer$FlowVertices.class',
    'assets/sourceofmystery/geo/armor/divine_blessing.geo.json',
    'assets/sourceofmystery/animations/armor/divine_blessing.animation.json',
    'assets/sourceofmystery/textures/armor/divine_blessing.png',
    'assets/sourceofmystery/textures/armor/divine_blessing_white_flow.png',
    'assets/sourceofmystery/textures/armor/divine_blessing_black_flow.png',
    'assets/sourceofmystery/textures/armor/divine_blessing_white_flow.png.mcmeta',
    'assets/sourceofmystery/textures/armor/divine_blessing_black_flow.png.mcmeta'}

def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--install',action='store_true');a=parser.parse_args()
    baseline=BACKUP if BACKUP.exists() else TARGETS[0];assert sha(baseline)==BASE
    with zipfile.ZipFile(baseline) as old,zipfile.ZipFile(RELEASE) as new:
        before={n for n in old.namelist() if not n.endswith('/')};after={n for n in new.namelist() if not n.endswith('/')}
        assert before<=after,'Existing release entries removed'
        changed={n for n in before if old.read(n)!=new.read(n)};added=after-before
        assert changed==EXPECTED_CHANGED,changed
        assert added==EXPECTED_NEW,added
        assert not any('SmokeCheck' in n or 'CompatibilityFixture' in n for n in after)
        for n in added:
            if n.startswith('assets/'):assert new.read(n)==(ROOT/'src/main/resources'/n).read_bytes(),n
        assets=[n for n in before if n.startswith('assets/')]
        assert all(old.read(n)==new.read(n) for n in assets)
        geometry=json.loads(new.read('assets/sourceofmystery/geo/armor/divine_blessing.geo.json'))['minecraft:geometry'][0]
        bones=geometry['bones'];names={b['name'] for b in bones}
        assert names=={'armorBody','taiji_emblem','armorRightArm','armorLeftArm','armorRightLeg','armorLeftLeg'}
        assert all('parent' not in b or b['parent'] in names for b in bones)
        plan=json.loads((ROOT/'build/divine-armor-asset-plan.json').read_text(encoding='utf8'))
        assert sum(len(b.get('cubes',[])) for b in bones)==plan['cubes']==158
        assert sum(len(c['uv']) for b in bones for c in b.get('cubes',[]))==plan['quad_faces']==943
    for texture in ['divine_blessing','divine_blessing_white_flow','divine_blessing_black_flow']:
        assert Image.open(ROOT/f'src/main/resources/assets/sourceofmystery/textures/armor/{texture}.png').size==(512,512)
    report={'baseline_sha256':BASE,'sha256':sha(RELEASE),'bytes':RELEASE.stat().st_size,
        'changed_existing_entries':sorted(changed),'new_entries':sorted(added),
        'preserved_existing_assets':len(assets),'all_other_existing_entries_unchanged':True,
        'model_cubes':plan['cubes'],'bones':plan['bones'],'quad_faces':plan['quad_faces'],
        'item_id':'sourceofmystery:divine_blessing_chestplate','slot':'CHEST',
        'halo_and_energy_regeneration_preserved_byte_identical':True,'test_classes_excluded':True,
        'validation':'compile, source/geometry/UV inspection, static CPU previews and release audit; no runtime test',
        'game_client_launched':False,'game_tests_launched':False,'installed_paths':[]}
    if a.install:
        for target in TARGETS:assert sha(target) in [BASE,sha(RELEASE)],str(target)
        BACKUP.parent.mkdir(parents=True,exist_ok=True)
        if not BACKUP.exists():shutil.copy2(baseline,BACKUP)
        assert sha(BACKUP)==BASE
        for target in TARGETS:
            shutil.copy2(RELEASE,target);assert sha(target)==sha(RELEASE);report['installed_paths'].append(str(target))
        report['backup']=str(BACKUP)
    (ROOT/'build/divine-armor-release-checks.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    print(json.dumps(report,ensure_ascii=False,indent=2))

if __name__=='__main__':main()
