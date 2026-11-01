# 域外人：PMX 模型测试资源

输入模型保持原始三角网格、UV 和权重；游戏内使用自定义蒙皮渲染器，
不是 GeckoLib 的方块模型。基础资源随模组 JAR 一起打包；有分发限制的参考动作只在本机配置目录使用。

## 参考来源

- [MC-MMD-rust / MmdSkin](https://github.com/shiroha-233/MC-MMD-rust)，
  检查版本 `5fe18f8fb741d25c31c89cdb22643609917446a0`。
  参考其 `EntityAnimationResolver` 用实体实际位移判定动作，以及
  `SubMeshDrawHelper` 的三角形渲染方式。
- `reference/idle.vmd` 与 `reference/walk.vmd` 来自上述版本的
  `common/src/main/resources/assets/mmdskin/default_anim/`。
  上游 MIT 许可证保存在 `reference/LICENSE`，并随 JAR 打包在
  `META-INF/licenses/MC-MMD-rust.md`。
- [KAIMyEntity-C](https://github.com/Gengorou-C/KAIMyEntity-C) 和
  [Saba](https://github.com/benikabocha/saba) 是可进一步参考的 MMD 实体渲染及
  PMX/VMD 播放项目；本次没有引入它们的运行库。

## 本次修正

1. 原渲染器把每个三角形的三个顶点交给 `QUADS` 缓冲区，错误拼接了相邻面。
   现在输出 A/B/C/C，每个四边形恰好包含一个原始三角形和一个退化三角形。
2. 材质槽从 PMX `texture_index` 自动解析。原模型的手工映射恰好正确；
   碎面问题不应归因于贴图未打包或 UV 颠倒。
3. 内置回退动作由 `smooth_gait.py` 生成，保留参考 idle 的放松手指姿态。
   双腿采用解析式两骨 IK，固定膝盖弯曲方向，并处理包括腿 D 骨在内的继承。
4. 待机 4 秒、步态 1.2 秒，均按 60 FPS 烘焙并在渲染时插值。
   支撑期占 60%，摆动期占 40%；五次 Hermite 足迹曲线在触地处保持速度、
   加速度连续，减小跨步并加入骨盆/躯干反向扭转、摆臂延迟和呼吸。
5. 动作二进制 v3 保存局部关节变换，混合后重建层级，避免插值世界位置导致腿缩短。
   `OutsiderLocomotion` 对位移测得的速度低通滤波，使用不同起步/停步过渡时间，
   忽略瞬移，停步淡出期间继续推进动作。身体朝向使用插值后的 body yaw。
6. `generate_face_morphs.py` 从 PMX 展平眨眼、微笑、惊讶、认真和左右单眼 morph。
   `OutsiderFace` 在客户端按实体种子安排 2.5--7.5 秒间隔的眨眼，闭眼曲线约 0.35 秒，
   偶尔安排双眨；待机微笑强度保持在 0.045 以下，行走时自动淡出。
7. 渲染器使用原生 `TRIANGLES`，不再把每个三角形补成退化四边形，减少 25% 的顶点提交。

## 再生成与验证

### 原神女性行走参考：本机外部动作

采用 [Hotaru7MMD 的 Genshin girl walk cycle](https://bowlroll.net/file/287330)
作为本机测试参考。这是作者重现的游戏步态，并非官方动作文件。
作者允许为模型适配修改，要求署名，禁止重新分发原始或修改后的动作数据。
因此 VMD 和派生的 `walk.bin` 均留在被 Git 忽略的 `build/`，不加入源码资源或 JAR。

已自行取得该动作后，可执行：

```powershell
python tools/outsider/import_local_vmd.py --vmd build/outsider-reference/genshin-walk.vmd --output build/outsider-private/config/sourceofmystery/outsider
python tools/outsider/preview.py --motion-dir build/outsider-private/config/sourceofmystery/outsider --prefix genshin-reference --cycles 2
```

适配保留原动作的时序和骨骼曲线，仅修正本模型的 Groove 旋转中心、手臂初始角度，
并烘焙腿/脚尖 IK 和追加变换。84 个源帧包含两个步态循环，完整烘焙为 169 帧、60 FPS，
不截断为单循环。外部动作长度由文件决定，不再按内置 1.2 秒时长重置时钟。

安装时，将本机生成的 `walk.bin` 放入测试实例的
`config/sourceofmystery/outsider/walk.bin`，同时保留作者 read-me 与 source.json。
加载新 JAR 后启动游戏，或在已加载新 JAR 时按 F3+T 重载资源。
日志 `Outsider: loaded local walk motion from ...` 表示文件已被加载。
缺失或无效的外部文件会回退到内置动作；**只替换 JAR 不会启用这个参考动作**。
预览作品署名 Hotaru7MMD，分享时链接作者原发布页，不附带其动作数据。

### 内置回退资源

Python 依赖：`pymeshio`、`numpy`、`scipy`。在项目根目录运行：

```powershell
python tools/outsider/generate_mesh.py
.\gradlew.bat build
```

离线 OpenGL 验证还需要 Pillow、ModernGL：

```powershell
python -m pip install --target build/outsider-preview-deps moderngl
python tools/outsider/preview.py
```

预览读取与游戏相同的二进制网格、动作和贴图，在 `build/outsider-validation/`
输出 `smooth-poses.png`、`smooth-walk.webp`（60 FPS）、`smooth-walk.gif`
（30 FPS）和 `smooth-checks.json`。包含循环接缝、权重、索引、三角形转换、
实际顶点位移、D 骨继承和腿部骨长检查。预览不等于 Minecraft 内实测。

`LocomotionCheck.java` 可与 `OutsiderLocomotion.java` 一起独立编译运行，
测试静止、移动更新间断、停步、瞬移和再次起步。

## 范围

早期友好测试版使用上述待机/行走管线。当前已扩展表情 morph、虚空 BOSS 外观、
三招战斗和六段飞行/起落动作，详见 `art/outsider_void_boss/v1/combat/README.md`。
新 BOSS 请依次使用 `combat_review.py`、`daily_motion.py`、`export_boss_runtime.py`，
不要运行旧 `generate_mesh.py` 覆盖网格。旧本机 554 骨骼 walk.bin 不用于当前飞行 BOSS。
尚未接入完整 MMD 刚体、toon/sphere 着色或任意 PMX 的 SDEF/QDEF（采用线性蒙皮近似）。
长发和裙摆使用编排的跟随动画，没有物理避让，较大动作仍需检查穿插。

表情管线：`face_motion.py` 定义九段动作的 12 通道眉眼口型曲线，
`export_boss_runtime.py` 自动调用并输出游戏二进制与含表情的 VMD。
运行时表情和身体共用阶段时间，并在状态切换时共同混合；不要再只使用旧的
blink/smile 两通道。预览可用 `--face`、`--hands`、`--beauty`。
陨灭杀手腕采用前臂捩骨分担旋转，回归断言限制腕部单帧变化和中立姿势偏差。

## 复用记录

本套链路已在 Forge 1.20.1-47.4.20、GeckoLib 4.8.4 的实际测试实例中通过，后续 MMD 实体优先从这里扩展：

1. 用 `generate_mesh.py` 导出固定 PMX 的网格、材质和权重。
2. 用 `bake_animation.py` / `import_local_vmd.py` 复用 VMD 动作，先检查来源许可。
3. 用 `generate_face_morphs.py` 导出眨眼、口型或其他顶点 morph。
4. 在 `OutsiderRenderer` 中复用局部骨骼插值、CPU 蒙皮和三角形提交。

模型若需要刚体布料、头发物理或任意 PMX 动态加载，再考虑接入完整 MMD 运行库；普通测试实体不应为此重复引入整套运行时。
