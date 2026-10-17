package com.sourceofmystery.block;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.advancement.AdvancementHelper;
import com.sourceofmystery.energy.MysteryEnergy;
import com.sourceofmystery.entity.BossSpawnHandler;
import com.sourceofmystery.item.ModItems;
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
import net.minecraft.world.entity.MoverType;
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
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = SourceOfMystery.MOD_ID)
public class MysteryAltarBlock extends Block {

    public static final int SCAN_RADIUS = 5;
    private static final int MAX_LISTED_MATERIALS = 5;
    private static final int RISE_TICKS = 30;
    private static final int HOLD_TICKS = 12;
    private static final int ORBIT_TICKS = 44;
    private static final int MATERIAL_DELAY = 3;
    private static final int MERGE_TICKS = 30;
    private static final int OUTPUT_TICK = 128;
    private static final int ANIMATION_TICKS = 140;
    private static final Map<ServerLevel, Map<BlockPos, CraftAnimation>> ACTIVE_ANIMATIONS = new HashMap<>();

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

        // 手持龙魂右键：献祭龙魂和五行之源，召唤神威天道
        ItemStack held = player.getItemInHand(hand);
        if (held.is(ModItems.DRAGON_SOUL.get())) {
            summonBoss(serverLevel, pos, player, held);
            return InteractionResult.SUCCESS;
        }

        BlockPos altarPos = pos.immutable();
        if (isAnimating(serverLevel, altarPos)) {
            player.sendSystemMessage(Component.translatable("message.sourceofmystery.altar.busy")
                    .withStyle(ChatFormatting.YELLOW));
            return InteractionResult.SUCCESS;
        }

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

