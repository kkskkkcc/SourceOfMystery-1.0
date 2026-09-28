package com.sourceofmystery.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.capability.energy.MysteryEnergyCapability;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collection;

/**
 * 调试指令：/somenergy <玩家> <数值>
 * 用于设置玩家的神秘之能（能量和上限同时设为该值），数值范围 1-1000000
 */
@Mod.EventBusSubscriber(modid = SourceOfMystery.MOD_ID)
public class ModCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("somenergy")
                        .requires(source -> source.hasPermission(2)) // 需要权限等级 2（作弊/OP）
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("value", IntegerArgumentType.integer(1, 1000000))
                                        .executes(ModCommands::setEnergy)))
        );
    }

    private static int setEnergy(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "player");
        int value = IntegerArgumentType.getInteger(ctx, "value");

        int count = 0;
        for (ServerPlayer player : players) {
            MysteryEnergyCapability.setEnergyAndMax(player, value);
            count++;
        }

        final int finalCount = count;
        ctx.getSource().sendSuccess(
                () -> Component.literal("§a已设置 §e" + finalCount + " §a名玩家的神秘之能为 §e" + value),
                true
        );
        return count;
    }
}
