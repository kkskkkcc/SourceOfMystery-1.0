"""Audit and optionally install the enchanter, rarity compatibility fix and luck-aware lottery."""
from pathlib import Path
import argparse,hashlib,json,shutil,zipfile
ROOT=Path(__file__).resolve().parents[2]
BASE='b6e0a932a64c2dae2932bce20124d0d50c66eee53d03007f01a5c4e98b242d26'
BACKUP=ROOT/'build/release-backups'/('sourceofmystery-before-enchanter-and-luck-'+BASE+'.jar')
RELEASE=ROOT/'build/libs/sourceofmystery-1.0.0.jar'
TARGETS=[Path('F:/AAAAmc/versions')/name/'mods/sourceofmystery-1.0.0.jar' for name in ['Integrated MC','1.20.1-Forge_47.4.20']]
ALLOWED=('com/sourceofmystery/SourceOfMystery.class','com/sourceofmystery/block/ModBlocks.class',
 'com/sourceofmystery/block/ModBlockEntities.class','com/sourceofmystery/item/ModItems.class',
 'com/sourceofmystery/creativetab/ModCreativeTabs.class','com/sourceofmystery/client/ClientModEvents.class',
 'com/sourceofmystery/client/OfferingLotteryScreen.class','com/sourceofmystery/network/ModNetwork.class',
 'com/sourceofmystery/offering/OfferingValues','com/sourceofmystery/offering/OfferingMenu',
 'com/sourceofmystery/offering/OfferingLotteryConfig.class','com/sourceofmystery/datagen/ModRecipeProvider.class',
 'com/sourceofmystery/datagen/ModBlockTagsProvider.class','assets/sourceofmystery/lang/',
 'data/minecraft/tags/blocks/mineable/pickaxe.json')
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
 parser=argparse.ArgumentParser();parser.add_argument('--install',action='store_true');a=parser.parse_args()
 baseline=BACKUP if BACKUP.exists() else TARGETS[0];assert sha(baseline)==BASE
 with zipfile.ZipFile(baseline) as old,zipfile.ZipFile(RELEASE) as new,zipfile.ZipFile(ROOT/'build/reobfShaderCheckJar/output.jar') as test:
  changed=[n for n in old.namelist() if not n.endswith('/') and (n not in new.namelist() or old.read(n)!=new.read(n))]
  unexpected=[n for n in changed if not n.startswith(ALLOWED)];assert not unexpected,unexpected
  for n in new.namelist():
   if not n.endswith('/'):assert n in test.namelist() and new.read(n)==test.read(n),('Release/test mismatch',n)
  assert not any(any(t in n for t in ['SmokeCheck','GameTests','CompatibilityFixture','assets/lotterycompat/','assets/enchantcompat/']) for n in new.namelist())
  assets=[n for n in old.namelist() if n.startswith(('assets/sourceofmystery/geo/','assets/sourceofmystery/animations/','assets/sourceofmystery/textures/','assets/sourceofmystery/sounds/')) and not n.endswith('/')]
  assert all(old.read(n)==new.read(n) for n in assets)
  recipe=json.loads(new.read('data/sourceofmystery/recipes/mystery_enchanter.json'));assert recipe['pattern']==['MSM','DED','OOO'] and recipe['key']['D']['item']=='minecraft:diamond_block'
  for n in new.namelist():
   if n.startswith('assets/sourceofmystery/') and 'mystery_enchanter' in n and not n.endswith('/'):
    p=ROOT/'src/main/resources'/n;assert p.exists() and p.read_bytes()==new.read(n),n
 report={'baseline_sha256':BASE,'sha256':sha(RELEASE),'bytes':RELEASE.stat().st_size,'changed_existing_entries':changed,
  'preserved_existing_model_texture_animation_audio_entries':len(assets),'release_matches_real_client_test':True,
  'test_fixtures_excluded':True,'exact_recipe_verified':True,'installed_paths':[]}
 if a.install:
  BACKUP.parent.mkdir(parents=True,exist_ok=True)
  if not BACKUP.exists():shutil.copy2(baseline,BACKUP)
  assert sha(BACKUP)==BASE
  for target in TARGETS:
   assert target.parent.resolve() in [t.parent.resolve() for t in TARGETS]
   assert sha(target) in [BASE,sha(RELEASE)],('Unexpected installed version',str(target))
   shutil.copy2(RELEASE,target);assert sha(target)==sha(RELEASE);report['installed_paths'].append(str(target))
  report['backup']=str(BACKUP)
 (ROOT/'build/enchanter-luck-release-checks.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf8');print(json.dumps(report,ensure_ascii=False,indent=2))
if __name__=='__main__':main()
