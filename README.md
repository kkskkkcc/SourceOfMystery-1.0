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
| 前置依赖 | GeckoLib 4.8.x（必需） |
| 许可证 | MIT |

## 特性

- **神秘祭坛（Mystery Altar）**：基于祭坛配方的合成系统，支持多阶配方（Tier）。
- **神秘能量系统**：能量 Capability，附带 HUD 能量条与网络同步。
- **多种套装与武器**：神秘套、暗源套、神罚剑、神佑套、源龙套、灵源套等，均附带粒子特效与套装效果。
- **Boss：神圣天道（Divine Heavenly Dao）**：使用 GeckoLib 模型的强大 Boss，包含生成机制与击杀成就。
- **成就系统**：一系列引导玩家探索模组内容的进度成就。
- **矿物与世界生成**：神秘源矿（Mystery Source Ore）及其自然生成。
- **多语言**：简体中文（`zh_cn`）与英文（`en_us`），所有提示信息均走翻译键。

## 调试指令

```
/somenergy <玩家> <数值>
```

需要 OP（权限等级 2），把目标玩家的神秘之能和上限同时设为该值（1–1000000），支持 `@a` 等选择器。

## 代码结构

| 包 | 职责 |
| --- | --- |
| `capability/energy` | 神秘之能（存于玩家 NBT）、每日回满、击杀奖励 |
| `block` / `recipe/altar` | 神秘祭坛及其配方（按 S+ → D 优先级匹配） |
| `item` | 剑（`MysterySwordItem` 为基类）、胸甲材料、`ChestplateEffectHandler` 套装效果、环绕粒子 |
| `entity` | 神威天道 Boss 及其召唤流程（状态存于末地 SavedData） |
| `client` / `hud` / `network` | 渲染、HUD、能量同步包 |

## 构建

环境要求：**JDK 17**。

```bash
./gradlew build
```

Windows 下也可直接运行：

```bash
gradlew.bat build
```

构建产物位于 `build/libs/sourceofmystery-1.0.0.jar`。

## 安装到游戏

1. 安装 Minecraft Forge 1.20.1（47.2.0）以及 [GeckoLib](https://modrinth.com/mod/geckolib) 4.8.x。
2. 将 `build/libs/sourceofmystery-1.0.0.jar` 放入游戏的 `.minecraft/mods/` 目录。

## 许可证

本项目采用 [MIT License](LICENSE) 开源。
