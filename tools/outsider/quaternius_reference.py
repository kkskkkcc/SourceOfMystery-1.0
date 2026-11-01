"""Read CC0 Quaternius glTF skeletal tracks using their original timing and SLERP."""
import json
from functools import lru_cache
from pathlib import Path
import numpy as np
from scipy.spatial.transform import Rotation as R, Slerp

ROOT=Path(__file__).parent/'reference/quaternius'


class Library:
    def __init__(self):
        self.doc=json.loads((ROOT/'library.gltf').read_text())
        self.buffers=[(ROOT/x['uri']).read_bytes() for x in self.doc['buffers']]
        self.nodes=self.doc['nodes'];self.names={n['name']:i for i,n in enumerate(self.nodes)}
        self.parents={c:i for i,n in enumerate(self.nodes) for c in n.get('children',[])}
        self.tracks={};self.durations={}
        for a in self.doc['animations']:
            channels=[]
            for c in a['channels']:
                s=a['samplers'][c['sampler']];times=self.access(s['input']).ravel();values=self.access(s['output'])
                path=c['target']['path']
                interpolation=s.get('interpolation','LINEAR')
                # The downloaded CC0 library uses LINEAR for most transforms;
                # tolerate STEP channels for holds while rejecting cubic data
                # that would require a different tangent layout.
                if interpolation=='CUBICSPLINE':
                    raise ValueError('unsupported cubic spline accessor')
                sampler=Slerp(times,R.from_quat(values)) if path=='rotation' and interpolation=='LINEAR' else None
                channels.append((c['target']['node'],path,times,values,sampler,interpolation))
            self.tracks[a['name']]=channels;self.durations[a['name']]=max(x[2][-1] for x in channels)
        self.rest=self.sample('A_TPose',0)

    def access(self,index):
        a=self.doc['accessors'][index];v=self.doc['bufferViews'][a['bufferView']]
        dtype={5126:'<f4',5123:'<u2',5125:'<u4'}[a['componentType']]
        width={'SCALAR':1,'VEC3':3,'VEC4':4,'MAT4':16}[a['type']]
        offset=v.get('byteOffset',0)+a.get('byteOffset',0)
        assert 'byteStride' not in v
        return np.frombuffer(self.buffers[v['buffer']],dtype,a['count']*width,offset).reshape(-1,width)

    def sample(self,name,t):
        pos=np.array([n.get('translation',[0,0,0]) for n in self.nodes],float)
        rot=[R.from_quat(n.get('rotation',[0,0,0,1])) for n in self.nodes]
        scales=np.array([n.get('scale',[1,1,1]) for n in self.nodes],float)
        for i,path,times,values,s,interpolation in self.tracks[name]:
            t0=np.clip(t,times[0],times[-1])
            if path=='rotation':
                rot[i]=s(t0) if s is not None else R.from_quat(values[np.searchsorted(times,t0,side='right')-1])
            else:
                if interpolation=='STEP': value=values[np.searchsorted(times,t0,side='right')-1]
                else: value=np.array([np.interp(t0,times,values[:,j]) for j in range(values.shape[1])])
                if path=='translation':pos[i]=value
                elif path=='scale':scales[i]=value
        @lru_cache(None)
        def world(i):
            if i not in self.parents:return rot[i],pos[i],scales[i]
            q,p,s=world(self.parents[i]);return q*rot[i],p+q.apply(pos[i]*s),s*scales[i]
        return {n['name']:(world(i)[0],world(i)[1]) for i,n in enumerate(self.nodes)}

    def apply_upper_body(self, model, rotations, base_world, clip, time, amount):
        """Transfer authored parent-relative deltas, keeping PMX IK contacts separate.

        Both models are Y-up. Conjugating through source/target rest parents avoids
        copying Blender bone roll into MMD local axes.
        """
        sample=self.sample(clip,float(np.clip(time,0,self.durations[clip])))
        for bone,name in [(6,'DEF-spine.001'),(8,'DEF-spine.003'),(10,'DEF-neck'),
                          (11,'DEF-head'),(15,'DEF-shoulder.L'),(53,'DEF-shoulder.R')]:
            parent_name=self.nodes[self.parents[self.names[name]]]['name']
            rest_parent=self.rest[parent_name][0]
            local=sample[parent_name][0].inv()*sample[name][0]
            local_rest=rest_parent.inv()*self.rest[name][0]
            world_delta=rest_parent*(local*local_rest.inv())*rest_parent.inv()
            parent=R.from_matrix(base_world[model.bones[bone].parent_index])
            delta=parent.inv()*world_delta*parent
            rotations[bone]=R.from_rotvec(delta.as_rotvec()*amount)*rotations[bone]


if __name__=='__main__':
    lib=Library()
    print({k:round(float(v),3) for k,v in lib.durations.items() if 'Spell' in k or 'Jump' in k})
    print({k:v[1].round(3).tolist() for k,v in lib.rest.items() if any(x in k for x in ['spine','upper_arm','forearm','hand','thigh','foot'])})
