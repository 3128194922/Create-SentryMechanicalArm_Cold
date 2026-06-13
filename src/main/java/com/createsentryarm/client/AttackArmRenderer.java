package com.createsentryarm.client;

import com.createsentryarm.content.AttackArmBlock;
import com.createsentryarm.content.AttackArmBlockEntity;
import com.createsentryarm.content.VirtualAttackArmBlockEntity;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.render.ContraptionMatrices;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;
import dev.engine_room.flywheel.lib.transform.PoseTransformStack;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

public class AttackArmRenderer extends KineticBlockEntityRenderer<AttackArmBlockEntity> {
    public AttackArmRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRenderOffScreen(AttackArmBlockEntity be) {
        return true;
    }

    @Override
    protected void renderSafe(AttackArmBlockEntity be, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int light, int overlay) {
        VertexConsumer rotatingBuilder = buffer.getBuffer(RenderType.solid());
        KineticBlockEntityRenderer.renderRotatingBuffer(
                be,
                CachedBuffers.partial(AllPartialModels.ARM_COG, be.getBlockState()),
                poseStack,
                rotatingBuilder,
                light
        );

        ItemStack item = be.getHeldItem();
        boolean hasItem = !item.isEmpty();
        boolean isBlockItem = false;
        if (hasItem) {
            ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
            BakedModel bakedModel = itemRenderer.getModel(item, be.getLevel(), (LivingEntity) null, 0);
            isBlockItem = item.getItem() instanceof BlockItem && bakedModel.isGui3d();
        }

        VertexConsumer builder = buffer.getBuffer(RenderType.solid());
        BlockState blockState = be.getBlockState();
        PoseStack localStack = new PoseStack();
        PoseTransformStack transform = TransformStack.of(localStack);

        float baseAngle = be.baseAngle.getValue(partialTicks);
        float lowerArmAngle = be.lowerArmAngle.getValue(partialTicks) - 135.0F;
        float upperArmAngle = be.upperArmAngle.getValue(partialTicks) - 90.0F;
        float headAngle = be.headAngle.getValue(partialTicks);
        boolean inverted = blockState.getValue(AttackArmBlock.CEILING);

        transform.center();
        if (inverted) {
            transform.rotateXDegrees(180);
        }
        renderArm(builder, poseStack, localStack, transform, blockState, baseAngle, lowerArmAngle, upperArmAngle, headAngle, inverted, hasItem, isBlockItem, light);

        if (!hasItem) {
            return;
        }

        poseStack.pushPose();
        float itemScale = isBlockItem ? .5f : .625f;
        transform.rotateXDegrees(90);
        localStack.translate(0, isBlockItem ? -9 / 16f : -10 / 16f, 0);
        localStack.scale(itemScale, itemScale, itemScale);
        poseStack.last().pose().mul(localStack.last().pose());
        Minecraft.getInstance().getItemRenderer().renderStatic(item, ItemDisplayContext.FIXED, light, overlay, poseStack, buffer, be.getLevel(), 0);
        poseStack.popPose();
    }

    private void renderArm(VertexConsumer builder, PoseStack poseStack, PoseStack localStack, TransformStack transform,
                           BlockState state, float baseAngle, float lowerArmAngle, float upperArmAngle, float headAngle,
                           boolean inverted, boolean hasItem, boolean isBlockItem, int light) {
        SuperByteBuffer base = CachedBuffers.partial(AllPartialModels.ARM_BASE, state).light(light);
        SuperByteBuffer lowerBody = CachedBuffers.partial(AllPartialModels.ARM_LOWER_BODY, state).light(light);
        SuperByteBuffer upperBody = CachedBuffers.partial(AllPartialModels.ARM_UPPER_BODY, state).light(light);
        SuperByteBuffer claw = CachedBuffers.partial(AllPartialModels.ARM_CLAW_BASE, state).light(light);
        SuperByteBuffer upperClawGrip = CachedBuffers.partial(AllPartialModels.ARM_CLAW_GRIP_UPPER, state).light(light);
        SuperByteBuffer lowerClawGrip = CachedBuffers.partial(AllPartialModels.ARM_CLAW_GRIP_LOWER, state).light(light);

        transformBase(transform, baseAngle);
        base.transform(localStack).renderInto(poseStack, builder);

        transformLowerArm(transform, lowerArmAngle);
        lowerBody.transform(localStack).renderInto(poseStack, builder);

        transformUpperArm(transform, upperArmAngle);
        upperBody.transform(localStack).renderInto(poseStack, builder);

        transformHead(transform, headAngle);
        if (inverted) {
            transform.rotateZDegrees(180);
        }
        claw.transform(localStack).renderInto(poseStack, builder);
        if (inverted) {
            transform.rotateZDegrees(180);
        }

        for (int flip : Iterate.positiveAndNegative) {
            localStack.pushPose();
            transformClawHalf(transform, hasItem, isBlockItem, flip);
            (flip > 0 ? lowerClawGrip : upperClawGrip).transform(localStack).renderInto(poseStack, builder);
            localStack.popPose();
        }
    }

