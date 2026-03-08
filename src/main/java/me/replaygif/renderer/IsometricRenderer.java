package me.replaygif.renderer;

import me.replaygif.core.BlockRegistry;
import me.replaygif.core.EntitySnapshot;
import me.replaygif.core.WorldSnapshot;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Turns a list of WorldSnapshots into a list of BufferedImages (one per frame) using a fixed
 * isometric projection. Block pass uses painter's algorithm (sort by relX+relZ+relY) and
 * cut plane so only the "front" half of the volume is drawn; entities and overlays (trigger
 * border, death tint, gravestone) are drawn on top. Dimensions are computed from volume
 * and tile size so the pipeline does not depend on hardcoded resolution.
 */
public class IsometricRenderer {

    private static final short AIR_ORDINAL = 0;
    private static final Color TRIGGER_BORDER_COLOR = new Color(0xFFD700); // yellow
    private static final Color DEATH_OVERLAY_COLOR = new Color(180, 0, 0, 76);
    private static final Color MARKER_FALLBACK_COLOR = new Color(128, 128, 128);

    /** Materials drawn with alpha 128 and SRC_OVER. */
    private static final Set<String> TRANSPARENT_MATERIALS = Set.of(
            "GLASS", "WHITE_STAINED_GLASS", "ORANGE_STAINED_GLASS", "MAGENTA_STAINED_GLASS",
            "LIGHT_BLUE_STAINED_GLASS", "YELLOW_STAINED_GLASS", "LIME_STAINED_GLASS",
            "PINK_STAINED_GLASS", "GRAY_STAINED_GLASS", "LIGHT_GRAY_STAINED_GLASS",
            "CYAN_STAINED_GLASS", "PURPLE_STAINED_GLASS", "BLUE_STAINED_GLASS",
            "BROWN_STAINED_GLASS", "GREEN_STAINED_GLASS", "RED_STAINED_GLASS", "BLACK_STAINED_GLASS",
            "TINTED_GLASS", "BARRIER", "ICE", "PACKED_ICE", "BLUE_ICE"
    );

    /** Materials that get per-frame hue shift (liquid shimmer). */
    private static final Set<String> LIQUID_MATERIALS = Set.of("WATER", "LAVA");

    private final int volumeSize;
    private final int tileWidth;
    private final int tileHeight;
    private final int cutOffset;
    private final int imageWidth;
    private final int imageHeight;
    private final BlockColorMap blockColorMap;
    private final BlockRegistry blockRegistry;
    private final EntitySpriteRegistry entitySpriteRegistry;
    private final SkinCache skinCache;

    /** Block-only renderer when entity/skin registries are not available. */
    public IsometricRenderer(int volumeSize, int tileWidth, int tileHeight, int cutOffset,
                            BlockColorMap blockColorMap, BlockRegistry blockRegistry) {
        this(volumeSize, tileWidth, tileHeight, cutOffset, blockColorMap, blockRegistry, null, null);
    }

    /**
     * Full renderer with optional entity and skin support. When entitySpriteRegistry and skinCache
     * are null, entity pass and gravestone are skipped; trigger border and death overlay still apply if context is set.
     */
    public IsometricRenderer(int volumeSize, int tileWidth, int tileHeight, int cutOffset,
                            BlockColorMap blockColorMap, BlockRegistry blockRegistry,
                            EntitySpriteRegistry entitySpriteRegistry, SkinCache skinCache) {
        this.volumeSize = volumeSize;
        this.tileWidth = tileWidth;
        this.tileHeight = tileHeight;
        this.cutOffset = cutOffset;
        this.blockColorMap = blockColorMap;
        this.blockRegistry = blockRegistry;
        this.entitySpriteRegistry = entitySpriteRegistry;
        this.skinCache = skinCache;
        int[] dims = computeImageDimensions();
        this.imageWidth = dims[0];
        this.imageHeight = dims[1];
    }

    /** Carries trigger frame index and death/last-alive state so we draw border and overlay on the right frame. */
    public record RenderFrameContext(
            int triggerFrameIndex,
            String playerName,
            double lastAliveRelX, double lastAliveRelY, double lastAliveRelZ,
            boolean allFramesDead) {}

