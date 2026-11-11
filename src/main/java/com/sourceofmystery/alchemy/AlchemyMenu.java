package com.sourceofmystery.alchemy;

import com.sourceofmystery.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Quotes are read-only menu slots. Only an authenticated revisioned purchase changes inventory. */
public final class AlchemyMenu extends AbstractContainerMenu {
    public static final int DISPLAY=AlchemyShop.COUNT*(1+AlchemyShop.MAX_MATERIALS);
    private final Player owner;
    private final BlockPos pos;
    private final InteractionHand hand;
    private final SimpleContainer quotes=new SimpleContainer(DISPLAY);
    private final ContainerData data=new SimpleContainerData(14);
    private UUID serverRevision;
    private List<AlchemyShop.Trade> serverTrades=List.of();
    private List<ItemStack> feedbackInventory=List.of();
    public AlchemyMenu(int id,Inventory inventory,FriendlyByteBuf buf){this(id,inventory,buf.readBoolean()?buf.readBlockPos():null,InteractionHand.values()[buf.readUnsignedByte()&1]);}
    public AlchemyMenu(int id,Inventory inventory,BlockPos pos,InteractionHand hand){
        super(AlchemyMenus.TABLE.get(),id);owner=inventory.player;this.pos=pos==null?null:pos.immutable();this.hand=hand;
        for(int i=0;i<DISPLAY;i++)addSlot(new Slot(quotes,i,-10000,-10000){
            @Override public boolean mayPlace(ItemStack stack){return false;}
            @Override public boolean mayPickup(Player player){return false;}
            @Override public boolean isActive(){return false;}
        });
        for(int y=0;y<3;y++)for(int x=0;x<9;x++)addSlot(new Slot(inventory,9+x+y*9,18+x*18,259+y*18));
        for(int x=0;x<9;x++)addSlot(new Slot(inventory,x,18+x*18,317));
        addDataSlots(data);if(owner instanceof ServerPlayer p)refresh(AlchemyShop.get(p.server));
    }
    private void putLong(int offset,long value){for(int i=0;i<4;i++)data.set(offset+i,(int)(value>>>(16*i))&65535);}
    private long getLong(int offset){long result=0;for(int i=0;i<4;i++)result|=(long)(data.get(offset+i)&65535)<<(16*i);return result;}
    public UUID revision(){return new UUID(getLong(0),getLong(4));}
    public long day(){return getLong(8);}
    public int feedback(){return data.get(12);}
    public int serial(){return data.get(13);}
    public ItemStack output(int index){return index>=0&&index<AlchemyShop.COUNT?quotes.getItem(index):ItemStack.EMPTY;}
    public List<ItemStack> materials(int index){var list=new ArrayList<ItemStack>();if(index<0||index>=AlchemyShop.COUNT)return list;
        for(int j=0;j<AlchemyShop.MAX_MATERIALS;j++){var cost=quotes.getItem(AlchemyShop.COUNT+index*AlchemyShop.MAX_MATERIALS+j);if(!cost.isEmpty())list.add(cost);}return list;}
    private void refresh(AlchemyShop shop){
        if(shop.revision().equals(serverRevision))return;
        serverRevision=shop.revision();serverTrades=shop.trades();quotes.clearContent();
        for(int i=0;i<serverTrades.size();i++){
            var trade=serverTrades.get(i);quotes.setItem(i,trade.output().copy());
            for(int j=0;j<trade.materials().size();j++)quotes.setItem(AlchemyShop.COUNT+i*AlchemyShop.MAX_MATERIALS+j,trade.materials().get(j).copy());
        }
        putLong(0,serverRevision.getMostSignificantBits());putLong(4,serverRevision.getLeastSignificantBits());putLong(8,shop.day());
    }
    private void feedback(int code){
        data.set(12,code);data.set(13,(data.get(13)+1)&65535);
        feedbackInventory=(code==1||code==2)?owner.getInventory().items.stream().map(ItemStack::copy).toList():List.of();
    }
    public static int available(Inventory inventory,ItemStack cost){int count=0;for(var stack:inventory.items)if(ItemStack.isSameItemSameTags(stack,cost))count+=stack.getCount();return count;}
    public boolean affordable(int index){var costs=materials(index);return !costs.isEmpty()&&costs.stream().allMatch(c->available(owner.getInventory(),c)>=c.getCount());}
    public boolean buy(ServerPlayer player,int index,UUID revision){
        if(player!=owner||player.containerMenu!=this||player.isSpectator()||!stillValid(player)||index<0||index>=AlchemyShop.COUNT)return false;
        var shop=AlchemyShop.get(player.server);
        if(!shop.revision().equals(revision)||!shop.revision().equals(serverRevision)){refresh(shop);feedback(3);broadcastChanges();return false;}
        if(index>=serverTrades.size())return false;var trade=serverTrades.get(index);
        var plan=new ArrayList<ItemStack>();for(var stack:player.getInventory().items)plan.add(stack.copy());
        for(var cost:trade.materials()){
            int left=cost.getCount();
            for(var stack:plan)if(ItemStack.isSameItemSameTags(stack,cost)){int take=Math.min(left,stack.getCount());stack.shrink(take);left-=take;if(left==0)break;}
            if(left>0){feedback(1);broadcastChanges();return false;}
        }
        var result=trade.output().copy();
        for(var stack:plan)if(!stack.isEmpty()&&ItemStack.isSameItemSameTags(stack,result)){
            int move=Math.min(result.getCount(),Math.max(0,Math.min(64,stack.getMaxStackSize())-stack.getCount()));stack.grow(move);result.shrink(move);
        }
        for(int i=0;i<plan.size()&&!result.isEmpty();i++)if(plan.get(i).isEmpty()){int amount=Math.min(result.getCount(),Math.min(64,result.getMaxStackSize()));var give=result.copy();give.setCount(amount);plan.set(i,give);result.shrink(amount);}
        if(!result.isEmpty()){feedback(2);broadcastChanges();return false;}
        // Material removal and delivery commit together on the server thread, with no partial charge.
        for(int i=0;i<plan.size();i++)if(!ItemStack.matches(player.getInventory().getItem(i),plan.get(i)))player.getInventory().setItem(i,plan.get(i));
        player.getInventory().setChanged();feedback(4);player.inventoryMenu.broadcastChanges();broadcastChanges();
        player.level().playSound(null,player.blockPosition(),SoundEvents.AMETHYST_BLOCK_CHIME,SoundSource.PLAYERS,.55f,1.2f);return true;
    }
    @Override public boolean stillValid(Player player){
        return player==owner&&player.isAlive()&&(pos==null?player.getItemInHand(hand).is(ModBlocks.ALCHEMIST_TABLE.get().asItem())
                :player.level().getBlockState(pos).is(ModBlocks.ALCHEMIST_TABLE.get())&&player.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=64);
    }
    @Override public void broadcastChanges(){
        if(owner instanceof ServerPlayer p){
            refresh(AlchemyShop.get(p.server));
            if(!feedbackInventory.isEmpty()){
                for(int i=0;i<feedbackInventory.size();i++)if(!ItemStack.matches(feedbackInventory.get(i),owner.getInventory().getItem(i))){feedback(0);break;}
            }
        }
        super.broadcastChanges();
    }
    @Override public void clicked(int slot,int button,ClickType type,Player player){if(slot>=0&&slot<DISPLAY)return;super.clicked(slot,button,type,player);}
    @Override public boolean canTakeItemForPickAll(ItemStack stack,Slot slot){return slot.index>=DISPLAY&&super.canTakeItemForPickAll(stack,slot);}
    @Override public ItemStack quickMoveStack(Player player,int index){
        if(index<DISPLAY||index>=slots.size())return ItemStack.EMPTY;var slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;
        var stack=slot.getItem();var copy=stack.copy();boolean moved=index<DISPLAY+27?moveItemStackTo(stack,DISPLAY+27,DISPLAY+36,false):moveItemStackTo(stack,DISPLAY,DISPLAY+27,false);
        if(!moved)return ItemStack.EMPTY;if(stack.isEmpty())slot.set(ItemStack.EMPTY);else slot.setChanged();slot.onTake(player,stack);return copy;
    }
}
