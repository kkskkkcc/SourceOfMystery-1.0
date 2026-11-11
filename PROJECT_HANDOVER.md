# 神秘之源项目接手记录

## 最新协作约束（2026-11-05）

用户要求后续测试客户端默认静音。`tools/outsider/run_oculus_smoke.py` 和 `tools/offering/run_integrated_compat.py` 仅在隔离测试目录的 options.txt 设置 `soundCategory_master:0.0`，不得改变用户正常游戏实例音量。预览文件可以保留音轨，由用户自行播放；启动其他测试脚本前也要落实静音。

用户提供宇宙升空 CG 的后续审美参考 https://www.bilibili.com/video/BV1NZxv6rEwJ/ 。具体访问限制、镜头实现与证据见 `docs/DIVINE_ASCENSION_CINEMATIC.md`。本文件是历史追加记录，当前发布信息以末尾最新日期小节及对应发布审计 JSON 为准，不能将下面旧标题的“当前正式版本”当成最新状态。

## 当前正式版本：光影法阵颜色、天空巨阵与云层遮挡修复（2026-10-20）

已安装 `F:\AAAAmc\versions\1.20.1-Forge_47.4.20\mods\sourceofmystery-1.0.0.jar`，34,109,617字节，SHA256 `b20690a4a709f01534a33f78ccb074c03f76c2fb04b84bd16168cd511050ede2`。旧 f91b84a… 包在 build/release-backups/sourceofmystery-before-cloud-fix-f91b84a6857bde7856860bdcf37e1bef8280099f8370d6da7e3952f01e9bba49.jar。安装报告 build/archmage-cloud-installed.json，已核对安装包与发布包哈希一致。

`OutsiderRitualComposite` 针对 Oculus 1.8.0 + Complementary Reimagined r5.9.3 使用真实深度预写和 deferred 后紫色合成，恢复其专用着色器的写入/混合状态，避免实体/距离雾抹掉高空法阵。云层真实距离保存在浮点 colortex5.a，原 RGBA8 会在低视距下把远云截断到1；两张原有纹理一次扩展为RGBA16F并保留内容和ID，尺寸改变时重新准备，无逐帧回读。1280×800额外显存约7.8MiB，不修改用户光影ZIP或设置。合成仍使用LEQUAL深度测试，前景云和方块正常遮挡，纹路不透出背景云；黑灰背环保留原色/透明度并写入笔画深度。其他光影沿用原兼容材质，未知内部目标版本安全回退。

build/archmage-cloud-release-check.log 构建/CPU顶点/表情/衣发检查通过；build/archmage-cloud-release-final.log 最终构建通过；build/archmage-cloud-jar-validation.log 发布资源校验通过。build/oculus-cloud-smoke/final/verification.json 六视角通过，排除HUD后负高度/8区块巨阵紫色像素均超过6万；石墙使近处法阵可见紫色从8521降为637像素，8区块云深度最大2.4101562，GL错误0。build/oculus-smoke/verification.json 完整模型、施法/天空法阵、陨石、普通两招、汇聚流光和说话表情/实际语音播放通过；该完整测试在背环深度补齐后的最终代码上重跑。19个角色语音/音效文件逐字节保持不变，实体与技能逻辑未变，见build/archmage-cloud-release-diff.json。参考来源、成本与实现说明见art/outsider_void_boss/oculus-cloud-fix.md。重启游戏加载新包。

## 前一正式版本：统一角色声线、逐句表情、日常语音与蓄力风效（2026-10-19）

用户已通过陨灭杀新版，授权其他日常/招式换成该声线，并明确要求实际应用。已安装至 `F:\AAAAmc\versions\1.20.1-Forge_47.4.20\mods\sourceofmystery-1.0.0.jar`。
JAR大小34095414字节，SHA256 `f91b84a6857bde7856860bdcf37e1bef8280099f8370d6da7e3952f01e9bba49`。旧版备份 `F:\神秘之源1.0\MCmodfind\build\release-backups\sourceofmystery-before-character-voice-v3-e0d5f094d2ee1de050c74c1c5df36e1f84ef4c25a0e17e12db84f486302d32f7.jar`，安装报告build/archmage-voice-v3-installed.json。

正式生成器 `tools/outsider/generate_archmage_voice_v3.py`：官方Qwen3-TTS-12Hz-1.7B-Base，以用户批准母带前3.1秒的本项目合成声音作唯一参考。17个事件/16个独立录音，包括原交战、两招、大招宣言、吟唱、击杀/死亡和四句日常。陨灭杀release_a/b均逐字节复制同一批准母带，避免备用版本混入旧音色。批准SHA256 b304f65262a3cee6f2d46f410239ab624bfd7ef38bcbc85249a40e0b41045099，录音6.945秒，干声4.945秒，末字回响2秒，喊招1.15倍速/+1.5半音。旧V2生成器已加正式版保护；不要运行它覆盖V3。旧试听历史在voice/v2/REVIEW_STATUS.md，当前批准记录voice/v3/approval.json。

`OutsiderVoice`有17组时长/SPEECH_TICKS/CLIMAX_TICKS，20Hz口型使用干声，回响阶段闭口。`OutsiderSpeechFace`逐句设置眉眼/嘴角，自然眨眼最后统一叠加；宣言转爆发依据录音时点。`OutsiderCombatAudio`按同步VOICE/VOICE_START播放，每BOSS单实例，战斗可中断日常、大招和死亡优先；死亡声音允许在尸体移除后结束。日常无目标时每24–40秒尝试，起落有35%概率短句且12–20秒移动间隔，战斗时不说闲话。

同批已安装：原创7.5秒循环狂风，陨灭杀3–22秒渐强/消退，人声时降低狂风/雷声/施法音；OutsiderChargeWind的48条三维紫色拖影从36–148格汇聚到真实法杖晶核，无粒子实体/区块加载/新增网络包；OutsiderCastingWind使衣摆外展、头发后扬，使用绝对骨骼目标而非逐链放大，保留弹簧锥角/阻尼/碰撞，刘海、手腕和挂点不改。

正式音频、试听、时长、来源见art/outsider_void_boss/voice/v3与CREDITS.md；所有模型/GPU/原生Rubber Band工具只留在build，不进入JAR。构建环境build/archmage-tts/venv，PyTorch2.8+cu128、qwen-tts0.1.1；官方模型逐文件SHA256已核对，模型无需重新下载。

验证：build/archmage-voice-v3-build-test.log中服务端GameTest通过（含日常不叠音/战斗打断日常/大招优先），17句表情/眨眼/回响闭口通过，1831帧几何和15/30/60/144FPS衣发稳定通过；最终build/archmage-voice-v3-release.log构建通过，发布资源校验archmage-voice-v3-jar-validation.log通过。最后改动只为起落语音增加35%触发，保证闲聊也有自然触发机会，已重新构建并验证发布资源。

Oculus此前完整画面/流光/表情检查在archmage-storm-oculus-final.log通过。本轮archmage-voice-v3-oculus.log因客户端正常提前退出而未完成，最新launch.log只有前三张截图，没有崩溃或新的语音播放检查通过标记，不能宣称本轮完整光影/语音实机通过。已保留此前成功报告并单独记录本轮中断。重启用户游戏后加载新JAR；未启动用户原存档，也未修改光影设置。

以下为此前接手与版本历史；当前规格以本节和CURRENT_DESIGN.md为准。

记录日期：2026-10-10。依据当前本地源码、README、开发设计表和历史会话整理；等待验收项已登记，当前仅完成源码构建检查，尚未完成游戏内实机验收。

## 后续开发原则（用户要求，2026-10-18）

- 新功能或修复优先检索成熟开源项目、官方资料、现成实现和公开参考，学习后再实现，避免反复从零造轮子。
- 优先复用或适配已有实现；先核对版本、用途、许可证和实际效果，再决定哪些部分需要自行编写。
- 动画以实际动作参考和多视角预览为依据；帧率、插值及编译通过不能代替对姿势、节奏与游戏内效果的检查。
- 交付时注明参考来源、复用或改写范围、已验证内容和仍待游戏实测的事项。

本轮域外人补充了 PMX 自带脸部 morph 的本地渲染：客户端按实体种子安排眨眼、双眨和轻微待机微笑；渲染提交改用原生三角形，减少原先退化四边形带来的顶点开销。测试环境固定为 Forge 1.20.1-47.4.20 + GeckoLib 4.8.4。

用户已确认本轮域外人模型、VMD 行走动作、脸部 morph、GeckoLib 4.8.4 共存及性能优化在上述测试环境通过。后续遇到 PMX/MMD 实体时，优先复用这套“PMX 三角网格 + 局部骨骼动作二进制 + 稀疏脸部 morph + 本机外部 VMD 导入”的链路：

- `tools/outsider/generate_mesh.py`：保留原始三角面、UV、材质槽和最多四根骨骼权重，生成游戏资源网格。
- `tools/outsider/bake_animation.py`：读取 VMD，处理 IK、追加变换并烘焙为局部骨骼动作；`import_local_vmd.py` 用于带分发限制的本机动作。
- `tools/outsider/generate_face_morphs.py`：把 PMX 顶点/组合 morph 展平为稀疏脸部资源，避免引入完整 MMD 运行库。
- `OutsiderRenderer`：CPU 蒙皮、原生 TRIANGLES 和 morph 混合；只有需要完整 MMD 刚体、布料、表情控制器或复杂物理时，才评估引入 `MC-MMD-rust`、`KAIMyEntity-C`、`Saba` 等完整方案。

复用前需要重新核对 PMX 许可证、动作来源许可证、骨骼命名、模型材质数量和目标 Forge/GeckoLib 版本。Hotaru7MMD 的动作只适合本机适配测试，不能随模组重新分发；MC-MMD-rust 的参考代码和动作按其 MIT 许可保留来源记录。

## 项目定位

Minecraft Java 1.20.1 / Forge 47.4.20 的 RPG 装备成长模组，Mod ID 为 `sourceofmystery`，当前版本 1.0.0。原始设计参考龙之研究。开发使用 Java 17，GeckoLib 4.8.4 为构建依赖；JEI 与 Patchouli 为可选联动。

## 当前玩法

- 获取神秘源矿、秘源锭，在神秘祭坛周围投放材料并右键合成。祭坛扫描半径为 5，多个配方满足时优先高阶配方；首次普通使用解锁成就与能量 HUD。
- 装备分为秘源、灵源、暗源、始源龙、神威五档，含剑和胸甲。高阶装备提供免疫、飞行、生命加成、卫星攻击和能量抵伤等能力；护甲系统提高原版属性上限并追加减伤。
- 神秘之能默认初始 100，每次有效击杀奖励 10，同时增长上限；按游戏日回满。实际存储于玩家 persistentData 的 NBT，由网络包同步到客户端，重生时复制。
- 龙魂：末影龙死亡流程后触发，当前基础生命 5000，含出场镜头、近战连招、投枪和大招。龙魂掉落用于后续装备和神威天道召唤。
- 神威天道：龙魂加五行之源，通过神秘祭坛召唤。默认生命 30000，包含法阵、雷击、天渊与巨型凋灵阶段；二阶段阈值可配置，当前默认 100。
- 泣死之主：原版凋灵死亡触发，基础生命 5000；包含独立出场运镜、镰刀、凋灵头、吸附、连斩和死亡激光。
- 本地新增守护祭坛：下界之星激活，7.4 秒时部署覆盖 3×3 区块、世界底部至 y=255 的结界。实现敌对生物清理、自然刷怪阻止、玩家恢复和饱和，以及针对 Boss 的伤害、爆炸、方块破坏保护与技能目标排除。
- 本地新增守护之灵：按已加载村民识别村庄区域，并用维度 SavedData 保留每片区域的守护名额，死亡时释放，下一轮检查后重生。基础生命 3000、攻击 50、护甲 50，每秒回血 5。服务端统一驱动村内漂浮/落地巡逻、悦灵跟随、友好目标排除、村民治疗、爱心动作和追踪蓝球；法阵闪电现已取消，见末节。

## 代码入口

| 位置 | 职责 |
| --- | --- |
| `SourceOfMystery.java` | 注册入口、配置、网络与护甲初始化 |
| `block`、`recipe/altar`、`datagen` | 祭坛交互、配方分配、配方及资源生成 |
| `energy`、`network`、`hud` | 能量保存、事件处理、同步与显示 |
| `item`、`combat` | 装备属性、增益、卫星、神罚天雷、护甲减伤 |
| `entity` | Boss 状态机、召唤处理、技能实体和守护灵；`GuardianVillageRegistry` 保存村庄守护名额 |
| `guardian` | 守护祭坛方块实体、渲染、结界 SavedData 和事件拦截 |
| `client` | GeckoLib 模型、特效、运镜、音乐和客户端缓存 |
| `loot`、`advancement`、`config` | 掉落、成就与配置 |
| `src/main/resources` | 手写语言、动画、模型、贴图、指南和世界生成 |
| `src/generated/resources` | 数据生成产物；后续修改应从生成器入手 |
| `tools/weeping_death_lord` | 泣死之主模型转换、动画及特效生成脚本 |

## 接手时的状态与注意事项

1. HEAD 为 `8923469`，即 PR #12 合并。工作区已有大量修改及未跟踪资源，集中于守护系统、守护灵和神佑光环。它们属于当前工作成果，后续不得用重置或清理覆盖。
2. 原始需求表和 README 不是当前实现的完整规格。例如 README 的 Capability 描述已过时；暗源和始源卫星当前默认能量消耗为 0，神威卫星为 10，而 README 写的是统一 10。原始表中的 Boss 召唤方式和装备数值也有后续演变。
3. 本地存在 `build/libs/sourceofmystery-1.0.0.jar`，但存在产物不等于当前源码通过构建或游戏验证。本次未验证二者一致性。
4. 常规构建为 `gradlew.bat build`，数据生成是 `gradlew.bat runData`。`jar` 后执行 `reobfJar`；发布必须使用重混淆后的产物。GitHub Actions 使用 Java 17 构建并上传 jar。
5. 根目录存在多份历史构建脚本和日志；已查看的 `build_forge.ps1` 硬编码旧路径 `D:\MCmodfind`，不宜直接作为当前目录的构建入口。
6. 后续功能开发应先明确具体需求，按当前源码校对设计文档；守护结界与 Boss 技能联动、村民聚类生成和客户端视觉效果需要游戏内验证。

## 等待验收

| 功能 | 当前状态 | 验收重点 |
| --- | --- | --- |
| 神秘祭坛合成动画 | 已优化为约 7 秒流程，等待游戏内验收 | 材料上浮并稳定、以祭坛中心做天体环绕并逐圈收拢，能量球结束后是否只生成一次产物；祭坛被破坏时材料是否退回 |
| 神威天佑胸甲神环 | 2026-11-05 已改为躯干装饰层并安装；普通/光影客户端通过，完整整合包复测按用户要求中止 | 随身体转向和潜行躯干倾斜，背面“神”字正立、无镜像；四层神环保持旋转，脱下胸甲即消失，盔甲架亦可展示 |

## 域外人外观审核（2026-10-18）

最新工作目录：`art/outsider_void_boss/v1/README.md`。模型/法杖先审核，战斗动作最后做，三招完整要求存于同目录 `combat_pending.md`。当前为 PMX 本机审核版，未部署游戏。

当前用户指定：灰白头发、黑色眉毛、上身/双臂正常肤色、黑紫服饰与三层旋转符文环；法杖添加蚀刻贴图、镂空冠饰、紫晶、双环及分节握柄。脸部减少高光，保留原始眼睑位置。预览器之前错误地将半透明眼影当成不透明，现改眼部透明混合；接入游戏时需要独立眼部材质通道，不能继续仅按贴图合批。原顶点和原贴图检查一致，保留 62 个表情，法阵循环检查通过；无实机帧率结论。生成脚本 `tools/outsider/void_boss_review.py`，预览脚本 `tools/outsider/render_void_review.py`。

## 域外人战斗与飞行状态（2026-10-18）

动作审核包和说明位于 `art/outsider_void_boss/v1/combat/README.md`：三招攻击、六段日常动作均以 60 FPS 实际 PMX 蒙皮导出。Quaternius CC0 施法、起落动作已实际参与躯干/肩颈曲线重定向；手脚使用本角色 IK 轨迹和接触约束。手臂做四元数平滑，客户端对不同状态按实体独立混合，光环时钟连续。

`OutsiderEntity` 为手动召唤的敌对飞行 BOSS，优先玩家，受击不抢占此优先级。生成时读取神威天道 `bossHealth`；修复原版 MAX_HEALTH=1024 上限截断后默认 30000 生效。免疫普通击退、推挤及外部速度写入。三招服务器伤害/警告/法球与客户端显示共用同步状态时间；射击原点读取实际手骨烘焙轨迹。巨球地形破坏受共享每 tick 预算限制，遵守结界、mobGriefing 和黑曜石抗性阈值。

游戏网格升级 v4，含 559 骨骼、28 材质和法杖贴图，眼部透明通道与服装乘色已接入。运行 `export_boss_runtime.py` 导出，不要运行旧 `generate_mesh.py` 覆盖新 BOSS 资源。`verify_boss_runtime.py` 检查最终 JAR 的网格、动作、贴图、伤害标签及 GeckoLib JSON 隔离。开发用 `runGameTestServer` 在 build 下独立世界验证，测试代码不进 JAR。当前候选版仍需用户验证客户端观感、帧率和多人效果；原 D:\mc\versions\1.20.1-Forge_47.4.20 路径在本次环境中不存在，未替换该实例。


## 域外人手腕、表情与渲染修订（2026-10-18）

陨灭杀手腕改为前臂分担旋转，腕部最大单帧 0.618°、中立偏差 11.994°。九段动作已接入 12 通道眉眼口型，身体/表情共用阶段时间并一起过渡，自然眨眼独立运行。原先 renderer 仅消费 blink/smile 的限制已移除。完整再生成入口仍为 export_boss_runtime.py，新增 face_motion.py、OutsiderExpressions.java；VMD 同时带有表情。

本次构建、最终 JAR 资源校验及手腕/表情回归通过；未重新执行客户端实测，服务器玩法不变。高清实际模型渲染与三视图位于 combat/model-render-hd.png、model-render-views.png；表情和手腕动图为 annihilation-face.webp、annihilation-hands.webp。新 JAR 仍在 build/libs/sourceofmystery-1.0.0.jar，未自动安装到不存在的旧游戏路径。


## 实测反馈修订（2026-10-19）

针对实测反馈，飞行巡游重新烘焙为 4.8 秒循环，增加躯干、髋腿、前臂和衣摆/头发的相位差；客户端新增 VRM SpringBone 风格的 Verlet 惯性、长度/锥角约束和身体胶囊碰撞，共 365 个运行时弹簧关节。攻击期间保留蓄势、转体和回收动作。

黑暗之握施法阶段会绕目标小幅侧移并加入胸腔/肩线波动。弹幕使用不可被爆炸、实体推挤或击退改变的固定发射轨迹，客户端显示、服务器碰撞共用轨迹。陨灭杀前 1.4 秒保持地面可见蓄势，随后升空；本地表情/法阵/法球时间统一到同一动作时钟，法球生成和实际命中保持同一轨迹。

客户端新增 `outsider` shader，为模型提供基础光照、紫色边缘光、深色材质层次，并在资源加载时写入 shader、12 通道表情和 365 个弹簧关节日志。新 JAR 已复制到 `F:\AAAAmc\versions\1.20.1-Forge_47.4.20\mods`。服务端 GameTest（Forge 47.4.20）本轮仍通过。开发目录的 `runClient` 单独启动被已存在的 GeckoLib 4.8.4 dev mixin 映射错误拦截，不能代替用户实例客户端验收。


## 飞行巡游姿势修订（2026-10-19）

飞行巡游由固定身体姿势改为按实际速度方向转身，`OutsiderEntity` 在巡游/下降时平滑更新 `yBodyRot`。`daily_motion.py` 将 flight_move 胸腔前倾提高到约 22°，头部做小幅反向补偿，双臂采用同向前后摆动和对称的放松肘部；肩、肘、腿仍叠加客户端惯性。新 JAR 已覆盖实际测试实例，GameTest 和 JAR 校验通过。


## 着色器崩溃修复（2026-10-19）

实际客户端日志确认崩溃来自自定义 shader JSON 的 vertex/fragment 使用了未限定命名空间，Minecraft 因而查找 `minecraft:shaders/core/outsider.vsh`。已改为 `sourceofmystery:outsider`，重新构建并覆盖 `F:\AAAAmc\versions\1.20.1-Forge_47.4.20\mods`。表情和弹簧资源已在崩溃前成功加载；新的崩溃修复 JAR 通过资源校验，SHA256 为 `68e184bcfabae91b8f9fb0dc263a106657e3e23a84a7a94eb9aba98967642c49`。

## 飞行物理、体型与战斗数值修订（2026-10-19）

飞行弹簧已改为低能量风力响应并提高链条阻尼，缩小身体碰撞半径，避免头发和衣摆脱离身体乱飞；flight_move 增加左右腿错相的髋、膝、踝和足尖随动。模型渲染缩放为 0.147，按顶点高度换算约 3.5 格，实体碰撞体为 1.25×3.5，法杖服务器发射点同步缩放。

域外人生命值固定为 7500。黑暗之握弹幕使用 Boss 当前朝向的随机扇形散射（水平约 54°、垂直约 32°），每枚弹幕发射后保持直线轨迹，不锁定玩家。神威天道全局 bossHealth 默认值及实际测试实例配置已调整为 8000。

本轮 JAR 资源校验和 Forge 47.4.20 GameTest 已通过。


## 最终候选版：陨灭杀与地形破坏修复（2026-10-19）

以下记录覆盖此前技能时长、爆炸预算及散射朝向说明。神威天道基础生命、配置默认值和实际实例 bossHealth 均为 8000；域外人 7500。已有存档实体保留自身属性，重新召唤验证新数值。

陨灭杀改为 3 秒可见蓄势，随后 6 秒展开 13 层法阵（总计第 9 秒生成法球）；下落速度由 3.5 改为 1.1 格/tick，寿命上限延长至 2400 tick。身体/表情沿同一重映射时钟播放，施法与无敌保持到落地后收招，不再按固定 9 秒提前结束。黑暗之握在前摇结束时固定散射朝向，发射期间不随目标转向，俯角 8–40°、水平偏角 ±27°，保持直线轨迹。

地形破坏从法球实体迁入 OutsiderBlastTasks（维度 SavedData）。施法者死亡、特效删除和任务读档不会丢掉已排队破坏。先扫描每列地表的浅层，再循环向下清理；未加载列等待区块加载，不强制加载。每维度共享 2 ms / 1024 次破坏 / 8192 次访问预算，继续遵守 mobGriefing、Forge 事件、结界及黑曜石抗性。伤害与震动落地即时触发，地形异步清理。物理位移、渲染与法杖发射点统一使用 0.147 缩放。

构建、JAR 校验及扩展 GameTest 通过（7500/8000 生命、技能时钟、长下落期间不提前收招、伤害/范围、岩石破坏/硬方块保留、施法者与特效删除后的任务恢复）。日志：build/outsider-final-fixes-test.log。客户端飞行物理观感仍待实机验收，离线关键帧不包含 Java 运行时弹簧。

最终 JAR：15101302 字节；SHA256 `ce45746074612b5cf2a7e29b759f3174813bff382b8153ff2a65e7e36a4a2731`。已覆盖 F:\AAAAmc\versions\1.20.1-Forge_47.4.20\mods\sourceofmystery-1.0.0.jar 并校验一致。重新启动游戏加载。


