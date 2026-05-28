package com.createsentryarm.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 运行时兼容层：在不引入编译依赖的情况下，与 sentrymechanicalarm 模组的火控方块交互。
 * 两个模组的火控剪贴板 NBT 格式完全兼容（TargetList + WhitelistMode），且黑白名单逻辑一致。
 */
public final class SentryCompat {
    public static final String SENTRY_MODID = "sentrymechanicalarm";
    public static final ResourceLocation SENTRY_FIRE_CONTROL_TYPE = new ResourceLocation(SENTRY_MODID, "blaze_fire_control");
    public static final ResourceLocation SENTRY_ARM_TYPE = new ResourceLocation(SENTRY_MODID, "sentry_mechanical_arm");
    private static final String SENTRY_FIRE_CONTROL_CLASS = "euphy.upo.sentrymechanicalarm.content.BlazeFireControlBlockEntity";
    private static final String SENTRY_ARM_CLASS = "euphy.upo.sentrymechanicalarm.content.SentryArmBlockEntity";

    private SentryCompat() {}

    public static boolean isSentryLoaded() {
        return ModList.get().isLoaded(SENTRY_MODID);
    }

    public static boolean isSentryFireControl(BlockEntity be) {
        if (be == null) return false;
        ResourceLocation key = ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(be.getType());
        if (key != null && SENTRY_FIRE_CONTROL_TYPE.equals(key)) return true;
        return SENTRY_FIRE_CONTROL_CLASS.equals(be.getClass().getName());
    }

    public static boolean isSentryArm(BlockEntity be) {
        if (be == null) return false;
        ResourceLocation key = ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(be.getType());
        if (key != null && SENTRY_ARM_TYPE.equals(key)) return true;
        return SENTRY_ARM_CLASS.equals(be.getClass().getName());
    }

    /**
     * 从 sentrymechanicalarm 的火控方块中读取黑白名单数据。
     * 返回 null 表示没有有效剪贴板数据。
     */
    @Nullable
    public static SentryFilterData readFireControlData(BlockEntity be) {
        if (!isSentryFireControl(be)) return null;
        return be.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve()
                .flatMap(handler -> {
                    ItemStack clipboard = handler.getStackInSlot(0);
                    if (clipboard.isEmpty()) return java.util.Optional.empty();
                    CompoundTag tag = clipboard.getTag();
                    if (tag == null) return java.util.Optional.empty();
                    boolean whitelist = tag.getBoolean("WhitelistMode");
                    List<String> targets = new ArrayList<>();
                    if (tag.contains("TargetList", Tag.TAG_LIST)) {
                        ListTag list = tag.getList("TargetList", Tag.TAG_STRING);
                        for (Tag entry : list) {
                            targets.add(entry.getAsString());
                        }
                    }
                    return java.util.Optional.of(new SentryFilterData(whitelist, targets));
                })
                .orElse(null);
    }

    /**
     * 从 sentrymechanicalarm 的火控方块对应动态结构的 NBT 中读取数据。
     */
    @Nullable
    public static SentryFilterData readFromBlockEntityData(CompoundTag beData) {
        if (beData == null || !beData.contains("Inventory")) return null;
        CompoundTag inventoryTag = beData.getCompound("Inventory");
        ItemStackHandler tempInv = new ItemStackHandler(1);
        tempInv.deserializeNBT(inventoryTag);
        ItemStack clipboard = tempInv.getStackInSlot(0);
        if (clipboard.isEmpty()) return null;
        CompoundTag tag = clipboard.getTag();
        if (tag == null) return null;
        boolean whitelist = tag.getBoolean("WhitelistMode");
        List<String> targets = new ArrayList<>();
        if (tag.contains("TargetList", Tag.TAG_LIST)) {
            ListTag list = tag.getList("TargetList", Tag.TAG_STRING);
            for (Tag entry : list) {
                targets.add(entry.getAsString());
            }
        }
        return new SentryFilterData(whitelist, targets);
    }

    public record SentryFilterData(boolean whitelist, List<String> targets) {}
}
