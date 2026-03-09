package me.replaygif.renderer;

import me.replaygif.core.BossBarRecord;
import me.replaygif.core.ItemSerializer;
import me.replaygif.core.WorldSnapshot;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Draws the Minecraft-style HUD overlay: hearts, armor, food, XP bar, hotbar, death indicator,
 * plus action bar text and boss bars. Drawn after the death overlay (Stage 1l).
 */
public final class HudRenderer {

    private static final Logger LOG = Logger.getLogger(HudRenderer.class.getName());
    private static final int MIN_IMAGE_HEIGHT_FOR_HUD = 80;
    private static final int MAX_HEARTS_PER_ROW = 10;
    private static final int MAX_ARMOR_ICONS = 10;
    private static final int HEART_SPACING_EXTRA = 2;
    private static final int XP_BAR_HEIGHT = 5;
    private static final Color XP_BG = Color.BLACK;
    private static final Color XP_GREEN = new Color(0x7EC030);
    private static final Color XP_YELLOW = new Color(0xF8C030);
    private static final Color XP_CYAN = new Color(0x58C0F0);
    private static final Color HEART_GOLDEN = new Color(0xFFAA00);
    private static final Color HOTBAR_BORDER = Color.WHITE;
    private static final Color DEATH_TEXT_FILL = Color.WHITE;
    private static final Color DEATH_TEXT_OUTLINE = new Color(0xFF0000);

    private static final Color ACTION_BAR_BG = new Color(0, 0, 0, 100);
    private static final int ACTION_BAR_PAD_H = 4;
    private static final int ACTION_BAR_PAD_V = 2;
    private static final int BOSS_BAR_GAP = 4;
    private static final int BOSS_BAR_TOP = 4;
    private static final Color BOSS_BAR_BG = new Color(0x1C1C1C);
    private static final int MAX_BOSS_BARS = 3;

    private static final Color PINK = new Color(0xFF6E91);
    private static final Color BLUE = new Color(0x199FFF);
    private static final Color RED = new Color(0xFF3333);
    private static final Color GREEN = new Color(0x33FF33);
    private static final Color YELLOW = new Color(0xFFFF33);
    private static final Color PURPLE = new Color(0xC033FF);
    private static final Color WHITE = new Color(0xFFFFFF);
    /** Enchantment glint: semi-transparent purple sweep (80, 0, 255, 80). */
    private static final Color GLINT_COLOR = new Color(0x80, 0x00, 0xFF, 80);

    private final int tileWidth;
    private final int tileHeight;
    private final HudResources resources;
    private final ItemTextureCache itemTextureCache;

    public HudRenderer(int tileWidth, int tileHeight, HudResources resources, ItemTextureCache itemTextureCache) {
        this.tileWidth = tileWidth;
        this.tileHeight = tileHeight;
        this.resources = resources;
        this.itemTextureCache = itemTextureCache;
    }

    /**
     * Draws the full HUD (hearts, armor, food, XP, hotbar/death, action bar, boss bars).
     * No-op if imageHeight &lt; 80 (logs DEBUG). frameIndex drives enchantment glint animation.
     */
    public void drawHud(Graphics2D g2d, WorldSnapshot snapshot, int imageWidth, int imageHeight) {
        drawHud(g2d, snapshot, imageWidth, imageHeight, 0);
    }

    /**
     * Draws the full HUD with frame index for glint animation.
     */
    public void drawHud(Graphics2D g2d, WorldSnapshot snapshot, int imageWidth, int imageHeight, int frameIndex) {
        if (imageHeight < MIN_IMAGE_HEIGHT_FOR_HUD) {
            LOG.fine("HUD skipped: image height " + imageHeight + " < " + MIN_IMAGE_HEIGHT_FOR_HUD);
            return;
        }
        int heartY = imageHeight - (tileHeight * 2);
        int spriteSize = Math.min(tileHeight, resources.getSpriteSize());
        boolean dead = snapshot.playerHealth == 0.0f;

        drawXpBar(g2d, snapshot, imageWidth, heartY);
        int armorPoints = totalArmorPoints(snapshot);
        if (armorPoints > 0 && !dead) {
            drawArmorBar(g2d, armorPoints, imageWidth, heartY - tileHeight - 2);
        }
        drawHearts(g2d, snapshot, heartY, spriteSize, dead);
        if (!dead) {
            drawFoodBar(g2d, snapshot.playerFood, imageWidth, heartY, spriteSize);
        }
        if (dead) {
            drawDeathIndicator(g2d, imageWidth, imageHeight);
        } else {
            drawHotbar(g2d, snapshot, imageWidth, imageHeight, frameIndex);
        }
        int barHeight = Math.max(6, tileHeight / 2);
        int fontSize = Math.max(1, (int) (tileHeight * 0.75));
        drawActionBar(g2d, snapshot.actionBarText, imageWidth, imageHeight, fontSize);
        drawBossBars(g2d, snapshot.activeBossBars, imageWidth, imageHeight, barHeight, fontSize);
    }