    private static void transformBase(TransformStack msr, float baseAngle) {
        msr.translate(0.0F, 0.25F, 0.0F);
        msr.rotateYDegrees(baseAngle);
    }

    private static void transformLowerArm(TransformStack msr, float lowerArmAngle) {
        msr.translate(0.0F, 0.125F, 0.0F);
        msr.rotateXDegrees(lowerArmAngle + 135.0F);
    }

    private static void transformUpperArm(TransformStack msr, float upperArmAngle) {
        msr.translate(0.0F, 0.0F, -0.875F);
        msr.rotateXDegrees(upperArmAngle - 90.0F);
    }

    private static void transformHead(TransformStack msr, float headAngle) {
        msr.translate(0.0F, 0.0F, -0.9375F);
        msr.rotateXDegrees(headAngle - 45.0F);
    }

    private static void transformClawHalf(TransformStack msr, boolean hasItem, boolean isBlockItem, int flip) {
        msr.translate(0.0F, -flip * (hasItem ? (isBlockItem ? 0.1875F : 0.078125F) : 0.0625F), -0.375F);
    }

    @Override
    protected SuperByteBuffer getRotatedModel(AttackArmBlockEntity be, BlockState state) {
        return CachedBuffers.partial(AllPartialModels.ARM_COG, state);
    }

