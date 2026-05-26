package com.example.createsentryarm.content;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class BlazeFireControlBlockEntity extends SmartBlockEntity {
    public final ItemStackHandler inventory = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.getItem() instanceof FireControlClipboardItem;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            notifyUpdate();
        }
    };
    private LazyOptional<IItemHandler> itemHandler = LazyOptional.of(() -> inventory);

    public BlazeFireControlBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    @Override
    protected void write(CompoundTag tag, boolean clientPacket) {
        super.write(tag, clientPacket);
        tag.put("Inventory", inventory.serializeNBT());
    }

    @Override
    protected void read(CompoundTag tag, boolean clientPacket) {
        super.read(tag, clientPacket);
        if (tag.contains("Inventory")) {
            inventory.deserializeNBT(tag.getCompound("Inventory"));
        }
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return itemHandler.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemHandler.invalidate();
    }

    public List<String> getTargetList() {
        ItemStack stack = inventory.getStackInSlot(0);
        List<String> targets = new ArrayList<>();
        if (!stack.isEmpty() && stack.hasTag() && stack.getTag().contains("TargetList", Tag.TAG_LIST)) {
            ListTag list = stack.getTag().getList("TargetList", Tag.TAG_STRING);
            for (Tag entry : list) {
                targets.add(entry.getAsString());
            }
        }
        return targets;
    }

    public boolean isWhitelist() {
        ItemStack stack = inventory.getStackInSlot(0);
        return !stack.isEmpty() && stack.getOrCreateTag().getBoolean("WhitelistMode");
    }
}
