"""Reproduce with the user's complete modpack, in an isolated flat world and copied config.

Mod jars are read-only hardlinks; the mod under test is a separate copy. No user
save, launcher account, game options, or original modpack config is changed.
"""
from pathlib import Path
import argparse,importlib.util,json,os,re,shutil,subprocess,sys,time,zipfile
ROOT=Path(__file__).resolve().parents[2]
MC=Path('F:/AAAAmc');INSTANCE=MC/'versions/Integrated MC'

def main():
    p=argparse.ArgumentParser();p.add_argument('--label',default='rarity-hotfix');p.add_argument('--prepare-only',action='store_true');p.add_argument('--mode',choices=['offering','enchanter','halo','divine-cg'],default='offering');p.add_argument('--artifact',type=Path,default=ROOT/'build/offering-rarity-hotfix/test.jar');a=p.parse_args()
    out=ROOT/'build/offering-integrated-compat'/a.label;out.mkdir(parents=True,exist_ok=True)
    for name in ['config','defaultconfigs','kubejs','resourcepacks','moonlight-global-datapacks','patchouli_books']:
        if (INSTANCE/name).exists():shutil.copytree(INSTANCE/name,out/name,dirs_exist_ok=True)
    mods=out/'mods';mods.mkdir(exist_ok=True);sources=[]
    for source in (INSTANCE/'mods').glob('*.jar'):
        if 'sourceofmystery' in source.name.lower():continue
        dest=mods/source.name
        if not dest.exists():os.link(source,dest)
        sources.append({'name':source.name,'bytes':source.stat().st_size})
    artifact=a.artifact
    assert artifact.exists(),artifact
    shutil.copy2(artifact,mods/'sourceofmystery-compat-check.jar')
    (out/'mod-manifest.json').write_text(json.dumps(sources,indent=2),encoding='utf8')
    (out/'config/oculus.properties').write_text('enableShaders=false\n',encoding='utf8')
    (out/'options.txt').write_text('renderDistance:5\nsimulationDistance:5\nmaxFps:45\nenableVsync:false\npauseOnLostFocus:false\nguiScale:2\nlang:zh_cn\nfullscreen:false\ntutorialStep:none\nsoundCategory_master:0.0\n',encoding='utf8')
    world=out/'saves/compat-smoke';world.mkdir(parents=True,exist_ok=True)
    if not (world/'level.dat').exists():
        sys.path.insert(0,str(ROOT/'build/oculus-deps'));import nbtlib
        data=nbtlib.load(ROOT/'build/outsider-gametest/world/level.dat');level=data['Data']
        level['WorldGenSettings']['dimensions']['minecraft:overworld']['generator']=nbtlib.Compound({
            'type':nbtlib.String('minecraft:flat'),'settings':nbtlib.Compound({'biome':nbtlib.String('minecraft:plains'),
            'features':nbtlib.Byte(0),'lakes':nbtlib.Byte(0),'layers':nbtlib.List[nbtlib.Compound]([]),'structure_overrides':nbtlib.List[nbtlib.String]([])})})
        level['LevelName']=nbtlib.String('Isolated offering compatibility');level.pop('Player',None);data.save(world/'level.dat')
    spec=importlib.util.spec_from_file_location('smoke_helpers',ROOT/'tools/outsider/run_oculus_smoke.py');helpers=importlib.util.module_from_spec(spec);spec.loader.exec_module(helpers)
    data=json.loads((INSTANCE/'Integrated MC.json').read_text(encoding='utf8'));classpath=[]
    for lib in data['libraries']:
        if not helpers.allowed(lib):continue
        path=lib.get('downloads',{}).get('artifact',{}).get('path')
        if path:
            file=MC/'libraries'/path;assert file.exists(),file;classpath.append(str(file))
    classpath.append(str(INSTANCE/'Integrated MC.jar'));native=out/'natives';native.mkdir(exist_ok=True)
    for file in classpath:
        if 'natives-windows' in file:
            with zipfile.ZipFile(file) as jar:
                for name in jar.namelist():
                    if name.endswith('.dll'):(native/Path(name).name).write_bytes(jar.read(name))
    values={'auth_player_name':'CompatCheck','version_name':'Integrated MC','game_directory':str(out),'assets_root':str(MC/'assets'),
        'assets_index_name':'5','auth_uuid':'0c9cc3fe65b24a42976f53488cabf94e','auth_access_token':'0','clientid':'0','auth_xuid':'0',
        'user_type':'mojang','version_type':'release','natives_directory':str(native),'launcher_name':'SourceOfMysteryCompatibilityTest',
        'launcher_version':'1','classpath':';'.join(classpath),'library_directory':str(MC/'libraries'),'classpath_separator':';'}
    def arguments(items):
        result=[]
        for item in items:
            if isinstance(item,dict):
                if not helpers.allowed(item):continue
                item=item['value']
            result.extend(re.sub(r'\$\{([^}]+)\}',lambda m:values[m[1]],s) for s in (item if isinstance(item,list) else [item]))
        return result
    flag='divineCgCheck' if a.mode=='divine-cg' else 'divineHaloCheck' if a.mode=='halo' else 'enchanterCheck' if a.mode=='enchanter' else 'offeringPackCheck'
    command=['-Xmx6G','-Dsourceofmystery.'+flag+'=true']+arguments(data['arguments']['jvm'])+[data['mainClass']]+arguments(data['arguments']['game'])
    command+=['--quickPlaySingleplayer','compat-smoke','--width','1280','--height','800']
    argfile=out/'launch.args';argfile.write_text('\n'.join('"'+s.replace('\\','/').replace('"','\\"')+'"' for s in command),encoding='utf8')
    print('Prepared',len(sources)+1,'mod jars in',out,flush=True)
    if a.prepare_only:return
    java=Path('C:/Program Files/Eclipse Adoptium/jdk-17.0.19.10-hotspot/bin/java.exe')
    with (out/'launch.log').open('w',encoding='utf8') as log:process=subprocess.run([str(java),'@'+str(argfile)],cwd=out,stdout=log,stderr=subprocess.STDOUT,timeout=900)
    log=(out/'launch.log').read_text(encoding='utf8',errors='replace')
    marker='DIVINE_CG_OK' if a.mode=='divine-cg' else 'DIVINE_HALO_OK' if a.mode=='halo' else 'ENCHANTER_SMOKE_OK' if a.mode=='enchanter' else 'OFFERING_PACK_COMPAT_OK'
    assert process.returncode==0 and marker in log,'Full-pack test failed; inspect '+str(out/'launch.log')
    print((out/('divine-cg-verification.json' if a.mode=='divine-cg' else 'divine-halo-verification.json' if a.mode=='halo' else 'enchanter-verification.json' if a.mode=='enchanter' else 'compatibility.json')).read_text(),flush=True)

if __name__=='__main__':main()
