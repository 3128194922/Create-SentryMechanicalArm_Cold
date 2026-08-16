package com.createsentryarm.content;

import com.createsentryarm.client.AttackArmRenderer;
import com.createsentryarm.compat.BiotechCompat;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.render.ContraptionMatrices;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.items.IItemHandler;

public class AttackArmMovementBehaviour implements MovementBehaviour {
    @Override
    public boolean isActive(MovementContext context) {
        return true;
    }

    @Override
    public boolean disableBlockEntityRendering() {
        return true;
    }

    @Override
    public void startMoving(MovementContext context) {
        VirtualAttackArmBlockEntity virtualBE = new VirtualAttackArmBlockEntity(context.localPos, context.state);
        virtualBE.setVirtualLevel(context.world);
        if (context.blockEntityData != null) {
            virtualBE.read(context.blockEntityData, false);
        }
        context.temporaryData = virtualBE;
        if (!context.data.contains("AttackCooldown")) {
            context.data.putInt("AttackCooldown", 0);
        }
        if (!context.data.contains("IdleScanTimer")) {
            context.data.putInt("IdleScanTimer", 20);
            if (context.blockEntityData != null) {
                context.data.putFloat("IdleTargetYaw", context.blockEntityData.getFloat("BaseAngle") - 180);
                context.data.putFloat("IdleTargetPitch", context.blockEntityData.getFloat("HeadAngle"));
            } else {
                context.data.putFloat("IdleTargetYaw", 0);
                context.data.putFloat("IdleTargetPitch", 0);
            }
        }
    }

    @Override
    public void tick(MovementContext context) {
        VirtualAttackArmBlockEntity virtualBE;
        if (context.temporaryData instanceof VirtualAttackArmBlockEntity loaded) {
            virtualBE = loaded;
        } else {
            startMoving(context);
            if (!(context.temporaryData instanceof VirtualAttackArmBlockEntity created)) {
                return;
            }
            virtualBE = created;
        }

        virtualBE.baseAngle.tickChaser();
        virtualBE.lowerArmAngle.tickChaser();
        virtualBE.upperArmAngle.tickChaser();
        virtualBE.headAngle.tickChaser();

        if (context.contraption.entity == null || virtualBE.getHeldItem().isEmpty() || context.motion.length() < 0.01) {
            idleScan(context, virtualBE);
            return;
        }

        AbstractContraptionEntity contraptionEntity = context.contraption.entity;
        Vec3 localCenter = VecHelper.getCenterOf(context.localPos);
        Vec3 globalCenter = contraptionEntity.toGlobalVector(localCenter, 1.0f);
        virtualBE.setVirtualPos(BlockPos.containing(globalCenter));
        virtualBE.setVirtualLevel(context.world);

        Vec3 muzzlePos = globalCenter.add(0, context.state.getValue(AttackArmBlock.CEILING) ? -1.1 : 1.45, 0);
        AttackArmBlockEntity.TargetFilter filter = FireControlMovementBehaviour.findFilter(context);
        double range = virtualBE.syncedAmmoRange > 0 ? virtualBE.syncedAmmoRange : 24.0;
        AmmoData ammoData = null;
        if (!context.world.isClientSide) {
            // 弹药数据包：射程上限 + 转向速度倍率
            IItemHandler storage = context.contraption.getStorage().getAllItems();
            ItemStack peeked = AttackArmBlockEntity.peekAmmo(storage, context.world, virtualBE.getHeldItem());
            ammoData = AmmoDataManager.get(context.world, virtualBE.getHeldItem(), peeked);
            range = ammoData.effectiveRange(virtualBE.isHighArc());
            virtualBE.aimTurnSpeed = (float) ammoData.turnSpeed();
        }
        LivingEntity target = AttackArmBlockEntity.findTarget(context.world, muzzlePos, range, filter, contraptionEntity);
        if (target == null) {
            if (!context.world.isClientSide) {
                virtualBE.syncedTargetId = -1;
            }
            idleScan(context, virtualBE);
            return;
        }

        Vec3 targetPos = AttackArmBlockEntity.getBestTargetPos(context.world, muzzlePos, target);
        if (targetPos == null) {
            idleScan(context, virtualBE);
            return;
        }

        float[] yawPitch = AttackArmBlockEntity.getYawPitch(muzzlePos, targetPos);
        float aimYaw = yawPitch[0];
        float aimPitch = yawPitch[1];

        // 头部对齐发射方向（与静态机械臂一致）：服务端解算发射角，
        // 经 blockEntityData 同步；客户端按同步值追踪
        Vec3 launchMotion = null;
        if (!context.world.isClientSide) {
            virtualBE.syncedTargetId = target.getId();
            launchMotion = AttackArmBlockEntity.solveAimMotion(muzzlePos, target, ammoData, virtualBE.isHighArc());
            if (launchMotion != null) {
                aimYaw = AttackArmBlockEntity.yawFromMotion(launchMotion);
                aimPitch = AttackArmBlockEntity.pitchFromMotion(launchMotion);
            }
            virtualBE.syncedLaunchYaw = aimYaw;
            virtualBE.syncedLaunchPitch = aimPitch;
        } else if (virtualBE.syncedTargetId != -1) {
            aimYaw = virtualBE.syncedLaunchYaw;
            aimPitch = virtualBE.syncedLaunchPitch;
        }
        virtualBE.aimAtAngle(aimYaw, aimPitch);

        if (!context.world.isClientSide) {
            int cooldown = context.data.getInt("AttackCooldown");
            if (cooldown > 0) {
                context.data.putInt("AttackCooldown", cooldown - 1);
                return;
            }
            // 炮塔式射击门控：机械臂转向到位后才开火
            if (!virtualBE.isRoughlyAimed(aimYaw, aimPitch)) {
                return;
            }
            // 当前发射方式无解：不发射、不耗弹
            if (launchMotion == null) {
                context.data.putInt("AttackCooldown", 10);
                return;
            }
            IItemHandler handler = context.contraption.getStorage().getAllItems();
            BiotechCompat.pushDamageContext(target, contraptionEntity);
            try {
                int nextCooldown = AttackArmBlockEntity.performAttack((net.minecraft.server.level.ServerLevel) context.world,
                        BlockPos.containing(globalCenter), muzzlePos, virtualBE.getHeldItem(), target, aimYaw, aimPitch, virtualBE.isHighArc(), handler);
                context.data.putInt("AttackCooldown", nextCooldown);
                virtualBE.kickRecoil();
            } finally {
                BiotechCompat.popDamageContext();
            }
        }
    }

