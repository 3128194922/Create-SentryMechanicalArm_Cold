package com.createsentryarm.content;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.TooltipFlag;
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
            if (player.isShiftKeyDown()) {
                addTarget(stack, player.getName().getString(), player);
            } else {
                openClipboardGui(player, stack);
            }
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() == null) {
            return InteractionResult.PASS;
        }
        if (!context.getLevel().isClientSide) {
            if (context.getPlayer().isShiftKeyDown()) {
                addTarget(context.getItemInHand(), context.getPlayer().getName().getString(), context.getPlayer());
            } else {
                openClipboardGui(context.getPlayer(), context.getItemInHand());
            }
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity entity, InteractionHand hand) {
        if (!player.level().isClientSide) {
            addTarget(player.getItemInHand(hand), entity.getName().getString(), player);
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    private void openClipboardGui(Player player, ItemStack stack) {
        List<String> targets = readTargets(stack);
        boolean whitelist = stack.getOrCreateTag().getBoolean("WhitelistMode");
        NetworkHooks.openScreen((ServerPlayer) player, new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.translatable("item.createsentryarm.fire_control_clipboard");
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

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltipComponents, flag);
        CompoundTag tag = stack.getTag();
        boolean whitelist = tag != null && tag.getBoolean("WhitelistMode");

        if (whitelist) {
            tooltipComponents.add(Component.translatable("tooltip.createsentryarm.clipboard.mode").withStyle(ChatFormatting.GRAY)
                    .append(Component.translatable("gui.createsentryarm.clipboard.whitelist").withStyle(ChatFormatting.AQUA)));
            tooltipComponents.add(Component.translatable("tooltip.createsentryarm.clipboard.whitelist_des_1").withStyle(ChatFormatting.DARK_GRAY));
            tooltipComponents.add(Component.translatable("tooltip.createsentryarm.clipboard.whitelist_des_2").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltipComponents.add(Component.translatable("tooltip.createsentryarm.clipboard.mode").withStyle(ChatFormatting.GRAY)
                    .append(Component.translatable("gui.createsentryarm.clipboard.blacklist").withStyle(ChatFormatting.RED)));
            tooltipComponents.add(Component.translatable("tooltip.createsentryarm.clipboard.blacklist_des_1").withStyle(ChatFormatting.DARK_GRAY));
            tooltipComponents.add(Component.translatable("tooltip.createsentryarm.clipboard.blacklist_des_2").withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltipComponents.add(Component.translatable("tooltip.createsentryarm.clipboard.open_gui").withStyle(ChatFormatting.DARK_GRAY));
        tooltipComponents.add(Component.translatable("tooltip.createsentryarm.clipboard.add_self").withStyle(ChatFormatting.DARK_GRAY));
        tooltipComponents.add(Component.translatable("tooltip.createsentryarm.clipboard.add_target").withStyle(ChatFormatting.DARK_GRAY));
        tooltipComponents.add(Component.translatable("tooltip.createsentryarm.clipboard.spyglass").withStyle(ChatFormatting.DARK_GRAY));
    }

    public static void addTarget(ItemStack stack, String name, @Nullable Player player) {
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
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.createsentryarm.target_added", name), true);
            }
        } else if (player != null) {
            player.displayClientMessage(Component.translatable("message.createsentryarm.target_exists", name), true);
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
