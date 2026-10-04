"""
泣死之主（weeping_death_lord）的全部动画。运行后写出
src/main/resources/assets/sourceofmystery/animations/entity/weeping_death_lord.animation.json

控制器（见 WeepingDeathLord.registerControllers，按注册顺序，后面的覆盖前面的同名骨骼）：
  physics  wl_phys_calm / wl_phys_combat     头发、胸部、裙甲、尾巴、凋零头的循环物理摆动
  base     wl_hover / wl_drift               待机姿势（含镰刀）
  blink    wl_blink                          先左眼、0.5 秒后右眼
  action   其余一次性动作                    出场、出招、后摇、待机动作 3（抛接镰刀）

约定（Blockbench 角度）：x 正 = 向前弯（低头 / 前倾）；四肢 x 负 = 向前抬；
左侧肢体 z 负 = 向外张开，右侧肢体 z 正 = 向外张开；小腿 x 正 = 屈膝；前臂 x 负 = 屈肘。
出招的命中时间写在各动画旁边，Java 里的常量必须与之一致。
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from anim_lib import *  # noqa: E402,F403

OUT = os.path.join(ASSETS, 'animations/entity/weeping_death_lord.animation.json')

# 动作动画需要完整接管的骨骼：手到镰刀的整条链（避免和底层动画混在一起时镰刀脱手）
CHAIN = ('Root', 'AllBody', 'UpBody', 'UpperBody', 'Arm', 'RightArm', 'RightForeArm', 'RightHand',
         'LeftArm', 'LeftForeArm', 'LeftHand', 'Scythe', 'DownBody', 'LeftLeg', 'RightLeg',
         'LeftLowerLeg', 'RightLowerLeg', 'LeftFoot', 'RightFoot', 'Head')


def hold(hand='R', gx=112, gy=0, gz=0, spin=-90, slide=20, scale=1.0):
    return {'@scythe': {'hand': hand, 'grip': (gx, gy, gz), 'spin': spin, 'slide': slide, 'scale': scale}}


def P(**bones):
    """P(RightArm=dict(rot=(..)), ...) 的简写；也可以直接给元组表示 rot"""
    out = {}
    for k, v in bones.items():
        out[k] = v if isinstance(v, dict) else {'rot': v}
    return out


def blend(a, b, u):
    """两个姿势按 u 插值（不存在的骨骼按静止姿势处理）。@scythe 同样插值"""
    out = {}
    for bone in set(a) | set(b):
        if bone.startswith('@'):
            sa, sb = a.get(bone), b.get(bone)
            if sa and sb and sa.get('hand') == sb.get('hand'):
                out[bone] = {k: (lerp(sa[k], sb[k], u) if k != 'hand' else sa[k]) for k in sa}
            else:
                out[bone] = (sa if u < 0.5 else sb) or sa or sb
            continue
        ea, eb = a.get(bone, {}), b.get(bone, {})
        e = {}
        for ch, rest in (('rot', (0, 0, 0)), ('pos', (0, 0, 0)), ('scale', (1, 1, 1))):
            va, vb = ea.get(ch, rest), eb.get(ch, rest)
            if not isinstance(va, (tuple, list)):
                va = (va,) * 3
            if not isinstance(vb, (tuple, list)):
                vb = (vb,) * 3
            if va != rest or vb != rest:
                e[ch] = lerp(tuple(va), tuple(vb), u)
        out[bone] = e
    return out


def scythe_free(anim_fn, t):
    """在时间 t 时（握在手里）镰刀骨骼的 rot / pos，用于从手里接到空中"""
    p = solve_scythe(anim_fn(t))
    return p['Scythe']


# ============================================================ 待机

def hover_pose(t, tense=0.0):
    """待机 1：双脚张开 60 度漂浮，右手单手持镰，刀头朝下、刀刃向内"""
    b = wave(t, 4)
    b2 = wave(t, 4, 0.25)
    return merge(
        P(Root=dict(pos=(0, 1.2 * b, 0)),
          AllBody=(2 + 1.0 * b2 + 4 * tense, 0, 0),
          UpBody=(-1 + 1.0 * wave(t, 4, 0.1), 0, 0),
          Head=(-3 - 1.5 * wave(t, 4, 0.15), 0, 2.5 * wave(t, 4, 0.3)),
          LeftLeg=(-4, 0, -30 - 2 * wave(t, 4, 0.1)),
          RightLeg=(-4, 0, 30 + 2 * wave(t, 4, 0.1)),
          LeftLowerLeg=(18 + 4 * wave(t, 4, 0.2), 0, 0),
          RightLowerLeg=(18 + 4 * wave(t, 4, 0.2), 0, 0),
          LeftFoot=(28, 0, 0), RightFoot=(28, 0, 0),
          LeftArm=(-4 + 3 * wave(t, 4, 0.3), 0, -8 - 3 * wave(t, 4, 0.3)),
          LeftForeArm=(-18 - 3 * wave(t, 4, 0.35), 0, 0),
          RightArm=(-6 + 1.5 * b2 - 6 * tense, 0, 8 + 3 * tense),
          RightForeArm=(-22 - 6 * tense, 0, 0)),
        hold('R', gx=112 + 2 * b2, gz=-28, spin=-90, slide=20))


def drift_pose(t):
    """待机 2：拖着镰刀像水母一样一收一放地漂"""
    p = (t % 2.4) / 2.4
    c = smooth(seg(p, 0.0, 0.3)) - smooth(seg(p, 0.3, 1.0))  # 0 -> 1 -> 0 收缩
    return merge(
        P(Root=dict(pos=(0, 2.0 * math.sin(2 * math.pi * (p - 0.2)), 0)),
          AllBody=(20 - 7 * c, 0, 0),
          UpBody=(-6 + 8 * c, 0, 0),
          Head=(-14 + 6 * c, 0, 0),
          LeftLeg=(6 - 10 * c, 0, -6 - 22 * (1 - c)),
          RightLeg=(6 - 10 * c, 0, 6 + 22 * (1 - c)),
          LeftLowerLeg=(14 + 45 * c, 0, 0), RightLowerLeg=(14 + 45 * c, 0, 0),
          LeftFoot=(35, 0, 0), RightFoot=(35, 0, 0),
          LeftArm=(18 + 25 * c, 0, -14 - 26 * (1 - c)),
          LeftForeArm=(-8 - 20 * c, 0, 0),
          RightArm=(28 + 12 * c, 0, 12 + 10 * (1 - c)),
          RightForeArm=(-6 - 8 * c, 0, 0),
          # 受力的偏移：头发、裙甲、尾巴、凋零头都被甩在后面
          LongHair=(22 + 8 * c, 0, 3 * wave(t, 2.4, 0.1)),
          hl_side=(16 + 6 * c, 0, 0),
          hl_m=(12 + 5 * c, 0, 0),
          Ribbon=(10 + 12 * c, 0, 0),
          SkirtFront=(14 + 10 * c, 0, 0),
          SkirtRight=(10 + 8 * c, 0, 6 * (1 - c)),
          SkirtLeft=(10 + 8 * c, 0, -6 * (1 - c)),
          Tail1=(-18 - 6 * c, 8 * wave(t, 2.4, 0.0), 0),
          Tail2=(-10 - 4 * c, 10 * wave(t, 2.4, -0.12), 0),
          Tail3=(-8, 13 * wave(t, 2.4, -0.24), 0),
          Tail4=(-6, 16 * wave(t, 2.4, -0.36), 0),
          WitherHeadR=dict(pos=(0, 2 + 2 * c, 7 + 3 * c), rot=(-10, 0, 0)),
          WitherHeadL=dict(pos=(0, 2 + 2 * c, 7 + 3 * c), rot=(-10, 0, 0))),
        hold('R', gx=132 - 8 * c, gz=-8, spin=-90, slide=4))


# ============================================================ 物理（呼吸 / 头发 / 尾巴 / 裙甲 / 凋零头）

def phys_pose(t, period, k):
    """k：幅度系数；战斗状态更快更大"""
    def w(phase, mult=1):
        return wave(t, period / mult, phase)
    br = w(0)
    return P(
        Breast=dict(pos=(0, 0.12 * k * br, -0.08 * k * br), scale=(1 + 0.012 * k * br, 1 + 0.02 * k * br, 1 + 0.02 * k * br)),
        Necklace=dict(pos=(0, 0.1 * k * br, 0)),
        LongHair=(3 * k * w(0.1), 0, 2 * k * w(0.3)),
        hl_side=(2 * k * w(0.15), 0, 1.5 * k * w(0.35)),
        hl_m=(2.5 * k * w(0.2), 0, 0),
        Ribbon=(4 * k * w(0.25), 3 * k * w(0.4), 0),
        front_h1=(0.8 * k * w(0.05, 2), 0, 0),
        SkirtFront=(3 * k * w(0.2), 0, 1.5 * k * w(0.45)),
        SkirtRight=(2 * k * w(0.3), 0, 3 * k * w(0.1)),
        SkirtLeft=(2 * k * w(0.35), 0, -3 * k * w(0.15)),
        Tail1=(3 * k * w(0.0), 9 * k * w(0.0), 0),
        Tail2=(3 * k * w(-0.1), 11 * k * w(-0.1), 0),
        Tail3=(4 * k * w(-0.2), 13 * k * w(-0.2), 0),
        Tail4=(4 * k * w(-0.3), 16 * k * w(-0.3), 0),
        WitherHeadR=dict(pos=(0.8 * k * w(0.5), 1.4 * k * w(0.0), 0.8 * k * w(0.25)), rot=(4 * k * w(0.1), 8 * k * w(0.6), 0)),
        WitherHeadL=dict(pos=(0.8 * k * w(0.0), 1.4 * k * w(0.5), 0.8 * k * w(0.75)), rot=(4 * k * w(0.6), 8 * k * w(0.1), 0)),
    )


def blink_pose(t):
    """恶魔不会两只眼同时眨：先左眼，0.5 秒后右眼"""
    def lid(t0):
        u = seg(t, t0, t0 + 0.18)
        closed = math.sin(math.pi * u) ** 0.7 if 0 < u < 1 else 0.0
        return 1 - 0.95 * closed
    return P(LeftEyelid=dict(scale=(1, lid(2.0), 1)),
             RightEyelid=dict(scale=(1, lid(2.5), 1)),
             LeftEyebrow=dict(pos=(0, -0.4 * (1 - lid(2.0)), 0)),
             RightEyebrow=dict(pos=(0, -0.4 * (1 - lid(2.5)), 0)))


# ============================================================ 待机 3：抛起镰刀，另一只手接住舞动，再换回右手

TOSS_LEN = 4.6


def toss_body(t):
    h = hover_pose(t)
    # 0~0.5 下蹲蓄力、右手上抛
    up = smooth(seg(t, 0.15, 0.5)) - smooth(seg(t, 0.5, 1.1))
    crouch = smooth(seg(t, 0.0, 0.3)) - smooth(seg(t, 0.3, 0.55))
    # 左手高举接刀 0.8~1.3，舞动 1.3~3.3，交回右手 3.3~3.9
    raise_l = smooth(seg(t, 0.7, 1.25)) - smooth(seg(t, 3.2, 3.9))
    spin = ease_in_out(seg(t, 1.35, 3.1))
    body_turn = 200 * math.sin(math.pi * ease_in_out(seg(t, 1.5, 2.9)))  # 转身再转回来，结束时朝向不变
    return merge(h, P(
        AllBody=(6 * crouch, body_turn, 0),
        UpBody=(-10 * up + 4 * crouch, 12 * math.sin(2 * math.pi * spin), 0),
        Head=(-12 * raise_l * (1 - spin) - 8 * up, 0, 0),
        RightArm=(-150 * up - 40 * smooth(seg(t, 3.2, 3.7)) * (1 - smooth(seg(t, 3.9, 4.4))), 0, 10 * up),
        RightForeArm=(30 * crouch - 10 * up, 0, 0),
        LeftArm=(-40 * raise_l - 25 * math.sin(2 * math.pi * spin) * raise_l, 0, -135 * raise_l + 40 * math.sin(4 * math.pi * spin) * raise_l),
        LeftForeArm=(-10 * raise_l, 0, 0),
        LeftLeg=(-10 * math.sin(2 * math.pi * spin), 0, 6 * math.sin(4 * math.pi * spin)),
        RightLeg=(10 * math.sin(2 * math.pi * spin), 0, -6 * math.sin(4 * math.pi * spin)),
    ))


def toss_left_grip(t):
    spin = ease_in_out(seg(t, 1.35, 3.1))
    # 左手转动镰刀：绕手腕转两圈
    return hold('L', gx=0 + 720 * spin, gz=0, spin=90, slide=30)


def toss_pose(t):
    body = toss_body(t)
    t_release, t_catch = 0.5, 1.25
    t_pass, t_back = 3.65, 3.75
    if t < t_release:
        return merge(body, hold('R', gx=112 - 112 * smooth(seg(t, 0.15, 0.5)), gz=-28, slide=20))
    if t < t_catch:
        a = scythe_free(lambda x: merge(toss_body(x), hold('R', gx=0, gz=-28, slide=20)), t_release)
        b = scythe_free(lambda x: merge(toss_body(x), toss_left_grip(x)), t_catch)
        u = seg(t, t_release, t_catch)
        pos = lerp(a['pos'], b['pos'], u)
        pos = (pos[0], pos[1] + 40 * math.sin(math.pi * u), pos[2])
        rot = lerp(a['rot'], (b['rot'][0] - 720, b['rot'][1], b['rot'][2]), ease_out(u, 1.5))
        return merge(body, {'Scythe': {'rot': rot, 'pos': pos}})
    if t < t_pass:
        return merge(body, toss_left_grip(t))
    if t < t_back:
        a = scythe_free(lambda x: merge(toss_body(x), toss_left_grip(x)), t_pass)
        b = scythe_free(lambda x: merge(toss_body(x), hold('R', gx=112, gz=-28, slide=20)), t_back)
        u = smooth(seg(t, t_pass, t_back))
        return merge(body, {'Scythe': {'rot': lerp(a['rot'], b['rot'], u), 'pos': lerp(a['pos'], b['pos'], u)}})
    return merge(body, hold('R', gx=112, gz=-28, slide=20))



# ============================================================ 出场

FLOAT_SCYTHE = {'rot': (-90, 0, 0), 'pos': (-16.1, -12.0, -14.0)}  # 竖着漂浮在她右侧，刀头朝上


def floating_scythe(t):
    return {'Scythe': {'rot': (-90, 6 * wave(t, 3.0), 3 * wave(t, 3.0, 0.3)),
                       'pos': (FLOAT_SCYTHE['pos'][0], FLOAT_SCYTHE['pos'][1] + 1.5 * wave(t, 3.0, 0.1), FLOAT_SCYTHE['pos'][2])}}


def eyes(open_amount):
    s = 0.04 + 0.96 * clamp01(open_amount)
    return P(LeftEyelid=dict(scale=(1, s, 1)), RightEyelid=dict(scale=(1, s, 1)),
             LeftEyebrow=dict(pos=(0, -0.5 * (1 - open_amount), 0)),
             RightEyebrow=dict(pos=(0, -0.5 * (1 - open_amount), 0)))


def curl_pose(t, lift=0.0):
    """蜷缩抱膝漂浮；lift 0~1：抬头"""
    b = wave(t, 3.0)
    return merge(P(
        Root=dict(pos=(0, 1.0 * b, 0)),
        AllBody=(12, 0, 0),
        UpBody=(30 - 12 * lift, 0, 0),
        Head=(38 - 40 * lift, 0, 4 * (1 - lift)),
        LeftLeg=(-88, 0, 6), RightLeg=(-88, 0, -6),
        LeftLowerLeg=(125, 0, 0), RightLowerLeg=(125, 0, 0),
        LeftFoot=(30, 0, 0), RightFoot=(30, 0, 0),
        RightArm=(-48, 0, -32), RightForeArm=(-62, 0, 0),
        LeftArm=(-48, 0, 32), LeftForeArm=(-62, 0, 0),
        Tail1=(-10, 45, 0), Tail2=(0, 40, 0), Tail3=(0, 40, 0), Tail4=(0, 35, 0),
        LongHair=(-10 + 3 * b, 0, 0),
        WitherHeadR=dict(pos=(3, -2 + 1.5 * wave(t, 3.0, 0.4), 2)),
        WitherHeadL=dict(pos=(-3, -2 + 1.5 * wave(t, 3.0, 0.9), 2)),
    ), floating_scythe(t))


def intro_curl(t):
    return merge(curl_pose(t), eyes(0))


WAKE_LEN = 5.0


def intro_wake(t):
    lift = ease_in_out(seg(t, 0.4, 4.6))
    return merge(curl_pose(t, lift), eyes(ease_in_out(seg(t, 0.6, 4.6))))


def spread_pose(t):
    s = 0.6 * wave(t, 0.13) + 0.4 * wave(t, 0.21)  # 吼叫时全身发颤
    return merge(P(
        Root=dict(pos=(0, 2, 0)),
        AllBody=(-6, 0, 0),
        UpBody=(-20 + 1.5 * s, 0, 0),
        Head=(-24 + 2 * s, 0, 0),
        LeftLeg=(4, 0, -32), RightLeg=(4, 0, 32),
        LeftLowerLeg=(6, 0, 0), RightLowerLeg=(6, 0, 0),
        LeftFoot=(30, 0, 0), RightFoot=(30, 0, 0),
        RightArm=(-18 + 2 * s, 0, 105), RightForeArm=(-8, 0, 0),
        LeftArm=(-18 - 2 * s, 0, -105), LeftForeArm=(-8, 0, 0),
        Tail1=(-30, 0, 0), Tail2=(-15, 6 * s, 0), Tail3=(-10, 8 * s, 0), Tail4=(-8, 10 * s, 0),
        LongHair=(-25, 0, 0), hl_side=(-18, 0, 0), hl_m=(-15, 0, 0), Ribbon=(-30, 0, 0),
        SkirtFront=(-15, 0, 0), SkirtRight=(-6, 0, 12), SkirtLeft=(-6, 0, -12),
        WitherHeadR=dict(pos=(-6, 3, 0), rot=(-15, 0, 0)),
        WitherHeadL=dict(pos=(6, 3, 0), rot=(-15, 0, 0)),
    ), eyes(1))


ROAR_LEN = 3.0


def intro_roar(t):
    start = intro_wake(WAKE_LEN)
    u = back_out(seg(t, 0.0, 0.35), 1.2)
    return merge(blend(start, spread_pose(t), u), floating_scythe(WAKE_LEN + t))


SHOULDER_LEN = 2.0
SHOULDER_CATCH = 0.7


def shoulder_body(t):
    reach = smooth(seg(t, 0.0, SHOULDER_CATCH))
    lift = ease_in_out(seg(t, SHOULDER_CATCH + 0.05, 1.5))
    settle = ease_in_out(seg(t, 0.0, 1.2))
    shoulder = P(
        Root=dict(pos=(0, 1, 0)), AllBody=(0, 0, 0), UpBody=(-4, 0, 0),
        Head=(6, -8, 7),
        LeftLeg=(-6, 0, -30), RightLeg=(2, 0, 30),
        LeftLowerLeg=(22, 0, 0), RightLowerLeg=(14, 0, 0),
        LeftFoot=(28, 0, 0), RightFoot=(28, 0, 0),
        LeftArm=(8, 0, -24), LeftForeArm=(-45, 0, 0),
        RightArm=(-150, 0, 22), RightForeArm=(-85, 0, 0),
    )
    catch = P(RightArm=(-70, 0, 45), RightForeArm=(-15, 0, 0))
    base = blend(spread_pose(ROAR_LEN), blend(shoulder, merge(shoulder, catch), 1.0), settle)
    if t <= SHOULDER_CATCH:
        return blend(base, merge(base, catch), reach)
    return blend(merge(base, catch), base, lift)


def shoulder_grip(t):
    lift = ease_in_out(seg(t, SHOULDER_CATCH + 0.05, 1.5))
    return hold('R', gx=lerp(40, 175, lift), gz=lerp(0, -20, lift), spin=-90, slide=lerp(20, 26, lift))


def intro_shoulder(t):
    body = merge(shoulder_body(t), eyes(1))
    if t >= SHOULDER_CATCH:
        return merge(body, shoulder_grip(t))
    target = scythe_free(lambda x: merge(shoulder_body(x), shoulder_grip(x)), SHOULDER_CATCH)
    start = floating_scythe(WAKE_LEN + ROAR_LEN + t)['Scythe']
    u = ease_in(seg(t, 0.1, SHOULDER_CATCH), 2)
    return merge(body, {'Scythe': {'rot': lerp(start['rot'], target['rot'], u), 'pos': lerp(start['pos'], target['pos'], u)}})


def back_to_hover(pose_fn, t, t0, t1):
    """从 pose_fn(t) 平滑过渡回待机姿势"""
    u = ease_in_out(seg(t, t0, t1))
    if u <= 0:
        return pose_fn(t)
    return blend(pose_fn(t), hover_pose(t), u)


# ============================================================ 招式 1：劈斩
# 前摇 1.6 s：甩到身后、镰刀变大；1.8 s 砸地（CLEAVE_HIT = 36 tick）；后摇 3.4 s

CLEAVE_LEN = 5.2


def cleave_core(t):
    up = ease_in_out(seg(t, 0.0, 0.45))
    tense = ease_in_out(seg(t, 0.45, 1.6))
    smash = ease_in(seg(t, 1.6, 1.8), 2)
    pull = ease_in_out(seg(t, 2.6, 3.6))
    grow = ease_in_out(seg(t, 0.5, 1.5)) * (1 - pull)
    raised = P(AllBody=(-4, 0, 0), UpBody=(-22 - 8 * tense, 0, 0), Head=(-12, 0, 0),
               RightArm=(-168, 0, -12), RightForeArm=(-15, 0, 0),
               LeftArm=(-168, 0, 12), LeftForeArm=(-15, 0, 0),
               LeftLeg=(10, 0, -24), RightLeg=(-20, 0, 24),
               LeftLowerLeg=(30, 0, 0), RightLowerLeg=(20, 0, 0))
    hit = P(Root=dict(pos=(0, -3, 0)), AllBody=(16, 0, 0), UpBody=(38, 0, 0), Head=(18, 0, 0),
            RightArm=(-42, 0, -14), RightForeArm=(-10, 0, 0),
            LeftArm=(-42, 0, 14), LeftForeArm=(-10, 0, 0),
            LeftLeg=(30, 0, -26), RightLeg=(-35, 0, 26),
            LeftLowerLeg=(40, 0, 0), RightLowerLeg=(15, 0, 0))
    pulled = P(AllBody=(4, 0, 0), UpBody=(8, 0, 0), Head=(0, 0, 0),
               RightArm=(-95, 0, 10), RightForeArm=(-20, 0, 0),
               LeftArm=(-60, 0, -10), LeftForeArm=(-20, 0, 0),
               LeftLeg=(0, 0, -28), RightLeg=(0, 0, 28),
               LeftLowerLeg=(18, 0, 0), RightLowerLeg=(18, 0, 0))
    start = hover_pose(0)
    if t < 0.45:
        pose = blend(start, raised, up)
    elif t < 1.6:
        pose = raised
    elif t < 2.6:
        pose = blend(raised, hit, smash)
        if t > 1.8:
            pose = merge(pose, P(Root=dict(pos=(0, 0.4 * wave(t, 0.1), 0))))
    else:
        pose = blend(hit, pulled, pull)
    gx = lerp(lerp(lerp(112, 15, up), 5, tense), 35, smash) if t < 2.6 else lerp(35, 100, pull)
    return merge(pose, hold('R', gx=gx, gz=lerp(-28, 0, up), spin=-90, slide=lerp(20, 2, up), scale=1 + 1.4 * grow))


def cleave(t):
    return back_to_hover(cleave_core, t, 3.6, 5.2)


# ============================================================ 招式 2：凋零头
# skull_throw 1.6 s：前摇 1.0 s，1.15 s 掷出镰刀（SKULL_THROW_HIT = 23 tick）
# skull_raise 1.0 s：双手抬起，两个凋零头飘到手上
# skull_fire 2 s 循环：右、左交替，每秒 3 发
# skull_end 3.4 s：0.4 s 接住飞回的镰刀，后摇

THROW_LEN = 1.6


def throw_core(t):
    cock = ease_in_out(seg(t, 0.0, 1.0))
    fling = ease_in(seg(t, 1.0, 1.15), 2)
    follow = ease_out(seg(t, 1.15, 1.6))
    cocked = P(AllBody=(-2, 0, 0), UpBody=(-6, -35, 0), Head=(0, 25, 0),
               RightArm=(45, 0, 30), RightForeArm=(-50, 0, 0),
               LeftArm=(-75, 0, -15), LeftForeArm=(-5, 0, 0),
               LeftLeg=(-25, 0, -26), RightLeg=(20, 0, 30),
               LeftLowerLeg=(20, 0, 0), RightLowerLeg=(30, 0, 0))
    thrown = P(AllBody=(10, 0, 0), UpBody=(20, 30, 0), Head=(-5, -20, 0),
               RightArm=(-105, 0, 5), RightForeArm=(-5, 0, 0),
               LeftArm=(30, 0, -25), LeftForeArm=(-20, 0, 0),
               LeftLeg=(10, 0, -26), RightLeg=(-25, 0, 30),
               LeftLowerLeg=(20, 0, 0), RightLowerLeg=(25, 0, 0))
    pose = blend(hover_pose(0), cocked, cock)
    if t > 1.0:
        pose = blend(cocked, thrown, fling)
    if t > 1.15:
        pose = merge(thrown, P(UpBody=(-6 * follow, 0, 0), RightArm=(15 * follow, 0, 0)))
    return merge(pose, hold('R', gx=lerp(112, -20, cock) if t < 1.0 else lerp(-20, 60, fling), gz=lerp(-28, 0, cock), slide=lerp(20, 4, cock)))


def hand_target(pose, hand, forward=5.0):
    """手的位置（模型坐标）往前一点：凋零头飘到这里"""
    bones, world = bone_world(GEO, {k: v for k, v in pose.items() if not k.startswith('@')})
    H = world('RightHand' if hand == 'R' else 'LeftHand')
    g = H @ np.append(mirror(hand_grip(hand)), 1)
    return np.array([-g[0], g[1], g[2] - forward])


RAISE_ARMS = P(AllBody=(0, 0, 0), UpBody=(-6, 0, 0), Head=(-4, 0, 0),
               RightArm=(-92, 0, 28), RightForeArm=(-12, 0, 0),
               LeftArm=(-92, 0, -28), LeftForeArm=(-12, 0, 0),
               LeftLeg=(-4, 0, -30), RightLeg=(-4, 0, 30),
               LeftLowerLeg=(18, 0, 0), RightLowerLeg=(18, 0, 0),
               LeftFoot=(28, 0, 0), RightFoot=(28, 0, 0))


def head_offsets(arms):
    out = {}
    for wrap, hand in (('WitherHeadR', 'R'), ('WitherHeadL', 'L')):
        target = hand_target(arms, hand, 6.0)
        center = np.array(BONES[wrap]['pivot'], float)
        d = target - center
        out[wrap] = (float(d[0]), float(d[1]) + 1.0, float(d[2]))
    return out


HEADS_AT_HANDS = head_offsets(RAISE_ARMS)
RAISE_LEN = 1.0


def throw_end_pose():
    return throw_core(THROW_LEN)


def skull_raise(t):
    u = ease_in_out(seg(t, 0.0, 0.8))
    h = ease_in_out(seg(t, 0.1, 1.0))
    pose = blend(throw_end_pose(), RAISE_ARMS, u)
    pose = merge(pose, P(WitherHeadR=dict(pos=lerp((0, 0, 0), HEADS_AT_HANDS['WitherHeadR'], h)),
                         WitherHeadL=dict(pos=lerp((0, 0, 0), HEADS_AT_HANDS['WitherHeadL'], h))))
    return merge(pose, hold('R', gx=60, slide=4))


FIRE_LEN = 2.0
FIRE_SHOTS = [(i / 3.0, 'R' if i % 2 == 0 else 'L') for i in range(6)]


def skull_fire(t):
    pose = merge(RAISE_ARMS, P(WitherHeadR=dict(pos=HEADS_AT_HANDS['WitherHeadR']),
                               WitherHeadL=dict(pos=HEADS_AT_HANDS['WitherHeadL'])))
    for t0, side in FIRE_SHOTS:
        for tt in (t - t0, t - t0 + FIRE_LEN):
            if 0 <= tt < 0.3:
                k = math.exp(-tt * 14) * (1 - math.exp(-tt * 60))
                head = 'WitherHeadR' if side == 'R' else 'WitherHeadL'
                arm = 'RightArm' if side == 'R' else 'LeftArm'
                add(pose, head, rot=(-18 * k, 0, 0), pos=(0, 1.0 * k, 3.5 * k))
                add(pose, arm, rot=(10 * k, 0, 0))
                add(pose, 'UpBody', rot=(-2 * k, 0, 0))
    return merge(pose, hold('R', gx=60, slide=4))


SKULL_END_LEN = 3.4
SKULL_CATCH = 0.4


def skull_end_core(t):
    reach = smooth(seg(t, 0.0, SKULL_CATCH)) - smooth(seg(t, SKULL_CATCH, 1.2))
    back = ease_in_out(seg(t, 0.0, 1.0))
    pose = merge(RAISE_ARMS, P(RightArm=(20 * reach, 0, 15 * reach)))
    pose = merge(pose, P(WitherHeadR=dict(pos=lerp(HEADS_AT_HANDS['WitherHeadR'], (0, 0, 0), back)),
                         WitherHeadL=dict(pos=lerp(HEADS_AT_HANDS['WitherHeadL'], (0, 0, 0), back))))
    return merge(pose, hold('R', gx=60, slide=4))


def skull_end(t):
    return back_to_hover(skull_end_core, t, 0.5, 2.4)


# ============================================================ 招式 3：吸附
# absorb_start 1.5 s 举镰蓄力、另一只手凝聚黑球；absorb_hold 2 s 循环；
# absorb_grab 0.5 s 抓住；absorb_slash 4.0 s（0.25 s 斩下：ABSORB_SLASH_HIT = 5 tick）；absorb_end 3.2 s 落空收招

CHARGE = P(AllBody=(-2, 0, 0), UpBody=(-8, -10, 0), Head=(4, 8, 0),
           RightArm=(-172, 0, 18), RightForeArm=(-10, 0, 0),
           LeftArm=(-78, 0, -8), LeftForeArm=(-38, 0, 0),
           LeftLeg=(-4, 0, -30), RightLeg=(-4, 0, 30),
           LeftLowerLeg=(18, 0, 0), RightLowerLeg=(18, 0, 0),
           LeftFoot=(28, 0, 0), RightFoot=(28, 0, 0))
CHARGE_GRIP = dict(gx=-10, gz=0, slide=6)
ABSORB_START_LEN = 1.5


def absorb_start(t):
    u = ease_in_out(seg(t, 0.0, 1.2))
    g = {k: lerp(v0, v1, u) for (k, v0), v1 in zip({'gx': 112, 'gz': -28, 'slide': 20}.items(), CHARGE_GRIP.values())}
    return merge(blend(hover_pose(0), CHARGE, u), hold('R', **g))


def absorb_hold(t):
    s = wave(t, 0.25) * 0.6
    return merge(CHARGE, P(UpBody=(s, 0, 0), RightArm=(s, 0, 0), LeftForeArm=(2 * wave(t, 1.0), 0, 0),
                           LeftHand=(0, 10 * wave(t, 2.0), 0)), hold('R', **CHARGE_GRIP))


GRAB = merge(CHARGE, P(AllBody=(8, 0, 0), LeftArm=(-12, 0, 0), LeftForeArm=(30, 0, 0)))


def absorb_grab(t):
    return merge(blend(CHARGE, GRAB, back_out(seg(t, 0, 0.3))), hold('R', **CHARGE_GRIP))


SLASHED = P(AllBody=(14, 0, 0), UpBody=(28, 35, 0), Head=(10, -20, 0),
            RightArm=(-35, 0, -35), RightForeArm=(-10, 0, 0),
            LeftArm=(20, 0, -40), LeftForeArm=(-20, 0, 0),
            LeftLeg=(20, 0, -26), RightLeg=(-25, 0, 30),
            LeftLowerLeg=(30, 0, 0), RightLowerLeg=(20, 0, 0))


def absorb_slash_core(t):
    u = ease_in(seg(t, 0.0, 0.28), 2.5)
    pose = blend(GRAB, SLASHED, u)
    return merge(pose, hold('R', gx=lerp(-10, 75, u), gz=lerp(0, -10, u), slide=6))


def absorb_slash(t):
    return back_to_hover(absorb_slash_core, t, 1.0, 3.2)


def absorb_end(t):
    return back_to_hover(lambda x: merge(CHARGE, hold('R', **CHARGE_GRIP)), t, 0.0, 1.6)


# ============================================================ 招式 4：快速攻击
# rapid_start 0.5 s；rapid_loop 1 s 循环三刀（命中 0.2 / 0.533 / 0.867 s = 4 / 11 / 17 tick）；rapid_end 3.2 s 后摇

def rapid_key(name):
    keys_ = {
        # 右侧蓄势
        'R': (P(UpBody=(0, -40, 0), Head=(0, 30, 0), RightArm=(-80, 0, 70), RightForeArm=(-30, 0, 0),
                LeftArm=(-20, 0, -30), AllBody=(4, 0, 0)), dict(gx=10, gz=0, slide=8)),
        # 横扫到左侧
        'L': (P(UpBody=(10, 45, 0), Head=(0, -30, 0), RightArm=(-85, 0, -45), RightForeArm=(-10, 0, 0),
                LeftArm=(10, 0, -45), AllBody=(8, 0, 0)), dict(gx=10, gz=0, slide=8)),
        # 上撩到头顶
        'U': (P(UpBody=(-15, -10, 0), Head=(-10, 5, 0), RightArm=(-170, 0, 15), RightForeArm=(-10, 0, 0),
                LeftArm=(-30, 0, -40), AllBody=(-4, 0, 0)), dict(gx=-20, gz=0, slide=8)),
        # 下劈
        'D': (P(UpBody=(30, 5, 0), Head=(10, 0, 0), RightArm=(-40, 0, 0), RightForeArm=(-5, 0, 0),
                LeftArm=(20, 0, -35), AllBody=(14, 0, 0)), dict(gx=70, gz=0, slide=8)),
    }
    pose, grip = keys_[name]
    legs = P(LeftLeg=(-8, 0, -26), RightLeg=(8, 0, 26), LeftLowerLeg=(25, 0, 0), RightLowerLeg=(25, 0, 0),
             LeftFoot=(28, 0, 0), RightFoot=(28, 0, 0))
    return merge(pose, legs), grip


def rapid_between(a, b, u):
    pa, ga = rapid_key(a)
    pb, gb = rapid_key(b)
    return merge(blend(pa, pb, u), hold('R', **{k: lerp(ga[k], gb[k], u) for k in ga}))


def rapid_loop(t):
    third = 1 / 3
    seq = [('R', 'L'), ('L', 'U'), ('U', 'R')]  # 横扫 -> 上撩 -> 下劈回到右侧
    i = min(int(t / third), 2)
    u = (t - i * third) / third
    # 每刀前 0.1 s 蓄势，0.1~0.2 s 挥出（命中在 0.2 s 附近），之后收势
    swing = ease_in(seg(u, 0.25, 0.65), 2)
    a, b = seq[i]
    if i == 2:
        # 下劈：先到 D 再回 R
        if u < 0.65:
            return rapid_between('U', 'D', swing)
        return rapid_between('D', 'R', ease_in_out(seg(u, 0.65, 1.0)))
    return rapid_between(a, b, swing)


RAPID_START_LEN = 0.5


def rapid_start(t):
    u = ease_out(seg(t, 0, 0.45))
    p, g = rapid_key('R')
    return merge(blend(hover_pose(0), p, u), hold('R', **{k: lerp(v0, g[k], u) for k, v0 in {'gx': 112, 'gz': -28, 'slide': 20}.items()}))


def rapid_end(t):
    def panting(x):
        p = rapid_loop(0)
        return merge(p, P(UpBody=(6 + 3 * wave(x, 0.8), 0, 0)))
    return back_to_hover(panting, t, 0.3, 2.6)


ANIMS = [
    Anim('wl_hover', 4.0, hover_pose, loop=True, fps=10),
    Anim('wl_drift', 2.4, drift_pose, loop=True, fps=20),
    Anim('wl_phys_calm', 4.0, lambda t: phys_pose(t, 4.0, 1.0), loop=True, fps=10),
    Anim('wl_phys_combat', 2.0, lambda t: phys_pose(t, 2.0, 2.0), loop=True, fps=10),
    Anim('wl_blink', 4.0, blink_pose, loop=True, fps=20),
    Anim('wl_toss', TOSS_LEN, toss_pose, fps=20, always=CHAIN),
    Anim('wl_intro_curl', 3.0, intro_curl, loop=True, fps=10, always=CHAIN),
    Anim('wl_intro_wake', WAKE_LEN, intro_wake, hold=True, fps=10, always=CHAIN),
    Anim('wl_intro_roar', ROAR_LEN, intro_roar, hold=True, fps=20, always=CHAIN),
    Anim('wl_intro_shoulder', SHOULDER_LEN, intro_shoulder, hold=True, fps=20, always=CHAIN),
    Anim('wl_cleave', CLEAVE_LEN, cleave, fps=20, always=CHAIN),
    Anim('wl_skull_throw', THROW_LEN, throw_core, hold=True, fps=20, always=CHAIN),
    Anim('wl_skull_raise', RAISE_LEN, skull_raise, hold=True, fps=20, always=CHAIN),
    Anim('wl_skull_fire', FIRE_LEN, skull_fire, loop=True, fps=20, always=CHAIN),
    Anim('wl_skull_end', SKULL_END_LEN, skull_end, fps=20, always=CHAIN),
    Anim('wl_absorb_start', ABSORB_START_LEN, absorb_start, hold=True, fps=20, always=CHAIN),
    Anim('wl_absorb_hold', 2.0, absorb_hold, loop=True, fps=20, always=CHAIN),
    Anim('wl_absorb_grab', 0.5, absorb_grab, hold=True, fps=20, always=CHAIN),
    Anim('wl_absorb_slash', 4.0, absorb_slash, fps=20, always=CHAIN),
    Anim('wl_absorb_end', 3.2, absorb_end, fps=20, always=CHAIN),
    Anim('wl_rapid_start', RAPID_START_LEN, rapid_start, hold=True, fps=20, always=CHAIN),
    Anim('wl_rapid_loop', 1.0, rapid_loop, loop=True, fps=30, always=CHAIN),
    Anim('wl_rapid_end', 3.2, rapid_end, fps=20, always=CHAIN),
]


if __name__ == '__main__':
    if len(sys.argv) > 1 and sys.argv[1] == 'preview':
        name = sys.argv[2]
        a = [x for x in ANIMS if x.name == name][0]
        times = [float(x) for x in sys.argv[3].split(',')]
        yaws = tuple(float(y) for y in sys.argv[5].split(',')) if len(sys.argv) > 5 else (0, -50)
        preview(a, times, sys.argv[4], yaws=yaws)
    else:
        write(ANIMS, OUT)
        print('wrote', OUT, os.path.getsize(OUT))
