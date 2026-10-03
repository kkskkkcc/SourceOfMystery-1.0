package com.sourceofmystery.network;

import com.sourceofmystery.SourceOfMystery;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public class ModNetwork {

    private static final String PROTOCOL_VERSION = "3";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(SourceOfMystery.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int id = 0;

    public static void register() {
        CHANNEL.registerMessage(id++,
                EnergySyncPacket.class,
                EnergySyncPacket::encode,
                EnergySyncPacket::decode,
                EnergySyncPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++,
                SatelliteSyncPacket.class,
                SatelliteSyncPacket::encode,
                SatelliteSyncPacket::decode,
                SatelliteSyncPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++,
                BossIntroPacket.class,
                BossIntroPacket::encode,
                BossIntroPacket::decode,
                BossIntroPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++,
                ScreenEffectPacket.class,
                ScreenEffectPacket::encode,
                ScreenEffectPacket::decode,
                ScreenEffectPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
}
