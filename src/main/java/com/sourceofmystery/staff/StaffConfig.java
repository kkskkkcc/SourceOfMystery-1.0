package com.sourceofmystery.staff;

import net.minecraftforge.common.ForgeConfigSpec;
import java.util.EnumMap;
import java.util.Map;

public final class StaffConfig {
    public record SpellSettings(ForgeConfigSpec.LongValue cost, ForgeConfigSpec.IntValue castTicks) {}
    public static final Map<StaffSpell, SpellSettings> SPELLS = new EnumMap<>(StaffSpell.class);
    public static ForgeConfigSpec.DoubleValue ATTACK_DAMAGE;
    public static ForgeConfigSpec.DoubleValue BREAK_SECONDS;
    public static ForgeConfigSpec.IntValue SPLIT_LENGTH, SPLIT_WIDTH, SPLIT_TOP, LIFT_DEPTH, LIFT_HEIGHT;
    public static ForgeConfigSpec.IntValue TIME_STOP_TICKS, TIME_STOP_COOLDOWN, PORTAL_TICKS;
    public static ForgeConfigSpec.DoubleValue SPLIT_DAMAGE;
    public static final ForgeConfigSpec SPEC;

    static {
        ForgeConfigSpec.Builder builder=new ForgeConfigSpec.Builder();
        define(builder);SPEC=builder.build();
    }

    private StaffConfig() {}

    private static void define(ForgeConfigSpec.Builder builder) {
        builder.comment("传说中打破次元的超位权杖").push("dimensionBreakerStaff");
        ATTACK_DAMAGE = builder.comment("包含玩家基础攻击力的普通攻击伤害")
                .defineInRange("attackDamage", 199.0, 1.0, 1000000.0);
        BREAK_SECONDS = builder.defineInRange("breakSeconds", .9, .05, 60);
        SPLIT_LENGTH = builder.comment("裂地魔法的裂缝长度，单位格")
                .defineInRange("splitLength", 500, 1, 512);
        SPLIT_WIDTH = builder.defineInRange("splitWidth", 30, 1, 128);
        SPLIT_TOP = builder.defineInRange("splitTopY", 255, -2048, 2048);
        SPLIT_DAMAGE = builder.defineInRange("splitDamage", 1000.0, 0, 1000000);
        LIFT_DEPTH = builder.defineInRange("liftDepth", 50, 1, 128);
        LIFT_HEIGHT = builder.defineInRange("liftHeight", 100, 1, 256);
        TIME_STOP_TICKS = builder.defineInRange("timeStopTicks", 240, 1, 2400);
        TIME_STOP_COOLDOWN = builder.comment("时间恢复后的冷却时间，单位 tick").defineInRange("timeStopCooldownTicks", 60, 0, 72000);
        PORTAL_TICKS = builder.defineInRange("portalTicks", 200, 1, 72000);
        for (StaffSpell spell : StaffSpell.values()) {
            builder.push(spell.id());
            var cost = builder.comment("成功释放消耗的神秘之能")
                    .defineInRange("energyCost", spell.defaultCost(), 0L, Long.MAX_VALUE);
            var ticks = builder.comment("施法动作时长，单位 tick（20 tick 为一秒）")
                    .defineInRange("castTicks", spell.defaultCastTicks(), 0, 72000);
            SPELLS.put(spell, new SpellSettings(cost, ticks));
            builder.pop();
        }
        builder.pop();
    }
}
