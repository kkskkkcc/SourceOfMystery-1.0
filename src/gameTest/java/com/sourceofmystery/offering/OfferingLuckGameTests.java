package com.sourceofmystery.offering;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.*;
import java.util.UUID;

@GameTestHolder("sourceofmystery")
@PrefixGameTestTemplate(false)
public final class OfferingLuckGameTests {
    @GameTest(template="outsider_test",timeoutTicks=40)
    public static void tenPercentBoostAndActualLuckEffect(GameTestHelper h) {
        var values=OfferingValues.get(h.getLevel().getServer());long paid=values.stack(new ItemStack(Items.DIRT));
        var baseline=new OfferingValues.Distribution(values.entries(),paid,1);
        var boosted=values.distribution(paid,0);double p0=baseline.goodProbability(paid),p1=boosted.goodProbability(paid);
        h.assertTrue(Math.abs(p1-Math.min(Math.max(p0,.9999),p0*1.10))<1e-8,"10% relative increase is not applied to aggregate good chance");
        var player=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"LotteryLuck"));
        player.addEffect(new MobEffectInstance(MobEffects.LUCK,200,0));double luck=player.getAttributeValue(Attributes.LUCK);
        h.assertTrue(luck>0,"Vanilla Luck did not affect the player attribute");
        var lucky=values.distribution(paid,luck);h.assertTrue(lucky.goodProbability(paid)>p1,"Luck did not increase good-reward probability");
        player.removeEffect(MobEffects.LUCK);player.addEffect(new MobEffectInstance(MobEffects.UNLUCK,200,0));
        var unlucky=values.distribution(paid,player.getAttributeValue(Attributes.LUCK));h.assertTrue(unlucky.goodProbability(paid)<p1,"Bad Luck did not reduce probability");
        for(double points:new double[]{-1000,0,1,10,1000}) {
            var dist=values.distribution(paid,points);
            for(int i=0;i<values.entries().size();i++)h.assertTrue(dist.probability(i)>0,"Luck removed a candidate from the pool");
        }
        com.sourceofmystery.SourceOfMystery.LOGGER.info("OFFERING_LUCK_OK baseline={} boosted={} luck1={} bad_luck={}",p0,p1,lucky.goodProbability(paid),unlucky.goodProbability(paid));h.succeed();
    }
}
