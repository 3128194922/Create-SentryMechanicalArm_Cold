package com.example.createsentryarm.content;

import com.example.createsentryarm.CreateSentryArmMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class FireControlMenu extends AbstractContainerMenu {
    private final List<String> targetList;
    public boolean whitelist;

    public FireControlMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        super(CreateSentryArmMod.FIRE_CONTROL_MENU.get(), id);
        buf.readBlockPos();
        whitelist = buf.readBoolean();
        targetList = new ArrayList<>();
        int size = buf.readVarInt();
        for (int i = 0; i < size; i++) {
            targetList.add(buf.readUtf());
        }
    }

    public FireControlMenu(int id, Inventory inventory, List<String> targets, boolean whitelist) {
        super(CreateSentryArmMod.FIRE_CONTROL_MENU.get(), id);
        this.targetList = new ArrayList<>(targets);
        this.whitelist = whitelist;
    }

    public List<String> getTargetList() {
        return targetList;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.getMainHandItem().getItem() instanceof FireControlClipboardItem
                || player.getOffhandItem().getItem() instanceof FireControlClipboardItem;
    }
}
