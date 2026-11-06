"""Reusable armor and sword building blocks on top of forge3d.Model.

Armor coordinates follow the GeckoLib humanoid template (geo space): body pivot
(0,24,0), arms (+-5,22,0), legs (+-2,12,0). Side s=-1 is the wearer's right.
Swords: grip centred on the origin, blade along +Y, edge plane X/Y, flat faces north/south.
"""
import math

from forge3d import Model

ALL = ('north', 'south', 'east', 'west', 'up', 'down')


def outer(s):
    return 'west' if s < 0 else 'east'


def armor_model(identifier, density=22):
    m = Model(identifier, density=density, atlas_width=2048, bounds=(3, 3.2, 1.4))
    m.bone('armorHead', (0, 24, 0))
    m.bone('armorBody', (0, 24, 0))
    arms = {s: m.bone('armor' + ('Right' if s < 0 else 'Left') + 'Arm', (s * 5, 22, 0)) for s in (-1, 1)}
    legs = {s: m.bone('armor' + ('Right' if s < 0 else 'Left') + 'Leg', (s * 2, 12, 0)) for s in (-1, 1)}
    return m, 'armorBody', arms, legs


def disc(m, name, bone, center, radius, face_mat, ring_mat, decals, facing='north', segments=16, ring_w=.32,
         ring_d=.4, density=96):
    """Round medallion: a polygonal bezel ring plus a single painted face."""
    hide = tuple(f for f in ALL if f != facing)
    if facing in ('north', 'south'):
        m.ring(name + '_ring', bone, center, radius, segments, ring_w, ring_d, ring_mat, plane='xy', bevel=.05)
        size = (radius * 1.9, radius * 1.9, .1)
    else:
        m.ring(name + '_ring', bone, center, radius, segments, ring_w, ring_d, ring_mat, plane='yz', bevel=.05)
        size = (.1, radius * 1.9, radius * 1.9)
    return m.box(name + '_face', bone, center, size, face_mat, hide=hide, density=density, bevel=0, ao=False,
                 decals={facing: decals})


def lames(m, prefix, bone, center, size, count, step, material, rotation=(0, 0, 0), shrink=.25, trim=None,
          faces=('north',), decals_first=None):
    """Overlapping horizontal plates stacked downward (abdomen, back, faulds)."""
    out = []
    for i in range(count):
        c = (center[0], center[1] - step * i, center[2] + math.copysign(.05 * i, center[2] or 1))
        sz = (size[0] - shrink * i, size[1], size[2])
        out.append(m.box(f'{prefix}_{i}', bone, c, sz, material, rotation=rotation, trim=trim, trim_faces=faces,
                         decals={faces[0]: decals_first} if (decals_first and i == 0) else None))
    return out


def tiered_pauldron(m, label, arm, s, plate, trim, count=3, cap_decals=None, cheek_decals=None, spread=.55,
                    slope0=22, slope_step=10, width=4.2, depth=6.0, cheeks=True, hem=None):
    o = outer(s)
    lean = lambda deg: (0, 0, -s * deg)
    m.box(f'{label}_cap', arm, (s * 6.25, 24.4, 0), (4.8, 1.05, depth - .2), plate, rotation=lean(9),
          trim=trim, trim_faces=('up', 'north', 'south', o), decals={'up': cap_decals} if cap_decals else None)
    for i in range(count):
        m.box(f'{label}_lame_{i}', arm, (s * (7.0 + spread * i), 23.35 - 1.15 * i, 0), (width, 1.5, depth + .2 * i),
              plate, rotation=lean(slope0 + slope_step * i), trim=trim, trim_faces=('north', 'south', o, 'up'))
    if cheeks:
        for z, tag in ((-1, 'front'), (1, 'rear')):
            f = 'north' if z < 0 else 'south'
            m.box(f'{label}_cheek_{tag}', arm, (s * 7.3, 22.8, z * (depth / 2 + .15)), (3.3, 2.9, .4), plate,
                  rotation=(0, s * z * 10, -s * 28), trim=trim, trim_faces=(f,),
                  decals={f: cheek_decals(-s * z)} if cheek_decals else None)
    if hem:
        last = count - 1
        m.box(f'{label}_hem', arm, (s * (7.6 + spread * last), 23.35 - 1.15 * last - .7, 0), (2.6, .32, depth + .6),
              hem, rotation=lean(slope0 + slope_step * last + 6))


