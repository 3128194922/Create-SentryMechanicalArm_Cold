package com.createsentryarm.content;

import com.createsentryarm.CreateSentryArmMod;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPointType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class CreateArmInteractionPoints {
    public static final ArmInteractionPointType ATTACK_ARM_POINT = new AttackArmPointType();
    private static boolean registered;

    public static void register() {
        if (registered) {
            return;
        }
        Registry.register(CreateBuiltInRegistries.ARM_INTERACTION_POINT_TYPE,
                new ResourceLocation(CreateSentryArmMod.MODID, "attack_arm"),
                ATTACK_ARM_POINT);
        ArmInteractionPointType.init();
        registered = true;
    }

    private static class AttackArmPointType extends ArmInteractionPointType {
        @Override
        public boolean canCreatePoint(Level level, BlockPos pos, BlockState state) {
            return state.is(CreateSentryArmMod.ATTACK_ARM.get());
        }

        @Override
        public ArmInteractionPoint createPoint(Level level, BlockPos pos, BlockState state) {
            return new AttackArmPoint(this, level, pos, state);
        }

        @Override
        public int getPriority() {
            return 200;
        }
    }

    private static class AttackArmPoint extends ArmInteractionPoint {
        public AttackArmPoint(ArmInteractionPointType type, Level level, BlockPos pos, BlockState state) {
            super(type, level, pos, state);
        }

        @Override
        protected Vec3 getInteractionPositionVector() {
            return Vec3.atLowerCornerOf(pos).add(.5f, 14 / 16f, .5f);
        }
    }
}
