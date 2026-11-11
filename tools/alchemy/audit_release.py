"""Audit the alchemist table against the last accepted candidate and its native test jar."""
from pathlib import Path
import hashlib,json,shutil,zipfile
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'build/alchemy-table'
BASE=ROOT/'build/eastern-fast-release/sourceofmystery-combat-timing-7cb185d22822.jar'
RUN=ROOT/'build/alchemy-table-smoke/final-native'
HOME=Path('F:/ChatGPT/我的世界神秘之源模组开发/modeling/alchemist_table')
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    assert sha(BASE)=='7cb185d228222a593c56bab074023ce77a1e1604210546864b4919fe3ae524b6'
    assert 'BUILD SUCCESSFUL' in (OUT/'build-final.log').read_text('utf8')
    result=json.loads((RUN/'alchemy-verification.json').read_text('utf8'))
    assert result['master_volume']==0 and result['catalog_count']>=20
    assert all(v for k,v in result.items() if isinstance(v,bool) and k not in ('shader_enabled','real_two_client_multiplayer'))
    saved=json.loads((OUT/'saved-world-verification.json').read_text('utf8'))
    assert saved['twenty_unique_saved_trades'] and saved['valid_saved_material_counts']
    log=(RUN/'launch.log').read_text('utf8',errors='replace')
    assert 'ALCHEMY_SMOKE_OK' in log and 'AssertionError' not in log and 'GL_INVALID_' not in log
    jar=ROOT/'build/libs/sourceofmystery-1.0.0.jar'
    allowed=('SourceOfMystery','block/ModBlocks','block/ModBlockEntities','item/ModItems','client/ClientModEvents',
             'creativetab/ModCreativeTabs','network/ModNetwork')
    with zipfile.ZipFile(BASE) as a,zipfile.ZipFile(jar) as b,zipfile.ZipFile(RUN/'mods/sourceofmystery-shader-check.jar') as tested:
        old={x for x in a.namelist() if not x.endswith('/')};new={x for x in b.namelist() if not x.endswith('/')}
        assert not old-new
        changed={x for x in old&new if a.read(x)!=b.read(x)}
        langs={'assets/sourceofmystery/lang/zh_cn.json','assets/sourceofmystery/lang/en_us.json'}
        assert all(x in langs or x.endswith('.class') and x.startswith(tuple('com/sourceofmystery/'+p for p in allowed)) for x in changed),sorted(changed)
        added=new-old
        assert all(x.startswith('com/sourceofmystery/alchemy/') or x.startswith(('com/sourceofmystery/client/AlchemyScreen','com/sourceofmystery/client/AlchemistTableRenderer','com/sourceofmystery/network/AlchemyTradePacket')) or 'alchemist_table' in x for x in added),sorted(added)
        assets={x for x in old if x.startswith('assets/')}
        assert not (changed&assets)-langs
        for name in langs:
            before=json.loads(a.read(name));after=json.loads(b.read(name));assert all(after.get(k)==v for k,v in before.items())
        assert all(b.read(x)==tested.read(x) for x in new)
        assert not any('Check' in x or 'Smoke' in x or 'CompatibilityFixture' in x or 'lotterycompat' in x for x in new)
        for x in new:
            if x.endswith('.json'):json.loads(b.read(x))
        recipe=json.loads(b.read('data/sourceofmystery/recipes/alchemist_table.json'))
        assert recipe['pattern']==['GOG','OFO','GOG'] and recipe['key']=={'G':{'item':'minecraft:gold_ingot'},'O':{'item':'minecraft:obsidian'},'F':{'item':'sourceofmystery:dark_dimensional_offering'}}
        geo=json.loads(b.read('assets/sourceofmystery/geo/block/alchemist_table.geo.json'))['minecraft:geometry'][0]
        cubes=[c for bone in geo['bones'] for c in bone.get('cubes',[])];assert len(cubes)==135
        for cube in cubes:
            for uv in cube['uv'].values():
                assert all(0<=uv['uv'][i]<=512 and 0<uv['uv_size'][i] and uv['uv'][i]+uv['uv_size'][i]<=512 for i in range(2))
    digest=sha(jar);candidate=OUT/f'sourceofmystery-alchemy-{digest[:12]}.jar'
    if candidate.exists():assert sha(candidate)==digest
    else:shutil.copy2(jar,candidate)
    preview=HOME/'export/preview-20261111-native';preview.mkdir(parents=True,exist_ok=True)
    images=[]
    for p in sorted((RUN/'screenshots').glob('*.png')):
        target=preview/p.name;shutil.copy2(p,target);images.append({'file':p.name,'sha256':sha(target)})
    assert len(images)>=6
    (preview/'preview-info.json').write_text(json.dumps({'jar_sha256':digest,'silent':True,'engine':'Minecraft Forge native client, no shaders','images':images},ensure_ascii=False,indent=2)+'\n','utf8')
    report={'status':'BUILD_AND_SILENT_NATIVE_PASS_APPEARANCE_PENDING_USER','protocol':17,
      'jar':{'path':candidate.relative_to(ROOT).as_posix(),'sha256':digest,'bytes':candidate.stat().st_size},
      'production_entries':len(new),'production_matches_native_run':True,'preserved_existing_assets':len(assets)-2,
      'existing_language_keys_preserved':True,'changed_entries':sorted(changed),'added_entries':sorted(added),'removed_entries':[],
      'native_evidence':RUN.relative_to(ROOT).as_posix(),'native_checks':result,'saved_world_checks':saved,'model_cubes':len(cubes),
      'installed':False,'pushed_to_github':False,
      'limits':['Single silent native client; no shader, full modpack, dedicated server or real two-client multiplayer run.',
                'Foreign namespace recipe fixture verifies registry/recipe integration, not every external mod.',
                'Daily rollover tested by changing world day time; persistence roundtrip and world save checked, not a fresh restart.',
                'Catalog includes safely inspectable static recipes and Forge lower-material tags; unsupported dynamic recipes are skipped.',
                'Subjective appearance is pending user acceptance; MASTER0 means no listening acceptance.']}
    for p in (OUT/'release-verification.json',ROOT/'docs/releases/2026-11-11-alchemist-table.json'):
        p.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n','utf8')
    print(json.dumps({k:report[k] for k in ('status','jar','production_entries','preserved_existing_assets')},ensure_ascii=False,indent=2))
if __name__=='__main__':main()
