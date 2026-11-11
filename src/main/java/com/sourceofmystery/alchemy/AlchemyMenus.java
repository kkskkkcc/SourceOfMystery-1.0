package com.sourceofmystery.alchemy;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.*;

public final class AlchemyMenus {
    public static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(ForgeRegistries.MENU_TYPES,"sourceofmystery");
    public static final RegistryObject<MenuType<AlchemyMenu>> TABLE=MENUS.register("alchemist_table",()->IForgeMenuType.create(AlchemyMenu::new));
    public static void open(ServerPlayer player,BlockPos pos,InteractionHand hand){
        if(player.isSpectator())return;
        NetworkHooks.openScreen(player,new SimpleMenuProvider((id,inv,p)->new AlchemyMenu(id,inv,pos,hand),Component.translatable("block.sourceofmystery.alchemist_table")),
                b->{b.writeBoolean(pos!=null);if(pos!=null)b.writeBlockPos(pos);b.writeByte(hand.ordinal());});
    }
    private AlchemyMenus(){}
}
