package com.createsentryarm.client;

import com.createsentryarm.CreateSentryArmMod;
import com.createsentryarm.content.FireControlClipboardItem;
import com.createsentryarm.network.CSANetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = CreateSentryArmMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SentryMarkHandler {
    private static final double MARK_RANGE = 256.0;
    private static final long MARK_COOLDOWN_MS = 500;
    private static long lastMarkTime = 0;

    @SubscribeEvent
    public static void onMouseInput(InputEvent.MouseButton.Pre event) {
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !mc.options.keyAttack.matchesMouse(event.getButton())) {
            return;
        }
        boolean holdingSpyglass = player.getMainHandItem().getItem() == Items.SPYGLASS;
        boolean usingSpyglass = player.isUsingItem() && player.getUseItem().getItem() == Items.SPYGLASS;
        boolean offhandClipboard = player.getOffhandItem().getItem() instanceof FireControlClipboardItem;
        if (!holdingSpyglass || !usingSpyglass || !offhandClipboard) {
            return;
        }
        event.setCanceled(true);
        if (System.currentTimeMillis() - lastMarkTime < MARK_COOLDOWN_MS) {
            return;
        }

        Entity target = getLookedAtEntity(player, MARK_RANGE);
        if (target != null) {
            CSANetwork.CHANNEL.sendToServer(new CSANetwork.RecordTargetPacket(target.getId()));
            player.playSound(SoundEvents.UI_BUTTON_CLICK.get(), 0.6f, 1.5f);
            lastMarkTime = System.currentTimeMillis();
        }
    }

    private static Entity getLookedAtEntity(LocalPlayer player, double range) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 viewVec = player.getViewVector(1.0F);
        Vec3 traceEnd = eyePos.add(viewVec.scale(range));

        BlockHitResult blockHit = player.level().clip(new ClipContext(
                eyePos, traceEnd,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));

        double actualLimit = range;
        if (blockHit.getType() != HitResult.Type.MISS) {
            actualLimit = blockHit.getLocation().distanceTo(eyePos);
            traceEnd = blockHit.getLocation();
        }

        AABB searchBox = player.getBoundingBox().expandTowards(viewVec.scale(actualLimit)).inflate(1.0D, 1.0D, 1.0D);

        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                player,
                eyePos,
                traceEnd,
                searchBox,
                e -> !e.isSpectator() && e.isPickable() && e instanceof net.minecraft.world.entity.LivingEntity,
                actualLimit * actualLimit
        );

        return entityHit != null ? entityHit.getEntity() : null;
    }
}
