package com.sourceofmystery.block;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.advancement.AdvancementHelper;
import com.sourceofmystery.capability.energy.MysteryEnergyCapability;
import com.sourceofmystery.recipe.altar.AltarRecipe;
import com.sourceofmystery.recipe.altar.AltarRecipeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MysteryAltarBlock extends Block implements EntityBlock {

    public static final int SCAN_RADIUS = 5;

    public MysteryAltarBlock() {
        super(Properties.copy(Blocks.STONE)
                .strength(3.0F, 6.0F)
                .noOcclusion());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModBlockEntities.MYSTERY_ALTAR.get().create(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        ServerPlayer serverPlayer = (ServerPlayer) player;

        // ==================== P0功能：神秘起源检查 ====================
        // 检查是否已完成"神秘起源"成就
        if (!AdvancementHelper.hasAdvancement(serverPlayer, "mysterious_origin")) {
            // 授予"神秘起源"成就
            AdvancementHelper.grantAdvancement(serverPlayer, "mysterious_origin");
            // 显示成就完成消息
            player.displayClientMessage(Component.literal("§6§l【成就解锁】§r §e神秘起源"), true);
            SourceOfMystery.LOGGER.info("Player {} unlocked mysterious_origin advancement", player.getName().getString());
        }

        // ==================== P0功能：初始化神秘之能 ====================
        // 初始化玩家神秘之能（如果尚未初始化）
        MysteryEnergyCapability.initPlayer(player);
        // 解锁神秘之能GUI
        MysteryEnergyCapability.setGuiUnlocked(player, true);

        // 获取当前神秘之能
        long currentEnergy = MysteryEnergyCapability.getEnergy(player);

        // 扫描祭坛周围的物品
        Map<Item, Integer> availableItems = scanNearbyItems(level, pos);

        // 查找最佳配方（最高优先级）
        AltarRecipeManager manager = AltarRecipeManager.getInstance();
        AltarRecipe matchingRecipe = manager.findBestMatchingRecipe(availableItems);

        // 显示祭坛状态信息
        player.displayClientMessage(Component.literal("§6§l【神秘祭坛】§r"), true);
        player.displayClientMessage(Component.literal("§7神秘之能: §a" + currentEnergy + " §7/ §e∞"), true);
        player.displayClientMessage(Component.literal("§7祭坛状态: §a运行中§7 | §7扫描范围: §e" + SCAN_RADIUS + "格"), true);

        if (!availableItems.isEmpty()) {
            // 显示扫描到的材料
            String itemsList = availableItems.entrySet().stream()
                    .map(e -> e.getKey().getDefaultInstance().getHoverName().getString() + " x" + e.getValue())
                    .limit(5)
                    .collect(Collectors.joining("§7, §e"));
            player.displayClientMessage(Component.literal("§7检测到材料: §e" + itemsList), true);
        }

        if (matchingRecipe != null) {
            // 找到配方，显示配方信息
            player.displayClientMessage(Component.literal("§a检测到可用配方：§e" + matchingRecipe.getTier().getDisplayName() + "§a级 - " + matchingRecipe.getDescription()), true);

            // 消耗材料
            matchingRecipe.consumeIngredients(availableItems);

            // 从世界中移除消耗的材料
            consumeItemsFromWorld(level, pos, matchingRecipe.getIngredients());

            // 在祭坛位置生成输出物品
            ItemStack output = matchingRecipe.getOutput().copy();
            ItemEntity outputEntity = new ItemEntity(level,
                    pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                    output);
            level.addFreshEntity(outputEntity);

            // 播放粒子效果
            spawnCraftingParticles((ServerLevel) level, pos);

            // 播放音效
            playCraftingSound((ServerLevel) level, pos);

            player.displayClientMessage(Component.literal("§a§l【转化成功！】§r"), true);
            player.displayClientMessage(Component.literal("§7获得: §f" + output.getCount() + "x " + output.getHoverName().getString()), true);

            SourceOfMystery.LOGGER.info("Player {} crafted {} using altar at {}",
                    player.getName().getString(), matchingRecipe.getDescription(), pos);
        } else {
            // 没有找到配方
            player.displayClientMessage(Component.literal("§c【无匹配配方】"), true);
            player.displayClientMessage(Component.literal("§7将材料放置在祭坛周围" + SCAN_RADIUS + "格范围内"), true);
            player.displayClientMessage(Component.literal("§7祭坛将自动检测可用配方"), true);
        }

        return InteractionResult.SUCCESS;
    }

    /**
     * 扫描祭坛周围5格球形范围内的物品
     */
    private Map<Item, Integer> scanNearbyItems(Level level, BlockPos altarPos) {
        Map<Item, Integer> items = new HashMap<>();

        AABB scanBox = new AABB(
                altarPos.getX() - SCAN_RADIUS, altarPos.getY() - SCAN_RADIUS, altarPos.getZ() - SCAN_RADIUS,
                altarPos.getX() + SCAN_RADIUS + 1, altarPos.getY() + SCAN_RADIUS + 1, altarPos.getZ() + SCAN_RADIUS + 1
        );

        List<ItemEntity> nearbyItems = level.getEntitiesOfClass(ItemEntity.class, scanBox);

        for (ItemEntity itemEntity : nearbyItems) {
            if (!itemEntity.isAlive()) continue;
            if (itemEntity.getItem().isEmpty()) continue;

            // 球形距离判断
            double dx = itemEntity.getX() - (altarPos.getX() + 0.5);
            double dy = itemEntity.getY() - (altarPos.getY() + 0.5);
            double dz = itemEntity.getZ() - (altarPos.getZ() + 0.5);
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

            // 只处理5格范围内的物品
            if (distance > SCAN_RADIUS) continue;

            ItemStack stack = itemEntity.getItem();
            Item item = stack.getItem();
            int count = stack.getCount();

            items.merge(item, count, Integer::sum);
        }

        return items;
    }

    /**
     * 从世界中消耗材料
     */
    private void consumeItemsFromWorld(Level level, BlockPos altarPos, Map<Item, Integer> consumedItems) {
        AABB scanBox = new AABB(
                altarPos.getX() - SCAN_RADIUS, altarPos.getY() - SCAN_RADIUS, altarPos.getZ() - SCAN_RADIUS,
                altarPos.getX() + SCAN_RADIUS + 1, altarPos.getY() + SCAN_RADIUS + 1, altarPos.getZ() + SCAN_RADIUS + 1
        );

        List<ItemEntity> nearbyItems = level.getEntitiesOfClass(ItemEntity.class, scanBox);

        // 复制需要消耗的数量
        Map<Item, Integer> toConsume = new HashMap<>(consumedItems);

        for (ItemEntity itemEntity : nearbyItems) {
            if (toConsume.isEmpty()) break;
            if (!itemEntity.isAlive()) continue;

            // 球形距离判断
            double dx = itemEntity.getX() - (altarPos.getX() + 0.5);
            double dy = itemEntity.getY() - (altarPos.getY() + 0.5);
            double dz = itemEntity.getZ() - (altarPos.getZ() + 0.5);
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

            // 只处理5格范围内的物品
            if (distance > SCAN_RADIUS) continue;

            ItemStack stack = itemEntity.getItem();
            Item item = stack.getItem();

            if (toConsume.containsKey(item)) {
                int needed = toConsume.get(item);
                int available = stack.getCount();

                if (available < needed) {
                    // 当前堆叠不足以满足需求：全部消耗，剩余需求继续找下一个实体
                    int remaining = needed - available;
                    toConsume.put(item, remaining);
                    itemEntity.discard();
                } else if (available == needed) {
                    // 恰好满足
                    toConsume.remove(item);
                    itemEntity.discard();
                } else {
                    // 当前堆叠超出需求：消耗部分
                    stack.setCount(available - needed);
                    toConsume.remove(item);
                }
            }
        }
    }

    /**
     * 生成祭坛转化粒子效果
     */
    private void spawnCraftingParticles(ServerLevel level, BlockPos pos) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 1.0;
        double z = pos.getZ() + 0.5;

        // 生成附魔符文粒子
        for (int i = 0; i < 30; i++) {
            double offsetX = (level.random.nextDouble() - 0.5) * 2.5;
            double offsetY = level.random.nextDouble() * 2.5;
            double offsetZ = (level.random.nextDouble() - 0.5) * 2.5;

            level.sendParticles(ParticleTypes.ENCHANT,
                    x + offsetX, y + offsetY, z + offsetZ,
                    1, 0, 0, 0, 0.05);
        }

        // 生成女巫粒子
        for (int i = 0; i < 15; i++) {
            double offsetX = (level.random.nextDouble() - 0.5) * 2.0;
            double offsetY = level.random.nextDouble() * 2.0;
            double offsetZ = (level.random.nextDouble() - 0.5) * 2.0;

            level.sendParticles(ParticleTypes.WITCH,
                    x + offsetX, y + offsetY, z + offsetZ,
                    1, 0, 0, 0, 0.02);
        }

        // 生成结束杆粒子
        for (int i = 0; i < 10; i++) {
            double offsetX = (level.random.nextDouble() - 0.5) * 1.5;
            double offsetY = level.random.nextDouble() * 1.5;
            double offsetZ = (level.random.nextDouble() - 0.5) * 1.5;

            level.sendParticles(ParticleTypes.END_ROD,
                    x + offsetX, y + offsetY, z + offsetZ,
                    1, 0, 0, 0, 0.02);
        }
    }

    /**
     * 播放祭坛合成音效
     */
    private void playCraftingSound(ServerLevel level, BlockPos pos) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 1.0;
        double z = pos.getZ() + 0.5;

        // 播放附魔台激活音效
        level.playSound(null, x, y, z, SoundEvents.ENCHANTMENT_TABLE_USE, net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.0f);

        // 播放信标激活音效
        level.playSound(null, x, y, z, SoundEvents.BEACON_ACTIVATE, net.minecraft.sounds.SoundSource.BLOCKS, 0.5f, 1.2f);
    }
}
