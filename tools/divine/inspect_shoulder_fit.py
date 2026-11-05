"""Offline geometric fit audit against Minecraft's classic/slim arm + skin sleeve."""
from pathlib import Path
import hashlib,json,zipfile
import numpy as np
from scipy.spatial.transform import Rotation

ROOT=Path(__file__).resolve().parents[2]
BASE='7b7a70bdee9d6f3a949949edd41eb76e570ab3031e681f6da6e8e0dd0382d2ed'
BACKUP=ROOT/'build/release-backups'/('sourceofmystery-before-shoulder-fit-'+BASE+'.jar')
BASELINE=BACKUP if BACKUP.exists() else Path('F:/AAAAmc/versions/Integrated MC/mods/sourceofmystery-1.0.0.jar')
assert hashlib.sha256(BASELINE.read_bytes()).hexdigest()==BASE
RESOURCE='assets/sourceofmystery/geo/armor/divine_blessing.geo.json'
with zipfile.ZipFile(BASELINE) as archive:old=json.loads(archive.read(RESOURCE))['minecraft:geometry'][0]
new=json.loads((ROOT/'src/main/resources'/RESOURCE).read_text(encoding='utf8'))['minecraft:geometry'][0]

def samples(side,slim):
    # Reference source-space bounds after the common per-skin arm-pivot offset:
    # classic 4x12x4 / slim 3x12x4, with the real 0.25 skin-sleeve inflation.
    x0,x1=3.75,(7.25 if slim else 8.25)
    lo=np.array([x0,22,-2.25]);hi=np.array([x1,24.25,2.25]);points=[]
    for axis in range(3):
        others=[i for i in range(3) if i!=axis]
        aa,bb=np.meshgrid(np.linspace(lo[others[0]],hi[others[0]],25),np.linspace(lo[others[1]],hi[others[1]],25))
        for fixed in (lo[axis],hi[axis]):
            p=np.zeros((aa.size,3));p[:,axis]=fixed;p[:,others[0]]=aa.ravel();p[:,others[1]]=bb.ravel();points.append(p)
    result=np.concatenate(points);result[:,0]*=side;return result

def covered(points,cubes):
    result=np.zeros(len(points),bool)
    for cube in cubes:
        origin=np.array(cube['origin']);size=np.array(cube['size']);pivot=np.array(cube.get('pivot',[0,0,0]))
        r=cube.get('rotation',[0,0,0]);rot=Rotation.from_euler('xyz',[-r[0],r[1],-r[2]],degrees=True).as_matrix()
        local=(points-pivot)@rot+pivot
        result|=np.all((local>=origin-1e-6)&(local<=origin+size+1e-6),axis=1)
    return result

rows=[]
for side,label in ((-1,'Right'),(1,'Left')):
    name='armor'+label+'Arm'
    before=next(b['cubes'] for b in old['bones'] if b['name']==name)
    after=next(b['cubes'] for b in new['bones'] if b['name']==name)
    for slim in (False,True):
        points=samples(side,slim);missing_before=int((~covered(points,before)).sum());missing_after=int((~covered(points,after)).sum())
        assert missing_before>0,(label,slim,'Old clipping regression was not reproduced')
        assert missing_after==0,(label,slim,missing_after)
        rows.append({'arm':label,'skin':'slim' if slim else 'classic','skin_surface_samples':len(points),
            'uncovered_before':missing_before,'uncovered_after':missing_after})
        # The changed closed cover and player sleeve share their arm bone. Apply
        # representative rigid poses and verify containment in that same frame.
        liner=after[0];lo=np.array(liner['origin']);hi=lo+liner['size'];joint=np.array([side*5,22,0])
        for angle in ([0,0,0],[40,0,side*5],[-30,15,side*12],[-100,-20,side*8],[-160,0,side*10]):
            rot=Rotation.from_euler('xyz',angle,degrees=True).as_matrix()
            posed=(points-joint)@rot.T+joint;local=(posed-joint)@rot+joint
            assert np.all((local>=lo+.15)&(local<=hi-.15)),(label,slim,angle)

report={'scope':'offline arm-bone geometry, not a Minecraft or MMD-skin runtime test',
    'native_reference':'PlayerModel.createMesh in local Forge 1.20.1; 0.25 sleeve deformation',
    'baseline_sha256':BASE,'results':rows,'all_skin_samples_covered':True,
    'model_source_sha256':hashlib.sha256((ROOT/'src/main/resources'/RESOURCE).read_bytes()).hexdigest(),
    'representative_rigid_arm_poses':5,'minimum_closed_cover_margin_model_units':.2,
    'geometry_changed_cubes':['right_bicep_liner','left_bicep_liner'],
    'game_client_launched':False}
(ROOT/'build/divine-shoulder-fit.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
print(json.dumps(report,indent=2))
