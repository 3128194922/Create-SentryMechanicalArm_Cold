package euphy.upo.sentrymechanicalarm.network;

import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlock;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import com.tacz.guns.api.item.IGun;
import euphy.upo.sentrymechanicalarm.content.SentryArmBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SentryInteractPacket {
    private final BlockPos pos;

    public SentryInteractPacket(BlockPos pos) {
        this.pos = pos;
    }

    public static void encode(SentryInteractPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.pos);
    }

    public static SentryInteractPacket decode(FriendlyByteBuf buffer) {
        return new SentryInteractPacket(buffer.readBlockPos());
    }

    public static void handle(SentryInteractPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            if (player.distanceToSqr(packet.pos.getX(), packet.pos.getY(), packet.pos.getZ()) > 64) return;

 
            BlockEntity be = player.level().getBlockEntity(packet.pos);

 
            if (be instanceof SentryArmBlockEntity sentry) { 

 
                if (sentry.getBlockState().getValue(ArmBlock.CEILING)) return;

                ItemStack playerStack = player.getMainHandItem();

 
 
                if (playerStack.getItem() instanceof IGun && sentry.getHeldItem().isEmpty()) {
                    ItemStack gunCopy = playerStack.copy();
                    gunCopy.setCount(1);

                    sentry.setHeldItem(gunCopy);

                    if (!player.isCreative()) {
                        playerStack.shrink(1);
                    }

                    player.level().playSound(null, packet.pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.5f, 1.0f);

                    sentry.setChanged();
                    sentry.sendData();
                }
            }
        });
        context.setPacketHandled(true);
    }
}