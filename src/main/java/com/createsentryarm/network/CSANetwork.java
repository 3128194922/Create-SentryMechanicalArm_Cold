package com.createsentryarm.network;

import com.createsentryarm.CreateSentryArmMod;
import com.createsentryarm.compat.SentryCompat;
import com.createsentryarm.content.AttackArmBlockEntity;
import com.createsentryarm.content.BlazeFireControlBlockEntity;
import com.createsentryarm.content.FireControlClipboardItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

public class CSANetwork {
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(CreateSentryArmMod.MODID, "main"),
            () -> "1",
            "1"::equals,
            "1"::equals
    );

    private CSANetwork() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, ToggleClipboardModePacket.class, ToggleClipboardModePacket::encode, ToggleClipboardModePacket::decode, ToggleClipboardModePacket::handle);
        CHANNEL.registerMessage(id++, ClearTargetPacket.class, ClearTargetPacket::encode, ClearTargetPacket::decode, ClearTargetPacket::handle);
        CHANNEL.registerMessage(id++, LinkFireControlPacket.class, LinkFireControlPacket::encode, LinkFireControlPacket::decode, LinkFireControlPacket::handle);
        CHANNEL.registerMessage(id++, RecordTargetPacket.class, RecordTargetPacket::encode, RecordTargetPacket::decode, RecordTargetPacket::handle);
    }

    private static ItemStack getHeldClipboard(ServerPlayer player) {
        if (player.getMainHandItem().getItem() instanceof FireControlClipboardItem) {
            return player.getMainHandItem();
        }
        if (player.getOffhandItem().getItem() instanceof FireControlClipboardItem) {
            return player.getOffhandItem();
        }
        return ItemStack.EMPTY;
    }

    public record ToggleClipboardModePacket() {
        public static void encode(ToggleClipboardModePacket packet, FriendlyByteBuf buf) {
        }

        public static ToggleClipboardModePacket decode(FriendlyByteBuf buf) {
            return new ToggleClipboardModePacket();
        }

        public static void handle(ToggleClipboardModePacket packet, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                ItemStack stack = getHeldClipboard(player);
                if (!stack.isEmpty()) {
                    stack.getOrCreateTag().putBoolean("WhitelistMode", !stack.getOrCreateTag().getBoolean("WhitelistMode"));
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public record ClearTargetPacket(int index) {
        public static void encode(ClearTargetPacket packet, FriendlyByteBuf buf) {
            buf.writeInt(packet.index);
        }

        public static ClearTargetPacket decode(FriendlyByteBuf buf) {
            return new ClearTargetPacket(buf.readInt());
        }

        public static void handle(ClearTargetPacket packet, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                ItemStack stack = getHeldClipboard(player);
                if (!stack.isEmpty() && stack.hasTag() && stack.getTag().contains("TargetList", 9)) {
                    var list = stack.getTag().getList("TargetList", 8);
                    if (packet.index >= 0 && packet.index < list.size()) {
                        list.remove(packet.index);
                    }
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public record LinkFireControlPacket(BlockPos first, BlockPos second) {
        public static void encode(LinkFireControlPacket packet, FriendlyByteBuf buf) {
            buf.writeBlockPos(packet.first);
            buf.writeBlockPos(packet.second);
        }

        public static LinkFireControlPacket decode(FriendlyByteBuf buf) {
            return new LinkFireControlPacket(buf.readBlockPos(), buf.readBlockPos());
        }

        public static void handle(LinkFireControlPacket packet, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                if (packet.first.distSqr(packet.second) > 36) {
                    player.displayClientMessage(Component.translatable("message.createsentryarm.link_too_far"), true);
                    return;
                }
                net.minecraft.world.level.block.entity.BlockEntity be1 = player.level().getBlockEntity(packet.first);
                net.minecraft.world.level.block.entity.BlockEntity be2 = player.level().getBlockEntity(packet.second);

                if (be1 instanceof AttackArmBlockEntity arm) {
                    if (be2 instanceof BlazeFireControlBlockEntity || SentryCompat.isSentryFireControl(be2)) {
                        arm.setConnectedFireControl(packet.second);
                    }
                } else if (be2 instanceof AttackArmBlockEntity arm) {
                    if (be1 instanceof BlazeFireControlBlockEntity || SentryCompat.isSentryFireControl(be1)) {
                        arm.setConnectedFireControl(packet.first);
                    }
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public record RecordTargetPacket(int entityId) {
        public static void encode(RecordTargetPacket packet, FriendlyByteBuf buf) {
            buf.writeVarInt(packet.entityId);
        }

        public static RecordTargetPacket decode(FriendlyByteBuf buf) {
            return new RecordTargetPacket(buf.readVarInt());
        }

        public static void handle(RecordTargetPacket packet, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                if (player.getMainHandItem().getItem() != net.minecraft.world.item.Items.SPYGLASS
                        || !(player.getOffhandItem().getItem() instanceof FireControlClipboardItem)) {
                    return;
                }
                net.minecraft.world.entity.Entity target = player.level().getEntity(packet.entityId);
                if (target == null || target.distanceToSqr(player) >= 65536.0) {
                    return;
                }
                FireControlClipboardItem.addTarget(player.getOffhandItem(), target.getName().getString(), player);
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
