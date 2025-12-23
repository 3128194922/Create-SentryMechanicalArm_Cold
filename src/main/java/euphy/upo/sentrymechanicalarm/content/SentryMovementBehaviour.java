package euphy.upo.sentrymechanicalarm.content;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.render.ContraptionMatrices;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

public class SentryMovementBehaviour implements MovementBehaviour {

    @Override
    public boolean isActive(MovementContext context) {
        return true;
    }

    @Override
    public void startMoving(MovementContext context) {
 
        VirtualSentryArmBlockEntity virtualBE = new VirtualSentryArmBlockEntity(
                null,
                BlockPos.ZERO,
                context.state
        );

        CompoundTag data = context.blockEntityData;
        if (data != null) {
 
            if (data.contains("SentryHeldItem")) {
                virtualBE.setHeldItem(ItemStack.of(data.getCompound("SentryHeldItem")));
            }

            if (data.contains("SentryAmmoBoxes")) {
                ContainerHelper.loadAllItems(data.getCompound("SentryAmmoBoxes"), virtualBE.attachedAmmoBoxes);
            }
            if (data.contains("Angles")) {
                CompoundTag angles = data.getCompound("Angles");
                virtualBE.baseAngle.setValue(angles.getFloat("Base"));
                virtualBE.lowerArmAngle.setValue(angles.getFloat("Lower"));
                virtualBE.upperArmAngle.setValue(angles.getFloat("Upper"));
                virtualBE.headAngle.setValue(angles.getFloat("Head"));
            }
        }
        if (!context.world.isClientSide) {
 
            context.data.putFloat("ShootDelay", 0f);
 
            context.data.putInt("ScanCooldown", 0);
 
            context.data.putInt("TargetId", -1);
        }
        context.temporaryData = virtualBE;
    }

    @Override
    public void tick(MovementContext context) {
        if (context.world.isClientSide && context.temporaryData instanceof VirtualSentryArmBlockEntity virtualBE) {
            virtualBE.baseAngle.tickChaser();
            virtualBE.lowerArmAngle.tickChaser();
            virtualBE.upperArmAngle.tickChaser();
            virtualBE.headAngle.tickChaser();
            tickTargeting(context, virtualBE);
        }
    }

    private void tickTargeting(MovementContext context, VirtualSentryArmBlockEntity virtualBE) {
        AbstractContraptionEntity contraptionEntity = context.contraption.entity;
        if (contraptionEntity == null) return;

        Vec3 localPosCenter = VecHelper.getCenterOf(context.localPos);
        Vec3 turretWorldPos = contraptionEntity.toGlobalVector(localPosCenter, 1.0f);

        double range = 20.0;
        AABB searchBox = new AABB(turretWorldPos, turretWorldPos).inflate(range);
        List<Monster> enemies = context.world.getEntitiesOfClass(Monster.class, searchBox);

        Entity target = null;
        double minDistSq = range * range;

        for (Monster enemy : enemies) {
            if (!enemy.isAlive()) continue;
            double distSq = enemy.distanceToSqr(turretWorldPos);
            if (distSq < minDistSq) {
                minDistSq = distSq;
                target = enemy;
            }
        }

 
        if (target != null) {
 
            Vec3 targetVecWorld = target.position().add(0, target.getEyeHeight() * 0.5, 0).subtract(turretWorldPos);

            Vec3 targetVecLocal = contraptionEntity.reverseRotation(targetVecWorld, 1.0f);


            double yawRad = Mth.atan2(targetVecLocal.x, targetVecLocal.z);
            float yawDeg = (float) Math.toDegrees(yawRad);

            virtualBE.baseAngle.chase(yawDeg + 180, 0.2f, LerpedFloat.Chaser.EXP);

            double horizontalDist = Math.sqrt(targetVecLocal.x * targetVecLocal.x + targetVecLocal.z * targetVecLocal.z);
            double pitchRad = Mth.atan2(targetVecLocal.y, horizontalDist);
            float pitchDeg = (float) Math.toDegrees(pitchRad);

            virtualBE.headAngle.chase(pitchDeg, 0.2f, LerpedFloat.Chaser.EXP);

            virtualBE.lowerArmAngle.chase(135f, 0.4f, LerpedFloat.Chaser.EXP); 
            virtualBE.upperArmAngle.chase(90f, 0.4f, LerpedFloat.Chaser.EXP); 

        } else {

            int timer = (int) (context.world.getGameTime() % 200);

            float scanAngle = Mth.sin(timer / 30f) * 45f;
            virtualBE.baseAngle.chase(scanAngle, 0.05f, LerpedFloat.Chaser.EXP);

            virtualBE.headAngle.chase(0f, 0.1f, LerpedFloat.Chaser.EXP);
            virtualBE.lowerArmAngle.chase(135f, 0.05f, LerpedFloat.Chaser.EXP); 
            virtualBE.upperArmAngle.chase(90f, 0.05f, LerpedFloat.Chaser.EXP); 
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void renderInContraption(MovementContext context, VirtualRenderWorld renderWorld,
                                    ContraptionMatrices matrices, MultiBufferSource buffer) {
        SentryArmRenderer.renderInContraption(context, renderWorld, matrices, buffer);
    }

    @Override
    public boolean disableBlockEntityRendering() {
        return true;
    }
}