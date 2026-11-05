package com.sourceofmystery.enchanter;

import com.sourceofmystery.block.ModBlocks;
import com.sourceofmystery.energy.MysteryEnergy;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.List;

public final class EnchanterMenu extends AbstractContainerMenu {
    public static final int SELECT_BASE=1000,APPLY=0,REMOVE=1,CLEAR=2,LESS=3,MORE=4,MAXIMUM=5;
    public final List<Enchantment> enchantments=EnchanterRules.all();
    private final Player owner;
    private final BlockPos pos;
    private final ContainerData data=new SimpleContainerData(21);
    private Enchantment selected;
    private boolean suppress;
    private final SimpleContainer input=new SimpleContainer(1) {
        @Override public void setChanged() {
            super.setChanged();if(!suppress && owner instanceof ServerPlayer p){EnchanterEscrow.store(p,getItem(0));quote();}
        }
    };
    public EnchanterMenu(int id,Inventory inv,FriendlyByteBuf buf) {this(id,inv,buf.readBlockPos());}
    public EnchanterMenu(int id,Inventory inv,BlockPos pos) {
        super(EnchanterMenus.ENCHANTER.get(),id);owner=inv.player;this.pos=pos.immutable();
        addSlot(new Slot(input,0,54,68){@Override public int getMaxStackSize(){return 1;}});
        for(int y=0;y<3;y++)for(int x=0;x<9;x++)addSlot(new Slot(inv,x+y*9+9,18+x*18,221+y*18));
        for(int x=0;x<9;x++)addSlot(new Slot(inv,x,18+x*18,279));
        addDataSlots(data);data.set(2,1);
        if(owner instanceof ServerPlayer) {if(!enchantments.isEmpty())choose(enchantments.get(0));else quote();}
    }
    private void putLong(int start,long value) {for(int i=0;i<4;i++)data.set(start+i,(int)(value>>>(16*i))&65535);}
    private long getLong(int start) {long value=0;for(int i=0;i<4;i++)value|=(long)(data.get(start+i)&65535)<<(16*i);return value;}
    public Enchantment selected() {return owner.level().isClientSide?Enchantment.byId(((data.get(0)&65535)|((data.get(1)&65535)<<16))):selected;}
    public int targetLevel(){return data.get(2);}
    public int status(){return data.get(3);}
    public long enchantCost(){return getLong(4);}
    public long removeCost(){return getLong(8);}
    public long clearCost(){return getLong(12);}
    public int currentLevel(){return data.get(16);}
    public int feedbackSerial(){return data.get(17);}
    public int feedback(){return data.get(18);}
    private void choose(Enchantment e) {
        if(e==selected)return;selected=e;int id=net.minecraft.core.registries.BuiltInRegistries.ENCHANTMENT.getId(e);data.set(0,id&65535);data.set(1,id>>>16);
        data.set(2,Math.min(EnchanterRules.maximum(e),Math.max(EnchanterRules.minimum(e),EnchanterRules.level(input.getItem(0),e)+1)));quote();
    }
    private void quote() {
        var stack=input.getItem(0);data.set(3,EnchanterRules.canApply(stack,selected,targetLevel()));
        putLong(4,EnchanterRules.enchantCost(stack,selected,targetLevel()));putLong(8,EnchanterRules.removeCost(stack,selected));putLong(12,EnchanterRules.clearCost(stack));
        data.set(16,EnchanterRules.level(stack,selected));int size=enchantments.size();data.set(19,size&65535);data.set(20,size>>>16);
    }
    private void feedback(int code) {data.set(18,code);data.set(17,(data.get(17)+1)&65535);}
    @Override public boolean stillValid(Player p) {return p==owner&&p.isAlive()&&p.level().getBlockState(pos).is(ModBlocks.MYSTERY_ENCHANTER.get())&&p.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=64;}
    @Override public boolean clickMenuButton(Player player,int button) {
        if(!(player instanceof ServerPlayer p)||p!=owner||p.isSpectator()||p.containerMenu!=this||!stillValid(p))return false;
        if(button>=SELECT_BASE) {var e=Enchantment.byId(button-SELECT_BASE);if(e==null||!enchantments.contains(e))return false;choose(e);return true;}
        if(selected!=null && (button==LESS||button==MORE||button==MAXIMUM)) {
            data.set(2,button==MAXIMUM?EnchanterRules.maximum(selected):Math.max(EnchanterRules.minimum(selected),Math.min(EnchanterRules.maximum(selected),targetLevel()+(button==MORE?1:-1))));quote();return true;
        }
        quote();var source=input.getItem(0);if(source.isEmpty()||source.getCount()!=1){feedback(5);return false;}
        long cost;ItemStack result;
        if(button==APPLY) {
            if(status()!=EnchanterRules.READY){feedback(6);return false;}cost=enchantCost();result=EnchanterRules.apply(source,selected,targetLevel());
        } else if(button==REMOVE) {
            cost=removeCost();if(cost<=0){feedback(5);return false;}result=EnchanterRules.remove(source,selected);
        } else if(button==CLEAR) {
            cost=clearCost();if(cost<=0){feedback(5);return false;}result=EnchanterRules.clear(source);
        } else return false;
        // Build the exact result before charging; never accept a client-supplied price or NBT.
        if(cost<=0||!MysteryEnergy.consumeEnergy(p,cost)){feedback(4);return false;}
        input.setItem(0,result);feedback(button+1);
        p.level().playSound(null,pos,button==APPLY?SoundEvents.ENCHANTMENT_TABLE_USE:SoundEvents.GRINDSTONE_USE,SoundSource.BLOCKS,.7f,button==APPLY?1.1f:.85f);
        broadcastChanges();return true;
    }
    @Override public void broadcastChanges(){if(owner instanceof ServerPlayer)quote();super.broadcastChanges();}
    @Override public void removed(Player player){super.removed(player);if(player instanceof ServerPlayer p){suppress=true;input.clearContent();suppress=false;EnchanterEscrow.recover(p);}}
    @Override public ItemStack quickMoveStack(Player player,int index) {
        if(index<0||index>=slots.size())return ItemStack.EMPTY;var slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;
        var stack=slot.getItem();var copy=stack.copy();if(index==0?!moveItemStackTo(stack,1,37,true):!moveItemStackTo(stack,0,1,false))return ItemStack.EMPTY;
        if(stack.isEmpty())slot.set(ItemStack.EMPTY);else slot.setChanged();slot.onTake(player,stack);return copy;
    }
}
