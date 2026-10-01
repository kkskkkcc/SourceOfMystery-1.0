package com.sourceofmystery.sound;

import com.sourceofmystery.SourceOfMystery;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 模组音效。音频文件复用原版素材，在 assets/sourceofmystery/sounds.json 里调过音高和音量，
 * 想换成自制音频时只需改 sounds.json 指向新的 .ogg，不用改代码。
 */
public final class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, SourceOfMystery.MOD_ID);

    /** 瞬移前的蓄力声 */
    public static final RegistryObject<SoundEvent> DRAGON_SOUL_TELEPORT_CHARGE = register("dragon_soul.teleport_charge");

    /** 瞬移落地 */
    public static final RegistryObject<SoundEvent> DRAGON_SOUL_TELEPORT = register("dragon_soul.teleport");

    /** 劈 */
    public static final RegistryObject<SoundEvent> DRAGON_SOUL_CLEAVE = register("dragon_soul.cleave");

    /** 刺 */
    public static final RegistryObject<SoundEvent> DRAGON_SOUL_THRUST = register("dragon_soul.thrust");

    /** 砍 */
    public static final RegistryObject<SoundEvent> DRAGON_SOUL_SLASH = register("dragon_soul.slash");

    /** 近战命中 */
    public static final RegistryObject<SoundEvent> DRAGON_SOUL_HIT = register("dragon_soul.hit");

    /** 踢 */
    public static final RegistryObject<SoundEvent> DRAGON_SOUL_KICK = register("dragon_soul.kick");

    /** 抓取俯冲 */
    public static final RegistryObject<SoundEvent> DRAGON_SOUL_GRAB = register("dragon_soul.grab");

    /** 抬手召唤法阵 */
    public static final RegistryObject<SoundEvent> DRAGON_SOUL_MAGIC_CHARGE = register("dragon_soul.magic_charge");

    /** 掷枪 */
    public static final RegistryObject<SoundEvent> DRAGON_SOUL_SPEAR_THROW = register("dragon_soul.spear_throw");

    /** 长枪飞回 */
    public static final RegistryObject<SoundEvent> DRAGON_SOUL_SPEAR_RETURN = register("dragon_soul.spear_return");

    /** 转枪后摇 */
    public static final RegistryObject<SoundEvent> DRAGON_SOUL_FLOURISH = register("dragon_soul.flourish");

    /** 振翅 */
    public static final RegistryObject<SoundEvent> DRAGON_SOUL_WINGS = register("dragon_soul.wings");

    /** 出场：威压 */
    public static final RegistryObject<SoundEvent> DRAGON_SOUL_OMEN = register("dragon_soul.omen");

    /** 出场：空间裂缝撕裂 */
    public static final RegistryObject<SoundEvent> DRAGON_SOUL_RIFT = register("dragon_soul.rift");

    /** 出场：龙吟 */
    public static final RegistryObject<SoundEvent> DRAGON_SOUL_ROAR = register("dragon_soul.roar");

    /** 受伤 */
    public static final RegistryObject<SoundEvent> DRAGON_SOUL_HURT = register("dragon_soul.hurt");

    /** 神威天道存在期间循环播放的威压音乐 */
    public static final RegistryObject<SoundEvent> DIVINE_HEAVENLY_DAO_AMBIENT = register("divine_heavenly_dao.ambient");

    /** 锤击地面 */
    public static final RegistryObject<SoundEvent> DIVINE_HEAVENLY_DAO_SLAM = register("divine_heavenly_dao.slam");

    /** 技能前摇 */
    public static final RegistryObject<SoundEvent> DIVINE_HEAVENLY_DAO_CHARGE = register("divine_heavenly_dao.charge");

    private ModSounds() {
    }

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(SourceOfMystery.MOD_ID, name)));
    }
}
