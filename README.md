# Source Of Mystery（神秘之源）

一个基于 Minecraft Forge 的 1.20.1 模组，为游戏带来神秘的祭坛、能量系统、强化装备与强大的 Boss。

## 模组信息

| 项目 | 内容 |
| --- | --- |
| 模组名 | Source Of Mystery（神秘之源） |
| Mod ID | `sourceofmystery` |
| 游戏版本 | Minecraft 1.20.1 |
| 模组加载器 | Forge 47.2.0 |
| Java 版本 | Java 17 |
| 前置依赖 | GeckoLib 4.8.x（必需）；JEI、Patchouli（可选） |
| 作者 | Kara251、Ba1Na1 |
| 许可证 | MIT |

## 特性

- **神秘祭坛（Mystery Altar）**：基于祭坛配方的合成系统，支持多阶配方（Tier）。
- **神秘能量系统**：能量 Capability，附带 HUD 能量条与网络同步。
- **多种套装与武器**：神秘套、暗源套、神罚剑、神佑套、源龙套、灵源套等，均附带粒子特效与套装效果。
- **Boss：龙魂（Dragon Soul）**：末影龙的原版死亡动画完全播完后，在它消失的位置裂开一道虚空裂缝（模型渲染，内部是星空虚空），龙魂化作残影高速冲出、雷鸣中盘旋两圈后降到玩家面前单手持枪相指并喊出台词（出场期间镜头自动对准她）。魂类战斗：瞬移贴脸通常有蓄力和落点预警，但也会突然无前摇瞬移直接出快招；劈 / 刺 / 砍（20 / 15 / 10，均为范围判定）、踢飞 10 格、抓上 20 格高空抛下（玩家吃东西 / 喝药时必定大前摇瞬移过来抓）、法阵五连龙息、掷枪回旋，出招后按概率转枪 / 挑衅 / 喘息作为反击窗口。大招「龙魂解放」：每次出招 20% 概率触发（冷却 2 分钟），瞬移贴身 5 秒乱舞、每秒 3 击、期间无敌，被打中的玩家陷入黑暗。有日语女声台词，说话时有对应表情和口型，动作栏显示中文字幕。和末影龙一样会被末地水晶治疗（出场时柱顶水晶重生）。
- **Boss：神威天道（Divine Heavenly Dao）**：手持龙魂、背包备齐五行之源，右键神秘祭坛召唤；倒计时期间金色光点不断汇入祭坛，结束后天上展开金色法阵，神威天道伴着漫天雷霆从天而降（镜头自动跟随）。10000 生命、约 50 格高，存在期间播放威压音乐；撼地锤（50 伤害并把地面实体抛上 20 格）、天雷（5 秒内 18 道，每道 100，至少一道必中玩家，其余劈向周围生物）、天威一击（50）；大招「天渊」：双手高举凝聚黑球 10 秒后掷向玩家，引发巨大爆炸（地形破坏遵守 mobGriefing）。第二阶段召唤末影龙大小的巨型凋灵，凋灵存活期间 Boss 受到的伤害减半。
- **五行之源**：木（玩家击杀僵尸 20%）、金（玩家击杀铁傀儡 20%）、火（玩家击杀烈焰人 20%）、土（玩家挖泥土 5%）、水（下雨时露天打水 10%），召唤神威天道各需一个。
- **胸甲卫星**：暗源之甲（1 颗）、始源龙甲（2 颗）、神威天佑外环（5 颗）的卫星会自动攻击 20 格内的敌对生物和攻击过穿戴者的实体（含可 PvP 的玩家），穿墙飞向目标并以加粗粒子环绕、每秒造成伤害，返回后冷却。默认数值：暗源 10 伤害 / 5 秒 / 冷却 10 秒；始源 15 / 5 秒 / 8 秒；神威天佑 20 / 8 秒 / 5 秒，每次出击消耗 10 点神秘之能。
- **神威天罚天雷**：神威天罚打不死目标时消耗 100 点神秘之能，目标脚下出现法阵，1 秒后落雷造成 200 点无视护甲的真实伤害。
- **宝箱战利品**：秘源锭出现在和钻石相同的宝箱里，概率和数量也与钻石一致（例如废弃矿井约 9%、埋藏的宝藏约 53%）。
- **龙魂**：击败龙魂 Boss 掉落，用于合成始源龙剑 / 始源龙甲，以及在神秘祭坛召唤神威天道。
- **突破原版护甲上限**：原版护甲值最多 30、减伤最多 80%，本模组提高了上限，并对超出 30 点的护甲追加减伤，高阶胸甲的护甲值因此真正生效。
- **成就系统**：一系列引导玩家探索模组内容的进度成就。
- **矿物与世界生成**：神秘源矿（Mystery Source Ore）及其自然生成。
- **多语言**：简体中文（`zh_cn`）与英文（`en_us`），所有提示信息均走翻译键。
- **可选联动**：安装 JEI 可查看所有祭坛配方；安装 Patchouli 可用"书 + 秘源锭"合成《神秘之源指南》。

