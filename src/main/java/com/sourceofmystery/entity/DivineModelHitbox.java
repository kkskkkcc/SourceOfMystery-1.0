package com.sourceofmystery.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.entity.PartEntity;

/** Forge indexes parts separately, so rays near the giant's head can find it. */
final class DivineModelHitbox extends PartEntity<DivineHeavenlyDaoBoss> {
    DivineModelHitbox(DivineHeavenlyDaoBoss parent) { super(parent); }

    void followParent() {
        setPos(getParent().position());
        setBoundingBox(getParent().modelHurtbox());
    }

    @Override public boolean isPickable() { return getParent().isAlive() && !getParent().isInvisible(); }
    @Override public boolean hurt(DamageSource source, float amount) { return getParent().hurt(source, amount); }
    @Override public boolean is(Entity entity) { return this == entity || getParent() == entity; }
    @Override public ItemStack getPickResult() { return getParent().getPickResult(); }
    @Override public boolean shouldBeSaved() { return false; }
    @Override protected void defineSynchedData() {}
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
}
