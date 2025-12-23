package euphy.upo.sentrymechanicalarm;

import euphy.upo.sentrymechanicalarm.datagen.DataGenerators;
import euphy.upo.sentrymechanicalarm.network.NetworkHandler;
import euphy.upo.sentrymechanicalarm.registry.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;


@Mod(SentryMechanicalArm.MODID)
public class SentryMechanicalArm
{
    public static final String MODID = "sentrymechanicalarm";
    public SentryMechanicalArm(FMLJavaModLoadingContext context)
    {
        IEventBus modEventBus = context.getModEventBus();
        SentryRegistry.register(modEventBus);
        SentryRegistry.MENUS.register(modEventBus);
        SentryRegistry.CREATIVE_TABS.register(modEventBus);
        SentryRegistry.REGISTRATE.registerEventListeners(modEventBus);
        SentryRecipeSerializers.register(modEventBus);
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(DataGenerators::gatherData);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> SentryPartialModels::init);
        SentryArmInteractionPointTypes.register(modEventBus);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {
        event.enqueueWork(() -> {
            NetworkHandler.register();
            event.enqueueWork(SentryRegistry::registerAllStressValues);
        });
    }


}
