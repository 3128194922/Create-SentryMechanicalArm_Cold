package com.createsentryarm.client;

import com.createsentryarm.entity.FlameProjectileEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class FlameProjectileRenderer extends EntityRenderer<FlameProjectileEntity> {
    public FlameProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(FlameProjectileEntity entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
    }

    @Override
    public ResourceLocation getTextureLocation(FlameProjectileEntity entity) {
        return new ResourceLocation("textures/misc/white.png");
    }
}
