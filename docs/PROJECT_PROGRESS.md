# 项目进度（2026-11-08）

本地法杖进度与工程师的装备 V4／登神 CG V5 已合并。源码同步与正式游戏安装分别记录，最新状态见文末；以下较早日期的实装记录属于历史，不能代表当前源码已经安装到游戏。

## 2026-11-06 实装记录（历史）

- 神威天佑内环使用黑白 GeckoLib 星芒，保持闪烁、移动渐隐和停稳渐显。
- 外环五颗恢复原绿、黄、蓝、红、橙粒子及出击、环绕、返回拖尾。
- 攻击、索敌、能量、冷却、服务端同步与原实现保持；网络协议仍为 8。
- 神威天佑胸甲、背部神环、登神 CG、祭品抽奖和附魔台等已有内容继续保留。
- 正式 JAR：40,732,572 字节，SHA256 `d73ce1d5d75b31b8cf6313155922c4f93747ec406931b8e171da552adf303f37`。

安装目标为 `Integrated MC` 和 `1.20.1-Forge_47.4.20` 两实例。两个目标文件均已逐个核对哈希；升级前旧包保存在本地 `build/release-backups/`。

## 本次同步保留的已有进展

| 模块 | 状态与说明 |
| --- | --- |
| 黑暗次元祭品 | GeckoLib 可放置展示模型；原传送门召唤入口保持 |
| 祭品抽奖 | 投入物品估价、1000 神秘之能费用、结果音效、好物概率相对提升和幸运影响；见 `OFFERING_LOTTERY.md` |
| 神秘附魔台 | 配方、可配置能量计费、运行时附魔注册表索引与退魔；见 `MYSTERY_ENCHANTER.md` |
| 神秘之能 | 每 10 秒恢复当前玩家上限的 1% |
| 神威天佑 | v3 白玉金纹胸甲与立体物品模型；见 `DIVINE_BLESSING_ARMOR.md` |
| 装备与武器 v4 | 五件胸甲、五把剑为 GeckoLib 轻甲模型；见 `GEAR_MODELS.md` |
| 域外魔导师 v2 | 受击反应、环绕走位、闪现、招式选择、弹幕与落柱预警、第二阶段；见 `OUTSIDER_COMBAT.md` |
| 登神 CG | 首次穿戴、成就、地表到宇宙升空、NASA 地球与灰白王座；v3 改为关键帧人物动作、运镜与 GeckoLib 天阶；v4 重做音效；v5 按几何求解手、脚、座位与神甲和王座的接触，CG 中空手；见 `DIVINE_ASCENSION_CINEMATIC.md` |
| 周身卫星 | 本次实装为内环星芒模型、外环原粒子；见 `DIVINE_SATELLITES.md` |

## 验证依据与边界

静音隔离 Forge 客户端已检查五颗外环待命粒子、出击时跳过待命位置、原忙碌位同步、实际攻击伤害，以及内环显隐和脱甲清理。一次隐藏内环期间的真实目标生命从 1900 降为 1600。

本次在整理主源码后，清理了编译类和资源输出缓存，使用 Java 17 / Gradle 8.5 离线重新构建。重建包与实际安装包的所有生产条目名称及内容逐字节一致，没有新增、缺失或变化条目。ZIP 封装元数据可能使整个文件哈希不同，不应与条目内容一致混淆。

验证摘要随库保存在 `releases/2026-11-06-inner-star-satellites.json`。本地原始构建日志、客户端截图和逐条审计在 `build/`，不作为源码提交。此次未重跑完整整合包、开启光影或真实双客户端联机，不据此宣称完整兼容。

## 未通过与撤回的设计

- 五把剑、四件胸甲的早期 GeckoLib 草稿被用户拒绝，保存在 `drafts/equipment_gecko/`。v3 为重新设计，未使用这些草稿。
- 外环球体、古文字贴图及缩小版本已撤回；古文字源文件保存在 `drafts/divine_satellites/`。
- `drafts/` 不属于任何 Gradle source set。归档仅用于接手和记录，不能视为验收成品；后续由用户逐件指挥重新设计。

