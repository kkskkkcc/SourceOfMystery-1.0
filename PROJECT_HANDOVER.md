# 神秘之源项目接手记录

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
- 本地新增守护之灵：按已加载村民识别村庄区域，并用维度 SavedData 保留每片区域的守护名额，死亡时释放，下一轮检查后重生。基础生命 3000、攻击 50、护甲 50，每秒回血 5。服务端统一驱动村内漂浮/落地巡逻、悦灵跟随、友好目标排除、村民治疗、爱心动作、追踪蓝球和法阵闪电。

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
| 神威天佑胸甲神环 | 已修复渲染与贴图，等待重新游戏内验收 | 穿戴时胸口背后保持明显间距，显示四层带缺口、半透明法阵纹路神环并循环旋转，中心清晰显示“神”字；脱下胸甲后特效立即消失 |

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