## 正式命名与超位陨灭杀（2026-10-19，最新）

正式名称为“域外魔导师”，刷怪蛋与英文译名同步更新；保持 sourceofmystery:outsider 注册 ID，生命 7500。神威天道保持 8000。

陨灭杀使用新的独立 annihilation_motion.py（复用现有 PMX IK、Quaternius 参考与 VMD 管线）。0–2 秒蹲伏，2–3.5 秒真实移动上升 10 格，3.5–5.5 秒双手举杖，5.5–7.5 秒依次亮起 15 个同心环；环带宽 .12 格、净间隙 .5 格，全部围绕法杖核心旋转。7.5–12.5 秒在核心上方 10 格呈现持续流动的多层超位术式与旋流，12.5 秒开始通天光束并用 2 秒淡出法阵。按“同时”要求，14.5 秒法球出现并开始下落，与 14.5–15.5 秒的激光淡出重叠。直径 50 格紫色法球从核心正上方 y=255 生成（目标太高时抬高出生点），60 tick 后命中发射时锁定的位置；17.5–19 秒回收。身体 5.5–17.5 秒定格，表情继续吟唱。飞行中会先落地再蹲伏，清空区域实测升高 10 格通过；低矮障碍仍服从实体碰撞。

新动作 1141 帧 / 60 FPS，双手握点误差 <0.000001 原生单位，腕部保持中立。法杖核心导出为 outsider_ritual_socket.bin；法阵和服务器发射点按同一最终骨骼位置计算。先前伤害和 SavedData 地形任务保留。自定义血条为黑紫金属框、紫晶符文、流动血量纹理和吟唱提示，按 Boss 网络 UUID 替换该实体的原版条；原图、提示词及审核图见 art/outsider_void_boss/v1/hud/README.md。

构建、最终 JAR 资源校验和 Forge 47.4.20 服务端 GameTest 通过，涵盖完整时钟、10 格平滑上升、14.5 秒落球、第 60 tick 命中、7500/8000 生命、原有伤害与地形恢复。HUD 和动作做过离线审核；客户端超位特效观感、兼容性、帧率尚待实机验收。新 JAR 已安装到 F:\AAAAmc\versions\1.20.1-Forge_47.4.20\mods，SHA256 `2a5e21951cb0bb9466100c0eebf65c5a37e7e5f4d42463e05b9854a8cedc1255`，16307825 字节。

审核：combat/annihilation.webp（身体动画，不含 Java 运行时特效）、combat/annihilation-poses.png、hud/archmage_hud_review.png。重新生成：python tools/outsider/annihilation_motion.py，然后 python tools/outsider/export_boss_runtime.py。完整 combat_review.py 也会调用新生成器。


## 飞行、法阵厚度、雷鸣与手部对齐修复（2026-10-19，最新）

飞行由站姿附近的脚踝 IK 目标改为明确的不对称 FK：左腿抬起且向后屈膝约 105–114°，右腿微微后屈约 30–36°，脚踝和脚尖持续柔和随动；实际变形骨骼 98–105 同步烘焙。悬停/巡游/下降/起落共用该姿势过渡，端点检查通过。flight_move.webp 已更新。

头发乱甩来自链上逐节继承已经偏转的弹簧方向，锥角累积。客户端新增未叠加弹簧的 authoredWorld 骨骼矩阵，用每节原始世界方向作锥角参考，在当前父骨根位置求解；头发最大锥角 .16rad、阻尼 24、120Hz 子步，暂停帧仍执行投影。SpringBoneCheck 对 18 节链执行 15/30/60/144 FPS、转向和零 dt 压力测试，最大世界偏差约 9.167°，尾长误差约 0.0000011 原生单位。

法阵使用 .28 格宽、.30 格厚的非加法暗紫实体壳层，净间隔仍为 .5 格；正反面和侧沿都有发光层。超位符文环和旋流加厚，7.5–17.5 秒周围持续出现纯视觉紫白电弧。增加 archmage.charge/chant/thunder/release 四个音效事件及中英字幕，复用原版音频事件调低音高；蓄势与举杖充能、每 2.5 秒吟唱低鸣、每 1.5 秒周围雷鸣、光束与落球时释放声。没有额外伤害、点火或天气改变。

针对“所有手部交互法阵不对齐”：模型曾使用 Mob 的非同步 yBodyRot，服务器发射点使用另一朝向；手中球还使用世界 +Y 固定偏移。现模型、动力学与服务器统一使用网络 YRot。黑暗之握法球和手掌符文、黑暗寂灭双掌符文都使用当前帧实际手骨矩阵；掌心偏移改为随手腕旋转的原生 -Z。陨灭杀法阵/光束直接使用该帧法杖 skin[557] 与实际紫晶中心绘制，不再生成独立静止法阵实体。姿态混合、缩放和转身统一，且没有为效果增加第二次全网格蒙皮。地面警告和已经发射的弹幕仍使用世界坐标。技能大范围特效在角色离开屏幕边缘时继续绘制。

构建、JAR 资源校验、角度/变形骨骼检查、物理数值测试和扩展 Forge GameTest 全部通过。GameTest 覆盖多朝向与 ±180° 跨界时服务器/渲染坐标一致、原有技能时序/伤害/地形任务。本轮没有在用户客户端验证最终特效视觉、声音混音和 FPS。最终 JAR 已覆盖实际测试实例 mods，16305595 字节，SHA256 `d0ca237720932753486225e6bb8cd99552257f42c335e5202d07a138159befab`。日志：build/archmage-attachment-final-validation.log；预览：combat/flight_move.webp；数值报告：daily-checks.json、spring-checks.json、jar-validation.json。


## 受击箱、飞行夹角与弹幕方向修复（2026-10-19，最新）

此节覆盖先前飞行髋部角度和弹幕固定俯角的记录。日常飞行/下降/起落已收紧双腿分离角，实际变形骨骼测得大腿最大夹角约 23.958°，髋到脚踝整腿最大夹角约 25.559°，给运行时轻微腿部摆动留出低于 30° 的余量。保留左腿抬起后屈、右腿轻微后屈及脚踝脚尖随动。daily-checks.json、最终 JAR 校验均验证角度；flight_move.webp 和关键帧已更新。

黑暗之握此前固定俯角 24±16°，导致全部向下。改为围绕水平施法朝向上下 ±32°、水平 ±27° 随机散射，速度 .75 格/tick；仍在开始施法时固定朝向，发射后直线飞行、不锁定目标。GameTest 检查五个朝向的上/下/水平发射和随机分布。

神威天道旧注册箱为 18×48 格，漏掉双臂、旋转雷环和原点下的尾部。tools/divine/bake_hitboxes.py 复用 GeckoLib 4.8.4 的模型坐标变换规则，以 60 FPS 和 16 个 idle 相位从现有 geo/animation 资源生成 DivineModelBounds.java，记录见 art/divine_heavenly_dao/hitbox-checks.json。常态本地包络为 X[-15.5,15.5]、Y[-6,47.75]、Z[-10.75,12.5] 格，随网络 YRot 转向；动作期间受击包络覆盖该动作全部帧及常态，使用普通 AABB，并非逐三角形判定。实体移动/地形碰撞保持常态包络，不随攻击伸手扩大到技能全范围。眼高保持原来的 40.8 格，渲染 body yaw 对齐网络 YRot。

单纯扩大 AABB 仍会被原版 EntitySectionStorage 按实体原点的查询漏掉高处/远侧，所以沿用 Forge PartEntity/末影龙连续 ID 分配模式增加一个命中代理。客户端射线和服务器 getEntityOrPart 都可找到代理，伤害转交 Boss 本体，保留原有护甲、远程免疫与降临无敌；移除/卸载跟随 Forge 的部件索引清理，不独立保存或生成。

构建、最终 JAR 资源检查、Forge 47.4.20 GameTest 通过：五种朝向的头/双手/尾/雷环射线、外侧射线未命中、位移与部件跟随、动作箱更新、玩家近战扣血、部件伤害转交及移除清理，以及既有技能/伤害/地形任务。日志 build/hitbox-flight-barrage-final.log。客户端画面和实际游玩手感仍待实测。

最终 JAR 已替换 F:\AAAAmc\versions\1.20.1-Forge_47.4.20\mods\sourceofmystery-1.0.0.jar，16310163 字节；SHA256 `79024ae877a555dbe542cd51c09cd03a9e57474b685e72bdde5551d2ef6840f1`，源文件与安装文件校验一致。重启游戏加载。


## 躯干—骨盆飞行联动、前倾落地与超位术式修订（2026-10-19，最新）

本节覆盖此前飞行上下身独立、法阵加厚及短电弧方案。日常动作继续复用 Quaternius CC0 Jump_Land/Jump_Start 的肩颈细节，修正最终 PMX 空间姿势：腰骨 5 带动上下两个骨骼分支，巡游胸腔前倾 27.7–30.3°、骨盆前倾约 15.4–17.2°；双腿继承该倾角，再加入共同滞后的摆动。运行时加速和转弯由 OutsiderFlightInertia 分配给腰部与胸腔，腿部不再只有独立横摆。15/30/60/144 FPS、暂停帧及转向的实际变形骨骼检查通过：最大整腿夹角约 25.949°、大腿夹角约 24.211°，仍低于 30°。报告 flight-inertia-checks.json，源 tools/outsider/FlightInertiaCheck.java。

落地在前倾约 24.649°时下沉、屈膝缓冲，再恢复直立，没有后仰帧；植地后脚踝误差约 0.00000133 原生单位。六个日常动作衔接端点检查通过。实际 PMX 审核图/动画：flight_move.webp、landing.webp、landing-profile-poses.png、flight_move-profile-poses.png。离线预览不包含客户端弹簧和本次 Java 特效。

按用户明确纠正，只修改陨灭杀超位魔法：恢复 .12 格细环带、移除 .30 格实体厚壳，使用普通 alpha 黑紫底色和紫色符文来加深颜色；法杖上方术式最大半径 16 格（直径 32 格，原约 19.6 格），倾斜环和旋流同步扩大。撤回黑暗之握/黑暗寂灭后来新增的厚手部符文环，保留原有手球、地面警告和光柱，以及已修正的手掌/法杖发射坐标。两招动作、陨灭杀身体动作和 OutsiderSpellRenderer.class 与安装前候选版逐字节一致，记录 other-skills-preserved.json。

天空雷电参考 Cataclysm 的显式端点/寿命处理思路，直接复用 Minecraft LightningBoltRenderer 的种子分叉网格；没有导入新的特效库。起点为建筑高度上方 32 格（角色上方至少 96 格），前端在两 tick 内向周边地面劈下，顶端保持天空高度，保留雷鸣声。稳定种子与落点、错开的闪烁时隙；仅渲染临时对象，不加入世界、不 tick，不增加伤害/火灾/天气。原来的近身短电弧已移除。参考链接与实现说明见 tools/outsider/reference/sky_lightning/README.md。

陨灭杀地形任务严格按实际落点 Vec3 为球心、半径 100 格的三维球体判断方块中心；不再将球心取整到 BlockPos。逐列上下界来自球面方程，兼顾非整数球心的边缘列，保留世界高度边界、2 ms 分批预算、抗性/结界/事件与 mobGriefing。SavedData 保存精确球心，并兼容旧 Centre 字段。伤害与其他技能数值不变。新增测试覆盖六个方向的 100/101 格边界、三维斜角、非整数球心；恢复到上下球面边缘的真实 SavedData 任务，验证 100 格内石头被破坏、101 格外保留。

最终构建、JAR 资源验证和扩展 Forge GameTest 全部通过；日志 build/flight-super-tier-sphere-final.log。客户端实际特效、物理穿模与帧率仍待用户验收，数值稳定性通过不代表已经实测完全不穿模。

已备份原安装 JAR 到 build/release-backups/sourceofmystery-before-flight-super-tier-79024ae8.jar，并替换实际实例 mods/sourceofmystery-1.0.0.jar。新 JAR 16329965 字节，SHA256 `110b14a9414754abdcbedd4c95ee297258f00573769b39f5504f2112ceac739f`；构建文件与安装文件一致。重启游戏加载。


## 超位法阵豪华层、施法连续性与空中释放修复（2026-10-19，最新）

超位魔法在原有 15 层法环上增加古代符文刻线、钩形符文段、银紫边缘、中心封印和八向放射脉络；在术式上方加入四组错位倾斜法环，分别绕 Y/X/Z 轴交错旋转，每组带四门封印与八个边缘符文节点。原有黑紫底色和 32 格术式直径保留，其他两招的特效不再加厚。实现位于 OutsiderRitualRenderer，自动校验要求 ancientMarks/luxurySeal 已打包。

黑暗之握/黑暗寂灭施法期间保留移动、腰部、胸腔、肩线和裙摆的连续物理响应，不再因为 attacking 状态跳过身体惯性。动作源文件对左右腕增加全轨迹平滑及每帧 6° 上限；运行时再以 8° 上限保护网络帧切换。手腕与裙摆之间保持至少 4.05 个 PMX 原生单位的水平间距，手腕后代骨骼同步平移，避免手部穿入裙摆。陨灭杀仍保持双手握杖的原有中立腕姿。

起跳由固定向下/固定向上速度改成 32 tick 的下沉、蹬地、平滑升空曲线；此前已完成的躯干—骨盆—腿联动和前倾落地保留。飞行运行时惯性压力测试最大大腿夹角约 24.211°、整腿夹角约 25.949°。

陨灭杀释放条件增加高度分支：实体处于 AIR 且离地高度大于 10 格时可直接进入 METEOR，不再强制先执行降落、落地、起跳过渡；低于或等于 10 格仍走原有安全落地流程。GameTest 覆盖阈值和原有完整技能时钟。

最终构建、JAR 资源校验和 Forge 47.4.20 GameTest 通过，日志 build/hand-wrist-air-release-test.log。动作离线审核图：annihilation-poses.png、dark_grasp-hands-poses.png、dark_silence-hands-poses.png。客户端实际裙摆穿模和超位法阵视觉仍需用户实机确认。

已备份上一版安装 JAR（SHA256 `110b14a9414754abdcbedd4c95ee297258f00573769b39f5504f2112ceac739f`）并安装新 JAR 到 F:\AAAAmc\versions\1.20.1-Forge_47.4.20\mods\sourceofmystery-1.0.0.jar。新 JAR 16335568 字节，SHA256 `5c15aed33744284e5c9f5ac6d5609721552dd6e332be7af85c0fca5f7f9c660d`，安装文件校验一致。重启游戏加载。


## 起跳蹬地与超位法阵精细化重做（2026-10-19，最新）

针对实测“蹲下后直接飞起”，起跳重新拆为四段：0–0.35 秒压低身体，0.35–0.62 秒保持压缩，0.62–0.95 秒腿部伸展蹬地，0.95–1.6 秒升空并前倾。动画根节点下沉约 4.5 个原生单位，脚部保持接触后再离地；服务器 TAKEOFF 前 10 tick 保持地面，之后采用平滑加速曲线。离线侧视审核图为 combat/takeoff-profile-poses.png，端点和腿角度检查通过。

