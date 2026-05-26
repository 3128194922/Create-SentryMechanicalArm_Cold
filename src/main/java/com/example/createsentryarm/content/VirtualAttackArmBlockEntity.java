package com.example.createsentryarm.content;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class VirtualAttackArmBlockEntity extends AttackArmBlockEntity {
    private BlockPos virtualPos;

    public VirtualAttackArmBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
        this.virtualPos = pos;
    }

    public void setVirtualLevel(Level level) {
        this.level = level;
    }

    public void setVirtualPos(BlockPos virtualPos) {
        this.virtualPos = virtualPos;
    }

    @Override
    public BlockPos getBlockPos() {
        return virtualPos;
    }

    @Override
    public void tick() {
    }
}
