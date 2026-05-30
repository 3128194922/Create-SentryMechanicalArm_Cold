package com.createsentryarm.compat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 运行时兼容层：检测 DungeonNowLoading mod 是否加载，
 * 识别 scorcher / soul_scorcher 物品，无编译依赖。
 */
public final class ScorcherCompat {
    public static final String DNL_MODID = "dungeonnowloading";
    private static final ResourceLocation SCORCHER_ID = ResourceLocation.fromNamespaceAndPath(DNL_MODID, "scorcher");
    private static final ResourceLocation SOUL_SCORCHER_ID = ResourceLocation.fromNamespaceAndPath(DNL_MODID, "soul_scorcher");

    private ScorcherCompat() {}

    public static boolean isDNLLoaded() {
        return ModList.get().isLoaded(DNL_MODID);
    }

    public static boolean isScorcher(ItemStack stack) {
        if (!isDNLLoaded() || stack.isEmpty()) return false;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return SCORCHER_ID.equals(id) || SOUL_SCORCHER_ID.equals(id);
    }

    public static boolean isSoulScorcher(ItemStack stack) {
        if (!isDNLLoaded() || stack.isEmpty()) return false;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return SOUL_SCORCHER_ID.equals(id);
    }
}
