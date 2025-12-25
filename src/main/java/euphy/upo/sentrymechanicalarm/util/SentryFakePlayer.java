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
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import org.valkyrienskies.core.api.ships.LoadedShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

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
        // Calculate base position from block position
        double x = arm.getBlockPos().getX() + 0.5;
        double y = arm.getBlockPos().getY() + 1.0;
        double z = arm.getBlockPos().getZ() + 0.5;

        // Valkyrien Skies integration: Transform ship-local position to world coordinates
        Vec3 worldPos;
        LoadedShip ship = VSGameUtilsKt.getShipObjectManagingPos(arm.getLevel(), arm.getBlockPos());
        if (ship != null) {
            // Arm is on a ship, transform from ship space to world space
            worldPos = VectorConversionsMCKt.toMinecraft(
                ship.getTransform().getShipToWorld().transformPosition(
                    VectorConversionsMCKt.toJOML(new Vec3(x, y, z))
                )
            );
        } else {
            // Arm is not on a ship, use position as-is
            worldPos = new Vec3(x, y, z);
        }

        // Set fake player position using world coordinates
        fp.setPos(worldPos.x, worldPos.y, worldPos.z);
        fp.xo = worldPos.x; fp.yo = worldPos.y; fp.zo = worldPos.z;
        fp.xOld = worldPos.x; fp.yOld = worldPos.y; fp.zOld = worldPos.z;


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