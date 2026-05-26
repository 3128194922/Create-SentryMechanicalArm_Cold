package com.example.createsentryarm.client;

import com.example.createsentryarm.content.AttackArmBlockEntity;
import com.example.createsentryarm.content.BlazeFireControlBlockEntity;
import com.example.createsentryarm.network.CSANetwork;
import com.simibubi.create.AllItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(value = Dist.CLIENT)
public class SentryLinkHandler {
    private static BlockPos firstSelectedPos;
    private static boolean firstIsArm;

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !event.getLevel().isClientSide) {
            return;
        }
        Player player = event.getEntity();
        ItemStack held = player.getMainHandItem();
        if (!held.is(AllItems.WRENCH.get()) && !held.getItem().getDescriptionId().contains("wrench")) {
            firstSelectedPos = null;
            return;
        }
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        boolean isArm = level.getBlockEntity(pos) instanceof AttackArmBlockEntity;
        boolean isControl = level.getBlockEntity(pos) instanceof BlazeFireControlBlockEntity;
        if (!isArm && !isControl) {
            return;
        }
        if (firstSelectedPos == null) {
            firstSelectedPos = pos;
            firstIsArm = isArm;
            player.displayClientMessage(Component.literal(isArm ? "请选择火控方块" : "请选择攻击机械臂"), true);
            event.setCanceled(true);
            return;
        }
        boolean validPair = (firstIsArm && isControl) || (!firstIsArm && isArm);
        if (validPair) {
            CSANetwork.CHANNEL.sendToServer(new CSANetwork.LinkFireControlPacket(firstSelectedPos, pos));
        }
        firstSelectedPos = null;
        event.setCanceled(true);
    }
}
