# 神秘之源 / Source of Mystery — 会话记录

> 工作目录：`F:\神秘之源1.0\MCmodfind`
> GitHub 仓库：`kkskkkcc/SourceOfMystery-1.0`（SSH: `git@github.com:kkskkkcc/SourceOfMystery-1.0.git`）
> 合作人 GitHub：`Kara251`（使用 Claude，通过 PR 协作）
> 会话起始 checkpoint：自龙魂 Boss 引入以后的所有动作。
> 导出时间：本回合结束。

---

## 0. 上下文背景（来自 checkpoint summary）

- 模组：**神秘之源（Source of Mystery）**，Minecraft Forge 1.20.1，gradle 8.5，依赖 GeckoLib 4.8.2。
- 已完成 PR #1–#7，主要功能：
  - 神秘源矿 / 宝箱秘源锭生成率与钻石一致（PR #6）。
  - 龙魂 Boss（500 HP→5000 HP，30 防御，免疫药水/击退/火/wither，仅锁玩家，100 格仇恨，瞬移近战，GeckoLib + Blockbench 模型；末影龙死亡 → 5 步召唤流程：等 10 s → 末影龙吼 3 s → 雷 5 座 20 格半径 → s 紫色卫星环绕 → boss 现形）。
  - 神威天道（Divine Heavenly Dao）Boss 重做（HP 翻 10000，STRIKE 50 / SLAM 50 / THUNDER_STORM 18 雷 / ABYSS 90 秒 CD 蓄力 10 秒扔球）。
  - 五行之源（木/金/水/火/土）+ 召唤祭坛 + 始龙灼焰 / 龙魂物品。
  - BossIntroCamera、BossIntroPacket、魂类节奏（招架/前摇/恢复窗口/挑逗/转枪后摇/狂暴机制）。
- Modrinth 上传已被用户叫停（"Modrinth 那边不用管"）；GitHub Actions 构建产物通过 Release `v1.0.0` 单一资产 `sourceofmystery-1.0.0.jar` 分发。
- 本地未提交目录（保留工作区不动）：`1ba/`、`ysmmox/`。

---

## 1. 神威天道数值有无修改？

**用户**：神威天道数值有无修改？

**Agent**：查 `DivineHeavenlyDaoBoss.java` 与 `02b6f37..50f8faa` 的 PR #8 diff。

**结论**：
- **MAX_HEALTH** 5000 → 10000（仍会被 `MysteryConfig.BOSS_MAX_HEALTH` 覆盖）。
- **技能重做**：`THUNDER_RING`（雷环，固定角度）→ `THUNDER_STORM`（乱劈带 AOE）+ 新增 `ABYSS`（天渊大招）：
  - 200 tick 蓄力 → 8 tick 抛投 → 30 tick 结束。
  - 冷却 90 秒（`ABYSS_COOLDOWN = 1800`）。
  - 开战 30 秒内不放（`abyssCooldown = 600` 初始）。
  - 触发几率 35%。
  - 球体半径最大 7.0 格。
- **雷电**：每生物一回合只受一次雷劈（`stormStruck` 去重），首雷直接劈玩家当前坐标；增加 BOLT 命中半径 2.5 格。
- **攻击概率**：近 45/30/25，远 60/40。
- **二阶段冷却**：60 → 40 tick。
- **未变**：STRIKE_DAMAGE=50、SLAM_DAMAGE=50、ARMOR=50、ARMOR_TOUGHNESS=20、KNOCKBACK_RESISTANCE=1.0、FOLLOW_RANGE=128、MOVEMENT_SPEED=0.3。

---

## 2. 合作人更新 github，合并（PR #9）

**用户**：我的合作人已经更新 github，合并。

**Agent**：列 PR → PR #9 "Boss 实测修正：出场隐藏、神威天道数值"（12 文件）。
- 文件写入遭遇 sandbox error（policy 在 "ask" 与 "never" 间切换），最终切到 danger-full-access + 用 `_*.cjs` 临时脚本。
- 合并成功（commit `03391fb4`）→ pull → 等 Actions Run #37113165248（31 秒成功）→ 下载 jar（932,961 B）→ 删除旧资产 id 607354633 → 上传新资产 id **607532639**。
- 本地 `build\libs\sourceofmystery-1.0.0.jar` 同步。

