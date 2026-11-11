package com.sourceofmystery.network;

import com.sourceofmystery.SourceOfMystery;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public class ModNetwork {

    private static final String PROTOCOL_VERSION = "17";
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
        CHANNEL.registerMessage(id++,
                WeepingIntroPacket.class,
                WeepingIntroPacket::encode,
                WeepingIntroPacket::decode,
                WeepingIntroPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++,
                RootPacket.class,
                RootPacket::encode,
                RootPacket::decode,
                RootPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, DimensionalOfferingPacket.class,
                DimensionalOfferingPacket::encode, DimensionalOfferingPacket::decode,
                DimensionalOfferingPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, StaffSelectionPacket.class,
                StaffSelectionPacket::encode, StaffSelectionPacket::decode,
                StaffSelectionPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, StaffEffectPacket.class,
                StaffEffectPacket::encode, StaffEffectPacket::decode,
                StaffEffectPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, StaffMiningPacket.class, StaffMiningPacket::encode, StaffMiningPacket::decode,
                StaffMiningPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, OfferingRollPacket.class, OfferingRollPacket::encode, OfferingRollPacket::decode,
                OfferingRollPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, DivineCinematicPacket.class, DivineCinematicPacket::encode, DivineCinematicPacket::decode,
                DivineCinematicPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, DomainSyncPacket.class,DomainSyncPacket::encode,DomainSyncPacket::decode,DomainSyncPacket::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, DomainStopPacket.class,DomainStopPacket::encode,DomainStopPacket::decode,DomainStopPacket::handle,Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++,MeteorAimPacket.class,MeteorAimPacket::encode,MeteorAimPacket::decode,MeteorAimPacket::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++,WeatherSyncPacket.class,WeatherSyncPacket::encode,WeatherSyncPacket::decode,WeatherSyncPacket::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++,WeatherStopPacket.class,WeatherStopPacket::encode,WeatherStopPacket::decode,WeatherStopPacket::handle,Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, DivineCinematicDonePacket.class, DivineCinematicDonePacket::encode, DivineCinematicDonePacket::decode,
                DivineCinematicDonePacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++,TimeMagicSyncPacket.class,TimeMagicSyncPacket::encode,TimeMagicSyncPacket::decode,TimeMagicSyncPacket::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++,DomainSlashPacket.class,DomainSlashPacket::encode,DomainSlashPacket::decode,DomainSlashPacket::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++,AlchemyTradePacket.class,AlchemyTradePacket::encode,AlchemyTradePacket::decode,AlchemyTradePacket::handle,Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++,EasternRitePacket.class,EasternRitePacket::encode,EasternRitePacket::decode,EasternRitePacket::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
}
