package com.createsentryarm.content;

import com.createsentryarm.CreateSentryArmMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.simibubi.create.api.equipment.potatoCannon.PotatoCannonProjectileType;
import com.simibubi.create.content.equipment.potatoCannon.PotatoCannonItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.LingeringPotionItem;
import net.minecraft.world.item.SplashPotionItem;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据包驱动的弹药数据：{@code data/createsentryarm/ammo/*.json}。
 * 服务端 /reload 或进入世界时自动重载；数据包条目按物品 ID 覆盖内置行为。
 */
@Mod.EventBusSubscriber(modid = CreateSentryArmMod.MODID)
public class AmmoDataManager extends SimpleJsonResourceReloadListener {
    public static final TagKey<Item> FIREWORK_TAG = TagKey.create(
            net.minecraft.core.registries.Registries.ITEM, new ResourceLocation("minecraft", "firework"));

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Map<ResourceLocation, AmmoData> ENTRIES = new ConcurrentHashMap<>();
    public static final AmmoDataManager INSTANCE = new AmmoDataManager();

    private AmmoDataManager() {
        super(GSON, "ammo");
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resourceManager, ProfilerFiller profiler) {
        ENTRIES.clear();
        files.forEach((id, element) -> {
            try {
                if (!element.isJsonObject()) {
                    return;
                }
                var json = element.getAsJsonObject();
                if (!json.has("item")) {
                    CreateSentryArmMod.LOGGER.warn("Ammo data {} missing required field 'item', skipped", id);
                    return;
                }
                ResourceLocation itemId = new ResourceLocation(json.get("item").getAsString());
                AmmoData data = AmmoData.fromJson(json, itemId);
                ENTRIES.put(itemId, data);
            } catch (Exception e) {
                CreateSentryArmMod.LOGGER.error("Failed to parse ammo data {}", id, e);
            }
        });
        CreateSentryArmMod.LOGGER.info("Loaded {} datapack ammo entries", ENTRIES.size());
    }

    /**
     * 取某武器当前装填弹药对应的弹药数据：
     * 弹药物品 ID 数据包条目优先，否则按类别回退到内置默认。
     */
    public static AmmoData get(@Nullable Level level, ItemStack weapon, ItemStack ammo) {
        if (!ammo.isEmpty()) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(ammo.getItem());
            if (id != null) {
                AmmoData entry = ENTRIES.get(id);
                if (entry != null) {
                    return entry;
                }
            }
        }
        return builtin(level, weapon, ammo);
    }

    private static AmmoData builtin(@Nullable Level level, ItemStack weapon, ItemStack ammo) {
        ResourceLocation id = ammo.isEmpty() ? new ResourceLocation("minecraft", "air")
                : ForgeRegistries.ITEMS.getKey(ammo.getItem());
        if (id == null) {
            id = new ResourceLocation("minecraft", "air");
        }
        if (ammo.is(Items.FIREWORK_ROCKET)) {
            // 原版烟花：直线飞行，射程 40，无高抛模式
            return AmmoData.of(id, 1.6, 0.0, 0.99, 40.0, 40.0, false, 1.0, 1.0, 0.0);
        }
        if (ammo.getItem() instanceof ArrowItem) {
            if (ammo.is(FIREWORK_TAG)) {
                // 无下坠箭（firework tag）：直线飞行，射程 40，无高抛模式
                return AmmoData.of(id, 1.6, 0.0, 0.99, 40.0, 40.0, false, 1.0, 1.0, 0.0);
            }
            // 普通箭：平射 20 格 / 高抛 40 格
            return AmmoData.of(id, 1.6, 0.05, 0.99, 20.0, 40.0, true, 1.0, 1.0, 0.0);
        }
        if (weapon.getItem() instanceof SplashPotionItem || weapon.getItem() instanceof LingeringPotionItem) {
            // 药水：平射 20 格 / 高抛 40 格
            return AmmoData.of(id, 2.5, 0.05, 0.99, 20.0, 40.0, true, 1.0, 1.0, 0.0);
        }
        if (level != null && weapon.getItem() instanceof PotatoCannonItem) {
            Optional<net.minecraft.core.Holder.Reference<PotatoCannonProjectileType>> ref =
                    PotatoCannonProjectileType.getTypeForItem(level.registryAccess(), ammo.getItem());
            if (ref.isPresent()) {
                PotatoCannonProjectileType type = ref.get().value();
                // 土豆炮：射程 25，无高抛模式
                return AmmoData.of(id,
                        2.0 * type.velocityMultiplier(),
                        0.05 * type.gravityMultiplier(),
                        type.drag(), 25.0, 25.0, false, 1.0, 1.0, 0.0);
            }
        }
        return AmmoData.direct(id, AmmoData.DEFAULT_SPEED, AmmoData.DEFAULT_GRAVITY, AmmoData.DEFAULT_DRAG);
    }
}