超位法阵不再只靠颜色或粗细区分。基础 15 层法环加入 16 组古代钩形符文，每组包含主干、横笔、尖尾和交替副笔；超位层加入八向放射脉络、中心封印和四组 X/Z/Y 错位的倾斜法环，每组有四门封印和八个边缘节点，持续独立旋转。法环仍采用细线宽度 .12 格和 32 格超位直径，颜色保持原深度，不再加厚。设计参考“天空坠落”类法术的多层细符文环和独立旋转结构；资料参考 [奇术天陨](https://www.curseforge.com/minecraft/mc-mods/thaumic-skyfall) 与 [Mahou Tsukai 天空坠落条目](https://www.mcmod.cn/item/474128.html)。

构建、JAR 资源校验和 Forge 47.4.20 GameTest 通过，日志 build/takeoff-luxury-final-test.log；新 JAR 16332650 字节，SHA256 `146c86fe75ffe15f0b22cf75046fa98e41a6515eec46f1147e9a538d65b5fed2`，已覆盖测试实例。上一版安装包 SHA256 `5c15aed33744284e5c9f5ac6d5609721552dd6e332be7af85c0fca5f7f9c660d` 已备份到 build/release-backups/sourceofmystery-before-takeoff-luxury.jar。客户端真实游戏中的法阵层次和起跳观感仍需重新实测。

## 透明符文法阵层与飞行倾角收敛（2026-10-19，最新）

针对上一版“程序线条叠环”仍然粗糙的问题，超位术式改为三张透明自发光纹理层：`ritual_circle_base.png`（同心外环、断续弧、放射脉络）、`ritual_circle_inner.png`（多重封印环、符文门、中心星印）和 `ritual_circle_sigil.png`（密集角形古符文、嵌套核心）。基础十五环在展开过程中逐步显现外环纹理；超位阶段按外环、内轮、核心三层反向/正向独立旋转，并叠加四个不同倾角的纹理法阵平面。纹理使用 `RenderType.eyes` 全亮透明材质，保留清晰细线和空隙，没有继续用加厚实体壳层替代纹路。

参考结构采用多层同心法环、细圆润符文和独立旋转的“天空坠落”类术式构图：[Thaumic Skyfall](https://www.curseforge.com/minecraft/mc-mods/thaumic-skyfall)、[Mahou Tsukai 天空坠落条目](https://www.mcmod.cn/item/474128.html)。

飞行巡游的离线最终躯干前倾已从约 27.7–30.3°收敛到 14.2–15.8°，运行时速度惯性俯仰上限由 0.16 弧度降至 0.09 弧度，横向滚转同步收小；左腿抬起、右腿后屈、落地前倾和起跳蹬地保持不变。巡游整腿夹角仍为 25.21°，低于 30°限制。

最终构建与 Forge 47.4.20 GameTest 通过，日志为 `build/texture-tilt-final-test.log`；JAR 资源校验通过，含三张法阵纹理、九段动作和 12 个表情通道。新 JAR SHA256 `60dfaa8ff890c467ce00229f74f86b031782e45798494395ad88fd3c3152534b`，大小 16,653,222 字节，已安装到 `F:\AAAAmc\versions\1.20.1-Forge_47.4.20\mods\sourceofmystery-1.0.0.jar`。客户端法阵层次和飞行姿态仍需在游戏内重新观察确认。

## 拍地手腕与全身起跳修复（2026-10-19，最新）

拍地动作的手腕问题来自掌心朝地 IK 在前臂接近共线时存在自由滚转，导致左右手腕偶发选择相反四元数分支。`combat_review.py` 现在让 `dark_silence` 的手腕保持与实际前臂父骨相同的 authored local frame，并在效果骨应用后再次锁定该局部姿态；左右手腕相对前臂的最大局部偏差为 `3.4e-14°`，不再独立扭转。拍地掌心高度和双手目标误差检查保留。

起跳不再只改腿部。`daily_motion.py` 新增完整的蓄力—爆发曲线：骨盆先压缩，胸腔和颈部前压，双肩与肘部同步回收；蹬地阶段骨盆、肋骨、肩臂和根节点一起释放，根节点先下沉约 4.5 原生单位再上冲约 4.5 原生单位，最后平滑接入飞行姿势。离线审核图为 `combat/takeoff-profile-poses.png`。

新增发布校验：拍地腕部局部姿态不得偏离中性前臂框架；起跳必须同时具备骨盆、胸腔、肩臂和根节点位移。构建、Forge 47.4.20 GameTest 和 JAR 资源验证全部通过，日志为 `build/wrist-takeoff-final-test.log`。

新 JAR SHA256 `5c13184d6355c41d4dfd4fd58d37ba862de7cc7c700b53b3f81c30d8512469d1`，大小 16,641,181 字节，已安装至 `F:\AAAAmc\versions\1.20.1-Forge_47.4.20\mods\sourceofmystery-1.0.0.jar`；上一版已备份到 `build/release-backups/sourceofmystery-before-wrist-takeoff-60dfaa8ff890c467ce00229f74f86b031782e45798494395ad88fd3c3152534b.jar`。

## 法阵纹理渲染崩溃修复（2026-10-19，最新）

用户客户端日志确认崩溃原因是 `java.lang.IllegalStateException: Not filled all elements of the vertex`，调用链为 `EffectGeometry.glowQuad -> OutsiderRitualRenderer.glyph`，发生在新增透明法阵纹理层后切换 RenderType 的过程中；不是 GeckoLib 冲突，也不是 PNG 文件损坏。旧代码在 `RenderType.eyes` 纹理缓冲结束后继续写入已失效的闪电缓冲。

修复后，法阵程序几何统一使用单一 `POSITION_COLOR` 的 `sourceofmystery_ritual_volume` 缓冲，纹理层切换后重新获取该缓冲；超位三张 PNG 纹理仍使用自发光纹理 RenderType，三层构图和错位法环保留。这样不会再把旧的 VertexConsumer 跨 RenderType 复用。

构建、Forge 47.4.20 GameTest、动作/资源校验全部通过，日志为 `build/ritual-texture-crash-fix-test.log`。新 JAR SHA256 `5224f884a4ff73e0fe470c133852c33b4c2e0636cd588a96c31638813668b4f8`，大小 16,641,222 字节，已覆盖安装到 `F:\AAAAmc\versions\1.20.1-Forge_47.4.20\mods\sourceofmystery-1.0.0.jar`。上一版已备份到 `build/release-backups/sourceofmystery-before-ritual-buffer-fix-5c13184d6355c41d4dfd4fd58d37ba862de7cc7c700b53b3f81c30d8512469d1.jar`。

## 参考法阵图章重做与透明边框修复（2026-10-19，最新）

根据用户提供的复杂法阵参考图，三层法阵贴图重新绘制为完整图章结构：多重外轮铭文带、三角/八芒星几何、中心封印、交叉法术带、八个节点圆徽、内外环伪文字刻线和小型符文节点。`tools/outsider/generate_ritual_textures.py` 可重复生成三张 PNG。法阵和法环使用 `entityTranslucentEmissive`，保留全亮发光；纹理外缘 alpha 硬清零，避免整张贴图四边形底板显示。

上一版截图中的大块白/粉色方形来自纹理四边形透明混合不正确，已改用带透明混合的自发光实体材质，并保留单一程序几何缓冲，避免再次触发 VertexConsumer 崩溃。

构建、Forge 47.4.20 GameTest、JAR 资源验证全部通过，日志为 `build/ritual-reference-fix-test.log`。新 JAR SHA256 `d288b2549f6faff88c4292580442e179788333ad0b65525683298d5a965cc5a2`，大小 17,580,946 字节，已安装到 `F:\AAAAmc\versions\1.20.1-Forge_47.4.20\mods\sourceofmystery-1.0.0.jar`。上一版已备份到 `build/release-backups/sourceofmystery-before-ritual-reference-5224f884a4ff73e0fe470c133852c33b4c2e0636cd588a96c31638813668b4f8.jar`。

## 四方卫星法阵与基础环移除（2026-10-19，最新）

超位魔法新增第四重法阵：四个专用紫色卫星法阵贴图，水平轨道半径 20 格，位于核心斜上方四个方位；每个法阵的法线始终朝向中间超位魔法球环，四个法阵围绕核心公转，同时自身以独立角速度自转。新增资源为 `ritual_circle_satellite.png`，结构参考用户提供的多重外轮、三角封印、节点圆徽和中心锁印图案。

按用户反馈移除了基础阶段旧的十五圈程序紫色环，基础阶段现在只显示完整的参考图式透明法阵贴图，不再叠加一圈圈粗糙紫色程序环；超位核心和四个卫星法阵保留发光与旋转。

构建、Forge 47.4.20 GameTest、JAR 资源校验全部通过，日志为 `build/satellite-no-base-rings-final-test.log`。新 JAR SHA256 `392e1234c90cf1b74d96066077b3933b2fd461cc4d821b19def0d6604cb1a204`，大小 17,993,243 字节，已安装到 `F:\AAAAmc\versions\1.20.1-Forge_47.4.20\mods\sourceofmystery-1.0.0.jar`。上一版已备份到 `build/release-backups/sourceofmystery-before-satellite-no-base-rings-d288b2549f6faff88c4292580442e179788333ad0b65525683298d5a965cc5a2.jar`。

## 基础参考环替换与超位程序线条清理（2026-10-19，最新）

基础法阵改用用户提供的单环镂空花纹参考图，经过透明通道提取和紫色自发光着色，资源为 `ritual_circle_base_reference.png`；旧基础十五圈程序紫色环不再绘制。

超位核心删除旧的 `glyph`、`ancientMarks`、`luxurySeal`、放射线和旋流程序线条，只保留纹理法阵层、中央球环、法杖光束和四方卫星法阵。四个卫星法阵同时增加同姿态紫色几何轮廓，避免自发光贴图排序异常时完全不可见。

新增资源校验已覆盖基础参考环和卫星法阵贴图。构建、Forge 47.4.20 GameTest、JAR 资源验证全部通过，日志为 `build/satellite-reference-clean-final-test.log`。新 JAR SHA256 `b37bd1f49259b44a2af15ec1644df801df50775554db664df245874c1a658776`，大小 18,515,600 字节，已安装到 `F:\AAAAmc\versions\1.20.1-Forge_47.4.20\mods\sourceofmystery-1.0.0.jar`。上一版已备份到 `build/release-backups/sourceofmystery-before-reference-base-clean-392e1234c90cf1b74d96066077b3933b2fd461cc4d821b19def0d6604cb1a204.jar`。

## 深紫色、尺寸与血条边缘清理（2026-10-19，最新）

基础参考环降低模糊光晕并增强线稿锐度；基础、内轮、核心和卫星贴图统一改为深紫黑主色，保留较亮的符文边缘维持质感。中央超位贴图层半径扩大为 21.5、17、11 格，倾斜纹理层扩大到 13–16 格；卫星法阵水平轨道由 20 格收至 10 格，斜上高度同步调整。

陨灭杀法球改为深紫不透明核心，外层保留低亮度紫色法环，避免整体发灰发白。自定义血条框通过 alpha 连通区域清理，移除边缘孤立黑色点块，保留黑紫金属主体和宝石纹理。

构建、Forge 47.4.20 GameTest、JAR 资源验证全部通过，日志为 `build/deep-purple-scale-hud-clean-final-test.log`。新 JAR SHA256 `6c30d2aee4de7b31128441bc7cf2e0889e177546ae4e3a6a56ddf88a709f391e`，大小 18,465,439 字节，已安装到 `F:\AAAAmc\versions\1.20.1-Forge_47.4.20\mods\sourceofmystery-1.0.0.jar`。上一版已备份到 `build/release-backups/sourceofmystery-before-deep-purple-scale-hud-clean-b37bd1f49259b44a2af15ec1644df801df50775554db664df245874c1a658776.jar`。

## 陨灭杀 V2 全重做与持续雷暴（2026-10-19，当前版本）

本节替代上方历次陨灭杀法阵/动作方案。按用户新要求重做：不足离地 10 格先用 METEOR_PREP 全身起跳/升空，已有高度直接施法。空中 3 秒双手举杖、双腿张开约 29.97°；随后分层展开 10/15/20/15 格直径的高清纹理法阵，四枚 10 格卫星法阵围绕超位层公转/自转。展开完成等 3 秒，光束持续 3 秒，再依次淡出法阵和光束共 4 秒。Y=255 天空法阵长大 2 秒后生成深红不透明法球，法球在 5 秒后命中；出现法球 1 秒后天空法阵开始消失。完整施法 29 秒加收势 1.5 秒，升空准备另计。持续雷声和自上而下闪电覆盖准备到收势全过程。

六张新法阵使用 image_gen 参照本机素材高清重绘，实际输出 1254×1254 RGBA，已复制到 assets/sourceofmystery/textures/entity/outsider/ritual_v2_*.png。所有法阵采用不受环境光衰减的透明颜色纹理材质、圆形网格，无整张矩形底板；复杂超位图案分四个同心区域独立转动。旧程序环不再绘制。旧背环三层几何在游戏绘制时跳过，换成 321321321.webp 构图的紫色高清背环。

手腕修复删除了只平移手掌却未同步前臂权重的运行时避裙操作；拍地及陨灭杀改用局部中性手腕，并避免身体叠加扰动接触动作。新陨灭杀腕部局部跳变为 0，握杖误差约 8e-15 原生单位。

验证：build/ritual-v2-final-test.log 构建及 Forge 47.4.20 GameTest 通过；build/ritual-v2-client-check.log 在 Forge 客户端中实际运行 1831 帧 BufferBuilder，7883 批、7584056 顶点，无顶点元素不完整；build/ritual-v2-jar-validation.log 发布资源/动作/表情/透明通道校验通过。客户端开发检查需 -PritualCheck，已处理 GeckoLib 开发环境 SRG/MCP refmap 映射；不会进入发布包。不能把这些检查说成已实机完整观感验收。

详细规格及预览：art/outsider_void_boss/v2/README.md、fully-open-offline.png（实际 Java 顶点离线 OpenGL 渲染，非游戏截图）。

发布 JAR：33,600,494 字节，SHA256 35838ee6c72138cea8cda832094258d8f2838e405bd757852cba5d71c011bf82。已覆盖 F:\AAAAmc\versions\1.20.1-Forge_47.4.20\mods\sourceofmystery-1.0.0.jar 并比对哈希；旧版备份 build/release-backups/sourceofmystery-before-ritual-v2-6c30d2aee4de7b31128441bc7cf2e0889e177546ae4e3a6a56ddf88a709f391e.jar。

## 天空巨大法阵不可见：世界特效绘制修复（2026-10-19，当前版本）

实测实例日志确认陨灭杀确实执行，测试地面约 Y=-60；天空法阵在 Y=255，两者相差约 315 格。检查本机 Forge 47.4.20 引擎后定位到 `LevelRenderer.renderLevel`：普通实体通过距离/视锥检查后，还要通过实体所在位置的 `isChunkCompiled` 检查。远处高空空分段未编译时，即使 `noCulling=true` 也会跳过实体，因此此前仅检查生成和顶点数据未能覆盖该缺陷。

新增 `OutsiderHighSpellEffects`，参考本仓库 `WeepingIntroDirector` 已用的 `RenderLevelStageEvent.AFTER_ENTITIES`，直接绘制已同步的 SKY_SEAL 和 METEOR；普通 `OutsiderSpellRenderer.render` 对这两类提前返回，防止双重绘制。世界入口不依赖实体所在高空分段编译，不按垂直高度限制可见距离，保持深度测试及实际轨迹相机相对坐标。原时序、法阵 Y=255/直径 50 格、颜色、法球运动和伤害均未改动。

客户端回归使用真实网格/BufferBuilder，在无世界及无已编译分段的条件下，从相机 Y=-63/-60/64/240/320 均输出天空法阵并核对相对高度与直径；高空落球输出数据，弹幕不进入该流程。日志 build/sky-seal-world-pass-client.log，SKY_WORLD_PASS_OK。构建及专用服务端 GameTest 通过：build/sky-seal-world-pass-final.log；发布资源检查 build/sky-seal-world-pass-jar.log 通过。没有宣称已在用户存档完成画面验收。

新 JAR 33,602,754 字节，SHA256 8464e52dfa587a777874d9388175d7f2971e7f5529ac873564a0d63deabd3243。已覆盖 F:\AAAAmc\versions\1.20.1-Forge_47.4.20\mods\sourceofmystery-1.0.0.jar 并比对哈希。旧版备份 build/release-backups/sourceofmystery-before-sky-world-pass-35838ee6c72138cea8cda832094258d8f2838e405bd757852cba5d71c011bf82.jar。

此前按用户要求生成的运转动画在 art/outsider_void_boss/v2/super-core-operation.webp、super-array-operation.webp。中央同心分区切断小圆徽的缺陷已明确告知用户，尚未重做；本轮只修高空法阵/落球可见性。

## 独立淡黑灰背环与激光轴线天空法阵（2026-10-19，当前版本）

用户追加：仅背后法阵调淡并自转，随后明确为黑灰色；天空巨大法阵半径改为 100 格，水平中心必须对应激光发射轴线，Y 仍为 255。

背环专用 `outsider_halo` 着色器实时将纹理转为黑灰，保留透明细节；透明度从 .90 降至 .35，自转从 3.2°/秒改为 10°/秒（36 秒一圈）。通过 BACK 专用 RenderType 选择该着色器，其他紫色法阵和角色皮肤均不受影响，背环大小保持原样。GPU 实际编译着色器并检查 RGB 等值、最大 alpha=89/255，通过报告 build/halo-gray-shader-check.json。

OutsiderRitual 新增 SKY_RADIUS=100（直径 200）、SKY_HEIGHT=255。第 15 秒激光发射时，用已导出的真实法杖核心轨迹与同步的身体朝向计算世界坐标，锁定天空法阵水平中心；第 22 秒从该中心展开天空法阵，不再跟随目标水平位置。法球第 24 秒从天空法阵中心出现，在原有 5 秒内落向锁定目标。球体大小、伤害和爆炸范围不变。

客户端回归校验半径 100 格、5 个观察高度通过：build/halo-gray-sky100-client.log。服务端验证移动目标不会把天空法阵中心拉到目标位置，法球出现在法阵中心且终点保持锁定目标，通过：build/halo-gray-sky100-final.log。发布资源验证 build/halo-gray-sky100-jar.log 通过。

发布 JAR 33,604,385 字节，SHA256 f8e7f785425567a60d80802db37a5fb7594febc52c30362720f4bd68ec2f5d0c。已覆盖 F:\AAAAmc\versions\1.20.1-Forge_47.4.20\mods\sourceofmystery-1.0.0.jar 并比对哈希，旧版备份 build/release-backups/sourceofmystery-before-gray-halo-sky100-8464e52dfa587a777874d9388175d7f2971e7f5529ac873564a0d63deabd3243.jar。重启游戏后生效，未把自动绘制数据检查描述为用户存档的实际画面验收。

## 背环加深、天空半径 200 格、降落地震/音效、删除确认的长摆（2026-10-19，当前版本）

用户强调以当前黑灰背环为准，不参考旧背环；背环不透明度 .35 -> .60，黑灰专用着色器与 10°/秒自转保持。天空法阵 SKY_RADIUS=200，即直径 400 格，Y=255 和激光轴线的 X/Z 中心锁定保持。

法球从第 24 秒开始降落时：在真实天空生成位置播放新增单声道 OGG `archmage.meteor_fall`，5 秒、44.1 kHz，原始程序合成的风噪/低频轰鸣/破空声；运行音量 32、音高 1，以默认衰减将传播范围扩至 512 格，避免从 Y=255 发声时地面听不到。脚本 tools/outsider/generate_meteor_audio.py，编码依赖位于 build/outsider-audio-deps，不进入游戏 JAR。编码后重新解码峰值 .9489，RMS .3489，通过检查 build/meteor-fall-audio-check.json。同时向落点周围水平 256 格内玩家发送已有 ScreenEffectPacket：100 tick、强度 1.25 的地震，落地原来的更强震动保留。

长摆已向用户展示隐藏背环的三视图、橙色高亮疑似部件，并取得明确回复“是，删除橙色长摆”。已从当前 PMX 源模型和导出的游戏网格删除 `VoidMantle` 所绑的两侧新增裙片及边线，共 54 个三角面；保留顶点/骨骼/表情索引，避免破坏已审核动作。combat_review.py 增加 remove_fixed_panels，后续生成不会再恢复它们。当前网格 52,536 顶点、52,492 三角面，4,002,076 字节。删除后预览 art/outsider_void_boss/v2/skirt-parts-removed.png，源模型 art/outsider_void_boss/v1/combat/outsider_combat.pmx。

验证：build/meteor-descent-skirt-client.log 客户端高空绘制检查通过，直径 400；build/meteor-descent-skirt-final.log 构建和服务端 GameTest 通过，额外捕获 Forge PlayLevelSoundEvent，确认音效在法球起点 Y=255 播放且只触发一次。build/meteor-descent-skirt-jar.log 发布资源检查通过，确认无三角面再引用确认的长摆骨骼，声音 OGG/字幕/注册资源存在。没有做用户存档中的最终画面或听感验收。

发布 JAR 33,652,913 字节，SHA256 f68b948fd43720eb2d02159de24abbd2a0b1b047843fcdedd3a6e0f55b33d915。已安装 F:\AAAAmc\versions\1.20.1-Forge_47.4.20\mods\sourceofmystery-1.0.0.jar 并比对哈希；旧版备份 build/release-backups/sourceofmystery-before-meteor-descent-skirt-f8e7f785425567a60d80802db37a5fb7594febc52c30362720f4bd68ec2f5d0c.jar。


## 30格寂灭、两分钟大招、Oculus颜色兼容及日语角色语音（2026-10-19，当前版本）

黑暗寂灭按本轮已向用户说明的半径30格（直径60格）实施：随机采样、选招距离及拍地时目标越界中断判定统一；保持19–40根光柱、原有伤害和警告时长。陨灭杀改为独立2400 tick冷却，从开始准备施放计算，不阻塞普通两招；使用世界gameTime期限并写入MeteorReadyAt存档，卸载和读档不能重置。服务端GameTest实际验证目标29格脚下光柱、扩展范围、越界限制、普通招式不重置大招CD、读档保留和自然到期。

Oculus修复：OutsiderShaderCompat通过可选IrisApi.isShaderPackInUse检测实际启用的光影，无硬依赖。角色在光影中使用标准NEW_ENTITY实体材质，每个PMX三角面补一个重复末端顶点适配QUADS，保留原始UV、蒙皮和眼部叠层。法阵提供完整实体属性并采用标准着色器入口；黑灰背环客户端灰度纹理保持尺寸、透明度、自转。通用EffectGeometry.voidBuffer将黑球/光柱/裂隙从Oculus未接管的原版末地传送门核心着色器切到标准实体材质，覆盖AbyssOrb、VoidRift、SpaceShatter和OutsiderSpell。

用户补充“颜色无法体现”后，实际光影截图证明只有可见性修复还不够。检查Complementary原始gbuffers_spidereyes.glsl，确认其对紫色的Enderman增强；改为深紫底纹+低量自发光两遍材质。仅发光纹路写入真实深度，透明底经Iris 0.1 alpha阈值丢弃，避免整片圆盘遮挡；天空层单独提高紫色发光，修正云层错误盖在巨阵前面。近处法阵紫色像素饱和度中位数约.192->.692，天空约.219->.437（筛选像素统计用于辅助，不替代截图审核）。关闭光影使用原有自定义材质。参考源码：https://github.com/Asek3/Oculus/tree/1.20.1-new ，实际光影源码来自用户已有Complementary包，未修改该包。

隔离实机验证工具tools/outsider/run_oculus_smoke.py和src/shaderTest。单独shaderCheckJar经过SRG重映射，不进发布JAR；复制GameTest世界到build/oculus-smoke，离线测试账号，不读取登录令牌，不改用户原存档、光影设置。Oculus 1.8.0、Embeddium 0.3.31、ComplementaryReimagined_r5.9.3.zip真实开启，RTX 5060 Ti，五张截图确认角色/黑灰背环、身边法阵、高空巨阵、深红法球、黑球和光柱可见，glGetError为0，客户端正常退出。记录build/oculus-smoke/verification.json与color-comparison.json；图片build/oculus-smoke/screenshots。原白化版本对比图保留在build/oculus-before-color。测试初版场景把角色埋在自然山体内，已改用Y200独立平台；不能把该初版模型不可见截图当成修复失败或成功证明。

新增13句原创定制日语台词，冷静成熟女声，声源VOICEVOX:九州そら。官方Core0.17.0 + VVM0.16.0/2.vvm/style16，本地离线合成，轻度降调、保留语调、齿音削减/轻度EQ/压缩、单声道24kHz OGG；不打包合成引擎和模型。普通两招、大招起手、吟唱、天空召唤各两句随机；交战、击杀、死亡各一句。实体语音互斥，避免叠音；按录音20Hz包络驱动A/O口型，中文客户端有逐句中文字幕。语音字幕遵循原版“显示字幕”设置。吟唱讲话时降低本实体雷鸣音量。声源按官方规则署名，mods.toml介绍页和META-INF/licenses/outsider-japanese-voice.md均提供；这些声音不被代码MIT授权覆盖。完整台词、试听、时长和来源在art/outsider_void_boss/voice；12秒样音archmage-combat-sample.wav、完整试听archmage-voice-preview.wav，生成器tools/outsider/generate_archmage_voice.py。若改动台词，须同步OutsiderVoice.IDS/TICKS和录音包络顺序。音频13条均已解码核对单声道24kHz、有限采样、峰值<.85。

验证：build/archmage-compat-build.log（构建+服务端GameTest通过）；build/archmage-compat-client.log（无Oculus时1831帧、实体属性适配与位置材质适配检查通过）；build/archmage-shader-color-depth-build.log（最终发布构建）；build/oculus-smoke-color-depth.log（最终真实光影客户端）；build/archmage-final-jar-validation.log（资源、13条语音与包络、无测试类泄漏检查）。更细的中央超位法阵组件动画缺陷、动作重量感/穿插等之前已记录问题仍需单独处理，本轮未声称完成。

发布 JAR 33992696 字节，SHA256 e0d5f094d2ee1de050c74c1c5df36e1f84ef4c25a0e17e12db84f486302d32f7。已覆盖 F:\AAAAmc\versions\1.20.1-Forge_47.4.20\mods\sourceofmystery-1.0.0.jar 并比对哈希；旧版备份 F:\神秘之源1.0\MCmodfind\build\release-backups\sourceofmystery-before-voice-oculus-f68b948fd43720eb2d02159de24abbd2a0b1b047843fcdedd3a6e0f55b33d915.jar。重启用户游戏生效。

## 黑暗次元祭品与域外魔导师召唤过场（2026-10-22，初版，已被下方重做替代）

新增物品 `sourceofmystery:dark_dimensional_offering`：EPIC、不可燃、不可堆叠；贴图为 128×128 黑紫晶体祭品，模型使用 `minecraft:item/generated`。唤魔者在 `doMobLoot=true` 且由玩家直接击杀时有 30% 概率掉落。物品右键标准 4×5、14 格黑曜石未激活门框（横向或纵向均可，门内 2×3 必须为空）后消耗 1 个祭品并召唤域外魔导师。

召唤由 `OfferingPortalRitual` 服务端时钟和 `DimensionalOfferingPacket` 客户端脚本共同驱动。时序为：4 秒第一人称移至玩家侧后上方；2 秒门前特写；7 秒 14 个黑曜石块拆分并漂浮成圆面边框；3 秒玩家面部；4 秒黑紫拖影汇聚；4 秒蜘蛛网裂隙扩大；3 秒虚空圆门破开并震动；7 秒域外魔导师左脚、上身、右脚逐步显现；4 秒回望传送门并抬手小法阵抹除黑曜石；最后 3 秒表情特写后交还玩家视角。服务端在 6 秒开始移除真实黑曜石，客户端临时绘制漂浮方块；34 秒小法阵完成后停止临时边框并保留无框虚空效果。

`OutsiderRenderer` 在出场期间仍逐帧执行原有 PMX 蒙皮、表情、弹簧骨骼、衣发物理和眼睛发光；仅在 27 秒裂隙破开前隐藏实体，之后使用实时物理姿态完成显现，避免把模型替换成静态贴图。横向/纵向门框坐标、镜头中心和特效平面已统一修正。

验证：`gradle build`（Forge 47.4.20、重混淆 JAR）通过；`runGameTestServer` 现有域外魔导师 GameTest 通过；`tools/outsider/verify_boss_runtime.py` 发布资源校验通过。当前开发 JAR 为 `build/libs/sourceofmystery-1.0.0.jar`，大小 34,151,527 字节，SHA256 `13E1D9B0D68FB6813BFB4FA72AE21B6A8147B53F606B42E39900877D2E9189F9`。尚未在用户存档中完成完整 41 秒画面观感验收。


## 召唤过场实机反馈后重做（2026-10-22）

用户实测否定初版：石块挤成一团，Boss 从地下升起，面部没有表演；允许按魂类 Boss 登场与原神虚空登场的审美重新编排。上节关于左脚/上身/右脚逐步穿门、完整表情和双轴坐标已修正的描述不准确：初版实际上是竖直平移 + takeoff，Z 轴 Frame.pos 仍有坐标交换错误，未实机验证美术。以下实现和实机记录取代旧描述。

新版 32 秒：0–3 秒退出第一人称到侧后方；3–8 秒十四块黑曜石保持轮廓顺序逐块外扩，半径 3.65 格，围绕自身中心轻微转动；8–10 秒玩家反应；10–13 秒裂隙扩张；13 秒短促爆开与快速衰减震动；14–22 秒真正从门后向前移动 3 格；22–26 秒回望、左手施法及门框消散；26–30 秒推近到眉眼表演；30–32 秒平滑交还视角和 FOV。沿门框法线朝玩家一侧入场，两种门框方向均支持。

独立 offering_intro 18 秒、1081 帧、559 骨骼动作，由 tools/outsider/offering_motion.py 生成；脚部双骨 IK，落脚阶段可达误差为 0，左/右脚依次迈步，身体重心、持杖、头颈和胸部回望、左臂施法与 12 通道表情均在同一动作时钟。只生成新动作，不重做已有 PMX/材质/战斗动作。运行期保留弹簧骨骼与衣发碰撞；取消过场中的飞行骨骼叠加以保护脚部约束。隐身等待期间也更新蒙皮与弹簧。门面裁切使用蒙皮后的三角形与 UV/法线插值；删除原版向上平移显现。虹膜发光复用真实眼球表面与 UV，受表情变形影响，不再用横向发光矩形覆盖眼睛。

客户端时间改为世界 tick，暂停时不会继续播放；保存/恢复视角、HUD、FOV，过场隐藏姓名牌和 HUD，后半段隐藏本地玩家以避免挡镜。实际方块在展开起点移除，临时几何在施法时缩散。加入召唤、裂隙爆开、消散音效。修正 Z 轴门框坐标、几何中心和 Boss 朝向；同一门框不能重复启动。服务端停止时清除临时仪式表。

验证：build/offering-rework-tests.log，两项 GameTest 通过（原战斗回归 + 两种方向的所有 14 个可点击方块、已激活门拒绝、穿门轨迹单调检查）。build/offering-rework-final-build.log 构建通过；build/offering-rework-final-assets.log 发布网格、十条动作、面部轨道与原资源检查通过。独立 shaderTest 测试类不进入发布 JAR。tools/outsider/run_oculus_smoke.py --offering 启动隔离 Forge 客户端，逐帧截图并检查摄像机/HUD恢复、OpenGL 错误。第一、二轮实机画面发现眼光横条、夜景太暗、测试底座埋低、玩家挡镜与姓名牌；均据此迭代。最终画面记录和安装校验见下方追加。


## 踏空登场、稳定物理与大步步态（2026-10-25，当前已安装版本）

已先读取本地聊天“接手模组开发工作”（01a14a2b-502e-7613-9b2e-daef6ecaa10c）继承上下文。项目实际根目录 F:\神秘之源1.0\MCmodfind。用户随后多次实测提出自制蓝色虚空门、威压感、侧面身体/脸部特写、胸口响指关门、真实高跟鞋踏地声、出场完整后保持2秒、脸部依次看左右再盯玩家、严肃眉眼配嘴角邪笑、眼睛速度0.7倍、上身呼吸和胸部惯性、衣发稳定、瞳孔不能出眼框、离地1格踏空及鞋下小紫法阵、脚到脸连续运镜。最新要求放大步幅，身体步态和胸部动态可参考雷电将军。以下取代上节过时的3格位移/回望关门时间描述。

32秒当前时序：0–3秒第一人称退到侧后上方；3–8秒14个黑曜石保持原有轮廓顺序外扩，不经过中央揉团；8–10秒玩家反应；10–13秒碎裂扩张；13–14秒蓝色裂隙冲击；14–22秒从门后沿法线向玩家走出，并以单条连续运镜从高跟鞋升到脸；22–24秒全身保持2秒，呼吸与物理仍运行；24–30.5秒面部近景左/右/玩家视线与邪笑，24.9秒左手在胸口响指，25秒门开始塌缩，25.8秒黑曜石消失；30.5–32秒平滑归还视角/FOV/HUD。门面为自制深蓝虚空和蓝色边缘/流纹，未使用原版地狱或末地传送门材质。14块边框半径3.85，门面半径3.4，底部留出踏空通道。

动作生成 tools/outsider/offering_motion.py：独立559骨骼、1081帧、18秒60Hz，只改 offering_intro，不重做现有网格和战斗动作。当前四次落脚全局15.9/18.2/20.2/22秒，前进位置0.65/1.30/1.95/2.20格，前三次相邻落脚0.65格，最后收至0.25格错步站姿。上一临时版六次0.23格的小步已被替代。两脚左右间距0.25格，抬脚5厘米；骨盆根据双支撑中心及步幅降低，髋部带动、胸肩反向、左臂小幅摆动、头颈稳定，持杖手腕仍严格驱动法杖。原“10度”已向用户说明用于左右张开，前后迈步按自然角度放开。实测左右张开最大1.612度、三维整腿夹角25.803度、大腿27.178度、膝盖弯曲36.042度，落脚可达误差0；原有全三维夹角<=10度的旧限制不再适用。检查报告 art/outsider_void_boss/v1/combat/offering-intro-checks.json。

实体根沿 -.45+2.20*smoothstep 前进，脚底位于门框底部上方1.04格。身体重心总前进2.075格，结束位于门前1.625格。OfferingPortalRitual.entranceBodyDistance 与生成器脚步轨迹一致，镜头跟随真实支撑中点而非实体根，避免放大步幅后错位。紫色脚底法阵半径0.17格，与落脚、抬脚和真实高跟鞋音效同步。

胸部由 OutsiderChestDynamics 在实际有权重的18/19骨骼及其子链上执行双侧二阶弹簧；胸廓呼吸周期3.8秒、肩/脊柱协同，软组织响应呼吸与移动加速度。当前刚度100/103、阻尼17.5/17.8、惯性系数.52、偏移上限.17 PMX单位（2.5厘米），微小非对称；每步积分<=1/120秒。相较临时版加强阻尼、减小呼吸目标，避免持续摇晃。实际光影录制胸部最大约1.37厘米，胸廓站姿范围约4.67厘米。衣物保留腿部碰撞，过场角度上限约1.72度；头发整体约3.15度锥角，力与重力降低，头部绝不被弹簧改写。瞳孔局部旋转最大3.2度、速度0.7倍、真实眼球网格UV发光；严肃眉眼+mouth channel6邪笑，不再误用cheerful眉毛通道1。

性能修复：移除逐tick同步递增过场计数，改为同步开始gameTime；摄像机、动作、实体统一时间。每渲染帧复用主/阴影姿态，避免重复推进物理。自制门面复用极坐标顶点数据并删重复反面。OutsiderVertexBatch 每个唯一蒙皮顶点只变换一次；针对已装 Embeddium0.3.31/Oculus1.8.0 使用可选原生批量顶点接口，Iris ENTITY stride56及完整光照/覆盖/中点UV/切线属性。无硬依赖，不支持组合走标准VertexConsumer。保留52,492三角面，没有降模换静态图片。实际日志 OUTSIDER_BULK_VERTICES active=true stride=56。

高跟鞋是 mefrancis13 的 CC0 “High Heels on Wooden Floor” 实录裁剪变体，来源 https://freesound.org/people/mefrancis13/sounds/117608/ ，源文件在 art/outsider_void_boss/audio_sources，许可随JAR META-INF/licenses/offering-heel-foley.md。其他虚空/响指由本项目合成。正式事件四次高跟鞋、一次响指，服务端时序和导出混音一致。未复制原神/鸣潮动作文件、角色资源、音乐或语音；官方雷电将军演示 https://www.youtube.com/watch?v=mvrW4aKwAXw 作为用户所指风格的来源，不能声称逐帧复刻或质量等同。

验证：build/offering-poised-stride-tests.log：build、shaderCheckJar、两项必需GameTest通过；build/offering-poised-stride-assets.log：完整网格/战斗动作、17条现有Qwen3语音等回归校验通过；build/offering-poised-stride-release-checks.json：发布动作/面部/音频与源资产一致，测试类不进入发布JAR。隔离实机工具 tools/outsider/run_oculus_smoke.py：复制GameTest世界、离线测试角色，从不读取用户登录令牌或改用户存档；光影 ComplementaryReimagined_r5.9.3，RTX5060Ti，1280x800，渲染距离18，上限60FPS。以下都完整播放32秒、摄像机/HUD恢复、GL无错、物理边界和4次落脚/1次响指检查通过：
- build/offering-smoke/poised-stride-recorded：真实异步PBO/NVENC录制，均值52.664FPS、出场44.719FPS、p95 23.602ms。
- build/offering-smoke/poised-stride-runtime：不录制，开启光影，均值54.450FPS、出场47.915FPS、p95 22.208ms；胸部1.16厘米、胸廓4.62厘米、衣物偏移3.26厘米、眼角3.20度。
- build/offering-smoke/poised-stride-axis-vanilla：另一门轴、关闭光影、不录制，均值58.305FPS、出场55.751FPS、p95 20.328ms。
旧版基准 performance-before 在相同隔离场景光影下均值38.24FPS、出场30.29FPS。不能描述为锁定60FPS或用户原存档最终美术验收。

新版预览 build/offering-preview/outsider-poised-stride-final.mp4，44,548,993字节，32秒，H2641280x800/60FPS容器、AAC。由实际游戏采样、NVENC编码，未使用光流或补画；不足60帧的时间槽重复采集帧以保持游戏时间轴，不能把60FPS容器当实测帧率。音轨是发布OGG按服务端落点混合，非系统声卡录音；来源/帧率记录 capture-source.json。8个实机关键画面在 poised-stride-recorded/screenshots，已查看脚部、连续升镜半身、全身、抬手面部和最终邪笑。调用open_in_codex返回queued，不代表用户已经播放。

已安装 F:\AAAAmc\versions\1.20.1-Forge_47.4.20\mods\sourceofmystery-1.0.0.jar，34,754,188字节，SHA256 352224234f206d86b121e478c8323ef2491fdf31874b9343d2e9e58608327094，源与安装文件哈希一致，重启游戏生效。安装记录 build/offering-poised-stride-installation.json。安装前版本8471462b...备份 build/release-backups/sourceofmystery-before-poised-stride-8471462b56d702da0a7a7bff5ebec1622495c764f7abb26a006bc61cccd6662b.jar。更早实际游戏仍是13e1d9b0...初版，已说明并在本轮前一阶段安装过847版本，现以352224版本为准。没有提交git，也未覆盖其他既有开发改动。


## 出场持杖手臂修正（2026-10-25，当前已安装）

用户指出右臂一直伸直、持杖不合理。检查旧 offering_intro：肘部走动时只弯13.2度，站定仅1.6度；原q60绕Y偏转主要是扭转，并未形成合理屈肘。现 tools/outsider/offering_motion.py 改为完整肩55/肘60/腕65双骨链求解，握点在腰腹前侧，上臂靠近肋部；前臂61–64分配旋转以对齐杖杆，保持腕65原有局部姿态。四指重新弯曲环住杖杆，以手掌接触点和横过指关节的轴线定义新握持关系，不再沿用直臂下垂的杖/腕绑定偏移。法杖557仍逐帧跟随实际手掌；步伐、呼吸带动握点细微起伏，7–8.5秒动作时间由迈步持杖收至站姿持杖。

生成器全1081帧验证：右肘屈曲88.028–98.625度，手腕无额外折弯，握点无相对滑动。build/offering-grip-isolation-check.json 确认只改变右臂/前臂、右手四指与法杖骨骼，其他动作骨骼逐值一致。发布包与上版逐条解压比较，仅 assets/sourceofmystery/animations/entity/outsider/offering_intro.bin 改变（build/offering-grip-release-checks.json），表情/步态/胸部物理/衣发/声音/战斗代码等均无额外修改。

验证：build/offering-grip-build.log 构建和重混淆通过；build/offering-grip-assets.log 完整资源回归通过；build/offering-smoke/natural-staff-grip/ 为真实光影客户端32秒录制，检查眼睛/衣发/胸部边界、声音事件、摄像机/HUD归还、GL均通过。已查看19.5秒半身、23秒全身、25.2秒面部抬手画面，右手在腰腹前握住杖杆、手肘收于身体侧面、法杖不遮脸。此前两项服务器GameTest仍为上轮已通过结果；本次只有动作资源变化，没有重复服务器测试。

预览 build/offering-preview/outsider-natural-staff-grip.mp4，真实游戏画面；音轨按正式OGG及服务端时点混合，60FPS容器保持时间轴，缺帧重复采集帧，不代表实测锁60。open_in_codex 返回queued，不代表用户已播放。

本次录制均值 50.77 FPS、出场段 42.76 FPS。已安装 F:\AAAAmc\versions\1.20.1-Forge_47.4.20\mods\sourceofmystery-1.0.0.jar，34847891 字节，SHA256 6035e2d63202a8f09fe0474a348b0ef42e8632d17b6c920d73921f451f8e38ed；源和安装哈希一致，重启生效。备份 F:\神秘之源1.0\MCmodfind\build\release-backups\sourceofmystery-before-natural-grip-352224234f206d86b121e478c8323ef2491fdf31874b9343d2e9e58608327094.jar。安装记录 build/offering-grip-installation.json。

## 出场音效重做与末尾镜头交接修复（2026-10-25，当前已安装）

用户本轮指出高跟鞋、响指、传送门开关声音均不合格，以及最后一帧镜头落到脚边。没有改动上一轮已完成的持杖手臂、步态、模型、表情或物理资源。

镜头根因已用本地 Minecraft Camera 字节码和实机画面核实：临时 Marker 的眼高为0，Camera 的 eyeHeight/eyeHeightOld 会跨摄像机实体切换持续平滑，切回玩家的首帧因此从脚部开始恢复；旧结尾还返回开始点击下方门框时的向下视线。OfferingIntroDirector 增加 CinematicCamera，眼高与玩家一致，镜头坐标按眼高换算实体脚部位置。最后1.5秒将玩家朝向和上一帧朝向同步到 Boss 头部，使用独立 Camera 计算实际玩家视角位置（含原第三人称类型及碰撞距离），插值完成后再归还摄像机、HUD和原视角类型。仅正常播放完成时更新玩家朝向，场景失效中断仍恢复原配置。

声音改用新来源：lenayrossouw / Freesound707566 的木地板高跟鞋实录剪出三种0.38秒落脚；SoundCollectah /109400 的真实响指裁剪0.38秒；CogFireStudios /617761 “Big Scary Portal Open” 编辑成5秒反向蓄势、3.048秒开启撕裂余响、1.35秒向内收缩及低频闭合。均为CC0，原始预览与许可元数据在 art/outsider_void_boss/audio_sources，完整出处随发布包 META-INF/licenses/offering-heel-foley.md。生成脚本 tools/outsider/generate_offering_audio.py 做轻度降噪、滤波、淡入淡出、峰值管理和44.1kHz单声道OGG编码；不再使用之前117608鞋声及合成响指/门声。不能将波形/事件测试描述为用户听感验收。

OfferingIntroAudio 仅为正在观看动画的客户端将这五个事件改为相对声、无距离衰减，镜头特写远近变化不再改变音量。增益鞋.90、响指.95、蓄势.55、开门.90、关门.70；其他玩家仍听原服务器空间声。响指24.9秒，关门起始由25.0调整为25.15秒（SEAL_TICK503），门完全删除25.9秒（518tick），门面及黑曜石缩放统一跟随该时间。首次隔离测试发现 Forge PlaySoundEvent 在 SoundInstance.resolve 之前触发，调用原实例 getPitch() 会空指针，现使用这些服务端事件固有的1f音高，已重新实机验证无该异常。首次失败日志保留在 build/offering-smoke/audio-handoff-final，不能引用为通过记录。

验证：build/offering-audio-handoff-build.log 构建、shaderCheckJar及2项必需GameTest全部通过；build/offering-audio-handoff-assets.log 完整运行资产回归通过。真实光影客户端 build/offering-smoke/audio-handoff-verified 使用离线隔离世界、Oculus/Embeddium/ComplementaryReimagined_r5.9.3，完整32秒动画并继续录制至34秒，通过4次鞋声、1次响指、各1次蓄势/开门/关门、无距离衰减属性、眼睛/衣发/胸部边界及GL检查。测试开始时玩家低头60度，并在结束前模拟鼠标下看82度，验证最终恢复仍朝Boss；32秒后16个测量帧的最低眼高1.61999989格、交接最大相邻镜头位移0.000968247格。已人工查看29.7秒面部和32.05秒切回玩家截图，后者保持眼高、面向Boss、HUD恢复，无脚部镜头。

实测录制均值49.67FPS、出场段40.14FPS、p95帧25.62ms；这次没有开展额外帧率优化，不能描述为锁定60FPS。新版预览 build/offering-preview/outsider-audio-camera-fixed.mp4，34秒、45,166,491字节，实际游戏PBO/NVENC画面，缺帧重复采集帧，未光流补画；音轨按正式OGG/增益/服务器时间混入，非系统声卡录音。FFmpeg解码校验34秒并检测音轨峰值-2.3dBFS。12秒单独试听 build/offering-preview/offering-new-sounds.wav，顺序为3种鞋声、响指、开门、关门。open_in_codex 返回queued，不代表用户已播放。

已备份旧6035e2d...版本并安装 F:\AAAAmc\versions\1.20.1-Forge_47.4.20\mods\sourceofmystery-1.0.0.jar，新大小34,857,028字节，SHA256 0d8e91d00fb6b41da988d76e4dbc9df153068b1fb741c4ffed01529a4c1cb199，源和安装文件哈希一致，重启生效。记录 build/offering-audio-handoff-installation.json；旧包备份 build/release-backups/sourceofmystery-before-audio-handoff-6035e2d63202a8f09fe0474a348b0ef42e8632d17b6c920d73921f451f8e38ed.jar。发布检查 build/offering-audio-handoff-release-checks.json 确认所有动作/网格逐字节保持上一版、音频源与包内一致、测试类未进正式JAR。未提交git，未回退或覆盖既有其他开发改动。

## 按用户要求回退本轮飞行物理尝试（2026-10-25）

用户明确要求“回退本次改动”，并再次强调问题发生在出场动画结束后 Boss 常态飞行时的头发和衣服物理，不接受把出场动画当作修复对象。已立即停止本轮尝试，恢复 OutsiderDynamics.java、OutsiderRenderer.java、SpringBoneSolver.java、OfferingMovieCapture.java、run_oculus_smoke.py 至本轮开始时逻辑，删除本轮新增 OutsiderFlightSmokeCheck.java、SpringAttachmentCheck.java 及其隔离测试路线、预览和临时报告。没有安装本轮飞行物理包；实际游戏 mods 始终保持上一轮音效/镜头修复版。

回退后 build、shaderCheckJar 构建成功，build/flight-rollback-build.log。逐条比较重建正式JAR与已安装的上一版，所有条目内容完全一致；build/libs 正式包已同步回上一版原文件。当前正式版 SHA256 仍为 0d8e91d00fb6b41da988d76e4dbc9df153068b1fb741c4ffed01529a4c1cb199，保留此前声音和末帧镜头修复。记录 build/flight-rollback-verification.json。常态飞行物理问题当前未解决，不能将已回退尝试描述为生效修复。后续必须直接复现动画结束后真实常态飞行状态，再处理其头发、衣物，不继续修改或优化出场动画。

## 出场结束后真实常态飞行的头发、衣物物理修复（2026-10-25，当前已安装）

在用户要求回退上轮尝试后，用户重新授权“继续修”。本轮明确处理完整出场动画结束后，同一只 Boss 使用原有 AI 日常巡游时的头发、衣物高频翻摆。没有重新应用已回退的 Renderer/共用物理求解器/摄像机时钟改动。

本轮仅更改 OutsiderDynamics，并新增 OutsiderFreeFlightSpring；新的挂点物理严格由 !cinematic && combatState()==AIR 启用。出场、战斗以及其他状态继续走原有 SpringBoneSolver。根因是小尺寸 PMX 控制点被整段飞行平移反复拖出角度约束，同时一些原本就在身体胶囊内的控制点被碰撞投影强行推走，导致约束边缘来回翻转。新分支将尾端及其历史位置随挂点平移，保留旋转和风力的小幅随动；碰撞半径以原始控制点可达到的间距为上限，避免不可同时满足的碰撞/锥角；限制约束修正带入下一子步的速度。正常巡游风力*.30，头发刚度*1.7/阻尼*1.5，衣物刚度*2/阻尼*1.8；最大附加角头发.085rad、衣物.060rad、短饰件.045rad。进入/离开 AIR 重置对应物理，避免旧状态能量遗留。未禁用物理，仍有可测量的细微摆动。

测试使用新增 src/shaderTest/.../OutsiderPostIntroFlightCheck.java，命令 tools/outsider/run_oculus_smoke.py --post-intro-flight。真实开启14块黑曜石祭品召唤，等待原完整出场播放和摄像机归还；随后才对同一 Boss 录制34秒。Boss 的 AI、路线、朝向、位移和动作均没有测试覆写，只调整隔离测试观察玩家的视角；固定实体随机种子以便对照。记录显示自然巡游约32.7格、自然转向约403度，并经历 AIR/DESCEND/LAND/GROUND/TAKEOFF；物理指标只在 AIR 状态统计。明确区别于出场动画测量，不能把它称为出场优化。

真实对照：build/post-intro-flight-smoke/post-flight-before，原0d8e91d版本在完整出场后常态飞行，头发/衣物附加摆角9.1675/10.3135度、最大挂点位移0.1242/0.2403格；附加摆动角速度p95=563.715度/秒，最大1031.291度/秒。修复后 post-flight-fixed-shaders：头发4.6172度、衣物3.2408度、位移0.0589/0.0725格、角速度p95=20.2866度/秒（下降96.40%）、最大124.228度/秒。关闭光影 post-flight-fixed-vanilla：头发4.6951/衣物3.3093度、位移0.0618/0.0752格、p95=21.8201度/秒、最大168.781度/秒。两次均真实完成原出场再观察34秒，正常退出、GL检查通过。统计和比较 build/post-flight-comparison.json，不能将这些统计描述为用户所有场景均无任何残余摆动。

tools/outsider/FreeFlightAttachmentCheck.java 以实际新包装求解器测试15/30/60/144FPS、0.12秒卡顿、暂停重复帧、带身体碰撞的短/长骨，角速度最大43.63度/秒、最大偏角4.8702度，验证没有冻结随动；记录 build/post-flight-attachment-check.json。原 SpringBoneCheck 同时通过。build/post-flight-fix-build.log 构建/重混淆通过，build/post-flight-assets.log 完整资产回归通过；本轮未改服务器代码，没有重复服务器GameTest。

发布包逐条对比上一版：只有 com/sourceofmystery/client/OutsiderDynamics.class 改变，新增 OutsiderFreeFlightSpring.class；出场导演、原 SpringBoneSolver、Renderer、所有动作/模型/贴图/音效/服务端类均逐字节一致，测试类未入正式JAR。证明记录 build/post-flight-release-checks.json。

预览 build/post-intro-flight-preview/outsider-normal-flight-after-intro.mp4，34秒、43,822,007字节，无音轨；从原出场结束后开始，拍摄真实 Boss AI 自行巡游，非新出场动画、非动作路线代演。实际PBO/NVENC采样，60FPS容器不足时间槽重复实际采样帧，无光流补帧。光影录制1448实际渲染帧/34秒，不能声称锁60FPS。open_in_codex 返回queued，不代表用户已观看。

已安装 F:\AAAAmc\versions\1.20.1-Forge_47.4.20\mods\sourceofmystery-1.0.0.jar，34,859,071字节，SHA256 9e2f00ad8f735cd6c7fc379e97ed8f4070d8a89f8e1e3c0018288590d64e2f52；源和安装文件哈希一致，重启生效。备份 build/release-backups/sourceofmystery-before-post-intro-flight-fix-0d8e91d00fb6b41da988d76e4dbc9df153068b1fb741c4ffed01529a4c1cb199.jar，安装记录 build/post-flight-installation.json。既有声音、末帧交接修复保持，未提交git，未覆盖其他开发改动。

## 黑暗次元祭品升级为 GeckoLib 高精模型及可放置观赏方块（2026-11-05，当前已安装）

用户要求祭品使用 GeckoLib 高精3D模型，可落地作为观赏方块，空手及任意工具均可挖回。用户通过问答明确选“外观优先：更细的镂空骨架和晶体层次”。本轮应用 blockbench-use / blockbench-modeling / blockbench-texturing 的建模、UV和实时预算规范；当前会话没有 Blockbench MCP 工具，已向用户说明，通过原生 GeckoLib JSON 与生成脚本制作，并用真实游戏验证，没有假称用 Blockbench 工具导出。

原创造型：一格内黑曜石符文底座、银色曲面支爪、金银色交错双环、青色/紫色嵌线、悬空紫色16面尖晶核及小晶簇。409个Cube、18个骨骼、2374个导出四边面；512×512原创材质图与发光遮罩，普通表面8像素/模型单位，晶核16像素/单位。晶核用真实倾斜在空间的16个切面搭配紧凑三角形alpha遮罩构成体积，不使用层叠立方体造成阶梯条纹。渲染为cutout+发光，不依赖PBR；静态展示，不添加服务端tick计算。最初预览发现坐标转换方向导致环段不连接，已按 GeckoLib 原始导入坐标修正X/Z旋转符号，完整游戏截图确认环段连接、主晶核尖角与切面清楚。

生成脚本 tools/offering/generate_offering_model.py；运行资源在 assets/sourceofmystery/geo/block/dark_dimensional_offering.geo.json、textures/block/dark_dimensional_offering{,_glowmask,_particle}.png、animations/block/dark_dimensional_offering.animation.json（静态无控制器）。可编辑几何、贴图、预算和说明拷贝在 art/dark_dimensional_offering/；该目录受现有art忽略规则影响，不要声称已提交版本控制，源生成器与运行资源可复现。未使用外部版权图像。

DarkDimensionalOfferingItem 保持原 sourceofmystery:dark_dimensional_offering 物品ID，改为 BlockItem + GeoItem；新增同ID方块与方块实体注册，手持/GUI/掉落及落地显示共用 DefaultedBlockGeoModel 和 AutoGlowingGeoLayer。DarkDimensionalOfferingBlock 水平朝向、非完整不透明方块、亮度6、硬度0、无正确工具门槛；生存空手/木棍/木镐/斧头均立即破坏，loot table 掉同一个祭品1件。ModItems 原注册属性（单件堆叠、史诗、耐火）及唤魔者30%掉落不改；旧物品不需要重新获取。物品右键服务端先尝试原仪式，成功则消耗祭品并召唤，不放置观赏方块；否则按正常 BlockItem 放置。客户端对黑曜石点击不预先摆方块，交给服务器决策。中英文方块名及放置/召唤说明已补充。原出场导演、Boss飞行物理、动作、模型、语音音效和仪式类均未改。

验证：build/offering-gecko-validation.log 的 build、shaderCheckJar、runGameTestServer 全部通过，4项必需GameTest通过（新增 OfferingDisplayGameTests.placeAndRecoverWithAnyTool，原有门框/实体/法杖回归仍通过）。新测试用真实生存 FakePlayer 调用物品 useOn 放置，检查消耗1件、正确方块实体、硬度/破坏进度；分别空手/木棍/木镐/铁斧经 ServerPlayerGameMode.destroyBlock 挖掘，确认掉落精确1件且不要求工具。最终模型美术修正后仅资源变化，build/offering-gecko-final-build.log 重建通过，不重复服务器代码测试。

真实客户端通过 tools/outsider/run_oculus_smoke.py --offering-display --label offering-display-final-shaders，以及 --offering-display --vanilla --label offering-display-final-vanilla。每次均渲染第一人称手持、落地近景、另一侧全貌、GUI图标；调用真实已连接 ServerPlayer 手持祭品右键完整14黑曜石门，验证栈消耗、Boss进入原出场、没有误摆观赏方块。两套配置返回 OFFERING_DISPLAY_OK、正常退出，GL无错。主预览 build/offering-display-smoke/offering-display-final-shaders/screenshots/03-model-back.png（无UI全貌）；01-placed-and-held.png 同时看手持与落地。已查看这些截图，open_in_codex 返回queued不代表用户已查看。

包内资源与最终源资产一致，新测试类没有进入正式JAR；与安装前9d031022...包逐条比较，Boss mesh/所有动作/音频/OfferingPortalRitual.class 保持逐字节一致，记录 build/offering-gecko-release-checks.json。原资产综合回归 build/offering-gecko-existing-assets.log 通过。

已备份并安装到 F:\AAAAmc\versions\1.20.1-Forge_47.4.20\mods\sourceofmystery-1.0.0.jar，新35,692,287字节，SHA256 89d8ac5df82f8aeea8eb92c31131b497eacef2bbb0eaea8a224ec4b9fa32ebde；源与安装文件一致，重启生效。安装记录 build/offering-gecko-installation.json。备份 build/release-backups/sourceofmystery-before-gecko-offering-9d03102288def7314e853ae4f950ca797320434ff6f67e7ab22343f59fba1fc8.jar。本轮开始时已有 chat_history.md 的修改，未覆盖；未提交git，未修改或回退其他窗口的开发成果。

## 黑暗次元祭品：虚空馈赠抽奖（2026-11-05，已安装）

新增原生 Minecraft 容器界面，上方连续滚动物品条、金色固定指针，下方一个投入格及玩家物品栏。手持对空气右键或右键落地祭品打开；地面放置及有效14格黑曜石门召唤的既有优先级保留。每次确认消耗投入格整组物品和固定1000点神秘之能，产出默认实例的物品1件。客户端按渲染时间平滑减速约5.5秒，服务端负责估价、随机、扣费、最终发奖。价值高于投入整组总值时播放原版升级+水晶音，低于时播放低音+迪吉里杜低沉音，相等为短钟音；1000能量不折算到物品价值。沿用原生音效，没有修改此前Boss出场音频。

实现：offering/OfferingMenus、OfferingMenu、OfferingValues、OfferingEscrow、OfferingLotteryConfig；client/OfferingLotteryScreen；network/OfferingRollPacket。注册菜单与屏幕、添加中英文文案，网络协议5升6，联机两端需一致。面板320×314，低GUI高度按比例适配，鼠标坐标与裁剪同步转换；真实客户端验证按钮鼠标点击及GUI缩放3的小窗口完整显示。

自动奖池遍历服务端注册表，包含当前世界启用的非空气物品类型；不按投入档位屏蔽物品，每个候选都有正概率。根据品质、工具/护甲属性、食物、公共材料标签、原版矿物锚值和六轮有界配方推导自动估价，不能解析的动态配方保留属性估价。数量/实际耐久/附魔影响投入总值；自定义名称、任意NBT大小和容器内容不额外计价。奖池是物品类型，不枚举所有NBT变体/附魔组合。配置世界 serverconfig/sourceofmystery-offering-server.toml 的 valueOverrides 可指定其他模组单件估值，数据包全服同步/重载时失效重建。快照按服务器缓存，不逐帧扫描。权重公式及玩家操作在 docs/OFFERING_LOTTERY.md。

每位玩家使用独立投入格，不共享方块库存。空格、能量不足、失效方块/超距、重复点击均不会产生额外扣费；已付款的奖品先写玩家persistentData，再播放滚动。未付费关闭退回投入；已付款关闭立即发同一个奖品，不能通过关界面重抽。满背包掉落并设置玩家拾取归属；登录/重生恢复未结算投入/奖励，Clone在神秘之能数据复制后清除旧实体托管数据。客户端只发原生菜单按钮请求，不能指定价值或结果。

验证 build/offering-lottery-validation.log：build、shaderCheckJar、runGameTestServer 成功，6项必需GameTest全部通过。新增抽奖测试覆盖全部启用类型入池、低/高投入每个候选均正概率、提高投入提高期望价值、数量估价、空格/999能量不扣、恰好扣1000和整组、重复点击、提前关闭/定时完成各发1次、玩家隔离、玩家数据/Clone恢复、满背包实物掉落、方块消失菜单失效。原有4项实体/门框/法杖/祭品放置挖掘回归继续通过。

真实生产Forge客户端：tools/outsider/run_oculus_smoke.py --offering-lottery --label lottery-shaders-final；--offering-lottery --vanilla --label lottery-vanilla-final。原生C2S方块交互、Shift投入、菜单按钮、服务端发奖和PlaySoundEvent确认两种声音均通过，并验证 lotterycompat 独立命名空间、无兼容代码的普通/史诗测试物品进入奖池且概率为正。普通渲染路线另验证手持空中右键打开、999能量的原生请求被拒且保留投入。最初光影测试因测试固定种子导致两轮相同提示token、第二次音效被去重而失败；仅修正隔离测试的不同轮次种子，保留失败日志，不将其描述为生产崩溃。

最后追加小窗口自适应后 build/offering-lottery-responsive-build.log 成功；最终真实光影客户端 --offering-lottery --label lottery-responsive-shaders 再次完整通过，包含鼠标点击抽取、两种音效、手持开界面、能量不足及GUI缩放3完整显示。图像已人工查看，GL无错误。正式包全部条目与最终测试包内同名条目一致，测试类/lotterycompat资源未进入正式包。主预览 build/offering-lottery-smoke/lottery-responsive-shaders/screenshots/01-ready.png；03-win.png（4投入→17奖品）、04-loss.png（2359296投入→17488暗源之甲）、05-insufficient.png（小窗口999能量保留圆石）。这只是自动系统估值下的盈亏，不能据品质颜色判断。

tools/offering/verify_lottery_release.py --install 已检查旧包到新包仅改动预期的9个既有条目（注册/交互/语言），新增抽奖类；168项既有网格、贴图、动作、音频以及其他原有类逐字节保持。正式包35,729,047字节，SHA256 b6e0a932a64c2dae2932bce20124d0d50c66eee53d03007f01a5c4e98b242d26，已安装 F:\AAAAmc\versions\1.20.1-Forge_47.4.20\mods\sourceofmystery-1.0.0.jar，源与安装哈希一致，重启游戏生效。备份 build/release-backups/sourceofmystery-before-offering-lottery-89d8ac5df82f8aeea8eb92c31131b497eacef2bbb0eaea8a224ec4b9fa32ebde.jar；审计/安装记录 build/offering-lottery-release-checks.json。未提交git，未覆盖既有 chat_history.md 或此前未提交成果。

## 秘源附魔台、整合包稀有度兼容修复和幸运抽奖（2026-11-05）

用户新增类附魔台，合成要求为 MSM / DED / OOO，其中M秘源锭、S下界之星、D钻石块、E附魔台、O黑曜石。用户明确选择“自动计价，可配置”。新增 mystery_enchanter 方块、BlockEntity、GeoItem、菜单、屏幕、创造页和工作台配方。外观按此前明确的外观优先偏好，使用 Blockbench 建模/UV/纹理规范；会话无Blockbench MCP，直接制作GeckoLib原生文件并告知用户。tools/enchanter/generate_enchanter_model.py 可复现黑曜石阶梯底座、秘源银色框架、钻石四角、镂空托架、真实立体下界之星，96Cube、504四边面、512atlas+glow、8px/模型单位，静态、无BE server ticker。art/mystery_enchanter下为可编辑geo及预算说明（art受已有忽略规则影响）。手持/物品栏/落地方块共用模型，钻石级镐采集掉自身1件。

enchanter/EnchanterRules 按 Forge 完整附魔注册表索引，含模组、宝藏和非随机附魔；保留作者canEnchant、isAllowedOnBooks、互斥和原生等级限制，最大255。右侧搜索名称/ID，选择等级、附魔、单条退魔和全退魔。每次单格1件，Shift堆叠会分1件；普通书变附魔书，全退魔回普通书，诅咒可移除；不耗XP/青金石/书架。费用默认1000×稀有度权重系数×等级平方差，退魔100×系数×当前级；全退魔求和。系数10/rarity.weight截断到1..16。配置 serverconfig/sourceofmystery-enchanter-server.toml。未知附魔NBT在单条操作中保留，只有全退魔才删除；名称、耐久、自定义NBT不改。EnchanterEscrow用玩家根persistentData保存单人物品，闭界面/断线/Clone恢复；满背包掉落并设置归属。所有选择、费用、资格和修改由服务端执行。重复附魔同一级、冲突、无可退条目、能量不足不收费。协议升7。

用户实测祭品右键崩溃，附件 d91730ee-e47a-4876-b506-ea59cfa4a1c8/已粘贴的文本.txt 及实际 F:\AAAAmc\versions\Integrated MC\crash-reports\crash-2026-11-05_15.17.30-server.txt 指向 OfferingValues.base:75 的 IncompatibleClassChangeError。根因是穷尽switch假设Rarity只有四项；Forge Rarity实现IExtensibleEnum，整合包新增枚举导致编译器default抛ICCE。已改可扩展if判断和保底估值；物品/配方/默认实例钩子对RuntimeException及LinkageError有隔离，不吞OOM等VM错误，不移除这些物品。新增真实第五种Rarity和恶意属性/default钩子GameTest，7项测试先通过。用户已回复“已退出游戏”以释放内存做完整隔离测试，未自行结束用户进程。

实际主实例现在是 F:\AAAAmc\versions\Integrated MC（Forge47.4.28），原1.20.1-Forge_47.4.20也保留。tools/offering/run_integrated_compat.py 复制config/kubejs/resourcepacks，310个模组JAR只读硬链接到项目build，使用独立离线用户和全空世界，不读启动器账户，不修改用户存档。首次仅稀有度热修复真实整合包开界面、投入扩展稀有物、抽奖通过：22,251候选、33种Rarity、186扩展稀有物，最小概率2.40406740868549E-10，扣1000。记录 build/offering-integrated-compat/rarity-hotfix/compatibility.json；纯热修复归档在 build/offering-rarity-hotfix，后续最终综合测试复用同隔离目录和warm cache，launch.log会被最终运行替换。

用户追加“好东西几率提升10%”与“幸运buff影响”。已向用户明确为相对提升10%，好物定义为奖品实际默认实例价值严格大于整组投入总值。候选Entry估值含默认NBT/附魔，先算原权重，再分好物/其他组重分配，让好物总概率×1.10，组内相对权重保留。幸运系数 max(.1,min(100,1+玩家Attributes.LUCK×.10))，包括原版幸运/霉运和模组装备幸运；幸运I再×1.10。概率接近1时保留其他候选的正概率，不造出奖池中不存在的更贵物品。配置 goodRewardChanceMultiplier=1.10、luckBonusPerPoint=.10；菜单同步服务端系数，界面显示“幸运+10%”“好物+10%”。每次1000点费用、按价值盈亏播放两类声音保持。

build/enchanter-luck-validation2.log：build/shaderCheckJar/runGameTestServer成功，10项全部通过，包含附魔差价、重入、冲突、NBT保留、未知标签、诅咒、书转换、精确配方、放置采掘、扩展稀有度及原有所有回归。真实原版LUCK/UNLUCK效果概率测试：基线0.3923462645938458，+10%后0.4315808910532344，幸运I后0.47473898015855726，霉运0.38842280194791495；测试极高/极低幸运后每个候选仍正概率。首轮资源构建发现手工mineable标签与generated重复，已合并到generated并更新ModBlockTagsProvider，未设置忽略重复掩盖错误。

最终独立客户端 --enchanter --vanilla --label enchanter-verified 通过，build/enchanter-client-build.log构建测试包。真实鼠标搜索、选择外部命名空间enchantcompat的宝藏且不可发现附魔、满级、附魔45000点、退魔1500点、自定义NBT/耐久保留，以及原生幸运I的抽奖UI和一次开奖均通过。enchanter-verification.json记录40类附魔。六张截图已查看，02-table-close.png为模型，03-enchantment-menu.png为界面，05-lucky-offering.png同时显示两项+10%。初次enchanter-final测试因测试场景高空传送未清fallDistance导致测试玩家摔死、流程超时，已只修测试场景清fallDistance并关闭隔离世界摔落伤害，重新成功，未修改正式世界的伤害规则。

最终完整整合包运行 python tools/offering/run_integrated_compat.py --mode enchanter --artifact build/reobfShaderCheckJar/output.jar --label rarity-hotfix，16:12:28返回 ENCHANTER_SMOKE_OK，正常退出。环境为310个原模组JAR（本模组替换测试版），所有原配置/脚本/资源包的隔离拷贝；读取218类附魔（含1种enchantcompat测试类型，正式包不含该类型）、奖池22,252物品（含2种lotterycompat测试物品）。实际鼠标搜索、选择外部附魔、升三级、支付45000附魔、支付1500退魔并保留NBT、切到祭品界面携带原生幸运I、显示“幸运+10%/好物+10%”、支付1000抽奖和最终发奖全部通过。enchanter-verification.json及launch.log、六张截图在 build/offering-integrated-compat/rarity-hotfix；已查看完整整合包界面及幸运界面。正式包不含测试类/测试附魔/测试物品，所以不能向用户把218描述为正式包新增附魔数量。

tools/enchanter/verify_enchanter_release.py --install 已确认：18个既有条目为预期注册、费用、稀有度/概率、界面、标签变化；此前168项模型、材质、动画、音频保持逐字节一致，其余既有类未改；工作台配方为精确指定材料；新模型源资产与正式JAR一致；正式JAR每个条目与真实测试JAR相同，所有测试资源/类均未打入正式JAR。审计和安装记录 build/enchanter-luck-release-checks.json。

已同时安装 F:\AAAAmc\versions\Integrated MC\mods\sourceofmystery-1.0.0.jar 和 F:\AAAAmc\versions\1.20.1-Forge_47.4.20\mods\sourceofmystery-1.0.0.jar，两者及 build/libs 正式包哈希完全一致：35,989,142字节，SHA256 0620b93e0d9893a290380bb21cb0f1bcd2ff6f2f70ae71e18c869954a9b558ab。旧b6e0...包保存在 build/release-backups/sourceofmystery-before-enchanter-and-luck-b6e0a932a64c2dae2932bce20124d0d50c66eee53d03007f01a5c4e98b242d26.jar。用户已退出游戏，下一次启动生效；没有修改真实世界存档及配置，没有关闭用户进程，没有提交git，也没有覆盖既有chat_history.md修改。主外观预览 build/enchanter-smoke/enchanter-verified/screenshots/02-table-close.png，完整整合包幸运预览 build/offering-integrated-compat/rarity-hotfix/screenshots/05-lucky-offering.png。


## 神威天佑神环挂载与文字方向修复（2026-11-05，当前已安装）

用户要求修复神环固定在世界一侧、应作为胸甲模型外置装饰的问题，并明确“神”字为上下颠倒。根因是 RenderPlayerEvent.Post 已离开 LivingEntityRenderer 的身体朝向、缩放、模型坐标翻转等父矩阵；旧实现再叠 body 骨骼无法恢复整套变换，且旧注释错误地把模型正Y写为朝上。DivineBlessingHaloRenderer 已改为真正 RenderLayer，在父模型矩阵内挂接 HumanoidModel.body。ClientModEvents 用 EntityRenderersEvent.AddLayers 给正常/纤细玩家与盔甲架各注册一次，移除旧全局Post事件监听。背部局部偏移Y=.35、Z=.45，保持四层旋转、中心发光和装备材质判断。V=0对应模型-Y，背后阅读的U反向，修正上下颠倒和后视镜像；原五张神环贴图逐字节保留。删除第一人称相机全局跳过，背包纸娃娃可显示。

build/divine-halo-build.log 的 build/shaderCheckJar 成功；补充测试场景后 build/divine-halo-client-build.log 测试包重建成功。真实普通客户端 tools/outsider/run_oculus_smoke.py --divine-halo --vanilla --label halo-vanilla 与光影客户端 --divine-halo --label halo-shaders 均返回DIVINE_HALO_OK并正常退出，后者日志确认ComplementaryReimagined_r5.9.3已启用。测试使用真正PlayerRenderer输出顶点，验证正常/纤细/盔甲架注册一次、身体转90度后神环跟转、保留调用方平移、图像上缘高于下缘、后视左右方向、潜行躯干倾斜、卸下胸甲和穿其他胸甲隐藏。最终测试包额外实际渲染本地玩家，确认第一人称相机时纸娃娃装饰不被误隐藏。各六张实际截图已查看；普通第一轮侧面转后机位被测试盔甲架挡住，已仅移动测试盔甲架，最终光影截图03/04/05清楚展示转后背面/潜行/卸甲。正式代码未因测试场景调整而变化。

完整310 JAR整合包隔离复测 tools/offering/run_integrated_compat.py --mode halo --artifact build/reobfShaderCheckJar/output.jar --label divine-halo 已启动。用户随后明确“测试通过，不用在再测试了”，因此按要求停止此隔离Java进程PID42048；没有启动新测试。日志截至16:42:56.686仍为普通运行记录，无新crash-reports或hs_err_pid文件；脚本因子进程被外部中止，抛出Full-pack test failed断言。用户随后报告该窗口“崩溃”，已核对其发来的debug.log/latest.log正是本隔离实例并说明人为停止原因。不得把这次整合包神环复测记成通过，也不得将脚本中止断言当作Minecraft崩溃根因；状态记录build/offering-integrated-compat/divine-halo/test-cancelled.json。已有整合包网络请求、资源缺失、JEI加载耗时错误日志存在，但中止前没有神环渲染崩溃证据。

发布审计 tools/divine/verify_halo_release.py --install 已完成，build/divine-halo-release-checks.json证明对上一版0620b93e...只有ClientModEvents.class与DivineBlessingHaloRenderer.class两条变化，其余正式包条目包括270项assets完全一致；正式类与真实测试包一致，测试类未入正式包。两实例F:\AAAAmc\versions\Integrated MC\mods\sourceofmystery-1.0.0.jar与F:\AAAAmc\versions\1.20.1-Forge_47.4.20\mods\sourceofmystery-1.0.0.jar均已安装，35,989,271字节，SHA256 657c999f209b7b23c94408b362a70d92858575d6686277f5623243a65069ecc1。备份build/release-backups/sourceofmystery-before-halo-0620b93e0d9893a290380bb21cb0f1bcd2ff6f2f70ae71e18c869954a9b558ab.jar。未改用户真实存档和配置，未提交git，保留chat_history.md和之前功能改动。重启真实实例加载新JAR。


## 神秘之能自然恢复改为每10秒1%（2026-11-05，当前已安装）

用户明确要求“每10s恢复当前玩家神秘之能上限的1%”。MysteryEnergyEvents 将自然恢复从每20tick触发改为每200tick触发（正常20TPS为10秒），仅服务端END阶段执行，恢复时读取玩家当时的上限。MysteryEnergy.regenerate 原有小数累计、上限封顶、不提高上限以及服务端同步保持。例：上限10000，每10秒恢复100；小于1点的结果保留到之后的恢复周期，不直接舍弃。每日回满机制及其每秒跨日检查保持原状。

MysteryConfig 新命名ENERGY_REGEN_FRACTION，旧公共Java字段ENERGY_REGEN_PER_SECOND作为同一ConfigValue的Deprecated别名保留；TOML旧键energy.regenerationPerSecond保留兼容，值仍为0.01，但注释明确实际每10秒生效。两个真实实例配置均同步此说明和值0.01，其余配置未改，配置原文件分别备份在build/release-backups/Integrated MC-common-before-energy-regen.toml与1.20.1-Forge_47.4.20-common-before-energy-regen.toml。

遵守用户此前“不再测试”的要求，本轮只执行Gradle assemble离线编译打包/重混淆，build/energy-regeneration-build.log记录BUILD SUCCESSFUL；没有启动客户端、服务器GameTest或完整整合包。tools/energy/release_regeneration.py完成发布包逐条比对、配置更新及双实例安装。相对上一版神环包只变更MysteryConfig/MysteryEnergy/MysteryEnergyEvents三类，以及配置源文件插入行导致重编译的两个内部类；两个内部类经javap -p -c -s确认运行字节码/API完全相同。其余所有正式包条目（包括神环修复、Boss、祭品、抽奖、附魔台及全部资产）逐字节一致。初次发布审计因新增行号引起两个内部类变化而中止在写入前，补充确认内部类字节码不变后才安装，没有跳过白名单验证。

当前build/libs与两真实实例mods/sourceofmystery-1.0.0.jar哈希一致：35,989,446字节，SHA256 1d07c7ff6a55987d1a65920f60c3beb9eec6c6738acab840d575e6a3d3f12661。旧657c999f...包备份build/release-backups/sourceofmystery-before-energy-regen-657c999f209b7b23c94408b362a70d92858575d6686277f5623243a65069ecc1.jar。完整安装记录build/energy-regeneration-release.json。重启游戏加载新逻辑；不能宣称本轮实机测试通过。未提交git，未覆盖chat_history.md修改。


## 神威天佑升级GeckoLib战神混沌胸甲、太极及贴甲流光（2026-11-05，当前已安装）

用户要求将穿戴模型升级GeckoLib，风格为战神铠甲、混沌纹理，偶尔黑白神光贴甲流动。过程中先要求“不要一体，就胸甲”，随后明确最新口径：“虽然只是胸甲物品，但是穿戴效果允许有胸铠、护肩、臂铠和腰甲连成一体的穿戴外观，腰甲随腿部活动。黑白神光会短暂贴着现有甲面流过”。最新口径优先，最终采用完整穿戴外观，但只保留原胸甲物品/槽位。用户另指定中心菱形改太极，已替换为带金属圆框的黑白双鱼，不能回退成菱形。应用blockbench-use/modeling/texturing技能的预算、UV和导出规范；继承外观优先偏好，本会话没有Blockbench MCP连接，已说明通过本地程序生成原生资源与可编辑源文件，没有假称用Blockbench工具导出。

新增DivineBlessingChestplateItem extends MysteryChestplateItem implements GeoItem，原注册ID不变，原材质DIVINE_BLESSING、+200生命及4d22fa12-dc4f-45ea-a2dd-2c94cb7defdb UUID、无限耐久、各效果处理器全部保留。只在客户端惰性创建GeoArmorRenderer，原物品图标、配方、神环挂载代码和资产原样保留。Geo模型158个Cube、6骨骼、943四边面/1886三角形；根骨armorBody/armorRightArm/armorLeftArm/armorRightLeg/armorLeftLeg，taiji_emblem子骨挂到armorBody。肩臂随手臂，腰甲分别随左右腿，渲染器对胸甲槽启用腿骨可见性，避免原生胸甲槽隐藏腿骨后腰甲消失。没有新增头盔/护腿/鞋子物品，也没有骨骼物理或服务端ticker。

战甲为黑色混沌涡纹、银色包边、旧金嵌线，分层胸背铠、护肩、臂铠、腰甲；太极黑白图案原创程序绘制，16段真实厚度金属边框。单512基础图、两张512流纹，不依赖PBR。基础UV固定8像素/模型单位，太极图案有意16像素/单位；全部943面UV比例/边界检查。黑白神光用同一已摆姿盔甲网格重绘、专用可平铺UV移动，13.6秒周期中约3秒短暂出现，连续partialTick时间与平滑淡入淡出，不同玩家UUID错开相位。黑层普通半透明暗影、白层eyes发光；没有漂浮粒子。太极透明圆形面片单独排除流光重绘，try/finally恢复骨骼可见性，避免滚动流纹填充圆图的透明方形角、遮挡双鱼。

tools/divine/generate_divine_armor.py重建运行资源与art/divine_blessing_armor/的bbmodel、贴图、parts.json、asset-plan.json。bbmodel为标准Bedrock逐面UV可编辑源，组树与骨骼一致；该art目录受现有Git忽略规则影响。tools/divine/preview_divine_armor.py直接从同几何和UV离线生成正/背/侧/神光/方格UV/摆腿六张CPU静态预览，均已查看。静态图不是游戏截图，也不是动作播放验收；用户可见的旧纯胸甲/菱形预览已被同路径最新模型覆盖。最终图art/divine_blessing_armor/preview_front.png与preview_flow.png均为1200×1200。说明docs/DIVINE_BLESSING_ARMOR.md含接口来源链接与验证边界。

build/divine-armor-final-build.log记录最终assemble成功；仅编译、打包、重混淆与静态美术检查，尊重用户“不再测试”，没有启动游戏客户端、服务器GameTest或整合包。不能宣称实际穿戴/步态/光影已实机通过，运行时帧率未知。tools/divine/release_divine_armor.py --install发布比对：相对上一1d07c7ff...正式包，只有ModItems.class这一既有条目改变，新增6个装备/渲染类与7项美术资源，其余既有条目及270项assets逐字节一致。确认之前神环、Boss、祭品、附魔台、抽奖和神秘之能每10秒恢复1%逻辑保持。

已安装两真实实例F:\AAAAmc\versions\Integrated MC\mods\sourceofmystery-1.0.0.jar与F:\AAAAmc\versions\1.20.1-Forge_47.4.20\mods\sourceofmystery-1.0.0.jar，与build/libs正式包完全一致：36,277,772字节，SHA256 7b7a70bdee9d6f3a949949edd41eb76e570ab3031e681f6da6e8e0dd0382d2ed。备份build/release-backups/sourceofmystery-before-gecko-armor-1d07c7ff6a55987d1a65920f60c3beb9eec6c6738acab840d575e6a3d3f12661.jar。完整安装记录build/divine-armor-release-checks.json。重启游戏生效，旧胸甲不需重新获取。未提交git，保留chat_history.md等既有工作改动，未修改真实游戏存档与配置。


## 神威天佑肩部穿模修正（2026-11-05，当前已安装）

用户提供真实穿戴截图C:\Users\ADMINI~1\AppData\Local\Temp\0168e982-7180-478e-adc5-d49fdb02469e.png，俯视可见护肩内侧露出皮肤肩部。核对旧模型：斜护肩内侧下沉，上臂内衬仅到Y23.3；本机Minecraft PlayerModel.createMesh袖层外扩0.25，原生肩顶在同一臂骨参考系可到Y24.25。之前无玩家身体的空甲预览不能观察这种穿模，不应因此前编译或预览成功就判定实际穿戴合格。

只调整generate_divine_armor.py中左右bicep_liner：中心X从±6.1改为±6，Y从20.4改为20.9；尺寸4.15×5.8×4.35改为5×7.1×5，顶部24.45包住肩顶和外层，表面取现有混沌金属材质0。仍挂原左右手臂骨骼，所有其他部件、太极、肩甲造型、腰甲、流光和装备能力保持，模型仍158Cube、6骨骼、943面。源bbmodel、parts、asset-plan与静态预览同步更新。

新增tools/divine/inspect_shoulder_fit.py离线几何覆盖检查，原生参照已通过本机映射依赖的javap核对（build/player-model-arm-fit-api.txt）。普通/纤细左右臂各3750肩部表面点，旧版未覆盖1196/1358/1196/1358点，修正后全部0；5种共同手臂刚性姿态包围检查通过，最小余量0.2模型单位。报告build/divine-shoulder-fit.json含模型源文件SHA防止复用旧结果。这是离线几何证据，不能当作实际Minecraft/MMD皮肤运行通过。预览器加入真实尺寸白色原生袖层示意，俯视preview_shoulder_before.png复现两边白色肩块，preview_shoulder_after.png显示混沌肩套完整覆盖，均已查看；这些是CPU模型预览而非用户游戏截图。此前用户要求不再启动游戏测试，本轮没有启动客户端、整合包或GameTest。

build/divine-shoulder-final-build.log记录最终assemble/重混淆成功。tools/divine/release_shoulder_fit.py --install审计：正式包相对7b7a70...仅assets/sourceofmystery/geo/armor/divine_blessing.geo.json一项变化，逐骨骼排除左右bicep_liner后整个模型JSON完全相同，所有其他正式包条目（所有类、全部纹理/声音、神环、10秒1%恢复等）逐字节一致。两实例mods中的正式包与build/libs一致：36,277,780字节，SHA256 3fd5ed68092364d959315c0e04f8c540fbce746844522548fa25eee90da7672a。安装使用同目录.updating临时文件后os.replace原子替换，没有原地覆写打开的ZIP句柄，也没有结束用户游戏进程。两实例均安装成功，无pending文件。

备份build/release-backups/sourceofmystery-before-shoulder-fit-7b7a70bdee9d6f3a949949edd41eb76e570ab3031e681f6da6e8e0dd0382d2ed.jar，安装记录build/divine-shoulder-release-checks.json。说明docs/DIVINE_BLESSING_ARMOR.md已补肩部修复和证据边界。重启游戏加载修正；未提交git，保留chat_history.md等原工作改动，未改真实存档与配置。


## 神威天佑首次穿戴登神 CG、用户王座灰白比例版（2026-11-05，当前已安装）

首次穿戴服务端单独通知穿戴者，其他人摄像头不调用。CG 玩家使用原版普通/纤细 PlayerModel 与本人皮肤、原有装备和附加渲染层；MMD 身体替换层跳过。真实玩家停留原地，客户端在高空舞台绘制宇宙地球、白色五阶、悬浮原神威天佑模型、王座。原始要求基础上按用户“史诗/荣誉/神圣感”延长至64.7秒：前4秒加速升空，环绕与近景、天阶揭示、拾级而上、对视触甲、强神光授甲、观察自身、凝视王座3秒，然后恢复。八组原创程序音效与宇宙合唱/钟鸣，授甲触发真实持久成就sourceofmystery:ascend_divinity及彩虹提示「登神」。

地球使用NASA官方Blue Marble 2048×1024原图（RGBA转换保留RGB），来源https://svs.gsfc.nasa.gov/2915/ ，附运行资源CREDITS。最初自制217 Cube王座已被用户否定，不得继续使用旧预览或运行generate_divine_throne.py覆盖新模型。现使用用户神秘之源素材/王座.zip中的madmaggie_throne_01_static_LOD0.obj.glb，SHA256 54d6aa4e2b458b9f2905e66677ffb417346336de95b7ac931eb5207b9266c925。原1713三角面/5139顶点、UV、原贴图字节保留，以GeoObjectRenderer/GeckoLib baked GeoQuad渲染。源压缩包没有许可文件，不称原创。

用户最新强调“椅子比人大太多”及“只能灰白色”，最终尺寸由高4.65米改为宽1.65×高2.30×深1.50米，高度约玩家1.28倍，收短座深/底座；非等比缩放对法线作逆转置。动态材质所有像素R=G=B：白框架、中灰座面靠背和深灰雕刻阴影，移除金与蓝染色。王座位置移到舞台Z=-7.7，距离玩家终点3.9米，最终机位朝向及头部俯仰同步适配。导入工具tools/divine/import_throne.py；源与报告art/divine_cinematic/imported_throne/。最终同框截图build/divine-cg-smoke/gray-throne-final/screenshots/10-throne.png。

持久首次标记divine_blessing_first_equip_v1，结束恢复摄像头类型/原视线/GUI/重力；真实坐标不变，动画期间禁止伤害和交互。Esc可跳过；OP2穿戴时/somdivinecg可重看。网络协议升为8，联机两端必须一致。原门框召唤仪式仅增加与该CG互斥守卫，原Boss导演/动作/物理/音效未变。异常重登恢复收据、会话超时/死亡/换维度/退出清理均实现，尚未逐项运行异常路径实测。

build/divine-cg-gray-throne-build.log离线assemble/重混淆成功。真实隔离客户端build/divine-cg-smoke/gray-throne-final/完整正常速度播放（seek_diagnostics=false），首次触发、真实坐标不变、摄像头/视线/GUI/重力恢复、第二次穿戴不重播、真实成就授予检查通过；10关键镜头及恢复截图，3829个渲染帧，中位16.8094ms，P95 17.8756ms，最大129.6666ms。环境Forge47.4.20+GeckoLib4.8.4+Embeddium+Oculus关闭光影；未进真实存档，未跑完整约310模组整合包，不能据此宣称全部饰品/MMD/光影均已运行通过。早期黑屏失败及旧椅子诊断保留，不当作当前验收。

最终游戏录制预览art/divine_cinematic/divine-ascension-gray-throne-preview.mp4，1280×800、60fps编码、约64.7秒，配完整原创音轨；全部视频帧/音频解码检查通过。记录build/divine-cg-preview-mux.log与streams.txt。编码60fps不等于无卡顿，实际帧时间以上述报告为准。

发布工具tools/divine/release_cinematic.py --install以肩部修正3fd5ed68...正式包审计，既有盔甲/神环/Boss/音频/能量10秒1%/抽奖/附魔台资产逐字节保留，语言及声音旧键、旧Mixin映射保留，只有CG接入点/注册、网络、材质重载与仪式互斥等预期已有类变化；测试和录像类不进入正式包。正式包与实际测试包的生产条目逐字节一致。双实例安装成功，无pending，原子替换并备份旧包，没有终止用户其他游戏或修改真实存档配置。

正式包SHA256：305aa53ace0ef89b0eaeead6b140de966370f868a1fabb0910024c908ee9f242，40583693字节。两目标：F:\AAAAmc\versions\Integrated MC\mods\sourceofmystery-1.0.0.jar、F:\AAAAmc\versions\1.20.1-Forge_47.4.20\mods\sourceofmystery-1.0.0.jar。完整审计安装记录build/divine-cg-release-checks.json，备份build/release-backups/sourceofmystery-before-divine-cg-3fd5ed68092364d959315c0e04f8c540fbce746844522548fa25eee90da7672a.jar。重启游戏生效；以前已有胸甲但尚无此首次标记的玩家，下次穿戴会触发，可用OP命令重看。说明docs/DIVINE_ASCENSION_CINEMATIC.md。未提交Git，保留chat_history.md等其他已有工作。


## 登神地表至轨道连续升空、后续测试静音（2026-11-05，最新已安装）

用户指出旧升空在高空后黑场直接进入宇宙，缺少地球过渡，另给出视频参考https://www.bilibili.com/video/BV1NZxv6rEwJ/，要求后续模组类似宇宙CG可学习。浏览器实际读到标题《轨道炮作者最新武器——SS-06 Gravitia》，但播放器截图/控制多次超时，元数据API返回HTTP412；未逐帧完整观看，不得称已复刻或凭标题编造镜头。链接与后续审美方向已保存在docs/DIVINE_ASCENSION_CINEMATIC.md。用户随后明确“下次测试不要开声音”：两个隔离启动器run_oculus_smoke.py及run_integrated_compat.py默认写soundCategory_master:0.0；不改真实游戏实例音量，不自动播放预览声音，之后其他测试启动方式也应落实静音。

升空改为8秒，总CG68.7秒；真实所在地摄像头先加速抬升并俯瞰地形，2.35秒起穿过双层流动云带，3.6秒云中衔接本地球体场景，持续拉远同一NASA地球、显出弧形地平线/蓝色大气薄边/渐现星空，最后转向玩家，8秒位置/视线/FOV与环绕机位连续。删除旧进入宇宙的黑场；云中仍有坐标衔接，没有把真实Minecraft世界弯成球。其后登阶、触甲、授甲、观察与王座定格保持，时间整体顺延4秒。八组原创音效与完整音轨同步68.7秒。王座继续宽1.65高2.30深1.50米，仅中性灰白，模型及材质相关类与305aa53a...版逐字节一致。

实现补充：地球用线性采样避免近景硬像素；单位球几何缓存，消除大气逐顶点临时Vec3分配；启动镜头前预载纹理与王座动态材质。新增仅客户端DivineMouseInputMixin，在原生MouseHandler消费鼠标增量之后、转动玩家之前阻止CG期间转向，避免结束恢复间隙被鼠标改变；最终停止再次恢复视线。网络仍协议8。原甲/神环/Boss全部音画、神秘之能每10秒恢复1%、抽奖、附魔台逻辑保持。

失败证据保留：earth-ascent-final在完整播放后因视线恢复断言失败；earth-ascent-polished功能通过但1.456秒单帧峰值，过渡截图明显迟到；earth-ascent-release在开头镜头迟到断言失败。发现录像器初始化发生在CG开始后，测试夹具改为穿戴前准备PBO/外部编码器；比较视线使用真正动画开始时的收据，角度按wrapDegrees比较，非提前一个服务端请求周期的快照。最终仍保留升空各关键镜头不能迟到超过0.5秒的检查，没有放宽为不检查。

最终build/divine-cg-ascent-silent-final-build.log assemble成功；真实隔离客户端build/divine-cg-smoke/earth-ascent-silent-final完整正常速度、静音播放通过，报告seek_diagnostics=false、test_client_master_volume=0，首次触发/真实坐标不变/相机GUI视线重力恢复/第二次不重播/登神真实授予/机位连续全部通过。14个关键镜头及恢复截图；地球弧面4.913秒、拉远6.407秒、轨道7.808秒，无关键过渡镜头跳过。4020帧，中位16.7026ms，P95 18.6122ms，最大814.8904ms，仍有个别停顿，不能宣称全程无卡顿或把60fps编码当实际帧率。仅Forge+GeckoLib+Embeddium+Oculus关闭光影，未跑完整整合包/多玩家/实际所有饰品MMD和异常重登路径，边界同CG说明。隔离测试世界为有草地表面的测试地图，未使用用户存档。

已导出完整预览art/divine_cinematic/divine-ascension-earth-ascent-preview.mp4（68.7秒、1280x800、60fps编码，保留原创音轨），前10秒art/divine_cinematic/divine-earth-ascent-first-10s.mp4。完整预览所有视频帧与音频解码通过，报告build/divine-cg-ascent-preview-checks.json；旧gray-throne-preview.mp4为历史64.7秒版，不能当最新版。未自动播放任何预览声音。

最新正式包SHA256 c5f6a2b6a8e1ee19e4073e30f9a4d4217d2f9b3b18b43933fa494a60d5e13bf3，40713261字节，已原子安装到Integrated MC与1.20.1-Forge_47.4.20两实例mods，无pending。build/divine-cg-release-checks.json验证所有生产条目与实际测试包相同，旧资产/语言键/声音键/Mixin映射完整保留，测试类未入正式包；额外检查原灰白小王座完整保留。原肩修3fd5ed68...及上个305aa53a...正式包都有备份。重启加载；已经触发过首次标记者，穿戴胸甲用OP2命令/somdivinecg重看。未提交Git，未回退其他窗口成果，保留chat_history.md等既有改动。


## 2026-11-06：武器模型被拒绝，停止本窗口武器工作

用户明确拒绝本批武器模型，并表示交给其他模型。本窗口停止武器建模、修改和发布；当前草稿不得作为验收成品。接手说明见 `docs/WEAPON_MODEL_HANDOFF.md`。四件胸甲和五把剑的工作目录改动尚未发布，草稿未删除、未全局回退。两处正式游戏实例 JAR 已重新核对，SHA256 仍为 `c5f6a2b6a8e1ee19e4073e30f9a4d4217d2f9b3b18b43933fa494a60d5e13bf3`。构建与隔离渲染检查不能替代用户外观验收。


## 2026-11-06：胸甲验收授权撤回，全部装备草稿停止交付

用户最新明确：胸甲也不允许验收，后续由用户指挥一件件完成。此前“胸甲模型允许交付”的授权已撤回。本批四件胸甲及五把剑全部为未通过草稿，停止打包、安装和自主重设计；等待用户指定单件任务。撤回前仅做了只读检查，尚未开始拆分、构建或安装，正式游戏实例没有因本批装备草稿发生更新。旧草稿保留供查阅，不得视为批准成品。


## 2026-11-06：神威天佑周身卫星 GeckoLib 候选版，待用户看外观

用户单独指挥升级神威天佑卫星。已实现黑白三维星芒与五元素球面模型，16 tick 位移渐隐、稳定3 tick后24 tick渐显；仅改变可见度，原服务端伤害、能量、冷却与返回保持。新位置包同步本人及追踪玩家，协议9。旧神威天佑环绕尘埃和飞行拖尾停用，原命中反馈保留，暗源与龙甲粒子保持。说明 docs/DIVINE_SATELLITES.md，模型与预览 art/divine_satellites/。

先前五把剑和四件胸甲仍未通过，不能直接发布 build/libs。tools/divine/prepare_satellites_candidate.py 从原正式包仅覆盖卫星白名单，生成 build/divine-satellites/sourceofmystery-1.0.0.jar，SHA256 af6ec5bce34525bd1ba6969574ed6702a49faaad3d6de9a4545be483cb5010c8，41451678字节。其他底包条目逐字节保持，被拒装备不在本候选中，神威天佑甲体/背环/CG保持。测试包生产条目与候选一致。

最终离线构建 build/divine-satellites-shader-fix-build.log 成功。真实隔离客户端 vanilla-final、shader-final 均静音通过：渐隐alpha0.4375，移动0，停下1；完全隐藏期间目标生命1800->1600，真实攻击位置接收、脱甲清理通过。光影为Oculus+ComplementaryReimagined_r5.9.3，首轮黑星被发光通道染白，改用保色透明通道，最终截图人工核对。日志有测试模板遗留GeckoLib示例/lotterycompat映射和Oculus/Embeddium提示，不是零ERROR。未测完整整合包或真实双客户端联机。核对报告 build/divine-satellites/review-verification.json。

preview-idle.png、preview-fading.png、preview-hidden.png、preview-attack.png及preview-shader系列均为真实客户端截图原图。状态 PENDING_USER，尚未安装。两正式实例已实际核对仍为 c5f6a2b6a8e1ee19e4073e30f9a4d4217d2f9b3b18b43933fa494a60d5e13bf3。按用户逐件指挥和先预览的约定，功能检查通过不代表模型外观已验收。


## 2026-11-06：球体版被拒绝，外环改为五元素古文字透明贴图模型

用户明确不允许通过球体版，指定五个元素对应的古代文字（象形字）及颜色，模型是贴图。当前外环已改为木绿、金黄、水蓝、火红、土橙五个原创古字形透明贴图面，参考港中文汉语多功能字库甲骨/金文字形特征作艺术化重绘，不宣称某件文物或同一时代字体的逐笔复制。每张512×512，模型0.62米正方形、2个有效三角面。朝向各自观察者，保持字形正面向上；移除球体几何，新候选包审计禁止divine_satellite_orb条目。内环星光资产与服务端攻击相关类均与被拒球体版逐字节保持，淡入淡出与隐身攻击继续工作。

外环生成器 tools/divine/generate_satellite_glyphs.py；generate_satellites.py默认入口也路由到新生成器，旧球面辅助函数不再调用。SVG笔画、字形总览、glyph模型源及预览在 art/divine_satellites/；球体旧包、源模型、贴图与记录已复制归档 rejected-spheres/ 并标记REJECTED_BY_USER。不要把旧preview-idle.png、旧球体测试记录或上一候选af6ec5bc...视作新版或已验收。

最终候选 build/divine-satellites/sourceofmystery-1.0.0.jar，SHA256 d6cae511313189d13abf980f4c2ebf4ee7704c3ecc3d03a28cfa37575c2510bd，40929405字节。只覆盖允许的卫星类和资源，排除被拒绝的五把剑与四件胸甲，原神威天佑甲体/背环/CG及其他底包条目保留。离线构建 build/divine-satellite-glyphs-build.log 成功。真实静音隔离客户端 glyphs-vanilla 与 glyphs-shader（Oculus+ComplementaryReimagined_r5.9.3）均通过，含新增的相反视角截图；已人工检查字形正向、颜色和透明背景，未见球体。移动alpha0，停下1，渐隐中0.4375；隐藏期间真实目标生命1800->1600；脱甲清理通过。模型外观由用户验收，仍PENDING_USER，未安装。原正式两实例核对仍c5f6a2b6...。

核对 build/divine-satellites/review-verification.json；新预览路径为 glyph-preview-idle.png、glyph-preview-opposite.png、glyph-preview-hidden.png、glyph-preview-attack.png 与 glyph-preview-shader-*，字形总览 glyphs-overview.png。当前说明 docs/DIVINE_SATELLITES.md。未测完整整合包、真实双客户端联机，测试模板旧映射与Embeddium/Oculus提示仍在，不称零ERROR。


## 2026-11-06：最新指令——外环恢复原粒子，内环黑白模型保留

用户在提出缩小古字后又指令“外环五个卫星回退为以前的粒子效果”。缩小25%的方案随外环模型方案一起失效。当前外环已恢复原五色粒子、出击/环绕/返回拖尾，按原逻辑移动时仍显示；内环黑白GeckoLib星芒及渐隐渐显保持。删除外环模型绘制和位置包，原SatelliteAttackHandler/ModNetwork源码及生产类恢复，协议8，额外同步取消。ArmorParticlePattern只省略内环旧粒子，外环原发射路径恢复，防止出击时同时留在身边。

构建 build/divine-outer-particles-build.log 成功；outer-particles真实静音客户端通过：恰好5颗待命粒子、忙碌位31时0颗待命、实际目标生命1900->1600、内环alpha移动0/停下1、脱甲清理。人工查看实机图确认只有五色粒子与黑白星芒，无字形/球体。服务器攻击、网络、原客户端粒子处理与服务端广播类已和底包逐字节核对。未重跑此版开启光影或完整整合包。

候选SHA256 d73ce1d5d75b31b8cf6313155922c4f93747ec406931b8e171da552adf303f37，40732572字节；仍未安装，实际正式两实例均保持c5f6a2b6a8e1ee19e4073e30f9a4d4217d2f9b3b18b43933fa494a60d5e13bf3。候选仍从原正式包按允许条目覆盖，不含被拒武器/胸甲。新审计 build/divine-satellites/review-verification.json，说明 docs/DIVINE_SATELLITES.md，新预览 outer-particles-preview-idle.png、opposite/moving/attack。旧字形历史与源码在reverted-outer-glyphs/，默认生成器已只生成内环星芒，不再生成外环模型。


## 2026-11-06：用户批准实装，并要求同步 GitHub 当前进度

已将内环黑白星芒模型、外环原五色粒子版本原子安装到Integrated MC和1.20.1-Forge_47.4.20，两目标SHA256均d73ce1d5d75b31b8cf6313155922c4f93747ec406931b8e171da552adf303f37，40732572字节，升级前c5f6a2b6...包已校验备份。安装收据build/divine-satellites/installation.json，无pending。用户已批准本版本实装，之前“待用户、未安装”的记录是历史状态。

为使GitHub主源码默认构建与实装内容一致，被拒绝的五把剑、四件胸甲模型及活动Java接线/资源已归档到drafts/equipment_gecko，原武器类、四件胸甲注册、数据生成器和旧贴图/物品模型恢复；未改神威天佑。外环古文字模型和工具归档drafts/divine_satellites。全部草稿源、可编辑bbmodel与生成器保留，不属于Gradle源集，不会在默认构建中再次启用。chat_history.md等既有进度保留。

仅清理编译类/资源输出缓存后离线assemble shaderCheckJar成功（build/github-progress-source-build.log），重建生产包与已安装包所有条目名称及内容逐字节一致，新增0、缺失0、变化0，审计build/github-progress-source-equivalence.json；整JAR哈希可因ZIP元数据不同而变化。未另开游戏或重跑完整整合包。当前总进度docs/PROJECT_PROGRESS.md，公开验证摘要docs/releases/2026-11-06-inner-star-satellites.json。GitHub目标origin main（kkskkkcc/SourceOfMystery-1.0），以最终推送收据为准。


## 2026-11-07：环境转变千格范围与网络参考版法阵

源码基线 b92ff09。本次新增超次元法杖 environment_shift：L 选术、潜行右键循环运行时 64 个原版群系、长按右键默认 4 秒成功消耗 2000 神秘之能；按用户最后要求半径 500、直径 1000，同时改群系、自然地表及植被。SavedData 异步分区块任务、共享软预算、断点恢复和原版客户端群系重发；多圈轮盘自动按 8/16/24 扩展，旧法术 ordinal 保持。协议 9，两端需同版。详见 docs/STAFF_ENVIRONMENT.md。

用户拒绝第一版薄环/粒子后，实际查看网络 Magic Circles – ISAS Addon 和 Mahou Tsukai 图例，加入 CC0 高清符文原图，重做手前竖直控制阵、地面符文带、六节点依次点亮、连续光流和双手动作/释放回收。素材来源及许可打入 JAR 的 STAFF_VFX_CREDITS.md；只参考其他模组构图，无额外依赖或受限资产复制。Oculus 远云盖住近阵问题通过刻纹深度写入修复。原法杖几何/材质与旧六段动画保持，新增关键帧同步 runtime JSON、bbmodel 与生成器。模型来源与预览集中在 F:\ChatGPT\我的世界神秘之源模组开发\modeling\staff_environment。

最终离线构建 build/environment-vfx-v4-build.log 成功。真实静音完整千格沙漠转换 diameter-1000-final 通过，覆盖 64 群系循环、多圈命中、取消/扣费、checkpoint、边界、建筑/容器与客户端群系；海洋/丛林使用相同处理器恢复半径50测试。最终视觉检查 vfx-v4-shaders（Oculus + Complementary 5.9.3）与 vfx-v5-vanilla 六主题第一/第三人称和真正释放通过，人工查看画面。V4 原版复核末段测试角色偏离站位，未用其末段画面验收；V5 固定测试站位并新增相机距离断言。保留旧失败/中间版本证据。静音实机视频46.5秒、1280×800，编码60帧不据此宣称实际渲染性能；完整解码通过。

生产包 build/libs/sourceofmystery-1.0.0.jar SHA256 2b23dc9561b38e8a7bd5167389e790c8083d8903b3c5a038d64c3cec1655fe52。build/staff-environment-release-checks.json 验证全部生产条目与两个最终实测测试包一致，25 个功能类条目与完整千格测试保持，测试器不进入生产包。状态 TECHNICAL_CHECKS_PASS_APPEARANCE_PENDING_USER；完整整合包、真实双客户端、所有维度/群系组合及主观声音未验收，不称日志零ERROR。

本次未安装、未提交/推送。实际 Integrated MC 仍d73ce1d5...；普通Forge实例现为00ff0433...（先前外部操作更新），均无新环境功能，历史两实例相同状态已过时。不要自动拿包含工程师装备V3等变化的普通构建覆盖整合包。


## 2026-11-07：领域展开，五种领域统一直径200格（最新）

用户确认五种领域全部半径100、直径200，覆盖原先各自范围。领域展开追加为第七种法术，旧编号不变；L选术，Shift+右键循环极寒/黑暗/重力/定身/斩杀，默认长按2秒、成功消耗5000神秘之能、持续6000tick，费用和时长可配置。持续按住不会误取消，新一次右键提前停止；定身固定释放地点，其他跟随玩家，施法者免疫。极寒锁位/蓝冰外壳/积雪/基础3HP每秒及寒风；黑暗头顶20格黑洞直径5、强制牵引5格每秒/基础10伤害和低级自然方块吸入；重力漂浮发光并清理/恢复既有状态。斩杀前方半圆基础30+最大生命1%独立真实伤害，领域持续5分钟，开场CG10秒只调用本人摄像头、期间无敌/隐藏持杖渲染，结印、面部特写、身后高处FOV130和红黑斩击，结束恢复。详情 docs/STAFF_DOMAINS.md。协议10，联机两端同版。

本地Blender实际组装97构件原创祭龛，GeckoLib运行模型/红色发光贴图及动画已接入，不把GLB当游戏模型。建模源与Blender、GLB、最终Gecko资源及预览集中 F:/ChatGPT/我的世界神秘之源模组开发/modeling/staff_domains/，未使用Lux3D付费任务。既有CC0符文沿用并保留出处。黑洞BreakEvent取消有效，默认数据包标签排除机器/容器/矿物等；最多48碎块，卸载后重载也核对领域会话清理，非生物发光恢复原标记。只处理加载区块，不强制加载100格半径世界；积雪和吸入的实际地形不自动回滚。

构建 build/staff-domain/actor-correction-build.log 成功。真实静音 final-full 完整五分钟通过，含200格定身边界、跟随进出、原生输入/5000扣费、控制/伤害、黑洞5格每秒与取消破坏、碎块重载清理、无AI漂浮、既有效果保留、CG无敌结束恢复和旧令牌拒绝。之后仅客户端DomainActor/DomainDirector修正正常成年玩家比例（EntityModel默认young=true导致近景错误）与相机跟随位置校正/面部构图；最终服务器功能字节与五分钟测试保持一致。actor-final-vanilla 和 actor-final-shaders 均与生产包全部条目逐字节一致，实机短回归与面部对准检查通过，关键截图已人工看过；后者开启Oculus+ComplementaryReimagined5.9.3。旧失败和幼体比例中间画面保留，不能当最终预览。

候选 build/libs/sourceofmystery-1.0.0.jar，54325771字节，SHA256 b4bf4fb0b524eb2541886fd05ecec92e7b7a44bc8961b85b4ca8adb93296a08c。汇总 build/staff-domain/release-verification.json，工具 tools/staff/audit_domain_release.py；测试代码排除，既有装备/Boss/登神资产、环境转换功能及法杖旧七段动画保留。上个环境候选已归档 build/staff-environment-candidate/，原环境审计指向归档包；开发前脏文件基线在 build/staff-domain/baseline/source-before-domain.zip。未安装、未提交、未推送，Integrated MC仍d73ce1d5...、普通Forge仍00ff0433...，不要自动覆盖。

最终静音实机预览 modeling/staff_domains/export/domain-execution-preview-silent.mp4（上述建模根目录），10秒1280x800、600帧完整解码、无音轨；编码60fps不作真实FPS保证。五领域原图和近景图同目录。视频核对 build/staff-domain/preview-verification.json。状态 TECHNICAL_CHECKS_PASS_APPEARANCE_PENDING_USER；完整整合包、真实双客户端与主观音效未验收，日志保留测试模板映射与Embeddium/Oculus提示，不能宣称零ERROR。


## 2026-11-07：陨灭杀准心锁定与追踪（最新候选）

用户要求法杖陨灭杀改为玩家准心选择实体或区域、私人发光参考、同一实体连续瞄准2秒后红色锁定，陨石实时追踪。已新增MeteorTargeting，服务端128格射线按最近实体/实心地形选择，固体遮挡有效，不再为空准心虚构前方80格地面。持杖选中陨灭杀即可预瞄，连续40tick同UUID红锁；换目标、移开、遮挡、换法术/收杖、死亡/离线/维度切换清理。释放瞬间重新验证准心，已锁实体绑定UUID、未锁定实体或区域固定当时落点；原末段天空法阵不再提前冻结目标。空准心最终释放失败且不扣能量。默认10000能量、480tick吟唱、100tick落下、200伤害和半径100爆炸保持，其他法术目标算法保持。

MeteorAimPacket仅PLAYER发送给对应施法者，客户端MeteorAimClient通过本机Minecraft发光判定与颜色Mixin显示白/红轮廓，不修改共享发光位或队伍。区域有地面符文、直径200边框及正对镜头的落点光标，解决墙面侧视细线问题。追踪期间私人红色标记保留，转动准心不改变已发出陨石。OutsiderSpell只有调用setTrackedImpact才启用追踪，每tick用剩余飞行段重定向并同步轨迹；目标死亡/离维度/卸载则落最后位置，不另选目标。Boss AI类和原定点陨灭杀调用、全部模型音画、环境及领域功能条目保持。协议11，两端需同版。详见docs/STAFF_METEOR_TARGETING.md。

构建 build/staff-meteor-targeting/final-build.log（build shaderCheckJar compileGameTestJava）成功。真实静音 final-native、area-final-shaders 均通过白色/40tick红色轮廓、共享发光与队伍不变、遮挡/区域、换目标重置、空准心清除、实际useItem释放扣费、目标移动到130格外后落点/伤害与原点旁观目标未受伤、发射后不改UUID、固定区域不跟随、失去目标最后落点及换法术清理。夹具只在隔离配置中把吟唱缩为60tick，生产480tick保持；实际双客户端与完整整合包未测，不把单机私人状态检查说成双端隐私验收。光影Oculus+Complementary5.9.3，最终GPU原图已人工检查。prototype-native因未持久化尸壳在130格外被原版刷怪机制删除而断言失败，修正夹具后通过；中间证据保留。

最终 build/libs/sourceofmystery-1.0.0.jar，54344554字节，SHA256 1ddf3b23fbda57da1788cca0c4fcc112cab279951e58125bc3d2e4913b6967e7；稳定副本build/staff-meteor-targeting/sourceofmystery-targeting-1ddf3b23fbda.jar。汇总build/staff-meteor-targeting/release-verification.json，工具tools/staff/audit_meteor_targeting.py；两最终实测包所有生产条目与候选逐字节相同，测试代码排除，旧资源/语言键/Mixin映射保留。上个领域b4bf4fb0...包已保存build/staff-domain-candidate/并更新旧领域审计路径；源基线build/staff-meteor-targeting/source-before-targeting.zip。最终截图art/staff_meteor_targeting/。本次未安装、未提交/推送，实际两实例仍d73ce1d5...和00ff0433...。状态TECHNICAL_CHECKS_PASS_APPEARANCE_PENDING_USER，保持静音，日志模板和Oculus/Embeddium提示仍在。


## 2026-11-08：天气掌控与四种巨型龙卷风（最新候选）

用户要求新增法杖天气魔法，Shift＋右键切换原版天气及巨型龙卷风；龙卷风按环境变体，统一半径100格、每秒5点基础伤害。已追加第八种法术 weather_control，L轮盘选「天气掌控」，潜行右键循环晴天／雨天／雷暴／巨型龙卷风，长按右键默认3秒施放。已说明并采用可配置默认值：原版天气2000能量、10分钟；龙卷风5000能量、5分钟，右键可提前消散。原法术编号保留，网络协议12，两端需同版。说明 docs/STAFF_WEATHER.md。

龙卷风约6秒从云底向下形成，高180格，成熟后约0.5格/秒弯曲前行，不强制加载区块。普通、沙暴、水、火依照真实地表／流体／群系标签每秒重新判断，支持数据包追加模组群系。风场服务端负责牵引、环绕、提升和高空抛出，施法者／其坐骑等免疫；沙暴失明缓慢、水灭火、火更强虚弱与发光并抽样焦化草木，火变体没有另加燃烧伤害。原版晴雨雷暴使用世界天气系统；龙卷风自己绘制云底和变体风雾，不再叠原版全局雨幕。有顶维度按玩家所处高度查找地表，不取基岩屋顶。

自然地表和木结构可被真实卷走，尊重mobGriefing、世界边界、交互权限及Forge BreakEvent；默认每秒20次尝试、每tick地表约2ms共享软预算、每风暴96碎块、每维度8风暴，客户端最多绘制最近4个。不破坏带方块实体的机器／容器或默认未列入标签的矿石。被破坏地形不自动回滚。碎块短时离场，退出／死亡／换维度清理，卸载重载核对风暴会话，避免永久悬空；风暴不跨重启恢复。已有领域定身／冰冻与时间停止优先。

视觉为多层流动湍流网格、软云团、地面尘墙及三个子涡旋；程序原创10张透明纹理、四套8秒风声和staff.weather法杖动作。独立风雾着色器避免实体材质硬透明裁切，Oculus使用项目已有方式选择合适帧缓冲／恢复材质写入，并且水丝与火星统一在同一材质通道。原版云层的有效绘制选项临时覆盖，玩家持久配置不改写；第三方光影自己的云仍可能存在，不能宣称通用光影云控制。形态仅参考NWS 2013 El Reno宽漏斗和多涡旋，不声称真实公里级流体模拟。没有Lux3D付费任务。素材、原始WAV、生成器、运行几何源及导出集中 F:/ChatGPT/我的世界神秘之源模组开发/modeling/staff_weather/。

最终构建 build/staff-weather/release-build.log 成功。真实静音 release-native 与 release-shaders（Oculus+ComplementaryReimagined5.9.3）通过原生右键／四选项、实际能量扣费、晴雨雷暴标记、四变体真实卷吸与5点伤害、100格外不受影响、施法者免疫、特殊效果、破坏事件取消与真实碎块、卸载碎块清理、旧停止令牌拒绝、提前消散和声音实例停止。有顶维度地表查找已查；到期实测采用配置200tick，默认6000tick只验证激活初值，未完整等待5分钟。主音量0，没有主观听音验收。最终两轮未发现GL_INVALID；模板旧映射与Embeddium/Oculus提示仍在，不是零ERROR日志。完整整合包和真实双客户端未测。

中间证据保留：soft-wind-shaders功能通过但自定义材质被Iris屏蔽；composite-final-shaders因混用Iris实体发光通道出现No active program，被测试器GL断言中止。已统一通道，最终release-shaders全部变体通过。不能把这些中间失败／旧预览当交付版本。

候选 build/staff-weather/sourceofmystery-weather-af5c053a3ebd.jar，54,821,823字节，SHA256 af5c053a3ebdc3839ed98dfdd1b2e6746475d9e066d4f15be01d7888f63ffb16；build/libs同内容。审计 build/staff-weather/release-verification.json，工具 tools/staff/audit_weather_release.py；859个生产条目与两最终实测包逐字节一致，旧359个未改资产、旧八段法杖动画及14个环境／领域／索敌／Boss逻辑条目保持，测试类排除。声纹与16秒预览完整解码见 media-verification.json；960帧1280x800，无音轨，60fps只代表编码参数。预览 modeling/staff_weather/export/weather-tornadoes-preview-silent.mp4；同目录preview-native-*为原版图，03-tornado-*为最终光影原图。

状态 TECHNICAL_CHECKS_PASS_APPEARANCE_PENDING_USER。本次未安装、未提交或推送GitHub。实际Integrated MC仍d73ce1d5...，普通Forge仍00ff0433...。陨灭杀1ddf3b23...稳定副本已保留，原陨灭杀审计路径更新指向该副本；天气前源码基线在build/staff-weather/source-before-weather.zip。继续保留开发前环境／领域／索敌脏文件，不要清仓库或自动覆盖正式游戏。


## 2026-11-08：用户授权提交 GitHub、合并工程师更新并同步本地

本地环境转变、五领域、准心陨灭杀与天气魔法共115条变更，先保存到提交5317ca25592d596191ec58e64b666f81f4bfeb3c，再与origin/main工程师两次更新合并：8b7c671（装备V4轻甲／武器、登神CG V4音效）及011821ca091356b70c90fa224cb43fe838214cc0（登神CG V5几何接触／坐姿／空手演出、正确剑柄握持）。这两次更新已经收到用户本次明确的合并授权，但源码合并不代表模型外观验收或安装。

仅中英文语言文件出现文本冲突。以b92ff09作共同基线，按JSON键完成三方合并，两边没有同键不同值的实际冲突；工程师的登神字幕／新增声音和本地所有法术文案均保留。声音注册自动合并后也做相同核对。代码／模型没有人工选边覆盖；合并原始内容时本地独有111条、工程师独有70条均与各自提交blob一致，其余4个共改路径为进度文档和两语言／声音注册。

Java17／Gradle8.5离线 build shaderCheckJar compileGameTestJava 成功。新生产包864条目、203个JSON解析通过，不含测试器；相对天气af5c053a...候选，385个不属于工程师改动的生产class和337个未被工程师修改的资产条目逐字节保持。CG的7个class条目按工程师更新改变，模型与音效采用工程师新版。旧359项资产等历史保留检查针对原候选，不应要求新增合并包与工程师更新前的全部资源保持相同。

新构建build/libs/sourceofmystery-1.0.0.jar，56,132,745字节，SHA256 fd7f1e1befeed41984dc03120755257a717ac99bfdc05f9b752da187e4b93230；稳定副本build/github-sync-20261108-180714/sourceofmystery-merged-fd7f1e1befee.jar。公共摘要docs/releases/2026-11-08-staff-engineer-merge.json。本地备份、合并核对、构建日志和最终同步回执集中build/github-sync-20261108-180714/；源备份local-progress-before-merge.zip含115条原改动及SHA清单，另有tracked-progress.patch、本地backup/staff-before-engineer-merge-20261108分支。以上只在本地保留，不提交缓存／日志／构建包／密钥。

本次只完成源码与仓库同步、合并和构建，未开游戏或安装新JAR。合并前法杖实机证据仍对应各自已归档候选，不能冒称合并后的装备／登神CG／整合包已重新验收。网络协议12。GitHub与本地最终提交、Actions状态以本次最终回执final-sync-receipt.json及GitHub实际分支为准；不要根据前节“未提交”历史状态判断本次结束后的同步情况。


## 2026-11-08：修复环境魔法无法覆盖上一轮转换

用户实测确认任意环境二次施法均提示“范围内已有环境转换”。根因是StaffEnvironment.start对所有运行中重叠圆盘直接拒绝，千格异步转换仍在工作时无法切换目标。现在完全覆盖旧任务时接替并释放其票据；部分重叠时旧任务保存新范围的排除圆盘，只在剩余区域继续处理。地表、植被和完整高度群系统一遵守后施法优先；Version3保存覆盖关系，防止新任务先结束、旧任务恢复后改回旧环境。Version2游标兼容。每任务独立UUID票据／前沿特效，避免同一玩家重叠任务互相释放票据或替换前沿。四任务上限仍保留，先剔除被完全接替的旧任务再计数；真正第5个任务拒绝不扣费，不改变旧覆盖关系。繁忙文案改为数量上限说明。

build/staff-environment-overwrite/build-final.log构建build shaderCheckJar通过。新增StaffEnvironmentOverwriteCheck及--environment-overwrite入口；真实静音overwrite-native通过两次完整80tick吟唱、旧任务未结束时原地R500接替／2000扣费、部分重叠R500准入、客户端／服务端群系、旧任务晚于新任务恢复后的覆盖优先、非重叠继续、箱子7钻石与建筑保护、断点掩码／游标保持、Version2迁移和满容量接替。准入采用生产半径500；完成改造与断点处理使用恢复为半径48的同一处理器，未重跑两次完整千格改造、光影、整合包或真实双客户端。主音量0，最终无GL_INVALID；模板旧映射／Embeddium提示保留。

候选build/staff-environment-overwrite/sourceofmystery-environment-overwrite-bd0a88439669.jar，56,135,704字节，SHA256 bd0a88439669c0549c408d8521d4afc430d0189aff1cdc922b44d8c349034113，build/libs同内容。相对fd7f1e1b合并包，仅StaffEnvironment及Job两class、两语言的繁忙文案改变，新增Area记录class，其余860条目逐字节保持；865生产条目与实测包相同，测试器排除。审计build/staff-environment-overwrite/release-verification.json；工具tools/staff/audit_environment_overwrite.py。原fd7包与旧记录保留。源码基线43a6591，本次未安装、未提交或推送；旧世界任务自动兼容，不必删存档或等待上一轮转换完成。

## 2026-11-08：环境覆盖修复的 GitHub 同步提交

按用户“更新推送，将当前项目同步github”的授权，本次同步提交纳入上述环境覆盖修复、真实静音回归夹具、启动入口与审计工具，并保留此前合并的工程师更新。发布摘要见docs/releases/2026-11-08-environment-overwrite.json。前节“未提交或推送”为修复完成时的历史快照；最终提交、远端一致性及Actions结果以GitHub实际分支和build/github-sync-20261108-185544-environment/final-sync-receipt.json为准。本次不替换游戏实例JAR；既有实测范围与限制保持不变。

## 2026-11-08：时停归类时间魔法，新增时间延缓与时间加速

按用户要求，轮盘原时停入口显示为「时间魔法」，保留原time_stop保存ID／第4项序号；Shift＋右键循环时间停止／时间延缓0.1倍／时间加速5倍，子类保存在法杖NBT。时间停止保留施法者豁免的旧规则；新增两种模式让所有玩家保持正常速度。默认各1000能量、持续240正常tick、共享结束后60tick冷却，新增模式的费用／时长独立配置；再次右键可提前结束自己释放的时间魔法。

以MinecraftServer的维度tick入口包住完整ServerLevel.tick及Forge维度前后事件，延缓每10服务器tick模拟一次，加速每服务器tick模拟5次。玩家实体每服务器tick只更新一次；主世界底层共享时间仍只推进一次，以保存的维度偏移提供独立时钟。客户端按倍率更新世界实体／方块实体、粒子、纹理和云层，并提供延缓插值；玩家保持正常插值。新同步包包含维度、会话UUID、子类、剩余正常tick、相位和时间，旧会话结束消息不能清除新状态。登录／重生／切维度同步／清理，施法者死亡／离开维度后结束，协议13，两端需同版。沿用旧时停偏移存档格式；活跃技能不跨重启恢复。

build/staff-time/build-final.log的Java17离线build shaderCheckJar compileGameTestJava通过。真实静音time-native-final通过原生右键、原生Shift切类、实际扣费、三模式240tick自然到期和60tick冷却。每100服务器tick：世界／Husk／天气／熔炉分别推进0、10、500tick，施法者均100tick，FakePlayer观察者分别0、100、100tick，另一维度时钟均100tick。红石计划tick与Forge维度事件按倍率，客户端非玩家实体也分别推进0／10／500tick，客户端玩家保持100tick。结束后70tick无补跑跳时，过期会话结束包拦截通过；三个实机截图无GL_INVALID。第一次time-native-initial同样通过，保留原始日志；最终增加客户端计数、Shift输入和FakePlayer观察者检查。主音量0，未做主观音效验收。

候选build/staff-time/sourceofmystery-time-magic-607386a2ce7a.jar，56,150,915字节，SHA256 607386a2ce7ac2f94e08c9ba89968875cb394672d922df8a2f9d76fe2897d7e4；build/libs同内容。871个生产条目与最终实测包逐字节一致；相对bd0a88439669候选保留845个旧条目，382个非语言资产完全保持（模型／贴图／动画／音效等）。测试夹具未入生产包。审计工具tools/staff/audit_time_magic_release.py，审计build/staff-time/release-verification.json，公共摘要docs/releases/2026-11-08-time-magic.json；文档docs/STAFF_TIME_MAGIC.md。源码基线d572ddd，源备份build/staff-time/source-before-time.zip，旧候选不覆盖。

本次未安装或推送GitHub。当前证明是单静音客户端＋服务端FakePlayer，不能当真实双客户端；施法作用于主世界，另查下界时钟隔离，未在下界／末地主动施法，未重跑完整整合包或开启光影。模板旧映射／Embeddium提示及FakePlayer未发玩家资料的测试警告仍在。联网和必要区块IO保持正常，异步生成／读盘物理耗时不保证倍率；第三方以全局服务器事件／独立线程／系统时间计时的功能不保证随维度倍率，见兼容说明。用户旧「下次测试不要开声音」继续生效。

## 2026-11-09：来自东方的术最终粒子／白球方案

新增法杖第九项「来自东方的术」，外圈轮盘；60tick右键蓄力，5000能量。五道橙红粒子神门依次升起后定格，近门32,000粒，带距离细节级别和深度写入，防Oculus云层盖断。CG仅调用施法者摄像头，原版玩家皮肤／装备；240tick发射，248tick收镜（12.4秒），恢复正常操作和受伤，法球继续飞行。网络协议14。

用户最新指定覆盖早期方案：双层嵌套纯白球，内半径8.6不透明、外半径10透明；没有球面文字、纹路或球下法阵。法杖充能法纹仍在。发射后三条灰色主丝带与三条细丝绕飞行轴持续螺旋，最长46格，向尾端渐变变细／变暗／变透明，球体结束后12tick消散。旧Blender神门和旧球纹仅存于F:/ChatGPT/我的世界神秘之源模组开发/modeling/eastern_rite/drafts，不得重新投入运行时。参考视频无法读取，只依据用户截图；外观待用户验收，不宣称一比一通过。

球心从发射位置计算255格，默认1.4格／模拟tick，半径10。实体每次施法受1000魔法伤害一次，之后裹挟继续飞行（最多256），施法者及禁止PVP的玩家除外。范围结束、撞硬度负值方块、施法者死亡／离维度、世界卸载时恢复原重力，异常标记在重载恢复。裹挟期间避免短暂嵌入清除队列尚未处理的方块引发窒息伤害。连续球体碰撞防穿透，抹除无掉落并清空容器，尊重交互权限和BreakEvent取消；权限取消也停止法球。不强制加载未加载区块，抹除有3ms软预算和数量上限。

最终build/eastern-rite/build-ribbons-final.log离线build shaderCheckJar compileGameTestJava通过。真实静音eastern-white-ribbons-native通过原生轮盘、取消蓄力不扣费、5000扣费、五门定格、CG免伤和发射即恢复摄像头、法球独立继续、1000单次伤害、实时裹挟／原重力恢复、精确255射程、基岩前沿截停、BreakEvent保护、半径边界和容器无掉落。首轮额外窒息伤害已修复并复测，旧失败日志保留。追加碰撞探针跳过重复CG预热，仅首轮录制完整CG。非真实双客户端／完整整合包测试，所有测试MASTER=0，没有主观音效验收。

最新无光影录像实际帧耗时中位17.2669ms，P95为21.6534ms，共588样本；视频编码60fps不能等同实测固定60fps。前版eastern-depth-shader-final查过Oculus＋Complementary粒子深度；最新白色嵌套球＋丝带的eastern-white-ribbons-shader在进入世界、施法之前发生JVM native allocation／GL_OUT_OF_MEMORY，最新光影复测未完成，不能沿用旧通过结论。

稳定候选build/eastern-rite/sourceofmystery-eastern-rite-0163871b553e.jar，56,418,755字节，SHA256 0163871b553e2d5cc0dab86dd601090a5894dec0f5c7062927268a77695c182a。894个生产条目与最新实测包完全一致，380旧资产保持，旧语言／声音／动画键保留；测试器、旧神门网格及取消的球纹均排除。审计build/eastern-rite/release-verification.json，公共摘要docs/releases/2026-11-09-eastern-rite.json，说明docs/STAFF_EASTERN_RITE.md。未安装、未提交或推送，原有未提交时间魔法完整保留。

两个静音原生视频和7张截图集中在上述建模目录的export/preview-20261109-white-ribbons：eastern-particle-cg-silent.mp4为12.4秒CG，eastern-flight-silent.mp4为恢复正常玩家视角后的9秒飞行。已完整解码验证，preview-info.json含包与媒体SHA。工具tools/staff/audit_eastern_rite_release.py及export_eastern_preview.py；初始源码备份build/eastern-rite/source-before-eastern.zip。勿把旧带铭文或追踪法球21秒CG预览当作当前版本。
## 2026-11-09：10格实心球、环境队列和陨灭杀保持锁定

后续用户反馈已合并处理：纯白实心内球从8.6改成半径10格（直径20），透明外壳11.6，灰色丝带根部随外形外移。服务端碰撞半径保持原10格。旧export/preview-20261109-white-ribbons的录像仍是8.6内球，未重录本次尺寸，不可当10格新版外观证据。

环境转变原有4个未完成任务硬上限导致千格任务积压后无法再施法。现在所有有效施法可准入，新的任务置于队首优先处理；最多4个任务处理并保有独立区块票据，其他任务释放临时票据、保留游标并排队。新范围覆盖旧范围的让位关系不变，不丢弃旧区域剩余工作；SavedData原格式直接保存队列顺序、覆盖和进度，旧世界无需清任务。共用3ms预算，原地覆盖和部分重叠规则继续。

陨灭杀原来服务端每tick按新射线覆盖Selection，客户端也只画当前准心对象。现在完成40tick红色锁定后保留已锁定实体，移开准心／看天空／蓄力及释放均保持，服务端重新验证实体并更新实时位置。另一个实体持续瞄准40tick可换锁；死亡／移除／离维度／超128格、切换法术或收起法杖清除待发锁定。已发出陨石继续原UUID追踪，不因后续瞄准变化改目标。仅PLAYER同步的格式不变，全局协议仍14，服务端和客户端均需更新本次包。

build/staff-followup-fixes/build-fixes.log的离线build shaderCheckJar compileGameTestJava通过。真实静音无光影queue-native通过R500新施法准入、第五第六处扣费准入、存档队列顺序／覆盖／游标、旧版本迁移、票据<=4、队列末任务提升并完成，以及旧覆盖修复的地表／客户端服务端群系／建筑容器保护。完整处理使用R48覆盖工作和R4队列工作，没有重跑六次完整千格地形。sticky-lock-native通过白轮廓／40tick红锁、移开看天仍红、蓄力与释放保留原实体、2秒换锁、目标超距释放待发锁但已发陨石继续追踪、原实体实际落点与伤害、区域／空目标不扣费、目标死亡最后位置、切法术清理。测试吟唱60tick，生产480tick未改。两次MASTER=0；未查光影、完整整合包或真实双客户端。

稳定候选build/staff-followup-fixes/sourceofmystery-staff-fixes-7c1db7d10b67.jar，56,419,402字节，SHA256 7c1db7d10b67601c6fad9bcbafed378f5f007e1ce3508268bced37e53caa2543。894生产条目与上述两实测包完全相同，相对0163871b553e仅7个逻辑class改变，392资产全部保持。编译产物javap常量确认CORE_RADIUS=10、SHELL_SCALE=1.16。审计build/staff-followup-fixes/release-verification.json；公开摘要docs/releases/2026-11-09-staff-followup-fixes.json，工具tools/staff/audit_followup_fixes.py。新增测试启动参数--heap-mb2048仅影响隔离测试堆内存。原源码备份在build/staff-followup-fixes及build/eastern-rite/source-before-radius10.java。本次未安装、未提交或推送，已有未提交工作保留。

## 2026-11-09：东方之术正常视角／任意朝向、斩杀全域、时间魔法无限持续

四项用户反馈合并处理。东方之术删除摄像头Director和替身Actor（原源码保存在build/eastern-gameplay-execution/retired-cg，之前完整源码备份source-before.zip），不再锁位、强制转头、锁物品栏、隐藏玩家或赋予CG免伤。五门和法球按原时间线继续，60tick蓄力/5000费用、240tick发射保持。原位置悬浮大法阵取消；新增EasternStaffVfx，白银螺旋能流、双层旋转象形刻纹、星芒和释放脉冲绑定实际GeckoLib crystal骨骼。仅手持第一/第三人称出现，不污染背包、展示框、掉落物。人物只调整持杖手臂，不锁腿/头/摄像头。

方向在蓄力完成时读取完整yaw/pitch，不再压平垂直分量。神门/法球/灰色丝带用同一局部空间，客户端裹挟位置同步包含局部up偏移；连续碰撞和抹除包围盒补齐前后端Y范围。允许任意水平角及抬头低头，神门完成后仍固定，不追随随后转头。内球半径10、透明外壳11.6、255格从球心发射位置计算、1000单次伤害和裹挟/重力恢复、保护事件/基岩截停保持。

斩杀移除服务端和客户端前方半平面判定，整个半径100领域360°受击，仍按碰撞箱中心三维距离。统一executionTarget为有效存活可攻击LivingEntity，施法者/其载具排除，掉落物与投射物不生成斩击。删除固定18条空斩，无有效目标时不绘制斩击且不播放扫击循环；祭龛与领域底纹仍表明领域存在。原5分钟/启动5000/伤害30加1%最大生命真实伤害/斩杀自己的10秒CG保留；取消的是东方之术CG。

时间停止、0.1倍延缓、5倍加速均取消服务端until到期和客户端倒计时。TimeMagicSyncPacket.remaining=-1代表无限生效，0只表示结束；时停画面也支持无限状态。再次右键解除，冷却从解除时开始；死亡/离线/离维度/关闭仍清理。旧timeStopTicks/timeSlowTicks/timeHasteTicks配置键兼容保留但不再控制时限，配置注释/中英文提示同步。东方包增加pitch、时间无限标志需两端更新，协议升为15。

离线build shaderCheckJar compileGameTestJava成功，日志build/eastern-gameplay-execution/build.log。真实静音gameplay-execution-native通过原生外圈选项/蓄力取消/扣费、摄像头与HUD不接管、真实走动转头、五门定格、不再免伤、实际1000单次伤害、裹挟/原重力恢复、精确255、基岩和BreakEvent保护、侧后方/斜向/斜上斜下/竖直共7个新增真实命中探针、客户端方向一致；加上原正前方共8种方向。斩杀四方同伤害、98格内/102格外边界、掉落物排除、空场停止斩击与扫击调度、保留斩杀CG通过。截图第一/第三人称杖尖和空场/目标场已人工查看。

真实静音indefinite-native三模式各维持400正常tick超过旧240限制，实际右键解除和60tick冷却/解除后70tick不补跑通过。100服务器tick内世界/非玩家实体/天气/熔炉分别0、10、500tick，施法者均100，另一维度均100；客户端计数、FakePlayer观察者、红石/Forge事件、旧会话结束消息保护继续通过。MASTER=0，非主观听音验收；没有重跑光影、完整整合包、真实双客户端或领域完整五分钟。日志仍有模板旧映射/Embeddium/Oculus提示及FakePlayer玩家资料测试警告，不是零警告。

稳定候选build/eastern-gameplay-execution/sourceofmystery-staff-gameplay-6f98b85b8ecf.jar，56410392字节，SHA256 6f98b85b8ecfbf335d42817367d2de216d5aa21761a8c7e8a75430b7bec72ab1；build/libs同内容。891生产条目与两次实测包逐字节一致，390非语言资产完整保持，两语言只改时间操作提示。4个东方CG class删除，新增EasternStaffVfx，其余改动按限定class清单审计；测试器不入生产包。工具tools/staff/audit_gameplay_refinements.py，审计build/eastern-gameplay-execution/release-verification.json，公共摘要docs/releases/2026-11-09-staff-gameplay.json。

新24秒静音原生预览在F:/ChatGPT/我的世界神秘之源模组开发/modeling/eastern_rite/export/preview-20261109-gameplay/eastern-gameplay-silent.mp4，另有9张截图与preview-info.json。测试夹具只为展示正常第三人称手动切换F5类型，生产法术不切镜。全部完整解码、无音轨、60fps编码；实测渲染帧中位20.5521ms/P95 28.228ms，1152样本，不能声称锁60。旧white-ribbons目录仍为8.6内球且含已取消CG，不可当本次预览。新导出工具tools/staff/export_eastern_gameplay_preview.py。

状态GAMEPLAY_NATIVE_PASS_APPEARANCE_PENDING_USER。本次未安装正式实例、未提交或推送；main仍d572ddd，所有既有未提交时间/东方/队列/锁定工作保留。旧7c1db7d10b67候选及历史证据不覆盖。


## 2026-11-09：村庄守卫取消雷击、东方前摇≤4秒、斩杀出伤同步

守护之灵GuardianSpirit移除PRAYER选择/施法状态、castLightning方法、PrayerCooldown读写及动画分支；GuardianSpiritRenderer移除对应蓝色法阵层。敌群不再触发雷击，继续原40tick蓄力蓝色追踪球。基础属性、巡逻、村民治疗、悦灵、友好目标规则与投射物代码保持；旧PrayerCooldown存档键自然忽略，读取时本来就重置IDLE。原始模型/动画/贴图资源保留但不再触发祈祷。源备份build/guardian-no-lightning/before；独立阶段build日志成功。守卫仅源码/编译/字节码核对，没有单独进入村庄实测。

东方之术默认蓄力20tick，StaffSpell.castTicks对该项最多20（兼容旧配置60或更大，无需改用户配置）；世界仪式58tick即发射，共78tick、20TPS下3.9秒。GATE_START2/INTERVAL8/RISE18，最后一门52tick定格，ORB_CHARGE_START34/FORM_START40/LAUNCH58，杖尖能流和人物释放手臂同步引用常量。长按到蓄力结束后的再次use调用消费输入，不重启/不重复扣费/不误报busy；真繁忙原因仍由原入口处理。取消CG、全yaw/pitch方向、10/11.6嵌套球、255射程、灰色丝带、1000伤害和裹挟/保护规则继续。

斩杀不再按客户端领域时钟循环绘制。StaffDomains.damage保留每20tick的30+1%最大生命结算，比较实际生命+吸收值减少，仅对真正承伤的目标发DomainSlashPacket；每包最多256目标，较大批次分包。消息包含维度、领域token、伤害轮次、实体ID/UUID。客户端只播放8tick短命中残影，核对会话与目标、拒绝旧轮次、去重，扫击声同消息触发；目标被杀时残影可短暂收尾，不循环斩尸。停止领域/换世界清理。取消伤害/免伤不出斩击，掉落物/投射物不出斩击，空场和两次伤害之间没有空斩。360°/半径100/5分钟/斩杀自身10秒CG保留。新增包协议16，两端同版；时间无限持续等之前成果保留。

离线build shaderCheckJar compileGameTestJava成功，最终日志build/eastern-fast-release/build-final.log。真实静音fast-release-hit-sync-final-native通过：原生右键到法球开始移动实测78服务器tick；测试显式保留旧castTicks60，实际20仍正确。八种方向命中、5门定格、5000扣费和取消免费、不接管摄像头/不免伤、1000单次伤害、裹挟/原重力恢复、255射程、基岩/BreakEvent保护、容器无掉落继续通过。连续9个斩击伤害轮次与真实LivingDamageEvent对应，活目标的两次伤害间存在无斩击空档，外部LivingAttackEvent取消的目标无伤害/无斩击；四方同伤害、100格边界、掉落物/空场/停止清理和斩杀CG继续通过。第一/第三人称、目标命中和空场原图已查看。MASTER0，无主观听音；未重测光影、完整整合包、真实双客户端、完整5分钟或村庄守卫实战。

最终候选build/eastern-fast-release/sourceofmystery-combat-timing-7cb185d22822.jar，56415131字节，SHA256 7cb185d228222a593c56bab074023ce77a1e1604210546864b4919fe3ae524b6，build/libs同内容。894生产条目与最终实测包逐字节匹配；392资产全部保持（含语言、声音、模型），仅限定战斗/视觉class改变。新增DomainSlashPacket/Target、DomainVisuals.Strike/StrikeKey；删除GuardianSpiritRenderer.MagicCircleLayer。守护灵字节码无castLightning/prayerCooldown、仍有fireOrb；其他Boss/武器雷电、时间魔法、环境队列、陨灭杀保持旧候选class。审计工具tools/staff/audit_combat_timing.py，完整回执build/eastern-fast-release/release-verification.json，公开docs/releases/2026-11-09-combat-timing.json。

初次fast-release-hit-sync-native也通过78tick/9轮同步，但录像有持续按键重复busy文案；之后只增加已有仪式use消费分支，再构建/完整重跑最终native。初次候选3515b1482f98、initial-release-verification.json及日志均保留，不当最终交付。此前6f98b85b8ecf也保留。当前新14秒静音原生预览在F:/ChatGPT/我的世界神秘之源模组开发/modeling/eastern_rite/export/preview-20261109-fast-release/eastern-gameplay-silent.mp4，10张截图、preview-info.json含哈希；导出脚本build/eastern-fast-release/export_preview.py。60fps是编码，不声称锁60，实测帧耗时见回执。旧24秒预览是旧前摇版本。

未安装正式实例、未提交或推送。main仍d572ddd；所有之前未提交成果继续保留。新源码备份build/eastern-fast-release/source-before.zip，附DimensionBreakerStaffItem-before-busy-fix.java；守卫原源码备份另见前述目录。状态TIMING_NATIVE_PASS_GUARDIAN_CODE_BUILD_PASS，用户外观验收仍待进行。

## 2026-11-11：炼金术士台每日材料商店

新增sourceofmystery:alchemist_table，严格按GOG/OFO/GOG合成，G金锭、O黑曜石、F黑暗次元祭品。手持右键开店，潜行右键放置，放置后右键开店，徒手拆下掉落一张。只扣主背包/快捷栏的标价材料，不额外扣神秘之能，不使用穿戴装备、副手或改名/自定义NBT材料。

每天按主世界日照时间floor(dayTime/24000)刷新20种不同商品，全世界、所有维度和玩家共用；同日开关/重载配方不重抽，实际报价保存为SavedData sourceofmystery_alchemy_shop。优先索引安全可读的静态配方与Forge材料标签，材料块对应锭/宝石/原材料，锭优先对应粒，金块1–9金锭。Unsupported动态配方跳过，不保证每个外部模组所有物品。5列4行GUI显示商品和价料，悬停完整材料/持有数量，左键发服务端编号+UUID版本，不接受客户端报价。36格背包扣料+发货原子计划，满包/缺料/旧报价不扣，展示格禁止拿取和克隆；背包改变清除旧缺料/满包错误，手持条件失效/离台超过8格关闭。协议升17，两端同版。

外观为135cube本地Blender/GeckoLib黑曜石金边台、双环祭品核心、三药瓶和账本，512贴图及发光遮罩。建模源.blend、GLB、GeckoLib、贴图和预览集中在F:/ChatGPT/我的世界神秘之源模组开发/modeling/alchemist_table，未使用收费云服务。检查了原生截图并修正GeckoLib与Blender坐标手性的环段旋转，金环现已连贯。外观仍待用户验收。

最终build/alchemy-table/build-final.log离线build shaderCheckJar compileGameTestJava通过。build/alchemy-table-smoke/final-native真实静音原生测试ALCHEMY_SMOKE_OK：准确配方、20件唯一清单、金块1–9、774候选及外部命名空间配方、同日稳定、SavedData往返、真实GUI左键、分散材料扣除、带自定义NBT物品保留、不扣能量、满包原子回滚、背包变化清提示、跨日同步与旧报价拒绝、手持/放置右键、徒手拆下、丢失手持物关闭。跨日通过设世界时间；最终退出存盘后另读实际.dat，确认20个唯一商品及合法材料数量。没有重启读取测试。首次initial-native夹具因传送残留跌落距离死亡已保留日志，修复测试角色状态后native-fixed和final-native通过，不掩盖首轮失败。

稳定候选build/alchemy-table/sourceofmystery-alchemy-df23c044ebe6.jar，57,256,050字节，SHA256 df23c044ebe64af90a5c133e8d0777a89388e4bc110188d481c5e4b959b7cd4d。920生产条目与最终实测包逐字节一致；相对7cb185d22822前版无删除，390旧非语言资产完整保持，两语言仅追加键，旧功能class保持，仅注册类更新。测试夹具/lotterycompat数据不入生产包。audit tools/alchemy/audit_release.py，build/alchemy-table/release-verification.json及docs/releases/2026-11-11-alchemist-table.json，使用说明docs/ALCHEMIST_TABLE.md，初始注册/语言/启动器源码备份build/alchemy-table/source-before.zip。

MASTER=0；只跑隔离原生单客户端，Oculus/Embeddium存在但未开光影，非完整整合包/独立服务器/真实双客户端/主观听音验收。旧映射、Embeddium与Oculus模板警告仍在，新增方块使隔离旧存档注册ID重新映射属于预期提示。未改正式实例，未安装、提交或推送；已有未提交时间/领域/东方等工作全部保留。六张最新原生截图及SHA清单在集中建模目录export/preview-20261111-native，Blender预览不能当实机渲染。


## 2026-11-11：按用户要求同步GitHub

用户授权上传当前项目进度。已先fetch确认origin/main与本地基线d572ddd一致，无需合并；将炼金术士台、此前未提交的时间/东方/领域/环境队列/陨灭杀/守护灵修复及对应资源、测试和文档一并提交为f8f1fbfca866dc6370ae61d66ee1868a280263ac，共112个文件。已成功普通推送origin/main，没有强推。仓库 https://github.com/kkskkkcc/SourceOfMystery-1.0 。本节取代前文“未提交/未推送”的当时状态；历史发布JSON中的pushed_to_github字段保留为生成时快照。

本次仅Git同步，没有重新游戏测试、安装到正式实例或发布GitHub Release。仍使用前次构建和静音实机验证的df23c044ebe6候选（协议17）；build/安装包/隔离世界/日志保持本地忽略，生产源码和运行资源已入库。此后的文档同步提交只记录本次上传状态，不改变模组产物。
