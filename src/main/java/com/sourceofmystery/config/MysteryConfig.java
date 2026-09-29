package com.sourceofmystery.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 模组配置。
 * COMMON 配置位于 config/sourceofmystery-common.toml，CLIENT 配置位于 config/sourceofmystery-client.toml。
 * 所有默认值与原先写死在代码里的数值一致。
 */
public final class MysteryConfig {

    public static final ForgeConfigSpec COMMON_SPEC;
    public static final ForgeConfigSpec CLIENT_SPEC;

    // ==================== 神秘之能 ====================
    public static final ForgeConfigSpec.LongValue INITIAL_ENERGY;
    public static final ForgeConfigSpec.LongValue ENERGY_PER_KILL;
    public static final ForgeConfigSpec.BooleanValue DAILY_REFRESH;

    // ==================== 武器 / 护甲 ====================
    public static final ForgeConfigSpec.DoubleValue DIVINE_PUNISHMENT_STRIKE_DAMAGE;
    public static final ForgeConfigSpec.LongValue DIVINE_PUNISHMENT_STRIKE_COST;
    public static final ForgeConfigSpec.LongValue DIVINE_BLESSING_BLOCK_COST;

    // ==================== 胸甲卫星 ====================
    public static final ForgeConfigSpec.DoubleValue SATELLITE_RANGE;
    public static final SatelliteTier DARK_SOURCE_SATELLITE;
    public static final SatelliteTier ORIGIN_DRAGON_SATELLITE;
    public static final SatelliteTier DIVINE_BLESSING_SATELLITE;

    // ==================== 突破原版护甲上限 ====================
    public static final ForgeConfigSpec.BooleanValue RAISE_ARMOR_CAPS;
    public static final ForgeConfigSpec.DoubleValue ARMOR_CAP;
    public static final ForgeConfigSpec.DoubleValue ARMOR_TOUGHNESS_CAP;
    public static final ForgeConfigSpec.DoubleValue EXTRA_ARMOR_REDUCTION_SCALE;

    // ==================== 神威天道 ====================
    public static final ForgeConfigSpec.DoubleValue BOSS_MAX_HEALTH;
    public static final ForgeConfigSpec.DoubleValue BOSS_SECOND_PHASE_HEALTH;
    public static final ForgeConfigSpec.DoubleValue BOSS_MINION_DAMAGE_REDUCTION;

    // ==================== 客户端 HUD ====================
    public static final ForgeConfigSpec.EnumValue<HudAnchor> HUD_ANCHOR;
    public static final ForgeConfigSpec.IntValue HUD_OFFSET_X;
    public static final ForgeConfigSpec.IntValue HUD_OFFSET_Y;
    public static final ForgeConfigSpec.LongValue HUD_LOW_ENERGY_THRESHOLD;

    /**
     * 一档胸甲卫星的攻击数值
     */
    public record SatelliteTier(ForgeConfigSpec.DoubleValue damage,
                                ForgeConfigSpec.IntValue attackSeconds,
                                ForgeConfigSpec.IntValue cooldownSeconds,
                                ForgeConfigSpec.LongValue energyCost) {

        static SatelliteTier define(ForgeConfigSpec.Builder builder, String path, String comment,
                                    double damage, int attackSeconds, int cooldownSeconds, long energyCost) {
            builder.comment(comment).push(path);
            SatelliteTier tier = new SatelliteTier(
                    builder.comment("卫星环绕目标期间每秒造成的伤害")
                            .defineInRange("damage", damage, 0.0, Double.MAX_VALUE),
                    builder.comment("每次攻击环绕目标的持续时间（秒）")
                            .defineInRange("attackSeconds", attackSeconds, 1, 600),
                    builder.comment("攻击结束、飞回玩家身边后的冷却时间（秒）")
                            .defineInRange("cooldownSeconds", cooldownSeconds, 0, 3600),
                    builder.comment("每颗卫星每次出击消耗的神秘之能，能量不足时不出击；0 表示不消耗")
                            .defineInRange("energyCost", energyCost, 0L, Long.MAX_VALUE));
            builder.pop();
            return tier;
        }
    }

    public enum HudAnchor {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
    }

