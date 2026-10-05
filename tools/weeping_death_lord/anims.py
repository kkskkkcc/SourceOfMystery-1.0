"""
泣死之主（weeping_death_lord）的全部动画。运行后写出
src/main/resources/assets/sourceofmystery/animations/entity/weeping_death_lord.animation.json

控制器（见 WeepingDeathLord.registerControllers，按注册顺序，后面的覆盖前面的同名骨骼）：
  blink    wl_blink      先左眼、0.5 秒后右眼（出场动画自己控制眼皮，会盖过它）
  main     其余全部动画  待机、出场、出招、后摇。每个动画都写满 MAIN_BONES 里的骨骼，
                         头发 / 胸部 / 裙甲 / 尾巴 / 凋零头的物理摆动直接烘焙进每个动画（待机是常态幅度，
                         出招是战斗幅度），这样控制器换动画时 GeckoLib 的过渡（BLEND 秒）能把所有骨骼都平滑地接过去，
                         不会有骨骼突然跳到另一个姿势。

所有动画都经过 anim_lib 的两道后处理：
  - 'spin': 'auto' 的镰刀：刀刃（刀尖）始终朝着刀头挥动的方向，像用刀刃劈砍而不是拿棍子抡
  - 裙甲防穿模：逐帧检查前挡和左右两片裙甲是否穿过大腿 / 小腿，穿了就往外掀开到不穿为止

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


# 物理摆动的骨骼
PHYS_BONES = ('Breast', 'Necklace', 'LongHair', 'hl_side', 'hl_m', 'Ribbon', 'front_h1', 'SkirtFront', 'SkirtRight',
              'SkirtLeft', 'Tail1', 'Tail2', 'Tail3', 'Tail4', 'WitherHeadR', 'WitherHeadL')
MAIN_BONES = CHAIN + PHYS_BONES + ('MagicCircle',)
EYE_BONES = ('LeftEyelid', 'RightEyelid', 'LeftEyebrow', 'RightEyebrow')

BLEND = 0.3  # 秒：main 控制器切换动画时的过渡时长（Java 里是 6 tick）


def hold(hand='R', gx=112, gy=0, gz=0, spin=-90, slide=20, scale=1.0, auto=False):
    """手握镰刀。auto=True：刀刃朝向按挥动方向自动算（spin 作为慢速时的默认朝向）"""
    s = {'hand': hand, 'grip': (gx, gy, gz), 'spin': spin, 'slide': slide, 'scale': scale}
    if auto:
        s['spin'] = 'auto'
        s['spin0'] = spin
    return {'@scythe': s}


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
                out[bone] = _blend_spec(sa, sb, u)
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


def _blend_spec(sa, sb, u):
    out = {'hand': sa['hand']}
    for k in ('grip', 'slide', 'scale'):
        out[k] = lerp(sa.get(k, (0, 0, 0) if k == 'grip' else (0 if k == 'slide' else 1.0)),
                      sb.get(k, (0, 0, 0) if k == 'grip' else (0 if k == 'slide' else 1.0)), u)
    a_auto, b_auto = sa.get('spin') == 'auto', sb.get('spin') == 'auto'
    s0a = sa.get('spin0', -90) if a_auto else sa.get('spin', -90)
    s0b = sb.get('spin0', -90) if b_auto else sb.get('spin', -90)
    if a_auto or b_auto:
        out['spin'] = 'auto'
        out['spin0'] = lerp(s0a, s0b, u)
    else:
        out['spin'] = lerp(s0a, s0b, u)
    return out


def scythe_free(anim_fn, t):
    """在时间 t 时（握在手里）镰刀骨骼的 rot / pos，用于从手里接到空中"""
    p = solve_scythe(anim_fn(t))
    return p['Scythe']




# ============================================================ 物理（呼吸 / 头发 / 尾巴 / 裙甲 / 凋零头）

def phys_pose(t, period, k):
    """k：幅度系数；战斗状态更快更大。烘焙进每个 main 动画（循环动画的长度必须是 period 的整数倍）"""
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
        SkirtFront=(-2 * k - 2 * k * w(0.2), 0, 1.5 * k * w(0.45)),
        SkirtRight=(-1 * k * w(0.3), 0, 2 * k + 2 * k * w(0.1)),
        SkirtLeft=(-1 * k * w(0.35), 0, -2 * k - 2 * k * w(0.15)),
        Tail1=(3 * k * w(0.0), 9 * k * w(0.0), 0),
        Tail2=(3 * k * w(-0.1), 11 * k * w(-0.1), 0),
        Tail3=(4 * k * w(-0.2), 13 * k * w(-0.2), 0),
        Tail4=(4 * k * w(-0.3), 16 * k * w(-0.3), 0),
        WitherHeadR=dict(pos=(0.8 * k * w(0.5), 1.4 * k * w(0.0), 0.8 * k * w(0.25)), rot=(4 * k * w(0.1), 8 * k * w(0.6), 0)),
        WitherHeadL=dict(pos=(0.8 * k * w(0.0), 1.4 * k * w(0.5), 0.8 * k * w(0.75)), rot=(4 * k * w(0.6), 8 * k * w(0.1), 0)),
    )


def calm(fn, period=4.0):
    return lambda t: merge(fn(t), phys_pose(t, period, 1.0))


def combat(fn, period=2.0):
    return lambda t: merge(fn(t), phys_pose(t, period, 2.0))


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


# ============================================================ 待机

HOVER_GRIP = dict(gx=112, gz=-28, spin=-90, slide=20)


def hover_pose(t, tense=0.0):
    """待机 1：双脚张开 30~40 度漂浮（随呼吸开合），右手单手持镰，刀头朝下、刀刃向内"""
    b = wave(t, 4)
    b2 = wave(t, 4, 0.25)
    spread = 17.5 + 2.5 * wave(t, 4, 0.1)  # 单腿 15~20 度，两腿夹角 30~40 度
    return merge(
        P(Root=dict(pos=(0, 1.2 * b, 0)),
          AllBody=(2 + 1.0 * b2 + 4 * tense, 0, 0),
          UpBody=(-1 + 1.0 * wave(t, 4, 0.1), 0, 0),
          Head=(-3 - 1.5 * wave(t, 4, 0.15) + 3 * tense, 0, 2.5 * wave(t, 4, 0.3) * (1 - tense)),
          LeftLeg=(-4, 0, -spread),
          RightLeg=(-4, 0, spread),
          LeftLowerLeg=(18 + 4 * wave(t, 4, 0.2), 0, 0),
          RightLowerLeg=(18 + 4 * wave(t, 4, 0.2), 0, 0),
          LeftFoot=(28, 0, 0), RightFoot=(28, 0, 0),
          LeftArm=(-4 + 3 * wave(t, 4, 0.3) - 6 * tense, 0, -8 - 3 * wave(t, 4, 0.3) - 4 * tense),
          LeftForeArm=(-18 - 3 * wave(t, 4, 0.35) - 10 * tense, 0, 0),
          RightArm=(-6 + 1.5 * b2 - 6 * tense, 0, 8 + 3 * tense),
          RightForeArm=(-22 - 6 * tense, 0, 0)),
        hold('R', gx=112 + 2 * b2, gz=-28, spin=-90, slide=20))


def drift_pose(t):
    """待机 2：拖着镰刀像水母一样一收一放地漂（双腿最多张开 40 度）"""
    p = (t % 2.4) / 2.4
    c = smooth(seg(p, 0.0, 0.3)) - smooth(seg(p, 0.3, 1.0))  # 0 -> 1 -> 0 收缩
    return merge(
        P(Root=dict(pos=(0, 2.0 * math.sin(2 * math.pi * (p - 0.2)), 0)),
          AllBody=(20 - 7 * c, 0, 0),
          UpBody=(-6 + 8 * c, 0, 0),
          Head=(-14 + 6 * c, 0, 0),
          LeftLeg=(6 - 10 * c, 0, -5 - 15 * (1 - c)),
          RightLeg=(6 - 10 * c, 0, 5 + 15 * (1 - c)),
          LeftLowerLeg=(14 + 45 * c, 0, 0), RightLowerLeg=(14 + 45 * c, 0, 0),
          LeftFoot=(35, 0, 0), RightFoot=(35, 0, 0),
          LeftArm=(18 + 25 * c, 0, -14 - 26 * (1 - c)),
          LeftForeArm=(-8 - 20 * c, 0, 0),
          RightArm=(28 + 12 * c, 0, 12 + 10 * (1 - c)),
          RightForeArm=(-6 - 8 * c, 0, 0),
          # 受力的偏移：头发、裙甲、尾巴、凋零头都被甩在后面（裙甲往后飘，防穿模处理会把碰到腿的部分掀开）
          LongHair=(22 + 8 * c, 0, 3 * wave(t, 2.4, 0.1)),
          hl_side=(16 + 6 * c, 0, 0),
          hl_m=(12 + 5 * c, 0, 0),
          Ribbon=(10 + 12 * c, 0, 0),
          SkirtFront=(-6 - 10 * c, 0, 0),
          SkirtRight=(6 + 6 * c, 0, 6 * (1 - c)),
          SkirtLeft=(6 + 6 * c, 0, -6 * (1 - c)),
          Tail1=(-18 - 6 * c, 8 * wave(t, 2.4, 0.0), 0),
          Tail2=(-10 - 4 * c, 10 * wave(t, 2.4, -0.12), 0),
          Tail3=(-8, 13 * wave(t, 2.4, -0.24), 0),
          Tail4=(-6, 16 * wave(t, 2.4, -0.36), 0),
          WitherHeadR=dict(pos=(0, 2 + 2 * c, 7 + 3 * c), rot=(-10, 0, 0)),
          WitherHeadL=dict(pos=(0, 2 + 2 * c, 7 + 3 * c), rot=(-10, 0, 0))),
        hold('R', gx=132 - 8 * c, gz=-8, spin=-90, slide=4))


def back_to_hover(pose_fn, t, t0, t1):
    """从 pose_fn(t) 平滑过渡回待机姿势"""
    u = ease_in_out(seg(t, t0, t1))
    if u <= 0:
        return pose_fn(t)
    return blend(pose_fn(t), hover_pose(t), u)


# ============================================================ 待机 3：抛起镰刀，另一只手接住舞动，再换回右手

TOSS_LEN = 4.6


def toss_body(t):
    h = hover_pose(t)
    up = smooth(seg(t, 0.15, 0.5)) - smooth(seg(t, 0.5, 1.1))
    crouch = smooth(seg(t, 0.0, 0.3)) - smooth(seg(t, 0.3, 0.55))
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


# ============================================================ 出场（一整段连续动画，时间从出场开始算，单位秒）
# 0 ~ 2      凋灵变白旋转（她还隐藏着）
# 2          凋灵爆炸，她出现：蜷缩抱膝漂浮 2 秒，镰刀竖着漂浮在身旁
# 4 ~ 9      缓缓睁眼（整整 5 秒才完全睁开）、慢慢抬头
# 9 ~ 12     猛地张开双臂咆哮 3 秒
# 12 ~ 14.5  镰刀飘到手上（13 秒接住），扛到肩上，看着玩家
# 14.5 ~ 21  扛着镰刀对峙（镜头运镜 + 定格 2 秒）
# 21 ~ 22    出场已结束（镜头还给玩家），她放下镰刀回到待机姿势
I_EXPLODE, I_WAKE, I_ROAR, I_SHOULDER, I_CATCH, I_SETTLE, I_LOWER, INTRO_LEN = 2.0, 4.0, 9.0, 12.0, 13.0, 14.5, 21.0, 22.0

FLOAT_SCYTHE_POS = (-16.1, -12.0, -14.0)  # 竖着漂浮在她右侧，刀头朝上


def floating_scythe(t):
    return {'Scythe': {'rot': (-90, 6 * wave(t, 3.0), 3 * wave(t, 3.0, 0.3)),
                       'pos': (FLOAT_SCYTHE_POS[0], FLOAT_SCYTHE_POS[1] + 1.5 * wave(t, 3.0, 0.1), FLOAT_SCYTHE_POS[2])}}


def eyes(open_amount):
    s = 0.04 + 0.96 * clamp01(open_amount)
    return P(LeftEyelid=dict(scale=(1, s, 1)), RightEyelid=dict(scale=(1, s, 1)),
             LeftEyebrow=dict(pos=(0, -0.5 * (1 - open_amount), 0)),
             RightEyebrow=dict(pos=(0, -0.5 * (1 - open_amount), 0)))


def eye_open(t):
    """5 秒内匀速地慢慢睁开（最后 0.4 秒收尾），中途有一次没睡醒似的微微合上"""
    u = seg(t, I_WAKE, I_ROAR)
    e = u ** 1.15
    e -= 0.12 * math.exp(-((u - 0.42) / 0.05) ** 2)
    return clamp01(e)


def curl_pose(t, lift=0.0):
    """蜷缩抱膝漂浮；lift 0~1：抬头、身体慢慢舒展一点"""
    b = wave(t, 3.0)
    return merge(P(
        Root=dict(pos=(0, 1.0 * b, 0)),
        AllBody=(8 - 4 * lift, 0, 0),
        UpBody=(16 - 10 * lift, 0, 0),
        Head=(40 - 46 * lift, 0, 4 * (1 - lift)),
        LeftLeg=(-72 + 8 * lift, 0, 10), RightLeg=(-72 + 8 * lift, 0, -10),
        LeftLowerLeg=(120 - 10 * lift, 0, 0), RightLowerLeg=(120 - 10 * lift, 0, 0),
        LeftFoot=(30, 0, 0), RightFoot=(30, 0, 0),
        RightArm=(-48 + 6 * lift, 0, -32 + 6 * lift), RightForeArm=(-62 + 8 * lift, 0, 0),
        LeftArm=(-48 + 6 * lift, 0, 32 - 6 * lift), LeftForeArm=(-62 + 8 * lift, 0, 0),
        Tail1=(-10, 45, 0), Tail2=(0, 40, 0), Tail3=(0, 40, 0), Tail4=(0, 35, 0),
        LongHair=(-10 + 3 * b, 0, 0),
        WitherHeadR=dict(pos=(3, -2 + 1.5 * wave(t, 3.0, 0.4), 2)),
        WitherHeadL=dict(pos=(-3, -2 + 1.5 * wave(t, 3.0, 0.9), 2)),
    ), floating_scythe(t))


def spread_pose(t, tremble=1.0):
    s = (0.6 * wave(t, 0.13) + 0.4 * wave(t, 0.21)) * tremble  # 吼叫 / 发射激光时全身发颤
    return P(
        Root=dict(pos=(0, 2, 0)),
        AllBody=(-6, 0, 0),
        UpBody=(-20 + 1.5 * s, 0, 0),
        Head=(-24 + 2 * s, 0, 0),
        LeftLeg=(4, 0, -20), RightLeg=(4, 0, 20),
        LeftLowerLeg=(6, 0, 0), RightLowerLeg=(6, 0, 0),
        LeftFoot=(30, 0, 0), RightFoot=(30, 0, 0),
        RightArm=(-18 + 2 * s, 0, 105), RightForeArm=(-8, 0, 0),
        LeftArm=(-18 - 2 * s, 0, -105), LeftForeArm=(-8, 0, 0),
        Tail1=(-30, 0, 0), Tail2=(-15, 6 * s, 0), Tail3=(-10, 8 * s, 0), Tail4=(-8, 10 * s, 0),
        LongHair=(-25, 0, 0), hl_side=(-18, 0, 0), hl_m=(-15, 0, 0), Ribbon=(-30, 0, 0),
        SkirtFront=(-15, 0, 0), SkirtRight=(-6, 0, 12), SkirtLeft=(-6, 0, -12),
        WitherHeadR=dict(pos=(-6, 3, 0), rot=(-15, 0, 0)),
        WitherHeadL=dict(pos=(6, 3, 0), rot=(-15, 0, 0)),
    )


def wake_pose(t):
    lift = ease_in_out(seg(t, I_WAKE + 0.3, I_ROAR - 0.1))
    return merge(curl_pose(t, lift), eyes(eye_open(t)))


def roar_pose(t):
    start = wake_pose(I_ROAR)
    u = back_out(seg(t, I_ROAR, I_ROAR + 0.35), 1.2)
    return merge(blend(start, spread_pose(t), u), eyes(1), floating_scythe(t))


SHOULDER_STANCE = P(
    Root=dict(pos=(0, 1, 0)), AllBody=(0, 0, 0), UpBody=(-4, 0, 0),
    Head=(6, -8, 7),
    LeftLeg=(-6, 0, -18), RightLeg=(2, 0, 18),
    LeftLowerLeg=(22, 0, 0), RightLowerLeg=(14, 0, 0),
    LeftFoot=(28, 0, 0), RightFoot=(28, 0, 0),
    LeftArm=(8, 0, -24), LeftForeArm=(-45, 0, 0),
    RightArm=(-150, 0, 22), RightForeArm=(-85, 0, 0),
)
REACH = P(RightArm=(-70, 0, 45), RightForeArm=(-15, 0, 0))
SHOULDER_GRIP = dict(gx=175, gz=-20, spin=-90, slide=26)


def shoulder_body(t):
    """12 秒起：手臂从张开收回，右手伸出去接镰刀，再扛到肩上"""
    s = t - I_SHOULDER
    catch = I_CATCH - I_SHOULDER
    settle = ease_in_out(seg(s, 0.0, 1.6))
    reach = smooth(seg(s, 0.0, catch))
    lift = ease_in_out(seg(s, catch + 0.1, catch + 1.1))
    base = blend(spread_pose(t, 1 - settle), SHOULDER_STANCE, settle)
    reach_pose = merge(base, REACH)
    if s <= catch:
        pose = blend(base, reach_pose, reach)
    else:
        pose = blend(reach_pose, base, lift)
    # 对峙时的呼吸
    br = wave(t, 3.0)
    return merge(pose, P(Root=dict(pos=(0, 0.8 * br * settle, 0)), UpBody=(0.8 * br * settle, 0, 0)), eyes(1))


def shoulder_grip(t):
    s = t - I_SHOULDER
    catch = I_CATCH - I_SHOULDER
    lift = ease_in_out(seg(s, catch + 0.1, catch + 1.1))
    return hold('R', gx=lerp(40, SHOULDER_GRIP['gx'], lift), gz=lerp(0, SHOULDER_GRIP['gz'], lift), spin=-90,
                slide=lerp(20, SHOULDER_GRIP['slide'], lift))


def shoulder_pose(t):
    body = shoulder_body(t)
    if t >= I_CATCH:
        return merge(body, shoulder_grip(t))
    target = scythe_free(lambda x: merge(shoulder_body(x), shoulder_grip(x)), I_CATCH)
    start = floating_scythe(t)['Scythe']
    u = ease_in(seg(t, I_SHOULDER + 0.15, I_CATCH), 2)
    return merge(body, {'Scythe': {'rot': lerp(start['rot'], target['rot'], u), 'pos': lerp(start['pos'], target['pos'], u)}})


def intro(t):
    if t < I_ROAR:
        pose = wake_pose(t)
    elif t < I_SHOULDER:
        pose = roar_pose(t)
    elif t < I_LOWER:
        pose = shoulder_pose(t)
    else:
        # 放下镰刀，回到待机姿势（握法从扛肩平滑过渡到下垂）
        u = ease_in_out(seg(t, I_LOWER, INTRO_LEN))
        pose = merge(blend(shoulder_pose(t), hover_pose(t), u), eyes(1))
    return merge(pose, phys_pose(t, 4.0, 1.0))


# ============================================================ 招式 1：劈斩
# 前摇 1.45 s：甩到身后、镰刀变大；1.65 s 砸地（CLEAVE_HIT）；后摇 3.35 s

CLEAVE_LEN = 5.0
CLEAVE_HIT = 1.65


def cleave_core(t):
    up = ease_in_out(seg(t, 0.0, 0.4))
    tense = ease_in_out(seg(t, 0.4, 1.45))
    smash = ease_in(seg(t, 1.45, CLEAVE_HIT), 2)
    pull = ease_in_out(seg(t, 2.5, 3.5))
    grow = ease_in_out(seg(t, 0.45, 1.35)) * (1 - pull)
    raised = P(AllBody=(-4, 0, 0), UpBody=(-22 - 8 * tense, 0, 0), Head=(-12, 0, 0),
               RightArm=(-168, 0, -12), RightForeArm=(-15, 0, 0),
               LeftArm=(-168, 0, 12), LeftForeArm=(-15, 0, 0),
               LeftLeg=(10, 0, -20), RightLeg=(-20, 0, 20),
               LeftLowerLeg=(30, 0, 0), RightLowerLeg=(20, 0, 0))
    hit = P(Root=dict(pos=(0, -3, 0)), AllBody=(18, 0, 0), UpBody=(24, 0, 0), Head=(16, 0, 0),
            RightArm=(-50, 0, -14), RightForeArm=(-10, 0, 0),
            LeftArm=(-50, 0, 14), LeftForeArm=(-10, 0, 0),
            LeftLeg=(30, 0, -20), RightLeg=(-20, 0, 20),
            LeftLowerLeg=(40, 0, 0), RightLowerLeg=(15, 0, 0))
    pulled = P(AllBody=(4, 0, 0), UpBody=(8, 0, 0), Head=(0, 0, 0),
               RightArm=(-95, 0, 10), RightForeArm=(-20, 0, 0),
               LeftArm=(-60, 0, -10), LeftForeArm=(-20, 0, 0),
               LeftLeg=(0, 0, -20), RightLeg=(0, 0, 20),
               LeftLowerLeg=(18, 0, 0), RightLowerLeg=(18, 0, 0))
    start = hover_pose(0)
    if t < 0.4:
        pose = blend(start, raised, up)
    elif t < 1.45:
        pose = raised
    elif t < 2.5:
        pose = blend(raised, hit, smash)
        if t > CLEAVE_HIT:
            pose = merge(pose, P(Root=dict(pos=(0, 0.4 * wave(t, 0.1), 0))))
    else:
        pose = blend(hit, pulled, pull)
    gx = lerp(lerp(lerp(112, 15, up), 5, tense), 35, smash) if t < 2.5 else lerp(35, 100, pull)
    return merge(pose, hold('R', gx=gx, gz=lerp(-28, 0, up), spin=-90, slide=lerp(20, 2, up), scale=1 + 1.4 * grow, auto=True))


def cleave(t):
    return back_to_hover(cleave_core, t, 3.5, CLEAVE_LEN)


# ============================================================ 招式 2：凋零头
# skull_throw 1.6 s：前摇 1.0 s，1.15 s 掷出镰刀（THROW_RELEASE）
# skull_raise 1.0 s：双手抬起，两个凋零头飘到手上
# skull_fire 2 s 循环：右、左交替，每秒 3 发
# skull_end 3.4 s：0.4 s 接住飞回的镰刀，后摇

THROW_LEN = 1.6
THROW_RELEASE = 1.15


def throw_core(t):
    cock = ease_in_out(seg(t, 0.0, 1.0))
    fling = ease_in(seg(t, 1.0, THROW_RELEASE), 2)
    follow = ease_out(seg(t, THROW_RELEASE, THROW_LEN))
    cocked = P(AllBody=(-2, 0, 0), UpBody=(-6, -35, 0), Head=(0, 25, 0),
               RightArm=(45, 0, 30), RightForeArm=(-50, 0, 0),
               LeftArm=(-75, 0, -15), LeftForeArm=(-5, 0, 0),
               LeftLeg=(-25, 0, -20), RightLeg=(20, 0, 22),
               LeftLowerLeg=(20, 0, 0), RightLowerLeg=(30, 0, 0))
    thrown = P(AllBody=(10, 0, 0), UpBody=(20, 30, 0), Head=(-5, -20, 0),
               RightArm=(-105, 0, 5), RightForeArm=(-5, 0, 0),
               LeftArm=(30, 0, -25), LeftForeArm=(-20, 0, 0),
               LeftLeg=(10, 0, -20), RightLeg=(-25, 0, 22),
               LeftLowerLeg=(20, 0, 0), RightLowerLeg=(25, 0, 0))
    pose = blend(hover_pose(0), cocked, cock)
    if t > 1.0:
        pose = blend(cocked, thrown, fling)
    if t > THROW_RELEASE:
        pose = merge(thrown, P(UpBody=(-6 * follow, 0, 0), RightArm=(15 * follow, 0, 0)))
    return merge(pose, hold('R', gx=lerp(112, -20, cock) if t < 1.0 else lerp(-20, 60, fling), gz=lerp(-28, 0, cock),
                            slide=lerp(20, 4, cock), auto=True))


def hand_target(pose, hand, forward=5.0):
    """手的位置（模型坐标，未镜像）往前一点"""
    bones, world = bone_world(GEO, {k: v for k, v in pose.items() if not k.startswith('@')})
    H = world('RightHand' if hand == 'R' else 'LeftHand')
    g = H @ np.append(mirror(hand_grip(hand)), 1)
    return np.array([-g[0], g[1], g[2] - forward])


RAISE_ARMS = P(AllBody=(0, 0, 0), UpBody=(-6, 0, 0), Head=(-4, 0, 0),
               RightArm=(-92, 0, 28), RightForeArm=(-12, 0, 0),
               LeftArm=(-92, 0, -28), LeftForeArm=(-12, 0, 0),
               LeftLeg=(-4, 0, -18), RightLeg=(-4, 0, 18),
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


def skull_raise(t):
    u = ease_in_out(seg(t, 0.0, 0.8))
    h = ease_in_out(seg(t, 0.1, 1.0))
    pose = blend({k: v for k, v in throw_core(THROW_LEN).items() if k != '@scythe'}, RAISE_ARMS, u)
    return merge(pose, P(WitherHeadR=dict(pos=lerp((0, 0, 0), HEADS_AT_HANDS['WitherHeadR'], h)),
                         WitherHeadL=dict(pos=lerp((0, 0, 0), HEADS_AT_HANDS['WitherHeadL'], h))),
                 hold('R', gx=60, slide=4))


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
# absorb_grab 0.5 s 抓住；absorb_slash 4.0 s（0.25 s 斩下：SLASH_HIT）；absorb_end 3.2 s 落空收招

CHARGE = P(AllBody=(-2, 0, 0), UpBody=(-8, -10, 0), Head=(4, 8, 0),
           RightArm=(-172, 0, 18), RightForeArm=(-10, 0, 0),
           LeftArm=(-78, 0, -8), LeftForeArm=(-38, 0, 0),
           LeftLeg=(-4, 0, -18), RightLeg=(-4, 0, 18),
           LeftLowerLeg=(18, 0, 0), RightLowerLeg=(18, 0, 0),
           LeftFoot=(28, 0, 0), RightFoot=(28, 0, 0))
CHARGE_GRIP = dict(gx=-10, gz=0, slide=6)
ABSORB_START_LEN = 1.5


def absorb_start(t):
    u = ease_in_out(seg(t, 0.0, 1.2))
    g = {k: lerp(HOVER_GRIP[k], CHARGE_GRIP[k], u) for k in CHARGE_GRIP}
    return merge(blend(hover_pose(0), CHARGE, u), hold('R', **g))


def absorb_hold(t):
    s = wave(t, 0.25) * 0.6
    return merge(CHARGE, P(UpBody=(s, 0, 0), RightArm=(s, 0, 0), LeftForeArm=(2 * wave(t, 1.0), 0, 0),
                           LeftHand=(0, 10 * wave(t, 2.0), 0)), hold('R', **CHARGE_GRIP))


GRAB = merge(CHARGE, P(AllBody=(8, 0, 0), LeftArm=(-12, 0, 0), LeftForeArm=(30, 0, 0)))


def absorb_grab(t):
    return merge(blend(CHARGE, GRAB, back_out(seg(t, 0, 0.3))), hold('R', **CHARGE_GRIP))


SLASH_HIT = 0.25
SLASHED = P(AllBody=(14, 0, 0), UpBody=(28, 35, 0), Head=(10, -20, 0),
            RightArm=(-35, 0, -35), RightForeArm=(-10, 0, 0),
            LeftArm=(20, 0, -40), LeftForeArm=(-20, 0, 0),
            LeftLeg=(20, 0, -20), RightLeg=(-25, 0, 22),
            LeftLowerLeg=(30, 0, 0), RightLowerLeg=(20, 0, 0))


def absorb_slash_core(t):
    u = ease_in(seg(t, 0.0, 0.28), 2.5)
    pose = blend(GRAB, SLASHED, u)
    return merge(pose, hold('R', gx=lerp(-10, 75, u), gz=lerp(0, -10, u), slide=6, auto=True))


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
    legs = P(LeftLeg=(-8, 0, -18), RightLeg=(8, 0, 18), LeftLowerLeg=(25, 0, 0), RightLowerLeg=(25, 0, 0),
             LeftFoot=(28, 0, 0), RightFoot=(28, 0, 0))
    return merge(pose, legs), grip


def rapid_between(a, b, u):
    pa, ga = rapid_key(a)
    pb, gb = rapid_key(b)
    return merge(blend(pa, pb, u), hold('R', auto=True, **{k: lerp(ga[k], gb[k], u) for k in ga}))


def rapid_loop(t):
    third = 1 / 3
    seq = [('R', 'L'), ('L', 'U'), ('U', 'R')]  # 横扫 -> 上撩 -> 下劈回到右侧
    i = min(int(t / third), 2)
    u = (t - i * third) / third
    swing = ease_in(seg(u, 0.25, 0.65), 2)
    a, b = seq[i]
    if i == 2:
        if u < 0.65:
            return rapid_between('U', 'D', swing)
        return rapid_between('D', 'R', ease_in_out(seg(u, 0.65, 1.0)))
    return rapid_between(a, b, swing)


RAPID_START_LEN = 0.5


def rapid_start(t):
    u = ease_out(seg(t, 0, 0.45))
    p, g = rapid_key('R')
    return merge(blend(hover_pose(0), p, u), hold('R', auto=True, **{k: lerp(HOVER_GRIP[k], g[k], u) for k in g}))


def rapid_end(t):
    def panting(x):
        p = rapid_loop(0)
        return merge(p, P(UpBody=(6 + 3 * wave(x, 0.8), 0, 0)))
    return back_to_hover(panting, t, 0.3, 2.6)


# ============================================================ 大招：死亡激光
# laser_start 1.6 s：双手高举凝聚黑色魔法阵（0~0.7）→ 放到胸前（0.7~1.25）→ 猛地张开双臂（1.25~1.6），
#                    1.6 s 胸口发射白色激光（LASER_FIRE）；镰刀离手竖着漂浮在身旁
# laser_fire 1 s 循环：张开双臂挺胸发射，全身发颤（持续 5 秒）
# laser_end 3.2 s：放下双臂，镰刀飘回手中（1.0 s 接住），回到待机

LASER_START_LEN = 1.6
LASER_FIRE = 1.6
LASER_END_LEN = 3.2
LASER_CATCH = 1.0

CIRCLE_PIVOT = np.array(BONES['MagicCircle']['pivot'], float)

LASER_RAISED = P(AllBody=(-4, 0, 0), UpBody=(-12, 0, 0), Head=(-26, 0, 0),
                 RightArm=(-168, 0, -10), RightForeArm=(-14, 0, 0),
                 LeftArm=(-168, 0, 10), LeftForeArm=(-14, 0, 0),
                 LeftLeg=(-4, 0, -18), RightLeg=(-4, 0, 18),
                 LeftLowerLeg=(18, 0, 0), RightLowerLeg=(18, 0, 0),
                 LeftFoot=(28, 0, 0), RightFoot=(28, 0, 0))
LASER_CHEST = P(AllBody=(2, 0, 0), UpBody=(6, 0, 0), Head=(6, 0, 0),
                RightArm=(-60, 0, -32), RightForeArm=(-78, 0, 0),
                LeftArm=(-60, 0, 32), LeftForeArm=(-78, 0, 0),
                LeftLeg=(-2, 0, -18), RightLeg=(-2, 0, 18),
                LeftLowerLeg=(22, 0, 0), RightLowerLeg=(22, 0, 0),
                LeftFoot=(28, 0, 0), RightFoot=(28, 0, 0))


def laser_spread(t, tremble=1.0):
    return merge(spread_pose(t, tremble), P(UpBody=(-6, 0, 0), Head=(10, 0, 0)))


def chest_point(pose, forward=3.0):
    """魔法阵在胸前的位置（模型坐标，未镜像）"""
    bones, world = bone_world(GEO, {k: v for k, v in pose.items() if not k.startswith('@')})
    M = world('UpperBody')
    p = M @ np.append(mirror(CIRCLE_PIVOT), 1)
    q = M @ np.append(mirror(CIRCLE_PIVOT + np.array([0, 0, -forward])), 1)
    return np.array([-q[0], q[1], q[2]])


def hands_mid(pose, up=2.0):
    a = hand_target(pose, 'R', 2.0)
    b = hand_target(pose, 'L', 2.0)
    return (a + b) / 2 + np.array([0, up, 0])


def circle_at(point):
    d = point - CIRCLE_PIVOT
    return P(MagicCircle=dict(pos=(float(d[0]), float(d[1]), float(d[2]))))


def laser_start_body(t):
    raise_ = ease_in_out(seg(t, 0.0, 0.7))
    lower = ease_in_out(seg(t, 0.7, 1.25))
    spread = back_out(seg(t, 1.25, LASER_FIRE), 1.3)
    start = {k: v for k, v in hover_pose(0).items() if k != '@scythe'}
    if t < 0.7:
        return blend(start, LASER_RAISED, raise_)
    if t < 1.25:
        return blend(LASER_RAISED, LASER_CHEST, lower)
    return blend(LASER_CHEST, laser_spread(t, 0.0), spread)


def laser_circle(t):
    """魔法阵：高举时在两手之间，随双手放到胸前，发射时停在胸前"""
    body = laser_start_body(t)
    if t < 1.25:
        return circle_at(hands_mid(body))
    a = hands_mid(laser_start_body(1.25))
    b = chest_point(laser_spread(LASER_FIRE, 0.0))
    return circle_at(lerp(a, b, ease_out(seg(t, 1.25, 1.45))))


def laser_start(t):
    body = laser_start_body(t)
    # 镰刀离手，竖着飘到身旁
    a = scythe_free(lambda x: merge(hover_pose(0), {}), 0.0)
    b = floating_scythe(t)['Scythe']
    u = ease_in_out(seg(t, 0.0, 0.6))
    scythe = {'Scythe': {'rot': lerp(a['rot'], b['rot'], u), 'pos': lerp(a['pos'], b['pos'], u)}}
    return merge(body, laser_circle(t), scythe)


def laser_fire(t):
    body = laser_spread(t)
    return merge(body, circle_at(chest_point(laser_spread(LASER_FIRE, 0.0))), floating_scythe(t))


def laser_end_body(t):
    u = ease_in_out(seg(t, 0.0, 1.4))
    return blend(laser_spread(t, 1 - u), {k: v for k, v in hover_pose(t).items() if k != '@scythe'}, u)


def laser_end(t):
    body = laser_end_body(t)
    circle = circle_at(chest_point(laser_spread(LASER_FIRE, 0.0)))
    if t < LASER_CATCH:
        target = scythe_free(lambda x: merge(laser_end_body(x), hold('R', **HOVER_GRIP)), LASER_CATCH)
        start = floating_scythe(t)['Scythe']
        u = ease_in(seg(t, 0.2, LASER_CATCH), 2)
        return merge(body, circle, {'Scythe': {'rot': lerp(start['rot'], target['rot'], u),
                                               'pos': lerp(start['pos'], target['pos'], u)}})
    return merge(body, circle, hold('R', **HOVER_GRIP))


# ============================================================ 输出

def main_anim(name, length, fn, loop=False, fps=20, settle=False):
    """main 控制器的动画：不循环的都停在最后一帧，等服务端切到下一个动画（GeckoLib 过渡衔接）"""
    return Anim(name, length, fn, loop=loop, hold=not loop, fps=fps, always=MAIN_BONES, settle=settle)


ANIMS = [
    Anim('wl_blink', 4.0, blink_pose, loop=True, fps=20),
    main_anim('wl_hover', 4.0, calm(hover_pose, 4.0), loop=True, fps=10),
    main_anim('wl_hover_combat', 4.0, combat(lambda t: hover_pose(t, 0.6), 2.0), loop=True, fps=10),
    main_anim('wl_drift', 2.4, calm(drift_pose, 2.4), loop=True),
    main_anim('wl_drift_combat', 2.4, combat(drift_pose, 1.2), loop=True),
    main_anim('wl_toss', TOSS_LEN, calm(toss_pose)),
    Anim('wl_intro', INTRO_LEN, intro, hold=True, fps=20, always=MAIN_BONES + EYE_BONES),
    main_anim('wl_cleave', CLEAVE_LEN, combat(cleave), settle=True),
    main_anim('wl_skull_throw', THROW_LEN, combat(throw_core)),
    main_anim('wl_skull_raise', RAISE_LEN, combat(skull_raise)),
    main_anim('wl_skull_fire', FIRE_LEN, combat(skull_fire), loop=True),
    main_anim('wl_skull_end', SKULL_END_LEN, combat(skull_end)),
    main_anim('wl_absorb_start', ABSORB_START_LEN, combat(absorb_start)),
    main_anim('wl_absorb_hold', 2.0, combat(absorb_hold), loop=True),
    main_anim('wl_absorb_grab', 0.5, combat(absorb_grab)),
    main_anim('wl_absorb_slash', 4.0, combat(absorb_slash), settle=True),
    main_anim('wl_absorb_end', 3.2, combat(absorb_end)),
    main_anim('wl_rapid_start', RAPID_START_LEN, combat(rapid_start)),
    main_anim('wl_rapid_loop', 1.0, combat(rapid_loop, 1.0), loop=True, fps=30),
    main_anim('wl_rapid_end', 3.2, combat(rapid_end), settle=True),
    main_anim('wl_laser_start', LASER_START_LEN, combat(laser_start)),
    main_anim('wl_laser_fire', 1.0, combat(laser_fire, 1.0), loop=True),
    main_anim('wl_laser_end', LASER_END_LEN, combat(laser_end)),
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
        print('laser chest point (model px, unmirrored):', chest_point(laser_spread(LASER_FIRE, 0.0)).round(2))
