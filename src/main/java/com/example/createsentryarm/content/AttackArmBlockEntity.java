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
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.FireworkRocketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.LingeringPotionItem;
import net.minecraft.world.item.SplashPotionItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
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

    public AttackArmBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
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
        heldItem = stack;
        setChanged();
        sendData();
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
        return rangeScroll == null ? 24 : rangeScroll.getValue();
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
            return fireBow(level, pos, muzzlePos, weapon, yaw, pitch, ammoHandler);
        }
        if (weapon.getItem() instanceof CrossbowItem) {
            return fireCrossbow(level, pos, muzzlePos, weapon, yaw, pitch, ammoHandler);
        }
        if (weapon.getItem() instanceof SplashPotionItem || weapon.getItem() instanceof LingeringPotionItem) {
            return throwPotion(level, pos, muzzlePos, weapon, yaw, pitch, ammoHandler);
        }
        if (weapon.getItem() instanceof PotatoCannonItem) {
            return firePotatoCannon(level, pos, muzzlePos, weapon, yaw, pitch, ammoHandler);
        }
        if (weapon.getItem() instanceof SwordItem || weapon.getItem() instanceof AxeItem) {
            return doMelee(level, pos, muzzlePos, weapon, target, yaw, pitch);
        }
        return 10;
    }

    private static int doMelee(ServerLevel level, BlockPos pos, Vec3 muzzlePos, ItemStack weapon, LivingEntity target, float yaw, float pitch) {
        if (target.distanceToSqr(muzzlePos) > 16.0) {
            return 4;
        }
        var fakePlayer = ArmFakePlayer.sync(level, pos, muzzlePos, yaw, pitch, weapon);
        fakePlayer.attack(target);
        if (weapon.isDamageableItem()) {
            weapon.hurtAndBreak(1, fakePlayer, p -> {});
        }
        level.playSound(null, muzzlePos.x, muzzlePos.y, muzzlePos.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.BLOCKS, 0.5f, 1.0f);
        return 12;
    }

    private static int fireBow(ServerLevel level, BlockPos pos, Vec3 muzzlePos, ItemStack weapon, float yaw, float pitch, @Nullable IItemHandler ammoHandler) {
        ItemStack ammo = extractFromHandler(ammoHandler, stack -> stack.getItem() instanceof ArrowItem);
        if (ammo.isEmpty()) {
            return 10;
        }
        var fakePlayer = ArmFakePlayer.sync(level, pos, muzzlePos, yaw, pitch, weapon);
        AbstractArrow arrow = ((ArrowItem) ammo.getItem()).createArrow(level, ammo, fakePlayer);
        arrow.setOwner(fakePlayer);
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        arrow.shootFromRotation(fakePlayer, pitch, yaw, 0, 3.0f, 1.0f);
        arrow.setCritArrow(true);
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
        level.playSound(null, muzzlePos.x, muzzlePos.y, muzzlePos.z, SoundEvents.ARROW_SHOOT, SoundSource.BLOCKS, 1.0f, 1.0f);
        return 20;
    }

    private static int fireCrossbow(ServerLevel level, BlockPos pos, Vec3 muzzlePos, ItemStack weapon, float yaw, float pitch, @Nullable IItemHandler ammoHandler) {
        ItemStack ammo = extractFromHandler(ammoHandler, stack -> stack.getItem() instanceof ArrowItem || stack.is(Items.FIREWORK_ROCKET));
        if (ammo.isEmpty()) {
            return 10;
        }
        var fakePlayer = ArmFakePlayer.sync(level, pos, muzzlePos, yaw, pitch, weapon);
        int multishot = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.MULTISHOT, weapon) > 0 ? 3 : 1;
        int piercing = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.PIERCING, weapon);
        for (int i = 0; i < multishot; i++) {
            float spread = multishot == 1 ? 0 : (i - 1) * 10f;
            if (ammo.is(Items.FIREWORK_ROCKET)) {
                FireworkRocketEntity firework = new FireworkRocketEntity(level, ammo.copyWithCount(1), muzzlePos.x, muzzlePos.y, muzzlePos.z, true);
                firework.shootFromRotation(fakePlayer, pitch, yaw + spread, 0, 1.6f, 1.0f);
                level.addFreshEntity(firework);
            } else {
                AbstractArrow arrow = ((ArrowItem) ammo.getItem()).createArrow(level, ammo, fakePlayer);
                arrow.setOwner(fakePlayer);
                arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
                arrow.setPierceLevel((byte) piercing);
                arrow.shootFromRotation(fakePlayer, pitch, yaw + spread, 0, 3.15f, 1.0f);
                level.addFreshEntity(arrow);
            }
        }
        weapon.hurtAndBreak(1, fakePlayer, p -> {});
        level.playSound(null, muzzlePos.x, muzzlePos.y, muzzlePos.z, ammo.is(Items.FIREWORK_ROCKET) ? SoundEvents.CROSSBOW_SHOOT : SoundEvents.CROSSBOW_LOADING_END, SoundSource.BLOCKS, 1.0f, 1.0f);
        int quickCharge = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.QUICK_CHARGE, weapon);
        return Math.max(6, 25 - quickCharge * 5);
    }

    private static int throwPotion(ServerLevel level, BlockPos pos, Vec3 muzzlePos, ItemStack weapon, float yaw, float pitch, @Nullable IItemHandler ammoHandler) {
        ItemStack ammo = extractFromHandler(ammoHandler, stack -> ItemStack.isSameItemSameTags(stack, weapon));
        if (ammo.isEmpty()) {
            return 10;
        }
        var fakePlayer = ArmFakePlayer.sync(level, pos, muzzlePos, yaw, pitch, weapon);
        ThrownPotion potion = new ThrownPotion(level, fakePlayer);
        potion.setItem(ammo.copyWithCount(1));
        potion.setPos(muzzlePos.x, muzzlePos.y, muzzlePos.z);
        potion.shootFromRotation(fakePlayer, pitch - 20f, yaw, 0f, 0.75f, 8.0f);
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
        PotatoProjectileEntity projectile = new PotatoProjectileEntity(com.simibubi.create.AllEntityTypes.POTATO_PROJECTILE.get(), level);
        projectile.setItem(ammo.copyWithCount(1));
        projectile.setEnchantmentEffectsFromCannon(weapon);
        projectile.setPos(muzzlePos.x, muzzlePos.y, muzzlePos.z);
        projectile.setOwner(fakePlayer);
        projectile.shootFromRotation(fakePlayer, pitch, yaw, 0, 2.0f * typeRef.get().value().velocityMultiplier(), 1.0f);
        level.addFreshEntity(projectile);
        weapon.hurtAndBreak(1, fakePlayer, p -> {});
        level.playSound(null, muzzlePos.x, muzzlePos.y, muzzlePos.z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 0.4f, typeRef.get().value().soundPitch());
        return Math.max(6, typeRef.get().value().reloadTicks());
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
