from PIL import Image, ImageDraw, ImageFilter
from pathlib import Path
import math, random

OUT=Path('src/main/resources/assets/sourceofmystery/textures/entity/outsider')
OUT.mkdir(parents=True,exist_ok=True)
S=2048
random.seed(7331)

# Original occult seal artwork inspired by the supplied reference plates:
# concentric inscription bands, triangular geometry, eight seal nodes and
# crossed spell bars. Everything is drawn on a transparent canvas.
def polar(cx,cy,r,a): return (cx+r*math.cos(a),cy+r*math.sin(a))
def poly_star(d,cx,cy,r1,r2,n,fill,width=8):
    pts=[]
    for k in range(n*2): pts.append(polar(cx,cy,r1 if k%2==0 else r2,k*math.pi/n-math.pi/2))
    d.line(pts+[pts[0]],fill=fill,width=width,joint='curve')
def ring(d,cx,cy,r,w,col): d.ellipse((cx-r,cy-r,cx+r,cy+r),outline=col,width=w)
def arcseg(d,cx,cy,r,a0,a1,w,col): d.arc((cx-r,cy-r,cx+r,cy+r),math.degrees(a0),math.degrees(a1),fill=col,width=w)
def rune(d,cx,cy,r,a,scale,col,width=6):
    # angular pseudo-script glyph, varied like the supplied engraved circles
    x,y=polar(cx,cy,r,a); t=(-math.sin(a),math.cos(a)); n=(math.cos(a),math.sin(a))
    p0=(x-t[0]*scale*.46,y-t[1]*scale*.46); p1=(x+t[0]*scale*.46,y+t[1]*scale*.46)
    d.line((p0,p1),fill=col,width=width)
    d.line((x-t[0]*scale*.18-n[0]*scale*.25,y-t[1]*scale*.18-n[1]*scale*.25,
            x-t[0]*scale*.18+n[0]*scale*.25,y-t[1]*scale*.18+n[1]*scale*.25),fill=col,width=max(2,width-2))
    if int((a*1000)+r)%3==0:
        q=polar(x,y,scale*.42,a+math.pi/2); d.line((x,y,q[0],q[1]),fill=col,width=max(2,width-2))
def runic_band(d,cx,cy,r,count,phase,col,width=6,scale=22):
    for k in range(count):
        a=phase+2*math.pi*k/count
        rune(d,cx,cy,r,a,scale,col,width)
        if k%4==0:
            arcseg(d,cx,cy,r-scale*.8,a-.07,a+.07,max(2,width-2),col)
def seal_node(d,cx,cy,r,a,col):
    x,y=polar(cx,cy,r,a)
    ring(d,x,y,48,7,col); ring(d,x,y,31,4,col)
    poly_star(d,x,y,29,12,6,col,5)
    d.line((x-35,y,x+35,y),fill=col,width=4);d.line((x,y-35,x,y+35),fill=col,width=4)
    for k in range(6): rune(d,x,y,23,k*math.pi/3+a*.2,9,col,3)
def triangle(d,cx,cy,r,a,col,width):
    pts=[polar(cx,cy,r,a+k*2*math.pi/3) for k in range(3)]
    d.line(pts+[pts[0]],fill=col,width=width,joint='curve')
def band_bar(d,cx,cy,r,a,length,col,width):
    # crossing spell ribbon with short engraved ticks
    dx,dy=math.cos(a),math.sin(a); nx,ny=-dy,dx
    x,y=polar(cx,cy,r,a)
    p1=(x-dx*length/2-nx*18,y-dy*length/2-ny*18);p2=(x+dx*length/2-nx*18,y+dy*length/2-ny*18)
    p3=(x+dx*length/2+nx*18,y+dy*length/2+ny*18);p4=(x-dx*length/2+nx*18,y-dy*length/2+ny*18)
    d.line((p1,p2,p3,p4,p1),fill=col,width=width,joint='curve')
    for k in range(13):
        q=x+dx*(k-6)*length/13; z=y+dy*(k-6)*length/13
        d.line((q-nx*12,z-ny*12,q+nx*12,z+ny*12),fill=col,width=max(2,width-2))