    /**
     * Draws only action bar and boss bars (for callers without vital HUD resources).
     */
    public static void draw(Graphics2D g, WorldSnapshot snapshot, int imageWidth, int imageHeight, int tileHeight) {
        int barHeight = Math.max(6, tileHeight / 2);
        int fontSize = Math.max(1, (int) (tileHeight * 0.75));
        drawActionBar(g, snapshot.actionBarText, imageWidth, imageHeight, fontSize);
        drawBossBars(g, snapshot.activeBossBars, imageWidth, imageHeight, barHeight, fontSize);
    }

    private void drawXpBar(Graphics2D g, WorldSnapshot snapshot, int imageWidth, int heartY) {
        int xpBarY = heartY - XP_BAR_HEIGHT - 2;
        g.setColor(XP_BG);
        g.fillRect(0, xpBarY, imageWidth, XP_BAR_HEIGHT);
        float progress = Math.max(0f, Math.min(1f, snapshot.playerXpProgress));
        int fillWidth = (int) (imageWidth * progress);
        if (fillWidth > 0) {
            g.setColor(xpColorForLevel(snapshot.playerXpLevel));
            g.fillRect(0, xpBarY, fillWidth, XP_BAR_HEIGHT);
        }
        if (snapshot.playerXpLevel > 0) {
            String levelText = String.valueOf(snapshot.playerXpLevel);
            g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 6));
            int tw = g.getFontMetrics().stringWidth(levelText);
            int tx = (imageWidth - tw) / 2;
            int ty = xpBarY + 4;
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(Color.BLACK);
            g.drawString(levelText, tx - 1, ty);
            g.drawString(levelText, tx + 1, ty);
            g.drawString(levelText, tx, ty - 1);
            g.drawString(levelText, tx, ty + 1);
            g.setColor(Color.WHITE);
            g.drawString(levelText, tx, ty);
        }
    }

    private static Color xpColorForLevel(int level) {
        if (level <= 15) return XP_GREEN;
        if (level <= 30) return XP_YELLOW;
        return XP_CYAN;
    }

    private int totalArmorPoints(WorldSnapshot snapshot) {
        Map<String, Integer> values = resources.getArmorValues();
        int total = 0;
        total += pointsFor(values, snapshot.helmetItem);
        total += pointsFor(values, snapshot.chestplateItem);
        total += pointsFor(values, snapshot.leggingsItem);
        total += pointsFor(values, snapshot.bootsItem);
        return total;
    }

    private static int pointsFor(Map<String, Integer> values, String item) {
        if (item == null) return 0;
        String material = ItemSerializer.getMaterialName(item);
        return material != null ? values.getOrDefault(material, 0) : 0;
    }

    private void drawArmorBar(Graphics2D g, int armorPoints, int imageWidth, int y) {
        int totalIcons = Math.min(MAX_ARMOR_ICONS, (armorPoints + 1) / 2);
        int spacing = tileHeight + HEART_SPACING_EXTRA;
        int rowWidth = MAX_ARMOR_ICONS * tileHeight + (MAX_ARMOR_ICONS - 1) * HEART_SPACING_EXTRA;
        int startX = (imageWidth - rowWidth) / 2;
        BufferedImage full = resources.getArmorFull();
        BufferedImage empty = resources.getArmorEmpty();
        int size = Math.min(tileHeight, resources.getSpriteSize());
        for (int i = 0; i < MAX_ARMOR_ICONS; i++) {
            int x = startX + i * (tileHeight + HEART_SPACING_EXTRA);
            boolean isHalf = (i == totalIcons - 1 && armorPoints % 2 == 1);
            boolean filled = i < totalIcons;
            if (isHalf) {
                g.drawImage(full, x, y, size, size, null);
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
                g.drawImage(empty, x, y, size, size, null);
                g.setComposite(AlphaComposite.SrcOver);
            } else {
                g.drawImage(filled ? full : empty, x, y, size, size, null);
            }
        }
    }

    private void drawHearts(Graphics2D g, WorldSnapshot snapshot, int heartY, int spriteSize, boolean dead) {
        int totalHearts = (int) Math.ceil(snapshot.playerMaxHealth / 2.0);
        totalHearts = Math.min(totalHearts, MAX_HEARTS_PER_ROW * 2);
        int heartSpacing = tileHeight + HEART_SPACING_EXTRA;
        BufferedImage full = resources.getHeartFull();
        BufferedImage half = resources.getHeartHalf();
        BufferedImage empty = resources.getHeartEmpty();
        boolean absorption = snapshot.activePotionEffects != null
                && snapshot.activePotionEffects.stream().anyMatch(s -> "absorption".equalsIgnoreCase(s));
        int startX = 0;
        if (absorption && !dead) {
            int goldenCount = 2;
            for (int i = 0; i < goldenCount; i++) {
                int gx = startX + i * heartSpacing;
                g.drawImage(full, gx, heartY, spriteSize, spriteSize, null);
                g.setComposite(AlphaComposite.SrcIn);
                g.setColor(HEART_GOLDEN);
                g.fillRect(gx, heartY, spriteSize, spriteSize);
                g.setComposite(AlphaComposite.SrcOver);
            }
            startX += goldenCount * heartSpacing;
        }
        int row1 = Math.min(MAX_HEARTS_PER_ROW, totalHearts);
        for (int i = 0; i < row1; i++) {
            double fullThreshold = (i + 1) * 2.0;
            double halfThreshold = i * 2.0 + 1.0;
            int x = startX + i * heartSpacing;
            if (dead) {
                g.drawImage(empty, x, heartY, spriteSize, spriteSize, null);
            } else if (snapshot.playerHealth >= fullThreshold) {
                g.drawImage(full, x, heartY, spriteSize, spriteSize, null);
            } else if (snapshot.playerHealth >= halfThreshold) {
                g.drawImage(half, x, heartY, spriteSize, spriteSize, null);
            } else {
                g.drawImage(empty, x, heartY, spriteSize, spriteSize, null);
            }
        }
        if (totalHearts > MAX_HEARTS_PER_ROW) {
            int row2Count = totalHearts - MAX_HEARTS_PER_ROW;
            int row2Y = heartY - tileHeight - 2;
            for (int i = 0; i < row2Count; i++) {
                double fullThreshold = (MAX_HEARTS_PER_ROW + i + 1) * 2.0;
                double halfThreshold = (MAX_HEARTS_PER_ROW + i) * 2.0 + 1.0;
                int x = startX + i * heartSpacing;
                if (dead) {
                    g.drawImage(empty, x, row2Y, spriteSize, spriteSize, null);
                } else if (snapshot.playerHealth >= fullThreshold) {
                    g.drawImage(full, x, row2Y, spriteSize, spriteSize, null);
                } else if (snapshot.playerHealth >= halfThreshold) {
                    g.drawImage(half, x, row2Y, spriteSize, spriteSize, null);
                } else {
                    g.drawImage(empty, x, row2Y, spriteSize, spriteSize, null);
                }
            }
        }
    }

    private void drawFoodBar(Graphics2D g, int playerFood, int imageWidth, int heartY, int spriteSize) {
        int spacing = tileHeight + HEART_SPACING_EXTRA;
        int totalWidth = 10 * tileHeight + 9 * HEART_SPACING_EXTRA;
        int startX = imageWidth - totalWidth;
        BufferedImage full = resources.getFoodFull();
        BufferedImage empty = resources.getFoodEmpty();
        int food = Math.max(0, Math.min(20, playerFood));
        for (int i = 0; i < 10; i++) {
            int x = startX + i * spacing;
            double fullThreshold = (i + 1) * 2.0;
            double halfThreshold = i * 2.0 + 1.0;
            if (food >= fullThreshold) {
                g.drawImage(full, x, heartY, spriteSize, spriteSize, null);
            } else if (food >= halfThreshold) {
                g.drawImage(full, x, heartY, spriteSize, spriteSize, null);
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
                g.drawImage(empty, x, heartY, spriteSize, spriteSize, null);
                g.setComposite(AlphaComposite.SrcOver);
            } else {
                g.drawImage(empty, x, heartY, spriteSize, spriteSize, null);
            }
        }
    }

    private void drawHotbar(Graphics2D g, WorldSnapshot snapshot, int imageWidth, int imageHeight, int frameIndex) {
        int slotSize = tileWidth + 4;
        int y = imageHeight - tileHeight;
        int mainX = (imageWidth - slotSize) / 2;
        g.setColor(HOTBAR_BORDER);
        g.drawRect(mainX - 2, y - 2, slotSize + 4, slotSize + 4);
        g.drawRect(mainX - 1, y - 1, slotSize + 2, slotSize + 2);
        String mainItem = snapshot.mainHandItem;
        if (mainItem != null) {
            String material = ItemSerializer.getMaterialName(mainItem);
            Optional<BufferedImage> tex = material != null ? itemTextureCache.getTexture(material) : Optional.empty();
            if (tex.isPresent()) {
                g.drawImage(tex.get(), mainX, y, tileWidth, tileWidth, null);
            } else {
                g.setColor(new Color(0x888888));
                g.fillRect(mainX, y, tileWidth, tileWidth);
            }
            if (ItemSerializer.isEnchanted(mainItem)) {
                drawEnchantmentGlint(g, mainX, y, tileWidth, tileWidth, frameIndex);
            }
            g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 4));
            g.setColor(Color.WHITE);
            String label = material != null ? truncate(material, tileWidth, g) : "";
            if (!label.isEmpty()) {
                int lw = g.getFontMetrics().stringWidth(label);
                g.drawString(label, mainX + (tileWidth - lw) / 2, y + tileWidth + 4);
            }
        }
        if (snapshot.offHandItem != null) {
            int smallSize = (int) (tileWidth * 0.75);
            int offX = mainX + slotSize + 4;
            int offY = y + (tileWidth - smallSize) / 2;
            g.setColor(HOTBAR_BORDER);
            g.drawRect(offX - 1, offY - 1, smallSize + 2, smallSize + 2);
            String material = ItemSerializer.getMaterialName(snapshot.offHandItem);
            Optional<BufferedImage> tex = material != null ? itemTextureCache.getTexture(material) : Optional.empty();
            if (tex.isPresent()) {
                g.drawImage(tex.get(), offX, offY, smallSize, smallSize, null);
            } else {
                g.setColor(new Color(0x888888));
                g.fillRect(offX, offY, smallSize, smallSize);
            }
            if (ItemSerializer.isEnchanted(snapshot.offHandItem)) {
                drawEnchantmentGlint(g, offX, offY, smallSize, smallSize, frameIndex);
            }
        }
    }

    /**
     * Draws a semi-transparent purple diagonal sweep (enchantment glint) over the given region.
     * glintOffset = (frameIndex * 2) % (width + height); band width = width/3 at 45°.
     */
    private void drawEnchantmentGlint(Graphics2D g, int left, int top, int width, int height, int frameIndex) {
        if (width < 1 || height < 1) return;
        int bandWidth = Math.max(1, width / 3);
        int period = width + height;
        int glintOffset = (frameIndex * 2) % (period > 0 ? period : 1);
        double t0 = (double) glintOffset / (width + height);
        double t1 = (double) (glintOffset + bandWidth) / (width + height);
        double x0 = t0 * width;
        double y0 = t0 * height;
        double x1 = t1 * width;
        double y1 = t1 * height;
        double len = Math.hypot(width, height);
        if (len < 1e-6) return;
        double perpX = -height / len;
        double perpY = width / len;
        int ext = (int) (2 * Math.max(width, height));
        int[] px = {
                left + (int) (x0 - ext * perpX),
                left + (int) (x0 + ext * perpX),
                left + (int) (x1 + ext * perpX),
                left + (int) (x1 - ext * perpX)
        };
        int[] py = {
                top + (int) (y0 - ext * perpY),
                top + (int) (y0 + ext * perpY),
                top + (int) (y1 + ext * perpY),
                top + (int) (y1 - ext * perpY)
        };
        Shape prevClip = g.getClip();
        g.setClip(left, top, width, height);
        g.setColor(GLINT_COLOR);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, GLINT_COLOR.getAlpha() / 255f));
        g.fillPolygon(px, py, 4);
        g.setComposite(AlphaComposite.SrcOver);
        g.setClip(prevClip);
    }

    private static String truncate(String material, int maxPx, Graphics2D g) {
        if (material == null) return "";
        Font f = g.getFont();
        if (f == null) return material;
        int w = g.getFontMetrics(f).stringWidth(material);
        if (w <= maxPx) return material;
        for (int len = material.length(); len > 0; len--) {
            String s = material.substring(0, len) + "…";
            if (g.getFontMetrics(f).stringWidth(s) <= maxPx) return s;
        }
        return "…";
    }

    private void drawDeathIndicator(Graphics2D g, int imageWidth, int imageHeight) {
        String text = "YOU DIED";
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, tileHeight));
        int tw = g.getFontMetrics().stringWidth(text);
        int tx = (imageWidth - tw) / 2;
        int ty = imageHeight - tileHeight / 2;
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                if (dx != 0 || dy != 0) {
                    g.setColor(DEATH_TEXT_OUTLINE);
                    g.drawString(text, tx + dx, ty + dy);
                }
            }
        }
        g.setColor(DEATH_TEXT_FILL);
        g.drawString(text, tx, ty);
    }

    private static void drawActionBar(Graphics2D g, String actionBarText, int imageWidth, int imageHeight, int fontSize) {
        if (actionBarText == null || actionBarText.isEmpty()) {
            return;
        }
        int y = (int) (imageHeight * 0.7);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, fontSize));
        int textWidth = g.getFontMetrics().stringWidth(actionBarText);
        int textHeight = g.getFontMetrics().getHeight();
        int left = (imageWidth - textWidth) / 2 - ACTION_BAR_PAD_H;
        int top = y - textHeight / 2 - ACTION_BAR_PAD_V;
        int bgWidth = textWidth + ACTION_BAR_PAD_H * 2;
        int bgHeight = textHeight + ACTION_BAR_PAD_V * 2;

        g.setColor(ACTION_BAR_BG);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, ACTION_BAR_BG.getAlpha() / 255f));
        g.fillRect(left, top, bgWidth, bgHeight);
        g.setComposite(AlphaComposite.SrcOver);

        int textX = imageWidth / 2 - textWidth / 2;
        int textY = y - textHeight / 2 + g.getFontMetrics().getAscent();
        g.setColor(Color.BLACK);
        g.drawString(actionBarText, textX + 1, textY + 1);
        g.setColor(Color.WHITE);
        g.drawString(actionBarText, textX, textY);
    }

    private static void drawBossBars(Graphics2D g, List<BossBarRecord> activeBossBars,
                                    int imageWidth, int imageHeight, int barHeight, int fontSize) {
        if (activeBossBars == null || activeBossBars.isEmpty()) {
            return;
        }
        int barWidth = (int) (imageWidth * 0.6);
        int barLeft = (imageWidth - barWidth) / 2;
        int maxBars = Math.min(MAX_BOSS_BARS, activeBossBars.size());

        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, fontSize));

        for (int i = 0; i < maxBars; i++) {
            BossBarRecord bar = activeBossBars.get(i);
            int yStart = BOSS_BAR_TOP + i * (barHeight + BOSS_BAR_GAP);

            int titleY = yStart - 2;
            String title = bar.title != null ? bar.title : "";
            int titleW = g.getFontMetrics().stringWidth(title);
            g.setColor(Color.WHITE);
            g.drawString(title, (imageWidth - titleW) / 2, titleY);

            g.setColor(BOSS_BAR_BG);
            g.fillRect(barLeft, yStart, barWidth, barHeight);

            float progress = Math.max(0f, Math.min(1f, bar.progress));
            int fillWidth = (int) (barWidth * progress);
            if (fillWidth > 0) {
                g.setColor(barColor(bar.color));
                g.fillRect(barLeft, yStart, fillWidth, barHeight);
            }
        }
    }

    private static Color barColor(String colorName) {
        if (colorName == null) return WHITE;
        switch (colorName.toUpperCase()) {
            case "PINK": return PINK;
            case "BLUE": return BLUE;
            case "RED": return RED;
            case "GREEN": return GREEN;
            case "YELLOW": return YELLOW;
            case "PURPLE": return PURPLE;
            case "WHITE": return WHITE;
            default: return WHITE;
        }
    }
}