def sleeve_and_bracer(m, label, arm, s, sleeve, bracer, rim, sleeve_decals=None, bracer_decals=None, trim=None,
                      fin=None, fin_decals=None):
    o = outer(s)
    m.box(f'{label}_sleeve', arm, (s * 6, 18.6, 0), (4.7, 4.6, 4.7), sleeve,
          decals={f: sleeve_decals for f in ('north', 'south', o)} if sleeve_decals else None)
    m.box(f'{label}_bracer', arm, (s * 6, 14.7, 0), (4.6, 4.1, 4.6), bracer, trim=trim,
          decals={f: bracer_decals(s) for f in ('north', 'south', o)} if bracer_decals else None)
    m.box(f'{label}_bracer_rim', arm, (s * 6, 16.9, 0), (4.8, .4, 4.8), rim)
    m.box(f'{label}_cuff', arm, (s * 6, 12.6, 0), (5.0, .7, 5.0), rim)
    if fin:
        m.box(f'{label}_bracer_fin', arm, (s * 8.45, 14.9, 0), (.4, 3.0, 1.3), fin, rotation=(0, 0, s * 8),
              decals={o: fin_decals} if fin_decals else None)


def tasset_set(m, label, leg, s, material, trim, hem=None, length=5.1, jewel=None, **opts):
    o = outer(s)
    y = 12.1 - length / 2
    m.box(f'{label}_tasset_front', leg, (s * 2.15, y, -2.75), (4.1, length, .5), material, rotation=(9, 0, s * 3),
          trim=trim, trim_faces=('north',), **opts)
    m.box(f'{label}_tasset_side', leg, (s * 4.5, y + .15, 0), (.5, length - .4, 4.6), material, rotation=(0, 0, s * 9),
          trim=trim, trim_faces=(o,), **opts)
    m.box(f'{label}_tasset_back', leg, (s * 2.15, y, 2.75), (4.1, length, .5), material, rotation=(-9, 0, s * 3),
          trim=trim, trim_faces=('south',), **opts)
    if hem:
        m.box(f'{label}_tasset_hem', leg, (s * 2.15, y - length / 2 + .1, -3.17), (4.1, .35, .35), hem,
              rotation=(9, 0, s * 3))
    if jewel:
        m.box(f'{label}_tasset_jewel', leg, (s * 2.15, y + 1.1, -3.08), (.6, .6, .2), jewel, glow_face=True)


# ---------------- swords ----------------
def sword_model(identifier, density=36):
    m = Model(identifier, density=density, atlas_width=1024, bounds=(3, 4, .8))
    root = m.bone('sword', (0, 0, 0))
    return m, root


def blade(m, bone, base_y, length, width, thick, material, edge, tip=3.0, segments=6, taper=.35,
          fuller=None, edge_w=.32, curve=0.0, profile=None, prefix='blade'):
    """Tapered double-edged blade from stacked slabs, steel edges and a pointed tip."""
    body_len = length - tip
    seg = body_len / segments
    for i in range(segments):
        t = i / max(1, segments - 1)
        w = width * (profile[i] if profile else (1 - taper * t))
        y = base_y + seg * (i + .5)
        x = curve * (y - base_y) ** 2 / max(1, length ** 2) * width
        decals = {f: fuller(i, segments) for f in ('north', 'south')} if fuller else None
        m.box(f'{prefix}_{i}', bone, (x, y, 0), (w - edge_w * 2, seg + .02, thick), material, decals=decals, bevel=.04, grad='x')
        for s in (-1, 1):
            m.box(f'{prefix}_edge_{i}_{s}', bone, (x + s * (w / 2 - edge_w / 2), y, 0), (edge_w, seg + .02, thick * .55),
                  edge, bevel=.03, grad='x')
    # Tip: two edge slabs leaning inward plus a narrowing core.
    w = width * (profile[-1] if profile else (1 - taper))
    top = base_y + body_len
    x = curve * body_len ** 2 / max(1, length ** 2) * width
    for k in range(3):
        f = (k + .5) / 3
        m.box(f'{prefix}_tip_{k}', bone, (x, top + tip * f * .9, 0), ((w - edge_w * 2) * (1 - f) + .1, tip / 3 + .02, thick),
              material, bevel=.04, grad='x')
    angle = math.degrees(math.atan2(w / 2, tip))
    edge_len = math.hypot(w / 2, tip)
    for s in (-1, 1):
        m.box(f'{prefix}_tip_edge_{s}', bone, (x + s * w / 4, top + tip / 2, 0), (edge_w, edge_len, thick * .55), edge,
              rotation=(0, 0, s * angle), bevel=.03, grad='x')


def grip(m, bone, length, radius, wrap, decals=None):
    return m.box('grip', bone, (0, -length / 2 + .2, 0), (radius, length, radius), wrap, decals=decals)
