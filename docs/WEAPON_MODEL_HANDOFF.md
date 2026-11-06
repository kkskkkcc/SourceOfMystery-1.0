> 本文记录已被拒绝的草稿。当前装备与武器模型为 v3，见 [GEAR_MODELS_V3.md](GEAR_MODELS_V3.md)。

# 武器模型交接：用户拒绝当前草稿

最新位置（2026-11-06）：为同步当前正式源码到 GitHub，未通过的五把剑和四件胸甲已完整隔离到 `drafts/equipment_gecko/`，保留其原相对路径、可编辑模型、生成器和清单。主 `src/` 已恢复原装备实现；下文的旧活动路径应在该归档目录内查找。默认构建不再包含这些草稿。当前实装与验证状态见 `PROJECT_PROGRESS.md`。

记录日期：2026-11-06。

## 当前决定

用户明确表示“武器模型不合格，不允验收”，随后要求“做不了武器模型就别做了我交给别的模型”。本窗口已经停止武器建模、修改和发布，不继续替用户选择新的设计方向。当前武器资产是被拒绝的草稿，不得作为合格成品交付。

用户最新设计要求：不再保留像素元素，只按装备的等级和名称重新设计。神威天佑胸甲、神环和登神 CG 本次排除，不得修改。后续测试客户端默认静音。

## 发布状态

本批装备改动仅存在于工作目录，没有安装到游戏实例。两处正式模组于交接时实际核对，SHA256 均为：

`c5f6a2b6a8e1ee19e4073e30f9a4d4217d2f9b3b18b43933fa494a60d5e13bf3`

- `F:\AAAAmc\versions\Integrated MC\mods\sourceofmystery-1.0.0.jar`
- `F:\AAAAmc\versions\1.20.1-Forge_47.4.20\mods\sourceofmystery-1.0.0.jar`

`build/libs` 和 `build/reobfShaderCheckJar` 中可能存在本批草稿构建，不能将其误认为已经批准的发布包。工作目录已有大量历史改动，不要使用全局 reset、checkout 或 clean 撤销其他工作。本次没有撤回或删除草稿文件，便于接手者检查。

## 草稿范围与入口

五把剑：`mystery_sword`、`spirit_source_sword`、`dark_source_sword`、`origin_dragon_sword`、`divine_punishment_sword`。

共同生成器：`tools/equipment/generate_equipment.py`。它同时生成四件胸甲和五把剑；直接运行会覆盖两类草稿资产，不是仅生成武器。

资产位置：

- `src/main/resources/assets/sourceofmystery/geo/item/*_sword.geo.json`
- `src/main/resources/assets/sourceofmystery/textures/item/*_sword.png` 及发光遮罩
- `src/main/resources/assets/sourceofmystery/animations/item/*_sword.animation.json`
- `src/main/resources/assets/sourceofmystery/models/item/equipment_sword_display.json`
- `src/generated/resources/assets/sourceofmystery/models/item/*_sword.json`
- `art/equipment_gecko/`：可编辑模型、部件清单和资产计划

Java 和数据生成接线：

- `src/main/java/com/sourceofmystery/item/MysterySwordItem.java`：增加 GeoItem 接口及渲染扩展，原战斗方法保留。
- `src/main/java/com/sourceofmystery/client/equipment/EquipmentSwordExtensions.java`：武器 GeoItemRenderer 与发光层。
- `src/main/java/com/sourceofmystery/datagen/ModItemModelProvider.java`：五把剑及四件胸甲的自定义物品模型父级。

同批未发布胸甲改动另涉及 `TieredGeckoChestplateItem.java`、`TieredArmorExtensions.java` 及 `ModItems.java` 中四件胸甲注册。不要在替换武器时意外丢弃胸甲接线或改动神威天佑。已有 `dimension_breaker_staff` 不属于这五把新转换剑，其模型不在本次重建范围内。

## 验证的实际含义

曾执行隔离、静音的 Forge 客户端渲染检查；记录在 `build/equipment-gecko/first-review.log` 与 `build/equipment-gecko-smoke/first-review/`。检查覆盖自定义渲染器、穿戴与物品模型骨骼缓存隔离、左右手以及物品栏和掉落场景。它只能说明工程渲染路径跑通，不能证明模型审美合格。用户已经拒绝本版武器，不得用构建成功或这些自动标记推翻用户验收结论。

接手者应先重新设计并向用户展示武器外观；保持原有物品 ID、配方、属性、耐久与技能行为。用户没有给出更具体的本版失败细节，不要将推测描述成用户已经批准的设计意见。


## 最新状态：胸甲也被拒绝（2026-11-06）

用户已经撤回胸甲交付授权。本批四件胸甲与五把剑全部未通过，不得构建为发布版或安装；后续等待用户逐件指挥。撤回前没有执行新的源代码修改、资源拆分、构建或安装。
