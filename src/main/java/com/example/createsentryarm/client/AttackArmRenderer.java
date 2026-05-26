package com.example.createsentryarm.client;

import com.example.createsentryarm.content.AttackArmBlock;
import com.example.createsentryarm.content.AttackArmBlockEntity;
import com.example.createsentryarm.content.VirtualAttackArmBlockEntity;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmRenderer;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.render.ContraptionMatrices;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;
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
        TransformStack transform = TransformStack.of(localStack);

        float baseAngle = be.baseAngle.getValue(partialTicks);
        float lowerArmAngle = be.lowerArmAngle.getValue(partialTicks) - 135;
        float upperArmAngle = be.upperArmAngle.getValue(partialTicks) - 90;
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

        ArmRenderer.transformBase(transform, baseAngle);
        base.transform(localStack).renderInto(poseStack, builder);

        ArmRenderer.transformLowerArm(transform, lowerArmAngle);
        lowerBody.transform(localStack).renderInto(poseStack, builder);

        ArmRenderer.transformUpperArm(transform, upperArmAngle);
        upperBody.transform(localStack).renderInto(poseStack, builder);

        ArmRenderer.transformHead(transform, headAngle);
        if (inverted) {
            transform.rotateZDegrees(180);
        }
        claw.transform(localStack).renderInto(poseStack, builder);
        if (inverted) {
            transform.rotateZDegrees(180);
        }

        for (int flip : Iterate.positiveAndNegative) {
            localStack.pushPose();
            ArmRenderer.transformClawHalf(transform, hasItem, isBlockItem, flip);
            (flip > 0 ? lowerClawGrip : upperClawGrip).transform(localStack).renderInto(poseStack, builder);
            localStack.popPose();
        }
    }

    @Override
    protected SuperByteBuffer getRotatedModel(AttackArmBlockEntity be, BlockState state) {
        return CachedBuffers.partial(AllPartialModels.ARM_COG, state);
    }

    public static void renderInContraption(MovementContext context, VirtualRenderWorld renderWorld, ContraptionMatrices matrices, MultiBufferSource buffer) {
        if (!(context.temporaryData instanceof VirtualAttackArmBlockEntity virtualBE)) {
            return;
        }
        BlockState blockState = context.state;
        float partialTicks = net.createmod.catnip.animation.AnimationTickHolder.getPartialTicks();
        float baseAngle = virtualBE.baseAngle.getValue(partialTicks);
        float lowerArmAngle = virtualBE.lowerArmAngle.getValue(partialTicks) - 135;
        float upperArmAngle = virtualBE.upperArmAngle.getValue(partialTicks) - 90;
        float headAngle = virtualBE.headAngle.getValue(partialTicks);
        boolean inverted = blockState.getValue(AttackArmBlock.CEILING);
        int light = LightTexture.FULL_BRIGHT;
        var localPos = net.createmod.catnip.math.VecHelper.getCenterOf(context.localPos);
        var globalPos = context.contraption.entity != null
                ? context.contraption.entity.toGlobalVector(localPos, partialTicks)
                : localPos;
        if (context.contraption.entity != null) {
            light = LevelRenderer.getLightColor(context.world, BlockPos.containing(globalPos));
        }

        ItemStack heldItem = virtualBE.getHeldItem();
        boolean hasItem = !heldItem.isEmpty();
        boolean isBlockItem = hasItem && Minecraft.getInstance().getItemRenderer().getModel(heldItem, renderWorld, null, 0).isGui3d();

        VertexConsumer builder = buffer.getBuffer(RenderType.solid());
        PoseStack poseStack = matrices.getModel();
        TransformStack transform = TransformStack.of(poseStack);
        poseStack.pushPose();
        transform.center();
        if (inverted) {
            transform.rotateXDegrees(180);
        }

        float kineticAngle = KineticBlockEntityRenderer.getAngleForBe(virtualBE, BlockPos.containing(globalPos), Direction.Axis.Y);
        CachedBuffers.partial(AllPartialModels.ARM_COG, blockState)
                .light(light)
                .rotateCentered(kineticAngle, Direction.UP)
                .renderInto(matrices.getViewProjection(), builder);

        SuperByteBuffer base = CachedBuffers.partial(AllPartialModels.ARM_BASE, blockState).light(light);
        SuperByteBuffer lowerBody = CachedBuffers.partial(AllPartialModels.ARM_LOWER_BODY, blockState).light(light);
        SuperByteBuffer upperBody = CachedBuffers.partial(AllPartialModels.ARM_UPPER_BODY, blockState).light(light);
        SuperByteBuffer claw = CachedBuffers.partial(AllPartialModels.ARM_CLAW_BASE, blockState).light(light);
        SuperByteBuffer upperClawGrip = CachedBuffers.partial(AllPartialModels.ARM_CLAW_GRIP_UPPER, blockState).light(light);
        SuperByteBuffer lowerClawGrip = CachedBuffers.partial(AllPartialModels.ARM_CLAW_GRIP_LOWER, blockState).light(light);

        ArmRenderer.transformBase(transform, baseAngle);
        base.transform(poseStack).renderInto(matrices.getViewProjection(), builder);
        ArmRenderer.transformLowerArm(transform, lowerArmAngle);
        lowerBody.transform(poseStack).renderInto(matrices.getViewProjection(), builder);
        ArmRenderer.transformUpperArm(transform, upperArmAngle);
        upperBody.transform(poseStack).renderInto(matrices.getViewProjection(), builder);
        ArmRenderer.transformHead(transform, headAngle);
        if (inverted) {
            transform.rotateZDegrees(180);
        }
        claw.transform(poseStack).renderInto(matrices.getViewProjection(), builder);
        if (inverted) {
            transform.rotateZDegrees(180);
        }
        for (int flip : Iterate.positiveAndNegative) {
            poseStack.pushPose();
            ArmRenderer.transformClawHalf(transform, hasItem, isBlockItem, flip);
            (flip > 0 ? lowerClawGrip : upperClawGrip).transform(poseStack).renderInto(matrices.getViewProjection(), builder);
            poseStack.popPose();
        }

        if (hasItem) {
            poseStack.pushPose();
            float itemScale = isBlockItem ? .5f : .625f;
            transform.rotateXDegrees(90);
            poseStack.translate(0, isBlockItem ? -9 / 16f : -10 / 16f, 0);
            poseStack.scale(itemScale, itemScale, itemScale);
            Minecraft.getInstance().getItemRenderer().renderStatic(heldItem, ItemDisplayContext.FIXED, light, 0, poseStack, buffer, renderWorld, 0);
            poseStack.popPose();
        }
        poseStack.popPose();
    }
}
