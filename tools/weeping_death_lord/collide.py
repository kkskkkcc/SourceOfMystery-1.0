"""
几何工具：把某个姿势下骨骼的方块换算成世界坐标的有向包围盒，用来做穿模检查。
"""
import numpy as np

from geo_render import bone_world, rot_matrix, mirror, affine


def cube_boxes(g, pose, names):
    """names 中骨骼（不含子骨骼）的每个方块 -> (中心, 3x3 半轴矩阵)，世界坐标（像素）"""
    bones, world = bone_world(g, {k: v for k, v in pose.items() if not k.startswith('@')})
    out = []
    for name in names:
        b = bones[name]
        M = world(name)
        for cube in b.get('cubes', []):
            Mc = M
            if 'rotation' in cube:
                Mc = M @ affine(rot_matrix(*cube['rotation']), mirror(cube.get('pivot', [0, 0, 0])))
            o = np.array(cube['origin'], float) - cube.get('inflate', 0)
            s = np.array(cube['size'], float) + 2 * cube.get('inflate', 0)
            c = mirror(o + s / 2)
            center = (Mc @ np.append(c, 1))[:3]
            axes = Mc[:3, :3] @ np.diag(np.maximum(s, 0.2) / 2)   # 薄片也给 0.1 的厚度
            out.append((center, axes))
    return out


def surface_points(boxes, n=5):
    """包围盒表面上的采样点"""
    pts = []
    g = np.linspace(-1, 1, n)
    for c, A in boxes:
        for a in g:
            for b in g:
                for sign in (-1, 1):
                    for perm in ((0, 1, 2), (1, 2, 0), (2, 0, 1)):
                        v = np.zeros(3)
                        v[perm[0]] = sign
                        v[perm[1]] = a
                        v[perm[2]] = b
                        pts.append(c + A @ v)
    return np.array(pts)


def depth_inside(points, boxes, shrink=0.0):
    """每个点在哪个盒子里有多深（像素，<=0 表示不在任何盒子里）"""
    best = np.full(len(points), -1e9)
    for c, A in boxes:
        lengths = np.linalg.norm(A, axis=0)
        U = A / lengths
        local = (points - c) @ U
        d = np.min(lengths - shrink - np.abs(local), axis=1)
        best = np.maximum(best, d)
    return best


def penetration(g, pose, a_names, b_names, n=5, shrink=0.15):
    pa = surface_points(cube_boxes(g, pose, a_names), n)
    return float(np.max(depth_inside(pa, cube_boxes(g, pose, b_names), shrink)))
