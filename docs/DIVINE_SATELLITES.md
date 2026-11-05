# 神威天佑卫星：内环模型、外环原版粒子

当前要求（2026-11-06）：用户要求“外环五个卫星回退为以前的粒子效果”。球体版、古文字版及其缩小方案均不再是当前外环方案。

## 当前行为

- 外环恢复原来的绿、黄、蓝、红、橙五色 Dust 粒子卫星、出击拖尾、环绕攻击时的加粗粒子和返回轨迹。原待命半径、颜色、位置、忙碌位屏蔽与第一人称隐藏规则均沿用原实现。
- 外环按原粒子逻辑显示，包括移动时；不再使用模型透明度控制外环。
- 内环保留黑白两颗 GeckoLib 星芒，原模型、贴图、闪烁、环绕，以及移动16 tick渐隐/停稳后24 tick渐显保持。
- 原攻击伤害、索敌、能量、冷却和返回不变。服务端 SatelliteAttackHandler 及 ModNetwork 已恢复为原正式包逐字节相同的类；协议回到8。
- ArmorParticlePattern 只停止发射旧内环黑白装饰粒子，避免和星芒模型重叠；外环粒子发射及忙碌时跳过待命粒子的路径恢复。
- 取消外环模型绘制与新增位置包，不再向外环发送10 Hz模型位置同步。

## 交付状态

用户已于2026-11-06明确要求实装，当前包已安装到两个正式实例并分别校验。SHA256为 `d73ce1d5d75b31b8cf6313155922c4f93747ec406931b8e171da552adf303f37`。原神威天佑甲体、神环、登神CG和其他底包条目保持；此前被拒绝的五把剑、四件胸甲仍不在候选中。

候选：`build/divine-satellites/sourceofmystery-1.0.0.jar`，40732572字节，SHA256 `d73ce1d5d75b31b8cf6313155922c4f93747ec406931b8e171da552adf303f37`。

本次已将全部未通过装备及外环字形模型隔离到 `drafts/`，整理后默认构建与安装包的生产条目已逐字节核对一致。`prepare_satellites_candidate.py` 从原正式包只覆盖内环模型相关类/资源和 ArmorParticlePattern，明确排除球体、文字面和新位置同步包。审计见 `candidate-audit.json`、`review-verification.json`。

## 检查与预览

Java17/Gradle8.5离线构建 `build/divine-outer-particles-build.log` 成功。隔离、静音真实客户端 `outer-particles` 检查通过：待命恰好五个外环粒子、五颗均出击时不发待命粒子、原忙碌位收到、真实敌对生物持续受到攻击、内环模型移动渐隐/停下恢复和脱甲清理。服务器攻击与网络类另与底包逐字节核对。

实机图在 `art/divine_satellites/outer-particles-preview-{idle,opposite,moving,attack}.png`。此次没有重跑开启光影或完整整合包，不据此声称全环境兼容。沿用测试模板的GeckoLib示例/lotterycompat旧注册映射与Oculus/Embeddium提示仍可能出现在日志中。

## 当前文件与历史

当前渲染：`DivineSatelliteRenderer` 只画内环；`DivineSatelliteModel` 只加载 star；活动资源为 `geo/effects/divine_satellite_star.*`、white_star/black_star贴图和公共动画占位文件。

`tools/divine/generate_satellites.py` 默认只生成内环星芒。`generate_satellite_glyphs.py` 已移至 `drafts/divine_satellites/tools/divine/`，不在活动流程中执行。古文字源文件保留供查阅，发布候选不包含它们。球体历史在 `rejected-spheres/`，字形回退前源码/候选在 `reverted-outer-glyphs/`；旧glyph-preview图片不能代表当前外环。

当前命令：

```text
python tools/divine/prepare_satellites_candidate.py
python tools/outsider/run_oculus_smoke.py --satellites --vanilla --label outer-particles
python tools/divine/finalize_satellites_review.py
```


实装及主源码重建核对摘要随库保存在 `releases/2026-11-06-inner-star-satellites.json`。被拒绝的装备源位于 `drafts/equipment_gecko/`，字形源位于 `drafts/divine_satellites/`；主源码中不存在这些草稿的运行注册。安装旧包备份与原始检查记录仍保留在本地build目录。