    public static void renderInContraption(MovementContext context, VirtualRenderWorld renderWorld, ContraptionMatrices matrices, MultiBufferSource buffer) {
        if (context.temporaryData == null || !(context.temporaryData instanceof VirtualAttackArmBlockEntity)) {
            VirtualAttackArmBlockEntity newBE = new VirtualAttackArmBlockEntity(context.localPos, context.state);
            if (context.blockEntityData != null) {
                if (context.blockEntityData.contains("HeldItem")) {
                    newBE.setHeldItem(ItemStack.of(context.blockEntityData.getCompound("HeldItem")));
                }
                if (context.blockEntityData.contains("BaseAngle")) {
                    newBE.baseAngle.setValue(context.blockEntityData.getFloat("BaseAngle"));
                }
                if (context.blockEntityData.contains("LowerAngle")) {
                    newBE.lowerArmAngle.setValue(context.blockEntityData.getFloat("LowerAngle"));
                }
                if (context.blockEntityData.contains("UpperAngle")) {
                    newBE.upperArmAngle.setValue(context.blockEntityData.getFloat("UpperAngle"));
                }
                if (context.blockEntityData.contains("HeadAngle")) {
                    newBE.headAngle.setValue(context.blockEntityData.getFloat("HeadAngle"));
                }
                if (context.blockEntityData.contains("Speed")) {
                    newBE.setSpeed(context.blockEntityData.getFloat("Speed"));
                }
            }
            context.temporaryData = newBE;
        }
        VirtualAttackArmBlockEntity virtualBE = (VirtualAttackArmBlockEntity) context.temporaryData;
        BlockState blockState = context.state;
        float partialTicks = net.createmod.catnip.animation.AnimationTickHolder.getPartialTicks();
        float baseAngle = virtualBE.baseAngle.getValue(partialTicks);
        float lowerArmAngle = virtualBE.lowerArmAngle.getValue(partialTicks) - 135.0F;
        float upperArmAngle = virtualBE.upperArmAngle.getValue(partialTicks) - 90.0F;
        float headAngle = virtualBE.headAngle.getValue(partialTicks);
        boolean inverted = blockState.getValue(AttackArmBlock.CEILING);
        int light = LightTexture.FULL_BRIGHT;
        if (context.contraption.entity != null) {
            var localPos = net.createmod.catnip.math.VecHelper.getCenterOf(context.localPos);
            var globalPos = context.contraption.entity.toGlobalVector(localPos, partialTicks);
            light = LevelRenderer.getLightColor(context.world, BlockPos.containing(globalPos));
        }

        ItemStack heldItem = virtualBE.getHeldItem();
        boolean hasItem = !heldItem.isEmpty();
        boolean isBlockItem = hasItem && Minecraft.getInstance().getItemRenderer().getModel(heldItem, renderWorld, null, 0).isGui3d();

        VertexConsumer builder = buffer.getBuffer(RenderType.solid());
        PoseStack ms = matrices.getModel();
        PoseTransformStack msr = TransformStack.of(ms);
        ms.pushPose();
        msr.center();
        if (inverted) {
            msr.rotateXDegrees(180);
        }

        ms.pushPose();
        transformBase(msr, baseAngle);
        CachedBuffers.partial(AllPartialModels.ARM_BASE, blockState)
                .light(light)
                .transform(ms)
                .renderInto(matrices.getViewProjection(), builder);
        ms.popPose();

        ms.pushPose();
        transformBase(msr, baseAngle);
        transformLowerArm(msr, lowerArmAngle);
        CachedBuffers.partial(AllPartialModels.ARM_LOWER_BODY, blockState)
                .light(light)
                .transform(ms)
                .renderInto(matrices.getViewProjection(), builder);

        transformUpperArm(msr, upperArmAngle);
        CachedBuffers.partial(AllPartialModels.ARM_UPPER_BODY, blockState)
                .light(light)
                .transform(ms)
                .renderInto(matrices.getViewProjection(), builder);

        transformHead(msr, headAngle);
        if (inverted) msr.rotateZDegrees(180);
        CachedBuffers.partial(AllPartialModels.ARM_CLAW_BASE, blockState)
                .light(light)
                .transform(ms)
                .renderInto(matrices.getViewProjection(), builder);

        for (int flip : Iterate.positiveAndNegative) {
            ms.pushPose();
            transformClawHalf(msr, hasItem, isBlockItem, flip);
            SuperByteBuffer grip = CachedBuffers.partial(
                    flip > 0 ? AllPartialModels.ARM_CLAW_GRIP_LOWER : AllPartialModels.ARM_CLAW_GRIP_UPPER,
                    blockState);
            grip.light(light)
                    .transform(ms)
                    .renderInto(matrices.getViewProjection(), builder);
            ms.popPose();
        }
        ms.popPose();

        ms.pushPose();
        msr.uncenter();
        msr.center();
        float speed = virtualBE.getSpeed();
        float time = net.createmod.catnip.animation.AnimationTickHolder.getRenderTime();
        float cogAngle = (time * speed * 3f / 10f) % 360;
        ms.mulPose(com.mojang.math.Axis.YP.rotationDegrees(cogAngle));
        msr.uncenter();
        CachedBuffers.partial(AllPartialModels.ARM_COG, blockState)
                .light(light)
                .transform(ms)
                .renderInto(matrices.getViewProjection(), builder);
        ms.popPose();

        if (hasItem) {
            ms.pushPose();
            float itemScale = isBlockItem ? .5f : .625f;
            msr.rotateXDegrees(90);
            ms.translate(0, isBlockItem ? -9 / 16f : -10 / 16f, 0);
            ms.scale(itemScale, itemScale, itemScale);
            Minecraft.getInstance().getItemRenderer().renderStatic(heldItem, ItemDisplayContext.FIXED, light, 0, ms, buffer, renderWorld, 0);
            ms.popPose();
        }

        ms.popPose();
    }
}
