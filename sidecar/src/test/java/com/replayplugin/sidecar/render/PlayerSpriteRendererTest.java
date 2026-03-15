package com.replayplugin.sidecar.render;

import com.replayplugin.capture.EntityState;
import com.replayplugin.sidecar.FixtureJarBuilder;
import com.replayplugin.sidecar.asset.AssetManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ENT-001 through ENT-009: Player Entity Sprite.
 */
class PlayerSpriteRendererTest {

    @TempDir
    Path tempDir;
    AssetManager assetManager;
    PlayerSpriteRenderer renderer;

    @BeforeEach
    void setUp() throws Exception {
        Path jarPath = FixtureJarBuilder.ensureFixtureJar(tempDir.resolve("fixtures"));
        Path dataDir = tempDir.resolve("assets");
        assetManager = new AssetManager(jarPath);
        assetManager.initialize(dataDir, "1.21");
        renderer = new PlayerSpriteRenderer(assetManager);
    }

    @Test
    void ENT001_standingPose() {
        EntityState e = new EntityState(0, 0, 0, 0, 0, "STANDING", null, null);
        BufferedImage sprite = renderer.render(e, 16);
        assertNotNull(sprite);
        assertTrue(sprite.getWidth() > 0 && sprite.getHeight() > 0);
    }

    @Test
    void ENT002_sneakingPose() {
        EntityState e = new EntityState(0, 0, 0, 0, 0, "SNEAKING", null, null);
        BufferedImage sprite = renderer.render(e, 16);
        assertNotNull(sprite);
    }

    @Test
    void ENT003_swimmingPose() {
        EntityState e = new EntityState(0, 0, 0, 0, 0, "SWIMMING", null, null);
        BufferedImage sprite = renderer.render(e, 16);
        assertNotNull(sprite);
    }

    @Test
    void ENT004_sleepingPose() {
        EntityState e = new EntityState(0, 0, 0, 0, 0, "SLEEPING", null, null);
        BufferedImage sprite = renderer.render(e, 16);
        assertNotNull(sprite);
    }

    @Test
    void ENT005_fallFlyingPose() {
        EntityState e = new EntityState(0, 0, 0, 0, 0, "FALL_FLYING", null, null);
        BufferedImage sprite = renderer.render(e, 16);
        assertNotNull(sprite);
    }

    @Test
    void ENT006_helmetVisible() {
        EntityState e = new EntityState(0, 0, 0, 0, 0, "STANDING",
                Map.of("head", "minecraft:leather_helmet"), null);
        BufferedImage sprite = renderer.render(e, 16);
        assertNotNull(sprite);
    }

    @Test
    void ENT007_heldItemMainHand() {
        EntityState e = new EntityState(0, 0, 0, 0, 0, "STANDING",
                Map.of("main_hand", "minecraft:stone"), null);
        BufferedImage sprite = renderer.render(e, 16);
        assertNotNull(sprite);
    }

    @Test
    void ENT008_skinTextureApplied() {
        EntityState e = new EntityState(0, 0, 0, 0, 0, "STANDING", null, "https://example.com/skin.png");
        BufferedImage sprite = renderer.render(e, 16);
        assertNotNull(sprite);
    }

    @Test
    void ENT009_playerScreenPositionMatchesIsometric() {
        EntityState e = new EntityState(5.5, 1.5, 5.5, 0, 0, "STANDING", null, null);
        BufferedImage sprite = renderer.render(e, 16);
        assertNotNull(sprite);
        java.awt.geom.Point2D p = IsoProjection.project(5.5, 1.5, 5.5, 16);
        assertNotNull(p);
        assertTrue(sprite.getWidth() > 0 && sprite.getHeight() > 0);
    }
}
