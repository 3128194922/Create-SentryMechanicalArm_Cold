package com.createsentryarm.content;

import com.createsentryarm.CreateSentryArmMod;
import com.createsentryarm.compat.SentryCompat;
import com.createsentryarm.util.ArmFakePlayer;
import com.simibubi.create.api.equipment.potatoCannon.PotatoCannonProjectileType;
import com.simibubi.create.content.equipment.potatoCannon.PotatoProjectileEntity;
import com.simibubi.create.content.equipment.potatoCannon.PotatoCannonItem;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.LingeringPotionItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.SplashPotionItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
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
    public int syncedTargetId = -1;
    private BlockPos connectedFireControlPos;

    // 瞄准动画状态（与参考实现 SentryMechanicalArm 对齐）
    private int idleScanTimer = 20;
    public float idleTargetYaw;
    public float idleTargetPitch;
    public float lowerArmRecoilOffset;
    public float aimTurnSpeed = 1.0f;

    // 护目镜显示所需的客户端同步状态
    public String syncedAmmoId = "";
    public float syncedAmmoRange = -1.0f;

    // 头部对齐发射方向：服务端解算并同步的发射角
    public float syncedLaunchYaw;
    public float syncedLaunchPitch;

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

        IItemHandler ammoHandler = null;
        double ammoRange = Double.MAX_VALUE;
        AmmoData ammoData = null;
        if (!level.isClientSide) {
            // 服务端：预读下一发弹药，取其数据包参数（按旋转模式的射程/转向速度），并同步给护目镜显示
            ammoHandler = getBelowHandler((ServerLevel) level, worldPosition);
            ItemStack peeked = peekAmmo(ammoHandler, level, heldItem);
            ammoData = AmmoDataManager.get(level, heldItem, peeked);
            ammoRange = ammoData.effectiveRange(isHighArc());
            float turn = (float) ammoData.turnSpeed();
            String peekedId = peeked.isEmpty() ? ""
                    : String.valueOf(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(peeked.getItem()));
            float dataRange = (float) ammoData.effectiveRange(isHighArc());
            if (turn != aimTurnSpeed || !peekedId.equals(syncedAmmoId) || dataRange != syncedAmmoRange) {
                aimTurnSpeed = turn;
                syncedAmmoId = peekedId;
                syncedAmmoRange = dataRange;
                setChanged();
                sendData();
            }
        } else {
            // 客户端：用服务端同步的弹药射程，保证两端索敌一致
            ammoRange = syncedAmmoRange > 0 ? syncedAmmoRange : 64.0;
        }

        TargetFilter filter = getTargetFilter();
        Vec3 muzzlePos = getMuzzlePos();
        // 索敌射程完全由弹药数据决定（平射/高抛按旋转模式取对应值）
        LivingEntity target = findTarget(level, muzzlePos, ammoRange, filter, null);
        if (target == null) {
            syncedTargetId = -1;
            idleScan();
            return;
        }
        syncedTargetId = target.getId();
        Vec3 targetPos = getBestTargetPos(level, muzzlePos, target);
        if (targetPos == null) {
            idleScan();
            return;
        }
        float[] yawPitch = getYawPitch(muzzlePos, targetPos);
        float aimYaw = yawPitch[0];
        float aimPitch = yawPitch[1];

        // 头部对齐发射方向：服务端每刻解算发射角（含弹道补偿与提前量）并同步客户端；
        // "无解"场景回退为指向目标本身
        Vec3 launchMotion = null;
        if (!level.isClientSide) {
            launchMotion = solveAimMotion(muzzlePos, target, ammoData, isHighArc());
            if (launchMotion != null) {
                aimYaw = yawFromMotion(launchMotion);
                aimPitch = pitchFromMotion(launchMotion);
            }
            // 0.5° 死区：避免目标移动时每刻全量同步（客户端有追踪平滑，视觉无损）
            float yawDiff = Math.abs(aimYaw - syncedLaunchYaw) % 360.0f;
            if (Math.min(yawDiff, 360.0f - yawDiff) > 0.5f || Math.abs(aimPitch - syncedLaunchPitch) > 0.5f) {
                syncedLaunchYaw = aimYaw;
                syncedLaunchPitch = aimPitch;
                setChanged();
                sendData();
            }
        } else if (syncedTargetId != -1) {
            aimYaw = syncedLaunchYaw;
            aimPitch = syncedLaunchPitch;
        }
        aimAtAngle(aimYaw, aimPitch);

        if (!level.isClientSide && attackCooldown <= 0) {
            ServerLevel serverLevel = (ServerLevel) level;

            // 炮塔式射击门控：机械臂转向到位后才开火
            if (!isRoughlyAimed(aimYaw, aimPitch)) {
                return;
            }

            if (launchMotion == null) {
                // 当前发射方式无解：保持瞄准姿态，不发射、不耗弹
                attackCooldown = 10;
            } else {
                attackCooldown = performAttack(serverLevel, worldPosition, muzzlePos, heldItem, target, aimYaw, aimPitch, isHighArc(), ammoHandler);
                kickRecoil();
            }
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

    /**
     * 供能且手持武器但没有目标：保持展开姿态缓慢扫描（与参考实现一致）。
     */
    protected void idleScan() {
        if (!level.isClientSide) {
            if (idleScanTimer-- <= 0) {
                idleTargetYaw = level.random.nextFloat() * 1800.0f;
                idleTargetPitch = level.random.nextFloat() * 30.0f - 15.0f;
                idleScanTimer = 80 + level.random.nextInt(60);
                setChanged();
                sendData();
            }
        }
        float animSpeed = getAnimationSpeed(0.1f);
        float diffYaw = idleTargetYaw - baseAngle.getValue();
        while (diffYaw < -180) diffYaw += 360;
        while (diffYaw > 180) diffYaw -= 360;
        baseAngle.chase(baseAngle.getValue() + diffYaw, animSpeed, LerpedFloat.Chaser.EXP);
        headAngle.chase(idleTargetPitch, animSpeed, LerpedFloat.Chaser.EXP);
        lowerArmAngle.chase(135, animSpeed, LerpedFloat.Chaser.EXP);
        upperArmAngle.chase(90, animSpeed, LerpedFloat.Chaser.EXP);
    }

    /**
     * 开火后坐力：下臂瞬时后踢并在瞄准时回弹。
     */
    public void kickRecoil() {
        lowerArmRecoilOffset = -12.0f;
    }

    /**
     * 动画速度随转速（RPM）缩放：1-256 RPM 映射到 0.01-1.0 倍。
     */
    protected float getAnimationSpeed(float base) {
        float rpm = Math.abs(getSpeed());
        float multiplier = Mth.clamp(Mth.map(rpm, 1.0f, 256.0f, 0.01f, 1.0f), 0.01f, 1.0f);
        return base * multiplier;
    }

    /**
     * 旋转方向决定发射方式：正转（顺时针，速度 > 0）平射，反转（逆时针，速度 < 0）高抛。
     * 选定方式后弹道解算只按该方式计算，无解则不发射。
     */
    public boolean isHighArc() {
        return getSpeed() < 0;
    }

    /**
     * 炮塔式射击门控：底座 yaw 已对准目标（偏差 < 1 度）、头部俯仰对准发射角
     * （偏差 < 3 度）且上臂已展开。
     */
    public boolean isRoughlyAimed(float trueYaw, float truePitch) {
        if (upperArmAngle.getValue() <= 80.0f) {
            return false;
        }
        boolean ceiling = getBlockState().getValue(AttackArmBlock.CEILING);
        float currentWorldYaw = ceiling ? baseAngle.getValue() : 180.0f - baseAngle.getValue();
        double absDiff = Math.abs(trueYaw - currentWorldYaw) % 360.0;
        if (Math.min(absDiff, 360.0 - absDiff) >= 1.0) {
            return false;
        }
        float currentWorldPitch = ceiling ? headAngle.getValue() : -headAngle.getValue();
        return Math.abs(truePitch - currentWorldPitch) < 3.0;
    }

    /**
     * 工程师护目镜信息：在动力学应力信息之后追加机械臂状态
     * （发射方式 / 索敌射程 / 当前目标 / 当前弹药）。
     */
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        boolean added = super.addToGoggleTooltip(tooltip, isPlayerSneaking);

        // 注意：LangBuilder.translate 会给键名加 "create." 前缀，自有键必须用 Component.translatable + add
        CreateLang.builder()
                .add(Component.translatable("gui.goggles.createsentryarm.attack_arm"))
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

        CreateLang.builder()
                .add(Component.translatable("gui.goggles.createsentryarm.firing_mode",
                        Component.translatable(isHighArc()
                                ? "gui.goggles.createsentryarm.mode.high_arc"
                                : "gui.goggles.createsentryarm.mode.flat")
                                .withStyle(ChatFormatting.AQUA)))
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip, 1);

        double shownRange = syncedAmmoRange > 0 ? syncedAmmoRange : 64.0;
        CreateLang.builder()
                .add(Component.translatable("gui.goggles.createsentryarm.range", Math.round(shownRange)))
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip, 1);

        Component targetName = Component.translatable("gui.goggles.createsentryarm.none")
                .withStyle(ChatFormatting.DARK_GRAY);
        if (level != null && syncedTargetId != -1 && level.getEntity(syncedTargetId) instanceof LivingEntity living) {
            targetName = living.getDisplayName().copy().withStyle(ChatFormatting.AQUA);
        }
        CreateLang.builder()
                .add(Component.translatable("gui.goggles.createsentryarm.target", targetName))
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip, 1);

        Component ammoName = Component.translatable("gui.goggles.createsentryarm.none")
                .withStyle(ChatFormatting.DARK_GRAY);
        if (!syncedAmmoId.isEmpty()) {
            Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new ResourceLocation(syncedAmmoId));
            if (item != null && item != Items.AIR) {
                ammoName = new ItemStack(item).getHoverName().copy().withStyle(ChatFormatting.AQUA);
            }
        }
        CreateLang.builder()
                .add(Component.translatable("gui.goggles.createsentryarm.ammo", ammoName))
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip, 1);

        return true;
    }

    protected Vec3 getMuzzlePos() {
        boolean ceiling = getBlockState().getValue(AttackArmBlock.CEILING);
        return worldPosition.getCenter().add(0, ceiling ? -1.1 : 1.45, 0);
    }

    protected TargetFilter getTargetFilter() {
        if (connectedFireControlPos == null || level == null) {
            return TargetFilter.DEFAULT_ALL;
        }
        if (connectedFireControlPos.distSqr(worldPosition) > 36.0) {
            disconnectFireControl();
            return TargetFilter.DEFAULT_ALL;
        }
        BlockEntity be = level.getBlockEntity(connectedFireControlPos);
        if (be == null) {
            disconnectFireControl();
            return TargetFilter.DEFAULT_ALL;
        }
        if (be instanceof BlazeFireControlBlockEntity fireControl) {
            if (fireControl.inventory.getStackInSlot(0).isEmpty()) {
                return TargetFilter.DEFAULT_ALL;
            }
            return new TargetFilter(true, fireControl.isWhitelist(), fireControl.getTargetList());
        }
        SentryCompat.SentryFilterData sentryData = SentryCompat.readFireControlData(be);
        if (sentryData != null) {
            return new TargetFilter(true, sentryData.whitelist(), new ArrayList<>(sentryData.targets()));
        }
        disconnectFireControl();
        return TargetFilter.DEFAULT_ALL;
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
        if (filter.targets.isEmpty()) {
            return filter.whitelist && entity instanceof Enemy;
        }
        ResourceLocation typeId = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        String name = entity.getName().getString();
        boolean listed = filter.targets.contains(name) || (typeId != null && filter.targets.contains(typeId.toString()));
        return filter.whitelist ? !listed : listed;
    }

    public static Vec3 getBestTargetPos(net.minecraft.world.level.Level level, Vec3 muzzlePos, LivingEntity target) {
        // 优先实体头部（眼睛位置），部分遮挡时逐步下移
        Vec3 base = target.position();
        float height = target.getBbHeight();
        Vec3[] points = new Vec3[] {
                target.getEyePosition(),
                base.add(0, height * 0.7, 0),
                base.add(0, height * 0.45, 0)
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

        // 分级追踪：接近目标时快速吸附，偏差大时平稳转动（与参考实现一致）
        float absYawDiff = Math.abs(yawDiff);
        if (absYawDiff < 0.5f) {
            baseAngle.chase(currentYaw + yawDiff, 1.0f, LerpedFloat.Chaser.EXP);
        } else {
            float yawSpeedBase;
            if (absYawDiff < 10.0f) {
                yawSpeedBase = 0.8f;
            } else if (absYawDiff < 45.0f) {
                yawSpeedBase = 0.4f;
            } else {
                yawSpeedBase = 0.35f;
            }
            baseAngle.chase(currentYaw + yawDiff, getAnimationSpeed(yawSpeedBase * aimTurnSpeed), LerpedFloat.Chaser.EXP);
        }

        float desiredPitch = Mth.clamp(-targetPitch, -90, 90);
        float pitchDiff = desiredPitch - headAngle.getValue();
        if (Math.abs(pitchDiff) < 0.5f) {
            headAngle.chase(desiredPitch, 1.0f, LerpedFloat.Chaser.EXP);
        } else {
            float pitchSpeedBase = Math.abs(pitchDiff) < 5.0f ? 0.8f : 0.35f;
            headAngle.chase(desiredPitch, getAnimationSpeed(pitchSpeedBase * aimTurnSpeed), LerpedFloat.Chaser.EXP);
        }

        upperArmAngle.chase(90, getAnimationSpeed(0.4f * aimTurnSpeed), LerpedFloat.Chaser.EXP);

        // 后坐力回弹
        lowerArmRecoilOffset = Mth.lerp(0.7f, lowerArmRecoilOffset, 0.0f);
        if (Math.abs(lowerArmRecoilOffset) < 0.01f) {
            lowerArmRecoilOffset = 0.0f;
        }
        lowerArmAngle.chase(135 + lowerArmRecoilOffset, 0.6f, LerpedFloat.Chaser.EXP);
    }

    public static int performAttack(ServerLevel level, BlockPos pos, Vec3 muzzlePos, ItemStack weapon, LivingEntity target, float yaw, float pitch, boolean highArc, @Nullable IItemHandler ammoHandler) {
        if (weapon.getItem() instanceof BowItem) {
            return fireBow(level, pos, muzzlePos, weapon, target, highArc, ammoHandler);
        }
        if (weapon.getItem() instanceof CrossbowItem) {
            return fireCrossbow(level, pos, muzzlePos, weapon, target, highArc, ammoHandler);
        }
        if (weapon.getItem() instanceof SplashPotionItem || weapon.getItem() instanceof LingeringPotionItem) {
            return throwPotion(level, pos, muzzlePos, weapon, target, highArc, ammoHandler);
        }
        if (weapon.getItem() instanceof PotatoCannonItem) {
            return firePotatoCannon(level, pos, muzzlePos, weapon, target, highArc, ammoHandler);
        }
        return 10;
    }

    public static final Predicate<ItemStack> BOW_AMMO = stack ->
            stack.getItem() instanceof ArrowItem || stack.is(AmmoDataManager.FIREWORK_TAG);
    public static final Predicate<ItemStack> CROSSBOW_AMMO = stack ->
            stack.getItem() instanceof ArrowItem || stack.is(Items.FIREWORK_ROCKET) || stack.is(AmmoDataManager.FIREWORK_TAG);

    public static boolean isAmmoFor(Level level, ItemStack weapon, ItemStack stack) {
        if (weapon.getItem() instanceof BowItem) {
            return BOW_AMMO.test(stack);
        }
        if (weapon.getItem() instanceof CrossbowItem) {
            return CROSSBOW_AMMO.test(stack);
        }
        if (weapon.getItem() instanceof SplashPotionItem || weapon.getItem() instanceof LingeringPotionItem) {
            return ItemStack.isSameItemSameTags(stack, weapon);
        }
        if (weapon.getItem() instanceof PotatoCannonItem) {
            return PotatoCannonProjectileType.getTypeForItem(level.registryAccess(), stack.getItem()).isPresent();
        }
        return false;
    }

    /**
     * 预读当前武器将在下方容器中取到的下一发弹药（不实际取出）。
     */
    public static ItemStack peekAmmo(@Nullable IItemHandler handler, Level level, ItemStack weapon) {
        if (handler == null) {
            return ItemStack.EMPTY;
        }
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack inSlot = handler.getStackInSlot(slot);
            if (!inSlot.isEmpty() && isAmmoFor(level, weapon, inSlot)) {
                return inSlot;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * 索敌点：按弹药数据的 aim_height 取目标身体上的位置。
     * >= 1 为头部（眼睛位置），小于 1 为相对身高比例（如 0.5 = 身体中部）。
     */
    public static Vec3 getAimPoint(LivingEntity target, double aimHeight) {
        if (aimHeight >= 1.0) {
            return target.getEyePosition();
        }
        return target.position().add(0, target.getBbHeight() * Mth.clamp(aimHeight, 0.05, 1.0), 0);
    }

    /**
     * 索敌解算：按目标速度计算提前量（预测拦截点），再以指定发射方式
     * （平射/高抛，由旋转方向决定）解算带重力/阻力的弹道。
     * <p>
     * 只使用指定方式的弹道解；该方式无解时返回 null，调用方应放弃本次
     * 射击（不消耗弹药）。无重力弹体为直线飞行，恒有解。
     *
     * @return 发射向量（模长为出膛速度），无解返回 null
     */
    public static @Nullable Vec3 solveAimMotion(Vec3 muzzle, LivingEntity target, AmmoData data, boolean preferHigh) {
        Vec3 anchor = getAimPoint(target, data.aimHeight());
        Vec3 targetVel = target.getDeltaMovement();
        Vec3 aimPoint = anchor;
        if (data.lead() > 0 && targetVel.lengthSqr() > 1.0e-6) {
            for (int i = 0; i < 3; i++) {
                double flightTicks = aimPoint.subtract(muzzle).length() / Math.max(data.speed(), 0.05);
                aimPoint = anchor.add(targetVel.scale(flightTicks * data.lead()));
            }
        }
        Vec3 delta = aimPoint.subtract(muzzle);
        if (data.gravity() <= 0.0) {
            return delta.normalize().scale(data.speed());
        }
        // 只使用当前模式的弹道解；该模式无解返回 null（不发射）
        Vec3 solved = dockerBallisticMotionDrag(delta.x, delta.y, delta.z, data.speed(), data.gravity(), data.drag(), preferHigh, 160, data.solverTolerance());
        if (solved == null) {
            return null;
        }
        // 闭环校验：按弹体实际物理（位移→阻力→重力）前推模拟，
        // 弹道必须充分接近瞄准点（如擦顶等边缘解会被拒绝，视为无解）；
        // verify_tolerance <= 0 时禁用校验
        if (data.verifyTolerance() > 0.0 && closestApproach(muzzle, solved, aimPoint, data) > data.verifyTolerance()) {
            return null;
        }
        return solved;
    }

    /**
     * 以给定发射向量前推模拟弹道，返回与瞄准点的最近距离。
     */
    private static double closestApproach(Vec3 muzzle, Vec3 motion, Vec3 aimPoint, AmmoData data) {
        double x = muzzle.x;
        double y = muzzle.y;
        double z = muzzle.z;
        double vx = motion.x;
        double vy = motion.y;
        double vz = motion.z;
        double best = Double.MAX_VALUE;
        for (int t = 0; t < 240; t++) {
            x += vx;
            y += vy;
            z += vz;
            double dx = x - aimPoint.x;
            double dy = y - aimPoint.y;
            double dz = z - aimPoint.z;
            best = Math.min(best, Math.sqrt(dx * dx + dy * dy + dz * dz));
            if (y < aimPoint.y - 4.0 && vy < 0.0) {
                break;
            }
            vx *= data.drag();
            vy = vy * data.drag() - data.gravity();
            vz *= data.drag();
        }
        return best;
    }

    private static Vec3 rotateAroundY(Vec3 v, double degrees) {
        double rad = Math.toRadians(degrees);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        return new Vec3(v.x * cos + v.z * sin, v.y, -v.x * sin + v.z * cos);
    }

    private static int fireBow(ServerLevel level, BlockPos pos, Vec3 muzzlePos, ItemStack weapon, LivingEntity target, boolean highArc, @Nullable IItemHandler ammoHandler) {

        ItemStack peeked = peekAmmo(ammoHandler, level, weapon);
        if (peeked.isEmpty()) return 20;

        AmmoData data = AmmoDataManager.get(level, weapon, peeked);
        Vec3 motion = solveAimMotion(muzzlePos, target, data, highArc);
        if (motion == null) {
            // 当前发射方式无解：不发射、不消耗弹药，短暂冷却后重试
            return 10;
        }

        ItemStack ammo = extractFromHandler(ammoHandler, BOW_AMMO);
        if (ammo.isEmpty()) return 20;

        var fakePlayer = ArmFakePlayer.sync(level, pos, muzzlePos, yawFromMotion(motion), pitchFromMotion(motion), weapon);

        AbstractArrow arrow = net.minecraft.world.entity.projectile.ProjectileUtil.getMobArrow(fakePlayer, ammo, 1.0f);
        arrow.setPos(muzzlePos.x, muzzlePos.y, muzzlePos.z);
        arrow.shoot(motion.x, motion.y, motion.z, (float) data.speed(), (float) data.spread());

        int power = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.POWER_ARROWS, weapon);
        if (power > 0) arrow.setBaseDamage(arrow.getBaseDamage() + power * 0.5 + 0.5);

        int punch = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.PUNCH_ARROWS, weapon);
        if (punch > 0) arrow.setKnockback(punch);

        if (EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FLAMING_ARROWS, weapon) > 0)
            arrow.setSecondsOnFire(5);

        if (data.damage() != null) {
            arrow.setBaseDamage(data.damage());
        }

        level.addFreshEntity(arrow);
        weapon.hurtAndBreak(1, fakePlayer, p -> {});

        level.playSound(null, muzzlePos.x, muzzlePos.y, muzzlePos.z,
                SoundEvents.SKELETON_SHOOT, SoundSource.BLOCKS, 1.0f,
                1.0f / (fakePlayer.getRandom().nextFloat() * 0.4F + 0.8F));

        return data.cooldown(20);
    }

    private static int fireCrossbow(ServerLevel level, BlockPos pos, Vec3 muzzlePos, ItemStack weapon, LivingEntity target, boolean highArc, @Nullable IItemHandler ammoHandler) {

        ItemStack peeked = peekAmmo(ammoHandler, level, weapon);
        if (peeked.isEmpty()) return 10;

        AmmoData data = AmmoDataManager.get(level, weapon, peeked);
        Vec3 motion = solveAimMotion(muzzlePos, target, data, highArc);
        if (motion == null) {
            return 10;
        }

        ItemStack ammo = extractFromHandler(ammoHandler, CROSSBOW_AMMO);
        if (ammo.isEmpty()) return 10;

        boolean isVanillaFirework = ammo.is(Items.FIREWORK_ROCKET);
        boolean noGravity = data.gravity() <= 0.0;

        var fakePlayer = ArmFakePlayer.sync(level, pos, muzzlePos, yawFromMotion(motion), pitchFromMotion(motion), weapon);

        int multishot = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.MULTISHOT, weapon) > 0 ? 3 : 1;
        int piercing = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.PIERCING, weapon);

        for (int i = 0; i < multishot; i++) {

            // 多重射击：左右各偏转 10 度
            Vec3 shotMotion = multishot > 1 ? rotateAroundY(motion, (i - 1) * 10.0) : motion;

            // 🎇 ===== 原版烟花（FireworkRocketEntity）=====
            if (isVanillaFirework) {
                FireworkRocketEntity rocket = new FireworkRocketEntity(
                        level,
                        ammo.copyWithCount(1),
                        muzzlePos.x, muzzlePos.y, muzzlePos.z,
                        true
                );

                rocket.setOwner(fakePlayer);
                rocket.shoot(shotMotion.x, shotMotion.y, shotMotion.z, (float) data.speed(), (float) data.spread());

                level.addFreshEntity(rocket);
                continue;
            }

            // 🏹 ===== 所有箭（普通箭 + firework tag 无下坠箭）=====
            AbstractArrow arrow = net.minecraft.world.entity.projectile.ProjectileUtil.getMobArrow(fakePlayer, ammo, 1.0f);

            arrow.setOwner(fakePlayer);
            arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
            arrow.setPos(muzzlePos.x, muzzlePos.y, muzzlePos.z);
            arrow.setPierceLevel((byte) piercing);

            if (noGravity) {
                arrow.setNoGravity(true);
            }
            arrow.shoot(shotMotion.x, shotMotion.y, shotMotion.z, (float) data.speed(), (float) data.spread());
            if (data.damage() != null) {
                arrow.setBaseDamage(data.damage());
            }

            level.addFreshEntity(arrow);
        }

        // 🔧 耐久
        weapon.hurtAndBreak(1, fakePlayer, p -> {});

        // 🔊 声音
        level.playSound(null, muzzlePos.x, muzzlePos.y, muzzlePos.z,
                SoundEvents.CROSSBOW_SHOOT, SoundSource.BLOCKS, 1.0f, 1.0f);

        // ⚡ 快速装填
        int quickCharge = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.QUICK_CHARGE, weapon);
        return Math.max(6, data.cooldown(25) - quickCharge * 5);
    }

    private static int throwPotion(ServerLevel level, BlockPos pos, Vec3 muzzlePos, ItemStack weapon, LivingEntity target, boolean highArc, @Nullable IItemHandler ammoHandler) {
        ItemStack peeked = peekAmmo(ammoHandler, level, weapon);
        if (peeked.isEmpty()) {
            return 10;
        }

        AmmoData data = AmmoDataManager.get(level, weapon, peeked);
        Vec3 motion = solveAimMotion(muzzlePos, target, data, highArc);
        if (motion == null) {
            return 10;
        }

        ItemStack ammo = extractFromHandler(ammoHandler, stack -> ItemStack.isSameItemSameTags(stack, weapon));
        if (ammo.isEmpty()) {
            return 10;
        }

        var fakePlayer = ArmFakePlayer.sync(level, pos, muzzlePos, yawFromMotion(motion), pitchFromMotion(motion), weapon);
        ThrownPotion potion = new ThrownPotion(level, fakePlayer);
        potion.setItem(ammo.copyWithCount(1));
        potion.setPos(muzzlePos.x, muzzlePos.y, muzzlePos.z);
        potion.setDeltaMovement(motion);
        level.addFreshEntity(potion);
        level.playSound(null, muzzlePos.x, muzzlePos.y, muzzlePos.z, SoundEvents.SPLASH_POTION_THROW, SoundSource.BLOCKS, 0.6f, 1.0f);
        return data.cooldown(weapon.getItem() instanceof LingeringPotionItem ? 28 : 20);
    }

    private static int firePotatoCannon(ServerLevel level, BlockPos pos, Vec3 muzzlePos, ItemStack weapon, LivingEntity target, boolean highArc, @Nullable IItemHandler ammoHandler) {
        ItemStack peeked = peekAmmo(ammoHandler, level, weapon);
        if (peeked.isEmpty()) {
            return 10;
        }
        Optional<net.minecraft.core.Holder.Reference<PotatoCannonProjectileType>> typeRef =
                PotatoCannonProjectileType.getTypeForItem(level.registryAccess(), peeked.getItem());
        if (typeRef.isEmpty()) {
            return 10;
        }

        AmmoData data = AmmoDataManager.get(level, weapon, peeked);
        Vec3 motion = solveAimMotion(muzzlePos, target, data, highArc);
        if (motion == null) {
            return 10;
        }

        ItemStack ammo = extractFromHandler(ammoHandler, stack -> PotatoCannonProjectileType.getTypeForItem(level.registryAccess(), stack.getItem()).isPresent());
        if (ammo.isEmpty()) {
            return 10;
        }

        var fakePlayer = ArmFakePlayer.sync(level, pos, muzzlePos, yawFromMotion(motion), pitchFromMotion(motion), weapon);
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
        projectile.setDeltaMovement(motion);
        level.addFreshEntity(projectile);
        weapon.hurtAndBreak(1, fakePlayer, p -> {});
        level.playSound(null, muzzlePos.x, muzzlePos.y, muzzlePos.z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 0.4f, typeRef.get().value().soundPitch());
        return Math.max(6, data.cooldown(typeRef.get().value().reloadTicks()));
    }

    public static float yawFromMotion(Vec3 motion) {
        return (float) Math.toDegrees(Mth.atan2(motion.z, motion.x)) - 90f;
    }

    public static float pitchFromMotion(Vec3 motion) {
        double horizontal = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        return (float) -Math.toDegrees(Mth.atan2(motion.y, horizontal));
    }

    /**
     * 带阻力的离散弹道解算。所需初速随飞行刻数 n 呈 U 形：
     * 左支（n 小）为平射解，右支（n 大）为高抛解，底部 n* 为最省速度的临界解。
     * 分别在左右支内取所需速度最接近 v 的候选（方向重缩放到精确 v），
     * 对应支无解（超 10% 容差）时返回 null。
     */
    private static @Nullable Vec3 dockerBallisticMotionDrag(double dx, double dy, double dz, double v, double g, double drag, boolean preferHigh, int maxTicks, double tolerance) {
        double oneMinus = 1.0 - drag;
        if (oneMinus <= 1e-9) {
            return null;
        }

        double[][] candidates = new double[maxTicks + 1][];
        double minRequired = Double.MAX_VALUE;
        int nStar = -1;

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
            double required = Math.sqrt(vx0 * vx0 + vy0 * vy0 + vz0 * vz0);
            if (!Double.isFinite(required) || required <= 1e-9) {
                continue;
            }
            candidates[n] = new double[] {required, vx0, vy0, vz0};
            if (required < minRequired) {
                minRequired = required;
                nStar = n;
            }
        }

        // 完全够不着：U 形最低点所需速度也远超 v
        if (nStar < 0 || minRequired > v * (1.0 + tolerance)) {
            return null;
        }

        MotionCandidate bestLow = null;
        MotionCandidate bestHigh = null;
        for (int n = 2; n <= maxTicks; n++) {
            double[] c = candidates[n];
            if (c == null) {
                continue;
            }
            double err = Math.abs(c[0] - v);
            MotionCandidate candidate = new MotionCandidate(c[1], c[2], c[3], n, err, c[0]);
            if (n <= nStar && (bestLow == null || err < bestLow.err)) {
                bestLow = candidate;
            }
            if (n >= nStar && (bestHigh == null || err < bestHigh.err)) {
                bestHigh = candidate;
            }
        }

        MotionCandidate picked = preferHigh ? bestHigh : bestLow;
        if (picked == null || picked.err > v * tolerance) {
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
        tag.putFloat("LowerArmRecoil", lowerArmRecoilOffset);
        tag.putFloat("AimTurnSpeed", aimTurnSpeed);
        tag.putString("SyncedAmmoId", syncedAmmoId);
        tag.putFloat("SyncedAmmoRange", syncedAmmoRange);
        tag.putFloat("SyncedLaunchYaw", syncedLaunchYaw);
        tag.putFloat("SyncedLaunchPitch", syncedLaunchPitch);
        tag.putFloat("IdleTargetYaw", idleTargetYaw);
        tag.putFloat("IdleTargetPitch", idleTargetPitch);
        tag.putInt("IdleScanTimer", idleScanTimer);
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
        lowerArmRecoilOffset = tag.getFloat("LowerArmRecoil");
        aimTurnSpeed = tag.contains("AimTurnSpeed") ? tag.getFloat("AimTurnSpeed") : 1.0f;
        syncedAmmoId = tag.contains("SyncedAmmoId") ? tag.getString("SyncedAmmoId") : "";
        syncedAmmoRange = tag.contains("SyncedAmmoRange") ? tag.getFloat("SyncedAmmoRange") : -1.0f;
        syncedLaunchYaw = tag.getFloat("SyncedLaunchYaw");
        syncedLaunchPitch = tag.getFloat("SyncedLaunchPitch");
        idleTargetYaw = tag.getFloat("IdleTargetYaw");
        idleTargetPitch = tag.getFloat("IdleTargetPitch");
        idleScanTimer = tag.getInt("IdleScanTimer");
        connectedFireControlPos = tag.contains("FireControlPos") ? NbtUtils.readBlockPos(tag.getCompound("FireControlPos")) : null;
    }

    public record TargetFilter(boolean strict, boolean whitelist, List<String> targets) {
        public static final TargetFilter DEFAULT_ALL = new TargetFilter(false, false, new ArrayList<>());
    }

}