## 配置

首次启动后生成配置文件：

- `config/sourceofmystery-common.toml`：神秘之能（初始值、击杀奖励、每日回满）、神威系列消耗、三档胸甲卫星（索敌范围、伤害、持续时间、冷却、消耗）、护甲上限与额外减伤、Boss 生命与二阶段阈值等。
- `config/sourceofmystery-client.toml`：HUD 位置、边距、低能量变红阈值。

所有默认值与原设计一致。

## 调试指令

```
/somenergy <玩家> <数值>
```

需要 OP（权限等级 2），把目标玩家的神秘之能和上限同时设为该值（1–1000000），支持 `@a` 等选择器。

## 代码结构

| 包 | 职责 |
| --- | --- |
| `energy` | 神秘之能（存于玩家 NBT）、每日回满、击杀奖励 |
| `block` / `recipe` | 神秘祭坛及其数据驱动配方（`sourceofmystery:altar` 配方类型） |
| `item` | 剑（`MysterySwordItem` 为基类）、胸甲（`MysteryChestplateItem`）、`ChestplateEffectHandler` 套装效果、环绕粒子、`SatelliteAttackHandler` 卫星攻击、`DivineStrikeScheduler` 神威天罚天雷 |
| `combat` | 突破原版护甲上限（`ArmorLimits`） |
| `entity` | 龙魂与神威天道两个 Boss（技能、出场动画、召唤流程，状态按维度存于 SavedData）、龙魂之枪、巨型凋灵 |
| `loot` | 全局战利品修改器（按钻石的爆率往宝箱追加秘源锭、五行之源掉落）、雨中打水得水之源 |
| `sound` | 模组音效（`sounds.json` 复用原版音频并调整音高）；龙魂语音见下方致谢 |
| `particle` | 自定义粒子（汇入祭坛的金色光点、凝聚天渊的黑色烟尘） |
| `config` | 配置文件定义 |
| `client` / `hud` / `network` | 渲染（含空间裂缝、金色法阵、天渊黑球、龙魂残影）、HUD、Boss 出场镜头与威压音乐、闪白 / 震屏、能量 / 卫星 / 出场同步包 |
| `compat/jei` | JEI 祭坛配方页面（可选依赖） |
| `datagen` | 数据生成器 |

## 构建

环境要求：**JDK 17**。

```bash
./gradlew build
```

Windows 下也可直接运行：

```bash
gradlew.bat build
```

构建产物位于 `build/libs/sourceofmystery-1.0.0.jar`。每次推送和 PR 都会由 GitHub Actions 自动构建。

### 数据生成

方块状态、物品模型、战利品表、方块标签和所有配方（含祭坛配方）由数据生成器产出，位于 `src/generated/resources`，**不要手改**，改 `com.sourceofmystery.datagen` 下的代码后运行：

```bash
./gradlew runData
```

语言文件、成就、祭坛方块模型和 Patchouli 指南书仍在 `src/main/resources` 中手写维护。

### 祭坛配方格式

祭坛配方是普通的数据包配方，可以用数据包增删改：

```json
{
  "type": "sourceofmystery:altar",
  "tier": "S+",
  "ingredients": [
    { "ingredient": { "item": "minecraft:nether_star" }, "count": 2 }
  ],
  "result": { "item": "sourceofmystery:divine_blessing_chestplate" }
}
```

`tier` 取 `S+`、`S`、`A+` … `D`，多个配方同时满足时优先合成等级高的。

## 安装到游戏

1. 安装 Minecraft Forge 1.20.1（47.2.0）以及 [GeckoLib](https://modrinth.com/mod/geckolib) 4.8.x。
2. 将 `build/libs/sourceofmystery-1.0.0.jar` 放入游戏的 `.minecraft/mods/` 目录。

## 许可证

本项目采用 [MIT License](LICENSE) 开源。

## 致谢

- 龙魂的日语语音由 Open JTalk 使用 HTS 语音 "Mei"（© 2009-2013 名古屋工业大学，MMDAgent Project Team，[CC BY 3.0](https://creativecommons.org/licenses/by/3.0/)）合成并经过后期处理，详见 `assets/sourceofmystery/sounds/voice/CREDITS.txt`。
