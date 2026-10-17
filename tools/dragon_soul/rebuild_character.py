"""Archived rejected screenshot remesh. Not called by the active 1ba generator."""
import math

def rebuild(bones, by, cube, beam, add):
    # Preserve animation-compatible joint names, rebuild every visible body surface.
    keep={'longspear'}
    for b in bones:
        if b['name'] not in keep: b['cubes']=[]
        if b['name'] not in {'RightArm','LeftArm'}: b.pop('rotation',None)
    def put(n,cs):by[n]['cubes']=cs
    def box(x,y,z,w,h,d,m):return cube([x,y,z],[w,h,d],m)
    # Compact head, angular cheek/chin and independent expressive facial elements.
    put('Head',[box(-2.9,33.7,-2.55,5.8,5.3,5,6),box(-2.5,32.9,-2.4,5,1.1,4.1,6),box(-1.7,32.5,-2.1,3.4,.55,3.4,7)])
    put('Mouth',[box(-.5,33.7,-2.64,1,.14,.12,7)])
    by['Mouth']['pivot']=[0,33.8,-2.64]
    for side,label in [(-1,'Right'),(1,'Left')]:
        x=side*1.45
        by[label+'Eyelid']['pivot']=[x,35.45,-2.65]
        put(label+'Eyelid',[box(x-.77,35.0,-2.7,1.54,.65,.14,10),box(x-.42,35.04,-2.82,.65,.57,.08,4),box(x-.12,35.08,-2.91,.23,.5,.05,0),box(x-.25,35.42,-2.98,.17,.13,.04,5)])
        by[label+'Eyebrow']['pivot']=[x,36,-2.7]
        put(label+'Eyebrow',[beam([x-.9,35.83,-2.89],[x+.7,35.96,-2.89],.18,8,.17)])
    # Dense parted fringe and layered dark wine hair, with pink inner strands.
    put('Hair',[box(-3.15,37,-2.6,6.3,2.5,5.6,8),box(-3.35,34.5,.2,6.7,4.2,3.15,8)])
    fringe=[]
    for i in range(7):
        x=-2.8+i*.87;end_y=35.65+(.6 if i in (0,6) else 0)+(.35 if i%2 else 0)
        fringe.extend([beam([x,38.9,-2.95],[x+.35,end_y,-3.02],1.05,8,.65),beam([x+.18,38.6,-3.32],[x+.47,end_y+.35,-3.36],.18,9,.1)])
    put('front_h1',fringe)
    sidehair=[]
    for s in [-1,1]:
        sidehair.extend([beam([s*2.9,38,-1.2],[s*3.8,32,-1],1.5,8,1.3),beam([s*3.6,33,-1],[s*4.8,29,1],.85,9,.8)])
    put('corner_h1',sidehair)
    long=[]
    for i in range(9):
        x=-3.2+i*.8;s=-1 if x<0 else 1
        points=[[x,37,3.2],[x*1.35,30,4.5],[x*1.65+s*1.5,22,5.8],[x*1.8+s*4,16+(i%3),6.8],[x*1.9+s*5.5,15+(i%3),7.1]]
        for j in range(4):
            long.append(beam(points[j],points[j+1],1.65-j*.25,8 if i%3 else 9,.9))
            if j>0:long.append(beam([points[j][0],points[j][1],points[j][2]-.38],[points[j+1][0],points[j+1][1],points[j+1][2]-.38],.3,4,.12))
    by['LongHair']['pivot']=[0,37,3.2];put('LongHair',long)
    # Horned headpiece and small mauve side fins.
    crown=[]
    for s in [-1,1]:
        for a,b,w,m in [([s*2.5,38,-.5],[s*4.1,41,0],1.3,0),([s*4.1,41,0],[s*3.5,44,.5],.9,0),([s*3.5,44,.5],[s*3,45,.7],.45,2),([s*2.8,37,-2.5],[s*4.9,38,-1.8],.7,2)]:crown.append(beam(a,b,w,m))
        crown.extend([box(s*2.3-.55,38.5,-2.9,1.1,.8,.55,4),beam([s*3,35,0],[s*5,36,1],1,5,.5)])
    crown.append(box(-.65,38.8,-3.25,1.3,1.0,.6,4));put('dragon_crown',crown)
    # Fitted cuirass: no retained exposed old torso, angular overlapping plates.
    put('UpperBody',[box(-3.2,24,-1.7,6.4,7,3.8,0),box(-2.75,21.8,-1.55,5.5,2.5,3.4,6)])
    put('Collar',[box(-1.45,31,-1.1,2.9,1.8,2.4,6),box(-2.4,30.7,-1.75,4.8,.75,3.5,1)])
    put('dragon_cuirass',[beam([-3,29.8,-2],[0,27,-2.9],2.2,1,1.1),beam([3,29.8,-2],[0,27,-2.9],2.2,1,1.1),beam([-2.9,30,-2.7],[0,28,-3.5],.5,2),beam([2.9,30,-2.7],[0,28,-3.5],.5,2),box(-.6,27.8,-3.55,1.2,1.6,.4,4),beam([-2.8,26,-2.2],[0,24.4,-2.4],.8,2),beam([2.8,26,-2.2],[0,24.4,-2.4],.8,2)])
    put('dragon_belt',[box(-3.2,22.4,-2,6.4,.9,4,0),beam([-3.3,22.6,-2.3],[0,21.2,-2.5],.8,2),beam([3.3,22.6,-2.3],[0,21.2,-2.5],.8,2)])
    put('DownBody',[box(-3.2,19,-1.6,6.4,2.8,3.5,0)])
    for s,label in [(-1,'Right'),(1,'Left')]:
        x=s*4.15
        put(label+'Arm',[box(x-1.15,25,-1.05,2.3,6,2.35,6),box(x-1.3,27.5,-1.25,2.6,2.4,2.7,0)])
        put(label+'ForeArm',[box(x-1.25,20,-1.25,2.5,5,2.65,0)])
        put(label+'Hand',[box(x-1.05,17.7,-1.1,2.1,2.6,2.1,6),box(x-1.12,18.9,-1.3,2.24,1.25,.5,0)])
        put(label+'Arm_pauldron',[beam([s*3.5,30,-.2],[s*6.5,30,-.2],2.7,1,3.8),beam([s*4,31.3,-1.7],[s*7,30.4,-1.7],.65,2),beam([s*4.5,31.2,0],[s*6.6,34.3,.7],1.1,0),beam([s*5.6,30.8,1],[s*8.3,32.7,1.7],.9,2)])
        put(label+'ForeArm_gauntlet',[box(x-1.4,22.4,-1.6,2.8,1.7,3.1,1),box(x-.6,22.7,-1.9,1.2,1.0,.4,4),beam([x,20,-1.4],[x,22.2,-1.8],.6,2)])
        lx=s*2.05
        put(label+'Leg',[box(lx-1.4,12,-1.35,2.8,7.8,3,0),box(lx-1.43,17.1,-1.4,2.86,1.35,3.1,6),box(lx-1.5,15.7,-1.5,3,1.0,3.2,1)])
        put(label+'LowerLeg',[box(lx-1.35,2,-1.3,2.7,9.5,2.85,0)])
        plates=[]
        for y in [3.5,6.5,9.5]:plates.extend([box(lx-1.5,y,-1.65,3,1.8,.65,1),beam([lx-1.1,y,-2],[lx+1.1,y+.8,-2],.4,2)])
        put(label+'LowerLeg_plate',plates)
        put(label+'Foot',[box(lx-1.5,.1,-2.9,3,2.2,4.5,0),box(lx-1.55,.05,-3,3.1,.65,4.65,1),box(lx-1.3,.7,-3.1,2.6,.35,.3,4)])
        t=[]
        for j in range(3):t.append(beam([s*(3.1+j*.7),21-j*.5,.5],[s*(4.8+j*1.4),17-j*1.4,1.8+j],1.9-j*.2,0 if j<2 else 3,1))
        put(label+'Leg_tasset',t)
    put('FrontSkirt',[beam([-2,21,-2],[0,18,-2.5],1.8,1,.65),beam([2,21,-2],[0,18,-2.5],1.8,1,.65)])
    put('BackSkirt',[beam([s*2,22,2],[s*6,13,4],2.5,3,.6) for s in [-1,1]])
    # Bat-wing panels made from tightly joined tapered strips, not feathers.
    for s,name in [(-1,'fly'),(1,'fly2')]:
        P=lambda x,y,z:[s*x,y,z]
        cs=[]
        for a,b,w,m in [((3,30,6),(10,42,7),2.3,1),((10,42,7),(14,55,7),1.6,2),((10,42,7),(30,33,9),1.8,2),((30,33,9),(47,17,11),1.4,2)]:cs.append(beam(P(*a),P(*b),w,m))
        for i in range(4):cs.append(beam(P(8+i*2,40-i*.5,6.5),P(10+i*2,43-i*.6,6.7),1.6,2))
        put(name,cs)
        cs=[]
        # Continuous serrated membrane, scan-converted to thin voxel strips.
        polygon=[(10,41),(30,32),(47,17),(39,20),(36,10),(30,18),(25,7),(19,16),(14,8),(10,28)]
        for row in range(38):
            y=7+row*.9;cross=[]
            for a,b in zip(polygon,polygon[1:]+polygon[:1]):
                if min(a[1],b[1])<=y<max(a[1],b[1]):
                    cross.append(a[0]+(y-a[1])*(b[0]-a[0])/(b[1]-a[1]))
            cross.sort()
            for lo,hi in zip(cross[::2],cross[1::2]):
                x=lo
                while x<hi:
                    width=min(2.0,hi-x)
                    u=(41-y)/34 + .10*math.sin(x*.47)
                    m=11 if u<.45 else (3 if u<.73 else 4)
                    if (row+int(x)*3)%13<2 and u>.45:m=4 if u<.83 else 5
                    cs.append(box(s*x if s>0 else -x-width,y,8.9,width,.92,.3,m))
                    x+=width
        # Each wedge tapers to a jagged pink tip. Adjacent wedges overlap at the root.
        for j in range(4):
            root=(10+j*3.8,40-j*1.6);tip=(17+j*9,12+j*1.3)
            for row in range(24):
                u=row/24;v=(row+1)/24
                x=root[0]*(1-u)+tip[0]*u;y=root[1]*(1-u)+tip[1]*u
                xx=root[0]*(1-v)+tip[0]*v;yy=root[1]*(1-v)+tip[1]*v
                width=(6.3-j*.3)*(1-u)+.3
                material=0 if u<.35 else (11 if u<.62 else (3 if u<.8 else 4))
                
                if row>10 and row%3!=0:cs.append(beam(P(x+width*.18,y,8.05+j*.4),P(xx+width*.18,yy,8.05+j*.4),width*.12,3 if row<19 else 4,.14))
            cs.append(beam(P(*root,8),P(*tip,8.1),.48,1,.65))
        put(name+'_tips',cs)
    # Segmented armored dragon tail visible in the supplied rear/side views.
    tail=[]
    points=[[0,20,2.5],[0,16,5],[1,11,7],[5,7,8],[10,6,7],[13,7,4],[12,9,1],[9,10,-1]]
    for i,(a,b) in enumerate(zip(points,points[1:])):
        width=2.6-i*.2
        tail.append(beam(a,b,width,0,width))
        mid=[(a[k]+b[k])/2 for k in range(3)]
        tail.append(beam([mid[0]-width*.5,mid[1],mid[2]-.5],[mid[0]+width*.5,mid[1],mid[2]-.5],.7,1,1.1))
        tail.append(beam(mid,[mid[0],mid[1]+2-i*.12,mid[2]+.3],.6,3))
    add('dragon_tail','DownBody',[0,20,2.5],tail)
    # Prune unused old bones while retaining joints used by animations.
    required={'Root','AllBody','UpperBody','RightArm','RightForeArm','LeftArm','LeftForeArm','RightLeg','LeftLeg','RightLowerLeg','LeftLowerLeg','LongHair','FrontSkirt','BackSkirt','fly','fly2','fly_tips','fly2_tips','RightEyelid','LeftEyelid','RightEyebrow','LeftEyebrow','Mouth'}|{b['name'] for b in bones if b.get('cubes')}
    for n in list(required):
        while by[n].get('parent'):
            n=by[n]['parent'];required.add(n)
    bones[:]=[b for b in bones if b['name'] in required]