def make(kind):
    art=Image.new('RGBA',(S,S),(0,0,0,0)); d=ImageDraw.Draw(art)
    cx=cy=S//2; white=(205,145,244,235); violet=(136,45,214,232); dim=(73,17,139,205); deep=(45,8,92,185)
    if kind=='base':
        for r,w,c in [(205,8,dim),(245,4,white),(330,8,dim),(360,4,white),(438,7,violet),(482,4,white)]: ring(d,cx,cy,r,w,c)
        for k in range(32):
            a=2*math.pi*k/32; arcseg(d,cx,cy,400,a+.03,a+.22,5,dim if k%2 else white)
        runic_band(d,cx,cy,394,48,.02,white,5,21)
        runic_band(d,cx,cy,310,32,.10,violet,5,25)
        triangle(d,cx,cy,315,-math.pi/2,white,8); triangle(d,cx,cy,315,math.pi/6,dim,6)
        poly_star(d,cx,cy,290,176,8,white,7)
        for k in range(16):
            a=k*math.pi/8; p=polar(cx,cy,260,a);q=polar(cx,cy,468,a)
            d.line((p[0],p[1],q[0],q[1]),fill=dim if k%2 else violet,width=4)
        for k in range(8): seal_node(d,cx,cy,445,k*math.pi/4,white if k%2 else violet)
        for k in range(8):
            a=k*math.pi/4; rune(d,cx,cy,178,a,30,white,5)
        band_bar(d,cx,cy,258,.18,700,white,6); band_bar(d,cx,cy,258,.18+math.pi/2,700,violet,4)
    elif kind=='inner':
        for r,w,c in [(92,6,white),(136,4,violet),(205,7,white),(263,4,dim),(332,7,violet),(405,4,white)]: ring(d,cx,cy,r,w,c)
        poly_star(d,cx,cy,300,160,6,white,8)
        triangle(d,cx,cy,286,-math.pi/2,violet,7); triangle(d,cx,cy,210,math.pi/6,white,6)
        runic_band(d,cx,cy,365,42,.06,white,5,20)
        runic_band(d,cx,cy,246,24,.18,violet,6,27)
        for k in range(12):
            a=k*math.pi/6; p=polar(cx,cy,120,a);q=polar(cx,cy,400,a)
            d.line((p[0],p[1],q[0],q[1]),fill=dim,width=3)
        for k in range(6): seal_node(d,cx,cy,390,k*math.pi/3+.1,violet if k%2 else white)
        # central eye, nested diamonds and four tiny glyph seals
        for rr in [28,56,80]:
            poly_star(d,cx,cy,rr,rr*.45,4,white,5)
        d.ellipse((cx-12,cy-12,cx+12,cy+12),outline=white,width=4)
        for k in range(4): rune(d,cx,cy,170,k*math.pi/2,34,white,5)
        band_bar(d,cx,cy,300,-.34,540,white,5);band_bar(d,cx,cy,300,math.pi-.34,540,violet,4)
    elif kind=='satellite':
        # Four-glyph satellite seal based on the supplied reference: a dense
        # outer inscription wheel, a central hexagram/triangle lock and four
        # node medallions. The game rotates the whole sheet around the core.
        for r,w,c in [(78,5,white),(118,4,violet),(170,7,white),(226,4,dim),(292,7,violet),(365,4,white),(446,6,dim)]:
            ring(d,cx,cy,r,w,c)
        poly_star(d,cx,cy,350,170,8,white,7)
        triangle(d,cx,cy,310,-math.pi/2,violet,7)
        triangle(d,cx,cy,228,math.pi/6,white,5)
        runic_band(d,cx,cy,410,48,.04,white,5,19)
        runic_band(d,cx,cy,275,32,.16,violet,5,23)
        for k in range(16):
            a=k*math.pi/8; d.line((polar(cx,cy,96,a),polar(cx,cy,430,a)),fill=dim,width=3)
        for k in range(4):
            seal_node(d,cx,cy,400,k*math.pi/2+math.pi/4,white if k%2 else violet)
        for k in range(8):
            a=k*math.pi/4; rune(d,cx,cy,205,a,31,white,5)
        band_bar(d,cx,cy,250,.12,540,white,5)
        band_bar(d,cx,cy,250,math.pi/2+.12,540,violet,4)
        for rr in [26,52,86]: poly_star(d,cx,cy,rr,rr*.42,6,white,4)
        d.ellipse((cx-10,cy-10,cx+10,cy+10),outline=white,width=4)
    else:
        for r,w,c in [(70,5,white),(118,6,violet),(182,4,white),(250,7,dim),(328,5,violet),(430,4,white)]: ring(d,cx,cy,r,w,c)
        poly_star(d,cx,cy,405,180,10,dim,6)
        poly_star(d,cx,cy,285,125,8,white,7)
        triangle(d,cx,cy,335,-math.pi/2,violet,6); triangle(d,cx,cy,220,math.pi/6,white,5)
        for r,count,col in [(150,16,white),(220,24,violet),(302,32,white),(375,40,dim)]: runic_band(d,cx,cy,r,count,.04,col,4,18)
        for k in range(8): seal_node(d,cx,cy,435,k*math.pi/4+.2,white if k%2 else violet)
        for k in range(12):
            a=k*math.pi/6; d.line((polar(cx,cy,92,a),polar(cx,cy,390,a)),fill=dim,width=3)
        for k in range(6):
            a=k*math.pi/3; rune(d,cx,cy,275,a,34,white,5)
        band_bar(d,cx,cy,245,.05,580,white,5); band_bar(d,cx,cy,245,math.pi/2+.05,580,violet,4)
        for k in range(6):
            a=k*math.pi/3; x,y=polar(cx,cy,115,a); d.ellipse((x-12,y-12,x+12,y+12),outline=white,width=4)
    # Build a restrained emissive halo from the actual alpha mask; the original
    # art remains fully transparent outside the inscriptions.
    alpha=art.getchannel('A')
    halo=Image.new('RGBA',(S,S),(118,23,205,0)); halo.putalpha(alpha.filter(ImageFilter.GaussianBlur(9)).point(lambda v:min(68,int(v*.26))))
    halo2=Image.new('RGBA',(S,S),(202,118,255,0)); halo2.putalpha(alpha.filter(ImageFilter.GaussianBlur(2.5)).point(lambda v:min(92,int(v*.22))))
    result=Image.alpha_composite(halo,Image.alpha_composite(halo2,art))
    result=result.resize((1024,1024),Image.Resampling.LANCZOS)
    # Hard-clear the outer transparent margin after downsampling so no square
    # border survives texture filtering at the quad edge.
    px=result.load()
    for y in range(1024):
        for x in range(1024):
            if x<8 or x>1015 or y<8 or y>1015: px[x,y]=(0,0,0,0)
    result.save(OUT/f'ritual_circle_{kind}.png',optimize=True)

for k in ('base','inner','sigil','satellite'): make(k)
for p in OUT.glob('ritual_circle_*.png'):
 im=Image.open(p); a=im.getchannel('A');print(p.name,p.stat().st_size,a.getextrema(),sum(v>0 for v in a.getdata()))