    static {
        ForgeConfigSpec.Builder common = new ForgeConfigSpec.Builder();

        common.comment("神秘之能 / Mystery Energy").push("energy");
        INITIAL_ENERGY = common.comment("玩家初始神秘之能（同时也是初始上限）")
                .defineInRange("initialEnergy", 100L, 0L, Long.MAX_VALUE);
        ENERGY_PER_KILL = common.comment("每次击杀获得的神秘之能（同时提升等量上限）")
                .defineInRange("energyPerKill", 10L, 0L, Long.MAX_VALUE);
        DAILY_REFRESH = common.comment("每个游戏日是否把神秘之能回满到上限")
                .define("dailyRefresh", true);
        common.pop();

        common.comment("神威系列装备 / Divine equipment").push("divine");
        DIVINE_PUNISHMENT_STRIKE_DAMAGE = common.comment("神威天罚天雷造成的无视护甲真实伤害（命中 1 秒后落下）")
                .defineInRange("divinePunishmentStrikeDamage", 200.0, 0.0, Double.MAX_VALUE);
        DIVINE_PUNISHMENT_STRIKE_COST = common.comment("神威天罚每次召唤天雷消耗的神秘之能")
                .defineInRange("divinePunishmentStrikeCost", 100L, 0L, Long.MAX_VALUE);
        DIVINE_BLESSING_BLOCK_COST = common.comment("神威天佑每次完全抵挡一次伤害消耗的神秘之能")
                .defineInRange("divineBlessingBlockCost", 1000L, 0L, Long.MAX_VALUE);
        common.pop();

        common.comment("胸甲卫星自动攻击 / Chestplate satellites").push("satellites");
        SATELLITE_RANGE = common.comment("卫星的索敌半径（格）")
                .defineInRange("range", 20.0, 1.0, 128.0);
        DARK_SOURCE_SATELLITE = SatelliteTier.define(common, "dark_source", "暗源之甲：1 颗卫星", 10.0, 5, 10, 0L);
        ORIGIN_DRAGON_SATELLITE = SatelliteTier.define(common, "origin_dragon", "始源龙甲：2 颗卫星", 15.0, 5, 8, 0L);
        DIVINE_BLESSING_SATELLITE = SatelliteTier.define(common, "divine_blessing", "神威天佑：外环 5 颗卫星", 20.0, 8, 5, 10L);
        common.pop();

        common.comment("突破原版护甲限制 / Armor limits").push("armor");
        RAISE_ARMOR_CAPS = common.comment(
                        "是否提高原版护甲值（上限 30）与护甲韧性（上限 20）的属性上限。",
                        "关闭后，超出原版上限的护甲值不再生效，额外减伤也随之失效。")
                .define("raiseArmorCaps", true);
        ARMOR_CAP = common.comment("护甲值属性上限（原版 30）")
                .defineInRange("armorCap", 1024.0, 30.0, 1_000_000.0);
        ARMOR_TOUGHNESS_CAP = common.comment("护甲韧性属性上限（原版 20）")
                .defineInRange("armorToughnessCap", 1024.0, 20.0, 1_000_000.0);
        EXTRA_ARMOR_REDUCTION_SCALE = common.comment(
                        "原版护甲公式最多只能减免 80% 伤害，超出 30 点的护甲不会带来任何收益。",
                        "本模组对超出 30 点的部分额外减伤：额外减伤比例 = 超出值 / (超出值 + 本参数)。",
                        "例：60 点护甲额外减伤 23%，100 点 41%，300 点 73%。设为 0 关闭额外减伤。")
                .defineInRange("extraArmorReductionScale", 100.0, 0.0, 1_000_000.0);
        common.pop();

        common.comment("神威天道 Boss / Divine Heavenly Dao").push("boss");
        BOSS_MAX_HEALTH = common.comment("Boss 生命上限（只影响之后新生成的 Boss）")
                .defineInRange("maxHealth", 5000.0, 1.0, 1_000_000.0);
        BOSS_SECOND_PHASE_HEALTH = common.comment("Boss 生命低于该值时进入第二阶段")
                .defineInRange("secondPhaseHealth", 100.0, 0.0, 1_000_000.0);
        BOSS_MINION_DAMAGE_REDUCTION = common.comment("第二阶段召唤的凋灵存活期间，Boss 受到伤害的减免比例（0-1）")
                .defineInRange("minionDamageReduction", 0.5, 0.0, 1.0);
        common.pop();

        COMMON_SPEC = common.build();

        ForgeConfigSpec.Builder client = new ForgeConfigSpec.Builder();
        client.comment("神秘之能 HUD / Mystery Energy HUD").push("hud");
        HUD_ANCHOR = client.comment("HUD 显示在屏幕哪个角")
                .defineEnum("anchor", HudAnchor.BOTTOM_LEFT);
        HUD_OFFSET_X = client.comment("距离屏幕边缘的水平距离（像素）")
                .defineInRange("offsetX", 10, 0, 10000);
        HUD_OFFSET_Y = client.comment("距离屏幕边缘的垂直距离（像素）")
                .defineInRange("offsetY", 10, 0, 10000);
        HUD_LOW_ENERGY_THRESHOLD = client.comment("神秘之能低于该值时 HUD 变为红色提醒（默认等于神威系列一次消耗）")
                .defineInRange("lowEnergyThreshold", 1000L, 0L, Long.MAX_VALUE);
        client.pop();
        CLIENT_SPEC = client.build();
    }

    private MysteryConfig() {
    }
}