默认 `gradlew build` 生成当前合并源码，包含后续工程师更新和新增法术，与上述历史实装包不同；`drafts/` 中撤回的草稿不会重新启用。保留原物品 ID、属性、配方与效果。


## 2026-11-07：环境转变开发

法杖新增环境转变，按最新指令直径 1000 格，潜行右键循环当前注册的 64 种原版生物群系，同时重塑自然地表与植被。默认吟唱 4 秒、消耗 2000 神秘之能，可在原法杖服务端配置调整。多圈轮盘绘制与命中共用布局。操作、保护边界和性能限制见 [STAFF_ENVIRONMENT.md](STAFF_ENVIRONMENT.md)。

用户否决第一版圆环/粒子表现后，按网络现成高清符文素材和 Magic Circles / Mahou Tsukai 的实际图例重做：手前直立法阵、地面符文带、六节点依次点亮、连续光流汇聚和释放回收动作。外部实际嵌入素材为 CC0，原图和来源保留；其他模组只参考构图，无新增模组依赖。

真实静音隔离客户端已完整完成半径 500 沙漠转换，验证 64 群系切换、多圈命中、取消/扣费、存档恢复、范围边界、建筑/箱子保护和客户端群系同步；海洋与丛林另用恢复半径 50 的任务检查相同处理器。视觉回归分别检查原版渲染与 Oculus + Complementary 的六主题第一/第三人称、实际释放，输出静音实机视频。最终证据以 `build/staff-environment-release-checks.json` 为准，主观外观待用户验收，完整整合包、真实双客户端和所有维度/群系的地形组合未重跑。

本次未安装或推送 GitHub。核对时 Integrated MC 仍为 d73ce1d5... 的已批准卫星版本；1.20.1-Forge_47.4.20 实际已由先前外部操作更新为 00ff0433... 的工程师源码版本，两者均尚无本次环境转变功能。历史“两实例均 d73...”记录已过时。源码基线仍 b92ff09，普通构建含已合并的工程师装备 V3、登神 CG 与域外魔导师 V2，不能把新候选包自动替换到整合包。


## 2026-11-07：领域展开候选

按最新确认，极寒、黑暗、重力、定身和斩杀均直径200格，默认5000能量、持续5分钟，可配置；定身固定地点，其余随玩家移动，右键可提前停止。斩杀开场CG10秒只接管施法者摄像头，CG期间无敌、法杖隐藏，人物结印/面部近景后切身后高处FOV130与持续红黑斩击。GeckoLib原创祭龛、五种领域效果、真实伤害、状态恢复与区块碎块清理已接入。操作和兼容边界见 [STAFF_DOMAINS.md](STAFF_DOMAINS.md)，协议现为10。

真实静音客户端已通过完整6000tick自然到期，最终原版/光影两次短回归通过并核对所有生产条目一致；五分钟运行之后仅调整两个客户端镜头/演员类，服务端功能字节保持。人工看过最终近景和全景，修正过临时玩家模型默认幼体比例。构建与审计见 `build/staff-domain/actor-correction-build.log`、`build/staff-domain/release-verification.json`；完整整合包、真实双客户端与主观声音仍待验收，外观待用户确认。

当前候选JAR SHA256 `b4bf4fb0b524eb2541886fd05ecec92e7b7a44bc8961b85b4ca8adb93296a08c`，54,325,771字节，未安装或推送。既有资源、环境转换、旧法杖七段动画保留，测试代码不进入生产包。最终10秒静音实机视频和五领域截图集中在 `F:/ChatGPT/我的世界神秘之源模组开发/modeling/staff_domains/export/`；视频600帧完整解码通过，60fps仅为编码参数。


## 2026-11-07：陨灭杀准心索敌候选

改为128格准心射线选择实体/区域，固体遮挡有效；仅施法者看到白色轮廓、地面区域符文与墙面落点光标。连续瞄准同一实体40tick后红色锁定，释放后陨石持续追踪该UUID至落下，转动准心不会改换飞行目标；未锁定则固定当时落点，无有效目标不发射且不扣费。费用、24秒吟唱和原伤害/音画保持。详见 [STAFF_METEOR_TARGETING.md](STAFF_METEOR_TARGETING.md)，全局网络协议现为11。

