package com.sourceofmystery.loot;

import com.mojang.serialization.Codec;
import com.sourceofmystery.SourceOfMystery;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModLootModifiers {

    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, SourceOfMystery.MOD_ID);

    public static final RegistryObject<Codec<AddChestLootModifier>> ADD_CHEST_LOOT =
            SERIALIZERS.register("add_chest_loot", AddChestLootModifier.CODEC);

    private ModLootModifiers() {
    }
}
