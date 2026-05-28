package com.createsentryarm.client;

import com.createsentryarm.compat.SentryCompat;
import com.createsentryarm.content.AttackArmBlockEntity;
import com.createsentryarm.content.BlazeFireControlBlockEntity;
import com.createsentryarm.network.CSANetwork;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(value = Dist.CLIENT)
public class SentryLinkHandler {
    private static final String FIRST_SELECTION_OUTLINE = "createsentryarm:first_link_selection";
    private static final String HOVER_TARGET_OUTLINE = "createsentryarm:hover_link_target";
    private static final String LINK_LINE_OUTLINE = "createsentryarm:link_preview_line";
    private static BlockPos firstSelectedPos;
    private static boolean firstIsArm;

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !event.getLevel().isClientSide) {
            return;
        }
        Player player = event.getEntity();
        ItemStack held = player.getMainHandItem();
        var itemId = ForgeRegistries.ITEMS.getKey(held.getItem());
        boolean looksLikeWrench = itemId != null && ("create".equals(itemId.getNamespace()) && "wrench".equals(itemId.getPath())
                || itemId.getPath().contains("wrench"));
        if (!looksLikeWrench && !held.getItem().getDescriptionId().contains("wrench")) {
            firstSelectedPos = null;
            return;
        }
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(pos);
        boolean isArm = be instanceof AttackArmBlockEntity || SentryCompat.isSentryArm(be);
        boolean isControl = be instanceof BlazeFireControlBlockEntity || SentryCompat.isSentryFireControl(be);
        if (!isArm && !isControl) {
            return;
        }
        if (firstSelectedPos == null) {
            firstSelectedPos = pos;
            firstIsArm = isArm;
            player.displayClientMessage(Component.literal(isArm ? "请选择火控方块" : "请选择攻击机械臂"), true);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        boolean validPair = (firstIsArm && isControl) || (!firstIsArm && isArm);
        if (validPair) {
            CSANetwork.CHANNEL.sendToServer(new CSANetwork.LinkFireControlPacket(firstSelectedPos, pos));
        }
        firstSelectedPos = null;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        Level level = mc.level;
        if (player == null || level == null) {
            firstSelectedPos = null;
            return;
        }

        ItemStack held = player.getMainHandItem();
        var itemId = ForgeRegistries.ITEMS.getKey(held.getItem());
        boolean looksLikeWrench = itemId != null && ("create".equals(itemId.getNamespace()) && "wrench".equals(itemId.getPath())
                || itemId.getPath().contains("wrench"));
        if (!looksLikeWrench && !held.getItem().getDescriptionId().contains("wrench")) {
            firstSelectedPos = null;
            return;
        }

        if (firstSelectedPos != null) {
            drawBox(level, firstSelectedPos, FIRST_SELECTION_OUTLINE, 0x6EDE74);

            HitResult hit = mc.hitResult;
            if (hit instanceof BlockHitResult blockHit) {
                BlockPos hovered = blockHit.getBlockPos();
                net.minecraft.world.level.block.entity.BlockEntity hoveredBe = level.getBlockEntity(hovered);
                boolean isArm = hoveredBe instanceof AttackArmBlockEntity || SentryCompat.isSentryArm(hoveredBe);
                boolean isControl = hoveredBe instanceof BlazeFireControlBlockEntity || SentryCompat.isSentryFireControl(hoveredBe);
                if (!hovered.equals(firstSelectedPos) && (isArm || isControl)) {
                    boolean validPair = (firstIsArm && isControl) || (!firstIsArm && isArm);
                    int color = validPair ? 0xFFCB74 : 0xFF7171;
                    drawBox(level, hovered, HOVER_TARGET_OUTLINE, color);
                    if (validPair) {
                        Vec3 start = Vec3.atCenterOf(firstSelectedPos);
                        Vec3 end = Vec3.atCenterOf(hovered);
                        Outliner.getInstance()
                                .showLine(LINK_LINE_OUTLINE, start, end)
                                .colored(color)
                                .lineWidth(1 / 16f);
                    }
                }
            }
        }
    }

    private static void drawBox(Level level, BlockPos pos, Object slot, int color) {
        BlockState state = level.getBlockState(pos);
        VoxelShape shape = state.getShape(level, pos);
        AABB bb = shape.isEmpty() ? new AABB(pos) : shape.bounds().move(pos);
        Outliner.getInstance()
                .showAABB(slot, bb)
                .colored(color)
                .lineWidth(1 / 16f);
    }
}
