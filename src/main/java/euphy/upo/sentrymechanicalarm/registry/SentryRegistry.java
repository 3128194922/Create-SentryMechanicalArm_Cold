package euphy.upo.sentrymechanicalarm.registry;


import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.foundation.data.SharedProperties;
import com.tterrag.registrate.util.entry.*;
import euphy.upo.sentrymechanicalarm.content.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.entity.decoration.PaintingVariant;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import static com.simibubi.create.foundation.data.TagGen.pickaxeOnly;
import static euphy.upo.sentrymechanicalarm.SentryMechanicalArm.MODID;

public class SentryRegistry {

    public static final CreateRegistrate REGISTRATE = CreateRegistrate.create(MODID);

    public static final ItemEntry<ApplePieItem> APPLE_PIE = REGISTRATE
            .item("apple_pie", ApplePieItem::new)
            .properties(p -> p
                    .food(new FoodProperties.Builder().nutrition(8).saturationMod(0.3f).build())
            )
            .register();

    public static final ItemEntry<UnfinishedAmmoItem> UNFINISHED_AMMO = REGISTRATE
            .item("unfinished_ammo", UnfinishedAmmoItem::new)
            .properties(p -> p
            )
            .model((ctx, prov) -> {
            })
            .register();

    public static final BlockEntry<SentryArmBlock> SENTRY_ARM_BLOCK = REGISTRATE
            .block("sentry_mechanical_arm", SentryArmBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.mapColor(MapColor.COLOR_GRAY))
            .blockstate((ctx, prov) -> {
            })
            .transform(pickaxeOnly())
            .onRegister(MovementBehaviour.movementBehaviour(new SentryMovementBehaviour()))
            .item()
            .model((ctx, prov) -> {
            })
            .build()
            .register();

    public static final BlockEntityEntry<SentryArmBlockEntity> SENTRY_ARM_BE = REGISTRATE
            .blockEntity("sentry_mechanical_arm", SentryArmBlockEntity::new)
            .validBlocks(SENTRY_ARM_BLOCK)
            .renderer(() -> SentryArmRenderer::new)
            .register();


    public static final BlockEntry<BlazeFireControlBlock> BLAZE_FIRE_CONTROL = REGISTRATE
            .block("blaze_fire_control", BlazeFireControlBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.mapColor(MapColor.COLOR_GRAY)
                    .lightLevel(s -> 15)
                    .noOcclusion())
            .transform(pickaxeOnly())
            .addLayer(() -> RenderType::cutoutMipped)
            .blockstate((ctx, prov) -> {
                prov.simpleBlock(
                        ctx.getEntry(),
                        prov.models().getExistingFile(prov.modLoc("block/blaze_fire_control"))
                );
            })

            .item(BlazeFireControlBlockItem::new)
            .model((ctx, prov) -> {
                prov.getBuilder(ctx.getName())
                        .parent(new ModelFile.UncheckedModelFile("builtin/entity"))
                        .guiLight(net.minecraft.client.renderer.block.model.BlockModel.GuiLight.FRONT)
                        .transforms()
                        .transform(ItemDisplayContext.GUI)
                        .rotation(30, 225, 0)
                        .translation(0, 0, 0)
                        .scale(0.625f)
                        .end()
                        .transform(ItemDisplayContext.GROUND)
                        .translation(0, 3, 0)
                        .scale(0.25f)
                        .end()
                        .transform(ItemDisplayContext.FIXED)
                        .scale(0.5f)
                        .end()
                        .transform(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)
                        .rotation(75, 45, 0)
                        .translation(0, 2.5f, 0)
                        .scale(0.375f)
                        .end()
                        .transform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND)
                        .rotation(0, 45, 0)
                        .scale(0.4f)
                        .end()
                        .transform(ItemDisplayContext.FIRST_PERSON_LEFT_HAND)
                        .rotation(0, 225, 0)
                        .scale(0.4f)
                        .end();
            })
            .build()
            .register();


    public static final BlockEntityEntry<BlazeFireControlBlockEntity> BLAZE_FIRE_CONTROL_BE = REGISTRATE
            .blockEntity("blaze_fire_control", BlazeFireControlBlockEntity::new)
            .validBlocks(BLAZE_FIRE_CONTROL)
            .renderer(() -> BlazeFireControlRenderer::new)
            .register();

    public static final ItemEntry<FireControlClipboardItem> FIRE_CONTROL_CLIPBOARD = REGISTRATE
            .item("fire_control_clipboard", FireControlClipboardItem::new)
            .properties(p -> p.stacksTo(1))
            .model((ctx, prov) -> prov.generated(ctx::getEntry, prov.modLoc("item/fire_control_clipboard")))
            .register();


    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, "sentrymechanicalarm"); 

    public static final RegistryObject<MenuType<FireControlMenu>> FIRE_CONTROL_MENU =
            MENUS.register("fire_control_menu", () -> IForgeMenuType.create(FireControlMenu::new));


    public static final DeferredRegister<PaintingVariant> PAINTING_VARIANTS =
            DeferredRegister.create(ForgeRegistries.PAINTING_VARIANTS, MODID);


    public static final RegistryObject<PaintingVariant> SEVETH_CHANBER =
            PAINTING_VARIANTS.register("seventh_chamber", () -> new PaintingVariant(32, 32));

    public static void registerAllStressValues() {
        double stressImpact = 3.0;
        BlockStressValues.IMPACTS.register(SENTRY_ARM_BLOCK.get(), () -> stressImpact);
    }

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final RegistryObject<CreativeModeTab> SENTRY_TAB = CREATIVE_TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + MODID))
                    .icon(BLAZE_FIRE_CONTROL::asStack)
                    .displayItems((parameters, output) -> {
                        output.accept(SENTRY_ARM_BLOCK.get());
                        output.accept(BLAZE_FIRE_CONTROL.get());
                        output.accept(FIRE_CONTROL_CLIPBOARD.get());
                        output.accept(APPLE_PIE.get());
                    })
                    .build());


    public static void register(IEventBus eventBus) {
        PAINTING_VARIANTS.register(eventBus);
    }
}