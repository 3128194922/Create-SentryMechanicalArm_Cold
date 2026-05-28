package com.createsentryarm.client;

import com.createsentryarm.content.AttackArmBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

public class CreateSentryArmClient {
    private CreateSentryArmClient() {
    }

    public static AttackArmRenderer createAttackArmRenderer(BlockEntityRendererProvider.Context context) {
        return new AttackArmRenderer(context);
    }
}