最终原版和Oculus+Complementary真实静音检查通过，验证远移130格后的实际落点/伤害、旧区域不受影响、状态清理与本机轮廓；所有生产条目与两实测包相同。夹具吟唱缩为60tick，生产默认480tick未变；完整整合包和真实双客户端隐私仍未测。构建/审计在 `build/staff-meteor-targeting/`，GPU原图在 `art/staff_meteor_targeting/`。

最新候选SHA256 `1ddf3b23fbda57da1788cca0c4fcc112cab279951e58125bc3d2e4913b6967e7`，54,344,554字节，副本 `build/staff-meteor-targeting/sourceofmystery-targeting-1ddf3b23fbda.jar`。尚未安装或推送；原环境、五领域、装备/Boss资源及先前未提交工作保留。上个领域候选归档至 `build/staff-domain-candidate/`。


## 2026-11-08：天气魔法候选

法杖追加「天气掌控」，L选术，Shift＋右键循环晴天、雨天、雷暴和巨型龙卷风，长按右键3秒释放。可配置默认：天气2000能量／10分钟，龙卷风5000能量／5分钟，再按右键可结束。四种环境变体统一半径100格、每秒5点基础伤害，包含真实卷吸、提升、碎块和对应特殊效果，原版天气仍使用原世界天气系统。风柱、尘墙、子涡旋、软云底与四套风声已接入；按地表和可扩展群系标签判断普通、沙暴、水、火。详见 [STAFF_WEATHER.md](STAFF_WEATHER.md)，网络协议12。

原版渲染与Oculus+Complementary最终两次真实静音客户端通过，生产859条目与实测包完全相同；保护事件、范围边界、实际伤害、清理及短配置自然到期均已检查。默认6000tick仅核对初值，未等满5分钟；完整整合包、真实双客户端和主观听感未测。中间光影材质屏蔽及混用通道的GL错误已修正，失败证据保留，不能当最终验收。外观待用户确认。

候选 `build/staff-weather/sourceofmystery-weather-af5c053a3ebd.jar`，SHA256 `af5c053a3ebdc3839ed98dfdd1b2e6746475d9e066d4f15be01d7888f63ffb16`，54,821,823字节。审计 `build/staff-weather/release-verification.json`；保留旧模型、八段法杖动画及环境／领域／索敌／Boss逻辑。素材和16秒静音实机预览集中 `F:/ChatGPT/我的世界神秘之源模组开发/modeling/staff_weather/`，视频960帧完整解码，60fps为编码参数。未安装、未提交或推送，两个正式实例保持核对时的旧版本。


## 2026-11-08：本地进度提交与工程师更新合并

按用户授权，本地四项法杖进度先保存为5317ca2，与工程师8b7c671（装备V4／登神V4声音）和011821c（登神V5接触动作与剑柄握持）合并。当前源码同时包含双方进展，原候选“未提交”的说明保留为历史记录。仅两语言文件有文本冲突，按JSON键合并并保留双方所有有效改动，音效注册同样核对；工程师的新模型／CG没有被本地旧资源覆盖。

合并后离线build、shaderCheckJar及GameTest源码编译通过，864个生产条目、203个JSON核对通过，385个无关生产class与337个未变资产保留。未重跑游戏／完整整合包或安装新JAR，构建通过不代替合并后模型和CG验收。当前构建56,132,745字节，SHA256 `fd7f1e1befeed41984dc03120755257a717ac99bfdc05f9b752da187e4b93230`，协议12。摘要 [2026-11-08-staff-engineer-merge.json](releases/2026-11-08-staff-engineer-merge.json)，本地原始备份与最终同步回执在 `build/github-sync-20261108-180714/`。正式实例安装状态保持之前记录。


## 2026-11-08：环境转变覆盖修复候选

修复旧异步转换未结束时拒绝所有重叠施法的问题。新施法拥有重叠范围，旧任务只完成范围外剩余部分；完整接替释放旧票据并移除任务。覆盖关系随Version3保存，旧任务恢复后不回滚新环境，Version2任务游标兼容。区块票据与前沿效果使用独立任务UUID。四任务上限保留，先释放完整接替的旧任务名额。