        List<ItemStack> visualMaterials = consumedStacks(nearbyItems, allocation);
        List<Vec3> visualPositions = consumedPositions(nearbyItems, allocation);
        consumeItems(nearbyItems, allocation);
        ItemStack output = recipe.assemble(new SimpleContainer(), level.registryAccess());
        startCraftingAnimation(serverLevel, altarPos, visualMaterials, visualPositions, output,
                recipe.getTier().getDisplayName(), player.getUUID());
        return InteractionResult.SUCCESS;
    }

    private static boolean isAnimating(ServerLevel level, BlockPos pos) {
        return ACTIVE_ANIMATIONS.getOrDefault(level, Map.of()).containsKey(pos);
    }

    private static void startCraftingAnimation(ServerLevel level, BlockPos pos, List<ItemStack> materials,
                                               List<Vec3> positions, ItemStack output, String tier, UUID playerId) {
        CraftAnimation animation = new CraftAnimation(level, pos, materials, positions, output, tier, playerId);
        ACTIVE_ANIMATIONS.computeIfAbsent(level, ignored -> new HashMap<>()).put(pos, animation);
        level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.0F, 0.75F);
        level.sendParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5,
                24, 1.4, 0.2, 1.4, 0.05);
    }

    /**
     * 召唤神威天道需要的五行之源（加上手里的龙魂），各 1 个，从背包里扣
     */
    private static List<Item> summonOfferings() {
        return List.of(ModItems.WOOD_SOURCE.get(), ModItems.GOLD_SOURCE.get(), ModItems.WATER_SOURCE.get(),
                ModItems.FIRE_SOURCE.get(), ModItems.EARTH_SOURCE.get());
    }

    /**
     * 献祭龙魂 + 五行之源开始召唤；材料不全或同一维度已有召唤在进行时什么都不消耗
     */
    private static void summonBoss(ServerLevel level, BlockPos pos, Player player, ItemStack soul) {
        boolean creative = player.getAbilities().instabuild;
        if (!creative) {
            MutableComponent missing = Component.empty();
            boolean anyMissing = false;
            for (Item offering : summonOfferings()) {
                if (player.getInventory().countItem(offering) <= 0) {
                    if (anyMissing) {
                        missing.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
                    }
                    missing.append(offering.getDescription().copy().withStyle(ChatFormatting.YELLOW));
                    anyMissing = true;
                }
            }
            if (anyMissing) {
                player.sendSystemMessage(Component.translatable("message.sourceofmystery.altar.summon_missing", missing)
                        .withStyle(ChatFormatting.RED));
                return;
            }
        }
        if (!BossSpawnHandler.startSummon(level, pos)) {
            player.sendSystemMessage(Component.translatable("message.sourceofmystery.altar.summon_busy")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        if (!creative) {
            soul.shrink(1);
            for (Item offering : summonOfferings()) {
                consumeOne(player, offering);
            }
        }
        level.playSound(null, pos, SoundEvents.WITHER_SPAWN, SoundSource.BLOCKS, 1.0F, 0.8F);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5,
                40, 0.4, 0.4, 0.4, 0.05);
    }

    private static void consumeOne(Player player, Item item) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) {
                stack.shrink(1);
                return;
            }
        }
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

    /** 保存实际被扣除的物品和位置，用于生成不会被玩家拾取的动画实体。 */
    private static List<ItemStack> consumedStacks(List<ItemEntity> itemEntities, int[] allocation) {
        List<ItemStack> stacks = new java.util.ArrayList<>();
        for (int i = 0; i < itemEntities.size(); i++) {
            if (allocation[i] > 0) {
                stacks.add(itemEntities.get(i).getItem().copyWithCount(allocation[i]));
            }
        }
        return stacks;
    }

    private static List<Vec3> consumedPositions(List<ItemEntity> itemEntities, int[] allocation) {
        List<Vec3> positions = new java.util.ArrayList<>();
        for (int i = 0; i < itemEntities.size(); i++) {
            if (allocation[i] > 0) {
                positions.add(itemEntities.get(i).position());
            }
        }
        return positions;
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        Map<BlockPos, CraftAnimation> animations = ACTIVE_ANIMATIONS.get(level);
        if (animations == null || animations.isEmpty()) {
            return;
        }
        for (var iterator = animations.entrySet().iterator(); iterator.hasNext();) {
            var entry = iterator.next();
            CraftAnimation animation = entry.getValue();
            if (!level.hasChunkAt(entry.getKey())
                    || !level.getBlockState(entry.getKey()).is(ModBlocks.MYSTERY_ALTAR.get())) {
                animation.cancel(level);
                iterator.remove();
                continue;
            }
            if (animation.tick(level)) {
                iterator.remove();
            }
        }
        if (animations.isEmpty()) {
            ACTIVE_ANIMATIONS.remove(level);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        for (Map<BlockPos, CraftAnimation> animations : ACTIVE_ANIMATIONS.values()) {
            for (CraftAnimation animation : animations.values()) {
                animation.discardVisuals();
            }
        }
        ACTIVE_ANIMATIONS.clear();
    }

    /** 一次祭坛转化的服务端动画状态。 */
    private static final class CraftAnimation {
        private final BlockPos altarPos;
        private final List<VisualMaterial> materials = new java.util.ArrayList<>();
        private final ItemStack output;
        private final String tier;
        private final UUID playerId;
        private final int mergeStart;
        private int tick;
        private boolean outputSpawned;

        private CraftAnimation(ServerLevel level, BlockPos altarPos, List<ItemStack> stacks, List<Vec3> positions,
                               ItemStack output, String tier, UUID playerId) {
            this.altarPos = altarPos;
            this.output = output.copy();
            this.tier = tier;
            this.playerId = playerId;
            for (int i = 0; i < stacks.size(); i++) {
                ItemEntity entity = new ItemEntity(level, positions.get(i).x, positions.get(i).y,
                        positions.get(i).z, stacks.get(i).copy());
                materials.add(new VisualMaterial(entity, positions.get(i), i));
            }
            // 固定总时长为 140 tick（约 7 秒），材料数量不影响掉落时刻。
            this.mergeStart = OUTPUT_TICK - MERGE_TICKS;
        }

        private boolean tick(ServerLevel level) {
            if (tick < mergeStart) {
                for (VisualMaterial material : materials) {
                    animateMaterial(level, material);
                }
            } else {
                for (VisualMaterial material : materials) {
                    if (tick < mergeStart + MERGE_TICKS) {
                        animateMergeMaterial(level, material);
                    } else if (material.entity.isAlive()) {
                        material.entity.discard();
                    }
                }
                animateSphere(level);
                if (!outputSpawned && tick >= mergeStart + MERGE_TICKS) {
                    spawnOutput(level);
                    outputSpawned = true;
                }
                if (outputSpawned && tick >= ANIMATION_TICKS) {
                    return true;
                }
            }
            tick++;
            return false;
        }

        private void animateMaterial(ServerLevel level, VisualMaterial material) {
            int age = tick - material.index * MATERIAL_DELAY;
            if (age < 0) {
                return;
            }
            if (!material.attached) {
                level.addFreshEntity(material.entity);
                material.entity.setNoGravity(true);
                material.entity.setPickUpDelay(32767);
                material.entity.setInvulnerable(true);
                material.entity.noPhysics = true;
                material.attached = true;
            }
            Vec3 center = altarPos.getCenter().add(0, 1.55, 0);
            double initialAngle = material.index * Math.PI * 2.0 / Math.max(1, materials.size());
            Vec3 orbitStart = center.add(Math.cos(initialAngle) * 2.15, 0.12, Math.sin(initialAngle) * 2.15);
            Vec3 current;
            if (age < RISE_TICKS) {
                double u = smoothstep(age / (double) RISE_TICKS);
                current = material.source.lerp(orbitStart, u);
            } else if (age < RISE_TICKS + HOLD_TICKS) {
                // 先稳定在祭坛上方，再开始天体环绕。
                current = orbitStart;
            } else {
                double orbitAge = age - RISE_TICKS - HOLD_TICKS;
                double u = smoothstep(orbitAge / (double) ORBIT_TICKS);
                double radius = 2.15 * (1.0 - u) + 0.12;
                double angle = initialAngle + Math.min(ORBIT_TICKS, Math.max(0, orbitAge)) * 0.24;
                current = center.add(Math.cos(angle) * radius, 0.12 + Math.sin(angle * 0.6) * 0.08,
                        Math.sin(angle) * radius);
            }
            moveVisual(material, current);
            if (tick % 2 == 0) {
                level.sendParticles(ParticleTypes.END_ROD, current.x, current.y, current.z,
                        1, 0, 0, 0, 0);
            }
        }

        private void animateMergeMaterial(ServerLevel level, VisualMaterial material) {
            if (!material.attached) {
                // 超出常规配方数量时，仍保证未出现的材料从原位置平滑进入融合阶段。
                level.addFreshEntity(material.entity);
                material.entity.setNoGravity(true);
                material.entity.setPickUpDelay(32767);
                material.entity.setInvulnerable(true);
                material.entity.noPhysics = true;
                material.entity.setPos(material.source.x, material.source.y, material.source.z);
                material.attached = true;
            }
            Vec3 center = altarPos.getCenter().add(0, 1.55, 0);
            double initialAngle = material.index * Math.PI * 2.0 / Math.max(1, materials.size());
            double finalAngle = initialAngle + ORBIT_TICKS * 0.24;
            Vec3 orbitEnd = center.add(Math.cos(finalAngle) * 0.12,
                    0.12 + Math.sin(finalAngle * 0.6) * 0.08,
                    Math.sin(finalAngle) * 0.12);
            double u = smoothstep((tick - mergeStart) / (double) MERGE_TICKS);
            moveVisual(material, orbitEnd.lerp(center, u));
        }

        private static void moveVisual(VisualMaterial material, Vec3 target) {
            Vec3 delta = target.subtract(material.entity.position());
            if (delta.lengthSqr() > 1.0E-8) {
                material.entity.move(MoverType.SELF, delta);
            }
            material.entity.setDeltaMovement(Vec3.ZERO);
        }

        private void animateSphere(ServerLevel level) {
            Vec3 center = altarPos.getCenter().add(0, 1.55, 0);
            double u = Math.min(1.0, (tick - mergeStart) / (double) MERGE_TICKS);
            double radius = 0.8 * (1.0 - u) + 0.08;
            for (int i = 0; i < 10; i++) {
                double angle = tick * 0.25 + i * Math.PI * 2.0 / 10.0;
                level.sendParticles(ParticleTypes.DRAGON_BREATH,
                        center.x + Math.cos(angle) * radius,
                        center.y + Math.sin(angle * 1.7) * radius,
                        center.z + Math.sin(angle) * radius,
                        1, 0, 0, 0, 0);
            }
            if (tick % 4 == 0) {
                level.sendParticles(ParticleTypes.END_ROD, center.x, center.y, center.z,
                        8, radius, radius, radius, 0.02);
            }
        }

        private void spawnOutput(ServerLevel level) {
            Vec3 center = altarPos.getCenter().add(0, 1.45, 0);
            ItemEntity result = new ItemEntity(level, center.x, center.y, center.z, output.copy());
            result.setPickUpDelay(20);
            level.addFreshEntity(result);
            level.playSound(null, altarPos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.8F, 1.15F);
            level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z,
                    1, 0, 0, 0, 0);
            Player player = level.getPlayerByUUID(playerId);
            if (player != null) {
                player.sendSystemMessage(Component.translatable("message.sourceofmystery.altar.success",
                        value(tier, ChatFormatting.YELLOW), output.getCount(),
                        output.getHoverName().copy().withStyle(ChatFormatting.WHITE))
                        .withStyle(ChatFormatting.GREEN));
                SourceOfMystery.LOGGER.debug("Player {} crafted {} using altar at {}",
                        player.getName().getString(), output.getHoverName().getString(), altarPos);
            }
        }

        private void cancel(ServerLevel level) {
            for (VisualMaterial material : materials) {
                if (material.entity.isAlive()) {
                    material.entity.discard();
                }
                ItemEntity refund = new ItemEntity(level, altarPos.getX() + 0.5, altarPos.getY() + 1.0,
                        altarPos.getZ() + 0.5, material.entity.getItem().copy());
                refund.setPickUpDelay(10);
                level.addFreshEntity(refund);
            }
        }

        private void discardVisuals() {
            for (VisualMaterial material : materials) {
                if (material.entity.isAlive()) {
                    material.entity.discard();
                }
            }
        }

        private static double smoothstep(double value) {
            double u = Math.max(0, Math.min(1, value));
            return u * u * (3 - 2 * u);
        }
    }

    private static final class VisualMaterial {
        private final ItemEntity entity;
        private final Vec3 source;
        private final int index;
        private boolean attached;

        private VisualMaterial(ItemEntity entity, Vec3 source, int index) {
            this.entity = entity;
            this.source = source;
            this.index = index;
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
