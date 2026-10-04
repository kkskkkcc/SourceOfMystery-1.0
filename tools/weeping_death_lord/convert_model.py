"""
把 2ba/凋零守卫 的 YSM 模型转换成 GeckoLib 实体模型 weeping_death_lord.geo.json，并生成贴图 / 眼睛发光贴图。

改动：
- 整体下移 13 像素（YSM 里脚离地 13 像素，Boss 的悬浮高度由实体自己控制）
- 删除空的定位骨骼（*Locator、molang、Collar 等）
- 裙甲：lingdongkuijia / bone11 / bone12 拆成 SkirtFront / SkirtRight / SkirtLeft，枢轴放在腰上
- 尾巴：bone13 的 7 个方块按位置拆成 Tail1~Tail4 四节，逐节嵌套，方便做摆动
- 两个凋零头外面各包一层 WitherHeadR / WitherHeadL，枢轴放在头的中心
- 镰刀（liandao + bone14）合并成根骨骼 Scythe，枢轴在握柄处，由动画放到手上 / 空中
- 新增 DarkOrb（左手黑球，渲染器按蓄力进度缩放）
用法：python3 convert_model.py <仓库根目录>
"""
import copy
import json
import os
import sys

import numpy as np
from PIL import Image

ROOT = sys.argv[1] if len(sys.argv) > 1 else '.'
SRC = os.path.join(ROOT, '2ba/凋零守卫')
ASSETS = os.path.join(ROOT, 'src/main/resources/assets/sourceofmystery')
DY = -13.0

# 镰刀握柄位置（原模型坐标，下移前）：握在离刀头远的一端
SCYTHE_GRIP = [-7.9, 32.0, 14.0]
ORB_UV = [0, 232]
ORB_SIZE = 6


def shift(v):
    return [round(v[0], 5), round(v[1] + DY, 5), round(v[2], 5)]


def shift_cube(c):
    c = copy.deepcopy(c)
    c['origin'] = shift(c['origin'])
    if 'pivot' in c:
        c['pivot'] = shift(c['pivot'])
    return c


def main():
    src = json.load(open(os.path.join(SRC, 'models/main.json')))
    geo = src['minecraft:geometry'][0]
    bones = {b['name']: b for b in geo['bones']}
    order = [b['name'] for b in geo['bones']]

    drop = {n for n in order if n.endswith('Locator')} | {
        'molang', 'Collar', 'RightSkirt', 'LeftSkirt', 'FrontSkirt', 'BackSkirt',
        'lingdongkuijia', 'bone11', 'bone12', 'bone13', 'liandao', 'bone14', 'KUIIJA', 'KUIIJA2'}
    out = []

    def add(name, parent, pivot, cubes=None, rotation=None):
        b = {'name': name}
        if parent:
            b['parent'] = parent
        b['pivot'] = [round(p, 5) for p in pivot]
        if rotation:
            b['rotation'] = rotation
        if cubes:
            b['cubes'] = cubes
        out.append(b)

    for n in order:
        if n in drop:
            continue
        b = copy.deepcopy(bones[n])
        b['pivot'] = shift(b['pivot'])
        if 'cubes' in b:
            b['cubes'] = [shift_cube(c) for c in b['cubes']]
        if n == 'bb_main':
            b['parent'] = 'Head'
        out.append(b)

    # 裙甲：在 Skirt 下面，枢轴在腰部（无旋转，换父骨骼不影响静止姿势）
    add('SkirtFront', 'Skirt', shift([1.0, 36.5, -2.4]), [shift_cube(c) for c in bones['lingdongkuijia']['cubes']])
    add('SkirtRight', 'Skirt', shift([-3.6, 36.6, 0.5]), [shift_cube(c) for c in bones['bone11']['cubes']])
    add('SkirtLeft', 'Skirt', shift([4.4, 36.2, 0.5]), [shift_cube(c) for c in bones['bone12']['cubes']])

    # 尾巴：按方块拆成四节
    tail = bones['bone13']['cubes']
    segs = [(('Tail1', 'DownBody', [0.0, 35.5, 2.5]), [0, 1]),
            (('Tail2', 'Tail1', [-1.0, 30.5, 7.5]), [2, 3]),
            (('Tail3', 'Tail2', [-3.2, 26.8, 12.5]), [4, 5]),
            (('Tail4', 'Tail3', [-5.8, 23.6, 17.8]), [6])]
    for (name, parent, pivot), idx in segs:
        add(name, parent, shift(pivot), [shift_cube(tail[i]) for i in idx])

    # 凋零头：外层包一个枢轴在头中心的骨骼
    for wrap, orig, center in (('WitherHeadR', 'KUIIJA', [-13.0, 45.3, -0.25]),
                               ('WitherHeadL', 'KUIIJA2', [14.3, 45.3, 2.6])):
        add(wrap, 'MUpperBody', shift(center))
        b = copy.deepcopy(bones[orig])
        b['name'] = wrap + 'Model'
        b['parent'] = wrap
        b['pivot'] = shift(b['pivot'])
        b['cubes'] = [shift_cube(c) for c in b['cubes']]
        out.append(b)

    # 镰刀
    add('Scythe', None, shift(SCYTHE_GRIP),
        [shift_cube(c) for c in bones['liandao']['cubes'] + bones['bone14']['cubes']])

    # 左手黑球（渲染器按蓄力进度缩放 / 隐藏）
    hand = [b for b in out if b['name'] == 'LeftHand'][0]
    palm = [hand['pivot'][0] + 0.4, hand['pivot'][1] - 3.0, hand['pivot'][2]]
    s = ORB_SIZE
    add('DarkOrb', 'LeftHand', palm,
        [{'origin': [palm[0] - s / 2, palm[1] - s / 2, palm[2] - s / 2], 'size': [s, s, s], 'uv': ORB_UV}])

    # 父骨骼必须排在子骨骼前面
    names = {b['name'] for b in out}
    ordered, placed = [], set()
    while len(ordered) < len(out):
        for b in out:
            if b['name'] not in placed and (b.get('parent') is None or b['parent'] in placed or b['parent'] not in names):
                ordered.append(b)
                placed.add(b['name'])
    out[:] = ordered

    geo_out = {
        'format_version': '1.12.0',
        'minecraft:geometry': [{
            'description': {
                'identifier': 'geometry.weeping_death_lord',
                'texture_width': 256,
                'texture_height': 256,
                'visible_bounds_width': 10,
                'visible_bounds_height': 6,
                'visible_bounds_offset': [0, 2, 0],
            },
            'bones': out,
        }],
    }
    with open(os.path.join(ASSETS, 'geo/entity/weeping_death_lord.geo.json'), 'w') as f:
        json.dump(geo_out, f, indent='\t', ensure_ascii=False)

    make_textures(out)
    make_thrown_scythe(bones)


