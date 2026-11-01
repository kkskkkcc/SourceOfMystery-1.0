# 天空落雷的现成实现参考

参考项目：[Cataclysm 的 Death_Laser_beam_Renderer](https://github.com/lender544/new1.20.1/blob/master/src/main/java/com/github/L_Ender/cataclysm/client/render/entity/Death_Laser_beam_Renderer.java)。重点学习明确区分闪电起点与终点、保持每次闪烁期间几何稳定，以及在客户端独立管理特效。没有复制其 LightningRender/LightningBoltData 源码或加入外部库。

当前实现直接调用 Minecraft 1.20.1 自带的 `LightningBoltRenderer`，其 API、128 格网格高度及 seed 行为用本地 Forge 47.4.20 映射开发包核对。模型位置/颜色的渲染通道沿用 [Forge Render Types](https://docs.minecraftforge.net/en/1.20.x/rendering/modelextensions/rendertypes/)。

`OutsiderSkyLightning` 创建一个仅供渲染器读取的 LightningBolt 对象；不加入世界、不 tick，避免新增火灾、额外伤害或天气。随机种子与落点在一次闪烁中保持固定。起点为世界建筑高度上方 32 格（或角色上方至少 96 格），前端在两 tick 内从天空向周围地面下降，分叉和多层辉光由原版渲染器完成。三个时隙错开，最多一条主闪电同时显示，原有雷声音效保留。

数值验证在 GameTest 的 `sphereAndSky` 中；客户端闪电观感尚待实机验收。
