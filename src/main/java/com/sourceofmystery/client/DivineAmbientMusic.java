package com.sourceofmystery.client;

import com.sourceofmystery.SourceOfMystery;
import com.sourceofmystery.entity.DivineHeavenlyDaoBoss;
import com.sourceofmystery.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 神威天道存在期间（附近 192 格内）循环播放低沉的威压音乐，并暂停原版背景音乐；Boss 消失后淡出。
 * 用敌对生物音量（而不是音乐音量）播放，关掉背景音乐的玩家也能听到。
 */
@Mod.EventBusSubscriber(modid = SourceOfMystery.MOD_ID, value = Dist.CLIENT)
public final class DivineAmbientMusic {

    private static final double RANGE = 192.0;
    private static final int SCAN_INTERVAL = 10;

    private static PressureSound current;
    private static boolean bossNearby;

    private DivineAmbientMusic() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            bossNearby = false;
            if (current != null) {
                current.fadeOut();
                current = null;
            }
            return;
        }
        if (mc.level.getGameTime() % SCAN_INTERVAL == 0) {
            bossNearby = !mc.level.getEntitiesOfClass(DivineHeavenlyDaoBoss.class,
                    mc.player.getBoundingBox().inflate(RANGE), boss -> boss.isAlive()).isEmpty();
        }

        if (bossNearby) {
            mc.getMusicManager().stopPlaying();
            if (current == null || current.isStopped()) {
                current = new PressureSound();
                mc.getSoundManager().play(current);
            }
        } else if (current != null) {
            current.fadeOut();
            current = null;
        }
    }

    private static final class PressureSound extends AbstractTickableSoundInstance {
        private static final float FADE_STEP = 0.02f;
        private boolean fadingOut;

        PressureSound() {
            super(ModSounds.DIVINE_HEAVENLY_DAO_AMBIENT.get(), SoundSource.HOSTILE, SoundInstance.createUnseededRandom());
            this.looping = true;
            this.delay = 0;
            this.volume = FADE_STEP;
            this.relative = true;
            this.attenuation = Attenuation.NONE;
        }

        void fadeOut() {
            fadingOut = true;
        }

        @Override
        public void tick() {
            if (fadingOut) {
                volume -= FADE_STEP;
                if (volume <= 0) {
                    stop();
                }
            } else if (volume < 1.0f) {
                volume = Math.min(1.0f, volume + FADE_STEP);
            }
        }
    }
}
