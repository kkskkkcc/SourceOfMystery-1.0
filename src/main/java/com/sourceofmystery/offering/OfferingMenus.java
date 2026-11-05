package com.sourceofmystery.offering;

import com.sourceofmystery.SourceOfMystery;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.*;

public final class OfferingMenus {
    public static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(ForgeRegistries.MENU_TYPES,SourceOfMystery.MOD_ID);
    public static final RegistryObject<MenuType<OfferingMenu>> LOTTERY=MENUS.register("offering_lottery",()->IForgeMenuType.create(OfferingMenu::new));
    public static void open(ServerPlayer player,BlockPos anchor) {
        if(player.isSpectator()) return;
        NetworkHooks.openScreen(player,new SimpleMenuProvider((id,inventory,p)->new OfferingMenu(id,inventory,anchor),
                Component.translatable("offering.sourceofmystery.title")),buf->{
            buf.writeBoolean(anchor!=null);if(anchor!=null)buf.writeBlockPos(anchor);
        });
    }
    private OfferingMenus() {}
}
