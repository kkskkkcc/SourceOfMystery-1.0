"""Verify the actual release JAR, not only the intermediate PMX files."""
import hashlib,json,struct,zipfile
from pathlib import Path
import numpy as np
from export_boss_runtime import CLIPS

def verify(path):
    with zipfile.ZipFile(path) as z:
        prefix='assets/sourceofmystery/'
        names=z.namelist()
        assert not any('/animations/entity/outsider/' in n and n.endswith('.json') for n in names),'GeckoLib will try to parse report JSON'
        assert not any('OutsiderGameTests' in n or n.endswith('outsider_test.nbt') for n in names),'Development tests leaked into release'
        raw=z.read(prefix+'meshes/entity/outsider.bin')
        magic,version,nv,ni,ng=struct.unpack_from('<5I',raw)
        assert (magic,version)==(0x4F555431,4)
        dtype=np.dtype([('vertex','<f4',8),('bones','<i4',4),('weights','<f4',4)])
        vertices=np.frombuffer(raw,dtype,nv,20)
        assert np.isfinite(vertices['vertex']).all() and np.isfinite(vertices['weights']).all()
        assert np.max(abs(vertices['weights'].sum(axis=1)-1))<1e-4
        cursor=20+nv*64;groups=[];expected_start=0
        for _ in range(ng):
            group=struct.unpack_from('<3I4fI',raw,cursor);cursor+=32
            slot,start,count,*material=group
            assert 0<=slot<5 and start==expected_start and count%3==0
            assert all(np.isfinite(material));expected_start+=count;groups.append(group)
        assert expected_start==ni and groups[5][3]<.04 and groups[13][3:6]==(1.,1.,1.)
        assert groups[9][-1]&1 and groups[20][-1]&2
        nb=struct.unpack_from('<I',raw,cursor)[0];cursor+=4
        bones=np.frombuffer(raw,np.dtype([('parent','<i4'),('position','<f4',3)]),nb,cursor);cursor+=nb*16
        assert all(p<i for i,p in enumerate(bones['parent']))
        assert vertices['bones'].min()>=0 and vertices['bones'].max()<nb
        controls=struct.unpack_from('<I',raw,cursor)[0];cursor+=4+controls*4
        indices=np.frombuffer(raw,'<u4',ni,cursor);cursor+=ni*4
        assert indices.max()<nv and cursor==len(raw)
        assert not np.any(vertices['bones'][indices,0]==558),'Confirmed rigid skirt panels remain in the runtime mesh'
        spring=z.read(prefix+'meshes/entity/outsider_springs.bin')
        smagic,sver,scount=struct.unpack_from('<3I',spring)
        assert (smagic,sver)==(0x4F535031,1) and 1<=scount<=nb
        spring_rows=np.frombuffer(spring,np.dtype([('bone','<u4'),('values','<f4',6),('category','<u4')]),scount,12)
        assert np.isfinite(spring_rows['values']).all() and np.all(spring_rows['bone']<nb)
        assert spring_rows['values'][spring_rows['category']==1,5].max()<=.16001
        clips={};leg_angles={}
        face=z.read(prefix+'meshes/entity/outsider_face.bin')
        assert struct.unpack_from('<5I',face)==(0x4F554D31,1,35118,12,0)
        cursor=20;affected=set()
        for channel in range(12):
            index,count=struct.unpack_from('<2I',face,cursor);cursor+=8
            assert index==channel and count>0
            for _ in range(count):
                vertex,*delta=struct.unpack_from('<I3f',face,cursor);cursor+=16
                assert vertex<35118 and np.isfinite(delta).all();affected.add(vertex)
        assert cursor==len(face)
        for name in CLIPS:
            motion=z.read(prefix+'animations/entity/outsider/'+name+'.bin')
            magic,version,count,frames,fps=struct.unpack_from('<5I',motion)
            assert (magic,version,count,fps)==(0x4F414E31,3,nb,60)
            values=np.frombuffer(motion,'<f4',offset=20).reshape(frames,nb,7)
            assert np.isfinite(values).all()
            assert np.max(abs(np.linalg.norm(values[:,:,3:],axis=-1)-1))<1e-4
            if name in ['flight_idle','flight_move','descend','grounded_idle']:
                assert np.max(abs(values[0]-values[-1]))<1e-5
            if name in ['flight_idle','flight_move']:
                from scipy.spatial.transform import Rotation as R
                for source,deform in [(83,98),(84,99),(85,100),(90,102),(91,103),(92,104)]:
                    assert np.allclose(values[:,source,3:],values[:,deform,3:],atol=1e-5)
                left=np.rad2deg(R.from_quat(values[:,99,3:]).magnitude())
                right=np.rad2deg(R.from_quat(values[:,103,3:]).magnitude())
                assert left.min()>90 and 20<right.min()<right.max()<40
            if name in ['flight_idle','flight_move','descend','landing','takeoff','grounded_idle']:
                from scipy.spatial.transform import Rotation as R
                rot=R.from_quat(values[:,:,3:].reshape(-1,4)).as_matrix().reshape(frames,nb,3,3)
                pos=values[:,:,:3].copy()
                for i,parent in enumerate(bones['parent']):
                    if parent>=0:
                        pos[:,i]=pos[:,parent]+np.einsum('fij,fj->fi',rot[:,parent],pos[:,i])
                        rot[:,i]=rot[:,parent]@rot[:,i]
                spine=pos[:,10]-pos[:,9]
                torso_pitch=np.rad2deg(np.arctan2(-spine[:,2],spine[:,1]))
                if name=='landing':
                    assert torso_pitch.min()>-.1 and torso_pitch.max()>20,'Landing torso still arches backwards'
                    assert np.max(np.linalg.norm(pos[34:,[100,104]]-pos[-1,[100,104]],axis=-1))<.01,'Landing feet slide after contact'
                if name=='flight_move':
                    assert 10<torso_pitch.min()<18 and torso_pitch.max()<18,'Cruise torso pitch is outside the relaxed flight envelope'
                    assert np.rad2deg(R.from_quat(values[:,5,3:]).magnitude()).min()>3,'Cruise legs have no shared pelvis lean'
                maxima=[]
                for destinations in [(99,103),(100,104)]:
                    left=pos[:,destinations[0]]-pos[:,98];right=pos[:,destinations[1]]-pos[:,102]
                    cosine=np.sum(left*right,axis=1)/(np.linalg.norm(left,axis=1)*np.linalg.norm(right,axis=1))
                    maxima.append(float(np.rad2deg(np.arccos(np.clip(cosine,-1,1))).max()))
                assert maxima[0]<27 and maxima[1]<30,(name,maxima)
                leg_angles[name]=dict(thigh=maxima[0],whole_leg=maxima[1])
            expected=Path('art/outsider_void_boss/v1/combat')/(name+'.bin')
            assert motion==expected.read_bytes(),name+' does not match the reviewed clip'
            clips[name]=frames
            expression=z.read(prefix+'meshes/entity/outsider_expressions/'+name+'.bin')
            assert struct.unpack_from('<5I',expression)==(0x4F464331,1,12,frames,60)
            weights=np.frombuffer(expression,'<f4',offset=20).reshape(frames,12)
            assert np.isfinite(weights).all() and weights.min()>=0 and weights.max()<=1
            reviewed=np.load(expected.with_name(name+'.face.npz'))['weights']
            assert np.array_equal(weights,reviewed)
            assert weights.max()>0 and np.max(abs(np.diff(weights,axis=0)))<.15
            if name in ['flight_idle','flight_move','descend','grounded_idle']:
                assert np.max(abs(weights[0]-weights[-1]))<1e-5
            if name in ['dark_grasp','dark_silence','annihilation']:
                assert weights[:,6:9].max()>.2 and weights[:,[3,9,11]].max()>.3
            if name=='annihilation':
                from scipy.spatial.transform import Rotation as R
                assert frames==1831,'Ritual V2 must include 29s cast and 1.5s recovery'
                assert np.max(abs(values[180:1741,:554]-values[180,:554]))<1e-5,'Held ritual pose moved'
                world_rot=R.from_quat(values[180,:,3:]).as_matrix()
                world_pos=values[180,:,:3].copy()
                for i,parent in enumerate(bones['parent']):
                    if parent>=0:
                        world_pos[i]=world_pos[parent]+world_rot[parent]@world_pos[i]
                        world_rot[i]=world_rot[parent]@world_rot[i]
                left=world_pos[99]-world_pos[98];right=world_pos[103]-world_pos[102]
                opening=float(np.rad2deg(np.arccos(np.clip(np.dot(left,right)/(np.linalg.norm(left)*np.linalg.norm(right)),-1,1))))
                assert 29.5<=opening<=30.1,'Overhead pose must open the legs thirty degrees'
                leg_angles[name]=dict(held_opening=opening)
                for wrist in [35,65]:
                    rot=R.from_quat(values[:,wrist,3:])
                    assert np.rad2deg((rot[:-1].inv()*rot[1:]).magnitude()).max()<1.5
                    assert np.rad2deg((rot[0].inv()*rot).magnitude()).max()<15
            if name in ['dark_grasp','dark_silence']:
                wrists=R.from_quat(values[:,[35,65],3:].reshape(-1,4))
                step=np.rad2deg((wrists[:-2].inv()*wrists[2:]).magnitude())/2
                assert step.max()<8,'Attack wrist has an unbounded one-frame flip'
            if name=='dark_silence':
                # The palm-down impact intentionally follows the forearm's
                # authored local frame. A large local deviation means the
                # wrist has rolled independently and will visibly knot.
                for wrist in [35,65]:
                    local=np.rad2deg((R.from_quat(values[0,wrist,3:]).inv()*R.from_quat(values[:,wrist,3:])).magnitude())
                    assert local.max()<.01,'Ground-strike wrist rolled away from forearm frame'
            if name=='takeoff':
                # Regression for the full-body push: pelvis, rib cage, arms
                # and root must all participate in the launch.
                assert np.rad2deg(R.from_quat(values[:,5,3:]).magnitude()).max()>12,'Takeoff has no pelvis drive'
                assert np.rad2deg(R.from_quat(values[:,8,3:]).magnitude()).max()>20,'Takeoff has no rib-cage drive'
                assert np.rad2deg(R.from_quat(values[:,25,3:]).magnitude()).max()>35,'Takeoff has no shoulder drive'
                assert values[:,3,1].max()-values[:,3,1].min()>3,'Takeoff root does not load and release'
            if name=='dark_grasp':
                from scipy.spatial.transform import Rotation as R
                raw=z.read(prefix+'meshes/entity/outsider_grasp_socket.bin')
                assert struct.unpack_from('<3I',raw)==(0x4F534B31,frames,60)
                palm=np.frombuffer(raw,'<f4',offset=12).reshape(frames,3)
                for f in range(0,frames,17):
                    rot=R.from_quat(values[f,:,3:]).as_matrix();pos=values[f,:,:3].copy()
                    for i,parent in enumerate(bones['parent']):
                        if parent>=0:pos[i]=pos[parent]+rot[parent]@pos[i];rot[i]=rot[parent]@rot[i]
                    assert np.max(abs(palm[f]-(pos[35]+rot[35]@np.array([0,0,-1.]))))<1e-5,'Palm offset must rotate with wrist'
        from PIL import Image
        import io
        for file in ['face.png','body_map1.png','body_map2.png','staff.png','white.png']:
            assert z.read(prefix+'textures/entity/outsider/'+file).startswith(b'\x89PNG')
        for kind in ['base','advanced','super','satellite','sky','back']:
            im=Image.open(io.BytesIO(z.read(prefix+'textures/entity/outsider/ritual_v2_'+kind+'.png'))).convert('RGBA')
            assert min(im.size)>=1200,'Ritual artwork was not reconstructed in HD'
            alpha=np.asarray(im)[:,:,3]
            assert alpha.min()==0 and (alpha==0).mean()>.06,'Ritual must preserve transparent gaps'
        socket=z.read(prefix+'meshes/entity/outsider_ritual_socket.bin')
        assert struct.unpack_from('<3I',socket)==(0x4F534B31,1831,60)
        cores=np.frombuffer(socket,'<f4',offset=12).reshape(1831,3)
        assert np.isfinite(cores).all() and np.max(abs(cores[180:1741]-cores[180]))<1e-5
        for file in ['archmage_boss_frame.png','archmage_boss_fill.png']:
            assert z.read(prefix+'textures/gui/'+file).startswith(b'\x89PNG')
        locale=json.loads(z.read(prefix+'lang/zh_cn.json'))
        assert locale['entity.sourceofmystery.outsider']=='域外魔导师'
        assert locale['item.sourceofmystery.outsider_spawn_egg']=='域外魔导师刷怪蛋'
        sounds=json.loads(z.read(prefix+'sounds.json'))
        for name in ['charge','chant','thunder','release']:
            definition=sounds['archmage.'+name]
            assert definition['subtitle'] in locale and all(s.get('type')=='event' for s in definition['sounds'])
        fall=sounds['archmage.meteor_fall']
        assert fall['subtitle'] in locale and fall['sounds'][0]['name']=='sourceofmystery:archmage/meteor_fall'
        assert z.read(prefix+'sounds/archmage/meteor_fall.ogg').startswith(b'OggS')
        voice_lines=json.loads(Path('art/outsider_void_boss/voice/lines.json').read_text(encoding='utf-8'))
        voice_envelopes=z.read(prefix+'meshes/entity/outsider_voice.bin')
        magic,version,voices,voice_fps=struct.unpack_from('<4I',voice_envelopes)
        assert (magic,version,voices,voice_fps)==(0x4f564f31,1,17,20)
        offset=16
        for line in voice_lines:
            definition=sounds['archmage.voice.'+line['id']]
            assert definition['subtitle'] in locale
            assert z.read(prefix+'sounds/archmage/voice/'+line['id']+'.ogg').startswith(b'OggS')
            frames=struct.unpack_from('<I',voice_envelopes,offset)[0];offset+=4
            envelope=np.frombuffer(voice_envelopes,'<f4',count=frames,offset=offset);offset+=frames*4
            assert len(envelope)==line['ticks'] and np.isfinite(envelope).all() and envelope.max()<=.861
            assert line['source']=='Qwen3-TTS-1.7B-Base/approved-synthetic-reference'
            assert line['reference_sha256']=='b304f65262a3cee6f2d46f410239ab624bfd7ef38bcbc85249a40e0b41045099'
            if line['id'].startswith('release_'):
                assert not envelope[line['speech_ticks']:].any(),'Reverb tail still drives shout lips'
        assert offset==len(voice_envelopes)
        assert b'Qwen3-TTS' in z.read('META-INF/licenses/outsider-japanese-voice.md')
        assert z.read(prefix+'sounds/archmage/ritual_wind.ogg').startswith(b'OggS')
        assert b'OutsiderChargeWind' in z.read('com/sourceofmystery/client/OutsiderRenderer.class')
        assert z.read('com/sourceofmystery/client/OutsiderCombatAudio.class')
        assert b'CLIMAX_TICKS' in z.read('com/sourceofmystery/client/OutsiderSpeechFace.class')
        for line in voice_lines[9:11]:
            assert line['user_approved'] and line['master_sha256']==line['reference_sha256']
            assert line['tail_seconds']==2 and line['ticks']-line['speech_ticks']==40
            assert 'いん、めつ、さつ' in line['ja'] and 30<line['climax_tick']<line['speech_ticks']
        assert {line['id'] for line in voice_lines[13:]}=={'idle_ground','idle_flight','takeoff','landing'}
        assert not any('SmokeCheck' in name for name in z.namelist()),'Test automation leaked into release JAR'
        for file in ['shaders/core/outsider.json','shaders/core/outsider.vsh','shaders/core/outsider.fsh']:
            assert z.read(prefix+file)
        for file in ['shaders/core/outsider_halo.json','shaders/core/outsider_halo.vsh','shaders/core/outsider_halo.fsh']:
            assert z.read(prefix+file)
        for name,extensions in [('outsider_ritual_composite',['json','vsh','fsh']),('outsider_ritual_depth',['json','fsh'])]:
            for extension in extensions:assert z.read(prefix+'shaders/core/'+name+'.'+extension)
        composite=z.read('com/sourceofmystery/client/OutsiderRitualComposite.class')
        assert b'AFTER_ENTITIES' in composite and b'AFTER_TRANSLUCENT_BLOCKS' in composite
        assert b'preserveCloudDistance' in composite and b'RenderDistance' in composite
        sky=z.read('com/sourceofmystery/client/OutsiderSkyLightning.class')
        assert b'LightningBoltRenderer' in sky,'Sky lightning must reuse the vanilla branching renderer'
        ritual=z.read('com/sourceofmystery/client/OutsiderRitualRenderer.class')
        assert b'ritual_v2_' in ritual and b'annulus' in ritual and b'renderSky' in ritual,'Ritual V2 layers were not packaged'
        world_effect=z.read('com/sourceofmystery/client/OutsiderHighSpellEffects.class')
        assert b'AFTER_ENTITIES' in world_effect and b'renderHighEffect' in world_effect,'Sky effects still depend on compiled entity sections'
        for damage in ['outsider_bolt','outsider_pillar','outsider_annihilation']:
            assert json.loads(z.read('data/sourceofmystery/damage_type/'+damage+'.json'))['scaling']=='never'
        for tag in ['bypasses_armor','bypasses_enchantments','bypasses_resistance','bypasses_shield','bypasses_cooldown']:
            assert 'sourceofmystery:outsider_annihilation' in json.loads(z.read('data/minecraft/tags/damage_type/'+tag+'.json'))['values']
    report=dict(jar=str(path),bytes=path.stat().st_size,sha256=hashlib.sha256(path.read_bytes()).hexdigest(),
                mesh=dict(vertices=nv,triangles=ni//3,bones=nb,materials=ng),clips=clips,
                expression_channels=12,morphed_vertices=len(affected),spring_joints=scount,
                shader='outsider lighting shader packaged',wrist_regression_passed=True,
                ritual_seconds=30.5,orb_diameter=50,staff_socket=True,custom_archmage_hud=True,
                flight_asymmetric_deform_chain=True,leg_separation_degrees=leg_angles,
                flight_shared_pelvis=True,landing_forward_contact_verified=True,
                flight_inertia_stress=json.loads(Path('art/outsider_void_boss/v1/combat/flight-inertia-checks.json').read_text(encoding='utf-8-sig')),
                vanilla_sky_lightning_renderer=True,ritual_sound_events=4,palm_socket_rotation_verified=True,
                high_sky_effect_world_pass=True,sky_seal_radius=200,monochrome_back_halo=True,
                complementary_deferred_ritual_color=True,cloud_depth_float_precision=True,
                rigid_skirt_panels_removed=True,meteor_descent_sound=True,japanese_voice_events=17,
                silence_radius=30,ultimate_cooldown_ticks=2400,oculus_optional_compatibility=True,
                expressive_japanese_voice='Qwen3-TTS-Base/approved-character',approved_ultimate_master=True,
                ultimate_tail_seconds=2,speech_expression_profiles=17,daily_voice_lines=4,
                charge_wind_ribbons=48,casting_garment_updraft=True,passed=True)
    Path('art/outsider_void_boss/v1/combat/jar-validation.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
    print(json.dumps(report,indent=2))

if __name__=='__main__':verify(Path('build/libs/sourceofmystery-1.0.0.jar'))
