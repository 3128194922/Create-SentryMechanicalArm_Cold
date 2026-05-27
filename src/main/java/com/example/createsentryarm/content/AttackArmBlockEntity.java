package com.example.createsentryarm.content;

import com.example.createsentryarm.CreateSentryArmMod;
import com.example.createsentryarm.util.ArmFakePlayer;
import com.simibubi.create.api.equipment.potatoCannon.PotatoCannonProjectileType;
import com.simibubi.create.content.equipment.potatoCannon.PotatoProjectileEntity;
import com.simibubi.create.content.equipment.potatoCannon.PotatoCannonItem;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.LingeringPotionItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.SplashPotionItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public class AttackArmBlockEntity extends KineticBlockEntity {
    public LerpedFloat baseAngle = LerpedFloat.angular().startWithValue(0);
    public LerpedFloat lowerArmAngle = LerpedFloat.angular().startWithValue(135);
    public LerpedFloat upperArmAngle = LerpedFloat.angular().startWithValue(45);
    public LerpedFloat headAngle = LerpedFloat.angular().startWithValue(0);

    private ItemStack heldItem = ItemStack.EMPTY;
    private int attackCooldown;
    private int syncedTargetId = -1;
    private BlockPos connectedFireControlPos;
    public ScrollValueBehaviour rangeScroll;
    private final IItemHandler weaponHandler = new IItemHandler() {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot == 0 ? heldItem : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot != 0 || stack.isEmpty() || !AttackArmBlock.isSupportedWeapon(stack) || !heldItem.isEmpty()) {
                return stack;
            }
            ItemStack remainder = stack.copy();
            ItemStack inserted = remainder.split(1);
            if (!simulate) {
                setHeldItem(inserted);
            }
            return remainder;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != 0 || amount <= 0 || heldItem.isEmpty()) {
                return ItemStack.EMPTY;
            }
            int extractedAmount = Math.min(amount, heldItem.getCount());
            ItemStack extracted = ItemHandlerHelper.copyStackWithSize(heldItem, extractedAmount);
            if (!simulate) {
                ItemStack remainder = heldItem.copy();
                remainder.shrink(extractedAmount);
                setHeldItem(remainder);
            }
            return extracted;
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == 0 ? 1 : 0;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 && AttackArmBlock.isSupportedWeapon(stack);
        }
    };
    private LazyOptional<IItemHandler> weaponHandlerCap = LazyOptional.of(() -> weaponHandler);

    public AttackArmBlockEntity(BlockPos pos, BlockState state) {
        super(CreateSentryArmMod.ATTACK_ARM_BE.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        rangeScroll = new ScrollValueBehaviour(net.minecraft.network.chat.Component.literal("Range"), this, new RangeValueBox());
        rangeScroll.between(4, 64);
        rangeScroll.setValue(24);
        rangeScroll.withCallback(v -> syncedTargetId = -1);
        behaviours.add(rangeScroll);
    }

    public ItemStack getHeldItem() {
        return heldItem;
    }

    public void setHeldItem(ItemStack stack) {
        heldItem = stack.copy();
        if (!heldItem.isEmpty()) {
            heldItem.setCount(1);
        }
        setChanged();
        sendData();
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        weaponHandlerCap.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        weaponHandlerCap = LazyOptional.of(() -> weaponHandler);
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return weaponHandlerCap.cast();
        }
        return super.getCapability(cap, side);
    }

    public void setConnectedFireControl(BlockPos pos) {
        connectedFireControlPos = pos;
        setChanged();
        sendData();
    }

    public @Nullable BlockPos getConnectedFireControl() {
        return connectedFireControlPos;
    }

    public void disconnectFireControl() {
        connectedFireControlPos = null;
        setChanged();
        sendData();
    }

    @Override
    public void tick() {
        super.tick();
        baseAngle.tickChaser();
        lowerArmAngle.tickChaser();
        upperArmAngle.tickChaser();
        headAngle.tickChaser();

        if (level == null) {
            return;
        }
        if (attackCooldown > 0) {
            attackCooldown--;
        }
        if (Math.abs(getSpeed()) < 1 || heldItem.isEmpty()) {
            idlePose();
            return;
        }

        TargetFilter filter = getTargetFilter();
        Vec3 muzzlePos = getMuzzlePos();
        LivingEntity target = findTarget(level, muzzlePos, getAttackRange(), filter, null);
        if (target == null) {
            syncedTargetId = -1;
            idlePose();
            return;
        }
        syncedTargetId = target.getId();
        Vec3 targetPos = getBestTargetPos(level, muzzlePos, target);
        if (targetPos == null) {
            idlePose();
            return;
        }
        float[] yawPitch = getYawPitch(muzzlePos, targetPos);
        aimAtAngle(yawPitch[0], yawPitch[1]);

        if (!level.isClientSide && attackCooldown <= 0) {
            attackCooldown = performAttack((ServerLevel) level, worldPosition, muzzlePos, heldItem, target, yawPitch[0], yawPitch[1], getBelowHandler((ServerLevel) level, worldPosition));
            setChanged();
            sendData();
        }
    }

    protected void idlePose() {
        baseAngle.chase(baseAngle.getValue(), 0.05f, LerpedFloat.Chaser.EXP);
        headAngle.chase(0, 0.08f, LerpedFloat.Chaser.EXP);
        lowerArmAngle.chase(135, 0.08f, LerpedFloat.Chaser.EXP);
        upperArmAngle.chase(45, 0.08f, LerpedFloat.Chaser.EXP);
    }

    protected double getAttackRange() {
        return Mth.clamp(Math.floor(Math.abs(getSpeed())/2.0), 4, 64);
    }

    protected Vec3 getMuzzlePos() {
        boolean ceiling = getBlockState().getValue(AttackArmBlock.CEILING);
        return worldPosition.getCenter().add(0, ceiling ? -1.1 : 1.45, 0);
    }

    protected TargetFilter getTargetFilter() {
        if (connectedFireControlPos == null || level == null) {
            return TargetFilter.DEFAULT_ALL;
        }
        BlockEntity be = level.getBlockEntity(connectedFireControlPos);
        if (!(be instanceof BlazeFireControlBlockEntity fireControl)) {
            disconnectFireControl();
            return TargetFilter.DEFAULT_ALL;
        }
        if (connectedFireControlPos.distSqr(worldPosition) > 36.0) {
            disconnectFireControl();
            return TargetFilter.DEFAULT_ALL;
        }
        if (fireControl.inventory.getStackInSlot(0).isEmpty()) {
            return TargetFilter.DEFAULT_ALL;
        }
        return new TargetFilter(true, fireControl.isWhitelist(), fireControl.getTargetList());
    }

    public static LivingEntity findTarget(net.minecraft.world.level.Level level, Vec3 muzzlePos, double range, TargetFilter filter, @Nullable Entity excluded) {
        AABB box = new AABB(muzzlePos, muzzlePos).inflate(range);
        return level.getEntitiesOfClass(LivingEntity.class, box, entity -> isValidTarget(level, muzzlePos, entity, filter, excluded)).stream()
                .min(Comparator.comparingDouble(entity -> entity.distanceToSqr(muzzlePos)))
                .orElse(null);
    }

    public static boolean isValidTarget(net.minecraft.world.level.Level level, Vec3 muzzlePos, LivingEntity entity, TargetFilter filter, @Nullable Entity excluded) {
        if (!entity.isAlive() || entity.isSpectator()) {
            return false;
        }
        if (entity == excluded) {
            return false;
        }
        if (entity instanceof Player player && player.getAbilities().invulnerable) {
            return false;
        }
        Vec3 hitPos = getBestTargetPos(level, muzzlePos, entity);
        if (hitPos == null) {
            return false;
        }
        if (!filter.strict) {
            return true;
        }
        ResourceLocation typeId = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        String name = entity.getName().getString();
        boolean listed = filter.targets.contains(name) || (typeId != null && filter.targets.contains(typeId.toString()));
        return filter.whitelist ? listed : !listed;
    }

    public static Vec3 getBestTargetPos(net.minecraft.world.level.Level level, Vec3 muzzlePos, LivingEntity target) {
        Vec3 base = target.position();
        float height = target.getBbHeight();
        Vec3[] points = new Vec3[] {
                base.add(0, height * 0.9, 0),
                base.add(0, height * 0.6, 0),
                base.add(0, height * 0.3, 0)
        };
        for (Vec3 point : points) {
            if (isVisible(level, muzzlePos, point)) {
                return point;
            }
        }
        return null;
    }

    private static boolean isVisible(net.minecraft.world.level.Level level, Vec3 start, Vec3 end) {
        BlockHitResult result = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null));
        return result.getType() == HitResult.Type.MISS;
    }

    public static float[] getYawPitch(Vec3 from, Vec3 to) {
        Vec3 delta = to.subtract(from);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) Math.toDegrees(Mth.atan2(delta.z, delta.x)) - 90f;
        float pitch = (float) -Math.toDegrees(Mth.atan2(delta.y, horizontal));
        return new float[] {yaw, pitch};
    }

    protected void aimAtAngle(float targetYaw, float targetPitch) {
        if (getBlockState().getValue(AttackArmBlock.CEILING)) {
            targetPitch = -targetPitch;
            targetYaw = -targetYaw + 180;
        }
        float currentYaw = baseAngle.getValue();
        float desiredYaw = -targetYaw + 180;
        float yawDiff = desiredYaw - currentYaw;
        while (yawDiff < -180) yawDiff += 360;
        while (yawDiff > 180) yawDiff -= 360;
        baseAngle.chase(currentYaw + yawDiff, 0.35f, LerpedFloat.Chaser.EXP);

        float desiredPitch = Mth.clamp(-targetPitch, -90, 90);
        headAngle.chase(desiredPitch, 0.35f, LerpedFloat.Chaser.EXP);
        upperArmAngle.chase(90, 0.3f, LerpedFloat.Chaser.EXP);
        lowerArmAngle.chase(135, 0.3f, LerpedFloat.Chaser.EXP);
    }

    public static int performAttack(ServerLevel level, BlockPos pos, Vec3 muzzlePos, ItemStack weapon, LivingEntity target, float yaw, float pitch, @Nullable IItemHandler ammoHandler) {
        if (weapon.getItem() instanceof BowItem) {
            return fireBow(level, pos, muzzlePos, weapon, target, ammoHandler);
        }
        if (weapon.getItem() instanceof CrossbowItem) {
            return fireCrossbow(level, pos, muzzlePos, weapon, target, ammoHandler);
        }
        if (weapon.getItem() instanceof SplashPotionItem || weapon.getItem() instanceof LingeringPotionItem) {
            return throwPotion(level, pos, muzzlePos, weapon, target, ammoHandler);
        }
        if (weapon.getItem() instanceof PotatoCannonItem) {
            return firePotatoCannon(level, pos, muzzlePos, weapon, yaw, pitch, ammoHandler);
        }
        return 10;
    }

    private static int fireBow(ServerLevel level, BlockPos pos, Vec3 muzzlePos, ItemStack weapon, LivingEntity target, @Nullable IItemHandler ammoHandler) {
        ItemStack ammo = extractFromHandler(ammoHandler, stack -> stack.getItem() instanceof ArrowItem);
        if (ammo.isEmpty()) {
            return 20;
        }
        Vec3 baseAim = target.position().subtract(muzzlePos);
        float yaw = yawFromMotion(baseAim);
        float pitch = pitchFromMotion(baseAim);
        var fakePlayer = ArmFakePlayer.sync(level, pos, muzzlePos, yaw, pitch, weapon);
        AbstractArrow arrow = net.minecraft.world.entity.projectile.ProjectileUtil.getMobArrow(fakePlayer, ammo, 1.0f);
        arrow.setPos(muzzlePos.x, muzzlePos.y, muzzlePos.z);
        double targetX = target.getX() - muzzlePos.x;
        double targetY = target.getY(0.3333333333333333D) - arrow.getY();
        double targetZ = target.getZ() - muzzlePos.z;
        double targetRadius = Math.sqrt(targetX * targetX + targetZ * targetZ);
        arrow.shoot(targetX, targetY + targetRadius * 0.2F, targetZ, 1.6F, 14.0F - level.getDifficulty().getId() * 4);
        int power = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.POWER_ARROWS, weapon);
        if (power > 0) {
            arrow.setBaseDamage(arrow.getBaseDamage() + power * 0.5 + 0.5);
        }
        int punch = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.PUNCH_ARROWS, weapon);
        if (punch > 0) {
            arrow.setKnockback(punch);
        }
        if (EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FLAMING_ARROWS, weapon) > 0) {
            arrow.setSecondsOnFire(5);
        }
        level.addFreshEntity(arrow);
        weapon.hurtAndBreak(1, fakePlayer, p -> {});
        level.playSound(null, muzzlePos.x, muzzlePos.y, muzzlePos.z, SoundEvents.SKELETON_SHOOT, SoundSource.BLOCKS, 1.0f, 1.0f / (fakePlayer.getRandom().nextFloat() * 0.4F + 0.8F));
        return 5;
    }

    private static int fireCrossbow(ServerLevel level, BlockPos pos, Vec3 muzzlePos, ItemStack weapon, LivingEntity target, @Nullable IItemHandler ammoHandler) {
        ItemStack ammo = extractFromHandler(ammoHandler, stack -> stack.getItem() instanceof ArrowItem || stack.is(Items.FIREWORK_ROCKET));
        if (ammo.isEmpty()) {
            return 10;
        }
        Vec3 baseAim = target.position().subtract(muzzlePos);
        float yaw = yawFromMotion(baseAim);
        float pitch = pitchFromMotion(baseAim);
        var fakePlayer = ArmFakePlayer.sync(level, pos, muzzlePos, yaw, pitch, weapon);
        double targetX = target.getX() - muzzlePos.x;
        double targetY = target.getY(0.3333333333333333D) - muzzlePos.y;
        double targetZ = target.getZ() - muzzlePos.z;
        double targetRadius = Math.sqrt(targetX * targetX + targetZ * targetZ);
        int multishot = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.MULTISHOT, weapon) > 0 ? 3 : 1;
        int piercing = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.PIERCING, weapon);

        for (int i = 0; i < multishot; i++) {
            if (ammo.is(Items.FIREWORK_ROCKET)) {
                FireworkRocketEntity rocket = new FireworkRocketEntity(level, ammo.copyWithCount(1), muzzlePos.x, muzzlePos.y, muzzlePos.z, true);
                rocket.setOwner(fakePlayer);
                rocket.shoot(targetX, targetY, targetZ, 1.6F, 0.0F);
                level.addFreshEntity(rocket);
                continue;
            }

            AbstractArrow arrow = net.minecraft.world.entity.projectile.ProjectileUtil.getMobArrow(fakePlayer, ammo, 1.0f);
            arrow.setOwner(fakePlayer);
            arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
            arrow.setPos(muzzlePos.x, muzzlePos.y, muzzlePos.z);
            arrow.setPierceLevel((byte) piercing);
            arrow.shoot(targetX, targetY + targetRadius * 0.2F, targetZ, 1.6F, 0.0F);
            level.addFreshEntity(arrow);
        }
        weapon.hurtAndBreak(1, fakePlayer, p -> {});
        level.playSound(null, muzzlePos.x, muzzlePos.y, muzzlePos.z, SoundEvents.CROSSBOW_SHOOT, SoundSource.BLOCKS, 1.0f, 1.0f);
        int quickCharge = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.QUICK_CHARGE, weapon);
        return Math.max(6, 25 - quickCharge * 5);
    }

    private static int throwPotion(ServerLevel level, BlockPos pos, Vec3 muzzlePos, ItemStack weapon, LivingEntity target, @Nullable IItemHandler ammoHandler) {
        ItemStack ammo = extractFromHandler(ammoHandler, stack -> ItemStack.isSameItemSameTags(stack, weapon));
        if (ammo.isEmpty()) {
            return 10;
        }
        Vec3 targetPos = target.position();
        if (!isVisible(level, muzzlePos, targetPos)) {
            Vec3 bodyPos = getBestTargetPos(level, muzzlePos, target);
            if (bodyPos != null) {
                targetPos = bodyPos;
            }
        }
        Vec3 motion = ballisticPotionMotion(targetPos.subtract(muzzlePos), 2.5, 0.05, 0.99);
        if (motion == null) {
            Vec3 fallback = targetPos.subtract(muzzlePos).normalize().scale(0.75f);
            motion = new Vec3(fallback.x, fallback.y, fallback.z);
        }
        var fakePlayer = ArmFakePlayer.sync(level, pos, muzzlePos, yawFromMotion(motion), pitchFromMotion(motion), weapon);
        ThrownPotion potion = new ThrownPotion(level, fakePlayer);
        potion.setItem(ammo.copyWithCount(1));
        potion.setPos(muzzlePos.x, muzzlePos.y, muzzlePos.z);
        potion.setDeltaMovement(motion);
        level.addFreshEntity(potion);
        level.playSound(null, muzzlePos.x, muzzlePos.y, muzzlePos.z, SoundEvents.SPLASH_POTION_THROW, SoundSource.BLOCKS, 0.6f, 1.0f);
        return weapon.getItem() instanceof LingeringPotionItem ? 28 : 20;
    }

    private static int firePotatoCannon(ServerLevel level, BlockPos pos, Vec3 muzzlePos, ItemStack weapon, float yaw, float pitch, @Nullable IItemHandler ammoHandler) {
        ItemStack ammo = extractFromHandler(ammoHandler, stack -> PotatoCannonProjectileType.getTypeForItem(level.registryAccess(), stack.getItem()).isPresent());
        if (ammo.isEmpty()) {
            return 10;
        }
        Optional<net.minecraft.core.Holder.Reference<PotatoCannonProjectileType>> typeRef =
                PotatoCannonProjectileType.getTypeForItem(level.registryAccess(), ammo.getItem());
        if (typeRef.isEmpty()) {
            return 10;
        }
        var fakePlayer = ArmFakePlayer.sync(level, pos, muzzlePos, yaw, pitch, weapon);
        net.minecraft.world.entity.EntityType<?> entityType = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES
                .getValue(new ResourceLocation("create", "potato_projectile"));
        if (entityType == null) {
            return 10;
        }
        @SuppressWarnings("unchecked")
        net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.projectile.AbstractHurtingProjectile> projectileType =
                (net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.projectile.AbstractHurtingProjectile>) entityType;
        PotatoProjectileEntity projectile = new PotatoProjectileEntity(projectileType, level);
        projectile.setItem(ammo.copyWithCount(1));
        projectile.setEnchantmentEffectsFromCannon(weapon);
        projectile.setPos(muzzlePos.x, muzzlePos.y, muzzlePos.z);
        projectile.setOwner(fakePlayer);
        Vec3 motion = directMotion(yaw, pitch, 2.0f * typeRef.get().value().velocityMultiplier());
        projectile.setDeltaMovement(motion);
        level.addFreshEntity(projectile);
        weapon.hurtAndBreak(1, fakePlayer, p -> {});
        level.playSound(null, muzzlePos.x, muzzlePos.y, muzzlePos.z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 0.4f, typeRef.get().value().soundPitch());
        return Math.max(6, typeRef.get().value().reloadTicks());
    }

    private static Vec3 directMotion(float yaw, float pitch, double speed) {
        return Vec3.directionFromRotation(pitch, yaw).scale(speed);
    }

    private static float yawFromMotion(Vec3 motion) {
        return (float) Math.toDegrees(Mth.atan2(motion.z, motion.x)) - 90f;
    }

    private static float pitchFromMotion(Vec3 motion) {
        double horizontal = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        return (float) -Math.toDegrees(Mth.atan2(motion.y, horizontal));
    }

    private static @Nullable Vec3 ballisticPotionMotion(Vec3 delta, double speed, double gravity, double drag) {
        Vec3 dragMotion = dockerBallisticMotionDrag(delta.x, delta.y, delta.z, speed, gravity, drag, true, 160);
        if (dragMotion != null) {
            return dragMotion;
        }
        return dockerBallisticMotion(delta.x, delta.y, delta.z, speed, gravity, true);
    }

    private static @Nullable Vec3 dockerBallisticMotion(double dx, double dy, double dz, double v, double g, boolean preferHigh) {
        double dh2 = dx * dx + dz * dz;
        double a = g * g;
        double b = 4.0 * (dy * g - v * v);
        double c = 4.0 * (dh2 + dy * dy);
        double discriminant = b * b - 4.0 * a * c;
        if (discriminant < 0) {
            return null;
        }
        double sqrtDisc = Math.sqrt(discriminant);
        double u1 = (-b + sqrtDisc) / (2.0 * a);
        double u2 = (-b - sqrtDisc) / (2.0 * a);
        List<Double> solutions = new ArrayList<>();
        if (u1 > 1e-9) {
            solutions.add(u1);
        }
        if (u2 > 1e-9 && Math.abs(u2 - u1) > 1e-9) {
            solutions.add(u2);
        }
        if (solutions.isEmpty()) {
            return null;
        }
        solutions.sort(Double::compareTo);
        double u = preferHigh ? solutions.get(solutions.size() - 1) : solutions.get(0);
        double t = Math.sqrt(u);
        if (!Double.isFinite(t) || t <= 1e-9) {
            return null;
        }
        double vx = dx / t;
        double vz = dz / t;
        double vy = (dy + 0.5 * g * u) / t;
        double mag = Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (!Double.isFinite(mag) || mag <= 1e-9) {
            return null;
        }
        double scale = v / mag;
        return new Vec3(vx * scale, vy * scale, vz * scale);
    }

    private static @Nullable Vec3 dockerBallisticMotionDrag(double dx, double dy, double dz, double v, double g, double drag, boolean preferHigh, int maxTicks) {
        double tolerance = 0.05;
        MotionCandidate bestLow = null;
        MotionCandidate bestHigh = null;

        double oneMinus = 1.0 - drag;
        if (oneMinus <= 1e-9) {
            return null;
        }

        for (int n = 2; n <= maxTicks; n++) {
            double dn = Math.pow(drag, n);
            double s = (1.0 - dn) / oneMinus;
            if (!Double.isFinite(s) || s <= 1e-9) {
                continue;
            }

            double vx0 = dx / s;
            double vz0 = dz / s;
            double vy0 = (dy + (g / oneMinus) * (n - s)) / s;
            if (!Double.isFinite(vx0) || !Double.isFinite(vy0) || !Double.isFinite(vz0)) {
                continue;
            }

            double projectileSpeed = Math.sqrt(vx0 * vx0 + vy0 * vy0 + vz0 * vz0);
            if (!Double.isFinite(projectileSpeed) || projectileSpeed <= 1e-9) {
                continue;
            }

            double err = Math.abs(projectileSpeed - v);
            if (err > v * tolerance) {
                continue;
            }

            MotionCandidate candidate = new MotionCandidate(vx0, vy0, vz0, n, err, projectileSpeed);
            if (bestLow == null || candidate.err < bestLow.err || (candidate.err == bestLow.err && candidate.ticks < bestLow.ticks)) {
                bestLow = candidate;
            }
            if (bestHigh == null || candidate.err < bestHigh.err || (candidate.err == bestHigh.err && candidate.ticks > bestHigh.ticks)) {
                bestHigh = candidate;
            }
        }

        MotionCandidate picked = preferHigh ? bestHigh : bestLow;
        if (picked == null) {
            return null;
        }
        double scale = v / picked.speed;
        return new Vec3(picked.x * scale, picked.y * scale, picked.z * scale);
    }

    private record MotionCandidate(double x, double y, double z, int ticks, double err, double speed) {
    }

    public static @Nullable IItemHandler getBelowHandler(ServerLevel level, BlockPos pos) {
        BlockEntity below = level.getBlockEntity(pos.below());
        if (below == null) {
            return null;
        }
        return below.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElse(null);
    }

    public static ItemStack extractFromHandler(@Nullable IItemHandler handler, Predicate<ItemStack> predicate) {
        if (handler == null) {
            return ItemStack.EMPTY;
        }
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack inSlot = handler.getStackInSlot(slot);
            if (!inSlot.isEmpty() && predicate.test(inSlot)) {
                return handler.extractItem(slot, 1, false);
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    protected void write(CompoundTag tag, boolean clientPacket) {
        super.write(tag, clientPacket);
        if (!heldItem.isEmpty()) {
            tag.put("HeldItem", heldItem.save(new CompoundTag()));
        }
        tag.putInt("AttackCooldown", attackCooldown);
        tag.putInt("TargetId", syncedTargetId);
        tag.putFloat("BaseAngle", baseAngle.getValue());
        tag.putFloat("LowerAngle", lowerArmAngle.getValue());
        tag.putFloat("UpperAngle", upperArmAngle.getValue());
        tag.putFloat("HeadAngle", headAngle.getValue());
        if (connectedFireControlPos != null) {
            tag.put("FireControlPos", NbtUtils.writeBlockPos(connectedFireControlPos));
        }
    }

    @Override
    protected void read(CompoundTag tag, boolean clientPacket) {
        super.read(tag, clientPacket);
        heldItem = tag.contains("HeldItem") ? ItemStack.of(tag.getCompound("HeldItem")) : ItemStack.EMPTY;
        attackCooldown = tag.getInt("AttackCooldown");
        syncedTargetId = tag.getInt("TargetId");
        baseAngle.setValue(tag.getFloat("BaseAngle"));
        lowerArmAngle.setValue(tag.getFloat("LowerAngle"));
        upperArmAngle.setValue(tag.getFloat("UpperAngle"));
        headAngle.setValue(tag.getFloat("HeadAngle"));
        connectedFireControlPos = tag.contains("FireControlPos") ? NbtUtils.readBlockPos(tag.getCompound("FireControlPos")) : null;
    }

    public record TargetFilter(boolean strict, boolean whitelist, List<String> targets) {
        public static final TargetFilter DEFAULT_ALL = new TargetFilter(false, false, new ArrayList<>());
    }

    private static class RangeValueBox extends ValueBoxTransform.Sided {
        @Override
        protected Vec3 getSouthLocation() {
            return new Vec3(8 / 16f, 8 / 16f, 8 / 16f);
        }
    }
}
