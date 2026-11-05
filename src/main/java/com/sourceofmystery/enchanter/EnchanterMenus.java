package com.sourceofmystery.enchanter;

import com.sourceofmystery.SourceOfMystery;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.*;

public final class EnchanterMenus {
    public static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(ForgeRegistries.MENU_TYPES,SourceOfMystery.MOD_ID);
    public static final RegistryObject<MenuType<EnchanterMenu>> ENCHANTER=MENUS.register("mystery_enchanter",()->IForgeMenuType.create(EnchanterMenu::new));
    public static void open(ServerPlayer player,BlockPos pos) {
        if(!player.isSpectator())NetworkHooks.openScreen(player,new SimpleMenuProvider((id,inv,p)->new EnchanterMenu(id,inv,pos),Component.translatable("block.sourceofmystery.mystery_enchanter")),buf->buf.writeBlockPos(pos));
    }
}