构建及真实静音单客户端已验证原生连续吟唱／扣费、R500准入、地表与客户端／服务端群系覆盖、新任务结束后恢复旧任务、非重叠继续、保护和断点迁移。完成改造的处理器回归使用半径48，不代表重跑两次完整千格改造；光影、完整整合包及真实双客户端未测。审计 `build/staff-environment-overwrite/release-verification.json`。

修复包 `build/staff-environment-overwrite/sourceofmystery-environment-overwrite-bd0a88439669.jar`，SHA256 `bd0a88439669c0549c408d8521d4afc430d0189aff1cdc922b44d8c349034113`，56,135,704字节。与fd7合并包相比只改两环境逻辑class／两条繁忙文案并新增Area记录，其余860条目保持。尚未安装、提交或推送，Github仍为此前43a6591；默认build输出已是修复候选。

## 2026-11-08：环境覆盖修复同步提交

当前同步提交包含环境转变覆盖修复、回归夹具及审计工具，沿用此前已通过的构建和静音实机证据。摘要[2026-11-08-environment-overwrite.json](releases/2026-11-08-environment-overwrite.json)记录候选哈希、生产条目保持检查及半径48处理器回归的限制。前节未提交状态属于历史快照；推送和Actions的最终状态见GitHub及`build/github-sync-20261108-185544-environment/final-sync-receipt.json`。本次未安装游戏实例。

## 2026-11-08：时间魔法分类与维度倍率

轮盘时停入口现为「时间魔法」，Shift＋右键选择时间停止、时间延缓0.1倍、时间加速5倍。新增模式的所有玩家保持正常速度，当前维度的完整模拟及Forge维度tick事件按倍率；旧时停保存ID／序号及偏移数据兼容。默认1000能量、12秒、恢复后3秒共享冷却，新增时长／费用可独立配置，右键可提前结束。网络协议13。

构建与真实静音客户端回归通过：100正常服务器tick对应世界／生物／天气／熔炉0、10、500tick，玩家正常100tick；另一维度保持100tick。红石、客户端实体计数、原生Shift切换、扣费、到期／冷却、无补跑跳时、过期消息拦截及FakePlayer观察者豁免已查。没有真实双客户端、主动跨维度施法、完整整合包或开启光影的本轮证明。联网与必要区块IO保持正常，独立线程和第三方全局计时不保证受控。

说明[STAFF_TIME_MAGIC.md](STAFF_TIME_MAGIC.md)，摘要[2026-11-08-time-magic.json](releases/2026-11-08-time-magic.json)。候选`build/staff-time/sourceofmystery-time-magic-607386a2ce7a.jar`，SHA256 `607386a2ce7ac2f94e08c9ba89968875cb394672d922df8a2f9d76fe2897d7e4`，56,150,915字节；871个生产条目与实测包相同，845旧条目／382个非语言资产保持。源码基线d572ddd；本次未安装、提交或推送。

## 2026-11-09：来自东方的术／粒子神门／嵌套纯白球

法杖外圈第九项，60tick蓄力、5000能量；五门为橙红发光粒子并保持固定。按用户最新要求，CG在发射后0.4秒收镜，总12.4秒，法球继续独立飞行；内球纯白不透明、外球透明，不带铭文或球下纹路。发射后三主三细灰色流星丝带沿飞行方向持续螺旋、变细变淡，最大拖尾46格。255射程从发射球心计算，1000单次伤害后带实体继续飞，撞不可破坏或受保护方块提前终止并恢复实体重力。

离线构建及最新真实静音无光影回归通过：输入／扣费、CG恢复、255射程、伤害／裹挟／重力、连续碰撞、基岩／BreakEvent保护和无掉落；实际渲染帧中位17.2669ms、P95为21.6534ms。最新光影启动因native／graphics内存分配失败，未进入施法，不能冒称最新光影通过。外观待用户验收，未做双客户端／整合包／主观音效验收。没有安装或推送。