    private void idleScan(MovementContext context, VirtualAttackArmBlockEntity virtualBE) {
        int timer = context.data.getInt("IdleScanTimer");
        if (timer <= 0) {
            float randomYaw = (context.world.random.nextFloat() - 0.5f) * 240f;
            float randomPitch = (context.world.random.nextFloat() * 30f) - 15f;
            context.data.putFloat("IdleTargetYaw", randomYaw);
            context.data.putFloat("IdleTargetPitch", randomPitch);
            context.data.putInt("IdleScanTimer", 80 + context.world.random.nextInt(60));
        } else {
            context.data.putInt("IdleScanTimer", timer - 1);
        }
        float[] yawPitch = new float[]{context.data.getFloat("IdleTargetYaw"), context.data.getFloat("IdleTargetPitch")};
        virtualBE.aimAtAngle(yawPitch[0], yawPitch[1]);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void renderInContraption(MovementContext context, VirtualRenderWorld renderWorld,
                                    ContraptionMatrices matrices, MultiBufferSource buffer) {
        AttackArmRenderer.renderInContraption(context, renderWorld, matrices, buffer);
    }

    @Override
    public void stopMoving(MovementContext context) {
        if (context.temporaryData instanceof VirtualAttackArmBlockEntity virtualBE) {
            virtualBE.write(context.blockEntityData, false);
        }
    }

    @Override
    public void writeExtraData(MovementContext context) {
        if (context.temporaryData instanceof VirtualAttackArmBlockEntity virtualBE) {
            virtualBE.write(context.blockEntityData, false);
        }
    }
}
