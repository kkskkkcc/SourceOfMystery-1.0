package com.sourceofmystery.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.sourceofmystery.client.staff.StaffItemExtensions;
import com.sourceofmystery.staff.StaffCasting;
import com.sourceofmystery.staff.StaffConfig;
import com.sourceofmystery.staff.StaffSpell;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;
import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nullable;

public final class DimensionBreakerStaffItem extends Item implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public DimensionBreakerStaffItem() {
        super(new Properties().stacksTo(1).fireResistant().rarity(Rarity.EPIC));
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    @Override public Component getName(ItemStack stack) {
        String name = Component.translatable(getDescriptionId(stack)).getString();
        var text = Component.empty();
        int[] letters = name.codePoints().toArray();
        float phase = (System.currentTimeMillis() % 6000) / 6000f;
        for (int index = 0; index < letters.length; index++) {
            int rgb = Mth.hsvToRgb((phase + index / (float) Math.max(1, letters.length)) % 1, .55f, 1);
            text.append(Component.literal(new String(Character.toChars(letters[index])))
                    .withStyle(Style.EMPTY.withColor(rgb)));
        }
        return text;
    }

    @Override public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> text, TooltipFlag flag) {
        text.add(Component.translatable("tooltip.sourceofmystery.dimension_breaker_staff.lore").withStyle(ChatFormatting.GOLD));
        text.add(Component.translatable("tooltip.sourceofmystery.dimension_breaker_staff.dream").withStyle(ChatFormatting.GRAY));
        text.add(StaffSpell.selected(stack).title().copy().withStyle(ChatFormatting.WHITE));
    }

    @Override public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        if (slot != EquipmentSlot.MAINHAND) return super.getAttributeModifiers(slot, stack);
        return ImmutableMultimap.of(
                Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Staff damage",
                        StaffConfig.ATTACK_DAMAGE.get() - 1, AttributeModifier.Operation.ADDITION),
                Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Staff speed",
                        -2.4, AttributeModifier.Operation.ADDITION));
    }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if(level.isClientSide&&com.sourceofmystery.client.ClientEnergyCache.energy<StaffSpell.selected(stack).cost())
            return InteractionResultHolder.fail(stack);
        if (!level.isClientSide && !StaffCasting.begin(player, hand, stack)) return InteractionResultHolder.fail(stack);
        if (StaffSpell.selected(stack).castTicks() > 0) player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override public int getUseDuration(ItemStack stack) { return Math.max(1, StaffSpell.selected(stack).castTicks()); }
    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.NONE; }
    @Override public boolean isCorrectToolForDrops(net.minecraft.world.level.block.state.BlockState state) { return true; }
    @Override public boolean canAttackBlock(net.minecraft.world.level.block.state.BlockState state, Level level,
                                            net.minecraft.core.BlockPos position, Player player) {
        return !state.is(net.minecraft.world.level.block.Blocks.BEDROCK);
    }
    @Override public boolean canPerformAction(ItemStack stack,net.minecraftforge.common.ToolAction action) {
        return net.minecraftforge.common.ToolActions.DEFAULT_PICKAXE_ACTIONS.contains(action)
                ||net.minecraftforge.common.ToolActions.DEFAULT_AXE_ACTIONS.contains(action)
                ||net.minecraftforge.common.ToolActions.DEFAULT_SHOVEL_ACTIONS.contains(action)
                ||net.minecraftforge.common.ToolActions.DEFAULT_HOE_ACTIONS.contains(action)
                ||net.minecraftforge.common.ToolActions.DEFAULT_SHEARS_ACTIONS.contains(action);
    }
    @Override public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (!level.isClientSide && entity instanceof Player player) StaffCasting.tick(player, stack, remaining);
    }
    @Override public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof Player player) StaffCasting.finish(player, stack);
        return stack;
    }
    @Override public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remaining) {
        if (!level.isClientSide && entity instanceof Player player) StaffCasting.cancel(player);
    }

    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        var controller = new AnimationController<>(this, "staff", 4,
                state -> state.setAndContinue(RawAnimation.begin().thenLoop("staff.idle")));
        controller.triggerableAnim("charge", RawAnimation.begin().thenLoop("staff.charge"));
        for (String name : List.of("cleave", "uplift", "rift", "time_stop"))
            controller.triggerableAnim(name, RawAnimation.begin().thenPlay("staff." + name));
        controllers.add(controller);
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
    @Override public void initializeClient(Consumer<IClientItemExtensions> consumer) { consumer.accept(new StaffItemExtensions()); }
}
