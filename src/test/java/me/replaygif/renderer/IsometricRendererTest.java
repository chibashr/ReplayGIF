package me.replaygif.renderer;

import me.replaygif.core.BlockRegistry;
import me.replaygif.core.EntitySnapshot;
import me.replaygif.core.WorldSnapshot;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for IsometricRenderer covering IR1–IR10 from .planning/testing.md.
 */
class IsometricRendererTest {

    @TempDir
    Path tempDir;

    private BlockRegistry blockRegistry;
    private BlockColorMap blockColorMap;
    private IsometricRenderer renderer;

    @BeforeEach
    void setUp() throws IOException {
        blockRegistry = new BlockRegistry();
        File dataFolder = tempDir.toFile();
        InputStream defaults = getClass().getResourceAsStream("/block_colors_defaults.json");
        blockColorMap = new BlockColorMap(dataFolder, "block_colors.json", blockRegistry, defaults);
        renderer = new IsometricRenderer(32, 16, 8, 4, blockColorMap, blockRegistry);
    }

    private EntitySpriteRegistry createEntitySpriteRegistry() throws IOException {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getResource(anyString())).thenAnswer(inv -> {
            String path = inv.getArgument(0);
            var url = IsometricRendererTest.class.getResource("/" + path);
            return url != null ? url.openStream() : null;
        });
        return new EntitySpriteRegistry(plugin, "");
    }

    private SkinCache createSkinCache() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getResource(anyString())).thenAnswer(inv -> {
            String path = inv.getArgument(0);
            var url = IsometricRendererTest.class.getResource("/" + path);
            return url != null ? url.openStream() : null;
        });
        return new SkinCache(plugin, true, 3600);
    }

    /** IR1 — Output dimensions: 32-block volume, tile 16×8 → exact formula dimensions. */
    @Test
    void ir1_outputDimensions() {
        int w = renderer.getImageWidth();
        int h = renderer.getImageHeight();
        // imageWidth  = (vol+vol)*(tileWidth/2)  + tileWidth  = 64*8 + 16 = 528
        // imageHeight = (vol+vol)*(tileHeight/2) + (vol*tileHeight) + tileHeight = 64*4 + 256 + 8 = 520
        assertEquals(528, w, "imageWidth");
        assertEquals(520, h, "imageHeight");
        int[] dims = renderer.computeImageDimensions();
        assertEquals(528, dims[0]);
        assertEquals(520, dims[1]);
        // Consistency: render a frame and check image size
        WorldSnapshot snapshot = emptySnapshot(32);
        BufferedImage img = renderer.renderFrame(snapshot, 0);
        assertEquals(528, img.getWidth());
        assertEquals(520, img.getHeight());
    }

    /** IR2 — Painter's algorithm: 2×2×2 cube renders with correct depth order; output saved for manual check. */
    @Test
    void ir2_paintersAlgorithm() throws IOException {
        int vol = 8;
        int half = vol / 2;
        short stone = blockRegistry.getOrdinal(Material.STONE);
        short[] blocks = new short[vol * vol * vol];
        // 2×2×2 cube at origin: rel (-1,0,-1) to (0,1,0) -> volume (half-1, half, half-1) to (half, half+1, half)
        for (int dx = 0; dx < 2; dx++) {
            for (int dy = 0; dy < 2; dy++) {
                for (int dz = 0; dz < 2; dz++) {
                    int x = half - 1 + dx;
                    int y = half + dy;
                    int z = half - 1 + dz;
                    blocks[x * vol * vol + y * vol + z] = stone;
                }
            }
        }
        IsometricRenderer smallRenderer = new IsometricRenderer(vol, 16, 8, 4, blockColorMap, blockRegistry);
        WorldSnapshot snapshot = new WorldSnapshot(
                0L, 0, 0, 0, 0f, 0f, 20f, 20,
                "minecraft:overworld", "world", blocks, vol, List.of(), false);
        BufferedImage img = smallRenderer.renderFrame(snapshot, 0);
        assertNotNull(img);
        assertTrue(img.getWidth() > 0 && img.getHeight() > 0);
        // Save for manual inspection: verify no face is drawn over a face that should be in front
        File out = new File(tempDir.toFile(), "ir2_cube.png");
        ImageIO.write(img, "PNG", out);
        assertTrue(out.exists(), "IR2 reference image written to " + out.getAbsolutePath() + " — inspect for painter's order");
    }

    /** IR3 — Dollhouse cutout: block at (5,0,5) culled when cutOffset=4; block at (2,0,2) not culled. */
    @Test
    void ir3_dollhouseCutout() {
        int vol = 12;
        int half = vol / 2;
        short stone = blockRegistry.getOrdinal(Material.STONE);
        short[] blocks = new short[vol * vol * vol];
        // rel (5,0,5) -> volume (half+5, half, half+5) = (11, 6, 11)
        int x1 = half + 5, y1 = half, z1 = half + 5;
        blocks[x1 * vol * vol + y1 * vol + z1] = stone;
        // rel (2,0,2) -> volume (half+2, half, half+2) = (8, 6, 8)
        int x2 = half + 2, y2 = half, z2 = half + 2;
        blocks[x2 * vol * vol + y2 * vol + z2] = stone;

        IsometricRenderer cutRenderer = new IsometricRenderer(vol, 16, 8, 4, blockColorMap, blockRegistry);
        WorldSnapshot snapshot = new WorldSnapshot(
                0L, 0, 0, 0, 0f, 0f, 20f, 20,
                "minecraft:overworld", "world", blocks, vol, List.of(), false);
        List<IsometricRenderer.BlockDrawEntry> drawList = cutRenderer.buildBlockDrawList(snapshot);
        // Only the block at (2,0,2) should be in the list; (5,0,5) is culled because 5+5=10 > 4
        assertEquals(1, drawList.size(), "Exactly one block should be drawn (other culled)");
        assertEquals(2, drawList.get(0).relX());
        assertEquals(0, drawList.get(0).relY());
        assertEquals(2, drawList.get(0).relZ());

        BufferedImage img = cutRenderer.renderFrame(snapshot, 0);
        assertNotNull(img);
        // Projected position of (2,0,2) should have non-transparent pixel; (5,0,5) should not appear
        java.awt.Point pVisible = cutRenderer.project(2, 0, 2);
        int rgb = img.getRGB(pVisible.x, pVisible.y);
        int alpha = (rgb >> 24) & 0xFF;
        assertTrue(alpha > 0, "Visible block at (2,0,2) should paint pixel at projection");
    }

    /** IR4 — Cave visibility: 10-block thick ceiling above origin; cut_offset=4 culls near-side blocks. */
    @Test
    void ir4_caveVisibility() {
        int vol = 12;
        int half = vol / 2;
        short stone = blockRegistry.getOrdinal(Material.STONE);
        short[] blocks = new short[vol * vol * vol];
        // Ceiling: relY 1..10, relX relZ in -2..2 (no hole — full ceiling)
        for (int relY = 1; relY <= 10; relY++) {
            for (int relX = -2; relX <= 2; relX++) {
                for (int relZ = -2; relZ <= 2; relZ++) {
                    int x = half + relX, y = half + relY, z = half + relZ;
                    if (x >= 0 && x < vol && y >= 0 && y < vol && z >= 0 && z < vol) {
                        blocks[x * vol * vol + y * vol + z] = stone;
                    }
                }
            }
        }
        IsometricRenderer caveRenderer = new IsometricRenderer(vol, 16, 8, 4, blockColorMap, blockRegistry);
        WorldSnapshot snapshot = new WorldSnapshot(
                0L, 0, 0, 0, 0f, 0f, 20f, 20,
                "minecraft:overworld", "world", blocks, vol, List.of(), false);
        List<IsometricRenderer.BlockDrawEntry> drawList = caveRenderer.buildBlockDrawList(snapshot);
        // With cut_offset=4, any block with relX+relZ > 4 must be culled (not in list)
        for (IsometricRenderer.BlockDrawEntry e : drawList) {
            assertTrue(e.relX() + e.relZ() <= 4,
                    "Drawn block (relX+relZ) must be <= cutOffset 4");
        }
        // Near-side ceiling blocks (e.g. rel (2,1,3) -> sum 5) must not be in draw list
        boolean hasCulled = drawList.stream().noneMatch(e -> e.relX() == 2 && e.relZ() == 3 && e.relY() == 1);
        assertTrue(hasCulled, "Block at (2,1,3) has relX+relZ=5 > 4 so must be culled");
        BufferedImage img = caveRenderer.renderFrame(snapshot, 0);
        assertEquals(caveRenderer.getImageWidth(), img.getWidth());
        assertEquals(caveRenderer.getImageHeight(), img.getHeight());
    }

    /** IR5 — Entity name tag: player at (0,0,0) with known name; name tag area has non-white pixels. */
    @Test
    void ir5_entityNameTag() throws IOException {
        EntitySpriteRegistry entityRegistry = createEntitySpriteRegistry();
        SkinCache skinCache = createSkinCache();
        IsometricRenderer fullRenderer = new IsometricRenderer(32, 16, 8, 4, blockColorMap, blockRegistry, entityRegistry, skinCache);
        EntitySnapshot player = new EntitySnapshot(
                EntityType.PLAYER, 0, 0, 0, 0f, UUID.randomUUID(), true, false, false,
                null, 0.6, 1.8);
        WorldSnapshot snapshot = new WorldSnapshot(
                0L, 0, 0, 0, 0f, 0f, 20f, 20,
                "minecraft:overworld", "world",
                new short[32 * 32 * 32], 32, List.of(player), false);
        var context = new IsometricRenderer.RenderFrameContext(0, "TestPlayer", 0, 0, 0, false);
        BufferedImage img = fullRenderer.renderFrame(snapshot, 0, context);
        java.awt.Point center = fullRenderer.project(0, 0, 0);
        int nameTagY = (int) (center.y + 8 - 2);
        int nameTagX = center.x;
        boolean hasNonWhite = false;
        for (int dx = -20; dx <= 20 && !hasNonWhite; dx++) {
            for (int dy = -5; dy <= 0 && !hasNonWhite; dy++) {
                int x = nameTagX + dx;
                int y = nameTagY + dy;
                if (x >= 0 && x < img.getWidth() && y >= 0 && y < img.getHeight()) {
                    int rgb = img.getRGB(x, y);
                    int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                    if (r != 255 || g != 255 || b != 255) hasNonWhite = true;
                }
            }
        }
        assertTrue(hasNonWhite, "Name tag area above player should contain non-white pixels");
    }

    /** IR6 — Post-death red overlay: frame after trigger with playerHealth=0 has elevated red channel. */
    @Test
    void ir6_postDeathRedOverlay() throws IOException {
        EntitySpriteRegistry entityRegistry = createEntitySpriteRegistry();
        SkinCache skinCache = createSkinCache();
        IsometricRenderer fullRenderer = new IsometricRenderer(32, 16, 8, 4, blockColorMap, blockRegistry, entityRegistry, skinCache);
        WorldSnapshot aliveSnapshot = new WorldSnapshot(
                0L, 0, 0, 0, 0f, 0f, 20f, 20,
                "minecraft:overworld", "world", new short[32 * 32 * 32], 32, List.of(), false);
        WorldSnapshot deadSnapshot = new WorldSnapshot(
                0L, 0, 0, 0, 0f, 0f, 0f, 20,
                "minecraft:overworld", "world", new short[32 * 32 * 32], 32, List.of(), false);
        var context = new IsometricRenderer.RenderFrameContext(0, null, 0, 0, 0, false);
        BufferedImage imgAlive = fullRenderer.renderFrame(aliveSnapshot, 1, context);
        BufferedImage imgDead = fullRenderer.renderFrame(deadSnapshot, 1, context);
        double avgRedAlive = averageRed(imgAlive);
        double avgRedDead = averageRed(imgDead);
        assertTrue(avgRedDead > avgRedAlive, "Death overlay frame should have elevated red channel");
    }

    /** IR7 — Gravestone marker: after death, gravestone at player origin position. */
    @Test
    void ir7_gravestoneMarker() throws IOException {
        EntitySpriteRegistry entityRegistry = createEntitySpriteRegistry();
        SkinCache skinCache = createSkinCache();
        IsometricRenderer fullRenderer = new IsometricRenderer(32, 16, 8, 4, blockColorMap, blockRegistry, entityRegistry, skinCache);
        WorldSnapshot deadSnapshot = new WorldSnapshot(
                0L, 0, 0, 0, 0f, 0f, 0f, 20,
                "minecraft:overworld", "world", new short[32 * 32 * 32], 32, List.of(), false);
        var context = new IsometricRenderer.RenderFrameContext(0, null, 0, 0, 0, false);
        BufferedImage img = fullRenderer.renderFrame(deadSnapshot, 1, context);
        java.awt.Point origin = fullRenderer.project(0, 0, 0);
        int gx = origin.x;
        int gy = (int) (origin.y + 8);
        boolean hasNonTransparent = false;
        for (int dx = -8; dx <= 8 && !hasNonTransparent; dx++) {
            for (int dy = 0; dy < 20 && !hasNonTransparent; dy++) {
                int x = gx + dx;
                int y = gy + dy;
                if (x >= 0 && x < img.getWidth() && y >= 0 && y < img.getHeight()) {
                    if (((img.getRGB(x, y) >> 24) & 0xFF) > 0) hasNonTransparent = true;
                }
            }
        }
        assertTrue(hasNonTransparent, "Gravestone area at player origin should have non-transparent pixels");
    }

    /** IR8 — Trigger frame yellow border: only frame index 4 has yellow border. */
    @Test
    void ir8_triggerFrameYellowBorder() throws IOException {
        WorldSnapshot snapshot = emptySnapshot(32);
        EntitySpriteRegistry entityRegistry = createEntitySpriteRegistry();
        SkinCache skinCache = createSkinCache();
        IsometricRenderer fullRenderer = new IsometricRenderer(32, 16, 8, 4, blockColorMap, blockRegistry, entityRegistry, skinCache);
        var context = new IsometricRenderer.RenderFrameContext(4, null, 0, 0, 0, false);
        int yellowBorderFrame = -1;
        for (int f = 0; f < 10; f++) {
            BufferedImage img = fullRenderer.renderFrame(snapshot, f, context);
            if (hasYellowBorder(img)) yellowBorderFrame = f;
        }
        assertEquals(4, yellowBorderFrame, "Only frame 4 should have yellow border");
    }

    /** IR9 — Liquid shimmer: LAVA (liquid) frame 0 and 5 differ (hue shift (f%10)*2). */
    @Test
    void ir9_waterShimmerVariesAcrossFrames() {
        int vol = 8;
        int half = vol / 2;
        short lava = blockRegistry.getOrdinal(Material.LAVA);
        short[] blocks = new short[vol * vol * vol];
        blocks[half * vol * vol + half * vol + half] = lava;
        WorldSnapshot snapshot = new WorldSnapshot(
                0L, 0, 0, 0, 0f, 0f, 20f, 20,
                "minecraft:overworld", "world", blocks, vol, List.of(), false);
        IsometricRenderer r = new IsometricRenderer(vol, 16, 8, 4, blockColorMap, blockRegistry);
        BufferedImage img0 = r.renderFrame(snapshot, 0);
        BufferedImage img5 = r.renderFrame(snapshot, 5);
        java.awt.Point p = r.project(0, 0, 0);
        boolean differs = false;
        for (int dx = -4; dx <= 4 && !differs; dx++) {
            for (int dy = 0; dy <= 8 && !differs; dy++) {
                int x = p.x + dx;
                int y = p.y + dy;
                if (x >= 0 && x < img0.getWidth() && y >= 0 && y < img0.getHeight()) {
                    if (img0.getRGB(x, y) != img5.getRGB(x, y)) differs = true;
                }
            }
        }
        assertTrue(differs, "Liquid block pixels should differ between frame 0 and 5 (shimmer)");
    }

    /** IR10 — Entity outside volume: relX = volumeSize; no exception, sprite drawn (clipped). */
    @Test
    void ir10_entityOutsideVolumeClamped() throws IOException {
        EntitySpriteRegistry entityRegistry = createEntitySpriteRegistry();
        SkinCache skinCache = createSkinCache();
        int vol = 32;
        IsometricRenderer fullRenderer = new IsometricRenderer(vol, 16, 8, 4, blockColorMap, blockRegistry, entityRegistry, skinCache);
        EntitySnapshot outside = new EntitySnapshot(
                EntityType.ZOMBIE, vol, 0, 0, 0f, UUID.randomUUID(), false, false, false,
                null, 0.6, 1.95);
        WorldSnapshot snapshot = new WorldSnapshot(
                0L, 0, 0, 0, 0f, 0f, 20f, 20,
                "minecraft:overworld", "world", new short[vol * vol * vol], vol, List.of(outside), false);
        var context = new IsometricRenderer.RenderFrameContext(0, null, 0, 0, 0, false);
        assertDoesNotThrow(() -> fullRenderer.renderFrame(snapshot, 0, context));
        BufferedImage img = fullRenderer.renderFrame(snapshot, 0, context);
        assertNotNull(img);
    }

    private static double averageRed(BufferedImage img) {
        long sum = 0;
        int count = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                sum += (img.getRGB(x, y) >> 16) & 0xFF;
                count++;
            }
        }
        return count > 0 ? (double) sum / count : 0;
    }

    private static boolean hasYellowBorder(BufferedImage img) {
        int yellow = 0xFFD700;
        int w = img.getWidth();
        int h = img.getHeight();
        for (int x = 1; x < w - 1; x++) {
            if (nearColor(img.getRGB(x, 1), yellow) || nearColor(img.getRGB(x, h - 2), yellow)) return true;
        }
        for (int y = 1; y < h - 1; y++) {
            if (nearColor(img.getRGB(1, y), yellow) || nearColor(img.getRGB(w - 2, y), yellow)) return true;
        }
        return false;
    }

    private static boolean nearColor(int rgb, int expected) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        int er = (expected >> 16) & 0xFF, eg = (expected >> 8) & 0xFF, eb = expected & 0xFF;
        return Math.abs(r - er) <= 2 && Math.abs(g - eg) <= 2 && Math.abs(b - eb) <= 2;
    }

    private static WorldSnapshot emptySnapshot(int volumeSize) {
        return new WorldSnapshot(
                0L, 0, 0, 0, 0f, 0f, 20f, 20,
                "minecraft:overworld", "world",
                new short[volumeSize * volumeSize * volumeSize],
                volumeSize, List.of(), false);
    }
}
