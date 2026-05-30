package com.createsentryarm;

import com.createsentryarm.client.BlazeFireControlRenderer;
import com.createsentryarm.client.CreateSentryArmClient;
import com.createsentryarm.client.FireControlScreen;
import com.createsentryarm.client.FlameProjectileRenderer;
import com.createsentryarm.client.SentryLinkHandler;
import com.createsentryarm.content.AttackArmBlock;
import com.createsentryarm.content.AttackArmBlockEntity;
import com.createsentryarm.content.AttackArmMovementBehaviour;
import com.createsentryarm.content.BlazeFireControlBlock;
import com.createsentryarm.content.BlazeFireControlBlockEntity;
import com.createsentryarm.content.CreateArmInteractionPoints;
import com.createsentryarm.content.FireControlClipboardItem;
import com.createsentryarm.content.FireControlMenu;
import com.createsentryarm.content.FireControlMovementBehaviour;
import com.createsentryarm.entity.FlameProjectileEntity;
import com.createsentryarm.network.CSANetwork;
import com.mojang.logging.LogUtils;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

@Mod(CreateSentryArmMod.MODID)
public class CreateSentryArmMod {
    public static final String MODID = "createsentryarm";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final boolean FIRE_CONTROL_ENABLED = !ModList.get().isLoaded("sentrymechanicalarm");

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);

    public static final RegistryObject<Block> ATTACK_ARM = BLOCKS.register("attack_arm",
            () -> new AttackArmBlock(Block.Properties.of().strength(3.0F).noOcclusion()));
    public static final RegistryObject<Item> ATTACK_ARM_ITEM = ITEMS.register("attack_arm",
            () -> new BlockItem(ATTACK_ARM.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<AttackArmBlockEntity>> ATTACK_ARM_BE =
            BLOCK_ENTITY_TYPES.register("attack_arm",
                    () -> BlockEntityType.Builder.of(AttackArmBlockEntity::new, ATTACK_ARM.get()).build(null));

    public static RegistryObject<Block> BLAZE_FIRE_CONTROL;
    public static RegistryObject<Item> BLAZE_FIRE_CONTROL_ITEM;
    public static RegistryObject<BlockEntityType<BlazeFireControlBlockEntity>> BLAZE_FIRE_CONTROL_BE;
    public static RegistryObject<Item> FIRE_CONTROL_CLIPBOARD;
    public static RegistryObject<MenuType<FireControlMenu>> FIRE_CONTROL_MENU;

    public static final RegistryObject<EntityType<FlameProjectileEntity>> FLAME_PROJECTILE =
            ENTITY_TYPES.register("flame_projectile",
                    () -> EntityType.Builder.<FlameProjectileEntity>of(FlameProjectileEntity::new, MobCategory.MISC)
                            .sized(0.25F, 0.25F)
                            .clientTrackingRange(64)
                            .updateInterval(10)
                            .build(MODID + ":flame_projectile"));

    public static final RegistryObject<CreativeModeTab> TAB = CREATIVE_TABS.register("main",
            () -> CreativeModeTab.builder()
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .title(net.minecraft.network.chat.Component.literal("Create:SentryArm_Cold"))
                    .icon(() -> ATTACK_ARM_ITEM.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(ATTACK_ARM_ITEM.get());
                        if (FIRE_CONTROL_ENABLED) {
                            output.accept(BLAZE_FIRE_CONTROL_ITEM.get());
                            output.accept(FIRE_CONTROL_CLIPBOARD.get());
                        }
                    })
                    .build());

    public CreateSentryArmMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        registerConditionalEntries();

        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITY_TYPES.register(modBus);
        MENUS.register(modBus);
        CREATIVE_TABS.register(modBus);
        ENTITY_TYPES.register(modBus);

        modBus.addListener(this::commonSetup);
        modBus.addListener(this::addCreative);
        MinecraftForge.EVENT_BUS.register(this);
        if (FIRE_CONTROL_ENABLED) {
            MinecraftForge.EVENT_BUS.register(SentryLinkHandler.class);
        }
    }

    private static void registerConditionalEntries() {
        if (!FIRE_CONTROL_ENABLED) {
            return;
        }
        BLAZE_FIRE_CONTROL = BLOCKS.register("blaze_fire_control",
                () -> new BlazeFireControlBlock(Block.Properties.copy(Blocks.BRICKS).noOcclusion()));
        BLAZE_FIRE_CONTROL_ITEM = ITEMS.register("blaze_fire_control",
                () -> new BlockItem(BLAZE_FIRE_CONTROL.get(), new Item.Properties()));
        BLAZE_FIRE_CONTROL_BE = BLOCK_ENTITY_TYPES.register("blaze_fire_control",
                () -> BlockEntityType.Builder.of(BlazeFireControlBlockEntity::new, BLAZE_FIRE_CONTROL.get()).build(null));
        FIRE_CONTROL_CLIPBOARD = ITEMS.register("fire_control_clipboard",
                () -> new FireControlClipboardItem(new Item.Properties().stacksTo(1)));
        FIRE_CONTROL_MENU = MENUS.register("fire_control",
                () -> net.minecraftforge.common.extensions.IForgeMenuType.create(FireControlMenu::new));
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            CreateArmInteractionPoints.register();
            MovementBehaviour.REGISTRY.register(ATTACK_ARM.get(), new AttackArmMovementBehaviour());
            if (FIRE_CONTROL_ENABLED) {
                MovementBehaviour.REGISTRY.register(BLAZE_FIRE_CONTROL.get(), new FireControlMovementBehaviour());
            }
            CSANetwork.register();
        });
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS) {
            event.accept(ATTACK_ARM_ITEM.get());
            if (FIRE_CONTROL_ENABLED) {
                event.accept(BLAZE_FIRE_CONTROL_ITEM.get());
                event.accept(FIRE_CONTROL_CLIPBOARD.get());
            }
        }
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(ATTACK_ARM_BE.get(), CreateSentryArmClient::createAttackArmRenderer);
                net.minecraft.client.renderer.entity.EntityRenderers.register(FLAME_PROJECTILE.get(), FlameProjectileRenderer::new);
                if (FIRE_CONTROL_ENABLED) {
                    net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(BLAZE_FIRE_CONTROL_BE.get(), BlazeFireControlRenderer::new);
                    MenuScreens.register(FIRE_CONTROL_MENU.get(), FireControlScreen::new);
                }
            });
        }
    }
}
