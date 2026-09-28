package com.sourceofmystery.block;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.capability.energy.MysteryEnergyCapability;
import com.sourceofmystery.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.item.ItemStack;
import javax.annotation.Nullable;
import java.util.List;

public class MysteryAltarTileEntity extends BlockEntity {
    private static final String TAG_PROCESSED = "processed";
    private static final String TAG_COOLDOWN = "cooldown";

    private boolean processed = false;
    private int cooldown = 0;

    public static final int SCAN_RADIUS = 3;
    public static final int ENERGY_COST = 50;

    // 工厂方法 - 由 BlockEntityType.Builder 调用
    public MysteryAltarTileEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MYSTERY_ALTAR.get(), pos, state);
    }

    public void onBlockStateChanged(BlockState oldState, BlockState newState) {
        this.processed = false;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean(TAG_PROCESSED, processed);
        tag.putInt(TAG_COOLDOWN, cooldown);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.processed = tag.getBoolean(TAG_PROCESSED);
        this.cooldown = tag.getInt(TAG_COOLDOWN);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
