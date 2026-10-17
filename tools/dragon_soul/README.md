# 龙魂模型与动作维护

龙魂的权威原始模型来自 `F:\AAAmc模型自动化\1ba`：

- `models/main.json`：原始 106 骨骼、304 个方块。脸、身体、腿、长发、裙甲、翅膀和尾部相关骨骼均从此文件保留。
- `textures/default.png`：原始贴图来源；构建时在不改变原始 UV 细节的前提下生成 512×576 的紫红材质图集。
- `animations/boss.animation.json`：原始动作说明和骨骼约定，尤其是脸部、长发、腿、翅膀和武器的枢轴。

`generate_assets.py` 调用 `enhance_1ba.py`：以 1ba 的原始脸、身体、腿、头发和服饰骨骼为底，增加视频中可见的翅膀分层骨架、翼尖细节、嘴部细节和紫红材质层。没有替换身体骨骼，也没有使用 `2ba`/`4ba` 模型。旧的试验脚本保存在 `generate_assets_procedural_legacy.py`，不参与构建。

增强后的 GeckoLib 几何为 112 个骨骼、512 个方块；新增骨骼承担嘴部、翼尖、尾巴、前发和胸甲细节。

动作文件保留当前实体的触发名称和服务端命中时刻；新增的翼尖和嘴部骨骼有对应的平滑轨道。`longspear` 的原始立方体和 UV 保持不变，只把显示枢轴烘焙到 `RightHand`，移除了会导致武器绕远枢轴旋转的独立位移。

出场服务端流程仍为盘旋、加速俯冲到地面、地面冲击、缓慢起身和最终定姿。地面冲击只产生碎屑、音效和镜头震动，不破坏地形。

验证：`python tools/dragon_soul/validate.py`；姿态预览：`python tools/dragon_soul/preview.py`。已完成资源静态检查和 Gradle 构建；需要实际启动 Minecraft 检查 GeckoLib 材质和武器握持。
