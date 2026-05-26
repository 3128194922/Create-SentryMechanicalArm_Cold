package com.example.createsentryarm.network;

import com.example.createsentryarm.CreateSentryArmMod;
import com.example.createsentryarm.content.AttackArmBlockEntity;
import com.example.createsentryarm.content.FireControlClipboardItem;
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
                ItemStack stack = player.getMainHandItem();
                if (stack.getItem() instanceof FireControlClipboardItem) {
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
                ItemStack stack = player.getMainHandItem();
                if (stack.getItem() instanceof FireControlClipboardItem && stack.hasTag() && stack.getTag().contains("TargetList", 9)) {
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
                    player.displayClientMessage(Component.literal("火控距离过远"), true);
                    return;
                }
                if (player.level().getBlockEntity(packet.first) instanceof AttackArmBlockEntity arm) {
                    arm.setConnectedFireControl(packet.second);
                } else if (player.level().getBlockEntity(packet.second) instanceof AttackArmBlockEntity arm) {
                    arm.setConnectedFireControl(packet.first);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
