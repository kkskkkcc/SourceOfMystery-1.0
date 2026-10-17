package com.sourceofmystery.guardian;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.block.ModBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * 守护祭坛：破碎的祭坛中间浮着淡蓝色的法阵。手持下界之星右键激活，祭坛原地播放修复动画，
 * 放出光柱后展开守护结界（见 {@link GuardianShields}、{@link GuardianAltarBlockEntity}）。
 * 祭坛被破坏时结界随之消失，激活用的下界之星也随之凋零（不返还）。
 */
public class GuardianAltarBlock extends BaseEntityBlock {

    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    // 三层石台 + 四角石柱（只取到一格高，柱帽和悬浮的碎块不算碰撞）
    private static final VoxelShape SHAPE = Shapes.or(
            box(0, 0, 0, 16, 2, 16),
            box(1, 2, 1, 15, 4, 15),
            box(3, 4, 3, 13, 7, 13),
            box(1, 4, 1, 3, 16, 3), box(13, 4, 1, 15, 16, 3),
            box(1, 4, 13, 3, 16, 15), box(13, 4, 13, 15, 16, 15));

    public GuardianAltarBlock() {
        super(Properties.of()
                .mapColor(MapColor.COLOR_LIGHT_BLUE)
                .strength(5.0F, 1200.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.DEEPSLATE_BRICKS)
                .noOcclusion()
                .pushReaction(PushReaction.BLOCK)
                .lightLevel(state -> state.getValue(ACTIVE) ? 15 : 6));
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GuardianAltarBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ModBlockEntities.GUARDIAN_ALTAR.get(), GuardianAltarBlockEntity::serverTick);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof GuardianAltarBlockEntity altar)) {
            return InteractionResult.PASS;
        }
        ServerLevel server = (ServerLevel) level;
        if (altar.isActive()) {
            AABB area = GuardianShields.area(level, pos);
            player.sendSystemMessage(Component.translatable("message.sourceofmystery.guardian_altar.running",
                    coords((int) area.minX, (int) area.minZ), coords((int) area.maxX - 1, (int) area.maxZ - 1),
                    GuardianShields.TOP_Y).withStyle(ChatFormatting.AQUA));
            return InteractionResult.SUCCESS;
        }
        ItemStack held = player.getItemInHand(hand);
        if (!held.is(Items.NETHER_STAR)) {
            player.displayClientMessage(Component.translatable("message.sourceofmystery.guardian_altar.need_star")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.SUCCESS;
        }
        if (pos.getY() > GuardianShields.TOP_Y) {
            player.sendSystemMessage(Component.translatable("message.sourceofmystery.guardian_altar.too_high",
                    GuardianShields.TOP_Y).withStyle(ChatFormatting.RED));
            return InteractionResult.SUCCESS;
        }
        Optional<BlockPos> conflict = GuardianShields.findConflict(server, pos);
        if (conflict.isPresent()) {
            BlockPos other = conflict.get();
            player.sendSystemMessage(Component.translatable("message.sourceofmystery.guardian_altar.conflict",
                    Component.literal(other.getX() + ", " + other.getY() + ", " + other.getZ())
                            .withStyle(ChatFormatting.YELLOW)).withStyle(ChatFormatting.RED));
            return InteractionResult.SUCCESS;
        }
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        altar.activate(server);
        player.sendSystemMessage(Component.translatable("message.sourceofmystery.guardian_altar.activated")
                .withStyle(ChatFormatting.AQUA));
        SourceOfMystery.LOGGER.info("Guardian altar activated at {} in {} by {}", pos, level.dimension().location(),
                player.getName().getString());
        return InteractionResult.CONSUME;
    }

    private static Component coords(int x, int z) {
        return Component.literal(x + ", " + z).withStyle(ChatFormatting.YELLOW);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && state.getValue(ACTIVE)) {
            player.sendSystemMessage(Component.translatable("message.sourceofmystery.guardian_altar.withered")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    /** 方块被移除（挖掉、爆炸、指令替换……）：结界消失，下界之星凋零 */
    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server) {
            GuardianShields.unregister(server, pos);
            if (state.getValue(ACTIVE)) {
                double x = pos.getX() + 0.5, y = pos.getY() + 0.8, z = pos.getZ() + 0.5;
                server.sendParticles(ParticleTypes.SMOKE, x, y, z, 40, 0.3, 0.3, 0.3, 0.02);
                server.sendParticles(ParticleTypes.SQUID_INK, x, y, z, 15, 0.2, 0.2, 0.2, 0.05);
                server.sendParticles(ParticleTypes.ASH, x, y, z, 60, 2.0, 1.5, 2.0, 0.0);
                server.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 2.0F, 0.7F);
                server.playSound(null, pos, SoundEvents.WITHER_DEATH, SoundSource.BLOCKS, 0.6F, 1.8F);
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
