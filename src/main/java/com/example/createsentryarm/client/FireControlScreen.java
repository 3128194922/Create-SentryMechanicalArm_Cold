package com.example.createsentryarm.client;

import com.example.createsentryarm.content.FireControlMenu;
import com.example.createsentryarm.network.CSANetwork;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class FireControlScreen extends AbstractContainerScreen<FireControlMenu> {
    private IconButton modeButton;

    public FireControlScreen(FireControlMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 256;
        imageHeight = 256;
    }

    @Override
    protected void init() {
        super.init();
        modeButton = new IconButton(leftPos + 180, topPos + 40, menu.whitelist ? AllIcons.I_WHITELIST : AllIcons.I_BLACKLIST);
        modeButton.withCallback(() -> {
            CSANetwork.CHANNEL.sendToServer(new CSANetwork.ToggleClipboardModePacket());
            menu.whitelist = !menu.whitelist;
            modeButton.setIcon(menu.whitelist ? AllIcons.I_WHITELIST : AllIcons.I_BLACKLIST);
        });
        addRenderableWidget(modeButton);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        AllGuiTextures.CLIPBOARD.render(graphics, leftPos - 1, topPos - 5);
        int y = topPos + 45;
        int index = 1;
        for (String target : menu.getTargetList()) {
            graphics.drawString(font, index + ". " + target, leftPos + 70, y, 0x222222, false);
            y += 12;
            index++;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int y = topPos + 45;
            for (int i = 0; i < menu.getTargetList().size(); i++) {
                if (mouseX >= leftPos + 70 && mouseX <= leftPos + 190 && mouseY >= y && mouseY <= y + 10) {
                    CSANetwork.CHANNEL.sendToServer(new CSANetwork.ClearTargetPacket(i));
                    menu.getTargetList().remove(i);
                    return true;
                }
                y += 12;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }
}