候选build/eastern-rite/sourceofmystery-eastern-rite-0163871b553e.jar，SHA256 0163871b553e2d5cc0dab86dd601090a5894dec0f5c7062927268a77695c182a，894生产条目与实测包一致、380旧资产保持。详情[STAFF_EASTERN_RITE.md](STAFF_EASTERN_RITE.md)及[发布摘要](releases/2026-11-09-eastern-rite.json)。最新静音视频与截图位于F:/ChatGPT/我的世界神秘之源模组开发/modeling/eastern_rite/export/preview-20261109-white-ribbons，旧否决方案仅保留drafts。
## 2026-11-09：10格实心球、环境队列和陨灭杀保持锁定

后续用户反馈已合并处理：纯白实心内球从8.6改成半径10格（直径20），透明外壳11.6，灰色丝带根部随外形外移。服务端碰撞半径保持原10格。旧export/preview-20261109-white-ribbons的录像仍是8.6内球，未重录本次尺寸，不可当10格新版外观证据。

环境转变原有4个未完成任务硬上限导致千格任务积压后无法再施法。现在所有有效施法可准入，新的任务置于队首优先处理；最多4个任务处理并保有独立区块票据，其他任务释放临时票据、保留游标并排队。新范围覆盖旧范围的让位关系不变，不丢弃旧区域剩余工作；SavedData原格式直接保存队列顺序、覆盖和进度，旧世界无需清任务。共用3ms预算，原地覆盖和部分重叠规则继续。

陨灭杀原来服务端每tick按新射线覆盖Selection，客户端也只画当前准心对象。现在完成40tick红色锁定后保留已锁定实体，移开准心／看天空／蓄力及释放均保持，服务端重新验证实体并更新实时位置。另一个实体持续瞄准40tick可换锁；死亡／移除／离维度／超128格、切换法术或收起法杖清除待发锁定。已发出陨石继续原UUID追踪，不因后续瞄准变化改目标。仅PLAYER同步的格式不变，全局协议仍14，服务端和客户端均需更新本次包。

build/staff-followup-fixes/build-fixes.log的离线build shaderCheckJar compileGameTestJava通过。真实静音无光影queue-native通过R500新施法准入、第五第六处扣费准入、存档队列顺序／覆盖／游标、旧版本迁移、票据<=4、队列末任务提升并完成，以及旧覆盖修复的地表／客户端服务端群系／建筑容器保护。完整处理使用R48覆盖工作和R4队列工作，没有重跑六次完整千格地形。sticky-lock-native通过白轮廓／40tick红锁、移开看天仍红、蓄力与释放保留原实体、2秒换锁、目标超距释放待发锁但已发陨石继续追踪、原实体实际落点与伤害、区域／空目标不扣费、目标死亡最后位置、切法术清理。测试吟唱60tick，生产480tick未改。两次MASTER=0；未查光影、完整整合包或真实双客户端。

稳定候选build/staff-followup-fixes/sourceofmystery-staff-fixes-7c1db7d10b67.jar，56,419,402字节，SHA256 7c1db7d10b67601c6fad9bcbafed378f5f007e1ce3508268bced37e53caa2543。894生产条目与上述两实测包完全相同，相对0163871b553e仅7个逻辑class改变，392资产全部保持。编译产物javap常量确认CORE_RADIUS=10、SHELL_SCALE=1.16。审计build/staff-followup-fixes/release-verification.json；公开摘要docs/releases/2026-11-09-staff-followup-fixes.json，工具tools/staff/audit_followup_fixes.py。新增测试启动参数--heap-mb2048仅影响隔离测试堆内存。原源码备份在build/staff-followup-fixes及build/eastern-rite/source-before-radius10.java。本次未安装、未提交或推送，已有未提交工作保留。

## 2026-11-09：法杖交互与范围修复

东方之术取消CG/操作锁，杖尖白银能流与旋转刻纹绑定GeckoLib骨骼；完整yaw/pitch支持全方位和上下释放，五门/双层白球/灰色丝带一致定向。斩杀覆盖整个半径100圆形领域，有存活有效生物才播放目标斩击和扫击声，掉落物/投射物排除。时间三模式无限持续，右键解除；生命周期清理和结束后冷却保留，旧持续时间配置不再生效。协议15，两端同版。

