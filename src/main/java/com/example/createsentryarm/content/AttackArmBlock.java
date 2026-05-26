package com.example.createsentryarm.content;

import com.example.createsentryarm.CreateSentryArmMod;
import com.simibubi.create.AllItems;
import com.simibubi.create.AllShapes;
import com.simibubi.create.content.equipment.potatoCannon.PotatoCannonItem;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.LingeringPotionItem;
import net.minecraft.world.item.SplashPotionItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class AttackArmBlock extends KineticBlock implements IBE<AttackArmBlockEntity>, ICogWheel {
    public static final BooleanProperty CEILING = BooleanProperty.create("ceiling");

    public AttackArmBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(CEILING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CEILING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(CEILING, ctx.getClickedFace() == Direction.DOWN);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(CEILING) ? AllShapes.MECHANICAL_ARM_CEILING : AllShapes.MECHANICAL_ARM;
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return Direction.Axis.Y;
    }

    @Override
    public Class<AttackArmBlockEntity> getBlockEntityClass() {
        return AttackArmBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends AttackArmBlockEntity> getBlockEntityType() {
        return CreateSentryArmMod.ATTACK_ARM_BE.get();
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.getBlock() != newState.getBlock()) {
            AttackArmBlockEntity blockEntity = getBlockEntity(level, pos);
            if (blockEntity != null && !blockEntity.getHeldItem().isEmpty()) {
                Block.popResource(level, pos, blockEntity.getHeldItem());
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);
        if (AllItems.WRENCH.isIn(stack)) {
            return InteractionResult.PASS;
        }
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        AttackArmBlockEntity blockEntity = getBlockEntity(level, pos);
        if (blockEntity == null) {
            return InteractionResult.FAIL;
        }
        ItemStack held = blockEntity.getHeldItem();
        if (stack.isEmpty()) {
            if (!held.isEmpty() && !level.isClientSide) {
                if (!player.getInventory().add(held.copy())) {
                    Block.popResource(level, pos, held.copy());
                }
                blockEntity.setHeldItem(ItemStack.EMPTY);
            }
            return held.isEmpty() ? InteractionResult.PASS : InteractionResult.SUCCESS;
        }
        if (!held.isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!isSupportedWeapon(stack)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            ItemStack copy = stack.copy();
            copy.setCount(1);
            blockEntity.setHeldItem(copy);
            if (!player.isCreative()) {
                stack.shrink(1);
            }
        }
        return InteractionResult.SUCCESS;
    }

    public static boolean isSupportedWeapon(ItemStack stack) {
        return stack.getItem() instanceof BowItem
                || stack.getItem() instanceof CrossbowItem
                || stack.getItem() instanceof SplashPotionItem
                || stack.getItem() instanceof LingeringPotionItem
                || stack.getItem() instanceof SwordItem
                || stack.getItem() instanceof AxeItem
                || stack.getItem() instanceof PotatoCannonItem;
    }
}
