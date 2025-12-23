package euphy.upo.sentrymechanicalarm.util;

import com.mojang.authlib.GameProfile;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import euphy.upo.sentrymechanicalarm.content.SentryArmBlockEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;

import java.util.UUID;
import java.util.WeakHashMap;

public class SentryFakePlayer {

    private static final WeakHashMap<SentryArmBlockEntity, FakePlayer> FAKE_PLAYERS = new WeakHashMap<>();

    public static FakePlayer get(SentryArmBlockEntity arm) {
        if (!(arm.getLevel() instanceof ServerLevel serverLevel)) return null;

        return FAKE_PLAYERS.computeIfAbsent(arm, k -> {

            String name = "Sentry_" + arm.getBlockPos().getX() + "_" + arm.getBlockPos().getY() + "_" + arm.getBlockPos().getZ();
            GameProfile profile = new GameProfile(UUID.randomUUID(), name);

            FakePlayer fp = FakePlayerFactory.get(serverLevel, profile);
            fp.setGameMode(GameType.ADVENTURE);
            fp.setNoGravity(true);
            fp.setInvisible(true);
            return fp;
        });
    }


    public static void sync(FakePlayer fp, SentryArmBlockEntity arm, float yaw, float pitch, ItemStack gunStack) {
        double x = arm.getBlockPos().getX() + 0.5;
        double y = arm.getBlockPos().getY() + 1.0;
        double z = arm.getBlockPos().getZ() + 0.5;

        fp.setPos(x, y, z);
        fp.xo = x; fp.yo = y; fp.zo = z;
        fp.xOld = x; fp.yOld = y; fp.zOld = z;


        fp.setYRot(yaw);
        fp.setXRot(pitch);
        fp.yHeadRot = yaw;
        fp.yBodyRot = yaw;

        IGunOperator operator = IGunOperator.fromLivingEntity(fp);

        operator.aim(true);

        if (operator.getDataHolder().currentGunItem == null) {
            operator.initialData();
        }
        ItemStack currentFakeItem = fp.getMainHandItem();
        boolean isSameGunId = false;

        IGun iGun = IGun.getIGunOrNull(gunStack);
        if (iGun != null && !currentFakeItem.isEmpty()) {
            IGun iGunCurrent = IGun.getIGunOrNull(currentFakeItem);
            if (iGunCurrent != null && iGunCurrent.getGunId(currentFakeItem).equals(iGun.getGunId(gunStack))) {
                isSameGunId = true;
            }
        }

        if (!isSameGunId) {
            fp.getInventory().clearContent();
            ItemStack newStack = gunStack.copy();
            fp.setItemSlot(EquipmentSlot.MAINHAND, newStack);
            operator.draw(() -> newStack);
            operator.getDataHolder().drawTimestamp = System.currentTimeMillis() - 10000;
        } else {
            if (gunStack.hasTag()) {
                currentFakeItem.setTag(gunStack.getTag().copy());
            } else {
                currentFakeItem.setTag(null);
            }
            currentFakeItem.setCount(gunStack.getCount());
        }

        if (iGun != null) {
            ResourceLocation gunId = iGun.getGunId(gunStack);
            TimelessAPI.getCommonGunIndex(gunId).ifPresent(index -> {
                ResourceLocation ammoId = index.getGunData().getAmmoId();
                net.minecraft.world.item.Item ammoItem = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(ammoId);
                if (ammoItem != null) {
                    ItemStack ammoStack = new ItemStack(ammoItem, 64);

                    fp.getInventory().setItem(1, ammoStack);
                }
            });
        }

        try { fp.tick(); } catch (Exception e) {}
    }
}