离线build shaderCheckJar compileGameTestJava通过；静音gameplay-execution-native验证八种方向、球体旧物理、360同伤害/边界/空场/掉落物；indefinite-native验证三模式超过旧240tick到400tick再实际右键解除，倍率、冷却和无补跑保持。仅原生客户端、无光影，非完整整合包/真实双客户端。外观待用户验收，未安装/推送。

候选build/eastern-gameplay-execution/sourceofmystery-staff-gameplay-6f98b85b8ecf.jar，SHA256 6f98b85b8ecfbf335d42817367d2de216d5aa21761a8c7e8a75430b7bec72ab1。891生产条目与两次实测相同，390非语言资产保持。审计build/eastern-gameplay-execution/release-verification.json，公开docs/releases/2026-11-09-staff-gameplay.json。新24秒静音预览位于modeling/eastern_rite/export/preview-20261109-gameplay（集中建模目录），旧含CG预览仅历史保留。详见PROJECT_HANDOVER末节。


## 2026-11-09：守卫去雷击／东方4秒内发射／斩杀按伤害触发

GuardianSpirit取消祈祷雷击及脚下法阵，保留追踪球；源码/编译/字节码核对，未单独村庄实测。东方默认蓄力20+世界58tick，旧castTicks60自动限20，原生静音实测78tick（20TPS下3.9秒）；持续按键不重复busy。斩杀以实际生命/吸收损失结算发送DomainSlashPacket，8tick残影与扫击同步，取消伤害、空场、掉落物及两轮伤害之间不空斩。协议16。

最终fast-release-hit-sync-final-native通过78tick、9轮真实伤害同步、取消伤害无FX，及之前球体物理/方向/保护/360领域回归。候选build/eastern-fast-release/sourceofmystery-combat-timing-7cb185d22822.jar，SHA256 7cb185d228222a593c56bab074023ce77a1e1604210546864b4919fe3ae524b6；894条目与实测相同，392资产保持。build/eastern-fast-release/release-verification.json和docs/releases/2026-11-09-combat-timing.json含边界。新14秒静音预览在集中建模目录eastern_rite/export/preview-20261109-fast-release。未安装/推送，非光影/整合包/真实双客户端验收。

## 2026-11-11：炼金术士台

新增准确九宫格配方、手持右键/潜行放置/放置右键、每天20种材料兑换商品。使用主世界游戏日，同日共用清单和报价存盘；静态配方及Forge标签自动关联下级材料，金块1–9金锭。界面悬停完整材料/持有数量，左键服务端原子兑换，满包/缺料/过期报价不扣，未额外收神秘之能。GeckoLib黑曜石金边、双环祭品核心、药瓶和账本模型；外观待用户确认。协议17。

离线build shaderCheckJar compileGameTestJava及最终静音final-native通过，含774候选/外部配方、交易与NBT保护、跨日/存档、放置挖取和便携有效性。实际SavedData存盘20件已读取，未重启验收。候选build/alchemy-table/sourceofmystery-alchemy-df23c044ebe6.jar，SHA256 df23c044ebe64af90a5c133e8d0777a89388e4bc110188d481c5e4b959b7cd4d，57,256,050字节，920条目与实测一致，旧非语言资产390条完整保留。详见[功能说明](ALCHEMIST_TABLE.md)和[发布证据](releases/2026-11-11-alchemist-table.json)。模型源和六张实机截图位于集中建模目录modeling/alchemist_table。无光影/完整整合包/独立服务器/真实双客户端验收；未安装或推送，正式实例未改动。


## 2026-11-11：GitHub同步

按用户授权，当前炼金术士台和之前未提交的法杖/时间/领域修复已提交并推送origin/main，功能提交f8f1fbfca866dc6370ae61d66ee1868a280263ac（112文件）。远端fetch后无需合并，未强推。仓库 https://github.com/kkskkkcc/SourceOfMystery-1.0 。此前各发布回执的“未推送”是生成时状态，以本记录为准。只同步源码/运行资源/测试/文档，build忽略项和正式实例保持原状，未另发GitHub Release或重跑游戏。
