package com.sourceofmystery.client.staff;

import com.mojang.blaze3d.platform.InputConstants;
import com.sourceofmystery.item.DimensionBreakerStaffItem;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import net.minecraftforge.client.event.InputEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.block.Blocks;
import com.sourceofmystery.network.ModNetwork;
import com.sourceofmystery.network.StaffMiningPacket;

@Mod.EventBusSubscriber(modid="sourceofmystery", value=Dist.CLIENT)
public final class StaffClientEvents {
    private static BlockPos mining;
    public static final KeyMapping WHEEL = new KeyMapping("key.sourceofmystery.staff_wheel",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_L, "key.categories.sourceofmystery");
    @SubscribeEvent public static void hand(net.minecraftforge.client.event.RenderHandEvent event) {
        var client=Minecraft.getInstance();var player=client.player;
        if(player==null||!player.isUsingItem()||event.getHand()!=player.getUsedItemHand()
                ||!(player.getUseItem().getItem() instanceof DimensionBreakerStaffItem))return;
        var arm=event.getHand()==InteractionHand.MAIN_HAND?player.getMainArm():player.getMainArm().getOpposite();
        var poses=event.getPoseStack();poses.pushPose();
        float lift=net.minecraft.util.Mth.clamp((player.getTicksUsingItem()+event.getPartialTick())/14f,0,1);
        poses.translate(0,lift*.15,0);
        ((com.sourceofmystery.mixin.ItemInHandAccess)client.gameRenderer.itemInHandRenderer)
                .som$arm(poses,event.getMultiBufferSource(),event.getPackedLight(),event.getEquipProgress(),0,arm);
        poses.popPose();
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var client = Minecraft.getInstance();
        while (WHEEL.consumeClick()) {
            if (client.player == null || client.screen != null || client.player.isUsingItem()) continue;
            for (InteractionHand hand : InteractionHand.values()) {
                if (client.player.getItemInHand(hand).getItem() instanceof DimensionBreakerStaffItem) {
                    client.setScreen(new StaffWheelScreen(hand)); break;
                }
            }
        }
        StaffVisuals.tick();
        BlockPos next=null;
        if(client.player!=null&&client.level!=null&&client.screen==null&&client.options.keyAttack.isDown()
                &&!StaffVisuals.frozen(client.player)&&client.player.getMainHandItem().getItem() instanceof DimensionBreakerStaffItem
                &&client.hitResult instanceof BlockHitResult hit&&hit.getType()==HitResult.Type.BLOCK) {
            var state=client.level.getBlockState(hit.getBlockPos());
            if(!state.is(Blocks.BEDROCK)&&state.getDestroySpeed(client.level,hit.getBlockPos())<0)next=hit.getBlockPos();
        }
        if(!java.util.Objects.equals(next,mining)) {
            if(mining!=null&&client.getConnection()!=null)ModNetwork.CHANNEL.sendToServer(new StaffMiningPacket(mining,false));
            mining=next;
            if(mining!=null)ModNetwork.CHANNEL.sendToServer(new StaffMiningPacket(mining,true));
        }
    }
    @SubscribeEvent public static void key(InputEvent.Key event) {
        var client=Minecraft.getInstance();
        if(event.getAction()!=GLFW.GLFW_PRESS||!WHEEL.matches(event.getKey(),event.getScanCode())||client.player==null
                ||client.screen!=null||client.player.isUsingItem()||StaffVisuals.frozen(client.player))return;
        for(InteractionHand hand:InteractionHand.values())if(client.player.getItemInHand(hand).getItem() instanceof DimensionBreakerStaffItem) {
            if(client.options.keyAdvancements.matches(event.getKey(),event.getScanCode())) {
                while(client.options.keyAdvancements.consumeClick()) {}
                client.options.keyAdvancements.setDown(false);
            }
            client.setScreen(new StaffWheelScreen(hand));return;
        }
    }
    @Mod.EventBusSubscriber(modid="sourceofmystery", value=Dist.CLIENT, bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event) { event.register(WHEEL); }
    }
}
