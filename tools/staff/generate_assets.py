"""Silver reliquary staff v2. Python 3 + Pillow; ordinary GeckoLib cubes/planes.

The pointed crystal uses eight fitted cutout facets, without a mesh extension.
"""
from pathlib import Path
import json
import math
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/sourceofmystery"
MODEL = "dimension_breaker_staff"
SIZE, TILE = 512, 64
MATERIALS = ["silver", "polished", "edge", "recess", "grip", "rune", "inlay", "engraved",
             "ceramic", "collar", "lilac", "bolt", "filigree", "core", "tip_metal", "socket"]
MATERIALS += [f"gem_{i}" for i in range(8)] + [f"point_{i}" for i in range(8)]
MAT = {name: i for i, name in enumerate(MATERIALS)}
SIDES = ("north", "south", "east", "west", "up", "down")


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def atlas():
    image = Image.new("RGBA", (SIZE, SIZE), (220, 226, 233, 255))
    glow = Image.new("RGBA", image.size)
    colors = [(183,196,209),(219,229,237),(243,246,248),(94,108,126),
              (124,128,142),(230,218,255),(177,161,213),(191,202,213),
              (226,226,238),(155,173,191),(191,176,222),(220,225,233),
              (203,214,226),(244,237,255),(217,228,238),(114,126,145)]
    gems = [(177,147,220),(207,188,238),(155,129,199),(222,209,246),
            (183,156,224),(224,213,245),(151,124,190),(199,178,231)]
    for index, material in enumerate(MATERIALS):
        tile = Image.new("RGBA", (TILE, TILE))
        base = colors[index] if index < 16 else gems[index % 8]
        for y in range(TILE):
            for x in range(TILE):
                edge = min(x,63-x)
                bevel = 18 if edge < 3 else (-25 if edge < 5 else 0)
                reflection = 17*math.exp(-((x-19)/7)**2)-13*math.exp(-((x-44)/10)**2)
                grain = ((x*7+y*13)%7-3)*.55
                gradient = (31-y)*.20
                if index >= 16:
                    reflection = 22*math.exp(-((x-14-y*.16)/5)**2)
                    gradient = (31-y)*.42
                    bevel = 12 if edge < 3 else 0
                alpha = 0 if material.startswith("point_") and abs(x-31.5)>y*.5 else 255
                tile.putpixel((x,y),tuple(round(max(0,min(255,c+bevel+reflection+grain+gradient))) for c in base)+(alpha,))
        draw = ImageDraw.Draw(tile)
        if material in ("engraved","filigree","collar","socket"):
            for y in (32,):
                motif=[(17,y),(25,y-7),(32,y),(39,y-7),(47,y),(39,y+7),(32,y),(25,y+7),(17,y)]
                draw.line(motif,fill=(106,124,146,255),width=2)
                draw.line([(x,y-1) for x,y in motif],fill=(236,242,247,255),width=1)
                draw.rectangle((30,y-2,33,y+2),fill=(219,211,240,255))
        elif material == "grip":
            for d in range(-64,128,12):
                draw.line((0,d,64,d+40),fill=(182,185,198,255),width=2)
                draw.line((0,d+3,64,d+43),fill=(85,94,112,255),width=1)
                draw.line((0,d+40,64,d),fill=(153,159,175,255),width=1)
        elif material == "rune":draw.line((31,0,31,63),fill=(253,249,255,255),width=10)
        elif material == "inlay":
            draw.line((10,0,10,63),fill=(228,215,248,255),width=3)
            draw.line((52,0,52,63),fill=(128,112,168,255),width=3)
        elif material == "bolt":
            draw.rectangle((5,5,58,58),outline=(248,249,250,255),width=3)
            draw.line((18,32,46,32),fill=(91,105,126,255),width=3)
        elif material == "core":draw.polygon([(32,1),(49,32),(32,62),(15,32)],fill=(254,249,255,255))
        if material.startswith("gem_"):
            # Sparse internal fractures distinguish a cut crystal from painted plastic.
            vein=[(12,7),(18,20),(14,29),(25,39),(21,56)]
            draw.line(vein,fill=(224,209,248,255),width=1)
            draw.line([(14,29),(29,24),(36,29)],fill=(204,182,235,255),width=1)
            draw.line([(25,39),(35,47),(49,43)],fill=(194,170,227,255),width=1)
        origin=((index%8)*TILE,(index//8)*TILE)
        image.paste(tile,origin)
        if material in ("rune","core"):glow.paste(tile,origin)
        elif material.startswith("gem_"):
            light=Image.new("RGBA",(TILE,TILE));traces=ImageDraw.Draw(light)
            for x in (4,59):traces.line((x,0,x,63),fill=(155,132,201,255),width=1)
            traces.line(vein,fill=(197,176,231,255),width=1)
            traces.line([(14,29),(29,24),(36,29)],fill=(171,145,214,255),width=1)
            glow.paste(light,origin)
        elif material.startswith("point_"):
            light=Image.new("RGBA",(TILE,TILE))
            for y in range(TILE):
                for x in range(TILE):
                    edge=y*.5-abs(x-31.5)
                    if 0<=edge<1.2:light.putpixel((x,y),(155,132,201,255))
            glow.paste(light,origin)
    image.save(ASSETS/"textures/item"/f"{MODEL}.png")
    glow.save(ASSETS/"textures/item"/f"{MODEL}_glowmask.png")


def uv(material,flip=False,whole=False):
    index=MAT[material]; margin=0 if whole else 4; size=TILE-2*margin
    return {"uv":[index%8*TILE+margin,index//8*TILE+margin+(size if flip else 0)],
            "uv_size":[size,-size if flip else size]}


def cube(origin,size,material="silver",pivot=None,rotation=None,faces=None,flip=False):
    result={"origin":[round(v,5) for v in origin],"size":[round(v,5) for v in size],
            "uv":{face:uv(material,flip,material.startswith("point_")) for face in (faces or SIDES)}}
    if rotation and any(rotation):
        result.update(pivot=[round(v,5) for v in pivot],rotation=[round(v,5) for v in rotation])
    return result


def block(b,center,size,material="silver",rotation=None):
    b["cubes"].append(cube([c-s/2 for c,s in zip(center,size)],size,material,center,rotation))


def bone(name,parent="staff",pivot=(0,0,0),rotation=None):
    b={"name":name,"pivot":list(pivot),"cubes":[]}
    if parent:b["parent"]=parent
    if rotation:b["rotation"]=rotation
    return b


def rod(b,a,z,width,depth,material="silver"):
    """Continuous rectangular ribbon aligned with an arbitrary 3D segment."""
    dx,dy,dz=[z[i]-a[i] for i in range(3)]; length=math.sqrt(dx*dx+dy*dy+dz*dz)
    b["cubes"].append(cube([a[0]-width/2,a[1]-.025,a[2]-depth/2],[width,length+.05,depth],
                          material,a,[-math.degrees(math.atan2(dz,dy)),0,math.degrees(math.atan2(dx,math.hypot(dy,dz)))]))


def ring(b,y,radius,thickness,height,material="silver",segments=32):
    for k in range(segments):
        angle=k*math.tau/segments; x,z=radius*math.cos(angle),radius*math.sin(angle)
        block(b,(x,y,z),(thickness,height,2*radius*math.tan(math.pi/segments)+.025),
              material,(0,-math.degrees(angle),0))


def sleeve(b,y0,y1,radius,material="silver",sides=8):
    half=math.pi/sides; apothem=radius*math.cos(half); width=2*radius*math.sin(half)
    # Unwrap each tube continuously; repeating an entire tile on every narrow
    # facet turns the grip weave into stretched vertical stripes.
    rows=max(1,round((y1-y0)/(width*sides))) if material == "grip" else 1
    for row in range(rows):
        low=y0+(y1-y0)*row/rows;high=y0+(y1-y0)*(row+1)/rows
        for k in range(sides):
            element=cube([-width/2,low,apothem-.018],[width,high-low,.036],material,
                         [0,(low+high)/2,0],[0,k*360/sides,0])
            for face in ("north","south"):
                face_uv=element["uv"][face]
                face_uv["uv"][0]+=face_uv["uv_size"][0]*k/sides
                face_uv["uv_size"][0]/=sides
            b["cubes"].append(element)


def crystal(b,y0,y1,y2,y3,radius):
    """Eight side facets and sixteen fitted triangular end facets."""
    apothem=radius*math.cos(math.pi/8); width=2*radius*math.sin(math.pi/8)
    for k in range(8):
        side=bone(f"{b['name']}_facet_{k}",b["name"],(0,0,0),[0,k*45,0])
        side["cubes"].append(cube([-width/2,y1,apothem],[width,y2-y1,0],f"gem_{k}",faces=("north","south")))
        for start,end,flip in ((y2,y3,False),(y1,y0,True)):
            height=abs(end-start);slant=math.hypot(apothem,height)
            rotation=math.degrees(math.atan2(apothem,height))*(1 if end>start else -1)
            side["cubes"].append(cube([-width/2,start if end>start else start-slant,apothem],
                [width,slant,0],f"point_{k}",[0,start,apothem],[rotation,0,0],("north","south"),flip))
        yield side


def geometry():
    root=bone("staff",None);bones=[root]
    shaft=bone("shaft");bones.append(shaft)
    for a,z,r,m in [(-23,-17,.46,"silver"),(-17,-4.2,.43,"silver"),
                    (-4.2,4.2,.63,"grip"),(4.2,14.7,.48,"silver"),(14.7,18,.68,"collar")]:sleeve(shaft,a,z,r,m)
    for y,r in [(-22.8,.60),(-18,.59),(-4.4,.81),(4.4,.81),(12.8,.71),(15,.96),(17.4,1.12)]:
        ring(shaft,y,r,.16,.38,"collar",12)
        ring(shaft,y-.24,r+.025,.12,.09,"edge",12)
        ring(shaft,y+.24,r+.025,.12,.09,"edge",12)
    for i in range(4):
        panel=bone(f"shaft_inlay_{i}",rotation=[0,i*90,0]);bones.append(panel)
        for low,high in [(-17.6,-5),(5,12.4)]:
            block(panel,(0,(low+high)/2,.452),(.14,high-low,.055),"recess")
            block(panel,(0,(low+high)/2,.49),(.052,high-low-.35,.04),"rune")
            for y in range(math.ceil(low+1),math.floor(high),2):block(panel,(0,y,.52),(.21,.21,.09),"lilac",[0,0,45])
        for y in [-18,-4.4,4.4,12.8,15]:block(panel,(0,y,.86 if abs(y)<5 else .76),(.24,.24,.12),"bolt",[0,0,45])
    grip=bone("grip_braid");bones.append(grip)
    for turn in range(6):
        for k in range(12):
            a=k*math.tau/12;radius=.65
            block(grip,(radius*math.sin(a),-3.8+(turn+k/12)*1.26,radius*math.cos(a)),
                  (.34,.095,.075),"polished",[0,math.degrees(a),-18])
    base=bone("pommel");bones.append(base)
    sleeve(base,-24.2,-22.9,.68,"engraved");ring(base,-24.1,.66,.13,.18,"edge",12)
    for q in range(4):
        tip=bone(f"pommel_leaf_{q}","pommel",rotation=[0,q*90+45,0]);bones.append(tip)
        rod(tip,(.40,-23.1,0),(.80,-24,0),.28,.35,"polished")
        rod(tip,(.80,-24,0),(.08,-26.2,0),.20,.25,"silver")
    block(base,(0,-24.2,0),(.55,1.6,.55),"lilac",[0,45,0])
    socket=bone("crown_socket",pivot=(0,17,0));bones.append(socket)
    for a,z,r,m in [(16.6,17.7,1.10,"engraved"),(17.7,18.3,1.38,"polished"),
                   (18.3,19.2,1.12,"socket"),(19.2,19.7,1.50,"silver"),(19.7,20.3,1.18,"inlay")]:sleeve(socket,a,z,r,m,12)
    for y,r in [(17.7,1.38),(19.15,1.25),(19.7,1.52)]:ring(socket,y,r,.13,.14,"edge",24)
    # Eight smaller lancet leaves form an openwork calyx beneath the suspended core.
    for q in range(8):
        calyx=bone(f"calyx_{q}",pivot=(0,19,0),rotation=[0,q*45,0]);bones.append(calyx)
        for k in range(12):
            t=k/12;tn=(k+1)/12
            def leaf(s):return (1.03+1.52*math.sin(math.pi*s*.72),19.3+4.5*s,0)
            a,z=leaf(t),leaf(tn);w=.24*(1-t)+.045;d=.56*(1-t)+.025
            rod(calyx,a,z,w,d,"polished")
            if k<10:rod(calyx,(a[0]-.08,a[1],0),(z[0]-.08,z[1],0),.045,d*.65,"inlay")
        block(calyx,(1.65,20.5,-.25),(.21,.45,.08),"lilac",[0,0,-19])
    for q in range(4):
        petal=bone(f"crown_{q}",pivot=(0,18.5,0),rotation=[0,45+q*90,0]);bones.append(petal)
        def path(t):return (.64+4.15*math.sin(math.pi*t*.94),18.7+14.3*t,0)
        for k in range(22):
            t=k/22;a=path(t);z=path((k+1)/22)
            width=(.38+.28*math.sin(math.pi*t))*(1-.93*t**4);depth=(1-t)**.8*1.12+.025
            rod(petal,a,z,width,depth,"silver")
            rod(petal,(a[0]+width*.40,a[1],0),(z[0]+width*.40,z[1],0),.12,depth*.70,"polished")
            rod(petal,(a[0]-width*.44,a[1],0),(z[0]-width*.44,z[1],0),.09,depth*.75,"recess")
            if 3<k<17:rod(petal,(a[0]-.20,a[1],-.02),(z[0]-.20,z[1],-.02),.045,depth*.48,"rune")
        for sign in (-1,1):
            for k in range(9):
                def scroll(s):return (1.0+2.4*s,20.0+3.1*s,sign*(.35+1.05*math.sin(math.pi*s)))
                rod(petal,scroll(k/9),scroll((k+1)/9),.17*(1-k/11),.18,"polished")
        for t in (.25,.43,.61):
            x,y,_=path(t)
            block(petal,(x+.05,y,-.63*(1-t)**.6),(.38,.38,.11),"polished",[0,0,45])
            block(petal,(x+.05,y,-.70*(1-t)**.6),(.19,.19,.10),"inlay",[0,0,45])
        # Silver chevrons follow the face of the leaf, without repeating a tiny atlas over every segment.
        for t in (.20,.32,.44,.56,.68):
            x,y,_=path(t);z=-.59*(1-t)**.8-.035
            rod(petal,(x-.16,y-.19,z),(x,y,z-.025),.045,.045,"edge")
            rod(petal,(x,y,z-.025),(x+.16,y-.19,z),.045,.045,"edge")
    core=bone("crystal",pivot=(0,26,0));bones.append(core)
    bones.extend(crystal(core,22.3,24.4,27.8,30.8,1.52))
    block(core,(0,26,0),(.28,5.0,.28),"core",[0,45,0])
    for q in range(4):
        shard=bone(f"crystal_tracer_{q}","crystal",rotation=[0,q*90+22.5,0]);bones.append(shard)
        block(shard,(0,26.1,1.43),(.085,2.4,.055),"rune")
        block(shard,(0,26.1,1.49),(.29,.29,.035),"core",[0,0,45])
    for i,tilt in enumerate((28,-38)):
        orbit=bone(f"orbit_{i}",pivot=(0,26,0),rotation=[tilt,0,12 if i else -12]);bones.append(orbit)
        r=2.55+i*.40
        ring(orbit,26,r,.22,.26,"filigree",48)
        ring(orbit,25.82,r+.025,.08,.065,"edge",48)
        ring(orbit,26.18,r+.025,.08,.065,"polished",48)
        for k in range(16):
            a=k*math.tau/16;x,z=r*math.cos(a),r*math.sin(a)
            block(orbit,(x,26,z),(.30,.34,.21),"collar",[0,-math.degrees(a),0])
            block(orbit,((r+.15)*math.cos(a),26,(r+.15)*math.sin(a)),(.06,.14,.10),
                  "rune" if k%4 else "core",[0,-math.degrees(a),0])
    return {"format_version":"1.12.0","minecraft:geometry":[{"description":{
        "identifier":f"geometry.{MODEL}","texture_width":SIZE,"texture_height":SIZE,
        "visible_bounds_width":3,"visible_bounds_height":5,"visible_bounds_offset":[0,.3,0]},"bones":bones}]}


def animations():
    result={}
    def add(name,length,loop,bones):result[f"staff.{name}"]={"loop":loop,"animation_length":length,"bones":bones}
    add("idle",8,True,{
        "crystal":{"rotation":{"0":[0,0,0],"8":[0,360,0]},"position":{"0":[0,0,0],"4":[0,.22,0],"8":[0,0,0]}},
        "orbit_0":{"rotation":{"0":[0,0,0],"8":[0,360,0]}},"orbit_1":{"rotation":{"0":[0,0,0],"8":[0,-360,0]}}})
    add("charge",2,True,{
        "crystal":{"scale":{"0":[1,1,1],"1":[1.08,1.04,1.08],"2":[1,1,1]},"rotation":{"0":[0,0,0],"2":[0,360,0]}},
        "orbit_0":{"rotation":{"0":[0,0,0],"2":[0,360,0]},"scale":[1.13,1,1.13]},
        "orbit_1":{"rotation":{"0":[0,0,0],"2":[0,-360,0]},"scale":[1.12,1,1.12]}})
    for name,length in (("cleave",3),("uplift",3),("rift",1),("time_stop",1)):
        add(name,length,False,{
            "crystal":{"scale":{"0":[1,1,1],str(round(length*.6,2)):[1.12,1.08,1.12],str(length):[1,1,1]}},
            "orbit_0":{"rotation":{"0":[0,0,0],str(length):[0,360,0]}},
            "orbit_1":{"rotation":{"0":[0,0,0],str(length):[0,-240,0]}}})
    result["staff.rift"]["bones"]["staff"]={"rotation":{"0":[0,0,0],".25":[90,0,0],".85":[90,0,0],"1":[0,0,0]}}
    result["staff.environment"]={'loop': False, 'animation_length': 4, 'bones': {'crystal': {'rotation': {'0': [0, 0, 0], '1': [0, 120, 0], '3.2': [0, 600, 0], '4': [0, 720, 0]}, 'position': {'0': [0, 0, 0], '.9': [0, 2.5, 0], '3.1': [0, 3, 0], '3.7': [0, 0, 0], '4': [0, 0, 0]}}, 'orbit_0': {'rotation': {'0': [0, 0, 0], '1.2': [25, 150, 0], '3.2': [60, 620, 0], '4': [0, 720, 0]}}, 'orbit_1': {'rotation': {'0': [0, 0, 0], '1.2': [-30, -160, 0], '3.2': [-60, -620, 0], '4': [0, -720, 0]}}}}
    result["staff.domain"]={'loop': False, 'animation_length': 2, 'bones': {'crystal': {'rotation': {'0.0': [0, 0, 0], '0.5': [0, 120, 0], '1.6': [0, 600, 0], '2.0': [0, 720, 0]}, 'position': {'0.0': [0, 0, 0], '0.45': [0, 2.5, 0], '1.55': [0, 3, 0], '1.85': [0, 0, 0], '2.0': [0, 0, 0]}}, 'orbit_0': {'rotation': {'0.0': [0, 0, 0], '0.6': [25, 150, 0], '1.6': [60, 620, 0], '2.0': [0, 720, 0]}}, 'orbit_1': {'rotation': {'0.0': [0, 0, 0], '0.6': [-30, -160, 0], '1.6': [-60, -620, 0], '2.0': [0, -720, 0]}}}}
    result["staff.weather"]={'loop': False, 'animation_length': 3, 'bones': {'crystal': {'rotation': {'0.0': [0, 0, 0], '0.75': [0, 120, 0], '2.4': [0, 600, 0], '3.0': [0, 720, 0]}, 'position': {'0.0': [0, 0, 0], '0.675': [0, 2.5, 0], '2.325': [0, 3, 0], '2.775': [0, 0, 0], '3.0': [0, 0, 0]}}, 'orbit_0': {'rotation': {'0.0': [0, 0, 0], '0.9': [25, 150, 0], '2.4': [60, 620, 0], '3.0': [0, 720, 0]}}, 'orbit_1': {'rotation': {'0.0': [0, 0, 0], '0.9': [-30, -160, 0], '2.4': [-60, -620, 0], '3.0': [0, -720, 0]}}}}
    return {"format_version":"1.8.0","animations":result}


def main():
    model=geometry()
    write_json(ASSETS/"geo/item"/f"{MODEL}.geo.json",model)
    write_json(ASSETS/"animations/item"/f"{MODEL}.animation.json",animations())
    model_path=ASSETS/"models/item"/f"{MODEL}.json"
    item=json.loads(model_path.read_text(encoding="utf-8"));item["textures"]={"particle":f"sourceofmystery:item/{MODEL}"}
    write_json(model_path,item);atlas()
    bones=model["minecraft:geometry"][0]["bones"]
    print(f"Generated staff v2: {sum(len(b['cubes']) for b in bones)} cubes/planes, {len(bones)} bones, {SIZE}px atlas")


if __name__ == "__main__":main()
