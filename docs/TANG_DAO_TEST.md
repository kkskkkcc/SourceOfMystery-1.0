# 唐刀测试物品

在「神秘之源」创造物品栏领取，或执行 `/give @s sourceofmystery:tang_dao`。物品使用原版普通物品的默认攻击属性，供模型和动作测试。

## 第一人称 v2

- 持刀方式改为单手正握。刀身垂直于前臂，刃口朝向指节一侧；手腕只在刀身与前臂所在的平面内转动。手臂使用 Minecraft 皮肤和原版第一人称手臂尺寸，只旋转和移动，不拉伸。刀固定在拳心，任何动作中刀与手臂同步。
- 拳头沿画面边缘移动，刀身负责扫过准星，因此手臂只露出前臂一段，手臂顶端始终在画面外。
- 收刀状态下，左手握鞘口，右手按刀柄。第一次左键拔刀斩：刀沿鞘抽出，由左向右横斩，左手带鞘下沉离开画面。
- 出鞘后左键依次为袈裟斩（右上到左下）、逆袈裟（左下到右上）、下劈（右侧上段举刀，手与刀一起落过准星）。拔刀斩算作第一段，之后接第二段。停手超过 16 个游戏刻后从第一段重新开始。
- 命中帧之后再次左键会被保留，在当前一刀的衔接帧直接接下一刀，并从屏幕上当前的姿势继续。四段分别在动作内第 4、5、4、7 个游戏刻（拔刀斩、袈裟斩、逆袈裟、下劈）提交原版近战判定。攻击距离、伤害和受击冷却沿用原版。
- 白色刀光取自刀刃外侧四成的轨迹，从命中前 1.8 个游戏刻开始，命中后 4 个游戏刻内淡出。
- 出鞘待机时按 `V` 检视，可在按键设置中修改。依次为横刀亮出刀面，左手沿刀背从刀根抹到刀尖，转刃面向自己，然后血振，回到立刀架势。检视中按左键从当时的姿势接攻击。
- 出鞘后空闲 200 个游戏刻（正常速度 10 秒）自动回鞘：左手提鞘迎上，刀尖对准鞘口滑入。回鞘过程中左键会在回鞘后重新拔刀。
- 左手持刀时整套动作左右镜像。第三人称持握、斜挂刀鞘和第三人称挥刀沿用上一版。
- 第一人称参考「黑伤」联动实录中的三段斩击、收刀和检视分段，使用唐刀自身模型和原创关键帧。

## 动作数据与工具

动作保存在 `assets/sourceofmystery/first_person/tang_dao.json`，游戏在资源重载时读取。每个关键帧记录主手拳头位置、刀身方向、刃口方向和握持角，以及副手、刀鞘的通道；游戏逐帧插值后由 `TangDaoRig` 解出手臂。`tools/tang_dao/` 下的脚本与游戏使用同一套解算：

```powershell
python tools/tang_dao/author.py
python tools/tang_dao/report.py
python tools/tang_dao/preview.py draw diagonal rising overhead sheathe inspect
```

`author.py` 写出关键帧，并为每个关键帧选择露出前臂最少的握持角。`report.py` 检查两侧手臂顶端是否进入画面、命中时刀身离准星的距离和刃口是否领先。`preview.py` 在 `build/tang-dao-preview/` 生成不进游戏的逐刻示意图。

## 验证

开发客户端验证在模组源码根目录运行，截图保存在 `build/tang-dao-client-check/screenshots/`。

```powershell
.\gradlew.bat runClient -PtangDaoCheck --offline
```

验证新建平坦世界和不动的尸壳，用程序触发原版攻击键和检视键。日志 `TANG_DAO_OK` 记录四次命中的动作顺序、服务端收到的攻击次数、两种主手设置下刀身离准星的最小距离和刃口领先程度，以及待机、检视、回鞘、第三人称和左手持刀的画面。带光影的独立客户端验证仍可使用 `tools/outsider/run_oculus_smoke.py --tang-dao`，它需要本机存在 `F:/AAAAmc` 实例。

构建使用 Java 17 和 Forge 1.20.1–47.4.20。游戏资源为 `assets/sourceofmystery/models/item/tang_dao*` 和共用的 `textures/item/tang_dao.png`。Blender 导出脚本保存在建模工作区 `modeling/han_ring_pommel_dao/source/export_minecraft.py`。

- [《拔刀剑：重锋》项目页](https://www.curseforge.com/minecraft/mc-mods/slashblade-resharped)
- [《三角洲行动》黑伤官方公众号实录](https://news.17173.com/content/11032026/112957604.shtml)
- [《故宫博物院院刊》太原乱石滩唐代壁画墓发掘简报](https://img.dpm.org.cn/Uploads/File/2023/05/19/u6466de438a7ad.pdf)