    /** Derived from volume and tile size so GIF dimensions are consistent and config-driven. Returns [width, height]. */
    public int[] computeImageDimensions() {
        int w = (volumeSize + volumeSize) * (tileWidth / 2) + tileWidth;
        int h = (volumeSize + volumeSize) * (tileHeight / 2) + (volumeSize * tileHeight) + tileHeight;
        return new int[] { w, h };
    }

    /** Width in pixels of each frame image. */
    public int getImageWidth() {
        return imageWidth;
    }

    /** Height in pixels of each frame image. */
    public int getImageHeight() {
        return imageHeight;
    }

    /**
     * Collects visible blocks (non-air, not culled by cut plane), sorts by relX+relZ+relY so
     * back-to-front draw order is correct for the isometric view.
     */
    public List<BlockDrawEntry> buildBlockDrawList(WorldSnapshot snapshot) {
        short[] blocks = snapshot.blocks;
        int vol = snapshot.volumeSize;
        int half = vol / 2;
        List<BlockDrawEntry> list = new ArrayList<>();
        for (int x = 0; x < vol; x++) {
            for (int y = 0; y < vol; y++) {
                for (int z = 0; z < vol; z++) {
                    int idx = x * vol * vol + y * vol + z;
                    short ordinal = blocks[idx];
                    if (ordinal == AIR_ORDINAL) {
                        continue;
                    }
                    int relX = x - half;
                    int relY = y - half;
                    int relZ = z - half;
                    if (CutPlanePolicy.isCulled(relX, relZ, cutOffset)) {
                        continue;
                    }
                    int sortKey = relX + relZ + relY;
                    list.add(new BlockDrawEntry(sortKey, relX, relY, relZ, ordinal));
                }
            }
        }
        list.sort((a, b) -> Integer.compare(a.sortKey, b.sortKey));
        return list;
    }

    /** Screen position of the top-center of a block at the given relative coords. */
    public Point project(int relX, int relY, int relZ) {
        return project((double) relX, (double) relY, (double) relZ);
    }

    /** Same as project(int) but for entity positions in double. */
    public Point project(double relX, double relY, double relZ) {
        int sx = imageWidth / 2 + (int) Math.round((relX - relZ) * (tileWidth / 2.0));
        int sy = imageHeight / 2 + (int) Math.round((relX + relZ) * (tileHeight / 2.0) - relY * tileHeight);
        return new Point(sx, sy);
    }

    /**
     * Draws top/left/right faces for one block. Applies transparency for glass-like blocks,
     * per-frame hue shift for liquids (shimmer), and uses BlockColorMap for emissive brightness.
     */
    public void drawBlock(Graphics2D g, int screenX, int screenY, short materialOrdinal, int frameIndex) {
        BlockFaceColors faces = blockColorMap.getFaces(materialOrdinal);
        boolean transparent = isTransparent(materialOrdinal);
        boolean liquid = isLiquid(materialOrdinal);

        Color top = faces.top();
        Color left = faces.left();
        Color right = faces.right();
        if (liquid) {
            int shiftDegrees = (frameIndex % 10) * 2;
            top = shiftHue(top, shiftDegrees);
            left = shiftHue(left, shiftDegrees);
            right = shiftHue(right, shiftDegrees);
        }
        if (transparent) {
            top = withAlpha(top, 128);
            left = withAlpha(left, 128);
            right = withAlpha(right, 128);
        }

        int halfW = tileWidth / 2;
        int halfH = tileHeight / 2;
        int th = tileHeight;

        // Top face
        int[] topX = { screenX, screenX + halfW, screenX, screenX - halfW };
        int[] topY = { screenY, screenY + halfH, screenY + th, screenY + halfH };
        g.setColor(top);
        g.fillPolygon(topX, topY, 4);

        // Left face
        int[] leftX = { screenX - halfW, screenX, screenX, screenX - halfW };
        int[] leftY = { screenY + halfH, screenY + th, screenY + th + th, screenY + halfH + th };
        g.setColor(left);
        g.fillPolygon(leftX, leftY, 4);

        // Right face
        int[] rightX = { screenX, screenX + halfW, screenX + halfW, screenX };
        int[] rightY = { screenY + th, screenY + halfH, screenY + halfH + th, screenY + th + th };
        g.setColor(right);
        g.fillPolygon(rightX, rightY, 4);
    }

