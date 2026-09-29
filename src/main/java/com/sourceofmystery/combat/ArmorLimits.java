package com.sourceofmystery.combat;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.config.MysteryConfig;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * 突破原版护甲限制。
 * <p>
 * 原版有两层限制：
 * <ol>
 *     <li>护甲值属性上限 30、护甲韧性上限 20，超出部分直接被截断；</li>
 *     <li>护甲减伤公式最多减免 80% 伤害，护甲达到 20 左右后再多也没有收益。</li>
 * </ol>
 * 这里先提高两个属性的上限，再对超出 30 点的护甲追加一层减伤。
 * 护甲不超过 30 的实体（包括所有原版生物）行为与原版完全一致。
 */
@Mod.EventBusSubscriber(modid = SourceOfMystery.MOD_ID)
public final class ArmorLimits {

    /** 原版护甲值属性上限，超出这部分的护甲才参与额外减伤 */
    private static final double VANILLA_ARMOR_CAP = 30.0;

    private ArmorLimits() {
    }

    /**
     * 提高护甲 / 护甲韧性的属性上限。需要在属性被使用之前（通用设置阶段）调用。
     */
    public static void raiseAttributeCaps() {
        if (!MysteryConfig.RAISE_ARMOR_CAPS.get()) {
            return;
        }
        setMaxValue(Attributes.ARMOR, MysteryConfig.ARMOR_CAP.get());
        setMaxValue(Attributes.ARMOR_TOUGHNESS, MysteryConfig.ARMOR_TOUGHNESS_CAP.get());
    }

    /**
     * RangedAttribute 的上限是 final 字段，没有公开的修改途径，只能反射。
     * 按"当前值等于 getMaxValue()"来定位字段，避免依赖混淆后的字段名。
     */
    private static void setMaxValue(Attribute attribute, double newMax) {
        if (!(attribute instanceof RangedAttribute ranged)) {
            SourceOfMystery.LOGGER.warn("Attribute {} is not a RangedAttribute, cannot raise its cap", attribute.getDescriptionId());
            return;
        }
        double oldMax = ranged.getMaxValue();
        if (oldMax >= newMax) {
            return; // 已经被其他模组调得更高，保持不变
        }
        try {
            for (Field field : RangedAttribute.class.getDeclaredFields()) {
                if (field.getType() != double.class || Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);
                if (field.getDouble(ranged) == oldMax) {
                    field.setDouble(ranged, newMax);
                    SourceOfMystery.LOGGER.info("Raised cap of {} from {} to {}", attribute.getDescriptionId(), oldMax, newMax);
                    return;
                }
            }
            SourceOfMystery.LOGGER.warn("Could not find the max value field of {}", attribute.getDescriptionId());
        } catch (ReflectiveOperationException | RuntimeException e) {
            SourceOfMystery.LOGGER.error("Failed to raise cap of {}", attribute.getDescriptionId(), e);
        }
    }

    /**
     * 超出 30 点的护甲追加减伤：额外减伤比例 = 超出值 / (超出值 + scale)。
     * 在原版护甲计算之前按比例缩小伤害，与原版的护甲减伤相乘叠加。
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide || event.getSource().is(DamageTypeTags.BYPASSES_ARMOR)) {
            return;
        }
        double scale = MysteryConfig.EXTRA_ARMOR_REDUCTION_SCALE.get();
        double excess = entity.getArmorValue() - VANILLA_ARMOR_CAP;
        if (scale <= 0 || excess <= 0) {
            return;
        }
        event.setAmount((float) (event.getAmount() * scale / (excess + scale)));
    }
}
