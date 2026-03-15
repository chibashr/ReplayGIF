package com.replayplugin.sidecar.render;

import com.replayplugin.capture.EntityState;
import com.replayplugin.sidecar.asset.AssetManager;
import com.replayplugin.sidecar.asset.AssetNotFoundException;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds a BufferedImage of the player sprite from EntityState: skin regions, pose, scale, equipment overlay.
 */
public final class PlayerSpriteRenderer {

    private static final int SKIN_W = 64;
    private static final int SKIN_H = 64;
    private static final int HEAD_X = 8, HEAD_Y = 0, HEAD_SZ = 8;
    private static final int BODY_X = 20, BODY_Y = 20, BODY_W = 8, BODY_H = 12;
    private static final int ARM_W = 4, ARM_H = 12;
    private static final int RIGHT_ARM_X = 44, RIGHT_ARM_Y = 20;
    private static final int LEFT_ARM_X = 36, LEFT_ARM_Y = 20;
    private static final int RIGHT_LEG_X = 4, RIGHT_LEG_Y = 20;
    private static final int LEFT_LEG_X = 20, LEFT_LEG_Y = 20;

    private final Map<String, BufferedImage> skinCache = new ConcurrentHashMap<>();
    private final AssetManager assetManager;

    public PlayerSpriteRenderer(AssetManager assetManager) {
        this.assetManager = assetManager;
    }

    /**
     * Renders the player sprite for the given entity state and scale (pixelsPerBlock / 16.0).
     */
    public BufferedImage render(EntityState entity, int pixelsPerBlock) {
        double scale = pixelsPerBlock / 16.0;
        BufferedImage skin = getSkin(entity.getSkinTextureUrl());
        if (skin == null) skin = createPlaceholderSkin();

        String pose = entity.getPose() != null ? entity.getPose() : "STANDING";
        BufferedImage sprite = assemblePose(skin, pose);
        if (scale != 1.0) {
            int w = (int) Math.round(sprite.getWidth() * scale);
            int h = (int) Math.round(sprite.getHeight() * scale);
            BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(sprite, 0, 0, w, h, null);
            g.dispose();
            sprite = scaled;
        }

        Map<String, String> equipment = entity.getEquipment() != null ? entity.getEquipment() : Map.of();
        overlayEquipment(sprite, equipment, scale);
        return sprite;
    }

    private BufferedImage getSkin(String url) {
        if (url == null || url.isEmpty()) return null;
        return skinCache.computeIfAbsent(url, u -> {
            try {
                return ImageIO.read(new URL(u));
            } catch (Exception e) {
                return null;
            }
        });
    }

    private BufferedImage createPlaceholderSkin() {
        BufferedImage img = new BufferedImage(SKIN_W, SKIN_H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(198, 124, 78));
        g.fillRect(0, 0, SKIN_W, SKIN_H);
        g.dispose();
        return img;
    }

    private BufferedImage assemblePose(BufferedImage skin, String pose) {
        int spriteW = 16;
        int spriteH = 32;
        switch (pose) {
            case "SNEAKING":
                return assembleSneaking(skin, spriteW, spriteH);
            case "SWIMMING":
                return assembleSwimming(skin, spriteW, spriteH);
            case "SLEEPING":
                return assembleSleeping(skin, spriteW, spriteH);
            case "FALL_FLYING":
                return assembleFallFlying(skin, spriteW, spriteH);
            default:
                return assembleStanding(skin, spriteW, spriteH);
        }
    }

    private BufferedImage assembleStanding(BufferedImage skin, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        int cx = (w - HEAD_SZ) / 2;
        g.drawImage(skin.getSubimage(HEAD_X, HEAD_Y, HEAD_SZ, HEAD_SZ), cx, 0, null);
        g.drawImage(skin.getSubimage(BODY_X, BODY_Y, BODY_W, BODY_H), (w - BODY_W) / 2, HEAD_SZ, null);
        g.drawImage(skin.getSubimage(RIGHT_ARM_X, RIGHT_ARM_Y, ARM_W, ARM_H), (w - BODY_W) / 2 - ARM_W, HEAD_SZ, null);
        g.drawImage(skin.getSubimage(LEFT_ARM_X, LEFT_ARM_Y, ARM_W, ARM_H), (w - BODY_W) / 2 + BODY_W, HEAD_SZ, null);
        g.drawImage(skin.getSubimage(RIGHT_LEG_X, RIGHT_LEG_Y, ARM_W, ARM_H), (w - BODY_W) / 2, HEAD_SZ + BODY_H, null);
        g.drawImage(skin.getSubimage(LEFT_LEG_X, LEFT_LEG_Y, ARM_W, ARM_H), (w - BODY_W) / 2 + ARM_W, HEAD_SZ + BODY_H, null);
        g.dispose();
        return out;
    }

    private BufferedImage assembleSneaking(BufferedImage skin, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        int cx = (w - HEAD_SZ) / 2;
        g.drawImage(skin.getSubimage(HEAD_X, HEAD_Y, HEAD_SZ, HEAD_SZ), cx, 0, null);
        g.drawImage(skin.getSubimage(BODY_X, BODY_Y, BODY_W, BODY_H), (w - BODY_W) / 2, HEAD_SZ, null);
        g.drawImage(skin.getSubimage(RIGHT_ARM_X, RIGHT_ARM_Y, ARM_W, ARM_H), (w - BODY_W) / 2 - ARM_W, HEAD_SZ, null);
        g.drawImage(skin.getSubimage(LEFT_ARM_X, LEFT_ARM_Y, ARM_W, ARM_H), (w - BODY_W) / 2 + BODY_W, HEAD_SZ, null);
        int legY = HEAD_SZ + BODY_H + 4;
        g.drawImage(skin.getSubimage(RIGHT_LEG_X, RIGHT_LEG_Y, ARM_W, ARM_H), (w - BODY_W) / 2 - 2, legY, 6, 12, null);
        g.drawImage(skin.getSubimage(LEFT_LEG_X, LEFT_LEG_Y, ARM_W, ARM_H), (w - BODY_W) / 2 + 2, legY, 6, 12, null);
        g.dispose();
        return out;
    }

