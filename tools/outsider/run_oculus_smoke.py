"""Launch an isolated production Forge client with the user's existing shader pack.

No launcher account is read. An offline test profile and a COPY of the GameTest
world are used. No user world or graphics configuration is modified.
"""
from pathlib import Path
import json, os, shutil, subprocess, zipfile, re
import hashlib,time,argparse,sys
ROOT=Path(__file__).resolve().parents[2]
MC=Path('F:/AAAAmc');VERSION='1.20.1-Forge_47.4.20';INSTANCE=MC/'versions'/VERSION
OUT=ROOT/'build/oculus-smoke'

def allowed(item):
    rules=item.get('rules')
    if not rules:return True
    result=False
    for rule in rules:
        operating=rule.get('os',{});features=rule.get('features',{})
        matches=(not operating.get('name') or operating['name']=='windows') and (not operating.get('arch') or operating['arch']=='x86_64')
        matches=matches and all(not value for value in features.values())
        if matches:result=rule['action']=='allow'
    return result

def main():
    global OUT
    parser=argparse.ArgumentParser();parser.add_argument('--clouds',action='store_true');parser.add_argument('--offering',action='store_true');parser.add_argument('--post-intro-flight',action='store_true');parser.add_argument('--vanilla',action='store_true');parser.add_argument('--axis',action='store_true');parser.add_argument('--capture-fps',type=int,default=30);parser.add_argument('--profile',action='store_true');parser.add_argument('--video',action='store_true');parser.add_argument('--label',default='current');args=parser.parse_args()
    if args.clouds:OUT=ROOT/'build/oculus-cloud-smoke'/args.label
    if args.offering:OUT=ROOT/'build/offering-smoke'/args.label
    if args.post_intro_flight:OUT=ROOT/'build/post-intro-flight-smoke'/args.label
    OUT.mkdir(exist_ok=True,parents=True)
    for folder in ['mods','config','shaderpacks','saves']: (OUT/folder).mkdir(exist_ok=True)
    for pattern in ['oculus-*.jar','embeddium-*.jar','geckolib-*.jar']:
        source=next((INSTANCE/'mods').glob(pattern));shutil.copy2(source,OUT/'mods'/source.name)
    artifact=ROOT/'build/reobfShaderCheckJar/output.jar'
    assert artifact.exists(),artifact
    shutil.copy2(artifact,OUT/'mods/sourceofmystery-shader-check.jar')
    pack='ComplementaryReimagined_r5.9.3.zip'
    shutil.copy2(INSTANCE/'shaderpacks'/pack,OUT/'shaderpacks'/pack)
    if args.clouds:(OUT/'shaderpacks'/(pack+'.txt')).write_text('CLOUD_SPEED_MULT=0\n')
    (OUT/'config/oculus.properties').write_text('enableShaders='+str(not args.vanilla).lower()+'\nshaderPack='+pack+'\nmaxShadowRenderDistance=12\n')
    (OUT/'options.txt').write_text('renderDistance:18\nsimulationDistance:5\nmaxFps:60\nenableVsync:false\nfov:0.5\npauseOnLostFocus:false\nhideMatchedNames:true\nguiScale:2\ntutorialStep:none\n')
    world=OUT/'saves/shader-smoke'
    if not (world/'level.dat').exists():
        if args.clouds:
            # A new isolated all-air world gives real Y=-60 and Y=64 views
            # without terrain hiding the effect. Do not edit any user save.
            sys.path.insert(0,str(ROOT/'build/oculus-deps'));import nbtlib
            world.mkdir(parents=True,exist_ok=True)
            data=nbtlib.load(ROOT/'build/outsider-gametest/world/level.dat');level=data['Data']
            level['WorldGenSettings']['dimensions']['minecraft:overworld']['generator']=nbtlib.Compound({
                'type':nbtlib.String('minecraft:flat'),'settings':nbtlib.Compound({'biome':nbtlib.String('minecraft:plains'),
                'features':nbtlib.Byte(0),'lakes':nbtlib.Byte(0),'layers':nbtlib.List[nbtlib.Compound]([]),'structure_overrides':nbtlib.List[nbtlib.String]([])})})
            level['LevelName']=nbtlib.String('Isolated cloud regression');level['GameType']=nbtlib.Int(1)
            level.pop('Player',None);data.save(world/'level.dat')
        else:shutil.copytree(ROOT/'build/outsider-gametest/world',world,ignore=shutil.ignore_patterns('session.lock'),dirs_exist_ok=True)
    data=json.loads((INSTANCE/(VERSION+'.json')).read_text())
    classpath=[]
    for lib in data['libraries']:
        if not allowed(lib):continue
        path=lib.get('downloads',{}).get('artifact',{}).get('path')
        if path:
            file=MC/'libraries'/path
            assert file.exists(),file
            classpath.append(str(file))
    classpath.append(str(INSTANCE/(VERSION+'.jar')))
    native=OUT/'natives';native.mkdir(exist_ok=True)
    for file in classpath:
        if 'natives-windows' in file:
            with zipfile.ZipFile(file) as jar:
                for name in jar.namelist():
                    if name.endswith('.dll'):(native/Path(name).name).write_bytes(jar.read(name))
    values={'auth_player_name':'ShaderCheck','version_name':VERSION,'game_directory':str(OUT),'assets_root':str(MC/'assets'),
            'assets_index_name':'5','auth_uuid':'0c9cc3fe65b24a42976f53488cabf94e','auth_access_token':'0','clientid':'0','auth_xuid':'0',
            'user_type':'mojang','version_type':'release','natives_directory':str(native),'launcher_name':'SourceOfMysteryTest',
            'launcher_version':'1','classpath':';'.join(classpath),'library_directory':str(MC/'libraries'),'classpath_separator':';'}
    def expand(arg):return re.sub(r'\$\{([^}]+)\}',lambda m:values[m[1]],arg)
    def arguments(items):
        result=[]
        for item in items:
            if isinstance(item,dict):
                if not allowed(item):continue
                item=item['value']
            result.extend(expand(x) for x in (item if isinstance(item,list) else [item]))
        return result
    flag='offeringSmoke' if args.offering else 'cloudSmoke' if args.clouds else 'shaderSmoke'
    if args.post_intro_flight:flag='postIntroFlightCheck'
    command=['-Xmx4G','-Dsourceofmystery.'+flag+'=true','-Dsourceofmystery.offeringCaptureFps='+str(args.capture_fps),'-Dsourceofmystery.offeringAxis='+str(args.axis).lower()]+arguments(data['arguments']['jvm'])+[data['mainClass']]+arguments(data['arguments']['game'])
    if args.profile:command.insert(0,'-XX:StartFlightRecording=filename='+str(OUT/'cinematic.jfr')+',settings=profile,dumponexit=true')
    if args.video:
        sys.path.insert(0,str(ROOT/'build/offering-preview-deps'));import imageio_ffmpeg
        command[:0]=['-Dsourceofmystery.offeringMovie=true','-Dsourceofmystery.offeringFfmpeg='+imageio_ffmpeg.get_ffmpeg_exe()]
    command+=['--quickPlaySingleplayer','shader-smoke','--width','1280','--height','800']
    argfile=OUT/'launch.args'
    argfile.write_text('\n'.join('"'+arg.replace('\\','/').replace('"','\\"')+'"' for arg in command),encoding='utf8')
    java=Path('C:/Program Files/Eclipse Adoptium/jdk-17.0.19.10-hotspot/bin/java.exe')
    started=time.time()
    with (OUT/'launch.log').open('w',encoding='utf8') as log:
        process=subprocess.run([str(java),'@'+str(argfile)],cwd=OUT,stdout=log,stderr=subprocess.STDOUT,timeout=180)
    if process.returncode==0:
        log=(OUT/'launch.log').read_text(encoding='utf8',errors='replace')
        if args.post_intro_flight:
            assert 'POST_INTRO_FLIGHT_OK' in log and 'AssertionError' not in log and 'NullPointerException' not in log
            report=json.loads((OUT/'post-flight-physics.json').read_text())
            (OUT/'verification.json').write_text(json.dumps({'real_forge_client':True,'intro_completed_before_capture':True,'normal_ai_unchanged':True,'capture_seconds':34,'shader_enabled':not args.vanilla,'exit_code':0},indent=2))
            print(json.dumps(report,indent=2));return 0
        if args.offering:
            assert 'OFFERING_SMOKE_OK' in log,'Cinematic capture did not finish'
            files=list((OUT/'screenshots').glob('intro-*.png'))
            if args.video:assert (OUT/'capture-silent.mp4').stat().st_size>100000
            elif args.capture_fps>0:assert len(files)>250,len(files)
            (OUT/'verification.json').write_text(json.dumps({'real_forge_capture':True,'frames':len(files),'capture_fps':60 if args.video else args.capture_fps,'video_capture':args.video,'duration_seconds':34,'shader_enabled':not args.vanilla,'axis':args.axis,'exit_code':0},indent=2))
            print('OFFERING_SMOKE_OK',len(files));return 0
        marker='CLOUD_SMOKE_OK' if args.clouds else 'OCULUS_SMOKE_OK'
        assert marker+' shader_pack_active=true' in log,'Client exited without completing shader checks'
        screenshots=[]
        required=['01-sky-ground.png','02-sky-negative-y.png','03-sky-distance8.png','04-sky-above.png','05-near-below-clouds.png','06-terrain-occlusion.png'] if args.clouds else [
                     '01-model.png','02-ritual.png','03-sky-seal.png','04-meteor.png','05-barrage-pillar.png',
                     '06-charge-wind.png','07-speech-confident.png','08-speech-declaration.png','09-speech-shout.png','10-speech-tail.png']
        for name in required:
            file=OUT/'screenshots'/name
            assert file.exists() and file.stat().st_mtime>=started,file
            screenshots.append({'file':str(file),'sha256':hashlib.sha256(file.read_bytes()).hexdigest()})
        report={'oculus':'1.8.0','embeddium':'0.3.31','shader_pack':pack,'shader_pack_active':True,
                'opengl_errors':0,'exit_code':0,'screenshots':screenshots,'user_instance_modified':False,
                'voice_playback_verified':'VOICE_PLAYBACK_OK voice=9' in log}
        if args.clouds:
            from PIL import Image
            import numpy as np
            counts={}
            for name in required:
                pixels=np.asarray(Image.open(OUT/'screenshots'/name).convert('RGB'))[170:730].astype('int16')
                purple=(pixels[:,:,0]>pixels[:,:,1]+25)&(pixels[:,:,2]>pixels[:,:,1]+35)&(pixels[:,:,0]>45)
                counts[name]=int(purple.sum())
            assert min(counts[name] for name in required[:4])>4000,counts
            assert counts[required[4]]>1000 and counts[required[5]]<counts[required[4]]*.5,counts
            assert 'RITUAL_CLOUD_DEPTH_OK format=RGBA16F' in log,'Cloud depth precision regression'
            report.update(purple_world_pixels=counts,cloud_depth_float_precision=True,terrain_occlusion_verified=True)
        (OUT/'verification.json').write_text(json.dumps(report,indent=2),encoding='utf8')
    print('Forge shader smoke client exit',process.returncode)
    return process.returncode

if __name__=='__main__':raise SystemExit(main())
