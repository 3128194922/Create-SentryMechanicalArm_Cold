package euphy.upo.sentrymechanicalarm.content;

import com.tacz.guns.api.event.common.GunFireEvent;
import euphy.upo.sentrymechanicalarm.util.SentryFakePlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.common.util.FakePlayer;

@Mod.EventBusSubscriber(modid = "sentrymechanicalarm")
public class SentryEventHandler {
    @SubscribeEvent
    public static void onGunFire(GunFireEvent event) {
        if (event.getLogicalSide() == LogicalSide.CLIENT) return;
        if (event.getShooter() instanceof FakePlayer fp && fp.getGameProfile().getName().startsWith("Sentry_")) {
            SentryFakePlayer.markFired(fp);
        }
    }
}