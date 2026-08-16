package com.createsentryarm.client;

import com.createsentryarm.content.FireControlMenu;
import com.createsentryarm.network.CSANetwork;
import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

public class FireControlScreen extends AbstractContainerScreen<FireControlMenu> {
    private static final ResourceLocation BOOK_TEXTURE = new ResourceLocation("textures/gui/book.png");
    private static final int BTN_WIDTH = 23;
    private static final int BTN_HEIGHT = 13;
    private static final int LIST_START_X = 70;
    private static final int LIST_START_Y = 45;
    private static final int LINE_HEIGHT = 16;
    private static final int ITEMS_PER_PAGE = 12;
    private IconButton modeButton;
    private boolean clientWhitelistState;
    private int currentPage = 0;
    private int btnPrevX;
    private int btnPrevY;
    private int btnNextX;
    private int btnNextY;

    public FireControlScreen(FireControlMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 256;
        imageHeight = 256;
    }

    @Override
    protected void init() {
        super.init();
        btnPrevX = leftPos + 40;
        btnPrevY = topPos + 225;
        btnNextX = leftPos + 180;
        btnNextY = topPos + 225;
        clientWhitelistState = menu.whitelist;

        modeButton = new IconButton(leftPos + 180, topPos + 40, AllIcons.I_BLACKLIST);
        modeButton.withCallback(() -> {
            CSANetwork.CHANNEL.sendToServer(new CSANetwork.ToggleClipboardModePacket());
            clientWhitelistState = !clientWhitelistState;
            menu.whitelist = clientWhitelistState;
            updateModeButtonVisuals();
            playClickSound();
        });
        updateModeButtonVisuals();
        addRenderableWidget(modeButton);
    }

    private void updateModeButtonVisuals() {
        if (clientWhitelistState) {
            modeButton.setIcon(AllIcons.I_WHITELIST);
            modeButton.setToolTip(Component.translatable("gui.createsentryarm.clipboard.whitelist")
                    .append("\n")
                    .append(Component.translatable("gui.createsentryarm.clipboard.whitelist.description").withStyle(ChatFormatting.GRAY))
                    .append("\n")
                    .append(Component.translatable("gui.createsentryarm.clipboard.whitelist.warning").withStyle(ChatFormatting.DARK_RED)));
        } else {
            modeButton.setIcon(AllIcons.I_BLACKLIST);
            modeButton.setToolTip(Component.translatable("gui.createsentryarm.clipboard.blacklist")
                    .append("\n")
                    .append(Component.translatable("gui.createsentryarm.clipboard.blacklist.description").withStyle(ChatFormatting.GRAY)));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        AllGuiTextures.CLIPBOARD.render(graphics, leftPos - 1, topPos - 5);

        List<String> allTargets = menu.getTargetList();
        int totalItems = allTargets.size();
        int maxPage = Math.max(0, (totalItems - 1) / ITEMS_PER_PAGE);
        if (currentPage > maxPage) {
            currentPage = maxPage;
        }

        graphics.drawString(font, (currentPage + 1) + "/" + (maxPage + 1), leftPos + 118, topPos + 15, 0x555555, false);

        int start = currentPage * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, totalItems);
        for (int i = start; i < end; i++) {
            int relativeIndex = i - start;
            String name = allTargets.get(i);
            int lineY = topPos + LIST_START_Y + relativeIndex * LINE_HEIGHT;
            int lineX = leftPos + LIST_START_X;
            String indexStr = (i + 1) + ". ";
            int indexWidth = font.width(indexStr);

            ResourceLocation skinLocation = null;
            if (minecraft != null && minecraft.getConnection() != null) {
                PlayerInfo info = minecraft.getConnection().getPlayerInfo(name);
                if (info != null) {
                    skinLocation = info.getSkinLocation();
                }
            }

            int headWidth = skinLocation != null ? 10 : 0;
            int totalContentWidth = indexWidth + headWidth + font.width(name);
            boolean hovering = isHoveringArea(mouseX, mouseY, lineX, lineY, totalContentWidth, LINE_HEIGHT);
            int color = hovering ? 0xFF0000 : 0x000000;

            graphics.drawString(font, indexStr, lineX, lineY + 1, color, false);
            if (skinLocation != null) {
                RenderSystem.enableBlend();
                int headX = lineX + indexWidth;
                graphics.blit(skinLocation, headX, lineY, 8, 8, 8.0F, 8.0F, 8, 8, 64, 64);
                graphics.blit(skinLocation, headX, lineY, 8, 8, 40.0F, 8.0F, 8, 8, 64, 64);
                RenderSystem.disableBlend();
            }

            graphics.drawString(font, name, lineX + indexWidth + headWidth, lineY + 1, color, false);
            if (hovering) {
                graphics.fill(lineX, lineY + 6, lineX + totalContentWidth, lineY + 7, 0xFFFF0000);
            }
        }

        if (currentPage > 0) {
            boolean hover = isHoveringArea(mouseX, mouseY, btnPrevX, btnPrevY, BTN_WIDTH, BTN_HEIGHT);
            graphics.blit(BOOK_TEXTURE, btnPrevX, btnPrevY, hover ? 23 : 0, 205, BTN_WIDTH, BTN_HEIGHT);
        }
        if (currentPage < maxPage) {
            boolean hover = isHoveringArea(mouseX, mouseY, btnNextX, btnNextY, BTN_WIDTH, BTN_HEIGHT);
            graphics.blit(BOOK_TEXTURE, btnNextX, btnNextY, hover ? 23 : 0, 192, BTN_WIDTH, BTN_HEIGHT);
        }
    }

    private boolean isHoveringArea(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY < y + h;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            List<String> targets = menu.getTargetList();
            int totalItems = targets.size();
            int maxPage = Math.max(0, (totalItems - 1) / ITEMS_PER_PAGE);

            if (currentPage > 0 && isHoveringArea((int) mouseX, (int) mouseY, btnPrevX, btnPrevY, BTN_WIDTH, BTN_HEIGHT)) {
                currentPage--;
                playClickSound();
                return true;
            }
            if (currentPage < maxPage && isHoveringArea((int) mouseX, (int) mouseY, btnNextX, btnNextY, BTN_WIDTH, BTN_HEIGHT)) {
                currentPage++;
                playClickSound();
                return true;
            }

            int start = currentPage * ITEMS_PER_PAGE;
            int end = Math.min(start + ITEMS_PER_PAGE, totalItems);
            for (int i = start; i < end; i++) {
                int relativeIndex = i - start;
                String name = targets.get(i);
                String indexStr = (i + 1) + ". ";
                int indexWidth = font.width(indexStr);
                boolean hasSkin = minecraft != null && minecraft.getConnection() != null && minecraft.getConnection().getPlayerInfo(name) != null;
                int headWidth = hasSkin ? 10 : 0;
                int totalContentWidth = indexWidth + headWidth + font.width(name);
                int lineY = topPos + LIST_START_Y + relativeIndex * LINE_HEIGHT;
                int lineX = leftPos + LIST_START_X;
                if (isHoveringArea((int) mouseX, (int) mouseY, lineX, lineY, totalContentWidth, LINE_HEIGHT)) {
                    CSANetwork.CHANNEL.sendToServer(new CSANetwork.ClearTargetPacket(i));
                    targets.remove(i);
                    playClickSound();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }
}