**PR #9 主要变更**：
- DragonSoulRenderer +4/-1（出场隐藏）
- DivineHeavenlyDaoBoss +35/-8（神威数值调整）
- DragonSoulBoss +49/-10
- AbyssOrb +18/-17
- BossSpawnHandler / DragonSoulSpawnHandler 各 +1/-1
- MysteryConfig +2/-2
- Patchouli 中英文 boss/dragon_soul / fight 更新

---

## 3. 新开 2ba 目录上传凋零守卫

**用户**：在 github 中新开一个文件 2ba 上传本地 `F:\aaaaaysm插件+blockbench\2ba\凋零守卫`。

**Agent**：
- 探查 `F:\aaaaaysm插件+blockbench\2ba\凋零守卫\`：19 文件 / 2.7 MB。
- 询问上传方式：用户选 **"加到当前仓库的 2ba/ 目录"**。
- 文件策略 read-only + approval ask → 询问是否授权写入 → 用户授权。
- 复制 `2ba\凋零守卫\` 到工作区 → `git add` → `git commit -m "chore: archive 凋零守卫 Blockbench/YSM source under 2ba/"`（commit `31b0074`）→ `git push origin main`。
- 推送超时（SSH push 1.5 MB 文件 120 秒超时），后台重试成功（`03391fb..31b0074 main -> main`）。

**已上传 19 文件**（含 `animations/` `slashblade.animation.json` 1.5 MB 最大）。

---

## 4. 合作人更新 github，合并（PR #10）

**用户**：我的合作人更新了 github，合并。

**Agent**：
- 列出 PR #10 "新增 Boss：泣死之主（原版凋灵死亡后出场）"（41 文件）。
- 合并 → commit `e8e0ad4` → pull → Run #37262300986（32 秒成功）→ 下载 jar 1,210,150 B → 删除旧资产 id 607532639 → 上传新资产 id **611444883**。

**PR #10 主要变更 — 泣死之主（WeepingDeathLord）新 Boss**：
- 核心：`WeepingDeathLord.java` +1470、`WeepingDeathLordSpawnHandler.java`。
- 模型/渲染：`WeepingDeathLordModel.java` / `WeepingDeathLordRenderer.java`（含发光层）。
- 出场：`WeepingIntroDirector.java` +293、`BossIntroCamera` +14/-3、`ClientCinematicCache` +19、`ClientForgeEvents` +14、`ClientModEvents` +6。
- 副实体：`DeathSkull.java`、`ThrownScythe.java` +242、`SpaceShatter.java`、`WitherHusk.java`。
- 渲染：`ThrownScytheModel/Renderer`、`WitherHuskRenderer`、`SpaceShatterRenderer`、`DeathSkullParticle`。
- 网络：`RootPacket.java`、`WeepingIntroPacket.java`、`ModNetwork` +13、`ModEntities` +49、`ModSounds` +27。
- 资源：
  - `geo/entity/weeping_death_lord.geo.json` +12,482
  - `geo/entity/weeping_scythe.geo.json` +263
  - `animations/entity/weeping_death_lord.animation.json` +29,852
  - `animations/entity/weeping_scythe.animation.json` +10
  - 贴图：`weeping_death_lord.png` / `_glow.png` / `space_shatter.png`
  - `lang/{zh_cn,en_us}.json` +18/-1
  - `patchouli_books/{zh_cn,en_us}/entries/boss/weeping_death_lord.json` +24
  - `sounds.json` +166/-1
- Python 工具（`tools/weeping_death_lord/`）：`anim_lib.py`、`anims.py`、`convert_model.py`、`geo_render.py`、`make_shatter_texture.py`。
- `tools/.gitignore`。

---

## 5. 合作人更新 github，合并（PR #11）

**用户**：我的合作人更新了 github，合并。

**Agent**：
- 列出 PR #11 "泣死之主：动画/出场返工、特效修复、新增死亡激光大招"（36 文件）。
- 合并 → commit `9af05074` → pull → Run #37271710248（62 秒成功）→ 下载 jar 1,542,381 B → 删除旧资产 id 611444883 → 上传新资产 id **611705661**。

**PR #11 主要变更 — 泣死之主返工 + 死亡激光大招**：
- `WeepingDeathLord.java` +539/-178 大改
- `WeepingDeathLordRenderer.java` +222/-13
- `WeepingIntroDirector.java` +149/-53
- `ThrownScythe.java` +3, `ThrownScytheRenderer.java` +17/-5
- `WitherHusk.java` +2/-2
- **`DeathSkullParticle.java` +82**
- `BossIntroCamera.java` +7/-1, `ClientCinematicCache.java` +9/-1, `ClientModEvents.java` +1/-0
- `weeping_death_lord.animation.json` +65,893/-19,209（动画大幅重写）
- `weeping_death_lord.geo.json` +28/-20
- `particles/death_skull.json` +8
- `ModParticles.java` +5/-1
- 新贴图：`weeping_halo.png` / `weeping_halo_glow.png` / **`weeping_laser.png`** / `weeping_magic_circle.png` / `_glow.png` / `weeping_void_orb.png` / `particle/death_skull_0..3.png`
- `lang/{zh_cn,en_us}.json` +9/-1
- `sounds.json` +116/-9, `ModSounds.java` +24
- `patchouli_books/{zh_cn,en_us}/entries/boss/weeping_death_lord.json` +9/-1
- 工具：`anim_lib.py` +193/-21、`anims.py` +382/-196、`convert_model.py` +12/-6、`collide.py` +60（新增）、`make_effect_textures.py` +257（新增）

---

## 6. 合作人更新 github，合并（PR #12）

**用户**：我的合作人更新了 github，合并。

**Agent**：
- 列出 PR #12 "泣死之主：修正神环位置、出场时序，数值对标暗源之甲，加强吸附"（11 文件）。
- 合并 → commit `89234697` → pull → Run #37285138779（31 秒成功）→ 下载 jar 1,543,296 B → 删除旧资产 id 611705661 → 上传新资产 id **611999304**。

**PR #12 主要变更 — 泣死之主 polish**：
- `WeepingDeathLord.java` +40/-13（数值对标暗源之甲，加强吸附）
- `DeathSkull.java` +3/-1
- `WeepingDeathLordRenderer.java` +10/-5（神环位置）
- `WeepingIntroDirector.java` +10/-10（出场时序：freeze pose 后日轮转场）
- `weeping_death_lord.animation.json` +5,831/-5,841
- `weeping_death_lord.geo.json` +1/-1
- `patchouli_books/{zh_cn,en_us}/entries/boss/weeping_death_lord.json` +5/-5, +4/-4
- `tools/weeping_death_lord/anims.py` +52/-7, `convert_model.py` +1/-1

---

## 7. 泣死之主的动作文件在本机吗？

**用户**：泣死之主的动作文件在本机吗？

**Agent**：glob `**/weeping*` 确认全部存在：
- 动画/模型：`weeping_death_lord.animation.json`、`weeping_scythe.animation.json`、`weeping_death_lord.geo.json`、`weeping_scythe.geo.json`。
- 贴图：`weeping_death_lord.png` / `_glow.png` / `weeping_halo.png` / `_glow.png` / `weeping_laser.png` / `weeping_magic_circle.png` / `_glow.png` / `weeping_void_orb.png`。
- 图鉴：`patchouli_books/guide/{zh_cn,en_us}/entries/boss/weeping_death_lord.json`。

---

## 8. 将聊天窗口导出为会话 md 文件

**用户**：将该聊天窗口导出为会话 md 文件。

**Agent**：本环境没有内置"导出聊天"工具。提议方案：
1. 我手写一份 Markdown 总结（压缩版 + 时间线），写到 `chat_history.md`。
2. 用户从 GUI 自带导出。

用户回复 "继续" → 默认按方案 1。

---

## 9. 完整同步当前项目到 GitHub（域外魔导师）

**用户**：将当前项目上传至github → 要完整同步，参考以前的同步流程 → 新boss模型等必要资源也同步，有另一个工程师通过仓库一起开发。

**Agent**：

### 9.1 提交范围判定
盘点未跟踪目录体积后确认必须排除项，并补进 `.gitignore`：

| 路径 | 体积 | 处理 | 原因 |
|---|---|---|---|
| `tools/ai_runtime/` | 1252 MB / 55057 文件 | 排除 | uv 包缓存（`CACHEDIR.TAG`、`.tmp*`），机器相关 |
| `art/` | 324 MB | 排除 | 设计资料，与既有 `原版素材/`、`开发需求/` 同类 |
| `mmd模型/` | 327 MB | 排除 | 第三方 PMX 原模型（林德薇恩/元空 Ame、Abby/是luruka才不是llk） |
| `模型/` | 6 MB | 排除 | YSM 设计参考图 |
| `形势与政策课程论文/` | 0.5 MB | 排除 | 课程作业，与项目无关 |
| `.gradle-local/` | 1.5 MB | 排除 | 本地 Gradle 缓存 |

入库：源码 146 文件 + `tools/` 49 个脚本（`divine`/`outsider`/`dragon_soul`/`guardian_spirit`/`weeping_death_lord`/`divine_halo`，含 quaternius 参考库及其 LICENSE）+ 运行时资源。合计 **196 文件 / 113.7 MB**，最大单文件 27.3 MB（低于 GitHub 100 MB 限制）。

**模型源文件例外**：`art/` 下用 `art/*` + `!art/outsider_void_boss/...` 逐级反选，仅放行 `outsider_void_boss_v1.pmx` 与 `outsider_combat.pmx` 两个加工后模型（各 3.3 MB），供协作工程师改模型。原始第三方 PMX 继续排除。按用户决定**不补署名**（`mods.toml` 未署名元空 Ame，与既有 Quaternius-CC0/VOX 的署名规范不同，属后续待办）。

### 9.2 提交与推送
- `51d2753` feat: 21 个已跟踪文件修改（+1014/-20）
- `8a0ba06` feat: 196 个新文件（+180972）
- `088bab9` chore: 2 个 PMX 模型源文件

推送 `8923469..088bab9`（3 次 push 累计 4 个提交，含原有 `fdd11e8`）。

### 9.3 CI 与 Release 资产
- Run #39（`8a0ba06`）success；Run #40（`088bab9`）success → 采用 Run #40 artifact（`13079797025`）。
- 认证：装 `gh` 2.103.3 + 设备码登录（`auth login --web --skip-ssh-key`），token 存 keyring，scopes `gist, read:org, repo`。免去手写 PAT。
- 删除旧资产 `611999304`（1,543,296 B）→ 上传新资产 `698047819`（**34,850,043 B**，SHA256 `aa1dbe0a…`）。
- 本地 `build\libs\sourceofmystery-1.0.0.jar` 同步为 CI 产物（SHA256 一致）。

### 9.4 本地构建验证
`gradlew.bat build --no-daemon` → BUILD SUCCESSFUL in 1m 2s，验证 25 个新增 Outsider 类可编译。jar 内容抽查：12 动画 / 17 mesh / 17 贴图 / 11 shader / 29 语音全部打包。

**注意**：本地构建与 CI jar 差 9028 字节（Gradle 时间戳差异），发布一律用 CI 产物。

### 9.5 踩坑
- `gh run download` 与 `curl` 下载 67.9 MB artifact 均在前台超时，须后台跑。
- Git ignore 反选：`art/` 被排除后其子项无法用 `!` 重新包含，必须改为排除 `art/*` 再逐级反选目录。
- 本机无 `gh`、无 PAT、无 `gh` hosts.yml；winget 可用，安装 `GitHub.cli` 成功。

---

## 10. 合并合作工程师的传奇权杖分支

**用户**：合作的工程师已更新github仓库，合并并同步到本地。

**Agent**：

### 10.1 定位更新
`git fetch --prune` 发现新分支 `origin/feat/legendary-staff-v1`（作者 Kara251，2 个提交），**协作者未开 PR**，因此走本地合并而非 gh PR 流程。merge-base 为 `088bab9`。

| Commit | 说明 |
|---|---|
| `5f414eb` | Add legendary dimension staff and restore energy regeneration |
| `ac0e452` | Refine legendary staff model and materials v2 |

### 10.2 改动规模
85 文件 / +138831 / -22，合计 5.2 MB（最大单文件 2.97 MB 的 `dimension_breaker_staff.geo.json`）。核心为**传奇维度法杖** `dimension_breaker_staff`：

- 五种法术：陨灭杀（10000 能量）、裂地（8000）、大地悬浮（1000）、时间停止（1000）、空间撕裂（0），L 键半透明法术轮盘选择。
- **引入 Mixin 框架**（`org.spongepowered.mixin` 0.7-SNAPSHOT + `annotationProcessor mixin:0.8.5` + SpongePowered maven），16 个 mixin 类实现维度级时停：服务器 tick 计数仍前进，通过 `Level.getGameTime/getDayTime` 注入冻结时钟，`tickNonPassenger/tickChunk/tickCustomSpawners/advanceWeatherCycle` 等在冻结期跳过。
- `OutsiderSpell` / `OutsiderBlastTasks` 的 owner 由 `OutsiderEntity` 泛化为 `LivingEntity` 以支持玩家施法，玩家施法绕过 `mobGriefing` 检查（Forge 标准语义），非玩家施法路径不变。
- 新增 `ENERGY_REGEN_PER_SECOND`（默认 0.01，每秒回上限 1%，小数累计，不提高上限），独立于每日回满开关。
- 资源：GeckoLib geo/animation/model、9 个 staff 音效、时停 post shader、`staff_earth_split` 伤害类型、域外魔导师战利品表（必掉权杖）、`staff_overworld_rift` 方块标签、16 个 mixin 配置。
- 文档：`docs/staff-v1.md`（完整规则表）、`docs/staff-model-v2.md`、`tools/staff/`（Blockbench 源、资产生成、音频生成）。

### 10.3 合并前验证（关键）
Mixin 引入改变了构建链（新增插件与 SpongePowered 仓库），且工作流只监听 `main` 与 `pull_request`，**协作者分支从未被 CI 验证过**。因此先本地验证：

1. `git merge-tree --write-tree origin/main origin/feat/legendary-staff-v1` → 无冲突，tree `437fb05b`。
2. 建临时分支 `merge-test` 合入 main → 仅 `chat_history.md` 文档冲突面，无代码冲突。
3. `gradlew.bat build --no-daemon` → **BUILD SUCCESSFUL in 1m 49s**，`addMixinsToJar` 正常执行。
4. jar 抽查：`sourceofmystery.mixins.json` 与 `sourceofmystery.refmap.json` 均在包内，24 个 staff 类 + 15 个 mixin 类 + 21 个 staff 资源齐全。
5. **refmap 校验**：映射到 SRG 名（`m_46467_` 等），非空且正确 —— 空 refmap 会导致生产环境崩溃。

审查结论：时停用 SavedData 持久化时钟偏移，Mixin 仅在 `TimeStop.active()` 为真时介入，其余情况零开销，设计正确。

### 10.4 合并与推送
`git merge --no-ff origin/feat/legendary-staff-v1` → `ca22e77`（保留协作者两个提交的历史），85 文件 / 5.2 MB。推送 `5d2f7fe..ca22e77` 成功。

CI Run #41（`41379911846`）→ **success**，确认 SpongePowered 依赖在 CI 环境可解析。

Release 资产：删除 `698047819` → 上传 `700351212`（**35,414,294 B**，SHA256 `1ea34f92…`）。本地 `build\libs\sourceofmystery-1.0.0.jar` 同步为 CI 产物，SHA256 一致。jar 抽查：mixin 配置与 refmap 各 1 份、24 个 staff 类、杖 geo、11 个 staff 音效、域外魔导师 12 个动画全部在包内。

### 10.5 踩坑
- 协作者未开 PR，`gh pr list` 返回空，需从 `git branch -a` 找远端分支。
- `gh run download` 下载 67.9 MB artifact 前台必超时，须 `run_in_background` 后 `job_output` 收集。

---

## 11. 裂地魔法长度改为 500 格

**用户**：将法杖的【地裂】魔法裂地长度改为500格。

**Agent**：

### 11.1 定位与影响面
长度由 `StaffConfig.SPLIT_LENGTH` 控制（`sourceofmystery-staff-server.toml` 的 `splitLength`，默认 100，上限 512）。改动只需该配置项，但 5 倍长度带来两个必须处理的问题：

| 项 | 100 格 | 500 格 |
|---|---|---|
| 裂缝列数 | 1,910 | 9,549 |
| 需处理方块 | 61 万 | 305 万 |
| 跨越区块 | 7 | 32 |
| 完成耗时（4096 改动/tick） | ~150 tick（7.5 秒） | ~746 tick（~37 秒） |

`withinSplit` 的渐缩宽度用 `sin(π·along/length)` 计算，长度变化会同时影响列生成与伤害 AABB —— 两者都从 `length` 推导，**自动跟随配置，无需改代码**。

### 11.2 修复区块加载缺陷
原 `splitTick` 在遇到未加载区块时执行 `getChunk` 后直接 `return`，导致**整个作业当 tick 中止**，每 tick 只处理一个区块。长度 500 时跨 32 区块，该缺陷会被显著放大。

改为：每 tick 最多强制加载 4 个区块，加载后 `continue` 重新检查同一列（`getChunk` 同步完成，此时区块已就绪），既能推进也不丢列。同时对世界边界外 `getChunk` 抛异常的情况做容错 —— 该列标记为已处理并跳过，避免异常中断整个 tick 处理器。

### 11.3 验证
- `gradlew.bat build` → BUILD SUCCESSFUL。
- `gradlew.bat runGameTestServer` → **All 3 required tests passed**。裂地真实伤害用例日志确认 `staff_earth_split amount=1000.0`，伤害结算正常；挖除不可破坏方块、保留基岩的断言均通过。
- 现有游戏测试用的是显式传入长度 100 的 `withinSplit` 几何断言（验证渐缩数学），与配置解耦，未受影响。

### 11.4 文档
`docs/staff-v1.md` 规则表长度改为 500，并补充性能说明：约 9500 列、300 万方块、按每 tick 最多 4096 推进，裂缝完全成形需数十秒。

---

## 附录 A：Release 资产历史

| 时间(UTC)         | 资产 ID    | 大小        | 备注                                  |
|----------------------|------------|-------------|--------------------------------------|
| (PR #7 前)         | 607354633 | 327,750 B  | 旧版                                |
| PR #8              | 607500452 | 931,991 B  | +龙魂 / 神威改动                  |
| PR #9              | 607532639 | 932,912 B  | 出场隐藏 / 龙魂血量               |
| PR #10             | 611444883 | 1,210,150 B | +泣死之主基础                      |
| PR #11             | 611705661 | 1,542,381 B | +死亡激光 / 动画返工           |
| PR #12             | 611999304 | 1,543,296 B | polish                               |
| 域外魔导师同步     | 698047819 | 34,850,043 B | +域外魔导师完整实现（CI Run #40）    |
| 超位权杖合并       | 700351212 | 35,414,294 B | +传奇维度法杖与 Mixin 时停（CI Run #41）|
| 裂地长度 500       | 见下      | 见下        | 裂地长度 100 → 500 格                  |

## 附录 B：本地合并 commit

| Commit    | 说明                                          |
|-----------|------------------------------------------------|
| `02b6f37` | PR #7 merge baseline                          |
| `50f8faa` | PR #8 merge（龙魂入场 / 神威天道 / 五行 / 龙息） |
| `31b0074` | 手动 commit：归档凋零守卫到 `2ba/`        |
| `03391fb` | PR #9 merge（出场隐藏 / 龙魂数值）          |
| `e8e0ad4` | PR #10 merge（泣死之主新增）                |
| `9af0507` | PR #11 merge（泣死返工 + 死亡激光）       |
| `8923469` | PR #12 merge（泣死 polish）                  |
| `fdd11e8` | 守护系统整合与当前模组更新                    |
| `51d2753` | 已跟踪文件修改（+1014/-20）                 |
| `8a0ba06` | 域外魔导师完整实现：196 文件 / 113.7 MB     |
| `088bab9` | 归档域外魔导师 PMX 模型源文件供协作开发      |
| `5d2f7fe` | 记录完整同步流程与 Release 资产更新          |
| `ca22e77` | 合并 Kara251 的 feat/legendary-staff-v1      |

## 附录 C：常驻脚本与命令

### gh CLI 认证（2026-11 新增，替代一次性 __gh_*.cjs）
本机已装 `gh` 2.103.3（`C:\Program Files\GitHub CLI\gh.exe`），用**设备码**登录，token 存于 Windows keyring，无需手写 PAT：
```
& "$env:ProgramFiles\GitHub CLI\gh.exe" auth login --hostname github.com --git-protocol ssh --web --skip-ssh-key
```
登录后账号 `kkskkkcc`，token scopes `gist, read:org, repo`，可完成 artifact 下载与 Release 资产增删。

### 同步 Release 资产全流程（gh CLI 版）
1. `gh run list --limit 4 --json databaseId,headSha,status,conclusion` — 取最新成功 run。
2. `gh run download <run-id> --name sourceofmystery --dir __gh_dl` — 下载 artifact（67.9 MB zip，**可能超时，须后台跑**）。
3. `gh api --method DELETE repos/kkskkkcc/SourceOfMystery-1.0/releases/assets/<old-asset-id>` — 删除旧资产。
4. `gh release upload v1.0.0 "<path>/sourceofmystery-1.0.0.jar#sourceofmystery-1.0.0.jar" --clobber` — 上传新资产（33 MB，**须后台跑**）。
5. `Copy-Item __gh_dl/sourceofmystery-1.0.0.jar build\libs\sourceofmystery-1.0.0.jar -Force` — 同步本地。
6. 清理：`Remove-Item -Recurse -Force .\__gh_dl; Remove-Item -Force .\__gh_*.cjs`

### 合并 PR 全流程（旧版一次性脚本，仍可用）
1. `node __gh_list.cjs` — 列出 open PR。
2. `node __gh_merge.cjs <pr>` — 合并。
3. `git pull origin main` — 拉取。
4. `node __gh_runs.cjs` — 列出最近 runs 取最新 in_progress 的 id。
6. `node __gh_wait.cjs <run-id>` — 轮询 25 分钟。
7. `node __gh_artifact.cjs <run-id> ./__gh_dl` — 下载 artifact。
8. `Expand-Archive ._\__gh_dl\sourceofmystery.zip -DestinationPath .\__gh_dl\ext -Force`
9. `node __gh_release.cjs` — 列出 release 与旧资产 id。
10. `node __gh_update.cjs <release-id> <old-asset-id> .\__gh_dl\ext\sourceofmystery-1.0.0.jar sourceofmystery-1.0.0.jar`
11. `Copy-Item .\__gh_dl\ext\sourceofmystery-1.0.0.jar .\build\libs\sourceofmystery-1.0.0.jar -Force`
12. 清理：`Remove-Item -Recurse -Force .\__gh_dl; Remove-Item -Force .\__gh_*.cjs`

### 上传 2ba/
```
$ F = "F:\aaaaaysm插件+blockbench\2ba\凋零守卫"
Copy-Item -Recurse $F "2ba\凋零守卫"
git add '2ba/'
git commit -m "chore: archive 凋零守卫 Blockbench/YSM source under 2ba/"
git push origin main
```

### 本地 Gradle 构建（备用，CI 已首选）
```
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
& 'C:\Users\Administrator\gradle-dist\gradle-8.5\bin\gradle.bat' build --console=plain --no-daemon
```

### Git 身份
```
Ba1Na1 <bssg158@163.com>
```

---

## 附录 D：环境/策略切换记录

| 时刻 | DSH 文件策略 / 审批策略 | 备注                                          |
|------|-----------------------------|----------------------------------------------|
| 起    | danger-full-access / never   | 正常推进                                      |
| 中    | read-only / ask              | PR #9 合并时一度受阻碍（拒绝写 .cjs）|
| 中    | danger-full-access / never   | 恢复                                          |
|    | read-only / ask              | 上传 2ba 时再次切换，用户授权写入        |
| 末    | danger-full-access / never   | 当前恢复正常                                  |