    /** Renders one frame without entities or overlays; context null means block (+ emissive) only. */
    public BufferedImage renderFrame(WorldSnapshot snapshot, int frameIndex) {
        return renderFrame(snapshot, frameIndex, null);
    }

    /**
     * Full frame: blocks (with emissive glow), then entities (if context and registries set),
     * then trigger border on the trigger frame index, then death overlay/gravestone when applicable.
     */
    public BufferedImage renderFrame(WorldSnapshot snapshot, int frameIndex, RenderFrameContext context) {
        BufferedImage image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            // Block pass
            List<BlockDrawEntry> drawList = buildBlockDrawList(snapshot);
            List<EmissiveGlow> glowPositions = new ArrayList<>();
            int vol = snapshot.volumeSize;
            short[] blocks = snapshot.blocks;
            int half = vol / 2;

            for (BlockDrawEntry e : drawList) {
                Point p = project(e.relX, e.relY, e.relZ);
                drawBlock(g, p.x, p.y, e.materialOrdinal, frameIndex);
                if (blockColorMap.isEmissive(e.materialOrdinal)) {
                    BlockFaceColors faces = blockColorMap.getFaces(e.materialOrdinal);
                    Color glowColor = faces.top();
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dy = -1; dy <= 1; dy++) {
                            for (int dz = -1; dz <= 1; dz++) {
                                if (dx == 0 && dy == 0 && dz == 0) continue;
                                int nx = e.relX + dx + half;
                                int ny = e.relY + dy + half;
                                int nz = e.relZ + dz + half;
                                if (nx < 0 || nx >= vol || ny < 0 || ny >= vol || nz < 0 || nz >= vol) continue;
                                int idx = nx * vol * vol + ny * vol + nz;
                                if (blocks[idx] == AIR_ORDINAL) {
                                    glowPositions.add(new EmissiveGlow(e.relX + dx, e.relY + dy, e.relZ + dz, glowColor));
                                }
                            }
                        }
                    }
                }
            }

            // Emissive glow pass
            Set<String> drawn = new HashSet<>();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.2f));
            for (EmissiveGlow glow : glowPositions) {
                String key = glow.relX + "," + glow.relY + "," + glow.relZ;
                if (drawn.contains(key)) continue;
                drawn.add(key);
                Point p = project(glow.relX, glow.relY, glow.relZ);
                g.setColor(glow.color);
                int halfW = tileWidth / 2;
                g.fillRect(p.x - halfW, p.y, tileWidth, tileHeight);
            }
            g.setComposite(AlphaComposite.SrcOver);

            // Entity pass (1f)
            if (context != null && entitySpriteRegistry != null && skinCache != null) {
                drawEntities(g, snapshot, context.playerName());
            }

            // Trigger frame marker (1g)
            if (context != null && frameIndex == context.triggerFrameIndex()) {
                drawTriggerBorder(g);
            }

            // Post-death overlay (1h)
            if (context != null) {
                boolean showDeath = context.allFramesDead()
                        || (snapshot.playerHealth == 0.0f && frameIndex > context.triggerFrameIndex());
                if (showDeath) {
                    drawDeathOverlay(g, context);
                }
            }
        } finally {
            g.dispose();
        }
        return image;
    }

    private void drawEntities(Graphics2D g, WorldSnapshot snapshot, String playerName) {
        List<EntitySnapshot> entities = new ArrayList<>(snapshot.entities);
        entities.sort(Comparator.comparingDouble(e -> e.relX + e.relZ + e.relY));

        for (EntitySnapshot e : entities) {
            Point p = project(e.relX, e.relY, e.relZ);
            int screenX = p.x;
            int screenY = p.y;

            double w = e.boundingWidth;
            double h = e.boundingHeight;
            int spriteW = (int) Math.round(w * tileWidth);
            int spriteH = (int) Math.round(h * tileHeight * 2);
            if (spriteW < 1) spriteW = 1;
            if (spriteH < 1) spriteH = 1;

            BufferedImage sprite = null;
            if (e.isPlayer) {
                Optional<BufferedImage> face = skinCache.getFace(e.uuid);
                sprite = face.orElse(skinCache.getPlaceholder());
            } else {
                Optional<BufferedImage> reg = entitySpriteRegistry.getSprite(e.type);
                sprite = reg.orElse(null);
            }
            if (sprite == null) {
                sprite = createColoredMarker(spriteW, spriteH);
            }

            BufferedImage scaled = scaleSprite(sprite, spriteW, spriteH);
            // Centered on screenX, bottom at screenY + tileHeight (1f)
            int left = screenX - spriteW / 2;
            int top = (int) (screenY + tileHeight - spriteH);
            if (e.invisible) {
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));
            }
            g.drawImage(scaled, left, top, null);
            g.setComposite(AlphaComposite.SrcOver);

            if (e.onFire && entitySpriteRegistry.getFireOverlay() != null) {
                BufferedImage fire = scaleSprite(entitySpriteRegistry.getFireOverlay(), spriteW, spriteH);
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));
                g.drawImage(fire, left, top, null);
                g.setComposite(AlphaComposite.SrcOver);
            }

            String nameTag = e.isPlayer ? (playerName != null ? playerName : "") : (e.customName != null ? e.customName : null);
            if (nameTag != null && !nameTag.isEmpty()) {
                drawNameTag(g, left, top, spriteW, spriteH, nameTag);
            }
        }
    }

    private BufferedImage createColoredMarker(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(MARKER_FALLBACK_COLOR);
        g.fillRect(0, 0, w, h);
        g.dispose();
        return img;
    }

    private static BufferedImage scaleSprite(BufferedImage src, int w, int h) {
        if (src.getWidth() == w && src.getHeight() == h) return src;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private void drawNameTag(Graphics2D g, int spriteLeft, int spriteTop, int spriteW, int spriteH, String text) {
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, tileHeight));
        int textY = spriteTop - 2;
        int textX = spriteLeft + spriteW / 2;
        g.setColor(Color.BLACK);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx != 0 || dy != 0) {
                    g.drawString(text, textX + dx - g.getFontMetrics().stringWidth(text) / 2, textY + dy);
                }
            }
        }
        g.setColor(Color.WHITE);
        g.drawString(text, textX - g.getFontMetrics().stringWidth(text) / 2, textY);
    }

    private void drawTriggerBorder(Graphics2D g) {
        g.setColor(TRIGGER_BORDER_COLOR);
        g.setStroke(new BasicStroke(2f));
        g.drawRect(1, 1, imageWidth - 2, imageHeight - 2);
    }

    private void drawDeathOverlay(Graphics2D g, RenderFrameContext context) {
        g.setColor(DEATH_OVERLAY_COLOR);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, DEATH_OVERLAY_COLOR.getAlpha() / 255f));
        g.fillRect(0, 0, imageWidth, imageHeight);
        g.setComposite(AlphaComposite.SrcOver);

        double gx = context.lastAliveRelX();
        double gy = context.lastAliveRelY();
        double gz = context.lastAliveRelZ();
        if (context.allFramesDead()) {
            gx = gy = gz = 0;
        }
        BufferedImage stone = entitySpriteRegistry != null ? entitySpriteRegistry.getGravestone() : null;
        if (stone != null) {
            int gw = tileWidth;
            int gh = tileHeight * 2;
            BufferedImage scaled = scaleSprite(stone, gw, gh);
            Point p = project(gx, gy, gz);
            int left = p.x - gw / 2;
            int top = (int) (p.y + tileHeight - gh);
            g.drawImage(scaled, left, top, null);
        }
    }

    private boolean isTransparent(short ordinal) {
        Material m = blockRegistry.getMaterial(ordinal);
        return m != null && TRANSPARENT_MATERIALS.contains(m.name());
    }

    private boolean isLiquid(short ordinal) {
        Material m = blockRegistry.getMaterial(ordinal);
        return m != null && LIQUID_MATERIALS.contains(m.name());
    }

    private static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.min(255, Math.max(0, alpha)));
    }

    private static Color shiftHue(Color c, int degrees) {
        float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
        float h = hsb[0] * 360f;
        h = (h + degrees) % 360f;
        if (h < 0) h += 360f;
        int rgb = Color.HSBtoRGB(h / 360f, hsb[1], hsb[2]);
        return new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
    }

    /** Single block entry for the sorted draw list; sortKey = relX+relZ+relY for painter's order. */
    public record BlockDrawEntry(int sortKey, int relX, int relY, int relZ, short materialOrdinal) {}

    private record EmissiveGlow(int relX, int relY, int relZ, Color color) {}
}
