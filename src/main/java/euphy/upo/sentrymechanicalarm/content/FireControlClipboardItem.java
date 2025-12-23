package euphy.upo.sentrymechanicalarm.content;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class FireControlClipboardItem extends Item {

    public FireControlClipboardItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);

        if (!level.isClientSide) {
            if (player.isShiftKeyDown()) {
                CompoundTag tag = stack.getOrCreateTag();

                ListTag listTag;
                if (tag.contains("TargetList", Tag.TAG_LIST)) {
                    listTag = tag.getList("TargetList", Tag.TAG_STRING);
                } else {
                    listTag = new ListTag();
                    tag.put("TargetList", listTag);
                }

                String selfName = player.getName().getString();
 
                boolean alreadyExists = false;
                for (Tag t : listTag) {
                    if (t.getAsString().equals(selfName)) {
                        alreadyExists = true;
                        break;
                    }
                }
                if (!alreadyExists) {
                    listTag.add(StringTag.valueOf(selfName));
                    player.displayClientMessage(Component.translatable("message.sentrymechanicalarm.added_self", selfName), true);
                } else {
                    player.displayClientMessage(Component.translatable("message.sentrymechanicalarm.already_on_list"), true);
                }
 
                return InteractionResultHolder.success(stack);
            }

            List<String> targets = new ArrayList<>();
            if (stack.hasTag() && stack.getTag().contains("TargetList", Tag.TAG_LIST)) {
                ListTag listTag = stack.getTag().getList("TargetList", Tag.TAG_STRING);
                for (Tag t : listTag) {
                    targets.add(t.getAsString());
                }
            }
            final List<String> finalTargets = targets;

            NetworkHooks.openScreen((ServerPlayer) player, new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.translatable("item.sentrymechanicalarm.fire_control_clipboard");
                }
                @Nullable
                @Override
                public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
                    return new FireControlMenu(id, inventory, finalTargets);
                }
            }, buf -> {
 
                buf.writeVarInt(finalTargets.size());
                for (String s : finalTargets) {
                    buf.writeUtf(s);
                }
            });
        }

        return InteractionResultHolder.success(stack);
    }
}