package com.example.createsentryarm.util;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class ArmFakePlayer {
    private ArmFakePlayer() {
    }

    public static FakePlayer get(ServerLevel level, BlockPos pos) {
        UUID uuid = UUID.nameUUIDFromBytes((level.dimension().location() + ":" + pos).getBytes(StandardCharsets.UTF_8));
        return FakePlayerFactory.get(level, new GameProfile(uuid, "[CreateSentryArm]"));
    }

    public static FakePlayer sync(ServerLevel level, BlockPos pos, Vec3 eyePos, float yaw, float pitch, ItemStack heldItem) {
        FakePlayer player = get(level, pos);
        player.teleportTo(eyePos.x, eyePos.y - player.getEyeHeight(), eyePos.z);
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.setYHeadRot(yaw);
        player.yRotO = yaw;
        player.xRotO = pitch;
        player.setItemInHand(InteractionHand.MAIN_HAND, heldItem.copy());
        return player;
    }
}
