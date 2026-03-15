package com.replayplugin.sidecar.asset;

import com.replayplugin.sidecar.FixtureJarBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BLK-001 through BLK-017: Block Model Resolution.
 */
class BlockModelResolverTest {

    @TempDir
    Path tempDir;
    AssetManager assetManager;
    BlockModelResolver resolver;

    @BeforeEach
    void setUp() throws IOException {
        Path jarPath = FixtureJarBuilder.ensureFixtureJar(tempDir.resolve("fixtures"));
        Path dataDir = tempDir.resolve("assets");
        assetManager = new AssetManager(jarPath);
        assetManager.initialize(dataDir, "1.21");
        resolver = new BlockModelResolver(assetManager);
    }

    @Test
    void BLK001_fullCubeBlock_stone() {
        BlockGeometry g = resolver.resolve("minecraft:stone", Map.of());
        assertNotNull(g);
        assertFalse(g.getElements().isEmpty());
        assertFalse(g.getTextures().isEmpty());
        assertTrue(g.getTextures().containsKey("all") || g.getTextures().values().stream().anyMatch(t -> t != null));
    }

    @Test
    void BLK002_slab_halfHeight() {
        BlockGeometry g = resolver.resolve("minecraft:oak_slab", Map.of("type", "bottom"));
        assertNotNull(g);
        assertFalse(g.getElements().isEmpty());
        double[] from = g.getElements().get(0).getFrom();
        double[] to = g.getElements().get(0).getTo();
        double height = Math.abs(to[1] - from[1]);
        assertTrue(height <= 8.01 && height >= 7.99, "slab height ~8: " + height);
    }

    @Test
    void BLK003_stairs_variant() {
        BlockGeometry g = resolver.resolve("minecraft:oak_stairs", Map.of("facing", "east", "half", "bottom", "shape", "straight"));
        assertNotNull(g);
        assertFalse(g.getElements().isEmpty());
    }

    @Test
    void BLK004_fence_multipart() {
        BlockGeometry g = resolver.resolve("minecraft:oak_fence", Map.of());
        assertNotNull(g);
        assertFalse(g.getElements().isEmpty());
    }

    @Test
    void BLK005_wall_multipart() {
        BlockGeometry g = resolver.resolve("minecraft:cobblestone_wall", Map.of());
        assertNotNull(g);
        assertFalse(g.getElements().isEmpty());
    }

    @Test
    void BLK006_door_lowerAndUpper() {
        BlockGeometry lower = resolver.resolve("minecraft:oak_door", Map.of("half", "lower", "facing", "north", "open", "false"));
        BlockGeometry upper = resolver.resolve("minecraft:oak_door", Map.of("half", "upper", "facing", "north", "open", "false"));
        assertNotNull(lower);
        assertNotNull(upper);
        assertFalse(lower.getElements().isEmpty());
        assertFalse(upper.getElements().isEmpty());
    }

    @Test
    void BLK007_trapdoor_variant() {
        BlockGeometry g = resolver.resolve("minecraft:oak_trapdoor", Map.of("half", "bottom", "open", "false"));
        assertNotNull(g);
        assertFalse(g.getElements().isEmpty());
    }

    @Test
    void BLK008_glassPane_paneGeometry() {
        BlockGeometry g = resolver.resolve("minecraft:glass_pane", Map.of());
        assertNotNull(g);
        assertFalse(g.getElements().isEmpty());
    }

    @Test
    void BLK009_ironBars() {
        BlockGeometry g = resolver.resolve("minecraft:iron_bars", Map.of());
        assertNotNull(g);
        assertFalse(g.getElements().isEmpty());
    }

    @Test
    void BLK010_blockNotInSet_fallbackSolidCube() {
        BlockGeometry g = resolver.resolve("minecraft:unknown_block_type", Map.of());
        assertNotNull(g);
        assertFalse(g.getElements().isEmpty());
        assertEquals(1, g.getElements().size());
        double[] to = g.getElements().get(0).getTo();
        assertEquals(16.0, to[0]);
        assertEquals(16.0, to[1]);
        assertEquals(16.0, to[2]);
    }

    @Test
    void BLK011_biomeTinted_grass() {
        BlockGeometry g = resolver.resolve("minecraft:grass_block", Map.of());
        assertNotNull(g);
        assertTrue(g.isRequiresBiomeTint());
    }

    @Test
    void BLK012_biomeTinted_leaves() {
        BlockGeometry g = resolver.resolve("minecraft:oak_leaves", Map.of());
        assertNotNull(g);
        assertTrue(g.isRequiresBiomeTint());
    }

    @Test
    void BLK013_biomeTinted_water() {
        BlockGeometry g = resolver.resolve("minecraft:water", Map.of());
        assertNotNull(g);
        assertTrue(g.isRequiresBiomeTint());
    }

    @Test
    void BLK014_animated_fire_firstFrame() {
        BlockGeometry g = resolver.resolve("minecraft:fire", Map.of());
        assertNotNull(g);
        assertFalse(g.getElements().isEmpty());
    }

    @Test
    void BLK015_animated_lava_firstFrame() {
        BlockGeometry g = resolver.resolve("minecraft:lava", Map.of());
        assertNotNull(g);
    }

    @Test
    void BLK016_animated_waterSurface_firstFrame() {
        BlockGeometry g = resolver.resolve("minecraft:water", Map.of());
        assertNotNull(g);
    }

    @Test
    void BLK017_animated_netherPortal_firstFrame() {
        BlockGeometry g = resolver.resolve("minecraft:nether_portal", Map.of("axis", "z"));
        assertNotNull(g);
        assertFalse(g.getElements().isEmpty());
    }
}
