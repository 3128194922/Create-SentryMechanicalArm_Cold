package com.createsentryarm.content;

import com.createsentryarm.compat.SentryCompat;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.render.ContraptionMatrices;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerRenderer;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.items.ItemStackHandler;
import org.apache.commons.lang3.tuple.MutablePair;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class FireControlMovementBehaviour implements MovementBehaviour {
    public static Supplier<Entity> CLIENT_PLAYER = () -> null;
    public static class FireControlData {
        public boolean whitelist;
        public ItemStack displayItem = ItemStack.EMPTY;
        public final List<String> targets = new ArrayList<>();
        public final LerpedFloat headAngle;

        public FireControlData(float initialAngle) {
            this.headAngle = LerpedFloat.angular().startWithValue(initialAngle);
        }
    }

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
        getOrInitData(context);
    }

    @Override
    public void tick(MovementContext context) {
        FireControlData data = getOrInitData(context);
        refresh(context, data);
        if (context.world.isClientSide) {
            data.headAngle.tickChaser();
            float target = getTargetAngle(context);
            data.headAngle.chase(target, 0.5f, LerpedFloat.Chaser.exp(5));
        }
    }

    public static FireControlData getOrInitData(MovementContext context) {
        if (!(context.temporaryData instanceof FireControlData)) {
            FireControlData newData = new FireControlData(0f);
            context.temporaryData = newData;
            return newData;
        }
        return (FireControlData) context.temporaryData;
    }

    private void refresh(MovementContext context, FireControlData data) {
        if (context.blockEntityData != null && context.blockEntityData.contains("Inventory")) {
            ItemStackHandler inventory = new ItemStackHandler(1);
            inventory.deserializeNBT(context.blockEntityData.getCompound("Inventory"));
            ItemStack stack = inventory.getStackInSlot(0);
            data.displayItem = stack;
            CompoundTag tag = stack.getTag();
            if (tag != null) {
                data.whitelist = tag.getBoolean("WhitelistMode");
                data.targets.clear();
                if (tag.contains("TargetList", Tag.TAG_LIST)) {
                    ListTag list = tag.getList("TargetList", Tag.TAG_STRING);
                    for (Tag entry : list) {
                        data.targets.add(entry.getAsString());
                    }
                }
            } else {
                data.displayItem = ItemStack.EMPTY;
                data.whitelist = false;
                data.targets.clear();
            }
        }
    }

    private static float getTargetAngle(MovementContext context) {
        var player = CLIENT_PLAYER.get();
        if (player != null && !player.isInvisible()
                && context.contraption != null && context.contraption.entity != null) {
            Vec3 worldPos = context.contraption.entity.toGlobalVector(
                    VecHelper.getCenterOf(context.localPos), 0);
            double dx = player.getX() - worldPos.x;
            double dz = player.getZ() - worldPos.z;
            return AngleHelper.deg(-Mth.atan2(dz, dx)) - 90;
        }
        return 0f;
    }

    public static AttackArmBlockEntity.TargetFilter findFilter(MovementContext context) {
        if (context.contraption == null) {
            return AttackArmBlockEntity.TargetFilter.DEFAULT_ALL;
        }
        for (MutablePair<?, MovementContext> pair : context.contraption.getActors()) {
            MovementContext other = pair.getRight();
            if (other.temporaryData instanceof FireControlData data && !data.displayItem.isEmpty()) {
                return new AttackArmBlockEntity.TargetFilter(true, data.whitelist, new ArrayList<>(data.targets));
            }
            SentryCompat.SentryFilterData sentryData = SentryCompat.readFromBlockEntityData(other.blockEntityData);
            if (sentryData != null) {
                return new AttackArmBlockEntity.TargetFilter(true, sentryData.whitelist(), new ArrayList<>(sentryData.targets()));
            }
        }
        return AttackArmBlockEntity.TargetFilter.DEFAULT_ALL;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void renderInContraption(MovementContext context, VirtualRenderWorld renderWorld,
                                    ContraptionMatrices matrices, MultiBufferSource buffer) {
        FireControlData data = getOrInitData(context);
        float horizontalAngle = AngleHelper.rad(data.headAngle.getValue(
                AnimationTickHolder.getPartialTicks(context.world)));
        boolean hasClipboard = !data.displayItem.isEmpty();

        BlazeBurnerRenderer.renderShared(
                matrices.getViewProjection(),
                matrices.getModel(),
                buffer,
                context.world,
                context.state,
                BlazeBurnerBlock.HeatLevel.KINDLED,
                0,
                horizontalAngle,
                false,
                false,
                hasClipboard ? AllPartialModels.LOGISTICS_HAT : null,
                context.localPos.hashCode()
        );
    }
}
