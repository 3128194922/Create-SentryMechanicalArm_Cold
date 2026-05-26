package com.example.createsentryarm.client;

import com.example.createsentryarm.content.AttackArmBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

public class CreateSentryArmClient {
    private CreateSentryArmClient() {
    }

    public static AttackArmRenderer createAttackArmRenderer(BlockEntityRendererProvider.Context context) {
        return new AttackArmRenderer(context);
    }
}