    private BufferedImage assembleSwimming(BufferedImage skin, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(skin.getSubimage(HEAD_X, HEAD_Y, HEAD_SZ, HEAD_SZ), 4, 2, null);
        g.drawImage(skin.getSubimage(BODY_X, BODY_Y, BODY_W, BODY_H), 4, 10, null);
        g.drawImage(skin.getSubimage(RIGHT_ARM_X, RIGHT_ARM_Y, ARM_W, ARM_H), 0, 10, null);
        g.drawImage(skin.getSubimage(LEFT_ARM_X, LEFT_ARM_Y, ARM_W, ARM_H), 12, 10, null);
        g.drawImage(skin.getSubimage(RIGHT_LEG_X, RIGHT_LEG_Y, ARM_W, ARM_H), 4, 22, null);
        g.drawImage(skin.getSubimage(LEFT_LEG_X, LEFT_LEG_Y, ARM_W, ARM_H), 8, 22, null);
        g.dispose();
        return out;
    }

    private BufferedImage assembleSleeping(BufferedImage skin, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(skin.getSubimage(HEAD_X, HEAD_Y, HEAD_SZ, HEAD_SZ), 0, 12, null);
        g.drawImage(skin.getSubimage(BODY_X, BODY_Y, BODY_W, BODY_H), 0, 20, null);
        g.drawImage(skin.getSubimage(RIGHT_ARM_X, RIGHT_ARM_Y, ARM_W, ARM_H), -2, 20, null);
        g.drawImage(skin.getSubimage(LEFT_ARM_X, LEFT_ARM_Y, ARM_W, ARM_H), 8, 20, null);
        g.drawImage(skin.getSubimage(RIGHT_LEG_X, RIGHT_LEG_Y, ARM_W, ARM_H), 0, 32, null);
        g.drawImage(skin.getSubimage(LEFT_LEG_X, LEFT_LEG_Y, ARM_W, ARM_H), 4, 32, null);
        g.dispose();
        return out;
    }

    private BufferedImage assembleFallFlying(BufferedImage skin, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        int cx = (w - HEAD_SZ) / 2;
        g.drawImage(skin.getSubimage(HEAD_X, HEAD_Y, HEAD_SZ, HEAD_SZ), cx, 0, null);
        g.drawImage(skin.getSubimage(BODY_X, BODY_Y, BODY_W, BODY_H), (w - BODY_W) / 2, HEAD_SZ, null);
        g.drawImage(skin.getSubimage(RIGHT_ARM_X, RIGHT_ARM_Y, ARM_W, ARM_H), (w - BODY_W) / 2 - ARM_W - 2, HEAD_SZ - 2, ARM_W + 4, ARM_H + 4, null);
        g.drawImage(skin.getSubimage(LEFT_ARM_X, LEFT_ARM_Y, ARM_W, ARM_H), (w - BODY_W) / 2 + BODY_W - 2, HEAD_SZ - 2, ARM_W + 4, ARM_H + 4, null);
        g.drawImage(skin.getSubimage(RIGHT_LEG_X, RIGHT_LEG_Y, ARM_W, ARM_H), (w - BODY_W) / 2 - 2, HEAD_SZ + BODY_H, ARM_W + 4, ARM_H, null);
        g.drawImage(skin.getSubimage(LEFT_LEG_X, LEFT_LEG_Y, ARM_W, ARM_H), (w - BODY_W) / 2 + BODY_W - 2, HEAD_SZ + BODY_H, ARM_W + 4, ARM_H, null);
        g.dispose();
        return out;
    }

    private void overlayEquipment(BufferedImage sprite, Map<String, String> equipment, double scale) {
        if (equipment.isEmpty()) return;
        Graphics2D g = sprite.createGraphics();
        String head = equipment.getOrDefault("head", "minecraft:air");
        if (head != null && !head.isEmpty() && !"minecraft:air".equals(head)) {
            BufferedImage helmet = getItemTexture(head);
            if (helmet != null) {
                int headW = (int) Math.round(8 * scale);
                int headH = (int) Math.round(8 * scale);
                int x = (sprite.getWidth() - headW) / 2;
                g.drawImage(helmet, x, 0, headW, headH, null);
            }
        }
        String mainHand = equipment.getOrDefault("main_hand", "minecraft:air");
        if (mainHand != null && !mainHand.isEmpty() && !"minecraft:air".equals(mainHand)) {
            BufferedImage item = getItemTexture(mainHand);
            if (item != null) {
                int itemW = (int) Math.round(8 * scale);
                int itemH = (int) Math.round(8 * scale);
                int x = sprite.getWidth() - itemW - 2;
                int y = (int) Math.round(12 * scale);
                g.drawImage(item, x, y, itemW, itemH, null);
            }
        }
        g.dispose();
    }

    private BufferedImage getItemTexture(String itemId) {
        if (itemId == null || itemId.isEmpty()) return null;
        String path = itemId.replace("minecraft:", "");
        try {
            return assetManager.getTexture("item/" + path);
        } catch (AssetNotFoundException e) {
            return null;
        }
    }
}
