package euphy.upo.sentrymechanicalarm.content;

import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.createmod.catnip.math.AngleHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class BlazeFireControlBlockEntity extends SmartBlockEntity {

    public final LerpedFloat headAngle = LerpedFloat.angular();
    public final LerpedFloat headAnimation = LerpedFloat.linear();


    public final ItemStackHandler inventory = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return stack.getItem() instanceof FireControlClipboardItem;
        }

        @Override
        protected void onContentsChanged(int slot) {
            notifyUpdate();
            if (level != null && !level.isClientSide) {
                notifyConnectedSentries(false);
            }
        }

    };

    protected LazyOptional<IItemHandler> itemCapability = LazyOptional.of(() -> inventory);

    public BlazeFireControlBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    @Override
    protected void read(CompoundTag compound, boolean clientPacket) {
        super.read(compound, clientPacket);
        inventory.deserializeNBT(compound.getCompound("Inventory"));
    }

    @Override
    protected void write(CompoundTag compound, boolean clientPacket) {
        super.write(compound, clientPacket);
        compound.put("Inventory", inventory.serializeNBT());
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return itemCapability.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemCapability.invalidate();
    }

    public List<String> getTargetList() {
        List<String> targets = new ArrayList<>();
        ItemStack stack = inventory.getStackInSlot(0);

        if (!stack.isEmpty() && stack.hasTag() && stack.getTag().contains("TargetList", Tag.TAG_LIST)) {
            ListTag listTag = stack.getTag().getList("TargetList", Tag.TAG_STRING);
            for (Tag t : listTag) {
                targets.add(t.getAsString());
            }
        }
        return targets;
    }

    public void notifyConnectedSentries(boolean isRemoving) {
        if (level == null) return;

        BlockPos.betweenClosedStream(
                this.worldPosition.offset(-3, -3, -3),
                this.worldPosition.offset(3, 3, 3)
        ).forEach(pos -> {
            if (level.getBlockEntity(pos) instanceof SentryArmBlockEntity sentry) {
                BlockPos connectedPos = sentry.getConnectedFireControl();
                if (connectedPos != null && connectedPos.equals(this.worldPosition)) {
                    if (isRemoving) {
                        sentry.disconnectFireControl();
                    } else {
                        sentry.updateFromFireControl();
                    }
                }
            }
        });
    }


    @Override
    public void tick() {
        super.tick();

        if (level.isClientSide) {
            tickAnimation();
            spawnIdleParticles();
        }
    }

    @OnlyIn(Dist.CLIENT)
    protected void tickAnimation() {
        float target = 0;
        LocalPlayer player = Minecraft.getInstance().player;

        if (player != null && !player.isInvisible()) {
            double dx = player.getX() - (getBlockPos().getX() + 0.5);
            double dz = player.getZ() - (getBlockPos().getZ() + 0.5);
            target = AngleHelper.deg(-Mth.atan2(dz, dx)) - 90;
        }

        target = headAngle.getValue() + AngleHelper.getShortestAngleDiff(headAngle.getValue(), target);
        headAngle.chase(target, .25f, Chaser.exp(5));
        headAngle.tickChaser();

        headAnimation.chase(0, .25f, Chaser.exp(.25f));
        headAnimation.tickChaser();
    }

    @OnlyIn(Dist.CLIENT)
    protected void spawnIdleParticles() {
 
        RandomSource random = level.getRandom();
        if (random.nextInt(100) == 0) {
            level.addParticle(ParticleTypes.LARGE_SMOKE,
                    worldPosition.getX() + 0.5 + random.nextGaussian() * 0.1,
                    worldPosition.getY() + 1.0,
                    worldPosition.getZ() + 0.5 + random.nextGaussian() * 0.1,
                    0, 0, 0);
        }
    }

    public BlazeBurnerBlock.HeatLevel getHeatLevel() {
        return BlazeBurnerBlock.HeatLevel.SMOULDERING;
    }
}