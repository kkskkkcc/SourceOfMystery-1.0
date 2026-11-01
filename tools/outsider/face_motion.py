"""Shared phase expressions for PMX review and Minecraft, sampled on the body clock."""
import json,struct
import numpy as np
from combat_review import DEST,ramp,write_vmd
from generate_face_morphs import NAMES,main as export_morphs
from generate_mesh import OUT

def offering_expressions(t):
    w=np.zeros(len(NAMES));resolve=ramp(t,14.25,15.2)
    w[3]=.52+.26*resolve;w[9]=.15+.17*resolve
    w[10]=.14+.22*resolve;w[11]=.28+.30*resolve
    w[6]=.84*ramp(t,14.5,15.5)
    w[0]=.84*ramp(t,11.64,11.83)*(1-ramp(t,11.92,12.13))
    return w.astype('<f4')

def expressions(name,t,duration):
    w=np.zeros(len(NAMES));active=ramp(t,0,.45)*(1-ramp(t,duration-.65,duration))
    # IDs: blink, smile brow, wide eyes, focused brow, L/R wink,
    # mouth smile, A, O, angry eyes, half-lids, angry brow.
    if name=='offering_intro':
        return offering_expressions(t)
    if name=='flight_idle':w[[1,6,10]]=[.12,.16,.10]
    elif name=='flight_move':w[[3,6,10]]=[.28,.04,.08]
    elif name=='descend':w[[3,9,10]]=[.34,.08,.12]
    elif name=='grounded_idle':w[[1,6,10]]=[.18,.23,.06]
    elif name=='landing':
        impact=np.exp(-((t-.5)/.15)**2)
        w[[3,9,7]]=[.22+.35*impact,.12*impact,.12*impact];w[0]=.65*impact
    elif name=='takeoff':
        strain=np.sin(np.pi*t/duration)**2
        w[[3,9,8]]=[.18+.35*strain,.18*strain,.10*strain]
    elif name=='dark_grasp':
        charge=ramp(t,.55,1.4)*(1-ramp(t,5.5,6.5));release=ramp(t,1.7,2.1)*(1-ramp(t,5.4,5.8))
        w[[3,9,11]]=[.38*active,.32*charge,.16*charge]
        w[6]=.26*release;w[10]=.16*release;w[8]=.06*charge*(1-release)
    elif name=='dark_silence':
        impact=np.exp(-((t-2)/.15)**2);effort=ramp(t,1,1.65)*(1-ramp(t,2.35,3.4))
        w[[3,9,11]]=[.52*active,.38*effort,.48*effort]
        w[0]=.75*impact;w[7]=.28*effort+.14*impact
        w[6]=.10*ramp(t,3.5,4.5)*(1-ramp(t,6.2,7.2))
    elif name=='annihilation':
        chant=ramp(t,3,3.5)*(1-ramp(t,15,15.5))
        release=ramp(t,14.7,15.2)*(1-ramp(t,21.5,22))
        resolve=ramp(t,24,24.5)*(1-ramp(t,29,30.5))
        w[3]=.65*active;w[11]=.26*chant+.56*release
        w[10]=.30*chant;w[9]=.32*release+.18*resolve;w[2]=.12*release
        # Slow alternating mouth shapes convey chanting, without rapid jaw flapping.
        w[7]=chant*(.13+.07*np.sin(2*np.pi*t/.95))+.34*release
        w[8]=chant*(.12+.06*np.sin(2*np.pi*t/.95+np.pi))
        w[6]=.17*resolve
    # Resting face at clip boundaries matches the next state's blend source.
    # Gameplay faces occupy few pixels: stronger authored changes remain readable
    # at normal combat distance, while keeping combined mouth deformation bounded.
    w[[1,3,6,9,10,11]]*=1.65
    w[[7,8]]*=2.1
    w=np.clip(w,0,1)
    if w[7]+w[8]>.85:w[[7,8]]*=.85/(w[7]+w[8])
    return w.astype('<f4')

def preview_blink(t):
    # Review only: the game uses per-entity irregular natural blink timing.
    phase=t%3.3
    return float(ramp(phase,2.4,2.49)*(1-ramp(phase,2.56,2.72)))

def compose(weights,blink):
    w=weights.copy();w[0]=max(w[0],blink)
    w[[2,4,5,9,10]]*=1-w[0]  # Never pull the same eyelids open and closed.
    return w

def main():
    export_morphs();output=OUT/'meshes/entity/outsider_expressions';output.mkdir(parents=True,exist_ok=True)
    from export_boss_runtime import CLIPS
    from pymeshio.pmx import reader
    model=reader.read_from_file(str(DEST/'outsider_combat.pmx'))
    report={}
    for name in CLIPS:
        frames=np.load(DEST/(name+'.npz'))['frames'];duration=(len(frames)-1)/60
        weights=np.asarray([expressions(name,i/60,duration) for i in range(len(frames))],dtype='<f4')
        np.savez_compressed(DEST/(name+'.face.npz'),weights=weights)
        review_weights=np.asarray([compose(w,preview_blink(i/60)) for i,w in enumerate(weights)])
        write_vmd(DEST/(name+'.vmd'),model,frames,review_weights)
        with (output/(name+'.bin')).open('wb') as f:
            f.write(struct.pack('<5I',0x4F464331,1,len(NAMES),len(weights),60));f.write(weights.tobytes())
        assert np.isfinite(weights).all() and weights.min()>=-1e-6 and weights.max()<=1
        report[name]={'channels':len(NAMES),'maximum_weights':weights.max(axis=0).tolist()}
    (DEST/'expression-checks.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
    print('Exported 12 morph channels and nine phase-expression tracks')

if __name__=='__main__':main()
