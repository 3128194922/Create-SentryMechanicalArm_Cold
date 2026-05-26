package com.example.createsentryarm.client;

import com.example.createsentryarm.content.BlazeFireControlBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.item.ItemDisplayContext;

public class BlazeFireControlRenderer implements BlockEntityRenderer<BlazeFireControlBlockEntity> {
    public BlazeFireControlRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(BlazeFireControlBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int light, int overlay) {
        if (blockEntity.inventory.getStackInSlot(0).isEmpty()) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.5, 0.75, 0.5);
        poseStack.scale(0.5f, 0.5f, 0.5f);
        net.minecraft.client.Minecraft.getInstance().getItemRenderer().renderStatic(
                blockEntity.inventory.getStackInSlot(0),
                ItemDisplayContext.GROUND,
                light,
                overlay,
                poseStack,
                buffer,
                blockEntity.getLevel(),
                0
        );
        poseStack.popPose();
    }
}
