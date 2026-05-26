package com.example.createsentryarm.content;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class FireControlClipboardItem extends Item {
    public FireControlClipboardItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            openClipboardGui(player, stack);
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() == null) {
            return InteractionResult.PASS;
        }
        if (!context.getLevel().isClientSide) {
            openClipboardGui(context.getPlayer(), context.getItemInHand());
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity entity, InteractionHand hand) {
        if (!player.level().isClientSide) {
            addTarget(stack, entity.getName().getString());
        }
        return InteractionResult.SUCCESS;
    }

    private void openClipboardGui(Player player, ItemStack stack) {
        List<String> targets = readTargets(stack);
        boolean whitelist = stack.getOrCreateTag().getBoolean("WhitelistMode");
        NetworkHooks.openScreen((ServerPlayer) player, new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.literal("火控剪贴板");
            }

            @Nullable
            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inventory, Player innerPlayer) {
                return new FireControlMenu(id, inventory, targets, whitelist);
            }
        }, buf -> {
            buf.writeBlockPos(BlockPos.ZERO);
            buf.writeBoolean(whitelist);
            buf.writeVarInt(targets.size());
            for (String target : targets) {
                buf.writeUtf(target);
            }
        });
    }

    public static void addTarget(ItemStack stack, String name) {
        CompoundTag tag = stack.getOrCreateTag();
        ListTag list = tag.contains("TargetList", Tag.TAG_LIST) ? tag.getList("TargetList", Tag.TAG_STRING) : new ListTag();
        boolean exists = false;
        for (Tag entry : list) {
            if (name.equals(entry.getAsString())) {
                exists = true;
                break;
            }
        }
        if (!exists) {
            list.add(StringTag.valueOf(name));
        }
        tag.put("TargetList", list);
    }

    public static List<String> readTargets(ItemStack stack) {
        List<String> targets = new ArrayList<>();
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("TargetList", Tag.TAG_LIST)) {
            for (Tag entry : tag.getList("TargetList", Tag.TAG_STRING)) {
                targets.add(entry.getAsString());
            }
        }
        return targets;
    }
}
