package euphy.upo.sentrymechanicalarm.util;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.resource.pojo.data.gun.BulletData;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.sound.SoundManager;
import euphy.upo.sentrymechanicalarm.content.SentryArmBlockEntity;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Optional;

@OnlyIn(Dist.CLIENT)
public class ArmSoundHelper {
    public static void playFireEffects(SentryArmBlockEntity sentry, Level level, Vec3 pos, Vec3 direction, double maxDistance, ItemStack stack, GunData gunData) {
        if (!(level instanceof ClientLevel clientLevel)) return;
        Optional<GunDisplayInstance> displayOpt = TimelessAPI.getGunDisplay(stack);
        if (displayOpt.isPresent()) {
            GunDisplayInstance display = displayOpt.get();
            ResourceLocation soundId = display.getSounds(SoundManager.SHOOT_SOUND);

            if (soundId != null) {
                float volume = 4.0f;
                if (gunData.getFireSound() != null) {
                    volume *= gunData.getFireSound().getFireMultiplier();
                }
                float pitch = 1.0f + (level.random.nextFloat() - 0.5f) * 0.1f;
                int distance = 64;

                Entity dummyEntity = new Snowball(clientLevel, pos.x, pos.y, pos.z);
                dummyEntity.setPos(pos.x, pos.y, pos.z);
                SoundPlayManager.playClientSound(dummyEntity, soundId, volume, pitch, distance);
            }
        }

        BulletData bulletData = gunData.getBulletData();
        if (bulletData != null && bulletData.hasTracerAmmo()) {
            int rpm = gunData.getRoundsPerMinute();
            float rps = rpm / 60.0f;
            if (rps <= 0) rps = 1;
            float chance = 30.0f / rps;

            if (level.random.nextFloat() < chance) {
                Vec3 startPos = pos.add(0, 1.8, 0).add(direction.scale(0.5));
 
            }
        }
    }
}