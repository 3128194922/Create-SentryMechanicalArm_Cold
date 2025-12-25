package euphy.upo.sentrymechanicalarm.content;

import com.mojang.logging.LogUtils;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.pojo.data.gun.*;
import euphy.upo.sentrymechanicalarm.network.NetworkHandler;
import euphy.upo.sentrymechanicalarm.network.SentryShootPacket;
import euphy.upo.sentrymechanicalarm.util.*;
import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.items.IItemHandler;
import org.slf4j.Logger;
import org.valkyrienskies.core.api.ships.LoadedShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

import java.util.*;

public class SentryArmBlockEntity extends KineticBlockEntity implements IArmAmmoStorage {

    private final IItemHandler smartItemHandler = new SentryItemHandler();
    private BlockPos connectedFireControlPos = null;
    private float lowerArmRecoilOffset = 0f;
    private BlockPos cachedTargetBlock = null;
    private int idleScanTimer = 0;
    public float idleTargetYaw = 0;
    public float idleTargetPitch = 0;
    private int syncedTargetId = -1;
    private boolean shouldEjectShell = false;
    private float shootDelayAccumulator = 0f;
    public LerpedFloat baseAngle;
    public LerpedFloat lowerArmAngle;
    public LerpedFloat upperArmAngle;
    public LerpedFloat headAngle;
    private ItemStack heldItem = ItemStack.EMPTY;
    public final NonNullList<ItemStack> attachedAmmoBoxes = NonNullList.withSize(2, ItemStack.EMPTY);
    private int lineOfSightTicker = 0;
    private long lastShootTime = 0;
    private LivingEntity cachedTarget;
    private int scanCooldown = 0;

