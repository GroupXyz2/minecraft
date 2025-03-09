package de.groupxyz.chunker;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class ChunkerScreen extends AbstractContainerScreen<ChunkerMenu> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Chunker.MODID, "textures/gui/chunker_gui.png");

    private Button increaseRadiusButton;
    private Button decreaseRadiusButton;

    public ChunkerScreen(ChunkerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 171;
        this.imageHeight = 128;
    }

    @Override
    protected void init() {
        super.init();

        this.increaseRadiusButton = this.addRenderableWidget(Button.builder(
                        Component.literal("+"),
                        button -> changeRadius(1))
                .pos(this.leftPos + 120, this.topPos + 34)
                .size(20, 20)
                .tooltip(Tooltip.create(Component.nullToEmpty("Increase Radius")))
                .build());

        this.decreaseRadiusButton = this.addRenderableWidget(Button.builder(
                        Component.literal("-"),
                        button -> changeRadius(-1))
                .pos(this.leftPos + 95, this.topPos + 34)
                .size(20, 20)
                .tooltip(Tooltip.create(Component.nullToEmpty("Decrease Radius")))
                .build());

        int currentRadius = menu.getRadius();
        int maxRadius = menu.getMaxRadius();
        this.decreaseRadiusButton.active = (currentRadius > 0);
        this.increaseRadiusButton.active = (currentRadius < maxRadius);
    }

    private int getColorForValue(float percentage) {
        if (percentage < 0.25f) {
            return 0xFF5555;
        } else if (percentage < 0.5f) {
            return 0xFFAA55;
        } else if (percentage < 0.75f) {
            return 0xFFFF55;
        } else {
            return 0x55FF55;
        }
    }

    private void changeRadius(int change) {
        int currentRadius = this.menu.getRadius();
        int newRadius = currentRadius + change;
        int maxRadius = menu.getMaxRadius();

        if (newRadius >= 0 && newRadius <= maxRadius) {
            this.menu.setRadius(newRadius);

            this.decreaseRadiusButton.active = (newRadius > 0);
            this.increaseRadiusButton.active = (newRadius < maxRadius);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Intentionally empty, dont touch!!!
    }

    @Override
    public void resize(net.minecraft.client.Minecraft minecraft, int width, int height) {
        super.resize(minecraft, width, height);
        this.width = width;
        this.height = height;
        this.leftPos = (width - imageWidth) / 2;
        this.topPos = (height - imageHeight) / 2;

        this.increaseRadiusButton.setPosition(this.leftPos + 120, this.topPos + 34);
        this.decreaseRadiusButton.setPosition(this.leftPos + 95, this.topPos + 34);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, TEXTURE);

        guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);

        guiGraphics.fill(leftPos + 8, topPos + 8, leftPos + 22, topPos + 62, 0xFF555555);
        guiGraphics.fill(leftPos + 9, topPos + 9, leftPos + 21, topPos + 61, 0xFF222222);

        int energyStored = menu.getEnergyStored();
        int maxEnergy = menu.getMaxEnergy();
        int energyBarHeight = 50;

        if (maxEnergy > 0) {
            int fillHeight = (int)(((float)energyStored / maxEnergy) * energyBarHeight);

            for (int i = 0; i < fillHeight; i++) {
                int color;
                float ratio = (float)i / energyBarHeight;
                if (ratio < 0.5f) {
                    color = 0xFF000000 | (0xFF << 16) | ((int)(0xFF * (ratio * 2)) << 8);
                } else {
                    color = 0xFF000000 | ((int)(0xFF * (2 - ratio * 2)) << 16) | (0xFF << 8);
                }

                guiGraphics.fill(leftPos + 10, topPos + 60 - i, leftPos + 20, topPos + 61 - i, color);
            }
        }

        guiGraphics.fill(leftPos + 28, topPos + 8, leftPos + 168, topPos + 10, 0xFF888888);  // Top
        guiGraphics.fill(leftPos + 28, topPos + 8, leftPos + 30, topPos + 90, 0xFF888888);   // Left
        guiGraphics.fill(leftPos + 28, topPos + 90, leftPos + 168, topPos + 92, 0xFF888888); // Bottom
        guiGraphics.fill(leftPos + 166, topPos + 8, leftPos + 168, topPos + 92, 0xFF888888); // Right

        guiGraphics.fill(leftPos + 30, topPos + 10, leftPos + 166, topPos + 90, 0x66000000);

        guiGraphics.fill(leftPos + 28, topPos + 100, leftPos + 168, topPos + 102, 0xFF888888);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        renderBackground(guiGraphics);
        int currentRadius = menu.getRadius();
        int maxRadius = menu.getMaxRadius();
        this.decreaseRadiusButton.active = (currentRadius > 0);
        this.increaseRadiusButton.active = (currentRadius < maxRadius);
        super.render(guiGraphics, mouseX, mouseY, delta);

        Component title = Component.translatable("block.chunker.chunker");
        guiGraphics.drawString(font, title, leftPos + imageWidth / 2 - font.width(title) / 2, topPos - 10, 0xFFFFFF, true);

        String energyText = "Energy: ";
        int energyColor = getColorForValue((float)menu.getEnergyStored() / menu.getMaxEnergy());
        guiGraphics.drawString(font, energyText, leftPos + 35, topPos + 15, 0xCCCCCC, true);
        guiGraphics.drawString(font, menu.getEnergyStored() + " / " + menu.getConfigMaxEnergy() + " FE",
                leftPos + 35 + font.width(energyText), topPos + 15, energyColor, true);

        String radiusText = "Radius: ";
        guiGraphics.drawString(font, radiusText, leftPos + 35, topPos + 35, 0xCCCCCC, true);
        guiGraphics.drawString(font, String.valueOf(menu.getRadius()),
                leftPos + 35 + font.width(radiusText), topPos + 35, 0xFFFFAA, true);

        String chunksText = "Chunks: ";
        guiGraphics.drawString(font, chunksText, leftPos + 35, topPos + 50, 0xCCCCCC, true);
        guiGraphics.drawString(font, String.valueOf(menu.getLoadedChunksCount()),
                leftPos + 35 + font.width(chunksText), topPos + 50, 0xFFFFAA, true);

        String energyPerTickText = "Energy/t: ";
        guiGraphics.drawString(font, energyPerTickText, leftPos + 35, topPos + 65, 0xCCCCCC, true);
        guiGraphics.drawString(font, menu.getEnergyPerTick() + " FE (" +
                        menu.getEnergyPerChunk() + " FE/C)",
                leftPos + 35 + font.width(energyPerTickText), topPos + 65, 0xFFAAAA, true);

        boolean isActive = menu.isActive();
        String statusText = "Status: ";
        String status = isActive ? "Active" : "Inactive";
        int statusColor = isActive ? 0x55FF55 : 0xFF5555;

        guiGraphics.drawString(font, statusText, leftPos + 35, topPos + 80, 0xCCCCCC, true);
        guiGraphics.drawString(font, status, leftPos + 35 + font.width(statusText), topPos + 80, statusColor, true);

        if (isActive) {
            RenderSystem.setShaderTexture(0, TEXTURE);
            guiGraphics.blit(TEXTURE, leftPos + 150, topPos + 80, 176, 50, 10, 10);
        }

        if (mouseX >= leftPos + 8 && mouseX <= leftPos + 22 && mouseY >= topPos + 8 && mouseY <= topPos + 62) {
            guiGraphics.renderTooltip(font, Component.literal(menu.getEnergyStored() + " / " + menu.getMaxEnergy() + " FE"), mouseX, mouseY);
        }
    }
}