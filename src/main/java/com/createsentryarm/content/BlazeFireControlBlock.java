package com.createsentryarm.content;

import com.createsentryarm.CreateSentryArmMod;
import com.simibubi.create.AllShapes;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BlazeFireControlBlock extends Block implements IBE<BlazeFireControlBlockEntity>, EntityBlock {
    public BlazeFireControlBlock(Properties properties) {
        super(properties);
    }

    @Override
    public Class<BlazeFireControlBlockEntity> getBlockEntityClass() {
        return BlazeFireControlBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends BlazeFireControlBlockEntity> getBlockEntityType() {
        return CreateSentryArmMod.BLAZE_FIRE_CONTROL_BE.get();
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return AllShapes.HEATER_BLOCK_SHAPE;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        BlazeFireControlBlockEntity blockEntity = getBlockEntity(level, pos);
        if (blockEntity == null) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        ItemStack clipboard = blockEntity.inventory.getStackInSlot(0);
        if (held.getItem() instanceof FireControlClipboardItem && clipboard.isEmpty()) {
            if (!level.isClientSide) {
                ItemStack copy = held.copy();
                copy.setCount(1);
                blockEntity.inventory.setStackInSlot(0, copy);
                if (!player.isCreative()) {
                    held.shrink(1);
                }
            }
            return InteractionResult.SUCCESS;
        }
        if (held.isEmpty() && !clipboard.isEmpty()) {
            if (!level.isClientSide) {
                if (!player.getInventory().add(clipboard.copy())) {
                    Block.popResource(level, pos, clipboard.copy());
                }
                blockEntity.inventory.setStackInSlot(0, ItemStack.EMPTY);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