    public SentryArmBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        baseAngle = LerpedFloat.angular().startWithValue(0);
        lowerArmAngle = LerpedFloat.angular().startWithValue(135);
        upperArmAngle = LerpedFloat.angular().startWithValue(45);
        headAngle = LerpedFloat.angular().startWithValue(0);
    }

    public boolean shouldEjectShell() { return shouldEjectShell; }
    public void setShellEjected() { this.shouldEjectShell = false; }
    public ItemStack getHeldItem() {
        return heldItem;
    }

    public void setHeldItem(ItemStack stack) {
        this.heldItem = stack;
        this.setChanged();
        this.sendData();
    }

    public long getLastShootTime() {
        return lastShootTime;
    }

    public boolean addAmmoBox(ItemStack stack) {
        for (int i = 0; i < attachedAmmoBoxes.size(); i++) {
            if (attachedAmmoBoxes.get(i).isEmpty()) {
                ItemStack copy = stack.copy();
                copy.setCount(1);
                attachedAmmoBoxes.set(i, copy);
                this.setChanged();
                this.sendData();
                return true;
            }
        }
        return false;
    }

    public ItemStack removeLastAmmoBox() {

        for (int i = attachedAmmoBoxes.size() - 1; i >= 0; i--) {
            if (!attachedAmmoBoxes.get(i).isEmpty()) {

                ItemStack stack = attachedAmmoBoxes.get(i);
                attachedAmmoBoxes.set(i, ItemStack.EMPTY);
                this.setChanged();
                this.sendData();
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack getAmmoBox() {
        for(ItemStack stack : attachedAmmoBoxes) {
            if(!stack.isEmpty()) return stack;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void setAmmoBox(ItemStack stack) {
 
        attachedAmmoBoxes.clear(); 
        attachedAmmoBoxes.set(0, stack);
        attachedAmmoBoxes.set(1, ItemStack.EMPTY);
        this.setChanged();
        this.sendData();
    }

    @Override
    public void tick() {
        super.tick();

        baseAngle.tickChaser();
        lowerArmAngle.tickChaser();
        upperArmAngle.tickChaser();
        headAngle.tickChaser();

        ItemStack currentHeld = getHeldItem();

        boolean isPowered = Math.abs(this.getSpeed()) > 0;

        if (!isPowered) {
            sentryDeactivated();
        }
        else if (!currentHeld.isEmpty() && currentHeld.getItem() instanceof IGun) {
            if (this.level.isClientSide) {
                updateClientTarget();
            }
            sentryLogic();
        }
        else {
            sentryDeactivated();
        }

        if (!level.isClientSide && !attachedAmmoBoxes.isEmpty()) {
            if (currentHeld.isEmpty() || !(currentHeld.getItem() instanceof IGun)) {
                popAmmoBox();
            }
        }
    }

    private void sentryDeactivated() {
        float speed = 0.05f;
        lowerArmAngle.chase(135, speed, LerpedFloat.Chaser.EXP);
        upperArmAngle.chase(45, speed, LerpedFloat.Chaser.EXP);
        headAngle.chase(0, speed, LerpedFloat.Chaser.EXP);
        baseAngle.chase(baseAngle.getValue(), 0, LerpedFloat.Chaser.EXP);
    }

    private double getSentryRange() {
        ItemStack currentHeld = getHeldItem();
        if (currentHeld.isEmpty() || !(currentHeld.getItem() instanceof IGun iGun)) {
            return 16.0;
        }

        ResourceLocation gunId = iGun.getGunId(currentHeld);
        Optional<CommonGunIndex> indexOpt = TimelessAPI.getCommonGunIndex(gunId);

        if (indexOpt.isPresent()) {
            GunData gunData = indexOpt.get().getGunData();
            BulletData bulletData = gunData.getBulletData();

            if (bulletData != null) {
                float baseEffectiveRange = -1.0f;

                ExtraDamage extraDamage = bulletData.getExtraDamage();
                if (extraDamage != null) {
                    LinkedList<ExtraDamage.DistanceDamagePair> damageAdjust = extraDamage.getDamageAdjust();
                    if (damageAdjust != null && !damageAdjust.isEmpty()) {
                        baseEffectiveRange = damageAdjust.get(0).getDistance();
                    }
                }

                if (baseEffectiveRange <= 0) {
                    float speed = bulletData.getSpeed();

                    baseEffectiveRange = (speed > 0 ? speed : 10.0f) * 12.0f;
                }

                double finalRange = baseEffectiveRange * 2.0;

                return Math.min(finalRange, 128.0);
            }
        }

        return 8.0;
    }

    public void setLastShootTime(long time) {
        this.lastShootTime = time;
    }


    private void sentryLogic() {
        if (!this.level.isClientSide) {
            FakePlayer fp = SentryFakePlayer.get(this);
            if (fp != null) {
                SentryFakePlayer.sync(fp, this, baseAngle.getValue(), headAngle.getValue(), this.heldItem);
            }
        }

        Vec3 currentTickBestPos = null;

        if (!this.level.isClientSide) {
            double maxRange = this.getSentryRange();
            boolean invalid = false;

            if (cachedTarget != null) {
                if (!cachedTarget.isAlive() || cachedTarget.isRemoved() ||
                        cachedTarget.distanceToSqr(this.worldPosition.getCenter()) > maxRange * maxRange) {
                    invalid = true;
                } else if (lineOfSightTicker++ >= 10) {
                    lineOfSightTicker = 0;
                    currentTickBestPos = getBestTargetPos(cachedTarget);
                    if (currentTickBestPos == null) invalid = true;
                }
            }
            else if (cachedTargetBlock != null) {
                if (cachedTargetBlock.distToCenterSqr(this.worldPosition.getCenter()) > maxRange * maxRange ||
                        !this.level.getBlockState(cachedTargetBlock).is(Blocks.TARGET)) {
                    invalid = true;

                    if (!this.level.getBlockState(cachedTargetBlock).is(Blocks.TARGET)) {
                        SentryTargetSavedData.get(this.level).removeTarget(cachedTargetBlock);
                    }
                    this.cachedTargetBlock = null;
                    syncTargetBlock();

                } else {
                    if (lineOfSightTicker++ >= 20) {
                        lineOfSightTicker = 0;
                        if (!isBlockVisible(getActualMuzzlePos(), cachedTargetBlock)) {
                            invalid = true;
                        }
                    }
                }
            } else {
                if (scanCooldown-- <= 0) {
                    scanCooldown = 20;
                    scanForTarget();
                }
            }

            if (invalid) {
                this.cachedTarget = null;
                if (this.cachedTargetBlock != null) {
                    this.cachedTargetBlock = null;
                    syncTargetBlock();
                }
                setTargetId(-1);
                scanCooldown = 0;
            }
        }


        if (currentTickBestPos == null) {
            if (cachedTarget != null && cachedTarget.isAlive()) {
                currentTickBestPos = getBestTargetPos(cachedTarget);
            } else if (cachedTargetBlock != null) {
                currentTickBestPos = Vec3.atCenterOf(cachedTargetBlock);
            }
        }

        if (currentTickBestPos != null) {

            Vec2 truthAngles = calculateTruthAngle(currentTickBestPos);
            float trueYaw = truthAngles.x;
            float truePitch = truthAngles.y;

            aimAtAngle(trueYaw, truePitch);

            if (!this.level.isClientSide) {
                float currentPhysicalYaw = 180 - baseAngle.getValue();
                float currentPhysicalPitch = headAngle.getValue();

                double absDiff = Math.abs(trueYaw - currentPhysicalYaw) % 360;
                double deviation = Math.min(absDiff, 360 - absDiff);
                float currentUpperArm = upperArmAngle.getValue();
                boolean isDeployed = currentUpperArm > 80f;
                if (deviation < 6.0 && isDeployed) {
                    fireGun(currentPhysicalYaw, -currentPhysicalPitch);
                }
            }
        } else {
            resetAimer();
        }
    }

    private void aimAtAngle(float targetYaw, float targetPitch) {
        float currentYaw = baseAngle.getValue();
        float desiredYaw = -targetYaw + 180;
        float yawDiff = desiredYaw - currentYaw;
        while (yawDiff < -180) yawDiff += 360;
        while (yawDiff > 180) yawDiff -= 360;

        float absYawDiff = Math.abs(yawDiff);
        float yawSpeedBase;
        if (absYawDiff < 0.5f) {
            baseAngle.chase(currentYaw + yawDiff, 1.0f, LerpedFloat.Chaser.EXP);
        }
        else {
 
            if (absYawDiff < 10.0f) {
                yawSpeedBase = 0.8f; 
            } else if (absYawDiff < 45.0f) {
                yawSpeedBase = 0.4f; 
            } else {
                yawSpeedBase = 0.35f; 
            }
            baseAngle.chase(currentYaw + yawDiff, getAnimationSpeed(yawSpeedBase), LerpedFloat.Chaser.EXP);
        }

        float currentPitch = headAngle.getValue();
        float desiredPitch = net.minecraft.util.Mth.clamp(-targetPitch, -90, 90);
        float pitchDiff = desiredPitch - currentPitch;
        float absPitchDiff = Math.abs(pitchDiff);

        if (absPitchDiff < 0.5f) {
 
            headAngle.chase(desiredPitch, 1.0f, LerpedFloat.Chaser.EXP);
        } else {
 
            float pitchSpeedBase;
            if (absPitchDiff < 5.0f) {
                pitchSpeedBase = 0.8f;
            } else {
                pitchSpeedBase = 0.35f;
            }
            headAngle.chase(desiredPitch, getAnimationSpeed(pitchSpeedBase), LerpedFloat.Chaser.EXP);
        }

        upperArmAngle.chase(90f, getAnimationSpeed(0.4f), LerpedFloat.Chaser.EXP);
        this.lowerArmRecoilOffset = net.minecraft.util.Mth.lerp(0.7f, this.lowerArmRecoilOffset, 0f);
        if (Math.abs(this.lowerArmRecoilOffset) < 0.01f) this.lowerArmRecoilOffset = 0f;
        float targetLowerArm = 135f + this.lowerArmRecoilOffset;
        lowerArmAngle.chase(targetLowerArm, 0.6f, LerpedFloat.Chaser.EXP);
    }

    public Vec3 getActualMuzzlePos() {
        FakePlayer fp = SentryFakePlayer.get(this);

        if (fp != null) {
            return fp.getEyePosition();
        }
        return this.worldPosition.getCenter().add(0, 1.5, 0);
    }

    private void resetAimer() {
        sentryIdleScanning();
    }

    public void updateFromFireControl() {
        this.cachedTarget = null;
        this.setTargetId(-1);
        this.scanCooldown = 0; 
    }

    private void scanForTarget() {
 
        double range = this.getSentryRange();
        if (range < 1.0) return;

        boolean isStrictControlMode = false;
        List<String> activeWhitelist = null;

        if (this.connectedFireControlPos != null) {
            if (level.isLoaded(this.connectedFireControlPos)) {
                BlockEntity be = level.getBlockEntity(this.connectedFireControlPos);

 
                if (be instanceof BlazeFireControlBlockEntity fc) {
 
                    if (this.connectedFireControlPos.distSqr(this.worldPosition) > 9.0) {
                        this.disconnectFireControl(); 
                    } else {
                        isStrictControlMode = true;
                        activeWhitelist = fc.getTargetList();
                    }
                } else {
                    this.disconnectFireControl();
                }
            }
        }

        final boolean finalStrict = isStrictControlMode;
        final List<String> finalList = activeWhitelist;

        AABB area = new AABB(this.worldPosition).inflate(range);

        List<LivingEntity> potentialTargets = this.level.getEntitiesOfClass(LivingEntity.class, area, e -> {
            if (!e.isAlive() || e.isSpectator()) return false;

            if (finalStrict) {

                if (finalList == null || finalList.isEmpty()) {
                    return false;
                }
                String name = e.getName().getString(); 
                for (String targetName : finalList) {
                    if (targetName.equals(name)) {
                        return true; 
                    }
                }
                return false; 

            } else {
 

                return (e instanceof Enemy);
            }
        });

        LivingEntity newTarget = potentialTargets.stream()
                .filter(target -> getBestTargetPos(target) != null) 
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(this.worldPosition.getCenter())))
                .orElse(null);

        if (newTarget != null) {
 
            if (this.cachedTarget != newTarget) {
                this.cachedTarget = newTarget;
                setTargetId(newTarget.getId());
            }
 
            this.cachedTargetBlock = null;
            return;
        } else {
 
            if (this.cachedTarget != null) {
                this.cachedTarget = null;
                setTargetId(-1);
            }
        }

        if (!this.level.isClientSide) {
            Set<BlockPos> targets = SentryTargetSavedData.get(this.level).getTargets();



            BlockPos bestBlock = null;
            double minDstSqr = range * range;
            Vec3 muzzle = this.getActualMuzzlePos(); 
            Vec3 center = this.worldPosition.getCenter();

            for (BlockPos pos : targets) {
 
                double dstSqr = center.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                if (dstSqr > minDstSqr) continue;

                if (isBlockVisible(muzzle, pos)) {
                    minDstSqr = dstSqr;
                    bestBlock = pos;
                }
            }

            if (!java.util.Objects.equals(this.cachedTargetBlock, bestBlock)) {
                this.cachedTargetBlock = bestBlock;
                syncTargetBlock();
            }

            this.cachedTargetBlock = bestBlock;

        }
    }


    private void setTargetId(int id) {
        if (this.syncedTargetId == id) return;

        this.syncedTargetId = id;
        this.setChanged();
        this.sendData();
    }

    private Vec3 getBestTargetPos(LivingEntity target) {

        Vec3 armPos = this.getActualMuzzlePos();

        float height = target.getBbHeight();
        // Entity position is already in world coordinates
        Vec3 basePos = target.position();

        // Try different height positions from best to worst
        Vec3 headPos = basePos.add(0, height * 0.90, 0);
        if (isPointVisible(armPos, headPos)) return headPos;

        Vec3 centerPos = basePos.add(0, height * 0.6, 0);
        if (isPointVisible(armPos, centerPos)) return centerPos;

        Vec3 legPos = basePos.add(0, height * 0.25, 0);
        if (isPointVisible(armPos, legPos)) return legPos;


        Vec3 feetPos = basePos.add(0, height * 0.1, 0);
        if (isPointVisible(armPos, feetPos)) return feetPos;


        return null;
    }

    private boolean isPointVisible(Vec3 start, Vec3 end) {
        BlockHitResult result = this.level.clip(new ClipContext(
                start, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null
        ));
 
        return result.getType() == HitResult.Type.MISS;
    }

    private float getAnimationSpeed(float baseChaserSpeed) {
        float currentRpm = Math.abs(this.getSpeed());
        float multiplier = Mth.map(currentRpm, 1.0f, 256.0f, 0.01f, 1.0f);

        multiplier = Mth.clamp(multiplier, 0.01f, 1.0f);

        return baseChaserSpeed * multiplier;
    }


    public void disconnectFireControl() {
        this.connectedFireControlPos = null;
        this.cachedTarget = null;
        this.setTargetId(-1);

 
        this.setChanged();
        this.syncTargetBlock();
    }

    private void fireGun(float targetYaw, float targetPitch) {
        if (heldItem.isEmpty()) return;
        if (this.level.isClientSide) return;

        IGun iGun = (IGun) heldItem.getItem();
        ResourceLocation gunId = iGun.getGunId(heldItem);
        Optional<CommonGunIndex> indexOpt = TimelessAPI.getCommonGunIndex(gunId);
        if (indexOpt.isEmpty()) return;
        GunData gunData = indexOpt.get().getGunData();
        float currentRPM = gunData.getRoundsPerMinute(FireMode.AUTO);
        if (gunData.hasHeatData()) {
            float rpmMultiplier = iGun.lerpRPM(heldItem);
            currentRPM *= rpmMultiplier;
        }
        Bolt boltType = gunData.getBolt();
        if (boltType == Bolt.MANUAL_ACTION) {
            currentRPM /= 5.0f;
        }

        if (currentRPM <= 0) currentRPM = 1;
        float ticksPerShot = 1200.0f / currentRPM;
        this.shootDelayAccumulator -= 1.0f;
        if (this.shootDelayAccumulator > 0) {
            return;
        }

        int currentGunAmmo = iGun.getCurrentAmmoCount(heldItem);
        boolean hasAmmo = false;
        ItemStack ammoSource = ItemStack.EMPTY;

        if (currentGunAmmo > 0) {
            hasAmmo = true;
        } else {
            for (ItemStack box : attachedAmmoBoxes) {
                if (!box.isEmpty() && box.getItem() instanceof IAmmoBox iBox) {
                    if (iBox.isAmmoBoxOfGun(heldItem, box)) {
                        if (iBox.isCreative(box) || iBox.isAllTypeCreative(box) || iBox.getAmmoCount(box) > 0) {
                            hasAmmo = true;
                            ammoSource = box;
                            break;
                        }
                    }
                }
            }
        }

        if (!hasAmmo) {
 
            if (this.shootDelayAccumulator < 0) this.shootDelayAccumulator = 0;
            return;
        }

        int maxShotsPerTick = 10;
        int shotsFired = 0;

        while (this.shootDelayAccumulator <= 0 && shotsFired < maxShotsPerTick) {
            boolean success = performSingleShot(targetYaw, targetPitch, iGun, gunData, currentGunAmmo, ammoSource);
            if (success) {
                this.shootDelayAccumulator += ticksPerShot;
                shotsFired++;
                currentGunAmmo = iGun.getCurrentAmmoCount(heldItem);
 
                if (currentGunAmmo <= 0 && !canFindNextAmmo(heldItem)) {
                    break;
                }
            } else {
 
                this.shootDelayAccumulator = Math.max(0, this.shootDelayAccumulator + ticksPerShot);
                break;
            }
        }
    }

    private boolean canFindNextAmmo(ItemStack gunStack) {
        for (ItemStack box : attachedAmmoBoxes) {
            if (!box.isEmpty() && box.getItem() instanceof IAmmoBox iBox) {
                if (iBox.isAmmoBoxOfGun(gunStack, box)) {
                    if (iBox.isCreative(box) || iBox.getAmmoCount(box) > 0) return true;
                }
            }
        }
        return false;
    }

    private boolean performSingleShot(float targetYaw, float targetPitch, IGun iGun, GunData gunData, int currentGunAmmo, ItemStack ammoSource) {
        FakePlayer fakePlayer = SentryFakePlayer.get(this);
        if (fakePlayer == null) return false;
        SentryFakePlayer.sync(fakePlayer, this, targetYaw, targetPitch, heldItem);
        fakePlayer.setGameMode(GameType.CREATIVE);
        IGunOperator operator = IGunOperator.fromLivingEntity(fakePlayer);
        AttachmentCacheProperty cache = operator.getCacheProperty();
        if (cache != null) {
            double distToTarget = 0.0;
            if (this.cachedTarget != null) {
                distToTarget = Math.sqrt(this.cachedTarget.distanceToSqr(this.worldPosition.getCenter()));
            } else if (this.cachedTargetBlock != null) {
                distToTarget = Math.sqrt(this.cachedTargetBlock.distToCenterSqr(this.worldPosition.getCenter()));
            }

            float effectiveRange = calculateEffectiveRange(gunData);

            float targetSpread;

            if (distToTarget <= effectiveRange) {
                targetSpread = 0.0f;
            } else {
                double excessDistance = distToTarget - effectiveRange;

                targetSpread = (float) (excessDistance * 0.02);

                targetSpread = Math.min(targetSpread, 5.0f);
            }

            String inaccuracyId = GunProperties.INACCURACY.name();
            @SuppressWarnings("unchecked")
            Map<InaccuracyType, Float> inaccuracyMap = (Map<InaccuracyType, Float>) cache.getCache(inaccuracyId);

            if (inaccuracyMap != null) {
                inaccuracyMap.put(InaccuracyType.AIM, targetSpread);
                inaccuracyMap.put(InaccuracyType.STAND, targetSpread);
            }
        }

        operator.getDataHolder().isAiming = true;
        operator.getDataHolder().aimingProgress = 1.0f;

 
        boolean usedCheatAmmo = false;
        ItemStack fakeHeldItem = fakePlayer.getMainHandItem();
        IGun iGunFake = IGun.getIGunOrNull(fakeHeldItem);

        if (currentGunAmmo <= 0 && !ammoSource.isEmpty() && iGunFake != null) {
            ResourceLocation ammoId = gunData.getAmmoId();
            net.minecraft.world.item.Item ammoItem = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(ammoId);
            if (ammoItem != null) {
                fakePlayer.getInventory().add(new ItemStack(ammoItem, 64));
            }
            iGunFake.setCurrentAmmoCount(fakeHeldItem, 1);
            usedCheatAmmo = true;
        }

        this.lastShootTime = System.currentTimeMillis();
        ShootResult result = operator.shoot(() -> targetPitch, () -> targetYaw);

        if (result == ShootResult.NEED_BOLT) {
            operator.bolt();
            return false; 
        }

        if (result == ShootResult.SUCCESS) {
 
            int slotToSync = -2;
            ItemStack stackToSync = ItemStack.EMPTY;
            if (usedCheatAmmo) {
                iGun.setCurrentAmmoCount(heldItem, 0);
                IAmmoBox iBox = (IAmmoBox) ammoSource.getItem();
                if (!iBox.isCreative(ammoSource) && !iBox.isAllTypeCreative(ammoSource)) {
                    iBox.setAmmoCount(ammoSource, iBox.getAmmoCount(ammoSource) - 1);
                    stackToSync = ammoSource;
                    for (int i = 0; i < attachedAmmoBoxes.size(); i++) {
                        if (attachedAmmoBoxes.get(i) == ammoSource) { slotToSync = i; break; }
                    }
                }
            } else {
                int newAmmoCount = Math.max(0, currentGunAmmo - 1);
                iGun.setCurrentAmmoCount(heldItem, newAmmoCount);
                slotToSync = -1;
                stackToSync = heldItem;
            }

            if (usedCheatAmmo) {
                fakePlayer.getInventory().clearContent();
            }

 
            Vec3 realStart = fakePlayer.getEyePosition();
            Vec3 lookVec = fakePlayer.getViewVector(1.0F);
            Vec3 traceEnd = realStart.add(lookVec.scale(100));
            net.minecraft.world.phys.BlockHitResult hitResult = this.level.clip(new net.minecraft.world.level.ClipContext(
                    realStart, traceEnd,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE,
                    fakePlayer
            ));
            Vec3 realEnd = hitResult.getLocation();

            NetworkHandler.sendToNearby(
                    new SentryShootPacket(this.worldPosition, slotToSync,
                            stackToSync.isEmpty() ? new CompoundTag() : stackToSync.getOrCreateTag(),
                            realStart, realEnd),
                    this.level, this.worldPosition
            );
            return true;
        } else {
            if (usedCheatAmmo) {
                iGun.setCurrentAmmoCount(heldItem, 0);
                fakePlayer.getInventory().clearContent();
            }
            return false;
        }
    }

    private float calculateEffectiveRange(GunData gunData) {
        BulletData bulletData = gunData.getBulletData();
        if (bulletData == null) return 32.0f;

        float effectiveRange = -1.0f;

 
        ExtraDamage extraDamage = bulletData.getExtraDamage();
        if (extraDamage != null) {
            LinkedList<ExtraDamage.DistanceDamagePair> damageAdjust = extraDamage.getDamageAdjust();
            if (damageAdjust != null && !damageAdjust.isEmpty()) {
 
                effectiveRange = damageAdjust.get(0).getDistance();
            }
        }
 
        if (effectiveRange <= 0) {
            float speed = bulletData.getSpeed();
 
 
            effectiveRange = (speed > 0 ? speed : 10.0f) * 12.0f;
        }

        return effectiveRange;
    }

    private void popAmmoBox() {
        for (int i = 0; i < attachedAmmoBoxes.size(); i++) {
            ItemStack stack = attachedAmmoBoxes.get(i);
            if (!stack.isEmpty()) {
                Block.popResource(this.level, this.worldPosition, stack);
                attachedAmmoBoxes.set(i, ItemStack.EMPTY);
            }
        }
        this.setChanged();
        this.sendData();
    }
 
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
 
        return ClientboundBlockEntityDataPacket.create(this);
    }
 
    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt) {
 
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            this.read(tag, true); 
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        boolean superResult = super.addToGoggleTooltip(tooltip, isPlayerSneaking); 

        if (heldItem.isEmpty() || !(heldItem.getItem() instanceof IGun)) {
            return superResult;
        }

        var player = Minecraft.getInstance().player;
        if (player == null) return superResult;

        addSentryGunTooltip(tooltip, heldItem);
        return true;
    }

    private void addSentryGunTooltip(List<Component> tooltip, ItemStack heldItem) {
        IGun iGun = (IGun) heldItem.getItem();
        Component indent = Component.literal("    ");

        tooltip.add(indent.copy().append(Component.translatable("sentry.tooltip.firepower").withStyle(ChatFormatting.GRAY)));

        int currentAmmo = iGun.getCurrentAmmoCount(heldItem);
        int totalAmmo = currentAmmo;
        boolean isInfinite = false;

        for (ItemStack box : attachedAmmoBoxes) {
            if (!box.isEmpty() && box.getItem() instanceof IAmmoBox iBox) {
                if (iBox.isCreative(box) || iBox.isAllTypeCreative(box)) {
                    isInfinite = true;
                } else {
                    totalAmmo += iBox.getAmmoCount(box);
                }
            }
        }

        if (isInfinite) {
            tooltip.add(indent.copy().append(Component.translatable("sentry.tooltip.ammo")
                    .withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(" ∞")
                            .withStyle(ChatFormatting.AQUA))));
        } else {
            tooltip.add(indent.copy().append(Component.translatable("sentry.tooltip.ammo")
                    .withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(" " + totalAmmo)
                            .withStyle(ChatFormatting.AQUA))));
        }

        tooltip.add(indent.copy().append(Component.translatable("sentry.tooltip.gun")
                .withStyle(ChatFormatting.GOLD)
                .append(heldItem.getHoverName().copy()
                        .withStyle(ChatFormatting.WHITE))));

        double range = this.getSentryRange();
        String rangeStr = String.format("%.1f", range);
        tooltip.add(indent.copy().append(Component.translatable("sentry.tooltip.range")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(rangeStr)
                        .withStyle(ChatFormatting.GREEN))));
    }

    public void triggerShootEffects() {
        this.lastShootTime = System.currentTimeMillis();
        this.shouldEjectShell = true;
        this.lowerArmRecoilOffset += 8.0f;
        if (this.lowerArmRecoilOffset > 18.0f) {
            this.lowerArmRecoilOffset = 18.0f;
        }
        float currentHead = headAngle.getValue();
        float targetHead = headAngle.getChaseTarget();
 
        if (currentHead > targetHead - 2.0f) { 
            headAngle.setValue(currentHead - 0.5f);
        }

        ItemStack gun = getHeldItem();
        if (!gun.isEmpty() && gun.getItem() instanceof IGun iGun) {
            Optional<CommonGunIndex> indexOpt = TimelessAPI.getCommonGunIndex(iGun.getGunId(gun));
            indexOpt.ifPresent(index -> {
 
                Vec3 center = this.worldPosition.getCenter();
                ArmSoundHelper.playFireEffects(
                        null, this.level, center, new Vec3(0,0,0), 0, gun, index.getGunData()
                );
            });
        }
    }

    public void updateAmmoFromPacket(int slotIndex, CompoundTag newTag) {
        if (slotIndex == -1) {
 
            if (!heldItem.isEmpty()) {
                heldItem.setTag(newTag);
            }
        } else if (slotIndex >= 0 && slotIndex < attachedAmmoBoxes.size()) {
 
            ItemStack box = attachedAmmoBoxes.get(slotIndex);
            if (!box.isEmpty()) {
                box.setTag(newTag);
            }
        }
    }

    private boolean isBlockVisible(Vec3 start, BlockPos targetPos) {
        Vec3 end = Vec3.atCenterOf(targetPos);

        BlockHitResult result = this.level.clip(new net.minecraft.world.level.ClipContext(
                start, end,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                SentryFakePlayer.get(this)
        ));

 
        if (result.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
            return true;
        }

        if (result.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            BlockPos hitPos = result.getBlockPos();
 
            if (hitPos.equals(targetPos)) {
                return true;
            }
        }

        return false;
    }

    private void updateClientTarget() {
 
        if (syncedTargetId == -1) {
            this.cachedTarget = null;
            return;
        }

 
        if (this.cachedTarget != null && this.cachedTarget.getId() == syncedTargetId) {
            if (!this.cachedTarget.isAlive() || this.cachedTarget.isRemoved()) {
                this.cachedTarget = null; 
            }
            return;
        }

        net.minecraft.world.entity.Entity entity = this.level.getEntity(syncedTargetId);
        if (entity instanceof LivingEntity living) {
            this.cachedTarget = living;
        } else {
 
            this.cachedTarget = null;
        }
    }

    private void sentryIdleScanning() {
 
        if (!this.level.isClientSide) {
            if (idleScanTimer-- <= 0) {
 
                this.idleTargetYaw = this.level.random.nextFloat() * 1800f;
                this.idleTargetPitch = (this.level.random.nextFloat() * 30f) - 15f;
 
                this.idleScanTimer = 80 + this.level.random.nextInt(60);

 
                this.sendData();
            }
        }

        float animSpeed = getAnimationSpeed(0.1f);
 
        float currentBase = baseAngle.getValue();
        float diffYaw = idleTargetYaw - currentBase;
        while (diffYaw < -180) diffYaw += 360;
        while (diffYaw > 180) diffYaw -= 360;

        baseAngle.chase(currentBase + diffYaw, animSpeed, LerpedFloat.Chaser.EXP);
        headAngle.chase(idleTargetPitch, animSpeed, LerpedFloat.Chaser.EXP);
        lowerArmAngle.chase(135f, animSpeed, LerpedFloat.Chaser.EXP);
        upperArmAngle.chase(90f, animSpeed, LerpedFloat.Chaser.EXP);
    }
    private Vec2 calculateTruthAngle(Vec3 targetPos) {
        // Get arm position in world coordinates
        Vec3 armPosWorld;
        FakePlayer fp = SentryFakePlayer.get(this);

        if (fp != null) {
            // FakePlayer position is already in world coordinates (set by SentryFakePlayer.sync)
            // Use it directly without any transformation
            armPosWorld = fp.getEyePosition();
        } else {
            // No FakePlayer, calculate position from BlockPos
            double originX = this.worldPosition.getX() + 0.5;
            double originY = this.worldPosition.getY() + 2.62;
            double originZ = this.worldPosition.getZ() + 0.5;

            // Valkyrien Skies integration: If arm is on a ship, transform from ship space to world space
            LoadedShip armShip = VSGameUtilsKt.getShipObjectManagingPos(level, this.worldPosition);
            if (armShip != null) {
                // Arm is on a ship, transform from ship space to world space
                armPosWorld = VectorConversionsMCKt.toMinecraft(
                    armShip.getTransform().getShipToWorld().transformPosition(
                        VectorConversionsMCKt.toJOML(new Vec3(originX, originY, originZ))
                    )
                );
            } else {
                // Arm is not on a ship, use position as-is
                armPosWorld = new Vec3(originX, originY, originZ);
            }
        }

        // Target position is already in world coordinates
        // Calculate angle in world space
        double diffX = targetPos.x - armPosWorld.x;
        double diffY = targetPos.y - armPosWorld.y;
        double diffZ = targetPos.z - armPosWorld.z;

        float yaw = (float) (Mth.atan2(diffZ, diffX) * (180D / Math.PI)) - 90.0F;
        double distHorizontal = Math.sqrt(diffX * diffX + diffZ * diffZ);
        float pitch = (float) -(Mth.atan2(diffY, distHorizontal) * (180D / Math.PI));

        return new Vec2(yaw, pitch);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag); 
        return tag;
    }

    private void syncTargetBlock() {
        this.setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

 
    public void setConnectedFireControl(BlockPos pos) {
        this.connectedFireControlPos = pos;
        this.setChanged();
        this.syncTargetBlock(); 
    }

    public BlockPos getConnectedFireControl() {
        return connectedFireControlPos;
    }


    @Override
    protected void read(CompoundTag compound, boolean clientPacket) {
        super.read(compound, clientPacket);

 
        if (compound.contains("SentryHeldItem")) {
            heldItem = ItemStack.of(compound.getCompound("SentryHeldItem"));
        } else {
            heldItem = ItemStack.EMPTY;
        }

        for (int i = 0; i < attachedAmmoBoxes.size(); i++) {
            attachedAmmoBoxes.set(i, ItemStack.EMPTY);
        }
        if (compound.contains("SentryAmmoBoxes")) {
            ContainerHelper.loadAllItems(compound.getCompound("SentryAmmoBoxes"), this.attachedAmmoBoxes);
        }

        if (compound.contains("TargetId")) {
            this.syncedTargetId = compound.getInt("TargetId");
        }

        if (compound.contains("Angles")) {
            CompoundTag angles = compound.getCompound("Angles");

            if (!clientPacket) {
 
                baseAngle.setValue(angles.getFloat("Base"));
                lowerArmAngle.setValue(angles.getFloat("Lower"));
                upperArmAngle.setValue(angles.getFloat("Upper"));
                headAngle.setValue(angles.getFloat("Head"));
            } else {

                boolean isCombatMode = (this.syncedTargetId != -1);

                if (!isCombatMode) {
                    float serverBase = angles.getFloat("Base");
                    if (Math.abs(baseAngle.getValue() - serverBase) > 10f) {
                        baseAngle.setValue(serverBase); 
                    }
                }
            }
        }

        if (compound.contains("TargetBlock")) {
            this.cachedTargetBlock = NbtUtils.readBlockPos(compound.getCompound("TargetBlock"));
        } else {
            this.cachedTargetBlock = null;
        }
        if (compound.contains("FireControlPos")) {
            this.connectedFireControlPos = NbtUtils.readBlockPos(compound.getCompound("FireControlPos"));
        } else {
 
 
            this.connectedFireControlPos = null;
        }

        this.idleTargetYaw = compound.getFloat("IdleTargetYaw");
        this.idleTargetPitch = compound.getFloat("IdleTargetPitch");
        this.idleScanTimer = compound.getInt("IdleScanTimer");
    }


    @Override
    protected void write(CompoundTag compound, boolean clientPacket) {
        super.write(compound, clientPacket);

        if (!heldItem.isEmpty()) {
            compound.put("SentryHeldItem", heldItem.save(new CompoundTag()));
        }

        CompoundTag ammoTag = new CompoundTag();
        ContainerHelper.saveAllItems(ammoTag, this.attachedAmmoBoxes);
        compound.put("SentryAmmoBoxes", ammoTag);
        compound.putInt("TargetId", this.syncedTargetId);

        CompoundTag angles = new CompoundTag();
        angles.putFloat("Base", baseAngle.getValue());
        angles.putFloat("Lower", lowerArmAngle.getValue());
        angles.putFloat("Upper", upperArmAngle.getValue());
        angles.putFloat("Head", headAngle.getValue());
        compound.put("Angles", angles);
        compound.putFloat("IdleTargetYaw", idleTargetYaw);
        compound.putFloat("IdleTargetPitch", idleTargetPitch);
        compound.putInt("IdleScanTimer", idleScanTimer);
        if (this.connectedFireControlPos != null) {
            compound.put("FireControlPos", NbtUtils.writeBlockPos(this.connectedFireControlPos));
        }

        if (this.cachedTargetBlock != null) {
            compound.put("TargetBlock", NbtUtils.writeBlockPos(this.cachedTargetBlock));
        }
    }

    @Override
    public AABB getRenderBoundingBox() {
        return super.getRenderBoundingBox().inflate(2.0);
    }

    @Override
    public <T> net.minecraftforge.common.util.LazyOptional<T> getCapability(net.minecraftforge.common.capabilities.Capability<T> cap, @org.jetbrains.annotations.Nullable net.minecraft.core.Direction side) {
        if (cap == net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER) {
            return net.minecraftforge.common.util.LazyOptional.of(() -> smartItemHandler).cast();
        }
        return super.getCapability(cap, side);
    }

    private class SentryItemHandler implements net.minecraftforge.items.IItemHandlerModifiable {

        @Override
        public int getSlots() {
            return attachedAmmoBoxes.size(); 
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot < 0 || slot >= getSlots()) return ItemStack.EMPTY;
            return attachedAmmoBoxes.get(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {

            if (!(stack.getItem() instanceof IAmmoBox iBox)) {
                return stack;
            }

            if (!attachedAmmoBoxes.get(slot).isEmpty()) {
                return stack;
            }

            ItemStack gun = getHeldItem();
            if (gun.isEmpty() || !(gun.getItem() instanceof IGun)) {
                return stack;
            }
            if (!iBox.isAmmoBoxOfGun(gun, stack)) {
                return stack;
            }

            if (!simulate) {
                ItemStack copy = stack.copy();
                copy.setCount(1);
                attachedAmmoBoxes.set(slot, copy);
                setChanged();
                sendData();
            }

            ItemStack remainder = stack.copy();
            remainder.shrink(1);
            return remainder;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {

            ItemStack inSlot = getStackInSlot(slot);
            if (inSlot.isEmpty()) return ItemStack.EMPTY;

 
            if (inSlot.getItem() instanceof IAmmoBox iBox) {
 
                if (iBox.isCreative(inSlot) || iBox.getAmmoCount(inSlot) > 0) {
                    return ItemStack.EMPTY; 
                }
            } else {
 
            }
            int extractCount = Math.min(inSlot.getCount(), amount);
            if (extractCount <= 0) return ItemStack.EMPTY;

            ItemStack extracted = inSlot.copy();
            extracted.setCount(extractCount);

            if (!simulate) {
                inSlot.shrink(extractCount);
                if (inSlot.isEmpty()) {
                    attachedAmmoBoxes.set(slot, ItemStack.EMPTY);
                }

                setChanged();
                sendData();
            }

            return extracted;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
 
            return stack.getItem() instanceof IAmmoBox;
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
 
            if (slot >= 0 && slot < attachedAmmoBoxes.size()) {
                attachedAmmoBoxes.set(slot, stack);
                setChanged();
                sendData();
            }
        }
    }

}