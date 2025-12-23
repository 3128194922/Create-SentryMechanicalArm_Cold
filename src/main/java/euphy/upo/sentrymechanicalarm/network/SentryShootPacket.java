package euphy.upo.sentrymechanicalarm.network;

import euphy.upo.sentrymechanicalarm.content.SentryArmBlockEntity;
import euphy.upo.sentrymechanicalarm.util.SentryTrailManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SentryShootPacket {
    private final BlockPos pos;
    private final int slotIndex;
    private final CompoundTag itemTag;
    private final Vec3 realStart;
    private final Vec3 realEnd;

    public SentryShootPacket(BlockPos pos, int slotIndex, CompoundTag itemTag, Vec3 realStart, Vec3 realEnd) {
        this.pos = pos;
        this.slotIndex = slotIndex;
        this.itemTag = itemTag;
        this.realStart = realStart;
        this.realEnd = realEnd;
    }

    public static void encode(SentryShootPacket msg, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(msg.pos);
        buffer.writeInt(msg.slotIndex);
        buffer.writeNbt(msg.itemTag);
        buffer.writeDouble(msg.realStart.x);
        buffer.writeDouble(msg.realStart.y);
        buffer.writeDouble(msg.realStart.z);
        buffer.writeDouble(msg.realEnd.x);
        buffer.writeDouble(msg.realEnd.y);
        buffer.writeDouble(msg.realEnd.z);
    }

    public static SentryShootPacket decode(FriendlyByteBuf buffer) {
        return new SentryShootPacket(
                buffer.readBlockPos(),
                buffer.readInt(),
                buffer.readNbt(),
 
                new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
                new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble())
        );
    }

    public static void handle(SentryShootPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
 
            Level level = Minecraft.getInstance().level;
            if (level != null) {
                BlockEntity be = level.getBlockEntity(msg.pos);
                if (be instanceof SentryArmBlockEntity sentry) {
                    sentry.triggerShootEffects();
                    sentry.updateAmmoFromPacket(msg.slotIndex, msg.itemTag);
                }
            }
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                Vec3 direction = msg.realEnd.subtract(msg.realStart).normalize();
                double totalDistance = msg.realStart.distanceTo(msg.realEnd);
                double offsetDistance = 0.8;
                Vec3 adjustedStart = totalDistance > offsetDistance
                        ? msg.realStart.add(direction.scale(offsetDistance))
                        : msg.realStart;
                double adjustedDist = totalDistance > offsetDistance
                        ? totalDistance - offsetDistance
                        : totalDistance;

                SentryTrailManager.addTracer(adjustedStart, direction, 8.0, 2.0, adjustedDist);
            });
        });
        ctx.get().setPacketHandled(true);
    }

 
    private static void drawRealTrajectory(Level level, Vec3 start, Vec3 end) {
        double distance = start.distanceTo(end);
        Vec3 dir = end.subtract(start).normalize();

 
        for (double d = 0; d < distance; d += 0.2) {
            Vec3 p = start.add(dir.scale(d));
 
            level.addParticle(net.minecraft.core.particles.ParticleTypes.END_ROD,
                    p.x, p.y, p.z, 0, 0, 0);
        }
    }
}