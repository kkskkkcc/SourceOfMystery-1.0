package com.sourceofmystery.block;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.advancement.AdvancementHelper;
import com.sourceofmystery.energy.MysteryEnergy;
import com.sourceofmystery.recipe.altar.AltarRecipe;
import com.sourceofmystery.recipe.ModRecipes;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MysteryAltarBlock extends Block {

    public static final int SCAN_RADIUS = 5;
    private static final int MAX_LISTED_MATERIALS = 5;

    public MysteryAltarBlock() {
        super(Properties.copy(Blocks.STONE)
                .strength(3.0F, 6.0F)
                .noOcclusion());
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        ServerPlayer serverPlayer = (ServerPlayer) player;
        ServerLevel serverLevel = (ServerLevel) level;

        // 首次使用祭坛：授予"神秘起源"成就，初始化并解锁神秘之能 HUD
        if (!AdvancementHelper.hasAdvancement(serverPlayer, "mysterious_origin")) {
            AdvancementHelper.grantAdvancement(serverPlayer, "mysterious_origin");
        }
        MysteryEnergy.initPlayer(player);
        MysteryEnergy.setGuiUnlocked(player, true);

        // 多行信息发到聊天栏（action bar 只能显示一行，连续发送会互相覆盖）
        player.sendSystemMessage(Component.empty()
                .append(Component.translatable("message.sourceofmystery.altar.header")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(" ")
                .append(Component.translatable("message.sourceofmystery.altar.energy",
                        value(MysteryEnergy.getEnergy(player), ChatFormatting.GREEN),
                        value(MysteryEnergy.getEnergyMax(player), ChatFormatting.YELLOW))
                        .withStyle(ChatFormatting.GRAY)));

        List<ItemEntity> nearbyItems = findNearbyItems(level, pos);
        Map<Item, Integer> availableItems = countItems(nearbyItems);
        if (!availableItems.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.sourceofmystery.altar.materials",
                    describeMaterials(availableItems)).withStyle(ChatFormatting.GRAY));
        }

        List<ItemStack> stacks = nearbyItems.stream().map(ItemEntity::getItem).toList();
        AltarRecipe recipe = null;
        int[] allocation = null;
        for (AltarRecipe candidate : sortedRecipes(level)) {
            allocation = candidate.allocate(stacks);
            if (allocation != null) {
                recipe = candidate;
                break;
            }
        }
        if (recipe == null) {
            player.sendSystemMessage(Component.translatable("message.sourceofmystery.altar.no_recipe", SCAN_RADIUS)
                    .withStyle(ChatFormatting.RED));
            return InteractionResult.SUCCESS;
        }

        consumeItems(nearbyItems, allocation);

        ItemStack output = recipe.assemble(new SimpleContainer(), level.registryAccess());
        level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, output.copy()));

        spawnCraftingParticles(serverLevel, pos);
        playCraftingSound(serverLevel, pos);

        player.sendSystemMessage(Component.translatable("message.sourceofmystery.altar.success",
                value(recipe.getTier().getDisplayName(), ChatFormatting.YELLOW),
                output.getCount(),
                output.getHoverName().copy().withStyle(ChatFormatting.WHITE))
                .withStyle(ChatFormatting.GREEN));

        SourceOfMystery.LOGGER.debug("Player {} crafted {} using altar at {}",
                player.getName().getString(), output.getHoverName().getString(), pos);
        return InteractionResult.SUCCESS;
    }

    private static MutableComponent value(Object value, ChatFormatting color) {
        return Component.literal(String.valueOf(value)).withStyle(color);
    }

    private static Component describeMaterials(Map<Item, Integer> items) {
        MutableComponent list = Component.empty();
        int shown = 0;
        for (Map.Entry<Item, Integer> entry : items.entrySet()) {
            if (shown == MAX_LISTED_MATERIALS) {
                list.append(Component.literal(", ...").withStyle(ChatFormatting.GRAY));
                break;
            }
            if (shown > 0) {
                list.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
            }
            list.append(entry.getKey().getDescription().copy().withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(" x" + entry.getValue()).withStyle(ChatFormatting.YELLOW));
            shown++;
        }
        return list;
    }

    /**
     * 找出祭坛周围 SCAN_RADIUS 格球形范围内的掉落物
     */
    private static List<ItemEntity> findNearbyItems(Level level, BlockPos altarPos) {
        Vec3 center = altarPos.getCenter();
        double radiusSq = SCAN_RADIUS * SCAN_RADIUS;
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(altarPos).inflate(SCAN_RADIUS),
                e -> e.isAlive() && !e.getItem().isEmpty() && e.position().distanceToSqr(center) <= radiusSq);
    }

    private static Map<Item, Integer> countItems(List<ItemEntity> itemEntities) {
        Map<Item, Integer> items = new HashMap<>();
        for (ItemEntity itemEntity : itemEntities) {
            ItemStack stack = itemEntity.getItem();
            items.merge(stack.getItem(), stack.getCount(), Integer::sum);
        }
        return items;
    }

    /**
     * 所有祭坛配方，按等级从高到低排序（同等级按配方 ID 排序，保证结果稳定）
     */
    private static List<AltarRecipe> sortedRecipes(Level level) {
        return level.getRecipeManager().getAllRecipesFor(ModRecipes.ALTAR_TYPE.get()).stream()
                .sorted(Comparator.comparingInt((AltarRecipe r) -> r.getTier().getPriority()).reversed()
                        .thenComparing(r -> r.getId().toString()))
                .toList();
    }

    /**
     * 按配方的分配结果从掉落物中扣除材料
     */
    private static void consumeItems(List<ItemEntity> itemEntities, int[] allocation) {
        for (int i = 0; i < itemEntities.size(); i++) {
            int take = allocation[i];
            if (take <= 0) {
                continue;
            }
            ItemEntity itemEntity = itemEntities.get(i);
            ItemStack stack = itemEntity.getItem();
            if (take >= stack.getCount()) {
                itemEntity.discard();
            } else {
                // 必须通过 setItem 更新，直接改 stack 数量不会同步到客户端
                itemEntity.setItem(stack.copyWithCount(stack.getCount() - take));
            }
        }
    }

    /**
     * 生成祭坛转化粒子效果
     */
    private static void spawnCraftingParticles(ServerLevel level, BlockPos pos) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 1.0;
        double z = pos.getZ() + 0.5;

        spawnParticleCloud(level, ParticleTypes.ENCHANT, x, y, z, 30, 2.5, 0.05);
        spawnParticleCloud(level, ParticleTypes.WITCH, x, y, z, 15, 2.0, 0.02);
        spawnParticleCloud(level, ParticleTypes.END_ROD, x, y, z, 10, 1.5, 0.02);
    }

    private static void spawnParticleCloud(ServerLevel level, ParticleOptions particle, double x, double y, double z,
                                           int count, double size, double speed) {
        for (int i = 0; i < count; i++) {
            level.sendParticles(particle,
                    x + (level.random.nextDouble() - 0.5) * size,
                    y + level.random.nextDouble() * size,
                    z + (level.random.nextDouble() - 0.5) * size,
                    1, 0, 0, 0, speed);
        }
    }

    /**
     * 播放祭坛合成音效
     */
    private static void playCraftingSound(ServerLevel level, BlockPos pos) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 1.0;
        double z = pos.getZ() + 0.5;
        level.playSound(null, x, y, z, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.0f, 1.0f);
        level.playSound(null, x, y, z, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.5f, 1.2f);
    }
}
