"""Verify the current inner-model / original-outer-particle review candidate. Never installs."""
from pathlib import Path
import argparse,hashlib,json,shutil,zipfile
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'build/divine-satellites';ART=ROOT/'art/divine_satellites'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
 parser=argparse.ArgumentParser();parser.add_argument('--label',default='outer-particles');args=parser.parse_args()
 audit=json.loads((OUT/'candidate-audit.json').read_text(encoding='utf8'))
 candidate=OUT/'sourceofmystery-1.0.0.jar';test=OUT/'sourceofmystery-satellites-check.jar'
 assert sha(candidate)==audit['candidate_sha256'] and sha(test)==audit['test_jar_sha256']
 baseline=OUT/('baseline-'+audit['baseline_sha256']+'.jar')
 with zipfile.ZipFile(candidate) as release,zipfile.ZipFile(test) as tested,zipfile.ZipFile(baseline) as original:
  assert not any('SmokeCheck' in n for n in release.namelist())
  assert not any('divine_satellite_glyph' in n or 'divine_satellite_orb' in n or 'DivineSatellitePacket' in n for n in release.namelist())
  for n in release.namelist():assert release.read(n)==tested.read(n),n
  for n in audit['added_entries']:
   if n.startswith('assets/') and not n.endswith('/'):assert release.read(n)==(ROOT/'src/main/resources'/n).read_bytes(),n
  for n in original.namelist():
   if n.startswith(('com/sourceofmystery/item/SatelliteAttackHandler','com/sourceofmystery/network/ModNetwork','com/sourceofmystery/client/ArmorParticleEffect','com/sourceofmystery/item/ArmorParticleServerHandler')):
    assert original.read(n)==release.read(n),'Original particle/attack class changed: '+n
  previous=ART/'rejected-spheres/build/divine-satellites/sourceofmystery-1.0.0.jar'
  with zipfile.ZipFile(previous) as old:
   for n in old.namelist():
    if not n.endswith('/') and ('divine_satellite_star' in n or '/white_star.' in n or '/black_star.' in n):assert old.read(n)==release.read(n),n
 run=ROOT/'build/divine-satellites-smoke'/args.label
 assert sha(run/'mods/sourceofmystery-shader-check.jar')==sha(test)
 result=json.loads((run/'satellites-verification.json').read_text())
 assert result['exactly_five_outer_particles_restored'] and result['busy_satellites_skip_idle_particles']
 assert result['server_satellites_attack_while_inner_models_hidden'] and result['hidden_target_health_after']<result['hidden_target_health_before']
 assert result['moving_alpha']==0 and result['stopped_alpha']==1 and result['test_master_volume']==0
 assert result['inner_geckolib_models']==2 and result['outer_visual']=='original_five_color_particles'
 assert result['unequip_clears_visual_state'] and audit['network_protocol']==8
 log=(run/'launch.log').read_text(encoding='utf8',errors='replace');assert 'DIVINE_SATELLITES_OK' in log and 'AssertionError' not in log
 installed_hashes=[sha(Path('F:/AAAAmc/versions')/instance/'mods/sourceofmystery-1.0.0.jar') for instance in ['Integrated MC','1.20.1-Forge_47.4.20']]
 assert all(value in (audit['baseline_sha256'],sha(candidate)) for value in installed_hashes)
 installed=all(value==sha(candidate) for value in installed_hashes)
 for source,suffix in [('02-idle-detail','idle'),('02b-opposite-view','opposite'),('04-moving-invisible','moving'),('06-stopped-attack-reappears','attack')]:
  shutil.copy2(run/'screenshots'/(source+'.png'),ART/('outer-particles-preview-'+suffix+'.png'))
 report={'status':'INSTALLED' if installed else 'READY_FOR_USER_REVIEW_NOT_INSTALLED','proposal':'inner_gecko_stars_original_outer_particles','candidate_sha256':sha(candidate),
  'candidate_bytes':candidate.stat().st_size,'checks':result,'candidate_audit':audit,'outer_models_and_new_sync_packet_excluded':True,
  'inner_star_assets_preserved':True,'original_server_combat_and_network_classes_restored':True,'network_protocol':8,
  'formal_instances_match_approved_package':installed,'installed':installed,'current_shader_run':False,'full_modpack_tested':False}
 (OUT/'review-verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
 print(json.dumps({k:v for k,v in report.items() if k not in ['candidate_audit','checks']},ensure_ascii=False,indent=2))
if __name__=='__main__':main()
