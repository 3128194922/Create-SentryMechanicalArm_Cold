package com.example.createsentryarm.content;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.render.ContraptionMatrices;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.items.ItemStackHandler;
import org.apache.commons.lang3.tuple.MutablePair;

import java.util.ArrayList;
import java.util.List;

public class FireControlMovementBehaviour implements MovementBehaviour {
    public static class FireControlData {
        public boolean whitelist;
        public ItemStack displayItem = ItemStack.EMPTY;
        public final List<String> targets = new ArrayList<>();
    }

    @Override
    public boolean isActive(MovementContext context) {
        return true;
    }

    @Override
    public void startMoving(MovementContext context) {
        refresh(context);
    }

    @Override
    public void tick(MovementContext context) {
        refresh(context);
    }

    private void refresh(MovementContext context) {
        FireControlData data = new FireControlData();
        if (context.blockEntityData != null && context.blockEntityData.contains("Inventory")) {
            ItemStackHandler inventory = new ItemStackHandler(1);
            inventory.deserializeNBT(context.blockEntityData.getCompound("Inventory"));
            ItemStack stack = inventory.getStackInSlot(0);
            data.displayItem = stack;
            CompoundTag tag = stack.getTag();
            if (tag != null) {
                data.whitelist = tag.getBoolean("WhitelistMode");
                if (tag.contains("TargetList", Tag.TAG_LIST)) {
                    ListTag list = tag.getList("TargetList", Tag.TAG_STRING);
                    for (Tag entry : list) {
                        data.targets.add(entry.getAsString());
                    }
                }
            }
        }
        context.temporaryData = data;
    }

    public static AttackArmBlockEntity.TargetFilter findFilter(MovementContext context) {
        if (context.contraption == null) {
            return AttackArmBlockEntity.TargetFilter.DEFAULT_ALL;
        }
        for (MutablePair<?, MovementContext> pair : context.contraption.getActors()) {
            MovementContext other = pair.getRight();
            if (other.temporaryData instanceof FireControlData data && !data.displayItem.isEmpty()) {
                return new AttackArmBlockEntity.TargetFilter(true, data.whitelist, new ArrayList<>(data.targets));
            }
        }
        return AttackArmBlockEntity.TargetFilter.DEFAULT_ALL;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void renderInContraption(MovementContext context, VirtualRenderWorld renderWorld, ContraptionMatrices matrices, MultiBufferSource buffer) {
    }
}
