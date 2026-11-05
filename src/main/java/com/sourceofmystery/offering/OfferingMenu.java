package com.sourceofmystery.offering;

import com.sourceofmystery.block.ModBlocks;
import com.sourceofmystery.energy.MysteryEnergy;
import com.sourceofmystery.network.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraftforge.network.PacketDistributor;
import java.util.*;

public final class OfferingMenu extends AbstractContainerMenu {
    public static final long ENERGY_COST=1000;
    public static final int ROLL_TICKS=110,STOP_INDEX=36;
    private final Player owner;
    private final BlockPos anchor;
    private boolean suppress,settled;
    private long finishAt,token,paid,prizeValue;
    private ItemStack prize=ItemStack.EMPTY;
    private final ContainerData data=new SimpleContainerData(14);
    private final SimpleContainer input=new SimpleContainer(1) {
        @Override public void setChanged() {
            super.setChanged();
            if(!suppress && owner instanceof ServerPlayer p) {
                OfferingEscrow.input(p,getItem(0));data.set(0,0);data.set(9,0);updateQuote();
            }
        }
    };
    public OfferingMenu(int id,Inventory inventory,FriendlyByteBuf buf) {this(id,inventory,buf.readBoolean()?buf.readBlockPos():null);}
    public OfferingMenu(int id,Inventory inventory,BlockPos anchor) {
        super(OfferingMenus.LOTTERY.get(),id);owner=inventory.player;this.anchor=anchor;
        addSlot(new Slot(input,0,151,132) {
            @Override public boolean mayPlace(ItemStack s) {return !rolling();}
            @Override public boolean mayPickup(Player p) {return !rolling();}
        });
        for(int y=0;y<3;y++)for(int x=0;x<9;x++)addSlot(new Slot(inventory,x+y*9+9,79+x*18,214+y*18));
        for(int x=0;x<9;x++)addSlot(new Slot(inventory,x,79+x*18,272));
        addDataSlots(data);if(owner instanceof ServerPlayer)updateQuote();
    }
    public boolean rolling() {return data.get(0)==1;}
    public int state() {return data.get(0);}
    public int error() {return data.get(9);}
    public int remaining() {return data.get(8);}
    public long quotedValue() {long value=0;for(int i=0;i<4;i++)value|=(long)(data.get(1+i)&65535)<<(16*i);return value;}
    public int poolSize() {return (data.get(5)&65535)|((data.get(6)&65535)<<16);}
    public int quality() {return data.get(7);}
    public float luckMultiplier() {return Float.intBitsToFloat((data.get(10)&65535)|((data.get(11)&65535)<<16));}
    public float goodMultiplier() {return Float.intBitsToFloat((data.get(12)&65535)|((data.get(13)&65535)<<16));}
    private void syncLuck() {
        double luck=owner.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.LUCK);
        int a=Float.floatToIntBits((float)OfferingValues.luckFactor(luck)),b=Float.floatToIntBits(OfferingLotteryConfig.GOOD_CHANCE_MULTIPLIER.get().floatValue());
        data.set(10,a&65535);data.set(11,a>>>16);data.set(12,b&65535);data.set(13,b>>>16);
    }
    private void updateQuote() {
        if(!(owner instanceof ServerPlayer p))return;
        var values=OfferingValues.get(p.server);long value=values.stack(input.getItem(0));
        syncLuck();
        for(int i=0;i<4;i++)data.set(i+1,(int)(value>>>(16*i))&65535);
        int size=values.entries().size();data.set(5,size&65535);data.set(6,size>>>16);data.set(7,(int)(OfferingValues.quality(value)*1000));
    }
    @Override public boolean stillValid(Player player) {
        return player==owner && player.isAlive() && (anchor==null || player.level().getBlockState(anchor).is(ModBlocks.DARK_DIMENSIONAL_OFFERING.get()) && player.distanceToSqr(anchor.getX()+.5,anchor.getY()+.5,anchor.getZ()+.5)<=64);
    }
    @Override public boolean clickMenuButton(Player player,int button) {
        if(button!=0 || !(player instanceof ServerPlayer p) || p!=owner || player.isSpectator() || !stillValid(player) || rolling() || p.containerMenu!=this)return false;
        var stack=input.getItem(0);
        if(stack.isEmpty()){data.set(9,1);return false;}
        if(MysteryEnergy.getEnergy(p)<ENERGY_COST){data.set(9,2);return false;}
        var values=OfferingValues.get(p.server);
        if(values.entries().isEmpty()){data.set(9,3);return false;}
        paid=values.stack(stack);syncLuck();var distribution=values.distribution(paid,p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.LUCK));
        // Choose first, then commit all payment/reward mutations on this server thread.
        prize=OfferingValues.prize(distribution.sample(p.getRandom()));prizeValue=values.stack(prize);
        if(!MysteryEnergy.consumeEnergy(p,ENERGY_COST))return false;
        OfferingEscrow.input(p,ItemStack.EMPTY);OfferingEscrow.commit(p,prize);
        suppress=true;input.setItem(0,ItemStack.EMPTY);suppress=false;
        settled=false;token=p.getRandom().nextLong();finishAt=p.level().getGameTime()+ROLL_TICKS;
        data.set(0,1);data.set(8,ROLL_TICKS);data.set(9,0);
        var reel=new ArrayList<OfferingRollPacket.Cell>();
        for(int i=0;i<44;i++) {
            var e=distribution.sample(p.getRandom());
            reel.add(new OfferingRollPacket.Cell(Item.getId(e.item()),OfferingValues.tier(e.value())));
        }
        reel.set(STOP_INDEX,new OfferingRollPacket.Cell(Item.getId(prize.getItem()),OfferingValues.tier(prizeValue)));
        send(new OfferingRollPacket(containerId,token,false,paid,prizeValue,prize.copy(),reel));broadcastChanges();return true;
    }
    private void send(OfferingRollPacket packet) {if(owner instanceof ServerPlayer p)ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->p),packet);}
    private void settle() {
        if(settled || !rolling() || !(owner instanceof ServerPlayer p))return;
        settled=true;OfferingEscrow.recover(p);data.set(0,2);data.set(8,0);
        send(new OfferingRollPacket(containerId,token,true,paid,prizeValue,prize.copy(),List.of()));
    }
    @Override public void broadcastChanges() {
        if(owner instanceof ServerPlayer && !rolling())syncLuck();
        if(owner instanceof ServerPlayer && rolling()) {
            data.set(8,(int)Math.max(0,finishAt-owner.level().getGameTime()));if(data.get(8)==0)settle();
        }
        super.broadcastChanges();
    }
    @Override public void removed(Player player) {
        super.removed(player);
        if(player instanceof ServerPlayer p) {
            settle();suppress=true;input.clearContent();suppress=false;OfferingEscrow.recover(p);
        }
    }
    @Override public ItemStack quickMoveStack(Player player,int index) {
        if(rolling() || index<0 || index>=slots.size())return ItemStack.EMPTY;
        var slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;
        var stack=slot.getItem();var copy=stack.copy();
        if(index==0 ? !moveItemStackTo(stack,1,37,true) : !moveItemStackTo(stack,0,1,false))return ItemStack.EMPTY;
        if(stack.isEmpty())slot.set(ItemStack.EMPTY);else slot.setChanged();
        slot.onTake(player,stack);return copy;
    }
}