def make_thrown_scythe(bones):
    """掷出的镰刀（ThrownScythe）单独的模型：枢轴在镰刀重心，旋转起来不晃"""
    center = [-7.9, 26.0, -12.0]
    lift = 8.0
    cubes = []
    for c in bones['liandao']['cubes'] + bones['bone14']['cubes']:
        c = copy.deepcopy(c)
        c['origin'] = [round(c['origin'][0] - center[0], 5), round(c['origin'][1] - center[1] + lift, 5),
                       round(c['origin'][2] - center[2], 5)]
        if 'pivot' in c:
            c['pivot'] = [round(c['pivot'][0] - center[0], 5), round(c['pivot'][1] - center[1] + lift, 5),
                          round(c['pivot'][2] - center[2], 5)]
        cubes.append(c)
    geo = {
        'format_version': '1.12.0',
        'minecraft:geometry': [{
            'description': {
                'identifier': 'geometry.weeping_scythe',
                'texture_width': 256, 'texture_height': 256,
                'visible_bounds_width': 6, 'visible_bounds_height': 6, 'visible_bounds_offset': [0, 0.5, 0],
            },
            'bones': [{'name': 'scythe', 'pivot': [0, lift, 0], 'cubes': cubes}],
        }],
    }
    with open(os.path.join(ASSETS, 'geo/entity/weeping_scythe.geo.json'), 'w') as f:
        json.dump(geo, f, indent='\t', ensure_ascii=False)
    with open(os.path.join(ASSETS, 'animations/entity/weeping_scythe.animation.json'), 'w') as f:
        json.dump({'format_version': '1.8.0', 'animations': {'idle': {'loop': True, 'animation_length': 1.0, 'bones': {}}}},
                  f, indent='\t')


def make_textures(out_bones):
    tex = Image.open(os.path.join(SRC, 'textures/default.png')).convert('RGBA')
    a = np.array(tex)
    # 黑球：紫黑色噪点
    rng = np.random.default_rng(7)
    u, v = ORB_UV
    s = ORB_SIZE
    h, w = 2 * s, 4 * s
    noise = rng.random((h, w))
    block = np.zeros((h, w, 4), np.uint8)
    block[..., 0] = (18 + noise * 30).astype(np.uint8)
    block[..., 1] = (4 + noise * 8).astype(np.uint8)
    block[..., 2] = (30 + noise * 45).astype(np.uint8)
    block[..., 3] = 255
    a[v:v + h, u:u + w] = block
    Image.fromarray(a).save(os.path.join(ASSETS, 'textures/entity/weeping_death_lord.png'))

    # 眼睛发光贴图：只有眼珠（*EyesBase）用到的像素，提亮成白光
    glow = np.zeros_like(a)
    for b in out_bones:
        if not b['name'].endswith('EyesBase'):
            continue
        for c in b.get('cubes', []):
            sx, sy, sz = c['size']
            if isinstance(c['uv'], list):
                cu, cv = c['uv']
                regions = [(cu + sz, cv + sz, sx, sy)]  # 只要正面
            else:
                f = c['uv'].get('north')
                if not f:
                    continue
                regions = [(f['uv'][0], f['uv'][1], f['uv_size'][0], f['uv_size'][1])]
            for ru, rv, rw, rh in regions:
                x0, x1 = sorted((int(ru), int(ru + rw + 0.999)))
                y0, y1 = sorted((int(rv), int(rv + rh + 0.999)))
                for y in range(y0, max(y1, y0 + 1)):
                    for x in range(x0, max(x1, x0 + 1)):
                        p = a[y, x]
                        if p[3] == 0:
                            continue
                        lum = (int(p[0]) + int(p[1]) + int(p[2])) / 3
                        k = 200 + int(55 * lum / 255)
                        glow[y, x] = (k, k, 255 if k > 230 else k + 20, 255)
    Image.fromarray(glow).save(os.path.join(ASSETS, 'textures/entity/weeping_death_lord_glow.png'))


if __name__ == '__main__':
    main()
