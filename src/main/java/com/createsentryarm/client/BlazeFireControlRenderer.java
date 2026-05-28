package com.createsentryarm.client;

import com.createsentryarm.content.BlazeFireControlBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerRenderer;
import net.createmod.catnip.math.AngleHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.util.Mth;

public class BlazeFireControlRenderer implements BlockEntityRenderer<BlazeFireControlBlockEntity> {
    public BlazeFireControlRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(BlazeFireControlBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int light, int overlay) {
        if (blockEntity.getLevel() != null) {
            float horizontalAngle = 0;
            var player = Minecraft.getInstance().player;
            if (player != null && !player.isInvisible()) {
                double dx = player.getX() - (blockEntity.getBlockPos().getX() + 0.5);
                double dz = player.getZ() - (blockEntity.getBlockPos().getZ() + 0.5);
                horizontalAngle = AngleHelper.rad(AngleHelper.deg(-Mth.atan2(dz, dx)) - 90);
            }
            BlazeBurnerRenderer.renderShared(
                    poseStack,
                    null,
                    buffer,
                    blockEntity.getLevel(),
                    blockEntity.getBlockState(),
                    BlazeBurnerBlock.HeatLevel.KINDLED,
                    0,
                    horizontalAngle,
                    false,
                    false,
                    blockEntity.inventory.getStackInSlot(0).isEmpty() ? null : AllPartialModels.LOGISTICS_HAT,
                    blockEntity.hashCode()
            );
        }
    }
}
