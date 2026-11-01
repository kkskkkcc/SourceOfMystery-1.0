"""Spring joints on the existing PMX rig (VRMC_springBone-style tail constraints)."""
import struct
import numpy as np
from generate_mesh import OUT

def export(model):
    positions=np.array([list(b.position) for b in model.bones]); joints=[]
    selected=set(range(155,293))|set(range(321,554))|{558}
    for i in sorted(selected):
        b=model.bones[i]
        children=[j for j in selected if model.bones[j].parent_index==i]
        if b.tail_index in children:tail=positions[b.tail_index]-positions[i]
        elif children:tail=positions[min(children)]-positions[i]
        else:tail=(positions[i]-positions[b.parent_index])*.6
        if i==558:tail=np.array([0,-6.,0])
        if np.linalg.norm(tail)<.08:continue
        hair=i<321;short=i<185
        # Spring frequency, damping, angular cone and collision category.
        # Keep the MMD chains responsive while avoiding the explosive
        # oscillation caused by applying game-space velocity in PMX units.
        # Hair gets the softest cone, cloth gets a firmer return to pose.
        joints.append((i,*tail,150 if short else 95 if hair else 110,
                       24 if short else 24 if hair else 24,
                       .08 if short else .16 if hair else .18,
                       0 if short else 1 if hair else 2))
    path=OUT/'meshes/entity/outsider_springs.bin'
    with path.open('wb') as f:
        f.write(struct.pack('<3I',0x4F535031,1,len(joints)))
        for row in joints:f.write(struct.pack('<I6fI',*row))
    print('Exported',len(joints),'runtime spring joints')
