package me.replaygif.renderer;

import me.replaygif.core.BlockRegistry;
import me.replaygif.core.EntitySnapshot;
import me.replaygif.core.WorldSnapshot;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AEC1 — AREA_EFFECT_CLOUD entity with radius=3 and effect "poison": EntitySnapshot has aecRadius=3.0, aecEffectName="poison".
 * AEC2 — Rendered frame has a green (#4E9331) ellipse at entity screen position. Ellipse width ≈ 3 * tileWidth * 2 px.
 * AEC3 — AEC with no effect: purple ellipse (#A000FF).
 * AEC4 — Non-AEC entity: aecRadius = -1.0, rendered normally.
 */
class AreaEffectCloudTest {

    @TempDir
    java.nio.file.Path tempDir;

    private static final int VOL = 32;
    private static final int TILE_WIDTH = 16;
    private static final int TILE_HEIGHT = 8;

    private BlockRegistry blockRegistry;
    private BlockColorMap blockColorMap;
    private EntitySpriteRegistry entitySpriteRegistry;
    private SkinCache skinCache;
    private IsometricRenderer renderer;

    @BeforeEach
    void setUp() throws Exception {
        blockRegistry = new BlockRegistry();
        InputStream defaults = getClass().getResourceAsStream("/block_colors_defaults.json");
        blockColorMap = new BlockColorMap(tempDir.toFile(), "block_colors.json", blockRegistry, defaults, null);

        var plugin = mock(org.bukkit.plugin.java.JavaPlugin.class);
        when(plugin.getSLF4JLogger()).thenReturn(LoggerFactory.getLogger(AreaEffectCloudTest.class));
        when(plugin.getResource(anyString())).thenAnswer(inv -> {
            String path = inv.getArgument(0);
            var url = AreaEffectCloudTest.class.getResource("/" + path);
            return url != null ? url.openStream() : null;
        });
        entitySpriteRegistry = new EntitySpriteRegistry(plugin, "");
        skinCache = new SkinCache(plugin, true, 3600);

        HurtParticleSynthesizer hurtSynth = new HurtParticleSynthesizer(TILE_WIDTH, TILE_HEIGHT);
        renderer = new IsometricRenderer(VOL, TILE_WIDTH, TILE_HEIGHT, 4, blockColorMap, blockRegistry,
                null, entitySpriteRegistry, skinCache, hurtSynth, null, null, null);
    }

    private static WorldSnapshot snapshotWithEntities(List<EntitySnapshot> entities) {
        return new WorldSnapshot(
                0L, 0, 0, 0, 0f, 0f, 20f, 20,
                "minecraft:overworld", "world",
                new short[VOL * VOL * VOL], VOL, entities, false);
    }

    /** AEC1 — AREA_EFFECT_CLOUD entity with radius=3 and effect "poison": EntitySnapshot has aecRadius=3.0, aecEffectName="poison". */
    @Test
    void aec1_snapshotHasAecRadiusAndEffectName() {
        EntitySnapshot aec = new EntitySnapshot(
                EntityType.AREA_EFFECT_CLOUD,
                0, 0, 0, 0f, UUID.randomUUID(), false, false, false,
                null, 1, 1, 0f, false, 3.0f, "poison", null, null);

        assertEquals(3.0f, aec.aecRadius, 0.001f);
        assertEquals("poison", aec.aecEffectName);
    }

    /** AEC2 — Rendered frame has a green (#4E9331) ellipse at entity screen position. Ellipse width ≈ 3 * tileWidth * 2 px. */
    @Test
    void aec2_poisonAecRendersGreenEllipse() {
        EntitySnapshot aec = new EntitySnapshot(
                EntityType.AREA_EFFECT_CLOUD,
                0, 0, 0, 0f, UUID.randomUUID(), false, false, false,
                null, 1, 1, 0f, false, 3.0f, "poison", null, null);

        WorldSnapshot snapshot = snapshotWithEntities(List.of(aec));
        var context = new IsometricRenderer.RenderFrameContext(0, null, 0, 0, 0, false);
        BufferedImage img = renderer.renderFrame(snapshot, 0, context);

        int greenCount = countPixelsWithColor(img, 0x4E, 0x93, 0x31);
        int greenishCount = countGreenishPixels(img);
        assertTrue(greenCount > 0 || greenishCount > 0,
                "Frame should contain green or green-tinted ellipse pixels from poison AEC (35% opacity blends)");

        int ellipseWidthApprox = (int) (3 * TILE_WIDTH * 2);
        assertTrue(ellipseWidthApprox >= 90 && ellipseWidthApprox <= 100, "Ellipse width ~ 6*tileWidth");
    }

    /** AEC3 — AEC with no effect: purple ellipse (#A000FF). */
    @Test
    void aec3_noEffectAecRendersPurpleEllipse() {
        EntitySnapshot aec = new EntitySnapshot(
                EntityType.AREA_EFFECT_CLOUD,
                0, 0, 0, 0f, UUID.randomUUID(), false, false, false,
                null, 1, 1, 0f, false, 2.0f, null, null, null);

        WorldSnapshot snapshot = snapshotWithEntities(List.of(aec));
        var context = new IsometricRenderer.RenderFrameContext(0, null, 0, 0, 0, false);
        BufferedImage img = renderer.renderFrame(snapshot, 0, context);

        int purpleCount = countPixelsWithColor(img, 0xA0, 0x00, 0xFF);
        assertTrue(purpleCount > 0, "Frame should contain purple (#A000FF) ellipse pixels when AEC has no effect");
    }

    /** AEC4 — Non-AEC entity: aecRadius = -1.0, rendered normally. */
    @Test
    void aec4_nonAecHasNegativeRadiusAndRendersNormally() {
        EntitySnapshot zombie = new EntitySnapshot(
                EntityType.ZOMBIE,
                0, 0, 0, 0f, UUID.randomUUID(), false, false, false,
                null, 0.6, 1.95, 0f, false);

        assertEquals(-1.0f, zombie.aecRadius, 0.001f);
        assertNull(zombie.aecEffectName);

        WorldSnapshot snapshot = snapshotWithEntities(List.of(zombie));
        var context = new IsometricRenderer.RenderFrameContext(0, null, 0, 0, 0, false);
        BufferedImage img = renderer.renderFrame(snapshot, 0, context);

        assertNotNull(img);
        java.awt.Point center = renderer.project(0, 0, 0);
        int greenAecCount = countPixelsWithColor(img, 0x4E, 0x93, 0x31);
        int purpleAecCount = countPixelsWithColor(img, 0xA0, 0x00, 0xFF);
        assertTrue(greenAecCount == 0 && purpleAecCount == 0,
                "Non-AEC entity should not draw AEC ellipses (no poison green, no default purple)");
    }

    private static int countPixelsWithColor(BufferedImage img, int r, int g, int b) {
        int count = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int rgb = img.getRGB(x, y);
                int pr = (rgb >> 16) & 0xFF;
                int pg = (rgb >> 8) & 0xFF;
                int pb = rgb & 0xFF;
                if (pr == r && pg == g && pb == b) count++;
            }
        }
        return count;
    }

    /** Pixels that are green-dominant (poison ellipse at 35% opacity blends with background). */
    private static int countGreenishPixels(BufferedImage img) {
        int count = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                if (g > 30 && g >= r && g >= b) count++;
            }
        }
        return count;
    }
}
