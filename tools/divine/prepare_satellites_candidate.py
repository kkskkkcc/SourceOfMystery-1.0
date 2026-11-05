"""Build a review candidate from the installed baseline, overlaying satellite changes only.

Deliberately keeps unapproved equipment work in the shared workspace out of the candidate.
Does not install any JAR. The native fixture runs this exact candidate's production entries.
"""
from pathlib import Path
import hashlib,json,shutil,zipfile
ROOT=Path(__file__).resolve().parents[2]
BASE='c5f6a2b6a8e1ee19e4073e30f9a4d4217d2f9b3b18b43933fa494a60d5e13bf3'
OUT=ROOT/'build/divine-satellites';OUT.mkdir(parents=True,exist_ok=True)
BASELINE=OUT/('baseline-'+BASE+'.jar')
CLASS_PREFIXES=['item/ArmorParticlePattern',
 'client/DivineSatelliteModel','client/DivineSatelliteVisual','client/DivineSatelliteRenderer']
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def selected(n):
 if n.endswith('/'):return False
 if n.startswith('com/sourceofmystery/') and n.endswith('.class'):
  tail=n.removeprefix('com/sourceofmystery/').removesuffix('.class').split('$')[0]
  return tail in CLASS_PREFIXES
 return n.startswith(('assets/sourceofmystery/geo/effects/divine_satellite_star.',
  'assets/sourceofmystery/textures/effects/divine_satellites/white_star.',
  'assets/sourceofmystery/textures/effects/divine_satellites/black_star.',
  'assets/sourceofmystery/animations/effects/divine_satellites.animation.json'))
def main():
 if not BASELINE.exists():
  installed=Path('F:/AAAAmc/versions/Integrated MC/mods/sourceofmystery-1.0.0.jar');assert sha(installed)==BASE
  shutil.copy2(installed,BASELINE)
 assert sha(BASELINE)==BASE
 candidate=OUT/'sourceofmystery-1.0.0.jar';test=OUT/'sourceofmystery-satellites-check.jar'
 with zipfile.ZipFile(BASELINE) as old,zipfile.ZipFile(ROOT/'build/libs/sourceofmystery-1.0.0.jar') as built:
  overlays={n for n in built.namelist() if selected(n)};assert len(overlays)>=13,overlays
  original={n for n in old.namelist() if not n.endswith('/')};contents={n:old.read(n) for n in original}
  for n in overlays:contents[n]=built.read(n)
  with zipfile.ZipFile(candidate,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=6) as z:
   for n,data in sorted(contents.items()):z.writestr(n,data)
  changed=sorted(n for n in original if contents[n]!=old.read(n));added=sorted(set(contents)-original)
  assert all(selected(n) for n in changed+added)
  assert not any('divine_satellite_orb' in n or 'divine_satellite_glyph' in n or 'DivineSatellitePacket' in n for n in contents),'Reverted outer model or packet still present'
  for n in original:
   if n.startswith(('com/sourceofmystery/item/SatelliteAttackHandler','com/sourceofmystery/network/ModNetwork')):
    assert built.read(n)==old.read(n),'Original server combat/network class not fully restored: '+n
  assert all(contents[n]==old.read(n) for n in original if 'Sword' in n or '_sword' in n or 'ModItems' in n or 'divine_blessing' in n or 'DivineCG' in n or 'Throne' in n)
  with zipfile.ZipFile(ROOT/'build/reobfShaderCheckJar/output.jar') as fixtures,zipfile.ZipFile(test,'w',compression=zipfile.ZIP_DEFLATED) as z:
   for n,data in sorted(contents.items()):z.writestr(n,data)
   names=[n for n in fixtures.namelist() if n.startswith('com/sourceofmystery/client/DivineSatellitesSmokeCheck') and n.endswith('.class')]
   assert names,'Build the satellite native fixture first'
   for n in names:z.writestr(n,fixtures.read(n))
 report={'status':'REVIEW_CANDIDATE_NOT_INSTALLED','baseline_sha256':BASE,'candidate_sha256':sha(candidate),
  'candidate_bytes':candidate.stat().st_size,'test_jar_sha256':sha(test),'changed_entries':changed,'added_entries':added,
  'all_other_baseline_entries_byte_identical':True,'rejected_weapons_and_chestplates_excluded':True,
  'divine_armor_halo_and_cg_byte_identical':True,'production_entries_identical_in_test_jar':True,'network_protocol':8,
  'outer_visual':'original_five_color_particles','inner_visual':'two_geckolib_stars','server_combat_and_network_restored_byte_identically':True,
  'installed':False}
 (OUT/'candidate-audit.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
 print(json.dumps(report,ensure_ascii=False,indent=2))
if __name__=='__main__':main()
