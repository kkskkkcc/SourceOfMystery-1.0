# 神威天佑 GeckoLib 穿戴外观

## v4 轻甲（2026-11-07）

v4 取代 v3 的白玉金纹设计和下文 v1 的黑铁设计。新外观为黑白轻甲：黑色绗缝软甲内衬，白色珐琅薄甲片，银色包边，少量金色点缀；胸前两片胸甲之间是太极护心镜。生成脚本为 `tools/gear/divine_blessing.py`，说明见 [GEAR_MODELS.md](GEAR_MODELS.md)。骨骼名称（含 `taiji_emblem`）、黑白流光、发光遮罩与属性保持不变。下文旧生成器 `tools/divine/generate_divine_armor.py` 不再使用，运行会覆盖当前资源。

## v1 记录

物品仍为 `sourceofmystery:divine_blessing_chestplate`，只占胸甲槽。按用户最后确认，穿戴外观包括胸铠、背甲、分层护肩、臂铠与腰甲；胸前采用圆形太极徽章。没有新增头盔、护腿或鞋子物品。

黑铁混沌涡纹作为主表面，银色包边、少量旧金镶边突出甲片结构。太极是独立的黑白双鱼图案，带两个阴阳鱼眼，嵌在16段金属边框内。纹理均为原创程序绘制，未使用第三方美术素材。

## 绑定与神光

`DivineBlessingChestplateItem` 继承原 `MysteryChestplateItem`，实现 `GeoItem`；客户端通过 Forge 原生装备模型扩展提供 `DivineBlessingArmorRenderer`。材质仍是原 `DIVINE_BLESSING`，生命加成 UUID、+200生命、300护甲、无限耐久及既有能力沿用原实现。旧胸甲不需要重新获取，原神环保留。

模型使用6个骨骼：`armorBody`、`taiji_emblem`、`armorRightArm`、`armorLeftArm`、`armorRightLeg`、`armorLeftLeg`。护肩与臂铠跟随各自手臂，左右腰甲分别跟随各自腿部。渲染器在胸甲槽明确启用腰甲的腿骨，使它们随原生步态活动。太极骨骼是躯干的子节点。没有新增独立骨骼物理或服务端计时器。

每13.6秒有约3秒出现黑白流光，平滑淡入淡出；玩家之间错开相位。黑色为贴面的暗影流，白色为发光层，使用专用可平铺流纹的UV移动。两层重绘当前已摆姿的盔甲几何，因此神光附着甲面。太极面片不参与流光重绘，避免图案透明边角被滚动纹理填成方块，也保持符号清晰。通常只绘制基础甲面，神光出现时最多追加两遍；这不包含原版附魔闪光及既有神环。

## 资源与编辑

- 运行几何：`src/main/resources/assets/sourceofmystery/geo/armor/divine_blessing.geo.json`
- 基础材质及流纹：`src/main/resources/assets/sourceofmystery/textures/armor/divine_blessing*.png`
- 动画资源：`src/main/resources/assets/sourceofmystery/animations/armor/divine_blessing.animation.json`，无独立动作控制器，由穿戴者原生姿态驱动。
- 生成器：`tools/divine/generate_divine_armor.py`。
- 静态预览器：`tools/divine/preview_divine_armor.py`。
- 可编辑源文件：`art/divine_blessing_armor/divine_blessing.bbmodel`，标准Bedrock骨骼/逐面UV源，附完整材质；同目录有运行几何拷贝、独立太极PNG、部件表和资产计划。该目录受现有Git忽略规则影响；生成器与运行资源可以重建这些文件。

最终158个Cube、943个四边面、1886个三角形；基础材质与两张流纹均为512×512。一般表面8像素/模型单位，太极图案刻意采用16像素/单位，逐面UV比例均检查。基础材质不依赖PBR或特定光影。

## 验证边界

`build/divine-armor-final-build.log` 记录最终离线 `assemble` 编译、打包与重混淆成功。正面、背面、侧面、流光状态、方格UV与摆腿姿态均生成并查看了静态CPU预览；预览不是Minecraft截图，摆腿示意也不代表已验证实际游戏动作。用户此前要求不再运行游戏测试，本轮没有启动客户端、服务器GameTest或完整整合包，运行时光影/其他模组搭配及帧率未实测。

`tools/divine/release_divine_armor.py --install` 逐条对比上一正式包，只有原 `ModItems.class` 改变，新增6个装备渲染/物品类和7项美术资源；其余既有条目包括270项assets、神环修复、自然恢复每10秒1%的逻辑完全一致。安装与备份记录在 `build/divine-armor-release-checks.json`。

接口依据：[GeckoLib 官方盔甲文档](https://github.com/bernie-g/geckolib/wiki/Geckolib-Armor-%28Geckolib4%29)，具体方法签名同时核对了本机GeckoLib 4.8.4与Forge 1.20.1依赖。

## 肩部覆盖修正（2026-11-05）

用户实际穿戴截图显示，俯视时玩家皮肤肩部会从护肩内侧露出。旧斜护肩内侧较低，而上臂内衬最高只到模型Y=23.3；原生手臂加0.25单位皮肤袖层的肩顶可到Y=24.25，因此原静态空甲预览没有发现这段缺口。

现将左右 `bicep_liner` 肩套居中到X=±6，尺寸改为5×7.1×5，中心Y=20.9、顶部Y=24.45，覆盖肩顶及前后外层，并使用既有混沌金属材质。仍绑定各自手臂，未新增部件、骨骼或渲染遍数；几何统计仍为158 Cube/943四边面。

`tools/divine/inspect_shoulder_fit.py` 从本机原生PlayerModel袖层尺寸建立独立的肩部表面采样，检查普通/纤细左右臂，共15000个采样点，修正后未覆盖点均为0；另检查5种共同手臂刚性姿态，肩套有至少0.2模型单位的余量。该检查是离线几何计算，不等于Minecraft或第三方MMD皮肤的实机验收。报告为 `build/divine-shoulder-fit.json`。预览器增加含白色原生袖层示意的俯视前后对比：`art/divine_blessing_armor/preview_shoulder_before.png` 与 `preview_shoulder_after.png`，均不是游戏截图。

最终 `build/divine-shoulder-final-build.log` 编译打包成功。发布审计/双实例安装记录为 `build/divine-shoulder-release-checks.json`，只变更运行几何中的左右肩套两处；所有类、材质和其他资产逐字节保留。旧包已备份，安装使用临时文件加原子替换，重启生效。本轮没有启动游戏客户端或GameTest。


## 首次穿戴登神 CG（2026-11-05）

已加入只调用穿戴者摄像头的68.7秒登神动画，包含8秒地表穿云至地球轨道的过渡；原穿戴模型与肩部覆盖修正保持。最新王座使用用户提供GLB，高2.3米、宽1.65米、深1.5米，仅灰白色；完整节奏、首次标记、OP重看命令、资源与测试边界见[登神 CG 说明](DIVINE_ASCENSION_CINEMATIC.md)。当前双实例安装及SHA256记录见 `build/divine-cg-release-checks.json`。后续测试客户端默认静音，预览文件可保留音轨。
